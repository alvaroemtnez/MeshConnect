package io.alvaroedo.meshconnect.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A received message whose sender's clock claims the future (seen on the beta, 2026-11-17:
 * a Public post dated 2075, pinned to the bottom of the thread and the channel to the top
 * of Chats). The companion reports only the sender's timestamp, so the app decides.
 */
class MessageClockTest {

    private val received = 1_794_942_785_123L // 2026-11-17 19:13:05 UTC
    private val receivedSec = received / 1000

    @Test
    fun `a claim from 2075 is placed at receive time and kept`() {
        val y2075 = 3_313_000_000L
        val (shown, claim) = MessageClock.place(y2075, received)
        assertEquals(receivedSec, shown)
        assertEquals(y2075, claim)
    }

    @Test
    fun `ordinary drift is believed`() {
        // Positive control: a sender a few minutes fast keeps its own time.
        val fast = receivedSec + MessageClock.FUTURE_TOLERANCE_SEC
        assertEquals(fast to null, MessageClock.place(fast, received))
        // One second past the tolerance is not.
        val over = receivedSec + MessageClock.FUTURE_TOLERANCE_SEC + 1
        assertEquals(receivedSec to over, MessageClock.place(over, received))
    }

    @Test
    fun `the past is believed, queued messages are rightly old`() {
        val yesterday = receivedSec - 86_400
        val (shown, claim) = MessageClock.place(yesterday, received)
        assertEquals(yesterday, shown)
        assertNull(claim)
    }

    @Test
    fun `reactions still hash the sender's own timestamp`() {
        val m = MessageEntity(
            selfKey = "s", kind = "ch", peerKey = "0", senderName = "A", text = "hi",
            timestamp = receivedSec, receivedAt = received, outgoing = false, status = 2,
            ackHash = null, contentKey = null, snr = null, claimedTimestamp = 3_313_000_000L,
        )
        assertEquals(3_313_000_000L, m.senderTimestamp)
        assertEquals(receivedSec, m.copy(claimedTimestamp = null).senderTimestamp)
        val repo = File("src/main/kotlin/io/alvaroedo/meshconnect/storage/MessageRepository.kt").readText()
        assertTrue(repo.contains("Reactions.targetHash(target.senderTimestamp,"))
        assertTrue(repo.contains("ReactionRouting.Candidate(it.id, it.senderTimestamp,"))
        val conv = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ConversationScreen.kt").readText()
        assertTrue(conv.contains("Reactions.targetHash(m.senderTimestamp,"))
    }

    @Test
    fun `both received kinds are placed, and stored rows are fixed by the migration`() {
        val repo = File("src/main/kotlin/io/alvaroedo/meshconnect/storage/MessageRepository.kt").readText()
        assertEquals(2, Regex("""MessageClock\.place\(event\.timestamp, now\)""").findAll(repo).count())
        val db = File("src/main/kotlin/io/alvaroedo/meshconnect/storage/MeshCoreDatabase.kt").readText()
        val mig = db.substringAfter("MIGRATION_20_21 = object").substringBefore("\n        }\n")
        assertTrue(mig.contains("ADD COLUMN `claimedTimestamp`"))
        assertTrue(mig.contains("WHERE `outgoing` = 0 AND `timestamp` > `receivedAt` / 1000 + "))
    }
}
