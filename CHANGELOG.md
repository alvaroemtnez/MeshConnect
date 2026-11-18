# Changelog

Release notes for MeshConnect, newest first.

The Forgejo release workflow turns the section matching a `vX.Y.Z` tag into the release notes
(on Forgejo and on the GitHub mirror) and fails if there is none, so write the section before
tagging. The app carries the same version list in `AboutSection.kt`, and `ChangelogTest` holds
the two together.

MeshConnect is a fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app).
Its history up to the fork point (0.10.15) is in
[docs/upstream/CHANGELOG-MeshCoreHardened.md](docs/upstream/CHANGELOG-MeshCoreHardened.md).

## 1.3.1

Fixes for 1.3.0.

- **DM requests come up again.** A request you had once accepted or declined was remembered
  by the asker alone, for every radio and for good, so a later request from them (say, after
  removing them) only buzzed the phone: no prompt, and tapping the notification did nothing.
  An answer now covers the requests that had arrived when you gave it, on that radio; anything
  they send later asks again. Not now puts off that request, not the person.
- **Requests on any channel get the prompt.** A request in a group (or any channel) comes up
  like one on #mcn-internal, says where it was asked, and Accept answers there. Its
  notification opens the prompt; if you have answered since, it opens their chat or says so,
  and if it was for another of your radios it says which. Answering clears the notification.
  A request is addressed to you, so a muted channel does not silence it; blocking the asker
  does.
- **Chat requests in Chats.** While requests are waiting, a row at the top of Chats opens
  them as a list (newest first, with where and when, and a warning when the name is not
  confirmed); tap one for its prompt. It is where a request put off with Not now comes back.
- Accepting the same person a second time writes its line and notifies again.
- In Chats, a direct chat's "You accepted…" / "… accepted your chat request" line reads as
  words, not as its wire text.
- Saved radios list the connected one first, then the most recently used; the no-radio
  screen's three are the latest, not the oldest.
- Settings → Advanced (and Appearance and alerts) no longer stretch their card to the bottom
  of the screen.
- Signal bands on the ping and trace screens, the map's neighbour links and their list, and
  neighbours' heard-ago are in your language.

## 1.3.0

Brings in MeshCore Hardened 0.10.16, 0.10.17 and 0.11.0.

- **Simple mode.** For handing the app to someone who only needs to talk to their people:
  contacts, groups and rooms, added by link or QR code. Choose it on the last first-run step
  or with the switch in Settings → Appearance and alerts. It shows Chats and Settings only;
  leaves out Public and hashtag channels (in Chats, in Settings → Channels and in their
  notifications) and repeater threads; shows groups and rooms with one group icon; and hides
  hop counts, signal, routing, node detail and the announce buttons. New chat is one list of
  everyone and every group, then Create a group, Scan a QR code and Get from clipboard. A
  group's ⋮ → Group settings renames it, shows its QR code or removes it. The radio card
  shows the radio's name, its link and battery, and the repeaters it hears. It only hides:
  nothing changes on the radio, and switching back shows everything as it was.
- **Ask someone for a DM from a channel.** Long-press their channel message and choose
  "Message privately". They get Accept or Decline; on Accept, both radios add each other, so
  direct messages work even with auto-add off. Everyone on the channel can see that you asked.
  An accept only counts if you asked that person in the last 7 days, or shared your own code
  in that time, so nobody can add themselves with a faked reply. In the thread, requests and
  replies read as a small grey line in your language, not as a key. After adding someone from
  a code or link, the app offers to post the same note on a channel you share, so they add you
  back. It works with MeshCore Hardened.
- **Can I talk to them?** Each contact on the Nodes list reads Two-way, Two-way before,
  They've heard you, Heard only or No answer yet, worked out from what the app already knows
  and without airtime: delivered messages, answered pings, sign-ins and telemetry, repeaters
  heard relaying you, and routes a node sent back. The node card says what it rests on.
- **Check if they can hear you.** People and sensors have a button on their card that asks
  them for the route both ways. Most radios only answer if their owner allows it, so no answer
  is explained rather than taken as proof they can't hear you.
- **Far away, heard only.** Heard nodes that show no sign of hearing you and are 4 or more
  hops or over 100 km away fold into their own group. Adding one asks first.
