#!/usr/bin/env python3
"""Fetch Material Symbols (Outlined, Apache 2.0) and write them as vector
drawables into androidApp/src/main/res/drawable/.

    python3 scripts/gen_icons.py          # (re)generate every icon below

Add an icon: append (drawable name, symbol name, mirror in RTL) to ICONS and
run the script; a fourth element "fill1" takes the filled glyph (a favourite's
star when it is on). `gen_icons.py ic_a ic_b` regenerates only those. Browse names at https://fonts.google.com/icons. Use them from
Compose with painterResource(R.drawable.ic_x). Adapted from Skypost.
"""

from __future__ import annotations

import re
import sys
import urllib.request
from pathlib import Path

ICONS = [
    # Navigation and app bars
    ("ic_chat", "chat", False),
    ("ic_hub", "hub", False),
    ("ic_map", "map", False),
    ("ic_settings", "settings", False),
    ("ic_search", "search", False),
    ("ic_more_vert", "more_vert", False),
    ("ic_arrow_back", "arrow_back", True),
    ("ic_close", "close", False),
    ("ic_check", "check", False),
    ("ic_qr_code", "qr_code_2", False),
    ("ic_qr_code_scanner", "qr_code_scanner", False),
    ("ic_chevron_right", "chevron_right", True),
    ("ic_expand_more", "expand_more", False),
    ("ic_expand_less", "expand_less", False),
    ("ic_arrow_upward", "arrow_upward", False),
    ("ic_arrow_downward", "arrow_downward", False),
    ("ic_list", "list", True),
    ("ic_info", "info", False),
    ("ic_mail", "mail", False),
    # Message composer
    ("ic_add", "add", False),
    ("ic_send", "send", True),
    ("ic_badge", "badge", False),
    ("ic_tag", "tag", False),
    ("ic_person_add", "person_add", False),
    ("ic_pin_drop", "pin_drop", False),
    ("ic_add_location", "add_location_alt", False),
    # Node kinds and filters
    ("ic_apps", "apps", False),
    ("ic_person", "person", False),
    ("ic_cell_tower", "cell_tower", False),
    ("ic_forum", "forum", False),
    ("ic_sensors", "sensors", False),
    ("ic_group", "group", False),
    ("ic_public", "public", False),
    ("ic_lock", "lock", False),
    ("ic_push_pin", "push_pin", False),
    ("ic_star", "star", False),
    ("ic_star_filled", "star", False, "fill1"),
    ("ic_notifications_off", "notifications_off", False),
    ("ic_alt_route", "alt_route", False),
    ("ic_add_comment", "add_comment", False),
    # Map
    ("ic_my_location", "my_location", False),
    ("ic_location_on", "location_on", False),
    ("ic_remove", "remove", False),
    ("ic_zoom_out_map", "zoom_out_map", False),
    ("ic_layers", "layers", False),
    ("ic_route", "route", False),
    ("ic_radar", "radar", False),
    ("ic_open_in_new", "open_in_new", False),
    ("ic_directions", "directions", False),
    # Status
    ("ic_bluetooth", "bluetooth", False),
    ("ic_usb", "usb", False),
    ("ic_lan", "lan", False),
    ("ic_warning", "warning", False),
    ("ic_link_off", "link_off", False),
    # Misc actions
    ("ic_delete", "delete", False),
    ("ic_delete_sweep", "delete_sweep", False),
    ("ic_content_copy", "content_copy", False),
    ("ic_share", "share", False),
    ("ic_code", "code", False),
    ("ic_bug_report", "bug_report", False),
    ("ic_gavel", "gavel", False),
    ("ic_history", "history", False),
    ("ic_refresh", "refresh", False),
    ("ic_sort", "sort", True),
    ("ic_edit", "edit", False),
    ("ic_block", "block", False),
    ("ic_favorite", "favorite", False),
    ("ic_translate", "translate", False),
    ("ic_shield", "shield", False),
    # Widget (Glance can't draw on a canvas, so its gauges are icons)
    ("ic_battery_full", "battery_full", False),
    ("ic_battery_5", "battery_5_bar", False),
    ("ic_battery_3", "battery_3_bar", False),
    ("ic_battery_1", "battery_1_bar", False),
    ("ic_battery_alert", "battery_alert", False),
    ("ic_signal_4", "signal_cellular_alt", False),
    ("ic_signal_2", "signal_cellular_alt_2_bar", False),
    ("ic_signal_1", "signal_cellular_alt_1_bar", False),
    ("ic_signal_0", "signal_cellular_null", False),
    ("ic_mark_unread", "mark_chat_unread", False),
    # Settings hub
    ("ic_link", "link", False),
    ("ic_password", "password", False),
    ("ic_antenna", "settings_input_antenna", False),
    ("ic_policy", "policy", False),
    ("ic_tune", "tune", False),
    ("ic_palette", "palette", False),
    ("ic_backup", "backup", False),
    ("ic_database", "database", False),
    ("ic_restart", "restart_alt", False),
    ("ic_swap_horiz", "swap_horiz", False),
    # Chat list and message actions
    ("ic_archive", "archive", False),
    ("ic_unarchive", "unarchive", False),
    ("ic_mark_read", "mark_chat_read", False),
    ("ic_keep_off", "keep_off", False),
    ("ic_reply", "reply", True),
    ("ic_add_reaction", "add_reaction", False),
    ("ic_forward", "forward", True),
    ("ic_alternate_email", "alternate_email", False),
    ("ic_bluetooth_searching", "bluetooth_searching", False),
    # Message status
    ("ic_done", "done", False),
    ("ic_done_all", "done_all", False),
    ("ic_schedule", "schedule", False),
    ("ic_error", "error", False),
    ("ic_visibility", "visibility", False),
    ("ic_visibility_off", "visibility_off", False),
    # Multi-select and admin tiles
    ("ic_check_circle", "check_circle", False),
    ("ic_circle", "radio_button_unchecked", False),
    ("ic_select", "checklist", True),
    ("ic_update", "update", False),
    ("ic_help", "help", False),
    ("ic_terminal", "terminal", False),
    ("ic_power", "power_settings_new", False),
]

