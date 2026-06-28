---
title: "Property-based testing"
description: "Schemathesis, Hypothesis, QuickCheck, and property-based testing from specs"
---

## Schemathesis

[Schemathesis](https://schemathesis.io/) is an open-source Python tool
([repo](https://github.com/schemathesis/schemathesis)) that runs property-based testing against REST
(OpenAPI) and GraphQL APIs, built on Hypothesis. It parses the schema, derives a generator for every
endpoint, parameter, body, and header, throws thousands of schema-valid and deliberately invalid
requests at the service, and validates each response against the schema (status codes, shapes,
content types), surfacing 500s, schema violations, validation bypasses, and integration failures. It
runs in four modes:

| Mode                | Description                                                                           |
| ------------------- | ------------------------------------------------------------------------------------- |
| Stateless           | individual API calls validated against the schema                                     |
| Stateful / workflow | multi-step sequences (create, read, update, delete) via OpenAPI Links or inferred links |
| Fuzzing             | random valid and invalid inputs to explore edge cases                                 |
| Coverage            | measures which endpoints and schema components were exercised                         |

Its [stateful phase](https://schemathesis.readthedocs.io/en/stable/explanations/stateful/) builds a
state machine from the operations, discovering connections from OpenAPI Links or by matching parameter
names against response fields, and learning more at runtime from `Location` headers;
`schema.as_state_machine()` produces a pytest class and `schemathesis run` exercises it automatically.
The point is that the OpenAPI spec is the test specification: every schema constraint becomes a checked
property, with no separate test-writing. It is mature, around 3,200 stars and 437-plus releases
(v4.15.0, April 2026), MIT-licensed, used by Spotify, WordPress, JetBrains, and Red Hat, with 789
dependents on PyPI.

## Hypothesis

[Hypothesis](https://hypothesis.readthedocs.io/en/latest/stateful.html) is the most widely used
property-based testing library in Python, and its `stateful` module does rule-based state-machine
testing. You subclass `RuleBasedStateMachine` and declare:

| Component       | Purpose                                                                          |
| --------------- | -------------------------------------------------------------------------------- |
| `@initialize`   | set up initial state; runs once at the start of each test case                   |
| `@rule`         | define an operation; takes strategies as arguments; can push results to Bundles  |
| `@invariant`    | a check that must hold after every rule execution                                |
| `@precondition` | a guard that restricts when a rule can fire                                      |
| `Bundle`        | a named collection of values produced by rules and consumed by later rules       |

The mechanism that makes it work is Bundles: a rule can `target` a bundle to push a return value, and
another rule draws from that bundle as an argument, so producer-consumer chains form on their own.

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

The state machine is the executable specification: rules are the allowed operations, preconditions
constrain when they fire, invariants encode what must always hold, and Hypothesis generates random
rule sequences and shrinks any failure to a minimal counterexample. This is
[model-based testing in all but name](https://hypothesis.works/articles/rule-based-stateful-testing/),
with the state machine as the model, and it was inspired by Erlang QuickCheck's `eqc_statem`. The core
library has 7,800-plus stars and is part of the standard Python testing toolchain.

## QuickCheck state machines

Property-based testing with explicit state-machine models began in Erlang's commercial
[QuickCheck (Quviq)](https://www.quviq.com/documentation/eqc/overview-summary.html) and spread from
there. The Erlang `eqc_statem` model is five callbacks:

| Callback          | Purpose                                              |
| ----------------- | ---------------------------------------------------- |
| `command/1`       | generate a random command given the current state   |
| `initial_state/0` | return the initial abstract model state              |
| `next_state/3`    | compute the new model state after a command          |
| `precondition/2`  | guard: is this command valid in the current state?   |
| `postcondition/3` | oracle: does the real result match expectations?     |

QuickCheck generates random command sequences, runs them against the real system, checks every
postcondition, and shrinks to a minimal counterexample on failure. Its parallel property is the
notable trick: run the sequence concurrently and check whether the results can be explained by some
sequential interleaving, and if not, report a race, all for free from the model. Open-source PropEr
mirrors it with `proper_statem`, and
[Makina](https://icfp21.sigplan.org/details/erlang-2021-papers/4/Makina-A-New-QuickCheck-State-Machine-Library)
(Elixir) compiles a friendlier, typed DSL down to QuickCheck state machines. The Haskell
[quickcheck-state-machine](https://github.com/stevana/quickcheck-state-machine) adapts the same model,
with the same sequential-prefix-plus-concurrent-suffix race testing, used by IOHK (Cardano), Wire, and
others on consensus and distributed systems. In each, the model is the specification, defining the
valid operations, how state evolves, and the expected results. Quviq's version is very mature
(Ericsson, Volvo), PropEr and the Haskell library are production-grade, and Makina is newer (2021).

## From a formal specification

The idea behind all of these: rather than write individual cases, state behavior as
universally-quantified properties and let a framework generate inputs and check them. Different
specification elements map to different property shapes:

| Specification element      | Property type        | Example                                |
| -------------------------- | -------------------- | -------------------------------------- |
| Type constraint            | type-level property  | "output is always a positive integer"  |
| Invariant                  | state invariant      | "balance never goes negative"          |
| Precondition/postcondition | Hoare-style property | "if input sorted, output sorted"       |
| Algebraic law              | round-trip property  | "decode(encode(x)) == x"               |
| State machine              | behavioral property  | "after create, get returns the item"   |
| Protocol rule              | sequence property    | "handshake must precede data transfer" |

The [pipeline Kiro describes](https://kiro.dev/blog/property-based-testing/) is to write acceptance
criteria in natural language, extract universally-quantified properties ("for any valid X, P holds"),
implement them as PBT tests in Hypothesis, QuickCheck, or fast-check, and let the framework generate,
check, and shrink. The insight, that the properties are
[another representation of the specification](https://link.springer.com/article/10.1007/s10270-017-0647-0),
keeps traceability between what stakeholders need and what tests check, and the underlying technique
goes back to the [original QuickCheck](https://dl.acm.org/doi/10.1145/351240.351266). In practice
this is how Amazon tests S3 and DynamoDB invariants, Volvo automotive protocols, Stripe financial
logic, and Ericsson telecom protocols.
