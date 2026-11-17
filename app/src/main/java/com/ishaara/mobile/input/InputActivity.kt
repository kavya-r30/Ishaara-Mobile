package com.ishaara.mobile.input

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ishaara.mobile.IshaaraApp
import com.ishaara.mobile.core.PanelState
import com.ishaara.mobile.core.ScriptLanguage
import com.ishaara.mobile.ui.components.IshaaraOrb
import com.ishaara.mobile.ui.components.RoundIconButton
import com.ishaara.mobile.ui.components.Waveform
import com.ishaara.mobile.ui.theme.Ishaara
import com.ishaara.mobile.ui.theme.IshaaraTheme
import com.ishaara.mobile.voice.VoiceEvent
import com.ishaara.mobile.voice.VoiceInput

/**
 * The chat bar, as a transparent activity over the current app. As a real foreground activity it
 * gets normal keyboard behaviour and microphone access, which overlay windows don't reliably get.
 */
class InputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // During a call, the assistant gesture fires by accident: a cheek on the screen corner, a held
        // side key or a headset button. The bar then popped up over the call, so it stays closed. Opening
        // it on purpose, from the Quick Settings tile or the app, still works.
        if (intent?.action == Intent.ACTION_ASSIST && onCall()) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as IshaaraApp
        app.speaker.stop()
        val reply = intent.getBooleanExtra(EXTRA_REPLY, false)
        val startListening = intent.getBooleanExtra(EXTRA_LISTEN, false)
        val voice = VoiceInput(app.http) { app.settings.current().sarvamApiKey }

        setContent {
            val state by app.session.state.collectAsState()
            IshaaraTheme {
                InputBar(
                    voice = voice,
                    reply = reply,
                    question = (state as? PanelState.Asking)?.takeIf { reply }?.question,
                    startListening = startListening,
                    hasMic = { checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                    onDismiss = ::finish,
                    onSubmit = { text, spokenLanguage, spoken ->
                        if (reply) {
                            app.session.answer(text, spoken)
                        } else {
                            app.session.start(text, ScriptLanguage.detect(text, spokenLanguage), spoken)
                        }
                        finish()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as IshaaraApp).inputOpen.value = true
    }

    override fun onStop() {
        (application as IshaaraApp).inputOpen.value = false
        super.onStop()
        // Otherwise a later launch would reopen this instance with its old mode.
        if (!isChangingConfigurations) finish()
    }

    /** A phone or internet call is ringing or in progress. */
    private fun onCall(): Boolean = when (getSystemService(AudioManager::class.java).mode) {
        AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> true
        else -> false
    }

    companion object {
        const val EXTRA_REPLY = "reply"
        const val EXTRA_LISTEN = "listen"
    }
}

private enum class Mic { OFF, LISTENING, UNDERSTANDING }

@Composable
private fun InputBar(
    voice: VoiceInput,
    reply: Boolean,
    question: String?,
    startListening: Boolean,
    hasMic: () -> Boolean,
    onDismiss: () -> Unit,
    onSubmit: (text: String, language: String?, spoken: Boolean) -> Unit,
) {
    val colors = Ishaara.colors
    var text by remember { mutableStateOf("") }
    var mic by remember { mutableStateOf(if (startListening && hasMic()) Mic.LISTENING else Mic.OFF) }
    var session by remember { mutableStateOf(if (mic == Mic.LISTENING) 1 else 0) }
    var level by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun startMic() {
        error = null
        text = ""
        mic = Mic.LISTENING
        session++
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startMic() else error = "Allow the microphone to use voice."
    }

    LaunchedEffect(Unit) { if (mic == Mic.OFF) focus.requestFocus() }
    LaunchedEffect(session) {
        if (session == 0) return@LaunchedEffect
        // Answers are usually names and emails, which belong in English letters.
        voice.listen(latin = reply).collect { event ->
            when (event) {
                is VoiceEvent.Level -> level = event.value
                VoiceEvent.Understanding -> mic = Mic.UNDERSTANDING
                is VoiceEvent.Final -> onSubmit(event.text.trimEnd('.', '।', ' '), event.language, true)
                is VoiceEvent.Failed -> error = event.message
            }
        }
        mic = Mic.OFF
        level = 0f
    }

    val shape = RoundedCornerShape(30.dp)
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) }
    val scrim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scrim.animateTo(0.18f, tween(200)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim.value))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .graphicsLayer {
                    translationY = (1f - entrance.value) * 40.dp.toPx()
                    alpha = entrance.value.coerceIn(0f, 1f)
                    val scale = 0.96f + 0.04f * entrance.value
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(shape)
                .background(colors.surface)
                .then(if (mic == Mic.LISTENING) Modifier.border(BorderStroke(1.5.dp, colors.signal), shape) else Modifier)
                .clickable(remember { MutableInteractionSource() }, null) { }
                .heightIn(min = 60.dp)
                .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            question?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp),
                )
            }
            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.danger,
                    modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IshaaraOrb(32.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (mic != Mic.OFF) {
                        Text(
                            if (mic == Mic.LISTENING) "Listening…" else "Understanding…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    } else {
                        if (text.isEmpty()) {
                            Text(
                                if (question != null) "Type your answer" else "Ask Ishaara",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.textPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) onSubmit(text.trim(), null, false) }),
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && text.isNotBlank()) {
                                        onSubmit(text.trim(), null, false)
                                        true
                                    } else {
                                        false
                                    }
                                },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                when (mic) {
                    Mic.LISTENING -> {
                        Waveform(level, Modifier.padding(end = 10.dp))
                        RoundIconButton(Icons.Rounded.Check, "Done speaking", filled = true) { voice.finishNow() }
                    }
                    Mic.UNDERSTANDING -> Unit
                    Mic.OFF -> {
                        RoundIconButton(Icons.Rounded.Mic, "Speak", filled = text.isBlank()) {
                            if (hasMic()) startMic() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        if (text.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            RoundIconButton(Icons.AutoMirrored.Rounded.Send, "Send", filled = true) { onSubmit(text.trim(), null, false) }
                        }
                    }
                }
            }
        }
    }
}
