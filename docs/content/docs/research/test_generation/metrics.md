---
title: "Test quality"
description: "How the generated tests are held to a standard"
---

## 9. Test quality metrics

### 9.1 Spec coverage

**Definition.** The percentage of spec elements that have at least one corresponding test.

```text
spec_coverage = (tested_elements / total_elements) * 100
```

The compiler can compute this statically by counting how many spec elements appear in the generated
test files:

| Spec Element Type        | Count Method                                                 |
| ------------------------ | ------------------------------------------------------------ |
| `ensures` clauses        | Count clauses; each should have >= 1 `@given` test           |
| `requires` clauses       | Count clauses; each should have >= 1 negative test           |
| `invariant` declarations | Count declarations; each should have >= 1 `@invariant`       |
| Entity invariants        | Count invariants; each should appear in strategy constraints |
| State declarations       | Count fields; each should appear as SM model attribute       |
| Operations               | Count operations; each should be an SM `@rule`               |

**Target:** 100% spec coverage. Since all tests are generated from the spec, this should be achieved
by construction. The metric is a self-check on the compiler.

### 9.2 Mutation testing

**Goal.** Verify that the generated tests would catch real bugs in the implementation.

**Approach.** Introduce controlled mutations into the generated service code and verify that at
least one test fails for each mutation.

| Mutation Type                       | What It Simulates            | Test That Should Catch It              |
| ----------------------------------- | ---------------------------- | -------------------------------------- |
| Remove a `store[key] = value` write | Forgetting to persist data   | `test_*_stores_correct_*`              |
| Return wrong status code            | Incorrect HTTP mapping       | Schemathesis structural test           |
| Skip input validation               | Missing requires enforcement | `test_*_rejects_invalid_*`             |
| Off-by-one in count                 | Incorrect cardinality        | `test_*_adds_exactly_one`              |
| Allow invalid state transition      | Missing precondition check   | SM negative rule test                  |
| Fail to restore inventory on cancel | Incomplete rollback          | `test_cancel_order_restores_inventory` |
| Return wrong field value            | Incorrect output mapping     | `test_*_returns_correct_*`             |

**Mutation testing tools:** `mutmut` (Python) or `cosmic-ray` (Python) can be configured to run
against the generated service code with the generated test suite as the test oracle.

```bash
# Example: run mutation testing with mutmut
mutmut run \
    --paths-to-mutate=app/ \
    --tests-dir=tests/ \
    --runner="pytest tests/test_behavioral_url_shortener.py -x --tb=no -q"
```

**Target metric.** Mutation score >= 90%. A mutation that is not caught indicates either a gap in
the spec (the spec does not constrain the mutated behavior) or a gap in the test generator.

### 9.3 Test effectiveness comparison

To compare the generated test suite against hand-written alternatives:

| Metric              | How to Measure                             | What It Tells You             |
| ------------------- | ------------------------------------------ | ----------------------------- |
| Bug detection rate  | Inject known bugs, measure detection %     | How many bugs the tests catch |
| Time to write       | Wall-clock time: hand-written vs generated | Developer productivity gain   |
| Maintenance cost    | Lines changed when spec changes            | Cost of keeping tests current |
| False positive rate | Tests that fail on correct code            | Noise level                   |
| Shrinking quality   | Size of minimal counterexample             | Debuggability of failures     |

#### Expected advantages of generated tests

1. Zero time to write, tests are generated from the spec.
2. Zero maintenance when spec changes, regenerate tests from updated spec.
3. No missed spec elements, 100% spec coverage by construction.
4. Better shrinking, Hypothesis's shrinking finds minimal counterexamples.
5. Broader input coverage, property tests explore more inputs than example tests.

#### Expected disadvantages

1. Slower execution, property tests run many examples per test function.
2. Harder to debug, generated test names are less intuitive than hand-written ones.
3. Cannot test unstated requirements, only tests what the spec declares.
4. Requires a running service, integration tests, rather than unit tests.
