package io.alvaroedo.meshconnect.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Keystore tier fallback (upstream PR #10). Robolectric has no
 * AndroidKeyStore provider, so this pins the source shape instead: each
 * tier must get its own KeyGenerator, never one shared across attempts.
 */
class KeystoreTierWiringTest {

    private val src =
        File("src/main/kotlin/io/alvaroedo/meshconnect/storage/KeystoreSecretVault.kt").readText()

    private val getOrCreate = src.substringAfter("private fun getOrCreateKey(): SecretKey {")
        .substringBefore("\n    private fun ")

    @Test
    fun `each tier creates its own key generator`() {
        val loop = getOrCreate.substringAfter("for ((tierIndex, tierConfig) in tiers.withIndex()) {")
        val beforeLoop = getOrCreate.substringBefore("for ((tierIndex, tierConfig) in tiers.withIndex()) {")
        assertTrue(
            "the KeyGenerator must be created inside the tier loop",
            loop.substringBefore("kg.init(spec)").contains("KeyGenerator.getInstance("),
        )
        assertEquals(
            "a KeyGenerator created before the loop would be shared by every tier",
            false,
            beforeLoop.contains("KeyGenerator.getInstance("),
        )
    }
}
