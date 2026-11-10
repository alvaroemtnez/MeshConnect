package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.platform.PhoneLocator
import io.alvaroedo.meshconnect.ui.model.RichMessage
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.ShareUri
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.thatsfguy.meshcore.presentation.AdminSession
import io.github.thatsfguy.meshcore.presentation.Inbox
import io.alvaroedo.meshconnect.ui.model.MessageSelection
import io.github.thatsfguy.meshcore.presentation.roomAccessAction
import io.github.thatsfguy.meshcore.presentation.roomAccessNotice
import io.github.thatsfguy.meshcore.presentation.Mentions
import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.storage.MessageStatus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import io.github.thatsfguy.meshcore.protocol.MessageRepeats
import io.github.thatsfguy.meshcore.protocol.HeardVia
import io.github.thatsfguy.meshcore.protocol.PathCodec
import io.github.thatsfguy.meshcore.protocol.ReactionCounts
import io.github.thatsfguy.meshcore.protocol.Quoting
import io.github.thatsfguy.meshcore.protocol.Reactions
import io.github.thatsfguy.meshcore.protocol.Frames
import java.text.DateFormat
import java.util.Date

/**
 * A single thread — direct messages or a channel. Channel threads show
 * the (unauthenticated) sender name on each inbound bubble and an
 * "obfuscated, not secure" banner per SCOPE.md; DMs show delivery ticks
 * driven by RESP_CODE_SENT / PUSH_CODE_SEND_CONFIRMED.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    vm: io.alvaroedo.meshconnect.ui.MeshCoreViewModel,
    nav: NavController,
    kind: String,
    peerKey: String,
) {
    val isChannel = kind == MessageRepository.KIND_CHANNEL
    val openAdmin = rememberAdminOpener(vm, nav)
    // Paged scrollback: long threads load newest-first in windows.
    var pageSize by remember(kind, peerKey) { mutableStateOf(PAGE_SIZE) }
    val total by remember(kind, peerKey) { vm.threadCount(kind, peerKey) }.collectAsState()
    val messages by remember(kind, peerKey, pageSize) {
        vm.threadPaged(kind, peerKey, pageSize)
    }.collectAsState()
    // threadPaged returns newest-first, which is exactly what the
    // reversed layout below wants — index 0 is the newest bubble and is
    // drawn at the bottom.
    val contacts by vm.dbContacts.collectAsState()
    val channels by vm.dbChannels.collectAsState()
    val channelKinds by vm.channelKinds.collectAsState()

    // A room relays posts from many people, so — like a channel — each
    // bubble has to say who wrote it. The room server is not the author.
    val isRoom = contacts.firstOrNull { it.keyHex == peerKey }?.type ==
        io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_ROOM
    val showSenders = isChannel || isRoom
    // A room needs a session to work at all — see roomAccessNotice.
    val roomSession = vm.adminSessions.collectAsState().value[peerKey] ?: AdminSession.None
    var roomSignIn by remember { mutableStateOf(false) }

    val title = if (isChannel) {
        val idx = peerKey.toIntOrNull()
        // The name as stored: a hashtag channel's already starts with '#',
        // and prefixing one to every channel made private ones look public.
        channels.firstOrNull { it.idx == idx }?.name?.ifBlank { null }
            ?: stringResource(R.string.ch_numbered, idx ?: 0)
    } else {
        contacts.firstOrNull { it.keyHex == peerKey }?.name?.ifBlank { null }
            ?: peerKey.take(12)
    }

    // Open only while the app is on screen. This was a DisposableEffect,
    // which leaves the thread "open" until the screen is left — and
    // turning the phone off or pocketing it does not leave the screen, it
    // stops the app. So the conversation last on screen was treated as
    // being read indefinitely: its messages neither notified nor counted
    // unread. Reported 2026-09-25 as a phone that stayed silent in a
    // pocket while another, on a different screen, alerted. Coming back
    // re-opens it, which also clears its notification and unread count.
    LifecycleStartEffect(kind, peerKey) {
        vm.markThreadOpen(kind, peerKey)
        onStopOrDispose { vm.markThreadClosed(kind, peerKey) }
    }

    val threadKey = Inbox.threadKey(kind, peerKey)
    // TextFieldValue rather than String: the `@` picker has to know
    // where the caret is, and a plain String composer cannot say. The
    // saved draft stays a String — the caret is not worth persisting,
    // and restoring one into text the user has since edited elsewhere
    // would put it in the wrong place.
    var draft by remember(threadKey) {
        val saved = vm.draftFor(threadKey)
        mutableStateOf(TextFieldValue(saved, TextRange(saved.length)))
    }
    var clearConfirm by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var showChannelEditor by remember { mutableStateOf(false) }
    var showSendersSheet by remember { mutableStateOf(false) }
    var replyingTo by remember(kind, peerKey) { mutableStateOf<MessageEntity?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    var reactingTo by remember { mutableStateOf<MessageEntity?>(null) }
    var details by remember { mutableStateOf<MessageEntity?>(null) }
    // The "heard via" chip's sheet: the route alone, map first.
    var routeOf by remember { mutableStateOf<MessageEntity?>(null) }
    // Full key hex -> name, for naming the hops on route chips. One map for
    // the whole thread; each bubble only splits its own path.
    val liveForRoutes by vm.liveContacts.collectAsState()
    val routeNames = remember(liveForRoutes) {
        liveForRoutes.values.associate { it.publicKeyHex to it.name }
    }
    // Multi-select: null when off, else the ids picked. Ending with
    // nothing picked turns the mode off rather than leaving an empty bar.
    var selection by remember(threadKey) { mutableStateOf<Set<Long>?>(null) }
    var confirmDeleteMany by remember { mutableStateOf<Set<Long>?>(null) }
    val clipboard = LocalClipboardManager.current
    androidx.activity.compose.BackHandler(enabled = selection != null) { selection = null }
    fun toggleSelected(id: Long) {
        selection = selection?.let { MessageSelection.toggle(it, id).ifEmpty { null } }
    }
    var attachOpen by remember { mutableStateOf(false) }
    var pickContact by remember { mutableStateOf(false) }
    var pickChannel by remember { mutableStateOf(false) }
    var pickOnMap by remember { mutableStateOf(false) }
    var confirmMyLocation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selfName = vm.selfInfo.collectAsState().value?.name
    // Who `@` can offer. On a channel that is the names seen posting on
    // it — the same list "Names seen…" shows, and the only names anyone
    // reading the channel would recognise. In a DM there is exactly one
    // other party, so the picker is the peer and nothing else: offering
    // the whole contact list would suggest tagging people who will
    // never see the message.
    var mentionNames by remember(threadKey) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(threadKey, isChannel) {
        mentionNames = if (isChannel) {
            peerKey.toIntOrNull()
                ?.let { vm.channelSenders(it).map { s -> s.name } }
                .orEmpty()
        } else {
            listOfNotNull(title.takeIf { Mentions.canMention(it) })
        }
    }
    val maxBytes = if (isChannel) {
        Frames.maxChannelMessageBytes(selfName)
    } else {
        Frames.maxContactMessageBytes()
    }
    // MeshCore has no reply field, so a quote is literally prefixed to
    // the text — and it comes out of the same ~150-byte budget. Reserve
    // it up front so the composer can't accept a message the quote will
    // push over the limit.
    val quotePrefix = replyingTo?.let { quotePrefixFor(it) }.orEmpty()
    val bodyBudget = (maxBytes - quotePrefix.encodeToByteArray().size).coerceAtLeast(0)

    var muted by remember(peerKey) {
        mutableStateOf(peerKey.toIntOrNull()?.let { vm.prefs.isChannelMuted(it) } ?: false)
    }
    val channelRegions by vm.channelRegions.collectAsState()
    val menu = if (isChannel) {
        listOf(
            MenuAction(stringResource(R.string.conv_mute), checked = muted) {
                peerKey.toIntOrNull()?.let {
                    muted = !muted
                    vm.prefs.setChannelMuted(it, muted)
                }
            },
            MenuAction(stringResource(R.string.conv_mark_unread)) { vm.markUnread(kind, peerKey); nav.popBackStack() },
            MenuAction(stringResource(R.string.conv_channel_settings)) { showChannelEditor = true },
            MenuAction(stringResource(R.string.conv_names_seen)) { showSendersSheet = true },
            MenuAction(stringResource(R.string.conv_clear), destructive = true) { clearConfirm = true },
        )
    } else {
        listOfNotNull(
            MenuAction(stringResource(R.string.conv_mark_unread)) { vm.markUnread(kind, peerKey); nav.popBackStack() },
            // A room's admin tools live inside the room: the hub, which
            // is gated on this same session.
            if (isRoom && roomSession.isAdmin) {
                MenuAction(stringResource(R.string.conv_manage_room)) { nav.navigate("repeater/$peerKey") }
            } else {
                null
            },
            MenuAction(stringResource(R.string.conv_contact_details)) { showContact = true },
            MenuAction(stringResource(R.string.conv_routing)) { showContact = true },
            MenuAction(stringResource(R.string.trace_route), icon = R.drawable.ic_route) { nav.navigate(traceRoute(peerKey)) },
            MenuAction(stringResource(R.string.conv_clear), destructive = true) { clearConfirm = true },
        )
    }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null) {
                SelectionTopBar(count = picked.size, onClose = { selection = null }) {
                    if (MessageSelection.canReply(picked)) {
                        IconButton(onClick = {
                            replyingTo = messages.firstOrNull { it.id == picked.single() }
                            selection = null
                        }) { Icon(painterResource(R.drawable.ic_reply), stringResource(R.string.conv_reply)) }
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(MessageSelection.copyText(messages, picked, showSenders)))
                        selection = null
                    }) { Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.copy)) }
                    SelectionDeleteAction {
                        if (picked.size == 1) {
                            vm.deleteMessage(picked.single())
                            selection = null
                        } else {
                            confirmDeleteMany = picked
                        }
                    }
                }
            } else AppTopBar(
                title = title,
                vm = vm,
                nav = nav,
                // A scoped channel goes out on a narrower flood, which
                // changes who carries it — worth seeing before sending,
                // without diluting the "not secure" part.
                subtitle = if (isChannel) {
                    val idx = peerKey.toIntOrNull()
                    io.alvaroedo.meshconnect.ui.model.SharedText.channelSubtitle(
                        LocalContext.current,
                        idx?.let { channelKinds[it] },
                        idx?.let { channelRegions[it] },
                    )
                } else {
                    null
                },
                menuActions = menu,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // Newest-message visibility is structural, not a scroll
            // effect: reverseLayout anchors index 0 (the newest bubble)
            // to the bottom of the viewport. It therefore stays visible
            // through conversation entry, new arrivals, and the keyboard
            // opening — adjustResize shrinks the viewport from the
            // bottom, and the anchor moves with it. A scrollToItem pin
            // has to be re-fired on every one of those events and misses
            // whichever one you forgot; this cannot.
            //
            // It also preserves the reading position for free: someone
            // scrolled up in the scrollback is far from the anchor, so an
            // arriving message doesn't yank the view.
            ThreadList(
                threadKey = threadKey,
                messages = messages,
                total = total,
                modifier = Modifier.weight(1f),
                onLoadOlder = { pageSize += PAGE_SIZE },
            ) { m ->
                MessageBubble(
                    m,
                    selecting = selection != null,
                    selected = selection?.contains(m.id) == true,
                    onToggleSelected = { toggleSelected(m.id) },
                    onStartSelecting = { selection = setOf(m.id) },
                    showSender = showSenders,
                    selfName = selfName,
                    // Only worth offering when there is a name to insert
                    // and more than one person who could read it.
                    canMention = isChannel && !m.outgoing &&
                        Mentions.canMention(m.senderName.orEmpty()),
                    showAvatar = showSenders,
                    onSwipeReply = { replyingTo = m },
                    onMention = {
                        val insert = Mentions.wire(m.senderName.orEmpty().trim()) + " "
                        val next = draft.text.substring(0, draft.selection.min) +
                            insert + draft.text.substring(draft.selection.max)
                        if (next.encodeToByteArray().size <= bodyBudget) {
                            draft = TextFieldValue(
                                next,
                                TextRange(draft.selection.min + insert.length),
                            )
                            vm.setDraft(threadKey, next)
                        }
                    },
                    onReact = { emoji -> vm.sendReaction(m.id, emoji) },
                    onMoreEmoji = { reactingTo = m },
                    onReply = { replyingTo = m },
                    onInfo = { details = m },
                    routeLabel = if (m.outgoing) null else routeChipLabel(m, routeNames),
                    onRoute = { routeOf = m },
                    onDelete = { vm.deleteMessage(m.id) },
                    onResend = { vm.resendMessage(m.id) },
                    onHttpLink = { pendingUrl = it },
                    onMeshcoreLink = { vm.importContactUri(it) },
                    rich = { content ->
                        RichContentCard(
                            content = content,
                            selfKey = vm.selfInfo.value?.publicKeyHex,
                            tilesEnabled = vm.prefs.mapTilesEnabled,
                            contacts = contacts,
                            channels = channels,
                            onOpenContact = { c ->
                                when (c.type) {
                                    Codes.ADV_TYPE_REPEATER, Codes.ADV_TYPE_SENSOR -> openAdmin(c.keyHex)
                                    else -> nav.navigate(conversationRoute(MessageRepository.KIND_DM, c.keyHex))
                                }
                            },
                            onAddContact = { vm.importContactUri(it) },
                            onJoinChannel = { vm.importScannedCode(it) },
                            onOpenChannel = { idx ->
                                nav.navigate(conversationRoute(MessageRepository.KIND_CHANNEL, idx.toString()))
                            },
                        )
                    },
                )
            }

            if (isRoom) {
                io.alvaroedo.meshconnect.ui.model.SharedText.roomNotice(LocalContext.current, roomSession)?.let { notice ->
                    RoomAccessBanner(
                        text = notice,
                        action = io.alvaroedo.meshconnect.ui.model.SharedText.roomAction(LocalContext.current, roomSession),
                        onAction = { roomSignIn = true },
                    )
                }
            }

            replyingTo?.let { target ->
                ReplyBanner(target, onCancel = { replyingTo = null })
            }

            // The `@` picker. Shown only while something still matches,
            // which is what makes it safe for the query to run on past
            // a space: an `@` typed in prose stops matching within a
            // word or two and the row disappears on its own.
            val mentionQuery = Mentions.activeQuery(draft.text, draft.selection.start)
            val suggestions = remember(mentionQuery, mentionNames) {
                mentionQuery?.let { Mentions.candidates(it, mentionNames) }.orEmpty()
            }
            if (suggestions.isNotEmpty()) {
                MentionPicker(suggestions) { name ->
                    val done = Mentions.complete(draft.text, draft.selection.start, name)
                    if (done.text.encodeToByteArray().size <= bodyBudget) {
                        draft = TextFieldValue(done.text, TextRange(done.cursor))
                        vm.setDraft(threadKey, done.text)
                    }
                }
            }

            MessageComposer(
                draft = draft,
                onDraft = {
                    // Enforce the frame-size limit at input time. Caret-only
                    // moves must always be accepted, or a draft sitting
                    // exactly on the limit becomes un-navigable.
                    if (it.text == draft.text || it.text.encodeToByteArray().size <= bodyBudget) {
                        draft = it
                        vm.setDraft(threadKey, it.text)
                    }
                },
                placeholder = stringResource(if (isChannel) R.string.compose_hint_channel else R.string.compose_hint),
                bytesLeft = bodyBudget - draft.text.encodeToByteArray().size,
                onSend = {
                    val text = quotePrefix + draft.text.trim()
                    if (text.isNotBlank()) {
                        sendText(vm, isChannel, peerKey, text)
                        draft = TextFieldValue("")
                        vm.setDraft(threadKey, "")
                        replyingTo = null
                    }
                },
                onAttach = { attachOpen = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // Render site for the Info action. This block was lost when the
    // long-press dialog became an inline action bar — `details` was still
    // being set, so Info silently did nothing.
    details?.let { m ->
        MessageInfoSheet(
            m,
            vm = vm,
            // Who it came from, for the first pin on the route map. A
            // channel message names its sender in the text; a DM's
            // sender IS the thread.
            senderLabel = m.senderName?.ifBlank { null } ?: title,
            isChannel = isChannel,
            showSender = showSenders,
            // Full key hex -> name, so the arrival route can name its
            // hops instead of showing bare hashes.
            contactNames = vm.liveContacts.collectAsState().value
                .values.associate { it.publicKeyHex to it.name },
            // Orders the candidates of an ambiguous hop nearest-first.
            // Resolved once here rather than per row: it walks the whole
            // contact list, and a route redraws on every arrival.
            metresAway = remember(details) { vm.metresFromThisRadio() },
            onDismiss = { details = null },
        )
    }

    routeOf?.let { m ->
        RouteSheet(
            m,
            vm = vm,
            senderLabel = m.senderName?.ifBlank { null } ?: title,
            contactNames = routeNames,
            metresAway = remember(routeOf) { vm.metresFromThisRadio() },
            onDismiss = { routeOf = null },
        )
    }

    reactingTo?.let { target ->
        ReactionPicker(
            onPick = { emoji ->
                vm.sendReaction(target.id, emoji)
                reactingTo = null
            },
            onDismiss = { reactingTo = null },
        )
    }

    pendingUrl?.let { url ->
        LeaveTheMeshDialog(url, onDismiss = { pendingUrl = null })
    }

    // Attachments: each builds the plain-text form other MeshCore clients
    // can read (RichMessage) and sends it as an ordinary message, without
    // the reply quote: a quoted card would no longer parse as one.
    fun sendAttachment(text: String?) {
        if (text == null) return
        if (text.encodeToByteArray().size > maxBytes) {
            vm.transientMessage.value = context.getString(R.string.attach_too_long)
            return
        }
        sendText(vm, isChannel, peerKey, text)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            confirmMyLocation = true
        } else {
            vm.transientMessage.value = context.getString(R.string.attach_location_denied)
        }
    }
    if (attachOpen) {
        AttachSheet(
            onPick = { a ->
                attachOpen = false
                when (a) {
                    Attachment.MyContact -> {
                        val me = vm.selfInfo.value
                        sendAttachment(AttachmentText.myContact(me?.publicKeyHex, me?.name))
                    }
                    Attachment.Contact -> pickContact = true
                    Attachment.Channel -> pickChannel = true
                    Attachment.MyLocation ->
                        if (PhoneLocator.granted(context)) {
                            confirmMyLocation = true
                        } else {
                            locationPermission.launch(PhoneLocator.PERMISSIONS)
                        }
                    Attachment.MapLocation -> pickOnMap = true
                }
            },
            onDismiss = { attachOpen = false },
        )
    }
    if (pickContact) {
        ContactPickerSheet(
            contacts = contacts,
            onPick = { pickContact = false; sendAttachment(AttachmentText.contact(it)) },
            onDismiss = { pickContact = false },
        )
    }
    if (pickChannel) {
        ChannelPickerSheet(
            channels = channels,
            onPick = { ch ->
                pickChannel = false
                scope.launch {
                    val psk = vm.channelPskHex(ch)
                    if (psk == null) {
                        vm.transientMessage.value = context.getString(R.string.attach_channel_no_key)
                    } else {
                        sendAttachment(ShareUri.encodeChannel(ch.name.ifBlank { "#${ch.idx}" }, psk))
                    }
                }
            },
            onDismiss = { pickChannel = false },
        )
    }
    if (confirmMyLocation) {
        MyLocationDialog(
            vm = vm,
            locate = { locateMe(vm, context) },
            onSend = { lat, lon ->
                confirmMyLocation = false
                sendAttachment(RichMessage.locationText(lat, lon))
            },
            onDismiss = { confirmMyLocation = false },
        )
    }
    if (pickOnMap) {
        val me = vm.selfInfo.value
        PositionPickerDialog(
            vm = vm,
            initialLat = me?.latitude ?: 0.0,
            initialLon = me?.longitude ?: 0.0,
            onPick = { lat, lon ->
                pickOnMap = false
                sendAttachment(RichMessage.locationText(lat, lon))
            },
            onDismiss = { pickOnMap = false },
            forAdvert = false,
        )
    }

    if (roomSignIn) {
        RepeaterLoginDialog(
            vm = vm,
            keyHex = peerKey,
            nodeName = title,
            isRoom = true,
            onDismiss = { roomSignIn = false },
        )
    }

    confirmDeleteMany?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmDeleteMany = null },
            title = { Text(stringResource(R.string.conv_delete_many_title, ids.size)) },
            text = { Text(stringResource(R.string.conv_delete_many_body)) },
            confirmButton = {
                TextButton(onClick = {
                    ids.forEach { vm.deleteMessage(it) }
                    confirmDeleteMany = null
                    selection = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteMany = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text(stringResource(R.string.conv_clear_title)) },
            text = { Text(stringResource(R.string.conv_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearThread(kind, peerKey)
                    clearConfirm = false
                }) { Text(stringResource(R.string.clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showContact) {
        contacts.firstOrNull { it.keyHex == peerKey }?.let { contact ->
            ContactDetailSheet(
                vm = vm,
                contact = contact,
                onDismiss = { showContact = false },
                onOpenChat = { showContact = false },
                onOpenAdmin = {
                    showContact = false
                    openAdmin(peerKey)
                },
                onTrace = {
                    showContact = false
                    nav.navigate(traceRoute(peerKey))
                },
                onShowOnMap = {
                    showContact = false
                    showOnMap(vm, nav, peerKey)
                },
            )
        } ?: run { showContact = false }
    }

    if (showChannelEditor) {
        channels.firstOrNull { it.idx == peerKey.toIntOrNull() }?.let { ch ->
            ChannelEditSheet(vm, ch, onDismiss = { showChannelEditor = false })
        } ?: run { showChannelEditor = false }
    }
    if (showSendersSheet) {
        peerKey.toIntOrNull()?.let { idx ->
            ChannelSendersSheet(vm, idx, onDismiss = { showSendersSheet = false })
        } ?: run { showSendersSheet = false }
    }
}

/**
 * Confirmation before a peer-supplied http(s) link opens.
 *
 * SECURITY: this app makes no outbound connections except map tiles. A
 * link in a message is chosen by the sender, so following it hands a
 * server of their choosing the user's IP and the fact that they're
 * online. That has to be a decision, not a mis-tap.
 */
