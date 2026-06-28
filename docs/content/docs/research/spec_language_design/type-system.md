---
title: "Type system"
description: "Primitives, compounds, entity and relation types, refinements, inference, and subtyping"
---

### 5.1 Primitive types and their constraints

| Type       | Values                       | Default | SQL Mapping                | JSON Mapping        |
| ---------- | ---------------------------- | ------- | -------------------------- | ------------------- |
| `String`   | Unicode text                 | `""`    | `TEXT` or `VARCHAR(n)`     | `string`            |
| `Int`      | Arbitrary-precision integers | `0`     | `INTEGER` or `BIGINT`      | `integer`           |
| `Float`    | IEEE 754 double              | `0.0`   | `DOUBLE PRECISION`         | `number`            |
| `Bool`     | `true`, `false`              | `false` | `BOOLEAN`                  | `boolean`           |
| `DateTime` | UTC timestamp                | epoch   | `TIMESTAMP WITH TIME ZONE` | `string` (ISO 8601) |

Primitive types have no subtypes. Each primitive can be refined with a `where` clause to create a
named constrained type.

### 5.2 Compound types

`Set[T]` is an unordered collection of unique values of type T.

```text
tags: Set[String]           // SQL: junction table or JSON array
                            // JSON: array (unique values enforced at app layer)
```

`Seq[T]` is an ordered sequence of values of type T and may contain duplicates.

```text
login_attempts: Seq[LoginAttempt]   // SQL: table with ordering column
                                    // JSON: array
```

`Map[K, V]` is a mapping from keys of type K to values of type V.

```text
settings: Map[String, String]       // SQL: key-value table or JSON column
                                    // JSON: object
```

`Option[T]` is either a value of type T or `none`.

```text
description: Option[String]         // SQL: nullable column
                                    // JSON: field may be absent or null
```

### 5.3 Entity types and database tables

Each `entity` declaration maps to a database table. The mapping follows these rules:

| Entity Feature                           | Database Mapping                                                           |
| ---------------------------------------- | -------------------------------------------------------------------------- |
| Entity name                              | Table name (pluralized, snake_case)                                        |
| Field with primitive type                | Column                                                                     |
| Field with `Option[T]`                   | Nullable column                                                            |
| Field with `Set[T]` where T is primitive | JSON array column or junction table                                        |
| Field with `Set[T]` where T is entity    | Junction table                                                             |
| Field with entity type                   | Foreign key column                                                         |
| `where` clause on field                  | `CHECK` constraint                                                         |
| Entity `invariant`                       | `CHECK` constraint (if expressible in SQL) or application-level validation |

Example:

```text
entity Todo {
  id: Int where value > 0
  title: String where len(value) >= 1 and len(value) <= 200
  status: Status
  tags: Set[String]
}
```

Generates:

```sql
CREATE TABLE todos (
  id INTEGER PRIMARY KEY CHECK (id > 0),
  title VARCHAR(200) NOT NULL CHECK (length(title) >= 1),
  status VARCHAR(20) NOT NULL CHECK (status IN ('TODO','IN_PROGRESS','DONE','ARCHIVED')),
  tags JSONB DEFAULT '[]'
);
```

### 5.4 Relation types and database foreign keys

A relation type `A -> mult B` declares a relationship between type A and type B with the given
multiplicity.

| Relation Type | Database Mapping                                                   |
| ------------- | ------------------------------------------------------------------ |
| `A -> one B`  | Column on A's table: `b_id INTEGER NOT NULL REFERENCES b(id)`      |
| `A -> lone B` | Column on A's table: `b_id INTEGER REFERENCES b(id)` (nullable)    |
| `A -> some B` | Junction table with at least-one constraint (trigger or app-level) |
| `A -> set B`  | Junction table: `a_b(a_id REFERENCES a, b_id REFERENCES b)`        |

State-level relations map to the core data model:

```text
state {
  store: ShortCode -> lone LongURL
}
```

This creates either a single table with both columns or two tables with a foreign key, depending on
whether ShortCode and LongURL are entities or value types.

### 5.5 Parametric types

The type system supports parameterized types:

```text
Set[T]          -- for any type T
Map[K, V]       -- for any types K, V
Seq[T]          -- for any type T
Option[T]       -- for any type T
```

These are the only parametric types in the language. Users cannot define their own generic types.
This keeps the type system simple and ensures every type has a clear database and JSON mapping.

### 5.6 Refinement types (constrained types)

The `where` clause creates a refinement type, a base type with an additional predicate that must
always hold:

```text
type ShortCode = String where len(value) >= 6 and len(value) <= 10
                            and value matches /^[a-zA-Z0-9]+$/

type Money = Int where value >= 0

type Percentage = Float where value >= 0.0 and value <= 100.0

type Email = String where value matches /^[^@]+@[^@]+\.[^@]+$/
```

Within the `where` clause, `value` refers to the instance being constrained.

Refinement types generate:

- Database: `CHECK` constraints
- API validation: Request body validation rules (e.g., JSON Schema `pattern`, `minimum`,
  `maximum`)
- OpenAPI: Corresponding schema constraints
- Runtime: Validation functions that throw on violation

Refinement types are **not** separate types from their base, a `ShortCode` is a `String` with
extra constraints. Any function that accepts `String` also accepts `ShortCode`. But a function that
requires `ShortCode` does not accept an arbitrary `String` (the constraint must be proven or checked
at runtime).

### 5.7 Type inference rules

The spec language uses explicit types in declarations but infers types in expressions:

| Expression                  | Inferred Type                                                                  |
| --------------------------- | ------------------------------------------------------------------------------ |
| Integer literal             | `Int`                                                                          |
| Float literal               | `Float`                                                                        |
| String literal              | `String`                                                                       |
| Boolean literal             | `Bool`                                                                         |
| `field_name` (in operation) | Type of the field from state declaration                                       |
| `entity.field`              | Type of that field in the entity                                               |
| `store[key]`                | Target type of the relation (e.g., `LongURL` from `ShortCode -> lone LongURL`) |
| `dom(rel)`                  | `Set[SourceType]`                                                              |
| `ran(rel)`                  | `Set[TargetType]`                                                              |
| `#collection`               | `Int`                                                                          |
| `a + b` (both Int)          | `Int`                                                                          |
| `a + b` (sets)              | Same set type                                                                  |
| `a and b`                   | `Bool`                                                                         |
| `x in S`                    | `Bool`                                                                         |
| `pre(field)`                | Same type as `field`                                                           |
| `field'`                    | Same type as `field`                                                           |
| `if c then a else b`        | Least common supertype of a and b                                              |
| Quantifier expression       | `Bool`                                                                         |

### 5.8 Subtyping and compatibility rules

The type system has a simple subtyping hierarchy:

1. Refinement subtyping: `type ShortCode = String where P` means `ShortCode <: String`. Any
   `ShortCode` value can be used where a `String` is expected, but not vice versa.

2. Option subtyping: `T <: Option[T]`, any value of type T can be used where `Option[T]` is
   expected (it is implicitly wrapped in `some`).

3. Enum subtyping: Enum types do not participate in subtyping. Each enum is a distinct type.

4. Entity subtyping: `entity Child extends Parent` means `Child <: Parent`. A `Child` value can
   be used where a `Parent` is expected. Child inherits all fields and invariants, and may add new
   ones.

5. Collection subtyping: Collections are invariant in their type parameter. `Set[Child]` is
   NOT a subtype of `Set[Parent]`. This prevents runtime type errors.

6. Numeric compatibility: `Int` values can be used where `Float` is expected (widening), but not
   vice versa.

Type errors are reported at spec-check time, before any code generation occurs.
