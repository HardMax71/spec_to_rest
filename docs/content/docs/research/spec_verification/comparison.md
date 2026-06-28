---
title: "How it compares"
description: "The verification engine against TLA+, Alloy, and other spec checkers"
---

## 8. Comparison with existing spec checkers

### 8.1 Alloy analyzer

**What it is.** SAT-based bounded model checker for Alloy's relational logic. Uses the Kodkod engine
(which compiles relational constraints to SAT via symmetry-breaking optimizations).

#### Strengths

- Excellent for relational data model properties.
- Transitive closure, relational composition, and set operations are native.
- Visualizer produces clear counterexample diagrams.
- The small scope hypothesis is well-validated in practice.
- Handles complex relational structures (graphs, hierarchies) well.

#### Weaknesses

- No string operations, limited arithmetic (bounded integer bitwidth).
- Cannot express temporal properties natively (Alloy 6 adds LTL but it is experimental).
- Bounded: cannot prove unbounded properties.
- SAT solver performance degrades rapidly with scope increase beyond ~10.
- No incremental checking (re-solves from scratch on every edit).

**Use for our engine.** Data model consistency, relational invariant checking, bounded reachability.
Translation from our spec to Alloy is natural because our data model (entities, relations,
multiplicities) mirrors Alloy's sigs and relations.

### 8.2 TLC (TLA+ explicit-state model checker)

**What it is.** Exhaustive state-space explorer for TLA+ specifications. Used extensively at AWS
(S3, DynamoDB, EBS, etc.).

#### Strengths

- Exhaustive within bounds (no bugs missed within scope).
- Handles temporal properties (liveness, fairness) natively.
- Battle-tested on production systems at massive scale.
- Can generate traces for counterexamples.
- Distributed mode for large state spaces.

#### Weaknesses

- Explicit-state: state space explodes combinatorially.
- TLA+ syntax is unfamiliar to most developers.
- No native string or floating-point operations.
- Slow startup (JVM-based).
- No SMT integration (purely SAT/enumeration).

**Use for our engine.** Temporal property checking for state machines, deadlock detection, liveness
verification. We would translate our spec to TLA+ and invoke TLC as a subprocess.

### 8.3 Apalache (TLA+ symbolic model checker)

**What it is.** SMT-based symbolic model checker for TLA+. Translates TLA+ to SMT constraints and
uses Z3, avoiding explicit state enumeration.

#### Strengths

- Handles larger state spaces than TLC (symbolic, rather than explicit).
- Same TLA+ input as TLC (no separate spec needed).
- Can find counterexamples that TLC misses at small bounds.
- Better for data-intensive specs (arithmetic, arrays).

#### Weaknesses

- Less mature than TLC.
- Cannot check liveness properties (safety only as of 2025).
- May produce spurious counterexamples if the encoding is imprecise.
- Slower than TLC for small state spaces (SMT overhead).

**Use for our engine.** Safety invariant checking for specs with arithmetic or data-intensive
operations, as an alternative to TLC when state spaces are too large for explicit enumeration.

### 8.4 Dafny verifier

**What it is.** Deductive verification system using Boogie/Z3 as backend. Verifies
pre/postconditions, loop invariants, and termination for imperative programs.

#### Strengths

- Unbounded verification (proves properties for ALL inputs, rather than just bounded).
- Handles complex data types, generics, classes, traits.
- Compiles verified code to multiple languages.
- Growing ecosystem (DafnyBench, smithy-dafny, AWS Cedar).
- Best LLM+verification success rates (86% on DafnyPro).

#### Weaknesses

- Requires annotations (loop invariants, decreases clauses) that are hard to write and hard for LLMs
  to generate.
- Verification is undecidable in general; may time out.
- The verifier sometimes requires non-obvious proof hints.
- Not a model checker: cannot explore state spaces or check temporal properties.
- Error messages can be cryptic.

**Use for our engine.** Invariant preservation proofs (the most important check). Dafny's
`requires`/`ensures` directly match our spec's pre/postconditions. We generate Dafny code from the
spec and verify it. Also used in Stage 4 (LLM synthesis) for generating verified implementations.

### 8.5 Spin (LTL model checking for promela)

**What it is.** Explicit-state model checker for concurrent systems, using Promela as its
specification language. Checks LTL (Linear Temporal Logic) properties.

#### Strengths

- Extremely efficient for concurrent/distributed protocol verification.
- Partial-order reduction and state compression for scalability.
- LTL property checking is native and well-optimized.
- Decades of industrial use (telecommunications, aerospace).

#### Weaknesses

- Promela is a process-oriented language, rather than a good fit for REST services.
- No native data structures beyond arrays and channels.
- Limited arithmetic.
- Translation from our spec to Promela would be unnatural.

**Use for our engine.** Limited. Spin is best for concurrent protocols, which are not the primary
domain of REST service specs. If the spec describes concurrent interactions between multiple
services, Spin could be useful, but TLC/Quint are more natural choices.

### 8.6 NuSMV / nuxmv (symbolic model checking)

**What it is.** BDD-based (NuSMV) and SMT-based (nuXmv) symbolic model checkers. Check CTL and LTL
properties over finite-state transition systems.

#### Strengths

- Very efficient for hardware-like finite-state systems.
- CTL model checking (branching-time logic) is unique to this tool family.
- nuXmv adds infinite-state capabilities via SMT.
- BDD-based approach can handle very large state spaces that defeat explicit enumeration.

#### Weaknesses

- Input language (SMV) is low-level and hardware-oriented.
- Not designed for software specifications.
- No native support for relational data models, strings, or complex data types.
- Translation from our spec to SMV would be complex and error-prone.

**Use for our engine.** Niche. Useful only if we need CTL properties (e.g., "there exists a path
where an order is delivered without being paid"), which is uncommon for REST services. Prefer
Quint/TLC for temporal checking.

### 8.7 Summary comparison

| Tool        | Best For                    | Our Use                         | Integration Effort |
| ----------- | --------------------------- | ------------------------------- | ------------------ |
| Alloy       | Relational data models      | Data invariants, reachability   | Medium (Java API)  |
| TLC         | Temporal properties         | Deadlock, liveness              | Medium (CLI)       |
| Apalache    | Large safety checks         | Fallback for complex invariants | Medium (CLI)       |
| Dafny       | Unbounded proof obligations | Invariant preservation, synth   | Low (CLI + API)    |
| Spin        | Concurrent protocols        | Unlikely needed                 | High               |
| NuSMV/nuXmv | Finite-state CTL            | Unlikely needed                 | High               |
| Z3 (direct) | SMT queries                 | Core: all SAT/SMT checks        | Low (Python API)   |
| Quint       | TLA+ with modern syntax     | Temporal, simulation            | Low (CLI + npm)    |

#### Our recommended stack

1. **Z3 (direct).** Core solver for satisfiability, invariant preservation, dead condition
   detection. Called via the z3-solver Python package or Z3's C API.

2. **Dafny.** For complex proof obligations that Z3 alone cannot handle, and for generating verified
   implementations.

3. **Alloy Analyzer.** For relational data model checking (entity relationships, multiplicity
   constraints, bounded reachability). Called via the Alloy API (Java) or by generating `.als` files
   and invoking the CLI.

4. **Quint.** For temporal property checking and state machine simulation. Called via the `quint`
   CLI.
