---
title: "Override system"
description: "The conventions block: the property surface, qualifiers, validation, and what stays fixed"
---

Every default the engine picks can be redirected, but only from one place: the service-wide
`conventions` block. There is no per-operation `with` clause. Each rule has the shape
`Target.property [qualifier] = value`, where the target is an operation, an entity, a type alias, or
an enum.

```spec
conventions {
  Shorten.http_method         = "POST"
  Shorten.http_path           = "/shorten"
  Shorten.http_status_success = 201

  Resolve.http_method            = "GET"
  Resolve.http_path              = "/{code}"
  Resolve.http_status_success    = 302
  Resolve.http_header "Location" = output.url

  UrlMapping.db_table      = "url_mappings"
  UrlMapping.db_timestamps = true
  UrlMapping.plural        = "url_mappings"

  ShortCode.strategy          = "tests.strategies_user:short_code"
  User.test_strategy.password = "tests.strategies_user:strong_password"
}
```

## What you can set

On an operation: `http_method`, `http_path`, `http_status_success`, and `http_header "Name"`, where
the header name is the qualifier and the value is an output field. On an entity: `db_table`,
`db_timestamps`, and `plural`. The last two control test-data generation: `strategy` names a
generator for a type alias or enum, and `test_strategy.field` names one per entity field, with the
field as the qualifier. The full property and target matrix is in
[the spec-language reference](/spec-language#convention-overrides).

The set is deliberately small. It covers presentation, the method, path, status, headers, table and
plural names, and the values a test harness invents, and it does not reach the parts of the output
that carry the spec's guarantees.

## Validation

`Validate.scala` checks each rule before anything is generated and reports a `ConventionDiagnostic`
for a target that names no operation, entity, alias, or enum; an unknown property; a property that
does not belong on its target, such as a `db_table` on an operation; a property that needs a
qualifier and has none, such as `http_header` without a name; or a duplicate rule for the same
target, property, and qualifier. A present override wins over the engine and profile default, and
that is the whole of the precedence, since duplicates are rejected rather than ordered.

## What stays fixed

Because the block exposes only presentation, no property removes a check or a constraint. The
validation a `requires` clause compiles to, the `CHECK` or `UNIQUE` a field invariant compiles to, a
table's surrogate primary key, and the foreign keys derived from state relations all stand, since
dropping any of them would let the generated service diverge from the spec it was proven against. You
can rename a column, change a status code, or rewrite an error message; you cannot remove the check
underneath it.
