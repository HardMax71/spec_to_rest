---
title: "What the parser must handle"
description: "The constructs the spec language has to parse"
---

## 1. What our spec language needs

From the grammar design in `01_spec_language_design.md`, our DSL requires these core constructs that
any reused parser must handle or be extended to handle:

| Construct                     | Description                                             | Example                                             |
| ----------------------------- | ------------------------------------------------------- | --------------------------------------------------- |
| **Entity declarations**       | Typed fields with multiplicities and inline constraints | `entity User { name: String where len(name) >= 1 }` |
| **State declarations**        | Mutable service state as typed collections              | `state { users: Set[User] }`                        |
| **Operations with contracts** | Input/output + requires/ensures clauses                 | `operation Create { requires: ... ensures: ... }`   |
| **Invariants and facts**      | Global constraints that must always hold                | `invariant: all u in users => len(u.email) > 0`     |
| **Transition declarations**   | Entity lifecycle state machines                         | `Active -> Suspended via Suspend when ...`          |
| **Convention blocks**         | HTTP mapping overrides                                  | `conventions { Resolve.http_method = "GET" }`       |
| **Multiplicity annotations**  | `one`, `lone`, `some`, `set` on relations               | `owner: one User`                                   |
| **Quantified expressions**    | `all`, `exists`, `no` with set comprehension            | `all u in users => u.email != ""`                   |
| **Primed variables**          | After-state references                                  | `users' = users + {newUser}`                        |
| **Relational types**          | Typed relations with multiplicity                       | `mapping: ShortCode -> lone LongURL`                |
| **Refinement types**          | Types with constraint predicates                        | `type Email = String where matches(email_regex)`    |
| **Cardinality operator**      | `#` for collection sizes                                | `#users' = #users + 1`                              |
| **Set/Map/Seq collections**   | Parameterized collection types                          | `Set[User]`, `Map[String, Int]`, `Seq[Event]`       |
| **Enum declarations**         | Finite value types for states                           | `enum Status { Active, Suspended, Deleted }`        |

The key insight from the prior survey: **no existing language provides all of these**. Our DSL
uniquely combines Alloy's relational modeling and multiplicities, VDM/Dafny's pre/postcondition
syntax, Quint's developer-friendly syntax, and TypeSpec/Smithy's HTTP override capability.
