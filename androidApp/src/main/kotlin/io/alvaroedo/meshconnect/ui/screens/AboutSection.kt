package io.alvaroedo.meshconnect.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel

const val SOURCE_URL = "https://code.alvaroedo.io/alvaroemtnez/MeshConnect"
const val UPSTREAM_URL = "https://github.com/thatSFguy/meshcore-mobile-app"

private data class Credit(val who: String, val what: Int, val url: String, val terms: String)

private val BASED_ON = listOf(
    Credit("MeshCore Hardened · thatSFguy", R.string.about_credit_mch, UPSTREAM_URL, "AGPL-3.0-only"),
    Credit(
        "reticulum-mobile-app · thatSFguy", R.string.about_credit_reticulum,
        "https://github.com/thatSFguy/reticulum-mobile-app", "AGPL-3.0",
    ),
    Credit("MeshCore · meshcore-dev", R.string.about_credit_meshcore, "https://github.com/meshcore-dev/MeshCore", "MIT"),
)

/** (component, licence, bundled text in assets/licences/) — every one is shipped in the APK. */
private val LIBRARIES = listOf(
    Triple("Android Jetpack, Jetpack Compose, Material 3, Room, Glance, CameraX", "Apache-2.0", "apache-2.0.txt"),
    Triple("Kotlin, kotlinx.coroutines, kotlinx.serialization, kotlinx-datetime", "Apache-2.0", "apache-2.0.txt"),
    Triple("MapLibre Native · © MapLibre contributors, MapTiler.com, Mapbox", "BSD-2-Clause", "maplibre-native.txt"),
    Triple("MapLibre gestures · © Mapbox, MapLibre contributors", "BSD-2-Clause", "maplibre-gestures.txt"),
    Triple("MapLibre GeoJSON and Turf", "Apache-2.0", "apache-2.0.txt"),
    Triple("SQLCipher for Android · © ZETETIC LLC", "BSD-3-Clause", "sqlcipher.txt"),
    Triple("Bouncy Castle · © The Legion of the Bouncy Castle Inc.", "MIT", "bouncycastle.txt"),
    Triple("ZXing", "Apache-2.0", "apache-2.0.txt"),
    Triple("OkHttp, Okio (Square), Gson, Guava listenablefuture (Google), Timber (Jake Wharton), JetBrains annotations", "Apache-2.0", "apache-2.0.txt"),
    Triple("Material Symbols (Google)", "Apache-2.0", "apache-2.0.txt"),
    Triple("Map tiles: OpenFreeMap, OpenMapTiles · © OpenStreetMap contributors", "ODbL · CC-BY 4.0", null),
)

private val PRIVACY = listOf(
    R.string.about_privacy_1,
    R.string.about_privacy_2,
    R.string.about_privacy_3,
    R.string.about_privacy_4,
    R.string.about_privacy_5,
)

/**
 * About: who made this, what it is built on, what it promises.
 *
 * The privacy claims are testable ones (no accounts, no analytics, which
 * hosts it talks to) rather than slogans, carried over from MeshCore
 * Hardened's About. The credits name the fork's origin first.
 */
