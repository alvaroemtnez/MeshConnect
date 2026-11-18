package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.model.DmRequestInbox
import io.alvaroedo.meshconnect.ui.model.DmRequestInbox.Answers
import io.alvaroedo.meshconnect.ui.model.DmRequestInbox.Tap
import io.github.thatsfguy.meshcore.presentation.DmRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Requests to us, on any channel, and which have been answered (2026-11-18).
 *
 * The defect behind this: 1.3.0 remembered an answered request by the asker's key alone, for
 * every radio and for good. Radio A asked radio B once on the beta and was answered; from then
 * on every request from A reached B as a notification that opened nothing, with no prompt.
 */
class DmRequestInboxTest {

    private val radioA = "a1".repeat(32)
    private val radioB = "b2".repeat(32)
    private val marta = "aa".repeat(32)
    private val pau = "bb".repeat(32)
    private val now = 2_000_000_000L
    private val nowMs = now * 1000

    private fun msg(
        sender: String,
        text: String,
        ts: Long,
        channel: Int = 39,
        outgoing: Boolean = false,
        receivedAtMs: Long = ts * 1000,
    ) = MessageEntity(
        selfKey = radioB, kind = "ch", peerKey = channel.toString(), senderName = sender, text = text,
        timestamp = ts, receivedAt = receivedAtMs, outgoing = outgoing, status = 0, ackHash = null,
        contentKey = null, snr = null,
    )

    private fun pending(messages: List<MessageEntity>, answers: Answers = Answers.NONE, blocked: Set<String> = emptySet()) =
        DmRequestInbox.pending(messages, "Bea", radioB, answers, { it in blocked }, now)

    @Test
    fun `requests to us surface once per asker, newest kept, oldest asker first`() {
        val messages = listOf(
            msg("Pau", DmRequest.request("Bea", pau)!!, now - 50),
            msg("Marta", DmRequest.request("Bea", marta)!!, now - 100),
            msg("Marta", DmRequest.request("bea", marta)!!, now - 10),
            // Not to us, our own, too old, and not a request at all: none of them surface.
            msg("Joan", DmRequest.request("Ana", pau)!!, now - 5),
            msg("Bea", DmRequest.request("Bea", radioB)!!, now - 5, outgoing = true),
            msg("Old", DmRequest.request("Bea", "cc".repeat(32))!!, now - DmRequest.Outstanding.LIFETIME_SEC - 1),
            msg("Marta", "hello", now - 1),
        )
        val p = pending(messages)
        assertEquals(listOf(pau, marta), p.map { it.keyHex })
        assertEquals(now - 10, p.last().atSeconds)
    }

    @Test
    fun `a request on a group is a request too, answered on the channel it came on`() {
        val p = pending(listOf(msg("Marta", DmRequest.request("Bea", marta)!!, now - 10, channel = 3)))
        assertEquals(listOf(3), p.map { it.channelIndex })
        // Asked on two channels: the later one is the one answered.
        val two = pending(
            listOf(
                msg("Marta", DmRequest.request("Bea", marta)!!, now - 100, channel = 39),
                msg("Marta", DmRequest.request("Bea", marta)!!, now - 10, channel = 3),
            ),
        )
        assertEquals(listOf(3), two.map { it.channelIndex })
    }

    @Test
    fun `a blocked asker does not surface`() {
        val messages = listOf(msg("Marta", DmRequest.request("Bea", marta)!!, now - 10))
        assertEquals(emptyList<DmRequestInbox.Pending>(), pending(messages, blocked = setOf(marta)))
        assertEquals(1, pending(messages).size)
    }

    // ---- answers ----

    @Test
    fun `asked, answered, asked again - the second request asks again`() {
        // The case seen on 1.3.0: the positive control for everything below that hides.
        val first = msg("Marta", DmRequest.request("Bea", marta)!!, now - 3600, receivedAtMs = nowMs - 3_600_000)
        val answers = Answers.NONE.answered(radioB, marta, nowMs - 3_000_000)
        assertEquals(emptyList<DmRequestInbox.Pending>(), pending(listOf(first), answers))
        val again = msg("Marta", DmRequest.request("Bea", marta)!!, now - 60, receivedAtMs = nowMs - 60_000)
        assertEquals(listOf(marta), pending(listOf(first, again), answers).map { it.keyHex })
        assertEquals(now - 60, pending(listOf(first, again), answers).single().atSeconds)
    }

