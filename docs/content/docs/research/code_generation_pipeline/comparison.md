---
title: "How it compares"
description: "The code generator against OpenAPI Generator, JHipster, and the rest"
---

## 10. Comparison with existing generators

### 10.1 OpenAPI generator

**What it is.** Generates client SDKs and server stubs from OpenAPI specs. Supports 40+ targets.

#### What it does well

- Breadth: 40+ language targets means wide coverage.
- Community: large contributor base, many edge cases handled.
- Schema-to-model generation is solid for simple types.

#### What it does poorly

- Generated code often does not compile for less-popular targets. The Java and TypeScript targets
  are well-maintained; Go and Rust targets frequently have issues. A 2023 study found that 30% of
  generated Go clients had compilation errors.
- Server stubs are truly stubs, empty method bodies with TODO comments. No business logic, no
  database integration, no tests.
- No behavioral specification support. The input is purely structural (OpenAPI), so there is no way
  to express "this operation requires X and ensures Y."
- Template quality varies wildly between targets because different maintainers own different targets
  with no cross-target quality standard.
- Generated code style is often non-idiomatic. Go code uses Java-style getters/setters. Python code
  does not follow PEP 8 conventions.

#### What we should copy

- The template-per-target architecture. Each target has its own template set.
- The mustache template system is simple and well-understood.
- The `--additional-properties` flag for customization per target.

#### What we should avoid

- Trying to support 40+ targets. We target 3 (Python, Go, TypeScript) and make them excellent.
- Generating stubs. Our output must be complete and runnable.
- Relying on community maintainers for individual targets with no integration testing.

### 10.2 JHipster

**What it is.** Full-stack application generator. Produces Spring Boot + Angular/React/Vue from a
domain model (JDL).

#### What it does well

- Generates COMPLETE, RUNNABLE applications. `docker-compose up` works out of the box. This is the
  bar we must meet.
- JDL (JHipster Domain Language) is simple and well-documented.
- Produces production-quality code: Liquibase migrations, Spring Security config, CI/CD pipelines,
  Docker files, Kubernetes manifests.
- Extensive entity relationship support (one-to-many, many-to-many, enums, pagination).
- Built-in support for microservice architectures (gateway, service discovery, config server).

#### What it does poorly

- CRUD-only. JDL defines entities and relationships, rather than behavior. There is no way to say "this
  operation requires X and ensures Y."
- Java/Spring only on the backend (with experimental .NET support). No Python, Go, or TypeScript
  backend.
- Opinionated to the point of rigidity. If you want something JHipster does not support (e.g., a
  custom auth scheme), you fight the generator.
- Overwhelming output: a simple 2-entity JHipster project generates 200+ files, most of which are
  boilerplate the developer never reads.
- No formal verification of any kind.

#### What we should copy

- The completeness standard: generated projects include migrations, Docker files, CI config, tests,
  and documentation. Everything works.
- The JDL syntax is a good model for how a simple DSL should feel.
- The entity relationship mapping (including junction tables for many-to-many).

#### What we should avoid

- Generating 200+ files for a simple service. Our URL shortener should produce ~25 files.
- Being opinionated about frontend framework. We generate backends only.
- Locking into one backend ecosystem (Spring).

### 10.3 Smithy code generators

**What it is.** AWS's API design language. Smithy IDL defines the model; code generators produce
clients and servers.

#### What it does well

- Production-proven: every AWS SDK is generated from Smithy models.
- The trait system is elegant: `@readonly`, `@paginated`, `@httpQuery` are annotations that modify
  code generation behavior without changing the model.
- smithy-dafny exists: Smithy models can be compiled to Dafny for formal verification, then to
  target languages. This is the closest prior art to our Dafny integration pipeline.
- Clean separation between model (Smithy IDL), transform (code generators), and output (target
  code).

#### What it does poorly

- AWS-centric. Smithy's type system and traits are designed for AWS services. Concepts like
  "eventually consistent reads" and "pagination tokens" are baked in as first-class constructs.
- Steep learning curve. The Smithy model requires understanding resource lifecycle, service
  closures, and trait application order.
- No behavioral specification. Smithy is purely structural. You can say "this operation takes X and
  returns Y" but not "this operation requires that X is in the database and ensures that Y is
  added."
- Server code generation is still limited (mainly Java/Kotlin via smithy4s).

#### What we should copy

- The trait system for extensible metadata.
- The smithy-dafny pipeline architecture for verified code generation.
- The clean model-transform-output separation.

#### What we should avoid

- AWS-specific abstractions. Our DSL is domain-agnostic.
- The complexity of the Smithy gradle build system.

### 10.4 Grpc-gateway

