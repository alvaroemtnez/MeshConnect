package io.alvaroedo.meshconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.alvaroedo.meshconnect.storage.Preferences

/**
 * Starts the radio service when the phone boots, and again after the app is updated (an update
 * stops it), so messages arrive without opening the app first — the way KDE Connect and
 * Thunderbird keep their connections. Off with Settings → Connection → "Start with the phone".
 *
 * Only when there is a radio to go back to: with nothing remembered, the service would sit in
 * the notification bar connecting to nothing.
 *
 * The lock screen does not matter. BOOT_COMPLETED arrives after the first unlock since boot,
 * and the database key works from then on whether the screen is locked or not (see
 * DatabaseKey). Until 2026-11-10 it needed an unlocked screen, and this receiver refused to
 * start behind one rather than run on an empty stand-in database.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val prefs = Preferences(context)
        if (!shouldStart(prefs.startAtBoot, prefs.setupComplete, prefs.resolveReconnect() != null)) {
            Log.i(TAG, "Not starting on ${intent.action} (setting, setup or radio)")
            return
        }
        runCatching { MeshCoreService.start(context) }
            .onFailure { Log.w(TAG, "Could not start the service on ${intent.action}", it) }
    }

    companion object {
        private const val TAG = "MeshConnectBoot"
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        /** Pure, so the rule is tested without a device. */
        fun shouldStart(enabled: Boolean, setupComplete: Boolean, hasRadio: Boolean): Boolean =
            enabled && setupComplete && hasRadio
    }
}
