package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.ui.FirmwareTargetKind
import io.alvaroedo.meshconnect.ui.FirmwareUi
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.firmware.DfuProgress
import io.github.thatsfguy.meshcore.firmware.FirmwareAsset
import io.github.thatsfguy.meshcore.firmware.FirmwareRole
import io.github.thatsfguy.meshcore.firmware.FirmwareVersion
import io.github.thatsfguy.meshcore.firmware.ConsoleRow
import io.github.thatsfguy.meshcore.firmware.Recovery
import io.github.thatsfguy.meshcore.firmware.NodeIdentityReplies
import io.github.thatsfguy.meshcore.firmware.VersionOrder
import io.github.thatsfguy.meshcore.firmware.deviceIdentityLine

/**
 * Firmware updates over Bluetooth.
 *
 * Every screen in this app can be backed out of except this one's last
 * step: once the image starts, a node that is interrupted stays in
 * update mode until it is flashed again. The design follows from that.
 *
 * - The confirmation names the **board**, not the file, because a wrong
 *   file for the right board is recoverable and the reverse is not.
 * - The package's hash is always shown, and whether it matched what the
 *   release published.
 * - The progress screen keeps itself awake and says, in as many words,
 *   not to walk away — the transfer is BLE and the phone leaving range
 *   is the commonest way it fails.
 * - A failure says what state the node is in. "It is waiting, not
 *   bricked" is the difference between a retry and a ladder.
 */
