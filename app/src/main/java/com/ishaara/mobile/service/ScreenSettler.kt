package com.ishaara.mobile.service

import android.os.SystemClock
import kotlinx.coroutines.delay

/** Waits until accessibility events stop arriving, with a ceiling so animated screens cannot block it. */
class ScreenSettler {
    @Volatile
    private var lastEvent = 0L

    @Volatile
    private var actionAt = 0L

    @Volatile
    private var expectChangeMs = 0L

    fun onEvent() {
        lastEvent = SystemClock.uptimeMillis()
    }

    /** Call after an action. The next wait gives the screen up to [expectMs] to start changing, so an app still launching isn't read as settled. */
    fun actionPerformed(expectMs: Long) {
        actionAt = SystemClock.uptimeMillis()
        expectChangeMs = expectMs
    }

    suspend fun await(quietMs: Long = 400, maxMs: Long = 3000) {
        delay(150)
        while (expectChangeMs > 0 && lastEvent < actionAt && SystemClock.uptimeMillis() - actionAt < expectChangeMs) {
            delay(50)
        }
        expectChangeMs = 0
        val start = SystemClock.uptimeMillis()
        while (SystemClock.uptimeMillis() - start < maxMs) {
            if (SystemClock.uptimeMillis() - lastEvent >= quietMs) return
            delay(50)
        }
    }
}
