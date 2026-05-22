#!/usr/bin/env node
// Auto-generated conformance test runner. Do not edit by hand.
//
// Runs the conformance suite (structural + behavioral + stateful test files)
// against an already-running service via vitest, forwarding the profile. The
// caller is responsible for bringing the service up with ENABLE_TEST_ADMIN=1.
//
// Usage: node tests/run_conformance.mjs [smoke|thorough|exhaustive]
// Profile precedence: argv[2] > SPEC_TEST_PROFILE > "thorough".
// Exit codes: 0 all passed | 1 test failures | 2 invalid profile / runner error.

import { spawnSync } from "node:child_process";

const PROFILES = ["smoke", "thorough", "exhaustive"];
const profile = process.argv[2] ?? process.env.SPEC_TEST_PROFILE ?? "thorough";

if (!PROFILES.includes(profile)) {
  console.error(`invalid profile '${profile}'; expected one of ${PROFILES.join(", ")}`);
  process.exit(2);
}

const baseUrl = process.env.SPEC_TEST_BASE_URL ?? "http://localhost:8080";
console.log(`Running conformance tests against ${baseUrl} (profile: ${profile})`);

const res = spawnSync("npx", ["vitest", "run"], {
  stdio: "inherit",
  env: { ...process.env, SPEC_TEST_PROFILE: profile, SPEC_TEST_BASE_URL: baseUrl },
});

if (res.error) {
  console.error(`runner error: ${String(res.error)}`);
  process.exit(2);
}
process.exit(res.status ?? 1);
