package io.alvaroedo.meshconnect.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** When each node last answered us, per own radio (Reachability, upstream 0.11.0). */
@RunWith(RobolectricTestRunner::class)
class ReachProofsTest {

    private lateinit var prefs: Preferences
    private val radioA = "aa".repeat(32)
    private val radioB = "bb".repeat(32)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("meshcore_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Preferences(context)
    }

    @Test
    fun `a proof lands on the radio that earned it, keyed lowercase`() {
        prefs.recordReachProof(radioA, "C9AB", 100)
        assertEquals(mapOf("c9ab" to 100L), prefs.reachProofs(radioA))
        assertTrue(prefs.reachProofs(radioB).isEmpty())
        assertEquals(mapOf("c9ab" to 100L), prefs.reachProofs(radioA.uppercase()))
    }

    @Test
    fun `a newer proof replaces the older one for the same node`() {
        prefs.recordReachProof(radioA, "c9ab", 100)
        prefs.recordReachProof(radioA, "c9ab", 250)
        assertEquals(mapOf("c9ab" to 250L), prefs.reachProofs(radioA))
    }

    @Test
    fun `the store keeps the newest proofs and drops the oldest`() {
        val n = Preferences.MAX_REACH_PROOFS
        for (i in 0..n) prefs.recordReachProof(radioA, "n$i", i.toLong())
        val kept = prefs.reachProofs(radioA)
        assertEquals(n, kept.size)
        assertTrue("n0" !in kept)
        assertEquals(n.toLong(), kept["n$n"])
    }

    @Test
    fun `blank keys record nothing`() {
        prefs.recordReachProof("", "c9ab", 1)
        prefs.recordReachProof(radioA, "", 1)
        assertTrue(prefs.reachProofs(radioA).isEmpty())
    }
}
