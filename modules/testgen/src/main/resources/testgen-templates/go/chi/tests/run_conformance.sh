#!/usr/bin/env bash
# Auto-generated conformance test runner. Do not edit by hand.
#
# Runs the conformance suite (structural + behavioral + stateful) against an
# already-running service via `go test`, forwarding the profile. The caller is
# responsible for bringing the service up with ENABLE_TEST_ADMIN=1 and a
# binary built with `-tags conformance`.
#
# Usage: bash tests/run_conformance.sh [smoke|thorough|exhaustive]
# Profile precedence: $1 > SPEC_TEST_PROFILE > "thorough".
# Exit codes: 0 all passed | 1 test failures | 2 invalid profile / runner error.
set -euo pipefail

profile="${1:-${SPEC_TEST_PROFILE:-thorough}}"
case "$profile" in
  smoke | thorough | exhaustive) ;;
  *)
    echo "invalid profile '$profile'; expected one of smoke, thorough, exhaustive" >&2
    exit 2
    ;;
esac

base="${SPEC_TEST_BASE_URL:-http://localhost:8080}"
echo "Running conformance tests against ${base} (profile: ${profile})"

SPEC_TEST_PROFILE="$profile" SPEC_TEST_BASE_URL="$base" \
  exec go test -tags conformance -count=1 ./tests/...
