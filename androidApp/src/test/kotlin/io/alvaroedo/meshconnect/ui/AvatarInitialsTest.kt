package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.AvatarInitials
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialsTest {

    @Test
    fun twoWordsGiveTwoInitials() {
        assertEquals("ÁE", AvatarInitials.of("Álvaro Edo"))
        assertEquals("DN", AvatarInitials.of("Donk-N8FWG"))
        assertEquals("BB", AvatarInitials.of("blue_base"))
        assertEquals("WB", AvatarInitials.of("  wall-Base"))
        // Only the first two words count.
        assertEquals("AB", AvatarInitials.of("Alpha Bravo Charlie"))
    }

    @Test
    fun emojiAndSymbolsInFrontAreSkipped() {
        assertEquals("DN", AvatarInitials.of("🐄Donk-N8FWG"))
        assertEquals("F", AvatarInitials.of("🐸 FROG"))
        assertEquals("K", AvatarInitials.of("★ KCEST"))
    }

    @Test
    fun aHumpCountsAsASecondWord() {
        assertEquals("BB", AvatarInitials.of("BlueBase"))
        assertEquals("SM", AvatarInitials.of("SpartaMI"))
        // All caps or all lower is one word, so one letter.
        assertEquals("E", AvatarInitials.of("ENGNR"))
        assertEquals("É", AvatarInitials.of("éclair"))
        // A digit then a capital is not a hump.
        assertEquals("B", AvatarInitials.of("BLUET1000"))
    }

    @Test
    fun nothingToReadFallsBackToTheGlyph() {
        assertEquals("🔥", AvatarInitials.of("🔥"))
        assertEquals("?", AvatarInitials.of(""))
        assertEquals("?", AvatarInitials.of("   "))
        assertEquals("?", AvatarInitials.of("\uD83D"))
    }
}
