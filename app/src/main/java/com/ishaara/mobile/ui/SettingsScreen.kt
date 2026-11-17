package com.ishaara.mobile.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ishaara.mobile.IshaaraApp
import com.ishaara.mobile.R
import com.ishaara.mobile.data.AppSettings
import com.ishaara.mobile.service.IshaaraTileService
import com.ishaara.mobile.ui.components.GroupCard
import com.ishaara.mobile.ui.components.ListRow
import com.ishaara.mobile.ui.components.PrimaryButton
import com.ishaara.mobile.ui.components.RowDivider
import com.ishaara.mobile.ui.components.SectionLabel
import com.ishaara.mobile.ui.components.TopBar
import com.ishaara.mobile.ui.theme.Ishaara
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

@Composable
fun SettingsScreen(app: IshaaraApp, settings: AppSettings, onBack: () -> Unit, onSetup: () -> Unit) {
    val colors = Ishaara.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun update(change: (AppSettings) -> AppSettings) {
        scope.launch { app.settings.update(change) }
    }

    fun test() {
        testing = true
        result = null
        scope.launch {
            val azure = app.llm.testConnection(sampleJpeg())
            if (azure.textOk) app.settings.update { it.copy(visionEnabled = azure.visionOk) }
            val voice = try {
                app.tts.synthesize("नमस्ते", "hi-IN")
                "Voice works."
            } catch (e: IOException) {
                "Voice failed: ${e.message}"
            }
            result = "${azure.message}\n$voice"
            testing = false
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar("Settings", onBack)
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Azure OpenAI")
            GroupCard {
                Field("Endpoint", settings.azureEndpoint) { v -> update { it.copy(azureEndpoint = v.trim()) } }
                RowDivider()
                Field("Deployment", settings.azureDeployment) { v -> update { it.copy(azureDeployment = v.trim()) } }
                RowDivider()
                Field("API version", settings.azureApiVersion) { v -> update { it.copy(azureApiVersion = v.trim()) } }
                RowDivider()
                Field("API key", settings.azureApiKey, secret = true) { v -> update { it.copy(azureApiKey = v.trim()) } }
            }

            SectionLabel("Voice")
            GroupCard {
                Toggle("Speak replies", "After a voice command.", settings.spokenReplies) { v -> update { it.copy(spokenReplies = v) } }
                RowDivider()
                Field("Sarvam API key", settings.sarvamApiKey, secret = true) { v -> update { it.copy(sarvamApiKey = v.trim()) } }
                RowDivider()
                Field("Speech model", settings.ttsModel) { v -> update { it.copy(ttsModel = v.trim()) } }
                RowDivider()
                Field("Voice", settings.ttsSpeaker) { v -> update { it.copy(ttsSpeaker = v.trim()) } }
                RowDivider()
                Field("Voice for English", settings.ttsSpeakerEnglish) { v -> update { it.copy(ttsSpeakerEnglish = v.trim()) } }
            }

            SectionLabel("Behaviour")
            GroupCard {
                Toggle("Use screenshots", "When a screen can't be read any other way.", settings.visionEnabled) { v -> update { it.copy(visionEnabled = v) } }
            }

            SectionLabel("Open from anywhere")
            GroupCard {
                ListRow(
                    title = "Set as default assistant",
                    subtitle = "Choose Ishaara as Digital assistant app, then hold the side button.",
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    RowDivider()
                    ListRow(
                        title = "Add to Quick Settings",
                        subtitle = "Open Ishaara from the panel you swipe down.",
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary) },
                        onClick = { requestTile(context) },
                    )
                }
                RowDivider()
                ListRow(
                    title = "Permissions",
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary) },
                    onClick = onSetup,
                )
            }

            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(if (testing) "Testing…" else "Test connection", Modifier.fillMaxWidth()) { if (!testing) test() }
                result?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val colors = Ishaara.colors
    // Local state: saving to DataStore is asynchronous, and feeding it straight back would make the cursor jump.
    var text by remember { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.textPrimary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Ishaara.colors
    ListRow(
        title = title,
        subtitle = subtitle,
        trailing = {
            Switch(
                checked,
                onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                    uncheckedTrackColor = colors.surfaceRaised,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedBorderColor = colors.surfaceRaised,
                ),
            )
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, IshaaraTileService::class.java),
        "Ishaara",
        Icon.createWithResource(context, R.drawable.ic_tile),
        context.mainExecutor,
    ) { }
}

private fun sampleJpeg(): String {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.DKGRAY) }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.getEncoder().encodeToString(out.toByteArray())
}
