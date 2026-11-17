package com.ishaara.mobile.overlay

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.telephony.TelephonyManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ishaara.mobile.IshaaraApp
import com.ishaara.mobile.core.PanelState
import com.ishaara.mobile.input.InputActivity
import com.ishaara.mobile.service.OverlayControl
import com.ishaara.mobile.ui.theme.IshaaraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Tells the controller about taps outside the card. Those still reach the app underneath. */
private class PanelRoot(context: Context, private val onOutsideTouch: () -> Unit) : FrameLayout(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch()
            return true
        }
        return super.dispatchTouchEvent(event)
    }
}

class OverlayController(
    private val service: AccessibilityService,
    private val app: IshaaraApp,
) : OverlayControl {
    private companion object {
        const val GLOW_FADE_MS = 500L
        const val IDLE_HEIGHT = 1
    }

    private val windows = service.getSystemService(WindowManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val owner = OverlayLifecycleOwner()
    private val call = MutableStateFlow(CallState.IDLE)
    private val capturing = MutableStateFlow(false)
    private var panel: View? = null
    private var glow: ComposeView? = null
    private var hasCard = false
    private var gesturing = false

    private val panelParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        IDLE_HEIGHT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        edgeToEdge()
    }

    private val callReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            call.value = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING -> CallState.RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> CallState.IN_CALL
                else -> CallState.IDLE
            }
        }
    }

    fun start() {
        owner.start()
        addGlow()
        addPanel()
        ContextCompat.registerReceiver(
            service, callReceiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        scope.launch {
            app.session.state.map { it is PanelState.Running }.distinctUntilChanged().collectLatest { running ->
                if (running) {
                    glow?.visibility = View.VISIBLE
                } else {
                    delay(GLOW_FADE_MS)
                    glow?.visibility = View.GONE
                }
            }
        }
        // In a voice conversation, questions are spoken and then listened for.
        scope.launch {
            app.session.state.collectLatest { state ->
                if (state is PanelState.Asking && app.session.spoken) {
                    if (app.settings.current().spokenReplies) app.speaker.speak(state.question)
                    openInput(reply = true, listen = true)
                }
            }
        }
    }

    fun stop() {
        service.unregisterReceiver(callReceiver)
        scope.cancel()
        panel?.let(windows::removeView)
        glow?.let(windows::removeView)
        panel = null
        glow = null
        owner.destroy()
    }

    override fun setTouchable(touchable: Boolean) {
        gesturing = !touchable
        applyPanelLayout()
    }

    override suspend fun setHidden(hidden: Boolean) {
        capturing.value = hidden
        panel?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        if (hidden) delay(120)
    }

    private fun applyPanelLayout() {
        val view = panel ?: return
        val notTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        panelParams.flags = if (hasCard && !gesturing) {
            panelParams.flags and notTouchable.inv()
        } else {
            panelParams.flags or notTouchable
        }
        windows.updateViewLayout(view, panelParams)
    }

    private fun setPanelHeight(px: Int) {
        hasCard = px > 0
        panelParams.height = if (hasCard) px else IDLE_HEIGHT
        applyPanelLayout()
    }

    private fun addGlow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { edgeToEdge() }
        val radius = displayCornerRadius()
        glow = ComposeView(service).also { view ->
            owner.attach(view)
            view.visibility = View.GONE
            view.setContent {
                val state by app.session.state.collectAsState()
                EdgeGlow(active = state is PanelState.Running, cornerRadiusPx = radius)
            }
            windows.addView(view, params)
        }
    }

    private fun addPanel() {
        val actions = OverlayActions(
            compose = { listen ->
                app.session.dismiss()
                openInput(reply = false, listen = listen)
            },
            reply = { listen -> openInput(reply = true, listen = listen) },
            stop = {
                app.speaker.stop()
                app.session.stop()
            },
            answer = { app.session.answer(it) },
            dismiss = { app.session.dismiss() },
            callCommand = { app.session.start(it, null, spoken = false) },
        )
        val density = service.resources.displayMetrics.density
        val navigationBar = windows.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        val compose = ComposeView(service).also { view ->
            owner.attach(view)
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            view.setContent {
                val state by app.session.state.collectAsState()
                val callState by call.collectAsState()
                val inputOpen by app.inputOpen.collectAsState()
                IshaaraTheme {
                    OverlayPanel(
                        state = state,
                        call = callState,
                        hidden = inputOpen,
                        bottomInset = (navigationBar / density).dp,
                        actions = actions,
                        onWindowHeight = ::setPanelHeight,
                    )
                }
            }
        }
        val root = PanelRoot(service) {
            if (app.session.state.value is PanelState.Result) app.session.dismiss()
        }
        owner.attach(root)
        root.addView(compose)
        panel = root
        windows.addView(root, panelParams)
    }

    private fun displayCornerRadius(): Float {
        val fallback = service.resources.displayMetrics.density * 28
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback
        val corner = windows.currentWindowMetrics.windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        return corner?.radius?.takeIf { it > 0 }?.toFloat() ?: fallback
    }

    private fun openInput(reply: Boolean, listen: Boolean) {
        service.startActivity(
            Intent(service, InputActivity::class.java)
                .putExtra(InputActivity.EXTRA_REPLY, reply)
                .putExtra(InputActivity.EXTRA_LISTEN, listen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Lets a window reach every edge of the display, under the status bar, navigation bar and any cutout. */
private fun WindowManager.LayoutParams.edgeToEdge() {
    fitInsetsTypes = 0
    isFitInsetsIgnoringVisibility = true
    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
}
