# Contributing

MeshConnect is a personal fork of [MeshCore Hardened](https://github.com/thatSFguy/meshcore-mobile-app),
developed on <https://code.alvaroedo.io/alvaroemtnez/MeshConnect> and mirrored to GitHub.

- **Bugs in MeshConnect** (including anything in the interface, the widget, the map, translations):
  open an issue on the Forgejo repository. Please don't report them to MeshCore Hardened.
- **Security problems:** report privately to the maintainer rather than in a public issue.
- **Protocol and engine (`shared/`):** this code tracks upstream (see [FORK.md](FORK.md)). Fixes
  that apply to MeshCore Hardened too are best sent there first, and MeshConnect will merge them.
- **Pull requests** are welcome on either host. GitHub is a mirror, so a PR opened there is
  reviewed there but merged on Forgejo, keeping your commits and authorship; GitHub then shows
  it as merged once the mirror catches up. If it had to be reworked first, it is closed with a
  link to the commit that carries it, crediting you as co-author. The details are in
  [FORK.md](FORK.md) → *Pull requests on the GitHub copy*.
- **Translations:** strings live in `scripts/strings_table.py` (English, Spanish, Valencian).
  Corrections are welcome.

## If you fork

The code is [AGPL-3.0-only](LICENSE). Keep the licence and the notices in [NOTICE](NOTICE),
change the application id and signing key, and make clear to your users that your fork is not
MeshConnect or MeshCore Hardened.
