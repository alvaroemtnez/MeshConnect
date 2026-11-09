# Logo

Mark: concept **B, "Mesh bubble"** — a speech bubble with three linked nodes cut
out of it. Seed colour `#0F7B6C` (teal; was `#2B5FD9` blue until 2026-11-09) (also the seed of the fallback Material scheme,
`scripts/gen_colors.py`). Chosen 2026-11-09 from five concepts (the others: Triad, Hops, Node M, On air).

Assets:

- `ic_launcher_background.xml` — flat seed colour (`@color/icon_background`).
- `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` — white bubble, 40 dp wide on the
  108 dp adaptive grid, centred on its area centroid (a bounding-box centre leaves it visibly
  high, because the tail hangs below the body). At 52 dp it looked oversized on a Pixel 9 Pro.
- `ic_stat_message.xml` / `ic_stat_link.xml` — the same mark on the 24 dp status-bar grid.

- `docs/icon.png` (512 px, square, the repository avatar) and `docs/logo.png` (256 px, round,
  the README header) — the launcher's visible 72 dp window.

All of these are written by `scripts/gen_logo.py` (needs shapely); never paste path data by hand.

The cut-outs are real geometry, not masks (Android vectors have none): the paths were computed by `scripts/gen_logo.py`
with shapely (bubble minus the union of the node discs and the buffered links) and written with
`fillType="evenOdd"`. To change the mark, regenerate the same four paths.
