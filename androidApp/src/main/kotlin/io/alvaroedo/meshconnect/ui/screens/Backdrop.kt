package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Blurs the whole app behind a focus overlay (the message long-press).
 *
 * This replaced window blur (`FLAG_BLUR_BEHIND`), which Android grants per moment, not per
 * device: it is switched off in battery saver, under thermal throttling, with "Disable window
 * blurs" in developer options, and on GPUs that do not support it — silently, leaving just the
 * scrim. That is why the blur was seen once and then only the darkening. A RenderEffect on the
 * app's own root is drawn by the app and does not ask anyone. It needs Android 12; below that
 * the scrim alone dims the thread, as before.
 *
 * A count rather than a flag, so two overlays overlapping during an animation cannot clear
 * each other's blur.
 */
class BackdropBlur {
    var requests by mutableIntStateOf(0)
        internal set
}

val LocalBackdropBlur = staticCompositionLocalOf { BackdropBlur() }

/** Blur the app behind while this is in composition. Call it from inside the overlay. */
@Composable
fun BlurBackdropWhileShown() {
    val blur = LocalBackdropBlur.current
    DisposableEffect(blur) {
        blur.requests++
        onDispose { blur.requests-- }
    }
}

/**
 * Applied once, at the root. The radius is read inside the layer block, so the animation
 * redraws one layer instead of recomposing every screen.
 */
@Composable
fun Modifier.backdropBlur(blur: BackdropBlur): Modifier {
    val target = if (blur.requests > 0) 1f else 0f
    val progress by animateFloatAsState(target, tween(200), label = "backdropBlur")
    val px = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        val r = progress * px
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
    }
}
