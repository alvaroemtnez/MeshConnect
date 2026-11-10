package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.LowBatteryAlert
import io.alvaroedo.meshconnect.ui.model.LowBatteryAlert.Notice
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LowBatteryAlertTest {

    /** Run a series of readings, returning every notice that fired, in order. */
    private fun run(vararg readings: Int?): List<Notice> {
        var state = LowBatteryAlert.State()
        val out = mutableListOf<Notice>()
        for (r in readings) {
            val step = LowBatteryAlert.step(state, r)
            state = step.state
            step.notice?.let { out += it }
        }
        return out
    }

    // Positive controls: every "says nothing" test below would pass if the
    // alert never fired at all.

    @Test
    fun `crossing twenty percent says so`() {
        assertEquals(listOf(Notice.Low), run(60, 40, 21, 20))
    }

    @Test
    fun `crossing ten percent says the stronger thing`() {
        assertEquals(listOf(Notice.Low, Notice.Critical), run(25, 18, 12, 10))
    }

    @Test
    fun `a radio first seen almost empty gets one alert, not two`() {
        assertEquals(listOf(Notice.Critical), run(7))
    }

    @Test
    fun `charging takes the alert back`() {
        assertEquals(listOf(Notice.Low, Notice.Recovered), run(19, 25, 30))
    }

    // Quiet cases.

    @Test
    fun `a healthy battery says nothing`() {
        assertEquals(emptyList<Notice>(), run(100, 80, 50, 21, 50))
    }

    @Test
    fun `a cell hovering at the threshold does not alert every poll`() {
        // Load changes the voltage; 19-21-20-19 is one battery, not four alerts.
        assertEquals(listOf(Notice.Low), run(21, 19, 21, 20, 19, 22, 20, 28, 19))
    }

    @Test
    fun `it re-arms only after climbing clear of the band`() {
        assertEquals(
            listOf(Notice.Low, Notice.Recovered, Notice.Low),
            run(19, 29, 18, 30, 19),
        )
    }

    @Test
    fun `critical re-arms on its own band while still low`() {
        // Down to 9, charged up to 20 (still within LOW's band), down to 9:
        // the radio really did go nearly flat twice.
        assertEquals(
            listOf(Notice.Critical, Notice.Critical),
            run(9, 20, 9),
        )
        // But 9 → 15 → 9 is the same discharge wobbling.
        assertEquals(listOf(Notice.Critical), run(9, 15, 9))
    }

    @Test
    fun `no estimate means no decision`() {
        // BatteryLevel returns null for a 2S pack, a USB rail or no sense
        // line. An alert from a guessed percentage would be invented.
        assertEquals(emptyList<Notice>(), run(null, null))
        assertEquals(listOf(Notice.Low), run(19, null, 19))
        assertEquals(listOf(Notice.Low), run(19, null, null, 18))
    }

    @Test
    fun `the service reads the existing poll and never asks the radio itself`() {
        val service = File(
            "src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt",
        ).readText()
        val watcher = service.substringAfter("private fun watchRadioBattery()")
            .substringBefore("private fun postBatteryNotification")
        assertTrue(watcher.contains("engine.battery"))
        assertTrue("the alert must not add radio traffic", !watcher.contains("refreshBattery"))
        assertTrue("the alert must not add wake-ups", !watcher.contains("delay("))
    }
}
