package io.alvaroedo.meshconnect.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.alvaroedo.meshconnect.ui.model.SharedText
import java.io.File
import java.util.Locale
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SharedText reads the Resources it is given (2026-11-13). Composables pass
 * LocalResources.current, which recomposes on a configuration change; a Context read
 * from LocalContext does not, so a language switch left these lines in the old language.
 */
@RunWith(RobolectricTestRunner::class)
class SharedTextResourcesTest {

    private fun resources(tag: String): Resources {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return app.createConfigurationContext(config).resources
    }

    @Test
    fun `the text follows the resources passed in, not the app's`() {
        assertEquals("2 hops", SharedText.pathHops(resources("en"), isFlood = false, hops = 2))
        assertEquals("2 saltos", SharedText.pathHops(resources("es"), isFlood = false, hops = 2))
        assertEquals("Flood (no stored path)", SharedText.pathHops(resources("en"), isFlood = true, hops = 0))
    }

    @Test
    fun `no composable hands SharedText a Context`() {
        val screens = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens")
        val calls = Regex("""SharedText\.\w+\(\s*([\w.]+)""")
        val args = screens.walk().filter { it.extension == "kt" }
            .flatMap { f -> calls.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
        // Positive control: the calls exist, so an empty set cannot pass vacuously.
        assertEquals(setOf("androidx.compose.ui.platform.LocalResources.current"), args)
    }
}
