---
title: "Model-based testing"
description: "TLA+ traces, the model-based landscape, conformance, Spec Explorer, and NModel"
---

## 3. TLA+ trace validation & test generation

**What it is.** A family of approaches that connect TLA+ formal specifications to real
implementations by either (a) validating execution traces against the spec, or (b) generating test
cases from the spec's state space.

### 3a. trace validation (constrained model checking)

#### Methodology (kuppe et al., 2024)

1. Instrument the implementation to emit a trace (log) of state transitions.
2. Express the trace as a constrained TLA+ specification.
3. Use the TLC model checker to verify the trace is a valid behavior of the spec.
4. The problem reduces to: "does there exist a sequence of spec states that matches the observed
   trace?"

**Key insight, partial traces.** Not all specification variables need to be traced. The model
checker can reconstruct missing information. This creates a tradeoff: less instrumentation = larger
search space but less invasive changes to implementation code.

#### Tooling

- Java API for program instrumentation
- TLA+ operator library for relating traces to specifications
- Scripts to drive TLC model checker

**Experimental results.** Found discrepancies between specs and implementations in all tested
distributed programs.

### 3b. mongodb's conformance checking

MongoDB uses TLA+ to specify distributed algorithms (e.g., replication protocol). Their approach:

1. Run implementation under test scenarios.
2. Capture execution traces of state transitions.
3. Validate traces against TLA+ specifications.
4. Verify no invariants are violated.

#### Lessons learned (after 5 years)

- Formal specs caught real algorithmic issues.
- Maintaining conformance checking requires significant ongoing effort.
- Keeping specs in sync with evolving implementations is the hardest part.
- "Agile modelling", specs must evolve with the codebase.

### 3c. omnilink (2025), unmodified concurrent systems

#### Methodology

- Records operation start/end times as "timeboxes" (no code modification needed).
- Automatically generates a fuzzer template from the TLA+ specification.
- Uses `rr` record/replay framework's chaos mode to randomize thread scheduling.
- Validates observed behaviors against the TLA+ spec via TLC.

**Results.** Detected 2 previously unknown bugs in WiredTiger (MongoDB storage engine), BAT
(concurrent search tree), and ConcurrentQueue. Outperformed Porcupine (state-of-art linearizability
checker) on large traces (200k+ operations).

### 3d. code + test generation from TLA+

Academic work (Fragoso Santos et al., 2022) on generating both code and tests from TLA+ specs. The
spec drives both artifacts, ensuring they are consistent by construction.

**How specs connect to tests.** The TLA+ specification is the formal model. Tests are either
generated from the state space (as valid behaviors the implementation must accept) or traces are
validated against the spec (as behaviors the implementation actually produced).

#### Maturity

- TLC model checker: mature, used at AWS, MongoDB, Microsoft, Cockroach Labs
- Trace validation: research-grade but rapidly maturing (2024-2025 papers)
- OmniLink: recent research (2025), rather than yet a production tool

#### Key sources

- https://arxiv.org/abs/2404.16075
- https://www.mongodb.com/company/blog/engineering/conformance-checking-at-mongodb-testing-our-code-matches-our-tla-specs
- https://arxiv.org/html/2601.11836
- https://dl.acm.org/doi/fullHtml/10.1145/3559744.3559747

## 5. Model-based testing for REST APIs (tool landscape)

### Overview of approaches

| Approach          | Representative Tools        | Spec Input                 | What It Tests                                      |
| ----------------- | --------------------------- | -------------------------- | -------------------------------------------------- |
| Schema-driven PBT | Schemathesis, RESTler       | OpenAPI/Swagger            | Schema conformance, edge cases, stateful workflows |
| Contract testing  | Pact, Spring Cloud Contract | Consumer-defined contracts | Service compatibility                              |
| Spec validation   | Dredd, Prism                | OpenAPI/API Blueprint      | Doc-implementation sync                            |
| Commercial MBT    | Tricentis Tosca             | Proprietary models         | End-to-end business flows                          |
| Academic MBT      | Spec Explorer, NModel       | C#/formal models           | Protocol conformance                               |

### Key insight: Spec-to-test spectrum

```text
Manual tests <-----> Schema validation <-----> PBT from spec <-----> Full MBT
   (Postman)          (Dredd, Prism)      (Schemathesis)    (Spec Explorer)

Less spec formality ---------------------------------> More spec formality
Less automation -------------------------------------> More automation
Less coverage ---------------------------------------> More coverage
Less setup ------------------------------------------> More setup
```

**How specs connect to tests.** In the REST API world, the OpenAPI specification is the de facto
"formal spec." Tools like Schemathesis and RESTler treat it as a machine-readable model from which
to generate tests. The richer the spec (with examples, links, constraints), the better the generated
tests.

#### Key sources

- https://tools.openapis.org/categories/testing.html
- https://www.softwaretestinghelp.com/api-testing-tools/

## 9. Conformance testing: Formal model vs. implementation

**Definition.** Verifying that an implementation correctly realizes the behavior specified by a
formal model. The conformance relation defines what "correctly" means.

