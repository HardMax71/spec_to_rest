---
title: "How it compares"
description: "The test generator against manual, snapshot, and contract testing"
---

## 10. Comparison with existing testing approaches

### 10.1 Comparison matrix

| Approach                | Structural         | Behavioral             | Stateful             | From Formal Spec       | Auto-Generated          | Shrinking |
| ----------------------- | ------------------ | ---------------------- | -------------------- | ---------------------- | ----------------------- | --------- |
| **Our approach**        | Yes (Schemathesis) | Yes (Hypothesis props) | Yes (Hypothesis SM)  | Yes                    | Yes                     | Yes       |
| **Schemathesis alone**  | Yes                | No                     | Partial (links only) | From OpenAPI only      | Yes                     | Partial   |
| **RESTler**             | Partial            | No                     | Yes (fuzzing)        | From OpenAPI only      | Yes                     | No        |
| **EvoMaster**           | Yes                | No                     | Yes (evolutionary)   | From OpenAPI only      | Yes                     | No        |
| **Dredd**               | Yes                | No                     | No                   | From OpenAPI/Blueprint | Yes                     | No        |
| **Pact**                | No                 | Partial (contracts)    | No                   | No (consumer-driven)   | Partial                 | No        |
| **QuickCheck SM**       | No                 | Yes                    | Yes                  | From model             | No (hand-written model) | Yes       |
| **Spec Explorer**       | No                 | Yes                    | Yes                  | From C# model          | Yes                     | No        |
| **Hand-written pytest** | Depends            | Depends                | Depends              | No                     | No                      | No        |

### 10.2 What each approach misses

#### Schemathesis alone

- Checks structural conformance (schemas, status codes, content types)
- Cannot verify behavioral postconditions (e.g., "the returned code was fresh")
- Stateful testing is limited to OpenAPI Links (data flow only, no model comparison)
- No invariant checking beyond schema validation
- What we add: behavioral property tests that check ensures clauses, stateful tests that
  maintain a model and compare it against the service, invariant checks after every operation

#### Restler (microsoft research)

- Excellent at finding security bugs (500 errors, resource leaks)
- Infers producer-consumer dependencies from OpenAPI
- Does _not_ check postconditions or invariants
- Fuzzing is unguided by a behavioral specification
- What we add: spec-guided testing that checks not just "does it crash?" but "does it satisfy
  the postconditions?" and "do invariants hold?"

#### Evomaster

- Evolutionary search for inputs that maximize code coverage
- White-box: instruments the service to guide search
- Good at achieving high line/branch coverage
- Does _not_ check behavioral correctness (only crashes and 500s)
- What we add: an oracle. EvoMaster finds inputs; we check outputs against the spec. The two
  approaches are complementary.

#### Dredd (archived)

- One request per documented endpoint, check response matches schema
- No randomization, no edge cases, no stateful sequences
- What we add: everything beyond "does the happy path return the documented shape?"

#### Pact (consumer-driven contracts)

- Verifies that a provider satisfies consumer expectations
- Consumer writes the contract, rather than the spec author
- Does not test internal invariants or state transitions
- What we add: provider-side behavioral verification derived from the authoritative
  specification, rather than from consumer expectations

#### QuickCheck state machine testing (erlang/haskell)

- The closest conceptual match to our stateful testing layer
- Requires manually writing the state machine model in Erlang/Haskell
- Excellent at finding bugs in stateful systems (used at Volvo, Ericsson)
- No HTTP/REST awareness, no structural testing
- What we add: automatic generation of the state machine model from the spec, HTTP client
  integration, structural testing via Schemathesis, entity-level strategy generation

#### Spec explorer (microsoft)

- Model-based testing from C# model programs
- Explored state graphs, generated covering test suites
- Saved 50 person-years at Microsoft
- Visual Studio-only, rather than maintained, no REST awareness
- What we add: REST-native, Python ecosystem, alive, spec-driven rather than code-driven

### 10.3 What our approach cannot do (honest limitations)

| Limitation                             | Why                                                                           | Mitigation                                                                                                   |
| -------------------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Cannot test unstated requirements      | Tests derive from spec; if the spec omits a requirement, no test is generated | Supplement with hand-written tests for domain-specific edge cases                                            |
| Cannot test performance/latency        | Spec does not declare performance requirements                                | Separate performance testing (e.g., Locust, k6)                                                              |
| Cannot test concurrent race conditions | Hypothesis SM is sequential by default                                        | Use Hypothesis's `@given` with `st.runner()` for limited parallelism; use TLA+ for full concurrency analysis |
| Cannot test UI/frontend                | Spec covers API behavior only                                                 | Separate frontend testing                                                                                    |
| Cannot test third-party integrations   | Spec describes the service's own behavior                                     | Mock external dependencies in test fixtures                                                                  |
| Slower than unit tests                 | Integration tests require a running service                                   | Use `smoke` profile for fast feedback, `exhaustive` for release gates                                        |

### 10.4 Positioning on the testing spectrum

```text
                    Structural                    Behavioral
                    conformance                   conformance
                    |                             |
  Dredd ---------->|                              |
  Schemathesis --->|------->                      |
  RESTler -------->|----------->                  |
  EvoMaster ------>|--------------->              |
  Pact ----------->|                  |           |
  QuickCheck SM    |                  |---------->|
  Spec Explorer    |                  |---------->|
  Our approach --->|--------------------------------->|
                   ^                                  ^
                   Least                              Most
                   coverage                           coverage
```

Our approach is the only one that covers the full spectrum from structural conformance (does the API
match its schema?) through behavioral conformance (does each operation satisfy its postconditions?)
to stateful conformance (do invariants hold across arbitrary operation sequences?).
