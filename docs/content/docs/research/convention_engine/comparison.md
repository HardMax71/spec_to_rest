---
title: "How it compares"
description: "The convention engine against Rails, JHipster, Django REST, Smithy, and OpenAPI generators"
---

## Ruby on Rails

Rails is the origin of convention over configuration: an `Order` model maps to an `orders` table,
`has_many :line_items` builds the foreign-key association, and `resources :orders` emits the standard
REST routes. Its pluralization and naming conventions are settled, and we adopt them directly; its
member routes for custom actions, a `post :place` on a resource, are the same shape as our transition
endpoints. The difference is where the routes come from and how far the guarantees reach. Rails has
no pre- or postconditions, and its validations are structural and application-only. We read the
method off mutation analysis rather than an explicit `resources` declaration, generate the schema and
the application code from one spec rather than migrations hand-written beside the models, and turn an
invariant into a `CHECK` constraint, an application check, and a test at once.

## JHipster JDL

JHipster generates a whole Spring stack from a JDL definition, the entities, repositories, services,
controllers, and a UI, with relationships becoming JPA annotations and foreign-key or junction
tables, and pagination and filtering for free. It proves that a full stack can come from a structural
spec, and its relationship types line up with our multiplicities. But JDL is structural only, with no
preconditions, postconditions, or invariants, so every generated endpoint accepts any well-shaped
request, and anything past CRUD (a state machine, a transfer) is hand-coded. We generate from a spec
that is model-checked before any code is emitted, and the conformance tests come from the spec rather
than as empty stubs.

## Django REST framework

DRF layers a model into a serializer, a viewset, and a router, with composable permissions,
throttling, filtering, and pagination, plus an `@action` decorator for endpoints outside the CRUD
set. The layering is clean, and `@action` matches our transition routes. The order is reversed,
though: DRF starts from a Django model you write, while we generate the model from the spec, and DRF's
validation stops at field types where ours comes from invariants and preconditions. Its viewsets are
CRUD-centric; the convention engine handles arbitrary operations, state machines and multi-entity
mutations included.

## Smithy traits

Smithy annotates shapes with behavioral traits (`@http`, `@auth`, `@paginated`, `@idempotent`), stays
protocol-agnostic across REST, gRPC, and MQTT, and backs every AWS SDK. Its trait system is the
closest model for extensible metadata, and the `conventions` block borrows from it. The split is that
Smithy describes what an API looks like and asks for every trait by hand, while we describe what an
operation does and infer most of the mapping, a read versus a mutation among them, from the spec.
Smithy generates no database schema and no validation logic, and it checks structural correctness
rather than behavioral.

## OpenAPI generators

The OpenAPI generators take a YAML or JSON spec and emit client SDKs and server stubs in dozens of
languages from a mature ecosystem. Two lessons carry over: OpenAPI is the common format for REST
description, so we emit it as one artifact, and the quality of generated server stubs varies enough
that production code should not lean on a third-party generator. The deeper difference is direction.
OpenAPI describes the HTTP surface with no notion of state, operations, or invariants, and it sits
downstream of our spec, not upstream. We generate it, along with the implementation and the tests
that check the implementation against the spec.

## Summary

| Feature                  | Rails                  | JHipster         | DRF               | Smithy           | OpenAPI-gen            | Our engine                  |
| ------------------------ | ---------------------- | ---------------- | ----------------- | ---------------- | ---------------------- | --------------------------- |
| Derives routes from spec | no, explicit           | no, explicit     | no, explicit      | partial, traits  | no, input is routes    | yes, from mutation analysis |
| Generates DB schema      | no, manual migrations  | yes, from JDL    | no, manual models | no               | no                     | yes, from the state model   |
| Behavioral validation    | no                     | no               | no                | no               | no                     | yes, from requires/ensures  |
| Formal verification      | no                     | no               | no                | structural only  | structural only        | yes, model checking         |
| Test generation          | basic                  | basic            | basic             | contract tests   | no                     | property-based from spec    |
| Custom operations        | manual                 | manual           | `@action`         | custom shapes    | manual                 | from spec operations        |
| State machines           | manual                 | manual           | manual            | not modeled      | not modeled            | from the transition block   |
| Multi-target             | Ruby only              | Java/Spring only | Python only       | 7+ languages     | 40+, variable quality  | 3 frameworks, 3 databases   |
