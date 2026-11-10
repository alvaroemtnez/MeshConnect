package io.alvaroedo.meshconnect.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Start with the phone (2026-11-10): when it may start, and that the manifest delivers it. */
class BootReceiverTest {

    @Test
    fun `starts only with the setting on, setup done and a radio to reach`() {
        assertTrue(BootReceiver.shouldStart(enabled = true, setupComplete = true, hasRadio = true))
        assertFalse(BootReceiver.shouldStart(enabled = false, setupComplete = true, hasRadio = true))
        assertFalse(BootReceiver.shouldStart(enabled = true, setupComplete = false, hasRadio = true))
        assertFalse("nothing to reconnect to", BootReceiver.shouldStart(true, true, hasRadio = false))
    }

    @Test
    fun `the lock screen no longer stops it, because the database key works after first unlock`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/service/BootReceiver.kt").readText()
        assertFalse(src.contains("isDeviceLocked"))
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
