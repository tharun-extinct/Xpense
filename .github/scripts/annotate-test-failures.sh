#!/usr/bin/env bash
# Turns JUnit XML results into workflow error annotations.
#
# Gradle only reports "There were failing tests" and points at an HTML report that requires
# repository-admin rights to download, so without this the run page never says *which* test failed.
# See annotate-gradle-failure.sh for the same reasoning applied to compiler output.
set -uo pipefail

results_dir="${1:?usage: annotate-test-failures.sh <test-results-dir>}"
[ -d "$results_dir" ] || exit 0

emit() {
  local message="$1"
  message="${message//'%'/'%25'}"
  message="${message//$'\r'/'%0D'}"
  message="${message//$'\n'/'%0A'}"
  echo "::error::${message}"
}

# Grouped by failure message, not one line per test: GitHub silently drops annotations past a
# small per-step limit, and a single environmental fault can fail a hundred cases at once, which
# would otherwise push every distinct failure off the page.
python3 - "$results_dir" <<'PY' | head -n 25 | while IFS= read -r line; do emit "$line"; done
import glob, os, sys, xml.etree.ElementTree as ET
from collections import OrderedDict

grouped = OrderedDict()
for path in sorted(glob.glob(os.path.join(sys.argv[1], "*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            detail = [
                line.strip()
                for line in (problem.get("message") or (problem.text or "")).strip().splitlines()
                if line.strip()
            ]
            # Compose assertions put the useful part on the *second* line ("Reason: ..."), so a
            # single line tells you a node interaction failed but never which node or why.
            message = " | ".join(detail[:3]) if detail else problem.get("type", "failed")
            grouped.setdefault(message, []).append(f"{case.get('classname')}.{case.get('name')}")

for message, cases in grouped.items():
    extra = f" (+{len(cases) - 1} more)" if len(cases) > 1 else ""
    print(f"{cases[0]}{extra}: {message}")
PY

exit 0