@Composable
private fun LeaveTheMeshDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conv_open_link)) },
        text = {
            Text(stringResource(R.string.conv_link_body, url))
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        ),
                    )
                }
                onDismiss()
            }) { Text(stringResource(R.string.conv_open_browser)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Replying to …" strip above the composer, with a cancel affordance. */
/**
 * Why a room conversation is not working yet, above the composer, with
 * the sign-in that fixes it. Shown for no session and for a read-only
 * one; the wording is [roomAccessNotice].
 */
@Composable
private fun RoomAccessBanner(text: String, action: String?, onAction: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(8.dp),
            )
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

@Composable
private fun ReplyBanner(target: MessageEntity, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(
                    R.string.conv_replying_to,
                    target.senderName?.takeIf { it.isNotBlank() }
                        ?: stringResource(if (target.outgoing) R.string.card_you else R.string.conv_them),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                target.text.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_close), contentDescription = stringResource(R.string.conv_cancel_reply))
        }
    }
}

/**
 * The quote text prefixed to a reply.
 *
 * Kept deliberately short: every byte here is taken from a ~150-byte
 * message budget, and MeshCore has no reply field to carry it out of
 * band — other clients will simply see the `>` line as text.
 */
internal fun quotePrefixFor(target: MessageEntity): String {
    val who = target.senderName?.takeIf { it.isNotBlank() }?.let { "$it: " }.orEmpty()
    val body = target.text.replace('\n', ' ').trim()
    val snippet = if (body.length > QUOTE_MAX_CHARS) {
        body.take(QUOTE_MAX_CHARS).trimEnd() + "…"
    } else {
        body
    }
    return "> $who$snippet\n"
}

