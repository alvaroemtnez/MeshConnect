package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import io.github.thatsfguy.meshcore.protocol.Regions
import io.github.thatsfguy.meshcore.presentation.ScannedSettingsPlan
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import io.github.thatsfguy.meshcore.presentation.RegionAdmin
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.protocol.CliReplies
import io.github.thatsfguy.meshcore.protocol.CliFormFields
import io.github.thatsfguy.meshcore.protocol.CliIds
import io.github.thatsfguy.meshcore.protocol.CliValues
import io.github.thatsfguy.meshcore.protocol.NodeRole
import io.github.thatsfguy.meshcore.protocol.PathHashMode
import io.github.thatsfguy.meshcore.protocol.RadioUnits
import kotlinx.coroutines.launch

/**
 * Remote (repeater/room) settings — the SAME surface as the local
 * Settings tab: collapsible [ExpandableSection]s that query their
 * values on expand ([QueryOnExpand]) and share every row/field/chip
 * component with it ([SettingsComponents]). Only the transport
 * differs: local settings speak companion frames, these speak the
 * text CLI (`get x` → `> value`, `set x v`).
 *
 * Save sends `set` commands only for fields you changed; switches
 * apply immediately; passwords are write-only.
 */
@Composable
fun RemoteSettingsForm(
    vm: MeshCoreViewModel,
    keyHex: String,
    contact: ContactEntity?,
    role: NodeRole,
    isAdmin: Boolean = true,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val engineState by vm.engineState.collectAsState()
    val isReady = engineState == EngineState.Ready

    val values = remember { mutableStateMapOf<String, String>() }
    val dirty = remember { mutableStateMapOf<String, Boolean>() }
    var confirmAction by remember { mutableStateOf<Pair<String, String>?>(null) }
    var presetSheet by remember { mutableStateOf(false) }

    /**
     * A settings QR scanned while administering THIS node.
     *
     * Deliberately not routed through the app-wide scan handler: that
     * one applies a code to the radio in your hand, which is the right
     * default from Chats or Nodes but exactly wrong here, where the
     * whole screen is about a node across the mesh. Scanning from the
     * repeater's own Radio panel means the repeater.
     */
    var scannedConfig by remember {
        mutableStateOf<io.github.thatsfguy.meshcore.protocol.ShareUri.Decoded.RadioConfig?>(null)
    }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        // Same decoder, same refusals — a code is no more trustworthy
        // for being scanned on an admin screen.
        //
        // Only a SETTINGS code is claimed here, because only that one
        // means something different on this screen than elsewhere: it
        // targets the node being administered rather than the radio in
        // your hand. Everything else falls through to the app-wide
        // handler, so scanning a contact card at this button still adds
        // the contact instead of complaining about the screen you chose
        // — which is the rule a test enforces after a scanner wired to
        // one decoder answered "Invalid community code" for a perfectly
        // good repeater card.
        when (val d = io.github.thatsfguy.meshcore.protocol.ShareUri.decode(text)) {
            is io.github.thatsfguy.meshcore.protocol.ShareUri.Decoded.RadioConfig ->
                scannedConfig = d
            is io.github.thatsfguy.meshcore.protocol.ShareUri.Decoded.UnsupportedVersion ->
                scanError = ctx.getString(R.string.scan_newer_app, d.version.toString())
            else ->
                vm.importScannedCode(text)
        }
    }

    /**
     * Set to the name of what was just saved when the node needs a
     * restart before it takes effect. `set radio` writes prefs only —
     * the firmware literally replies "OK - reboot to apply" — so
     * without this the operator gets a success and no change, which
     * reads as the feature being broken.
     */
    var pendingReboot by remember { mutableStateOf<String?>(null) }

    // Seed from the cached contact record so the form is never empty.
    remember(contact?.keyHex) {
        if (contact != null) {
            values.putIfAbsent(CliIds.NAME, contact.name)
            contact.latitude?.let { values.putIfAbsent(CliIds.LAT, it.toString()) }
            contact.longitude?.let { values.putIfAbsent(CliIds.LON, it.toString()) }
        }
        true
    }

    fun edit(id: String, v: String) {
        values[id] = v
        dirty[id] = true
    }

    /** Fetch [ids] one at a time into the form; clears their dirty flags. */
    suspend fun fetch(ids: List<String>): Int {
        var ok = 0
        for (id in ids) {
            val value = vm.cliQuery(keyHex, "get $id")?.let { CliReplies.extractGetValue(it) }
                ?: continue
            if (id == CliIds.RADIO) {
                CliReplies.parseRadioCsv(value)?.let { r ->
                    // The node stores frequency as a 32-bit float and
                    // prints it at full precision, so 910.525 comes back
                    // as 910.5250244 — the nearest float32, rendered
                    // honestly. tidyDecimal shortens it ONLY when the
                    // shorter form is the same number to the radio, so
                    // anything genuinely finer than a kHz survives.
                    values[CliFormFields.RADIO_FREQ] = RadioUnits.tidyDecimal(r.freqMhz.toString())
                    values[CliFormFields.RADIO_BW] = RadioUnits.tidyDecimal(r.bwKhz.toString())
                    values[CliFormFields.RADIO_SF] = r.sf.toString()
                    values[CliFormFields.RADIO_CR] = r.cr.toString()
                    CliFormFields.RADIO_FIELDS
                        .forEach { dirty[it] = false }
                    ok++
                }
            } else {
                // Firmware encodes newlines in owner.info as '|'.
                values[id] =
                    if (id == CliIds.OWNER_INFO) CliValues.decodeOwnerInfo(value).replace('\n', ' ')
                    else value
                dirty[id] = false
                ok++
            }
        }
        return ok
    }

    suspend fun send(commands: List<String>): Int =
        commands.count { vm.cliQuery(keyHex, it) != null }

    /** Immediate-apply switch (matches the local Settings tab's switches). */
    fun applySwitch(id: String, on: Boolean, onText: String = "on", offText: String = "off") {
        if (!isAdmin) return
        val word = if (on) onText else offText
        values[id] = word
        scope.launch { vm.cliQuery(keyHex, "set $id $word") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        if (!isReady) {
            HintText(stringResource(R.string.rsf_connect))
        }
        if (!isAdmin) {
            HintText(
                // Not "values can be fetched": every `get` here is CLI, and
                // the node runs CLI text from admins only (MyMesh.cpp:689).
                stringResource(R.string.rsf_not_admin),
            )
        }

        RemoteSection(
            title = stringResource(R.string.rsf_basic),
            isReady = isReady,
            fetchIds = listOf(CliIds.NAME, CliIds.OWNER_INFO),
            fetch = ::fetch,
            saveEnabled = isAdmin && (dirty[CliIds.NAME] == true || dirty[CliIds.OWNER_INFO] == true),
            buildSaves = {
                buildList {
                    values[CliIds.NAME]?.trim()?.takeIf { it.isNotEmpty() && dirty[CliIds.NAME] == true }
                        ?.let { add("set name $it") }
                    if (dirty[CliIds.OWNER_INFO] == true) {
                        add("set owner.info ${values[CliIds.OWNER_INFO].orEmpty().trim()}")
                    }
                }
            },
            send = ::send,
            clearDirty = { listOf(CliIds.NAME, CliIds.OWNER_INFO).forEach { dirty[it] = false } },
        ) {
            SettingsTextField(stringResource(R.string.rr_name), values[CliIds.NAME].orEmpty()) { edit(CliIds.NAME, it) }
            SettingsTextField(stringResource(R.string.rsf_owner), values[CliIds.OWNER_INFO].orEmpty()) { edit(CliIds.OWNER_INFO, it) }
        }

        RemoteSection(
            title = stringResource(R.string.hub_radio),
            isReady = isReady,
            fetchIds = listOf(CliIds.RADIO, CliIds.TX, CliIds.PATH_HASH_MODE),
            fetch = ::fetch,
            saveEnabled = isAdmin && listOf(
                CliFormFields.RADIO_FREQ,
                CliFormFields.RADIO_BW,
                CliFormFields.RADIO_SF,
                CliFormFields.RADIO_CR,
                CliIds.TX,
            )
                .any { dirty[it] == true },
            buildSaves = {
                buildList {
                    val f = values[CliFormFields.RADIO_FREQ]?.trim()?.toDoubleOrNull()
                    val bw = values[CliFormFields.RADIO_BW]?.trim()?.toDoubleOrNull()
                    val sf = values[CliFormFields.RADIO_SF]?.trim()?.toIntOrNull()
                    val cr = values[CliFormFields.RADIO_CR]?.trim()?.toIntOrNull()
                    val radioEdited = CliFormFields.RADIO_FIELDS
                        .any { dirty[it] == true }
                    if (radioEdited && f != null && bw != null && sf in 5..12 && cr in 5..8) {
                        add("set radio ${CliReplies.RadioCsv(f, bw, sf!!, cr!!).toCsv()}")
                        // Same as the preset path: the node writes these
                        // and keeps running on the old ones until it
                        // restarts. Typing them by hand is no different.
                        pendingReboot = ctx.getString(R.string.rsf_saved_radio)
                    }
                    values[CliIds.TX]?.trim()?.toIntOrNull()?.let {
                        if (dirty[CliIds.TX] == true) add("set tx $it")
                    }
                }
            },
            send = ::send,
            clearDirty = {
                listOf(
                    CliFormFields.RADIO_FREQ,
                    CliFormFields.RADIO_BW,
                    CliFormFields.RADIO_SF,
                    CliFormFields.RADIO_CR,
                    CliIds.TX,
                )
                    .forEach { dirty[it] = false }
            },
        ) {
            HintText(stringResource(R.string.radio_must_match))
            if (isAdmin) {
                ButtonFlowRow {
                    TextButton(
                        enabled = isReady,
                        onClick = { presetSheet = true },
                    ) { Text(stringResource(R.string.setup_pick_preset)) }
                    TextButton(
                        enabled = isReady,
                        onClick = { scanLauncher.launch(meshScanOptions(ctx.getString(R.string.radio_scan_prompt))) },
                    ) { Text(stringResource(R.string.radio_scan)) }
                }
            }
            Row {
                SettingsTextField(
                    stringResource(R.string.radio_freq), values[CliFormFields.RADIO_FREQ].orEmpty(), Modifier.weight(1.2f),
                ) { edit(CliFormFields.RADIO_FREQ, it) }
                Spacer(Modifier.width(6.dp))
                SettingsTextField(stringResource(R.string.radio_bw), values[CliFormFields.RADIO_BW].orEmpty(), Modifier.weight(1f)) {
                    edit(CliFormFields.RADIO_BW, it)
                }
            }
            Row {
                SettingsTextField("SF", values[CliFormFields.RADIO_SF].orEmpty(), Modifier.weight(1f)) {
                    edit(CliFormFields.RADIO_SF, it)
                }
                Spacer(Modifier.width(6.dp))
                SettingsTextField("CR", values[CliFormFields.RADIO_CR].orEmpty(), Modifier.weight(1f)) {
                    edit(CliFormFields.RADIO_CR, it)
                }
                Spacer(Modifier.width(6.dp))
                SettingsTextField(stringResource(R.string.rsf_tx), values[CliIds.TX].orEmpty(), Modifier.weight(1f)) {
                    edit(CliIds.TX, it)
                }
            }

            // Applies on tap rather than on Save, like the switches: the
            // chips ARE the committed value, and a chip that shows a
            // selection the node has not been told about is a lie.
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pol_hash_width), style = MaterialTheme.typography.labelLarge)
            HintText(stringResource(R.string.rsf_hash_hint))
            // Blank until the node answers `get path.hash.mode` — the
            // width is mesh truth and guessing a default would show a
            // selection nobody chose. -1 selects nothing.
            val hashMode = values[CliIds.PATH_HASH_MODE]?.trim()?.toIntOrNull()
                ?.takeIf { PathHashMode.isValid(it) }
            ChoiceChips(
                PathHashMode.LABELS,
                selected = hashMode ?: -1,
                enabled = isAdmin && isReady,
            ) { mode ->
                values[CliIds.PATH_HASH_MODE] = mode.toString()
                scope.launch { vm.cliQuery(keyHex, "set ${CliIds.PATH_HASH_MODE} $mode") }
            }
            if (hashMode == null) {
                HintText(stringResource(R.string.rsf_hash_fetch))
            }
        }

        RemoteSection(
            title = stringResource(R.string.rsf_position),
            isReady = isReady,
            fetchIds = listOf(CliIds.LAT, CliIds.LON),
            fetch = ::fetch,
            saveEnabled = isAdmin && (dirty[CliIds.LAT] == true || dirty[CliIds.LON] == true),
            buildSaves = {
                buildList {
                    values[CliIds.LAT]?.trim()?.toDoubleOrNull()?.takeIf {
                        dirty[CliIds.LAT] == true && it in -90.0..90.0
                    }?.let { add("set lat $it") }
                    values[CliIds.LON]?.trim()?.toDoubleOrNull()?.takeIf {
                        dirty[CliIds.LON] == true && it in -180.0..180.0
                    }?.let { add("set lon $it") }
                }
            },
            send = ::send,
            clearDirty = { listOf(CliIds.LAT, CliIds.LON).forEach { dirty[it] = false } },
        ) {
            Row {
                SettingsTextField(stringResource(R.string.rsf_lat), values[CliIds.LAT].orEmpty(), Modifier.weight(1f)) {
                    edit(CliIds.LAT, it)
                }
                Spacer(Modifier.width(6.dp))
                SettingsTextField(stringResource(R.string.rsf_lon), values[CliIds.LON].orEmpty(), Modifier.weight(1f)) {
                    edit(CliIds.LON, it)
                }
            }
        }

        RemoteSection(
            title = stringResource(R.string.rsf_adverts),
            isReady = isReady,
            fetchIds = listOf(CliIds.ADVERT_INTERVAL, CliIds.FLOOD_ADVERT_INTERVAL),
            fetch = ::fetch,
            saveEnabled = isAdmin && (
                dirty[CliIds.ADVERT_INTERVAL] == true || dirty[CliIds.FLOOD_ADVERT_INTERVAL] == true
                ),
            buildSaves = {
                buildList {
                    for (id in listOf(CliIds.ADVERT_INTERVAL, CliIds.FLOOD_ADVERT_INTERVAL)) {
                        values[id]?.trim()?.toIntOrNull()?.let {
                            if (dirty[id] == true) add("set $id $it")
                        }
                    }
                }
            },
            send = ::send,
            clearDirty = {
                listOf(
                    CliIds.ADVERT_INTERVAL,
                    CliIds.FLOOD_ADVERT_INTERVAL,
                ).forEach { dirty[it] = false }
            },
            extraActions = { setStatus ->
                TextButton(onClick = {
                    scope.launch {
                        vm.cliQuery(keyHex, CliIds.ADVERT)
                        setStatus(ctx.getString(R.string.rsf_advert_sent))
                    }
                }) { Text(stringResource(R.string.rsf_advert_now)) }
            },
        ) {
            Row {
                SettingsTextField(
                    stringResource(R.string.rsf_zero_hop), values[CliIds.ADVERT_INTERVAL].orEmpty(), Modifier.weight(1f),
                ) { edit(CliIds.ADVERT_INTERVAL, it) }
                Spacer(Modifier.width(6.dp))
                SettingsTextField(
                    stringResource(R.string.rsf_flood_hours), values[CliIds.FLOOD_ADVERT_INTERVAL].orEmpty(), Modifier.weight(1f),
                ) { edit(CliIds.FLOOD_ADVERT_INTERVAL, it) }
            }
        }

        // Every role forwards unless `repeat off` (allowPacketForward in
        // simple_room_server and simple_sensor alike), and `repeat`,
        // `flood.max` and the delays are CommonCLI settings — so a room
        // server that doubles as a repeater is configured here too.
        if (role != NodeRole.Companion) {
            RemoteSection(
                title = stringResource(R.string.rsf_forwarding),
                isReady = isReady,
                fetchIds = listOf(
                    CliIds.REPEAT,
                    CliIds.FLOOD_MAX,
                    CliIds.RXDELAY,
                    CliIds.TXDELAY,
                    CliIds.DIRECT_TXDELAY,
                ),
                fetch = ::fetch,
                saveEnabled = isAdmin && listOf(
                    CliIds.FLOOD_MAX,
                    CliIds.RXDELAY,
                    CliIds.TXDELAY,
                    CliIds.DIRECT_TXDELAY,
                )
                    .any { dirty[it] == true },
                buildSaves = {
                    buildList {
                        for (id in listOf(
                            CliIds.FLOOD_MAX,
                            CliIds.RXDELAY,
                            CliIds.TXDELAY,
                            CliIds.DIRECT_TXDELAY,
                        )) {
                            values[id]?.trim()?.takeIf { it.isNotEmpty() && dirty[id] == true }
                                ?.let { add("set $id $it") }
                        }
                    }
                },
                send = ::send,
                clearDirty = {
                    listOf(CliIds.FLOOD_MAX, CliIds.RXDELAY, CliIds.TXDELAY, CliIds.DIRECT_TXDELAY)
                        .forEach { dirty[it] = false }
                },
            ) {
                SettingRow(
                    stringResource(R.string.rsf_repeat),
                    CliReplies.isTruthy(values[CliIds.REPEAT].orEmpty()),
                ) { applySwitch(CliIds.REPEAT, it) }
                Row {
                    SettingsTextField(
                        stringResource(R.string.rsf_flood_max), values[CliIds.FLOOD_MAX].orEmpty(), Modifier.weight(1f),
                    ) { edit(CliIds.FLOOD_MAX, it) }
                    Spacer(Modifier.width(6.dp))
                    SettingsTextField(stringResource(R.string.rsf_rx_delay), values[CliIds.RXDELAY].orEmpty(), Modifier.weight(1f)) {
                        edit(CliIds.RXDELAY, it)
                    }
                }
                Row {
                    SettingsTextField(stringResource(R.string.rsf_tx_delay), values[CliIds.TXDELAY].orEmpty(), Modifier.weight(1f)) {
                        edit(CliIds.TXDELAY, it)
                    }
                    Spacer(Modifier.width(6.dp))
                    SettingsTextField(
                        stringResource(R.string.rsf_direct_delay), values[CliIds.DIRECT_TXDELAY].orEmpty(), Modifier.weight(1f),
                    ) { edit(CliIds.DIRECT_TXDELAY, it) }
                }
            }
        }

        RemoteSection(
            title = stringResource(R.string.rsf_access),
            isReady = isReady,
            fetchIds = if (role == NodeRole.Room) listOf(CliIds.ALLOW_READ_ONLY) else emptyList(),
            fetch = ::fetch,
            saveEnabled = isAdmin && (
                !values[CliFormFields.PASSWORD_NEW].isNullOrBlank() ||
                    !values[CliFormFields.GUEST_PASSWORD_NEW].isNullOrBlank()
                ),
            buildSaves = {
                buildList {
                    values[CliFormFields.PASSWORD_NEW]?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { add("password $it") }
                    values[CliFormFields.GUEST_PASSWORD_NEW]?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { add("set guest.password $it") }
                }
            },
            send = ::send,
            clearDirty = {
                values.remove(CliFormFields.PASSWORD_NEW)
                values.remove(CliFormFields.GUEST_PASSWORD_NEW)
            },
        ) {
            if (role == NodeRole.Room) {
                SettingRow(
                    stringResource(R.string.rsf_allow_guest),
                    CliReplies.isTruthy(values[CliIds.ALLOW_READ_ONLY].orEmpty()),
                ) { applySwitch(CliIds.ALLOW_READ_ONLY, it) }
            }
            // Passwords are write-only — blank fields that only send
            // when filled.
            SettingsTextField(
                stringResource(R.string.rsf_new_admin_pw), values[CliFormFields.PASSWORD_NEW].orEmpty(), sensitive = true,
            ) { values[CliFormFields.PASSWORD_NEW] = it }
            SettingsTextField(
                stringResource(R.string.rsf_new_guest_pw), values[CliFormFields.GUEST_PASSWORD_NEW].orEmpty(), sensitive = true,
            ) { values[CliFormFields.GUEST_PASSWORD_NEW] = it }
            HintText(stringResource(R.string.rsf_pw_hint))
        }

        RemoteSection(
            title = stringResource(R.string.rsf_advanced),
            isReady = isReady,
            fetchIds = listOf(
                CliIds.RADIO_RXGAIN,
                CliIds.DUTYCYCLE,
                CliIds.MULTI_ACKS,
                CliIds.INT_THRESH,
                CliIds.LOOP_DETECT,
            ),
            fetch = ::fetch,
            saveEnabled = isAdmin && listOf(CliIds.DUTYCYCLE, CliIds.INT_THRESH).any { dirty[it] == true },
            buildSaves = {
                buildList {
                    // Coercions live in CliValues (unit-tested, positive
                    // and negative) because a bad one sends a malformed
                    // `set` to somebody's repeater.
                    if (dirty[CliIds.DUTYCYCLE] == true) {
                        CliValues.parsePercent(values[CliIds.DUTYCYCLE].orEmpty())
                            ?.let { add("set dutycycle $it") }
                    }
                    if (dirty[CliIds.INT_THRESH] == true) {
                        CliValues.parseInt(values[CliIds.INT_THRESH].orEmpty())
                            ?.let { add("set int.thresh $it") }
                    }
                }
            },
            send = ::send,
            clearDirty = {
                listOf(CliIds.DUTYCYCLE, CliIds.INT_THRESH).forEach { dirty[it] = false }
            },
        ) {
            SettingRow(
                stringResource(R.string.pol_multiacks),
                CliReplies.isTruthy(values[CliIds.MULTI_ACKS].orEmpty()),
            ) {
                applySwitch(
                    CliIds.MULTI_ACKS, it,
                    onText = CliValues.oneZero(true), offText = CliValues.oneZero(false),
                )
            }
            // RX gain is a BOOLEAN in firmware (LNA boost on/off), not a
            // numeric gain value — a text field here would send garbage.
            SettingRow(
                stringResource(R.string.rsf_rx_gain),
                CliReplies.isTruthy(values[CliIds.RADIO_RXGAIN].orEmpty()),
            ) { applySwitch(CliIds.RADIO_RXGAIN, it) }
            Row {
                SettingsTextField(
                    stringResource(R.string.rsf_duty), values[CliIds.DUTYCYCLE].orEmpty().trimEnd('%'), Modifier.weight(1f),
                ) { edit(CliIds.DUTYCYCLE, it) }
                Spacer(Modifier.width(6.dp))
                SettingsTextField(
                    stringResource(R.string.rsf_int_thresh), values[CliIds.INT_THRESH].orEmpty(), Modifier.weight(1f),
                ) { edit(CliIds.INT_THRESH, it) }
            }
            // loop.detect is an enumerated mode, not free text.
            Text(stringResource(R.string.rsf_loop), style = MaterialTheme.typography.labelMedium)
            val loopModes = CliValues.LOOP_DETECT_MODES
            // An unrecognised reply selects nothing rather than falsely
            // showing "off".
            val loopSelected = CliValues.parseLoopDetect(values[CliIds.LOOP_DETECT].orEmpty()) ?: -1
            ChoiceChips(
                loopModes.map { it.replaceFirstChar { c -> c.uppercase() } },
                loopSelected,
                enabled = isAdmin,
            ) { i ->
                values[CliIds.LOOP_DETECT] = loopModes[i]
                scope.launch { vm.cliQuery(keyHex, "set loop.detect ${loopModes[i]}") }
            }
            if (loopSelected < 0 && !values[CliIds.LOOP_DETECT].isNullOrBlank()) {
                HintText(stringResource(R.string.rsf_loop_unknown, values[CliIds.LOOP_DETECT].orEmpty()))
            }
        }

        ExpandableSection(stringResource(R.string.rsf_maintenance)) {
            HintText(stringResource(R.string.rsf_console_hint))
            ButtonFlowRow {
                for ((label, command) in buildList {
                    add(stringResource(R.string.rsf_m_clock) to CliIds.CLOCK)
                    add(stringResource(R.string.rsf_m_clock_sync) to "clock sync")
                    add(stringResource(R.string.rsf_m_version) to CliIds.VER)
                    add(stringResource(R.string.rsf_m_board) to CliIds.BOARD)
                    if (role == NodeRole.Repeater) add(stringResource(R.string.rs_neighbours) to CliIds.NEIGHBORS)
                    add(stringResource(R.string.rsf_m_acl) to "get acl")
                    add(stringResource(R.string.rsf_m_log_start) to "log start")
                    add(stringResource(R.string.rsf_m_log_stop) to "log stop")
                }) {
                    TextButton(onClick = { scope.launch { vm.cliQuery(keyHex, command) } }) {
                        Text(label)
                    }
                }
            }
            if (isAdmin) ButtonFlowRow {
                for ((label, command) in listOf(
                    stringResource(R.string.rsf_m_reboot) to CliIds.REBOOT,
                    stringResource(R.string.rsf_m_clear_stats) to "clear stats",
                    stringResource(R.string.rsf_m_log_erase) to "log erase",
                    stringResource(R.string.rsf_m_factory) to CliIds.ERASE,
                    stringResource(R.string.rsf_m_ota) to "start ota",
                )) {
                    TextButton(onClick = { confirmAction = label to command }) {
                        Text(label, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (presetSheet) {
        RadioPresetSheet(
            vm = vm,
            onDismiss = { presetSheet = false },
            targetName = contact?.name?.ifBlank { null } ?: keyHex.take(12),
            onApply = { preset ->
                scope.launch {
                    vm.cliQuery(keyHex, "set ${CliIds.TX} ${preset.txPowerDbm}")
                    vm.cliQuery(keyHex, "set ${CliIds.RADIO} ${preset.toRadioCsv()}")
                    // `set radio` over the CLI only writes prefs — the
                    // firmware answers "OK - reboot to apply" and keeps
                    // running on the old parameters until it restarts
                    // (CommonCLI.cpp:571). So the node is still on the
                    // air and still reachable, and re-reading it now
                    // would show the OLD values, which is why the form
                    // shows what was asked for instead.
                    values[CliFormFields.RADIO_FREQ] =
                        RadioUnits.khzToMhzText(preset.frequencyKhz)
                    values[CliFormFields.RADIO_BW] = RadioUnits.hzToKhzText(preset.bandwidthHz)
                    values[CliFormFields.RADIO_SF] = preset.spreadingFactor.toString()
                    values[CliFormFields.RADIO_CR] = preset.codingRate.toString()
                    values[CliIds.TX] = preset.txPowerDbm.toString()
                    CliFormFields.RADIO_FIELDS.forEach { dirty[it] = false }
                    dirty[CliIds.TX] = false
                    // Nothing has changed on air yet. Saying so — and
                    // offering the reboot — is the difference between a
                    // preset that works and one that looks like it did
                    // nothing.
                    pendingReboot = preset.name
                }
            },
        )
    }

    scanError?.let { message ->
        AlertDialog(
            onDismissRequest = { scanError = null },
            title = { Text(stringResource(R.string.scan_cant_use)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { scanError = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    scannedConfig?.let { config ->
        val node = contact?.name?.ifBlank { null } ?: keyHex.take(12)
        // The tree this code carries, or its lone region.
        val tree = config.regionChoices()
        // What the node holds NOW, read before anything is offered, so a
        // second scan of the same code sends nothing — and never re-opens
        // a region its operator denied (see ScannedSettingsPlan).
        var nodeNow by remember(config) { mutableStateOf<ScannedNodeState?>(null) }
        LaunchedEffect(config) {
            val radio = vm.cliQuery(keyHex, "get ${CliIds.RADIO}")
                ?.let { CliReplies.extractGetValue(it) }
                ?.let { CliReplies.parseRadioCsv(it) }
            val hash = vm.cliQuery(keyHex, "get ${CliIds.PATH_HASH_MODE}")
                ?.let { CliReplies.extractGetValue(it) }?.trim()?.toIntOrNull()
            val plan = if (tree.isEmpty()) {
                null
            } else {
                val current = Regions.parseRegionTree(vm.cliQuery(keyHex, Regions.tree()))
                val currentDefault = if (config.region != null) {
                    Regions.parseDefaultScope(vm.cliQuery(keyHex, Regions.default()))
                } else {
                    null
                }
                ScannedSettingsPlan.regions(tree, config.region, current, currentDefault)
            }
            nodeNow = ScannedNodeState(ScannedSettingsPlan.radioMatches(config, radio, hash), plan)
        }
        val read = nodeNow
        val changes = read?.plan as? ScannedSettingsPlan.RegionPlan.Changes
        // Ticked by the operator, never by default — as on the USB writer.
        var writeRegions by remember(config) { mutableStateOf(false) }
        val nothingToDo = read != null && read.radioSame && changes == null
        AlertDialog(
            onDismissRequest = { scannedConfig = null },
            title = { Text(stringResource(R.string.rsf_apply_q, node)) },
            text = {
                // Scrolls: the tree and the list of changes add a row each.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        config.name.ifBlank { stringResource(R.string.rsf_unnamed_mesh) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(config.summary(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    when {
                        read == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.rsf_checking, node), style = MaterialTheme.typography.bodySmall)
                        }
                        read.radioSame -> Text(
                            stringResource(R.string.rsf_radio_same, node),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> Text(
                            stringResource(R.string.rsf_remote_node, node),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scan_unsigned) + " " + stringResource(R.string.regulatory_caveat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (tree.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.backup_regions), style = MaterialTheme.typography.titleSmall)
                        config.regionHeadline()?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        // The whole tree, widest first, as the generator draws it.
                        tree.forEachIndexed { depth, name ->
                            Row(
                                Modifier.padding(start = (8 + 14 * depth).dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    (if (depth > 0) "└ " else "") + "#$name",
                                    fontFamily = FontFamily.Monospace,
                                )
                                if (name == config.region) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.rr_tag_default),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val small = MaterialTheme.typography.bodySmall
                        when (val plan = read?.plan) {
                            null -> if (read != null) {
                                Text(stringResource(R.string.rsf_regions_unwritable), style = small)
                            }
                            ScannedSettingsPlan.RegionPlan.AlreadyApplied -> Text(
                                config.region?.let { stringResource(R.string.rsf_regions_same_default, node, it) } ?: stringResource(R.string.rsf_regions_same, node),
                                style = small,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            is ScannedSettingsPlan.RegionPlan.Changes -> {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .toggleable(
                                            value = writeRegions,
                                            role = Role.Checkbox,
                                            onValueChange = { writeRegions = it },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = writeRegions, onCheckedChange = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.rsf_also_regions, node))
                                }
                                for (line in ScannedSettingsPlan.describe(plan)) {
                                    Text("• $line", style = small, modifier = Modifier.padding(start = 12.dp))
                                }
                                Text(
                                    stringResource(R.string.rsf_regions_note),
                                    style = small,
                                )
                            }
                            ScannedSettingsPlan.RegionPlan.Unreadable -> Text(
                                stringResource(R.string.rsf_regions_unreadable, node),
                                style = small,
                            )
                            ScannedSettingsPlan.RegionPlan.TooManyToRead -> Text(
                                stringResource(R.string.rsf_regions_too_many, node),
                                style = small,
                            )
                            ScannedSettingsPlan.RegionPlan.TooLong -> Text(
                                stringResource(R.string.rsf_regions_too_long),
                                style = small,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = read != null, onClick = {
                    val c = config
                    val state = read ?: return@TextButton
                    val regionCommands = changes?.commands?.takeIf { writeRegions }
                    scannedConfig = null
                    if (nothingToDo) return@TextButton
                    scope.launch {
                        if (!state.radioSame) {
                            // TX is untouched: it is not in the code, and it
                            // is a local legal limit rather than a property
                            // of the mesh.
                            val csv = RadioUnits.khzToMhzText(c.frequencyKhz) + "," +
                                RadioUnits.hzToKhzText(c.bandwidthHz) + "," +
                                c.spreadingFactor + "," + c.codingRate
                            vm.cliQuery(keyHex, "set ${CliIds.RADIO} $csv")
                            vm.cliQuery(keyHex, "set ${CliIds.PATH_HASH_MODE} ${c.pathHashMode}")
                            values[CliFormFields.RADIO_FREQ] =
                                RadioUnits.khzToMhzText(c.frequencyKhz)
                            values[CliFormFields.RADIO_BW] = RadioUnits.hzToKhzText(c.bandwidthHz)
                            values[CliFormFields.RADIO_SF] = c.spreadingFactor.toString()
                            values[CliFormFields.RADIO_CR] = c.codingRate.toString()
                            values[CliIds.PATH_HASH_MODE] = c.pathHashMode.toString()
                            CliFormFields.RADIO_FIELDS.forEach { dirty[it] = false }
                        }
                        if (regionCommands != null) {
                            // In order, stopping at the first refusal; save is
                            // last, so a refused step leaves nothing on flash.
                            var refused: String? = null
                            for (command in regionCommands) {
                                val reply = vm.cliQuery(keyHex, command)
                                if (RegionAdmin.treeStepRefused(reply)) {
                                    refused = ctx.getString(
                                        R.string.rsf_step_refused, node, command,
                                        reply?.trim()?.let { r ->
                                            if (r.contains("??")) ctx.getString(R.string.rsf_needs_116, r) else r
                                        } ?: ctx.getString(R.string.clock_no_answer),
                                    )
                                    break
                                }
                            }
                            vm.transientMessage.value = refused ?: ctx.getString(R.string.rsf_regions_updated, node)
                        }
                        if (!state.radioSame) pendingReboot = c.name.ifBlank { ctx.getString(R.string.rsf_scanned_settings) }
                    }
                }) {
                    Text(
                        stringResource(if (nothingToDo) R.string.close else R.string.rr_apply),
                        color = if (nothingToDo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                if (!nothingToDo) TextButton(onClick = { scannedConfig = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingReboot?.let { what ->
        val node = contact?.name?.ifBlank { null } ?: keyHex.take(12)
        AlertDialog(
            onDismissRequest = { pendingReboot = null },
            title = { Text(stringResource(R.string.rsf_reboot_q, node)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.rsf_reboot_body, node, what),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.rsf_reboot_warn, node),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.cliQuery(keyHex, CliIds.REBOOT) }
                    pendingReboot = null
                    vm.transientMessage.value = ctx.getString(R.string.rsf_reboot_sent, node)
                }) { Text(stringResource(R.string.rsf_reboot_now), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReboot = null }) { Text(stringResource(R.string.rsf_later)) }
            },
        )
    }

    confirmAction?.let { (label, command) ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(label) },
            text = { Text(stringResource(R.string.rsf_destructive, command)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.cliQuery(keyHex, command) }
                    confirmAction = null
                }) { Text(stringResource(R.string.send), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * One remote settings section: same collapsible chrome as the local
 * Settings tab, with the CLI fetch wired to the expand gesture and a
 * Save row for the dirty fields.
 */
@Composable
private fun RemoteSection(
    title: String,
    isReady: Boolean,
    fetchIds: List<String>,
    fetch: suspend (List<String>) -> Int,
    saveEnabled: Boolean,
    buildSaves: () -> List<String>,
    send: suspend (List<String>) -> Int,
    clearDirty: () -> Unit,
    extraActions: (@Composable (setStatus: (String) -> Unit) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ExpandableSection(title) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }

        QueryOnExpand(
            enabled = isReady && fetchIds.isNotEmpty(),
            query = {
                val ok = fetch(fetchIds)
                status = if (ok > 0) null else ctx.getString(R.string.rs_no_reply)
            },
        ) {
            content()
            ButtonFlowRow {
                extraActions?.invoke { status = it }
                TextButton(
                    enabled = isReady && fetchIds.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            val ok = fetch(fetchIds)
                            status = if (ok > 0) ctx.resources.getQuantityString(R.plurals.rsf_refreshed, ok, ok) else ctx.getString(R.string.clock_no_answer)
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.rsf_refresh)) }
                TextButton(
                    enabled = saveEnabled && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            status = null
                            val commands = buildSaves()
                            val ok = send(commands)
                            status = when {
                                commands.isEmpty() -> ctx.getString(R.string.rsf_nothing_valid)
                                ok == commands.size -> ctx.getString(R.string.rr_saved)
                                else -> ctx.getString(R.string.rsf_saved_partial, ok, commands.size)
                            }
                            if (ok == commands.size) clearDirty()
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.rsf_save)) }
            }
            if (busy) SectionSpinner(stringResource(R.string.id_talking))
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** What a repeater held when a scanned code was checked against it. */
private data class ScannedNodeState(
    /** Its radio values already match the code's; see ScannedSettingsPlan.radioMatches. */
    val radioSame: Boolean,
    /** Null when the code carries no regions. */
    val plan: ScannedSettingsPlan.RegionPlan?,
)
