---
title: "Extensions and appendices"
description: "Built-in functions, convention defaults, a notation cheat sheet, and sketches for auth, concurrency, external effects, and non-determinism"
---

## Appendix A: Built-in functions

The following functions are available in all expression contexts:

| Function                | Signature                   | Meaning                                     |
| ----------------------- | --------------------------- | ------------------------------------------- |
| `len(s)`                | `String -> Int`             | Length of string                            |
| `isValidURI(s)`         | `String -> Bool`            | String is a valid URI (RFC 3986)            |
| `startsWith(s, prefix)` | `String, String -> Bool`    | String starts with prefix                   |
| `endsWith(s, suffix)`   | `String, String -> Bool`    | String ends with suffix                     |
| `contains(s, sub)`      | `String, String -> Bool`    | String contains substring                   |
| `matches(s, regex)`     | `String, Regex -> Bool`     | String matches regex pattern                |
| `dom(r)`                | `Relation -> Set`           | Domain of a relation                        |
| `ran(r)`                | `Relation -> Set`           | Range of a relation                         |
| `#expr`                 | `Collection -> Int`         | Cardinality (number of elements)            |
| `sum(coll, fn)`         | `Collection, Lambda -> Int` | Sum of fn applied to each element           |
| `min(a, b)`             | `Int, Int -> Int`           | Minimum                                     |
| `max(a, b)`             | `Int, Int -> Int`           | Maximum                                     |
| `abs(n)`                | `Int -> Int`                | Absolute value                              |
| `now()`                 | `-> DateTime`               | Current timestamp                           |
| `days(n)`               | `Int -> Duration`           | Duration of n days                          |
| `hours(n)`              | `Int -> Duration`           | Duration of n hours                         |
| `minutes(n)`            | `Int -> Duration`           | Duration of n minutes                       |
| `hash(s)`               | `String -> String`          | Cryptographic hash (implementation-defined) |
| `some(v)`               | `T -> Option[T]`            | Wrap value in Option                        |
| `none`                  | `-> Option[T]`              | Empty Option                                |

## Appendix B: Convention engine default rules

The convention engine maps spec elements to REST/HTTP/DB artifacts using these default rules. All
defaults can be overridden in the `conventions` block.

### HTTP method inference

| Operation Characteristic                                           | Default Method |
| ------------------------------------------------------------------ | -------------- |
| Has ensures with state mutation, has input, creates new entity     | `POST`         |
| No state mutation (all primed = unprimed)                          | `GET`          |
| Removes entity from state                                          | `DELETE`       |
| Modifies existing entity (primed state field changes, no new keys) | `PATCH`        |
| Replaces existing entity entirely                                  | `PUT`          |

### HTTP path inference

| Operation Characteristic   | Default Path                               |
| -------------------------- | ------------------------------------------ |
| Creates entity of type T   | `/{t-plural}`                              |
| Reads single entity by key | `/{t-plural}/{key}`                        |
| Lists entities             | `/{t-plural}`                              |
| State transition on entity | `/{t-plural}/{key}/{operation-name-kebab}` |
| Deletes entity by key      | `/{t-plural}/{key}`                        |

### HTTP status code inference

| Outcome                                               | Default Status             |
| ----------------------------------------------------- | -------------------------- |
| Successful creation (POST)                            | `201 Created`              |
| Successful read (GET)                                 | `200 OK`                   |
| Successful update (PATCH/PUT)                         | `200 OK`                   |
| Successful deletion (DELETE)                          | `204 No Content`           |
| Precondition fails: entity not found (`x in store`)   | `404 Not Found`            |
| Precondition fails: validation (`isValidURI(url)`)    | `422 Unprocessable Entity` |
| Precondition fails: state conflict (`status = DRAFT`) | `409 Conflict`             |
| Invariant would be violated                           | `422 Unprocessable Entity` |

### Database mapping

