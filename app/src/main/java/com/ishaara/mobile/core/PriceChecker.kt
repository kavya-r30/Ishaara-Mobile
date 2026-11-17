package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.PriceLookup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What one look at a shopping app's screen found. */
data class PriceReport(
    val state: String,
    val product: String?,
    val size: String?,
    val price: String?,
    val exact: Boolean,
    val element: Int?,
    val reason: String?,
)

/**
 * Finds an item's price in one shopping app. Code opens the app and makes sure it is the one on screen,
 * because a model left to do this reported one app's price as another's and skipped apps altogether.
 * The model only reads each screen.
 */
class PriceChecker(
    private val llm: LlmClient,
    private val device: Device,
    private val lookup: PriceLookup,
    private val visionEnabled: Boolean,
    private val onStep: suspend (String) -> Unit,
) {
    suspend fun run(): String {
        val app = lookup.app
        onStep("Searching $app for ${lookup.item}")
        val opened = device.execute(AgentAction.SearchInApp(app, lookup.item))
        if (!opened.ok) return "$app: couldn't be opened on this phone."
        // Shopping apps draw their home screen, banners and search box in stages after opening.
        device.execute(AgentAction.Wait(OPEN_WAIT_S))

        val expected = ShopApps.packagesFor(app)
        var reopened = false
        var searches = 0
        var picks = 0
        var boxless = 0
        repeat(MAX_ROUNDS) {
            val screen = device.observe()
            if (expected != null && screen.packageName !in expected) {
                // Another app or the home screen came up instead; try once more before giving up.
                if (reopened) return "$app: it didn't stay open, so I couldn't check."
                reopened = true
                device.execute(AgentAction.SearchInApp(app, lookup.item))
                device.execute(AgentAction.Wait(OPEN_WAIT_S))
                return@repeat
            }
            val shot = if (visionEnabled) device.screenshot(screen) else null
            val text = "App: $app\nItem: ${lookup.item}\n\nCurrent screen:\n${ScreenFormatter.format(screen)}"
            val report = parse(
                llm.complete(listOf(ChatMessage.system(Prompts.PRICE_READER), ChatMessage.user(text, shot?.jpegBase64)), ToolSchema.PRICE),
            )
            val element = report.element?.takeIf { id -> screen.elements.any { it.id == id } }
            when (report.state) {
                "price" -> {
                    val price = report.price?.takeIf { it.any(Char::isDigit) }
                    if (price != null) {
                        val details = listOfNotNull(report.product ?: lookup.item, report.size, price).joinToString(", ")
                        return if (report.exact) "$app: $details" else "$app: no exact match for ${lookup.item}; the closest is $details"
                    }
                }
                "suggestions" -> {
                    // Some apps ignore the keyboard's search key and only list suggestions; tap one as a person would.
                    if (element == null || picks >= MAX_SEARCHES) return "$app: I couldn't get search results for ${lookup.item}."
                    picks++
                    onStep("Picking the ${lookup.item} suggestion in $app")
                    device.execute(AgentAction.Tap(element))
                }
                "needs_search" -> {
                    if (element == null) {
                        // Usually the app is still drawing its home screen, so the search box isn't there yet.
                        if (++boxless > MAX_SEARCHES) return "$app: I couldn't find its search box."
                    } else {
                        if (searches >= MAX_SEARCHES) return "$app: I couldn't search for ${lookup.item} there."
                        searches++
                        onStep("Searching $app for ${lookup.item}")
                        val editable = screen.elements.first { it.id == element }.editable
                        device.execute(if (editable) AgentAction.TypeText(element, lookup.item, true) else AgentAction.Tap(element))
                    }
                }
                "choose_address" -> {
                    if (element == null) return "$app: it wants a delivery address, and there's no saved one to pick."
                    onStep("Using your saved address in $app")
                    device.execute(AgentAction.Tap(element))
                }
                "popup" -> {
                    onStep("Closing a pop-up in $app")
                    device.execute(if (element != null) AgentAction.Tap(element) else AgentAction.Press(DeviceKey.BACK))
                }
                "unavailable" -> return "$app: ${report.reason ?: "the item isn't available there"}."
                else -> Unit
            }
            // Results, addresses and closed pop-ups load from the network, so give the screen time to settle.
            device.execute(AgentAction.Wait(LOADING_WAIT_S))
        }
        return "$app: I couldn't read a price there."
    }

    companion object {
        private const val MAX_ROUNDS = 10
        private const val MAX_SEARCHES = 2
        private const val LOADING_WAIT_S = 2
        private const val OPEN_WAIT_S = 3

        fun parse(call: ToolCall): PriceReport {
            if (call.name != "report_price") throw ToolParseException("Unknown price tool: ${call.name}")
            val json = try {
                Json.parseToJsonElement(call.argumentsJson).jsonObject
            } catch (e: IllegalArgumentException) {
                throw ToolParseException("Price report isn't valid JSON")
            }
            fun text(name: String) = json[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            return PriceReport(
                state = text("state") ?: "loading",
                product = text("product"),
                size = text("size"),
                price = text("price"),
                // Missing means the model didn't say it wasn't exact, as for older replies.
                exact = json["exact"]?.jsonPrimitive?.booleanOrNull ?: true,
                element = json["element"]?.jsonPrimitive?.intOrNull,
                reason = text("reason"),
            )
        }
    }
}

/** Package names of shopping apps, so the checker knows the right app is on screen. */
object ShopApps {
    private val apps = listOf(
        listOf("instamart") to setOf("in.swiggy.android.instamart", "in.swiggy.android"),
        listOf("swiggy") to setOf("in.swiggy.android", "in.swiggy.android.instamart"),
        listOf("zepto") to setOf("com.zeptoconsumerapp"),
        listOf("blinkit", "grofers") to setOf("com.grofers.customerapp"),
        listOf("bigbasket", "big basket") to setOf("com.bigbasket.mobileapp"),
        listOf("jiomart", "jio mart") to setOf("com.jpl.jiomart"),
        listOf("amazon") to setOf("in.amazon.mShop.android.shopping"),
        listOf("flipkart") to setOf("com.flipkart.android"),
    )

    fun packagesFor(app: String): Set<String>? {
        val name = app.lowercase()
        return apps.firstOrNull { (names, _) -> names.any { it in name } }?.second
    }
}
