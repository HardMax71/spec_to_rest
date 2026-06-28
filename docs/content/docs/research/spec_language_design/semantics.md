---
title: "Semantic model"
description: "State, pre and primed values, quantifiers, set and relation operations, and multiplicities"
---

### 3.1 What is "State"?

State is a **snapshot of all declared state fields at a point in time**. Formally:

```text
State = { field_name -> value | field_name in state_decl.fields }
```

A state field holds a value of its declared type. For relation types (e.g.,
`store: ShortCode -> lone LongURL`), the value is a set of tuples. For simple types, the value is a
single element.

The system begins in the **initial state** where all relation-typed fields are empty sets/maps and
all scalar fields hold their type's default value (empty string, 0, false, epoch time).

Each operation transforms one state into another. The sequence of states forms a **trace**:
`S_0, S_1, S_2, ...` where `S_0` is the initial state and each subsequent state is the result of
applying an operation.

### 3.2 What does `pre(store)` mean?

Within an `ensures` clause, `pre(store)` refers to **the value of `store` in the state immediately
before the operation executed**. It is equivalent to `old(store)` in Dafny or `store~` in VDM-SL.

```text
operation Shorten {
  ensures:
    code not in pre(store)    // code was NOT in store before this operation
    store'[code] = url        // code IS in store after this operation
}
```

The `pre()` function can wrap any state field name. It is only valid inside `ensures` clauses.
Inside `requires` clauses, all state field references implicitly refer to the pre-state (since the
requires clause is checked before the operation runs).

### 3.3 What does `store'` mean?

The prime notation `store'` refers to **the value of `store` in the state immediately after the
operation executes**. This is the post-state.

Formally, if operation `Op` transforms state `S` to state `S'`:

- `store` (unprimed, in requires) = `S.store`
- `pre(store)` (in ensures) = `S.store`
- `store'` (primed, in ensures) = `S'.store`

The ensures clause is a **predicate over the pre-state and post-state**. It must hold for the
operation to be correct.

In an `ensures` clause, an unprimed state field reference (`store`) refers to the **pre-state** (the
state before the operation), following the TLA+ convention. This means `pre(store)` and `store` are
synonymous in ensures clauses, `pre()` exists for readability when the intent might otherwise be
ambiguous.

```text
ensures:
  store'[code] = url          // post-state: code maps to url
  #store' = #store + 1        // post-size = pre-size + 1 (unprimed = pre-state)
  store' = store + {code -> url}  // post = pre + new mapping
```

### 3.4 How quantifiers work

Four quantifier forms:

| Quantifier        | Syntax                  | Meaning                                                                                    |
| ----------------- | ----------------------- | ------------------------------------------------------------------------------------------ |
| Universal         | `all x in S \| P(x)`    | For every element `x` in collection `S`, predicate `P(x)` holds                            |
| Existential       | `some x in S \| P(x)`   | There exists at least one element `x` in `S` where `P(x)` holds                            |
| Existential (alt) | `exists x in S \| P(x)` | Synonym for `some`                                                                         |
| Negated universal | `no x in S \| P(x)`     | There is no element `x` in `S` where `P(x)` holds (equivalent to `all x in S \| not P(x)`) |

The collection `S` can be any expression that evaluates to a set, sequence, map (iterates over
keys), or the domain/range of a relation.

Nested quantifiers are supported:

```text
all c in store | all d in store |
  (c != d) implies (store[c] != store[d])
// No two codes map to the same URL
```

### 3.5 How set operations work

| Operation      | Syntax                          | Meaning                            |
| -------------- | ------------------------------- | ---------------------------------- |
| Union          | `A + B` or `A union B`          | All elements in A or B             |
| Intersection   | `A & B` or `A intersect B`      | All elements in both A and B       |
| Difference     | `A - B` or `A minus B`          | Elements in A but not in B         |
| Cardinality    | `#A`                            | Number of elements in A            |
| Membership     | `x in A`                        | True if x is an element of A       |
| Non-membership | `x not in A`                    | True if x is not an element of A   |
| Subset         | `A subset B`                    | True if every element of A is in B |
| Empty check    | `#A = 0` or `no x in A \| true` | True if A has no elements          |

Set comprehensions create new sets:

```text
{ c in store | store[c].startsWith("https") }
// The set of all codes whose URLs start with https
```

### 3.6 How relation operations work

Relations are sets of tuples. A state field `store: ShortCode -> lone LongURL` is a set of
`(ShortCode, LongURL)` pairs.

| Operation                    | Syntax                  | Meaning                                                               |
| ---------------------------- | ----------------------- | --------------------------------------------------------------------- |
| Lookup                       | `store[code]`           | The LongURL associated with code (if multiplicity allows exactly one) |
| Domain                       | `dom(store)`            | The set of all ShortCodes that have mappings                          |
| Range                        | `ran(store)`            | The set of all LongURLs that are mapped to                            |
| Override                     | `store + {code -> url}` | Store with the code->url mapping added or replaced                    |
| Removal                      | `store - {code}`        | Store with the code mapping removed                                   |
| Restriction                  | `store \| S`            | Store restricted to codes in set S                                    |
| Composition                  | `R . S`                 | Relational join: `{(a,c) \| exists b: (a,b) in R and (b,c) in S}`     |
| Transitive closure           | `^R`                    | R composed with itself until fixed point                              |
| Reflexive-transitive closure | `*R`                    | `^R + identity`                                                       |

### 3.7 How multiplicities constrain relations

Multiplicities appear in relation type declarations and constrain how many target values each source
value can map to:

| Multiplicity | Meaning                        | SQL Analogy                | Example                            |
| ------------ | ------------------------------ | -------------------------- | ---------------------------------- |
| `one`        | Exactly one target per source  | `NOT NULL` foreign key     | `owner: User -> one Team`          |
| `lone`       | Zero or one target per source  | Nullable foreign key       | `store: ShortCode -> lone LongURL` |
| `some`       | One or more targets per source | Junction table, `NOT NULL` | `tags: Item -> some Tag`           |
| `set`        | Any number (zero or more)      | Junction table, nullable   | `followers: User -> set User`      |

Multiplicities generate proof obligations:

- `one`: every operation that adds to the source domain must also add a target
- `lone`: operations may leave the target as "none" for a source
- `some`: every operation that adds a source must add at least one target
- `set`: no constraints on the number of targets

When the convention engine maps these to a database schema:

- `one` and `lone` become foreign key columns on the source table
- `some` and `set` become junction tables with appropriate constraints
