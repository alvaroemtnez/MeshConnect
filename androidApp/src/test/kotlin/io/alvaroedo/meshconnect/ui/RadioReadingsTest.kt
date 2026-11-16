package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.RadioReadings
import io.alvaroedo.meshconnect.ui.model.RadioReadings.Noise
import io.github.thatsfguy.meshcore.protocol.RadioStats
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The top-bar sheet's radio readings (upstream issue #6, rules from upstream 0.10.16). */
class RadioReadingsTest {

    private fun radio(noise: Int = -110, rssi: Int = -90, snr: Double = 6.5) = RadioStats.Radio(noise, rssi, snr, 10, 20)
    private fun packets(received: Long) = RadioStats.Packets(received, 3, 0, 0, 0, 0, 1)

    @Test
    fun `zero is not measured yet, the clamp is a floor, anything else is the value`() {
        assertEquals(Noise.Measuring, RadioReadings.noise(0))
        assertEquals(Noise.AtClamp(-120), RadioReadings.noise(-120))
        assertEquals(Noise.AtClamp(-120), RadioReadings.noise(-125))
        assertEquals(Noise.Value(-119), RadioReadings.noise(-119))
        assertEquals(Noise.Value(-95), RadioReadings.noise(-95))
    }

    @Test
    fun `counters decide whether anything was heard, whatever the last reading says`() {
        assertTrue(RadioReadings.nothingHeard(radio(rssi = -90), packets(0)))
        assertFalse(RadioReadings.nothingHeard(radio(rssi = 0, snr = 0.0), packets(5)))
    }

    @Test
    fun `without counters only an all-zero reading means nothing yet`() {
        assertTrue(RadioReadings.nothingHeard(radio(rssi = 0, snr = 0.0), null))
        assertFalse(RadioReadings.nothingHeard(radio(rssi = 0, snr = -2.0), null))
        assertFalse(RadioReadings.nothingHeard(radio(rssi = -80, snr = 0.0), null))
    }

    @Test
    fun `the range skips unmeasured samples and needs two`() {
        val r = RadioReadings.NoiseRange()
        r.add(0)
        r.add(-110)
        assertNull(r.range())
        r.add(0)
        r.add(-104)
        assertEquals(-110 to -104, r.range())
    }

    @Test
    fun `the range forgets the oldest past the cap`() {
        val r = RadioReadings.NoiseRange()
        r.add(-130)
        repeat(RadioReadings.NoiseRange.MAX_SAMPLES) { r.add(-100) }
        assertEquals(-100 to -100, r.range())
    }

    @Test
    fun `the sheet polls through SignalPoll and stops when it says so`() {
        val bar = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/AppTopBar.kt").readText()
        assertTrue(bar.contains("val keepGoing = poll.onResult(result)"))
        assertTrue(bar.contains("if (!keepGoing) break"))
    }
}
