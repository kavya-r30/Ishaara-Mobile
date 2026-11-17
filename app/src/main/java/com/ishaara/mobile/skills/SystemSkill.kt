package com.ishaara.mobile.skills

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.SettingsSection

class SystemSkill(private val context: Context) {
    fun setAlarm(hour: Int, minute: Int, label: String?): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return launch(intent, "Alarm set for %02d:%02d.".format(hour, minute), "No clock app was found.", selfVerified = true)
    }

    fun setTimer(seconds: Int, label: String?): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        return launch(intent, "Timer started.", "No clock app was found.", selfVerified = true)
    }

    fun openAlarms(): ActionResult = launch(
        Intent(AlarmClock.ACTION_SHOW_ALARMS),
        "Alarm list opened. Tap the switch next to the alarm.",
        "No clock app was found. Use open_app with Clock.",
    )

    fun setFlashlight(on: Boolean): ActionResult {
        val camera = context.getSystemService(CameraManager::class.java)
        return try {
            val id = camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ActionResult.fail("NO_FLASH", "This phone has no flashlight.")
            camera.setTorchMode(id, on)
            ActionResult.ok(if (on) "Flashlight on." else "Flashlight off.", selfVerified = true)
        } catch (e: CameraAccessException) {
            ActionResult.fail("FLASH_BUSY", "The flashlight is in use by another app.")
        }
    }

    fun openCamera(video: Boolean): ActionResult = launch(
        Intent(if (video) MediaStore.INTENT_ACTION_VIDEO_CAMERA else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        "Camera opened. Now tap the shutter.",
        "No camera app was found.",
    )

    fun openSettings(section: SettingsSection): ActionResult {
        val action = when (section) {
            SettingsSection.MAIN -> Settings.ACTION_SETTINGS
            SettingsSection.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingsSection.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingsSection.MOBILE_DATA -> Settings.ACTION_DATA_ROAMING_SETTINGS
            SettingsSection.SOUND -> Settings.ACTION_SOUND_SETTINGS
            SettingsSection.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingsSection.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingsSection.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            SettingsSection.APPS -> Settings.ACTION_APPLICATION_SETTINGS
        }
        // CLEAR_TASK so it opens on the requested page, not wherever Settings was last left.
        return launch(Intent(action).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK), "Settings opened.", "That settings page isn't available.")
    }

    fun webSearch(query: String): ActionResult = launch(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")),
        "Search results opened.",
        "No browser was found.",
    )

    fun navigate(place: String): ActionResult = launch(
        Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}")),
        "Directions to $place started.",
        "No maps app was found.",
    )

    fun setVolume(percent: Int): ActionResult {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max * percent / 100, AudioManager.FLAG_SHOW_UI)
        return ActionResult.ok("Volume set to $percent%.", selfVerified = true)
    }

    fun addContact(name: String, phone: String): ActionResult = launch(
        Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(ContactsContract.Intents.Insert.NAME, name)
            .putExtra(ContactsContract.Intents.Insert.PHONE, phone),
        "New contact form opened with $name and $phone filled in. Now tap Save.",
        "No contacts app was found.",
    )

    fun openUrl(url: String): ActionResult {
        if (!UrlPolicy.isAllowed(url)) return ActionResult.fail("URL_BLOCKED", "That kind of link isn't allowed.")
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "Link opened.", "No app can open that link.")
    }

    private fun launch(intent: Intent, success: String, missing: String, selfVerified: Boolean = false): ActionResult = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.ok(success, selfVerified)
    } catch (e: ActivityNotFoundException) {
        ActionResult.fail("NOT_AVAILABLE", missing)
    }
}
