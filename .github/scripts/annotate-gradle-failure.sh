#!/usr/bin/env bash
# Re-emits a failed Gradle run's diagnostics as workflow error annotations.
#
# Job logs and step summaries on this repository require repository-admin rights to download, so a
# contributor without them can otherwise see only "Process completed with exit code 1". Annotations
# render on the public run page, which makes the actual compiler message readable to anyone who can
# see the run. This is the same reasoning behind the plain-text lint report in app/build.gradle.kts.
set -uo pipefail

log="${1:?usage: annotate-gradle-failure.sh <gradle-log>}"
[ -f "$log" ] || exit 0

# GitHub treats %, CR and LF as control characters inside a workflow command.
emit() {
  local message="$1"
  message="${message//'%'/'%25'}"
  message="${message//$'\r'/'%0D'}"
  message="${message//$'\n'/'%0A'}"
  echo "::error::${message}"
}

# Kotlin ("e: ") and javac ("error: ") compile failures, which are the common case.
while IFS= read -r line; do
  [ -n "$line" ] && emit "$line"
done < <(grep -E '^(e: |error: )' "$log" | head -n 20)

# Otherwise Gradle's own explanation, e.g. a failing test task or an unresolvable dependency.
while IFS= read -r line; do
  [ -n "$line" ] && emit "$line"
done < <(sed -n '/^\* What went wrong:/,/^\* Try:/p' "$log" | head -n 20)

exit 0
