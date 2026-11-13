package io.alvaroedo.meshconnect

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * tools/upstream-sync.sh, run against a throwaway upstream and fork built with real git
 * (2026-11-13), and the workflow that turns its answer into a pull request.
 */
class UpstreamSyncTest {

    private val script = File("../tools/upstream-sync.sh").absoluteFile
    private val tmp = Files.createTempDirectory("upstream-sync").toFile()
    private val upstream = File(tmp, "upstream")
    private val fork = File(tmp, "fork")
    private val body = File(tmp, "body.md")

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    private fun git(dir: File, vararg args: String): String {
        val p = ProcessBuilder(
            listOf("git", "-c", "user.name=t", "-c", "user.email=t@invalid", "-c", "init.defaultBranch=main", "-c", "commit.gpgsign=false") + args,
        ).directory(dir).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { "git ${args.joinToString(" ")}: $out" }
        return out.trim()
    }

    private fun commit(dir: File, path: String, text: String, msg: String) {
        File(dir, path).apply { parentFile!!.mkdirs(); writeText(text) }
        git(dir, "add", "-A")
        git(dir, "commit", "-qm", msg)
    }

    /** Upstream at the fork point, and a fork with one change of its own. */
    private fun setUp() {
        upstream.mkdirs()
        git(upstream, "init", "-q")
        commit(upstream, "shared/Engine.kt", "engine v1\n", "fork point")
        commit(upstream, "androidApp/Screen.kt", "screen v1\n", "screen")
        git(tmp, "clone", "-q", upstream.path, fork.path)
        commit(fork, "androidApp/Screen.kt", "screen, ours\n", "our screen")
    }

    private fun run(): Map<String, String> {
        val p = ProcessBuilder("bash", script.path).directory(fork).apply {
            environment()["UPSTREAM_URL"] = upstream.path
            environment()["BODY_FILE"] = body.path
        }.start()
        val out = p.inputStream.bufferedReader().readLines()
        val err = p.errorStream.bufferedReader().readText()
        assertEquals(0, p.waitFor(), err)
        return out.associate { it.substringBefore('=') to it.substringAfter('=') }
    }

    @Test
    fun `nothing new upstream is up to date, and writes no body`() {
        setUp()
        val r = run()
        assertEquals("up-to-date", r["STATUS"])
        assertEquals("0", r["COUNT"])
        assertFalse(body.exists())
    }

    @Test
    fun `new upstream commits are found, split by area, and the checkout is untouched`() {
        setUp()
        commit(upstream, "shared/Engine.kt", "engine v2\n", "Fix the hop width")
        commit(upstream, "README.md", "readme\n", "Docs")
        val head = git(fork, "rev-parse", "HEAD")

        val r = run()
        assertEquals("new", r["STATUS"])
        assertEquals("2", r["COUNT"])
        assertEquals(git(upstream, "rev-parse", "HEAD"), r["SHA"])
        assertEquals(r["SHA"]!!.take(7), r["SHORT"])

        val text = body.readText()
        assertTrue(text.contains("Upstream has 2 new commit(s)"))
        val shared = text.substringAfter("## shared/").substringBefore("## androidApp/")
        assertTrue(shared.contains("shared/Engine.kt"), shared)
        assertTrue(text.substringAfter("## androidApp/").substringBefore("## Everything else").contains("No changes."))
        assertTrue(text.substringAfter("## Everything else").substringBefore("## Trial merge").contains("README.md"))
        assertTrue(text.contains("Merges without conflicts."))
        val commits = text.substringAfter("## Commits")
        assertTrue(commits.indexOf("Fix the hop width") in 0 until commits.indexOf("Docs"), "oldest first")
        assertTrue(text.contains("FORK.md"))

        // Read-only: same HEAD, clean tree, no worktree left behind.
        assertEquals(head, git(fork, "rev-parse", "HEAD"))
        assertEquals("", git(fork, "status", "--porcelain"))
        assertEquals(1, git(fork, "worktree", "list").lines().size)
    }

    @Test
    fun `a conflicting upstream change is named`() {
        setUp()
        commit(upstream, "androidApp/Screen.kt", "screen v2, theirs\n", "Rework the screen")
        val r = run()
        assertEquals("new", r["STATUS"])
        val merge = body.readText().substringAfter("## Trial merge").substringBefore("## Commits")
        assertTrue(merge.contains("- `androidApp/Screen.kt`"), merge)
        assertEquals("", git(fork, "status", "--porcelain"))
    }

    @Test
    fun `once merged, it is up to date again`() {
        setUp()
        commit(upstream, "shared/Engine.kt", "engine v2\n", "Fix")
        git(fork, "pull", "-q", "--no-rebase", "--no-edit", upstream.path, "main")
        assertEquals("up-to-date", run()["STATUS"])
    }

    // ---- the workflow ----

    private val wf = File("../.forgejo/workflows/upstream.yml").readText()

    @Test
    fun `the workflow reads GitHub through the script and needs no secret of its own`() {
        assertTrue(wf.contains("tools/upstream-sync.sh | tee -a \"\$GITHUB_OUTPUT\""))
        assertTrue(wf.contains("fetch-depth: 0"))
        assertTrue(wf.contains("TOKEN: \${{ secrets.GITHUB_TOKEN }}"))
        assertEquals(1, Regex("""secrets\.""").findAll(wf).count())
        assertFalse(wf.contains("mirrors/"), "the mirrors org is members-only")
        assertTrue(File("../tools/upstream-sync.sh").readText().contains("https://github.com/thatSFguy/meshcore-mobile-app.git"))
    }

    @Test
    fun `it opens one PR per upstream head and closes the ones it supersedes`() {
        assertTrue(wf.contains("if: steps.sync.outputs.STATUS == 'new'"))
        assertTrue(wf.contains("BRANCH=\"upstream/\$SHORT\""))
        assertTrue(wf.contains("git ls-remote --exit-code origin \"refs/heads/\$BRANCH\""))
        assertTrue(wf.contains("select(.head.ref | startswith(\"upstream/\")) | select(.head.ref != \$b)"))
        assertTrue(wf.contains("'{\"state\": \"closed\"}'"))
    }
}
