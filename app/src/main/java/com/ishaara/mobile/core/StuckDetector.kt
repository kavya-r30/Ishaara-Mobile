package com.ishaara.mobile.core

enum class StuckSignal { OK, HINT, STOP }

/** Flags the same action being repeated on an unchanged screen, which means it is having no effect. */
class StuckDetector {
    private var lastKey: String? = null
    private var count = 0

    fun record(screen: String, action: String): StuckSignal {
        val key = "$screen|$action"
        count = if (key == lastKey) count + 1 else 1
        lastKey = key
        return when (count) {
            1 -> StuckSignal.OK
            2 -> StuckSignal.HINT
            else -> StuckSignal.STOP
        }
    }
}