### Theoretical foundation

**ioco (input-output conformance):** The dominant conformance relation for reactive systems. An
implementation `i` conforms to specification `s` (written `i ioco s`) if, for every trace that `s`
can produce, the outputs that `i` produces after that trace are a subset of the outputs that `s`
allows.

#### Formal models used

| Model Type                      | Expressiveness              | Typical Domain       |
| ------------------------------- | --------------------------- | -------------------- |
| FSM (Finite State Machine)      | States + transitions        | Protocol conformance |
| LTS (Labeled Transition System) | Non-determinism, quiescence | Reactive systems     |
| EFSM (Extended FSM)             | Data variables + guards     | Richer protocols     |
| TFSM (Timed FSM)                | Real-time constraints       | Real-time systems    |
| TLA+ specifications             | Arbitrary math              | Distributed systems  |

### Test generation from formal models

**Soundness.** A conforming implementation passes all generated test cases. **Completeness:** A
non-conforming implementation fails at least one test case.

#### Coverage criteria

- State coverage: every model state is visited
- Transition coverage: every model transition is exercised
- Path coverage: specific paths through the model are traversed

### Verified model-based conformance testing

#### Approach (differential fuzzing against verified model)

1. Build a small, verified model (proven correct via formal proofs).
2. Generate random operations.
3. Execute on both the model and the real implementation.
4. Compare outputs and states.
5. Any divergence is a real bug (because the model is provably correct).

**Best suited for.** State machines, protocols, financial logic, parsers, systems with strict
invariants.

**How specs connect to tests.** The formal model defines the "should" behavior. Test cases are
generated by traversing the model's state space. The implementation is the system under test.
Conformance is checked by comparing implementation behavior against model behavior.

#### Key sources

- https://www.sciencedirect.com/topics/computer-science/conformance-testing
- https://welltyped.systems/blog/verified-conformance-testing-for-dummies
- https://www.sciencedirect.com/science/article/abs/pii/S0950584910001278
- https://link.springer.com/content/pdf/10.1007/978-0-387-34883-4_12.pdf

## 10. Spec Explorer, Microsoft model-based testing

**What it is.** A Visual Studio extension for model-based testing. Developed by Microsoft Research,
used internally at Microsoft for 10+ years, saved an estimated 50 person-years of testing effort.

### How it works

### Step 1: Write model programs (c#)

- System state = class fields
- State transitions = rule methods with `[Rule]` attribute
- Enabling conditions = `Condition.IsTrue(...)` calls
- Model is pure C#, no new language to learn

### Step 2: Define machines (cord scripting language)

- `construct model program`, explores the full state space
- Scenarios, regular-expression-like patterns of action sequences
- `||` (synchronized parallel composition), slices behavior by intersecting a scenario with the
  full model (critical for infinite state spaces)

### Step 3: Explore & visualize

- Spec Explorer generates a state graph from the model.
- Circle states = controllable (test sends stimulus)
- Diamond states = observable (test expects response from SUT)
- Non-deterministic states = multiple possible SUT responses

### Step 4: Generate tests

- `construct test cases` converts explored behavior into "test normal form" (no state has multiple
  outgoing call-return steps).
- Traversal uses edge coverage (every transition covered at least once).
- Generated code is human-readable Visual Studio unit tests or NUnit tests.

#### Impact at microsoft

- Used for Windows protocol compliance (250 person-years of testing; MBT saved ~50 person-years =
  40% effort reduction)
- Used for .NET framework, operating system components
- Deployed since 2004

#### When MBT pays off (microsoft's rules of thumb)

- Infinite or very large state spaces
- Reactive / distributed / asynchronous systems
- Non-deterministic interactions
- Methods with many complex parameters
- Requirements that can be covered in multiple ways

#### Current status

- Spec Explorer 2010: last Visual Studio extension release
- NModel: open-source successor (C# model programs, same conceptual approach)
- The approach is mature but the specific tooling is aging

**How specs connect to tests.** The C# model program _is_ the formal specification. Spec Explorer
explores it as a state machine, generates a finite graph via scenario slicing, then produces
executable test cases from the graph.

#### Key sources

- https://www.microsoft.com/en-us/research/project/model-based-testing-with-specexplorer/
- https://learn.microsoft.com/en-us/archive/msdn-magazine/2013/december/model-based-testing-an-introduction-to-model-based-testing-and-spec-explorer
- https://jon-jacky.github.io/NModel/

## Appendix B: NModel

**What it is.** Open-source model-based testing framework for C#. Spiritual successor to Spec
Explorer, usable without Visual Studio.

### Components

- Library of attributes and data types for writing model programs in C#
- `mpv` (Model Program Viewer), visualization and analysis
- `mp2dot`, export to Graphviz DOT format
- `ct`, test generation and execution tool

**How specs connect to tests.** Same as Spec Explorer: model programs in C# define the state
machine; tools explore the state space and generate test cases.

### Key sources

- https://jon-jacky.github.io/NModel/
- http://staff.washington.edu/jon/modeling-book/
