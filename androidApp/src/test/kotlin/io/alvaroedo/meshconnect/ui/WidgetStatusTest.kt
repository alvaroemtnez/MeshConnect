package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.widget.WidgetSnapshot
import io.alvaroedo.meshconnect.widget.WidgetSnapshot.Status
import io.alvaroedo.meshconnect.widget.WidgetStatus
import io.alvaroedo.meshconnect.widget.WidgetStatus.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the widget says about the link (1.2, reported 2026-11-12: "you can't really tell if the
 * companion is connected").
 */
class WidgetStatusTest {

    private val now = 1_800_000_000_000L
    private fun snap(status: Status, plaintext: Boolean = false, ageMs: Long = 0) =
        WidgetSnapshot(status = status, plaintext = plaintext, batteryPercent = 87, signalBars = 3, updatedAtMs = now - ageMs)

    @Test
    fun `a live connection is connected - the positive control`() {
        // Without this, every "not connected" assertion below would pass for a widget that
        // never says connected at all.
        assertEquals(Link.Connected, WidgetStatus.of(snap(Status.Connected), now))
        assertTrue(WidgetStatus.showsGauges(Link.Connected))
    }

    @Test
    fun `TCP is connected, and flagged as unencrypted`() {
        assertEquals(Link.ConnectedUnencrypted, WidgetStatus.of(snap(Status.Connected, plaintext = true), now))
        assertTrue(WidgetStatus.showsGauges(Link.ConnectedUnencrypted))
    }

    @Test
    fun `connecting and not connected are told apart`() {
        assertEquals(Link.Connecting, WidgetStatus.of(snap(Status.Connecting), now))
        assertEquals(Link.NotConnected, WidgetStatus.of(snap(Status.Offline), now))
    }

    @Test
    fun `without a link the gauges give way to words`() {
        // The battery and signal are the radio's last readings: shown beside no link, they
        // read as a live radio, which is the report this fixes.
        for (link in listOf(Link.Connecting, Link.NotConnected, Link.NotRunning)) {
            assertFalse("$link shows gauges", WidgetStatus.showsGauges(link))
        }
    }

    @Test
    fun `a stale connected snapshot is not running, not connected`() {
        val old = WidgetSnapshot.STALE_MS + 1
        assertEquals(Link.NotRunning, WidgetStatus.of(snap(Status.Connected, ageMs = old), now))
        assertEquals(Link.NotRunning, WidgetStatus.of(snap(Status.Connected, plaintext = true, ageMs = old), now))
        assertEquals(Link.NotRunning, WidgetStatus.of(snap(Status.Offline, ageMs = old), now))
    }

    @Test
    fun `the staleness boundary is the snapshot's own`() {
        // Exactly at the limit is still live (isStale is strictly greater).
        assertEquals(Link.Connected, WidgetStatus.of(snap(Status.Connected, ageMs = WidgetSnapshot.STALE_MS), now))
        assertEquals(Link.NotRunning, WidgetStatus.of(snap(Status.Connected, ageMs = WidgetSnapshot.STALE_MS + 1), now))
    }

    @Test
    fun `a widget drawn before the service ever ran says not running`() {
        // A fresh install: no saved snapshot, updatedAtMs 0.
        assertEquals(Link.NotRunning, WidgetStatus.of(WidgetSnapshot(), now))
    }
}
