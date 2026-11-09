package io.alvaroedo.meshconnect.ui.model

import android.content.Context
import android.text.format.DateUtils
import io.alvaroedo.meshconnect.R

/** "just now" under a minute, else the platform's localized "5 minutes ago". */
object Ago {
    fun of(context: Context, thenMs: Long, nowMs: Long): String =
        if (nowMs - thenMs in 0 until 60_000) {
            context.getString(R.string.just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(thenMs, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()
        }
}