| Spec Element                       | Default DB Artifact                            |
| ---------------------------------- | ---------------------------------------------- |
| Entity                             | Table (name: snake_case plural of entity name) |
| Entity field (primitive)           | Column                                         |
| Entity field (Option[T])           | Nullable column                                |
| Entity field (Set[T], T primitive) | JSONB column or junction table                 |
| Entity field (Set[T], T entity)    | Junction table                                 |
| State field (A -> one B)           | Non-nullable foreign key on A's table          |
| State field (A -> lone B)          | Nullable foreign key on A's table              |
| State field (A -> some/set B)      | Junction table                                 |
| Type constraint (where)            | CHECK constraint                               |
| Invariant (field-level)            | CHECK constraint                               |
| Invariant (cross-entity)           | Trigger or application-level enforcement       |
| Enum                               | PostgreSQL ENUM type or CHECK constraint       |

## Appendix C: Formal notation comparison cheat sheet

This table maps common formal notation to our DSL syntax, showing how we replace mathematical
symbols with readable keywords.

| Concept               | Math / Alloy / TLA+       | Our DSL                    |
| --------------------- | ------------------------- | -------------------------- |
| For all               | `\A x \in S`, `all x: S`  | `all x in S \| P(x)`      |
| There exists          | `\E x \in S`, `some x: S` | `some x in S \| P(x)`     |
| None satisfy          | `no x: S`                 | `no x in S \| P(x)`       |
| Not element of        | `\notin`, `not in`        | `x not in S`              |
| Element of            | `\in`, `in`               | `x in S`                  |
| Union                 | `\cup`, `+`               | `A + B`                   |
| Intersection          | `\cap`, `&`               | `A & B`                   |
| Difference            | `\setminus`, `-`          | `A - B`                   |
| Cardinality           | `\|S\|`, `#S`             | `#S`                      |
| Next-state (primed)   | `x'`, `EXCEPT`            | `x'`                      |
| Previous state        | `~`, `old()`              | `pre(x)`                  |
| Logical and           | `\land`, `/\`             | `and`                     |
| Logical or            | `\lor`, `\/`              | `or`                      |
| Logical not           | `\lnot`, `~`              | `not`                     |
| Implies               | `=>`, `\implies`          | `implies`                 |
| If and only if        | `<=>`, `\iff`             | `iff`                     |
| Partial function      | `+->`, `-> lone`          | `-> lone`                 |
| Total function        | `-->`, `-> one`           | `-> one`                  |
| Always (temporal)     | `[]`, `always`            | (future: `always`)        |
| Eventually (temporal) | `<>`, `eventually`        | (future: `eventually`)    |

<!-- Added: auth/authz model (gap analysis) -->

## Appendix D: Authentication and authorization in the spec language

The spec language must model authentication and authorization as first-class constructs so that
access control becomes a formally verifiable property of the spec rather than a middleware afterthought. This addresses OWASP API
Top 10 #1 (Broken Object Level Authorization), #2 (Broken Authentication), and #5 (Broken Function
Level Authorization).

### D.1 Caller context

Every operation implicitly has access to a `caller` context representing the authenticated
principal. The `caller` entity is built-in:

```text
builtin entity Caller {
  id: Int
  role: Role           // enum defined per-service
  authenticated: Bool
  scopes: Set[String]  // OAuth-style scopes
}
```

### D.2 Authentication guards

The simplest guard requires only that a caller is authenticated:

```text
operation CreatePost {
  input:  title: String, body: String
  output: post: Post

  requires:
    authenticated                         // shorthand for caller.authenticated = true
    len(title) >= 1

  ensures:
    post.author_id = caller.id
    posts' = pre(posts) + {post.id -> post}
}
```

The convention engine maps `requires: authenticated` to JWT/bearer token validation middleware.
Unauthenticated requests receive HTTP 401.

### D.3 Role-based access control (RBAC)

Role checks use the `caller.role` field against a service-defined enum:

```text
enum Role { User, Moderator, Admin }