private const val QUOTE_MAX_CHARS = Quoting.QUOTE_MAX_CHARS

/**
 * Split a message into its leading quote lines and the reply body.
 *
 * Moved to [Quoting] in `shared` — the conversation list and the
 * reaction notification need the same split, and a copy per caller is
 * how the list came to preview the quoted message instead of the reply.
 */
internal fun splitQuote(text: String): Pair<String?, String> = Quoting.split(text)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: MessageEntity,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onStartSelecting: () -> Unit = {},
    showSender: Boolean,
    selfName: String? = null,
    canMention: Boolean = false,
    showAvatar: Boolean = false,
    onSwipeReply: () -> Unit = {},
    onMention: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onMoreEmoji: () -> Unit = {},
    onReply: () -> Unit = {},
    onInfo: () -> Unit = {},
    /** "via A › B" when the arrival route is known (RouteChip), else null. */
    routeLabel: String? = null,
    onRoute: () -> Unit = {},
    onDelete: () -> Unit = {},
    onResend: () -> Unit = {},
    onHttpLink: (String) -> Unit = {},
    onMeshcoreLink: (String) -> Unit = {},
    rich: @Composable (RichMessage.Content) -> Unit = {},
) {
    // Long-press opens an inline action bar anchored on the bubble —
    // one tap to react, no dialog to read and dismiss first.
    var showActions by remember(m.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val outgoing = m.outgoing
    val (quoted, body) = remember(m.text) { splitQuote(m.text) }
    // A message that is wholly a shared contact, channel or place is drawn
    // as a card (RichMessage); quoted ones stay text.
    val richContent = remember(m.text) { if (quoted == null) RichMessage.parse(body) else null }
    val reactions = remember(m.reactionsJson) { ReactionCounts.decode(m.reactionsJson) }
    // A reaction whose target we couldn't find still has to render as
    // something a human understands, not as "r:1a2b:00" — nor as
    // MeshCore One's "😂@[Someone]\nb0c26wb5", which is what those
    // looked like in the thread until 2026-08-23.
    val orphanReaction = remember(m.text) {
        io.github.thatsfguy.meshcore.protocol.AnyReaction.emojiOf(m.text)
    }
    // Does this message tag the reader? A reading aid and nothing more:
    // the name it matched is unauthenticated display text that anyone on
    // the channel can advertise, so this may tint a bubble and must
    // never gate an action or stand in for identity (Mentions, §12).
    val tagsMe = remember(body, selfName) {
        !outgoing && Mentions.tags(body, selfName)
    }

    // Swipe-right-to-reply: accumulate the rightward drag as a visual
    // pull, and fire on release once past the threshold.
    val density = LocalDensity.current
    val thresholdPx = with(density) { 56.dp.toPx() }
    var dragOffsetX by remember(m.id) { mutableStateOf(0f) }
    val dragState = rememberDraggableState { delta ->
        if (delta > 0f || dragOffsetX > 0f) {
            dragOffsetX = (dragOffsetX + delta).coerceIn(0f, thresholdPx * 1.5f)
        }
    }
    val bubbleShape = RoundedCornerShape(
        topStart = 12.dp, topEnd = 12.dp,
        bottomStart = if (outgoing) 12.dp else 2.dp,
        bottomEnd = if (outgoing) 2.dp else 12.dp,
    )

    var bubbleBounds by remember(m.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val bubble: @Composable (Modifier) -> Unit = { extra ->
        Box(
            extra
                .widthIn(max = 300.dp)
                .background(
                    color = when {
                        outgoing -> MaterialTheme.colorScheme.primaryContainer
                        // Tagged: the same container the app uses for
                        // "this one is about you" elsewhere, rather than
                        // a colour invented for the occasion.
                        tagsMe -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = bubbleShape,
                )
                .then(
                    // Colour alone is not a signal on a colour-blind
                    // screen, and a tinted container against a tinted
                    // theme can be nearly the same value. The outline
                    // carries it a second time.
                    if (tagsMe) {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary,
                            bubbleShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column {
                if (showSender && !outgoing && !m.senderName.isNullOrBlank()) {
                    Text(
                        m.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                quoted?.let { QuoteBlock(it) }
                if (orphanReaction != null) {
                    Text(
                        stringResource(R.string.conv_orphan_reaction, orphanReaction),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (richContent != null) {
                    // A long-press on the card's face opens this message's
                    // actions (or toggles it while selecting), not nothing.
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalMessageLongPress provides { if (selecting) onToggleSelected() else showActions = true },
                    ) { rich(richContent) }
                } else {
                    MessageText(
                        body,
                        selfName = selfName,
                        onHttpLink = onHttpLink,
                        onMeshcoreLink = onMeshcoreLink,
                    )
                }
                if (reactions.isNotEmpty()) ReactionChips(reactions)
                routeLabel?.let { HeardViaChip(it, onRoute) }
                MessageFooter(m)
            }
        }
    }

    val selectTint = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .background(if (selected) selectTint.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent),
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffsetX.toInt(), 0) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = !selecting,
                onDragStopped = {
                    if (dragOffsetX >= thresholdPx) onSwipeReply()
                    dragOffsetX = 0f
                },
            )
            .combinedClickable(
                onClick = { if (selecting) onToggleSelected() },
                onLongClick = { if (selecting) onToggleSelected() else showActions = true },
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // The selection circle. It pushes received bubbles right; sent
        // ones stay against the right edge, so it sits at the far left.
        androidx.compose.animation.AnimatedVisibility(
            visible = selecting,
            enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            Icon(
                painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle),
                null,
                tint = if (selected) selectTint else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, end = 10.dp).size(24.dp),
            )
        }
        if (outgoing && selecting) Spacer(Modifier.weight(1f))
        // No name, no avatar: a "?" circle on every historical room post
        // (stored before authors were parsed) is noise, not information.
        if (showAvatar && !outgoing && !m.senderName.isNullOrBlank()) {
            // Seeded on the sender NAME, which is unauthenticated display
            // text — the colour is a readability aid, never identity.
            NodeAvatar(
                seed = "sender|" + m.senderName.orEmpty(),
                label = m.senderName.orEmpty(),
                size = 28.dp,
            )
            Spacer(Modifier.width(6.dp))
        }
        bubble(
            Modifier.onGloballyPositioned { bubbleBounds = it.boundsInWindow() },
        )
        if (showActions) {
            MessageFocusOverlay(
                bounds = bubbleBounds,
                outgoing = outgoing,
                // Reacting to yourself is a round-trip over LoRa that tells nobody anything.
                canReact = !m.outgoing,
                canResend = m.outgoing && m.status == MessageStatus.Failed.ordinal,
                canMention = canMention,
                bubble = { bubble(Modifier) },
                onReact = { emoji -> showActions = false; onReact(emoji) },
                onMoreEmoji = { showActions = false; onMoreEmoji() },
                onReply = { showActions = false; onReply() },
                onMention = { showActions = false; onMention() },
                onCopy = {
                    showActions = false
                    clipboard.setText(AnnotatedString(m.text))
                },
                onInfo = { showActions = false; onInfo() },
                onDelete = { showActions = false; onDelete() },
                onResend = { showActions = false; onResend() },
                onSelect = { showActions = false; onStartSelecting() },
                onDismiss = { showActions = false },
            )
        }
    }
    // While selecting, a tap anywhere on the row picks it — including on a
    // card or a link, which would otherwise open instead.
    if (selecting) {
        // It covers the row, so it carries the row's meaning for TalkBack.
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = m.text
                    this.selected = selected
                }
                .combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleSelected,
                    onLongClick = onToggleSelected,
                ),
        )
    }
    }
}

