---
title: "Test quality"
description: "How the generated suite is held to a standard: coverage and a mutation gate"
---

Generated tests exist by construction, so the interesting question is not whether they are present
but whether they would catch a real bug. Two measures answer it.

## Coverage

The first is coverage: how much of the spec became a test at all. Most clauses do, but not all. A
tail of constructs the translator cannot yet turn into a runnable check, around a tenth of clauses on
the canonical fixtures, is skipped, and each skip is logged with its reason in
`tests/_testgen_skips.json` rather than silently dropped. A skip-rate probe in CI locks that
proportion, so a regression that widens the gap fails the build. The per-fixture coverage and the
skip file are on the [test-generation pipeline](/pipelines/test-generation) page.

## Mutation

Coverage says a clause has a test; it does not say the test is any good, since a test that asserts
nothing passes on everything. Mutation testing is the check that bites: introduce a deliberate fault
into the generated service, a dropped write, a wrong status code, an off-by-one in a count, a skipped
validation, and a sound suite should fail on it. The nightly gate runs
[mutmut](https://mutmut.readthedocs.io) over the generated `app/` for each canonical fixture and fails
if the kill rate falls below 90%. A surviving mutant is a real signal: either the spec did not
constrain the mutated behavior, or the generator did not turn that constraint into a test. A
complementary stryker4s gate does the same for the Scala generator modules themselves. The gate
mechanics, the matrix, the threshold rationale, and the in-process runner mode are on the
[mutation-testing pipeline](/pipelines/mutation-testing) page.

## What the suite trades

Generating from the spec buys two things a hand-written suite does not have. It costs nothing to
write and nothing to maintain, since a spec change regenerates it, and the property and stateful
layers explore far more inputs than example tests, with Hypothesis shrinking any failure to a minimal
counterexample. The costs are the mirror image: those runs are slower than fixed examples, the
generated test names read less clearly than a person's, and the suite tests only what the spec
states. That last one is the honest limit. The suite is exactly as complete as the spec, and an
unwritten requirement has no test.