operation DeleteUser {
  input:  user_id: Int

  requires:
    authenticated
    caller.role in {Admin}                // only admins

  ensures:
    user_id not in users'
}

operation BanUser {
  input:  user_id: Int

  requires:
    authenticated
    caller.role in {Admin, Moderator}     // admins or moderators

  ensures:
    users'[user_id].banned = true
}
```

The convention engine maps `caller.role in {X, Y}` to role-checking middleware. Unauthorized callers
receive HTTP 403.

### D.4 Resource ownership (object-level authorization)

The most critical pattern, preventing users from accessing or modifying resources they do not own:

```text
operation UpdatePost {
  input:  post_id: Int, body: String
  output: post: Post

  requires:
    authenticated
    post_id in posts
    caller.id = posts[post_id].author_id   // ownership check

  ensures:
    post.body = body
    posts' = pre(posts) + {post_id -> post}
}
```

The convention engine maps `caller.id = resource.owner_field` to a generated authorization check
that runs after resource lookup. Violations return HTTP 403 with an error body
`{ "code": "FORBIDDEN", "detail": "You do not own this resource" }`.

### D.5 Scope-based access (OAuth/API key scopes)

For APIs that use OAuth scopes or API key permissions:

```text
operation ReadAnalytics {
  input:  report_id: Int
  output: report: AnalyticsReport

  requires:
    authenticated
    "analytics:read" in caller.scopes

  ensures:
    report = analytics[report_id]
}
```

### D.6 Convention engine mapping

| Spec Construct                            | Convention Output                                |
| ----------------------------------------- | ------------------------------------------------ |
| `requires: authenticated`                 | Bearer token middleware; HTTP 401 on failure     |
| `requires: caller.role in {Admin}`        | Role-check middleware; HTTP 403 on failure       |
| `requires: caller.id = resource.owner_id` | Post-lookup ownership check; HTTP 403 on failure |
| `requires: "scope" in caller.scopes`      | Scope validation middleware; HTTP 403 on failure |
| No auth `requires` on an operation        | Public endpoint (no middleware)                  |

The auth model is formally verifiable: the model checker can confirm that every state mutation is
reachable only through operations with appropriate guards, and that no operation accidentally
exposes state without an ownership check.

<!-- Added: concurrent access modeling (gap analysis) -->

## Appendix E: Concurrent access and conflict detection

REST services must handle concurrent requests that modify the same resource. The spec language
models this through optimistic concurrency control using a built-in `version` mechanism and explicit
conflict detection in preconditions.

### E.1 Version fields

Any entity can declare a `version` field that the runtime auto-increments on each mutation:

```text
entity Order {
  id: Int
  status: OrderStatus
  total: Float
  version: Int          // auto-incremented on each write
}
```

### E.2 Optimistic locking in operations

Operations that update existing resources can require version matching:

```text
operation UpdateOrder {
  input:  order_id: Int, total: Float, expected_version: Int
  output: order: Order

  requires:
    order_id in orders
    orders[order_id].version = expected_version   // conflict detection

  ensures:
    order.total = total
    order.version = pre(orders)[order_id].version + 1
    orders' = pre(orders) + {order_id -> order}
}
```

The convention engine maps `version = expected_version` preconditions to:

- An `If-Match` / ETag header check (HTTP layer)
- A `WHERE version = $expected` clause in the UPDATE SQL (DB layer)
- HTTP 409 Conflict response when the version does not match

### E.3 Convention engine output

| Spec Pattern                                    | Generated Artifact                                                        |
| ----------------------------------------------- | ------------------------------------------------------------------------- |
| `entity.version: Int`                           | DB column `version INT NOT NULL DEFAULT 0`, auto-increment trigger        |
| `requires: resource.version = expected_version` | `UPDATE ... WHERE version = $v` + row-count check                         |
| Version mismatch detected                       | HTTP 409 with `{ "code": "CONFLICT", "detail": "Resource was modified" }` |
| `ETag` header                                   | Generated from `"{entity_type}:{id}:{version}"`                           |

<!-- Added: external service calls as abstract effects (gap analysis) -->

## Appendix F: External service calls as abstract effects

Real services call external systems: payment gateways, email providers, SMS APIs, third-party data
sources. The spec language models these as **abstract effects**, declared interfaces with
postconditions but no implementation, similar to Haskell's IO monad or algebraic effects.

### F.1 Declaring external effects

```text
effect PaymentGateway {
  action Charge {
    input:  amount: Float, currency: String, token: String
    output: charge_id: String, success: Bool

    ensures:
      success implies len(charge_id) > 0
  }
}

