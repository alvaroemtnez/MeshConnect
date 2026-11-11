<div align="center">
  <img src="docs/logo.png" width="128" alt="MeshConnect logo">
  <h1>MeshConnect</h1>
  <p><strong>Private off-grid LoRa messaging for Android.</strong><br>
  Message people across a MeshCore radio mesh with no internet, no phone signal and no accounts.</p>
  <!-- Stores, one row: F-Droid, Accrescent and Flathub join Obtainium here. -->
  <p>
    <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/alvaroemtnez/MeshConnect"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="72" alt="Get it on Obtainium"></a>
  </p>
</div>

## What it is

[MeshCore](https://github.com/meshcore-dev/MeshCore) is open firmware that turns small, cheap LoRa
radios into a mesh network. Each radio reaches a few kilometres on its own, and repeaters pass
messages along so they can travel much further, without any internet or mobile network behind them.
That makes it useful for hiking, events, rural areas, or when the usual networks are down.

A **companion radio** is the one you carry. It does the radio work, and MeshConnect is the app on
your phone that talks to it. You use it to:

- send direct messages to people and post in group channels;
- see who is on the mesh: people, repeaters, rooms and sensors, in a list and on a map;
- check how your messages travel, and whether they arrived;
- set up and look after your own radio and repeaters.

MeshConnect runs entirely on your phone. There is no server, no sign-up, no analytics and no Google
Play Services.

## What you need

- A phone running **Android 8.0** or newer.
- A LoRa radio running **MeshCore companion firmware**: Heltec, RAK WisBlock, LilyGo T-Echo, Seeed
  Xiao/Wio and the other boards MeshCore supports. Connect it over **Bluetooth**, a **USB-C** cable,
  or **TCP** if it is a base station on your network.

MeshCore and Meshtastic are different networks and cannot talk to each other. A Meshtastic radio
needs MeshCore firmware before MeshConnect can use it.

## Install

- **[Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/alvaroemtnez/MeshConnect)**
  installs MeshConnect and keeps it updated from the official releases.
- Or download the APK from the
  [releases page](https://github.com/alvaroemtnez/MeshConnect/releases).
  [docs/verifying.md](docs/verifying.md) shows how to check a download really came from here.

Open the app, turn on your radio and pick it from the list. The app stays connected in the
background and reconnects on its own.

## Features

**Messaging**
- Direct messages and channels (public, hashtag and private), with automatic retries.
- A delivery tick only when the other side has really confirmed it.
- Replies, reactions and @mentions.
- The route a message took to reach you, drawn on the map, and which repeaters carried yours.
- Join a channel or add a contact by scanning a QR code or opening a link.

**Nodes and map**
- Everyone your radio hears: contacts, repeaters, rooms and sensors, with search, sorting and
  filters, and a tidy-up for nodes not heard in a while.
- A map of every node with a known position, and the links between a repeater and its neighbours.
- Traces and pings along a route, with the signal at each hop.

**Your radio and repeaters**
- Radio settings, regional presets, and settings codes you can share as a QR.
- Repeater and room administration: status, telemetry, neighbours, regions, settings and a console.
- Firmware updates over Bluetooth, with the board checked before anything is written.
- Live signal and battery in the top bar, battery alerts, and a home-screen widget.

**Everyday**
- Notifications, per-chat mute, mark read or unread.
- Backups, message retention, blocking.
- English, Spanish and Valencian, following your phone's language.

The complete list, with the details and limits of each feature, is in
[docs/FEATURES.md](docs/FEATURES.md).

## Privacy and security

- **Nothing leaves your phone that you did not ask for.** The only internet use is map tiles
  (which you can turn off) and firmware downloads when you start an update.
- **Your message history is encrypted** on the phone, and passwords, channel keys and other secrets
  are kept in the Android Keystore. If the Keystore is unavailable, the app refuses to store them
  rather than keep them in plain text. Android cloud backup is off.
- **Nodes are verified.** A node's announcement must carry a valid signature before it is added or
  shown on the map, and a name typed in a channel is never treated as proof of who sent it.
- **Channels are obfuscated, not secure.** MeshCore encrypts channel messages in a way anyone with
  the channel's key can read, and the protocol leaves no room for better. The app says so plainly.
  Direct messages are end-to-end encrypted between the two radios.
- **TCP is off by default.** The MeshCore TCP link is unencrypted, so the app warns you before
  enabling it and shows a warning for as long as it is in use.
- The diagnostics log is off by default and removes secrets from what it records.

To report a vulnerability, see [SECURITY.md](SECURITY.md).

## Building from source

Everything goes through [`build.sh`](build.sh); Android Studio is not needed. It runs on any
x86_64 Linux, including immutable distros (Fedora Silverblue), and needs only `bash`, `curl`,
`tar`, `unzip` and `sha256sum` from the host. The first run downloads Temurin JDK 17, the Android
command-line tools and the SDK packages into `.tools/` (ignored by git, ~2 GB); Gradle,
Kotlin/Native and Robolectric caches live there too, so nothing is written to `~/.gradle`,
`~/.android` or `~/.m2`. (adb keeps its key in `~/.android/adbkey`, shared with every project.)

```console
$ ./build.sh            # debug APK -> dist/MeshConnect-debug.apk
$ ./build.sh test       # shared + app unit tests
$ ./build.sh install    # build and install on the phone over adb
$ ./build.sh run        # install, launch, follow logcat
$ ./build.sh release    # release APK + AAB (signed if MESHCONNECT_KEYSTORE is set)
$ ./build.sh gradle <task>       # any Gradle task with the bundled toolchain
$ eval "$(./build.sh env)"       # put java/adb/gradle env in your shell
$ ./build.sh distclean  # remove build outputs and .tools/
```

Release signing reads `MESHCONNECT_KEYSTORE`, `MESHCONNECT_KEY_ALIAS`, `MESHCONNECT_KEYSTORE_PASS`
and `MESHCONNECT_KEY_PASS`. Create a keystore once with the bundled JDK and keep it outside the
repository:

```console
$ .tools/jdk-17/bin/keytool -genkeypair -v -keystore ~/meshconnect.jks -alias meshconnect \
      -keyalg RSA -keysize 4096 -validity 10000
```

### Strings and icons

- **Strings** (English, Spanish, Valencian) are written by `scripts/gen_strings.py` from
  `scripts/strings_table.py`. Edit the table, never the XML.
- **Icons** are Material Symbols vector drawables written by `scripts/gen_icons.py`. Add a name
  to its list and run it.

### Emulator

A plain AOSP image (no Google services) runs headless from `.tools/` (needs `/dev/kvm`); debug
builds include x86_64 for it:

```console
$ eval "$(./build.sh env)"
$ android --no-metrics --sdk=$ANDROID_HOME sdk install emulator system-images/android-34/default/x86_64
$ tools/emulator.sh start      # creates the AVD on first use
$ ./build.sh install && adb shell screencap -p > shot.png
```

### Releases

CI runs on the self-hosted Forgejo (`.forgejo/workflows/`). Pushing a tag `vX.Y.Z` builds a signed
APK + AAB and publishes the release, with the `## X.Y.Z` section of [CHANGELOG.md](CHANGELOG.md)
as notes, on Forgejo and on the GitHub mirror. `versionCode` is `X*10000 + Y*100 + Z`. Signing
keys, secrets, F-Droid and Accrescent: [docs/releasing.md](docs/releasing.md).

### Project docs

- [docs/FEATURES.md](docs/FEATURES.md) — the full feature inventory.
- [MESHCORE_PROTOCOL.md](MESHCORE_PROTOCOL.md) — the MeshCore wire protocol as implemented, with
  citations into the firmware; code comments cite its sections.
- [docs/PARITY.md](docs/PARITY.md) — the feature surface compared with the official MeshCore app.
- [CHANGELOG.md](CHANGELOG.md), [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md).
- [FORK.md](FORK.md) and [docs/upstream/](docs/upstream/) — how the code relates to MeshCore
  Hardened, and that project's own history, kept for provenance.

## Credits

MeshConnect is based on **[MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app)**
by thatSFguy. Its protocol engine, its security design and much of the app come from that
project.

- **[reticulum-mobile-app](https://github.com/thatSFguy/reticulum-mobile-app)** by thatSFguy, for
  the transports and app scaffolding MeshCore Hardened reused.
- **[MeshCore](https://github.com/meshcore-dev/MeshCore)**, the firmware and protocol (MIT).
- Map data © OpenStreetMap contributors; tiles by OpenFreeMap. Material Symbols © Google
  (Apache-2.0). Full notices in [NOTICE](NOTICE).

MeshConnect is licensed under the [GNU AGPL-3.0](LICENSE). It is an independent project, not
affiliated with or endorsed by the MeshCore project or the MeshCore Hardened author.
