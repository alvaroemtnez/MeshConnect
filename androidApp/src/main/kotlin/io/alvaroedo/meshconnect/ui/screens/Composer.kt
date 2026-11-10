package io.alvaroedo.meshconnect.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.ui.model.RichMessage
import io.alvaroedo.meshconnect.ui.theme.MonoFontFamily
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * The message bar, after Skypost and Signal: a rounded field that grows
 * upwards line by line and one round button at its end. While the field
 * is empty the button is + and opens [AttachSheet]; with text it sends.
 *
 * The line under the field counts down the bytes left once a message gets
 * near the frame limit, so the cut-off never comes as a surprise.
 */
@Composable
fun MessageComposer(
    draft: TextFieldValue,
    onDraft: (TextFieldValue) -> Unit,
    placeholder: String,
    bytesLeft: Int,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    decorationBox = { inner ->
                        Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh) {
                            Box(
                                Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (draft.text.isEmpty()) {
                                    Text(
                                        placeholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                inner()
                            }
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (draft.text.isBlank()) {
                    FilledTonalIconButton(onClick = onAttach, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.attach))
                    }
                } else {
                    FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_send), stringResource(R.string.send))
                    }
                }
            }
            Text(
                if (draft.text.isNotEmpty() && bytesLeft <= BYTES_WARNING) {
                    stringResource(R.string.compose_bytes_left, bytesLeft)
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (bytesLeft <= 0) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 18.dp).height(18.dp),
            )
        }
    }
}

private const val BYTES_WARNING = 30

enum class Attachment(@DrawableRes val icon: Int, val label: Int) {
    MyContact(R.drawable.ic_badge, R.string.attach_my_contact),
    Channel(R.drawable.ic_tag, R.string.attach_channel),
    Contact(R.drawable.ic_person_add, R.string.attach_contact),
    MyLocation(R.drawable.ic_pin_drop, R.string.attach_my_location),
    MapLocation(R.drawable.ic_map, R.string.attach_map_location),
}

/** What + offers: a grid of round buttons, like the attach sheets in Signal and WhatsApp. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(onPick: (Attachment) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val entries = Attachment.entries
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // A short last row is centred under the full ones, not left-aligned with gaps.
            for (row in entries.chunked(3)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    for (a in row) AttachButton(a) { onPick(a) }
                }
            }
        }
    }
}

@Composable
private fun AttachButton(a: Attachment, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(a.icon), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(a.label),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Every node on the radio, searchable, to share as a contact token. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerSheet(contacts: List<ContactEntity>, onPick: (ContactEntity) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.attach_contact),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SearchPill(query, { query = it }, stringResource(R.string.nodes_search_hint))
            val shown = contacts
                .filter { it.type in Codes.ADV_TYPE_CHAT..Codes.ADV_TYPE_SENSOR }
                .filter { query.isBlank() || it.name.contains(query, true) || it.keyHex.startsWith(query.lowercase()) }
                .sortedBy { it.name.lowercase() }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(shown, key = { it.keyHex }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name.ifBlank { c.keyHex.take(12) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(stringResource(singleKindLabel(c.type))) },
                        leadingContent = { NodeAvatar(seed = c.keyHex, label = c.name.ifBlank { c.keyHex }, type = c.type) },
                        modifier = Modifier.clickable { onPick(c) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

/** The radio's channels, to share one as its join link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelPickerSheet(channels: List<ChannelEntity>, onPick: (ChannelEntity) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.attach_channel),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.share_channel_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            for (ch in channels.sortedBy { it.idx }) {
                ListItem(
                    headlineContent = { Text(ch.name.ifBlank { "#${ch.idx}" }) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_tag), null) },
                    modifier = Modifier.clickable { onPick(ch) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, hint: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards for messages that are a shared thing (RichMessage)
// ---------------------------------------------------------------------------

/**
 * A shared contact. Known nodes open (chat, room, or admin hub); unknown
 * ones offer Add, which goes through the same confirmation as a scanned QR.
 * The name is whatever the sender typed: it is shown, never trusted.
 */
@Composable
fun ContactCard(
    c: RichMessage.Contact,
    known: ContactEntity?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    isSelf: Boolean = false,
) {
    CardShell(onClick = if (isSelf) ({}) else if (known != null) onOpen else onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NodeAvatar(seed = c.publicKeyHex, label = c.name, type = c.type)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    known?.name?.ifBlank { null } ?: c.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (isSelf) R.string.card_you else singleKindLabel(c.type)) + " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        c.publicKeyHex.take(8).uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isSelf) return@CardShell
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = if (known != null) onOpen else onAdd, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when {
                        known == null -> R.string.card_add_contact
                        c.type == Codes.ADV_TYPE_REPEATER || c.type == Codes.ADV_TYPE_SENSOR -> R.string.card_manage
                        else -> R.string.card_message
                    },
                ),
            )
        }
    }
}

