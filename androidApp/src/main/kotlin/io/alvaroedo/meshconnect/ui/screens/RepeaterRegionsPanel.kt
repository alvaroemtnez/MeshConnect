package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.RegionAdmin
import io.github.thatsfguy.meshcore.presentation.RegionAdmin.Action
import io.github.thatsfguy.meshcore.presentation.RegionAdmin.Untagged
import io.github.thatsfguy.meshcore.protocol.Regions
import kotlinx.coroutines.launch

/**
 * Region administration on a repeater (PARITY.md §8, second row).
 *
 * The firmware keeps a tree of named regions and a per-region flood
 * permission; this is the `region …` CLI surface with the guesswork
 * taken out. The rules — what a region offers, where a new one can hang,
 * what each untagged-traffic choice sends — are [RegionAdmin], tested in
 * `shared`. Two things it deliberately does not do:
 *
 *  - **`region load`** is not offered. It puts the node into a
 *    multi-line mode where each following line is a region name; a
 *    one-shot CLI message would strand it there.
 *  - **Nothing is auto-saved.** `region save` is the only thing that
 *    persists region edits across a reboot, and running it silently after
 *    every change would make an experiment permanent. The panel says so
 *    instead. (`region default` and `set flood.max.unscoped` save
 *    themselves — that is the firmware's choice, and the banner follows
 *    it.)
 *
 * After every change the node is read again, so the screen shows what
 * the node now holds rather than what the app expects it to.
 */
