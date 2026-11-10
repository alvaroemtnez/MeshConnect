package io.alvaroedo.meshconnect.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.model.LinkMeter
import io.alvaroedo.meshconnect.ui.theme.ExtraColors
import io.alvaroedo.meshconnect.ui.theme.MonoFontFamily
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import kotlin.math.hypot
import kotlin.math.max

/** One entry in a screen's overflow (⋮) menu. */
data class MenuAction(
    val label: String,
    val destructive: Boolean = false,
    /** Non-null renders a checkmark toggle state. */
    val checked: Boolean? = null,
    @DrawableRes val icon: Int? = null,
    val onClick: () -> Unit,
)

/**
 * A screen's search, hoisted so the query survives navigation. The top bar
 * shows a magnifying glass; tapping it turns the whole bar into a rounded
 * search field with a back arrow (Signal/Molly), revealed as a circle
 * growing from the icon.
 */
class TopBarSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val active: Boolean,
    val onActiveChange: (Boolean) -> Unit,
    val hint: String,
)

/**
 * The app-wide top bar: title on the left, connection status, the
 * screen's own actions, search, then the overflow menu.
 *
 * It shares its colour with the bottom navigation bar and draws under the
 * status bar (edge-to-edge), so each reads as one band with the system bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    vm: MeshCoreViewModel,
    menuActions: List<MenuAction>,
    nav: NavController? = null,
    subtitle: String? = null,
    info: (@Composable () -> Unit)? = null,
    search: TopBarSearch? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    Box {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = barColor,
                scrolledContainerColor = barColor,
            ),
            title = {
                Column {
                    // softWrap off: with wrapping on, a line break was allowed
                    // right after a leading '#', so a long channel name showed
                    // as just "#" (MeshCore Hardened issue #8).
                    Text(title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (nav != null) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                }
            },
            actions = {
                // Sub-screens (with a back arrow) get the icons only, so a long
                // title keeps its room (MeshCore Hardened issue #8).
                if (info != null) info() else ConnectionStatus(vm, compact = nav != null)
                actions()
                if (search != null) {
                    IconButton(onClick = { search.onActiveChange(true) }) {
                        Icon(painterResource(R.drawable.ic_search), stringResource(R.string.search))
                    }
                }
                if (menuActions.isNotEmpty()) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        for (action in menuActions) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        action.label,
                                        color = if (action.destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                                    )
                                },
                                leadingIcon = action.icon?.let { res ->
                                    {
                                        Icon(
                                            painterResource(res),
                                            null,
                                            tint = if (action.destructive) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (action.checked == true) Icon(painterResource(R.drawable.ic_check), null)
                                },
                                onClick = {
                                    menuOpen = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            },
        )
        if (search != null) {
            SearchOverlay(
                search = search,
                // The reveal grows from the magnifying glass, which sits one
                // icon (48 dp) left of the overflow menu when there is one.
                iconEndOffsetDp = if (menuActions.isNotEmpty()) 76f else 28f,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchOverlay(search: TopBarSearch, iconEndOffsetDp: Float, modifier: Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (search.active) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "searchReveal",
    )
    BackHandler(enabled = search.active) {
        search.onQueryChange("")
        search.onActiveChange(false)
    }
    if (progress == 0f) return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val focus = remember { FocusRequester() }
    LaunchedEffect(search.active) { if (search.active) runCatching { focus.requestFocus() } }

    Box(
        modifier
            .drawWithContent {
                val inset = iconEndOffsetDp.dp.toPx()
                val cx = if (rtl) inset else size.width - inset
                val top = size.height - 64.dp.toPx()
                val cy = top + 32.dp.toPx()
                val radius = progress * hypot(max(cx, size.width - cx), max(cy, size.height - cy))
                val circle = Path().apply { addOval(Rect(Offset(cx, cy), radius)) }
                clipPath(circle) { this@drawWithContent.drawContent() }
            }
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                search.onQueryChange("")
                search.onActiveChange(false)
            }) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.search_close))
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (search.query.isEmpty()) {
                            Text(
                                search.hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = search.query,
                            onValueChange = search.onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { search.onQueryChange("") }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.clear))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The status cluster in every top bar.
 *
 * Connected: a signal meter for the best repeater heard directly (with its
 * hash), and the radio's battery. Tapping opens [LinkSheet]. Connecting:
 * a pulsing amber dot. TCP: the plaintext warning, which wins over all.
 */