- **Codes and links go where you meant.** From Chats (its scanner, New chat, a link you tap, a
  card in a message), a person, a room or a group opens its conversation; from Nodes, a node
  opens its card. A repeater or sensor opens its card from either. Something you already have
  skips the confirmation and goes straight there. After adding a person, the app says they
  won't get your messages until they add you too, with your QR code one tap away; this
  replaces the offer to post on a channel. Simple mode turns away repeater, sensor, Public and
  hashtag codes with a note, and adds nothing.
- **Simple cards.** Holding someone, a room or a group in New chat opens its card, in both
  modes. Simple mode's card for a person or a room has the favourite star, "Check if they can
  hear you", and Show QR code, Rename, Block and Remove; a conversation's ⋮ opens it. New chat
  lists Favourites first, then a section per letter, with search on top. Nodes ⋮ has Scan QR
  code. Simple mode's long-press in Chats no longer offers a private nickname.
- **Rename is yours.** Renaming someone on their card gives them a name on this phone only,
  shown in Chats, the conversation, New chat and Nodes; the card shows the name they chose
  underneath. Renaming them on the radio did not stick: their next advert put their own name
  back. Holding a chat now offers their card, which replaces "Private nickname".
- **Rooms sign in on their own.** With a saved password, once when the radio connects and
  when you open the room, one attempt each and nothing on a timer. A switch in Settings →
  Connection turns it off.
- **Favourite groups**, per radio, with the star on the group's card: they head New chat.
- **Everything destructive asks first,** with a red button: removing a group or a contact,
  blocking, deleting one message as well as several, clearing a chat.
- The ongoing notification says the radio's battery. The battery icon turns red at 15% and
  blinks at 10%. Simple mode refuses radio-settings codes, and no longer shows the Public
  channel for a moment when the app starts.
- **Ask someone to add you, over the mesh.** After adding a person, from their card, or from
  their chat's ⋮: MeshConnect posts a short request that their MeshConnect shows as a prompt
  and a notification, and accepting adds you both. It goes on `#mcn-internal`, a channel
  MeshConnect keeps on the radio for itself (in the last free slot, hidden everywhere, said so
  during setup), or in a group you share. The prompt says whether their radio has confirmed
  the name, and shows the start of their key.
- **The tab bar moves with its screen**, instead of vanishing the moment another screen
  starts sliding in. Nodes search finds nicknames too.
- **GPS in Identity.** On a radio with GPS: "From the radio's GPS" (the position follows each
  fix, read again while the screen is open) or "Set by hand" (GPS off, so a typed position
  stays). Also in Simple mode. A radio without GPS works as before.
- **Adding opens faster.** After adding someone or joining a channel, the app reads back only
  what changed instead of the radio's whole contact list or every channel slot, so the chat
  opens sooner (most noticeable on a radio with many contacts).
- **Settings → Advanced.** Map tiles, the diagnostics log and the channel datagram tool moved
  here from Appearance and alerts. New chat's "Message a contact" is now "Existing contact,
  group or room", one alphabetical list of all three. Disconnect is red, and About shows the
  logo in the launcher's teal at the launcher's size.
- **Fixed:** on a fresh install, tapping a tab at the bottom blanked the screen and slid it in
  again until the app was restarted. The radio card said a repeater was heard "in 0 minutes",
  and a node's card "In 0 seconds" when the radio's clock ran ahead.
- **Distance on node rows, and sort by nearest.** The heard section now follows the chosen
  sort too, so "Fewest hops" no longer looks like it does nothing.
- **Codes and links from other apps confirm before adding.** A signed contact code was added
  straight away, and with auto-add off was reported as "Contact imported" when the radio had
  declined it. It now gets the usual confirm card and really is added. A link tapped before the
  radio connects waits until it does.
- **Radio readings.** The top-bar sheet adds packets received and sent and receive errors,
  shows "Measuring…" and "-120 dBm or lower" instead of misleading numbers, and stops asking
  radios whose firmware has no readings.
- **Message signal says whose it is.** On a relayed message the SNR is the last repeater's,
  so it reads "last hop X dB". Channel messages that queued on the radio while the app was
  closed keep their SNR.
- **Ping** warns when other known nodes share the hop hash, since any of them could have
  answered. On a mesh with 3-byte hop hashes, ping and trace now say they can't run instead of
  sending a trace no node would answer.
- **Reconnects when Bluetooth dies under it.** If the phone's Bluetooth restarted while
  connected, the app kept saying "connected" with nothing getting through. A failed send now
  counts as a lost link, and turning Bluetooth off drops the link at once.
