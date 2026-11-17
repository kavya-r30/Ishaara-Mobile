# Device test results

**Date:** 2026-11-03
**Device:** Headless emulator, `Pixel_10_Pro` AVD, Android 17 (API 37), resized to 720x1600
**Model:** Azure `gpt-5.4-mini`
**Unit tests:** 130 passing

Most commands ran only once or a few times, so the spec's target of 8 passes out of 10 has not been measured yet. Times are from sending the command to the result, and the emulator is slower than a real phone.

## Passed

| Command | Result | Time |
|---|---|---|
| Search Chrome for Everest's height, then text the answer to a number | Found it in Chrome; texted "Mount Everest is 8,848.86 meters tall."; both tasks verified | 34 s |
| Open Chrome and search for the capital of Australia, then tell me | Got past Chrome's first-run screens; answered "Canberra" | 74 s |
| Open Chrome, go to reddit.com, tell me the first post's title | Correct title from the page | 54 s |
| Search YouTube for lofi beats, play the first video, then pause it | Paused and verified | 38 s |
| Send a text message to a number | Sent; verifier saw the message bubble | 19 s |
| Create a contact with a name and number | **Saved: confirmed in Android's contacts database.** The agent then asked for confirmation because the phone had returned to the home screen. | 82 s |
| Turn on dark theme in Settings | Switch verified on | 24 s |
| Open Settings, then Display, then Sound | 3 tasks ticked off | 34–66 s |
| Set an alarm for 6:30 tomorrow morning | One-call tool, reported set. Not left to ring. | 7 s |
| Start a 10 minute timer | One-call tool. The timer did ring. | 11 s |
| What is my battery level | "100%, charging" | 10 s |
| Take a photo | One tap on the shutter. A thumbnail was seen in an earlier run; the saved file was not checked directly. | 17 s |
| Put text on the clipboard, then read it | Correct, through the focus workaround | 20 s |
| Check battery, then put it on the clipboard | Second task used the first task's result | 14 s |
| Close YouTube | `close_app` called | 7 s |
| What apps did I use recently | Listed by name, with Usage access on | 7 s |
| Read my notifications | Read the message correctly, with Notification access on | 7 s |
| What is playing right now | "Nothing is playing." | 7 s |
| Turn on the flashlight | Reported on. Not visually confirmed; emulator has no real torch. | 7 s |
| Settings > Test connection | "Connected. Screenshots are supported. Voice works." | About 10 s |

## Round 2 (2026-11-06, emulator, after real-phone feedback)

| Check | Result |
|---|---|
| Set an alarm, then turn it off | Passed: `open_alarms`, tapped the switch, verified off (26 s) |
| Fill a form by question and answer (httpbin order form) | Name, phone and email asked one at a time and entered. Invented values are blocked in code. **Radio buttons, checkboxes and the time picker are less reliable:** the agent sometimes skips asking about them. |
| What's on my screen | Passed: correct description of the Chrome page |
| Follow-up through the result card's box | Passed: "how many people live there" was answered about Tokyo |
| Assistant shortcut | Passed: with Ishaara as assistant, the assist key opens the bar |
| Sarvam whole-recording speech, `codemix` | 5 of 5 generated English, Hindi and mixed samples correct, about 0.3 s each |
| Hindi reply language | Script detection is unit tested. Spoken end to end on a real phone: **not tested yet** |

## Google account sign-up

The agent worked through Google's real sign-up form: name, birthday dropdowns, gender and username. It then stopped at the password page and asked for it to be entered by hand, as designed. After that **Google required phone verification**, so the test ended there. No account was created. The user then asked to skip this test.

## Known issues

