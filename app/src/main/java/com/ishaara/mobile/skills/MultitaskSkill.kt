package com.ishaara.mobile.skills

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.service.ActionPerformer
import kotlinx.coroutines.delay

class MultitaskSkill(
    private val service: AccessibilityService,
    private val apps: AppLauncher,
    private val performer: ActionPerformer,
) {
    private companion object {
        const val STEP_MS = 1200L
        const val LAUNCH_MS = 2000L
        const val SPLIT_CHECKS = 10
        const val SPLIT_CHECK_MS = 500L

        /** Packages are tried in order: Instamart is its own app on some phones and part of Swiggy on others. */
        private class Shop(val names: List<String>, val packageNames: List<String>, val url: String)

        val SHOPS = listOf(
            Shop(listOf("zepto"), listOf("com.zeptoconsumerapp"), "https://www.zeptonow.com/search?query="),
            Shop(listOf("blinkit", "grofers"), listOf("com.grofers.customerapp"), "https://blinkit.com/s/?q="),
            Shop(
                listOf("instamart", "swiggy"), listOf("in.swiggy.android.instamart", "in.swiggy.android"),
                "https://www.swiggy.com/instamart/search?query=",
            ),
            Shop(listOf("amazon"), listOf("in.amazon.mShop.android.shopping"), "https://www.amazon.in/s?k="),
            Shop(listOf("flipkart"), listOf("com.flipkart.android"), "https://www.flipkart.com/search?q="),
        )

        /** Split windows each cover part of the screen; anything bigger is a full-screen app. */
        const val MAX_SPLIT_SHARE = 0.8
    }

    suspend fun splitScreen(first: String, second: String): ActionResult {
        val a = when (val resolved = apps.resolve(first)) {
            is AppLauncher.Resolved.Error -> return resolved.result
            is AppLauncher.Resolved.App -> resolved
        }
        val b = when (val resolved = apps.resolve(second)) {
            is AppLauncher.Resolved.Error -> return resolved.result
            is AppLauncher.Resolved.App -> resolved
        }
        if (isSplit(a.packageName, b.packageName)) {
            return ActionResult.ok("${a.label} and ${b.label} are already side by side.", selfVerified = true)
        }
        val pm = service.packageManager
        val firstIntent = pm.getLaunchIntentForPackage(a.packageName)
            ?: return ActionResult.fail("APP_NOT_FOUND", "${a.label} can't be opened.")
        val secondIntent = pm.getLaunchIntentForPackage(b.packageName)
            ?: return ActionResult.fail("APP_NOT_FOUND", "${b.label} can't be opened.")

        // The second app opens first, so it's in recents for phones whose picker shows only those.
        service.startActivity(Intent(secondIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(LAUNCH_MS)
        service.startActivity(Intent(firstIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(LAUNCH_MS)

        // The direct shortcut does nothing on many phones, so this follows the steps a person would:
        // in recents, the app's icon opens a menu with "Split screen" (Pixel) or
        // "Open in split screen view" (Samsung). The icon is the smallest match; the card is large
        // and tapping it would just reopen the app.
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        delay(STEP_MS)
        val icon = smallest(a.label)
        if (icon != null) {
            performer.tap(icon)
            delay(STEP_MS)
            smallest("split")?.let {
                performer.tap(it)
                delay(STEP_MS)
                // Next the phone asks for the second app: Pixel shows recents cards, Samsung an app picker.
                // The largest match is the card or picker item; a small one may be a title that opens a menu.
                largest(b.label)?.let { choice ->
                    performer.tap(choice)
                    delay(STEP_MS)
                }
                // Samsung's picker may not list the app. With one app placed, Android puts an app
                // launched "adjacent" into the other half.
                if (!isSplit(a.packageName, b.packageName)) {
                    service.startActivity(
                        Intent(secondIntent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT),
                    )
                    delay(STEP_MS)
                }
            }
        }
        // Android takes a moment to settle the two apps into place.
        var split = false
        for (check in 1..SPLIT_CHECKS) {
            split = isSplit(a.packageName, b.packageName)
            if (split) break
            delay(SPLIT_CHECK_MS)
        }
        return if (split) {
            ActionResult.ok("${a.label} and ${b.label} are side by side.", selfVerified = true)
        } else {
            ActionResult.fail(
                "SPLIT_NOT_CONFIRMED",
                "Split screen didn't start. Look at the screen: in recents, the app's icon opens a menu with a split screen option. If that fails too, call fail_task and say this phone didn't allow it.",
            )
        }
    }

    /** Visible elements whose text or description contains [text], outside Ishaara, with their areas. */
    private fun matches(text: String): Sequence<Pair<AccessibilityNodeInfo, Long>> =
        service.windows.asSequence()
            .mapNotNull { it.root }
            .filter { it.packageName?.toString() != service.packageName }
            .flatMap { it.findAccessibilityNodeInfosByText(text).asSequence() }
            .filter { it.isVisibleToUser }
            .map { it to Rect().also(it::getBoundsInScreen) }
            .filter { (_, r) -> !r.isEmpty }
            .map { (node, r) -> node to r.width().toLong() * r.height() }

    private fun smallest(text: String): AccessibilityNodeInfo? = matches(text).minByOrNull { it.second }?.first

    private fun largest(text: String): AccessibilityNodeInfo? = matches(text).maxByOrNull { it.second }?.first

    fun exitSplitScreen(): ActionResult {
        if (splitPackages().size < 2) return ActionResult.ok("Split screen isn't on.", selfVerified = true)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        return ActionResult.ok("Closed split screen.")
    }

    fun searchInApp(app: String, query: String): ActionResult {
        val name = app.lowercase()
        val shop = SHOPS.firstOrNull { s -> s.names.any { it in name } }
        if (shop != null) {
            for (pkg in shop.packageNames) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shop.url + Uri.encode(query)))
                    .setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    service.startActivity(intent)
                    return ActionResult.ok("Opened $app. If results for \"$query\" aren't showing, use its search box.")
                } catch (e: ActivityNotFoundException) {
                    // This package doesn't take search links or isn't installed. Plain launch below.
                }
            }
            for (pkg in shop.packageNames) {
                val launch = service.packageManager.getLaunchIntentForPackage(pkg) ?: continue
                service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return ActionResult.ok("Opened $app. Now use its search box for \"$query\".")
            }
        }
        val opened = apps.open(app)
        return if (opened.ok) ActionResult.ok("Opened $app. Now use its search box for \"$query\".") else opened
    }

    /**
     * Apps shown in part of the screen each. A plain count of app windows isn't enough: Samsung's
     * Edge panel and app picker are app windows too, which once made a single full-screen app pass.
     */
    private fun splitPackages(): Set<String> {
        val metrics = service.resources.displayMetrics
        val screenArea = metrics.widthPixels.toLong() * metrics.heightPixels
        return service.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .filter { w -> Rect().also(w::getBoundsInScreen).let { it.width().toLong() * it.height() < screenArea * MAX_SPLIT_SHARE } }
            .mapNotNull { it.root?.packageName?.toString() }
            .filter { it != service.packageName }
            .toSet()
    }

    private fun isSplit(first: String, second: String): Boolean = splitPackages().containsAll(listOf(first, second))
}
