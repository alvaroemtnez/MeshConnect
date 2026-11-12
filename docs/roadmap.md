# Roadmap

Open work only. Done items go into the commit history and `CHANGELOG.md`. Upstream syncing
and what to offer upstream are in [FORK.md](../FORK.md); feature parity in [PARITY.md](PARITY.md).

## Distribution

- **F-Droid**: the first release signed by the fixed `build.sh` (1.2.0 onward) is the one to
  submit, so F-Droid's build verifies against ours. Steps, the recipe and what reviewers may
  ask: [releasing.md](releasing.md), "F-Droid". Tell thatSFguy before submitting.
- **Store screenshots from a real radio over Bluetooth**: the current ones
  (`fastlane/metadata/android/en-US/images/phoneScreenshots/`) come from the fake radio over
  TCP, so every one shows the unencrypted-link flag (the red ⚠, the red link on the widget),
  which a Bluetooth user never sees. Retake the same five screens (chats, a conversation, the
  map, nodes, the widget) from a radio over Bluetooth, redacted with
  `tools/redact-screenshot.py` (CLAUDE.md, "Screenshots and demo assets").
- **Accrescent**: sign-up is closed (November 2026). The APK set meets the publishing
  requirements and the app targets API 37; when sign-up opens, register, verify the domain by
  DNS and upload ([releasing.md](releasing.md), "Accrescent").
