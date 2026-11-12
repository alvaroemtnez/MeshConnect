<div align="center">
  <img src="docs/logo.png" width="112" alt="">
  <h1>MeshConnect</h1>
  <p><strong>Text people across a LoRa radio mesh, with no internet, no phone signal and no accounts.</strong><br>
  For Android, with a MeshCore radio. No servers, no tracking, no ads; your messages never touch the internet.</p>
  <!-- Stores, one row: F-Droid, Accrescent and Flathub join Obtainium here. -->
  <p>
    <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/alvaroemtnez/MeshConnect"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="72" alt="Get it on Obtainium"></a>
  </p>
  <p>
    <a href="https://code.alvaroedo.io/alvaroemtnez/MeshConnect/releases"><img src="https://img.shields.io/badge/Releases-APK-0F7B6C?style=for-the-badge" height="40" alt="Releases"></a>
  </p>
</div>

## What it does

[MeshCore](https://github.com/meshcore-dev/MeshCore) turns small, cheap LoRa radios into a mesh:
each radio reaches a few kilometres on its own, and repeaters on hills and rooftops pass messages
along much further. Nothing in between belongs to anyone, so it keeps working on a mountain, at a
festival, in the countryside, or when the phone network is down.

You carry a small **companion radio**, and MeshConnect is the app on your
phone that talks to it over Bluetooth, USB-C or your network. With it you get:

- **Messages**: direct messages and group channels (public, `#hashtag` and private), with
  automatic retries. A message gets its delivery tick only when the other radio has confirmed
  it, never because it simply left yours. Replies, reactions and @mentions.
- **The route a message took**: the repeaters a message passed through on its way to you, drawn
  on the map, and which repeaters picked up the ones you sent.
- **Everyone on the mesh**: the people, repeaters, rooms and sensors your radio hears, with
  search, sorting and filters, and a clean-up for nodes not heard in weeks. Add a contact or
  join a channel by scanning a QR code or opening a link.
- **A map** of every node with a known position, a repeater's links to its neighbours coloured
  by signal, and traces and pings along a route with the signal at each hop.
- **Your radio**: its name, position and radio settings, regional presets, and settings codes to
  share as a QR. Live signal and battery in the top bar, low-battery alerts, and a home-screen
  widget.
- **Your repeaters and rooms**: sign in to see status and telemetry, change settings and regions,
  list neighbours, or use the console.
- **Firmware updates over Bluetooth**, checking that the firmware matches the board before
  anything is written.
- **Backups** of your settings, message retention, blocking, per-chat mute, and notifications.

Material You colours, light and dark themes. English, Spanish and Valencian, following your
phone's language. The full inventory, with what each feature can and cannot tell you, is in
[docs/FEATURES.md](docs/FEATURES.md).

## What you need

- **A MeshCore companion radio**: Heltec, RAK WisBlock, LilyGo T-Echo, Seeed Xiao and Wio, and
  the other boards [MeshCore supports](https://github.com/meshcore-dev/MeshCore). The radio needs
  MeshCore *companion* firmware (Bluetooth or USB). A radio running Meshtastic needs reflashing:
  the two networks do not talk to each other.
- **A TCP base station** works too, for a radio wired into your network. TCP is off by default
  (see Privacy).

## Install

- **Android 8.0 or later**: with [Obtainium](https://obtainium.imranr.dev/) (button above), or
  download the APK from the [releases](https://code.alvaroedo.io/alvaroemtnez/MeshConnect/releases)
  (also on the [GitHub mirror](https://github.com/alvaroemtnez/MeshConnect/releases)). No
  Google services needed: GrapheneOS, LineageOS and other de-Googled systems are first-class.

Open the app, switch the radio on and pick it from the list. The connection stays up in the
background and comes back on its own; it can also start when the phone does.

Every release carries a `SHA256SUMS` file, and the APKs are signed with the same key on every
release. See [docs/verifying.md](docs/verifying.md) for the certificate fingerprint.

## Privacy

Messages go from your phone to your radio and out over the air; they never touch the internet.
The message history on the phone is encrypted (SQLCipher), and passwords, channel keys and other
secrets are kept in the Android Keystore; if there is no Keystore, the app refuses to store them
rather than keep them in plain text. Android cloud backup is off. There is no analytics and no
crash reporting.

The app asks for these Android permissions: **Bluetooth** to reach the radio; **location** only
when you share your position in a chat (and, on Android 11 and older, because the system
requires it to scan for Bluetooth devices); **camera** only to scan a QR code; **notifications**
for new messages; and **network**, for the two connections below and for TCP.

With the network on, two services see your IP address: **OpenFreeMap**, for map tiles while the
map is open (and it learns which area you are looking at), and **GitHub**, only when you check for
or download a firmware update. Turn map tiles off in Settings and the map draws nodes on a plain
background; nothing else connects on its own, and everything else works in airplane mode.

What the mesh itself does and does not protect:

- **Direct messages** are encrypted between your radio and theirs.
- **Channels are obfuscated, not secure.** Anyone with a channel's key can read it, and MeshCore's
  channel encryption is weak by design (AES-ECB with a 2-byte check). The app says so where it
  matters.
- **Who a node is** is checked: a node's announcement must carry a valid signature before it is
  added or shown on the map. A name typed in a channel is never taken as proof of who sent it.
- **Your position**, if you set one on the radio, goes out to everyone in range in its
  announcements. The app warns you when you set it.
- **TCP to a radio is unencrypted and unauthenticated.** It stays off until you turn it on past a
  warning, and the app flags it for as long as it is in use.

The diagnostics log is off by default and removes secrets from what it records. To report a
vulnerability, see [SECURITY.md](SECURITY.md).

## Build

Everything goes through [`build.sh`](build.sh), which downloads its own JDK 17, Android SDK and
Gradle caches into `.tools/` (about 2 GB, ignored by git); nothing is installed system-wide and
Android Studio is not needed. You need an x86_64 Linux with `bash`, `curl`, `tar`, `unzip` and
`sha256sum`; immutable distros such as Fedora Silverblue work.

```sh
./build.sh              # debug APK -> dist/MeshConnect-debug.apk
./build.sh test         # unit tests + Android lint
./build.sh install      # build and install on a phone over adb
./build.sh run          # install, launch, follow logcat
./build.sh release      # signed APK + AAB (with MESHCONNECT_KEYSTORE set)
./build.sh gradle <task>          # any Gradle task with the bundled toolchain
eval "$(./build.sh env)"          # java, adb and gradle in your shell
tools/emulator.sh start           # headless AOSP emulator, no Google services (needs /dev/kvm)
```

Strings (English, Spanish, Valencian) are generated by `scripts/gen_strings.py` from
`scripts/strings_table.py`, and icons by `scripts/gen_icons.py`: edit those, never the XML.

More: [MESHCORE_PROTOCOL.md](MESHCORE_PROTOCOL.md) for the wire protocol as implemented, with
citations into the firmware; [docs/releasing.md](docs/releasing.md) for releases and signing;
[CONTRIBUTING.md](CONTRIBUTING.md); [CHANGELOG.md](CHANGELOG.md); and [FORK.md](FORK.md) for how
the code tracks MeshCore Hardened.

## Credits

MeshConnect is based on [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app) by
thatSFguy: its protocol engine, its security design and much of the app come from there, and
MeshCore Hardened in turn reused transports and scaffolding from the same author's
[reticulum-mobile-app](https://github.com/thatSFguy/reticulum-mobile-app). It speaks the protocol
of [MeshCore](https://github.com/meshcore-dev/MeshCore) (MIT). Map data © OpenStreetMap
contributors, tiles by OpenFreeMap; Material Symbols by Google. Full details and terms in
[NOTICE](NOTICE). MeshConnect is not affiliated with or endorsed by the MeshCore project or the
MeshCore Hardened author.

## Licence

AGPL-3.0-only. See [LICENSE](LICENSE). Third-party components keep their own licences, listed in
[NOTICE](NOTICE) and under About → Open-source licences in the app.

## AI use

Most of MeshConnect's code, documentation and artwork was written by an AI assistant (Anthropic's
Claude) working with its maintainer, who directs the work, reviews it, tests it on real devices
and is responsible for what is published.
