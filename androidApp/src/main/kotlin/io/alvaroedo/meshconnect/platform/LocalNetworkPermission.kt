package io.alvaroedo.meshconnect.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17's local network permission, for the TCP transport.
 *
 * Apps targeting Android 17 (API 37) need `ACCESS_LOCAL_NETWORK`, a runtime permission, to
 * reach devices on the LAN, which is where a TCP radio is. It belongs to the "Nearby devices"
 * group with the Bluetooth permissions, so someone who granted Bluetooth is not asked again.
 *
 * Kept apart from [BlePermissions]: those gate the Bluetooth scan in RadioPicker, and someone
 * who declines local network access must still see Bluetooth radios. Asked only when TCP is on
 * (it is off by default), at start with the others and when connecting over TCP.
 */
object LocalNetworkPermission {

    /** Android 17. */
    const val SINCE_SDK = 37

    /** `Manifest.permission.ACCESS_LOCAL_NETWORK`, spelled out so older code paths don't inline a new constant. */
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Whether to ask for it: Android 17 or later, TCP on, not yet granted. Pure, for the tests. */
    fun shouldRequest(sdk: Int, tcpEnabled: Boolean, granted: Boolean): Boolean =
        tcpEnabled && sdk >= SINCE_SDK && !granted

    /** Granted, or not needed on this Android. */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < SINCE_SDK ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
