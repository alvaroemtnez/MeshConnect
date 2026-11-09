package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.CliResend
import io.github.thatsfguy.meshcore.firmware.ConsoleRow
import io.github.thatsfguy.meshcore.firmware.DfuTuning
import io.github.thatsfguy.meshcore.firmware.NodeIdentityReplies
import io.github.thatsfguy.meshcore.firmware.OtaEntry
import io.github.thatsfguy.meshcore.firmware.OtaEvidence
import io.github.thatsfguy.meshcore.presentation.UpdateModeView
import io.github.thatsfguy.meshcore.presentation.encodePrefill
import io.github.thatsfguy.meshcore.protocol.NodeRole
import io.github.thatsfguy.meshcore.util.RelativeTime
import kotlinx.coroutines.delay

/**
 * Updating a repeater or room server.
 *
 * Only the first step of this travels over the mesh. `start ota` tells
 * the node to reboot into its bootloader; from that moment it is off
 * the mesh entirely and the firmware itself has to be carried there by
 * a phone standing next to it, over Bluetooth. There is no version of
 * this feature where a repeater on a hill is updated from the sofa, and
 * the screen says so before the button rather than after it.
 *
 * The command is deliberately not one tap: it is `start ota` on a node
 * whose next state depends on someone physically going there.
 */
