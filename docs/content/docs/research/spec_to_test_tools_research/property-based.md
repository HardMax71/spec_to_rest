---
title: "Property-based testing"
description: "Schemathesis, Hypothesis, QuickCheck, and property-based testing from specs"
---

## 1. Schemathesis

**What it is.** Open-source Python tool that performs automated property-based testing of REST
(OpenAPI) and GraphQL APIs. Built on top of the Hypothesis library.

### How it works

- Parses an OpenAPI/GraphQL schema and derives generators for every endpoint, parameter type,
  request body, header, etc.
- Generates thousands of random-but-schema-valid (and deliberately invalid) requests.
- Validates responses against the schema (status codes, response shapes, content types).
- Detects: 500 errors, schema violations, validation bypasses, integration failures.

### Testing modes

| Mode                | Description                                                                                                               |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Stateless           | Individual API calls validated against schema                                                                             |
| Stateful / Workflow | Multi-step sequences (create -> read -> update -> delete) using OpenAPI Links or inferred response-to-request connections |
| Fuzzing             | Random valid + invalid inputs to explore edge cases                                                                       |
| Coverage            | Measures which endpoints / schema components were exercised                                                               |

### Stateful testing detail

- Schemathesis builds a state machine from API operations.
- It discovers connections by analyzing the OpenAPI schema (Open API Links or parameter name
  matching against response fields).
- At runtime it can also learn connections dynamically from `Location` headers.
- Python API: `schema.as_state_machine()` creates a test class for pytest.
- CLI: stateful phase runs automatically with `schemathesis run`.

**How specs connect to tests.** The OpenAPI spec _is_ the test specification. Every schema
constraint becomes a property that the tool checks. No separate test writing needed, the spec is
the single source of truth.

### Maturity

- ~3,200 GitHub stars, 437+ releases (v4.15.0 as of April 2026)
- Adopted by Spotify, WordPress, JetBrains, Red Hat
- MIT licensed, actively maintained
- 789 dependent projects on PyPI

### Key sources

- https://github.com/schemathesis/schemathesis
- https://schemathesis.readthedocs.io/en/stable/explanations/stateful/
- https://schemathesis.io/

## 2. Hypothesis

**What it is.** Python property-based testing library (the most widely used PBT library in the
Python ecosystem). Its `stateful` module provides rule-based state machine testing.

### How stateful testing works

Users subclass `RuleBasedStateMachine` and define:

| Component       | Purpose                                                                         |
| --------------- | ------------------------------------------------------------------------------- |
| `@initialize`   | Set up initial state; runs once at the start of each test case                  |
| `@rule`         | Define an operation; takes strategies as arguments; can push results to Bundles |
| `@invariant`    | A check that must hold after every rule execution                               |
| `@precondition` | Guard that restricts when a rule can fire                                       |
| `Bundle`        | Named collection of values produced by rules, consumed by later rules           |

**Key mechanism, Bundles.** Bundles allow data flow between rules. A rule can `target=some_bundle`
to push a return value, and another rule can draw from `some_bundle` as an argument. This creates
producer-consumer chains automatically.

### Example pattern

```python
class DatabaseMachine(RuleBasedStateMachine):
    keys = Bundle("keys")

    @rule(target=keys, k=text(), v=integers())
    def create(self, k, v):
        db.put(k, v)
        return k

    @rule(k=keys)
    def read(self, k):
        result = db.get(k)
        assert result is not None

    @invariant()
    def size_non_negative(self):
        assert db.size() >= 0
```

**How specs connect to tests.** The state machine _is_ the executable specification. Rules define
the allowed operations, preconditions constrain when they can fire, invariants encode properties
that must always hold. Hypothesis generates random sequences of rule applications and shrinks
failures to minimal counterexamples.

**Connection to model-based testing.** Hypothesis's stateful testing is essentially model-based
testing (the docs acknowledge this). The "model" is the state machine; the "system under test" is
whatever the rules operate on. The framework was inspired by Erlang QuickCheck's `eqc_statem`.

### Maturity

- Core Hypothesis library: 7,800+ GitHub stars, extremely widely adopted
- Stateful module: stable, well-documented, used in production at many companies
- Part of standard Python testing toolchain

### Key sources

- https://hypothesis.readthedocs.io/en/latest/stateful.html
- https://hypothesis.works/articles/rule-based-stateful-testing/