/** A shared channel: its name, the obfuscated-not-secure reminder, and Join or Open. */
@Composable
fun ChannelCard(c: RichMessage.Channel, joined: Boolean, onJoin: () -> Unit, onOpen: () -> Unit) {
    CardShell(onClick = if (joined) onOpen else onJoin) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tag), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.card_channel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = if (joined) onOpen else onJoin, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (joined) R.string.open else R.string.join))
        }
    }
}

/**
 * A shared place. The preview is a map snapshot when tiles are available;
 * tapping hands the point to the phone's own map app.
 */
@Composable
fun LocationCard(loc: RichMessage.Location, preview: @Composable () -> Unit) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.card_no_map_app)
    CardShell(
        padded = false,
        onClick = { openInMaps(context, loc, noApp) },
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
            preview()
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_location_on), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.card_location), style = MaterialTheme.typography.titleSmall)
                Text(
                    RichMessage.locationText(loc.lat, loc.lon),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_open_in_new), stringResource(R.string.card_open_in_maps), modifier = Modifier.size(18.dp))
        }
    }
}

fun openInMaps(context: android.content.Context, loc: RichMessage.Location, noAppMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RichMessage.geoUri(loc.lat, loc.lon)))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, noAppMessage, android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * What a long-press on a card inside a message does: open that message's actions.
 *
 * A card's own click handler consumes the whole gesture, long-press included, so without
 * this a press on the card's face did nothing and only the bubble's thin margin around it
 * opened the menu. Provided by the bubble; null outside one (pickers, previews).
 */
val LocalMessageLongPress = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CardShell(onClick: () -> Unit, padded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val longPress = LocalMessageLongPress.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp).padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = longPress),
    ) {
        Column(if (padded) Modifier.padding(12.dp) else Modifier) { content() }
    }
}

/** Placeholder preview: a pin on the theme's surface, used without map tiles. */
@Composable
fun PinPreview() {
    Box(
        Modifier.fillMaxWidth().height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().height(140.dp)) {}
        Icon(
            painterResource(R.drawable.ic_location_on),
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** The send text for each picked attachment, or null if it cannot be built. */
object AttachmentText {
    fun myContact(publicKeyHex: String?, name: String?): String? =
        publicKeyHex?.takeIf { it.length == 64 }?.let { RichMessage.contactText(it, Codes.ADV_TYPE_CHAT, name.orEmpty()) }

    fun contact(c: ContactEntity): String? =
        c.keyHex.takeIf { it.length == 64 && c.type in Codes.ADV_TYPE_CHAT..Codes.ADV_TYPE_SENSOR }
            ?.let { RichMessage.contactText(it, c.type, c.name) }
}


/**
 * The map behind a location card: a static MapLibre snapshot with the pin
 * on top, or the plain pin when map tiles are off.
 */
@Composable
fun LocationPreview(loc: RichMessage.Location, tilesEnabled: Boolean) {
    if (!tilesEnabled) {
        PinPreview()
        return
    }
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = context.resources.displayMetrics.density
    val key = "${loc.lat},${loc.lon},$dark"
    var bitmap by remember(key) { mutableStateOf(SnapshotCache.get(key)) }
    DisposableEffect(key) {
        var snapshotter: org.maplibre.android.snapshotter.MapSnapshotter? = null
        if (bitmap == null) {
            io.alvaroedo.meshconnect.ui.map.MapStyles.init(context)
            val options = org.maplibre.android.snapshotter.MapSnapshotter.Options((280 * density).toInt(), (140 * density).toInt())
                .withStyleBuilder(
                    org.maplibre.android.maps.Style.Builder().fromUri(
                        if (dark) io.alvaroedo.meshconnect.ui.map.MapStyles.DARK else io.alvaroedo.meshconnect.ui.map.MapStyles.LIGHT,
                    ),
                )
                .withCameraPosition(
                    org.maplibre.android.camera.CameraPosition.Builder()
                        .target(org.maplibre.android.geometry.LatLng(loc.lat, loc.lon)).zoom(14.0).build(),
                )
                .withLogo(false)
            snapshotter = org.maplibre.android.snapshotter.MapSnapshotter(context, options).also {
                it.start({ snap ->
                    SnapshotCache.put(key, snap.bitmap)
                    bitmap = snap.bitmap
                }, { /* offline or refused: the pin placeholder stays */ })
            }
        }
        onDispose { snapshotter?.cancel() }
    }
    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b == null) {
            PinPreview()
        } else {
            androidx.compose.foundation.Image(
                b.asImageBitmap(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(140.dp),
            )
            // The pin's tip on the point.
            Icon(
                painterResource(R.drawable.ic_location_on),
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp).padding(bottom = 22.dp),
            )
        }
    }
}

/** Recent snapshots, so scrolling back up a thread doesn't render them again. */
private object SnapshotCache {
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(24)
    fun get(key: String): android.graphics.Bitmap? = cache.get(key)
    fun put(key: String, b: android.graphics.Bitmap) { cache.put(key, b) }
}
