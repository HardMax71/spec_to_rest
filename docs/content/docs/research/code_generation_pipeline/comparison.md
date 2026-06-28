---
title: "How it compares"
description: "The code generator against OpenAPI Generator, JHipster, Smithy, gRPC-gateway, Prisma, and Alembic"
---

No existing generator takes a behavioral spec and emits a complete, verified service, but several
solve pieces of the problem, and the design borrows from each.

## OpenAPI Generator

OpenAPI Generator covers more than forty targets from an OpenAPI document, with a deep community and
solid schema-to-model generation. Its limits are the ones this project set out to avoid. Its server
output is genuinely stubs, empty bodies with TODO comments, because OpenAPI is structural: there is
no way to say an operation requires X and ensures Y. Quality varies by target: the generator's own
tracker carries a long tail of Go cases where `oneOf` and `anyOf` union types
[emit code that does not compile](https://github.com/OpenAPITools/openapi-generator/issues/11851),
since each target has a different maintainer and no shared standard. What carries over is the template-per-target architecture and the mustache
engine; what does not is the breadth-over-depth bet. This project keeps three targets compiling,
tested, and idiomatic rather than forty that might not.

## JHipster

JHipster is the bar for completeness: a JDL domain model becomes a Spring stack that runs under
`docker-compose up`, with migrations, security config, CI, and Docker all generated. But JDL is
structural, CRUD over entities and relationships with no behavior, the backend is Java and Spring
only, and the output is voluminous enough (hundreds of files for a two-entity project) that most of
it goes unread. The completeness standard is what this project matches; the rigidity, the single
backend, and the file sprawl are what it leaves, the URL shortener comes out as a few dozen files on
any of three backends.

## Smithy

Smithy is the closest prior art on the verification axis. Every AWS SDK is generated from a Smithy
model, its trait system (`@readonly`, `@paginated`, and the like) is a clean way to attach metadata,
and smithy-dafny already compiles Smithy models to Dafny for verification before emitting target
code, the same shape as this pipeline's [Dafny path](/research/llm_verifier_synthesis/dafny). Its
model is AWS-centric and steep, though, and it is still structural: a trait cannot say an operation
requires a row to exist and ensures another is added. The trait idea and the verify-through-Dafny
architecture carry over; the AWS abstractions do not.

## gRPC-gateway, Prisma, and Alembic

Three narrower tools each contribute one idea. gRPC-gateway maps RPC to REST from protobuf
annotations and co-generates OpenAPI, but it is a Go-only proxy with no database or behavior, and the
annotation-as-override and the co-generated spec are what carry over. Prisma's `schema.prisma` is one
of the best data-modeling DSLs, and its migrate command diffs schema changes into SQL automatically,
but it stops at data, TypeScript only, no routes or logic, and the schema-diff-to-migration approach
is the model for this project's migrations. Alembic auto-generates Python migrations by comparing
model metadata to the live database, with a revision chain and an up/down pattern worth copying,
except this compiler diffs the spec against its own snapshot and so needs no database connection to
produce one.

## The differentiator

| Capability              | OpenAPI Gen     | JHipster | Smithy                | gRPC-GW   | Prisma   | Alembic | Ours                  |
| ----------------------- | --------------- | -------- | --------------------- | --------- | -------- | ------- | --------------------- |
| Behavioral spec input   | no              | no       | no                    | no        | no       | no      | yes                   |
| Complete service output | no, stubs       | yes      | partial               | no, proxy | no, ORM  | no      | structure; logic synthesized |
| Targets                 | 40+, variable   | 1, Java  | 7+ clients            | 1, Go     | 1, TS    | 1, Python | 3, maintained       |
| Migrations              | no              | yes      | no                    | no        | yes      | yes     | yes                   |
| Generated tests         | no              | partial  | no                    | no        | no       | no      | yes                   |
| Formal verification     | no              | no       | partial, smithy-dafny | no        | no       | no      | yes, Dafny            |
| Docker and CI           | no              | yes      | no                    | no        | no       | no      | yes                   |

No tool combines a behavioral spec with complete generation. JHipster comes closest on completeness,
smithy-dafny on verification, and this project's wager is that both belong in one tool. The one
honest asterisk is the operation bodies: the structure, the routes, schema, validation, tests, and
infrastructure, is always emitted, but the logic inside each operation is verified only when
synthesis runs, and otherwise ships as a fail-loud stub.
