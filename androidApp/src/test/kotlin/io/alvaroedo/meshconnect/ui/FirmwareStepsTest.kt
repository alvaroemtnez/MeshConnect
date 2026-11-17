package io.alvaroedo.meshconnect.ui

import io.github.thatsfguy.meshcore.firmware.DfuProgress
import io.github.thatsfguy.meshcore.firmware.FirmwareRole
import io.github.thatsfguy.meshcore.firmware.FirmwareVersion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Back goes on the firmware screen.
 *
 * Reported on the beta (2026-11-17): from Settings → Firmware → "another
 * node is in update mode" → Check for firmware, Back no longer worked
 * properly. Every step was one destination as far as navigation knew, so
 * Back left the screen, and the step it was on turned up again on the
 * other firmware screen because both share one controller.
 */
class FirmwareStepsTest {

    private val versions = FirmwareUi.ChoosingVersion(
        versions = listOf(FirmwareVersion(FirmwareRole.Repeater, "v1.17.0", "repeater-v1.17.0")),
        currentVersion = null,
        boardName = "RAK 4631",
    )
    private val builds = FirmwareUi.Choosing(emptyList(), emptyList(), "RAK 4631", "v1.17.0")

    @Test
    fun `the start and a running transfer let Back leave the screen`() {
        assertNull(FirmwareSteps.previous(FirmwareUi.Idle(), versions, builds))
        // Leaving does not stop a transfer, and stepping "back" out of one
        // would hide that it is still writing.
        assertNull(FirmwareSteps.previous(FirmwareUi.Running(DfuProgress.Preparing), versions, builds))
    }

    @Test
    fun `builds step back to versions, versions to the start`() {
        assertEquals(versions, FirmwareSteps.previous(builds, versions, builds))
        assertTrue(FirmwareSteps.previous(versions, versions, null) is FirmwareUi.Idle)
    }

    @Test
    fun `the confirmation steps back to the list it was picked from`() {
        val confirm = FirmwareUi.Downloading("x.zip")
        assertEquals(builds, FirmwareSteps.previous(confirm, versions, builds))
        // A release whose only build for the board was taken without asking.
        assertEquals(versions, FirmwareSteps.previous(confirm, versions, null))
        // A .zip from storage came from no list.
        assertTrue(FirmwareSteps.previous(confirm, null, null) is FirmwareUi.Idle)
    }

    @Test
    fun `loading a release steps back to the versions`() {
        assertEquals(versions, FirmwareSteps.previous(FirmwareUi.Checking, versions, null))
        assertTrue(FirmwareSteps.previous(FirmwareUi.Checking, null, null) is FirmwareUi.Idle)
    }

    @Test
    fun `an outcome goes back to the start`() {
        assertTrue(FirmwareSteps.previous(FirmwareUi.Finished(null), versions, builds) is FirmwareUi.Idle)
        assertTrue(FirmwareSteps.previous(FirmwareUi.Failed("x", null), versions, builds) is FirmwareUi.Idle)
    }

    @Test
    fun `Settings has one firmware screen, not a button into a second one`() {
        val screen = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/FirmwareScreen.kt").readText()
        val main = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()
        assertTrue(
            "Settings → Firmware no longer chooses its target in place",
            main.contains("""composable("settings/firmware") { FirmwareScreen(vm, nav, chooseTarget = true) }"""),
        )
        assertTrue(
            "the firmware screen navigates into a copy of itself again",
            !screen.contains("nav.navigate(\"firmware/node\")"),
        )
        assertTrue("Back no longer walks the steps", screen.contains("BackHandler"))
    }

    @Test
    fun `a new target drops the previous one's step`() {
        val controller = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/FirmwareUpdateController.kt").readText()
        val aim = controller.substringAfter("fun aimAt(").substringBefore("fun reset()")
        assertTrue(
            "aimAt no longer resets a stale step when the target changes",
            aim.contains("if (changed && _state.value !is FirmwareUi.Running) reset()"),
        )
    }
}
