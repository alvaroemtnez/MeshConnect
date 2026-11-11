package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import io.github.thatsfguy.meshcore.presentation.Units
import io.github.thatsfguy.meshcore.presentation.UnitSystem
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.firmware.deviceIdentityLine
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.PathHashMode
import io.github.thatsfguy.meshcore.protocol.RadioUnits
import io.github.thatsfguy.meshcore.protocol.Regions
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import java.text.DateFormat
import java.util.Date

// ----------------------------------------------------------------------
// Section scaffolding
// ----------------------------------------------------------------------

@Composable
internal fun ConnectionSection(vm: MeshCoreViewModel, onAddNode: () -> Unit) {
    val engineState by vm.engineState.collectAsState()
    val label by vm.connectionLabel.collectAsState()
    val plaintext by vm.plaintextLink.collectAsState()
    val lastError by vm.lastError.collectAsState()
    val battery by vm.battery.collectAsState()
    val deviceInfo by vm.deviceInfo.collectAsState()
    var autoReconnect by remember { mutableStateOf(vm.prefs.autoReconnect) }

    Text(
        when (engineState) {
            EngineState.Ready -> stringResource(R.string.setup_connected_to, label ?: "")
            EngineState.Handshaking -> stringResource(R.string.status_handshake)
            EngineState.Connecting -> stringResource(R.string.status_connecting)
            EngineState.Detached -> stringResource(R.string.hub_not_connected)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (plaintext && engineState == EngineState.Ready) {
        Text(
            stringResource(R.string.set_link_unencrypted),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    battery?.let {
        HintText(stringResource(R.string.set_battery, BatteryLevel.label(it.batteryMillivolts)))
    }
    deviceIdentityLine(deviceInfo)?.let { HintText(it) }
    lastError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(4.dp))
    ButtonFlowRow {
        FilledTonalButton(onClick = onAddNode) { Text(stringResource(R.string.set_add_node)) }
        if (engineState != EngineState.Detached) {
            FilledTonalButton(onClick = { vm.disconnect() }) { Text(stringResource(R.string.set_disconnect)) }
        }
    }
    SavedNodesList(vm)
    SettingRow(stringResource(R.string.set_auto_reconnect), autoReconnect) {
        autoReconnect = it
        vm.prefs.autoReconnect = it
    }
    var roam by remember { mutableStateOf(vm.prefs.roamSavedRadios) }
    SettingRow(stringResource(R.string.set_roam), roam) {
        roam = it
        vm.prefs.roamSavedRadios = it
    }
    HintText(stringResource(R.string.set_roam_hint))
    var startAtBoot by remember { mutableStateOf(vm.prefs.startAtBoot) }
    SettingRow(stringResource(R.string.set_start_at_boot), startAtBoot) {
        startAtBoot = it
        vm.prefs.startAtBoot = it
    }
    HintText(stringResource(R.string.set_start_at_boot_hint))
}

@Composable
internal fun TransportsSection(
    vm: MeshCoreViewModel,
    tcpEnabled: Boolean,
    onTcpToggle: (Boolean) -> Unit,
) {
    var bleEnabled by remember { mutableStateOf(vm.prefs.bleEnabled) }
    var usbEnabled by remember { mutableStateOf(vm.prefs.usbEnabled) }

    SettingRow("Bluetooth LE", bleEnabled) {
        bleEnabled = it
        vm.prefs.bleEnabled = it
    }
    SettingRow(stringResource(R.string.set_usb), usbEnabled) {
        usbEnabled = it
        vm.prefs.usbEnabled = it
    }
    SettingRow(stringResource(R.string.set_tcp), tcpEnabled, onChange = onTcpToggle)
    if (tcpEnabled) {
        Text(
            stringResource(R.string.set_tcp_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    HintText(stringResource(R.string.set_transport_hint))
}

@Composable
internal fun IdentitySection(vm: MeshCoreViewModel, onShowSelfQr: () -> Unit) {
    val self by vm.selfInfo.collectAsState()
    val info = self
    if (info == null) {
        HintText(stringResource(R.string.set_identity_connect))
        return
    }
    var name by remember(info.name) { mutableStateOf(info.name) }
    // SELF_INFO has no "unset" — a radio with no fix reports 0, 0, which
    // as a coordinate is a point in the Gulf of Guinea. Printing it into
    // the field states a position this radio has never had, and the
    // reader has no way to tell it apart from one they typed. Blank is
    // what "not set" looks like in a text field.
    val fixed = isPlausiblePosition(info.latitude, info.longitude)
    var lat by remember(info.latitude) {
        mutableStateOf(if (fixed) info.latitude.toString() else "")
    }
    var lon by remember(info.longitude) {
        mutableStateOf(if (fixed) info.longitude.toString() else "")
    }

    // Who this radio is (key, with its QR beside it), what it is called, where it is, and
    // telling the mesh: one block each, rather than a row of unrelated buttons (2026-11-10).
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfoTile(
            stringResource(R.string.set_public_key),
            info.publicKeyHex,
            Modifier.weight(1f),
            mono = true,
        )
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onShowSelfQr,
            modifier = Modifier.size(56.dp),
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_qr_code),
                stringResource(R.string.my_qr),
            )
        }
    }

    SheetHeading(stringResource(R.string.set_name_heading), Modifier.padding(top = 0.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.set_advert_name)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { vm.setAdvertName(name.trim()) },
            enabled = name.trim() != info.name && name.isNotBlank(),
        ) { Text(stringResource(R.string.set_set)) }
    }
    var pickPosition by remember { mutableStateOf(false) }
    if (pickPosition) {
        PositionPickerDialog(
            vm = vm,
            initialLat = lat.toDoubleOrNull() ?: 0.0,
            initialLon = lon.toDoubleOrNull() ?: 0.0,
            onPick = { pickedLat, pickedLon ->
                lat = "%.5f".format(pickedLat)
                lon = "%.5f".format(pickedLon)
                pickPosition = false
            },
            onDismiss = { pickPosition = false },
        )
    }
    // Picking on the map fills the two fields, so it sits on their heading.
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.node_position),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { pickPosition = true }) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_add_location),
                null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.set_pick_on_map))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = lat, onValueChange = { lat = it },
            label = { Text(stringResource(R.string.set_latitude)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FormField(
            value = lon, onValueChange = { lon = it },
            label = { Text(stringResource(R.string.set_longitude)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = {
            val la = lat.toDoubleOrNull()
            val lo = lon.toDoubleOrNull()
            if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) {
                vm.setAdvertLocation(la, lo)
            }
        }) { Text(stringResource(R.string.set_set)) }
    }
    if (!fixed) {
        HintText(
            stringResource(R.string.set_no_position),
        )
    }

    SheetHeading(stringResource(R.string.set_announce), Modifier.padding(top = 0.dp))
    HintText(stringResource(R.string.set_announce_hint))
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { vm.sendSelfAdvert(flood = false) }, modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_sensors), null, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.set_announce_near))
        }
        FilledTonalButton(onClick = { vm.sendSelfAdvert(flood = true) }, modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_campaign), null, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.set_announce_mesh))
        }
    }
}

