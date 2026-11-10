package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.github.thatsfguy.meshcore.presentation.AdminSession
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import io.github.thatsfguy.meshcore.presentation.HubTile
import io.github.thatsfguy.meshcore.presentation.repeaterHubTiles
import io.github.thatsfguy.meshcore.presentation.showsGuestQueries
import io.github.thatsfguy.meshcore.presentation.FloodRegionsAsk
import io.github.thatsfguy.meshcore.presentation.floodRegionsSummary
import io.github.thatsfguy.meshcore.protocol.OwnerInfo
import io.github.thatsfguy.meshcore.presentation.repeaterRoleLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.NodeRole
import io.github.thatsfguy.meshcore.protocol.PathCodec

/**
 * The repeater/room/sensor admin **hub** — an identity card, the grant
 * the node gave us, and a list of tools that each open their own
 * screen.
 *
 * This replaced `RepeaterAdminScreen`, which put six scrollable tabs
 * (Status · Settings · Regions · Identity · Console · Help) and a login
 * row on one surface. The reference client has had hub-and-spoke here
 * the whole time — the rebuild copied its *screen list* and invented
 * its own answer to how they connect, which is the exact failure
 * REBUILD-PLAYBOOK §1.4a is about.
 *
 * Which tools appear is [repeaterHubTiles]; it is a pure function so
 * the gating rules are testable without a device.
 */