URL = "https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/{}/{}/24px.svg"
OUT = Path(__file__).resolve().parents[1] / "androidApp" / "src" / "main" / "res" / "drawable"
LONG_PATH = 800

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols "{symbol}" (Apache 2.0). Generated by scripts/gen_icons.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{tools_ns}
    android:width="24dp"
    android:height="24dp"{mirror}
    android:tint="?android:attr/colorControlNormal"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@android:color/white"
            android:pathData="{d}"{ignore} />
    </group>
</vector>
"""


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1:])
    for name, symbol, mirror, *style in ICONS:
        if only and name not in only:
            continue
        svg = urllib.request.urlopen(URL.format(symbol, style[0] if style else "default"), timeout=30).read().decode()
        if 'viewBox="0 -960 960 960"' not in svg:
            raise SystemExit(f"{symbol}: unexpected viewBox")
        paths = re.findall(r'<path d="([^"]+)"', svg)
        if len(paths) != 1:
            raise SystemExit(f"{symbol}: expected one path, got {len(paths)}")
        d = paths[0]
        long = len(d) > LONG_PATH
        (OUT / f"{name}.xml").write_text(
            TEMPLATE.format(
                symbol=symbol,
                d=d,
                mirror='\n    android:autoMirrored="true"' if mirror else "",
                tools_ns='\n    xmlns:tools="http://schemas.android.com/tools"' if long else "",
                ignore='\n            tools:ignore="VectorPath"' if long else "",
            )
        )
        print(f"{name}.xml  ({symbol}, {len(d)} chars)")


if __name__ == "__main__":
    main()
