package io.alvaroedo.meshconnect.storage

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The database key moved to the after-first-unlock vault (2026-11-10). What matters is
 * that the passphrase an existing install already uses survives the move, that a phone
 * locked at the wrong moment never mints a second passphrase, and that the strict key
 * stops being needed once the move is done.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseKeyTest {

    /** XOR "sealing" tagged with a vault id, so one vault cannot open another's blob. */
    private class FakeVault(private val id: Byte) : SecretVault {
        var locked = false
        var seals = 0
        override suspend fun seal(plaintext: ByteArray): ByteArray {
            if (locked) error("locked")
            seals++
            return byteArrayOf(id) + plaintext.map { (it.toInt() xor 0x5A).toByte() }
        }

        override suspend fun unseal(sealed: ByteArray): ByteArray {
            if (locked) error("locked")
            require(sealed[0] == id) { "wrong vault" }
            return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private class MapStore : DatabaseKey.SealedStore {
        val map = mutableMapOf<String, ByteArray>()
        override fun get(key: String) = map[key]
        override fun put(key: String, sealed: ByteArray?) {
            if (sealed == null) map.remove(key) else map[key] = sealed
        }
    }

    @Test
    fun `a fresh install seals under the after-first-unlock vault, even while locked`() = runTest {
        val store = MapStore()
        val afu = FakeVault(1)
        val strict = FakeVault(2).apply { locked = true }
        val key = DatabaseKey.passphrase(store, afu, strict)
        assertNotNull(key)
        assertTrue(store.map.containsKey(DatabaseKey.PREF_KEY))
        assertFalse(store.map.containsKey(DatabaseKey.LEGACY_PREF_KEY))
        assertArrayEquals(key, DatabaseKey.passphrase(store, afu, strict))
    }

    @Test
    fun `an existing passphrase moves to the new vault and keeps its bytes`() = runTest {
        val store = MapStore()
        val strict = FakeVault(2)
        val original = ByteArray(32) { it.toByte() }
        store.put(DatabaseKey.LEGACY_PREF_KEY, strict.seal(original))

        val afu = FakeVault(1)
        assertArrayEquals(original, DatabaseKey.passphrase(store, afu, strict))
        assertFalse("the strict copy is dropped once moved", store.map.containsKey(DatabaseKey.LEGACY_PREF_KEY))

        // The point of the change: from now on a locked phone still opens it.
        strict.locked = true
        assertArrayEquals(original, DatabaseKey.passphrase(store, afu, strict))
    }

    @Test
    fun `locked before the move, it declines and never mints a second passphrase`() = runTest {
        val store = MapStore()
        val strict = FakeVault(2)
        val original = ByteArray(32) { (it * 3).toByte() }
        store.put(DatabaseKey.LEGACY_PREF_KEY, strict.seal(original))
        strict.locked = true
        val afu = FakeVault(1)

        assertNull(DatabaseKey.passphrase(store, afu, strict))
        assertTrue("the old blob is kept for the next unlocked start", store.map.containsKey(DatabaseKey.LEGACY_PREF_KEY))
        assertFalse(store.map.containsKey(DatabaseKey.PREF_KEY))
        assertTrue("nothing new was sealed", afu.seals == 0)

        strict.locked = false
        assertArrayEquals(original, DatabaseKey.passphrase(store, afu, strict))
    }

    @Test
    fun `if the new vault refuses to seal, the old blob stays and the passphrase is still returned`() = runTest {
        val store = MapStore()
        val strict = FakeVault(2)
        val original = ByteArray(32) { 7 }
        store.put(DatabaseKey.LEGACY_PREF_KEY, strict.seal(original))
        val afu = FakeVault(1).apply { locked = true }
        assertArrayEquals(original, DatabaseKey.passphrase(store, afu, strict))
        assertTrue(store.map.containsKey(DatabaseKey.LEGACY_PREF_KEY))
    }

    @Test
    fun `the after-first-unlock vault is the one without the unlocked-device requirement`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/storage/KeystoreSecretVault.kt").readText()
        val afu = src.substringAfter("fun afterFirstUnlock(): KeystoreSecretVault =").substringBefore("\n\n")
        assertTrue(afu.contains("requireUnlocked = false"))
        // And the flag reaches the spec rather than being hard-coded true.
        assertFalse(src.contains("                setUnlockedDeviceRequired(true)"))
        assertTrue(src.contains("setUnlockedDeviceRequired(requireUnlocked)"))
        // Its own alias: reusing the strict one would hand back the strict key.
        assertFalse(afu.contains("alias = KEY_ALIAS"))
    }

    @Test
    fun `the service and the app both open the database with the after-first-unlock key`() {
        for (path in listOf(
            "src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt",
            "src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt",
        )) {
            val src = File(path).readText()
            assertTrue(path, src.contains("DatabaseKey.passphrase(prefs)"))
            assertTrue("$path seals channel PSKs with it", src.contains("KeystoreSecretVault.afterFirstUnlock()"))
        }
    }
}
