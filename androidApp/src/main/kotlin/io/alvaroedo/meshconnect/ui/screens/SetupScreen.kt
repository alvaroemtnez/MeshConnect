package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.protocol.RadioPresets

/**
 * First run: welcome, connect, name and radio settings.
 *
 * Upstream showed a four-step checklist whose "connect" step was a sentence telling you
 * to go to Settings → Connection, plus a step of security notes, and asked for Bluetooth
 * permission before any of it. Now each step is something you do on the spot: the radio
 * list is right there (permission asked when it is needed), the flow moves on by itself
 * once the radio answers, and the last step is the two things that decide whether you
 * see a mesh at all: a name, and being on your area's radio settings.
 */
@Composable
fun SetupScreen(vm: MeshCoreViewModel, onDone: () -> Unit) {
    val engineState by vm.engineState.collectAsState()
    val connected = engineState == EngineState.Ready
    var step by rememberSaveable { mutableIntStateOf(if (connected) 2 else 0) }

    fun finish() {
        vm.prefs.setupComplete = true
        onDone()
    }

    // The radio answering is what moves "Find your radio" on.
    LaunchedEffect(connected) { if (connected && step == 1) step = 2 }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        StepDots(step)
        AnimatedContent(targetState = step, label = "setup", modifier = Modifier.weight(1f)) { s ->
            when (s) {
                0 -> WelcomeStep(onStart = { step = 1 }, onLater = ::finish)
                1 -> ConnectStep(vm, onLater = ::finish)
                else -> FinishStep(vm, onFinish = ::finish)
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                    .background(
                        if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep(onStart: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(128.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The launcher foreground is drawn on the 108 dp grid; at 176 dp its 40 dp glyph
            // fills ~65 dp of this circle, the same proportion as the launcher icon.
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(176.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.onb_start))
        }
        TextButton(onClick = onLater) { Text(stringResource(R.string.onb_later)) }
    }
}

@Composable
private fun ConnectStep(vm: MeshCoreViewModel, onLater: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onb_connect_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onb_connect_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            RadioPicker(vm)
        }
        TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.onb_later))
        }
    }
}

@Composable
private fun FinishStep(vm: MeshCoreViewModel, onFinish: () -> Unit) {
    val self by vm.selfInfo.collectAsState()
    var name by remember(self?.name) { mutableStateOf(self?.name.orEmpty()) }
    var presetSheet by remember { mutableStateOf(false) }
    val matches = remember(self?.freqKhz, self?.bwHz, self?.sf, self?.cr) {
        self?.let { RadioPresets.matching(it.freqKhz, it.bwHz, it.sf, it.cr) }.orEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.onb_setup_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.onb_setup_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard(Modifier) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.setup_node_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (matches.isNotEmpty()) {
                            stringResource(R.string.onb_preset_current, matches.joinToString(", ") { it.name })
                        } else {
                            stringResource(R.string.onb_preset_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = { presetSheet = true }) {
                        Text(stringResource(if (matches.isNotEmpty()) R.string.onb_change_preset else R.string.setup_pick_preset))
                    }
                    ExpandableHint(stringResource(R.string.onb_legal_short)) { DetailText(stringResource(R.string.regulatory_caveat)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && trimmed != self?.name) vm.setAdvertName(trimmed)
                onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.onb_finish)) }
    }

    if (presetSheet) RadioPresetSheet(vm, onDismiss = { presetSheet = false })
}
