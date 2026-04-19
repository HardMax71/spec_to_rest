#!/usr/bin/env bash
set -euo pipefail

# Generate golden fixtures from the TS build for the Scala migration.
# Run on TS main (tagged v0.9-ts-final). Do NOT run on the migration branch
# after cutover — the TS sources are gone.
#
# Output layout:
#   fixtures/spec/<name>.spec              # flat copy of every .spec fixture
#   fixtures/golden/ir/<name>.json         # inspect --format json
#   fixtures/golden/smt/<name>.smt2        # verify --dump-smt
#   fixtures/golden/ir-errors/<name>.txt   # stderr for specs that fail to parse/build
#   fixtures/golden/smt-errors/<name>.txt  # stderr for specs that fail to translate

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

spec_dir="fixtures/spec"
ir_dir="fixtures/golden/ir"
smt_dir="fixtures/golden/smt"
ir_err_dir="fixtures/golden/ir-errors"
smt_err_dir="fixtures/golden/smt-errors"

mkdir -p "$spec_dir" "$ir_dir" "$smt_dir" "$ir_err_dir" "$smt_err_dir"

# Copy every .spec into a flat fixtures/spec/ directory (basename as filename).
find test -name '*.spec' -type f -print0 | while IFS= read -r -d '' src; do
  cp "$src" "$spec_dir/$(basename "$src")"
done

count=0
for spec in "$spec_dir"/*.spec; do
  name="$(basename "$spec" .spec)"
  count=$((count + 1))

  # IR
  if out="$(npx tsx src/cli.ts inspect --format json "$spec" 2>/tmp/ir-err)"; then
    printf '%s' "$out" > "$ir_dir/$name.json"
    rm -f "$ir_err_dir/$name.txt"
  else
    cp /tmp/ir-err "$ir_err_dir/$name.txt"
    rm -f "$ir_dir/$name.json"
  fi

  # SMT-LIB
  if out="$(npx tsx src/cli.ts verify --dump-smt "$spec" 2>/tmp/smt-err)"; then
    printf '%s' "$out" > "$smt_dir/$name.smt2"
    rm -f "$smt_err_dir/$name.txt"
  else
    cp /tmp/smt-err "$smt_err_dir/$name.txt"
    rm -f "$smt_dir/$name.smt2"
  fi

  echo "[$count] $name"
done

echo ""
echo "Goldens written:"
echo "  IR (success):    $(ls "$ir_dir" 2>/dev/null | wc -l) files"
echo "  IR (error):      $(ls "$ir_err_dir" 2>/dev/null | wc -l) files"
echo "  SMT (success):   $(ls "$smt_dir" 2>/dev/null | wc -l) files"
echo "  SMT (error):     $(ls "$smt_err_dir" 2>/dev/null | wc -l) files"
