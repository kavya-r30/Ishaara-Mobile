package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.PlanDecision
import com.ishaara.mobile.core.model.PriceLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCheckerTest {
    private fun report(json: String) = call("report_price", json)

    private fun checker(llm: FakeLlm, device: FakeDevice, app: String = "Zepto") =
        PriceChecker(llm, device, PriceLookup(app, "Amul Taaza milk"), visionEnabled = true) {}

    @Test
    fun opensTheAppAndReadsThePrice() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val llm = FakeLlm(report("""{"state":"price","product":"Amul Taaza Toned Milk","size":"500 ml","price":"₹30"}"""))
        val found = checker(llm, device).run()
        assertEquals(AgentAction.SearchInApp("Zepto", "Amul Taaza milk"), device.executed.first())
        assertEquals("Zepto: Amul Taaza Toned Milk, 500 ml, ₹30", found)
    }

    @Test
    fun anotherAppsScreenIsNeverReadAsThisAppsPrice() = runTest {
        // Blinkit stayed on screen when Instamart was asked for; its price was once reported as Instamart's.
        val device = FakeDevice(labelledScreen("com.grofers.customerapp"))
        val found = checker(FakeLlm(), device, app = "Instamart").run()
        assertTrue(found.startsWith("Instamart: it didn't stay open"))
        assertEquals(2, device.executed.count { it is AgentAction.SearchInApp })
    }

    @Test
    fun theSavedAddressIsPickedThenThePriceRead() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val llm = FakeLlm(
            report("""{"state":"choose_address","element":2}"""),
            report("""{"state":"price","product":"Amul Taaza","price":"₹30"}"""),
        )
        val found = checker(llm, device).run()
        assertTrue(AgentAction.Tap(2) in device.executed)
        assertEquals("Zepto: Amul Taaza, ₹30", found)
    }

    @Test
    fun anAddressThatIsntOnScreenIsNeverTapped() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val found = checker(FakeLlm(report("""{"state":"choose_address","element":99}""")), device).run()
        assertTrue(device.executed.none { it is AgentAction.Tap })
        assertTrue(found.contains("no saved one"))
    }

    @Test
    fun anotherVariantIsReportedAsTheClosestNotAsTheItem() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val llm = FakeLlm(report("""{"state":"price","product":"Amul Slim n Trim Milk","size":"500 ml","price":"₹26","exact":false}"""))
        assertEquals(
            "Zepto: no exact match for Amul Taaza milk; the closest is Amul Slim n Trim Milk, 500 ml, ₹26",
            checker(llm, device).run(),
        )
    }

    @Test
    fun aSuggestionIsTappedWhenTheSearchKeyDoesNothing() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val llm = FakeLlm(
            report("""{"state":"suggestions","element":1}"""),
            report("""{"state":"price","product":"Amul Taaza","price":"₹30","exact":true}"""),
        )
        assertEquals("Zepto: Amul Taaza, ₹30", checker(llm, device).run())
        assertTrue(AgentAction.Tap(1) in device.executed)
    }

    @Test
    fun anUnavailableItemSaysWhy() = runTest {
        val device = FakeDevice(labelledScreen("com.zeptoconsumerapp"))
        val found = checker(FakeLlm(report("""{"state":"unavailable","reason":"the store is closed"}""")), device).run()
        assertEquals("Zepto: the store is closed.", found)
    }

    @Test
    fun theStandaloneInstamartAppCounts() {
        assertTrue("in.swiggy.android.instamart" in ShopApps.packagesFor("Instamart")!!)
    }

    @Test
    fun plannerTasksCarryTheAppAndItem() {
        val plan = ToolParser.parsePlan(
            call(
                "create_plan",
                """{"tasks":[{"title":"Check Zepto","success_check":"price known","irreversible":false,"price_app":"Zepto","price_item":"Amul milk"},{"title":"Compare prices","success_check":"compared","irreversible":false}]}""",
            ),
        ) as PlanDecision.Plan
        assertEquals(PriceLookup("Zepto", "Amul milk"), plan.tasks[0].price)
        assertEquals(null, plan.tasks[1].price)
    }
}
