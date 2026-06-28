---
title: "Prior art and design rationale"
description: "Synthesis across 7 research domains for the spec-to-REST compiler"
---

> A compiler that turns a formal behavioral specification into a running, verified REST service. This
> is the prior-art survey and the design rationale behind it, across seven areas: Alloy-to-code tools,
> LLM-plus-verifier synthesis, spec-first REST generators, program synthesis, model-driven
> engineering, property-based testing, and service DSLs.

## 1. Executive Summary

The goal was a single pass: write a formal behavioral specification of a REST service and have a
compiler emit a complete, verified, running implementation, with HTTP, the database, the language,
and infrastructure abstracted away. No such tool existed when this work began. Every piece had been
built, but by communities that did not talk to each other, so the task was integration, not a
research moonshot. The compiler described here is built and shipped.

Each community solved one half and stopped.

| Capability                  | Mature in                               | What is missing          |
| --------------------------- | --------------------------------------- | ------------------------ |
| Behavioral verification     | TLA+, Alloy, Quint, AWS's P             | no code generation       |
| Structural API specs        | TypeSpec, Smithy, OpenAPI               | no behavioral checking   |
| Code generation from specs  | openapi-generator, JHipster, Ballerina  | no correctness guarantee |
| Verified code extraction    | Dafny, F\*, Coq                         | no HTTP awareness        |
| LLM-plus-verifier synthesis | Clover, AutoVerus, DafnyPro             | no service target        |
| Conformance testing         | Schemathesis, RESTler, Hypothesis       | tests only, no codegen   |
| Alloy to implementation     | Alchemy (2008)                          | dead since 2010          |