@Composable
fun AboutScreen(vm: MeshCoreViewModel, nav: NavController) {
    val context = LocalContext.current
    val open = { url: String ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        Unit
    }
    var changelog by remember { mutableStateOf(false) }
    // (title, asset file) of the licence being read, if any.
    var licence by remember { mutableStateOf<Pair<String, String>?>(null) }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val rowColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.about_title), vm = vm, nav = nav, menuActions = emptyList())
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    // primary, not primaryContainer: the bubble is white, as on the
                    // launcher's teal, and white on a pale container barely showed.
                    Modifier.size(96.dp).clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    // The bubble is 40 of the foreground's 108 units (the rest is the
                    // adaptive-icon safe zone), so the image is drawn larger than its tile:
                    // 176 dp puts a 65 dp bubble in the 96 dp tile, as on the launcher.
                    Image(
                        painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.requiredSize(176.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.about_version, version),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "© 2026 Álvaro Edo Martínez\n© thatSFguy and MeshCore Hardened contributors",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.about_description),
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_source)) },
                supportingContent = { Text(SOURCE_URL) },
                leadingContent = { Icon(painterResource(R.drawable.ic_code), null) },
                modifier = Modifier.clickable { open(SOURCE_URL) },
                colors = rowColors,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_issues)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_bug_report), null) },
                modifier = Modifier.clickable { open("$SOURCE_URL/issues") },
                colors = rowColors,
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_whats_new)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_history), null) },
                modifier = Modifier.clickable { changelog = true },
                colors = rowColors,
            )

            AboutGroup(stringResource(R.string.about_based_on))
            for (c in BASED_ON) {
                ListItem(
                    headlineContent = { Text(c.who) },
                    supportingContent = { Text(stringResource(c.what) + " · " + c.terms) },
                    modifier = Modifier.clickable { open(c.url) },
                    colors = rowColors,
                )
            }
            Text(
                stringResource(R.string.about_endorsement),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AboutGroup(stringResource(R.string.about_privacy))
            for (line in PRIVACY) {
                ListItem(
                    headlineContent = { Text(stringResource(line), style = MaterialTheme.typography.bodyMedium) },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_shield), null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = rowColors,
                )
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            AboutGroup(stringResource(R.string.about_licences))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_this_licence)) },
                supportingContent = { Text("AGPL-3.0-only") },
                leadingContent = { Icon(painterResource(R.drawable.ic_gavel), null) },
                modifier = Modifier.clickable { licence = "MeshConnect" to "agpl-3.0.txt" },
                colors = rowColors,
            )
            for ((what, terms, file) in LIBRARIES) {
                ListItem(
                    headlineContent = { Text(what) },
                    supportingContent = { Text(terms) },
                    modifier = if (file != null) Modifier.clickable { licence = what to file } else Modifier,
                    colors = rowColors,
                )
            }
        }
    }

    licence?.let { (title, file) ->
        val text = remember(file) {
            runCatching { context.assets.open("licences/$file").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { licence = null },
            title = { Text(title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = io.alvaroedo.meshconnect.ui.theme.MonoFontFamily,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { licence = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (changelog) {
        AlertDialog(
            onDismissRequest = { changelog = false },
            title = { Text(stringResource(R.string.about_whats_new)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for ((release, notes) in CHANGELOG) {
                        Text(release, style = MaterialTheme.typography.labelLarge)
                        for (note in notes) {
                            Text("• $note", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { changelog = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun AboutGroup(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/**
 * Release notes, newest first. Held to CHANGELOG.md's version list by
 * ChangelogTest. Kept in the app rather than behind a URL: a changelog you
 * need a network to read is one you can't read in the field.
 *
 * MeshConnect's own versions only; MeshCore Hardened's history up to the
 * fork is in docs/upstream/CHANGELOG-MeshCoreHardened.md.
 */
private val CHANGELOG: List<Pair<String, List<String>>> = listOf(
    "1.2.0" to listOf(
        "The widget shows the link with an icon (connected, connecting, not connected) and says it in words when there is no link.",
        "The widget follows the link as it changes, instead of sometimes staying on \"connected\".",
        "Targets Android 17: the radio service runs all day and starts with the phone on every link.",
        "On Android 17, a network (TCP) radio asks for local network access, part of Nearby devices.",
    ),
    "1.1.0" to listOf(
        "Widgets redone: every size from 2×1 to 4×4 uses its space, with the latest message and nearby repeaters sharing it.",
        "Bigger chat and node rows, with two initials for contacts.",
        "New QR scanner: rounded frame, prompt in a bubble, still reads light-on-dark codes.",
        "Repeater settings with pickers, a map picker, clearer announce and maintenance buttons.",
        "Repeater sign-in follows your radio when you switch companions.",
        "Swiping a chat to read or unread toggles once, every time.",
        "Popup menus share the rounded long-press look; New chat has a new icon.",
        "A larger logo on the About page.",
    ),
    "1.0.0" to listOf(
        "First MeshConnect release, forked from MeshCore Hardened 0.10.15.",
        "New name and app id: it installs alongside MeshCore Hardened, with its own database.",
        "Material You colours and icons, edge-to-edge bars, quick transitions and a new icon.",
        "Spanish and Valencian throughout, following the phone or a per-app language.",
        "Link meter in the top bar: signal, battery and live radio statistics.",
        "Search in Chats and Nodes.",
        "Composer + menu: share contacts, channels and locations, shown as cards.",
        "Nodes: kind filters with matching icons on the map and list, heard nodes in a fold-out row, QR scan button.",
        "MapLibre map that follows light and dark mode.",
        "Ping and trace a route on the map, coloured by SNR.",
        "Home-screen widget from 2×1 to 4×3, with nearby repeaters at the largest size.",
        "Connect without Settings: a three-step first run, and connect or switch radios from the top bar.",
        "Settings reworked into grouped cards with icons; reboot radio is in This node.",
        "Signal-style long-press on messages; swipe chats to mark read or archive.",
        "A \"via …\" chip on received messages opens the route on a map.",
        "Select several messages to copy or delete; the long-press blur shows reliably.",
        "Sending your location shows it on a map to confirm first.",
        "Chats: channel icons, a long-press card with edit and delete, and multi-select.",
        "Mark unread works on every chat and shows a dot.",
        "One node card for the Nodes list, the map, heard nodes and nearby repeaters; tapping any node opens it.",
        "Message info laid out like the node card.",
        "Telemetry grouped by channel: the board apart from each sensor.",
        "New chat: message a contact, add a channel or scan any code.",
        "Public and hashtag channel keys can no longer be edited by accident.",
        "Share QR links through Android's share sheet.",
        "meshcore:// links open the app; New chat can paste a code from the clipboard.",
        "Settings polish: spacing, a regrouped Identity screen and radio value pickers.",
        "Fixes for switching radios from a chat and for the first message after setup.",
        "Repeater Settings, Status, Regions and Identity redesigned as cards.",
        "The message database opens while the phone is locked, after the first unlock.",
        "No jump when long-pressing a message with the keyboard up.",
        "The widget uses all of its size on any launcher grid.",
        "Battery percentage follows a Li-ion discharge curve.",
        "Start with the phone, and switch to a saved radio in range.",
        "Each radio keeps its own chat settings: mutes, pins, archives, channel regions.",
        "Radio battery alerts at 20% and 10%, at no cost to the phone's battery.",
        "Node sign-in opens over the current screen.",
        "Regional presets re-synced with MeshCore Open.",
        "Hop labels follow the mesh's hop width.",
        "Android only; newer Kotlin, AGP and MapLibre.",
        "New About screen with credits and readable licences.",
        "Long node names no longer wrap the top bar.",
        "Each keystore fallback tier gets its own key generator (upstream PR #10).",
    ),
)
