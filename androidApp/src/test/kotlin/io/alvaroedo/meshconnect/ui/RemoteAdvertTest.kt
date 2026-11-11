package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.RemoteAdvert
import io.alvaroedo.meshconnect.ui.model.RemoteAdvert.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** Replies are CommonCLI.cpp's own strings (MeshCore main, lines 202-209). */
class RemoteAdvertTest {

    @Test
    fun `the commands are the firmware's`() {
        assertEquals("advert.zerohop", RemoteAdvert.command(nearby = true))
        assertEquals("advert", RemoteAdvert.command(nearby = false))
    }

    @Test
    fun `current firmware does what was asked`() {
        assertEquals(Outcome.Nearby, RemoteAdvert.outcome(true, "OK - zerohop advert sent"))
        assertEquals(Outcome.Mesh, RemoteAdvert.outcome(false, "OK - Advert sent"))
        assertEquals(Outcome.Nearby, RemoteAdvert.outcome(true, "> OK - zerohop advert sent\n"))
    }

    @Test
    fun `older firmware floods when asked for nearby, and the app says so`() {
        // No advert.zerohop handler: the "advert" prefix match takes it and floods.
        assertEquals(Outcome.MeshInsteadOfNearby, RemoteAdvert.outcome(true, "OK - Advert sent"))
    }

    @Test
    fun `silence and errors are not reported as sent`() {
        assertEquals(Outcome.NoAnswer, RemoteAdvert.outcome(true, null))
        assertEquals(Outcome.NoAnswer, RemoteAdvert.outcome(false, "  "))
        assertEquals(Outcome.Refused, RemoteAdvert.outcome(false, "Error: unknown command"))
        // An OK in other words is not a refusal.
        assertEquals(Outcome.Nearby, RemoteAdvert.outcome(true, "OK"))
        assertEquals(Outcome.Mesh, RemoteAdvert.outcome(false, "> OK"))
    }
}
