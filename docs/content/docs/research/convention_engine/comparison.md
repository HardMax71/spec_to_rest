---
title: "How it compares"
description: "The convention engine against Rails, JHipster, Django REST, Smithy, and OpenAPI generators"
---

### 7.1 Ruby on Rails (convention over configuration)

#### What Rails does

- Model class `Order` maps to table `orders` (pluralized, snake_case)
- `has_many :line_items` generates FK-based association
- `resources :orders` generates 7 RESTful routes (index, show, new, create, edit, update, destroy)
- Validation via `validates :name, presence: true, length: { maximum: 255 }`
- Callbacks (`before_save`, `after_create`) for side effects

#### What we learn

- Pluralization and naming conventions are proven and well-understood; we adopt them directly
- The 7 standard REST actions (index/show/create/update/destroy + new/edit forms) cover most CRUD;
  we generate the same set
- Rails' approach to "member routes" for custom actions
  (`resources :orders do; member do; post :place; end; end`) maps directly to our state machine
  transition endpoints

#### What we do differently

- Rails has no notion of preconditions or postconditions; its validations are structural only
- We derive routes from spec semantics (mutation analysis), rather than from explicit `resources`
  declarations
- We generate the database schema AND the application code from a single spec; Rails requires you to
  write migrations separately from models
- Our invariants become CHECK constraints AND application validation AND tests; Rails validations
  are application-only

### 7.2 JHipster JDL (entity-to-Spring-boot)

#### What JHipster does

- JDL entity definitions -> Spring Boot entities, repositories, services, REST controllers,
  Angular/React UI
- Relationships (`OneToMany`, `ManyToMany`) -> JPA annotations + FK/junction tables
- Pagination, filtering, sorting generated automatically
- DTOs, mappers, and service layer generated

#### What we learn

- JHipster proves that a full stack can be generated from a structural spec; this validates our
  approach
- JHipster's relationship types directly correspond to our multiplicity annotations
- JHipster's DTO/mapper pattern is a good model for separating API schemas from DB models

#### What we do differently

- JHipster has no behavioral specs, no preconditions, postconditions, or invariants. All generated
  endpoints accept any valid-shaped request regardless of business rules
- JHipster generates CRUD only; custom operations (state machines, transfers, computations) must be
  hand-coded
- We generate from a formal spec that can be model-checked BEFORE code generation; JHipster cannot
  detect spec inconsistencies
- We generate conformance tests from the spec; JHipster generates basic unit tests but no
  property-based tests

### 7.3 Django REST framework (model -> serializer -> viewset -> router)

#### What DRF does

- `ModelSerializer` auto-generates serialization from Django model fields
- `ModelViewSet` generates list/create/retrieve/update/partial_update/destroy actions
- `DefaultRouter` maps ViewSet actions to URLs
- Permission classes, throttling, filtering, pagination are composable

#### What we learn

- DRF's layered architecture (model -> serializer -> viewset -> router) is a clean separation of
  concerns
- DRF's `@action` decorator for custom endpoints maps to our state machine transition routes
- DRF's filter backends (django-filter) show how to auto-generate filtering from model fields

#### What we do differently

- DRF requires you to write the Django model first; we generate the model from the spec
- DRF has no formal validation beyond field types; we generate validation from invariants and
  preconditions
- DRF's viewsets are CRUD-centric; our convention engine handles arbitrary operations including
  state machines and multi-entity mutations

### 7.4 Smithy traits (protocol-agnostic behavior)

#### What Smithy does

- Traits annotate shapes with behavioral metadata (`@http`, `@auth`, `@paginated`, `@idempotent`)
- Protocol-agnostic: same Smithy model can target REST, gRPC, MQTT
- Code generators produce client SDKs and server stubs
- Used for all AWS SDK APIs

#### What we learn

- Smithy's trait system is the best model for extensible metadata; our `conventions` block is
  directly inspired by Smithy traits
- Smithy's `@readonly`, `@idempotent` traits correspond to our mutation analysis (we infer these
  instead of requiring annotations)
- Smithy's resource-based modeling (resource with CRUD lifecycle) matches our entity concept

#### What we do differently

- Smithy models API structure, rather than behavior; you describe WHAT the API looks like, not WHAT it does.
  We describe what the API does (pre/postconditions) and derive what it looks like
- Smithy requires manual annotation of every trait; we infer most traits from spec semantics
- Smithy does not generate database schemas or validation logic; it focuses on API surface
- We can verify spec consistency; Smithy validates structural correctness but not behavioral
  correctness

### 7.5 OpenAPI code generators

#### What they do

- Take an OpenAPI YAML/JSON spec and generate client SDKs and server stubs in 40+ languages
- Mature, widely-used ecosystem

#### What we learn

- OpenAPI is the lingua franca of REST API description; we should generate OpenAPI as an
  intermediate artifact
- The quality of generated server stubs varies wildly; we should not depend on third-party
  generators for production code

#### What we do differently

- OpenAPI describes the API surface (paths, methods, schemas) but not the implementation; we
  generate both
- OpenAPI has no notion of state, operations, or invariants; it describes the HTTP interface only
- Our spec is upstream of OpenAPI; we generate OpenAPI from our spec as one of many artifacts
- We generate tests that verify the implementation matches the spec; OpenAPI generators produce no
  behavioral tests

### 7.6 Summary comparison table

| Feature                  | Rails                  | JHipster         | DRF                | Smithy           | OpenAPI-Gen            | **Our Engine**                     |
| ------------------------ | ---------------------- | ---------------- | ------------------ | ---------------- | ---------------------- | ---------------------------------- |
| Derives routes from spec | No (explicit)          | No (explicit)    | No (explicit)      | Partial (traits) | No (input IS routes)   | **Yes (from mutation analysis)**   |
| Generates DB schema      | No (manual migrations) | Yes (from JDL)   | No (manual models) | No               | No                     | **Yes (from state model)**         |
| Behavioral validation    | No                     | No               | No                 | No               | No                     | **Yes (from requires/ensures)**    |
| Formal verification      | No                     | No               | No                 | Structural only  | Structural only        | **Yes (model checking)**           |
| Test generation          | Basic                  | Basic            | Basic              | Contract tests   | No                     | **Property-based from spec**       |
| Custom operations        | Manual                 | Manual           | @action decorator  | Custom shapes    | Manual                 | **From spec operations**           |
| State machines           | Manual                 | Manual           | Manual             | Not modeled      | Not modeled            | **Auto-detected from transitions** |
| Multi-target             | Ruby only              | Java/Spring only | Python only        | 7+ languages     | 40+ (variable quality) | **4 targets (high quality)**       |