JHipster comes closest structurally, building full CRUD stacks from JDL. TLA+ and P come closest
behaviorally, used at Amazon on S3, DynamoDB, and
[eight other systems](https://cacm.acm.org/practice/systems-correctness-practices-at-amazon-web-services/).
Putting a behavioral spec and a verified, running service on one axis is what this compiler does.

## 2. Prior Art: What Has Been Tried

The most direct ancestor is
[Alchemy](https://cs.brown.edu/~sk/Publications/Papers/Published/kdfy-alchemy-trans-alloy-spec-impl/)
(Krishnamurthi, Dougherty, Fisler, and Yoo at WPI and Brown, 2008), which compiled Alloy specs to
database-backed implementations: signatures to tables, predicates to stored procedures, facts to
integrity constraints, by rewriting relational-algebra formulas into transaction code. It handled
only a subset of Alloy, had no HTTP layer, and stopped after
[a 2010 follow-up](https://arxiv.org/abs/1003.5350). Its convention, that state-changing predicates
are writes and facts are integrity constraints, carries straight to REST and is the one this project
inherits.

Three projects from Daniel Jackson's MIT group made Alloy executable from different angles. Joseph
Near's [Imperative Alloy](https://people.csail.mit.edu/jnear/papers/jnear_ms.pdf) (2010) compiled
imperative-extended Alloy to Prolog, whose nondeterminism suits Alloy's constraints;
[aRby](https://github.com/sdg-mit/arby) (2014) embedded Alloy in Ruby to mix imperative code with
constraint solving; and [Squander](https://github.com/aleksandarmilicevic/squander) (2011) ran
Alloy-style Java annotations against the live heap through Kodkod and SAT. The shared lesson is that a
spec language can live inside an executable host and stay verifiable.

[Milicevic's PhD thesis](https://aleksandarmilicevic.github.io/papers/mit15-milicevic-phd.pdf) (MIT,
2015) unified that line and added SUNNY, a DSL with declarative constraints, runtime model checking,
online code generation, and reactive UI, the closest historical precedent to this project. Its living
descendant is Emina Torlak's [Rosette](https://emina.github.io/rosette/), built after Kodkod (Alloy's
SAT backend): write a DSL interpreter in solver-aided Racket and get synthesis and verification for
free.

## 3. The LLM-plus-verifier frontier (2023 to 2026)

This is the most active area, and its systems almost all share one loop: the model proposes code, a
verifier checks it, and on failure the error and a counterexample feed back until the proof goes
through or the budget runs out. The idea predates the current wave, descending from
counterexample-guided synthesis and its
[neural-network-guided variant](https://arxiv.org/abs/2001.09245) (2020). The field has converged on
Dafny, whose specs sit inline with code, need no separate proof scripts, and
[compile to five languages](https://dafny.org/latest/HowToFAQ/FAQCompileTargets); Amazon verifies SDK
code through [smithy-dafny](https://github.com/smithy-lang/smithy-dafny).

| Project                                                          | Target       | Result                                                        |
| ---------------------------------------------------------------- | ------------ | ------------------------------------------------------------- |
| [Clover](https://arxiv.org/abs/2310.17807) (Stanford, 2023)      | Dafny        | 87% acceptance, 0 false positives; triangulates code/annotations/docstrings |
| [DafnyPro](https://arxiv.org/abs/2601.05385) (2026)              | Dafny        | 86% on DafnyBench; diff-checker, pruner, hint augmentation    |
| [Laurel](https://arxiv.org/abs/2405.16792) (UCSD, 2024)          | Dafny        | ~half the needed assertions, by localizing where they belong  |
| [VerMCTS](https://arxiv.org/abs/2402.08147) (Harvard, 2024)      | Dafny, Coq   | +30% over baselines; verifier as the MCTS heuristic           |
| [AutoVerus](https://arxiv.org/abs/2409.13082) (MSR, 2024)        | Verus/Rust   | >90% of 150 tasks; multi-agent by proof phase                 |
| [AlphaVerus](https://arxiv.org/abs/2412.06176) (CMU, 2024)       | Verus/Rust   | self-improving by iterative translation                       |
| SAFE (MSR, 2024)                                                 | Verus/Rust   | 43% on VerusBench; evolves spec and proof together            |
| Baldur (UMass, Google, 2023)                                     | Isabelle/HOL | 65.7%; whole-proof generation and repair                      |
| [LMGPA](https://arxiv.org/abs/2512.09758) (Northeastern, 2025)   | TLA+         | 38-59% on protocols; constrained proof decomposition          |
| [Eudoxus/SPEAC](https://arxiv.org/abs/2406.03636) (Berkeley, 2024) | UCLID5     | 84.8% parse rate; a "parent language" the model aligns to     |
| LLMLift (Berkeley, 2024)                                         | Spark        | 44/45 benchmarks; Python as the LLM's intermediate repr       |
| SynVer (Purdue, 2024)                                            | C, Rocq      | verified lists and BSTs; split across a coder and a prover    |

For this compiler the loop answers the business-logic problem: the convention engine maps structure,
but computing a short code or validating a URL has to be synthesized. The research is consistent. The
model reliably produces code a verifier accepts, the feedback loop is what makes that true rather
than raw generation, Dafny is the place to verify before compiling onward, and Clover's cross-check
of code against annotations and docstrings is the part worth copying.

## 4. Spec-first REST tools: the structural side

A second body of work generates code from structural API descriptions.
[openapi-generator](https://github.com/OpenAPITools/openapi-generator) turns OpenAPI YAML into stubs
for more than forty languages, with quality uneven enough that "it often doesn't compile" is a common
complaint, which is why this compiler emits OpenAPI as an intermediate artifact but does not lean on
third-party generators for the final code. [Smithy](https://smithy.io/2.0/) drives every AWS SDK from
one IDL and has the best trait system for extensible metadata; [TypeSpec](https://typespec.io/) emits
OpenAPI, JSON Schema, and protobuf from a single source; and
[Ballerina](https://ballerina.io/spec/lang/master/) builds structural typing and first-class HTTP
into the language itself. The closest of all is [JHipster](https://www.jhipster.tech/jdl/intro/), a
full Spring Boot stack and frontend from JDL, but CRUD-only with no behavioral verification.
Model-driven work points the same way: [EMF-REST](https://arxiv.org/pdf/1504.03498) turns EMF models
into JAX-RS APIs, [LEMMA](https://github.com/SeelabFhdo/lemma) models microservice architecture
across small DSLs, and [Context Mapper](https://contextmapper.org/docs/jhipster-microservice-generation/)
feeds DDD bounded contexts into JHipster.

Checking an implementation against its spec is the other half.
[Schemathesis](https://github.com/schemathesis/schemathesis) fuzzes an OpenAPI document for schema
violations, 500s, and validation bypasses; [RESTler](https://github.com/microsoft/restler-fuzzer)
fuzzes statefully through inferred request dependencies;
[EvoMaster](https://github.com/EMResearch/EvoMaster) evolves test suites and has found dozens of real
bugs in live services; [Pact](https://pact.io/) checks consumer-driven contracts;
[Hypothesis](https://hypothesis.readthedocs.io/en/latest/stateful.html) drives model-based stateful
tests; and [Dredd](https://github.com/apiaryio/dredd) did schema validation until it was archived in
2024. Microsoft's [Spec Explorer](https://www.microsoft.com/en-us/research/project/spec-explorer/)
pioneered this kind of model-based testing years earlier. All of it finds problems; none generates
the service.

## 5. Formal specification languages: the behavioral side

On the behavioral side the field is deep, and the question is which ideas to borrow.

| Language                                                        | What this project borrows                       | Generates code?       |
| --------------------------------------------------------------- | ----------------------------------------------- | --------------------- |
| [Dafny](https://dafny.org/dafny/DafnyRef/DafnyRef)              | pre/postconditions, invariants, termination; the verification target | yes, five languages |
| [TLA+](https://github.com/tlaplus/tlaplus)                      | state machines and temporal logic               | no                    |
| [Quint](https://github.com/informalsystems/quint/blob/main/docs/content/docs/lang.md) | TLA+ semantics with TypeScript-like syntax | traces only |
| [Alloy](https://alloytools.org/)                                | relational logic and transitive closure         | no                    |
| AWS's [P](https://p-org.github.io/P/)                           | communicating state machines                    | no                    |
| [Event-B](https://wiki.event-b.org/index.php/Code_Generation_Activity) | refinement                               | C, Java, Ada via plugins |
| [VDM](https://www.overturetool.org/)                            | operation modeling                              | Java, C via Overture  |
| [F\*](https://www.fstar-lang.org/)                              | dependent types and a worked extraction proof   | C via KaRaMeL         |

Dafny is used in anger on [Cedar](https://www.cedarpolicy.com/) at AWS. P is worth singling out as
the closest to the target domain: it specifies communicating state machines, which is to say
microservices, and Amazon has used it on S3's strong-consistency migration, DynamoDB, MemoryDB,
Aurora, EC2, and IoT, with PObserve checking after the fact that production logs match the spec. It
still emits no service code; the same after-the-fact check exists for TLA+ through
[trace validation](https://arxiv.org/abs/2404.16501).

Session types are the one idea that does not transfer cleanly.
[Scribble](https://github.com/scribble/scribble-language-guide) (Imperial College) generates type-safe
channels from a global protocol and guarantees freedom from communication errors and deadlocks, with
[implementations in more than sixteen languages](https://groups.inf.ed.ac.uk/abcd/session-implementations.html).
The trouble is that REST is stateless request and response while session types assume a stateful,
multi-step conversation. They could still model a create-read-update-delete workflow if this project
ever needs them.

## 6. The design this produced

The survey pointed to a five-stage pipeline, and that is what was built: parse the spec to an IR, run
a convention engine that maps structure to HTTP routes, a database schema, and OpenAPI, verify the
spec itself, synthesize the business logic through an LLM-plus-verifier loop, and generate conformance
tests. The stage-by-stage detail is in [Architecture](/design/architecture), the convention mapping in
[the convention engine](/design/convention-engine), and the implementation rationale in
[07_implementation_architecture](/research/07_implementation_architecture).

Each stage exists partly to clear a risk the research had already exposed. The widest was the
abstraction gap: a spec says the store gains an entry, but real code has to settle transactions,
retries, and connection pooling. The convention engine closes it with infrastructure templates keyed
to a deployment profile, so Postgres-and-FastAPI brings transaction handling and Redis-and-go-chi
brings connection management, the move that makes JHipster work. Synthesis carries its own risk, since
DafnyPro's 86% is a benchmark and real operations can be harder, so the convention engine handles the
CRUD majority while synthesis covers the rest with Clover-style triangulation, and a failed proof
fails loud: a stub that halts at runtime rather than a silent skeleton, with an unverified skeleton
only under `--allow-skeletons`. Generated-code quality is a risk openapi-generator shows is real,
which is why the targets are few and well-tested (Python with FastAPI, Go with chi, TypeScript with
Express) and each emitter is hand-tuned rather than derived from a meta-generator.

The language answers a risk of its own, because TLA+ and Alloy are steep enough to keep most
developers away, so it was designed to read like pseudocode. It uses words like `not in` and `and`
rather than mathematical symbols, keeps structure and behavior in one file, treats conventions as
overridable defaults, explains specs in plain-English errors, and can even take natural-language
requirements the way the Eudoxus work does. Its syntax and a worked `url_shortener` example are in
[the spec language reference](/spec-language); the rationale is in
[01_spec_language_design](/research/01_spec_language_design). One risk stays open by nature, since
verifying the spec is bounded and never total. For REST services the state spaces are usually small
enough that this matters less than for distributed protocols, so Alloy-style bounded checking covers
the data model and Quint or TLA+ covers temporal properties, with Amazon's experience as the
reassurance, since bounded methods still found bugs in every system they were pointed at.

The whole thing shipped in five phases: the core spec language and convention engine first, then
verification, test generation, the synthesis loop, and finally multi-target support and polish (Go and
TypeScript, deployment artifacts, the `conventions` override system). Current status and what comes
next are in the [roadmap](/roadmap).
