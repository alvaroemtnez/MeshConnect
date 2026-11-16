package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.ReachText
import io.alvaroedo.meshconnect.ui.model.ReachText.Line
import io.github.thatsfguy.meshcore.presentation.Reachability
import io.github.thatsfguy.meshcore.presentation.Reachability.Evidence
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sentence under "Can you talk to them?", picked by the same branches as upstream's
 * Reachability.of, and the places an answer is recorded as proof.
 */
class ReachTextTest {

    private val now = 10_000_000L

    private fun line(e: Evidence) = ReachText.line(ReachText.Info(Reachability.of(e, now), e), now)

    @Test
    fun `a fresh confirmation says when`() {
        assertEquals(Line.Confirmed(now - 3600), line(Evidence(confirmedAt = now - 3600)))
    }

    @Test
    fun `a repeater relaying us is two-way without a date, even with an old proof`() {
        assertEquals(Line.RepeatsYou, line(Evidence(repeatsUs = true)))
        assertEquals(Line.RepeatsYou, line(Evidence(repeatsUs = true, confirmedAt = now - Reachability.FRESH_SECONDS - 1)))
    }

    @Test
    fun `an old confirmation is two-way before`() {
        val at = now - Reachability.FRESH_SECONDS - 1
        assertEquals(Line.ConfirmedBefore(at), line(Evidence(confirmedAt = at)))
    }

    @Test
    fun `the remaining states each get their own line`() {
        assertEquals(Line.TheyHeardYou, line(Evidence(routeFromThem = true, heardThem = true)))
        assertEquals(Line.NotReached, line(Evidence(heardThem = true, tried = true)))
        assertEquals(Line.NoSignOfHearing, line(Evidence(heardThem = true)))
        assertEquals(Line.NoAnswer, line(Evidence(tried = true)))
        assertEquals(Line.None, line(Evidence()))
    }

    private val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()

    @Test
    fun `an answered ping, sign-in, telemetry or check is recorded as proof`() {
        val trace = vm.substringAfter("suspend fun traceNode(").substringBefore("fun resetPath(")
        assertTrue(trace.contains("if (forwards && result != null) recordReachProof(keyHex)"))
        assertTrue(vm.contains("if (outcome.answered) recordReachProof(keyHex)"))
        val telemetry = vm.substringAfter("suspend fun fetchTelemetry(").substringBefore("suspend fun cliQuery(")
        assertTrue(telemetry.contains("recordReachProof(keyHex)"))
        val check = vm.substringAfter("suspend fun checkReachability(").substringBefore("    /** Switch an app-side")
        val discover = check.indexOf("svc.engine.discoverPath(key)")
        val record = check.indexOf("recordReachProof(keyHex)")
        // Only after an answer: the null branch returns before it.
        assertTrue(discover in 0 until record)
        assertTrue(check.substring(discover, record).contains("?: return ReachCheck.NoAnswer(asksPermission)"))
    }

    @Test
    fun `a pinned route proves nothing about them, and pinning re-reads it`() {
        assertTrue(vm.contains("routeFromThem = !PathCodec.decodePathLen(c.pathLen).isFlood && !prefs.isRoutePinned(c.keyHex)"))
        assertTrue(vm.contains("pinnedRoutesTick.value++"))
    }

    @Test
    fun `adding a far heard-only node asks first, from the list and from its sheet`() {
        val nodes = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/NodesScreen.kt").readText()
        val farRows = nodes.substringAfter("items(farHeard").substringBefore("item(key = \"heard_divider\")")
        assertTrue(farRows.contains("onAdd = { confirmFarAdd = d }"))
        assertTrue(nodes.contains("farAddRequest = node"))
    }
}
