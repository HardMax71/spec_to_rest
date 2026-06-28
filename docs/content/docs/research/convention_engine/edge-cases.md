---
title: "Edge cases"
description: "Read-and-write operations, multiple inputs, idempotency, pagination, and other ambiguous cases"
---

### 5.1 Operations that both read AND write

**Example:** "Increment counter and return new value"

```text
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

**Resolution.** This mutates state (`view_count'` differs from `view_count`) AND returns data. The
engine maps this to **POST** because the operation is not safe (it has side effects). GET would
violate RFC 7231's safety requirement.

**Rule.** If an operation mutates ANY state relation, it is not a GET, regardless of whether it also
returns data. The method is determined by the mutation rules (M1-M6, M8-M10).

### 5.2 Operations with multiple entity inputs

**Example:** "Transfer between accounts"

```text
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

**Resolution.** This mutates existing entities but doesn't clearly belong to one resource. The
engine applies Rule M8 (side-effect POST) and creates a top-level action endpoint:

- `POST /transfers` with body `{"from_id": 1, "to_id": 2, "amount": 100.00}`

The entity to use for the path is the _operation name itself_, treated as a verb-noun (Transfer ->
`transfers`). Neither `from_id` nor `to_id` becomes a path parameter because neither uniquely
identifies the target resource.

**Rule.** When an operation takes multiple entity IDs as input and mutates both, the operation name
becomes the resource name and all IDs go in the request body.

### 5.3 Operations that create child entities

**Example:** "Add item to order"

As shown in Example 4.2, the engine detects parent-child relationships from the state model and
routes child creation to the parent's sub-collection:

- `POST /orders/{order_id}/line-items` (NOT `POST /line-items`)

**Rule.** If entity B is a child of entity A (detected from `r: AId -> set B` in the state model),
then creating B routes to `POST /{A_plural}/{a_id}/{B_plural}`.

**Exception.** If B also exists independently (has its own top-level read operations not scoped to a
parent), the engine generates BOTH:

- `POST /orders/{order_id}/line-items` (create under parent)
- `GET /line-items/{id}` (direct access by ID)

### 5.4 Idempotency

| HTTP Method   | Idempotent?         | Engine Behavior                                                                                   |
| ------------- | ------------------- | ------------------------------------------------------------------------------------------------- |
| GET           | Yes (by definition) | No special handling                                                                               |
| PUT           | Yes (by definition) | No special handling, full replacement is inherently idempotent                                  |
| DELETE        | Yes (by definition) | Return 204 even if already deleted (do not return 404 on re-delete)                               |
| PATCH         | Not necessarily     | No special handling                                                                               |
| POST (create) | Not idempotent      | Engine generates an `Idempotency-Key` header option: clients can send a UUID, server deduplicates |
| POST (action) | Not idempotent      | Same `Idempotency-Key` mechanism                                                                  |

The `Idempotency-Key` mechanism is opt-in. The engine generates the infrastructure (a table to store
idempotency keys and their responses) but only activates it when the conventions block enables it:

```text
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

### 5.5 Optional vs required inputs

| Input Declaration                 | Treatment                       |
| --------------------------------- | ------------------------------- |
| `input: name: String`             | Required (422 if absent)        |
| `input: name?: String`            | Optional (null/absent is valid) |
| `input: name: String = "default"` | Optional with default value     |

For GET operations, optional inputs become optional query parameters. For POST/PUT/PATCH, they
become optional body fields (absent or null).

### 5.6 Collection returns: Pagination, filtering, sorting

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

### 5.7 File uploads and binary data

If an entity has a `Bytes` field or the spec mentions file/binary data:

```text
entity Attachment {
  filename: String
  content_type: String
  data: Bytes
}
```

The engine maps creation to a multipart/form-data endpoint:

```text
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

### 5.8 Async operations and long-running tasks

If an operation's ensures clause references eventual consistency or the operation is annotated as
async:

```text
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

```text
conventions {
  PlaceOrder.http_webhook = "order.placed"
}
```

The engine generates:

1. A webhook registration endpoint: `POST /webhooks` with
   `{"url": "...", "events": ["order.placed"]}`
2. A webhook delivery table in the DB
3. Async delivery logic that POSTs to registered URLs after the operation completes
