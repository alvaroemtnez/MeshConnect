package io.alvaroedo.meshconnect.ui.model

import android.content.Context
import android.text.format.DateUtils
import io.alvaroedo.meshconnect.R

/** "just now" under a minute, else the platform's localized "5 minutes ago". */
object Ago {
    fun of(context: Context, thenMs: Long, nowMs: Long): String =
        if (isJustNow(thenMs, nowMs)) {
            context.getString(R.string.just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(thenMs, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()
        }

    /**
     * Under a minute old — or newer than [nowMs]. Callers pass a "now" that ticks every 30 s
     * (the radio card), so anything heard since the last tick is a little in the future, and
     * the platform worded that "in 0 minutes" (seen on the radio card, 2026-11-16).
     */
    fun isJustNow(thenMs: Long, nowMs: Long): Boolean = nowMs - thenMs < 60_000
}
