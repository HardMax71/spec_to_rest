---
title: "The convention ruleset"
description: "Design philosophy and the convention ruleset; the live, code-anchored rules are in the reference"
---

The Convention Engine inherits the central insight from Alchemy (2008): state-change predicates map
to write operations, and facts map to integrity constraints. We generalize this beyond databases to
the full REST + DB + validation + serialization stack.

### Core principles

1. **Every spec element maps to exactly one infrastructure artifact**, no ambiguity, no manual
   wiring. An entity becomes a table, a mutation becomes a POST/PUT/DELETE, a precondition becomes a
   validation check, an invariant becomes a constraint.

2. **Conventions are deterministic**, given the same spec, the engine always produces the same
   output. There is no randomness, no heuristic guessing.

3. **Conventions are overridable**, every decision the engine makes can be overridden by the user
   via the `conventions` block. The engine provides sensible defaults; the user adjusts what doesn't
   fit.

4. **Conventions are target-agnostic in the abstract, target-specific in the concrete**, the
   abstract mapping (operation -> HTTP method) is universal. The concrete rendering (Python class vs
   Go struct vs Java interface) varies by deployment profile.

5. **When in doubt, follow RFC 7231 and REST best practices**, the engine does not invent novel
   HTTP semantics. It maps to well-understood patterns.

## 2. The complete convention ruleset

### 2.1 HTTP method mapping

The engine determines the HTTP method for each operation by analyzing its effect on state. The
decision tree is evaluated top-to-bottom; the first matching rule wins.

#### Rule table

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

#### PUT vs PATCH disambiguation

The distinction between PUT (M3) and PATCH (M4) requires analyzing the ensures clause field
coverage:

```text
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

```text
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

```text
operation UpdateUserEmail {
  input: id: UserId, email: String
  requires: id in users
  ensures:
    users'[id].email = email
    // other fields implicitly unchanged
}
// Only email changes -> PATCH
```

#### Operations that don't fit

**Read-with-body.** When an operation reads state but requires a complex structured input
(e.g., a search query with nested filters, geo-bounding boxes, or full-text search parameters), the
engine applies rule M7. It first attempts to flatten the input into query parameters. If the input
contains nested structures, arrays of objects, or would produce a query string exceeding 2048
characters, it falls back to `POST /{resource}/search` with the input as the request body.

**Side-effect-only operations.** Operations that have side effects external to the modeled state
(sending emails, triggering webhooks, publishing events) are recognized by the pattern: the ensures
clause either leaves state unchanged or the mutation is to an audit/log relation. These always map
to POST (rule M8).

**Batch operations.** Operations whose input includes a collection of entities (e.g.,
`input: items: Set[OrderItem]`) or whose ensures clause modifies multiple keys in a state relation
trigger rule M9.

### 2.2 URL path mapping

#### Resource name derivation

| Spec Element                                         | URL Path Segment                               | Rule                                                                                      |
| ---------------------------------------------------- | ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Entity name (singular)                               | Pluralized, lowercased, hyphenated             | `OrderItem` -> `order-items`                                                              |
| State relation name (if it differs from the entity)  | Ignored for path; entity name takes precedence | `store: ShortCode -> LongURL` uses `short-codes` not `store`                              |
| Operation that acts on a specific entity by ID       | `/{resource}/{id}`                             | Input with the entity's key type -> path parameter                                        |
| Operation that acts on a collection                  | `/{resource}`                                  | No key-type input or input is a filter                                                    |
| Operation that acts on a child entity under a parent | `/{parent}/{parent_id}/{children}`             | Detected when ensures clause creates/reads/deletes in a relation anchored to a parent key |

#### Pluralization rules

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

#### Nested resource detection

The engine detects parent-child relationships from the state model:

```text
state {
  orders: OrderId -> Order
  line_items: OrderId -> set LineItem   // LineItem is nested under Order
}
```

The relation `line_items: OrderId -> set LineItem` signals that LineItem is a child of Order because
the relation key is `OrderId`. This produces:

- `GET /orders/{order_id}/line-items`, list items for an order
- `POST /orders/{order_id}/line-items`, add item to an order
- `GET /orders/{order_id}/line-items/{item_id}`, get specific item
- `DELETE /orders/{order_id}/line-items/{item_id}`, remove item

**Nesting depth limit.** The engine nests at most 2 levels deep. If a relation chain goes deeper
(e.g., Order -> LineItem -> LineItemOption), the third level gets a top-level resource with a query
parameter filter: `GET /line-item-options?line_item_id={id}` rather than
`GET /orders/{oid}/line-items/{lid}/options`.

#### Path parameter extraction

An operation input becomes a path parameter when:

1. Its type matches the key type of a state relation (e.g., `input: id: OrderId` where
   `orders: OrderId -> Order`), AND
2. The operation reads, mutates, or deletes a specific entity identified by that key.

All other inputs become either query parameters (for GET) or body fields (for POST/PUT/PATCH).

#### Query parameter mapping for filters

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

### 2.3 HTTP status code mapping

#### Success status codes

| Condition                                                      | Status Code                                                        | Headers                          |
| -------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------------------- |
| POST that creates a new entity                                 | **201 Created**                                                    | `Location: /{resource}/{new_id}` |
| GET that returns data                                          | **200 OK**                                                         |   |
| PUT/PATCH that updates an entity                               | **200 OK** (if response body) or **204 No Content** (if no output) |   |
| DELETE that removes an entity                                  | **204 No Content**                                                 |   |
| POST action that triggers a side effect                        | **200 OK** (if response body) or **202 Accepted** (if async)       |   |
| Operation with redirect semantics (overridden via conventions) | **302 Found**                                                      | `Location: {target_url}`         |

#### Error status codes from requires clauses

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

##### Priority logic for compound requires clauses

When a requires clause is a conjunction (`and`), the engine generates checks in order and returns
the first failure. When it is a disjunction (`or`), the engine returns an error only if ALL
disjuncts fail, using the code of the most "specific" failing clause (404 > 409 > 422).

Example:

```text
requires:
  id in orders                           // -> 404 if fails
  and orders[id].status = "draft"        // -> 409 if fails
  and total > 0                          // -> 422 if fails
```

The engine generates three sequential checks. If `id` is not in orders, return 404. If the order
exists but status is not "draft", return 409. If total is not positive, return 422.

#### Error status codes from invariant violations

| Invariant Type                                                   | Status Code                  | When                                          |
| ---------------------------------------------------------------- | ---------------------------- | --------------------------------------------- |
| Entity field constraint (e.g., `len(value) >= 6`)                | **422 Unprocessable Entity** | Violated during input validation (pre-DB)     |
| Uniqueness constraint (detected from `state: Key -> lone Value`) | **409 Conflict**             | Violated at DB level (duplicate key)          |
| Cross-entity invariant (e.g., `inventory >= 0 after order`)      | **409 Conflict**             | Violated at application level (business rule) |
| Database CHECK constraint violation                              | **409 Conflict**             | Caught from DB exception                      |

### 2.4 Request/response body mapping

#### Input-to-request mapping

| Input Characteristic                                           | Placement                                      | Format                              |
| -------------------------------------------------------------- | ---------------------------------------------- | ----------------------------------- |
| Key type matching a state relation (entity ID)                 | Path parameter                                 | `/{resource}/{id}`                  |
| Primitive type on a GET operation                              | Query parameter                                | `?name=value`                       |
| Complex/nested type on a GET operation                         | Query parameter (dot-notation) or POST /search | `?filter.status=active`             |
| Any type on a POST/PUT/PATCH operation (excluding path params) | Request body (JSON)                            | `{"field": value}`                  |
| File or binary type                                            | Multipart form data (POST/PUT only)            | `Content-Type: multipart/form-data` |

#### Output-to-response mapping

