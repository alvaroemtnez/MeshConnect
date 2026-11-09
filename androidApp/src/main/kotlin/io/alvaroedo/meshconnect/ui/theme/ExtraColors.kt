package io.alvaroedo.meshconnect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The only hand-picked colours in the app: semantic states that must not
 * follow the wallpaper (a "connecting" amber that turned blue would stop
 * meaning anything). Everything else comes from MaterialTheme.colorScheme.
 */
object ExtraColors {
    private val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** Connecting / handshaking. */
    val connecting: Color
        @Composable @ReadOnlyComposable
        get() = if (isDark) Color(0xFFFFB74D) else Color(0xFFE08600)

    /** Linked to the radio. */
    val connected: Color
        @Composable @ReadOnlyComposable
        get() = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
}
