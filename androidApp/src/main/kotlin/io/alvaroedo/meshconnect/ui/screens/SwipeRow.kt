package io.alvaroedo.meshconnect.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A list row with two swipes, after Skypost's: right ([onStartSwipe]) over the primary
 * container, left ([onEndSwipe]) over the secondary one, each icon showing from behind.
 * It acts once, when the swipe settles ([SwipeToDismissBox]'s `onDismiss`), then springs
 * back.
 *
 * It used to act from `confirmValueChange`, returning false to stay put. Material3 1.4
 * consults that on target changes DURING the drag as well, so one swipe could run the
 * action twice; for a toggle (read/unread, archive) the second run read the row as already
 * flipped and flipped it back. That is the "mark unread works two or three times, then
 * sometimes does nothing" report (2026-11-11): it depended on whether a recomposition
 * landed between the two calls.
 */
@Composable
fun SwipeRow(
    onStartSwipe: (() -> Unit)?,
    onEndSwipe: (() -> Unit)?,
    @DrawableRes startIcon: Int,
    @DrawableRes endIcon: Int,
    content: @Composable () -> Unit,
) {
    val start by rememberUpdatedState(onStartSwipe)
    val end by rememberUpdatedState(onEndSwipe)
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = onStartSwipe != null,
        enableDismissFromEndToStart = onEndSwipe != null,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> start?.invoke()
                SwipeToDismissBoxValue.EndToStart -> end?.invoke()
                SwipeToDismissBoxValue.Settled -> {}
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> c.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> c.secondaryContainer
                SwipeToDismissBoxValue.Settled -> c.surface
            }
            Box(
                Modifier.fillMaxSize().background(bg).padding(horizontal = 24.dp),
                contentAlignment = if (dir == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                when (dir) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(painterResource(startIcon), null, tint = c.onPrimaryContainer)
                    SwipeToDismissBoxValue.EndToStart -> Icon(painterResource(endIcon), null, tint = c.onSecondaryContainer)
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        },
    ) { Box(Modifier.background(c.surface)) { content() } }
}
