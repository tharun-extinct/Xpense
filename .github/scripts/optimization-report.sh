#!/usr/bin/env bash
# Reports what R8 actually did to the release build, as a Markdown table on stdout.
#
# Play Console shows optimization/obfuscation/shrinking percentages only after an upload, which is
# a slow and lossy way to find out that a keep rule silently disabled half the pipeline. R8 writes
# the same underlying facts to mapping.txt and usage.txt on every release build, so the numbers are
# available here, one push after the change that caused them.
#
# These are derived from R8's own output, not from Play's model, so treat them as the repo's
# definition of the three words rather than as figures that will match the console exactly.
set -uo pipefail

mapping_dir="${1:?usage: optimization-report.sh <mapping-dir> <apk>}"
apk="${2:?usage: optimization-report.sh <mapping-dir> <apk>}"

python3 - "$mapping_dir" "$apk" <<'PY'
import os
import sys
import zipfile

mapping_dir, apk = sys.argv[1], sys.argv[2]
mapping_path = os.path.join(mapping_dir, "mapping.txt")
usage_path = os.path.join(mapping_dir, "usage.txt")


def pct(part, whole):
    return f"{(100.0 * part / whole):.1f}%" if whole else "n/a"


kept_classes = renamed_classes = kept_members = 0
if os.path.isfile(mapping_path):
    with open(mapping_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if line.startswith("#"):
                continue
            if line[:1].isspace():
                # Member lines carry a "-> newName" only when R8 recorded a rename or a line-number
                # remap; either way they are members that survived.
                if "->" in line:
                    kept_members += 1
                continue
            if "->" in line and line.rstrip().endswith(":"):
                original, _, obfuscated = line.rstrip()[:-1].partition(" -> ")
                kept_classes += 1
                if original.strip() != obfuscated.strip():
                    renamed_classes += 1

removed_classes = removed_members = 0
if os.path.isfile(usage_path):
    with open(usage_path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if not line.strip() or line.startswith("#"):
                continue
            if line[:1].isspace():
                removed_members += 1
            elif not line.rstrip().endswith(":"):
                # A bare class line is a class removed whole; a trailing ":" introduces the members
                # that were stripped out of a class that survived.
                removed_classes += 1

total_classes = kept_classes + removed_classes
total_members = kept_members + removed_members

print("### R8 release optimization")
print()
if not total_classes:
    print(
        "No R8 output found under `%s`. Either the release build did not run, or "
        "`isMinifyEnabled` is off." % mapping_dir
    )
    raise SystemExit(0)

print("| Measure | Value | Definition |")
print("| --- | --- | --- |")
print(
    "| Shrinking | %s | %d of %d classes removed entirely |"
    % (pct(removed_classes, total_classes), removed_classes, total_classes)
)
print(
    "| Optimization | %s | %d of %d members removed, inlined or merged |"
    % (pct(removed_members, total_members), removed_members, total_members)
)
print(
    "| Obfuscation | %s | %d of %d surviving classes renamed |"
    % (pct(renamed_classes, kept_classes), renamed_classes, kept_classes)
)

if os.path.isfile(apk):
    with zipfile.ZipFile(apk) as archive:
        entries = archive.infolist()
    dex = sum(e.compress_size for e in entries if e.filename.endswith(".dex"))
    res = sum(
        e.compress_size
        for e in entries
        if e.filename.startswith("res/") or e.filename == "resources.arsc"
    )
    print()
    print("| Artifact | Compressed size |")
    print("| --- | --- |")
    print("| DEX | %.2f MB |" % (dex / 1048576.0))
    print("| Resources | %.2f MB |" % (res / 1048576.0))
    print("| APK total | %.2f MB |" % (os.path.getsize(apk) / 1048576.0))

print()
print(
    "Obfuscation below ~90% usually means a broad `-keep` is matching more than it should. "
    "A shrinking figure that drops after adding a dependency is the cue to check its consumer rules."
)
PY

exit 0
