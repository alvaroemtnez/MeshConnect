package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.util.avatarColors

/**
 * Hash-colored avatar circle for contacts and channels: the background
 * derives from the node pubkey / channel seed (AvatarColors, ported
 * from the sibling app / Meshtastic scheme) so every row is instantly
 * distinguishable. Repeaters are deliberately NEUTRAL GRAY — same as
 * their map pins — because infrastructure shouldn't scream for
 * attention (and red reads as trouble).
 */
@Composable
fun NodeAvatar(
    seed: String,
    label: String,
    type: Int? = null,
    isChannel: Boolean = false,
    size: Dp = 40.dp,
    /**
     * A channel's kind, from its key. Only a [ChannelKind.Hashtag]
     * channel gets a '#': putting one on every channel made private
     * channels look like ones anyone can join. Null (key not readable)
     * falls back to the name's initial rather than claiming a kind.
     */
    channelKind: io.github.thatsfguy.meshcore.protocol.ChannelKind? = null,
    /**
     * Show the node-type icon instead of an initial for a chat node too, on its hashed
     * colour. The Nodes list does: there the type is what you scan for, and the icons
     * match its filter chips and the map pins. Chats keep the initial.
     */
    typeIcon: Boolean = false,
) {
    // Infrastructure nodes reuse the MAP marker artwork (same colors +
    // glyphs) so a repeater looks identical in the list and on the map.
    if (type == Codes.ADV_TYPE_REPEATER ||
        type == Codes.ADV_TYPE_ROOM ||
        type == Codes.ADV_TYPE_SENSOR
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val badge = androidx.compose.runtime.remember(type, size) {
            io.alvaroedo.meshconnect.platform.NodeMarkers
                .buildBadge(context, type, sizeDp = size.value.toInt())
        }
        androidx.compose.foundation.Image(
            bitmap = badge.bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
        return
    }

    val c = avatarColors(seed)
    val background = Color(c.backgroundArgb)
    val darkText = c.useDarkText

    val ink = if (darkText) Color.Black else Color.White
    // A channel whose kind is known shows it as an icon: a lock for a private
    // key, a globe for Public, a tag for a hashtag channel (whose key anyone
    // can derive from its name). Unknown kind falls through to the initial.
    if (isChannel && channelKind != null) {
        val (icon, label) = when (channelKind) {
            io.github.thatsfguy.meshcore.protocol.ChannelKind.Private -> R.drawable.ic_lock to R.string.cd_private_channel
            io.github.thatsfguy.meshcore.protocol.ChannelKind.Hashtag -> R.drawable.ic_tag to R.string.cd_hashtag_channel
            io.github.thatsfguy.meshcore.protocol.ChannelKind.Public -> R.drawable.ic_public to R.string.cd_public_channel
        }
        Box(
            modifier = Modifier.size(size).background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(icon),
                contentDescription = stringResource(label),
                tint = ink,
                modifier = Modifier.size(size * 0.55f),
            )
        }
        return
    }

    if (typeIcon && !isChannel) {
        Box(
            modifier = Modifier.size(size).background(background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(
                    io.alvaroedo.meshconnect.platform.NodeMarkers.iconFor(type ?: Codes.ADV_TYPE_CHAT, isSelf = false),
                ),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(size * 0.58f),
            )
        }
        return
    }

    // Not the first character: an emoji is two, and half of one drew "�". See AvatarGlyph.
    val glyph = io.github.thatsfguy.meshcore.presentation.AvatarGlyph.of(label)

    Box(
        modifier = Modifier
            .size(size)
            .background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (darkText) Color.Black else Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/** Neutral infrastructure gray, shared with the map's repeater pins. */
val REPEATER_GRAY = Color(0xFF757575)
