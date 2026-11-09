package io.alvaroedo.meshconnect.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Where the phone is, for "Share my location". Android's own
 * LocationManager: GPS first (it works off-grid), then whatever else the
 * phone has. No Google Play services and no network lookups. Adapted from
 * Skypost's AndroidLocator.
 */
object PhoneLocator {

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun granted(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** A fresh fix within [timeoutMs], else one up to 15 minutes old; null without permission or signal. */
    @SuppressLint("MissingPermission") // checked by granted()
    suspend fun fix(context: Context, timeoutMs: Long = 20_000): Pair<Double, Double>? {
        if (!granted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        providers.firstOrNull()
            ?.let { p -> withTimeoutOrNull(timeoutMs) { current(context, lm, p) } }
            ?.let { return it.latitude to it.longitude }
        val cutoff = System.currentTimeMillis() - 15 * 60_000
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { it.time >= cutoff }
            .maxByOrNull { it.time }
            ?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    private suspend fun current(context: Context, lm: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, cancel, ContextCompat.getMainExecutor(context)) { loc ->
                if (cont.isActive) cont.resume(loc)
            }
        }
}
