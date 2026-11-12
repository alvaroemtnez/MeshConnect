package io.alvaroedo.meshconnect.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.platform.BlePermissions
import io.alvaroedo.meshconnect.platform.LocalNetworkPermission
import io.alvaroedo.meshconnect.platform.BleScanner
import io.alvaroedo.meshconnect.platform.DiscoveredDevice
import io.alvaroedo.meshconnect.platform.UsbDevices
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.transport.ConnectionMemory

/**
 * Every way to reach a radio, in one list: saved radios first (one tap reconnects), then
 * Bluetooth radios nearby, USB, and TCP when it is enabled. Used by the first run, the
 * no-radio state, the top bar's connection card and Settings → Connection, so connecting
 * is the same gesture everywhere.
 *
 * Bluetooth permission is asked for here, when the user is looking for a radio, rather
 * than at first launch with no context.
 */
@Composable
fun RadioPicker(
    vm: MeshCoreViewModel,
    onPicked: () -> Unit = {},
    showSaved: Boolean = true,
) {
    val context = LocalContext.current
    val engineState by vm.engineState.collectAsState()
    val label by vm.connectionLabel.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val saved = remember(refresh, engineState) { vm.prefs.savedNodes() }
    val currentKey = remember(engineState, refresh) { vm.prefs.reconnectKey() }

    var granted by remember { mutableStateOf(BlePermissions.allGranted(context)) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = BlePermissions.allGranted(context)
    }
    var discovered by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    if (vm.prefs.bleEnabled && granted) {
        LaunchedEffect(Unit) {
            runCatching { BleScanner.scan(context).collect { discovered = it } }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (engineState == EngineState.Connecting || engineState == EngineState.Handshaking) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    label?.let { stringResource(R.string.picker_connecting_to, it) } ?: stringResource(R.string.status_connecting),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (showSaved && saved.isNotEmpty()) {
            PickerHeader(stringResource(R.string.set_saved_nodes))
            for (node in saved) {
                val isCurrent = node.key == currentKey && engineState == EngineState.Ready
                PickerRow(
                    icon = transportIcon(node.kind),
                    title = node.name ?: node.address,
                    subtitle = if (isCurrent) {
                        stringResource(R.string.picker_connected)
                    } else {
                        node.address + (node.port?.let { ":$it" } ?: "")
                    },
                    highlighted = isCurrent,
                    enabled = node.kind != ConnectionMemory.KIND_USB,
                ) {
                    if (!isCurrent) {
                        vm.connectSaved(node)
                        onPicked()
                    }
                }
            }
        }

        if (vm.prefs.bleEnabled) {
            PickerHeader(stringResource(R.string.add_ble_nearby))
            if (!granted) {
                HintText(stringResource(R.string.picker_allow_ble_hint))
                FilledTonalButton(onClick = { permissions.launch(BlePermissions.required()) }) {
                    Icon(painterResource(R.drawable.ic_bluetooth_searching), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.picker_allow_ble))
                }
            } else {
                val savedAddresses = saved.map { it.address.lowercase() }.toSet()
                val fresh = discovered.filter { it.address.lowercase() !in savedAddresses || !showSaved }
                if (fresh.isEmpty()) {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        HintText(stringResource(R.string.add_scanning))
                    }
                }
                for (dev in fresh) {
                    PickerRow(
                        icon = R.drawable.ic_bluetooth,
                        title = dev.name ?: dev.address,
                        subtitle = "${dev.rssi} dBm",
                    ) {
                        vm.connectBle(dev.address, dev.name)
                        onPicked()
                    }
                }
            }
        }

        if (vm.prefs.usbEnabled) {
            val usbDevices = remember { UsbDevices.attached(context) }
            if (usbDevices.isNotEmpty()) {
                PickerHeader(stringResource(R.string.add_usb))
                for (attached in usbDevices) {
                    PickerRow(
                        icon = R.drawable.ic_usb,
                        title = attached.device.productName ?: attached.device.deviceName,
                        subtitle = attached.driver,
                    ) {
                        UsbDevices.requestPermission(context, attached.device) { ok ->
                            if (ok) vm.connectUsb(attached.device)
                        }
                        onPicked()
                    }
                }
            }
        }

        if (vm.prefs.tcpEnabled) {
            PickerHeader(stringResource(R.string.add_tcp))
            var host by remember { mutableStateOf(vm.prefs.lastTcpHost ?: "192.168.40.10") }
            var port by remember { mutableStateOf(vm.prefs.lastTcpPort.takeIf { it != 0 }?.toString() ?: "5000") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FormField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.add_host)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FormField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.add_port)) },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                )
            }
            HintText(stringResource(R.string.set_tcp_warning))
            // Android 17+: a radio on the local network needs ACCESS_LOCAL_NETWORK. Asked here,
            // at the tap that needs it; the connection starts only once it is granted, and a
            // refusal says why TCP cannot connect instead of failing in the background.
            var pendingTcp by remember { mutableStateOf<Pair<String, Int>?>(null) }
            var lanRefused by remember { mutableStateOf(false) }
            val lanPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                val target = pendingTcp
                pendingTcp = null
                lanRefused = !ok
                if (ok && target != null) {
                    vm.connectTcp(target.first, target.second)
                    onPicked()
                }
            }
            if (lanRefused) HintText(stringResource(R.string.add_tcp_local_network))
            FilledTonalButton(onClick = {
                val p = port.toIntOrNull()
                if (host.isNotBlank() && p != null && p in 1..65535) {
                    if (LocalNetworkPermission.shouldRequest(
                            android.os.Build.VERSION.SDK_INT, tcpEnabled = true,
                            granted = LocalNetworkPermission.granted(context),
                        )
                    ) {
                        pendingTcp = host.trim() to p
                        lanPermission.launch(LocalNetworkPermission.PERMISSION)
                    } else {
                        vm.connectTcp(host.trim(), p)
                        onPicked()
                    }
                }
            }) { Text(stringResource(R.string.add_tcp_connect)) }
        }
    }
}