/** Replaces a screen's top bar while items are being selected (messages, chats). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    androidx.compose.material3.TopAppBar(
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = barColor,
            scrolledContainerColor = barColor,
        ),
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.conv_select_close))
            }
        },
        title = { Text(stringResource(R.string.conv_selected_count, count)) },
        actions = actions,
    )
}

/** The red Delete at the end of a selection bar. */
@Composable
internal fun SelectionDeleteAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
    }
}

/**
 * The row of names the `@` picker offers, above the composer.
 *
 * A horizontal strip rather than a dropdown: it must not cover the
 * message being replied to, and on a 384dp phone with the keyboard up
 * there is no room above the composer for a list anyway.
 *
 * The names are unauthenticated channel display text (§12) — inserting
 * one writes a string into a message and addresses nobody in
 * particular. That is the whole convention, and the UI must not dress
 * it up as picking a recipient.
 */
@Composable
private fun MentionPicker(names: List<String>, onPick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (name in names) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onPick(name) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Long-press on a message, the way Signal and Molly do it: everything behind blurs, the
 * message stays where it was, quick reactions sit above it and the actions are a list with
 * icons below it. It replaced a small popup of bare text labels.
 *
 * A full-screen dialog over the app, which blurs itself behind it ([BlurBackdropWhileShown])
 * so the top bar and composer blur too. Android 12+; below that the scrim alone dims it.
 */
@Composable
private fun MessageFocusOverlay(
    bounds: androidx.compose.ui.geometry.Rect,
    outgoing: Boolean,
    canReact: Boolean,
    canResend: Boolean,
    canMention: Boolean,
    bubble: @Composable () -> Unit,
    onReact: (String) -> Unit,
    onMoreEmoji: () -> Unit,
    onReply: () -> Unit,
    onMention: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onResend: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    BlurBackdropWhileShown()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            // The blur is the app's own (BlurBackdropWhileShown), not the
            // window's: window blur is switched off by the system whenever
            // it likes, which is why it was seen once and then not again.
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        val density = LocalDensity.current
        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 8.dp.roundToPx() }
        val insets = androidx.compose.foundation.layout.WindowInsets.safeDrawing
        val top = insets.getTop(density) + margin
        val bottomInset = insets.getBottom(density) + margin
        androidx.compose.ui.layout.Layout(
            content = {
                // Scrim: tapping anywhere off the menu closes it.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
                Box { if (canReact) ReactionPill(onReact, onMoreEmoji) }
                Box { bubble() }
                ActionMenu(canMention, canResend, onReply, onMention, onCopy, onSelect, onInfo, onResend, onDelete)
            },
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val scrim = measurables[0].measure(constraints)
            val pill = measurables[1].measure(loose)
            val menu = measurables[3].measure(loose)
            val room = (h - top - bottomInset - pill.height - menu.height - 2 * gap).coerceAtLeast(0)
            val msg = measurables[2].measure(loose.copy(maxHeight = room))
            // Where the bubble was, pushed just enough to fit the pill above and menu below.
            val minY = top + pill.height + gap
            val maxY = (h - bottomInset - menu.height - gap - msg.height).coerceAtLeast(minY)
            val y = bounds.top.toInt().coerceIn(minY, maxY)
            val bubbleX = bounds.left.toInt().coerceIn(0, (w - msg.width).coerceAtLeast(0))
            fun side(width: Int) = if (outgoing) {
                (bubbleX + msg.width - width).coerceIn(margin, (w - width - margin).coerceAtLeast(margin))
            } else {
                bubbleX.coerceIn(margin, (w - width - margin).coerceAtLeast(margin))
            }
            layout(w, h) {
                scrim.place(0, 0)
                pill.place(side(pill.width), y - gap - pill.height)
                msg.place(bubbleX, y)
                menu.place(side(menu.width), y + msg.height + gap)
            }
        }
    }
}

