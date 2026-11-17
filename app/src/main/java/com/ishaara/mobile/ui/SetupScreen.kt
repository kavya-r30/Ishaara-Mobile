package com.ishaara.mobile.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ishaara.mobile.data.AppSettings
import com.ishaara.mobile.service.IshaaraService
import com.ishaara.mobile.ui.components.Chip
import com.ishaara.mobile.ui.components.GroupCard
import com.ishaara.mobile.ui.components.IconBadge
import com.ishaara.mobile.ui.components.ListRow
import com.ishaara.mobile.ui.components.RowDivider
import com.ishaara.mobile.ui.components.SectionLabel
import com.ishaara.mobile.ui.components.TopBar
import com.ishaara.mobile.ui.theme.Ishaara

/** Reads each setup requirement. Accessibility, microphone and connection are the ones required to work. */
object SetupStatus {
    fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun accessibility(): Boolean = IshaaraService.instance.value != null

    fun usageAccess(context: Context): Boolean =
        context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    fun notificationAccess(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.contains(context.packageName) == true

    fun ready(context: Context, settings: AppSettings?): Boolean =
        accessibility() && has(context, Manifest.permission.RECORD_AUDIO) && settings?.azure()?.isComplete == true
}

private val INSET_AFTER_BADGE = 70.dp

@Composable
fun SetupScreen(settings: AppSettings?, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val colors = Ishaara.colors
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val phone = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar("Set up Ishaara", onBack)
        // key: every status below is read again when the user comes back from a system screen.
        key(refresh) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("Required")
                GroupCard {
                    SetupRow(
                        Icons.Rounded.AccessibilityNew, "Accessibility",
                        if (SetupStatus.accessibility()) {
                            "Lets Ishaara see the screen and act in other apps."
                        } else {
                            "Lets Ishaara see the screen and act in other apps. If the switch is greyed out, open App info for Ishaara, tap the menu, then choose Allow restricted settings."
                        },
                        SetupStatus.accessibility(),
                    ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    RowDivider(INSET_AFTER_BADGE)
                    SetupRow(
                        Icons.Rounded.Mic, "Microphone", "For voice commands.",
                        SetupStatus.has(context, Manifest.permission.RECORD_AUDIO),
                    ) { permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) }
                    RowDivider(INSET_AFTER_BADGE)
                    SetupRow(
                        Icons.Rounded.Cloud, "Connection", "Your Azure OpenAI and Sarvam details.",
                        settings?.azure()?.isComplete == true,
                        onAction = onOpenSettings,
                    )
                }

                SectionLabel("Recommended")
                GroupCard {
                    SetupRow(
                        Icons.Rounded.Contacts, "Contacts", "To message and call people by name.",
                        SetupStatus.has(context, Manifest.permission.READ_CONTACTS),
                    ) { permissions.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }
                    RowDivider(INSET_AFTER_BADGE)
                    SetupRow(
                        Icons.Rounded.Call, "Phone", "To place, answer and end calls.",
                        phone.all { SetupStatus.has(context, it) },
                    ) { permissions.launch(phone) }
                }

                SectionLabel("Optional")
                GroupCard {
                    SetupRow(
                        Icons.Rounded.BarChart, "Usage access", "To list recently used apps.",
                        SetupStatus.usageAccess(context),
                    ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    RowDivider(INSET_AFTER_BADGE)
                    SetupRow(
                        Icons.Rounded.Notifications, "Notification access", "To read notifications and what's playing.",
                        SetupStatus.notificationAccess(context),
                    ) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                }

                Text(
                    "Ishaara reads the screen only while a command you started is running. Screen text and screenshots go to your Azure deployment, and voice goes to Sarvam while the mic is on.\n\nOn Xiaomi, Oppo, Vivo and Realme phones, also turn off battery optimisation for Ishaara so Android doesn't stop it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, reason: String, done: Boolean, onAction: () -> Unit) {
    val colors = Ishaara.colors
    ListRow(
        title = title,
        subtitle = reason,
        leading = { IconBadge(icon) },
        trailing = {
            if (done) {
                Icon(Icons.Rounded.CheckCircle, "Done", Modifier.size(24.dp), tint = colors.success)
            } else {
                Chip("Allow", onClick = onAction)
            }
        },
    )
}
