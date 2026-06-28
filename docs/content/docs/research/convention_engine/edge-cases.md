---
title: "Edge cases"
description: "How the classifier and conventions handle reads-with-writes, multi-entity inputs, child resources, optional inputs, and collections"
---

The M-rules and the conventions block cover most operations cleanly. A few shapes need a closer look.

## Operations that read and write

An operation can mutate state and still return data, like recording a vote and handing back the new
total:

```spec
operation Vote {
  input:  post_id: PostId
  output: total: Int
  requires: post_id in posts
  ensures:
    posts'[post_id].votes = posts[post_id].votes + 1
    total = posts'[post_id].votes
}
```

The method comes from the mutation, never the read. If an operation changes any state relation it is
not a `GET`, since `GET` must stay safe under RFC 7231. `Vote` updates one field of an existing post,
so the field-coverage rule makes it a `PATCH`, and the total it returns rides in the response body.

## Operations over several entities

Some operations touch more than one entity and belong to no single resource. A transfer between two
accounts is the stock example:

```spec
operation Transfer {
  input: from_id: AccountId, to_id: AccountId, amount: Decimal
  requires:
    from_id in accounts
    to_id in accounts
    from_id != to_id
    accounts[from_id].balance >= amount
  ensures:
    accounts'[from_id].balance = accounts[from_id].balance - amount
    accounts'[to_id].balance   = accounts[to_id].balance + amount
}
```

Neither id identifies a single target, so the classifier falls to M8, the catch-all mutation: the
path is the operation name in kebab-case (`POST /transfer`), and both ids travel in the request body.

## Child resources

A line item lives under an order, and the natural endpoint is `POST /orders/{order_id}/items`. The
engine does not infer that nesting on its own. M6, the reserved rule for child creation, is not
emitted by the classifier, so a parent-scoped path is an explicit override:

```spec
conventions {
  AddLineItem.http_method = "POST"
  AddLineItem.http_path   = "/orders/{order_id}/items"
}
```

That is what `ecommerce.spec` does for its line-item and transition routes. Until M6 ships, the
nesting is a decision the spec author makes, not one the classifier reaches.

## Optional inputs

Optionality comes from the type, not a marker on the parameter. `CreateTodo` takes a required title
and an optional description:

```spec
operation CreateTodo {
  input: title: String, description: Option[String]
}
```

`title` is required, and a missing value is a 422 at the request edge; `description` may be absent or
null. On a `GET` an optional input is an optional query parameter, and on a body method it is an
optional field.

## Collection returns

When an operation returns a set or a list, like `ListAll` over the URL store, pagination is injected
rather than left to the spec:

```spec
operation ListAll {
  output: entries: Set[UrlMapping]
  ensures: entries = { m in metadata | true }
}
```

The generated handler for `GET /urls` takes `page` and `limit` query parameters with the engine's
defaults, and every target carries a pagination module of its own. An input field whose name matches
an entity field is exposed as an exact-match filter. Sorting is not inferred.

## What the engine does not do

The target is synchronous, CRUD-shaped services, so a few patterns are out of scope today. There is
no idempotency-key handling, no multipart or file-upload endpoint for `Bytes` fields (the column is
generated, the upload route is not), no asynchronous `202`-and-poll flow, and no webhook registration
or delivery. Each would need a new convention property and the codegen behind it, and none is built.
