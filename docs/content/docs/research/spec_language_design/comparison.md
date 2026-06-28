---
title: "Why not an existing language"
description: "Why not Alloy, TLA+, OpenAPI, Dafny, or TypeSpec, and what this language adds"
---

### 7.1 Why not just use Alloy directly

**What Alloy offers.** The most expressive relational data modeling of any specification language.
Multiplicities, transitive closure, set comprehensions, and the Alloy Analyzer for bounded model
checking.

Why it is insufficient for our purpose:

1. No HTTP concepts. Alloy has no notion of requests, responses, status codes, or endpoints.
   Every REST mapping would need to be encoded as a convention outside Alloy.

2. Bounded semantics. The Alloy Analyzer only checks properties up to a finite bound (e.g., "for
   all models with at most 5 ShortCodes and 5 LongURLs"). This is useful for finding bugs but cannot
   prove properties for all sizes.

3. No code generation. Alloy is analysis-only. Alchemy (2008) attempted Alloy-to-code
   compilation but it was a research prototype that died. There is no maintained path from Alloy to
   running code.

4. Unfamiliar syntax. Alloy's `sig`/`fact`/`pred`/`assert` vocabulary and relational operators
   (`.`, `~`, `^`, `+`, `&`) are unfamiliar to most developers. The learning curve is significant.

5. No input/output modeling. Alloy predicates take parameters but there is no distinction
   between "input to an operation" and "output from an operation." This makes it impossible to
   derive request/response schemas automatically.

6. Weak string handling. Alloy treats strings as opaque atoms. There is no way to express
   constraints like "length >= 6" or "matches regex" natively.

**What we take from Alloy.** Relational data modeling, multiplicities, the `sig`-like entity
concept, bounded analysis as a verification backend.

### 7.2 Why not just use TLA+/Quint

**What TLA+/Quint offers.** The most powerful state-transition modeling of any specification
language. Temporal logic for safety and liveness properties. Industrial validation at Amazon (S3,
DynamoDB, EBS, and others).

Why it is insufficient:

1. No data modeling. TLA+ has no concept of entities, fields, or multiplicities. Everything is a
   mathematical function or set. There is no way to declare that "a ShortCode has a value field of
   type String", you just have sets and functions.

2. No code generation. TLA+ and Quint are verification-only. The model checker explores states
   but produces no implementation code. Quint can generate traces but not service code.

3. No pre/postcondition structure. TLA+ actions are predicates over current and next state.
   There is no syntactic distinction between preconditions (what must hold before) and
   postconditions (what must hold after). This makes it harder to derive HTTP status codes
   (precondition failure = 4xx) and validation logic.

4. Global state model. TLA+ uses global variables with `UNCHANGED` for framing. This does not
   map well to REST services where each operation should declare what state it touches.

5. No type constraints. TLA+ is untyped (Quint adds types but not refinement types). There is no
   way to express "ShortCode is a String of length 6 to 10."

**What we take from TLA+/Quint.** Primed variables for post-state (`store'`), action semantics (each
operation is a predicate over pre/post state), Quint's developer-friendly syntax style.

### 7.3 Why not just use OpenAPI

**What OpenAPI offers.** The industry standard for describing REST API structure. Massive ecosystem
of tools (editors, validators, code generators, testing tools).

Why it is insufficient:

1. No behavioral specification. OpenAPI describes the shape of requests and responses but says
   nothing about what the operations do. You can say "POST /shorten accepts a URL and returns a
   code" but you cannot say "the returned code was not previously in the store" or "the store now
   has one more entry."

2. No state model. OpenAPI has no concept of server-side state. There is no way to express "the
   service maintains a mapping of codes to URLs" or "deleting a code removes it from the mapping."

3. No invariants. OpenAPI cannot express "all stored URLs are valid" or "no two users share the
   same email." Schema constraints (patterns, min/max) apply to individual fields rather than cross-entity
   relationships.

4. No relationship between operations. OpenAPI cannot express that "Resolve returns the URL that
   was stored by a previous Shorten call." Each endpoint is specified independently.

5. Verbose. An OpenAPI spec for a simple URL shortener is hundreds of lines of YAML. Our DSL
   expresses the same structural information plus behavioral specs in under 80 lines.

**What we take from OpenAPI.** It is a compilation target. The convention engine emits OpenAPI specs
from our DSL, gaining the entire OpenAPI ecosystem (Swagger UI, Schemathesis, client generators) for
free.

### 7.4 Why not just use Dafny

**What Dafny offers.** The most mature auto-active verification language. Pre/postconditions,
invariants, termination proofs, and compilation to 5+ languages.

Why it is insufficient:

1. No convention engine. Dafny requires the programmer to write the full implementation,
   including HTTP routing, request parsing, response serialization, database queries, and error
   handling. Our DSL delegates all of this to the convention engine.

2. Too low-level. A Dafny implementation of a URL shortener requires explicit loop invariants,
   framing conditions (`modifies`), and implementation details that are not part of the
   specification. Our DSL only requires the "what," not the "how."

3. No relational modeling. Dafny uses maps, sequences, and sets but has no concept of
   multiplicities or relational joins. The type `map<string, string>` does not communicate that this
   is a partial function from codes to URLs.

4. No HTTP awareness. Dafny has no decorators, annotations, or conventions for mapping
   operations to REST endpoints.

5. String verification is weak. Dafny struggles to verify properties of string operations
   (length, pattern matching, concatenation). This is a limitation of the underlying Z3 SMT solver.

**What we take from Dafny.** The `requires`/`ensures` syntax, `old()` for pre-state reference, and
Dafny as the intermediate verification target for business logic synthesis.

### 7.5 Why not just use TypeSpec

**What TypeSpec offers.** A clean, modern DSL for API description with excellent tooling and
multiple output formats (OpenAPI, JSON Schema, Protobuf).

Why it is insufficient:

1. No behavioral verification. TypeSpec describes API structure but cannot express what
   operations do, what state they modify, or what invariants they maintain. It is a schema language,
   not a specification language.

2. No state model. TypeSpec has no concept of server-side state. It describes the interface, not
   the behavior behind it.

3. No pre/postconditions. TypeSpec's `@doc` decorators can describe behavior in natural language
   but this is not machine-checkable.

4. No invariants. TypeSpec can constrain field values (patterns, min/max) but cannot express
   cross-field or cross-entity invariants.

**What we take from TypeSpec.** The decorator/annotation pattern for HTTP overrides, the syntax
style (TypeScript-like), and the multi-output compilation model.

### 7.6 What our language adds that none of these have

Our DSL is the first language that combines all of the following in a single formalism:

| Capability                                          | Source of Inspiration              | Status in Existing Languages                      |
| --------------------------------------------------- | ---------------------------------- | ------------------------------------------------- |
| Relational data model with multiplicities           | Alloy                              | Only in Alloy (no code gen)                       |
| Pre/postcondition operation contracts               | VDM, Dafny                         | Only in verification languages (no HTTP)          |
| State-transition modeling with primed vars          | TLA+, Quint                        | Only in temporal logic tools (no data model)      |
| Enum-based state machines with transition rules     | P language, Event-B                | Only in protocol verifiers (no REST)              |
| Refinement types with constraints                   | Dafny, Liquid Haskell              | Only in type-theory languages                     |
| Convention-based HTTP/DB mapping                    | JHipster (implicit)                | No spec language has this                         |
| Convention override blocks                          | TypeSpec decorators, Smithy traits | Only in API description languages (no behavior)   |
| Global invariants generating DB constraints + tests | Event-B (partial)                  | No language generates both                        |
| Single-file service specification                   | None                               | Each existing language covers part of the picture |

The key innovation is not any individual feature but the combination of all of them in a language
designed specifically for REST service specification. The convention engine is the bridge that makes
a behavioral specification into a running service without requiring the developer to write HTTP
routes, SQL queries, or serialization code.

The design philosophy is straightforward. The developer specifies WHAT the service should do (entities, state,
operations, invariants). The convention engine decides HOW to implement it (HTTP methods, database
schema, API paths, error responses). The developer can override any convention decision, but the
defaults should be correct for 90% of cases.

This is analogous to how a modern web framework works, the developer writes business logic and the
framework handles routing, serialization, and middleware, but elevated to the specification level.
Instead of writing business logic code, the developer writes behavioral contracts, and the compiler
generates verified business logic code.
