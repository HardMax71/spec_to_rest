#!/bin/bash
# isabelle_trace.sh — full per-command timing trace of the SpecRest session.
#
# Usage:
#   ./scripts/isabelle_trace.sh              # build, then report
#   ./scripts/isabelle_trace.sh --no-build   # report from last build DB
#
# Background:
# Isabelle's session DB stores per-command timing for every command whose
# elapsed time meets `build_timing_threshold` (default 0.1 s). The
# `command_timings` BLOB is zstd-compressed YXML; this script extracts and
# pretty-prints it as a flat report sorted by per-file cumulated cost +
# top-N slowest commands with file:line locations.
#
# Knobs:
#   THRESHOLD=N   override timing threshold (seconds)
#   TOP=N         number of slowest single commands to print (default 20)
#   SESSION=name  override session name (default SpecRest)

set -euo pipefail
cd "$(dirname "$0")/.."

THRESHOLD="${THRESHOLD:-0.1}"
TOP="${TOP:-20}"
SESSION="${SESSION:-SpecRest}"
NO_BUILD="${1:-}"

if [ "$NO_BUILD" != "--no-build" ]; then
  echo "==> isabelle build -c (threshold=${THRESHOLD}s)…"
  isabelle build -c -d proofs/isabelle/SpecRest -v \
    -o "build_timing_threshold=${THRESHOLD}" \
    "$SESSION" > /tmp/isabelle_trace_build.log 2>&1
  grep -E "Timing|Finished" /tmp/isabelle_trace_build.log | tail -3
fi

DB="$HOME/.isabelle/Isabelle2025-2/heaps/polyml-5.9.2_x86_64_32-linux/log/${SESSION}.db"
[ -f "$DB" ] || { echo "ERROR: DB not found at $DB" >&2; exit 1; }

BLOB="/tmp/isabelle_trace_cmd.blob"
YXML="/tmp/isabelle_trace_cmd.yxml"
sqlite3 "$DB" \
  "SELECT writefile('${BLOB}', command_timings) FROM isabelle_session_info LIMIT 1" \
  > /dev/null
zstd -dq -f "$BLOB" -o "$YXML"

python3 - "$YXML" "$TOP" <<'PY'
import sys, os
from collections import defaultdict

yxml_path, top_n = sys.argv[1], int(sys.argv[2])
with open(yxml_path, 'rb') as f:
    data = f.read().decode('utf-8', errors='replace')

entries = []
for chunk in data.split('\x05'):
    if not chunk:
        continue
    fields = {}
    for kv in chunk.split('\x06'):
        if '=' in kv:
            k, v = kv.split('=', 1)
            fields[k] = v
    if 'elapsed' in fields and 'file' in fields:
        try:
            entries.append((
                float(fields['elapsed']),
                fields.get('name', '?'),
                fields.get('file', '?'),
                int(fields.get('offset', '0'))
            ))
        except ValueError:
            pass

# Map byte-offset → line number (cache per file)
_cache = {}
def offset_to_line(path: str, offset: int) -> int:
    expanded = path.replace('~~', os.environ.get('ISABELLE_HOME', '/opt/Isabelle2025-2')) \
                   .replace('~/', os.environ.get('HOME', '') + '/')
    try:
        if expanded not in _cache:
            with open(expanded, 'rb') as f:
                _cache[expanded] = f.read()
        return _cache[expanded][:offset].count(b'\n') + 1
    except (FileNotFoundError, KeyError):
        return -1

# Filter to project files (exclude HOL-Library imports for the per-file view)
project = [e for e in entries if '/proofs/isabelle/' in e[2]]

print(f"\nTotal commands ≥{0.1}s: {len(entries)}  (in this project: {len(project)})\n")
print(f"=== Top {top_n} slowest commands ===")
for elapsed, name, path, offset in sorted(entries, key=lambda x: -x[0])[:top_n]:
    line = offset_to_line(path, offset)
    file = path.split('/')[-1]
    where = f"{file}:{line}" if line > 0 else f"{file}:?  (off={offset})"
    print(f"  {elapsed:7.2f}s  {name:14s}  {where}")

# Per-file cumulated
by_file = defaultdict(lambda: [0.0, 0])
for elapsed, name, path, offset in entries:
    by_file[path.split('/')[-1]][0] += elapsed
    by_file[path.split('/')[-1]][1] += 1

print(f"\n=== Cumulated by file (descending) ===")
for f, (total, count) in sorted(by_file.items(), key=lambda x: -x[1][0]):
    print(f"  {total:7.2f}s ({count:5d} commands)  {f}")
PY
