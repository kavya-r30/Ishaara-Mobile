package com.ishaara.mobile.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ishaara.mobile.IshaaraApp
import com.ishaara.mobile.overlay.OverlayController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class IshaaraService : AccessibilityService() {
    val settler = ScreenSettler()
    var device: AndroidDevice? = null
        private set

    private var overlay: OverlayController? = null

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            (application as IshaaraApp).session.stop()
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Ishaara service connected")
        val controller = OverlayController(this, application as IshaaraApp)
        overlay = controller
        device = AndroidDevice(this, controller)
        controller.start()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        _instance.value = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != packageName) settler.onEvent()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        _instance.value = null
        device = null
        overlay?.let {
            it.stop()
            unregisterReceiver(screenOff)
        }
        overlay = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Ishaara"
        private val _instance = MutableStateFlow<IshaaraService?>(null)
        val instance: StateFlow<IshaaraService?> = _instance
    }
}
