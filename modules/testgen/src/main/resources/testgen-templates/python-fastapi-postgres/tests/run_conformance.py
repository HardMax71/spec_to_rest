"""Auto-generated conformance test runner. Do not edit by hand.

Runs the three test layers (structural, behavioral, stateful) sequentially against
an already-running service, emitting JUnit XML per phase and aggregating an exit
code. The Makefile target `test-conformance-docker` is responsible for bringing
the service up and tearing it down; this script only owns the phase orchestration.

Usage:
    python tests/run_conformance.py [smoke|thorough|exhaustive]

Profile selection (highest priority first):
    1. argv[1] if present
    2. SPEC_TEST_PROFILE environment variable
    3. "thorough" (default)

Environment:
    SPEC_TEST_BASE_URL  base URL for the SUT (default: http://localhost:8000)
    SPEC_TEST_PROFILE   used when argv[1] is not given; forwarded to each phase

Exit codes:
    0   all phases passed
    1   one or more pytest phases reported test failures
    2   service unreachable / admin router disabled / invalid profile
"""
import glob
import os
import subprocess
import sys
from enum import Enum
from pathlib import Path

import httpx

PROFILES = ("smoke", "thorough", "exhaustive")
DEFAULT_PROFILE = "thorough"

BASE_URL = os.environ.get("SPEC_TEST_BASE_URL", "http://localhost:8000")
RESULTS_DIR = Path("results")

PHASES = (
    ("structural", "tests/test_structural_*.py", "--tb=short"),
    ("behavioral", "tests/test_behavioral_*.py", "--tb=short"),
    ("stateful",   "tests/test_stateful_*.py",   "--tb=long"),
)


class Outcome(Enum):
    PASS = "PASS"
    FAIL = "FAIL"
    INFRA = "INFRA"


def select_profile() -> str:
    if len(sys.argv) > 1:
        candidate = sys.argv[1]
    else:
        candidate = os.environ.get("SPEC_TEST_PROFILE", DEFAULT_PROFILE)
    if candidate not in PROFILES:
        allowed = ", ".join(PROFILES)
        sys.stderr.write(
            f"ERROR: invalid profile {candidate!r}; expected one of: {allowed}\n"
        )
        sys.exit(2)
    return candidate


def reset_state() -> bool:
    try:
        r = httpx.post(f"{BASE_URL}/__test_admin__/reset", timeout=5.0)
    except httpx.HTTPError as e:
        sys.stderr.write(f"ERROR: service unreachable at {BASE_URL}: {e}\n")
        return False
    if r.status_code == 403:
        sys.stderr.write(
            "ERROR: /__test_admin__/reset returned 403; "
            "start the service with ENABLE_TEST_ADMIN=1\n"
        )
        return False
    if r.status_code != 204:
        sys.stderr.write(
            f"ERROR: /__test_admin__/reset returned {r.status_code}; "
            f"expected 204\n"
        )
        return False
    return True


def run_phase(name: str, pattern: str, tb_flag: str, profile: str) -> Outcome:
    print(f"\n{'=' * 60}\nPHASE: {name} ({profile})\n{'=' * 60}\n", flush=True)
    matches = sorted(glob.glob(pattern))
    if not matches:
        print(f"{name}: SKIPPED (no files match {pattern!r})", flush=True)
        return Outcome.PASS
    if not reset_state():
        return Outcome.INFRA
    junit = RESULTS_DIR / f"{name}-{profile}.xml"
    cmd = [
        sys.executable, "-m", "pytest",
        "-v", tb_flag,
        f"--junitxml={junit}",
        *matches,
    ]
    env = {**os.environ, "SPEC_TEST_PROFILE": profile, "SPEC_TEST_BASE_URL": BASE_URL}
    result = subprocess.run(cmd, env=env)
    outcome = Outcome.PASS if result.returncode == 0 else Outcome.FAIL
    print(f"\n{name}: {outcome.value} (junit: {junit})", flush=True)
    return outcome


def main() -> int:
    profile = select_profile()
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Running conformance tests against {BASE_URL} (profile: {profile})")

    results: dict[str, Outcome] = {}
    for name, pattern, tb_flag in PHASES:
        results[name] = run_phase(name, pattern, tb_flag, profile)

    print(f"\n{'=' * 60}\nCONFORMANCE TEST SUMMARY\n{'=' * 60}")
    for phase, outcome in results.items():
        print(f"  {phase:12s} {outcome.value}")
    has_infra = any(o is Outcome.INFRA for o in results.values())
    has_fail = any(o is Outcome.FAIL for o in results.values())
    if has_infra:
        print("\nOverall: INFRA FAILURE")
        return 2
    if has_fail:
        print("\nOverall: FAILURES DETECTED")
        return 1
    print("\nOverall: ALL PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
