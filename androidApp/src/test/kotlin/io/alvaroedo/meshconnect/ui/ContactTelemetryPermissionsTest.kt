package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-contact telemetry switches against the global policy.
 *
 * The firmware reads a contact's flag only when the global mode for that
 * kind is "per contact" (companion_radio/MyMesh.cpp, onContactRequest);
 * Nobody and Everyone ignore it. Asked for on the beta (2026-11-17): the
 * switch stays, greyed out, with one short red line, and per contact gets
 * no line at all.
 */
class ContactTelemetryPermissionsTest {

    private val body = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/NodesScreen.kt")
        .readText()
        .substringAfter("private fun ContactPermissions(")
        .substringBefore("var muted by remember")

    @Test
    fun `the switch decides only under per contact`() {
        assertTrue(body.contains("val decides = globalMode == 1"))
        assertTrue("the switch is not disabled by the global policy", body.contains("enabled = decides"))
    }

    @Test
    fun `the overriding policy is named in red, and per contact says nothing`() {
        assertTrue(body.contains("if (!decides)"))
        assertTrue(body.contains("colorScheme.error"))
        assertFalse("per contact grew its line back", body.contains("perm_policy_flags"))
    }

    @Test
    fun `base telemetry is called device telemetry`() {
        val table = File("../scripts/strings_table.py").readText()
        assertTrue(table.contains("\"perm_battery\": (\"Device telemetry\""))
    }
}
