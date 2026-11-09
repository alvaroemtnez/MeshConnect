package io.alvaroedo.meshconnect.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

/**
 * Material's own type scale, unmodified.
 *
 * Upstream once carried a compacted copy (body at 14 sp instead of 16),
 * which made the app read smaller than everything else on the phone at
 * any system font size (MeshCore Hardened issue #2).
 */
internal val MeshCoreTypography = Typography()

/**
 * Material 3 with dynamic colour (Material You) on Android 12+, and the
 * scheme generated from the logo's seed ([LightColors]/[DarkColors],
 * `scripts/gen_colors.py`) before that. Never hard-code colours in
 * screens; use `MaterialTheme.colorScheme`.
 *
 * The activity is edge-to-edge: the system bars are transparent and the
 * app's own top and bottom bars draw under them, so each reads as one
 * band with the status bar and the gesture bar.
 */
@Composable
fun MeshCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Paint the window itself so it can't flash a mismatched
            // background through the IME's resize transition.
            window.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = MeshCoreTypography, content = content)
}

/** Monospace for hashes and the diagnostics log. */
val MonoFontFamily: FontFamily = FontFamily.Monospace
