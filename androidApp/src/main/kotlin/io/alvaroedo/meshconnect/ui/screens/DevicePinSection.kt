package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.protocol.DevicePin

/**
 * Change the radio's Bluetooth pairing PIN.
 *
 * Worth its own place rather than a row among the radio parameters:
 * it is the only setting here that is a *credential*, the default is
 * public, and getting it wrong costs you Bluetooth access to the node.
 *
 * The current PIN is never shown, because the firmware offers no way
 * to read it. An app that displayed "123456" because that is the usual
 * default would be guessing at a security setting, which is the one
 * place guessing is least acceptable.
 */
@Composable
fun DevicePinSection(vm: MeshCoreViewModel) {
    val engineState by vm.engineState.collectAsState()
    val deviceInfo by vm.deviceInfo.collectAsState()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var bluetoothPrompt by remember { mutableStateOf(false) }
    var rebootPrompt by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (engineState != EngineState.Ready) {
        HintText(stringResource(R.string.pin_connect))
        return
    }

    Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.titleSmall)
    // The node reports its configured PIN in DEVICE_INFO. 0.6.4 claimed
    // this could not be read and showed nothing — the four bytes were
    // simply being skipped by the parser.
    HintText(stringResource(R.string.pin_configured, DevicePin.describe(deviceInfo?.blePin)))
    ExpandableHint(
        stringResource(R.string.pin_default, DevicePin.FACTORY_DEFAULT),
    ) {
        DetailText(
            stringResource(R.string.pin_detail),
        )
    }
    Spacer(Modifier.height(8.dp))
    FormField(
        value = pin,
        onValueChange = { typed -> pin = typed.filter { it in '0'..'9' }.take(DevicePin.LENGTH) },
        label = { Text(stringResource(R.string.pin_new, DevicePin.LENGTH)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        supportingText = {
            val why = DevicePin.rejection(pin)
            when {
                why != null -> Text(why)
                DevicePin.isUseDefault(pin) ->
                    Text(stringResource(R.string.pin_clears))
                DevicePin.isFactoryDefault(pin) ->
                    Text(stringResource(R.string.pin_factory))
            }
        },
        isError = DevicePin.rejection(pin) != null,
        modifier = Modifier.fillMaxWidth(),
    )
    ButtonFlowRow {
        FilledTonalButton(
            onClick = { confirm = true },
            enabled = DevicePin.isAcceptable(pin),
        ) { Text(stringResource(R.string.pin_change)) }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.pin_change_title)) },
            text = {
                Text(
                    (
                        if (DevicePin.isUseDefault(pin)) {
                            stringResource(R.string.pin_will_default)
                        } else {
                            stringResource(R.string.pin_will_use, pin)
                        }
                        ) + "\n\n" + stringResource(R.string.pin_consequences),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDevicePin(pin)
                    confirm = false
                    pin = ""
                    rebootPrompt = true
                }) { Text(stringResource(R.string.pin_change_it)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (rebootPrompt) {
        AlertDialog(
            onDismissRequest = { rebootPrompt = false },
            title = { Text(stringResource(R.string.pin_reboot_title)) },
            text = {
                Text(
                    stringResource(R.string.pin_reboot_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rebootRadio()
                    rebootPrompt = false
                    bluetoothPrompt = true
                }) { Text(stringResource(R.string.pin_reboot_now)) }
            },
            dismissButton = {
                TextButton(onClick = { rebootPrompt = false }) { Text(stringResource(R.string.pin_later)) }
            },
        )
    }

    if (bluetoothPrompt) {
        AlertDialog(
            onDismissRequest = { bluetoothPrompt = false },
            title = { Text(stringResource(R.string.pin_unpair_title)) },
            text = {
                Text(
                    stringResource(R.string.pin_unpair_body),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    bluetoothPrompt = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text(stringResource(R.string.pin_open_bt)) }
            },
            dismissButton = {
                TextButton(onClick = { bluetoothPrompt = false }) { Text(stringResource(R.string.pin_not_now)) }
            },
        )
    }
}
