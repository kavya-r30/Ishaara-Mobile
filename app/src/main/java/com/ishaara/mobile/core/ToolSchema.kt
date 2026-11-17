package com.ishaara.mobile.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private class Param(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null,
    val schema: JsonObject? = null,
)

private fun tool(name: String, description: String, vararg params: Param): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", name)
        put("description", description)
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                params.forEach { p ->
                    put(p.name, p.schema ?: buildJsonObject {
                        put("type", p.type)
                        put("description", p.description)
                        p.enum?.let { values -> putJsonArray("enum") { values.forEach { add(it) } } }
                    })
                }
            }
            putJsonArray("required") { params.filter { it.required }.forEach { add(it.name) } }
        }
    }
}

private val tasksParam = Param(
    "tasks", "array", "Ordered tasks.",
    schema = buildJsonObject {
        put("type", "array")
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Short task name shown to the user, in the user's language.")
                }
                putJsonObject("success_check") {
                    put("type", "string")
                    put("description", "What must be observably true on the phone when this task is done.")
                }
                putJsonObject("irreversible") {
                    put("type", "boolean")
                    put("description", "True if doing this twice would be harmful: sending, posting, buying, installing, deleting, calling.")
                }
                putJsonObject("fills_form") {
                    put("type", "boolean")
                    put("description", "True if this task fills in a form or sign-up with details the user must give.")
                }
                putJsonObject("price_app") {
                    put("type", "string")
                    put("description", "Only for a task that finds an item's price in one shopping app: that app's name, e.g. Zepto.")
                }
                putJsonObject("price_item") {
                    put("type", "string")
                    put("description", "With price_app: the item to search for, in English letters, e.g. Amul Taaza milk.")
                }
            }
            put("required", JsonArray(listOf("title", "success_check", "irreversible").map { JsonPrimitive(it) }))
        }
    },
)

object ToolSchema {
    val FORM: JsonArray = Json.parseToJsonElement(
        """
        [{"type":"function","function":{"name":"report_form","description":"Report the form on the screen.","parameters":{"type":"object","properties":{
          "no_form":{"type":"boolean","description":"True if no form is on screen."},
          "fields":{"type":"array","description":"Empty fields still to fill, top to bottom.","items":{"type":"object","properties":{
            "label":{"type":"string","description":"The field's name on the form."},
            "question":{"type":"string","description":"One short, friendly question for the user. For a choice, name the choices."},
            "kind":{"type":"string","enum":["text","email","phone","number","choice","multi","dropdown","manual","secret"]},
            "element":{"type":"integer","description":"Element number of the input."},
            "options":{"type":"array","description":"For choice and multi: each option with its own element number.","items":{"type":"object","properties":{"label":{"type":"string"},"element":{"type":"integer"}},"required":["label","element"]}},
            "value":{"type":"string","description":"Only if the user already stated this exact value in their command or answers."}
          },"required":["label","question","kind"]}},
          "more_below":{"type":"boolean","description":"True if the form continues below what's visible."},
          "action_element":{"type":"integer","description":"Element number of the Next, Continue or Submit button, if visible."},
          "final":{"type":"boolean","description":"True if that button submits the form rather than going to the next page."},
          "submit_question":{"type":"string","description":"A short, friendly question asking whether to submit."},
          "done_message":{"type":"string","description":"A short, friendly line to say once the form is finished."}
        },"required":["fields"]}}}]
        """,
    ).jsonArray

    val PRICE: JsonArray = Json.parseToJsonElement(
        """
        [{"type":"function","function":{"name":"report_price","description":"Report what the shopping app's screen shows.","parameters":{"type":"object","properties":{
          "state":{"type":"string","enum":["price","needs_search","suggestions","choose_address","popup","loading","unavailable"],"description":"price: a matching product and its price are visible. needs_search: no results for the item yet. suggestions: the item is typed in the search box and a list of search suggestions shows below it, without prices. choose_address: the app asks which delivery address or location to use. popup: a dialog, banner or permission prompt is in the way. loading: still loading. unavailable: the item isn't sold here, the store is closed or unserviceable, or the app needs a sign-in or a new address."},
          "product":{"type":"string","description":"For price: the product's full name as shown."},
          "size":{"type":"string","description":"For price: pack size, e.g. 500 ml."},
          "price":{"type":"string","description":"For price: the selling price as shown, e.g. ₹30."},
          "exact":{"type":"boolean","description":"For price: true only if the product is the item asked for, including its variant, e.g. Taaza. False for another variant such as Gold or Slim n Trim."},
          "element":{"type":"integer","description":"needs_search: the search box or search button. suggestions: the suggestion that best matches the item. choose_address: the saved address to use. popup: the button that closes it without granting or buying anything."},
          "reason":{"type":"string","description":"For unavailable: a short reason in plain words."}
        },"required":["state"]}}}]
        """,
    ).jsonArray