- **No crash when Android refuses the background service.** On Android 17 the app could crash
  in a loop when the system restarted it in the background. It now stops quietly and starts
  again the next time you open it.

- **Firmware updates, simpler.** Settings → Firmware is one screen that asks which node: this
  radio, or another node in update mode (and what it runs). Back now steps back through the
  version and board lists instead of leaving the screen, and the last Flash button is red. On
  a repeater's card, "Recover or update firmware" is "Firmware update", offering Flash
  firmware or Leave update mode.
- **Fixed: updating the connected radio hung** at "Asking the radio to restart in update mode"
  and the radio never restarted: the app closed the Bluetooth link at the same moment it sent
  the request. It now keeps the link for the update, and each step gives up after 10 seconds
  instead of waiting forever.
- **Diagnostics log you can send.** It opens full screen, oldest first and following new lines,
  from Settings → Advanced and from the firmware screen, with Share, Save as a file, Copy and
  Clear. Times read `2026-11-17T20:13:05.123+01:00` instead of Unix seconds, and it keeps 2000
  lines.
- **Telemetry.** "Battery telemetry" is "Device telemetry": battery and chip temperature, and
  without it the radio answers no telemetry request or connection check at all. Location is
  only ever sent from a live GPS fix. On a contact, the switches are greyed out when the
  global setting is Nobody or Everyone, with one short line saying so.
- **Heard repeaters.** One repeater heard at different hash widths (AB, ABCD, ABCDEF) is one
  row, unless another repeater shares the short hash. Repeaters you have not added are named
  from their adverts.
- **Widget.** Up to three repeaters, at the bottom; the latest message of several chats above
  them; signal before battery, as in the top bar; no repeater hash in Simple mode.
- **DM requests say when they are accepted.** Accepting opens the chat with "You accepted…";
  the person who asked gets "… accepted your chat request" in that chat and a notification. An
  unconfirmed name in a request is a red warning.
- **Messages from a clock in the future.** A received message dated more than ten minutes
  ahead is placed when it arrived, with "their clock: …" in red beside the time. Messages
  already stored with such a date move too.
- **Saved radios** in Settings look like the top bar's radio switcher, with a red bin to delete
  one, and the connected radio no longer offers Connect. A channel's favourite star is a round
  button, as on a node's card. Settings fields are no longer hidden behind the keyboard.

## 1.2.1

- **New chat opens the chat.** Adding someone from the New chat button (by scanning their code
  or pasting their link) now opens the conversation with them as soon as they are added, and so
  does joining or creating a channel there, including one you already had. Adding a channel
  from Settings → Channels, or a node from the Nodes tab, still leaves you where you were.
- **The map's menus are split.** What the map shows (node kinds, labels, neighbour links) is
  behind a new filter icon, laid out like the Nodes sort menu; the icon is tinted while a kind
  is hidden. The ⋮ menu keeps the actions, each with an icon: show the whole network, export
  as GPX, load map tiles, clear the cache. "Show the whole network" had sat among the filters
  and read as the way to undo them; it only zooms out. Sensors can now be filtered too, and a
  filter that hides every pin says so instead of claiming no node has a position.
- **Hidden newcomers, said quietly.** With "Hide nodes not added" on, the Nodes list has a
  faint "Hiding nodes not added" line where the heard nodes' row would be, just above the top,
  instead of a line with a Show button under the tabs.
- **The widget's unread count matches the Chats tab.** It added up every contact, repeaters
  included, and a repeater's thread is its admin console: each reply to a console command was
  counted as unread, nothing could clear it, and the widget's number only went up. The widget
  now counts the chats the list shows, and console replies are no longer counted as unread. Its
  "last message" line skips console replies too.

## 1.2.0

- **The widget tells you whether the radio is connected.** The status dot is now an icon with
  a different shape for each state: a link (connected; red over unencrypted TCP), a sync
  arrow (connecting), a broken link (not connected, or the app not running). While there is
  no link, the battery and signal line says so in words instead of showing the last readings.
  And the widget now follows the link as it changes: until now a widget could keep showing
  "connected" after the radio dropped.
- **Ready for Android 15, 16 and 17.** MeshConnect targets Android 17. The service that keeps
  the radio connected is now a "connected device" service for every link, so it keeps running
  past six hours and still starts with the phone; before, a TCP-only setup that had refused
  Bluetooth would have hit both limits. On Android 17, connecting to a network (TCP) radio asks
  for access to devices on your local network, in the same "Nearby devices" permission as
  Bluetooth: if you already allowed Bluetooth, nothing new is asked.

