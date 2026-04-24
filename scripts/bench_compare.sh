#!/usr/bin/env bash
# Compare a fresh JMH CSV against the checked-in golden and fail on regression.
#
# Usage: scripts/bench_compare.sh <new.csv> <golden.csv> [threshold_percent]
#
# The CSV schema (from `jmh -rf csv`) is:
#   "Benchmark","Mode","Threads","Samples","Score","Score Error (99.9%)","Unit","Param: maxParallel"
#
# For each (Benchmark, Param: maxParallel) row in the golden, we look up the
# matching row in the new CSV and check that its Score is not more than
# `threshold_percent`% above the golden Score. Lower is better (avgt ms/op).
#
# Regressions print to stderr and cause the script to exit non-zero.
set -euo pipefail
export LC_ALL=C

new="${1:?usage: $0 new.csv golden.csv [threshold_percent]}"
golden="${2:?usage: $0 new.csv golden.csv [threshold_percent]}"
threshold_pct="${3:-15}"

if [ ! -f "$new" ]; then
  echo "new CSV not found: $new" >&2; exit 2
fi
if [ ! -f "$golden" ]; then
  echo "golden CSV not found: $golden" >&2; exit 2
fi

awk -v new="$new" -v golden="$golden" -v thresh="$threshold_pct" '
  BEGIN {
    FS=","
    # Build golden map: key = Benchmark|Param, value = Score
    while ((getline line < golden) > 0) {
      sub(/\r$/, "", line)
      if (line ~ /^"Benchmark"/) continue
      n = split(line, f, ",")
      gsub(/"/, "", f[1])
      key = f[1] "|" f[n]
      gold[key] = f[5] + 0.0
    }
    close(golden)
    regressed = 0
    found = 0
    while ((getline line < new) > 0) {
      sub(/\r$/, "", line)
      if (line ~ /^"Benchmark"/) continue
      n = split(line, f, ",")
      gsub(/"/, "", f[1])
      key = f[1] "|" f[n]
      new_score = f[5] + 0.0
      if (!(key in gold)) {
        printf "warn: no golden row for %s (param=%s); skipping\n", f[1], f[n] > "/dev/stderr"
        continue
      }
      found++
      g = gold[key]
      pct = 100.0 * (new_score - g) / g
      marker = "ok"
      if (pct > thresh) {
        regressed++
        marker = "REGRESSION"
      }
      printf "%-10s %s param=%s: golden=%.3f new=%.3f delta=%+.2f%%\n", marker, f[1], f[n], g, new_score, pct
    }
    close(new)
    if (found == 0) {
      print "no rows compared — is the benchmark schema correct?" > "/dev/stderr"
      exit 2
    }
    if (regressed > 0) {
      printf "\n%d row(s) exceeded the %s%% regression threshold\n", regressed, thresh > "/dev/stderr"
      exit 1
    }
    printf "\nall %d row(s) within %s%% of golden\n", found, thresh
  }
'
