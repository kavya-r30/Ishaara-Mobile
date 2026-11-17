package com.ishaara.mobile.core.model

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Int get() = (right - left) * (bottom - top)
}

enum class ElementRole { BUTTON, INPUT, TEXT, IMAGE, LIST, SWITCH }

data class UiElement(
    val id: Int,
    val role: ElementRole,
    val label: String,
    val bounds: Bounds,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val isPassword: Boolean = false,
    val checked: Boolean? = null,
    val value: String? = null,
    val resourceId: String? = null,
)

/** A JPEG of the screen. Multiply image coordinates by [scale] to get screen coordinates. */
data class Screenshot(val jpegBase64: String, val scale: Double)

data class ScreenSnapshot(
    val packageName: String,
    val elements: List<UiElement>,
    val screenshot: Screenshot? = null,
)
