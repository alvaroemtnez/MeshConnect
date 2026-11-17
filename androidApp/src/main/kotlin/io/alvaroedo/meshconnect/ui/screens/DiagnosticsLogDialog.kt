package io.alvaroedo.meshconnect.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel

/**
 * The diagnostics log, full screen (2026-11-17). It was a 240 dp box inside Settings →
 * Advanced, newest first, with no way to get the text out: no use for a failed firmware
 * update, which happens on another screen and has to be read by someone else.
 *
 * Oldest first, like a log file, and it follows new lines while it is scrolled to the end,
 * so it can be watched while something runs. Share, save as a .txt, copy, clear. The switch
 * is here too: a log that is off is empty, and this is where someone finds that out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsLogDialog(vm: MeshCoreViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lines by vm.diagnosticsLines.collectAsState()
    var enabled by remember { mutableStateOf(vm.prefs.diagnosticsEnabled) }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = io.alvaroedo.meshconnect.ui.rememberTextClipboard()
    val res = androidx.compose.ui.platform.LocalResources.current

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(vm.diagnosticsExport().toByteArray()) } != null
        }.getOrDefault(false)
        vm.transientMessage.value = res.getString(if (ok) R.string.diag_saved else R.string.diag_save_failed)
    }

    val list = rememberLazyListState()
    // Follow the end while the reader is at it; leave them alone once they scroll up.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(list.isScrollInProgress) {
        if (list.isScrollInProgress) return@LaunchedEffect
        val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        following = last >= list.layoutInfo.totalItemsCount - 1
    }
    LaunchedEffect(lines.size) {
        if (following && lines.isNotEmpty()) list.scrollToItem(lines.size - 1)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.set_diag_log), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.diag_count, lines.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            vm.prefs.diagnosticsEnabled = it
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val has = lines.isNotEmpty()
                    LogAction(R.drawable.ic_share, stringResource(R.string.diag_share), has) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.diag_subject))
                            putExtra(Intent.EXTRA_TEXT, vm.diagnosticsExport())
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                    LogAction(R.drawable.ic_download, stringResource(R.string.diag_save), has) {
                        save.launch(DiagnosticsFile.name(System.currentTimeMillis()))
                    }
                    LogAction(R.drawable.ic_content_copy, stringResource(R.string.copy), has) {
                        clipboard.copy(vm.diagnosticsExport())
                    }
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        enabled = has,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(painterResource(R.drawable.ic_delete_sweep), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.diag_clear))
                    }
                }
                Text(
                    stringResource(R.string.set_diag_redacted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                if (lines.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(if (enabled) R.string.set_log_empty else R.string.diag_off),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    SelectionContainer {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            items(lines.size) { i ->
                                Text(
                                    lines[i],
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.diag_clear_q)) },
            text = { Text(stringResource(R.string.diag_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearDiagnostics()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.diag_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun LogAction(icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label)
    }
}

/** The saved file's name: sortable, no colons (some file pickers refuse them). */
object DiagnosticsFile {
    private val STAMP = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss")

    fun name(epochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        "meshconnect-log-" + STAMP.format(java.time.Instant.ofEpochMilli(epochMs).atZone(zone)) + ".txt"
}