| Issue | Detail |
|---|---|
| **Screen unreadable after a first-run permission prompt** | Android hides permission prompts from accessibility services, so the agent asks the user to answer them. Afterwards, the agent could not read that app's window (Android returned an empty result for it) until Ishaara's service restarted. The cause isn't confirmed, and it may be specific to this emulator. The agent now stops with a clear message instead of asking repeatedly. **Check this on a real phone first.** |
| Secure pages | Pages such as Google's password entry can't be read or tapped. The agent hands them to the user. The question card can be collapsed so it doesn't cover buttons. |
| Take a selfie | This emulator's old camera app hides the front-camera switch in a menu. Retest on a real phone. |
| Calendar event | Reported that it needs a Google sign-in, which is correct with no account on the emulator. Creating an event itself hasn't been tested. |
| Agent claims | The agent sometimes claims success too early. The verifier caught this in testing, but it does cost retries. |
| Emulator stability | The headless emulator ran out of memory and corrupted its graphics at times on this laptop. Results taken during that were thrown out and rerun. |

## Voice

| Check | Result |
|---|---|
| Sarvam speech output, `simran_en_narration` and `simran_hi_narration` on `bulbul:v4-flash` | Accepted, about 1.5 s |
| Sarvam speech input, `saaras:v4`, `translit` mode | Fed generated speech: returned "Send hi to Rahul on WhatsApp." with `en-IN` |
| Microphone through the app | **Not tested:** the headless emulator has no audio |
| Gender and tone of the Simran voices | **Needs your ears:** see `docs/voice-samples/` |

## Round 3 (6 Nov 2026)

Unit tests: 142, all passing.

| Check | Result |
|---|---|
| Fill the pizza form at httpbin.org/forms/post, one question at a time | **Passed twice in a row.** Name, phone, email typed; Medium picked; Extra Cheese and Onion ticked; time handed to the user; "No onions" typed into the unnamed textarea; asked before submitting and left it unsent on "No" |
| Open Gmail | Passed: "Gmail's open now." It no longer lists the apps instead |
| YouTube and Chrome in split screen | Passed |
| Bugs fixed this round | A checkbox tap could hit Chrome's Home button, because element numbers were looked up on a newer screen. An unnamed field was lost after the page scrolled. An answer could be lost when the next question came straight after it. Gestures and actions could hang forever; they now time out |
| Shopping address rule | Zepto, Blinkit, Instamart, Amazon and Flipkart keep the saved default address and never get a new one unless asked. **Not run:** the apps aren't on the emulator |
| Ride address rule | Uber, Ola and Rapido get the destination typed into "Where to", and booking waits for your yes. **Not run:** the apps aren't on the emulator |
| New default voice | Sanchita (`sanchita_hi_assistant`, `sanchita_en_companion`); samples in `docs/voice-samples/` |

## Round 4 (17 Nov 2026, Samsung Galaxy S20 FE)

Unit tests: 159, all passing.

| Check | Result |
|---|---|
| Compare Amul Taaza milk on Zepto, Blinkit and Instamart | Passed. Zepto ₹77 (1 L), Blinkit ₹31 (500 ml), Instamart closest match ₹38 (500 ml); Blinkit named cheapest; saved address kept; nothing added to a cart |
| Show Uber fares to Dadar station | Passed. All four rides read out with fares; nothing booked |
| Open Google and YouTube in split screen | Passed in one step |
| Call during a price comparison | Fixed. A call rang mid-command and the agent answered it. Calls now need the user's own words to ask for them, with a unit test for this exact case |
| Fill the demo pizza form | **Needs another look on this phone.** It asked name, phone, email, size and time, then skipped toppings and delivery instructions and finished without asking to submit. It passes in full on the Pixel emulator |
| Microphone granted "Only this time" | Android takes it back in the background, which hides the command bar. Grant it as "Allow only while using the app" |

## Not tested yet

These need a real phone, a SIM or a signed-in account:

- WhatsApp
- Calls: place, answer, reject, hold, speaker, mute, hang up
- Play Store install (no account on the emulator)
- Instagram and other logged-in social apps (not installed)
- Notes apps (none on the emulator)
- Gallery: open last photo, share a photo
- Swipe and double-tap gestures in real apps
- Payment-app refusal (covered by unit tests only)
- Fresh install through the Setup screen, including Android 13+ restricted settings
- Battery and background killing on Xiaomi, Oppo and Vivo phones
