<p align="center"><img src="docs/logo.png" width="128" alt="MeshConnect logo"></p>

<h1 align="center">MeshConnect</h1>

<p align="center">
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/alvaroemtnez/MeshConnect"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" height="72" alt="Get it on Obtainium"></a>
</p>

Off-grid encrypted LoRa messaging for **MeshCore** radios, on Android. No servers, no accounts, no
Google Play Services, no analytics, no crash reporting.

> **MeshConnect is a fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app)
> by [thatSFguy](https://github.com/thatSFguy).** The protocol layer, the radio engine, storage,
> security work and most of the screens are theirs; this fork reworks the interface (Material You
> colours, Signal-style search and composer, rich message cards, a MapLibre map), adds Spanish and
> Valencian/Catalan, a home-screen widget and a ping/trace map, and builds and releases from a
> self-hosted Forgejo. Upstream's own README is kept at
> [docs/upstream/README-MeshCoreHardened.md](docs/upstream/README-MeshCoreHardened.md) and its
> history up to the fork at [docs/upstream/CHANGELOG-MeshCoreHardened.md](docs/upstream/CHANGELOG-MeshCoreHardened.md).
> Please report MeshConnect problems here, not upstream.

- Source: <https://code.alvaroedo.io/alvaroemtnez/MeshConnect>, mirrored to
  <https://github.com/alvaroemtnez/MeshConnect>
- Licence: [AGPL-3.0-only](LICENSE), as upstream
- App id: `io.alvaroedo.meshconnect` — installs alongside MeshCore Hardened, not over it

## Radios

Any radio running **MeshCore companion firmware**, over **Bluetooth LE**, **USB-C serial** or
**TCP** to a networked base station (TCP is off by default and flagged as unencrypted whenever it
is in use). Heltec, RAK WisBlock, LilyGo T-Echo, Seeed Xiao/Wio and the rest of the MeshCore board
list. MeshCore and Meshtastic are different protocols and do not interoperate.

Requires Android 8.0 (API 26) or newer.

## Features

Everything MeshCore Hardened does (see [docs/FEATURES.md](docs/FEATURES.md) and [docs/PARITY.md](docs/PARITY.md)):
direct and channel messages with retries and real delivery ticks, mentions and reactions, the route
a message took, channels and community QR join, nodes with discovery and a stale-node sweep,
per-contact routing and traces, a node map with repeater neighbour links, repeater and room
administration, Bluetooth firmware updates, backups, blocking and a redacting diagnostics log.

## Security posture

Carried over unchanged from upstream, and enforced in code:

- Advert Ed25519 signatures are verified before any node is imported or mapped.
- Channel sender names are never trusted for identity or contact changes.
- Secrets live in the Android Keystore; with no Keystore the app declines to store them rather
  than falling back to plaintext. The message database is encrypted (SQLCipher).
- Every frame parse is bounds-checked; delivery is only ever inferred from a well-formed ACK.
- Channels are presented as **obfuscated, not secure**: the protocol mandates AES-ECB with a
  2-byte MAC.
- Auto Backup is disabled; the diagnostics log is off by default and redacts secrets.
- Outbound internet: map tiles and (only when you ask) firmware downloads. Nothing else.

## Building

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

## Releases

CI runs on the self-hosted Forgejo (`.forgejo/workflows/`). Pushing a tag `vX.Y.Z` builds a signed
APK + AAB and publishes the release, with the `## X.Y.Z` section of [CHANGELOG.md](CHANGELOG.md)
as notes, on Forgejo and on the GitHub mirror. `versionCode` is `X*10000 + Y*100 + Z`. Signing keys,
secrets, F-Droid and Accrescent: [docs/releasing.md](docs/releasing.md); checking a download is
really ours: [docs/verifying.md](docs/verifying.md).

## Project docs

- [MESHCORE_PROTOCOL.md](MESHCORE_PROTOCOL.md) — the MeshCore wire spec as implemented, with
  citations into the firmware. Inherited from upstream; code comments cite its sections.
- [docs/FEATURES.md](docs/FEATURES.md) — the full feature inventory.
- [docs/PARITY.md](docs/PARITY.md) — the feature surface against the mainstream MeshCore app,
  and where this app deliberately differs.
- [FORK.md](FORK.md) — how MeshConnect tracks upstream. [SECURITY.md](SECURITY.md),
  [CONTRIBUTING.md](CONTRIBUTING.md).
- [docs/upstream/](docs/upstream/) — MeshCore Hardened's own history and process documents
  (README, changelog, security review, lessons, rebuild playbook, original scope and reuse
  plan), kept for provenance.

## Credits

- **[MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app)** by thatSFguy — the
  app this is forked from (AGPL-3.0).
- **[reticulum-mobile-app](https://github.com/thatSFguy/reticulum-mobile-app)** by thatSFguy —
  transports and app scaffolding upstream reused.
- **[MeshCore](https://github.com/meshcore-dev/MeshCore)** — the firmware and protocol (MIT).
- Map data © OpenStreetMap contributors. Material Symbols © Google (Apache-2.0).

MeshConnect is an independent project, not affiliated with or endorsed by the MeshCore project or
the MeshCore Hardened author.
