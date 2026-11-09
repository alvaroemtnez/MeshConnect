package io.alvaroedo.meshconnect.ui.model

import android.content.Context
import io.alvaroedo.meshconnect.R

/**
 * One line for a message that is a card (chat list, notifications), so a
 * shared place reads "Location" rather than a pair of numbers.
 */
object RichPreview {
    fun of(context: Context, body: String): String? = when (val c = RichMessage.parse(body)) {
        is RichMessage.Contact -> context.getString(R.string.preview_contact, c.name)
        is RichMessage.Channel -> context.getString(R.string.preview_channel, c.name)
        is RichMessage.Location -> context.getString(R.string.preview_location)
        null -> null
    }
}
