#!/usr/bin/env python3
"""List hard-coded user-facing literals in a Kotlin file, for moving them
into scripts/strings_table.py. Dev aid only; reviews what to translate.

    python3 scripts/i18n_extract.py androidApp/src/main/kotlin/.../Foo.kt
"""
import re
import sys

# Calls whose first string argument is shown to the user, in a composable.
CALLS = r'(?:Text|HintText|DetailText|SettingsGroupHeader|SectionHeader|MenuHeading|EmptyHint|SettingsSpoke|ExpandableHint|StatField|InfoRow|DetailRow|MenuAction|AppTopBar|SettingTile|ToggleRow|SwitchRow)'
PAT = re.compile(CALLS + r'\(\s*(?:text\s*=\s*|title\s*=\s*|label\s*=\s*)?"((?:[^"\\$]|\\.)+)"')
NAMED = re.compile(r'\b(?:title|label|subtitle|placeholder|text|hint|note|summary|description)\s*=\s*"((?:[^"\\$]|\\.)+)"')

for path in sys.argv[1:]:
    src = open(path, encoding="utf-8").read()
    # Drop comments so documentation prose isn't listed.
    src_nc = re.sub(r"//[^\n]*", lambda m: " " * len(m.group(0)), src)
    src_nc = re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), src_nc, flags=re.S)
    seen = set()
    for m in list(PAT.finditer(src_nc)) + list(NAMED.finditer(src_nc)):
        s = m.group(1)
        line = src_nc.count("\n", 0, m.start(1)) + 1
        if re.search(r"[A-Za-z]{2}", s) and (line, s) not in seen:
            seen.add((line, s))
            print(f"{line}\t{s}")
