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
  tabs; the heard-adverts list appears on pull-down; "Dismiss all" asks first; scanning a QR
  code is the floating button.
- **Map on MapLibre** (OpenFreeMap tiles) instead of osmdroid, light or dark with the phone,
  with my-node, whole-network and zoom buttons.
- **Ping and trace on a map:** trace a route to a node and back, with each leg coloured by
  its SNR (upstream issue #5).
- **Home-screen widget** in four sizes: connection, battery, nearby repeaters, unread count.
- **About screen** with credits, licences you can read offline, and the privacy promises.
- **Long node names in the top bar** are ellipsised instead of wrapping (upstream issue #8).
- **Keystore hardening from upstream PR #10:** each key-generation fallback tier gets its own
  `KeyGenerator`.
- **Build and release** through `build.sh` and Forgejo Actions, with signed releases published
  on Forgejo and the GitHub mirror.