@Composable
fun FirmwareScreen(
    vm: MeshCoreViewModel,
    nav: NavController,
    target: FirmwareTargetKind = FirmwareTargetKind.ConnectedRadio,
    /**
     * Which release series to offer. A repeater and a room server run
     * different firmware on the same board, so this cannot be inferred
     * from the hardware — offering the wrong one would not brick the
     * node but would quietly turn it into a different kind of node.
     */
    role: FirmwareRole = FirmwareRole.Companion,
    /** The address the node reported when it took `start ota`, if we saw it. */
    otaAddress: String? = null,
    /**
     * The board and version of the node being updated, when it is not
     * the radio this app is connected to. A repeater reports both over
     * its admin session (`board`, `ver`); without them the picker cannot
     * narrow forty boards down to one and has to show them all.
     */
    nodeBoard: String? = null,
    nodeVersion: String? = null,
    /**
     * The node's contact key, when it is not the connected radio.
     *
     * Its console thread is read live so that a `board` reply arriving
     * after this screen opened still narrows the firmware list. Reading
     * it only from the route would mean a slow mesh — the normal case
     * for a distant repeater — silently fell back to showing every
     * board.
     */
    nodeKey: String? = null,
) {
    val context = LocalContext.current
    val state by vm.firmware.state.collectAsState()
    val deviceInfo by vm.deviceInfo.collectAsState()
    val capable = vm.firmwareUpdatesSupported()

    // A remote node's identity comes from its own console replies and
    // may land after this screen opens, so it is read live rather than
    // frozen into the route.
    val nodeReplies by remember(nodeKey) {
        if (nodeKey.isNullOrBlank()) MutableStateFlow(emptyList()) else vm.cliThread(nodeKey)
    }.collectAsState()
    // What the node last said it was, from the contact record. This is
    // the only source that still works once it is in its bootloader and
    // has stopped answering the mesh entirely.
    val storedContact = vm.dbContacts.collectAsState().value
        .firstOrNull { nodeKey != null && it.keyHex == nodeKey }
    // A `ver` answer from before the node last entered update mode
    // describes the firmware it had before it was flashed.
    val versionsSince = storedContact?.otaReplyHandledAt ?: 0L
    val liveIdentity = remember(nodeReplies, versionsSince) {
        NodeIdentityReplies.fromRows(
            nodeReplies.map { ConsoleRow(it.outgoing, it.text, it.receivedAt) },
            versionsSince,
        )
    }
    val remoteBoard = liveIdentity.board ?: nodeBoard ?: storedContact?.boardName
    val remoteVersion = liveIdentity.version ?: nodeVersion ?: storedContact?.firmwareVersion

    // For the connected radio the board comes from DEVICE_INFO; for
    // another node it is whatever it said when asked (`board`).
    val board = if (target == FirmwareTargetKind.ConnectedRadio) {
        deviceInfo?.boardName
    } else {
        remoteBoard
    }
    val nodeIdentity =
        listOfNotNull(remoteBoard, remoteVersion).joinToString(" · ").ifBlank { null }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "firmware.zip"
        if (bytes == null) {
            vm.firmware.useLocalPackage(ByteArray(0), name)
        } else {
            vm.firmware.useLocalPackage(bytes, name)
        }
    }

    // The whole flow belongs to one target; a screen opened from the
    // repeater hub must not reboot the phone's own radio.
    // The stored address wins over the route argument: it is updated
    // whenever the node is found, so it stays right as the node moves
    // between its own address and its bootloader's.
    val effectiveAddress = otaAddress ?: storedContact?.otaAddress
    remember(target, effectiveAddress) { vm.firmware.aimAt(target, effectiveAddress); target }
    DisposableEffect(Unit) {
        onDispose { vm.firmware.leftScreen() }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.hub_firmware), vm = vm, nav = nav, menuActions = emptyList()) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            when (val current = state) {
                is FirmwareUi.Idle -> IdlePanel(
                    vm = vm,
                    target = target,
                    role = role,
                    capable = capable,
                    identity = if (target == FirmwareTargetKind.ConnectedRadio) {
                        deviceIdentityLine(deviceInfo)
                    } else {
                        nodeIdentity
                    },
                    boardName = board,
                    currentVersion = if (target == FirmwareTargetKind.ConnectedRadio) {
                        deviceInfo?.firmwareVersion
                    } else {
                        remoteVersion
                    },
                    message = current.message,
                    onPickFile = { openFile.launch(arrayOf("application/zip", "*/*")) },
                    onFlashOtherNode = { nav.navigate("firmware/node") },
                )

                FirmwareUi.Checking -> SectionSpinner(stringResource(R.string.fw_reading_list))

                is FirmwareUi.ChoosingVersion -> VersionPanel(
                    state = current.copy(boardName = board ?: current.boardName),
                    onChoose = { vm.firmware.chooseVersion(it, board) },
                )

                is FirmwareUi.Choosing -> ChoosePanel(
                    state = current,
                    // Picking from the full list means the user is
                    // telling us what the board is. Remember it: this
                    // node cannot be asked while it is in update mode,
                    // which is exactly when it is being flashed.
                    onChoose = { asset ->
                        val identified = current.suggested.isEmpty()
                        if (identified && nodeKey != null) {
                            vm.rememberHardware(nodeKey, asset.boardPrefix, null)
                        }
                        vm.firmware.choose(asset, remember = identified)
                    },
                    onPickFile = { openFile.launch(arrayOf("application/zip", "*/*")) },
                )

                is FirmwareUi.Downloading -> SectionSpinner(stringResource(R.string.fw_downloading, current.name))

                is FirmwareUi.Confirm -> ConfirmPanel(
                    state = current,
                    boardName = board,
                    onFlash = { vm.firmware.flash() },
                    onCancel = { vm.firmware.reset() },
                )

                is FirmwareUi.Running -> RunningPanel(current.progress)

                is FirmwareUi.Finished -> {
                    // The node has rebooted into new firmware, so it is
                    // out of update mode. The FLAG goes; the address
                    // stays — it is the node's Bluetooth address, which
                    // an update does not change, and it is what makes
                    // this node findable the next time.
                    LaunchedEffect(nodeKey) {
                        nodeKey?.let {
                            vm.setUpdateMode(it, false)
                            // What it is running now. Without this the
                            // record kept the version from before the
                            // flash, and the picker went on calling that
                            // one "Installed now".
                            vm.rememberHardware(it, null, current.version)
                            // Only where nothing is recorded: an address
                            // the node reported itself is better evidence
                            // than one worked out from a scan.
                            if (storedContact?.otaAddress == null) {
                                current.nodeAddress?.let { address ->
                                    vm.rememberOtaAddress(it, address)
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.fw_complete), style = MaterialTheme.typography.titleMedium)
                    HintText(
                        current.version?.let { stringResource(R.string.fw_rebooting_into, it) } ?: stringResource(R.string.fw_rebooting),
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = { vm.firmware.reset() }) { Text(stringResource(R.string.setup_done)) }
                }

                is FirmwareUi.WeakSignal -> {
                    Text(stringResource(R.string.fw_too_far), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.fw_too_far_body, current.peer.toString(), current.rssi, current.pkg.imageSize / 1024),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    ButtonFlowRow {
                        Button(onClick = { vm.firmware.flash() }) { Text(stringResource(R.string.ota_try_again)) }
                        FilledTonalButton(onClick = { vm.firmware.reset() }) { Text(stringResource(R.string.fw_stop)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpandableHint(stringResource(R.string.fw_anyway_hint)) {
                        DetailText(
                            stringResource(R.string.fw_anyway_detail),
                        )
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { vm.firmware.retryOverWeakSignal() }) {
                            Text(stringResource(R.string.fw_transfer_anyway))
                        }
                    }
                }

                is FirmwareUi.Failed -> {
                    Text(stringResource(R.string.fw_failed), style = MaterialTheme.typography.titleMedium)
                    Text(current.message, color = MaterialTheme.colorScheme.error)
                    current.recovery?.let {
                        Spacer(Modifier.height(8.dp))
                        HintText(it)
                    }
                    Spacer(Modifier.height(8.dp))
                    // "Try again" only where it can work — see
                    // [FirmwareUi.Failed.retry]. On a stock bootloader
                    // with the firmware erased, a retry is refused by the
                    // latched session, so the button would be a lie.
                    ButtonFlowRow {
                        if (current.retry != null) {
                            Button(onClick = { vm.firmware.flash() }) { Text(stringResource(R.string.ota_try_again)) }
                        }
                        FilledTonalButton(onClick = { vm.firmware.reset() }) { Text(stringResource(R.string.back)) }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun IdlePanel(
    vm: MeshCoreViewModel,
    target: FirmwareTargetKind,
    role: FirmwareRole,
    capable: Boolean,
    identity: String?,
    boardName: String?,
    currentVersion: String?,
    message: String?,
    onPickFile: () -> Unit,
    onFlashOtherNode: () -> Unit,
) {
    identity?.let {
        Text(it, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
    }
    message?.let {
        HintText(it)
        Spacer(Modifier.height(8.dp))
    }

    if (target == FirmwareTargetKind.ConnectedRadio && !capable) {
        Text(
            stringResource(R.string.fw_no_ota),
            style = MaterialTheme.typography.titleSmall,
        )
        HintText(stringResource(R.string.fw_no_ota_hint))
        Spacer(Modifier.height(8.dp))
        // Not a dead end. The radio in your hand having no DFU service
        // says nothing about the node you are standing next to, and
        // reaching that node needs no mesh, no admin session and no
        // contact record — only Bluetooth range.
        FilledTonalButton(onClick = onFlashOtherNode) {
            Text(stringResource(R.string.fw_flash_other))
        }
        Spacer(Modifier.height(8.dp))
        ExpandableHint(stringResource(R.string.fw_what_ota)) {
            DetailText(
                stringResource(R.string.fw_what_ota_detail),
            )
        }
        return
    }

    if (target == FirmwareTargetKind.NodeInUpdateMode) {
        HintText(stringResource(R.string.fw_bt_range))
        Spacer(Modifier.height(8.dp))
        ExpandableHint(stringResource(R.string.fw_what_happens)) {
            DetailText(
                stringResource(R.string.fw_what_happens_detail),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    ExpandableHint(stringResource(R.string.fw_unreachable)) {
        DetailText(
            stringResource(R.string.fw_unreachable_detail),
        )
    }

    Spacer(Modifier.height(8.dp))
    ButtonFlowRow {
        Button(onClick = {
            vm.firmware.checkForFirmware(
                boardName = boardName,
                role = role,
                currentVersion = currentVersion,
            )
        }) { Text(stringResource(R.string.fw_check)) }
        FilledTonalButton(onClick = onPickFile) { Text(stringResource(R.string.fw_open_zip)) }
    }
    HintText(stringResource(R.string.fw_check_hint))
    if (target == FirmwareTargetKind.ConnectedRadio) {
        Spacer(Modifier.height(8.dp))
        // A node already in update mode is off the mesh, so there is no
        // admin session to reach it through and possibly no contact
        // record either. It is found by scanning, which needs none of
        // that — so this must not be reachable only from the node's own
        // hub, which is precisely where it cannot be reached.
        FilledTonalButton(onClick = onFlashOtherNode) {
            Text(stringResource(R.string.fw_another))
        }
    }
}

/**
 * Which version to install.
 *
 * Newest first, but nothing is preselected and the newest is not
 * treated as the answer: MeshCore ships releases that turn out not to
 * be ready, and someone updating a node they have to climb to reach is
 * usually choosing a version they have already run somewhere else.
 */
@Composable
private fun VersionPanel(
    state: FirmwareUi.ChoosingVersion,
    onChoose: (FirmwareVersion) -> Unit,
) {
    Text(stringResource(R.string.fw_choose_version), style = MaterialTheme.typography.titleMedium)
    HintText(
        state.boardName?.let { stringResource(R.string.fw_for_board, it) } ?: stringResource(R.string.fw_board_unknown),
    )
    Spacer(Modifier.height(8.dp))
    for (version in state.versions) {
        // A node reports "v1.16.0-07a3ca9"; the tag is "v1.16.0". Compare
        // the numbers, not the strings.
        val isCurrent = state.currentVersion != null &&
            VersionOrder.key(version.version) == VersionOrder.key(state.currentVersion) &&
            VersionOrder.key(version.version) > 0
        val isNewest = version == state.versions.first()
        SettingsTileRow(
            title = version.version,
            subtitle = when {
                isCurrent && isNewest -> stringResource(R.string.fw_installed_newest)
                isCurrent -> stringResource(R.string.fw_installed)
                isNewest -> stringResource(R.string.fw_newest)
                else -> stringResource(R.string.fw_older)
            },
            dimmed = false,
            onClick = { onChoose(version) },
        )
    }
    Spacer(Modifier.height(8.dp))
    ExpandableHint(stringResource(R.string.fw_which)) {
        DetailText(
            stringResource(R.string.fw_which_detail),
        )
    }
}

@Composable
private fun ChoosePanel(
    state: FirmwareUi.Choosing,
    onChoose: (FirmwareAsset) -> Unit,
    onPickFile: () -> Unit,
) {
    if (state.suggested.isEmpty()) {
        Text(stringResource(R.string.fw_pick_build), style = MaterialTheme.typography.titleMedium)
        HintText(
            stringResource(R.string.fw_pick_build_hint),
        )
        ExpandableHint(stringResource(R.string.fw_why_asking)) {
            DetailText(
                stringResource(R.string.fw_why_asking_detail),
            )
        }
    } else {
        Text("${state.boardName} · ${state.version}", style = MaterialTheme.typography.titleMedium)
        HintText(
            if (state.suggested.size > 1) {
                stringResource(R.string.fw_multi_build)
            } else {
                stringResource(R.string.fw_build_in, state.version)
            },
        )
    }
    Spacer(Modifier.height(8.dp))
    for (asset in state.suggested) {
        AssetRow(asset, onChoose)
    }
    if (state.others.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        ExpandableHint(stringResource(R.string.fw_every_board, state.others.size)) {
            Column {
                for (asset in state.others) AssetRow(asset, onChoose)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(onClick = onPickFile) { Text(stringResource(R.string.fw_open_zip_instead)) }
}

@Composable
private fun AssetRow(asset: FirmwareAsset, onChoose: (FirmwareAsset) -> Unit) {
    SettingsTileRow(
        title = asset.boardPrefix,
        subtitle = "${asset.version} · ${asset.sizeBytes / 1024} KB" +
            if (asset.sha256 == null) " · " + stringResource(R.string.fw_no_checksum_short) else "",
        dimmed = false,
        onClick = { onChoose(asset) },
    )
}

@Composable
private fun ConfirmPanel(
    state: FirmwareUi.Confirm,
    boardName: String?,
    onFlash: () -> Unit,
    onCancel: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Text(stringResource(R.string.fw_ready), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(state.source.sourceDescription, style = MaterialTheme.typography.bodyLarge)
    HintText(stringResource(R.string.fw_image_kb, state.pkg.imageSize / 1024))
    Spacer(Modifier.height(8.dp))

    Text("SHA-256", style = MaterialTheme.typography.labelMedium)
    Text(
        state.source.sha256,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    HintText(
        if (state.source.verifiedAgainstRelease) {
            stringResource(R.string.fw_checksum_ok)
        } else {
            stringResource(R.string.fw_checksum_none)
        },
    )

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.fw_board_warning),
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    ButtonFlowRow {
        Button(onClick = { confirming = true }) { Text(stringResource(R.string.fw_flash_this)) }
        FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(boardName?.let { stringResource(R.string.fw_flash_q, it) } ?: stringResource(R.string.fw_flash_q_node)) },
            text = {
                Text(
                    (boardName?.let { stringResource(R.string.fw_writes_board, state.source.sourceDescription, it) } ?: stringResource(R.string.fw_writes, state.source.sourceDescription)) + "\n\n" + stringResource(R.string.fw_keep_close),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onFlash()
                }) { Text(stringResource(R.string.fw_flash)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RunningPanel(progress: DfuProgress) {
    KeepScreenOn()
    val label = when (progress) {
        DfuProgress.Preparing -> stringResource(R.string.dfu_preparing)
        DfuProgress.EnteringBootloader -> stringResource(R.string.dfu_entering)
        DfuProgress.FindingNode -> stringResource(R.string.dfu_finding)
        is DfuProgress.Connecting -> stringResource(R.string.dfu_connecting, progress.peer.name ?: progress.peer.address)
        is DfuProgress.Transferring -> stringResource(R.string.dfu_sending)
        DfuProgress.Verifying -> stringResource(R.string.dfu_verifying)
        DfuProgress.Finished -> stringResource(R.string.setup_done)
        is DfuProgress.SignalTooWeak -> stringResource(R.string.dfu_too_far)
        // Said out loud rather than hidden behind a progress bar that
        // silently starts again.
        is DfuProgress.Retrying ->
            progress.reason + " " + stringResource(R.string.dfu_retrying)

        is DfuProgress.Failed -> progress.message
    }
    Text(label, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    if (progress is DfuProgress.Transferring) {
        LinearProgressIndicator(
            progress = { progress.fraction.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        HintText(stringResource(R.string.dfu_progress, progress.bytesSent / 1024, progress.totalBytes / 1024))
    } else {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(12.dp))
    HintText(
        stringResource(R.string.dfu_stay),
    )
}
