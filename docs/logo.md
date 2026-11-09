# Logo

Mark: concept **B, "Mesh bubble"** — a speech bubble with three linked nodes cut
out of it. Seed colour `#2B5FD9` (also the seed of the fallback Material scheme,
`scripts/gen_colors.py`). Chosen 2026-11-09 from five concepts (the others: Triad, Hops, Node M, On air).

Assets:

- `ic_launcher_background.xml` — flat seed colour (`@color/icon_background`).
- `ic_launcher_foreground.xml` / `ic_launcher_monochrome.xml` — white bubble on the 108 dp
  adaptive grid, inside the 66 dp safe circle.
- `ic_stat_message.xml` / `ic_stat_link.xml` — the same mark on the 24 dp status-bar grid.

All four are written by `scripts/gen_logo.py` (needs shapely); never paste path data by hand.

The cut-outs are real geometry, not masks (Android vectors have none): the paths were computed by `scripts/gen_logo.py`
with shapely (bubble minus the union of the node discs and the buffered links) and written with
`fillType="evenOdd"`. To change the mark, regenerate the same four paths.