@Composable
fun RepeaterRegionsPanel(vm: MeshCoreViewModel, keyHex: String, isAdmin: Boolean) {
    val res = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()
    var tree by remember(keyHex) { mutableStateOf<Regions.RegionTree?>(null) }
    var rawReply by remember(keyHex) { mutableStateOf<String?>(null) }
    var defaultScope by remember(keyHex) { mutableStateOf<String?>(null) }
    var defaultKnown by remember(keyHex) { mutableStateOf(false) }
    /** Null until `ver` has been read; the hop limit depends on it. */
    var limitSupported by remember(keyHex) { mutableStateOf<Boolean?>(null) }
    var hopLimit by remember(keyHex) { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var pendingEdits by remember(keyHex) { mutableStateOf(false) }

    var chosenMode by remember(keyHex) { mutableStateOf<Untagged?>(null) }
    var chosenHops by remember(keyHex) { mutableIntStateOf(RegionAdmin.SUGGESTED_HOPS) }
    var actionsFor by remember { mutableStateOf<Regions.RegionEntry?>(null) }
    var addUnder by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<PendingRegionAction?>(null) }

    suspend fun read() {
        val listing = vm.cliQuery(keyHex, Regions.tree())
        rawReply = listing
        tree = Regions.parseRegionTree(listing)
        val default = vm.cliQuery(keyHex, Regions.default())
        defaultScope = Regions.parseDefaultScope(default)
        defaultKnown = default != null
        if (limitSupported == null) {
            limitSupported = RegionAdmin.supportsHopLimit(vm.cliQuery(keyHex, "ver"))
        }
        hopLimit = if (limitSupported == true) {
            RegionAdmin.parseHopLimit(vm.cliQuery(keyHex, RegionAdmin.GET_HOP_LIMIT))
        } else {
            null
        }
        tree?.let { t ->
            chosenMode = RegionAdmin.currentUntagged(t.wildcardFloodAllowed, hopLimit, limitSupported)
            hopLimit?.takeIf { it in RegionAdmin.NEARBY_HOPS }?.let { chosenHops = it }
        }
        if (listing == null) note = res.getString(R.string.rs_no_reply)
    }

    fun refresh() {
        scope.launch {
            loading = true
            note = null
            read()
            loading = false
        }
    }

    /**
     * Send [commands] in order, stopping at the first the node refuses or
     * doesn't answer, then read the node again.
     */
    fun run(commands: List<String>, describe: String) {
        if (commands.isEmpty()) return
        scope.launch {
            loading = true
            note = null
            var failed: String? = null
            for (command in commands) {
                val reply = vm.cliQuery(keyHex, command)
                when {
                    reply == null -> failed = res.getString(R.string.rr_no_reply_to, command)
                    reply.trim().startsWith("Err", ignoreCase = true) ->
                        failed = "`$command`: ${reply.trim()}"
                    RegionAdmin.savesRegions(command) -> pendingEdits = false
                    RegionAdmin.needsRegionSave(command) -> pendingEdits = true
                }
                if (failed != null) break
            }
            note = failed ?: describe
            read()
            loading = false
        }
    }

    fun run(command: String, describe: String) = run(listOf(command), describe)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        ExpandableHint(
            stringResource(R.string.rr_intro),
        ) {
            DetailText(
                stringResource(R.string.rr_intro_d),
            )
        }

        // Edits go to the node's RAM; Save writes them to its flash.
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(enabled = !loading, onClick = { refresh() }, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(R.drawable.ic_refresh), null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.rr_fetch))
            }
            androidx.compose.material3.FilledTonalButton(
                enabled = !loading && isAdmin,
                onClick = { run(Regions.save(), describe = res.getString(R.string.rr_saved)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.rr_save)) }
        }
        if (loading || note != null || pendingEdits) {
            Column {
                if (loading) SectionSpinner(stringResource(R.string.rs_asking))
                note?.let { HintText(it) }
                if (pendingEdits) {
                    Text(
                        stringResource(R.string.rr_unsaved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // --- Untagged traffic -----------------------------------------------
        val parsed = tree
        if (parsed != null) PanelCard(stringResource(R.string.rr_untagged), R.drawable.ic_public) {
            HintText(stringResource(R.string.rr_untagged_hint))
            val current =
                RegionAdmin.currentUntagged(parsed.wildcardFloodAllowed, hopLimit, limitSupported)
            if (current == null) {
                HintText(
                    stringResource(R.string.rr_limit_unknown),
                )
            }
            UntaggedChoices(
                current = current,
                currentHops = hopLimit,
                chosen = chosenMode,
                hops = chosenHops,
                limitSupported = limitSupported == true,
                enabled = isAdmin && !loading,
                onChoose = { chosenMode = it },
                onHops = { chosenHops = it },
            )
            if (limitSupported == false) {
                HintText(stringResource(R.string.rr_nearby_needs))
            }
            val target = chosenMode
            if (isAdmin && target != null) {
                val commands = runCatching {
                    RegionAdmin.commandsFor(
                        target,
                        chosenHops,
                        parsed.wildcardFloodAllowed,
                        hopLimit,
                        limitSupported == true,
                    )
                }.getOrDefault(emptyList())
                TextButton(
                    enabled = !loading && commands.isNotEmpty(),
                    onClick = {
                        if (target == Untagged.Refuse) {
                            confirm = PendingRegionAction(
                                title = res.getString(R.string.rr_refuse_q),
                                body = res.getString(R.string.rr_refuse_body),
                                confirmLabel = res.getString(R.string.rr_refuse),
                                destructive = true,
                                commands = commands,
                                describe = res.getString(R.string.rr_refused),
                            )
                        } else {
                            run(commands, describe = res.getString(R.string.rr_updated))
                        }
                    },
                ) { Text(stringResource(R.string.rr_apply)) }
            }
        }

        // --- Default scope --------------------------------------------------
        PanelCard(stringResource(R.string.rr_default), R.drawable.ic_pin_drop) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HintText(
                    when {
                        !defaultKnown -> stringResource(R.string.rr_not_read)
                        defaultScope == Regions.GLOBAL_SELECTOR ->
                            stringResource(R.string.rr_default_none)
                        defaultScope != null -> stringResource(R.string.rr_default_is, defaultScope ?: "")
                        // A reply we couldn't parse is unknown, never "cleared":
                        // the two lead to opposite decisions.
                        else -> stringResource(R.string.rr_unrecognised)
                    },
                )
            }
            if (isAdmin && defaultScope != null && defaultScope != Regions.GLOBAL_SELECTOR) {
                TextButton(enabled = !loading, onClick = {
                    confirm = PendingRegionAction(
                        title = res.getString(R.string.rr_clear_q),
                        body = res.getString(R.string.rr_clear_body),
                        confirmLabel = res.getString(R.string.clear),
                        destructive = false,
                        commands = listOf(Regions.setDefault(null)),
                        describe = res.getString(R.string.rr_cleared),
                    )
                }) { Text(stringResource(R.string.clear)) }
            }
        }

        }

        // --- Region tree ----------------------------------------------------
        PanelCard(
            stringResource(R.string.backup_regions),
            R.drawable.ic_map,
            action = if (isAdmin && parsed != null) {
                {
                    TextButton(enabled = !loading, onClick = { addUnder = Regions.GLOBAL_SELECTOR }) {
                        Text(stringResource(R.string.rr_add))
                    }
                }
            } else {
                null
            },
        ) {
        if (parsed == null && rawReply != null) {
            HintText(
                stringResource(R.string.rr_not_tree),
            )
            rawReply?.lineSequence()?.filter { it.isNotBlank() }?.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (parsed != null && parsed.regions.isEmpty()) HintText(stringResource(R.string.rr_none))
        if (parsed != null && isAdmin && parsed.regions.isNotEmpty()) {
            HintText(stringResource(R.string.rr_tap_hint))
        }
        for (entry in parsed?.regions.orEmpty()) {
            val tappable = isAdmin && entry.actionable && !loading
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .clickable(enabled = tappable) { actionsFor = entry }
                    .padding(start = (4 + 20 * (entry.depth - 1)).dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(
                        if (entry.floodAllowed) R.drawable.ic_public else R.drawable.ic_block,
                    ),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontFamily = FontFamily.Monospace)
                HintText(
                    listOfNotNull(
                        stringResource(R.string.rr_tag_default).takeIf { entry.name == defaultScope },
                        stringResource(R.string.rr_tag_home).takeIf { entry.home },
                        stringResource(if (entry.floodAllowed) R.string.rr_tag_flood_on else R.string.rr_tag_flood_off),
                    ).joinToString(" · "),
                )
                if (!entry.actionable) {
                    HintText(stringResource(R.string.rr_not_valid))
                }
            }
                if (tappable) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_chevron_right),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (parsed?.truncated == true) {
            HintText(
                stringResource(R.string.rr_truncated),
            )
        }
        }
        if (!isAdmin) {
            HintText(stringResource(R.string.rr_admin_only))
        }
    }

    // --- Dialogs -------------------------------------------------------------

    actionsFor?.let { entry ->
        val t = tree
        val actions = if (t == null) emptyList() else RegionAdmin.actionsFor(entry, t, defaultScope)
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(entry.name, fontFamily = FontFamily.Monospace) },
            text = {
                Column {
                    for (action in actions) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                actionsFor = null
                                when (action) {
                                    Action.MakeDefault -> run(
                                        Regions.setDefault(entry.name),
                                        describe = res.getString(R.string.rr_now_default, entry.name),
                                    )
                                    Action.SetHome -> run(
                                        Regions.setHome(entry.name),
                                        describe = res.getString(R.string.rr_now_home, entry.name),
                                    )
                                    Action.AddChild -> addUnder = entry.name
                                    Action.AllowFlood -> run(
                                        Regions.allowFlood(entry.name),
                                        describe = res.getString(R.string.rr_flood_allowed, entry.name),
                                    )
                                    Action.DenyFlood -> confirm = PendingRegionAction(
                                        title = res.getString(R.string.rr_deny_q, entry.name),
                                        body = res.getString(R.string.rr_deny_body, entry.name),
                                        confirmLabel = res.getString(R.string.rr_deny),
                                        destructive = true,
                                        commands = listOf(Regions.denyFlood(entry.name)),
                                        describe = res.getString(R.string.rr_flood_denied, entry.name),
                                    )
                                    Action.Remove -> confirm = PendingRegionAction(
                                        title = res.getString(R.string.rr_remove_q, entry.name),
                                        body = res.getString(R.string.rr_remove_body),
                                        confirmLabel = res.getString(R.string.rr_remove),
                                        destructive = true,
                                        commands = listOf(Regions.remove(entry.name)),
                                        describe = res.getString(R.string.rr_removed, entry.name),
                                    )
                                }
                            },
                        ) {
                            Text(
                                actionLabel(action),
                                modifier = Modifier.fillMaxWidth(),
                                color = if (action == Action.Remove) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionsFor = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    addUnder?.let { preset ->
        AddRegionDialog(
            choices = RegionAdmin.parentChoices(tree),
            initialParent = preset,
            onDismiss = { addUnder = null },
            onAdd = { name, parent ->
                addUnder = null
                run(Regions.put(name, parent), describe = res.getString(R.string.rr_added, Regions.canonical(name)))
            },
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.body) },
            confirmButton = {
                TextButton(onClick = {
                    run(action.commands, describe = action.describe)
                    confirm = null
                }) {
                    Text(
                        action.confirmLabel,
                        color = if (action.destructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun actionLabel(action: Action): String = stringResource(when (action) {
    Action.MakeDefault -> R.string.rr_make_default
    Action.SetHome -> R.string.rr_make_home
    Action.AddChild -> R.string.rr_add_child
    Action.AllowFlood -> R.string.rr_allow_flood
    Action.DenyFlood -> R.string.rr_deny_flood
    Action.Remove -> R.string.rr_remove
})

/** Relay all · only nearby (N hops) · refuse — one control for two firmware settings. */
@Composable
private fun UntaggedChoices(
    current: Untagged?,
    currentHops: Int?,
    chosen: Untagged?,
    hops: Int,
    limitSupported: Boolean,
    enabled: Boolean,
    onChoose: (Untagged) -> Unit,
    onHops: (Int) -> Unit,
) {
    val options = buildList {
        add(Untagged.RelayAll)
        if (limitSupported) add(Untagged.Nearby)
        add(Untagged.Refuse)
    }
    for (option in options) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = chosen == option,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = { onChoose(option) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen == option, onClick = null, enabled = enabled)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (option) {
                        Untagged.RelayAll -> stringResource(R.string.rr_relay_all)
                        Untagged.Nearby -> stringResource(R.string.rr_relay_nearby)
                        Untagged.Refuse -> stringResource(R.string.rr_refuse)
                    } + if (option == current) "  " + stringResource(R.string.rr_now) else "",
                )
                if (option == Untagged.Nearby && (chosen == option || current == option)) {
                    HintText(describeNearby(if (chosen == option) hops else currentHops ?: hops))
                }
            }
        }
        if (option == Untagged.Nearby && chosen == option && enabled) {
            Row(
                Modifier.padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = hops > RegionAdmin.NEARBY_HOPS.first,
                    onClick = { onHops(hops - 1) },
                ) { Text("−") }
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.rr_hops, hops, hops))
                TextButton(
                    enabled = hops < RegionAdmin.NEARBY_HOPS.last,
                    onClick = { onHops(hops + 1) },
                ) { Text("+") }
            }
        }
    }
}