## 1.1.0

- **Widgets redone.** Every size uses its space. The one-row widget is two lines: status dot,
  name and unread count; then battery and the repeater heard best. The card has the same
  first line; from three columns it puts a narrow status column beside the latest message,
  and whatever height is left goes to nearby repeaters (up to six) and more of the message.
  SNR is gone from the widget: the signal bars already show it. Space goes to what there is
  to show, so a radio with no messages or few repeaters leaves no blank band.
- **Bigger chat and node rows,** after Signal: larger avatars and names, more room between
  rows. Contacts show two initials; channels keep their globe, # or lock.
- **New QR scanner,** built into the app: a rounded frame over the camera and the prompt in a
  bubble. It still reads light-on-dark codes, which about half the MeshCore codes around are.
  It replaces the ZXing Android Embedded scanner with Android's own camera library (CameraX).
- **Repeater settings** match the phone's own: radio values as pickers with the preset and
  QR buttons, a map picker for the position, Nearby and Whole mesh announce buttons that
  report what the node actually sent, and Maintenance grouped into reads, clock and log, and
  restart and reset.
- **Repeater sign-in follows your radio.** Switching companion radios no longer carries the
  other radio's admin or guest session over to it.
- **Mark read and unread by swiping** works on every swipe. One swipe could toggle twice and
  end where it started.
- **Mark unread from a menu** no longer does nothing while the radio is reconnecting.
- **Popup menus** have the same rounded look as the long-press menu, and New chat has a
  new icon.
- **About** shows the logo at its proper size.

## 1.0.0

First MeshConnect release, forked from MeshCore Hardened 0.10.15.

- **New name and app id.** MeshConnect, `io.alvaroedo.meshconnect`. It installs alongside
  MeshCore Hardened rather than over it, and starts with its own empty database.
- **New look.** Material You colours (dynamic on Android 12+), Material Symbols icons, top and
  bottom bars that run under the status and navigation bars, quick slide transitions, and a
  new launcher icon with a monochrome themed icon and notification glyph.
- **Spanish and Valencian.** The whole app, notifications and widget included, follows the
  phone's language, or a per-app language from Android settings.
- **Link meter in the top bar.** Signal bars from the last direct hop and the radio's battery;
  a pulsing dot while connecting, and a warning icon on plaintext TCP. Tap it for live radio
  statistics (noise floor, RSSI, SNR, packet counts).
- **Search** in Chats and Nodes opens from a magnifier into a rounded field.
- **New composer** with a + menu: send your contact, share a contact or a channel, your
  location, or a point picked on the map. These arrive as cards: a map preview that opens your
  maps app, a contact card you can add, a channel card you can join.
- **Nodes:** filter by kind (all, contacts, repeaters, rooms, sensors) with icons instead of
  tabs, and the same icons on map pins and list rows. Nodes heard but not added fold out from
  a "Heard, not added" row at the top of the list (the pull-to-refresh that did nothing is
  gone); "Dismiss all" asks first; scanning a QR code is the floating button.
- **Map on MapLibre** (OpenFreeMap tiles) instead of osmdroid, light or dark with the phone,
  with my-node, whole-network and zoom buttons.
