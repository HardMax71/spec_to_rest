---
title: "The verified subset"
description: "Which constructs the soundness theorem covers, and how their sorts are encoded"
---

The soundness theorem is conditional: it guarantees meaning preservation for any expression that
`translate` actually handles. So the verified subset is not a separate type. It is the set of IR
expressions `translate` accepts, and the progress half of the proof
([`cat_h_progress_and_preservation_direct`](/research/translator_soundness/theorem)) pins it down
exactly, every well-typed expression translates. An expression is in the subset precisely when the
type checker accepts it.

## What it covers

The subset started as a minimal first-ship set, a handful of boolean and integer operators, and was
widened construct by construct until it covers every canonical fixture spec; the round-trip oracle
test now exercises two dozen in-subset shapes end to end with none skipped, against a green proof
build ([`STATUS.md`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/STATUS.md)).
What is in:

- boolean logic, and integer arithmetic with its comparisons
- equality, and membership in a state relation
- all four quantifier kinds (`all`, `some`, `no`, `exists`) over both enum domains and relation domains
- state, including the pre- and post-state coupling that `pre(...)` and primed variables introduce
- entity field access, enum members, and record updates
- set and sequence operations, and cardinality on relations
- the lifted builtins: string predicates like `matches`, and the integer functions behind durations

That breadth was a deliberate lifting campaign rather than a single design: each construct a real spec
needed was added to the verified semantics and re-proved, instead of being left to a trusted fallback.

## What is out, and why that is safe

A few constructs stay outside, the ones genuinely undecidable in first-order SMT (the powerset
operator), the higher-order ones (arbitrary lambdas), and any not yet lifted. The safety comes from
how the boundary is enforced. When `translate` meets an expression it does not handle, it returns
`None`, and the verify layer turns that into an `unknown` verdict, never an `unsat`. An out-of-subset
construct can only make the engine admit it cannot decide; it cannot produce a false all-clear. The
soundness theorem together with this fail-closed boundary is what makes the in-subset guarantee
unconditional.

## How the sorts are encoded

The encoding the proof reasons about, and the one the extracted translator emits, models each kind of
value like this:

- entities: an uninterpreted sort plus one accessor function per field (`Entity_field : Entity -> FieldSort`)
- enums: an uninterpreted sort with a constant per member and a distinctness axiom
- options: flattened to the inner type
- sets: an SMT-LIB set sort with store and select
- strings: an uninterpreted sort with a fresh distinct constant per literal
- state relations: a pair of functions, `field_dom : K -> Bool` and `field_map : K -> V`
- scalar state fields: a single constant function
- the post-state: the same encoding under a `_post` suffix, toggled by the translator's state mode