@Composable
fun RepeaterFirmwarePanel(
    vm: MeshCoreViewModel,
    nav: NavController,
    keyHex: String,
    role: NodeRole,
) {
    val roleArg = if (role == NodeRole.Room) "room" else "repeater"
    var confirming by remember { mutableStateOf(false) }
    // The contact row carries what the app has learned about this node
    // and could not ask for again later: its board, its firmware version,
    // and the address it announced when it last entered update mode. All
    // three matter precisely when the node has stopped answering.
    //
    // What the row does NOT carry is whether the node is in update mode
    // now — see [UpdateModeView] for why that cannot be stored, and what
    // reading it out of storage did.
    val storedContact = vm.dbContacts.collectAsState().value.firstOrNull { it.keyHex == keyHex }

    // The node answers `start ota` with the address it is now
    // advertising on ("OK - mac: …"). Catching it out of the console
    // thread means the update matches that node exactly rather than
    // picking between everything nearby whose name ends in OTA.
    val replies by remember(keyHex) { vm.cliThread(keyHex) }.collectAsState()

    // Evidence and memory are different kinds of thing, and conflating
    // them is what made this screen lie. The rules live in [OtaEntry]
    // and [OtaEvidence] so they are testable without a device.
    val rows = remember(replies) {
        replies.map { ConsoleRow(it.outgoing, it.text, it.receivedAt) }
    }
    val updateMode = UpdateModeView.of(
        flaggedInUpdateMode = (storedContact?.updateModeSince ?: 0L) > 0L,
        knownAddress = storedContact?.otaAddress,
    )
    val advertisingMac = updateMode.flashAddress
    val inUpdateMode = updateMode.inUpdateMode

    // The sequence that puts a node into update mode: `ver` first, and
    // `start ota` only once the node has answered it. See [OtaEntry] —
    // `start ota` is not a question, so it must not be asked of a node
    // that has not just proved it is listening.
    var entry by remember(keyHex) { mutableStateOf<OtaEntry>(OtaEntry.Idle) }

    // Drive it. Rows arriving move it along; the ticker exists only so
    // that a node which never answers gives up on its own rather than
    // leaving a spinner running until someone navigates away.
    LaunchedEffect(entry, rows) {
        val running = entry is OtaEntry.Queued ||
            entry is OtaEntry.ProvingTheNodeAnswers ||
            entry is OtaEntry.AwaitingUpdateMode
        if (!running) return@LaunchedEffect
        while (true) {
            val next = OtaEntry.advance(entry, rows, System.currentTimeMillis())
            if (next != entry) {
                entry = next
                return@LaunchedEffect
            }
            delay(1_000)
        }
    }

    // Each state's own command, sent on the way in. Keyed on the state's
    // send time so a recomposition cannot send it twice.
    LaunchedEffect((entry as? OtaEntry.ProvingTheNodeAnswers)?.sentAt) {
        if (entry is OtaEntry.ProvingTheNodeAnswers) vm.sendCli(keyHex, "ver")
    }
    LaunchedEffect((entry as? OtaEntry.AwaitingUpdateMode)?.sentAt) {
        if (entry is OtaEntry.AwaitingUpdateMode) vm.sendCli(keyHex, "start ota")
    }
    // Keyed on the reply's time, not the address: a confirmation with no
    // address (an all-zero MAC) has the same null key as "not confirmed".
    LaunchedEffect((entry as? OtaEntry.Confirmed)?.at) {
        val done = entry as? OtaEntry.Confirmed ?: return@LaunchedEffect
        // A MAC is hardware, so an address recorded earlier stays right;
        // with none reported, the flash step matches the node by name.
        done.address?.let { vm.rememberOtaAddress(keyHex, it) }
        vm.setUpdateMode(keyHex, true, handledAt = done.at)
    }

    // The passive path: `start ota` typed into the console tab by hand
    // is a real entry into update mode and has to be noticed too.
    //
    // The thread is PERSISTED, so that row does not go away — which is
    // why it is consumed once, against the watermark in
    // `otaReplyHandledAt`, and never read as a live state. A reply
    // re-read from history on every render would assert update mode
    // exactly as permanently as the stored address did.
    val passive = remember(rows, storedContact?.otaReplyHandledAt) {
        // The contact row, not a default for it. `dbContacts` starts
        // empty and fills in a frame or two later, so reading the
        // watermark as `?: 0` meant the first composition saw "nothing
        // has been accounted for" and consumed a reply from days ago as
        // though it had just arrived. Caught on hardware: the panel
        // announced "Recorded just now" for a node that had been
        // reflashed over USB and was back on the mesh.
        storedContact?.let { OtaEvidence.freshAdvertisingAddress(rows, it.otaReplyHandledAt) }
    }
    LaunchedEffect(passive, entry) {
        // While a sequence is running its own confirmation is the one
        // that counts; this is only for a `start ota` nobody here sent.
        if (entry != OtaEntry.Idle) return@LaunchedEffect
        val seen = passive ?: return@LaunchedEffect
        vm.rememberOtaAddress(keyHex, seen.address)
        vm.setUpdateMode(keyHex, true, handledAt = seen.at)
    }

    // Ask the node what it is. `board` returns getManufacturerName() —
    // the same string a companion reports in DEVICE_INFO — and `ver`
    // returns "<version> (Build: <date>)" (CommonCLI.cpp). Without them
    // the firmware picker has no way to narrow forty boards to one, and
    // the operator is left choosing a build by eye.
    // Asked once, and only when the answer is not already known. It was
    // firing on every visit — including after the node had been told to
    // enter update mode, which is both pointless traffic and a question
    // put to a node that is on its way out of the conversation.
    //
    // One at a time. These went out as two calls in a row, each of which
    // starts its own coroutine to write its console row — so the rows
    // could land in the opposite order to the sends, and a reply matched
    // by position then belonged to the other command. It read on a live
    // repeater as "v1.15.0-dee3e26 (Build: 19-Apr-2026) · ProMicro DIY",
    // with the version stored against the contact as its board name,
    // which is what the firmware picker and the bootloader scan both
    // work from.
    // Timed, so a command that went unanswered cannot take later replies,
    // and a `ver` from before the last update-mode entry is not taken for
    // what the node runs now. See [NodeIdentityReplies.fromRows].
    val identity = remember(rows, storedContact?.otaReplyHandledAt) {
        NodeIdentityReplies.fromRows(rows, storedContact?.otaReplyHandledAt ?: 0L)
    }
    // Asked again on silence — see [CliResend]: a CLI command has no
    // delivery receipt, so a lost answer looks exactly like a lost
    // command, and asking once meant a single lost `board` left the
    // panel without a board or a version for the rest of the visit.
    // Each loop ends the moment its answer lands (the effect is keyed on
    // it), and neither runs while the update sequence is — one command
    // in flight at a time, or answers are matched to the wrong question.
    val boardKnown = identity.board != null || storedContact?.boardName != null
    LaunchedEffect(keyHex, boardKnown, inUpdateMode, entry == OtaEntry.Idle) {
        if (boardKnown || inUpdateMode || entry != OtaEntry.Idle) return@LaunchedEffect
        repeat(CliResend.MAX_SENDS) {
            vm.sendCli(keyHex, "board")
            delay(CliResend.RESEND_AFTER_MS)
        }
    }
    // `ver` follows once the board is known, so there is only ever one
    // command outstanding. The update sequence sends its own `ver` and
    // does not want a second one racing it.
    LaunchedEffect(keyHex, boardKnown, identity.version != null, inUpdateMode, entry == OtaEntry.Idle) {
        if (!boardKnown || identity.version != null || inUpdateMode || entry != OtaEntry.Idle) {
            return@LaunchedEffect
        }
        repeat(CliResend.MAX_SENDS) {
            vm.sendCli(keyHex, "ver")
            delay(CliResend.RESEND_AFTER_MS)
        }
    }
    // Persist it: once this node is in its bootloader it can no longer
    // be asked, and that is precisely when the answer is needed.
    LaunchedEffect(identity) {
        vm.rememberHardware(keyHex, identity.board, identity.version)
    }

    val flashRoute = "firmware/node?role=$roleArg&node=$keyHex" +
        (advertisingMac?.let { "&mac=$it" } ?: "") +
        (identity.board?.let { "&board=${encodePrefill(it)}" } ?: "") +
        (identity.version?.let { "&fw=${encodePrefill(it)}" } ?: "")

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ota_title), style = MaterialTheme.typography.titleMedium)
        // The stored record fills what the thread cannot: a `ver` from
        // before the last update-mode entry is deliberately not trusted,
        // and a node in update mode is not asked again.
        HintText(
            NodeIdentityReplies(
                board = identity.board ?: storedContact?.boardName,
                version = identity.version ?: storedContact?.firmwareVersion,
                buildDate = null,
            ).describe() ?: stringResource(R.string.ota_asking_board),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ota_steps),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        ExpandableHint(stringResource(R.string.ota_unreachable)) {
            DetailText(
                stringResource(R.string.ota_unreachable_detail) +
                    // Named only when the FAQ names it. A board that is
                    // not on that list gets no claim either way — this
                    // is advice about what to put on a node, and being
                    // confidently wrong about it costs the node.
                    if (DfuTuning.hasOtafixBootloader(storedContact?.boardName)) {
                        "\n\n" + stringResource(R.string.ota_otafix_board, DfuTuning.OTAFIX_URL)
                    } else {
                        ""
                    },
            )
        }
        Spacer(Modifier.height(16.dp))

        if (inUpdateMode) {
            Text(stringResource(R.string.ota_in_mode), style = MaterialTheme.typography.titleSmall)
            // Always say when it was recorded, and always offer the
            // correction. This is a tracked state, not a reading: nothing
            // reachable from here can see a node that was reflashed over
            // USB and put back into service, and the operator is the only
            // witness to that. They get a control rather than a screen
            // that argues with them.
            val since = storedContact?.updateModeSince ?: 0L
            HintText(
                listOfNotNull(
                    if (since > 0) stringResource(R.string.ota_recorded_ago, relativeAge(since)) else stringResource(R.string.ota_recorded),
                    advertisingMac?.let { stringResource(R.string.ota_advertising_on, it) },
                ).joinToString(", ") + ". " + stringResource(R.string.ota_again_nothing),
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { nav.navigate(flashRoute) }) { Text(stringResource(R.string.ota_flash)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.setUpdateMode(keyHex, false) }) {
                Text(stringResource(R.string.ota_not_in_mode))
            }
        } else {
            when (val step = entry) {
                is OtaEntry.Queued -> {
                    Text(stringResource(R.string.ota_wait_console), style = MaterialTheme.typography.titleSmall)
                    HintText(
                        stringResource(R.string.ota_wait_console_hint),
                    )
                }

                is OtaEntry.ProvingTheNodeAnswers -> {
                    Text(stringResource(R.string.ota_checking), style = MaterialTheme.typography.titleSmall)
                    HintText(
                        stringResource(R.string.ota_checking_hint),
                    )
                }

                is OtaEntry.AwaitingUpdateMode -> {
                    Text(stringResource(R.string.rs_waiting), style = MaterialTheme.typography.titleSmall)
                    HintText(
                        stringResource(R.string.ota_awaiting, step.version),
                    )
                }

                is OtaEntry.GaveUp -> {
                    Text(stringResource(R.string.ota_gave_up), style = MaterialTheme.typography.titleSmall)
                    HintText(step.reason)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { entry = OtaEntry.Idle }) { Text(stringResource(R.string.ota_try_again)) }
                }

                else -> {
                    Button(onClick = { confirming = true }) {
                        Text(stringResource(R.string.ota_send_btn))
                    }
                    HintText(stringResource(R.string.ota_send_hint))
                }
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { nav.navigate(flashRoute) }) {
                Text(stringResource(R.string.ota_already))
            }
            // The address is kept whatever the state — it is hardware,
            // and it is what lets a flash pick this node out of
            // everything else nearby advertising for an update. Shown
            // here so it is visible that the app still holds it.
            advertisingMac?.let { mac ->
                Spacer(Modifier.height(16.dp))
                DetailText(stringResource(R.string.ota_mac_on_file, mac))
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.ota_send_q)) },
            text = {
                Text(
                    stringResource(R.string.ota_send_detail),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // `ver` first — sent by [OtaEntry], once the console
                    // is settled. It is a free round trip whose answer
                    // proves the application firmware is running (a node
                    // in its bootloader has no LoRa stack at all) and it
                    // records the version while there is still something
                    // able to report it. `start ota` follows from the
                    // answer, and only from it.
                    entry = OtaEntry.Queued(System.currentTimeMillis())
                    confirming = false
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