**What it is.** Generates a REST reverse proxy in Go from protobuf service definitions with HTTP
annotations.

#### What it does well

- Battle-tested at massive scale (millions of requests per day at many companies).
- Clean mapping from RPC to REST via annotations.
- Generates OpenAPI from the same protobuf source.
- The generated code is idiomatic Go.

#### What it does poorly

- Go-only server generation.
- Protobuf is verbose for simple REST APIs.
- No database integration. The generated code is a proxy; you still write the backend.
- No behavioral contracts beyond input/output types.

#### What we should copy

- The annotation-based convention override system (`option (google.api.http) = {...}`).
- The OpenAPI co-generation approach: produce the API spec alongside the server code.

#### What we should avoid

- Requiring protobuf as input. Our DSL should be simpler than protobuf.
- Being proxy-only. We generate the complete service, rather than a proxy.

### 10.5 Prisma

**What it is.** ORM and schema management tool for TypeScript/JavaScript (with growing Rust and
Python support). Schema-first: define models in `schema.prisma`, generate type-safe client code.

#### What it does well

- The `schema.prisma` DSL is one of the best-designed data modeling languages. Simple, readable,
  powerful.
- Migration generation is excellent: `prisma migrate dev` detects schema changes and generates SQL
  migrations automatically.
- Type-safe client: queries are statically typed, preventing runtime errors.
- Introspection: can reverse-engineer an existing database into a schema.

#### What it does poorly

- Data modeling only. No HTTP routes, no business logic, no tests.
- TypeScript/JavaScript only (Rust client is new, Python is community-maintained).
- No behavioral contracts. You cannot express preconditions or postconditions.
- The query engine is a Rust binary, adding deployment complexity.

#### What we should copy

- The migration generation approach: diff the old and new schemas, generate SQL.
- The schema.prisma DSL design: simple, declarative, readable.
- The introspection capability (future feature: import existing DB into spec).

#### What we should avoid

- Shipping a separate query engine binary. Our generated code uses the target's native ORM/database
  driver.

### 10.6 Sqlalchemy alembic

**What it is.** Database migration tool for Python. Auto-generates migrations by comparing
SQLAlchemy model metadata to the current database state.

#### What it does well

- Auto-detection of schema changes: add a column to a model, `alembic revision --autogenerate`
  produces the migration.
- Supports complex migrations: data migrations, multi-step changes, custom SQL.
- The migration chain (revision graph) handles branching and merging.
- Mature and battle-tested (used by most Python ORMs in production).

#### What it does poorly

- Python-only.
- Auto-generation misses some changes (renamed columns appear as drop+add).
- No support for behavioral constraints (CHECK constraints must be added manually).
- No cross-language migration generation.

#### What we should copy

- The auto-generation approach: compare model state to database state, generate diff.
- The revision chain for migration ordering.
- The `upgrade()`/`downgrade()` pattern.

#### What we should avoid

- Depending on a running database for migration generation. Our compiler generates migrations from
  the spec diff alone, without needing a database connection.

### 10.7 Summary comparison matrix

| Capability               | OpenAPI Gen        | JHipster | Smithy                 | gRPC-GW    | Prisma   | Alembic         | **Ours**             |
| ------------------------ | ------------------ | -------- | ---------------------- | ---------- | -------- | --------------- | -------------------- |
| Behavioral spec input    | No                 | No       | No                     | No         | No       | No              | **Yes**              |
| Complete runnable output | No (stubs)         | **Yes**  | Partial                | No (proxy) | No (ORM) | No (migrations) | **Yes**              |
| Multi-language targets   | 40+ (poor quality) | 1 (Java) | 7+ (clients)           | 1 (Go)     | 1 (TS)   | 1 (Python)      | **3 (high quality)** |
| Database migrations      | No                 | **Yes**  | No                     | No         | **Yes**  | **Yes**         | **Yes**              |
| Generated tests          | No                 | Partial  | No                     | No         | No       | No              | **Yes (3 tiers)**    |
| Formal verification      | No                 | No       | Partial (smithy-dafny) | No         | No       | No              | **Yes (Dafny)**      |
| OpenAPI co-generation    | Input, rather than output  | **Yes**  | **Yes**                | **Yes**    | No       | No              | **Yes**              |
| Docker/infra generation  | No                 | **Yes**  | No                     | No         | No       | No              | **Yes**              |
| Incremental regeneration | No                 | Partial  | No                     | No         | **Yes**  | **Yes**         | **Yes**              |

The key differentiator is that no existing tool combines behavioral specification with complete code
generation. JHipster comes closest on the "complete output" axis; Smithy-Dafny comes closest on the
"verified output" axis. Our compiler merges both.
