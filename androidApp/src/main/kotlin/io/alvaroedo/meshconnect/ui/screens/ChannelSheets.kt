package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.alvaroedo.meshconnect.platform.PortraitCaptureActivity
import io.alvaroedo.meshconnect.storage.ChannelEntity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import io.alvaroedo.meshconnect.platform.Qr
import io.github.thatsfguy.meshcore.protocol.ShareUri
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.ChannelSetup
import androidx.compose.foundation.clickable

/** The add sheet's choices, in the order Liam's MeshCore app offers them. */
private enum class AddChannelChoice { Public, CreatePrivate, JoinPrivate, Hashtag }

/**
 * Channel add sheet: four named choices, each a short form of its own,
 * plus the community QR scan.
 *
 * It was one form with two hidden rules — a name starting with '#' derived
 * the key, and a blank key field meant "make a random one" — and nothing
 * said that the KEY is the channel. That is how two radios end up holding
 * one channel under two names without anyone knowing. The choices and
 * their one-line descriptions follow the mainstream MeshCore app; the
 * security wording stays ours (obfuscated, not secure).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAddSheet(vm: MeshCoreViewModel, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf<AddChannelChoice?>(null) }

    val communityScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.importScannedCode(it) }
        onDismiss()
    }

    val scanPrompt = stringResource(R.string.scan_prompt)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            when (val c = choice) {
                null -> {
                    Text(stringResource(R.string.ch_add_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ch_add_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(stringResource(R.string.ch_public), stringResource(R.string.ch_public_sub)) {
                        choice = AddChannelChoice.Public
                    }
                    ChoiceRow(stringResource(R.string.ch_create_private), stringResource(R.string.ch_create_private_sub)) {
                        choice = AddChannelChoice.CreatePrivate
                    }
                    ChoiceRow(stringResource(R.string.ch_join_private), stringResource(R.string.ch_join_private_sub)) {
                        choice = AddChannelChoice.JoinPrivate
                    }
                    ChoiceRow(stringResource(R.string.ch_hashtag), stringResource(R.string.ch_hashtag_sub)) {
                        choice = AddChannelChoice.Hashtag
                    }
                    ChoiceRow(stringResource(R.string.ch_scan), stringResource(R.string.ch_scan_sub)) {
                        communityScanLauncher.launch(meshScanOptions(scanPrompt))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ch_add_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> AddChannelForm(vm, c, onBack = { choice = null }, onDone = onDismiss)
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddChannelForm(
    vm: MeshCoreViewModel,
    choice: AddChannelChoice,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var name by remember(choice) { mutableStateOf("") }
    var key by remember(choice) { mutableStateOf("") }

    val title = when (choice) {
        AddChannelChoice.Public -> stringResource(R.string.ch_public)
        AddChannelChoice.CreatePrivate -> stringResource(R.string.ch_create_private)
        AddChannelChoice.JoinPrivate -> stringResource(R.string.ch_join_private)
        AddChannelChoice.Hashtag -> stringResource(R.string.ch_hashtag)
    }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))

    // Validation runs as you type, but only complains once there is
    // something to complain about.
    val nameResult = when (choice) {
        AddChannelChoice.Hashtag -> ChannelSetup.hashtag(name)
        AddChannelChoice.CreatePrivate, AddChannelChoice.JoinPrivate -> ChannelSetup.privateName(name)
        AddChannelChoice.Public -> null
    }
    val keyResult = if (choice == AddChannelChoice.JoinPrivate) ChannelSetup.privateKey(key) else null
    fun problem(r: ChannelSetup.Result<*>?, typed: String) =
        (r as? ChannelSetup.Result.Problem)?.message?.takeIf { typed.isNotBlank() }

    when (choice) {
        AddChannelChoice.Public -> Explain(stringResource(R.string.ch_public_explain))
        AddChannelChoice.Hashtag -> Explain(stringResource(R.string.ch_hashtag_explain))
        AddChannelChoice.CreatePrivate -> Explain(stringResource(R.string.ch_create_explain))
        AddChannelChoice.JoinPrivate -> Explain(stringResource(R.string.ch_join_explain))
    }

    if (choice != AddChannelChoice.Public) {
        FormField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(if (choice == AddChannelChoice.Hashtag) R.string.ch_channel_name else R.string.ch_name)) },
            prefix = if (choice == AddChannelChoice.Hashtag) ({ Text("#") }) else null,
            singleLine = true,
            isError = problem(nameResult, name) != null,
            supportingText = problem(nameResult, name)?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (choice == AddChannelChoice.JoinPrivate) {
        FormField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.ch_secret_key)) },
            singleLine = true,
            isError = problem(keyResult, key) != null,
            supportingText = problem(keyResult, key)?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val okName = (nameResult as? ChannelSetup.Result.Ok)?.value
    val okKey = (keyResult as? ChannelSetup.Result.Ok)?.value
    val ready = when (choice) {
        AddChannelChoice.Public -> true
        AddChannelChoice.JoinPrivate -> okName != null && okKey != null
        else -> okName != null
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.Button(
            enabled = ready,
            onClick = {
                when (choice) {
                    AddChannelChoice.Public -> vm.joinPublicChannel()
                    AddChannelChoice.Hashtag -> vm.joinHashtagChannel(okName!!)
                    AddChannelChoice.CreatePrivate -> vm.createPrivateChannel(okName!!)
                    AddChannelChoice.JoinPrivate -> vm.joinPrivateChannel(okName!!, okKey!!)
                }
                onDone()
            },
        ) { Text(stringResource(if (choice == AddChannelChoice.CreatePrivate) R.string.ch_create else R.string.join_confirm)) }
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
}

/** Channel editor (rename / change PSK / remove) for Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelEditSheet(vm: MeshCoreViewModel, channel: ChannelEntity, onDismiss: () -> Unit) {
    // See the confirmation below for why sharing needs one.
    var name by remember { mutableStateOf(channel.name) }
    var pskHex by remember { mutableStateOf("") }
    var pskLoaded by remember { mutableStateOf(false) }
    var shareConfirm by remember { mutableStateOf(false) }
    var shareQr by remember { mutableStateOf(false) }

    LaunchedEffect(channel.idx) {
        vm.channelPskHex(channel)?.let { pskHex = it }
        pskLoaded = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.ch_numbered, channel.idx), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            FormField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.ch_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = pskHex,
                onValueChange = { pskHex = it },
                label = { Text(stringResource(if (pskLoaded) R.string.ch_secret_key else R.string.ch_loading_key)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.ch_key_stored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Default,
            )
            Spacer(Modifier.height(12.dp))
            // The region is local-only routing state (PARITY §8): it
            // scopes which repeaters carry this channel's traffic and is
            // never written to the radio's channel slot.
            ChannelRegionPicker(vm, channel.idx)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                vm.editChannel(channel.idx, name.trim(), pskHex.trim())
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
            TextButton(
                onClick = { shareConfirm = true },
                enabled = pskLoaded && pskHex.isNotBlank(),
            ) { Text(stringResource(R.string.ch_share_qr)) }
            TextButton(onClick = {
                vm.deleteChannel(channel.idx)
                onDismiss()
            }) { Text(stringResource(R.string.ch_remove), color = MaterialTheme.colorScheme.error) }
        }
    }

    // Sharing a channel hands over its key, and the key IS the channel.
    // Make that a decision, not a tap.
    if (shareConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { shareConfirm = false },
            title = { Text(stringResource(R.string.ch_share_title)) },
            text = {
                Text(
                    stringResource(R.string.ch_share_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { shareConfirm = false; shareQr = true }) {
                    Text(stringResource(R.string.ch_show_code), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { shareConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (shareQr) {
        ChannelQrDialog(
            name = name.ifBlank { "#${channel.idx}" },
            pskHex = pskHex.trim(),
            // Share the scope along with the key: a code that omits it
            // hands someone a channel that behaves differently from the
            // one you are running, and neither of you can see why.
            regionScope = vm.prefs.channelRegion(channel.idx).orEmpty(),
            onDismiss = { shareQr = false },
        )
    }
}

/** QR for `meshcore://channel/add?…` — the form other MeshCore apps scan. */
@Composable
private fun ChannelQrDialog(
    name: String,
    pskHex: String,
    regionScope: String,
    onDismiss: () -> Unit,
) {
    val uri = remember(name, pskHex, regionScope) {
        ShareUri.encodeChannel(name, pskHex, regionScope)
    }
    val qr = remember(uri) { runCatching { Qr.encode(uri) }.getOrNull() }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ch_share_named, name), maxLines = 2) },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                qr?.let {
                    androidx.compose.foundation.Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(R.string.ch_qr),
                        modifier = Modifier.size(280.dp),
                    )
                }
                Text(
                    stringResource(R.string.ch_qr_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(uri))
            }) { Text(stringResource(R.string.ch_copy_link)) }
        },
    )
}
