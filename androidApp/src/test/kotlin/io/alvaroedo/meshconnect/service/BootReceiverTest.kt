package io.alvaroedo.meshconnect.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Start with the phone (2026-11-10): when it may start, and that the manifest delivers it. */
class BootReceiverTest {

    @Test
    fun `starts only with the setting on, setup done, a radio to reach, and the phone unlocked`() {
        assertTrue(BootReceiver.shouldStart(enabled = true, setupComplete = true, hasRadio = true, locked = false))
        assertFalse(BootReceiver.shouldStart(enabled = false, setupComplete = true, hasRadio = true, locked = false))
        assertFalse(BootReceiver.shouldStart(enabled = true, setupComplete = false, hasRadio = true, locked = false))
        assertFalse("nothing to reconnect to", BootReceiver.shouldStart(true, true, hasRadio = false, locked = false))
        // The database key needs an unlocked device; started behind the lock screen the
        // service would run on an empty stand-in database.
        assertFalse(BootReceiver.shouldStart(true, true, true, locked = true))
    }

    @Test
    fun `the manifest asks for boot and registers the receiver for boot and updates`() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertTrue(m.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        val receiver = m.substringAfter("android:name=\".service.BootReceiver\"").substringBefore("</receiver>")
        assertTrue(receiver.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(receiver.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(receiver.contains("android:exported=\"false\""))
    }
}
