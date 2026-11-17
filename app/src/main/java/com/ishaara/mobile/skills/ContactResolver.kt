package com.ishaara.mobile.skills

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.TelephonyManager
import com.ishaara.mobile.core.NameMatch
import com.ishaara.mobile.core.NameMatcher
import com.ishaara.mobile.core.model.ActionResult

class ContactResolver(private val context: Context) {
    sealed interface Resolved {
        /** [number] has country code and no plus sign. */
        data class Contact(val name: String, val number: String) : Resolved
        data class Error(val result: ActionResult) : Resolved
    }

    fun resolve(target: String): Resolved {
        if (PhoneNumbers.looksLikeNumber(target)) {
            return Resolved.Contact(target, PhoneNumbers.international(target, country()))
        }
        val contacts = all() ?: return Resolved.Error(noPermission())
        return when (val match = NameMatcher.match(target, contacts.keys.toList())) {
            is NameMatch.One -> Resolved.Contact(
                match.name,
                PhoneNumbers.international(contacts.getValue(match.name), country()),
            )
            is NameMatch.Many -> Resolved.Error(
                ActionResult.fail("AMBIGUOUS_CONTACT", "Several contacts match: ${match.names.joinToString()}. Ask the user which one."),
            )
            NameMatch.None -> Resolved.Error(ActionResult.fail("CONTACT_NOT_FOUND", "No saved contact is called $target."))
        }
    }

    fun find(name: String): ActionResult {
        val contacts = all() ?: return noPermission()
        return when (val match = NameMatcher.match(name, contacts.keys.toList())) {
            is NameMatch.One -> ActionResult.ok("${match.name}: ${contacts.getValue(match.name)}")
            is NameMatch.Many -> ActionResult.ok(match.names.joinToString("\n") { "$it: ${contacts.getValue(it)}" })
            NameMatch.None -> ActionResult.fail("CONTACT_NOT_FOUND", "No saved contact is called $name.")
        }
    }

    private fun noPermission() = ActionResult.fail("CONTACTS_PERMISSION", "Contacts permission is off. Tell the user to allow it in Ishaara's setup.")

    private fun all(): Map<String, String>? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val result = linkedMapOf<String, String>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                result.putIfAbsent(name, number)
            }
        }
        return result
    }

    private fun country(): String =
        context.getSystemService(TelephonyManager::class.java)?.simCountryIso.orEmpty()
}
