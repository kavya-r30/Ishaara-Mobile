package com.ishaara.mobile.core

/** Short, app-specific tips added to the prompt when that app is on screen. */
object AppHints {
    private const val CAMERA =
        "The shutter is usually labelled Shutter, Take photo, Capture or Camera. If nothing fits, use look_at_screen, then tap_point on the large round button at the bottom centre. To take a selfie, tap the button labelled Switch camera, Flip or Front camera first. Tap the shutter exactly once for one photo, then call complete_task. The photo is taken when a small round thumbnail of it appears near the shutter; the rest of the screen barely changes, so don't tap again, and don't open the thumbnail unless asked."
    private const val CALL =
        "Call buttons are labelled Hold, Mute, Speaker, Keypad and End call, and may be behind a More or three-dot button. A ringing call shows Answer and Decline, which may need a swipe gesture on the lock screen; try answer_call and end_call first."
    private const val GALLERY =
        "The newest photo is the first item in the grid. Tap it to open it. Share is labelled Share."

    private const val CLOCK =
        "Each alarm row has a switch that reads on or off, with the alarm's time in its label. Tap the switch for the right time. A ringing alarm shows Dismiss or Stop, which may need a swipe."

    private const val SHOP =
        "Search results show each product's name, pack size and price in rupees. Use the first result that really matches, and note its pack size. Skip items labelled Ad or Sponsored. Never add to cart or pay. " +
            "Keep the delivery address the app already shows. Never type, add or edit an address unless the user's command gave one. If the app asks you to choose an address, tap the saved one marked Default, Home or Current, or else the first saved one. If there is no saved address or it needs a sign-in, say so and stop."

    private const val RIDE =
        "Tap the Where to or Search destination box and type the destination the user gave, then tap the suggestion that matches it. Pickup stays as the current location unless the user named another; then tap the pickup box and type it the same way. " +
            "If the user didn't say where to, ask once. Once ride options appear, read out each option with its fare and time. Never tap Request, Book, Confirm or Choose ride until the user says yes to the exact ride and fare."

    private val hints = mapOf(
        "com.zeptoconsumerapp" to SHOP,
        "com.grofers.customerapp" to SHOP,
        "in.swiggy.android" to SHOP,
        "in.swiggy.android.instamart" to SHOP,
        "in.amazon.mShop.android.shopping" to SHOP,
        "com.flipkart.android" to SHOP,
        "com.ubercab" to RIDE,
        "com.olacabs.customer" to RIDE,
        "com.rapido.passenger" to RIDE,
        "com.sec.android.app.clockpackage" to CLOCK,
        "com.google.android.deskclock" to CLOCK,
        "com.android.vending" to "To install: open the app's page, tap Install, then wait until the button reads Open or Uninstall; this can take over a minute, so use wait. Never tap a button showing a price. Do not tap Sponsored results.",
        "com.google.android.youtube" to "Video results show title, channel and views. Skip items labelled Ad, Sponsored or Shorts. A video is playing once a player with pause controls and a title appears.",
        "com.instagram.android" to "Bottom tabs are Home, Search and explore, Reels, Profile. Messages are behind the Messenger or Messages icon on the Home tab. Like is a heart labelled Like; it reads Unlike once liked.",
        "com.whatsapp" to "The message box is labelled Message and Send is labelled Send. A sent message appears at the bottom of the chat.",
        "com.google.android.apps.messaging" to "The text box is labelled Text message and Send is labelled Send SMS. Tap Send once; a sent message then appears as a bubble in the conversation and the box empties, even if delivery fails on a phone with no network.",
        "com.samsung.android.messaging" to "Send is labelled Send.",
        "com.google.android.GoogleCamera" to CAMERA,
        "com.sec.android.app.camera" to CAMERA,
        "com.android.camera" to CAMERA,
        "com.android.camera2" to CAMERA,
        "com.google.android.dialer" to CALL,
        "com.samsung.android.incallui" to CALL,
        "com.samsung.android.dialer" to CALL,
        "com.android.incallui" to CALL,
        "com.google.android.apps.photos" to GALLERY,
        "com.sec.android.gallery3d" to GALLERY,
        "com.miui.gallery" to GALLERY,
        "com.android.settings" to "Toggles appear as switches with on or off. Use scroll to find items lower down.",
    )

    fun forPackage(packageName: String): String? = hints[packageName]
}
