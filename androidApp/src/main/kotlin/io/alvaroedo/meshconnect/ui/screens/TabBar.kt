package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * The bottom tab bar, built once by the shell (MainActivity: tabs, badges, navigation) and drawn
 * by each tab screen as its own Scaffold's bottomBar (2026-11-16). It used to belong to the shell,
 * which removed it the moment a screen was pushed: the bar vanished and the content resized at
 * once, while the new screen was still sliding in. Inside each tab screen it leaves with that
 * screen instead, under the one sliding over it.
 */
/** Called with the tab screen's own route. */
val LocalTabBar = compositionLocalOf<@Composable (String) -> Unit> { {} }
