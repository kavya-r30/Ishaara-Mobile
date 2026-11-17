package com.ishaara.mobile.service

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.ishaara.mobile.core.Device
import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.DeviceKey
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.Screenshot
import com.ishaara.mobile.skills.AppLauncher
import com.ishaara.mobile.skills.AppStateSkill
import com.ishaara.mobile.skills.CameraSkill
import com.ishaara.mobile.skills.ClipboardSkill
import com.ishaara.mobile.skills.ContactResolver
import com.ishaara.mobile.skills.KeySkill
import com.ishaara.mobile.skills.MediaSkill
import com.ishaara.mobile.skills.MessagingSkill
import com.ishaara.mobile.skills.MultitaskSkill
import com.ishaara.mobile.skills.PhoneSkill
import com.ishaara.mobile.skills.SystemSkill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidDevice(
    private val service: IshaaraService,
    private val overlay: OverlayControl,
) : Device {
    private companion object {
        const val MAX_RESULT = 2000
        const val ACTION_TIMEOUT_MS = 30000L
        const val LAUNCH_CHANGE_MS = 4000L
        const val TAP_CHANGE_MS = 1000L
        const val EMPTY_READ_ATTEMPTS = 10
        const val EMPTY_READ_DELAY_MS = 500L
    }

    private val settler = service.settler
    private val reader = NodeTreeReader(service.packageName)
    private val performer = ActionPerformer(service, overlay)
    private val screenshots = ScreenshotTaker(service, overlay)
    private val apps = AppLauncher(service)
    private val contacts = ContactResolver(service)
    private val messaging = MessagingSkill(service, settler, performer, contacts)
    private val media = MediaSkill(service)
    private val phone = PhoneSkill(service, contacts)
    private val system = SystemSkill(service)
    private val camera = CameraSkill(service, settler, performer)
    private val multitask = MultitaskSkill(service, apps, performer)
    private val appState = AppStateSkill(service, apps)
    private val clipboard = ClipboardSkill(service)
    private val keys = KeySkill(service, clipboard)

    private var live = LiveScreen.EMPTY
    private var lastScreenshotScale = 1.0
    private var leavingPackage: String? = null

    override suspend fun observe(): ScreenSnapshot {
        settler.await()
        val metrics = service.resources.displayMetrics
        val leaving = leavingPackage
        leavingPackage = null
        // During a transition there can be no readable windows for a moment. After a launch, the old app can
        // also still be on top, and acting on that stale screen would tap the wrong thing, so wait it out.
        for (attempt in 1..EMPTY_READ_ATTEMPTS) {
            // Reading the tree is many cross-process calls; off the main thread it can't stutter the overlay.
            live = withContext(Dispatchers.Default) {
                reader.read(service.windows, service.rootInActiveWindow, Rect(0, 0, metrics.widthPixels, metrics.heightPixels), phone.isRinging)
            }
            val pkg = live.snapshot.packageName
            if (live.snapshot.elements.isNotEmpty() && pkg != "unknown" && pkg != leaving) break
            if (live.snapshot.elements.isEmpty() && attempt == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                service.clearCache()
            }
            delay(EMPTY_READ_DELAY_MS)
        }
        return live.snapshot
    }

    override suspend fun screenshot(snapshot: ScreenSnapshot): Screenshot? {
        val shot = screenshots.capture(snapshot) ?: return null
        lastScreenshotScale = shot.scale
        return shot
    }

    override suspend fun execute(action: AgentAction): ActionResult {
        val result = try {
            // A hung app or system call must never freeze the whole task.
            withTimeoutOrNull(ACTION_TIMEOUT_MS) { perform(action) }
                ?: ActionResult.fail("ACTION_TIMEOUT", "That took too long. Look at the screen and try another way.")
        } catch (e: TimeoutCancellationException) {
            ActionResult.fail("ACTION_TIMEOUT", "That took too long. Look at the screen and try another way.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Other apps and system services can throw anything; the agent can try another way.
            ActionResult.fail("ACTION_ERROR", "That didn't work: ${e.message}")
        }
        settler.actionPerformed(expectedChangeMs(action))
        if (result.ok && expectedChangeMs(action) == LAUNCH_CHANGE_MS) leavingPackage = live.snapshot.packageName
        return result.copy(message = result.message.take(MAX_RESULT))
    }

    private fun expectedChangeMs(action: AgentAction): Long = when (action) {
        is AgentAction.OpenApp, is AgentAction.OpenUrl, is AgentAction.SendSms, is AgentAction.YouTubeSearch,
        is AgentAction.PlayStoreSearch, is AgentAction.MakeCall, is AgentAction.OpenCamera, is AgentAction.OpenSettings,
        is AgentAction.AddContact, AgentAction.OpenAlarms, is AgentAction.SearchInApp,
        is AgentAction.WebSearch, is AgentAction.Navigate,
        AgentAction.ShowCallScreen -> LAUNCH_CHANGE_MS

        is AgentAction.Tap, is AgentAction.LongPress, is AgentAction.DoubleTap, is AgentAction.TapPoint,
        is AgentAction.TypeText, is AgentAction.Scroll, is AgentAction.Swipe, is AgentAction.Drag,
        is AgentAction.Press -> TAP_CHANGE_MS

        else -> 0
    }

    private suspend fun perform(action: AgentAction): ActionResult = when (action) {
        is AgentAction.Tap -> withNode(action.element) { performer.tap(it) }
        is AgentAction.LongPress -> withNode(action.element) { performer.longPress(it) }
        is AgentAction.DoubleTap -> withNode(action.element) { performer.doubleTap(it) }
        is AgentAction.TypeText -> withNode(action.element) { performer.type(it, action.text, action.submit) }
        is AgentAction.Scroll -> withOptionalNode(action.element) { performer.scroll(action.direction, it, live.nodes.values) }
        is AgentAction.Swipe -> withOptionalNode(action.element) { performer.swipe(action.direction, it) }
        is AgentAction.Drag -> withNode(action.fromElement) { from -> withNode(action.toElement) { to -> performer.drag(from, to) } }
        is AgentAction.Press -> performer.press(action.key)
        is AgentAction.Wait -> {
            delay(action.seconds * 1000L)
            ActionResult.ok("Waited.")
        }
        is AgentAction.TapPoint -> performer.tapAt(
            (action.x * lastScreenshotScale).toInt(),
            (action.y * lastScreenshotScale).toInt(),
        )

        is AgentAction.OpenApp -> apps.open(action.name)
        is AgentAction.OpenUrl -> system.openUrl(action.url)
        is AgentAction.WhatsAppSend -> messaging.whatsapp(action.contact, action.message)
        is AgentAction.SendSms -> messaging.sms(action.contact, action.message)
        is AgentAction.YouTubeSearch -> media.youtubeSearch(action.query)
        is AgentAction.PlayStoreSearch -> media.playStoreSearch(action.query)
        is AgentAction.Media -> media.control(action.command)
        is AgentAction.MakeCall -> phone.call(action.target)
        AgentAction.AnswerCall -> phone.answer()
        AgentAction.EndCall -> phone.end()
        AgentAction.ShowCallScreen -> phone.showCallScreen()
        is AgentAction.SetAlarm -> system.setAlarm(action.hour, action.minute, action.label)
        is AgentAction.SetTimer -> system.setTimer(action.seconds, action.label)
        AgentAction.OpenAlarms -> system.openAlarms()
        is AgentAction.SetFlashlight -> system.setFlashlight(action.on)
        is AgentAction.OpenCamera -> system.openCamera(action.video)
        is AgentAction.TakePhoto -> camera.takePhoto(action.selfie)
        is AgentAction.OpenSettings -> system.openSettings(action.section)

        is AgentAction.CloseApp -> {
            performer.press(DeviceKey.HOME)
            settler.await()
            appState.stopBackground(action.name)
        }
        AgentAction.GetRunningApps -> appState.recentApps()
        AgentAction.GetMediaState -> appState.mediaState()
        AgentAction.ReadNotifications -> appState.notifications()
        AgentAction.GetDeviceStatus -> appState.deviceStatus()
        AgentAction.ListApps -> apps.list()
        is AgentAction.FindContact -> contacts.find(action.name)
        is AgentAction.AddContact -> system.addContact(action.name, action.phone)
        is AgentAction.SplitScreen -> multitask.splitScreen(action.first, action.second)
        AgentAction.ExitSplitScreen -> multitask.exitSplitScreen()
        is AgentAction.SearchInApp -> multitask.searchInApp(action.app, action.query)
        is AgentAction.WebSearch -> system.webSearch(action.query)
        is AgentAction.Navigate -> system.navigate(action.place)
        is AgentAction.SetVolume -> system.setVolume(action.percent)
        AgentAction.GetUiTree -> ActionResult.ok(reader.dump(service.windows, phone.isRinging))
        AgentAction.ReadClipboard -> clipboard.read()
        is AgentAction.SetClipboard -> clipboard.set(action.text)
        is AgentAction.SelectText -> withNode(action.element) { clipboard.select(it, action.start, action.end) }
        is AgentAction.CopyText -> withNode(action.element) { clipboard.copy(it) }
        is AgentAction.CutText -> withNode(action.element) { clipboard.cut(it) }
        is AgentAction.PasteText -> withNode(action.element) { clipboard.paste(it) }
        is AgentAction.PressKeys -> keys.press(action.combo)

        AgentAction.LookAtScreen,
        is AgentAction.AskUser,
        is AgentAction.UpdatePlan,
        is AgentAction.CompleteTask,
        is AgentAction.FailTask -> ActionResult.fail("INTERNAL", "This is not a phone action.")
    }

    private suspend fun withNode(id: Int, block: suspend (AccessibilityNodeInfo) -> ActionResult): ActionResult {
        val node = live.nodes[id]
            ?: return ActionResult.fail("ELEMENT_NOT_FOUND", "No element $id on the latest screen.")
        if (!node.refresh()) return ActionResult.fail("STALE_ELEMENT", "Element $id is gone. Look at the new screen.")
        return block(node)
    }

    private suspend fun withOptionalNode(id: Int?, block: suspend (AccessibilityNodeInfo?) -> ActionResult): ActionResult =
        if (id == null) block(null) else withNode(id) { block(it) }
}
