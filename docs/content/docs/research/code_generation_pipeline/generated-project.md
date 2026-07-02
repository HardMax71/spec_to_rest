---
title: "Generated project"
description: "What a compile run emits: the project layout, its infrastructure, and its observability"
---

A `compile` run does not emit a single file; it emits a whole project, ready to run, test, and
deploy. The exact files differ by target, and the per-target references under
[deployment targets](/targets/python/fastapi/postgres) are the authoritative listing. This page is the shape they share and
the infrastructure they carry.

## What a project contains

The application is laid out in the target's idiomatic style. The FastAPI target's URL shortener comes
out like this:

```text
url_shortener/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── database.py
│   ├── db/base.py
│   ├── models/url_mapping.py
│   ├── schemas/url_mapping.py
│   ├── services/url_mapping.py
│   ├── routers/
│   │   ├── url_mappings.py
│   │   └── admin.py
│   ├── security.py
│   ├── pagination.py
│   ├── redaction.py
│   └── extensions/
├── alembic/
│   ├── env.py
│   └── versions/001_initial_schema.py
├── tests/
│   ├── test_health.py
│   └── test_log_redaction.py
├── openapi.yaml
├── Dockerfile
├── docker-compose.yml
├── docker-compose.staging.yml
├── docker-compose.prod.yml
├── .github/workflows/ci.yml
├── Makefile
├── .env.example
├── pyproject.toml
└── .spec-snapshot.json
```

The layering is idiomatic for the target: configuration and database setup, the SQLAlchemy models,
the Pydantic schemas, a service layer (where a synthesized or stubbed body lands), and the routers,
including a bearer-guarded admin surface. `redaction.py` and `security.py` are the cross-cutting
pieces. `extensions/` holds hand-written code, and `.spec-snapshot.json` records the last generation,
so the next one can regenerate without clobbering either (see
[regeneration](/research/code_generation_pipeline/regeneration)). The Go and TypeScript targets
mirror the same separation in their own idioms.

## Infrastructure

The Dockerfile is multi-stage: a builder stage installs dependencies and builds, then a slim runtime
stage copies only what runs and drops to a non-root user. It carries a `HEALTHCHECK` against the
generated `GET /health` route. The `docker-compose.yml` wires the application to a PostgreSQL
container with a health check, a one-shot migration runner, and a persistent volume, and the
`.staging` and `.prod` variants layer environment-specific overrides on top.

A `.github/workflows/ci.yml` ships with the project: it stands up Postgres, lints, type-checks, runs
the migrations, runs the test suite, runs the conformance suite against the live service, and builds
and smoke-tests the image. The Makefile gives every target the same verbs, `run`, `test`, `lint`,
`build`, `migrate`, `docker-up`, and `docker-down`, so moving between a Python, Go, or TypeScript
project does not change the commands. The `.env.example` documents each variable the service reads,
with safe defaults.

## Observability

The observability that ships is logging, not tracing. Each service logs through structlog, and a
redaction processor replaces any field the spec marks sensitive, or whose name matches a pattern like
`password`, `api_key`, or `session_token`, with `***REDACTED***` before the line is written:

```json
{ "event": "login_failed", "email": "a@b.com", "password": "***REDACTED***" }
```

`email` is not sensitive and passes through; `password` matches and is redacted. The generated
`tests/test_log_redaction.py` locks that in. Three infrastructure routes ship alongside the spec
routes: `GET /health` is the liveness signal the Dockerfile probes, `GET /ready` reports readiness
by round-tripping a `SELECT 1` through the database (200 or 503), and `GET /metrics` exposes
Prometheus text format with `http_requests_total` and `http_request_duration_seconds`, labelled by
method, route template, and status code. The pipeline does not generate distributed tracing; that
is left to the operator.
