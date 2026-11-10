package io.alvaroedo.meshconnect

import io.github.thatsfguy.meshcore.presentation.decodePrefill
import io.github.thatsfguy.meshcore.presentation.Inbox
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import io.alvaroedo.meshconnect.ui.screens.backdropBlur
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.alvaroedo.meshconnect.platform.BlePermissions
import io.alvaroedo.meshconnect.ui.FirmwareTargetKind
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.screens.FirmwareScreen
import io.github.thatsfguy.meshcore.firmware.FirmwareRole
import io.alvaroedo.meshconnect.ui.screens.HEARD_REPEATS_ROUTE
import io.alvaroedo.meshconnect.ui.screens.HeardRepeatsScreen
import io.alvaroedo.meshconnect.ui.screens.CONVERSATION_ROUTE
import io.alvaroedo.meshconnect.ui.screens.ChatsScreen
import io.alvaroedo.meshconnect.ui.screens.conversationRoute
import io.alvaroedo.meshconnect.ui.screens.ConversationScreen
import io.alvaroedo.meshconnect.ui.screens.MapScreen
import io.alvaroedo.meshconnect.ui.screens.TRACE_ROUTE
import io.alvaroedo.meshconnect.ui.screens.TraceScreen
import io.alvaroedo.meshconnect.ui.screens.NodesScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterConsoleScreen
import io.alvaroedo.meshconnect.ui.screens.ScanConfirmations
import io.alvaroedo.meshconnect.ui.screens.RepeaterFirmwareScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterHelpScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterHubScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterIdentityScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterRegionsScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterSettingsScreen
import io.alvaroedo.meshconnect.ui.screens.RepeaterStatusScreen
import io.alvaroedo.meshconnect.ui.screens.AboutScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsAppScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsAutoAddScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsBackupScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsBlockingScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsChannelsScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsClockScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsConnectionScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsCustomVarsScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsDataScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsDevicePinScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsIdentityScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsPoliciesScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsRadioScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsScreen
import io.alvaroedo.meshconnect.ui.screens.SettingsTransportsScreen
import io.alvaroedo.meshconnect.ui.screens.SetupScreen
import io.alvaroedo.meshconnect.ui.theme.MeshCoreTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /**
     * A conversation the user asked for by tapping its notification.
     *
     * Held as state rather than read directly in the composable because
     * it arrives twice over: once in the Intent that starts the activity
     * cold, and again through [onNewIntent] when the app is already
     * running — which, with `launchMode=singleTask`, is the common case.
     */
    private val openThread = MutableStateFlow<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars; the app's top bar and bottom navigation
        // draw under them (see MeshCoreTheme).
        enableEdgeToEdge()
        // First run asks when the user goes looking for a radio (RadioPicker), with the
        // reason on screen; asking here, before anything is shown, explains nothing.
        if (io.alvaroedo.meshconnect.storage.Preferences(this).setupComplete && !BlePermissions.allGranted(this)) {
            permissionLauncher.launch(BlePermissions.required())
        }
        // MapLibre needs its singleton before any MapView exists.
        io.alvaroedo.meshconnect.ui.map.MapStyles.init(this)
        openThread.value = threadFrom(intent)
        setContent {
            val vm: MeshCoreViewModel = viewModel()
            val theme by vm.prefs.themeFlow.collectAsStateWithLifecycle()
            MeshCoreTheme(
                darkTheme = when (theme) {
                    "dark" -> true
                    "light" -> false
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                AppShell(vm, openThread)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() current, or a later configuration change
        // replays whichever notification started the activity.
        setIntent(intent)
        openThread.value = threadFrom(intent)
    }

    private fun threadFrom(intent: Intent?): Pair<String, String>? {
        val kind = intent?.getStringExtra(EXTRA_THREAD_KIND)?.takeIf { it.isNotBlank() }
        val peer = intent?.getStringExtra(EXTRA_THREAD_PEER)?.takeIf { it.isNotBlank() }
        return if (kind != null && peer != null) kind to peer else null
    }

    companion object {
        const val EXTRA_THREAD_KIND = "thread_kind"
        const val EXTRA_THREAD_PEER = "thread_peer"
    }
}

private const val SLIDE_MS = 250
private const val TAB_FADE_MS = 120
private val TAB_ROUTES = setOf("chats", "nodes", "map", "settings")

/**
 * Screens that show one radio's conversation or one of its nodes, which mean nothing once
 * another radio is connected. Settings screens follow whichever radio is connected, and stay.
 */
internal fun leavesOnRadioSwitch(route: String?): Boolean =
    route != null && (
        route.startsWith("conversation/") || route.startsWith("repeater/") || route.startsWith("trace/")
        )

/** Both ends are bottom-bar tabs: switching tabs, not drilling in. */
internal fun isTabSwitch(from: String?, to: String?): Boolean =
    from in TAB_ROUTES && to in TAB_ROUTES

private data class Tab(
    val route: String,
    val label: String,
    /** "" for no badge — [Inbox.badgeLabel] decides, not the caller. */
    val badge: String = "",
    val dot: Boolean = false,
    val icon: @Composable () -> Unit,
)

@Composable
private fun AppShell(
    vm: MeshCoreViewModel,
    openThread: MutableStateFlow<Pair<String, String>?>,
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // Tapping a notification lands on the conversation it was about.
    // popUpTo("chats") so Back leaves you in the app on the message
    // list, rather than dropping you out of it or onto whatever screen
    // happened to be open when the message arrived.
    val pendingThread by openThread.collectAsStateWithLifecycle()
    LaunchedEffect(pendingThread) {
        val (kind, peer) = pendingThread ?: return@LaunchedEffect
        nav.navigate(conversationRoute(kind, peer)) {
            popUpTo("chats")
            launchSingleTop = true
        }
        openThread.value = null
    }

    // Another radio took over (the top bar's switcher, or roaming between saved radios):
    // a conversation or a node's admin screen belongs to the radio it was opened on, and
    // left open it shows an empty thread under a bare key. Back to Chats instead. The same
    // radio reconnecting is not a switch.
    LaunchedEffect(Unit) {
        var last: String? = null
        vm.selfInfo.collect { info ->
            val key = info?.publicKeyHex ?: return@collect
            if (last != null && key != last && leavesOnRadioSwitch(nav.currentDestination?.route)) {
                if (!nav.popBackStack("chats", inclusive = false)) {
                    nav.navigate("chats") { popUpTo(nav.graph.startDestinationId) { inclusive = true } }
                }
            }
            last = key
        }
    }

    val transient by vm.transientMessage.collectAsStateWithLifecycle()
    LaunchedEffect(transient) {
        transient?.let {
            snackbar.showSnackbar(it)
            vm.transientMessage.value = null
        }
    }

    // The count the Chats tab carries. Every row's badge already exists
    // inside the list; what was missing is the one number visible from
    // the other three tabs, which is the only place it changes anyone's
    // behaviour — a message that arrives while you are on Map or Nodes
    // was otherwise invisible until you happened to look.
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val unreadBadge = Inbox.badgeLabel(Inbox.unreadTotal(conversations.map { it.unread }))
    // Nothing to count, but a chat marked unread by hand: a dot.
    val unreadDot = unreadBadge.isEmpty() && conversations.any { it.markedUnread }

    val tabs = listOf(
        Tab("chats", stringResource(R.string.nav_chats), unreadBadge, dot = unreadDot) { Icon(painterResource(R.drawable.ic_chat), null) },
        Tab("nodes", stringResource(R.string.nav_nodes)) { Icon(painterResource(R.drawable.ic_hub), null) },
        Tab("map", stringResource(R.string.nav_map)) { Icon(painterResource(R.drawable.ic_map), null) },
        Tab("settings", stringResource(R.string.nav_settings)) { Icon(painterResource(R.drawable.ic_settings), null) },
    )
    val unreadLabel = stringResource(R.string.unread_count, unreadBadge)
    val backdrop = remember { io.alvaroedo.meshconnect.ui.screens.BackdropBlur() }

    androidx.compose.runtime.CompositionLocalProvider(
        io.alvaroedo.meshconnect.ui.screens.LocalBackdropBlur provides backdrop,
    ) {
    Scaffold(
        modifier = Modifier.backdropBlur(backdrop),
        snackbarHost = { SnackbarHost(snackbar) },
        // Edge-to-edge: the shell adds no system-bar padding of its own.
        // Each screen's top bar pads for the status bar, the NavigationBar
        // for the gesture bar, and screens without it pad themselves.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            // Hide the bar inside conversation/repeater routes.
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.badge.isEmpty() && tab.dot) {
                                    BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) { tab.icon() }
                                } else if (tab.badge.isEmpty()) {
                                    tab.icon()
                                } else {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ) {
                                                Text(
                                                    tab.badge,
                                                    // The number is on the icon, which
                                                    // already names the tab; a screen
                                                    // reader saying "Chats, 3" beats
                                                    // "Chats, 3, Chats".
                                                    modifier = Modifier.semantics {
                                                        contentDescription = unreadLabel
                                                    },
                                                )
                                            }
                                        },
                                    ) { tab.icon() }
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Decided once. Read on every recomposition, it changed from "setup" to "chats"
        // when setup finished, and the next recomposition (the first message sent, which
        // updates the chat list) handed NavHost a different graph: it reset the back stack
        // to the new start and threw the user out of the chat (reproduced 2026-11-10).
        val startDestination = remember { if (vm.prefs.setupComplete) "chats" else "setup" }
        NavHost(
            navController = nav,
            // First run lands on setup rather than an empty Chats list
            // with no hint that a radio is required (PARITY §1).
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            // Push: the new screen slides in from the right over the old
            // one; back: it slides out to the right, uncovering the one
            // below (Skypost, Signal). Between tabs: a short crossfade.
            enterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeIn(tween(TAB_FADE_MS))
                } else {
                    slideInHorizontally(tween(SLIDE_MS)) { it }
                }
            },
            exitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeOut(tween(TAB_FADE_MS))
                } else {
                    slideOutHorizontally(tween(SLIDE_MS)) { -it / 4 }
                }
            },
            popEnterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeIn(tween(TAB_FADE_MS))
                } else {
                    slideInHorizontally(tween(SLIDE_MS)) { -it / 4 }
                }
            },
            popExitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    fadeOut(tween(TAB_FADE_MS))
                } else {
                    slideOutHorizontally(tween(SLIDE_MS)) { it }
                }
            },
        ) {
            composable("setup") {
                SetupScreen(vm) {
                    nav.navigate("chats") { popUpTo("setup") { inclusive = true } }
                }
            }
            composable("chats") { ChatsScreen(vm, nav) }
            composable("chats/archived") { ChatsScreen(vm, nav, archivedView = true) }
            composable("nodes") { NodesScreen(vm, nav) }
            // Reached from Nodes ⋮ — it is about the repeaters around
            // this node, which is what that tab is for.
            composable(HEARD_REPEATS_ROUTE) { HeardRepeatsScreen(vm, nav) }
            composable("map") { MapScreen(vm, nav) }
            composable(TRACE_ROUTE) { entry ->
                TraceScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            // Settings is hub-and-spoke for the same reason repeater
            // admin is: eleven expandable sections on one scroll, one of
            // which held another eight surfaces (REBUILD-PLAYBOOK §6.2).
            composable("settings") { SettingsScreen(vm, nav) }
            composable("settings/connection") { SettingsConnectionScreen(vm, nav) }
            composable("settings/transports") { SettingsTransportsScreen(vm, nav) }
            composable("settings/pin") { SettingsDevicePinScreen(vm, nav) }
            composable("settings/identity") { SettingsIdentityScreen(vm, nav) }
            composable("settings/radio") { SettingsRadioScreen(vm, nav) }
            composable("settings/clock") { SettingsClockScreen(vm, nav) }
            composable("settings/policies") { SettingsPoliciesScreen(vm, nav) }
            composable("settings/autoadd") { SettingsAutoAddScreen(vm, nav) }
            composable("settings/customvars") { SettingsCustomVarsScreen(vm, nav) }
            composable("settings/firmware") { FirmwareScreen(vm, nav) }
            // The repeater path reaches the same screen with a different
            // target: that node is already in update mode, so nothing
            // here may reboot the phone's own radio.
            composable(
                "firmware/node?role={role}&mac={mac}&board={board}&fw={fw}&node={node}",
                arguments = listOf(
                    navArgument("role") { defaultValue = "repeater" },
                    navArgument("mac") { defaultValue = "" },
                    // Hex-encoded like the console prefill: a board name
                    // has spaces and brackets in it.
                    navArgument("board") { defaultValue = "" },
                    navArgument("fw") { defaultValue = "" },
                    navArgument("node") { defaultValue = "" },
                ),
            ) { entry ->
                FirmwareScreen(
                    vm,
                    nav,
                    FirmwareTargetKind.NodeInUpdateMode,
                    // The address the node reported in its own reply to
                    // `start ota`, when we caught it.
                    otaAddress = entry.arguments?.getString("mac")?.ifBlank { null },
                    nodeBoard = decodePrefill(entry.arguments?.getString("board") ?: "")
                        .ifBlank { null },
                    nodeVersion = decodePrefill(entry.arguments?.getString("fw") ?: "")
                        .ifBlank { null },
                    nodeKey = entry.arguments?.getString("node")?.ifBlank { null },
                    // A repeater and a room server run different builds
                    // on identical hardware, so the role travels with
                    // the route rather than being inferred.
                    role = when (entry.arguments?.getString("role")) {
                        "room" -> FirmwareRole.RoomServer
                        else -> FirmwareRole.Repeater
                    },
                )
            }
            composable("settings/channels") { SettingsChannelsScreen(vm, nav) }
            composable("settings/blocking") { SettingsBlockingScreen(vm, nav) }
            composable("settings/app") { SettingsAppScreen(vm, nav) }
            composable("settings/backup") { SettingsBackupScreen(vm, nav) }
            composable("settings/data") { SettingsDataScreen(vm, nav) }
            composable("settings/about") { AboutScreen(vm, nav) }
            composable(CONVERSATION_ROUTE) { entry ->
                ConversationScreen(
                    vm = vm,
                    nav = nav,
                    kind = entry.arguments?.getString("kind") ?: "dm",
                    peerKey = entry.arguments?.getString("peer") ?: "",
                )
            }
            // Repeater/room/sensor admin is hub-and-spoke: `repeater/{key}`
            // is the hub, and each tool owns a route under it. This was
            // one screen with six tabs (REBUILD-PLAYBOOK §6.2) — the
            // spokes exist so that "back" means something and so each
            // tool's app bar can say which node it is acting on.
            composable("repeater/{key}") { entry ->
                RepeaterHubScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/status") { entry ->
                RepeaterStatusScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/settings") { entry ->
                RepeaterSettingsScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/regions") { entry ->
                RepeaterRegionsScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/identity") { entry ->
                RepeaterIdentityScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/help") { entry ->
                RepeaterHelpScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable("repeater/{key}/firmware") { entry ->
                RepeaterFirmwareScreen(vm, nav, entry.arguments?.getString("key") ?: "")
            }
            composable(
                "repeater/{key}/console?prefill={prefill}",
                arguments = listOf(
                    navArgument("prefill") { defaultValue = "" },
                ),
            ) { entry ->
                RepeaterConsoleScreen(
                    vm = vm,
                    nav = nav,
                    keyHex = entry.arguments?.getString("key") ?: "",
                    prefill = decodePrefill(entry.arguments?.getString("prefill") ?: ""),
                )
            }
        }

        // Scan confirmations live HERE, once, rather than on whichever
        // screen happens to host a scanner.
        //
        // They used to be composed only by NodesScreen, so a code
        // scanned from the Chats button set the pending state and
        // nothing ever drew it: the scan appeared to do nothing at all.
        // That is the same failure as the old "Show route on map" — a
        // flag set for a screen that was not listening — and it applies
        // to contact cards, channel shares and settings codes alike.
        ScanConfirmations(vm)
    }
    }
}
