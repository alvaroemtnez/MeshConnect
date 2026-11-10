package io.alvaroedo.meshconnect.storage

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Channel PSKs are cached under the after-first-unlock vault (2026-11-10) so a service
 * syncing channels behind the lock screen keeps them; rows sealed before then still open.
 */
@RunWith(RobolectricTestRunner::class)
class PskVaultTest {

    private class Tagged(private val id: Byte, var locked: Boolean = false) : SecretVault {
        override suspend fun seal(plaintext: ByteArray): ByteArray {
            if (locked) error("locked"); return byteArrayOf(id) + plaintext
        }
        override suspend fun unseal(sealed: ByteArray): ByteArray {
            if (locked) error("locked"); require(sealed[0] == id); return sealed.copyOfRange(1, sealed.size)
        }
    }

    private val prefs = Preferences(ApplicationProvider.getApplicationContext())
    private val psk = ByteArray(16) { it.toByte() }

    @Test
    fun `seals with the psk vault, which works while the strict one is locked`() = runTest {
        val strict = Tagged(2, locked = true)
        val secrets = SecretsRepository(prefs, strict, Tagged(1))
        val sealed = secrets.sealPsk(psk)!!
        assertArrayEquals(psk, secrets.unsealPsk(sealed))
    }

    @Test
    fun `a row sealed by the strict vault before the move still opens`() = runTest {
        val strict = Tagged(2)
        val old = strict.seal(psk)
        assertArrayEquals(psk, SecretsRepository(prefs, strict, Tagged(1)).unsealPsk(old))
        // And without the fallback it would not — the control for the line above.
        assertNull(SecretsRepository(prefs, Tagged(1)).unsealPsk(old))
    }
}