@Composable
fun RepeaterHubScreen(vm: MeshCoreViewModel, nav: NavController, keyHex: String) {
    val contacts by vm.dbContacts.collectAsState()
    val contact = contacts.firstOrNull { it.keyHex == keyHex }
    val name = contact?.name?.ifBlank { null } ?: keyHex.take(12)
    val role = when (contact?.type) {
        Codes.ADV_TYPE_ROOM -> NodeRole.Room
        Codes.ADV_TYPE_SENSOR -> NodeRole.Sensor
        else -> NodeRole.Repeater
    }

    val sessions by vm.adminSessions.collectAsState()
    val session = sessions[keyHex] ?: AdminSession.None

    // Sign-in is the GATE, not an overlay: with no session there is no
    // hub, just the dialog, and cancelling returns to the node list.
    //
    // The first cut rendered the hub underneath in a not-signed-in
    // state — an identity card, an explanatory line, a Sign in button
    // and a single tile. Driving it on hardware, that screen is 80%
    // empty and offers nothing you came for. The reference client had
    // this right from the start: `contacts_screen` shows the login
    // dialog and only pushes the hub `onLogin` success, so an
    // unusable hub cannot exist.
    if (!session.signedIn) {
        // Callers sign in first (rememberAdminOpener), so this is only reached by a route
        // opened some other way, or a session that ended. Paint a surface behind the dialog
        // rather than leave the window bare (black in dark theme).
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        )
        RepeaterLoginDialog(
            vm = vm,
            keyHex = keyHex,
            nodeName = name,
            nodeType = contact?.type,
            onDismiss = { if (!(vm.adminSessions.value[keyHex] ?: AdminSession.None).signedIn) nav.popBackStack() },
        )
        return
    }

    val tiles = remember(role, session) { repeaterHubTiles(role, session) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = name,
                vm = vm,
                nav = nav,
                subtitle = stringResource(
                    when (role) {
                        NodeRole.Room -> R.string.kind_room
                        NodeRole.Sensor -> R.string.kind_sensor
                        else -> R.string.kind_repeater
                    },
                ),
                menuActions = buildList {
                    if (session.signedIn) {
                        add(MenuAction(stringResource(R.string.rpt_request_status)) { vm.requestRepeaterStatus(keyHex) })
                    }
                    if (session.isAdmin) {
                        add(
                            MenuAction(stringResource(R.string.rpt_sync_clock)) {
                                vm.sendCli(keyHex, "time ${System.currentTimeMillis() / 1000}")
                            },
                        )
                    }
                    if (session.signedIn) {
                        add(MenuAction(stringResource(R.string.rpt_sign_out)) { vm.signOutOfNode(keyHex) })
                    }
                    add(
                        MenuAction(stringResource(R.string.rpt_forget_password), destructive = true) {
                            vm.forgetLoginPassword(keyHex)
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            IdentityCard(
                name = name,
                keyHex = keyHex,
                type = contact?.type,
                pathLen = contact?.pathLen,
                latitude = contact?.latitude,
                longitude = contact?.longitude,
                session = session,
            )

            if (showsGuestQueries(role, session)) {
                GuestQueriesCard(vm, keyHex)
            }

            SectionLabel(
                when (session) {
                    AdminSession.Admin -> stringResource(R.string.rpt_tools_admin)
                    else -> stringResource(R.string.rpt_tools_guest)
                },
            )

            for (tile in tiles) {
                HubTileRow(tile) { nav.navigate("repeater/$keyHex/${tile.route}") }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Who you are administering and what you were granted, in one card at
 * the top of the hub. The grant chip is the whole of the old
 * three-line read-only explainer.
 */
@Composable
private fun IdentityCard(
    name: String,
    keyHex: String,
    type: Int?,
    pathLen: Int?,
    latitude: Double?,
    longitude: Double?,
    session: AdminSession,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeAvatar(seed = keyHex, label = name, type = type, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    keyHex.take(12),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pathLen?.let {
                    val info = PathCodec.decodePathLen(it)
                    Text(
                        when {
                            info.isFlood -> stringResource(R.string.path_flood)
                            info.hops == 0 -> stringResource(R.string.path_direct)
                            else -> pluralStringResource(R.plurals.hops, info.hops, info.hops)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isPlausiblePosition(latitude, longitude)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_location_on),
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "%.4f, %.4f".format(latitude, longitude),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            GrantChip(session)
        }
    }
}

/**
 * Where one manual request stands. [NotAsked] is the resting state:
 * opening the hub puts nothing on the air. Silence is its own state,
 * never shown as an empty answer.
 */
private sealed interface Ask<out T> {
    data object NotAsked : Ask<Nothing>
    data object Asking : Ask<Nothing>
    data class Done<T>(val result: T) : Ask<T>
}

/**
 * What a guest can ask a repeater, each on a tap.
 *
 * The node takes CLI `get`s from admins only, so Settings and Regions are
 * closed to a guest. Two things are still open: owner text and firmware
 * version (`REQ_TYPE_GET_OWNER_INFO`, see [OwnerInfo]) and the regions the
 * repeater floods (the anonymous regions request, see
 * [io.github.thatsfguy.meshcore.protocol.Regions.FloodList]).
 */
@Composable
private fun GuestQueriesCard(vm: MeshCoreViewModel, keyHex: String) {
    var ownerAsks by remember(keyHex) { mutableIntStateOf(0) }
    var owner by remember(keyHex) { mutableStateOf<Ask<OwnerInfo.Reply?>>(Ask.NotAsked) }
    LaunchedEffect(keyHex, ownerAsks) {
        if (ownerAsks == 0) return@LaunchedEffect
        owner = Ask.Asking
        owner = Ask.Done(vm.repeaterOwnerInfo(keyHex))
    }
    var regionAsks by remember(keyHex) { mutableIntStateOf(0) }
    var regions by remember(keyHex) { mutableStateOf<Ask<FloodRegionsAsk>>(Ask.NotAsked) }
    LaunchedEffect(keyHex, regionAsks) {
        if (regionAsks == 0) return@LaunchedEffect
        regions = Ask.Asking
        regions = Ask.Done(vm.repeaterFloodRegions(keyHex))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            QueryRow(
                title = stringResource(R.string.rpt_owner),
                idle = stringResource(R.string.rpt_owner_idle),
                ask = owner,
                onAsk = { ownerAsks++ },
            ) { reply ->
                if (reply == null) {
                    NoAnswerText(stringResource(R.string.rpt_owner_none))
                } else {
                    Text(
                        reply.ownerInfo.ifEmpty { stringResource(R.string.rpt_owner_empty) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reply.ownerInfo.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        stringResource(R.string.rpt_firmware, reply.firmwareVersion),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            QueryRow(
                title = stringResource(R.string.backup_regions),
                idle = stringResource(R.string.rpt_regions_idle),
                ask = regions,
                onAsk = { regionAsks++ },
            ) { outcome ->
                when (outcome) {
                    FloodRegionsAsk.NoRoute -> NoAnswerText(
                        stringResource(R.string.rpt_regions_noroute),
                    )
                    FloodRegionsAsk.NoAnswer -> NoAnswerText(
                        stringResource(R.string.rpt_regions_none),
                    )
                    is FloodRegionsAsk.Answered -> {
                        Text(
                            floodRegionsSummary(outcome.list),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.rpt_regions_partial),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A labelled request: its idle line and button, then its answer. */
@Composable
private fun <T> QueryRow(
    title: String,
    idle: String,
    ask: Ask<T>,
    onAsk: () -> Unit,
    answer: @Composable (T) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            when (ask) {
                Ask.NotAsked -> NoAnswerText(idle)
                Ask.Asking -> NoAnswerText(stringResource(R.string.mapnode_asking))
                is Ask.Done -> answer(ask.result)
            }
        }
        if (ask !is Ask.Asking) {
            TextButton(onClick = onAsk) {
                Text(stringResource(if (ask is Ask.NotAsked) R.string.rpt_ask else R.string.rpt_ask_again))
            }
        }
    }
}

@Composable
private fun NoAnswerText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What the node granted — never what was asked for. */
@Composable
private fun GrantChip(session: AdminSession) {
    val color = when (session) {
        AdminSession.Admin -> MaterialTheme.colorScheme.primary
        AdminSession.Guest, AdminSession.Member -> MaterialTheme.colorScheme.tertiary
        AdminSession.None -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            session.chipLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun HubTileRow(tile: HubTile, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            androidx.compose.ui.res.painterResource(hubTileIcon(tile.route)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(hubTileTitle(tile.route, tile.title), style = MaterialTheme.typography.bodyLarge)
            Text(
                hubTileSubtitle(tile.route, tile.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hubTileIcon(route: String): Int = when (route) {
    "status" -> R.drawable.ic_info
    "settings" -> R.drawable.ic_settings
    "regions" -> R.drawable.ic_location_on
    "identity" -> R.drawable.ic_lock
    "console" -> R.drawable.ic_terminal
    "firmware" -> R.drawable.ic_update
    "help" -> R.drawable.ic_help
    else -> R.drawable.ic_list
}

/** Translated hub tile text; the tiles and their gating stay upstream's (RepeaterHubModel). */
@Composable
private fun hubTileTitle(route: String, fallback: String): String = when (route) {
    "status" -> stringResource(R.string.rpt_tile_status)
    "settings" -> stringResource(R.string.rpt_tile_settings)
    "regions" -> stringResource(R.string.backup_regions)
    "identity" -> stringResource(R.string.hub_identity)
    "console" -> stringResource(R.string.rpt_tile_console)
    "firmware" -> stringResource(R.string.hub_firmware)
    "help" -> stringResource(R.string.rpt_tile_help)
    else -> fallback
}

@Composable
private fun hubTileSubtitle(route: String, fallback: String): String = when (route) {
    "status" -> stringResource(R.string.rpt_tile_status_sub)
    "settings" -> stringResource(R.string.rpt_tile_settings_sub)
    "regions" -> stringResource(R.string.rpt_tile_regions_sub)
    "identity" -> stringResource(R.string.rpt_tile_identity_sub)
    "console" -> stringResource(R.string.rpt_tile_console_sub)
    "firmware" -> stringResource(R.string.rpt_tile_firmware_sub)
    "help" -> stringResource(R.string.rpt_tile_help_sub)
    else -> fallback
}