- **Ping and trace on a map:** trace a route to a node and back, with each leg coloured by
  its SNR (upstream issue #5).
- **Home-screen widget** from 2×1 to 4×3: a one-row compact entry (status, battery, signal,
  unread), the card with the latest message, and at the largest size the repeaters your radio
  hears directly.
- **Connecting without Settings.** A three-step first run (welcome, find your radio, name and
  preset), a "Connect a radio" button where there is no radio, and the top-bar card connects,
  switches between saved radios or disconnects. Bluetooth permission is asked when scanning.
- **Settings reworked** into grouped cards with an icon per item, one field style, tonal
  buttons and segmented choices. Reboot radio moved into This node, with a red confirm.
- **Messages:** long-press the Signal way (blurred background, reactions above, actions
  below); swipe a chat right for read/unread and left to archive, with archived chats behind
  one row and kept in backups; a "via …" chip on received messages whose route is known,
  opening the route on a map.
- **Selecting messages:** Select in the long-press menu puts a circle beside each message;
  reply to one, copy or delete several. The long-press blur now shows reliably (it relied on
  window blur, which Android switches off in battery saver and elsewhere), and a long-press on
  a card in a message opens its menu.
- **Sending your location** asks first: a spinner while the phone looks, then the place on a
  map with Cancel and Send.
- **Chats:** a lock, globe or tag on each channel; a long-press card with mark read or unread,
  pin, archive, edit (the channel editor, or a contact's nickname), select and delete, and
  selecting several chats to mark read, archive or delete. Deleting a channel removes it from
  the radio. Mark unread works on every chat and shows a dot rather than a number.
- **One node card** for the Nodes list and the map: quick actions, facts as tiles, and the
  map's neighbour links for a repeater. Tapping any node opens it (a repeater no longer goes
  straight to sign-in); favouriting shows at once, with a filled star; Position opens the node
  on the map. Heard-but-not-added nodes and the Nearby repeaters list use the same card, the
  latter with the signal both ways.
- **Message info** in the node card's layout: the message, its facts as tiles, then the route.
- **Telemetry grouped by channel:** the board's own readings (battery, processor temperature)
  apart from each sensor's, so a RAK4631 with a temperature module no longer shows two
  identical "Temperature" rows.
- **New chat** replaces Add channel in Chats: message a contact, join or create a channel, or
  scan any code. The chat ⋮ menu has icons.
- **Channel editor:** fields no longer crowd each other, and the Public and hashtag channels'
  keys are locked (a hashtag channel's name too, since its key is made from it).
- **QR codes** can be shared as a link through Android's share sheet.
- **meshcore:// links open MeshConnect**, from a camera app's QR scan, a browser or a message,
  with the same confirmations as the in-app scanner; New chat can also take a code from the
  clipboard.
- **Settings polish:** no field touches its button; Identity regrouped (the key with a QR button,
  name, position, announcing); Radio offers bandwidth, spreading factor and coding rate as
  pickers of the values the chip takes.
- **Switching radios from a chat** goes back to Chats, and the first message sent after setup no
  longer throws you out of the chat.
- **Repeater admin screens** (Settings, Status, Regions, Identity) as cards with icons; Settings
  sections still ask the node only when opened.
- **The message database opens while the phone is locked** (after the first unlock since
  boot), as Signal's does. A service Android restarted behind the lock screen used to run on an
  empty stand-in database, or on a first run create an unencrypted one.
- **Long-pressing a message with the keyboard up** no longer makes the bubble jump.
- **The widget lays out by its real size**, so a 3×2 on a six-row grid shows nearby repeaters
  instead of empty space, and a tall 2×1 draws larger.
- **Battery percentage** follows a Li-ion discharge curve instead of a straight line from 3.0
  to 4.2 V, which read a half-empty cell as nearly 60% and warned too late.
- **Start with the phone:** reconnects after a restart or an app update, without opening the
  app. A setting, on by default.
- **Switch to a saved radio in range:** when the current Bluetooth radio has been out of reach
  for a while, the app connects to another saved one nearby (one at home, one at work). Each radio keeps its
  own chats, and now its own settings too: muted channels, pinned, archived and unread-marked
  chats, per-channel regions and retention, pinned routes and the flood scope. Before, "channel
  1" on two radios shared them although they are different channels.
- **Status card** shows the radio's frequency and settings, with readings laid out as tiles.
- **QR codes** load inside a single dialog; the 2×1 widget shows the heard repeater and its SNR.
- **Radio battery alerts** at 20% and 10%, from the reading the app already takes, so they
  cost the phone nothing. Their own notification channel and a switch in Notifications.
- **Node sign-in** opens over the current screen and leads to the node only once it accepts.
- **Regional presets** re-synced with MeshCore Open's list.
- **Hop labels** in the top bar are cut to your mesh's hop width, so a repeater no longer
  shows 4 digits one moment and 6 the next.
- **Map attribution** drawn by the app instead of a misplaced ⓘ.
- **Valencian:** "escoltat" instead of "oït".
- **About screen** with credits, licences you can read offline, and the privacy promises.
- **Long node names in the top bar** are ellipsised instead of wrapping (upstream issue #8).
- **Keystore hardening from upstream PR #10:** each key-generation fallback tier gets its own
  `KeyGenerator`.
- **Build and release** through `build.sh` and Forgejo Actions, with signed releases published
  on Forgejo and the GitHub mirror.
- **Android only.** The unbuilt iOS skeleton and its CI are gone. Kotlin 2.3, AGP 8.13,
  MapLibre 13.7, compile SDK 36.
