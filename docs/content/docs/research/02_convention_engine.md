---
title: "Convention Engine"
description: "Mapping abstract spec concepts to REST, HTTP, and DB conventions"
---

> The Convention Engine is the central component of the spec-to-REST compiler. It takes an abstract
> formal specification (entities, state, operations, invariants) and produces concrete
> infrastructure decisions: HTTP endpoints, database schemas, validation logic, error responses, and
> serialization formats. This document defines every rule, every edge case, and every override
> mechanism.

---

## Table of Contents

1. [Design Philosophy](#1-design-philosophy)
2. [The Complete Convention Ruleset](#2-the-complete-convention-ruleset)
   - 2.1 HTTP Method Mapping
   - 2.2 URL Path Mapping
   - 2.3 HTTP Status Code Mapping
   - 2.4 Request/Response Body Mapping
   - 2.5 Database Schema Mapping
   - 2.6 Validation Mapping
   - 2.7 Error Response Mapping
3. [Convention Override System](#3-convention-override-system)
4. [Worked Examples](#4-worked-examples)
   - 4.1 URL Shortener
   - 4.2 E-commerce Order Service
   - 4.3 Social Media Feed
5. [Edge Cases and Ambiguities](#5-edge-cases-and-ambiguities)
6. [Convention Profiles (Deployment Targets)](#6-convention-profiles-deployment-targets)
7. [Comparison with Existing Convention Systems](#7-comparison-with-existing-convention-systems)
8. [Implementation Architecture](#8-implementation-architecture)

---

## 1. Design Philosophy

The Convention Engine inherits the central insight from Alchemy (2008): state-change predicates map
to write operations, and facts map to integrity constraints. We generalize this beyond databases to
the full REST + DB + validation + serialization stack.

**Core principles:**

1. **Every spec element maps to exactly one infrastructure artifact** -- no ambiguity, no manual
   wiring. An entity becomes a table, a mutation becomes a POST/PUT/DELETE, a precondition becomes a
   validation check, an invariant becomes a constraint.

2. **Conventions are deterministic** -- given the same spec, the engine always produces the same
   output. There is no randomness, no heuristic guessing.

3. **Conventions are overridable** -- every decision the engine makes can be overridden by the user
   via the `conventions` block. The engine provides sensible defaults; the user adjusts what doesn't
   fit.

4. **Conventions are target-agnostic in the abstract, target-specific in the concrete** -- the
   abstract mapping (operation -> HTTP method) is universal. The concrete rendering (Python class vs
   Go struct vs Java interface) varies by deployment profile.

5. **When in doubt, follow RFC 7231 and REST best practices** -- the engine does not invent novel
   HTTP semantics. It maps to well-understood patterns.

---

## 2. The Complete Convention Ruleset

### 2.1 HTTP Method Mapping

The engine determines the HTTP method for each operation by analyzing its effect on state. The
decision tree is evaluated top-to-bottom; the first matching rule wins.

#### Rule Table

| #   | Condition                                                                                                       | HTTP Method                                                                                                                 | Rationale                                                                                      |
| --- | --------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| M1  | Operation mutates state AND creates a new entity (new key appears in a state relation)                          | **POST**                                                                                                                    | RFC 7231: POST processes representations; creating a subordinate resource is the canonical use |
| M2  | Operation reads state, no mutation (`ensures: state' = state` or state is not referenced in ensures)            | **GET**                                                                                                                     | RFC 7231: GET retrieves a representation; safe and idempotent                                  |
| M3  | Operation mutates an existing entity AND every field of the entity appears in the ensures clause                | **PUT**                                                                                                                     | RFC 7231: PUT replaces the current representation entirely                                     |
| M4  | Operation mutates an existing entity AND only a subset of fields appear in the ensures clause                   | **PATCH**                                                                                                                   | RFC 5789: PATCH applies partial modifications                                                  |
| M5  | Operation removes an entity from state (key disappears from a state relation)                                   | **DELETE**                                                                                                                  | RFC 7231: DELETE removes the target resource                                                   |
| M6  | Operation mutates state AND creates a child entity under an existing parent                                     | **POST**                                                                                                                    | POST to the parent's sub-collection (e.g., POST /orders/{id}/items)                            |
| M7  | Operation reads state with complex filtering inputs (more than 3 filter parameters or structured filter object) | **GET** with query params (preferred) or **POST** to a `/search` sub-resource if query string would exceed practical limits | Pragmatic: GET is semantically correct for reads, but URL length limits exist                  |
| M8  | Operation has side effects but no state mutation (e.g., sends email, triggers webhook)                          | **POST**                                                                                                                    | POST is the catch-all for unsafe operations that don't fit other methods                       |
| M9  | Operation performs a batch mutation on multiple entities simultaneously                                         | **POST** to a `/batch` sub-resource                                                                                         | Batch operations don't map cleanly to single-resource methods                                  |
| M10 | Operation performs a state machine transition on an existing entity                                             | **POST** to an action sub-resource (e.g., POST /orders/{id}/place)                                                          | State transitions are commands, not CRUD; POST to a verb endpoint is the standard REST pattern |

#### PUT vs PATCH Disambiguation

The distinction between PUT (M3) and PATCH (M4) requires analyzing the ensures clause field
coverage:

```
Given entity E with fields {f1, f2, f3, f4}
Given operation Op that mutates an existing E

If Op.ensures references assignments to ALL of {f1, f2, f3, f4}:
    -> PUT (full replacement)

If Op.ensures references assignments to a STRICT SUBSET of {f1, f2, f3, f4}:
    -> PATCH (partial update)

If Op.ensures references assignments to ALL fields BUT some are
  conditional (if-then in ensures):
    -> PATCH (conditional update is partial by nature)
```

Example:

```
operation UpdateUser {
  input: id: UserId, name: String, email: String, bio: String
  requires: id in users
  ensures:
    users'[id].name = name
    users'[id].email = email
    users'[id].bio = bio
    users'[id].created_at = users[id].created_at  // unchanged
}
// All fields accounted for -> PUT
```

```
operation UpdateUserEmail {
  input: id: UserId, email: String
  requires: id in users
  ensures:
    users'[id].email = email
    // other fields implicitly unchanged
}
// Only email changes -> PATCH
```

#### Operations That Don't Fit

**Read-with-body (search):** When an operation reads state but requires a complex structured input
(e.g., a search query with nested filters, geo-bounding boxes, or full-text search parameters), the
engine applies rule M7. It first attempts to flatten the input into query parameters. If the input
contains nested structures, arrays of objects, or would produce a query string exceeding 2048
characters, it falls back to `POST /{resource}/search` with the input as the request body.

**Side-effect-only operations:** Operations that have side effects external to the modeled state
(sending emails, triggering webhooks, publishing events) are recognized by the pattern: the ensures
clause either leaves state unchanged or the mutation is to an audit/log relation. These always map
to POST (rule M8).

**Batch operations:** Operations whose input includes a collection of entities (e.g.,
`input: items: Set[OrderItem]`) or whose ensures clause modifies multiple keys in a state relation
trigger rule M9.

### 2.2 URL Path Mapping

#### Resource Name Derivation

| Spec Element                                         | URL Path Segment                               | Rule                                                                                      |
| ---------------------------------------------------- | ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Entity name (singular)                               | Pluralized, lowercased, hyphenated             | `OrderItem` -> `order-items`                                                              |
| State relation name (if it differs from the entity)  | Ignored for path; entity name takes precedence | `store: ShortCode -> LongURL` uses `short-codes` not `store`                              |
| Operation that acts on a specific entity by ID       | `/{resource}/{id}`                             | Input with the entity's key type -> path parameter                                        |
| Operation that acts on a collection                  | `/{resource}`                                  | No key-type input or input is a filter                                                    |
| Operation that acts on a child entity under a parent | `/{parent}/{parent_id}/{children}`             | Detected when ensures clause creates/reads/deletes in a relation anchored to a parent key |

#### Pluralization Rules

The engine uses a standard English pluralization algorithm (equivalent to Rails'
`ActiveSupport::Inflector`) with a manually curated exception table:

| Singular    | Plural        | Rule Applied                             |
| ----------- | ------------- | ---------------------------------------- |
| `User`      | `users`       | Regular: append -s                       |
| `Category`  | `categories`  | -y to -ies                               |
| `Address`   | `addresses`   | -s to -es                                |
| `Person`    | `people`      | Irregular                                |
| `Status`    | `statuses`    | -us to -uses                             |
| `Inventory` | `inventory`   | Uncountable (no pluralization)           |
| `ShortCode` | `short-codes` | CamelCase split + pluralize last segment |

Users can override pluralization via the conventions block if the algorithm gets it wrong (e.g.,
`conventions { ShortCode.plural = "codes" }`).

#### Nested Resource Detection

The engine detects parent-child relationships from the state model:

```
state {
  orders: OrderId -> Order
  line_items: OrderId -> set LineItem   // LineItem is nested under Order
}
```

The relation `line_items: OrderId -> set LineItem` signals that LineItem is a child of Order because
the relation key is `OrderId`. This produces:

- `GET /orders/{order_id}/line-items` -- list items for an order
- `POST /orders/{order_id}/line-items` -- add item to an order
- `GET /orders/{order_id}/line-items/{item_id}` -- get specific item
- `DELETE /orders/{order_id}/line-items/{item_id}` -- remove item

**Nesting depth limit:** The engine nests at most 2 levels deep. If a relation chain goes deeper
(e.g., Order -> LineItem -> LineItemOption), the third level gets a top-level resource with a query
parameter filter: `GET /line-item-options?line_item_id={id}` rather than
`GET /orders/{oid}/line-items/{lid}/options`.

#### Path Parameter Extraction

An operation input becomes a path parameter when:

1. Its type matches the key type of a state relation (e.g., `input: id: OrderId` where
   `orders: OrderId -> Order`), AND
2. The operation reads, mutates, or deletes a specific entity identified by that key.

All other inputs become either query parameters (for GET) or body fields (for POST/PUT/PATCH).

#### Query Parameter Mapping for Filters

For collection-read operations (GET on a plural resource), the engine inspects the operation's input
fields and maps them:

| Input Characteristic                                        | Mapping                                     |
| ----------------------------------------------------------- | ------------------------------------------- |
| Field with same name/type as an entity field                | `?field=value` (exact match filter)         |
| Field named `{entity_field}_min` or `{entity_field}_max`    | `?field_min=X&field_max=Y` (range filter)   |
| Field named `query` or `search` of type String              | `?q=value` (full-text search)               |
| Field named `page` or `offset` of type Int                  | `?page=N` or `?offset=N` (pagination)       |
| Field named `limit` or `page_size` of type Int              | `?limit=N` (page size, default 20, max 100) |
| Field named `sort_by` of type String                        | `?sort_by=field` (sort column)              |
| Field named `sort_order` or `order` of type Enum{asc, desc} | `?sort_order=asc` (sort direction)          |

If no explicit pagination fields exist in the operation input, the engine injects default pagination
parameters: `page` (default 1) and `limit` (default 20, max 100).

### 2.3 HTTP Status Code Mapping

#### Success Status Codes

| Condition                                                      | Status Code                                                        | Headers                          |
| -------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------------------- |
| POST that creates a new entity                                 | **201 Created**                                                    | `Location: /{resource}/{new_id}` |
| GET that returns data                                          | **200 OK**                                                         | --                               |
| PUT/PATCH that updates an entity                               | **200 OK** (if response body) or **204 No Content** (if no output) | --                               |
| DELETE that removes an entity                                  | **204 No Content**                                                 | --                               |
| POST action that triggers a side effect                        | **200 OK** (if response body) or **202 Accepted** (if async)       | --                               |
| Operation with redirect semantics (overridden via conventions) | **302 Found**                                                      | `Location: {target_url}`         |

#### Error Status Codes from Requires Clauses

The engine analyzes each `requires` clause to determine the appropriate error status code. The
analysis is pattern-based:

| Requires Pattern                                                    | Status Code                                                                             | Rationale                                                      |
| ------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `{key} in {state_relation}` (entity existence check)                | **404 Not Found**                                                                       | The identified resource does not exist                         |
| `{key} not in {state_relation}` (uniqueness check)                  | **409 Conflict**                                                                        | The resource already exists; creating it would conflict        |
| `{field} {comparison} {value}` (value validation)                   | **422 Unprocessable Entity**                                                            | The input is syntactically valid JSON but semantically invalid |
| `isValidURI(...)`, `len(...) >= N`, regex match (format validation) | **422 Unprocessable Entity**                                                            | Input fails format/structural validation                       |
| `{entity}.status = {expected_state}` (state machine guard)          | **409 Conflict**                                                                        | The operation cannot be performed in the current state         |
| `{input1} != {input2}` (relational constraint between inputs)       | **422 Unprocessable Entity**                                                            | Inputs are individually valid but invalid in combination       |
| Boolean combination of the above (`and`, `or`)                      | Use the **highest priority** code from the sub-clauses. Priority: 404 > 409 > 422 > 400 | Compound preconditions use the most specific applicable code   |

**Priority logic for compound requires clauses:**

When a requires clause is a conjunction (`and`), the engine generates checks in order and returns
the first failure. When it is a disjunction (`or`), the engine returns an error only if ALL
disjuncts fail, using the code of the most "specific" failing clause (404 > 409 > 422).

Example:

```
requires:
  id in orders                           // -> 404 if fails
  and orders[id].status = "draft"        // -> 409 if fails
  and total > 0                          // -> 422 if fails
```

The engine generates three sequential checks. If `id` is not in orders, return 404. If the order
exists but status is not "draft", return 409. If total is not positive, return 422.

#### Error Status Codes from Invariant Violations

| Invariant Type                                                   | Status Code                  | When                                          |
| ---------------------------------------------------------------- | ---------------------------- | --------------------------------------------- |
| Entity field constraint (e.g., `len(value) >= 6`)                | **422 Unprocessable Entity** | Violated during input validation (pre-DB)     |
| Uniqueness constraint (detected from `state: Key -> lone Value`) | **409 Conflict**             | Violated at DB level (duplicate key)          |
| Cross-entity invariant (e.g., `inventory >= 0 after order`)      | **409 Conflict**             | Violated at application level (business rule) |
| Database CHECK constraint violation                              | **409 Conflict**             | Caught from DB exception                      |

### 2.4 Request/Response Body Mapping

#### Input-to-Request Mapping

| Input Characteristic                                           | Placement                                      | Format                              |
| -------------------------------------------------------------- | ---------------------------------------------- | ----------------------------------- |
| Key type matching a state relation (entity ID)                 | Path parameter                                 | `/{resource}/{id}`                  |
| Primitive type on a GET operation                              | Query parameter                                | `?name=value`                       |
| Complex/nested type on a GET operation                         | Query parameter (dot-notation) or POST /search | `?filter.status=active`             |
| Any type on a POST/PUT/PATCH operation (excluding path params) | Request body (JSON)                            | `{"field": value}`                  |
| File or binary type                                            | Multipart form data (POST/PUT only)            | `Content-Type: multipart/form-data` |

#### Output-to-Response Mapping

| Output Characteristic       | Response Format                                                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Single entity               | `{"data": {entity_fields...}, "meta": {...}}`                                                                             |
| Collection of entities      | `{"data": [{...}, {...}], "meta": {"page": 1, "limit": 20, "total": N}}`                                                  |
| Single scalar value         | `{"data": value}`                                                                                                         |
| No output (void)            | Empty body, 204 No Content                                                                                                |
| Entity with nested children | Inline nested array: `{"data": {"id": 1, "items": [{...}]}}` up to 1 nesting level; deeper nesting returns IDs with links |

#### Naming Conventions

The engine uses **snake_case** for JSON field names by default (matching Python/Ruby conventions and
the majority of public REST APIs). This is configurable per profile:

| Profile                     | JSON Field Convention                       | Rationale                                               |
| --------------------------- | ------------------------------------------- | ------------------------------------------------------- |
| `python-fastapi-postgres`   | snake_case                                  | Python ecosystem standard                               |
| `go-chi-postgres`           | snake_case (JSON) / PascalCase (Go structs) | Go exports require PascalCase; JSON tags use snake_case |
| `typescript-express-prisma` | camelCase                                   | JavaScript ecosystem standard                           |
| `java-spring-jpa`           | camelCase                                   | Java/Jackson default                                    |

Conversion between spec field names (which are snake_case in the DSL) and the target naming
convention is handled by the code emitter, not the convention engine. The convention engine always
works in snake_case internally.

#### Envelope Format

All responses use a consistent envelope:

```json
{
  "data": <payload>,
  "meta": {
    "request_id": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

Error responses:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Human-readable description",
    "details": [
      {
        "field": "email",
        "constraint": "must be a valid email address",
        "value": "not-an-email"
      }
    ]
  },
  "meta": {
    "request_id": "uuid",
    "timestamp": "ISO-8601"
  }
}
```

The envelope is configurable. Users can override to use bare objects (no envelope), JSON:API format,
or a custom structure.

### 2.5 Database Schema Mapping

#### Entity-to-Table Mapping

| Spec Element                                                | Database Artifact                                        | Details                                     |
| ----------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------- |
| `entity Foo { ... }`                                        | Table `foos`                                             | Pluralized, snake_case                      |
| Entity field `name: String`                                 | Column `name TEXT`                                       | Type mapping table below                    |
| Entity field `age: Int`                                     | Column `age INTEGER`                                     | --                                          |
| Entity field with `invariant: len(x) >= 6 and len(x) <= 10` | Column with `CHECK (length(x) >= 6 AND length(x) <= 10)` | --                                          |
| Entity field with `invariant: x matches /^[a-z]+$/`         | Column with `CHECK (x ~ '^[a-z]+$')` (Postgres)          | Regex syntax is target-specific             |
| No explicit primary key declared                            | Auto-generated `id` column: `BIGSERIAL PRIMARY KEY`      | Convention: every table gets a surrogate PK |

#### Type Mapping

| Spec Type                   | PostgreSQL         | SQLite            | MySQL           |
| --------------------------- | ------------------ | ----------------- | --------------- |
| `String`                    | `TEXT`             | `TEXT`            | `VARCHAR(255)`  |
| `String` with `len(x) <= N` | `VARCHAR(N)`       | `TEXT`            | `VARCHAR(N)`    |
| `Int`                       | `INTEGER`          | `INTEGER`         | `INT`           |
| `Float`                     | `DOUBLE PRECISION` | `REAL`            | `DOUBLE`        |
| `Bool`                      | `BOOLEAN`          | `INTEGER`         | `TINYINT(1)`    |
| `DateTime`                  | `TIMESTAMPTZ`      | `TEXT` (ISO-8601) | `DATETIME`      |
| `Date`                      | `DATE`             | `TEXT`            | `DATE`          |
| `UUID`                      | `UUID`             | `TEXT`            | `CHAR(36)`      |
| `Decimal`                   | `NUMERIC(19,4)`    | `TEXT`            | `DECIMAL(19,4)` |
| `Bytes`                     | `BYTEA`            | `BLOB`            | `LONGBLOB`      |

#### Relation-to-Schema Mapping

This is the most critical mapping and follows directly from Alloy's multiplicity semantics:

| Relation Declaration          | Multiplicity                   | Schema Artifact                                                                                                          | Explanation                                     |
| ----------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| `r: A -> one B`               | Each A maps to exactly one B   | Column `b_id` in table `as`, `NOT NULL REFERENCES bs(id)`                                                                | Mandatory foreign key, every A must have a B    |
| `r: A -> lone B`              | Each A maps to at most one B   | Column `b_id` in table `as`, `REFERENCES bs(id)` (nullable)                                                              | Optional foreign key, A may or may not have a B |
| `r: A -> some B`              | Each A maps to one or more Bs  | Junction table `a_b_r(a_id REFERENCES as, b_id REFERENCES bs)` with trigger or CHECK ensuring `COUNT(*) >= 1` per `a_id` | M:N with minimum cardinality 1                  |
| `r: A -> set B`               | Each A maps to zero or more Bs | Junction table `a_b_r(a_id REFERENCES as, b_id REFERENCES bs)`                                                           | Standard M:N junction table                     |
| `r: A -> B` (no multiplicity) | Treated as `A -> one B`        | Same as `one`                                                                                                            | Default multiplicity is "exactly one"           |

**Junction table naming convention:** `{parent_table}_{child_table}_{relation_name}`, all
snake_case. Example: `orders_products_line_items`.

**Junction table structure:**

```sql
CREATE TABLE orders_line_items_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    line_item_id BIGINT NOT NULL REFERENCES line_items(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(order_id, line_item_id)  -- prevent duplicates
);
```

#### Invariant-to-Constraint Mapping

| Invariant Pattern                                               | Database Artifact                                                     |
| --------------------------------------------------------------- | --------------------------------------------------------------------- |
| `invariant: len(f) >= N and len(f) <= M`                        | `CHECK (length(f) >= N AND length(f) <= M)`                           |
| `invariant: f matches /^regex$/`                                | `CHECK (f ~ '^regex$')` (Postgres)                                    |
| `invariant: f > 0`                                              | `CHECK (f > 0)`                                                       |
| `invariant: f in {v1, v2, v3}` (enum-like)                      | `CHECK (f IN ('v1', 'v2', 'v3'))` or create an ENUM type              |
| `invariant: all x in R \| P(x)` (global, over a relation)       | Trigger: `BEFORE INSERT OR UPDATE` that checks P for the affected row |
| Uniqueness detected from `state: Key -> lone Value` (injective) | `UNIQUE` constraint on the value column                               |

#### Automatic Columns

Every table gets these columns automatically unless overridden:

| Column       | Type                                 | Purpose                                |
| ------------ | ------------------------------------ | -------------------------------------- |
| `id`         | `BIGSERIAL PRIMARY KEY`              | Surrogate primary key                  |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | Creation timestamp                     |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | Last modification (updated by trigger) |

If the spec includes a `deleted` or `removed` state concept (e.g., an operation that "archives"
instead of deleting), the engine adds:

| Column       | Type                     | Purpose               |
| ------------ | ------------------------ | --------------------- |
| `deleted_at` | `TIMESTAMPTZ` (nullable) | Soft delete timestamp |

And the DELETE endpoint sets `deleted_at = NOW()` instead of issuing `DELETE FROM`.

#### Index Generation

The engine automatically creates indexes based on operation patterns:

| Pattern                                                                        | Index                                                                                        |
| ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| Operation filters by field `f` (appears in requires or input-to-query mapping) | `CREATE INDEX idx_{table}_{field} ON {table}({field})`                                       |
| Foreign key column                                                             | `CREATE INDEX idx_{table}_{fk} ON {table}({fk})` (most databases auto-index PKs but not FKs) |
| Junction table columns                                                         | Composite index on `(parent_id, child_id)`                                                   |
| Field with uniqueness invariant                                                | `CREATE UNIQUE INDEX` instead of regular index                                               |
| `sort_by` parameter references a field                                         | Index on that field (supports efficient ORDER BY)                                            |
| Full-text search operation exists                                              | `GIN` index on the searchable text column (Postgres-specific)                                |

### 2.6 Validation Mapping

Validation occurs at three layers, and the convention engine decides what goes where:

#### Layer 1: HTTP / Request Validation (before any application logic)

| Spec Element                                                                  | Validation Check          | Implementation                                             |
| ----------------------------------------------------------------------------- | ------------------------- | ---------------------------------------------------------- |
| Entity field type `Int` but input is string                                   | Type coercion / rejection | JSON Schema / Pydantic type enforcement                    |
| `invariant: len(f) >= N` on entity field                                      | String length check       | JSON Schema `minLength` / Pydantic `min_length`            |
| `invariant: f matches /^regex$/`                                              | Regex pattern match       | JSON Schema `pattern` / Pydantic `regex`                   |
| `invariant: f > 0`                                                            | Numeric range check       | JSON Schema `exclusiveMinimum: 0` / Pydantic `gt=0`        |
| Required field (appears in `one` multiplicity or operation input without `?`) | Presence check            | JSON Schema `required` array / Pydantic non-Optional field |
| Optional field (appears in `lone` multiplicity or input with `?`)             | Allow null/absent         | JSON Schema without `required` / Pydantic `Optional`       |

**Principle:** Anything that can be checked without hitting the database is checked at this layer.
This includes type checks, format checks, range checks, and required field checks.

#### Layer 2: Application / Business Logic Validation (after parsing, before DB)

| Spec Element                                          | Validation Check       | Implementation                             |
| ----------------------------------------------------- | ---------------------- | ------------------------------------------ |
| `requires: id in {relation}`                          | Entity existence check | `SELECT EXISTS(...)` query or ORM `.get()` |
| `requires: entity.status = "expected"`                | State machine guard    | Load entity, check field value             |
| `requires: input.x != input.y`                        | Cross-field validation | Application code after parsing             |
| `requires: f(input)` where `f` is a complex predicate | Custom business rule   | Generated function from spec predicate     |
| Cross-entity invariant                                | Consistency check      | Transaction-scoped query                   |

**Principle:** Anything that requires reading current database state is checked at this layer. It
runs inside a transaction so the check and the subsequent mutation are atomic.

#### Layer 3: Database Constraints (last line of defense)

| Spec Element                            | Validation Check       | Implementation                                 |
| --------------------------------------- | ---------------------- | ---------------------------------------------- |
| Entity field invariant                  | CHECK constraint       | Catches violations that somehow bypass Layer 1 |
| Uniqueness                              | UNIQUE constraint      | Catches race conditions that bypass Layer 2    |
| Foreign key existence                   | FOREIGN KEY constraint | Catches referential integrity violations       |
| `some` multiplicity minimum cardinality | Trigger                | Ensures at least one child exists              |

**Principle:** Database constraints are the safety net. They catch violations that bypass
application-level checks (race conditions, direct DB access, bugs). The application should never
rely on the DB constraint as the primary validation mechanism because DB error messages are not
user-friendly.

#### Validation Error Aggregation

When multiple validation checks fail simultaneously (Layer 1), the engine aggregates all errors into
a single 422 response:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "constraint": "must match pattern ^.+@.+$", "value": "bad" },
      { "field": "age", "constraint": "must be greater than 0", "value": -5 }
    ]
  }
}
```

Layer 2 checks (which require DB reads) are evaluated sequentially and return on the first failure
(because each check may be expensive).

### 2.7 Error Response Mapping

#### Structured Error Codes

Each requires clause generates a unique error code derived from the operation name and the clause
position:

| Requires Clause                         | Generated Error Code          | HTTP Status |
| --------------------------------------- | ----------------------------- | ----------- |
| `requires: id in orders`                | `ORDER_NOT_FOUND`             | 404         |
| `requires: orders[id].status = "draft"` | `ORDER_NOT_IN_EXPECTED_STATE` | 409         |
| `requires: total > 0`                   | `INVALID_TOTAL`               | 422         |
| `requires: email not in users_by_email` | `EMAIL_ALREADY_EXISTS`        | 409         |

Error code derivation rules:

1. If the clause checks existence (`x in R`): `{ENTITY}_NOT_FOUND`
2. If the clause checks non-existence (`x not in R`): `{FIELD}_ALREADY_EXISTS`
3. If the clause checks state (`x.status = val`): `{ENTITY}_NOT_IN_EXPECTED_STATE`
4. If the clause checks a value constraint: `INVALID_{FIELD}`
5. If the clause is a complex expression: `{OPERATION}_PRECONDITION_FAILED`

#### Human-Readable Error Messages

The engine generates default messages from the clause structure:

| Clause                        | Generated Message                                           |
| ----------------------------- | ----------------------------------------------------------- |
| `id in orders`                | "Order with the given ID was not found"                     |
| `orders[id].status = "draft"` | "Order must be in 'draft' status to perform this operation" |
| `total > 0`                   | "Total must be greater than 0"                              |
| `len(code) >= 6`              | "Code must be at least 6 characters long"                   |

These messages are overridable via the conventions block.

---

## 3. Convention Override System

### 3.1 Override Syntax

Overrides live in the `conventions` block of the spec file. Every convention decision is addressable
by a dotted path:

```
conventions {
  // HTTP method override
  Resolve.http_method = "GET"

  // URL path override
  Resolve.http_path = "/{code}"

  // Status code override
  Resolve.http_status_success = 302

  // Custom header
  Resolve.http_header "Location" = output.url

  // DB table name override
  ShortCode.db_table = "codes"

  // DB column type override
  ShortCode.value_db_type = "VARCHAR(12)"

  // DB index override
  ShortCode.value_db_index = "unique"

  // JSON field naming convention (global)
  global.json_naming = "camelCase"    // "snake_case" | "camelCase" | "PascalCase"

  // Envelope format (global)
  global.response_envelope = "bare"   // "standard" | "bare" | "jsonapi"

  // Pagination defaults (global)
  global.pagination_default_limit = 50
  global.pagination_max_limit = 200

  // Authentication requirement
  Shorten.http_auth = "bearer"        // "none" | "bearer" | "api_key" | "basic"
  global.http_auth = "bearer"         // applied to all endpoints

  // Soft delete behavior
  Delete.http_soft_delete = true

  // Custom error message
  Resolve.requires_0_error_message = "Short code not found. It may have expired."

  // Custom error code
  Resolve.requires_0_error_code = "CODE_EXPIRED_OR_NOT_FOUND"

  // Disable auto-generated timestamp columns
  ShortCode.db_timestamps = false

  // Custom plural form
  ShortCode.plural = "codes"

  // Sort the resource name override
  Inventory.db_table = "inventory_levels"
}
```

### 3.2 Override Categories

| Category           | Addressable Properties                                     | Example                                       |
| ------------------ | ---------------------------------------------------------- | --------------------------------------------- |
| **HTTP Method**    | `{Op}.http_method`                                         | `Shorten.http_method = "POST"`                |
| **URL Path**       | `{Op}.http_path`                                           | `Resolve.http_path = "/go/{code}"`            |
| **Status Codes**   | `{Op}.http_status_success`, `{Op}.http_status_{condition}` | `Shorten.http_status_success = 200`           |
| **Headers**        | `{Op}.http_header "{Name}"`                                | `Resolve.http_header "Location" = output.url` |
| **Auth**           | `{Op}.http_auth`, `global.http_auth`                       | `global.http_auth = "bearer"`                 |
| **DB Table**       | `{Entity}.db_table`                                        | `User.db_table = "app_users"`                 |
| **DB Column**      | `{Entity}.{field}_db_type`, `{Entity}.{field}_db_column`   | `User.email_db_column = "email_address"`      |
| **DB Index**       | `{Entity}.{field}_db_index`                                | `User.email_db_index = "unique"`              |
| **DB Timestamps**  | `{Entity}.db_timestamps`                                   | `AuditLog.db_timestamps = false`              |
| **JSON Naming**    | `global.json_naming`                                       | `global.json_naming = "camelCase"`            |
| **Envelope**       | `global.response_envelope`                                 | `global.response_envelope = "bare"`           |
| **Pagination**     | `global.pagination_{prop}`                                 | `global.pagination_max_limit = 500`           |
| **Pluralization**  | `{Entity}.plural`                                          | `Person.plural = "people"`                    |
| **Error Messages** | `{Op}.requires_{n}_error_message`                          | --                                            |
| **Error Codes**    | `{Op}.requires_{n}_error_code`                             | --                                            |
| **Soft Delete**    | `{Op}.http_soft_delete`, `global.http_soft_delete`         | `global.http_soft_delete = true`              |

### 3.3 Override Resolution Order

When multiple overrides could apply, they are resolved in this order (most specific wins):

1. **Operation-level override** (e.g., `Shorten.http_method = "PUT"`)
2. **Entity-level override** (e.g., `ShortCode.db_table = "codes"`)
3. **Global override** (e.g., `global.http_auth = "bearer"`)
4. **Profile default** (e.g., python-fastapi profile uses snake_case)
5. **Engine default** (the rules in Section 2)

### 3.4 Override Conflicts

When overrides conflict with each other:

| Conflict                                                               | Resolution                                                                |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Operation override conflicts with global override                      | Operation wins                                                            |
| Two operation overrides for the same property                          | Compilation error: "Duplicate override for {path}"                        |
| Override contradicts spec semantics (e.g., setting GET for a mutation) | Warning: "Override {path} may violate REST semantics: GET should be safe" |
| Override sets an invalid value (e.g., `Op.http_method = "INVALID"`)    | Compilation error: "Invalid HTTP method: INVALID"                         |

### 3.5 What Cannot Be Overridden

Some aspects are derived from the spec and cannot be overridden because doing so would break
correctness:

| Property                                                   | Why Not Overridable                                         |
| ---------------------------------------------------------- | ----------------------------------------------------------- |
| The _existence_ of validation checks from requires clauses | Removing a precondition check could violate spec guarantees |
| The _existence_ of DB constraints from invariants          | Removing constraints could allow invalid state              |
| The primary key column on a table                          | Surrogate keys are required for internal consistency        |
| The `request_id` in error responses                        | Required for debugging/tracing                              |
| Foreign key relationships derived from state relations     | Removing FK would break referential integrity               |

Users can, however, override the _presentation_ of these (error messages, status codes, column
names) without removing the underlying check.

---

## 4. Worked Examples

### 4.1 URL Shortener

#### Spec

```
service UrlShortener {

  entity ShortCode {
    value: String
    invariant: len(value) >= 6 and len(value) <= 10
    invariant: value matches /^[a-zA-Z0-9]+$/
  }

  entity LongURL {
    value: String
    invariant: isValidURI(value)
  }

  state {
    store: ShortCode -> lone LongURL
    created_at: ShortCode -> DateTime
  }

  operation Shorten {
    input:   url: LongURL
    output:  code: ShortCode, short_url: String

    requires: isValidURI(url.value)

    ensures:
      code not in pre(store)
      store'[code] = url
      short_url = base_url + "/" + code.value
      #store' = #store + 1
  }

  operation Resolve {
    input:  code: ShortCode
    output: url: LongURL

    requires: code in store

    ensures:
      url = store[code]
      store' = store
  }

  operation Delete {
    input:  code: ShortCode

    requires: code in store

    ensures:
      code not in store'
      #store' = #store - 1
  }

  operation ListAll {
    output: entries: Set[UrlMapping]

    ensures:
      entries = store
      store' = store
  }

  conventions {
    Resolve.http_status_success = 302
    Resolve.http_header "Location" = output.url
  }
}
```

#### Convention Engine Output: HTTP Endpoints

| #   | Method | Path                  | Status (Success) | Status (Errors)      | Request Body                        | Response Body                                                                             |
| --- | ------ | --------------------- | ---------------- | -------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------- |
| 1   | POST   | `/short-codes`        | 201 Created      | 422 (invalid URL)    | `{"url": {"value": "https://..."}}` | `{"data": {"code": {"value": "abc123"}, "short_url": "https://sho.rt/abc123"}}`           |
| 2   | GET    | `/short-codes/{code}` | 302 Found        | 404 (code not found) | --                                  | Empty (redirect via Location header)                                                      |
| 3   | DELETE | `/short-codes/{code}` | 204 No Content   | 404 (code not found) | --                                  | --                                                                                        |
| 4   | GET    | `/short-codes`        | 200 OK           | --                   | --                                  | `{"data": [{"code": {...}, "url": {...}}], "meta": {"page": 1, "limit": 20, "total": N}}` |

**Decision trace for endpoint 1 (Shorten):**

- Ensures clause adds to `store` -> state mutation, new key created -> Rule M1 -> POST
- Entity is ShortCode -> pluralize -> `short-codes`
- No ID in input -> collection endpoint -> `/short-codes`
- Creates new entity -> 201 + Location header

**Decision trace for endpoint 2 (Resolve):**

- Ensures clause: `store' = store` -> no state mutation -> Rule M2 -> GET
- Input is `code: ShortCode` which is the key type of `store` -> path parameter
- Override: `Resolve.http_status_success = 302` -> 302 instead of 200
- Override: `Location` header set to URL value

#### Convention Engine Output: SQL DDL

```sql
-- Entity: ShortCode (primary table from the 'store' relation)
CREATE TABLE short_codes (
    id          BIGSERIAL PRIMARY KEY,
    value       VARCHAR(10) NOT NULL
                CHECK (length(value) >= 6 AND length(value) <= 10)
                CHECK (value ~ '^[a-zA-Z0-9]+$'),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_short_codes_value ON short_codes(value);

-- Relation: store (ShortCode -> lone LongURL)
-- Since 'lone' means at most one, this is a nullable FK on short_codes
-- But since the relation is the core state, we embed it in the same table:
ALTER TABLE short_codes
    ADD COLUMN long_url TEXT
    CHECK (long_url ~ '^https?://');  -- derived from isValidURI invariant

-- Metadata: created_at relation
-- Already covered by the auto-generated created_at column

-- Trigger: update updated_at on modification
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_short_codes_updated_at
    BEFORE UPDATE ON short_codes
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at();
```

**Schema design decision:** The `store: ShortCode -> lone LongURL` relation is embedded as a column
in `short_codes` rather than a separate table because LongURL is a value type (single field, no
identity of its own) and the multiplicity is `lone` (at most one). If LongURL were a full entity
with its own state relations, a separate table with a foreign key would be generated instead.

#### Convention Engine Output: OpenAPI Snippet

```yaml
openapi: 3.1.0
info:
  title: UrlShortener API
  version: 1.0.0

paths:
  /short-codes:
    post:
      operationId: shorten
      summary: Create a new short code
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: "#/components/schemas/ShortenRequest"
      responses:
        "201":
          description: Short code created
          headers:
            Location:
              schema:
                type: string
              description: URL of the created resource
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ShortenResponse"
        "422":
          description: Validation failed
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
    get:
      operationId: listAll
      summary: List all short codes
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
            minimum: 1
        - name: limit
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        "200":
          description: List of short codes
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ListAllResponse"

  /short-codes/{code}:
    get:
      operationId: resolve
      summary: Resolve a short code to its URL
      parameters:
        - name: code
          in: path
          required: true
          schema:
            type: string
            minLength: 6
            maxLength: 10
            pattern: "^[a-zA-Z0-9]+$"
      responses:
        "302":
          description: Redirect to the long URL
          headers:
            Location:
              schema:
                type: string
              description: The original long URL
        "404":
          description: Short code not found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"
    delete:
      operationId: delete
      summary: Delete a short code
      parameters:
        - name: code
          in: path
          required: true
          schema:
            type: string
            minLength: 6
            maxLength: 10
            pattern: "^[a-zA-Z0-9]+$"
      responses:
        "204":
          description: Short code deleted
        "404":
          description: Short code not found
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/ErrorResponse"

components:
  schemas:
    ShortenRequest:
      type: object
      required: [url]
      properties:
        url:
          type: object
          required: [value]
          properties:
            value:
              type: string
              format: uri

    ShortenResponse:
      type: object
      properties:
        data:
          type: object
          properties:
            code:
              type: object
              properties:
                value:
                  type: string
                  minLength: 6
                  maxLength: 10
            short_url:
              type: string
              format: uri

    ListAllResponse:
      type: object
      properties:
        data:
          type: array
          items:
            type: object
            properties:
              code:
                $ref: "#/components/schemas/ShortCodeSchema"
              url:
                $ref: "#/components/schemas/LongURLSchema"
        meta:
          $ref: "#/components/schemas/PaginationMeta"

    ShortCodeSchema:
      type: object
      properties:
        value:
          type: string
          minLength: 6
          maxLength: 10
          pattern: "^[a-zA-Z0-9]+$"

    LongURLSchema:
      type: object
      properties:
        value:
          type: string
          format: uri

    PaginationMeta:
      type: object
      properties:
        page:
          type: integer
        limit:
          type: integer
        total:
          type: integer

    ErrorResponse:
      type: object
      properties:
        error:
          type: object
          properties:
            code:
              type: string
            message:
              type: string
            details:
              type: array
              items:
                type: object
                properties:
                  field:
                    type: string
                  constraint:
                    type: string
                  value: {}
```

#### Validation Logic Description

| Check                                                   | Layer          | Implementation                                             |
| ------------------------------------------------------- | -------------- | ---------------------------------------------------------- |
| `url.value` is a valid URI                              | Layer 1 (HTTP) | Pydantic `AnyUrl` type / JSON Schema `format: uri`         |
| `url.value` is present and not null                     | Layer 1 (HTTP) | `required` in schema                                       |
| `code.value` length 6-10                                | Layer 1 (HTTP) | Path parameter schema validation                           |
| `code.value` matches alphanumeric                       | Layer 1 (HTTP) | Path parameter pattern validation                          |
| `code in store` (for Resolve, Delete)                   | Layer 2 (App)  | `SELECT EXISTS(SELECT 1 FROM short_codes WHERE value = ?)` |
| `code not in pre(store)` (for Shorten, post-generation) | Layer 2 (App)  | Check after generating code; retry if collision            |
| `length(value) >= 6 AND length(value) <= 10`            | Layer 3 (DB)   | CHECK constraint (safety net)                              |
| `value ~ '^[a-zA-Z0-9]+$'`                              | Layer 3 (DB)   | CHECK constraint (safety net)                              |

### 4.2 E-commerce Order Service

#### Spec

```
service OrderService {

  entity Product {
    name: String
    price: Decimal
    sku: String
    invariant: price > 0
    invariant: len(sku) = 10
  }

  entity LineItem {
    quantity: Int
    unit_price: Decimal
    invariant: quantity > 0
    invariant: unit_price > 0
  }

  entity Order {
    status: String
    total: Decimal
    customer_email: String
    invariant: status in {"draft", "placed", "paid", "shipped", "cancelled"}
    invariant: total >= 0
    invariant: customer_email matches /^.+@.+$/
  }

  entity InventoryRecord {
    quantity_available: Int
    invariant: quantity_available >= 0
  }

  state {
    products: ProductId -> one Product
    inventory: ProductId -> one InventoryRecord
    orders: OrderId -> one Order
    line_items: OrderId -> set LineItem
    item_product: LineItem -> one Product    // each line item references a product
  }

  // --- CRUD Operations ---

  operation CreateProduct {
    input: name: String, price: Decimal, sku: String, initial_stock: Int
    output: product: Product

    requires: initial_stock >= 0

    ensures:
      product not in pre(products)
      products'[product] = Product{name, price, sku}
      inventory'[product] = InventoryRecord{initial_stock}
  }

  operation GetProduct {
    input: id: ProductId
    output: product: Product, stock: Int

    requires: id in products

    ensures:
      product = products[id]
      stock = inventory[id].quantity_available
      products' = products
  }

  operation ListProducts {
    input: name_filter?: String, min_price?: Decimal, max_price?: Decimal
    output: results: set Product

    ensures:
      results = {p in products | matches_filters(p, name_filter, min_price, max_price)}
      products' = products
  }

  // --- Order Lifecycle ---

  operation CreateOrder {
    input: customer_email: String
    output: order: Order

    requires: customer_email matches /^.+@.+$/

    ensures:
      order not in pre(orders)
      orders'[order] = Order{status: "draft", total: 0, customer_email}
      line_items'[order] = {}
  }

  operation AddLineItem {
    input: order_id: OrderId, product_id: ProductId, quantity: Int
    output: item: LineItem

    requires:
      order_id in orders
      orders[order_id].status = "draft"
      product_id in products
      quantity > 0
      inventory[product_id].quantity_available >= quantity

    ensures:
      item = LineItem{quantity, unit_price: products[product_id].price}
      line_items'[order_id] = line_items[order_id] + {item}
      item_product'[item] = products[product_id]
      orders'[order_id].total = orders[order_id].total + item.quantity * item.unit_price
  }

  operation RemoveLineItem {
    input: order_id: OrderId, item_id: LineItemId

    requires:
      order_id in orders
      orders[order_id].status = "draft"
      item_id in line_items[order_id]

    ensures:
      line_items'[order_id] = line_items[order_id] - {item_id}
      orders'[order_id].total = orders[order_id].total - item_id.quantity * item_id.unit_price
  }

  // --- State Machine Transitions ---

  operation PlaceOrder {
    input: order_id: OrderId

    requires:
      order_id in orders
      orders[order_id].status = "draft"
      #line_items[order_id] > 0      // must have at least one item

    ensures:
      orders'[order_id].status = "placed"
      // reserve inventory
      all item in line_items[order_id] |
        inventory'[item_product[item]].quantity_available =
          inventory[item_product[item]].quantity_available - item.quantity
  }

  operation PayOrder {
    input: order_id: OrderId, payment_token: String

    requires:
      order_id in orders
      orders[order_id].status = "placed"

    ensures:
      orders'[order_id].status = "paid"
  }

  operation ShipOrder {
    input: order_id: OrderId, tracking_number: String

    requires:
      order_id in orders
      orders[order_id].status = "paid"

    ensures:
      orders'[order_id].status = "shipped"
  }

  operation CancelOrder {
    input: order_id: OrderId

    requires:
      order_id in orders
      orders[order_id].status in {"draft", "placed"}

    ensures:
      orders'[order_id].status = "cancelled"
      // release inventory if was placed
      orders[order_id].status = "placed" implies
        all item in line_items[order_id] |
          inventory'[item_product[item]].quantity_available =
            inventory[item_product[item]].quantity_available + item.quantity
  }

  operation GetOrder {
    input: order_id: OrderId
    output: order: Order, items: set LineItem

    requires: order_id in orders

    ensures:
      order = orders[order_id]
      items = line_items[order_id]
      orders' = orders
  }
}
```

#### Convention Engine Output: HTTP Endpoints

| #   | Method | Path                                      | Operation      | Success | Error Codes                                                                                      |
| --- | ------ | ----------------------------------------- | -------------- | ------- | ------------------------------------------------------------------------------------------------ |
| 1   | POST   | `/products`                               | CreateProduct  | 201     | 422 (invalid stock/price/sku)                                                                    |
| 2   | GET    | `/products/{id}`                          | GetProduct     | 200     | 404 (product not found)                                                                          |
| 3   | GET    | `/products`                               | ListProducts   | 200     | --                                                                                               |
| 4   | POST   | `/orders`                                 | CreateOrder    | 201     | 422 (invalid email)                                                                              |
| 5   | GET    | `/orders/{order_id}`                      | GetOrder       | 200     | 404 (order not found)                                                                            |
| 6   | POST   | `/orders/{order_id}/line-items`           | AddLineItem    | 201     | 404 (order/product not found), 409 (order not draft, insufficient stock), 422 (invalid quantity) |
| 7   | DELETE | `/orders/{order_id}/line-items/{item_id}` | RemoveLineItem | 204     | 404 (order/item not found), 409 (order not draft)                                                |
| 8   | POST   | `/orders/{order_id}/place`                | PlaceOrder     | 200     | 404 (order not found), 409 (not draft, no items)                                                 |
| 9   | POST   | `/orders/{order_id}/pay`                  | PayOrder       | 200     | 404 (order not found), 409 (not placed)                                                          |
| 10  | POST   | `/orders/{order_id}/ship`                 | ShipOrder      | 200     | 404 (order not found), 409 (not paid)                                                            |
| 11  | POST   | `/orders/{order_id}/cancel`               | CancelOrder    | 200     | 404 (order not found), 409 (not draft/placed)                                                    |

**Decision trace for endpoint 6 (AddLineItem):**

- Mutates state + creates new entity (LineItem) -> Rule M1 -> POST
- LineItem is a child of Order (detected from `line_items: OrderId -> set LineItem`)
- Parent is Order, child is LineItem -> nested resource
- Path: `/orders/{order_id}/line-items`
- `order_id` is a path parameter (key of orders relation)
- `product_id` and `quantity` are body fields (not keys of the target resource)

**Decision trace for endpoints 8-11 (state machine transitions):**

- Each mutates status field -> state mutation -> Rule M10 (state transition)
- The operation name after removing the entity prefix gives the action verb
- `PlaceOrder` -> action `place` -> POST `/orders/{order_id}/place`
- `PayOrder` -> action `pay` -> POST `/orders/{order_id}/pay`
- `ShipOrder` -> action `ship` -> POST `/orders/{order_id}/ship`
- `CancelOrder` -> action `cancel` -> POST `/orders/{order_id}/cancel`

#### Convention Engine Output: SQL DDL

```sql
-- Entity: Product
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    price       NUMERIC(19,4) NOT NULL CHECK (price > 0),
    sku         VARCHAR(10) NOT NULL CHECK (length(sku) = 10),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_products_sku ON products(sku);

-- Entity: InventoryRecord (1:1 with Product via 'inventory' relation)
CREATE TABLE inventory_records (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
    quantity_available  INTEGER NOT NULL CHECK (quantity_available >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_records_product_id ON inventory_records(product_id);

-- Entity: Order
CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(20) NOT NULL DEFAULT 'draft'
                    CHECK (status IN ('draft', 'placed', 'paid', 'shipped', 'cancelled')),
    total           NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (total >= 0),
    customer_email  TEXT NOT NULL CHECK (customer_email ~ '.+@.+'),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_customer_email ON orders(customer_email);

-- Entity: LineItem (child of Order via 'line_items' relation)
CREATE TABLE line_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity    INTEGER NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(19,4) NOT NULL CHECK (unit_price > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_line_items_order_id ON line_items(order_id);
CREATE INDEX idx_line_items_product_id ON line_items(product_id);

-- updated_at trigger (applied to all tables)
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_inventory_records_updated_at BEFORE UPDATE ON inventory_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER trg_line_items_updated_at BEFORE UPDATE ON line_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
```

**Schema design decisions:**

- `inventory: ProductId -> one InventoryRecord` creates a 1:1 relationship. Since it's keyed by
  ProductId, the `inventory_records` table gets a `product_id` column with a UNIQUE constraint
  (ensuring 1:1).
- `line_items: OrderId -> set LineItem` creates a 1:N relationship. Since it's `set` (zero or more),
  no minimum-cardinality trigger is needed. The `line_items` table gets an `order_id` foreign key.
- `item_product: LineItem -> one Product` becomes `product_id` in `line_items` with NOT NULL
  (because `one` means exactly one).
- The `status` field's enum invariant becomes a CHECK constraint with IN clause.

#### State Machine Visualization

```
                ┌──────────────────────────────────────────┐
                │                                          │
                v                                          │
  ┌───────┐  POST /orders  ┌───────┐  POST .../place  ┌────────┐
  │       │ ─────────────> │       │ ───────────────> │        │
  │ (new) │                │ draft │                   │ placed │
  │       │                │       │ <──────┐          │        │
  └───────┘                └───┬───┘        │          └───┬────┘
                               │            │              │
                    POST .../cancel    POST .../cancel     │ POST .../pay
                               │            │              │
                               v            │              v
                          ┌──────────┐      │         ┌────────┐
                          │          │      │         │        │
                          │cancelled │      └─────────│  paid  │
                          │          │                │        │
                          └──────────┘                └───┬────┘
                                                          │
                                                          │ POST .../ship
                                                          │
                                                          v
                                                     ┌────────┐
                                                     │        │
                                                     │shipped │
                                                     │        │
                                                     └────────┘
```

### 4.3 Social Media Feed

#### Spec

```
service SocialFeed {

  entity User {
    username: String
    display_name: String
    bio: String
    invariant: len(username) >= 3 and len(username) <= 30
    invariant: username matches /^[a-zA-Z0-9_]+$/
  }

  entity Post {
    content: String
    posted_at: DateTime
    invariant: len(content) >= 1 and len(content) <= 280
  }

  entity Comment {
    content: String
    commented_at: DateTime
    invariant: len(content) >= 1 and len(content) <= 500
  }

  state {
    users: UserId -> one User
    posts: PostId -> one Post
    post_author: Post -> one User
    comments: PostId -> set Comment
    comment_author: Comment -> one User

    // M:N relations
    follows: User -> set User          // who a user follows
    likes: User -> set Post            // which posts a user liked
  }

  // --- User Operations ---

  operation CreateUser {
    input: username: String, display_name: String, bio: String
    output: user: User

    requires:
      len(username) >= 3
      // username must be unique (not already a value in users)
      all u in users | users[u].username != username

    ensures:
      user not in pre(users)
      users'[user] = User{username, display_name, bio}
  }

  operation GetUser {
    input: id: UserId
    output: user: User, follower_count: Int, following_count: Int

    requires: id in users

    ensures:
      user = users[id]
      follower_count = #{u in users | id in follows[u]}
      following_count = #follows[id]
  }

  // --- Post Operations ---

  operation CreatePost {
    input: author_id: UserId, content: String
    output: post: Post

    requires:
      author_id in users
      len(content) >= 1 and len(content) <= 280

    ensures:
      post not in pre(posts)
      posts'[post] = Post{content, posted_at: now()}
      post_author'[post] = users[author_id]
  }

  operation GetFeed {
    input: user_id: UserId, page: Int, limit: Int
    output: feed: list Post

    requires:
      user_id in users
      page >= 1
      limit >= 1 and limit <= 100

    ensures:
      feed = sorted_by_time(
        {p in posts | post_author[p] in follows[users[user_id]]},
        descending
      )
      // paginated to (page, limit)
  }

  // --- Social Graph ---

  operation Follow {
    input: follower_id: UserId, followee_id: UserId

    requires:
      follower_id in users
      followee_id in users
      follower_id != followee_id
      users[followee_id] not in follows[users[follower_id]]

    ensures:
      follows'[users[follower_id]] = follows[users[follower_id]] + {users[followee_id]}
  }

  operation Unfollow {
    input: follower_id: UserId, followee_id: UserId

    requires:
      follower_id in users
      followee_id in users
      users[followee_id] in follows[users[follower_id]]

    ensures:
      follows'[users[follower_id]] = follows[users[follower_id]] - {users[followee_id]}
  }

  // --- Likes ---

  operation LikePost {
    input: user_id: UserId, post_id: PostId

    requires:
      user_id in users
      post_id in posts
      posts[post_id] not in likes[users[user_id]]

    ensures:
      likes'[users[user_id]] = likes[users[user_id]] + {posts[post_id]}
  }

  operation UnlikePost {
    input: user_id: UserId, post_id: PostId

    requires:
      user_id in users
      post_id in posts
      posts[post_id] in likes[users[user_id]]

    ensures:
      likes'[users[user_id]] = likes[users[user_id]] - {posts[post_id]}
  }

  // --- Comments ---

  operation AddComment {
    input: post_id: PostId, author_id: UserId, content: String
    output: comment: Comment

    requires:
      post_id in posts
      author_id in users
      len(content) >= 1 and len(content) <= 500

    ensures:
      comment not in pre(comments[post_id])
      comments'[post_id] = comments[post_id] + {comment}
      comment = Comment{content, commented_at: now()}
      comment_author'[comment] = users[author_id]
  }

  operation GetComments {
    input: post_id: PostId, page: Int, limit: Int
    output: results: list Comment

    requires: post_id in posts

    ensures:
      results = sorted_by_time(comments[post_id], descending)
  }
}
```

#### Convention Engine Output: HTTP Endpoints

| #   | Method | Path                                           | Operation   | Notes                                                        |
| --- | ------ | ---------------------------------------------- | ----------- | ------------------------------------------------------------ |
| 1   | POST   | `/users`                                       | CreateUser  |                                                              |
| 2   | GET    | `/users/{id}`                                  | GetUser     | Includes computed follower/following counts                  |
| 3   | POST   | `/posts`                                       | CreatePost  | `author_id` in body                                          |
| 4   | GET    | `/users/{user_id}/feed`                        | GetFeed     | Feed is a sub-resource of User; pagination via query params  |
| 5   | POST   | `/users/{follower_id}/following`               | Follow      | `followee_id` in body. Following is a sub-collection of User |
| 6   | DELETE | `/users/{follower_id}/following/{followee_id}` | Unfollow    |                                                              |
| 7   | POST   | `/users/{user_id}/likes`                       | LikePost    | `post_id` in body                                            |
| 8   | DELETE | `/users/{user_id}/likes/{post_id}`             | UnlikePost  |                                                              |
| 9   | POST   | `/posts/{post_id}/comments`                    | AddComment  | `author_id` and `content` in body                            |
| 10  | GET    | `/posts/{post_id}/comments`                    | GetComments | Pagination via query params                                  |

**Decision trace for endpoint 5 (Follow):**

- `follows: User -> set User` is a M:N self-relation on User
- Follow mutates state (adds to follows) -> Rule M1 -> POST
- The relation is from the follower's perspective, so the sub-resource is under the follower
- Path: `/users/{follower_id}/following` (the set of users this user follows)
- `followee_id` is not a path param of the target collection -> goes in body

**Decision trace for endpoint 6 (Unfollow):**

- Removes from follows -> Rule M5 -> DELETE
- Targets a specific follow relationship: `/users/{follower_id}/following/{followee_id}`

**Decision trace for endpoint 4 (GetFeed):**

- Reads state, no mutation -> Rule M2 -> GET
- Feed is conceptually a sub-resource of a user (their personalized feed)
- Path: `/users/{user_id}/feed`
- `page` and `limit` are pagination params -> query parameters

#### Convention Engine Output: SQL DDL (junction tables)

```sql
-- Entity: User
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30) NOT NULL
                  CHECK (length(username) >= 3 AND length(username) <= 30)
                  CHECK (username ~ '^[a-zA-Z0-9_]+$'),
    display_name  TEXT NOT NULL,
    bio           TEXT NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_users_username ON users(username);

-- Entity: Post
CREATE TABLE posts (
    id          BIGSERIAL PRIMARY KEY,
    author_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content     VARCHAR(280) NOT NULL
                CHECK (length(content) >= 1 AND length(content) <= 280),
    posted_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_posts_author_id ON posts(author_id);
CREATE INDEX idx_posts_posted_at ON posts(posted_at DESC);

-- Entity: Comment
CREATE TABLE comments (
    id            BIGSERIAL PRIMARY KEY,
    post_id       BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content       VARCHAR(500) NOT NULL
                  CHECK (length(content) >= 1 AND length(content) <= 500),
    commented_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comments_post_id ON comments(post_id);
CREATE INDEX idx_comments_author_id ON comments(author_id);
CREATE INDEX idx_comments_commented_at ON comments(commented_at DESC);

-- Junction Table: follows (User -> set User, M:N self-relation)
CREATE TABLE user_follows (
    id           BIGSERIAL PRIMARY KEY,
    follower_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (follower_id != followee_id),  -- from requires: follower_id != followee_id
    UNIQUE(follower_id, followee_id)
);

CREATE INDEX idx_user_follows_follower_id ON user_follows(follower_id);
CREATE INDEX idx_user_follows_followee_id ON user_follows(followee_id);

-- Junction Table: likes (User -> set Post, M:N)
CREATE TABLE user_post_likes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id     BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, post_id)
);

CREATE INDEX idx_user_post_likes_user_id ON user_post_likes(user_id);
CREATE INDEX idx_user_post_likes_post_id ON user_post_likes(post_id);
```

**Junction table design decisions:**

- `follows: User -> set User` is a M:N self-relation. The junction table `user_follows` has
  `follower_id` and `followee_id` both referencing `users(id)`. The
  `CHECK (follower_id != followee_id)` constraint comes from the
  `requires: follower_id != followee_id` clause in the Follow operation -- the engine promotes
  operation-level preconditions to DB constraints when they are universally applicable.

- `likes: User -> set Post` is a standard M:N relation. The junction table `user_post_likes` has a
  UNIQUE constraint on `(user_id, post_id)` because the spec's requires clause for LikePost includes
  `posts[post_id] not in likes[users[user_id]]`, which means duplicates are not allowed.

- Both junction tables get individual indexes on each foreign key column to support efficient
  lookups in both directions (e.g., "who follows this user" and "who does this user follow").

#### Pagination Convention

For GetFeed and GetComments, the engine generates:

```
GET /users/{user_id}/feed?page=1&limit=20

Response:
{
  "data": [
    {"id": 42, "content": "Hello world", "posted_at": "2026-04-05T10:30:00Z", ...},
    ...
  ],
  "meta": {
    "page": 1,
    "limit": 20,
    "total": 1523,
    "has_next": true,
    "has_prev": false
  }
}
```

The engine adds `has_next` and `has_prev` boolean fields to the pagination metadata based on the
current page and total count. This allows clients to implement forward/ backward pagination without
computing it themselves.

For cursor-based pagination (when the spec uses `sorted_by_time` or similar ordered access
patterns), the engine can optionally generate cursor-based endpoints:

```
GET /users/{user_id}/feed?after=eyJpZCI6NDJ9&limit=20
```

This is activated via `global.pagination.strategy = "cursor"` in the conventions block.

---

## 5. Edge Cases and Ambiguities

### 5.1 Operations That Both Read AND Write

**Example:** "Increment counter and return new value"

```
operation IncrementViewCount {
  input: code: ShortCode
  output: new_count: Int

  requires: code in store

  ensures:
    view_count'[code] = view_count[code] + 1
    new_count = view_count'[code]
    store' = store
}
```

**Resolution:** This mutates state (`view_count'` differs from `view_count`) AND returns data. The
engine maps this to **POST** because the operation is not safe (it has side effects). GET would
violate RFC 7231's safety requirement.

**Rule:** If an operation mutates ANY state relation, it is not a GET, regardless of whether it also
returns data. The method is determined by the mutation rules (M1-M6, M8-M10).

### 5.2 Operations With Multiple Entity Inputs

**Example:** "Transfer between accounts"

```
operation Transfer {
  input: from_id: AccountId, to_id: AccountId, amount: Decimal

  requires:
    from_id in accounts
    to_id in accounts
    from_id != to_id
    accounts[from_id].balance >= amount
    amount > 0

  ensures:
    accounts'[from_id].balance = accounts[from_id].balance - amount
    accounts'[to_id].balance = accounts[to_id].balance + amount
}
```

**Resolution:** This mutates existing entities but doesn't clearly belong to one resource. The
engine applies Rule M8 (side-effect POST) and creates a top-level action endpoint:

- `POST /transfers` with body `{"from_id": 1, "to_id": 2, "amount": 100.00}`

The entity to use for the path is the _operation name itself_, treated as a verb-noun (Transfer ->
`transfers`). Neither `from_id` nor `to_id` becomes a path parameter because neither uniquely
identifies the target resource.

**Rule:** When an operation takes multiple entity IDs as input and mutates both, the operation name
becomes the resource name and all IDs go in the request body.

### 5.3 Operations That Create Child Entities

**Example:** "Add item to order"

As shown in Example 4.2, the engine detects parent-child relationships from the state model and
routes child creation to the parent's sub-collection:

- `POST /orders/{order_id}/line-items` (NOT `POST /line-items`)

**Rule:** If entity B is a child of entity A (detected from `r: AId -> set B` in the state model),
then creating B routes to `POST /{A_plural}/{a_id}/{B_plural}`.

**Exception:** If B also exists independently (has its own top-level read operations not scoped to a
parent), the engine generates BOTH:

- `POST /orders/{order_id}/line-items` (create under parent)
- `GET /line-items/{id}` (direct access by ID)

### 5.4 Idempotency

| HTTP Method   | Idempotent?         | Engine Behavior                                                                                   |
| ------------- | ------------------- | ------------------------------------------------------------------------------------------------- |
| GET           | Yes (by definition) | No special handling                                                                               |
| PUT           | Yes (by definition) | No special handling -- full replacement is inherently idempotent                                  |
| DELETE        | Yes (by definition) | Return 204 even if already deleted (do not return 404 on re-delete)                               |
| PATCH         | Not necessarily     | No special handling                                                                               |
| POST (create) | Not idempotent      | Engine generates an `Idempotency-Key` header option: clients can send a UUID, server deduplicates |
| POST (action) | Not idempotent      | Same `Idempotency-Key` mechanism                                                                  |

The `Idempotency-Key` mechanism is opt-in. The engine generates the infrastructure (a table to store
idempotency keys and their responses) but only activates it when the conventions block enables it:

```
conventions {
  global.http_idempotency_key = true
}
```

When enabled, the engine generates:

```sql
CREATE TABLE idempotency_keys (
    key         UUID PRIMARY KEY,
    endpoint    TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);
```

### 5.5 Optional vs Required Inputs

| Input Declaration                 | Treatment                       |
| --------------------------------- | ------------------------------- |
| `input: name: String`             | Required (422 if absent)        |
| `input: name?: String`            | Optional (null/absent is valid) |
| `input: name: String = "default"` | Optional with default value     |

For GET operations, optional inputs become optional query parameters. For POST/PUT/PATCH, they
become optional body fields (absent or null).

### 5.6 Collection Returns: Pagination, Filtering, Sorting

When an operation returns a collection (`output: results: set Entity` or `list Entity`):

1. **Pagination** is always injected (default: page-based with page=1, limit=20)
2. **Filtering** is derived from the operation's input fields that match entity field names
3. **Sorting** is injected if the operation's ensures clause references ordering (e.g.,
   `sorted_by_time`)

The engine generates query parameter documentation in the OpenAPI spec:

```yaml
parameters:
  - name: page
    in: query
    schema: { type: integer, default: 1, minimum: 1 }
  - name: limit
    in: query
    schema: { type: integer, default: 20, minimum: 1, maximum: 100 }
  - name: sort_by
    in: query
    schema: { type: string, enum: [created_at, name, price] }
  - name: sort_order
    in: query
    schema: { type: string, enum: [asc, desc], default: desc }
```

### 5.7 File Uploads and Binary Data

If an entity has a `Bytes` field or the spec mentions file/binary data:

```
entity Attachment {
  filename: String
  content_type: String
  data: Bytes
}
```

The engine maps creation to a multipart/form-data endpoint:

```
POST /attachments
Content-Type: multipart/form-data

--boundary
Content-Disposition: form-data; name="filename"
document.pdf
--boundary
Content-Disposition: form-data; name="content_type"
application/pdf
--boundary
Content-Disposition: form-data; name="data"; filename="document.pdf"
Content-Type: application/octet-stream
<binary data>
--boundary--
```

The database stores the binary data as BYTEA (Postgres) or stores a file path if
`global.storage.binary = "filesystem"` is set in conventions.

### 5.8 Async Operations and Long-Running Tasks

If an operation's ensures clause references eventual consistency or the operation is annotated as
async:

```
operation ProcessLargeImport {
  input: file: Bytes
  output: job_id: JobId

  requires: len(file) > 0

  ensures:
    // async: result available later
    job_id not in pre(jobs)
    jobs'[job_id].status = "pending"
}
```

The engine generates:

1. `POST /imports` -> 202 Accepted, body: `{"data": {"job_id": "uuid"}}`
2. `GET /imports/{job_id}` -> 200 OK, body:
   `{"data": {"status": "pending"|"running"|"completed"|"failed", "result": ...}}`

The 202 status code signals that the request has been accepted but processing is not complete. The
engine generates a polling endpoint automatically.

### 5.9 Webhooks

If the spec mentions notification or callback patterns:

```
conventions {
  PlaceOrder.http_webhook = "order.placed"
}
```

The engine generates:

1. A webhook registration endpoint: `POST /webhooks` with
   `{"url": "...", "events": ["order.placed"]}`
2. A webhook delivery table in the DB
3. Async delivery logic that POSTs to registered URLs after the operation completes

---

## 6. Convention Profiles (Deployment Targets)

### 6.1 `python-fastapi-postgres`

**Stack:** FastAPI + SQLAlchemy (async) + PostgreSQL + Pydantic + Alembic

| Aspect              | Implementation                                                                                 |
| ------------------- | ---------------------------------------------------------------------------------------------- |
| **Entity -> Model** | SQLAlchemy `DeclarativeBase` class with `Mapped[]` type annotations                            |
| **Validation**      | Pydantic `BaseModel` with `Field()` validators; invariants become `field_validator` decorators |
| **DB Access**       | SQLAlchemy async sessions; each endpoint gets a `Depends(get_db)`                              |
| **Routing**         | FastAPI `APIRouter` with typed path/query params and auto-OpenAPI                              |
| **Error Handling**  | Custom `HTTPException` subclasses; `@app.exception_handler` for structured errors              |
| **Migrations**      | Alembic `env.py` + generated migration files from DDL                                          |
| **JSON naming**     | snake_case (native)                                                                            |
| **Async**           | Full async/await; `asyncpg` driver                                                             |

Example generated code structure:

```
generated/
  app/
    main.py                   # FastAPI app, middleware, exception handlers
    models/
      short_code.py           # SQLAlchemy model
    schemas/
      short_code.py           # Pydantic request/response schemas
    routers/
      short_codes.py          # FastAPI router with endpoint functions
    services/
      short_code_service.py   # Business logic (requires checks, state transitions)
    db/
      session.py              # Async DB session factory
      migrations/
        versions/
          001_initial.py      # Alembic migration
    tests/
      test_short_codes.py     # Pytest + httpx async tests
      conftest.py             # Fixtures (test DB, client)
```

Validation example:

```python
from pydantic import BaseModel, Field, field_validator

class ShortenRequest(BaseModel):
    url: str = Field(..., description="The long URL to shorten")

    @field_validator('url')
    @classmethod
    def validate_url(cls, v: str) -> str:
        # Generated from: invariant: isValidURI(value)
        from urllib.parse import urlparse
        result = urlparse(v)
        if not all([result.scheme, result.netloc]):
            raise ValueError('Must be a valid URI')
        return v
```

Error handling example:

```python
from fastapi import HTTPException

class NotFoundError(HTTPException):
    def __init__(self, entity: str, id: str):
        super().__init__(
            status_code=404,
            detail={
                "error": {
                    "code": f"{entity.upper()}_NOT_FOUND",
                    "message": f"{entity} with the given ID was not found",
                }
            }
        )

class ConflictError(HTTPException):
    def __init__(self, code: str, message: str):
        super().__init__(
            status_code=409,
            detail={"error": {"code": code, "message": message}}
        )
```

### 6.2 `go-chi-postgres`

**Stack:** Go chi router + sqlc + pgx + PostgreSQL

| Aspect              | Implementation                                                             |
| ------------------- | -------------------------------------------------------------------------- |
| **Entity -> Model** | Go struct with `json` and `db` tags                                        |
| **Validation**      | `go-playground/validator` struct tags + custom validation functions        |
| **DB Access**       | sqlc-generated type-safe query functions from SQL                          |
| **Routing**         | chi `Router` with middleware stack                                         |
| **Error Handling**  | Custom error types implementing `error` interface; middleware renders JSON |
| **Migrations**      | golang-migrate `.sql` files                                                |
| **JSON naming**     | snake_case via `json:"field_name"` struct tags                             |
| **Async**           | Goroutines for background tasks; context.Context for cancellation          |

Example generated code structure:

```
generated/
  cmd/
    server/
      main.go                 # HTTP server setup, dependency injection
  internal/
    models/
      models.go               # Go structs for entities
    handlers/
      short_codes.go          # HTTP handler functions
    services/
      short_code_service.go   # Business logic
    db/
      queries.sql             # SQL queries (input for sqlc)
      sqlc.yaml               # sqlc configuration
      query.sql.go            # sqlc-generated Go code (DO NOT EDIT)
      migrations/
        001_initial.up.sql
        001_initial.down.sql
    middleware/
      error.go                # Error rendering middleware
      validate.go             # Request validation middleware
  go.mod
  go.sum
```

Validation example:

```go
type ShortenRequest struct {
    URL string `json:"url" validate:"required,url"`
}

type ShortCode struct {
    Value string `json:"value" validate:"required,min=6,max=10,alphanum"`
}
```

Error handling example:

```go
type AppError struct {
    Code    string `json:"code"`
    Message string `json:"message"`
    Status  int    `json:"-"`
}

func (e *AppError) Error() string { return e.Message }

var ErrCodeNotFound = &AppError{
    Code:    "SHORT_CODE_NOT_FOUND",
    Message: "Short code not found",
    Status:  http.StatusNotFound,
}
```

### 6.3 `typescript-express-prisma`

**Stack:** Express.js + Prisma ORM + PostgreSQL + Zod

| Aspect              | Implementation                                                   |
| ------------------- | ---------------------------------------------------------------- |
| **Entity -> Model** | Prisma schema `model` declarations                               |
| **Validation**      | Zod schemas with `.parse()` / `.safeParse()`                     |
| **DB Access**       | Prisma Client with typed queries                                 |
| **Routing**         | Express `Router` with middleware                                 |
| **Error Handling**  | Custom error classes extending `Error`; Express error middleware |
| **Migrations**      | Prisma Migrate                                                   |
| **JSON naming**     | camelCase (JavaScript convention)                                |
| **Async**           | async/await with Express async handler wrapper                   |

Example generated code structure:

```
generated/
  src/
    index.ts                    # Express app setup
    prisma/
      schema.prisma             # Prisma schema
      migrations/
        001_initial/
          migration.sql
    models/
      shortCode.ts              # TypeScript interfaces
    schemas/
      shortCode.schema.ts       # Zod validation schemas
    routes/
      shortCodes.ts             # Express routes
    services/
      shortCodeService.ts       # Business logic
    middleware/
      errorHandler.ts           # Global error handler
      validate.ts               # Zod validation middleware
    errors/
      index.ts                  # Custom error classes
  package.json
  tsconfig.json
```

Validation example:

```typescript
import { z } from "zod";

export const ShortenRequestSchema = z.object({
  url: z.string().url("Must be a valid URI"),
});

export const ShortCodeParamSchema = z.object({
  code: z
    .string()
    .min(6, "Code must be at least 6 characters")
    .max(10, "Code must be at most 10 characters")
    .regex(/^[a-zA-Z0-9]+$/, "Code must be alphanumeric"),
});
```

### 6.4 `java-spring-jpa`

**Stack:** Spring Boot + Spring Data JPA + Hibernate + PostgreSQL + Bean Validation

| Aspect              | Implementation                                                                    |
| ------------------- | --------------------------------------------------------------------------------- |
| **Entity -> Model** | JPA `@Entity` class with `@Column` annotations                                    |
| **Validation**      | Bean Validation annotations (`@NotNull`, `@Size`, `@Pattern`) + custom validators |
| **DB Access**       | Spring Data JPA `Repository` interfaces with derived query methods                |
| **Routing**         | `@RestController` with `@RequestMapping`, `@GetMapping`, etc.                     |
| **Error Handling**  | `@ControllerAdvice` with `@ExceptionHandler` methods                              |
| **Migrations**      | Flyway `.sql` migration files                                                     |
| **JSON naming**     | camelCase (Jackson default)                                                       |
| **Async**           | `@Async` for background tasks; `CompletableFuture` return types                   |

Example generated code structure:

```
generated/
  src/main/java/com/example/urlshortener/
    UrlShortenerApplication.java
    model/
      ShortCode.java                # JPA entity
    dto/
      ShortenRequest.java           # Request DTO with Bean Validation
      ShortenResponse.java          # Response DTO
    repository/
      ShortCodeRepository.java      # Spring Data JPA interface
    service/
      ShortCodeService.java         # Business logic
    controller/
      ShortCodeController.java      # REST controller
    exception/
      GlobalExceptionHandler.java   # @ControllerAdvice
      ResourceNotFoundException.java
      ConflictException.java
  src/main/resources/
    application.yml
    db/migration/
      V1__initial.sql               # Flyway migration
  pom.xml
```

Validation example:

```java
public class ShortenRequest {
    @NotNull
    @URL(message = "Must be a valid URI")
    private String url;
}

@Entity
@Table(name = "short_codes")
public class ShortCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    @Size(min = 6, max = 10)
    @Pattern(regexp = "^[a-zA-Z0-9]+$")
    private String value;
}
```

---

## 7. Comparison with Existing Convention Systems

### 7.1 Ruby on Rails (Convention over Configuration)

**What Rails does:**

- Model class `Order` maps to table `orders` (pluralized, snake_case)
- `has_many :line_items` generates FK-based association
- `resources :orders` generates 7 RESTful routes (index, show, new, create, edit, update, destroy)
- Validation via `validates :name, presence: true, length: { maximum: 255 }`
- Callbacks (`before_save`, `after_create`) for side effects

**What we learn:**

- Pluralization and naming conventions are proven and well-understood; we adopt them directly
- The 7 standard REST actions (index/show/create/update/destroy + new/edit forms) cover most CRUD;
  we generate the same set
- Rails' approach to "member routes" for custom actions
  (`resources :orders do; member do; post :place; end; end`) maps directly to our state machine
  transition endpoints

**What we do differently:**

- Rails has no notion of preconditions or postconditions; its validations are structural only
- We derive routes from spec semantics (mutation analysis), not from explicit `resources`
  declarations
- We generate the database schema AND the application code from a single spec; Rails requires you to
  write migrations separately from models
- Our invariants become CHECK constraints AND application validation AND tests; Rails validations
  are application-only

### 7.2 JHipster JDL (Entity-to-Spring-Boot)

**What JHipster does:**

- JDL entity definitions -> Spring Boot entities, repositories, services, REST controllers,
  Angular/React UI
- Relationships (`OneToMany`, `ManyToMany`) -> JPA annotations + FK/junction tables
- Pagination, filtering, sorting generated automatically
- DTOs, mappers, and service layer generated

**What we learn:**

- JHipster proves that a full stack can be generated from a structural spec; this validates our
  approach
- JHipster's relationship types directly correspond to our multiplicity annotations
- JHipster's DTO/mapper pattern is a good model for separating API schemas from DB models

**What we do differently:**

- JHipster has no behavioral specs -- no preconditions, postconditions, or invariants. All generated
  endpoints accept any valid-shaped request regardless of business rules
- JHipster generates CRUD only; custom operations (state machines, transfers, computations) must be
  hand-coded
- We generate from a formal spec that can be model-checked BEFORE code generation; JHipster cannot
  detect spec inconsistencies
- We generate conformance tests from the spec; JHipster generates basic unit tests but no
  property-based tests

### 7.3 Django REST Framework (Model -> Serializer -> ViewSet -> Router)

**What DRF does:**

- `ModelSerializer` auto-generates serialization from Django model fields
- `ModelViewSet` generates list/create/retrieve/update/partial_update/destroy actions
- `DefaultRouter` maps ViewSet actions to URLs
- Permission classes, throttling, filtering, pagination are composable

**What we learn:**

- DRF's layered architecture (model -> serializer -> viewset -> router) is a clean separation of
  concerns
- DRF's `@action` decorator for custom endpoints maps to our state machine transition routes
- DRF's filter backends (django-filter) show how to auto-generate filtering from model fields

**What we do differently:**

- DRF requires you to write the Django model first; we generate the model from the spec
- DRF has no formal validation beyond field types; we generate validation from invariants and
  preconditions
- DRF's viewsets are CRUD-centric; our convention engine handles arbitrary operations including
  state machines and multi-entity mutations

### 7.4 Smithy Traits (Protocol-Agnostic Behavior)

**What Smithy does:**

- Traits annotate shapes with behavioral metadata (`@http`, `@auth`, `@paginated`, `@idempotent`)
- Protocol-agnostic: same Smithy model can target REST, gRPC, MQTT
- Code generators produce client SDKs and server stubs
- Used for all AWS SDK APIs

**What we learn:**

- Smithy's trait system is the best model for extensible metadata; our `conventions` block is
  directly inspired by Smithy traits
- Smithy's `@readonly`, `@idempotent` traits correspond to our mutation analysis (we infer these
  instead of requiring annotations)
- Smithy's resource-based modeling (resource with CRUD lifecycle) matches our entity concept

**What we do differently:**

- Smithy models API structure, not behavior; you describe WHAT the API looks like, not WHAT it does.
  We describe what the API does (pre/postconditions) and derive what it looks like
- Smithy requires manual annotation of every trait; we infer most traits from spec semantics
- Smithy does not generate database schemas or validation logic; it focuses on API surface
- We can verify spec consistency; Smithy validates structural correctness but not behavioral
  correctness

### 7.5 OpenAPI Code Generators

**What they do:**

- Take an OpenAPI YAML/JSON spec and generate client SDKs and server stubs in 40+ languages
- Mature, widely-used ecosystem

**What we learn:**

- OpenAPI is the lingua franca of REST API description; we should generate OpenAPI as an
  intermediate artifact
- The quality of generated server stubs varies wildly; we should not depend on third-party
  generators for production code

**What we do differently:**

- OpenAPI describes the API surface (paths, methods, schemas) but not the implementation; we
  generate both
- OpenAPI has no notion of state, operations, or invariants; it describes the HTTP interface only
- Our spec is upstream of OpenAPI; we generate OpenAPI from our spec as one of many artifacts
- We generate tests that verify the implementation matches the spec; OpenAPI generators produce no
  behavioral tests

### 7.6 Summary Comparison Table

| Feature                  | Rails                  | JHipster         | DRF                | Smithy           | OpenAPI-Gen            | **Our Engine**                     |
| ------------------------ | ---------------------- | ---------------- | ------------------ | ---------------- | ---------------------- | ---------------------------------- |
| Derives routes from spec | No (explicit)          | No (explicit)    | No (explicit)      | Partial (traits) | No (input IS routes)   | **Yes (from mutation analysis)**   |
| Generates DB schema      | No (manual migrations) | Yes (from JDL)   | No (manual models) | No               | No                     | **Yes (from state model)**         |
| Behavioral validation    | No                     | No               | No                 | No               | No                     | **Yes (from requires/ensures)**    |
| Formal verification      | No                     | No               | No                 | Structural only  | Structural only        | **Yes (model checking)**           |
| Test generation          | Basic                  | Basic            | Basic              | Contract tests   | No                     | **Property-based from spec**       |
| Custom operations        | Manual                 | Manual           | @action decorator  | Custom shapes    | Manual                 | **From spec operations**           |
| State machines           | Manual                 | Manual           | Manual             | Not modeled      | Not modeled            | **Auto-detected from transitions** |
| Multi-target             | Ruby only              | Java/Spring only | Python only        | 7+ languages     | 40+ (variable quality) | **4 targets (high quality)**       |

---

## 8. Implementation Architecture

### 8.1 Internal Structure

The convention engine is a pure function:

```
ConventionEngine(spec_ir: SpecIR, profile: Profile, overrides: Overrides) -> ConventionOutput
```

It has no side effects, no I/O, no database access. It takes a parsed spec (IR), a deployment
profile, and user overrides, and returns a complete set of infrastructure decisions.

```
                  ┌──────────────────────────────────┐
                  │         Convention Engine          │
                  │                                    │
  SpecIR ────────>│  ┌────────────────────────────┐   │
                  │  │   Phase 1: Entity Analysis  │   │
                  │  │   - Classify entities        │   │
                  │  │   - Detect relationships      │   │
                  │  │   - Identify value types       │   │
                  │  └──────────────┬───────────────┘   │
                  │                 v                    │
                  │  ┌────────────────────────────┐   │
                  │  │   Phase 2: Op Analysis       │   │
                  │  │   - Classify mutations        │   │
                  │  │   - Detect state transitions  │   │
                  │  │   - Identify parent-child ops  │   │
                  │  └──────────────┬───────────────┘   │
                  │                 v                    │
                  │  ┌────────────────────────────┐   │
                  │  │   Phase 3: Rule Application  │   │
                  │  │   - HTTP method rules         │   │
                  │  │   - URL path rules            │   │
                  │  │   - Status code rules         │   │
                  │  │   - DB schema rules           │   │
                  │  │   - Validation rules          │   │
                  │  └──────────────┬───────────────┘   │
                  │                 v                    │
  Profile ───────>│  ┌────────────────────────────┐   │
                  │  │   Phase 4: Override Merge    │   │
  Overrides ─────>│  │   - Apply user overrides     │   │
                  │  │   - Apply profile defaults    │   │
                  │  │   - Validate consistency      │   │
                  │  └──────────────┬───────────────┘   │
                  │                 v                    │
                  │           ConventionOutput           │──────> HTTP endpoints
                  │                                    │──────> DB schema
                  └──────────────────────────────────┘──────> Validation logic
                                                       ──────> Error mappings
                                                       ──────> OpenAPI spec
```

### 8.2 Phase 1: Entity Analysis

The entity analyzer classifies each entity in the spec:

| Classification      | Criteria                                          | Example                            |
| ------------------- | ------------------------------------------------- | ---------------------------------- |
| **Root entity**     | Has its own key in a state relation, no parent FK | `Order`, `User`, `Product`         |
| **Child entity**    | Referenced via `ParentId -> set Child` relation   | `LineItem` (child of Order)        |
| **Value type**      | Single field, no own state relations, used inline | `ShortCode`, `LongURL`             |
| **Junction entity** | Only exists to link two other entities (M:N)      | Auto-detected from `set` relations |

It also builds a relationship graph:

```python
@dataclass
class EntityInfo:
    name: str
    classification: Literal["root", "child", "value", "junction"]
    fields: list[FieldInfo]
    invariants: list[InvariantExpr]
    parent: Optional[str]          # for child entities
    children: list[str]            # for root entities
    relations: list[RelationInfo]  # all relations involving this entity
```

### 8.3 Phase 2: Operation Analysis

The operation analyzer classifies each operation:

```python
@dataclass
class OperationClassification:
    op_name: str
    kind: Literal[
        "create",          # new entity added to state
        "read_one",        # single entity retrieved by ID
        "read_many",       # collection retrieved with optional filters
        "update_full",     # all fields of existing entity modified
        "update_partial",  # subset of fields modified
        "delete",          # entity removed from state
        "transition",      # status/state field changed
        "action",          # side effect, possibly cross-entity
        "search",          # complex read with structured input
    ]
    target_entity: Optional[str]      # primary entity affected
    parent_entity: Optional[str]      # if operating on a child
    mutated_relations: list[str]      # state relations that change
    read_relations: list[str]         # state relations that are read
    path_params: list[ParamInfo]      # inputs that become path params
    query_params: list[ParamInfo]     # inputs that become query params
    body_params: list[ParamInfo]      # inputs that become body fields
    transition_field: Optional[str]   # for state machine transitions
    transition_from: Optional[set[str]]  # valid source states
    transition_to: Optional[str]      # target state
```

The classification algorithm:

1. **Mutation detection:** Compare `state'` references in ensures clause with `state` references.
   Any relation where the primed version differs from the unprimed is mutated.

2. **Create detection:** If the ensures clause contains `x not in pre(R)` followed by `R'[x] = ...`,
   this is a create operation on the entity stored in R.

3. **Delete detection:** If the ensures clause contains `x not in R'` where `x in R` is in the
   requires clause, this is a delete.

4. **Transition detection:** If the ensures clause modifies exactly one field (typically `status`)
   and the requires clause guards on the current value of that field, this is a state machine
   transition.

5. **Read detection:** If no relation is mutated (all `R' = R`), this is a read. Single-entity reads
   have an ID input; collection reads do not.

6. **Update detection:** If a relation's value is modified but its key set doesn't change, this is
   an update. Full vs partial is determined by field coverage (see Section 2.1).

### 8.4 Phase 3: Rule Application

Rules are applied in a deterministic order. Each rule is a function:

```python
class ConventionRule:
    name: str
    priority: int                    # lower = higher priority
    applies_to: Callable[[OpClassification], bool]
    apply: Callable[[OpClassification, EntityInfo], ConventionDecision]
```

Rules are organized into groups:

```python
HTTP_METHOD_RULES = [
    Rule("M1_create",     priority=10, applies_to=is_create,     apply=lambda _: POST),
    Rule("M2_read",       priority=10, applies_to=is_read,       apply=lambda _: GET),
    Rule("M3_update_full", priority=10, applies_to=is_full_update, apply=lambda _: PUT),
    Rule("M4_update_partial", priority=10, applies_to=is_partial_update, apply=lambda _: PATCH),
    Rule("M5_delete",     priority=10, applies_to=is_delete,     apply=lambda _: DELETE),
    Rule("M6_child_create", priority=5, applies_to=is_child_create, apply=lambda _: POST),
    Rule("M7_search",     priority=20, applies_to=is_complex_search, apply=decide_search_method),
    Rule("M8_action",     priority=30, applies_to=is_action,     apply=lambda _: POST),
    Rule("M9_batch",      priority=15, applies_to=is_batch,      apply=lambda _: POST),
    Rule("M10_transition", priority=5, applies_to=is_transition,  apply=lambda _: POST),
]
```

**Priority resolution:** When multiple rules match (e.g., an operation both creates an entity AND is
a child creation), the rule with the lower priority number wins. If two rules have equal priority
and both match, it is a convention engine bug (the rules are designed to be mutually exclusive at
each priority level).

**Conflict detection:** After all rules are applied, the engine checks for conflicts:

- Two operations mapping to the same (method, path) pair -> compilation error
- A GET endpoint with a mutation -> warning
- A DELETE endpoint that also creates -> warning

### 8.5 Phase 4: Override Merge

Overrides are merged in the resolution order specified in Section 3.3:

```python
def resolve(path: str, op: OpClassification) -> Any:
    # 1. Operation-level override
    if f"{op.name}.{path}" in overrides:
        return overrides[f"{op.name}.{path}"]
    # 2. Entity-level override
    if op.target_entity and f"{op.target_entity}.{path}" in overrides:
        return overrides[f"{op.target_entity}.{path}"]
    # 3. Global override
    if f"global.{path}" in overrides:
        return overrides[f"global.{path}"]
    # 4. Profile default
    if path in profile.defaults:
        return profile.defaults[path]
    # 5. Engine default
    return engine_default(path)
```

### 8.6 Output Data Structure

The convention engine produces a structured output that downstream code emitters consume:

```python
@dataclass
class ConventionOutput:
    endpoints: list[EndpointSpec]
    db_schema: DatabaseSchema
    validation_rules: list[ValidationRule]
    error_mappings: list[ErrorMapping]
    openapi: OpenAPISpec

@dataclass
class EndpointSpec:
    method: str                        # GET, POST, PUT, PATCH, DELETE
    path: str                          # /orders/{order_id}/line-items
    operation_id: str                  # addLineItem
    summary: str                       # Add a line item to an order
    path_params: list[ParamSpec]
    query_params: list[ParamSpec]
    request_body: Optional[SchemaSpec]
    response_body: Optional[SchemaSpec]
    success_status: int                # 200, 201, 204, 302
    error_responses: list[ErrorSpec]
    headers: dict[str, str]            # custom response headers
    auth: Optional[str]                # bearer, api_key, etc.

@dataclass
class DatabaseSchema:
    tables: list[TableSpec]
    indexes: list[IndexSpec]
    constraints: list[ConstraintSpec]
    triggers: list[TriggerSpec]
    migrations: list[MigrationSpec]

@dataclass
class TableSpec:
    name: str
    columns: list[ColumnSpec]
    primary_key: str
    foreign_keys: list[ForeignKeySpec]
    checks: list[str]                  # CHECK constraint SQL expressions
    unique_constraints: list[list[str]]  # groups of columns
```

### 8.7 Extensibility: Custom Rules and Plugins

The convention engine supports two extension points:

**1. Custom rules:** Users can define additional rules that participate in the rule application
phase:

```
conventions {
  rules {
    // Any operation whose name starts with "Admin" requires admin auth
    match: op.name starts_with "Admin"
    set: op.http_auth = "bearer"
    set: op.http_path_prefix = "/admin"
  }
}
```

Custom rules are evaluated after built-in rules but before overrides. They cannot override built-in
rules (use explicit overrides for that).

**2. Profile plugins:** Each deployment profile is implemented as a plugin that can define:

- Additional output artifacts (e.g., Dockerfile, docker-compose.yml, CI config)
- Profile-specific schema transformations (e.g., using Prisma's schema language instead of raw SQL)
- Profile-specific validation implementations

Plugin interface:

```python
class ProfilePlugin:
    def transform_schema(self, schema: DatabaseSchema) -> Any:
        """Convert generic schema to profile-specific format"""
        ...

    def transform_endpoint(self, endpoint: EndpointSpec) -> Any:
        """Convert generic endpoint to profile-specific route definition"""
        ...

    def additional_artifacts(self, output: ConventionOutput) -> dict[str, str]:
        """Generate additional files (Dockerfile, config, etc.)"""
        ...
```

### 8.8 Testing and Debugging

The convention engine is designed to be testable:

**Unit testing:** Each rule is a pure function that can be tested in isolation:

```python
def test_create_operation_maps_to_post():
    op = make_op(kind="create", target="Order")
    result = HTTP_METHOD_RULES.apply(op)
    assert result.method == "POST"

def test_read_operation_maps_to_get():
    op = make_op(kind="read_one", target="Order")
    result = HTTP_METHOD_RULES.apply(op)
    assert result.method == "GET"
```

**Decision tracing:** The engine can produce a trace of every decision it made, which is invaluable
for debugging:

```json
{
  "operation": "AddLineItem",
  "decisions": [
    {
      "aspect": "http_method",
      "rule": "M6_child_create",
      "value": "POST",
      "reason": "Operation creates LineItem (child of Order via line_items relation)"
    },
    {
      "aspect": "url_path",
      "rule": "nested_resource",
      "value": "/orders/{order_id}/line-items",
      "reason": "LineItem is child of Order; order_id is path param (key of orders relation)"
    },
    {
      "aspect": "success_status",
      "rule": "create_201",
      "value": 201,
      "reason": "POST that creates a new entity returns 201 Created"
    },
    {
      "aspect": "error_404",
      "rule": "existence_check",
      "source": "requires: order_id in orders",
      "value": 404,
      "reason": "Entity existence check on orders relation"
    }
  ]
}
```

This trace is emitted to stderr or a log file when the compiler runs with `--verbose` or
`--trace-conventions`.

**Snapshot testing:** The full convention output for each worked example (URL Shortener, E-commerce,
Social Feed) is captured as a snapshot test. Any change to the convention rules that alters the
output triggers a test failure, forcing explicit review of the change.

### 8.9 Performance Considerations

The convention engine processes each operation independently (no cross-operation dependencies except
for path conflict detection). This means:

- **Time complexity:** O(E + O \* R) where E = number of entities, O = number of operations, R =
  number of rules. For a typical service with 10 entities and 30 operations, this is
  sub-millisecond.
- **Memory:** The engine holds the full spec IR and produces the full output in memory. For any
  reasonable spec (thousands of entities), this is trivially small.
- **No I/O:** The engine does no file reading, network access, or database queries. It is a pure
  computation.

---

## Appendix A: Complete Decision Tree (Pseudocode)

```
function classify_operation(op, spec):
    mutated = {r for r in spec.state if r' != r in op.ensures}
    created = {r for r in mutated if exists x: "x not in pre(r)" in op.ensures}
    deleted = {r for r in mutated if exists x: "x not in r'" in op.ensures}
    transition_fields = detect_status_field_changes(op, spec)

    if mutated is empty:
        if has_collection_output(op):
            return READ_MANY
        else:
            return READ_ONE

    if created is not empty:
        parent = find_parent_entity(created, spec)
        if parent is not None:
            return CHILD_CREATE(parent)
        else:
            return CREATE

    if deleted is not empty:
        return DELETE

    if transition_fields is not empty:
        return TRANSITION(transition_fields)

    modified_fields = fields_assigned_in_ensures(op)
    entity_fields = all_fields_of(target_entity(op, spec))
    if modified_fields == entity_fields:
        return UPDATE_FULL
    else:
        return UPDATE_PARTIAL


function determine_http_method(classification):
    match classification:
        CREATE | CHILD_CREATE -> POST
        READ_ONE | READ_MANY  -> GET
        UPDATE_FULL           -> PUT
        UPDATE_PARTIAL        -> PATCH
        DELETE                -> DELETE
        TRANSITION            -> POST
        ACTION                -> POST


function determine_url_path(op, classification, spec):
    entity = target_entity(op, spec)
    plural = pluralize(entity.name)

    match classification:
        CREATE:
            return f"/{plural}"
        READ_ONE | UPDATE_FULL | UPDATE_PARTIAL | DELETE:
            id_param = find_id_param(op, entity, spec)
            return f"/{plural}/{{{id_param.name}}}"
        READ_MANY:
            return f"/{plural}"
        CHILD_CREATE(parent):
            parent_plural = pluralize(parent.name)
            parent_id = find_id_param(op, parent, spec)
            child_plural = pluralize(entity.name)
            return f"/{parent_plural}/{{{parent_id.name}}}/{child_plural}"
        TRANSITION(field):
            action = extract_action_verb(op.name, entity.name)
            id_param = find_id_param(op, entity, spec)
            return f"/{plural}/{{{id_param.name}}}/{action}"


function determine_status_codes(op, classification, spec):
    success = match classification:
        CREATE | CHILD_CREATE -> 201
        READ_ONE | READ_MANY | UPDATE_FULL | UPDATE_PARTIAL | TRANSITION | ACTION -> 200
        DELETE -> 204

    errors = []
    for clause in op.requires:
        if is_existence_check(clause):
            errors.append(404)
        elif is_uniqueness_check(clause):
            errors.append(409)
        elif is_state_guard(clause):
            errors.append(409)
        elif is_value_validation(clause):
            errors.append(422)

    return success, errors
```

## Appendix B: Naming Convention Reference

| Concept              | Convention                       | Example                 |
| -------------------- | -------------------------------- | ----------------------- |
| DB table name        | Plural, snake_case               | `line_items`            |
| DB column name       | Singular, snake_case             | `order_id`              |
| DB FK column         | `{referenced_table_singular}_id` | `product_id`            |
| DB junction table    | `{table1}_{table2}_{relation}`   | `user_follows`          |
| DB index name        | `idx_{table}_{column(s)}`        | `idx_orders_status`     |
| DB trigger name      | `trg_{table}_{purpose}`          | `trg_orders_updated_at` |
| API path segment     | Plural, kebab-case               | `/line-items`           |
| API path param       | Singular, snake_case in braces   | `{order_id}`            |
| API query param      | snake_case                       | `sort_by`               |
| JSON field (default) | snake_case                       | `customer_email`        |
| OpenAPI operationId  | camelCase, verb + noun           | `addLineItem`           |
| Error code           | SCREAMING_SNAKE_CASE             | `ORDER_NOT_FOUND`       |
| Pydantic model       | PascalCase + suffix              | `ShortenRequest`        |
| Go struct            | PascalCase                       | `LineItem`              |
| Java class           | PascalCase                       | `ShortCodeController`   |

## Appendix C: Multiplicity Quick Reference

| Alloy Syntax    | Meaning                     | DB Schema                      | Example                            |
| --------------- | --------------------------- | ------------------------------ | ---------------------------------- |
| `A -> one B`    | Every A has exactly one B   | NOT NULL FK in A's table       | `post_author: Post -> one User`    |
| `A -> lone B`   | Every A has at most one B   | Nullable FK in A's table       | `store: ShortCode -> lone LongURL` |
| `A -> some B`   | Every A has one or more Bs  | Junction table + min-1 trigger | `tags: Article -> some Tag`        |
| `A -> set B`    | Every A has zero or more Bs | Junction table                 | `likes: User -> set Post`          |
| `A -> B` (bare) | Same as `A -> one B`        | NOT NULL FK                    | Default multiplicity               |

---

<!-- Added: security defaults (gap analysis) -->

## Appendix D: Security Defaults in Generated Code

The convention engine applies secure defaults to every generated service. These can be overridden
via the `conventions` block but are designed to be safe out of the box.

### D.1 CORS Policy

The default CORS configuration is restrictive:

```
conventions {
  global.cors.allow_origins = []          // no origins allowed by default
  global.cors.allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
  global.cors.allow_headers = ["Authorization", "Content-Type"]
  global.cors.max_age = 86400             // 24 hours preflight cache
}
```

Developers must explicitly list allowed origins. The generated code never uses
`allow_origins = ["*"]` unless the user overrides to `global.cors.allow_origins = ["*"]`.

### D.2 Rate Limiting

Default rate limiting is applied globally and can be customized per-operation:

```
conventions {
  global.rate_limit = "100/minute"            // default for all endpoints
  CreateUser.rate_limit = "10/minute"         // tighter limit for account creation
  Login.rate_limit = "5/minute"               // brute-force protection
}
```

The convention engine generates rate-limiting middleware using an in-memory token bucket (single
instance) or Redis-backed token bucket (when a Redis convention profile is active). Rate-limited
requests receive HTTP 429 with a `Retry-After` header.

### D.3 Input Sanitization and Request Limits

| Convention              | Default                                                           | Override                            |
| ----------------------- | ----------------------------------------------------------------- | ----------------------------------- |
| Max request body size   | 1 MB                                                              | `global.max_request_body = "10MB"`  |
| Max JSON nesting depth  | 20 levels                                                         | `global.max_json_depth = 50`        |
| String field max length | 10,000 chars (unless entity specifies otherwise)                  | Per-field `where` constraint        |
| Regex complexity check  | Enabled -- rejects ReDoS-vulnerable patterns in entity invariants | `global.regex_safety_check = false` |
| SQL injection           | Prevented by default (ORM + parameterized queries)                | Not overridable                     |

---

<!-- Added: API versioning (gap analysis) -->

## Appendix E: API Versioning Conventions

The convention engine supports three API versioning strategies, selected via convention override.
The default is URL-prefix versioning.

### E.1 URL Prefix Versioning (Default)

```
conventions {
  global.api_version = "v1"
  global.versioning_strategy = "url_prefix"    // default
}
```

All generated paths are prefixed: `POST /v1/shorten`, `GET /v1/{code}`, etc. When the spec evolves
to a breaking change, the developer bumps `global.api_version = "v2"` and the compiler can generate
both versions side by side (the old spec file is preserved as `v1.spec`, the new one as `v2.spec`).

### E.2 Header-Based Versioning

```
conventions {
  global.api_version = "1"
  global.versioning_strategy = "header"
  global.version_header = "X-API-Version"      // default header name
}
```

The generated router inspects the `X-API-Version` header and routes to the appropriate handler.
Missing header defaults to the latest version. The OpenAPI spec includes the header as a parameter
on every operation.

### E.3 Content Negotiation (Accept Header)

```
conventions {
  global.api_version = "1"
  global.versioning_strategy = "content_type"
}
```

Clients send `Accept: application/vnd.service-name.v1+json`. The generated router parses the vendor
media type and routes accordingly. This follows GitHub API conventions.

### E.4 Convention Engine Mapping

| Strategy               | Path          | Header             | Media Type                          |
| ---------------------- | ------------- | ------------------ | ----------------------------------- |
| `url_prefix` (default) | `/v1/shorten` | --                 | `application/json`                  |
| `header`               | `/shorten`    | `X-API-Version: 1` | `application/json`                  |
| `content_type`         | `/shorten`    | --                 | `application/vnd.myservice.v1+json` |

---

<!-- Added: caching conventions (gap analysis) -->

## Appendix F: Caching Conventions

The convention engine generates HTTP caching headers and ETag support for read operations, enabling
clients and CDNs to cache responses efficiently.

### F.1 ETag Generation

For every GET response that returns an entity with an `updated_at` or `version` field, the
convention engine generates an ETag header:

```
ETag: "{entity_type}:{id}:{version}"       // if version field exists
ETag: "{entity_type}:{id}:{updated_at_epoch}"  // fallback to timestamp
```

The generated router supports conditional requests:

- `If-None-Match` on GET: returns HTTP 304 Not Modified if the ETag matches
- `If-Match` on PUT/PATCH/DELETE: returns HTTP 412 Precondition Failed if stale

### F.2 Cache-Control Headers

Default cache-control conventions based on operation type:

| Operation Type        | Default Cache-Control                 | Rationale                          |
| --------------------- | ------------------------------------- | ---------------------------------- |
| GET single entity     | `private, max-age=0, must-revalidate` | Safe with ETag; client revalidates |
| GET collection/list   | `private, max-age=60`                 | Short TTL for lists                |
| POST/PUT/PATCH/DELETE | `no-store`                            | Mutations must not be cached       |

Override via conventions:

```
conventions {
  Resolve.cache_control = "public, max-age=3600"    // CDN-cacheable for 1 hour
  GetFeed.cache_control = "private, max-age=30"     // short-lived personalized content
}
```

### F.3 Application-Level Caching

When a Redis convention profile is active, the convention engine generates a read-through cache for
GET operations. Cache invalidation is triggered automatically by any operation that mutates the same
state relation:

```
conventions {
  global.cache_backend = "redis"
  Resolve.cache_ttl = 3600                 // cache resolved URLs for 1 hour
}
```

The generated code invalidates the cache entry for a short code whenever `Delete` or any operation
mutating `store` is invoked for that key.