| Output Characteristic       | Response Format                                                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| Single entity               | `{"data": {entity_fields...}, "meta": {...}}`                                                                             |
| Collection of entities      | `{"data": [{...}, {...}], "meta": {"page": 1, "limit": 20, "total": N}}`                                                  |
| Single scalar value         | `{"data": value}`                                                                                                         |
| No output (void)            | Empty body, 204 No Content                                                                                                |
| Entity with nested children | Inline nested array: `{"data": {"id": 1, "items": [{...}]}}` up to 1 nesting level; deeper nesting returns IDs with links |

#### Naming conventions

The engine uses **snake_case** for JSON field names by default (matching Python/Ruby conventions and
the majority of public REST APIs). This is configurable per profile:

| Profile                     | JSON Field Convention                       | Rationale                                               |
| --------------------------- | ------------------------------------------- | ------------------------------------------------------- |
| `python-fastapi-postgres`   | snake_case                                  | Python ecosystem standard                               |
| `go-chi-postgres`           | snake_case (JSON) / PascalCase (Go structs) | Go exports require PascalCase; JSON tags use snake_case |
| `typescript-express-prisma` | camelCase                                   | JavaScript ecosystem standard                           |
| `java-spring-jpa`           | camelCase                                   | Java/Jackson default                                    |

Conversion between spec field names (which are snake_case in the DSL) and the target naming
convention is handled by the code emitter, rather than the convention engine. The convention engine always
works in snake_case internally.

#### Envelope format

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

### 2.5 Database schema mapping

#### Entity-to-table mapping

| Spec Element                                                | Database Artifact                                        | Details                                     |
| ----------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------- |
| `entity Foo { ... }`                                        | Table `foos`                                             | Pluralized, snake_case                      |
| Entity field `name: String`                                 | Column `name TEXT`                                       | Type mapping table below                    |
| Entity field `age: Int`                                     | Column `age INTEGER`                                     |   |
| Entity field with `invariant: len(x) >= 6 and len(x) <= 10` | Column with `CHECK (length(x) >= 6 AND length(x) <= 10)` |   |
| Entity field with `invariant: x matches /^[a-z]+$/`         | Column with `CHECK (x ~ '^[a-z]+$')` (Postgres)          | Regex syntax is target-specific             |
| No explicit primary key declared                            | Auto-generated `id` column: `BIGSERIAL PRIMARY KEY`      | Convention: every table gets a surrogate PK |

#### Type mapping

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

#### Relation-to-schema mapping

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

##### Junction table structure

```sql
CREATE TABLE orders_line_items_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    line_item_id BIGINT NOT NULL REFERENCES line_items(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(order_id, line_item_id)  -- prevent duplicates
);
```

#### Invariant-to-constraint mapping

| Invariant Pattern                                               | Database Artifact                                                     |
| --------------------------------------------------------------- | --------------------------------------------------------------------- |
| `invariant: len(f) >= N and len(f) <= M`                        | `CHECK (length(f) >= N AND length(f) <= M)`                           |
| `invariant: f matches /^regex$/`                                | `CHECK (f ~ '^regex$')` (Postgres)                                    |
| `invariant: f > 0`                                              | `CHECK (f > 0)`                                                       |
| `invariant: f in {v1, v2, v3}` (enum-like)                      | `CHECK (f IN ('v1', 'v2', 'v3'))` or create an ENUM type              |
| `invariant: all x in R \| P(x)` (global, over a relation)       | Trigger: `BEFORE INSERT OR UPDATE` that checks P for the affected row |
| Uniqueness detected from `state: Key -> lone Value` (injective) | `UNIQUE` constraint on the value column                               |

#### Automatic columns

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

#### Index generation

The engine automatically creates indexes based on operation patterns:

