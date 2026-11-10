package io.alvaroedo.meshconnect.platform

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import io.github.thatsfguy.meshcore.platform.BleTransport
import io.github.thatsfguy.meshcore.protocol.MESHCORE_BLE_NAME_PREFIXES
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Active scan for MeshCore companion radios. A device qualifies when it
 * advertises the Nordic UART Service UUID OR its advertised name starts
 * with a known MeshCore prefix (`MeshCore-`, `Whisper-`, `WisCore-`,
 * `Seeed`, `Lilygo`, `HT-`, `LowMesh_MC_`) — some firmwares omit the
 * service UUID from the advertisement, so the scan runs unfiltered and
 * matches in the callback.
 *
 * Structure carried over from reticulum-mobile-app's BleScanner.
 * Caller MUST hold BLUETOOTH_SCAN (Android 12+); [BlePermissions]
 * handles the prompt.
 */
data class DiscoveredDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
)

object BleScanner {

    /** Delegates to the shared rule so iOS cannot disagree with us. */
    fun matchesMeshCoreName(name: String?): Boolean =
        io.github.thatsfguy.meshcore.protocol.matchesMeshCoreName(name)

    /** Cold flow emitting the running set of discovered radios,
     *  deduplicated by MAC, sorted by RSSI. Cancelling stops the scan. */
    @SuppressLint("MissingPermission")
    fun scan(context: Context): Flow<List<DiscoveredDevice>> = callbackFlow {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = mgr.adapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val nusUuid = ParcelUuid(BleTransport.NUS_SERVICE_UUID)
        val seen = mutableMapOf<String, DiscoveredDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val displayName = dev.name ?: result.scanRecord?.deviceName
                val advertisesNus = result.scanRecord?.serviceUuids?.contains(nusUuid) == true
                if (!advertisesNus && !matchesMeshCoreName(displayName)) return
                seen[dev.address] = DiscoveredDevice(
                    name = displayName,
                    address = dev.address,
                    rssi = result.rssi,
                )
                trySend(seen.values.sortedByDescending { it.rssi })
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Unfiltered scan + callback-side matching (see class KDoc).
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    /**
     * Listen for [timeoutMs] for the given radios only, returning address → RSSI of those
     * heard. Filtered by address, which Android keeps running with the screen off (an
     * unfiltered scan is paused then) — this runs from the service, usually in a pocket.
     * Empty without Bluetooth or without BLUETOOTH_SCAN.
     */
    @SuppressLint("MissingPermission")
    suspend fun listenFor(context: Context, addresses: Collection<String>, timeoutMs: Long): Map<String, Int> {
        if (addresses.isEmpty()) return emptyMap()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return emptyMap()
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = mgr?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return emptyMap()
        val heard = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.address?.let { heard[it.uppercase()] = result.rssi }
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
        }
        val filters = addresses.mapNotNull { a ->
            runCatching { android.bluetooth.le.ScanFilter.Builder().setDeviceAddress(a.uppercase()).build() }.getOrNull()
        }
        if (filters.isEmpty()) return emptyMap()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        if (runCatching { scanner.startScan(filters, settings, callback) }.isFailure) return emptyMap()
        try {
            kotlinx.coroutines.delay(timeoutMs)
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
        return heard.toMap()
    }
}
