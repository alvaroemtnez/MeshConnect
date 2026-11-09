package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.github.thatsfguy.meshcore.engine.EngineState

/**
 * Translated text for the Settings hub. The hub's structure and its
 * decisions (which subtitle wins, when TCP is flagged) stay in upstream's
 * SettingsHubModel in shared/, which is tested there; only the words are
 * produced here, in the same order and on the same conditions, so shared/
 * stays as upstream wrote it (FORK.md).
 */
object SettingsHubText {

    @Composable
    fun group(index: Int, fallback: String): String = when (index) {
        0 -> stringResource(R.string.hub_group_link)
        1 -> stringResource(R.string.hub_group_node)
        2 -> stringResource(R.string.hub_group_messaging)
        3 -> stringResource(R.string.hub_group_app)
        else -> fallback
    }

    @Composable
    fun title(route: String, fallback: String): String = when (route) {
        "connection" -> stringResource(R.string.link_connection)
        "transports" -> stringResource(R.string.hub_transports)
        "pin" -> stringResource(R.string.hub_pin)
        "identity" -> stringResource(R.string.hub_identity)
        "radio" -> stringResource(R.string.hub_radio)
        "clock" -> stringResource(R.string.hub_clock)
        "policies" -> stringResource(R.string.hub_policies)
        "autoadd" -> stringResource(R.string.hub_autoadd)
        "customvars" -> stringResource(R.string.hub_customvars)
        "firmware" -> stringResource(R.string.hub_firmware)
        "channels" -> stringResource(R.string.hub_channels)
        "blocking" -> stringResource(R.string.hub_blocking)
        "app" -> stringResource(R.string.hub_app)
        "backup" -> stringResource(R.string.hub_backup)
        "data" -> stringResource(R.string.hub_data)
        "about" -> stringResource(R.string.about_title)
        else -> fallback
    }

    /** The static line under a tile, used when it has no live value. */
    @Composable
    fun subtitle(route: String, fallback: String): String = when (route) {
        "connection" -> stringResource(R.string.hub_connection_sub)
        "transports" -> stringResource(R.string.hub_transports_sub)
        "pin" -> stringResource(R.string.hub_pin_sub)
        "identity" -> stringResource(R.string.hub_identity_sub)
        "radio" -> stringResource(R.string.hub_radio_sub)
        "clock" -> stringResource(R.string.hub_clock_sub)
        "policies" -> stringResource(R.string.hub_policies_sub)
        "autoadd" -> stringResource(R.string.hub_autoadd_sub)
        "customvars" -> stringResource(R.string.hub_customvars_sub)
        "firmware" -> stringResource(R.string.hub_firmware_sub)
        "channels" -> stringResource(R.string.hub_channels_sub)
        "blocking" -> stringResource(R.string.hub_blocking_sub)
        "app" -> stringResource(R.string.hub_app_sub)
        "backup" -> stringResource(R.string.hub_backup_sub)
        "data" -> stringResource(R.string.hub_data_sub)
        "about" -> stringResource(R.string.hub_about_sub)
        else -> fallback
    }

    // Live values: same conditions as SettingsHubModel's functions.

    @Composable
    fun connection(state: EngineState, label: String?): String = when (state) {
        EngineState.Ready -> stringResource(R.string.setup_connected_to, label ?: "")
        EngineState.Handshaking -> stringResource(R.string.status_handshake)
        EngineState.Connecting -> stringResource(R.string.status_connecting)
        EngineState.Detached -> stringResource(R.string.hub_not_connected)
    }

    @Composable
    fun transports(ble: Boolean, usb: Boolean, tcp: Boolean): String {
        val on = buildList {
            if (ble) add("Bluetooth")
            if (usb) add("USB")
            if (tcp) add("TCP")
        }
        return when {
            on.isEmpty() -> stringResource(R.string.hub_transports_none)
            tcp -> stringResource(R.string.hub_transports_tcp, on.joinToString(", "))
            else -> on.joinToString(", ")
        }
    }

    @Composable
    fun identity(ready: Boolean, name: String?): String = when {
        !ready -> stringResource(R.string.hub_connect_first)
        name.isNullOrBlank() -> stringResource(R.string.hub_no_name)
        else -> name
    }

    @Composable
    fun firmware(current: String?, latest: String?, capable: Boolean, connected: Boolean): String = when {
        !connected -> stringResource(R.string.hub_connect_first)
        !capable -> (current?.let { "$it · " } ?: "") + stringResource(R.string.hub_fw_no_ota)
        current == null -> stringResource(R.string.hub_fw_no_version)
        latest != null && io.github.thatsfguy.meshcore.firmware.VersionOrder.isNewer(latest, current) ->
            stringResource(R.string.hub_fw_available, current, latest)
        latest == null -> current
        else -> stringResource(R.string.hub_fw_current, current)
    }

    @Composable
    fun channels(count: Int): String =
        if (count == 0) stringResource(R.string.hub_channels_none) else pluralStringResource(R.plurals.hub_channels, count, count)

    @Composable
    fun blocking(count: Int): String =
        if (count == 0) stringResource(R.string.hub_blocking_none) else pluralStringResource(R.plurals.hub_blocked, count, count)

    @Composable
    fun app(theme: String, notifications: Boolean, encrypted: Boolean): String {
        if (!encrypted) return stringResource(R.string.hub_app_unencrypted)
        val look = stringResource(
            when (theme) {
                "light" -> R.string.theme_light
                "dark" -> R.string.theme_dark
                else -> R.string.theme_system
            },
        )
        val alerts = stringResource(if (notifications) R.string.hub_notifications_on else R.string.hub_notifications_off)
        return "$look · $alerts"
    }
}
