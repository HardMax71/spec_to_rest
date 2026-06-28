---
title: "Errors and developer experience"
description: "What the checker catches, and IDE support considerations"
---

### 6.1 Categories of errors the spec checker can catch

The spec checker validates the specification before any code generation. It detects errors in
several categories:

#### Category 1: syntax errors

Standard parse errors. The parser reports the exact location and suggests corrections.

```text
error[E001]: Expected ':' after field name
  --> url_shortener.spec:12:15
   |
12 |     code ShortCode
   |          ^^^^^^^^^^ expected ':' here
   |
help: add a colon between the field name and type
   |
12 |     code: ShortCode
   |         +
```

#### Category 2: type errors

Type mismatches in expressions, wrong argument types, missing fields.

```text
error[E101]: Type mismatch in ensures clause
  --> url_shortener.spec:28:7
   |
28 |     code not in pre(store)
   |     ^^^^ expected ShortCode, found LongURL
   |
note: 'code' is declared as type LongURL in the output clause (line 24)
note: 'store' maps ShortCode -> LongURL, so membership test requires ShortCode
help: did you mean to check the output 'code' field? check the output type declaration
```

#### Category 3: multiplicity violations

Operations that violate the declared multiplicity constraints.

```text
error[E201]: Multiplicity violation in ensures clause
  --> url_shortener.spec:30:7
   |
30 |     store'[code] = url1
31 |     store'[code] = url2
   |     ^^^^^^^^^^^^^^^^^^ 'store' has multiplicity 'lone' (at most one target per source)
   |     but postcondition assigns two different values for the same key
   |
note: store is declared as 'ShortCode -> lone LongURL' at line 18
note: 'lone' means each ShortCode maps to at most one LongURL
```

#### Category 4: unreachable operations

Operations whose preconditions can never be satisfied given the invariants and other operations.

```text
warning[W301]: Operation 'Resolve' may be unreachable
  --> url_shortener.spec:33:3
   |
33 |   operation Resolve {
   |   ^^^^^^^^^^^^^^^^^
   |
note: Resolve requires 'code in store' (line 37)
note: No operation in this service adds entries to 'store'
      (Shorten's ensures clause does not establish 'code in store' for arbitrary codes)
help: verify that at least one operation's postcondition establishes the precondition
```

#### Category 5: conflicting invariants

Invariants that cannot all hold simultaneously.

```text
error[E401]: Conflicting invariants detected
  --> order_service.spec:145:3
   |
145 |   invariant: all oid in orders | #orders[oid].items > 0
   |   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
   |
  --> order_service.spec:48:7
   |
48  |     order.items = {}      // CreateDraftOrder ensures empty items
   |     ^^^^^^^^^^^^^^^^
   |
note: Invariant on line 145 requires all orders to have at least one item
note: But CreateDraftOrder (line 42) creates orders with empty items
note: This means the invariant is violated immediately after CreateDraftOrder
help: Either weaken the invariant to exclude DRAFT orders:
   |
145 |   invariant: all oid in orders |
   |     orders[oid].status != DRAFT implies #orders[oid].items > 0
   |
      Or change CreateDraftOrder to require at least one item
```

#### Category 6: incomplete frame conditions

Operations that do not specify what happens to all state fields.

```text
warning[W501]: Incomplete frame condition in operation 'Shorten'
  --> url_shortener.spec:25:3
   |
25 |   operation Shorten {
   |   ^^^^^^^^^^^^^^^^^
   |
note: State has fields: store, metadata, base_url
note: Shorten's ensures clause mentions: store', metadata'
note: Shorten's ensures clause does NOT mention: base_url
help: If base_url should not change, add: base_url' = base_url
      If this omission is intentional, the checker assumes unchanged fields
      remain unchanged (closed-world assumption)
```

#### Category 7: postcondition not achievable

The postcondition cannot be satisfied given the precondition.

```text
error[E601]: Postcondition may not be achievable
  --> url_shortener.spec:29:7
   |
29 |     #store' = #pre(store) + 2
   |     ^^^^^^^^^^^^^^^^^^^^^^^^^
   |
note: Operation Shorten adds exactly one mapping (store' = pre(store) + {code -> url})
note: But the cardinality postcondition claims two entries were added
note: Counterexample: pre(store) = {}, after adding one mapping, #store' = 1, not 2
help: Did you mean #store' = #pre(store) + 1?
```

#### Category 8: transition violations

State machine transitions that violate the declared transition rules.

```text
error[E701]: Invalid state transition
  --> todo_service.spec:62:7
   |
62 |     todo = pre(todos)[id] with { status = DONE }
   |                                          ^^^^
   |
note: Operation 'StartWork' transitions status from TODO
note: But DONE is not a valid target from TODO
note: Valid transitions from TODO: IN_PROGRESS (via StartWork), ARCHIVED (via Archive)
note: Defined in transition 'TodoLifecycle' at line 38
help: Did you mean status = IN_PROGRESS?
```

### 6.2 IDE support considerations

The spec language should support modern IDE features via a Language Server Protocol (LSP)
implementation.

#### Syntax highlighting

Token categories for TextMate/tree-sitter grammars:

- `keyword.control`, `service`, `entity`, `operation`, `requires`, `ensures`, `invariant`,
  `state`, `transition`, `conventions`
- `keyword.operator`, `and`, `or`, `not`, `implies`, `iff`, `in`, `all`, `some`, `no`, `exists`
- `storage.type`, `String`, `Int`, `Bool`, `Float`, `DateTime`, `Set`, `Map`, `Seq`, `Option`
- `storage.modifier`, `one`, `lone`, `some`, `set`
- `entity.name.type`, user-defined entity names and type aliases
- `entity.name.function`, operation names
- `variable.other`, field names, parameter names
- `string.quoted.double`, string literals
- `constant.numeric`, integer and float literals
- `constant.language`, `true`, `false`, `none`
- `comment.line`, `//` comments
- `comment.block`, `/* */` comments

#### Auto-complete suggestions

| Context                         | Suggestions                                                                                      |
| ------------------------------- | ------------------------------------------------------------------------------------------------ |
| After `requires:` or `ensures:` | State field names, input/output parameter names, built-in functions                              |
| After a state field name        | `.` for field access, `'` for prime, `[` for index                                               |
| After `->` in type expr         | Multiplicity keywords (`one`, `lone`, `some`, `set`)                                             |
| Inside `conventions { }`        | Operation names, convention properties                                                           |
| After `in`                      | State fields that are collections, `dom()`, `ran()`                                              |
| After `entity` keyword          | `extends` for inheritance                                                                        |
| Top level of service            | `entity`, `enum`, `type`, `state`, `operation`, `transition`, `invariant`, `fact`, `conventions` |

#### Diagnostics

Real-time type checking and invariant consistency checking as the user types. Squiggly underlines
for errors and warnings. Quick-fix suggestions for common mistakes.

#### Go-to-definition

Click on a type name to jump to its entity/type declaration. Click on a state field to jump to its
declaration. Click on an operation name in a transition rule to jump to the operation.

#### Hover information

Hovering over a state field shows its type and the invariants that mention it. Hovering over an
operation shows a summary: inputs, outputs, what state it reads and writes. Hovering over a
multiplicity keyword shows a plain-English explanation.

#### Code lenses

Above each operation: "2 invariants apply" (clickable to list them). Above each invariant: "Checked
by operations: Shorten, Delete" (clickable). Above each transition: a visual state machine diagram
rendered inline.
