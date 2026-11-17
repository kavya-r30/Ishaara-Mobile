package com.ishaara.mobile.skills

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.telecom.TelecomManager
import com.ishaara.mobile.core.model.ActionResult
import kotlinx.coroutines.delay

@SuppressLint("MissingPermission")
class PhoneSkill(private val context: Context, private val contacts: ContactResolver) {
    private val telecom get() = context.getSystemService(TelecomManager::class.java)
    private val audio get() = context.getSystemService(AudioManager::class.java)

    val isRinging: Boolean get() = audio.mode == AudioManager.MODE_RINGTONE
    private val isInCall: Boolean
        get() = audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION

    suspend fun call(target: String): ActionResult {
        if (!granted(Manifest.permission.CALL_PHONE)) return denied()
        val person = when (val resolved = contacts.resolve(target)) {
            is ContactResolver.Resolved.Error -> return resolved.result
            is ContactResolver.Resolved.Contact -> resolved
        }
        context.startActivity(
            Intent(Intent.ACTION_CALL, Uri.parse("tel:+${person.number}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return if (waitFor { isInCall }) {
            ActionResult.ok("Calling ${person.name}.", selfVerified = true)
        } else {
            ActionResult.ok("Started a call to ${person.name}. Check the screen.")
        }
    }

    suspend fun answer(): ActionResult {
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) return denied()
        if (!isRinging) return ActionResult.fail("NOT_RINGING", "No call is ringing.")
        @Suppress("DEPRECATION")
        telecom.acceptRingingCall()
        return if (waitFor { isInCall }) {
            ActionResult.ok("Answered the call.", selfVerified = true)
        } else {
            ActionResult.fail("ANSWER_FAILED", "Android did not answer. Tap or swipe the Answer button on screen.")
        }
    }

    fun end(): ActionResult {
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) return denied()
        @Suppress("DEPRECATION")
        val ended = telecom.endCall()
        return if (ended) {
            ActionResult.ok("Ended the call.", selfVerified = true)
        } else {
            ActionResult.fail("END_FAILED", "Android did not end it. Tap Decline or End call on screen.")
        }
    }

    fun showCallScreen(): ActionResult {
        if (!granted(Manifest.permission.READ_PHONE_STATE)) return denied()
        if (!telecom.isInCall) return ActionResult.fail("NOT_IN_CALL", "No call is active.")
        telecom.showInCallScreen(false)
        return ActionResult.ok("Call screen opened.")
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun denied() = ActionResult.fail("PHONE_PERMISSION", "Phone permission is off. Tell the user to allow it in Ishaara's setup.")

    private suspend fun waitFor(condition: () -> Boolean): Boolean {
        repeat(12) {
            if (condition()) return true
            delay(250)
        }
        return false
    }
}
