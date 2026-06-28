---
title: "Override system"
description: "The conventions block: override syntax, categories, resolution order, and what cannot be overridden"
---

### 3.1 Override syntax

Overrides live in the `conventions` block of the spec file. Every convention decision is addressable
by a dotted path:

```text
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

### 3.2 Override categories

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
| **Error Messages** | `{Op}.requires_{n}_error_message`                          |   |
| **Error Codes**    | `{Op}.requires_{n}_error_code`                             |   |
| **Soft Delete**    | `{Op}.http_soft_delete`, `global.http_soft_delete`         | `global.http_soft_delete = true`              |

### 3.3 Override resolution order

When multiple overrides could apply, they are resolved in this order (most specific wins):

1. **Operation-level override** (e.g., `Shorten.http_method = "PUT"`)
2. **Entity-level override** (e.g., `ShortCode.db_table = "codes"`)
3. **Global override** (e.g., `global.http_auth = "bearer"`)
4. **Profile default** (e.g., python-fastapi profile uses snake_case)
5. **Engine default** (the rules in Section 2)

### 3.4 Override conflicts

When overrides conflict with each other:

| Conflict                                                               | Resolution                                                                |
| ---------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Operation override conflicts with global override                      | Operation wins                                                            |
| Two operation overrides for the same property                          | Compilation error: "Duplicate override for {path}"                        |
| Override contradicts spec semantics (e.g., setting GET for a mutation) | Warning: "Override {path} may violate REST semantics: GET should be safe" |
| Override sets an invalid value (e.g., `Op.http_method = "INVALID"`)    | Compilation error: "Invalid HTTP method: INVALID"                         |

### 3.5 What cannot be overridden

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
