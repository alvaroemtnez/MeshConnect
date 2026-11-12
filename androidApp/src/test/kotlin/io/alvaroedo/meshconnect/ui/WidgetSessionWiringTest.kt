package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget must draw the latest snapshot, not the one its Glance session started with.
 *
 * Seen on the Android 17 emulator (2026-11-12): the radio's TCP link dropped, the service
 * published Connecting (the saved snapshot said so), and the widget kept saying Connected.
 * provideGlance read the snapshot once, outside provideContent; Glance keeps a session running
 * between updates and updateAll() only recomposes it, so the composition kept its first value.
 * A source pin, because sessions only exist on a launcher.
 */
class WidgetSessionWiringTest {

    private val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
    private val provide = src.substringAfter("override suspend fun provideGlance(").substringBefore("companion object")

    @Test
    fun `the composition collects the live snapshot`() {
        val content = provide.substringAfter("provideContent {")
        assertTrue(content.contains("WidgetState.live.collectAsState()"))
        assertTrue(content.contains("Content(live ?: initial)"))
    }

    @Test
    fun `nothing captured outside the composition is drawn`() {
        val outside = provide.substringBefore("provideContent {")
        val inside = provide.substringAfter("provideContent {")
        // The saved snapshot is only the fallback before the service has published.
        assertTrue(outside.contains("val initial = WidgetState.current(context)"))
        assertFalse("drawn straight from the captured value", inside.contains("Content(initial)"))
        assertFalse(inside.contains("Content(snapshot)"))
    }

    @Test
    fun `publishing updates the flow the session collects`() {
        val publish = src.substringAfter("fun publish(context: Context, snapshot: WidgetSnapshot) {").substringBefore("\n    }\n")
        assertTrue(publish.contains("_live.value = snapshot"))
        // And still asks Glance to draw, for a widget with no running session.
        assertTrue(publish.contains("MeshWidget.updateEverywhere(app)"))
    }
}
