---
title: "What the parser must handle"
description: "The constructs the spec language has to parse"
---

The spec language is small but specific, and the constructs it has to parse are the yardstick every
reuse candidate is measured against. They come straight from its
[grammar](/research/spec_language_design/grammar).

| Construct                 | What it is                                                      | Example                                                  |
| ------------------------- | -------------------------------------------------------------- | -------------------------------------------------------- |
| Entity declarations       | typed fields with multiplicities and inline `where` constraints | `entity UrlMapping { code: ShortCode where len(code) >= 6 }` |
| State declarations        | mutable service state as typed relations                       | `state { store: ShortCode -> lone LongURL }`             |
| Operations with contracts | input and output, plus requires and ensures                    | `operation Shorten { requires: ... ensures: ... }`       |
| Invariants and facts      | global constraints that always hold                            | `invariant: isValidURI(url)`                             |
| Transition declarations   | entity lifecycle state machines                                | `transition Lifecycle { Active -> Suspended via Suspend }` |
| Convention blocks         | HTTP mapping overrides                                          | `conventions { Resolve.http_method = "GET" }`            |
| Multiplicity annotations  | `one`, `lone`, `some`, `set` on relations                      | `tags: Post -> set Tag`                                  |
| Quantified expressions    | `all`, `some`, `no`, `exists` over a set                       | `all c in store \| store[c] != none`                     |
| Primed and pre-state      | after-state and before-state references                        | `store' = pre(store) + {code -> url}`                    |
| Relational types          | typed relations with a multiplicity                            | `mapping: ShortCode -> lone LongURL`                     |
| Refinement types          | a base type narrowed by a predicate                            | `type Email = String where value matches /.../`          |
| Cardinality operator      | `#` for collection size                                        | `#store' = #store + 1`                                   |
| Collection types          | parameterized `Set`, `Map`, `Seq`                              | `Set[UrlMapping]`, `Map[String, Int]`                    |
| Enum declarations         | finite value types                                             | `enum Status { Active, Suspended, Deleted }`             |

No single existing language offers all of them. The set borrows on purpose from several traditions:
Alloy's relational modeling and multiplicities, the pre- and postcondition style of VDM and Dafny,
Quint's readable surface syntax, and the HTTP-override idea from TypeSpec and Smithy. That spread is
exactly why reusing one parser wholesale is hard, and the next two pages weigh the attempt, the
formal-methods languages first, then the API description languages.
