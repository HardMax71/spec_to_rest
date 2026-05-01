#!/usr/bin/env bash
set -euo pipefail

status_doc="docs/content/docs/research/12_global_proof_status.md"
governance_doc="docs/content/docs/research/11_global_proof_governance.md"

proof_governed_surfaces=(
  "modules/ir/src/main/scala/specrest/ir/Types.scala"
  "modules/parser/src/main/scala/specrest/parser/Parse.scala"
  "modules/parser/src/main/scala/specrest/parser/Builder.scala"
  "modules/verify/src/main/scala/specrest/verify/Classifier.scala"
  "modules/verify/src/main/scala/specrest/verify/Consistency.scala"
  "modules/verify/src/main/scala/specrest/verify/z3/Backend.scala"
  "modules/verify/src/main/scala/specrest/verify/z3/Translator.scala"
  "modules/verify/src/main/scala/specrest/verify/z3/Types.scala"
)

mapfile -t changed_files < <(
  if [[ -n "${GITHUB_BASE_REF:-}" ]] && git rev-parse --verify -q "origin/${GITHUB_BASE_REF}" >/dev/null
  then
    diff_base="$(git merge-base HEAD "origin/${GITHUB_BASE_REF}")"
    git diff --name-only "${diff_base}...HEAD"
  elif git rev-parse --verify -q HEAD^ >/dev/null
  then
    diff_base="$(git rev-parse HEAD^)"
    git diff --name-only "${diff_base}...HEAD"
  else
    git diff --name-only HEAD
  fi
)

if [[ ${#changed_files[@]} -eq 0 ]]
then
  echo "No changed files detected."
  exit 0
fi

impacted_surfaces=()
for changed in "${changed_files[@]}"
do
  for surface in "${proof_governed_surfaces[@]}"
  do
    if [[ "${changed}" == "${surface}" ]]
    then
      impacted_surfaces+=("${changed}")
      break
    fi
  done
done

if [[ ${#impacted_surfaces[@]} -eq 0 ]]
then
  echo "No proof-governed surfaces changed."
  exit 0
fi

status_updated=0
governance_updated=0
for changed in "${changed_files[@]}"
do
  if [[ "${changed}" == "${status_doc}" ]]
  then
    status_updated=1
  fi
  if [[ "${changed}" == "${governance_doc}" ]]
  then
    governance_updated=1
  fi
done

if [[ ${status_updated} -eq 0 ]]
then
  echo "Proof-governed surfaces changed:"
  printf '  - %s\n' "${impacted_surfaces[@]}"
  echo "Update ${status_doc} in the same PR before merge."
  exit 1
fi

echo "Proof-governed surfaces changed and ${status_doc} was updated."
printf '  - %s\n' "${impacted_surfaces[@]}"

if [[ ${governance_updated} -eq 0 ]]
then
  echo "If the theorem boundary, trusted computing base, or governed-surface set changed,"
  echo "also update ${governance_doc} or the governing issue."
fi