@Composable
fun ConnectionStatus(vm: MeshCoreViewModel, compact: Boolean = false) {
    val engineState by vm.engineState.collectAsStateWithLifecycle()
    val plaintext by vm.plaintextLink.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    val detailsLabel = stringResource(R.string.status_open_details)

    Row(
        Modifier
            .padding(end = 4.dp)
            .clip(CircleShape)
            .clickable { sheet = true }
            .semantics { contentDescription = detailsLabel }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            engineState == EngineState.Ready -> {
                // TCP is always flagged (upstream's rule): a red warning in
                // front of the meter, spelled out in the details sheet.
                if (plaintext) {
                    Icon(
                        painterResource(R.drawable.ic_warning),
                        stringResource(R.string.status_tcp_plain),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                ConnectedCluster(vm, compact)
            }
            engineState == EngineState.Handshaking || engineState == EngineState.Connecting -> {
                PulsingDot(ExtraColors.connecting)
                if (!compact) Text(
                    stringResource(
                        if (engineState == EngineState.Handshaking) R.string.status_handshake else R.string.status_connecting,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            else -> {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                if (!compact) Text(
                    stringResource(R.string.status_offline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    if (sheet) LinkSheet(vm) { sheet = false }
}

@Composable
private fun ConnectedCluster(vm: MeshCoreViewModel, compact: Boolean) {
    val repeaters by vm.directRepeaters.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val self by vm.selfInfo.collectAsStateWithLifecycle()
    val now = rememberNow()
    val best = LinkMeter.fresh(repeaters, now).firstOrNull()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    SignalBars(bars = best?.let { LinkMeter.bars(it.snr, self?.sf) } ?: 0, live = best != null)
    if (best != null && !compact) {
        Text(
            best.prefixHex.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFontFamily,
            color = ink,
            maxLines = 1,
        )
    }
    val pct = battery?.let { BatteryLevel.percent(it.batteryMillivolts) }
    if (battery != null) {
        Spacer(Modifier.width(2.dp))
        BatteryGlyph(pct)
        if (pct != null && !compact) {
            Text("$pct%", style = MaterialTheme.typography.labelSmall, color = ink, maxLines = 1)
        }
    }
}

/** Wall-clock "now" that ticks every 30 s, for ageing readings. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun PulsingDot(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val s by t.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Canvas(Modifier.size(10.dp)) {
        drawCircle(color.copy(alpha = a * 0.35f), radius = size.minDimension / 2 * s)
        drawCircle(color.copy(alpha = a), radius = size.minDimension / 2 * 0.7f)
    }
}

/** Four ascending bars, like the phone's own cell meter. */
@Composable
fun SignalBars(bars: Int, live: Boolean, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.onSurface.copy(alpha = if (live) 0.22f else 0.15f)
    Canvas(modifier.size(width = 16.dp, height = 14.dp)) {
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRoundRect(
                color = if (i < bars) on else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

/** A horizontal battery outline filled to [percent]; error colour at 15% or less. */
@Composable
fun BatteryGlyph(percent: Int?, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val fillColor = if (percent != null && percent <= 15) MaterialTheme.colorScheme.error else ink
    Canvas(modifier.size(width = 22.dp, height = 12.dp)) {
        val stroke = 1.2.dp.toPx()
        val nub = 2.dp.toPx()
        val body = Size(size.width - nub, size.height)
        drawRoundRect(
            color = ink.copy(alpha = 0.7f),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(body.width - stroke, body.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.7f),
            topLeft = Offset(body.width, size.height * 0.32f),
            size = Size(nub, size.height * 0.36f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        if (percent != null) {
            val pad = stroke + 1.dp.toPx()
            val full = body.width - pad * 2
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(pad, pad),
                size = Size(full * (percent.coerceIn(0, 100) / 100f), size.height - pad * 2),
                cornerRadius = CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}

/** Details behind the status cluster: battery, link, and the repeaters heard directly. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkSheet(vm: MeshCoreViewModel, onDismiss: () -> Unit) {
    val engineState by vm.engineState.collectAsStateWithLifecycle()
    val self by vm.selfInfo.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val label by vm.connectionLabel.collectAsStateWithLifecycle()
    val repeaters by vm.directRepeaters.collectAsStateWithLifecycle()
    val contacts by vm.liveContacts.collectAsStateWithLifecycle()
    val now = rememberNow()
    val rows = ListItemDefaults.colors(containerColor = Color.Transparent)
    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            // Header: which radio, and on what settings. 16 dp, the rows' and
            // tiles' left edge, so the sheet reads down one line.
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(self?.name ?: stringResource(R.string.link_title), style = MaterialTheme.typography.titleLarge)
                if (engineState == EngineState.Ready) {
                    Text(
                        io.github.thatsfguy.meshcore.presentation.radioSubtitle(self?.freqKhz, self?.sf, self?.txPowerDbm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (engineState != EngineState.Ready) {
                // Not connected: the card is where you connect. Saved radios are one tap.
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    RadioPicker(vm, onPicked = onDismiss)
                }
                return@Column
            }
            val plaintext by vm.plaintextLink.collectAsStateWithLifecycle()
            // Every row carries a leading icon in the same 24 dp slot, so the text
            // lines up; without one on Battery it started further left than Connection.
            label?.let {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.link_connection)) },
                    supportingContent = {
                        Text(it, color = if (plaintext) MaterialTheme.colorScheme.error else Color.Unspecified)
                    },
                    leadingContent = {
                        Icon(
                            painterResource(
                                when {
                                    plaintext -> R.drawable.ic_warning
                                    it.startsWith("USB") -> R.drawable.ic_usb
                                    else -> R.drawable.ic_bluetooth
                                },
                            ),
                            null,
                            tint = if (plaintext) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    colors = rows,
                )
            }
            battery?.let { b ->
                ListItem(
                    headlineContent = { Text(stringResource(R.string.link_battery)) },
                    supportingContent = { Text(BatteryLevel.label(b.batteryMillivolts)) },
                    leadingContent = {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            BatteryGlyph(BatteryLevel.percent(b.batteryMillivolts))
                        }
                    },
                    colors = rows,
                )
            }
            // Live while the sheet is open; firmware before v8 has no stats.
            var stats by remember { mutableStateOf<io.github.thatsfguy.meshcore.protocol.RadioStats?>(null) }
            LaunchedEffect(Unit) {
                while (true) {
                    stats = vm.radioStats() ?: stats
                    kotlinx.coroutines.delay(3_000)
                }
            }
            stats?.let { st ->
                // Readings as labelled tiles, two to a row: the paragraph they were
                // wrapped mid-phrase on a phone ("last / SNR 8.00 dB").
                SheetSection(stringResource(R.string.link_radio))
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(stringResource(R.string.link_noise), "${st.noiseFloorDbm} dBm", Modifier.weight(1f))
                        StatTile(stringResource(R.string.link_last_rssi), "${st.lastRssiDbm} dBm", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            stringResource(R.string.link_last_snr),
                            String.format(java.util.Locale.ROOT, "%.1f dB", st.lastSnrDb),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stringResource(R.string.link_airtime_label),
                            android.text.format.DateUtils.formatElapsedTime(st.txAirSecs) + " / " +
                                android.text.format.DateUtils.formatElapsedTime(st.rxAirSecs),
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
            SheetSection(stringResource(R.string.link_repeaters))
            Text(
                stringResource(R.string.link_repeaters_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            val fresh = LinkMeter.fresh(repeaters, now)
            if (fresh.isEmpty()) {
                Text(
                    stringResource(R.string.link_no_repeaters),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            for (r in fresh) {
                val name = LinkMeter.nameFor(r.prefixHex, contacts.values)
                    ?: stringResource(R.string.link_unknown_repeater)
                ListItem(
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            r.prefixHex.uppercase() + " · " +
                                stringResource(R.string.link_snr_rssi, "%.1f".format(r.snr), r.rssi) + " · " +
                                io.alvaroedo.meshconnect.ui.model.Ago.of(context, r.lastHeardMs, now),
                        )
                    },
                    leadingContent = { SignalBars(LinkMeter.bars(r.snr, self?.sf), live = true) },
                    colors = rows,
                )
            }
            // Switch to another saved radio, or a new one, without going through Settings.
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            var switching by remember { mutableStateOf(false) }
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                if (!switching) {
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.FilledTonalButton(onClick = { switching = true }) {
                            Icon(painterResource(R.drawable.ic_swap_horiz), null, Modifier.size(18.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.picker_more))
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            vm.disconnect()
                            onDismiss()
                        }) { Text(stringResource(R.string.set_disconnect)) }
                    }
                } else {
                    RadioPicker(vm, onPicked = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
    )
}

/** One reading: a small label over its value, on a tinted rounded tile. */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = MonoFontFamily, maxLines = 1)
        }
    }
}
