package com.ishaara.mobile.skills

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Notification
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Process
import com.ishaara.mobile.core.model.ActionResult
import java.text.DateFormat
import java.util.Date

class AppStateSkill(private val context: Context, private val apps: AppLauncher) {
    private companion object {
        const val RECENT_WINDOW_MS = 3 * 60 * 60 * 1000L
        const val MAX_NOTIFICATIONS = 15
    }

    /** The caller presses Home first. */
    fun stopBackground(name: String): ActionResult = when (val resolved = apps.resolve(name)) {
        is AppLauncher.Resolved.Error -> resolved.result
        is AppLauncher.Resolved.App -> {
            context.getSystemService(ActivityManager::class.java).killBackgroundProcesses(resolved.packageName)
            ActionResult.ok("Closed ${resolved.label}.", selfVerified = true)
        }
    }

    fun recentApps(): ActionResult {
        if (!hasUsageAccess()) {
            return ActionResult.fail(
                "USAGE_ACCESS_NEEDED",
                "Usage access is off. Use press with recents and read that screen, or tell the user to allow it in Ishaara's setup.",
            )
        }
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(now - RECENT_WINDOW_MS, now)
        val event = UsageEvents.Event()
        val seen = linkedSetOf<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                seen.remove(event.packageName)
                seen.add(event.packageName)
            }
        }
        // asReversed, not reversed: compileSdk 36 binds reversed() to a JDK method missing before Android 15.
        // Anything without a launcher label, such as the home screen itself, comes back as its package name.
        val recent = seen.toList().asReversed().filter { it != context.packageName }.map(apps::labelOf).filter { '.' !in it }
        return ActionResult.ok(if (recent.isEmpty()) "No recent apps." else "Recently used, newest first: ${recent.joinToString()}")
    }

    fun mediaState(): ActionResult {
        val playing = context.getSystemService(AudioManager::class.java).isMusicActive
        val fallback = if (playing) "Audio is playing, but details need Notification access." else "Nothing is playing."
        val sessions = try {
            context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(context, MediaAccessService::class.java))
        } catch (e: SecurityException) {
            return ActionResult.ok(fallback)
        }
        val session = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return ActionResult.ok(fallback)
        val metadata = session.metadata
        val state = if (session.playbackState?.state == PlaybackState.STATE_PLAYING) "Playing" else "Paused"
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "unknown title"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        return ActionResult.ok(
            "$state: $title" + (artist?.let { " by $it" } ?: "") + " in ${apps.labelOf(session.packageName)}.",
        )
    }

    fun notifications(): ActionResult {
        val listener = MediaAccessService.instance ?: return ActionResult.fail(
            "NOTIFICATION_ACCESS_NEEDED",
            "Notification access is off. Use press with notifications and read that screen, or tell the user to allow it in Ishaara's setup.",
        )
        val lines = listener.activeNotifications.orEmpty()
            .filter { !it.isOngoing }
            .sortedByDescending { it.postTime }
            .take(MAX_NOTIFICATIONS)
            .map { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)
                "${apps.labelOf(sbn.packageName)}: $title: $text"
            }
        return ActionResult.ok(if (lines.isEmpty()) "No notifications." else lines.joinToString("\n"))
    }

    fun deviceStatus(): ActionResult {
        val battery = context.getSystemService(BatteryManager::class.java)
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "connected"
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val time = DateFormat.getDateTimeInstance().format(Date())
        val charging = if (battery.isCharging) ", charging" else ""
        return ActionResult.ok("Battery $level%$charging. Network: $network. Media volume $volume%. Time: $time.")
    }

    private fun hasUsageAccess(): Boolean {
        val mode = context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
