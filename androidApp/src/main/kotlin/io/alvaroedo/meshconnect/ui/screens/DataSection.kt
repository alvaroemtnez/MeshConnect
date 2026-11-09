package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.Retention
import kotlinx.coroutines.launch

/**
 * Retention and purge — Settings → App. PARITY.md §1 (PurgeDataScreen)
 * and §3 (ChannelMessageRetentionSettingsScreen).
 *
 * Both are privacy features wearing storage clothes. The database is
 * encrypted at rest, but the strongest form of "encrypted" is "not
 * there", and these are the two ways to get there.
 */
@Composable
fun DataSection(vm: MeshCoreViewModel) {
    val scope = rememberCoroutineScope()
    var policy by remember { mutableStateOf(vm.prefs.retentionPolicy) }
    var purgeConfirm by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Text(stringResource(R.string.data_retention), style = MaterialTheme.typography.labelLarge)
    ExpandableHint(stringResource(R.string.data_retention_hint)) {
        DetailText(
            stringResource(R.string.data_retention_detail),
        )
    }
    ChoiceChips(
        options = Retention.PRESETS.map { it.shortLabel() },
        selected = Retention.PRESETS.indexOfFirst { it == policy }.coerceAtLeast(0),
    ) { index ->
        val chosen = Retention.PRESETS[index]
        policy = chosen
        vm.prefs.retentionPolicy = chosen
        scope.launch {
            val removed = vm.applyRetentionNow()
            note = if (removed > 0) {
                "${policy.label()}. Removed $removed message(s) now."
            } else {
                "${policy.label()}. Nothing needed removing."
            }
        }
    }
    note?.let { HintText(it) }

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.data_purge), style = MaterialTheme.typography.labelLarge)
    HintText(
        stringResource(R.string.data_purge_hint),
    )
    ButtonFlowRow {
        FilledTonalButton(onClick = { purgeConfirm = true }) {
            Text(stringResource(R.string.data_purge_menu), color = MaterialTheme.colorScheme.error)
        }
    }

    if (purgeConfirm) {
        PurgeDialog(
            onDismiss = { purgeConfirm = false },
            onConfirm = { alsoSecrets ->
                purgeConfirm = false
                scope.launch { note = vm.purgeLocalData(alsoSecrets) }
            },
        )
    }
}

/**
 * Purge needs the list spelled out. "Are you sure?" is not consent when
 * the user can't see what is about to go — especially here, where the
 * message history is the one thing that cannot be re-synced from the
 * radio afterwards.
 */
@Composable
private fun PurgeDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var typed by remember { mutableStateOf("") }
    var alsoSecrets by remember { mutableStateOf(false) }
    val armed = typed.trim().equals(CONFIRM_WORD, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.data_purge)) },
        text = {
            Column {
                Text(stringResource(R.string.data_deletes), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                for (line in listOf(
                    stringResource(R.string.data_del_1),
                    stringResource(R.string.data_del_2),
                    stringResource(R.string.data_del_3),
                    stringResource(R.string.data_del_4),
                )) {
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.data_keeps), style = MaterialTheme.typography.bodyMedium)
                for (line in listOf(
                    stringResource(R.string.data_keep_1),
                    stringResource(R.string.data_keep_2),
                )) {
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.data_unrecoverable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                SettingRow(stringResource(R.string.data_also_secrets), alsoSecrets) {
                    alsoSecrets = it
                }
                if (alsoSecrets) {
                    Text(
                        stringResource(R.string.data_secrets_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                FormField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.data_type_confirm, CONFIRM_WORD)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoSecrets) }, enabled = armed) {
                Text(stringResource(R.string.data_purge_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val CONFIRM_WORD = "PURGE"

/** Compact chip label — the full sentence lives in the hint. */
private fun Retention.Policy.shortLabel(): String = when {
    !isBounded -> "Forever"
    mode == Retention.Mode.Days && effectiveValue >= 365 -> "1 year"
    mode == Retention.Mode.Days -> "${effectiveValue}d"
    else -> "$effectiveValue msg"
}