@Composable
private fun ReactionPill(onReact: (String) -> Unit, onMoreEmoji: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (emoji in Reactions.QUICK) {
                Text(
                    emoji,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onReact(emoji) }
                        .padding(6.dp),
                )
            }
            // The full list. Not the system emoji keyboard: the wire format is an index into
            // a fixed list, so anything outside it cannot be sent.
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMoreEmoji),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add_reaction), stringResource(R.string.conv_more_reactions), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionMenu(
    canMention: Boolean,
    canResend: Boolean,
    onReply: () -> Unit,
    onMention: () -> Unit,
    onCopy: () -> Unit,
    onSelect: () -> Unit,
    onInfo: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.width(232.dp),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            MenuRow(R.drawable.ic_reply, stringResource(R.string.conv_reply), onReply)
            // Separate from Reply on purpose: Reply quotes the text being answered, which
            // costs those bytes out of a ~150-byte frame; Mention costs only the name.
            if (canMention) MenuRow(R.drawable.ic_alternate_email, stringResource(R.string.conv_mention), onMention)
            MenuRow(R.drawable.ic_content_copy, stringResource(R.string.copy), onCopy)
            MenuRow(R.drawable.ic_select, stringResource(R.string.conv_select), onSelect)
            MenuRow(R.drawable.ic_info, stringResource(R.string.conv_info), onInfo)
            if (canResend) MenuRow(R.drawable.ic_refresh, stringResource(R.string.conv_resend), onResend)
            MenuRow(R.drawable.ic_delete, stringResource(R.string.delete), onDelete, destructive = true)
        }
    }
}

