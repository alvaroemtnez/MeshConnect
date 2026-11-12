package io.alvaroedo.meshconnect.platform

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When MeshConnect asks for Android 17's local network permission (1.2.0, targetSdk 37). */
class LocalNetworkPermissionTest {

    @Test
    fun `the spelled-out name is the platform's constant`() {
        assertEquals(Manifest.permission.ACCESS_LOCAL_NETWORK, LocalNetworkPermission.PERMISSION)
    }

    @Test
    fun `asked on Android 17 with TCP on and not yet granted`() {
        assertTrue(LocalNetworkPermission.shouldRequest(sdk = 37, tcpEnabled = true, granted = false))
        assertTrue(LocalNetworkPermission.shouldRequest(sdk = 38, tcpEnabled = true, granted = false))
    }

    @Test
    fun `never asked with TCP off, the default`() {
        // Bluetooth and USB users must not see a prompt for a transport they don't use.
        for (sdk in listOf(26, 34, 36, 37, 38)) {
            assertFalse(LocalNetworkPermission.shouldRequest(sdk, tcpEnabled = false, granted = false))
        }
    }

    @Test
    fun `never asked before Android 17, where it does not exist`() {
        for (sdk in 26..36) {
            assertFalse(LocalNetworkPermission.shouldRequest(sdk, tcpEnabled = true, granted = false))
        }
    }

    @Test
    fun `not asked again once granted`() {
        assertFalse(LocalNetworkPermission.shouldRequest(sdk = 37, tcpEnabled = true, granted = true))
    }
}
