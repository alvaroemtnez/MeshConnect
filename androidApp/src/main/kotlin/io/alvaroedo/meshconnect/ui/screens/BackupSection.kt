package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.ConfigBackupRepository
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.ConfigBackup
import kotlinx.coroutines.launch

/**
 * Config backup / restore — Settings → App. PARITY.md §1.
 *
 * The honest framing, which the copy here has to carry: a backup file
 * has none of the phone's protections. Without a passphrase it holds who
 * you talk to; with one it also holds everything needed to *be* you on
 * this mesh. Both facts are said out loud before anything is written.
 */
@Composable
fun BackupSection(vm: MeshCoreViewModel) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var exportPrompt by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var importPreview by remember { mutableStateOf<ConfigBackup.Parsed?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val text = pendingExport
        pendingExport = null
        if (uri == null || text == null) return@rememberLauncherForActivityResult
        scope.launch { note = vm.writeBackupFile(uri, text) }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val parsed = vm.readBackupFile(uri)
            if (parsed == null) {
                note = ctx.getString(R.string.backup_not_ours)
            } else {
                importPreview = parsed
            }
        }
    }

    HintText(
        stringResource(R.string.backup_intro),
    )
    ButtonFlowRow {
        FilledTonalButton(onClick = { exportPrompt = true }) { Text(stringResource(R.string.backup_export_menu)) }
        TextButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_import_menu)) }
    }
    note?.let { HintText(it) }

    if (exportPrompt) {
        ExportDialog(
            canEncrypt = vm.supportsBackupEncryption,
            onDismiss = { exportPrompt = false },
            onExport = { passphrase ->
                exportPrompt = false
                scope.launch {
                    val text = vm.buildBackup(passphrase)
                    if (text == null) {
                        note = ctx.getString(R.string.backup_build_failed)
                    } else {
                        pendingExport = text
                        createFile.launch(ConfigBackupRepository.suggestedFileName(vm.nowSeconds()))
                    }
                }
            },
        )
    }

    importPreview?.let { parsed ->
        ImportDialog(
            parsed = parsed,
            selfKeyHex = vm.selfKeyHex(),
            onDismiss = { importPreview = null },
            onApply = { options, passphrase ->
                importPreview = null
                scope.launch { note = vm.applyBackup(parsed, options, passphrase) }
            },
        )
    }
}

@Composable
private fun ExportDialog(
    canEncrypt: Boolean,
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
) {
    var includeSecrets by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passphraseOk = passphrase.length >= ConfigBackup.MIN_PASSPHRASE_LENGTH &&
        passphrase == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.backup_always),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includeSecrets,
                        onCheckedChange = { includeSecrets = it },
                        enabled = canEncrypt,
                    )
                    Text(stringResource(R.string.backup_include_secrets))
                }
                if (!canEncrypt) {
                    Text(
                        stringResource(R.string.backup_no_cipher),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (includeSecrets) {
                    Text(
                        stringResource(R.string.backup_secrets_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    FormField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.backup_passphrase_min, ConfigBackup.MIN_PASSPHRASE_LENGTH)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FormField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.backup_passphrase_confirm)) },
                        singleLine = true,
                        isError = confirm.isNotEmpty() && confirm != passphrase,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(if (includeSecrets) passphrase else null) },
                enabled = !includeSecrets || passphraseOk,
            ) { Text(stringResource(if (includeSecrets) R.string.backup_export_secrets else R.string.backup_export_plain)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Preview before applying. Nothing in a backup is trustworthy just
 * because it parsed, and the radio-side parts are not undoable — so
 * every destination is a separate, off-by-default choice.
 */
@Composable
private fun ImportDialog(
    parsed: ConfigBackup.Parsed,
    selfKeyHex: String,
    onDismiss: () -> Unit,
    onApply: (ConfigBackupRepository.ApplyOptions, String?) -> Unit,
) {
    var settings by remember { mutableStateOf(true) }
    var regions by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf(false) }
    var wantSecrets by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }

    val differentRadio = parsed.plain.selfKeyHex.isNotEmpty() &&
        selfKeyHex.isNotEmpty() && parsed.plain.selfKeyHex != selfKeyHex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        if (parsed.hasSecrets) R.string.backup_contains_secrets else R.string.backup_contains,
                        parsed.plain.settings.size,
                        parsed.plain.contacts.size,
                        parsed.plain.channels.size,
                        parsed.plain.regions.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (differentRadio) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.backup_other_radio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                CheckRow(stringResource(R.string.backup_settings), settings) { settings = it }
                CheckRow(stringResource(R.string.backup_regions), regions) { regions = it }
                CheckRow(stringResource(R.string.backup_channels), channels) { channels = it }
                CheckRow(stringResource(R.string.backup_contacts), contacts) { contacts = it }
                if (parsed.hasSecrets) {
                    CheckRow(stringResource(R.string.backup_secrets), wantSecrets) { wantSecrets = it }
                    if (wantSecrets) {
                        FormField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.backup_passphrase)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (channels || contacts) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.backup_no_undo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(
                    ConfigBackupRepository.ApplyOptions(
                        settings = settings,
                        regions = regions,
                        channels = channels,
                        contacts = contacts,
                        secrets = wantSecrets,
                    ),
                    passphrase.ifBlank { null },
                )
            }) { Text(stringResource(R.string.backup_restore)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
