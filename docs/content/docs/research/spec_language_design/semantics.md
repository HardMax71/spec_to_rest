---
title: "Semantic model"
description: "State, pre and primed values, quantifiers, set and relation operations, and multiplicities"
---

## State

A state is the value of every declared state field at one point in time. A scalar field holds a
single value; a relation field such as `store: ShortCode -> lone LongURL` holds a set of key-value
pairs. The reference semantics in
[`Semantics_Eval.thy`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/SpecRest/semantics/Semantics_Eval.thy)
records a state as four maps: scalars, relation domains, lookup pairs, and entity fields.

The initial state is empty. Nothing is in any relation and no scalar is set, and operations populate
it from there. Each operation takes one state to the next, so a run is a trace `S0, S1, S2, ...`
with `S0` the empty state.

## The pre-state and the post-state

Inside an `ensures` clause two views of the same field are in scope. `pre(store)` is the field as it
stood before the operation ran, the same idea as `old(store)` in Dafny or `store~` in VDM. The
primed `store'` is the field afterward. The clause is a predicate over both, and it has to hold for
the pre-state and the post-state the operation produces.

Following the TLA+ convention, an unprimed name in an `ensures` clause already means the pre-state,
so `pre(store)` and `store` are the same thing there; `pre()` exists only to make the intent obvious
where a bare name might read either way. In a `requires` clause the question never arises, since it
runs before the operation and every field reference is the pre-state.

```spec
operation Shorten {
  ensures:
    code not in pre(store)            // code was not in store before
    store'[code] = url                // code maps to url after
    #store' = #store + 1              // one more entry than before
}
```

The formal version carries a pre-state and a post-state together (`state_pair` in the reference
semantics), and a mode picks which one a reference reads.

## Quantifiers

A quantifier ranges over a collection. A binding is either membership (`x in S`) or by type
(`x: User`, which ranges over the entity's instances).

| Form        | Syntax                  | Meaning                                                |
| ----------- | ----------------------- | ------------------------------------------------------ |
| Universal   | `all x in S \| P(x)`    | `P(x)` holds for every `x` in `S`                      |
| Existential | `some x in S \| P(x)`   | `P(x)` holds for at least one `x` in `S` (`exists` is the same) |
| None        | `no x in S \| P(x)`     | `P(x)` holds for no `x` in `S`, the same as `all x in S \| not P(x)` |

The collection can be a set, a sequence, a map (ranging over its keys), or the domain or range of a
relation. Quantifiers nest:

```spec
all c in store | all d in store |
  c != d implies store[c] != store[d]
```

## Set operations

| Operation      | Syntax                   | Meaning                          |
| -------------- | ------------------------ | -------------------------------- |
| Union          | `A union B` (or `A + B`) | elements in `A` or `B`           |
| Intersection   | `A intersect B`          | elements in both `A` and `B`     |
| Difference     | `A minus B`              | elements in `A` but not `B`      |
| Cardinality    | `#A`                     | number of elements in `A`        |
| Membership     | `x in A`, `x not in A`   | whether `x` is an element of `A` |
| Subset         | `A subset B`             | every element of `A` is in `B`   |

A set comprehension builds a new set:

```spec
{ c in store | store[c].startsWith("https") }
```

That is the set of codes whose URL starts with `https`.

## Relation operations

A relation is a set of key-value pairs, so `store: ShortCode -> lone LongURL` pairs each code with at
most one URL.

| Operation          | Syntax                  | Meaning                                                   |
| ------------------ | ----------------------- | --------------------------------------------------------- |
| Lookup             | `store[code]`           | the URL for `code`, when the multiplicity allows one      |
| Domain             | `dom(store)`            | the codes that have a mapping                             |
| Range              | `ran(store)`            | the URLs that are mapped to                              |
| Insert or replace  | `store + {code -> url}` | `store` with that pair added, or overwritten if `code` exists |
| Transitive closure | `^store`                | `store` composed with itself to a fixed point            |

Transitive closure is the one relation operator the SMT layer does not handle, so a spec that uses
`^` routes to Alloy instead of Z3.

## Multiplicities

A multiplicity on a relation type bounds how many targets each source maps to.

| Multiplicity | Meaning                        | SQL analogy                | Example                            |
| ------------ | ------------------------------ | -------------------------- | ---------------------------------- |
| `one`        | exactly one target per source  | `NOT NULL` foreign key     | `owner: User -> one Team`          |
| `lone`       | zero or one target per source  | nullable foreign key       | `store: ShortCode -> lone LongURL` |
| `some`       | one or more targets per source | junction table, `NOT NULL` | `tags: Item -> some Tag`           |
| `set`        | any number, zero or more       | junction table, nullable   | `followers: User -> set User`      |

These bounds become obligations the verifier checks. `one` requires every operation that adds a
source to add a target; `lone` lets a source carry no target; `some` requires at least one target
per source; `set` adds no constraint. The convention engine then turns `one` and `lone` into a
foreign-key column on the source table, and `some` and `set` into a junction table.

## Non-determinism

Some values are fixed only at runtime: a fresh short code, a timestamp, the next id. The language
states them by constraint rather than by formula. A fresh value is existential, so `code not in
pre(store)` says an unused code exists without naming it, and the synthesizer or convention engine
chooses how to produce one (a hash, a counter, a random draw) while the verifier confirms that any
choice meets the postcondition. A timestamp comes from the built-in `now()`, which the verifier
treats as a fresh value no earlier than any previous one and which a controllable clock supplies in
tests. A sequential id is `max(dom(posts)) + 1`, which the convention engine maps to an
auto-increment column: the spec fixes the property, fresh and sequential, and leaves the mechanism to
codegen.
