"""Logo concept B geometry -> SVG path data for the Android vectors (docs/logo.md).

Needs shapely: python3 -m venv /tmp/v && /tmp/v/bin/pip install shapely && /tmp/v/bin/python scripts/gen_logo.py
Prints TRAY (24 grid) and FG (108 grid) path data; paste into the four drawables.
"""
from shapely.geometry import Point, LineString, Polygon, box
from shapely.ops import unary_union
import shapely.affinity as aff

def rrect(x, y, w, h, r):
    return box(x + r, y + r, x + w - r, y + h - r).buffer(r, quad_segs=16)

def bubble(scale):
    # Geometry drawn on the 24 grid, scaled for the 108 grid.
    body = rrect(3, 3.5, 18, 14.5, 4)
    tail = Polygon([(6.2, 16.5), (5.5, 21.5), (11, 17.6)])
    b = unary_union([body, tail])
    pts = [(7.5, 14), (12, 9.5), (16.5, 14)]
    holes = unary_union([Point(p).buffer(2.05, quad_segs=16) for p in pts] +
                        [LineString(pts).buffer(0.8, cap_style=1, join_style=1)])
    g = b.difference(holes)
    return aff.scale(g, scale, scale, origin=(0, 0))

def path(geom, dx=0, dy=0):
    out = []
    polys = [geom] if geom.geom_type == "Polygon" else list(geom.geoms)
    for p in polys:
        for ring in [p.exterior] + list(p.interiors):
            c = list(ring.coords)
            out.append("M" + " L".join(f"{x+dx:.2f},{y+dy:.2f}" for x, y in c[:-1]) + "Z")
    return "".join(out)

import sys
print("TRAY", path(bubble(1)))
# Launcher: 24-grid mark scaled so the bubble spans ~52 units, centred in 108.
g = bubble(52 / 18)
minx, miny, maxx, maxy = g.bounds
print("FG", path(g, 54 - (minx + maxx) / 2, 54 - (miny + maxy) / 2))
