package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.runtime.compositionLocalOf
import io.github.thatsfguy.meshcore.presentation.AppMode

/**
 * The app mode, provided once by the shell (MainActivity) so that leaf components shared by
 * many screens — avatars, the status cluster — follow it without each caller passing it in.
 * Screens that decide what to show still read it the same way; the rules are SimpleMode's.
 */
val LocalAppMode = compositionLocalOf { AppMode.Full }