    @Test
    fun `an answer is by our clock, so a slow sender clock cannot date a new request before it`() {
        // Sender timestamp an hour BEFORE the answer, but received after it: a new request.
        val answers = Answers.NONE.answered(radioB, marta, nowMs - 600_000)
        val slow = msg("Marta", DmRequest.request("Bea", marta)!!, now - 3600, receivedAtMs = nowMs - 60_000)
        assertEquals(listOf(marta), pending(listOf(slow), answers).map { it.keyHex })
    }

    @Test
    fun `an answer on one radio leaves the other radio's requests alone`() {
        val answers = Answers.NONE.answered(radioA, marta, nowMs)
        assertTrue(answers.isAnswered(radioA, marta, nowMs - 1))
        assertFalse(answers.isAnswered(radioB, marta, nowMs - 1))
        assertFalse(answers.isAnswered(radioA, pau, nowMs - 1))
        // Keys are compared whatever their case.
        assertTrue(answers.isAnswered(radioA.uppercase(), marta.uppercase(), nowMs))
        assertFalse(answers.isAnswered(radioA, marta, nowMs + 1))
    }

    @Test
    fun `1_3_0's answered set hides what it hid, on every radio, and nothing later`() {
        val legacy = Answers.fromLegacy(setOf(marta), nowMs)
        assertTrue(legacy.isAnswered(radioA, marta, nowMs - 1))
        assertTrue(legacy.isAnswered(radioB, marta, nowMs))
        assertFalse(legacy.isAnswered(radioB, marta, nowMs + 1))
        assertFalse(legacy.isAnswered(radioB, pau, nowMs - 1))
    }

    @Test
    fun `answers lapse once nothing they cover can still be shown`() {
        val old = Answers.NONE.answered(radioB, marta, nowMs - Answers.KEEP_MS - 1)
        val kept = old.answered(radioB, pau, nowMs)
        assertEquals(setOf(Answers.slot(radioB, pau)), kept.toMap().keys)
        // KEEP_MS outlasts a request's week, so nothing it hid can come back.
        assertTrue(Answers.KEEP_MS > DmRequest.Outstanding.LIFETIME_SEC * 1000)
    }

    // ---- tapping the notification ----

    private val waiting = DmRequestInbox.Pending("Marta", marta, now - 10, nowMs - 10_000, 39)

    private fun tap(
        pending: List<DmRequestInbox.Pending> = emptyList(),
        answers: Answers = Answers.NONE,
        isContact: Boolean = false,
        notifiedFor: String? = radioB,
        connected: String = radioB,
    ) = DmRequestInbox.tap(marta, notifiedFor, "Blue Base", connected, pending, answers, isContact, postedAtMs = nowMs - 10_000)

    @Test
    fun `a waiting request's tap brings its prompt up`() {
        assertEquals(Tap.Show(waiting), tap(pending = listOf(waiting)))
        // Keys may arrive in either case.
        assertEquals(Tap.Show(waiting), DmRequestInbox.tap(marta.uppercase(), radioB.uppercase(), null, radioB, listOf(waiting), Answers.NONE, false, 0))
    }

    @Test
    fun `answered since - their chat if they were added, else a note`() {
        val answered = Answers.NONE.answered(radioB, marta, nowMs)
        assertEquals(Tap.OpenChat(marta), tap(answers = answered, isContact = true))
        assertEquals(Tap.Answered, tap(answers = answered, isContact = false))
        // An answer given BEFORE the notification was posted does not cover it.
        assertEquals(Tap.Gone, tap(answers = Answers.NONE.answered(radioB, marta, nowMs - 20_000), isContact = true))
    }

    @Test
    fun `a newer request waiting wins over an older one answered`() {
        val answered = Answers.NONE.answered(radioB, marta, nowMs)
        assertEquals(Tap.Show(waiting), tap(pending = listOf(waiting), answers = answered, isContact = true))
    }

    @Test
    fun `a request to another of our radios says so, and does not answer from this one`() {
        assertEquals(Tap.OtherRadio("Blue Base"), tap(pending = listOf(waiting), notifiedFor = radioA))
        // A notification without a radio is read against whichever is connected.
        assertEquals(Tap.Show(waiting), tap(pending = listOf(waiting), notifiedFor = null))
    }

    @Test
    fun `gone - lapsed, or never answered here`() {
        assertEquals(Tap.Gone, tap())
    }