effect EmailService {
  action Send {
    input:  to: String, subject: String, body: String
    output: sent: Bool
  }
}
```

### F.2 Using effects in operations

Operations declare which effects they use. The convention engine generates a dependency injection
interface; the actual implementation is provided by the developer or by a generated client stub.

```text
operation PlaceOrder {
  input:  order_id: Int, payment_token: String
  output: confirmation: OrderConfirmation
  uses:   PaymentGateway, EmailService

  requires:
    order_id in orders
    orders[order_id].status = Pending

  ensures:
    let charge = PaymentGateway.Charge(orders[order_id].total, "USD", payment_token)
    charge.success implies orders'[order_id].status = Confirmed
    not charge.success implies orders'[order_id].status = PaymentFailed
    charge.success implies EmailService.Send(customer_email, "Order Confirmed", ...)
}
```

### F.3 Convention engine mapping

| Spec Construct                  | Convention Output                                                        |
| ------------------------------- | ------------------------------------------------------------------------ |
| `effect PaymentGateway { ... }` | Abstract interface/protocol (Python ABC, Go interface, Java interface)   |
| `uses: PaymentGateway`          | Constructor dependency injection parameter                               |
| Effect `action` postconditions  | Property tests for the mock; integration test contract for the real impl |

The model checker verifies operation logic assuming effect postconditions hold. The conformance test
suite provides mock implementations that satisfy the declared postconditions. The developer supplies
production implementations.

<!-- Added: non-determinism handling (gap analysis) -->

## Appendix G: Handling non-determinism in specifications

Specifications must handle values that are non-deterministic at spec time but deterministic at
runtime: UUIDs, timestamps, random codes, auto-increment IDs. The spec language uses **existential
binding** and **abstract functions** to model these.

### G.1 Fresh value generation

When an operation produces a value that must be unique but whose exact value is unspecified, use
existential binding:

```text
operation Shorten {
  input:  url: LongURL
  output: code: ShortCode

  requires: isValidURI(url.value)

  ensures:
    code not in pre(store)             // code is fresh (existential: SOME code exists)
    store'[code] = url
    len(code.value) >= 6 and len(code.value) <= 10
}
```

The spec says "there exists a code that satisfies these constraints" without specifying which one.
The LLM synthesis engine or convention engine chooses the algorithm (hash, random, counter). The
verifier checks that any conforming implementation is correct.

### G.2 Timestamps via `now()`

The built-in function `now()` represents the current time at operation execution. The spec
constrains its relationship to other values without fixing it:

```text
ensures:
  order.created_at = now()                        // assigned at execution time
  order.created_at >= pre(orders)[last_id].created_at   // monotonic
```

For verification, `now()` is treated as a fresh value satisfying `now() >= any previous now()`. For
property tests, the test harness injects a controllable clock.

### G.3 Auto-increment IDs

```text
ensures:
  post.id = max(dom(posts)) + 1    // next sequential ID
  post.id not in pre(posts)         // always fresh
```

The convention engine maps `max(dom(...)) + 1` to `BIGSERIAL` / auto-increment in the database. The
spec states the semantic property (fresh, sequential) without naming the mechanism.
