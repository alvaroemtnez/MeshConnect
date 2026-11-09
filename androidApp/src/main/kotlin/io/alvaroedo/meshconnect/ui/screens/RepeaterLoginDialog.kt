package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import io.github.thatsfguy.meshcore.presentation.AdminSession
import io.github.thatsfguy.meshcore.presentation.roomPasswordHint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel

/**
 * Repeater/room sign-in, as a dialog in front of the hub — the
 * reference client's shape (`repeater_login_dialog` → `repeater_hub`).
 *
 * It replaced a password row welded to the top of a six-tab screen,
 * where it stayed visible and re-typable for the whole session and
 * carried a three-line explanation of what read-only meant. The nuance
 * lives here now, once, at the moment it is relevant
 * (REBUILD-PLAYBOOK §6.3: one line per control, nuance behind a tap).
 *
 * There is no access-level control on this dialog and there must never
 * be one: you present a password and the node decides. See
 * [AdminSession].
 */
@Composable
fun RepeaterLoginDialog(
    vm: MeshCoreViewModel,
    keyHex: String,
    nodeName: String,
    isRoom: Boolean = false,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var savePassword by remember { mutableStateOf(true) }
    var reveal by remember { mutableStateOf(false) }
    var prefilled by remember { mutableStateOf(false) }

    val inFlight by vm.loginInFlight.collectAsState()
    val errors by vm.loginError.collectAsState()
    val busy = keyHex in inFlight
    val error = errors[keyHex]

    // A sealed password fills the field so the common case is one tap.
    LaunchedEffect(keyHex) {
        val saved = vm.savedLoginPassword(keyHex)
        if (!saved.isNullOrEmpty()) {
            password = saved
            prefilled = true
        }
    }

    // The session becomes non-None only when the node has answered, so
    // this is the authoritative "we're done here" signal.
    val sessions by vm.adminSessions.collectAsState()
    val session = sessions[keyHex] ?: AdminSession.None
    LaunchedEffect(session) {
        if (session.signedIn) onDismiss()
    }

    fun submit() {
        if (!busy) vm.repeaterLogin(keyHex, password, savePassword)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.login_title, nodeName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (isRoom) {
                        // The room password is the one its members share;
                        // an admin password also opens the room's tools.
                        stringResource(R.string.login_room_intro)
                    } else {
                        stringResource(R.string.login_node_intro)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (isRoom) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.login_room_hint, io.github.thatsfguy.meshcore.presentation.ROOM_DEFAULT_PASSWORD),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (error != null) vm.clearLoginError(keyHex)
                    },
                    label = { Text(stringResource(if (prefilled) R.string.login_password_saved else R.string.login_password)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    leadingIcon = { Icon(androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_lock), contentDescription = null) },
                    // Material Symbols drawables (generated, no extra
                    // dependency) give the eye icon the old note lacked.
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                androidx.compose.ui.res.painterResource(
                                    if (reveal) io.alvaroedo.meshconnect.R.drawable.ic_visibility_off else io.alvaroedo.meshconnect.R.drawable.ic_visibility,
                                ),
                                stringResource(if (reveal) R.string.login_hide else R.string.login_show),
                            )
                        }
                    },
                    visualTransformation = if (reveal) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = savePassword,
                        onCheckedChange = { savePassword = it },
                        enabled = !busy,
                    )
                    Text(
                        stringResource(R.string.login_keep),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { submit() }, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.login_busy))
                } else {
                    Text(stringResource(R.string.login_sign_in))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}