    // ---- wiring ----

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `the notification carries the request, and notifies before the channel rules`() {
        val svc = src("service/MeshCoreService.kt")
        val notify = svc.substringAfter("private fun postMessageNotification(").substringBefore("val title =")
        val request = notify.indexOf("postDmRequestNotification(senderName, text)")
        assertTrue("requests no longer notify as themselves", request >= 0)
        assertTrue(request < notify.indexOf("in internalSlots()"))
        assertTrue("a muted group would silence a request to you", request < notify.indexOf("isChannelMuted"))
        val post = svc.substringAfter("private fun postDmRequestNotification(").substringBefore("\n    }\n")
        for (extra in listOf("EXTRA_DM_REQUEST_FROM", "EXTRA_DM_REQUEST_TO", "EXTRA_DM_REQUEST_RADIO", "EXTRA_DM_REQUEST_AT")) {
            assertTrue(extra, post.contains("MainActivity.$extra"))
        }
        // One PendingIntent per asker: request code 0 for all of them shared one, and the
        // latest extras would have rewritten where every notification points.
        assertTrue(post.contains("PendingIntent.getActivity(\n            this, id,"))
        assertTrue(post.contains("isKeyBlocked(asked.requesterKeyHex)"))
    }

    @Test
    fun `tapping it reaches the prompt, but a rotation does not replay it`() {
        val main = src("MainActivity.kt")
        val create = main.substringAfter("override fun onCreate(").substringBefore("setContent {")
        assertTrue(create.substringAfter("if (savedInstanceState == null) {").substringBefore("}").contains("dmRequestFrom(intent)"))
        assertTrue(main.substringAfter("override fun onNewIntent(").substringBefore("\n    }\n").contains("dmRequestFrom(intent)"))
        assertTrue(main.contains("vm.openDmRequestFromNotification("))
        val prompt = src("ui/screens/DmRequestUi.kt").substringAfter("fun DmRequestPrompt(")
        assertTrue("the tapped request is not shown first", prompt.substringBefore("?: return").contains("vm.dmRequestFocus"))
        assertTrue(src("ui/screens/ScanConfirmations.kt").contains("DmRequestPrompt(vm)"))
    }

    @Test
    fun `an answer clears its notification and its prompt`() {
        val vm = src("ui/MeshCoreViewModel.kt")
        val mark = vm.substringAfter("private fun markDmRequestHandled(").substringBefore("\n    }\n")
        assertTrue(mark.contains("clearDmRequestNotification(requesterKeyHex)"))
        assertTrue(mark.contains(".answered(self, requesterKeyHex,"))
        assertTrue(mark.contains("dmAnswersRevision.value++"))
        // The thread's buttons read the same record, by when the request arrived.
        assertTrue(src("ui/screens/ConversationScreen.kt").contains("!dmAnswers.isAnswered(myKey, it.requesterKeyHex, m.receivedAt)"))
        // Blocking re-reads the waiting requests.
        assertTrue(vm.substringAfter("fun setBlocked(").substringBefore("\n    }\n").contains("dmAnswersRevision.value++"))
    }

    @Test
    fun `accepting twice at once adds and answers once`() {
        val accept = src("ui/MeshCoreViewModel.kt").substringAfter("fun acceptDmRequest(").substringBefore("fun declineDmRequest(")
        assertTrue(accept.indexOf("if (!acceptingDm.add(asker)) return") in 0 until accept.indexOf("viewModelScope.launch"))
        assertTrue(accept.contains("acceptingDm.remove(asker)"))
    }
}

/** "Not now" puts off one request; the asker's next one asks again (found driving it, 2026-11-18). */
class DmRequestLaterTest {
    @Test
    fun `a later request from the same person is not put off`() {
        val key = "aa".repeat(32)
        val first = DmRequestInbox.Pending("Marta", key, 100, 100_000, 39)
        val again = first.copy(atSeconds = 500, receivedAtMs = 500_000)
        assertFalse(first.laterKey == again.laterKey)
        assertEquals(first.laterKey, first.copy(keyHex = key.uppercase(), name = "marta").laterKey)
        val prompt = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/DmRequestUi.kt").readText()
        assertTrue(prompt.contains("pending.firstOrNull { it.laterKey !in later }"))
        assertTrue(File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
            .contains("dmRequestsLater.value = dmRequestsLater.value + request.laterKey"))
    }
}
