---
title: "Why not an existing language"
description: "Why not Alloy, TLA+, OpenAPI, Dafny, or TypeSpec, and what this language adds"
---

## Why not Alloy

Alloy has the most expressive relational data modeling of any spec language: multiplicities,
transitive closure, set comprehensions, and the Analyzer's bounded model checking. It still cannot
be the language here. It has no notion of HTTP, so every REST mapping would live outside Alloy as a
convention. Its analysis is bounded, good for finding bugs in a finite scope but unable to prove a
property for all sizes. It generates no code; Alchemy tried Alloy-to-code in 2008 and died as a
prototype. The `sig`, `fact`, and `pred` vocabulary is unfamiliar to most developers. Its predicates
take parameters but draw no line between an operation's input and its output, so request and response
schemas cannot be derived from them. And it treats strings as opaque atoms, with no native way to say
"length at least six" or "matches this pattern." What carries over is the relational data model, the
multiplicities, the signature-like entity, and the Analyzer as a bounded-checking backend.

## Why not TLA+ or Quint

TLA+ has the strongest state-transition modeling around, with temporal logic for safety and liveness
and a track record at Amazon on S3, DynamoDB, and EBS; Quint keeps the semantics with friendlier
syntax. Neither models data: there are no entities, fields, or multiplicities, only functions and
sets, so "a ShortCode has a String value" cannot be stated. Neither emits service code, since Quint
produces traces rather than an implementation. Actions are predicates over the current and next state
with no syntactic split between precondition and postcondition, which makes deriving a
4xx-on-precondition-failure harder. State is global, framed with `UNCHANGED`, rather than each
operation declaring what it touches. And there are no refinement types for "a String of length six to
ten." What carries over is the primed-variable post-state, the action-as-predicate-over-pre-and-post
shape, and Quint's developer-facing syntax.

## Why not OpenAPI

OpenAPI is the industry standard for REST structure, with a deep ecosystem of editors, validators,
generators, and test tools. It says nothing about behavior: it can state that `POST /shorten` takes a
URL and returns a code, but not that the code was fresh or that the store grew by one. It has no
server-side state, no cross-entity invariants (its constraints are per-field), and no way to relate
one operation to another, since each endpoint stands alone. It is verbose too, hundreds of lines of
YAML for a shortener that this DSL specifies, behavior included, in under eighty. OpenAPI is not a
rival but a target: the convention engine emits it, which buys the whole OpenAPI ecosystem (Swagger
UI, Schemathesis, client generators) for free.

## Why not Dafny

Dafny is the most mature auto-active verifier, with pre- and postconditions, invariants, termination
proofs, and compilation to several languages. It sits a level below what a service spec wants. It
expects the programmer to write the whole implementation, the HTTP routing, parsing, serialization,
queries, and error handling, all of which this DSL hands to the convention engine. It needs explicit
loop invariants and `modifies` framing, the how rather than the what. It has maps, sequences, and
sets but no multiplicities or relational reading, so `map<string, string>` does not say "partial
function from codes to URLs." It has no HTTP awareness. And string verification is weak, a limit of
the underlying Z3. What this project borrows is the `requires` and `ensures` syntax, `old()` for the
pre-state, and Dafny as the verification target on the opt-in synthesis path.

## Why not TypeSpec

TypeSpec is a clean, modern API-description DSL with strong tooling and several output formats. It is
a schema language, not a specification language: it cannot say what an operation does, what state it
changes, or what invariant it keeps. It has no server-side state, and its `@doc` decorators describe
behavior only in prose, which a machine cannot check. Its field constraints do not reach cross-field
or cross-entity invariants. What this project takes is the decorator pattern for HTTP overrides, the
TypeScript-like syntax, and the multi-output compilation model.

## What this language adds

No existing language combines all of these in one formalism:

| Capability                                          | Inspiration                        | Status elsewhere                                  |
| --------------------------------------------------- | ---------------------------------- | ------------------------------------------------- |
| Relational data model with multiplicities           | Alloy                              | only in Alloy, with no code generation            |
| Pre/postcondition operation contracts               | VDM, Dafny                         | only in verification languages, with no HTTP      |
| State-transition modeling with primed variables     | TLA+, Quint                        | only in temporal-logic tools, with no data model  |
| Enum-based state machines with transition rules     | P, Event-B                         | only in protocol verifiers, with no REST          |
| Refinement types with constraints                   | Dafny, Liquid Haskell              | only in type-theory languages                     |
| Convention-based HTTP and DB mapping                | JHipster, implicitly               | no spec language has it                           |
| Convention override blocks                          | TypeSpec decorators, Smithy traits | only in API-description languages, with no behavior |
| Global invariants that become DB constraints and tests | Event-B, partially             | no language generates both                        |
| Single-file service specification                   | none                               | each existing language covers only part           |

The point is the combination, not any single feature, in a language built for REST service
specification. The convention engine is the bridge that turns a behavioral spec into a running
service without the developer writing HTTP routes, SQL, or serialization. The split is deliberate:
the developer says what the service does (entities, state, operations, invariants), the convention
engine decides how (methods, schema, paths, error responses), and any decision can be overridden. The
structural service, the routes, schema, validation, OpenAPI, and conformance tests, is generated, and
the spec is verified before anything is emitted. The business logic inside each operation is left as a
fail-loud stub unless the opt-in synthesis path fills and verifies it.
