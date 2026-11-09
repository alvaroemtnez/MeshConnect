package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import androidx.navigation.NavController

/**
 * The spokes of [SettingsScreen] — one settings group per screen.
 *
 * The section bodies in `SettingsSections.kt` are unchanged; what
 * changed is that each now owns a screen with a title and a back
 * button instead of being the eleventh chevron on a single scroll.
 *
 * The "get"-backed ones still query the radio on arrival rather than
 * showing a cached value, so what you read is what the node reports
 * NOW — that behaviour moved from [QueryOnExpand] on expand to
 * [QueryOnExpand] on entry, which is the same contract with a better
 * trigger: you can only see the screen by opening it.
 */
@Composable
private fun SettingsSpoke(
    title: String,
    vm: MeshCoreViewModel,
    nav: NavController,
    menuActions: List<MenuAction> = emptyList(),
    scroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar(title = title, vm = vm, nav = nav, menuActions = menuActions)
        },
    ) { padding ->
        val base = Modifier.fillMaxSize().padding(padding)
        Column(
            if (scroll) {
                base.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            } else {
                base.padding(horizontal = 16.dp)
            },
        ) {
            Spacer(Modifier.height(8.dp))
            // Every spoke sits in the same rounded card as the hub's groups, with one
            // spacing rhythm, so the screens read as one app rather than one per author.
            SettingsCard(Modifier) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) { content() }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Query the radio when the screen opens, spinner until the answer
 * lands. [QueryOnExpand] already does exactly this; it is named for
 * the accordion it was written for.
 */
@Composable
private fun QueryOnEntry(
    vm: MeshCoreViewModel,
    query: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    val engineState by vm.engineState.collectAsState()
    QueryOnExpand(enabled = engineState == EngineState.Ready, query = query, content = content)
}

// --- Radio link --------------------------------------------------------

@Composable
fun SettingsConnectionScreen(vm: MeshCoreViewModel, nav: NavController) {
    var showAddNode by remember { mutableStateOf(false) }
    SettingsSpoke(stringResource(R.string.link_connection), vm, nav) {
        ConnectionSection(vm, onAddNode = { showAddNode = true })
    }
    if (showAddNode) {
        AddNodeSheet(vm, tcpEnabled = vm.prefs.tcpEnabled, onDismiss = { showAddNode = false })
    }
}

@Composable
fun SettingsTransportsScreen(vm: MeshCoreViewModel, nav: NavController) {
    var tcpEnabled by remember { mutableStateOf(vm.prefs.tcpEnabled) }
    var showTcpWarning by remember { mutableStateOf(false) }
    SettingsSpoke(stringResource(R.string.hub_transports), vm, nav) {
        TransportsSection(
            vm,
            tcpEnabled = tcpEnabled,
            onTcpToggle = { wanted ->
                if (wanted && !vm.prefs.tcpWarningAccepted) {
                    showTcpWarning = true
                } else {
                    tcpEnabled = wanted
                    vm.prefs.tcpEnabled = wanted
                }
            },
        )
    }
    if (showTcpWarning) {
        TcpSternWarningDialog(
            onAccept = {
                vm.prefs.tcpWarningAccepted = true
                vm.prefs.tcpEnabled = true
                tcpEnabled = true
                showTcpWarning = false
            },
            onCancel = { showTcpWarning = false },
        )
    }
}

@Composable
fun SettingsDevicePinScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_pin), vm, nav) { DevicePinSection(vm) }
}

// --- This node ---------------------------------------------------------

@Composable
fun SettingsIdentityScreen(vm: MeshCoreViewModel, nav: NavController) {
    var showSelfQr by remember { mutableStateOf(false) }
    SettingsSpoke(
        stringResource(R.string.hub_identity),
        vm,
        nav,
        menuActions = listOf(
            MenuAction(stringResource(R.string.chats_advert_zero_hop)) { vm.sendSelfAdvert(flood = false) },
            MenuAction(stringResource(R.string.chats_advert_flood)) { vm.sendSelfAdvert(flood = true) },
            MenuAction(stringResource(R.string.my_qr)) { showSelfQr = true },
        ),
    ) {
        QueryOnEntry(vm, query = { vm.querySelfInfo() }) {
            IdentitySection(vm, onShowSelfQr = { showSelfQr = true })
        }
    }
    if (showSelfQr) {
        SelfQrDialog(vm, onDismiss = { showSelfQr = false })
    }
}