    val PLANNER: JsonArray = JsonArray(
        listOf(
            tool("answer", "Reply directly when the request needs no action on the phone.", Param("text", "string", "The reply.")),
            tool("create_plan", "Create the task checklist. Use 1 task for a simple command, up to 8 for a complex one.", tasksParam),
        ),
    )

    val VERIFIER: JsonArray = JsonArray(
        listOf(
            tool(
                "report_verification", "Report whether the task's success check holds.",
                Param("verdict", "string", "Result.", enum = listOf("passed", "failed", "uncertain")),
                Param("reason", "string", "One sentence citing what is on the screen."),
            ),
        ),
    )

    val EXECUTOR: JsonArray = JsonArray(
        listOf(
            tool("open_app", "Open an installed app by name.", Param("name", "string", "App name.")),
            tool("open_url", "Open an https, geo, market or mailto link.", Param("url", "string", "The link.")),
            tool(
                "whatsapp_send", "Send a WhatsApp message to a saved contact or phone number and confirm it went.",
                Param("contact", "string", "Contact name or phone number."),
                Param("message", "string", "Exact message text."),
            ),
            tool(
                "send_sms", "Open the SMS app with the message ready. You then tap Send on screen.",
                Param("contact", "string", "Contact name or phone number."),
                Param("message", "string", "Exact message text."),
            ),
            tool("youtube_search", "Open YouTube search results.", Param("query", "string", "Search text.")),
            tool("play_store_search", "Open Play Store search results for apps.", Param("query", "string", "Search text.")),
            tool(
                "media_control", "Control whatever media is playing, in any app. Use this rather than tapping player buttons, which toggle.",
                Param("command", "string", "Command. Use pause or play when the user asks for one of them.", enum = listOf("pause", "play", "play_pause", "next", "previous", "stop")),
            ),
            tool("make_call", "Call a contact or number.", Param("target", "string", "Contact name or phone number.")),
            tool("answer_call", "Answer the ringing call."),
            tool("end_call", "Reject a ringing call or end the current call."),
            tool("show_call_screen", "Bring the in-call screen forward, to reach hold, mute, speaker and keypad."),
            tool(
                "set_alarm", "Set an alarm.",
                Param("hour", "integer", "Hour, 0 to 23."),
                Param("minute", "integer", "Minute, 0 to 59."),
                Param("label", "string", "Optional label.", required = false),
            ),
            tool(
                "set_timer", "Start a timer.",
                Param("seconds", "integer", "Duration in seconds."),
                Param("label", "string", "Optional label.", required = false),
            ),
            tool("open_alarms", "Open the list of alarms. Use this to turn an alarm off: then tap that alarm's switch."),
            tool("set_flashlight","Turn the flashlight on or off.", Param("on", "boolean", "True for on.")),
            tool(
                "open_camera", "Open the camera. You then tap the shutter on screen.",
                Param("video", "boolean", "True for video mode.", required = false),
            ),
            tool(
                "take_photo", "Open the camera and take one photo. Use this for every photo or selfie request; it does the whole job.",
                Param("selfie", "boolean", "True to use the front camera.", required = false),
            ),
            tool(
                "open_settings", "Open a phone settings page.",
                Param(
                    "section", "string", "Page.",
                    enum = listOf("main", "wifi", "bluetooth", "mobile_data", "sound", "display", "battery", "location", "apps"),
                ),
            ),
            tool("close_app", "Close an app: goes to the home screen and stops its background processes.", Param("name", "string", "App name.")),
            tool("get_running_apps", "List recently used apps, newest first."),
            tool("get_media_state", "Find out whether media is playing, and its title, artist and app when available."),
            tool("get_ui_tree", "Get every element on screen with class, ID and bounds. Use only when the normal element list is not enough."),
            tool("read_clipboard", "Read the clipboard text. Use only when the command needs it."),
            tool("set_clipboard", "Put text on the clipboard.", Param("text", "string", "Text to copy.")),
            tool(
                "select_text", "Select text in an input. Selects all of it if no range is given.",
                Param("element", "integer", "Input element number."),
                Param("start", "integer", "Start index.", required = false),
                Param("end", "integer", "End index.", required = false),
            ),
            tool("copy_text", "Copy the selected text, or the element's whole text if nothing is selected.", Param("element", "integer", "Element number.")),
            tool("cut_text", "Cut the selected text from an input.", Param("element", "integer", "Input element number.")),
            tool("paste_text", "Paste the clipboard into an input.", Param("element", "integer", "Input element number.")),
            tool(
                "press_keys", "Press a key or key combo. Only the listed values work.",
                Param(
                    "combo", "string", "Key or combo.",
                    enum = listOf(
                        "ctrl+a", "ctrl+c", "ctrl+x", "ctrl+v", "enter", "volume_up", "volume_down", "mute",
                        "power+volume_down", "dpad_up", "dpad_down", "dpad_left", "dpad_right", "dpad_center", "headset",
                    ),
                ),
            ),
            tool(
                "swipe", "Swipe across the screen or an element, e.g. next reel, next photo, or the Answer button on a lock screen.",
                Param("direction", "string", "Direction.", enum = listOf("up", "down", "left", "right")),
                Param("element", "integer", "Optional element to swipe on.", required = false),
            ),
            tool("double_tap", "Double tap a numbered element, e.g. to like a post or zoom a photo.", Param("element", "integer", "Element number.")),
            tool(
                "drag", "Drag one numbered element onto another.",
                Param("from_element", "integer", "Element to drag."),
                Param("to_element", "integer", "Element to drop on."),
            ),
            tool("read_notifications", "Read the current notifications: app, title and text."),
            tool("get_device_status", "Get battery, charging, network, volume and the current time."),
            tool("list_apps", "List installed apps that can be opened."),
            tool("find_contact", "Look up saved contacts by name.", Param("name", "string", "Name to search.")),
            tool(
                "open_split_screen", "Show two apps side by side.",
                Param("first_app", "string", "App name."),
                Param("second_app", "string", "App name."),
            ),
            tool("exit_split_screen", "Leave split screen."),
            tool(
                "search_in_app", "Search inside a shopping or food app such as Zepto, Blinkit, Instamart, Amazon or Flipkart. Then read the results on screen.",
                Param("app", "string", "App name."),
                Param("query", "string", "What to search for."),
            ),
            tool("web_search", "Search the web in the browser.", Param("query", "string", "Search text.")),
            tool("navigate_to", "Start directions to a place in the maps app.", Param("place", "string", "Place name or address.")),
            tool("set_volume", "Set the media volume.", Param("percent", "integer", "0 to 100.")),
            tool(
                "add_contact", "Open the new-contact form with the details already filled in. You then tap Save on screen. Use this for every new contact.",
                Param("name", "string", "Full name."),
                Param("phone", "string", "Phone number."),
            ),
            tool("tap", "Tap a numbered element from the latest screen.", Param("element", "integer", "Element number.")),
            tool("long_press", "Long press a numbered element.", Param("element", "integer", "Element number.")),
            tool(
                "type_text", "Set the text of an input element.",
                Param("element", "integer", "Input element number."),
                Param("text", "string", "Text to enter."),
                Param("submit", "boolean", "Press Enter afterwards.", required = false),
            ),
            tool(
                "scroll", "Scroll the screen or a given list.",
                Param("direction", "string", "Direction.", enum = listOf("up", "down", "left", "right")),
                Param("element", "integer", "Optional scrollable element number.", required = false),
            ),
            tool(
                "press", "Press a system key.",
                Param(
                    "key", "string", "Key.",
                    enum = listOf("back", "home", "recents", "notifications", "quick_settings", "lock_screen", "screenshot"),
                ),
            ),
            tool("wait", "Wait for loading to finish.", Param("seconds", "integer", "1 to 5.")),
            tool("look_at_screen", "Get a screenshot on the next step, when the element list does not explain the screen."),
            tool(
                "tap_point", "Tap coordinates on the latest screenshot. Use only when no numbered element fits.",
                Param("x", "integer", "X in image pixels."),
                Param("y", "integer", "Y in image pixels."),
            ),
            tool(
                "ask_user", "Ask the user a question and wait for the answer.",
                Param("question", "string", "The question, in the user's language."),
                Param(
                    "options", "array", "Optional short answer choices.", required = false,
                    schema = buildJsonObject {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                    },
                ),
            ),
            tool("update_plan", "Replace the remaining tasks when the plan no longer fits reality.", tasksParam),
            tool(
                "complete_task", "Claim the current task is done. An independent check of the screen follows.",
                Param("summary", "string", "What was achieved, in one sentence, in the user's language."),
            ),
            tool("fail_task", "Give up this attempt at the current task.", Param("reason", "string", "Why.")),
        ),
    )
}
