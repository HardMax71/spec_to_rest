---
title: "Overview"
description: "What a generated project contains, its infrastructure, and its observability"
---

A `compile` run does not emit a single file; it emits a whole project, ready to run, test, and
deploy. The exact files differ by target, and the per-target references under
[deployment targets](/targets) are the authoritative listing. This page is the shape they share and
the infrastructure they carry.

## What a project contains

The application is laid out in the target's idiomatic style. For the FastAPI target that means a
layered `app/`: configuration and database setup, the ORM models, the request and response schemas, a
service layer, the routers (including an admin surface), and the cross-cutting pieces, security,
pagination, and log redaction. Alongside it sit the Alembic migrations with a first revision, the
`openapi.yaml`, and a conformance test suite. The Go and TypeScript targets mirror the same
separation in their own idioms.

Around the application is everything needed to run it: a Dockerfile, a `docker-compose.yml` with
staging and production overrides, a generated CI workflow, a Makefile, an `.env.example`, the package
manifest, a README, and a `.spec-snapshot.json` that the next regeneration diffs against (see
[regeneration](/research/code_generation_pipeline/regeneration)).

## Infrastructure

The Dockerfile is multi-stage: a builder stage installs dependencies and builds, then a slim runtime
stage copies only what runs and drops to a non-root user. It carries a `HEALTHCHECK` that hits the
generated `GET /health` route. The `docker-compose.yml` wires the application to a PostgreSQL
container with a health check, a one-shot migration runner, and a persistent volume, and
`docker-compose.staging.yml` and `docker-compose.prod.yml` layer environment-specific overrides on
top.

A `.github/workflows/ci.yml` ships with the project. It stands up a Postgres service, lints,
type-checks, runs the migrations, runs the test suite, runs the conformance suite against the live
service, and builds and smoke-tests the Docker image. The Makefile gives every target the same verbs,
`run`, `test`, `lint`, `build`, `migrate`, `docker-up`, and `docker-down`, so moving between a Python,
Go, or TypeScript project does not change the commands. The `.env.example` documents each variable the
service reads, with safe defaults.

## Observability

The observability that ships is logging, not tracing. Each service logs in a structured form, and
fields the spec marks sensitive, or whose names match patterns like `password` or `token`, are
redacted before a line is written; the generated `tests/test_log_redaction.py` checks that they stay
out of the logs. The `GET /health` route is the liveness signal the Dockerfile and an orchestrator
probe. The pipeline does not generate distributed tracing, a metrics endpoint, or a separate
readiness probe; those are left to the operator.
