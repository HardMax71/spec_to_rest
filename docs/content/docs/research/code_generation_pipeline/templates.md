---
title: "Template architecture"
description: "How the Handlebars templates and the per-target Scala turn the IR into a project"
---

The emitter is Handlebars, not a Python template engine, because codegen lives on the JVM beside the
parser and the verifier. jknack's Handlebars-Java runs in-process with no sidecar:
[`Engine`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/codegen/src/main/scala/specrest/codegen/Engine.scala)
compiles a `.hbs` source inline and applies it to a context object, with a couple of custom helpers
(`snake_case`, `join`) registered next to the library's string helpers.

## The template tree

Templates sit under `modules/codegen/src/main/resources/templates/<language>/<framework>/`, one tree per target, each file a `.hbs` named after the output it renders:

```text
templates/
├── python/fastapi/   main.py, config.py, database.py, app/{redaction,pagination}.py,
│                     schemas/entity.py, routers/entity.py, services/entity.py
├── go/chi/           cmd/, internal/{models,handlers,auth}, migrations/
└── ts/express/       src/{index,app,config}.ts, src/{routes,services,schemas}/entity.ts,
                      src/middleware/{error,validate,auth}.ts
```

There is no JSON manifest. Each target's file list is Scala:
[`TsTemplates`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/codegen/src/main/scala/specrest/codegen/TsTemplates.scala)
and its Go and Python siblings load every `.hbs` by name into a typed record, so a missing or renamed
template is a compile error, not a runtime surprise. The `entity`-named templates (`routers/entity`,
`services/entity`, `schemas/entity`) render once per entity; the rest render once per service.

## What a template renders against

The context is the IR with the convention engine's decisions folded in: every operation arrives
carrying its endpoint (method, path, status), and an `OperationContext` precomputes what each file
needs, the imports, the operations grouped by entity, the auth dependencies, so a template reads them
directly rather than recomputing. The head of the FastAPI router template:

```handlebars
{{#each routerImports.stdlibImports}}
from {{this.module}} import {{join this.names ", "}}
{{/each}}
from fastapi import APIRouter{{#if routerImports.needsHttpException}}, HTTPException{{/if}}
{{#if routerImports.authDeps.length}}
from app.security import {{join routerImports.authDeps ", "}}
{{/if}}
router = APIRouter(tags=["{{snake_case entity.entityName}}"])
```

Those decisions come from the convention engine once and feed every target, so a new convention rule
changes one place and all three targets inherit it.

## The synthesized kernel

When an operation is synthesized, its verified Dafny body is compiled and dropped in as a kernel
rather than inlined into a template.
[`DafnyKernel`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/codegen/src/main/scala/specrest/codegen/DafnyKernel.scala)
places the translated module, and the templates emit a thin adapter (`dafnyKernel/adapter`, plus a
copy script on the TypeScript target) that converts between the kernel's types and the service's. How
the body gets verified and compiled is the
[synthesis subsection](/research/llm_verifier_synthesis/cegis-loop); here it is just another file the
service imports.

## Keeping templates honest

Three gates catch template rot. Golden tests render each target for the canonical fixtures and diff
against checked-in output, so any change that alters a byte gets reviewed. A compilation gate runs the
generated project through the target's own tools, `ruff` and `mypy`, `go build` and `go vet`, `tsc
--noEmit`. And the conformance suite runs the generated service against its spec. A template that
emits code which does not lint, compile, or conform fails CI.
