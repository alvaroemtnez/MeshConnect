package io.alvaroedo.meshconnect.service

import android.app.KeyguardManager
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
 * Not while the device is locked. The database key is sealed by a Keystore key that requires
 * an unlocked device, so a service started behind the lock screen could not open the message
 * history and would run on an empty stand-in database for the whole session. BOOT_COMPLETED
 * arrives once the user has unlocked, so in practice this is the normal path; if it was
 * delivered late and the phone is locked again, the next app open starts the service instead.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val prefs = Preferences(context)
        val locked = context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
        if (!shouldStart(prefs.startAtBoot, prefs.setupComplete, prefs.resolveReconnect() != null, locked)) {
            Log.i(TAG, "Not starting on ${intent.action} (setting, setup, radio or lock)")
            return
        }
        runCatching { MeshCoreService.start(context) }
            .onFailure { Log.w(TAG, "Could not start the service on ${intent.action}", it) }
    }

    companion object {
        private const val TAG = "MeshConnectBoot"
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)

        /** Pure, so the rule is tested without a device. */
        fun shouldStart(enabled: Boolean, setupComplete: Boolean, hasRadio: Boolean, locked: Boolean): Boolean =
            enabled && setupComplete && hasRadio && !locked
    }
}