| Pattern                                                                        | Index                                                                                        |
| ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- |
| Operation filters by field `f` (appears in requires or input-to-query mapping) | `CREATE INDEX idx_{table}_{field} ON {table}({field})`                                       |
| Foreign key column                                                             | `CREATE INDEX idx_{table}_{fk} ON {table}({fk})` (most databases auto-index PKs but not FKs) |
| Junction table columns                                                         | Composite index on `(parent_id, child_id)`                                                   |
| Field with uniqueness invariant                                                | `CREATE UNIQUE INDEX` instead of regular index                                               |
| `sort_by` parameter references a field                                         | Index on that field (supports efficient ORDER BY)                                            |
| Full-text search operation exists                                              | `GIN` index on the searchable text column (Postgres-specific)                                |

### 2.6 Validation mapping

Validation occurs at three layers, and the convention engine decides what goes where:

#### Layer 1: HTTP / request validation (before any application logic)

| Spec Element                                                                  | Validation Check          | Implementation                                             |
| ----------------------------------------------------------------------------- | ------------------------- | ---------------------------------------------------------- |
| Entity field type `Int` but input is string                                   | Type coercion / rejection | JSON Schema / Pydantic type enforcement                    |
| `invariant: len(f) >= N` on entity field                                      | String length check       | JSON Schema `minLength` / Pydantic `min_length`            |
| `invariant: f matches /^regex$/`                                              | Regex pattern match       | JSON Schema `pattern` / Pydantic `regex`                   |
| `invariant: f > 0`                                                            | Numeric range check       | JSON Schema `exclusiveMinimum: 0` / Pydantic `gt=0`        |
| Required field (appears in `one` multiplicity or operation input without `?`) | Presence check            | JSON Schema `required` array / Pydantic non-Optional field |
| Optional field (appears in `lone` multiplicity or input with `?`)             | Allow null/absent         | JSON Schema without `required` / Pydantic `Optional`       |

**Principle.** Anything that can be checked without hitting the database is checked at this layer.
This includes type checks, format checks, range checks, and required field checks.

#### Layer 2: Application / business logic validation (after parsing, before db)

| Spec Element                                          | Validation Check       | Implementation                             |
| ----------------------------------------------------- | ---------------------- | ------------------------------------------ |
| `requires: id in {relation}`                          | Entity existence check | `SELECT EXISTS(...)` query or ORM `.get()` |
| `requires: entity.status = "expected"`                | State machine guard    | Load entity, check field value             |
| `requires: input.x != input.y`                        | Cross-field validation | Application code after parsing             |
| `requires: f(input)` where `f` is a complex predicate | Custom business rule   | Generated function from spec predicate     |
| Cross-entity invariant                                | Consistency check      | Transaction-scoped query                   |

**Principle.** Anything that requires reading current database state is checked at this layer. It
runs inside a transaction so the check and the subsequent mutation are atomic.

#### Layer 3: Database constraints (last line of defense)

| Spec Element                            | Validation Check       | Implementation                                 |
| --------------------------------------- | ---------------------- | ---------------------------------------------- |
| Entity field invariant                  | CHECK constraint       | Catches violations that somehow bypass Layer 1 |
| Uniqueness                              | UNIQUE constraint      | Catches race conditions that bypass Layer 2    |
| Foreign key existence                   | FOREIGN KEY constraint | Catches referential integrity violations       |
| `some` multiplicity minimum cardinality | Trigger                | Ensures at least one child exists              |

**Principle.** Database constraints are the safety net. They catch violations that bypass
application-level checks (race conditions, direct DB access, bugs). The application should never
rely on the DB constraint as the primary validation mechanism because DB error messages are not
user-friendly.

#### Validation error aggregation

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

### 2.7 Error response mapping

#### Structured error codes

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

#### Human-readable error messages

The engine generates default messages from the clause structure:

| Clause                        | Generated Message                                           |
| ----------------------------- | ----------------------------------------------------------- |
| `id in orders`                | "Order with the given ID was not found"                     |
| `orders[id].status = "draft"` | "Order must be in 'draft' status to perform this operation" |
| `total > 0`                   | "Total must be greater than 0"                              |
| `len(code) >= 6`              | "Code must be at least 6 characters long"                   |

These messages are overridable via the conventions block.
