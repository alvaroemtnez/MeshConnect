# Changelog

Release notes for MeshConnect, newest first.

The Forgejo release workflow turns the section matching a `vX.Y.Z` tag into the release notes
(on Forgejo and on the GitHub mirror) and fails if there is none, so write the section before
tagging. The app carries the same version list in `AboutSection.kt`, and `ChangelogTest` holds
the two together.

MeshConnect is a fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app).
Its history up to the fork point (0.10.15) is in
[docs/upstream/CHANGELOG-MeshCoreHardened.md](docs/upstream/CHANGELOG-MeshCoreHardened.md).

## 0.1.0

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
  for a while, the app connects to another saved one nearby (one at home, one at work).
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