/** Name, plus a parent chosen from the tree rather than retyped. */
@Composable
private fun AddRegionDialog(
    choices: List<RegionAdmin.ParentChoice>,
    initialParent: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, parent: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var parent by remember { mutableStateOf(initialParent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rr_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FormField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rr_name)) },
                    singleLine = true,
                    keyboardOptions = VERBATIM_KEYBOARD,
                    isError = name.isNotBlank() && !Regions.isValid(name),
                    supportingText = { Text(stringResource(R.string.rr_name_rule)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.rr_under), style = MaterialTheme.typography.titleSmall)
                for (choice in choices) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = parent == choice.name,
                                role = Role.RadioButton,
                                onClick = { parent = choice.name },
                            )
                            .padding(start = (16 * choice.depth).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = parent == choice.name, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (choice.name == Regions.GLOBAL_SELECTOR) stringResource(R.string.rr_top_level) else choice.name,
                            fontFamily = if (choice.name == Regions.GLOBAL_SELECTOR) {
                                null
                            } else {
                                FontFamily.Monospace
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = Regions.isValid(name),
                onClick = { onAdd(name, parent) },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Region commands held back until the user confirms them. */
private data class PendingRegionAction(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val destructive: Boolean,
    val commands: List<String>,
    val describe: String,
)

/** RegionAdmin.describeNearby, translated. */
@Composable
private fun describeNearby(hops: Int): String =
    if (hops == 1) {
        stringResource(R.string.rr_nearby_direct)
    } else {
        androidx.compose.ui.res.pluralStringResource(R.plurals.rr_nearby_via, hops - 1, hops - 1)
    }
