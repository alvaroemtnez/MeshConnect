package io.alvaroedo.meshconnect.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R

/**
 * The buttons beside a QR code's Done: copy its link, or hand the link to another app through
 * Android's share sheet (a text message, Signal, email…) for someone who is not in the room
 * to scan it (2026-11-10). One place, so the contact, channel and radio codes offer the same.
 */
@Composable
internal fun QrLinkActions(uri: String?) {
    val clipboard = io.alvaroedo.meshconnect.ui.rememberTextClipboard()
    val context = LocalContext.current
    Row {
        TextButton(
            enabled = uri != null,
            onClick = { uri?.let { clipboard.copy(it) } },
        ) { Text(stringResource(R.string.ch_copy_link)) }
        TextButton(
            enabled = uri != null,
            onClick = { uri?.let { shareLink(context, it) } },
        ) { Text(stringResource(R.string.qr_share_link)) }
    }
}

/** ACTION_SEND as plain text, through the system chooser. Nothing leaves until the user picks an app. */
internal fun shareLink(context: Context, uri: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, uri)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.qr_share_chooser))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
