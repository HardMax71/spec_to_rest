---
title: "Regeneration and extensibility"
description: "How a project survives re-compilation, and how to add a target"
---

Running `compile` again overwrites the spec-derived files; the generated README says so plainly, do
not edit them, because the next run will. What survives is the one file the compiler never touches.

## The extension point

Each generated project carries an `app/extensions/` module with a single `register` function that
`compile` writes once as a stub
([`ExtensionStub`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/codegen/src/main/scala/specrest/codegen/ExtensionStub.scala))
and never overwrites again:

```python
from fastapi import FastAPI


def register(app: FastAPI) -> None:
    """Register custom routes, middleware, and lifecycle hooks.

    This file is never overwritten by `spec-to-rest compile`. The generated
    `app/main.py` calls this once BEFORE mounting any spec-derived router, so
    middleware added here wraps every generated endpoint and routes declared
    here take precedence on path collisions.
    """
    del app
```

The generated `main.py` calls `register(app)` before it mounts the spec's routers, and that ordering
is what gives the hook its two powers: middleware added there wraps every generated endpoint, and a
route declared there wins a path collision against a generated one. That is the whole extension
surface, one function, called at a defined point. The Go and TypeScript targets ship the same shape,
a `register` in their own `extensions` package. There are no protected-region markers in the
generated files and no separate overrides mechanism; custom code lives in the extension file or not
at all.

## Migrations are the incremental part

The application code is regenerated wholesale, but the database is not. A schema change does not
rewrite the first migration; it accrues as a new delta, computed by diffing the spec against the
`.spec-snapshot.json` the previous run left behind. That snapshot-driven delta generation, reversible
up and down across Alembic, golang-migrate, and Prisma, is the [migrations](/pipelines/migrations)
pipeline's job, not the emitter's.

## Adding a target

A target is a
[`DeploymentProfile`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/profile/src/main/scala/specrest/profile/Registry.scala)
entry plus a Handlebars template tree. The profile names the language and framework and carries the
type and dependency mappings, the templates live under `templates/<language>/<framework>/`, and
[`Emit`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/codegen/src/main/scala/specrest/codegen/Emit.scala)
dispatches on the profile. The Go/chi and TypeScript/Express targets are exactly this: a profile
registered in `Registry` and a tree of `.hbs` files. Adding a Kotlin/Spring target, say, would mean
writing its profile and templates and one registry line:

```scala
object KotlinSpring:
  val profile = DeploymentProfile(
    name = "kotlin-spring",
    language = "kotlin",
    framework = "spring",
    // type map, dependencies, and the rest
  )
```

There is no plugin-discovery step and no runtime loading. A target is code in the tree, registered at
compile time, which is why a missing template is a build error rather than a deploy-time one.
