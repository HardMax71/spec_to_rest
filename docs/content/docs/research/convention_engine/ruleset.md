---
title: "Ruleset"
description: "The design philosophy and the reasoning behind the M1-M10 rules; the live, code-anchored rules are in the reference"
---

The convention engine inherits Alchemy's central insight: a state-changing predicate is a write
operation, and a fact is an integrity constraint. The engine carries that beyond the database to the
whole stack, turning each spec element into one HTTP, schema, or validation artifact.

Five principles shape it. Every spec element maps to exactly one artifact, so an entity is a table, a
mutation is a POST, PUT, or DELETE, a precondition is a validation check, and an invariant is a
constraint. The mapping is deterministic, so the same spec always produces the same output with no
heuristic guessing. Every decision is overridable through the `conventions` block. The abstract
mapping (operation to HTTP method) is universal, while the concrete rendering, a Python class or a Go
struct, varies by deployment profile. And where a choice is open it follows RFC 7231 and common REST
practice rather than inventing semantics.

The current rules, code-anchored and kept in sync with the classifier, live in
[the convention engine reference](/design/convention-engine). This page is the reasoning behind them.

## Classifying an operation

Each operation gets exactly one of ten rules, M1 through M10, decided by its effect on state and
matched first to last. A mutation that introduces a new key is a `POST` (M1); a read is a `GET`
(M2); a mutation that touches every field of an existing entity is a `PUT` (M3), and one that touches
a subset is a `PATCH` (M4); removing a key is a `DELETE` (M5). The rest cover reads with more than
three filters (M7), a catch-all state mutation that fits no other shape (M8), a batch mutation over a
collection input (M9), and an operation named in a `transition` block, which becomes a `POST` to a
verb sub-resource (M10). M6, nested resource creation under a parent, is reserved in the classifier
but not yet emitted. The exact conditions and default paths are in the reference; the dispatch lives
in `Classify.scala`.

The `PUT`-versus-`PATCH` line is the one that needs care, since it turns on field coverage rather than
a keyword. If the `ensures` clause pins every field of the entity, it is a full replacement (`PUT`);
if it pins a subset, or pins them all but some conditionally, it is a partial update (`PATCH`).

## Turning preconditions into error codes

A `requires` clause becomes a guard with an HTTP status chosen by the shape of the check:

| Precondition                                       | Status |
| -------------------------------------------------- | ------ |
| existence, `id in orders`                          | 404    |
| uniqueness, `email not in users`                   | 409    |
| value or format, `total > 0`, `isValidURI(u)`      | 422    |
| state-machine guard, `order.status = Draft`        | 409    |

A conjunction generates the checks in order and returns the first failure. When more than one could
apply, the most specific wins, with 404 ahead of 409 ahead of 422. So
`id in orders and orders[id].status = Draft and total > 0` checks existence, then state, then value,
and returns the first that fails.

## Entities and relations to schema

An entity becomes a table, pluralized and snake_case, with a surrogate `BIGSERIAL` primary key and,
unless overridden, `created_at` and `updated_at` timestamps. Fields become columns, and a `where`
clause or a field invariant becomes a `CHECK`. Relations follow Alloy's multiplicity directly: `A ->
one B` is a non-null foreign key, `A -> lone B` a nullable one, and `A -> some B` or `A -> set B` a
junction table, with `some` adding an at-least-one constraint. The spec-type to column-type table is
in [the reference](/design/convention-engine), which is also where the shipped target dialects are
listed.

## Three validation layers

Validation lands at the layer that can afford it. Anything checkable without the database, the type,
length, format, range, and required-field checks, runs at the HTTP edge before any logic, and its
failures aggregate into a single 422. Anything that needs current state, an existence check, a
state-machine guard, a cross-field or cross-entity rule, runs in the application inside the
operation's transaction, evaluated in order and returning on the first failure. Database constraints
(`CHECK`, `UNIQUE`, foreign keys) are the last line: they catch what a race or a direct write slips
past the first two. The application never treats a database error as the user-facing one, since those
messages are not readable.

Responses use a consistent envelope, `{ "data": ..., "meta": { request_id, timestamp } }`, and an
error carries a `code`, a `message`, and per-field `details`. Worked output for real specs is on the
[worked examples](/research/convention_engine/worked-examples) page.
