package io.alvaroedo.meshconnect.ui.model

/** How the battery glyph warns (AppTopBar.BatteryGlyph): red at 15% or less, blinking at 10% or less. */
object BatteryAlarm {
    const val LOW_PERCENT = 15
    const val BLINK_PERCENT = 10

    fun isLow(percent: Int?): Boolean = percent != null && percent <= LOW_PERCENT

    fun blinks(percent: Int?): Boolean = percent != null && percent <= BLINK_PERCENT
}
