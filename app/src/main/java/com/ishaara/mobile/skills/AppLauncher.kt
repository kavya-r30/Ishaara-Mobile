package com.ishaara.mobile.skills

import android.content.Context
import android.content.Intent
import com.ishaara.mobile.core.NameMatch
import com.ishaara.mobile.core.NameMatcher
import com.ishaara.mobile.core.model.ActionResult

class AppLauncher(private val context: Context) {
    sealed interface Resolved {
        data class App(val label: String, val packageName: String) : Resolved
        data class Error(val result: ActionResult) : Resolved
    }

    private fun installed(): Map<String, String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(launcher, 0).associate {
            it.loadLabel(pm).toString() to it.activityInfo.packageName
        }
    }

    fun resolve(name: String): Resolved {
        val apps = installed()
        return when (val match = NameMatcher.match(name, apps.keys.toList())) {
            is NameMatch.One -> Resolved.App(match.name, apps.getValue(match.name))
            is NameMatch.Many -> Resolved.Error(
                ActionResult.fail("AMBIGUOUS_APP", "Several apps match: ${match.names.joinToString()}. Choose one or ask the user."),
            )
            NameMatch.None -> Resolved.Error(ActionResult.fail("APP_NOT_FOUND", "No installed app is called $name."))
        }
    }

    fun open(name: String): ActionResult = when (val resolved = resolve(name)) {
        is Resolved.Error -> resolved.result
        is Resolved.App -> {
            val intent = context.packageManager.getLaunchIntentForPackage(resolved.packageName)
            if (intent == null) {
                ActionResult.fail("APP_NOT_FOUND", "${resolved.label} can't be opened.")
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult.ok("Opened ${resolved.label}.")
            }
        }
    }

    fun list(): ActionResult = ActionResult.ok(installed().keys.sorted().joinToString())

    fun labelOf(packageName: String): String =
        installed().entries.firstOrNull { it.value == packageName }?.key ?: packageName
}
