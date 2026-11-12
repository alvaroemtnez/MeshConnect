package io.alvaroedo.meshconnect

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a release tag stands for (tools/release-version.sh), betas included (2026-11-13). */
class ReleaseVersionTest {

    private val repoRoot = File("..")

    private fun run(tag: String): Pair<Int, Map<String, String>> {
        val proc = ProcessBuilder("bash", "tools/release-version.sh", tag)
            .directory(repoRoot).redirectErrorStream(false).start()
        val out = proc.inputStream.bufferedReader().readLines()
        val code = proc.waitFor()
        return code to out.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    private fun accepted(tag: String): Map<String, String> {
        val (code, env) = run(tag)
        assertEquals(0, code, "$tag should be accepted")
        return env
    }

    private fun refused(tag: String) {
        val (code, env) = run(tag)
        assertTrue(code != 0 && env.isEmpty(), "$tag should be refused, got $env")
    }

    @Test
    fun `a release is not a pre-release`() {
        assertEquals(
            mapOf("VERSION_NAME" to "1.2.0", "VERSION_CODE" to "10200", "NOTES_VERSION" to "1.2.0", "PRERELEASE" to "false"),
            accepted("v1.2.0"),
        )
    }

    @Test
    fun `a beta is a pre-release of its release, with the release's code and notes`() {
        assertEquals(
            mapOf("VERSION_NAME" to "1.3.0-beta.2", "VERSION_CODE" to "10300", "NOTES_VERSION" to "1.3.0", "PRERELEASE" to "true"),
            accepted("v1.3.0-beta.2"),
        )
        // Above every 1.2.x, so a beta tester updates from the last release.
        assertTrue(accepted("v1.3.0-beta.1").getValue("VERSION_CODE").toInt() > accepted("v1.2.99").getValue("VERSION_CODE").toInt())
    }

    @Test
    fun `malformed tags are refused rather than guessed at`() {
        listOf(
            "1.2.0", "v1.2", "v1.2.0.1", "v1.3.0-beta", "v1.3.0-beta.0", "v1.3.0-rc.1", "v1.3.0-BETA.1",
            "v01.2.0", "v1.02.0", "v1.100.0", "v1.0.100", "v1.2.0 ", "v1.2.0\nv1.3.0", "",
        ).forEach(::refused)
    }

    @Test
    fun `the workflow takes the notes section and the pre-release flag from the script`() {
        val wf = File(repoRoot, ".forgejo/workflows/release.yml").readText()
        assertTrue(wf.contains("tools/release-version.sh \"\$GITHUB_REF_NAME\" | tee -a \"\$GITHUB_ENV\""))
        assertTrue(wf.contains("awk -v v=\"## \$NOTES_VERSION\""))
        // Both the Forgejo and the GitHub release carry it; Obtainium reads GitHub's.
        assertEquals(2, wf.split("--argjson pre \"\$PRERELEASE\"").size - 1)
        assertEquals(2, wf.split("prerelease:\$pre}").size - 1)
    }

    @Test
    fun `F-Droid never follows a beta tag`() {
        val recipe = File(repoRoot, "packaging/fdroid/io.alvaroedo.meshconnect.yml").readText()
        val pattern = Regex(recipe.substringAfter("UpdateCheckMode: Tags ").substringBefore('\n').trim())
        assertTrue(pattern.containsMatchIn("v1.3.0"))
        assertTrue(!pattern.containsMatchIn("v1.3.0-beta.1"))
    }
}
