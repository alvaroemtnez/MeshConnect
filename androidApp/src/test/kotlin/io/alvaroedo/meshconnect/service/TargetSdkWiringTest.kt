package io.alvaroedo.meshconnect.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.2.0 targets Android 17 (API 37). These are the decisions that only show on a device, pinned
 * in the source:
 *
 * - The radio service is a `connectedDevice` foreground service, never `dataSync`. Targeting
 *   Android 15+, a `dataSync` service may not start from BOOT_COMPLETED (so "Start with the
 *   phone" would throw) and is stopped after six hours a day (so the link would drop). Until
 *   1.2.0 it was the fallback for TCP-only users who denied Bluetooth.
 * - `CHANGE_NETWORK_STATE` is declared: it is what lets that TCP-only user start a
 *   `connectedDevice` service at all.
 * - `ACCESS_LOCAL_NETWORK` is declared, and TCP connects only through its check (Android 17+).
 */
class TargetSdkWiringTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val service = File("src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt").readText()
    private val gradle = File("build.gradle.kts").readText()
    private val picker = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/RadioPicker.kt").readText()

    /** Comments explain the history and name dataSync; only declarations count. */
    private fun withoutComments(xml: String) = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `targets Android 17`() {
        val target = Regex("""targetSdk = (\d+)""").find(gradle)!!.groupValues[1].toInt()
        assertTrue("targetSdk $target", target >= 37)
    }

    @Test
    fun `the radio service is connectedDevice only`() {
        val m = withoutComments(manifest)
        assertTrue(m.contains("""android:foregroundServiceType="connectedDevice""""))
        assertFalse("no dataSync type", m.contains("dataSync"))
        assertFalse("no dataSync permission", m.contains("FOREGROUND_SERVICE_DATA_SYNC"))
        assertFalse(service.contains("FOREGROUND_SERVICE_TYPE_DATA_SYNC"))
        assertTrue(service.contains("startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)"))
    }

    @Test
    fun `connectedDevice is allowed without Bluetooth`() {
        val m = withoutComments(manifest)
        assertTrue(m.contains("""<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />"""))
        assertTrue(m.contains("""<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />"""))
    }

    @Test
    fun `TCP asks for the local network before connecting`() {
        val m = withoutComments(manifest)
        assertTrue(m.contains("""<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />"""))
        val button = picker.substringAfter("R.string.set_tcp_warning").substringBefore("R.string.add_tcp_connect")
        val check = button.indexOf("LocalNetworkPermission.shouldRequest(")
        val ask = button.indexOf("lanPermission.launch(LocalNetworkPermission.PERMISSION)")
        assertTrue("the button checks, then asks", check in 0 until ask)
        // The connection after a grant comes from the permission result, not the tap.
        val result = button.substringAfter("RequestPermission()) { ok ->").substringBefore("if (lanRefused)")
        assertTrue(result.contains("if (ok && target != null)") && result.contains("vm.connectTcp(target.first, target.second)"))
    }
}