/** [RadioPicker] in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioPickerSheet(vm: MeshCoreViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.radio_picker_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            RadioPicker(vm, onPicked = onDismiss)
        }
    }
}

@DrawableRes
private fun transportIcon(kind: String): Int = when (kind) {
    ConnectionMemory.KIND_TCP -> R.drawable.ic_lan
    ConnectionMemory.KIND_USB -> R.drawable.ic_usb
    else -> R.drawable.ic_bluetooth
}

@Composable
private fun PickerHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickerRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                FieldShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (highlighted) {
            Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(4.dp))
}

/**
 * What an empty screen shows when there is no radio. Detached: say so and offer the
 * picker right there, with saved radios one tap away. While a saved radio is coming back
 * (Connecting / Handshaking) it says that instead and offers nothing to pick: an
 * auto-reconnect is not a reason to choose another radio. Changing radios is the
 * connection card's job (top bar) or Settings → Connection.
 */
@Composable
fun NoRadioState(vm: MeshCoreViewModel, modifier: Modifier = Modifier) {
    val engineState by vm.engineState.collectAsState()
    val label by vm.connectionLabel.collectAsState()
    var picker by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (engineState == EngineState.Detached) R.drawable.ic_link_off else R.drawable.ic_bluetooth_searching),
                null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        if (engineState == EngineState.Detached) {
            Text(stringResource(R.string.empty_no_radio_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_no_radio_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = { picker = true }) {
                Text(stringResource(R.string.onb_start))
            }
            val saved = remember { vm.prefs.savedNodes().filter { it.kind != ConnectionMemory.KIND_USB } }
            if (saved.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth()) {
                    PickerHeader(stringResource(R.string.set_saved_nodes))
                    for (node in saved.take(3)) {
                        PickerRow(
                            icon = transportIcon(node.kind),
                            title = node.name ?: node.address,
                            subtitle = node.address + (node.port?.let { ":$it" } ?: ""),
                        ) { vm.connectSaved(node) }
                    }
                }
            }
        } else if (engineState != EngineState.Ready) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    label?.let { stringResource(R.string.picker_connecting_to, it) } ?: stringResource(R.string.status_connecting),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    if (picker) RadioPickerSheet(vm, onDismiss = { picker = false })
}
