# Contributing

MeshConnect is a personal fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app),
developed on <https://code.alvaroedo.io/alvaroemtnez/MeshConnect> and mirrored to GitHub.

- **Bugs in MeshConnect** (including anything in the interface, the widget, the map, translations):
  open an issue on the Forgejo repository. Please don't report them to MeshCore Hardened.
- **Security problems:** report privately to the maintainer rather than in a public issue.
- **Protocol and engine (`shared/`):** this code tracks upstream (see [FORK.md](FORK.md)). Fixes
  that apply to MeshCore Hardened too are best sent there first, and MeshConnect will merge them.
- **Translations:** strings live in `scripts/strings_table.py` (English, Spanish, Valencian).
  Corrections are welcome.

## If you fork

The code is [AGPL-3.0-only](LICENSE). Keep the licence and the notices in [NOTICE](NOTICE),
change the application id and signing key, and make clear to your users that your fork is not
MeshConnect or MeshCore Hardened.