@Composable
private fun MenuRow(@androidx.annotation.DrawableRes icon: Int, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** The `>` lines of a reply, rendered as a quote rather than as text. */
@Composable
private fun QuoteBlock(quoted: String) {
    Row(Modifier.padding(bottom = 4.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (quoted.length > 40) 32.dp else 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            quoted,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Message body with tappable links and styled `@[Name]` mentions
 * (plain [Text] when it holds neither).
 */
@Composable
private fun MessageText(
    text: String,
    selfName: String?,
    onHttpLink: (String) -> Unit,
    onMeshcoreLink: (String) -> Unit,
) {
    if (!MessageLinks.hasMarkup(text)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val mentionColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor, mentionColor, selfName) {
        MessageLinks.annotate(
            text = text,
            linkColor = linkColor,
            mentionColor = mentionColor,
            selfName = selfName,
            onHttpLink = onHttpLink,
            onMeshcoreLink = onMeshcoreLink,
        )
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium)
}


/** Reaction counts under a bubble; tapping adds one of your own. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReactionChips(counts: Map<String, Int>) {
    Row(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((emoji, count) in counts.entries.sortedByDescending { it.value }) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    if (count > 1) "$emoji $count" else emoji,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Emoji palette for reacting.
 *
 * Only emoji from [Reactions.ALL] can be offered: the wire format is a
 * positional index into that exact list, so anything else is unsendable
 * rather than merely unusual.
 */
@Composable
private fun ReactionPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conv_react)) },
        text = {
            Column {
                // Six quick emoji don't fit a dialog's width as buttons
                // with default padding — lay them out on the same grid as
                // the rest so none fall off the edge.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(Reactions.QUICK) { emoji ->
                        TextButton(
                            onClick = { onPick(emoji) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.height(220.dp),
                ) {
                    items(Reactions.ALL.drop(Reactions.QUICK.size)) { emoji ->
                        TextButton(
                            onClick = { onPick(emoji) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) { Text(emoji) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}


/**
 * The words for a received message's route chip, or null when no route
 * is known — then the footer's hop count is all there is to say.
 */
internal fun routeChipLabel(m: MessageEntity, contactNames: Map<String, String>): String? {
    val width = m.arrivalHashWidth?.takeIf { it in 1..4 } ?: return null
    val bytes = m.arrivalPathHex?.takeIf { it.isNotEmpty() }
        ?.let { io.github.thatsfguy.meshcore.util.hexToBytesOrNull(it) }
        ?.takeIf { it.size % width == 0 }
        ?: return null
    return io.alvaroedo.meshconnect.ui.model.RouteChip.label(PathCodec.resolveHops(bytes, width, contactNames))
}

/** "via Hilltop › Port": a small tonal pill inside the bubble. Tap for the map. */
@Composable
private fun HeardViaChip(label: String, onClick: () -> Unit) {
    val desc = stringResource(R.string.conv_heard_via_desc)
    Row(
        Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_route), null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.conv_heard_via, label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The route a message arrived on, alone: the map, then the ordered hops. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheet(
    m: MessageEntity,
    vm: MeshCoreViewModel,
    senderLabel: String,
    contactNames: Map<String, String>,
    metresAway: (String) -> Double?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            MessagePathMap(vm, m, senderLabel)
            ArrivalRoute(m, contactNames, metresAway)
        }
    }
}

/** Everything known about one message, on its own sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageInfoSheet(
    m: MessageEntity,
    vm: MeshCoreViewModel,
    senderLabel: String,
    isChannel: Boolean,
    showSender: Boolean,
    contactNames: Map<String, String>,
    metresAway: (String) -> Double?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrollable since the route map arrived: the sheet gained
        // ~250dp of map and legend, and a Column that overflows a sheet
        // simply loses whatever is past the bottom — here the hop list,
        // which is the part the map cannot replace.
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // The node card's layout (2026-11-10): what the message said, then its
            // facts as tiles, then how it travelled, then the fine print.
            Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (m.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    m.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (m.outgoing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            TileRow {
                InfoTile(
                    stringResource(R.string.info_direction),
                    stringResource(if (m.outgoing) R.string.conv_sent else R.string.info_received),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                InfoTile(
                    stringResource(R.string.info_time),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                        .format(Date(m.timestamp * 1000)),
                    Modifier.weight(1.6f).fillMaxHeight(),
                )
            }
            if (m.outgoing) {
                Spacer(Modifier.height(8.dp))
                TileRow {
                    InfoTile(
                        stringResource(R.string.info_state),
                        stringResource(
                            when (m.status) {
                                MessageStatus.Delivered.ordinal -> R.string.info_state_delivered
                                MessageStatus.Sent.ordinal -> R.string.info_state_sent
                                MessageStatus.Failed.ordinal -> R.string.conv_failed
                                else -> R.string.conv_pending
                            },
                        ),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                    if (m.attempts > 0) {
                        InfoTile(stringResource(R.string.info_attempts), m.attempts.toString(), Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            // A COUNT, not a path. The inbound message frame carries
            // path_len only (MESHCORE_PROTOCOL §9) — the radio keeps the
            // route to itself — so calling it "Path" implied we knew
            // which repeaters carried it. We don't. And on a routed
            // arrival there is not even a count: see arrivalLabel.
            val arrival = io.alvaroedo.meshconnect.ui.model.SharedText.arrival(LocalContext.current, m.hops)
            if (arrival != null || m.snr != null) {
                Spacer(Modifier.height(8.dp))
                TileRow {
                    arrival?.let { InfoTile(stringResource(R.string.info_arrival), it, Modifier.weight(1f).fillMaxHeight()) }
                    m.snr?.let { InfoTile("SNR", "%.1f dB".format(it), Modifier.weight(1f).fillMaxHeight()) }
                }
            }
            copiesLabel(m.copies)?.let {
                Spacer(Modifier.height(8.dp))
                InfoTile(
                    stringResource(R.string.info_copies),
                    pluralStringResource(R.plurals.info_delivered_times, m.copies, m.copies),
                    Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.info_copies_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (!m.outgoing) {
                ArrivalRoute(m, contactNames, metresAway)
                Spacer(Modifier.height(8.dp))
                MessagePathMap(vm, m, senderLabel)
            } else {
                RepeatedBy(m, contactNames)
            }

            SheetHeading(stringResource(R.string.node_more))
            if (showSender) {
                InfoTile(
                    stringResource(R.string.info_sender_name),
                    stringResource(R.string.info_unauthenticated, m.senderName?.takeIf { it.isNotBlank() } ?: "–"),
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
            }
            val bytes = m.text.encodeToByteArray().size
            TileRow {
                InfoTile(
                    stringResource(R.string.info_reaction_id),
                    Reactions.targetHash(m.timestamp, if (isChannel) m.senderName.orEmpty() else null, m.text),
                    Modifier.weight(1f).fillMaxHeight(),
                    mono = true,
                )
                InfoTile(stringResource(R.string.info_length), pluralStringResource(R.plurals.bytes, bytes, bytes), Modifier.weight(1f).fillMaxHeight())
            }
            m.ackHash?.let {
                Spacer(Modifier.height(8.dp))
                InfoTile("Ack hash", "%08x".format(it), Modifier.fillMaxWidth(), mono = true)
            }
        }
    }
}

/**
 * "How did this get to me" — the route the message arrived on, in the
 * order it travelled: the sender at the top, this radio at the bottom.
 *
 * Only shown when the route is actually known. The message frame states
 * a hop COUNT and nothing more, so this comes from the RX-log packet —
 * exactly for a channel message, by correlation for a direct one — and
 * when no single packet could be credited the honest answer is a
 * sentence saying so, not an empty diagram (see HeardVia).
 */
@Composable
private fun ArrivalRoute(
    m: MessageEntity,
    contactNames: Map<String, String>,
    metresAway: (String) -> Double?,
) {
    val width = m.arrivalHashWidth?.takeIf { it in 1..4 }
    val path = m.arrivalPathHex?.takeIf { it.isNotEmpty() && width != null }
    SheetHeading(stringResource(R.string.info_arrived_via))
    if (path == null || width == null) {
        Text(
            io.alvaroedo.meshconnect.ui.model.SharedText.heardVia(LocalContext.current, m.hops, null, 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val bytes = io.github.thatsfguy.meshcore.util.hexToBytesOrNull(path) ?: return
    val hops = PathCodec.resolveHops(bytes, width, contactNames, metresAway)
    Text(
        io.alvaroedo.meshconnect.ui.model.SharedText.heardVia(LocalContext.current, m.hops, path, width),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    // The sender first, then each repeater in the order it carried the
    // message, then us. Reading it top-to-bottom IS the journey.
    StepsCard {
        RouteStep("↑", stringResource(R.string.info_sender), dim = true)
        hops.forEachIndexed { i, hop ->
            // An ambiguous hop names every node it could be, nearest
            // first, and never picks one. A 2-byte hop is 16 bits: nodes
            // share one honestly and a collision is cheap to manufacture, so
            // naming a winner would be a guess presented as a fact (PARITY
            // §12) — but naming the whole set is just the truth, and it is
            // what the reader came to the sheet for.
            RouteStep("${i + 1}.", hop.label)
        }
        RouteStep("↓", stringResource(R.string.info_this_radio), dim = true)
    }
}

/** Route steps on one tinted card, the tiles' surface. */
@Composable
private fun StepsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), content = content)
    }
}

/**
 * "Who picked this up" — nodes heard re-broadcasting a message we sent.
 *
 * The outgoing counterpart to [ArrivalRoute], and deliberately NOT
 * called a route: these are nodes observed carrying the message, in the
 * order we first heard them, not an ordered path it travelled.
 *
 * Silence is stated rather than left blank, because an empty section
 * here reads as "nobody repeated it" when what it means is "no repeat
 * reached this radio" — a node that relayed the message away from us
 * never sends a copy back.
 */
@Composable
private fun RepeatedBy(m: MessageEntity, contactNames: Map<String, String>) {
    val relays = MessageRepeats.relays(m.repeatHopsHex, m.repeatHashWidth)
    SheetHeading(stringResource(R.string.info_repeated_by))
    Text(
        MessageRepeats.summary(m.repeatHopsHex, m.repeatHashWidth),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (relays.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    val width = m.repeatHashWidth ?: return
    StepsCard { for ((i, hash) in relays.withIndex()) {
        // Named through the shared resolver, so an ambiguous hash stays
        // "(N matches)" here exactly as it does everywhere else. A
        // 2-byte hop is 16 bits; naming a winner would be a guess
        // wearing a fact's clothes (PARITY §12).
        val bytes = io.github.thatsfguy.meshcore.util.hexToBytesOrNull(hash)
        val label = bytes
            ?.let { PathCodec.resolveHops(it, width, contactNames).singleOrNull()?.label }
            ?: hash
        RouteStep("${i + 1}.", label)
    } }
}

@Composable
private fun RouteStep(marker: String, label: String, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(28.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = if (dim) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}



/**
 * "3 hops" / "direct" / "routed" — null when the message predates hop
 * recording or we sent it ourselves.
 *
 * "routed" is the negative-hops case and it used to read "flood",
 * which was exactly backwards: on an arrival the firmware writes 0xFF
 * when the packet was NOT flooded, and a real count when it was (see
 * PathCodec.decodeArrival). A count here therefore means the message
 * flooded — so the word "flood" belonged to every row that does not
 * show one.
 */
internal fun hopsLabel(hops: Int?): String? = when {
    hops == null -> null
    hops < 0 -> "routed"
    hops == 0 -> "direct"
    hops == 1 -> "1 hop"
    else -> "$hops hops"
}

/**
 * The same fact spelled out for the info sheet, where there is room to
 * say which of the two ways it came and what that implies.
 */
internal fun arrivalLabel(hops: Int?): String? = when {
    hops == null -> null
    hops < 0 -> "Routed — the sender had a path, so no hop count was recorded"
    hops == 0 -> "Flooded — heard directly, no repeater in between"
    hops == 1 -> "Flooded — 1 hop"
    else -> "Flooded — $hops hops"
}

/**
 * "Delivered 6 times" — null for the ordinary single delivery.
 *
 * Says DELIVERED, not "received" or "sent": each copy is one the radio
 * handed us, which is a fact about this end. How many the sender
 * transmitted is a different number that nobody here can see.
 */
internal fun copiesLabel(copies: Int): String? =
    if (copies > 1) "Delivered $copies times" else null

/** Newest-N window; "Load older" grows it. */
private const val PAGE_SIZE = 50


/** Send one message on this thread, channel or direct. */
private fun sendText(vm: io.alvaroedo.meshconnect.ui.MeshCoreViewModel, isChannel: Boolean, peerKey: String, text: String) {
    if (isChannel) {
        peerKey.toIntOrNull()?.let { vm.sendChannelMessage(it, text) }
    } else {
        vm.sendDirectMessage(peerKey, text)
    }
}

/**
 * The phone's GPS fix, else the radio's own advertised position, saying which; null when
 * there is neither, rather than a made-up place.
 */
private suspend fun locateMe(
    vm: io.alvaroedo.meshconnect.ui.MeshCoreViewModel,
    context: android.content.Context,
): Pair<Pair<Double, Double>, FixSource>? =
    PhoneLocator.fix(context)?.let { it to FixSource.Phone }
        ?: vm.selfInfo.value?.let { me ->
            (me.latitude to me.longitude)
                .takeIf { io.github.thatsfguy.meshcore.util.isPlausiblePosition(me.latitude, me.longitude) }
                ?.let { it to FixSource.Radio }
        }

/** Picks the card for a [RichMessage.Content] and what tapping it does. */
@Composable
private fun RichContentCard(
    content: RichMessage.Content,
    selfKey: String?,
    tilesEnabled: Boolean,
    contacts: List<io.alvaroedo.meshconnect.storage.ContactEntity>,
    channels: List<io.alvaroedo.meshconnect.storage.ChannelEntity>,
    onOpenContact: (io.alvaroedo.meshconnect.storage.ContactEntity) -> Unit,
    onAddContact: (String) -> Unit,
    onJoinChannel: (String) -> Unit,
    onOpenChannel: (Int) -> Unit,
) {
    when (content) {
        is RichMessage.Contact -> {
            val known = contacts.firstOrNull { it.keyHex.equals(content.publicKeyHex, ignoreCase = true) }
            ContactCard(
                c = content,
                known = known,
                onOpen = { known?.let(onOpenContact) },
                onAdd = { onAddContact(ShareUri.encodeContact(content.name, content.publicKeyHex, content.type)) },
                isSelf = selfKey.equals(content.publicKeyHex, ignoreCase = true),
            )
        }
        is RichMessage.Channel -> {
            // Joined means a channel of the same name; the join dialog
            // itself compares keys before changing anything.
            val joined = channels.firstOrNull { it.name.equals(content.name, ignoreCase = true) }
            ChannelCard(
                c = content,
                joined = joined != null,
                onJoin = { onJoinChannel(content.uri) },
                onOpen = { joined?.let { onOpenChannel(it.idx) } },
            )
        }
        is RichMessage.Location -> LocationCard(content) { LocationPreview(content, tilesEnabled) }
    }
}

/**
 * Time, delivery state as an icon (pending, sent, delivered, failed),
 * retries, who repeated it, and for received traffic how far it came and
 * how strong it landed.
 */
@Composable
private fun MessageFooter(m: MessageEntity) {
    val context = LocalContext.current
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.timestamp * 1000)), style = style, color = ink)
        if (m.outgoing) {
            val (icon, label, tint) = when (m.status) {
                MessageStatus.Delivered.ordinal -> Triple(R.drawable.ic_done_all, R.string.conv_delivered, MaterialTheme.colorScheme.primary)
                MessageStatus.Sent.ordinal -> Triple(R.drawable.ic_done, R.string.conv_sent, ink)
                MessageStatus.Failed.ordinal -> Triple(R.drawable.ic_error, R.string.conv_failed, MaterialTheme.colorScheme.error)
                else -> Triple(R.drawable.ic_schedule, R.string.conv_pending, ink)
            }
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(icon), stringResource(label), tint = tint, modifier = Modifier.size(14.dp))
        }
        val extras = buildList {
            // Retries make a struggling route visible.
            if (m.outgoing && m.attempts > 1) add(context.getString(R.string.conv_try, m.attempts))
            // Who picked this up off the air; absent is not "nobody".
            if (m.outgoing) MessageRepeats.badge(m.repeatHopsHex, m.repeatHashWidth)?.let { add(it) }
            io.alvaroedo.meshconnect.ui.model.SharedText.footerHops(context, m.hops)?.let { add(it) }
            m.snr?.let { add(String.format(java.util.Locale.ROOT, "%.1f dB", it)) }
        }
        if (extras.isNotEmpty()) {
            Text("  ·  " + extras.joinToString("  ·  "), style = style, color = ink, maxLines = 1)
        }
    }
}
