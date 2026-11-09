package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Shared settings-screen building blocks, used by BOTH the local
 * device Settings tab and the remote repeater/room settings editor so
 * the two surfaces look and behave identically: collapsible sections
 * that query their values on expand, the same rows, chips, hints and
 * wrap-safe button rows.
 */

/** Collapsible titled section with a divider, collapsed by default. */
@Composable
fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_expand_less) else androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_expand_more),
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(bottom = 8.dp)) { content() }
        }
        HorizontalDivider()
    }
}

/**
 * Query-on-expand: runs [query] when the section's content enters
 * composition (i.e. on every expand — AnimatedVisibility disposes
 * collapsed content) and shows a spinner until it answers. [enabled]
 * false skips the query so the caller's "connect / log in first" hint
 * renders immediately.
 */
@Composable
fun QueryOnExpand(
    enabled: Boolean,
    query: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    var loading by remember { mutableStateOf(enabled) }
    LaunchedEffect(Unit) {
        if (enabled) query()
        loading = false
    }
    if (loading) {
        SectionSpinner()
    } else {
        content()
    }
}

@Composable
fun SectionSpinner(label: String = "Querying radio…") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        HintText(label)
    }
}

/**
 * A spinner in a box already the size of what it is waiting for. A dialog that opens on a
 * one-line "Asking the radio…" and then grows to a QR code reads as two popups, because a
 * local load is over in a frame; reserving the final size keeps it one.
 */
@Composable
fun LoadingBox(height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun SettingRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A search box with an ✕ that empties it. Without one, the only way to
 * drop a search was to delete it character by character or back out of
 * the screen. The ✕ appears only when there is something to clear.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FormField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_close), contentDescription = stringResource(R.string.clear))
                }
            }
        } else {
            null
        },
        modifier = modifier,
    )
}

/**
 * Body text inside an [ExpandableHint]. Distinct from [HintText]
 * because it is the only place in the app that may run to a paragraph
 * — the user asked for it by tapping "More".
 */
@Composable
fun DetailText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One line of hint, with the rest of the truth behind a tap.
 *
 * This app's instinct to say the whole honest thing is right and worth
 * keeping — but it was spent three sentences at a time on every row of
 * every screen until the whole app read like a disclaimer, which is
 * its own kind of dishonesty because nobody reads a wall
 * (LESSONS §14, REBUILD-PLAYBOOK §6.3).
 *
 * The budget is one line per control. [summary] must be true on its
 * own — this is not a teaser for the real warning, it is the short
 * version of it. Anything that is dangerous to miss belongs in
 * [summary], not in [detail].
 */
@Composable
fun ExpandableHint(summary: String, detail: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            // 48dp minimum. The first cut zeroed the vertical content
            // padding to make the row compact, which produced a ~27x16dp
            // target sitting inline inside a scrollable — small enough to
            // miss, and easy to fire by accident while scrolling.
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 48.dp),
            ) {
                Text(
                    stringResource(if (expanded) R.string.hint_less else R.string.hint_more),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(bottom = 8.dp)) { detail() }
        }
    }
}

/**
 * Single choice. Up to four short options are a full-width segmented button row (the M3
 * control for this); more, or long labels, fall back to wrapping chips so nothing is
 * squeezed into vertical text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(
    options: List<String>,
    selected: Int,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit,
) {
    if (options.size in 2..4 && options.all { it.length <= 12 }) {
        androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i,
                    onClick = { if (enabled) onSelect(i) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
                    enabled = enabled,
                    label = { Text(label, maxLines = 1) },
                )
            }
        }
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { if (enabled) onSelect(i) },
                label = { Text(label) },
                enabled = enabled,
                shape = FieldShape,
            )
        }
    }
}

/** Buttons that must never be squeezed into vertical text: each keeps
 *  its intrinsic width and overflow wraps to the next line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ButtonFlowRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { content() }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
    onChange: (String) -> Unit,
) {
    FormField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (sensitive) PasswordVisualTransformation()
        else VisualTransformation.None,
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

/**
 * Holds the screen awake while this composable is on screen.
 *
 * Used by the firmware update, which is a two-minute Bluetooth transfer
 * that must not be interrupted. The alternative is a WAKE_LOCK
 * permission, and this app asks for none it can avoid — keeping the
 * screen on is both weaker and honest, since the user has to stay near
 * the node anyway.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