@Composable
internal fun RadioSection(vm: MeshCoreViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val self by vm.selfInfo.collectAsState()
    val info = self
    if (info == null) {
        HintText(stringResource(R.string.radio_connect))
        return
    }
    // MHz and kHz — what the label says and what the remote form shows.
    // The WIRE stays kHz/Hz (CMD_SET_RADIO_PARAMS); RadioUnits converts
    // at the edge. Showing the wire's own units here meant the same
    // radio read 910525 on this screen and 910.5250244 on a repeater's,
    // one tap apart.
    var freq by remember(info.freqKhz) {
        mutableStateOf(RadioUnits.khzToMhzText(info.freqKhz))
    }
    var bw by remember(info.bwHz) { mutableStateOf(RadioUnits.hzToKhzText(info.bwHz)) }
    var sf by remember(info.sf) { mutableStateOf(info.sf.toString()) }
    var cr by remember(info.cr) { mutableStateOf(info.cr.toString()) }
    var tx by remember(info.txPowerDbm) { mutableStateOf(info.txPowerDbm.toString()) }

    HintText(stringResource(R.string.radio_must_match))

    // Which preset (if any) the radio is currently sitting on. More than
    // one can match — several Russian city presets share parameters — so
    // all matches are named rather than one being picked.
    var presetSheet by remember { mutableStateOf(false) }
    var shareSettings by remember { mutableStateOf(false) }
    val matches = remember(info.freqKhz, info.bwHz, info.sf, info.cr) {
        io.github.thatsfguy.meshcore.protocol.RadioPresets.matching(
            info.freqKhz, info.bwHz, info.sf, info.cr,
        )
    }
    HintText(
        when {
            matches.isEmpty() -> stringResource(R.string.radio_no_preset)
            matches.size == 1 -> stringResource(R.string.setup_radio_current, matches[0].name)
            else -> stringResource(R.string.setup_radio_current, matches.joinToString(", ") { it.name })
        },
    )
    // The scanner belongs HERE as well as on Chats/Nodes. A settings QR
    // changes exactly what this screen changes, and nobody looking for
    // "how do I apply the code my mesh handed me" thinks to open the
    // contact-import button on another tab.
    val scanLauncher = rememberLauncherForActivityResult(io.alvaroedo.meshconnect.platform.ScanQr()) { result ->
        result?.let { vm.importScannedCode(it) }
    }
    if (presetSheet) {
        RadioPresetSheet(vm, onDismiss = { presetSheet = false })
    }
    if (shareSettings) {
        val device by vm.deviceInfo.collectAsState()
        val region by vm.floodScopeRegion.collectAsState()
        RadioShareDialog(
            uri = io.github.thatsfguy.meshcore.presentation.RadioShare.uriFor(
                info.freqKhz, info.bwHz, info.sf, info.cr,
                device?.pathHashByteWidth,
                region,
                matches.singleOrNull()?.name,
            ),
            region = region?.takeIf { it.isNotBlank() },
            onDismiss = { shareSettings = false },
        )
    }

    // The values first, as pickers where the chip only takes a fixed set (RadioChoices);
    // then where else they can come from (a preset, a code) or go to (share); then Apply.
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = freq, onValueChange = { freq = it },
            label = { Text(stringResource(R.string.radio_freq)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        val bwHz = RadioUnits.khzTextToHz(bw)
        DropdownField(
            label = stringResource(R.string.radio_bw),
            value = bwHz,
            options = io.alvaroedo.meshconnect.ui.model.RadioChoices.bandwidths(bwHz)
                .map { it to RadioUnits.hzToKhzText(it) },
            modifier = Modifier.weight(1f),
        ) { bw = RadioUnits.hzToKhzText(it!!) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DropdownField(
            label = stringResource(R.string.radio_sf),
            value = sf.toIntOrNull(),
            options = io.alvaroedo.meshconnect.ui.model.RadioChoices.spreadingFactors(sf.toIntOrNull())
                .map { it to "SF$it" },
            modifier = Modifier.weight(1f),
        ) { sf = it.toString() }
        Spacer(Modifier.width(8.dp))
        DropdownField(
            label = stringResource(R.string.radio_cr),
            value = cr.toIntOrNull(),
            options = io.alvaroedo.meshconnect.ui.model.RadioChoices.codingRates(cr.toIntOrNull())
                .map { it to io.alvaroedo.meshconnect.ui.model.RadioChoices.codingRateLabel(it) },
            modifier = Modifier.weight(1f),
        ) { cr = it.toString() }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { presetSheet = true }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.setup_pick_preset), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.FilledTonalIconButton(onClick = {
            scanLauncher.launch(ctx.getString(R.string.radio_scan_prompt))
        }) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_qr_code_scanner),
                stringResource(R.string.radio_scan),
            )
        }
        androidx.compose.material3.FilledTonalIconButton(onClick = { shareSettings = true }) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_share),
                stringResource(R.string.radio_share),
            )
        }
    }
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            // Typed in MHz/kHz, sent in kHz/Hz. The bounds stay in wire
            // units so they keep meaning the same thing as the radio's:
            // 300_000..2_500_000 kHz is 300..2500 MHz. An earlier upper
            // bound of 2_500_000_000 was that range read as Hz, so BOTH
            // interpretations passed and the check caught nothing.
            val f = RadioUnits.mhzTextToKhz(freq)
            val b = RadioUnits.khzTextToHz(bw)
            val s = sf.toIntOrNull()
            val c = cr.toIntOrNull()
            if (f != null && f in 300_000..2_500_000 && b != null && b in 7_000..500_000 &&
                s != null && s in 5..12 && c != null && c in 5..8
            ) {
                vm.setRadioParams(f, b, s, c)
            } else {
                vm.transientMessage.value = ctx.getString(R.string.radio_bad_params)
            }
        },
    ) { Text(stringResource(R.string.radio_apply_params)) }

    SheetHeading(stringResource(R.string.radio_power_heading), Modifier.padding(top = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = tx, onValueChange = { tx = it },
            label = { Text(stringResource(R.string.radio_tx_power, info.maxTxPowerDbm)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = {
            tx.toIntOrNull()?.takeIf { it in 1..info.maxTxPowerDbm }?.let { vm.setTxPower(it) }
        }) { Text(stringResource(R.string.set)) }
    }
}

@Composable
internal fun ClockSection(vm: MeshCoreViewModel) {
    val engineState by vm.engineState.collectAsState()
    var radioTime by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(engineState, refresh) {
        if (engineState == EngineState.Ready) {
            loading = true
            radioTime = vm.deviceTime()
        }
        loading = false
    }
    if (engineState != EngineState.Ready) {
        HintText(stringResource(R.string.clock_connect))
        return
    }
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            HintText(stringResource(R.string.qr_loading))
        }
        return
    }
    val drift = radioTime?.let { it - System.currentTimeMillis() / 1000 }
    Text(
        stringResource(R.string.clock_radio, radioTime?.let { DateFormat.getDateTimeInstance().format(Date(it * 1000)) } ?: stringResource(R.string.clock_no_answer)),
    )
    drift?.let { d ->
        Text(
            stringResource(R.string.clock_drift, formatDrift(d, driftWords())),
            style = MaterialTheme.typography.bodyMedium,
            color = if (kotlin.math.abs(d) > 30) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
    ButtonFlowRow {
        FilledTonalButton(onClick = {
            vm.syncDeviceClock()
            refresh++
        }) { Text(stringResource(R.string.clock_sync)) }
        FilledTonalButton(onClick = { refresh++ }) { Text(stringResource(R.string.clock_reread)) }
    }
    ExpandableHint(stringResource(R.string.clock_auto)) {
        Text(
            stringResource(R.string.clock_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PoliciesSection(vm: MeshCoreViewModel) {
    val self by vm.selfInfo.collectAsState()
    val info = self
    if (info == null) {
        HintText(stringResource(R.string.pol_connect))
        return
    }

    // Decompose the SELF_INFO policy bytes.
    val teleBase = info.telemetryModes and 0x03
    val teleLoc = (info.telemetryModes shr 2) and 0x03
    val teleEnv = (info.telemetryModes shr 4) and 0x03

    fun apply(
        base: Int = teleBase, loc: Int = teleLoc, env: Int = teleEnv,
        locPolicy: Int = info.advertLocPolicy, multiAcks: Int = info.multiAcks,
    ) {
        vm.setOtherParams((env shl 4) or (loc shl 2) or base, locPolicy, multiAcks)
    }

    SettingRow(stringResource(R.string.pol_location), info.advertLocPolicy == 1) {
        apply(locPolicy = if (it) 1 else 0)
    }
    SettingRow(stringResource(R.string.pol_multiacks), info.multiAcks != 0) {
        apply(multiAcks = if (it) 1 else 0)
    }

    // A local send policy rather than a radio setting, but this is the
    // screen about how traffic moves, and it is the one MeshCore's FAQ
    // means when it says the fallback "can be turned off in settings".
    var floodFallback by remember { mutableStateOf(vm.prefs.floodFallbackOnLastRetry) }
    SettingRow(stringResource(R.string.pol_flood_retry), floodFallback) {
        floodFallback = it
        vm.prefs.floodFallbackOnLastRetry = it
    }
    ExpandableHint(stringResource(R.string.pol_flood_hint)) {
        DetailText(
            stringResource(R.string.pol_flood_detail),
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.pol_telemetry), style = MaterialTheme.typography.labelLarge)
    HintText(stringResource(R.string.pol_telemetry_hint))
    val teleOptions = listOf(stringResource(R.string.pol_tele_deny), stringResource(R.string.pol_tele_flags), stringResource(R.string.pol_tele_all))
    Text(stringResource(R.string.perm_battery), style = MaterialTheme.typography.bodySmall)
    ChoiceChips(teleOptions, teleBase) { apply(base = it) }
    Text(stringResource(R.string.perm_location), style = MaterialTheme.typography.bodySmall)
    ChoiceChips(teleOptions, teleLoc) { apply(loc = it) }
    Text(stringResource(R.string.perm_environment), style = MaterialTheme.typography.bodySmall)
    ChoiceChips(teleOptions, teleEnv) { apply(env = it) }

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pol_hash_width), style = MaterialTheme.typography.labelLarge)
    HintText(stringResource(R.string.pol_hash_hint))
    // The active width is radio truth (DEVICE_INFO), not local state.
    val deviceInfo by vm.deviceInfo.collectAsState()
    // Options come from PathHashMode, not a literal list: this offered a
    // "4 B" chip for mode 3, which the radio refuses with
    // ERR_CODE_ILLEGAL_ARG, so tapping it silently did nothing.
    val pathMode = PathHashMode.modeFor(deviceInfo?.pathHashByteWidth ?: 1)
    ChoiceChips(PathHashMode.LABELS, pathMode) {
        vm.setPathHashMode(it)
    }

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pol_scope), style = MaterialTheme.typography.labelLarge)
    ExpandableHint(stringResource(R.string.pol_scope_hint)) {
        Text(
            stringResource(R.string.pol_scope_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val radioDefault by vm.radioDefaultScope.collectAsState()
    io.github.thatsfguy.meshcore.presentation.radioDefaultScopeLine(radioDefault)?.let { HintText(it) }
    val currentRegion by vm.floodScopeRegion.collectAsState()
    var region by remember(currentRegion) { mutableStateOf(currentRegion ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = region,
            onValueChange = { region = it },
            label = { Text(stringResource(R.string.reg_name)) },
            singleLine = true,
            isError = region.isNotBlank() && !Regions.isValid(region),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = { vm.setFloodScope(region.trim()) },
            enabled = Regions.isValid(region),
        ) { Text(stringResource(R.string.set)) }
        FilledTonalButton(onClick = {
            region = ""
            vm.setFloodScope(null)
        }) { Text(stringResource(R.string.clear)) }
    }

    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.backup_regions), style = MaterialTheme.typography.labelLarge)
    RegionsSection(vm)
}

@Composable
internal fun AutoAddSection(vm: MeshCoreViewModel) {
    val flags by vm.autoAddFlags.collectAsState()
    val current = flags
    if (current == null) {
        HintText(stringResource(R.string.auto_connect))
        return
    }
    HintText(stringResource(R.string.auto_hint))

    fun toggle(bit: Int, on: Boolean) {
        vm.setAutoAddConfig(if (on) current or bit else current and bit.inv())
    }
    SettingRow(stringResource(R.string.auto_companions), current and Codes.AUTO_ADD_CHAT != 0) {
        toggle(Codes.AUTO_ADD_CHAT, it)
    }
    SettingRow(stringResource(R.string.kind_repeaters), current and Codes.AUTO_ADD_REPEATER != 0) {
        toggle(Codes.AUTO_ADD_REPEATER, it)
    }
    // With the radio adding every repeater, there is nothing to choose
    // between, so these only appear while it is not. They are the app's
    // own rules — the radio has no notion of which repeaters are useful.
    if (current and Codes.AUTO_ADD_REPEATER == 0) {
        var relaying by remember { mutableStateOf(vm.prefs.autoAddRelayingRepeaters) }
        var direct by remember { mutableStateOf(vm.prefs.autoAddDirectRepeaters) }
        Column(Modifier.padding(start = 16.dp)) {
            HintText(stringResource(R.string.auto_app_adds))
            SettingRow(stringResource(R.string.auto_relaying), relaying) {
                relaying = it
                vm.setRepeaterRule(relaysMyTraffic = it)
            }
            SettingRow(stringResource(R.string.auto_direct), direct) {
                direct = it
                vm.setRepeaterRule(heardDirect = it)
            }
            ExpandableHint(stringResource(R.string.auto_relayed_hint)) {
                Text(
                    stringResource(R.string.auto_relayed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    SettingRow(stringResource(R.string.auto_rooms), current and Codes.AUTO_ADD_ROOM != 0) {
        toggle(Codes.AUTO_ADD_ROOM, it)
    }
    SettingRow(stringResource(R.string.kind_sensors), current and Codes.AUTO_ADD_SENSOR != 0) {
        toggle(Codes.AUTO_ADD_SENSOR, it)
    }
    SettingRow(stringResource(R.string.auto_overwrite), current and Codes.AUTO_ADD_OVERWRITE_OLDEST != 0) {
        toggle(Codes.AUTO_ADD_OVERWRITE_OLDEST, it)
    }
    // The radio does this, not the app (BaseChatMesh.cpp,
    // allocateContactSlot skips flags & 0x01) — but the toggle alone
    // reads as though a favourite could be the one that goes.
    HintText(
        stringResource(R.string.auto_overwrite_hint),
    )
}

@Composable
internal fun CustomVarsSection(vm: MeshCoreViewModel) {
    val vars by vm.customVars.collectAsState()
    val engineState by vm.engineState.collectAsState()
    if (engineState != EngineState.Ready) {
        HintText(stringResource(R.string.vars_connect))
        return
    }

    // GPS is the variable users actually flip — give it a switch.
    if (vars.containsKey("gps")) {
        SettingRow(stringResource(R.string.vars_gps), vars["gps"] == "1") {
            vm.setCustomVar("gps", if (it) "1" else "0")
        }
    }
    if (vars.isEmpty()) {
        HintText(stringResource(R.string.vars_none))
    } else {
        for ((k, v) in vars) {
            Text("$k: $v", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(4.dp))
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = key, onValueChange = { key = it },
            label = { Text(stringResource(R.string.vars_variable)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FormField(
            value = value, onValueChange = { value = it },
            label = { Text(stringResource(R.string.vars_value)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick = {
                vm.setCustomVar(key.trim(), value.trim())
                key = ""; value = ""
            },
            enabled = key.isNotBlank(),
        ) { Text(stringResource(R.string.set)) }
    }
}

@Composable
internal fun ChannelsSection(vm: MeshCoreViewModel, onEdit: (ChannelEntity) -> Unit) {
    val channels by vm.dbChannels.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (channels.isEmpty()) {
        HintText(stringResource(R.string.hub_channels_none))
    }
    for (ch in channels) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ch.idx}: ${ch.name.ifBlank { "–" }}", Modifier.weight(1f))
            FilledTonalButton(onClick = { onEdit(ch) }) { Text(stringResource(R.string.set_edit)) }
        }
    }
    ButtonFlowRow {
        FilledTonalButton(onClick = { showAdd = true }) { Text(stringResource(R.string.chats_add_channel)) }
    }

    if (showAdd) {
        ChannelAddSheet(vm, onDismiss = { showAdd = false })
    }
}

// `AppSection` used to be all six of the following in one accordion:
// theme, storage-encryption status, map tiles, notifications and their
// two sub-switches, the diagnostics toggle AND its log viewer, backup,
// blocking and retention. A screen inside a section inside a screen.
// They are separate screens now (LESSONS §13).

@Composable
internal fun AppearanceSection(vm: MeshCoreViewModel) {
    var theme by remember { mutableStateOf(vm.prefs.theme) }
    ButtonFlowRow {
        for (option in listOf("system", "light", "dark")) {
            FilledTonalButton(
                onClick = {
                    theme = option
                    vm.prefs.theme = option
                },
                enabled = theme != option,
            ) {
                Text(
                    stringResource(
                        when (option) {
                            "light" -> R.string.theme_light
                            "dark" -> R.string.theme_dark
                            else -> R.string.theme_system
                        },
                    ),
                )
            }
        }
    }
    HintText(stringResource(R.string.set_theme_hint))

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.set_units), style = MaterialTheme.typography.labelLarge)
    var units by remember { mutableStateOf(vm.prefs.units) }
    val resolved by vm.unitSystem.collectAsState()
    ButtonFlowRow {
        for ((value, label) in listOf(
            UnitSystem.FOLLOW_SYSTEM to stringResource(R.string.set_units_system),
            UnitSystem.METRIC to stringResource(R.string.set_units_metric),
            UnitSystem.IMPERIAL to stringResource(R.string.set_units_imperial),
        )) {
            FilledTonalButton(
                onClick = {
                    units = value
                    vm.prefs.units = value
                },
                enabled = units != value,
            ) { Text(label) }
        }
    }
    // Says what is in force, not just what was chosen: "System" on its
    // own does not tell you which way it went, and that is the only
    // thing the reader actually wants to know.
    HintText(stringResource(R.string.set_units_hint, stringResource(if (resolved == UnitSystem.Imperial) R.string.set_units_imperial else R.string.set_units_metric)))
    ExpandableHint(stringResource(R.string.set_units_scope)) {
        Text(
            stringResource(R.string.set_units_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PrivacySection(vm: MeshCoreViewModel) {
    // Storage encryption status — if the Keystore failed, the user must
    // know the database is NOT encrypted rather than assume it is. This
    // is also surfaced on the hub tile, because it is the one thing here
    // nobody should have to open a page to discover.
    val dbWarning = io.alvaroedo.meshconnect.storage.DatabaseKey
        .encryptionUnavailableReason
    if (dbWarning == null) {
        HintText(stringResource(R.string.set_db_encrypted))
    } else {
        Text(
            "⚠ $dbWarning",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(12.dp))
    var tiles by remember { mutableStateOf(vm.prefs.mapTilesEnabled) }
    SettingRow(stringResource(R.string.map_load_tiles), tiles) {
        tiles = it
        vm.prefs.mapTilesEnabled = it
    }
    ExpandableHint(stringResource(R.string.set_tiles_hint)) {
        Text(
            stringResource(R.string.set_tiles_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun NotificationsSection(vm: MeshCoreViewModel) {
    var notifications by remember { mutableStateOf(vm.prefs.notificationsEnabled) }
    SettingRow(stringResource(R.string.set_notif_messages), notifications) {
        notifications = it
        vm.prefs.notificationsEnabled = it
    }
    // Finer control (PARITY §3). Per-thread mutes live on the channel
    // and contact sheets; these are the per-kind switches above them.
    if (notifications) {
        var notifyDirect by remember { mutableStateOf(vm.prefs.notifyDirect) }
        var notifyChannels by remember { mutableStateOf(vm.prefs.notifyChannels) }
        SettingRow("  · " + stringResource(R.string.set_notif_direct), notifyDirect) {
            notifyDirect = it
            vm.prefs.notifyDirect = it
        }
        SettingRow("  · " + stringResource(R.string.set_notif_channels), notifyChannels) {
            notifyChannels = it
            vm.prefs.notifyChannels = it
        }
        ExpandableHint(stringResource(R.string.set_notif_mute_hint)) {
            Text(
                stringResource(R.string.set_notif_mute_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Not under the message switch: it is about the radio, not messages.
    var lowBattery by remember { mutableStateOf(vm.prefs.notifyLowBattery) }
    SettingRow(stringResource(R.string.set_notif_low_battery), lowBattery) {
        lowBattery = it
        vm.prefs.notifyLowBattery = it
    }
    Text(
        stringResource(R.string.set_notif_low_battery_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DiagnosticsSection(vm: MeshCoreViewModel) {
    var diagnostics by remember { mutableStateOf(vm.prefs.diagnosticsEnabled) }
    SettingRow(stringResource(R.string.set_diag_log), diagnostics) {
        diagnostics = it
        vm.prefs.diagnosticsEnabled = it
    }
    HintText(stringResource(R.string.set_diag_redacted))
    if (diagnostics) {
        DiagnosticsViewer(vm)
    }
    Spacer(Modifier.height(16.dp))
    ChannelDatagramTool(vm)
}

/**
 * Send a binary datagram to a channel — a developer tool, and labelled
 * as one.
 *
 * This app is a chat client and has no use for datagrams of its own.
 * What it now has is the ability to carry them, and the only honest way
 * to show that works is to let someone send one and watch another
 * client receive it. Inbound datagrams are written to the diagnostics
 * log rather than rendered: the payload belongs to whichever
 * application owns its data type, and drawing another app's bytes as
 * text is the mistake PARITY §12 exists to prevent.
 */
@Composable
private fun ChannelDatagramTool(vm: MeshCoreViewModel) {
    val channels by vm.dbChannels.collectAsState()
    val connected by vm.engineState.collectAsState()
    var channelIdx by remember { mutableStateOf("0") }
    var dataType by remember { mutableStateOf("FF00") }
    var payloadHex by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current

    // Spaced: filled fields stacked with no gap read as one overlapping block.
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.dg_title), style = MaterialTheme.typography.labelLarge)
        ExpandableHint(stringResource(R.string.dg_hint)) {
            Text(
                stringResource(R.string.dg_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FormField(
                value = channelIdx,
                onValueChange = { channelIdx = it.filter(Char::isDigit).take(1) },
                label = { Text(stringResource(R.string.dg_channel)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FormField(
                value = dataType,
                onValueChange = { dataType = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }.take(4) },
                label = { Text(stringResource(R.string.dg_type)) },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
            )
        }
        FormField(
            value = payloadHex,
            onValueChange = { payloadHex = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" } },
            label = { Text(stringResource(R.string.dg_payload)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val idx = channelIdx.toIntOrNull()
        val type = dataType.toIntOrNull(16)
        val named = channels.firstOrNull { it.idx == idx }?.name?.takeIf { it.isNotBlank() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    busy = true
                    note = null
                    scope.launch {
                        val bytes = io.github.thatsfguy.meshcore.util.hexToBytesOrNull(payloadHex)
                        note = when {
                            bytes == null -> ctx.getString(R.string.dg_odd)
                            else -> {
                                val ok = vm.sendChannelDatagram(idx ?: -1, type ?: 0, bytes)
                                if (ok) {
                                    ctx.getString(R.string.dg_sent, bytes.size, idx ?: -1)
                                } else {
                                    ctx.getString(R.string.dg_refused)
                                }
                            }
                        }
                        busy = false
                    }
                },
                enabled = !busy && connected == EngineState.Ready &&
                    idx != null && type != null && payloadHex.isNotBlank(),
            ) { Text(stringResource(if (busy) R.string.dg_sending else R.string.dg_send)) }
            if (named != null) {
                Spacer(Modifier.width(8.dp))
                Text("→ $named", style = MaterialTheme.typography.bodySmall)
            }
        }
        note?.let { HintText(it) }
    }
}

// ----------------------------------------------------------------------
// Shared pieces (also used by AddNodeSheet)
// ----------------------------------------------------------------------

@Composable
internal fun SavedNodesList(vm: MeshCoreViewModel) {
    var refresh by remember { mutableIntStateOf(0) }
    val nodes = remember(refresh) { vm.prefs.savedNodes() }
    if (nodes.isEmpty()) return
    Column {
        Text(
            stringResource(R.string.set_saved_nodes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        for (node in nodes) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(node.name ?: node.address, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${node.kind.uppercase()} · ${node.address}${node.port?.let { ":$it" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { vm.connectSaved(node) }) { Text(stringResource(R.string.set_connect)) }
                // "Remove", not "Forget". Android's Bluetooth settings has
                // a "Forget device" that drops the PAIRING; this only drops
                // the app's list entry. Sharing the word sent someone
                // looking for the first one to this button, and their new
                // BLE PIN went untested because the bond was still in place.
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    vm.forgetNode(node)
                    refresh++
                }) { Text(stringResource(R.string.node_remove_confirm), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
internal fun DiagnosticsViewer(vm: MeshCoreViewModel) {
    val lines by vm.diagnosticsLines.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .padding(top = 4.dp),
    ) {
        if (lines.isEmpty()) {
            HintText(stringResource(R.string.set_log_empty))
        }
        LazyColumn {
            items(lines.size) { i ->
                Text(
                    lines[lines.size - 1 - i],
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** The stern one-time warning SCOPE.md requires before TCP can be enabled. */
@Composable
fun TcpSternWarningDialog(onAccept: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.tcp_title)) },
        text = {
            Text(stringResource(R.string.tcp_body))
        },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.tcp_accept), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { androidx.compose.material3.Button(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Kept for its callers; the picker itself is [RadioPicker]. */
@Composable
fun AddNodeSheet(vm: MeshCoreViewModel, tcpEnabled: Boolean, onDismiss: () -> Unit) {
    RadioPickerSheet(vm, onDismiss)
}

/**
 * Human-readable clock drift. Sign is stated in words rather than as a
 * bare "+"/"-", which nobody reads correctly under a status line.
 */
internal data class DriftWords(
    val none: String = "none — clocks agree",
    val ahead: String = "%s (radio ahead)",
    val behind: String = "%s (radio behind)",
    val days: (Long) -> String = { "$it days" },
)

@Composable
internal fun driftWords(): DriftWords {
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    return DriftWords(
        none = stringResource(R.string.drift_none),
        ahead = stringResource(R.string.drift_ahead),
        behind = stringResource(R.string.drift_behind),
        days = { res.getQuantityString(R.plurals.drift_days, it.toInt(), it.toInt()) },
    )
}

internal fun formatDrift(seconds: Long, words: DriftWords = DriftWords()): String {
    val magnitude = kotlin.math.abs(seconds)
    val amount = when {
        magnitude == 0L -> return words.none
        magnitude < 60 -> "$magnitude s"
        magnitude < 3600 -> "${magnitude / 60} min ${magnitude % 60} s"
        magnitude < 86_400 -> "${magnitude / 3600} h ${(magnitude % 3600) / 60} min"
        else -> words.days(magnitude / 86_400)
    }
    return (if (seconds > 0) words.ahead else words.behind).format(amount)
}

/**
 * This radio's settings as a `meshcore://radio/set` code, for someone
 * joining the mesh. Values come from the radio, not the edit fields, so
 * an unsaved edit is never handed out. TX power is not in it, on purpose
 * (MESHCORE_PROTOCOL §QR): that is the scanner's legal limit, not the
 * mesh's.
 */
@Composable
private fun RadioShareDialog(uri: String?, region: String?, onDismiss: () -> Unit) {
    val qr = remember(uri) { uri?.let { runCatching { io.alvaroedo.meshconnect.platform.Qr.encode(it) }.getOrNull() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rshare_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr == null) {
                    Text(
                        stringResource(R.string.rshare_no_width),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    androidx.compose.foundation.Image(
                        qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.rshare_title),
                        modifier = Modifier.size(280.dp),
                    )
                    Text(
                        if (region != null) {
                            stringResource(R.string.rshare_region, region)
                        } else {
                            stringResource(R.string.rshare_no_region)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
        dismissButton = { if (uri != null) QrLinkActions(uri) },
    )
}
