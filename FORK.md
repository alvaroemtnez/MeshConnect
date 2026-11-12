# Tracking upstream

MeshConnect is a fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app)
by thatSFguy, forked at upstream commit `8f226db` (0.10.15, 2026-11-09).

## Where things live

| What | Where |
|---|---|
| MeshConnect (origin) | `code.alvaroedo.io/alvaroemtnez/MeshConnect`, a Forgejo fork of the mirror below |
| Upstream mirror | `code.alvaroedo.io/mirrors/meshcore-mobile-app` (pull mirror of upstream's GitHub repo) |
| GitHub copy | `github.com/alvaroemtnez/MeshConnect`, a GitHub fork of upstream, fed by a push mirror from Forgejo. Releases are created there by `.forgejo/workflows/release.yml` (`GH_REPO=alvaroemtnez/MeshConnect`) |

Because the GitHub copy is a real GitHub fork of upstream, changes to `shared/` can go back to
thatSFguy as pull requests from it.

## Decision: soft fork for `shared/`, hard fork for `androidApp/`

Measured at the end of the first round of work (fork point → HEAD):

- `shared/`: 4 files, +103 / −1, all additive (the stats parser and engine call, a User-Agent).
- `androidApp/`: 176 files, about +6,400 / −3,000. Every file moved package
  (`io.github.thatsfguy.meshcore.android` → `io.alvaroedo.meshconnect`), and the screens most
  upstream work touches (Nodes, conversation, map, top bar) were rewritten. The i18n pass will
  touch nearly every remaining UI line.

A pure soft fork, merging upstream wholesale and keeping a thin patch on top, is not realistic
for the app module: once i18n lands, almost any upstream UI change conflicts textually.
A pure hard fork would throw away the part worth tracking. Upstream's real value is the
protocol and engine work in `shared/`: firmware-verified wire fixes, hop-width bugs, retry policy,
firmware updates. That code barely needs to change for MeshConnect.

So:

- **`shared/` stays a soft fork.** Keep its package (`io.github.thatsfguy.meshcore`) and keep
  changes small, additive and in upstream's style. Pass fork-specific values in from the app
  rather than editing it (for example `MeshCoreEngine(appName = "MeshConnect")`). Offer every
  `shared/` change upstream as a PR. Upstream merges into `shared/` should be clean or nearly so.
- **`androidApp/` is a hard fork.** Upstream UI changes are ported by hand, feature by feature,
  after reading the diff. Upstream's *tests* for app logic are worth porting even when the UI
  isn't, because they encode hardware findings.

## Syncing

Use real merges, not cherry-picks, so git keeps track of the upstream base and each sync only
covers what is new.

```console
$ git remote add upstream https://code.alvaroedo.io/mirrors/meshcore-mobile-app.git   # once
$ git fetch upstream
$ git log --oneline HEAD..upstream/main            # what's new
$ git diff HEAD...upstream/main --stat -- shared   # protocol/engine changes: merge these
$ git diff HEAD...upstream/main --stat -- androidApp
$ git switch -c upstream-sync
$ git merge upstream/main
```

Then resolve:

- **`shared/`**: take upstream's side; re-check the four MeshConnect additions still compile.
  One deliberate *change* rather than addition: `presentation/BatteryLevel.percent` uses a
  Li-ion discharge curve instead of upstream's 3000–4200 mV line (2026-11-10, see its KDoc).
  If upstream touches that file, keep our `percent` and `CURVE_MV`.
- **Build files** (1.2.0 moved to AGP 9.5.1, Gradle 9.8.1, Kotlin 2.4.21; upstream stays on AGP 8
  until it moves): `shared/build.gradle.kts` now uses `com.android.kotlin.multiplatform.library`
  with a `kotlin { android { } }` block, and its host tests live in `src/androidHostTest` (was
  `src/androidUnitTest`). A trial merge of a simulated upstream change (2026-11-12) showed:
  protocol fixes and dependency bumps in `shared/` merge cleanly; edits to existing tests follow
  the rename; a *new* upstream test in `androidUnitTest/` is moved into `androidHostTest/` by git,
  which asks you to confirm (`git add` it there); plugin versions in the root
  `build.gradle.kts` conflict, so keep ours, and port upstream's other root changes by hand.
- **`androidApp/`**: git's rename detection maps upstream's old paths onto ours, so many hunks
  apply cleanly. Where they don't, keep our version and port the change by hand. Strings go
  through `scripts/strings_table.py`, icons through `scripts/gen_icons.py`. Watch for upstream
  wiring tests that pin source text (`*WiringTest.kt`); they need the same translation.
- **Docs**: upstream's `CHANGELOG.md` is ours now; append upstream's new sections to
  `docs/upstream/CHANGELOG-MeshCoreHardened.md` instead.
- Run `./build.sh test`, drive the change on the emulator (`tools/emulator.sh`,
  `tools/fake_radio.py`), and describe the upstream range in the merge commit.

## Things to offer upstream

- `BatteryLevel` on a discharge curve: the straight line reads 3.70 V as 58% and puts 20% at
  3.24 V, so a low-battery warning arrives with nothing left to act on.

- `RadioStats` + `MeshCoreEngine.radioStats()`: live noise floor, RSSI and SNR (their issue #6).
- The trace round trip (`androidApp/.../ui/model/TraceRoutes.kt`): upstream sends the stored
  route as-is and then reversed, which only returns when the far end is in direct range of the
  sender (`Mesh.cpp:42-67`). A round-trip hop list always comes back. Worth an issue with the
  firmware citation even if they want their own implementation.
- `LinkMeter` (last hop of flooded RX-log packets as the "repeaters heard directly" signal).
- Their `RawPacketTest` labels route type 1 "direct"; it is `ROUTE_TYPE_FLOOD` (`Packet.h:15`).
  The parser is right; only the comment is wrong.
