#!/usr/bin/env bash
# Gate the mutation score from a `mutmut run` against a per-fixture threshold.
#
# Usage: scripts/mutation_check.sh <project-dir> [threshold-percent]
#
# Expects mutmut 3.x to be installed in <project-dir>'s venv (uv sync) and a
# `.mutmut-cache` populated by a prior `mutmut run` in <project-dir>.
#
# Score = killed / (killed + survived + suspicious + timeout)
# `skipped` and `no_tests` mutations are excluded from the denominator.
#
# On failure, prints each survived/suspicious/timeout mutation with its diff.
set -euo pipefail
export LC_ALL=C

project="${1:?usage: $0 <project-dir> [threshold-percent]}"
threshold="${2:-90}"

if [ ! -d "$project" ]; then
  echo "project dir not found: $project" >&2
  exit 2
fi

cd "$project"

if ! command -v uv >/dev/null 2>&1; then
  echo "uv not on PATH; mutation_check.sh expects to invoke uv-managed mutmut" >&2
  exit 2
fi

results=$(uv run --no-sync mutmut results --all True 2>&1 || true)

if [ -z "$results" ]; then
  echo "mutmut produced no results; did mutmut run complete?" >&2
  exit 2
fi

killed=$(printf '%s\n' "$results" | awk '/: killed$/ {n++} END{print n+0}')
survived=$(printf '%s\n' "$results" | awk '/: survived$/ {n++} END{print n+0}')
suspicious=$(printf '%s\n' "$results" | awk '/: suspicious$/ {n++} END{print n+0}')
timeout=$(printf '%s\n' "$results" | awk '/: timeout$/ {n++} END{print n+0}')
skipped=$(printf '%s\n' "$results" | awk '/: skipped$/ {n++} END{print n+0}')
no_tests=$(printf '%s\n' "$results" | awk '/: no_tests$/ {n++} END{print n+0}')

evaluated=$((killed + survived + suspicious + timeout))
total=$((evaluated + skipped + no_tests))

if [ "$evaluated" -eq 0 ]; then
  echo "no mutations evaluated (total=$total skipped=$skipped no_tests=$no_tests)" >&2
  exit 2
fi

score=$(awk -v k="$killed" -v t="$evaluated" 'BEGIN{printf "%.2f", 100*k/t}')

printf 'mutmut score: %s%% (killed=%d evaluated=%d; survived=%d suspicious=%d timeout=%d; skipped=%d no_tests=%d)\n' \
  "$score" "$killed" "$evaluated" "$survived" "$suspicious" "$timeout" "$skipped" "$no_tests"

if awk -v s="$score" -v th="$threshold" 'BEGIN{exit !(s+0 < th+0)}'; then
  printf '\nMUTATION SCORE %s%% < threshold %s%%\n' "$score" "$threshold" >&2
  printf 'survived/suspicious/timeout mutations:\n' >&2
  printf '%s\n' "$results" \
    | awk '/: (survived|suspicious|timeout)$/ {sub(/^[[:space:]]+/, ""); sub(/:.*/, ""); print}' \
    | while read -r id; do
        [ -n "$id" ] || continue
        printf '\n--- %s ---\n' "$id" >&2
        uv run --no-sync mutmut show "$id" 2>&1 >&2 || true
      done
  exit 1
fi

printf '\nmutation score %s%% >= threshold %s%%\n' "$score" "$threshold"
