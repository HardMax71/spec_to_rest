---
title: "Type system"
description: "Primitives, compounds, entity and relation types, refinements, expression types, and compatibility"
---

## Primitive types

| Type       | Values                       | Default | SQL                    | JSON                |
| ---------- | ---------------------------- | ------- | ---------------------- | ------------------- |
| `String`   | ASCII text                   | `""`    | `TEXT` or `VARCHAR(n)` | string              |
| `Int`      | arbitrary-precision integers | `0`     | `INTEGER` or `BIGINT`  | integer             |
| `Float`    | IEEE 754 double              | `0.0`   | `DOUBLE PRECISION`     | number              |
| `Bool`     | `true`, `false`              | `false` | `BOOLEAN`              | boolean             |
| `DateTime` | UTC timestamp                | epoch   | `TIMESTAMPTZ`          | string (ISO 8601)   |
| `Duration` | a span of time               | `0`     | `BIGINT` (seconds)     | integer             |
| `UUID`     | a UUID                       | none    | `UUID` or `TEXT`       | string              |

Underneath, the verifier's type system has four scalar types: `Bool`, `Int`, `Real`, and `String`.
`DateTime` and `Duration` are integers (an epoch and a count of seconds), and `UUID` is a string.
No primitive has subtypes, and any of them refines into a named, constrained type with a `where`
clause.

`String` values are ASCII. The verifier reasons over ASCII strings, so the parser rejects any
non-ASCII character in a string literal with a build-time diagnostic rather than carrying it into
verification or codegen.

## Compound types

`Set[T]` is an unordered collection of unique values; `Seq[T]` is ordered and may repeat; `Map[K, V]`
maps keys to values; `Option[T]` is a value or `none`.

```spec
tags:           Set[String]
login_attempts: Seq[LoginAttempt]
settings:       Map[String, String]
description:    Option[String]
```

A `Set[T]` becomes a JSON array, with uniqueness enforced in the application layer, backed by either
a JSON column or a junction table. A `Seq[T]` keeps an ordering column. An `Option[T]` is a nullable
column.

## Entity types and tables

Each `entity` maps to a database table:

| Entity feature           | Database mapping                                                  |
| ------------------------ | ----------------------------------------------------------------- |
| Entity name              | table name, pluralized and snake_case                             |
| Primitive field          | column                                                            |
| `Option[T]` field        | nullable column                                                   |
| `Set[T]` of a primitive  | JSON array column or junction table                               |
| `Set[T]` of an entity    | junction table                                                    |
| Entity-typed field       | foreign-key column                                                |
| `where` on a field       | `CHECK` constraint                                                |
| Entity `invariant`       | `CHECK` constraint where SQL can express it, else app validation  |

For example,

```spec
entity Todo {
  id: Int where value > 0
  title: String where len(value) >= 1 and len(value) <= 200
  status: Status
  tags: Set[String]
}
```

generates:

```sql
CREATE TABLE todos (
  id INTEGER PRIMARY KEY CHECK (id > 0),
  title VARCHAR(200) NOT NULL CHECK (length(title) >= 1),
  status VARCHAR(20) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','DONE','ARCHIVED')),
  tags JSONB DEFAULT '[]'
);
```

## Relation types and foreign keys

A relation type `A -> mult B` relates `A` and `B` with a multiplicity:

| Relation      | Database mapping                                                   |
| ------------- | ----------------------------------------------------------------- |
| `A -> one B`  | column on `A`: `b_id INTEGER NOT NULL REFERENCES b(id)`           |
| `A -> lone B` | nullable column on `A`: `b_id INTEGER REFERENCES b(id)`           |
| `A -> some B` | junction table with an at-least-one constraint (trigger or app)   |
| `A -> set B`  | junction table `a_b(a_id REFERENCES a, b_id REFERENCES b)`        |

A state relation maps the same way:

```spec
state {
  store: ShortCode -> lone LongURL
}
```

Whether that becomes one table or two joined by a foreign key depends on whether `ShortCode` and
`LongURL` are entities or value types.

## Parametric types

`Set[T]`, `Map[K, V]`, `Seq[T]`, and `Option[T]` are the only parametric types, and there are no
user-defined generics. Keeping the set closed means every type has a clear database and JSON mapping.

## Refinement types

A `where` clause refines a base type with a predicate that must always hold. Inside the clause,
`value` is the instance being constrained.

```spec
type ShortCode  = String where len(value) >= 6 and len(value) <= 10
                             and value matches /^[a-zA-Z0-9]+$/
type Money      = Int where value >= 0
type Percentage = Float where value >= 0.0 and value <= 100.0
type Email      = String where value matches /^[^@]+@[^@]+\.[^@]+$/
```

A refinement compiles to a `CHECK` constraint, request-body validation, the matching OpenAPI schema
constraint, and a runtime check. It is not a separate type from its base: `ShortCode` is a `String`
with extra constraints, so anything that takes a `String` takes a `ShortCode`. The reverse needs the
constraint proven or checked, since an arbitrary `String` is not a valid `ShortCode`.

## The type of an expression

Declarations carry explicit types, and each expression then has a determined type.

| Expression               | Type                                                |
| ------------------------ | --------------------------------------------------- |
| integer literal          | `Int`                                               |
| float literal            | `Float`                                             |
| string literal           | `String`                                            |
| boolean literal          | `Bool`                                              |
| a field reference        | the field's declared type                           |
| `entity.field`           | that field's type                                   |
| `store[key]`             | the relation's target type                          |
| `dom(rel)`               | a `Set` of the source type                          |
| `ran(rel)`               | a `Set` of the target type                          |
| `#collection`            | `Int`                                               |
| `a + b` on `Int`         | `Int`                                               |
| `a + b` on sets          | the same set type                                   |
| `a and b`                | `Bool`                                              |
| `x in S`                 | `Bool`                                              |
| `pre(field)`, `field'`   | the field's type                                    |
| `if c then a else b`     | the branches' shared type (numeric branches widen)  |
| a quantifier             | `Bool`                                              |

## Subtyping and compatibility

The type layer keeps a few compatibility rules rather than a full subtype lattice. A numeric value
widens: an `Int` is accepted where a `Float` is expected, never the reverse, since the verifier joins
`Int` and `Real` to `Real`. A refined type stands in for its base, because it is that base with a
constraint. A bare value is accepted where an `Option[T]` is expected, wrapped in `some`. Enums are
distinct types that do not widen into one another, and collections are invariant in their element
type, so a `Set[Child]` is not a `Set[Parent]`.

Entity inheritance is structural. `entity Child extends Parent` gives `Child` all of `Parent`'s
fields and invariants and lets it add more, but the type layer treats each entity as its own type, so
substituting a `Child` for a `Parent` is not something it checks.

Type checking is partial today. The `check` command runs a narrow structural lint, `L01`, that
catches a literal used at the wrong type; a full type checker is future work.
