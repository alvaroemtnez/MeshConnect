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
- **New About screen** crediting MeshCore Hardened, the project this is built on.
- **Keystore hardening from upstream PR #10:** each key-generation fallback tier gets its own
  `KeyGenerator`.