## 4. QuickCheck state machine testing

**What it is.** Property-based testing with explicit state machine models. Originated in Erlang's
commercial QuickCheck (Quviq), now available in multiple ecosystems.

### 4a. erlang QuickCheck (eqc_statem)

**How it works.** Users define a state machine model with:

| Callback          | Purpose                                             |
| ----------------- | --------------------------------------------------- |
| `command/1`       | Generate a random command given current model state |
| `initial_state/0` | Return the initial abstract model state             |
| `next_state/3`    | Compute new model state after a command             |
| `precondition/2`  | Guard: is this command valid in current state?      |
| `postcondition/3` | Oracle: does the real result match expectations?    |

QuickCheck generates random sequences of commands, executes them against the real system, and checks
all postconditions. On failure, it shrinks to a minimal counterexample.

**Race condition testing.** The parallel property runs command sequences concurrently and checks if
results can be explained by _some_ sequential interleaving. If not, a race condition is reported.
This comes "for free" from the state machine model.

#### Related tools

- PropEr (open-source Erlang): eqc-inspired, has `proper_statem`
- Makina (Elixir): DSL that compiles to QuickCheck state machines via macros; improves
  maintainability and reuse; encourages typed specifications

### 4b. quickcheck-state-machine (haskell)

**How it works.** Same conceptual model as Erlang's eqc_statem, adapted for Haskell:

1. Define a datatype of possible actions
2. Provide a model (abstract state), pre/postconditions, state transitions
3. Framework generates, executes, and shrinks action sequences

**Parallel testing.** Generates a sequential prefix + concurrent suffixes. If no valid sequential
interleaving explains the parallel results, reports a race condition.

**Adoption.** Used by IOHK (Cardano blockchain), Wire (messaging), and others testing consensus
algorithms and distributed systems.

**How specs connect to tests.** The state machine model _is_ the specification. It defines what
operations are valid, how state evolves, and what results are expected. The framework generates
tests by exploring this model.

#### Maturity

- Erlang QuickCheck (Quviq): commercial, very mature, used at Ericsson, Volvo, etc.
- PropEr: open-source, mature, actively maintained
- quickcheck-state-machine (Haskell): mature, used in production
- Makina (Elixir): newer (2021), research + practice

#### Key sources

- https://www.quviq.com/documentation/eqc/overview-summary.html
- https://icfp21.sigplan.org/details/erlang-2021-papers/4/Makina-A-New-QuickCheck-State-Machine-Library
- https://github.com/stevana/quickcheck-state-machine

## 8. Property-based testing from formal specifications

**Core idea.** Instead of writing individual test cases, express system behavior as formal
properties (universally quantified statements). A PBT framework generates random inputs and verifies
the properties hold.

### How properties derive from specifications

| Specification Element      | Property Type        | Example                                |
| -------------------------- | -------------------- | -------------------------------------- |
| Type constraint            | Type-level property  | "output is always a positive integer"  |
| Invariant                  | State invariant      | "balance never goes negative"          |
| Precondition/postcondition | Hoare-style property | "if input sorted, output sorted"       |
| Algebraic law              | Round-trip property  | "decode(encode(x)) == x"               |
| State machine              | Behavioral property  | "after create, get returns the item"   |
| Protocol rule              | Sequence property    | "handshake must precede data transfer" |

### The spec-to-test pipeline (as described by kiro/AWS)

1. Write acceptance criteria / requirements in natural language.
2. Extract universally quantified properties ("for any valid input X, property P holds").
3. Implement properties as PBT tests using Hypothesis (Python), QuickCheck (Haskell/Erlang),
   fast-check (JS/TS), etc.
4. Framework generates random inputs, checks properties, shrinks counterexamples.

**Key insight.** The properties _are_ "another representation of (parts of) your specification",
maintaining traceability between what stakeholders need and what tests validate.

### Industrial adoption

- Amazon (formal specs + PBT for S3, DynamoDB invariants)
- Volvo (QuickCheck for automotive protocols)
- Stripe (property-based testing of financial logic)
- Ericsson (QuickCheck for telecom protocols)

### Key sources

- https://kiro.dev/blog/property-based-testing/
- https://link.springer.com/chapter/10.1007/978-3-642-17071-3_13
- https://link.springer.com/article/10.1007/s10270-017-0647-0
- https://dl.acm.org/doi/pdf/10.1145/263244.263267
