---
title: "Examples"
description: "Four complete specs: URL shortener, todo list, authentication, and e-commerce"
---

Four complete specs, each pulled from `fixtures/spec/` at build time, so the page shows what the
compiler actually runs rather than a copy that drifts from it.

## URL shortener

The running example: it maps short codes to URLs, keeps a little metadata per code, and overrides a
few conventions so the routes read cleanly.

```spec file="fixtures/spec/url_shortener.spec"
```

Without the `conventions` block, the engine infers the REST surface from each operation's shape:

| Operation | Inferred method                 | Inferred path          | Inferred status |
| --------- | ------------------------------- | ---------------------- | --------------- |
| Shorten   | POST (mutates state, has input) | `/url-mappings`        | 201             |
| Resolve   | GET (read-oriented, bumps click_count)  | `/url-mappings/{code}` | 200             |
| Delete    | DELETE (removes from state)     | `/url-mappings/{code}` | 204             |
| ListAll   | GET (reads a collection)        | `/url-mappings`        | 200             |

The `conventions` block in the spec overrides those defaults for a cleaner API.

## Todo list

A fuller CRUD service, with an enum-valued status and invariants that tie the fields together.

```spec file="fixtures/spec/todo_list.spec"
```

## Authentication service

Login and sessions, with a rate limit on failed attempts modeled as a `failed_logins` relation, and
a `security` block plus `requires_auth` marking the protected operations.

```spec file="fixtures/spec/auth_service.spec"
```

## E-commerce order service

The largest of the four: orders, line items, payments, and returns, with cross-entity invariants and
conditions quantified over collections.

```spec file="fixtures/spec/ecommerce.spec"
```
