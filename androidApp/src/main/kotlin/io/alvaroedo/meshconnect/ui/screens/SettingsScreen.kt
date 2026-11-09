package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import io.github.thatsfguy.meshcore.presentation.appSubtitle
import io.github.thatsfguy.meshcore.presentation.blockingSubtitle
import io.github.thatsfguy.meshcore.presentation.channelsSubtitle
import io.github.thatsfguy.meshcore.presentation.connectionSubtitle
import io.github.thatsfguy.meshcore.presentation.firmwareSubtitle
import io.github.thatsfguy.meshcore.presentation.identitySubtitle
import io.github.thatsfguy.meshcore.presentation.radioSubtitle
import io.github.thatsfguy.meshcore.presentation.settingsGroups
import io.github.thatsfguy.meshcore.presentation.transportsSubtitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.storage.DatabaseKey
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState

/**
 * Settings — a hub of grouped tiles, each opening one screen.
 *
 * It was eleven expandable sections on a single scroll, with the App
 * section holding another eight surfaces inside it. Nothing designed
 * that; it accumulated, one feature at a time, because adding a
 * section to an existing screen costs nothing and making a screen
 * feels like work (LESSONS §13, REBUILD-PLAYBOOK §6.2).
 *
 * The structure is [settingsGroups]; the tiles report their current
 * value so the common question — *is TCP on? is the map fetching
 * tiles? what frequency am I on?* — is answered without opening
 * anything.
 *
 * Per the app-wide header contract, device actions with no natural
 * home live in the ⋮ menu. Adverts and the node QR are NOT among them
 * any more: they belong to Identity, and now have a screen to live on.
 */
@Composable
fun SettingsScreen(vm: MeshCoreViewModel, nav: NavController) {
    var rebootConfirm by remember { mutableStateOf(false) }

    val engineState by vm.engineState.collectAsState()
    val connectionLabel by vm.connectionLabel.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val channels by vm.dbChannels.collectAsState()
    val blocked by vm.blockedKeys.collectAsState()
    val isReady = engineState == EngineState.Ready

    // Read once per composition — these are plain prefs, not flows, and
    // the hub recomposes when it is returned to.
    val subtitles: Map<String, String> = mapOf(
        "connection" to SettingsHubText.connection(engineState, connectionLabel),
        "transports" to SettingsHubText.transports(
            ble = vm.prefs.bleEnabled,
            usb = vm.prefs.usbEnabled,
            tcp = vm.prefs.tcpEnabled,
        ),
        "identity" to SettingsHubText.identity(isReady, self?.name),
        "radio" to if (self?.freqKhz?.let { it > 0 } == true) {
            radioSubtitle(self?.freqKhz, self?.sf, self?.txPowerDbm)
        } else {
            stringResource(R.string.hub_radio_connect)
        },
        "firmware" to SettingsHubText.firmware(
            current = vm.deviceInfo.collectAsState().value?.firmwareVersion?.takeIf { it.isNotBlank() },
            latest = vm.firmware.latestVersion.collectAsState().value,
            capable = vm.firmwareUpdatesSupported(),
            connected = isReady,
        ),
        "channels" to SettingsHubText.channels(channels.size),
        "blocking" to SettingsHubText.blocking(blocked.size),
        "app" to SettingsHubText.app(
            theme = vm.prefs.theme,
            notifications = vm.prefs.notificationsEnabled,
            encrypted = DatabaseKey.encryptionUnavailableReason == null,
        ),
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_settings),
                vm = vm,
                menuActions = emptyList(),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            for ((index, group) in settingsGroups().withIndex()) {
                GroupLabel(SettingsHubText.group(index, group.title))
                SettingsCard {
                    for (tile in group.tiles) {
                        SettingsTileRow(
                            title = SettingsHubText.title(tile.route, tile.title),
                            subtitle = subtitles[tile.route] ?: SettingsHubText.subtitle(tile.route, tile.subtitle),
                            // NOT dimmed. Driving this disconnected, six of the ten rows on
                            // the first screen were greyed; the subtitle already says
                            // "Connect to a radio", which is the whole message.
                            dimmed = false,
                            icon = settingsIcon(tile.route),
                            onClick = { nav.navigate("settings/${tile.route}") },
                        )
                    }
                    // Rebooting the radio is a device action with a home: This node. It
                    // lived in the overflow menu, where nobody looks for it.
                    if (isNodeGroup(group.tiles.map { it.route }) && isReady) {
                        SettingsTileRow(
                            title = stringResource(R.string.hub_reboot_menu).trimEnd('…'),
                            subtitle = stringResource(R.string.hub_reboot_sub),
                            dimmed = false,
                            icon = R.drawable.ic_restart,
                            destructive = true,
                            onClick = { rebootConfirm = true },
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (rebootConfirm) {
        AlertDialog(
            onDismissRequest = { rebootConfirm = false },
            title = { Text(stringResource(R.string.hub_reboot_title)) },
            text = { Text(stringResource(R.string.hub_reboot_body)) },
            icon = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_restart), null) },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        vm.rebootRadio()
                        rebootConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.hub_reboot)) }
            },
            dismissButton = {
                TextButton(onClick = { rebootConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun isNodeGroup(routes: List<String>) = "firmware" in routes

/** The icon for each hub route. */
@androidx.annotation.DrawableRes
internal fun settingsIcon(route: String): Int = when (route) {
    "connection" -> R.drawable.ic_link
    "transports" -> R.drawable.ic_swap_horiz
    "pin" -> R.drawable.ic_password
    "identity" -> R.drawable.ic_badge
    "radio" -> R.drawable.ic_antenna
    "clock" -> R.drawable.ic_schedule
    "policies" -> R.drawable.ic_policy
    "autoadd" -> R.drawable.ic_person_add
    "customvars" -> R.drawable.ic_tune
    "firmware" -> R.drawable.ic_system_update
    "channels" -> R.drawable.ic_tag
    "blocking" -> R.drawable.ic_block
    "app" -> R.drawable.ic_palette
    "backup" -> R.drawable.ic_backup
    "data" -> R.drawable.ic_database
    "about" -> R.drawable.ic_info
    else -> R.drawable.ic_settings
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A rounded group of rows: the hub's groups, and the blocks inside a settings screen. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier.padding(horizontal = 16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

/** Also used by the firmware picker, which is a list of the same shape. */
@Composable
internal fun SettingsTileRow(
    title: String,
    subtitle: String,
    dimmed: Boolean,
    onClick: () -> Unit,
    @androidx.annotation.DrawableRes icon: Int? = null,
    destructive: Boolean = false,
) {
    val warn = subtitle.startsWith("⚠")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.ui.res.painterResource(icon),
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    destructive -> MaterialTheme.colorScheme.error
                    dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (icon == null) {
            Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