@Composable
fun SettingsRadioScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_radio), vm, nav) {
        QueryOnEntry(vm, query = { vm.querySelfInfo() }) { RadioSection(vm) }
    }
}

@Composable
fun SettingsClockScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_clock), vm, nav) { ClockSection(vm) }
}

@Composable
fun SettingsPoliciesScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_policies), vm, nav) {
        // Policies span two frames: SELF_INFO (telemetry/advert/multi-ack
        // bytes) and DEVICE_INFO (path-hash width).
        QueryOnEntry(
            vm,
            query = {
                vm.querySelfInfo()
                vm.queryDeviceInfo()
            },
        ) { PoliciesSection(vm) }
    }
}

@Composable
fun SettingsAutoAddScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_autoadd), vm, nav) {
        QueryOnEntry(vm, query = { vm.queryAutoAddConfig() }) { AutoAddSection(vm) }
    }
}

@Composable
fun SettingsCustomVarsScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_customvars), vm, nav) {
        QueryOnEntry(vm, query = { vm.queryCustomVars() }) { CustomVarsSection(vm) }
    }
}

// --- Messaging ---------------------------------------------------------

@Composable
fun SettingsChannelsScreen(vm: MeshCoreViewModel, nav: NavController) {
    var editChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    SettingsSpoke(stringResource(R.string.hub_channels), vm, nav) {
        ChannelsSection(vm, onEdit = { editChannel = it })
    }
    editChannel?.let { ch ->
        ChannelEditSheet(vm, ch, onDismiss = { editChannel = null })
    }
}

@Composable
fun SettingsBlockingScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_blocking), vm, nav) { BlockingSection(vm) }
}

// --- App ---------------------------------------------------------------

/**
 * Appearance, notifications, privacy and the diagnostics log, as
 * titled sections on one screen.
 *
 * These were four screens. Each held one control, and "Privacy and
 * network" measured one toggle against 70% empty space on a 384dp
 * phone — a spoke with less in it than its own tile. Plain section
 * headers, deliberately NOT [ExpandableSection]: the accordion is what
 * made the original Settings unreadable, and there is little enough
 * here that everything fits without hiding any of it.
 */
@Composable
fun SettingsAppScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_app), vm, nav, scroll = false) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroupHeader(stringResource(R.string.set_appearance))
            AppearanceSection(vm)
            AddWidgetButton()
            SettingsGroupHeader(stringResource(R.string.set_notifications))
            NotificationsSection(vm)
            SettingsGroupHeader(stringResource(R.string.set_privacy))
            PrivacySection(vm)
            SettingsGroupHeader(stringResource(R.string.set_diagnostics))
            DiagnosticsSection(vm)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun SettingsBackupScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_backup), vm, nav) { BackupSection(vm) }
}

@Composable
fun SettingsDataScreen(vm: MeshCoreViewModel, nav: NavController) {
    SettingsSpoke(stringResource(R.string.hub_data), vm, nav) { DataSection(vm) }
}

/**
 * Ask the launcher to pin the home-screen widget. The launcher shows its
 * own confirmation; a launcher that can't pin hides the button.
 */
@Composable
private fun AddWidgetButton() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) return
    androidx.compose.material3.FilledTonalButton(
        onClick = {
            manager.requestPinAppWidget(
                android.content.ComponentName(context, io.alvaroedo.meshconnect.widget.MeshWidgetReceiver::class.java),
                null,
                null,
            )
        },
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_add),
            null,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 6.dp))
        Text(androidx.compose.ui.res.stringResource(io.alvaroedo.meshconnect.R.string.widget_add))
    }
}
