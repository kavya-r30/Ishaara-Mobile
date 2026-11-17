package com.ishaara.mobile.core

import com.ishaara.mobile.core.model.ActionResult
import com.ishaara.mobile.core.model.AgentAction
import com.ishaara.mobile.core.model.ScreenSnapshot
import com.ishaara.mobile.core.model.Screenshot

interface Device {
    /** Waits for the UI to settle, then reads the current screen. */
    suspend fun observe(): ScreenSnapshot

    /** Returns an annotated screenshot of [snapshot], or null if the screen cannot be captured. */
    suspend fun screenshot(snapshot: ScreenSnapshot): Screenshot?

    suspend fun execute(action: AgentAction): ActionResult
}
