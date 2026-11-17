package com.ishaara.mobile.skills

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.service.ActionPerformer
import com.ishaara.mobile.service.ScreenSettler
import kotlinx.coroutines.delay

class MessagingSkill(
    private val service: AccessibilityService,
    private val settler: ScreenSettler,
    private val performer: ActionPerformer,
    private val contacts: ContactResolver,
) {
    private companion object {
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
        const val SEND_POLLS = 6
    }

    suspend fun whatsapp(contact: String, message: String): ActionResult {
        val person = when (val resolved = contacts.resolve(contact)) {
            is ContactResolver.Resolved.Error -> return resolved.result
            is ContactResolver.Resolved.Contact -> resolved
        }
        val url = "https://wa.me/${person.number}?text=${Uri.encode(message)}"
        val pkg = WHATSAPP_PACKAGES.firstOrNull { open(url, it) }
            ?: return ActionResult.fail("WHATSAPP_NOT_INSTALLED", "WhatsApp is not installed.")

        settler.await()
        var send: AccessibilityNodeInfo? = null
        for (i in 1..SEND_POLLS) {
            send = findSend(pkg)
            if (send != null) break
            delay(500)
        }
        send ?: return ActionResult.fail(
            "CHAT_NOT_OPENED",
            "The chat did not show a Send button. ${person.name} may not be on WhatsApp. Look at the screen.",
        )
        performer.tap(send)
        settler.await()

        val entry = service.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("$pkg:id/entry")?.firstOrNull()
        val text = entry?.text?.toString().orEmpty()
        return if (entry != null && (text.isEmpty() || text == entry.hintText?.toString())) {
            ActionResult.ok("Sent \"$message\" to ${person.name} on WhatsApp.", selfVerified = true)
        } else {
            ActionResult.ok("Tapped Send. Check that the message appears in the chat.")
        }
    }

    fun sms(contact: String, message: String): ActionResult {
        val person = when (val resolved = contacts.resolve(contact)) {
            is ContactResolver.Resolved.Error -> return resolved.result
            is ContactResolver.Resolved.Contact -> resolved
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+${person.number}"))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            service.startActivity(intent)
            ActionResult.ok("Message ready to ${person.name}. Now tap Send on screen.")
        } catch (e: ActivityNotFoundException) {
            ActionResult.fail("SMS_NOT_AVAILABLE", "No SMS app was found.")
        }
    }

    private fun open(url: String, pkg: String): Boolean = try {
        service.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun findSend(pkg: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Send").firstOrNull { it.isVisibleToUser && it.isClickable }
    }
}
