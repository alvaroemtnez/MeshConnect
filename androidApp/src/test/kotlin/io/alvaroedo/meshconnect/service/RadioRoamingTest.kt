package io.alvaroedo.meshconnect.service

import io.github.thatsfguy.meshcore.transport.ConnectionMemory
import io.github.thatsfguy.meshcore.transport.SavedNode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A radio at home and one at work, and the app following between them (2026-11-10). */
class RadioRoamingTest {

    private val home = SavedNode(ConnectionMemory.KIND_BLE, "AA:AA:AA:AA:AA:01", name = "Home")
    private val work = SavedNode(ConnectionMemory.KIND_BLE, "AA:AA:AA:AA:AA:02", name = "Work")
    private val car = SavedNode(ConnectionMemory.KIND_BLE, "aa:aa:aa:aa:aa:03", name = "Car")
    private val base = SavedNode(ConnectionMemory.KIND_TCP, "10.0.0.5", port = 5000)
    private val atHome = ConnectionMemory.Ble(home.address, "Home")

    @Test
    fun `moves to the saved radio it hears - the positive control`() {
        val next = RadioRoaming.pick(RadioRoaming.candidates(listOf(home, work), atHome), mapOf(work.address to -70))
        assertEquals(ConnectionMemory.Ble(work.address, "Work"), next)
    }

    @Test
    fun `the strongest of several wins, matching addresses case-insensitively`() {
        val c = RadioRoaming.candidates(listOf(home, work, car), atHome)
        val next = RadioRoaming.pick(c, mapOf(work.address to -90, "AA:AA:AA:AA:AA:03" to -60))
        assertEquals(car.address, next?.address)
    }

    @Test
    fun `never the current radio, never TCP, and nothing when nothing is heard`() {
        val c = RadioRoaming.candidates(listOf(home, work, base), atHome)
        assertEquals(listOf(work), c)
        assertNull(RadioRoaming.pick(c, emptyMap()))
        assertNull(RadioRoaming.pick(c, mapOf(home.address to -40)))
    }

    @Test
    fun `waits before looking, and only from a Bluetooth radio`() {
        val now = 1_000_000L
        val long = now - RadioRoaming.UNREACHABLE_MS
        val brief = now - RadioRoaming.UNREACHABLE_MS + 1
        assertTrue(RadioRoaming.shouldLook(true, atHome, ready = false, busy = false, unreachableSinceMs = long, nowMs = now))
        assertFalse("only briefly away", RadioRoaming.shouldLook(true, atHome, false, false, brief, now))
        assertFalse("connected", RadioRoaming.shouldLook(true, atHome, ready = true, busy = false, unreachableSinceMs = long, nowMs = now))
        assertFalse("firmware update", RadioRoaming.shouldLook(true, atHome, false, busy = true, unreachableSinceMs = long, nowMs = now))
        assertFalse("setting off", RadioRoaming.shouldLook(false, atHome, false, false, long, now))
        assertFalse("TCP", RadioRoaming.shouldLook(true, ConnectionMemory.Tcp("h", 1), false, false, long, now))
        assertFalse("no clock", RadioRoaming.shouldLook(true, atHome, false, false, null, now))
    }

    @Test
    fun `the service runs it and does not count a deliberate disconnect as unreachable`() {
        val svc = File("src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt").readText()
        assertTrue(svc.contains("startRoaming()"))
        assertTrue(svc.contains("if (ready || supervisor == null) null else unreachableSince ?: now"))
        // Filtered by address: an unfiltered scan stops when the screen is off.
        val scanner = File("src/main/kotlin/io/alvaroedo/meshconnect/platform/BleScanner.kt").readText()
        assertTrue(scanner.contains("ScanFilter.Builder().setDeviceAddress("))
    }
}
