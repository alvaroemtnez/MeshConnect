package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connected radio's jump to its bootloader (2026-11-17, RAK 4631 with OTAFIX, companion
 * v1.18): the update sat at "asking the node to enter its bootloader" for minutes, nothing
 * after it in the log, the radio still running its own firmware. The link had been closed
 * under the jump by the supervisor's stop(), and no step of the jump had a timeout.
 */
class DfuJumpBoundedTest {

    private val ble = File("../shared/src/androidMain/kotlin/io/github/thatsfguy/meshcore/platform/BleTransport.kt")
        .readText()
        .substringAfter("override suspend fun requestBootloaderReboot()")
        .substringBefore("override suspend fun connect()")

    @Test
    fun `both GATT steps of the jump are bounded and logged`() {
        assertEquals(2, Regex("""withTimeoutOrNull\(JUMP_STEP_TIMEOUT_MS\)""").findAll(ble).count())
        assertTrue(ble.contains("log(\"DFU jump: subscribing to the control point\")"))
        assertTrue(ble.contains("DFU jump: subscribed; writing the jump"))
    }

    @Test
    fun `the radio is handed over, not stopped`() {
        val svc = File("src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt").readText()
            .substringAfter("suspend fun <T> withRadioHandedOver(")
            .substringBefore("return try {")
        assertTrue(svc.contains("supervisor?.handOver()"))
        assertTrue("stop() drops the link under the jump", !svc.contains("supervisor?.stop()"))
    }
}
