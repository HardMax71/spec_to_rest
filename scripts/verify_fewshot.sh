#!/usr/bin/env bash
# Verify every few-shot Dafny example shown to the synthesis LLM with `dafny verify`.
# Usage: scripts/verify_fewshot.sh [dafny-bin]   (defaults to $DAFNY_BIN, else `dafny` on PATH)
set -euo pipefail

dafny_bin="${1:-${DAFNY_BIN:-dafny}}"
dir="modules/synth/src/main/resources/specrest/synth/few-shot"

shopt -s nullglob
files=("$dir"/*.dfy)
(( ${#files[@]} > 0 )) || { echo "no few-shot .dfy files under $dir" >&2; exit 1; }

rc=0
for f in "${files[@]}"; do
  if out="$("$dafny_bin" verify "$f" 2>&1)"; then
    echo "ok   $(basename "$f")"
  else
    echo "FAIL $(basename "$f")"
    echo "$out"
    rc=1
  fi
done
exit "$rc"
