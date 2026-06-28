---
title: "Template architecture"
description: "The Handlebars template architecture and the per-target template tree"
---

## 2. Template architecture

### 2.1 Template engine selection

> **Shipped reality.** The `python-fastapi-postgres` target ships with Handlebars templates,
> not Jinja2. This section captures the original research-time recommendation; the implementation
> chose Handlebars because the codegen module is JVM/Scala-resident and Handlebars-Java integrates
> directly without a Python sidecar. The trade-off discussion below still applies if a future
> target wants to revisit the choice.

We use a **hybrid approach**: Jinja2 as the primary template engine with a thin orchestration layer
written in Python.

#### Why Jinja2

| Criterion            | Jinja2                              | Go templates | StringTemplate | Custom    |
| -------------------- | ----------------------------------- | ------------ | -------------- | --------- |
| Familiarity          | High (Python dev audience)          | Moderate     | Low            | None      |
| Expressiveness       | Rich (filters, macros, inheritance) | Limited      | Moderate       | Unlimited |
| Whitespace control   | Excellent (`{%- -%}`)               | Adequate     | Good           | Manual    |
| Template inheritance | Built-in (`extends`/`block`)        | No           | No             | Custom    |
| Library ecosystem    | Huge                                | Moderate     | Small          | N/A       |
| Performance          | Adequate (one-shot generation)      | Fast         | Fast           | Varies    |

Code generation is a batch process that runs once and produces files on disk. Template rendering
speed is irrelevant, a URL shortener spec generates ~25 files in under 100ms regardless of engine.
Expressiveness and developer familiarity dominate the choice.

### 2.2 IR-to-template variable mapping

The IR produced by the spec parser is a Scala 3 ADT (sealed abstract classes + final case
classes) extracted from Isabelle by `Code_Target_Scala`. The Handlebars engine receives a
`ProfiledService` view that wraps the IR with the convention-engine annotations. The shipped
types live in
[`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala)
(extracted; do not hand-edit) and `modules/profile/.../Types.scala`. The conceptual shape
(extracted Scala uses positional letters `a`/`b`/`c`/... instead of English field names, see
[`IR.thy`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/SpecRest/IR.thy)
for the source-of-truth definitions):

```scala
final case class ServiceIR(
    name: String,                      // "UrlShortener"
    entities: List[EntityDecl],        // UrlMapping
    enums: List[EnumDecl],
    typeAliases: List[TypeAliasDecl],  // ShortCode, LongURL, BaseURL
    state: Option[StateDecl],          // store, metadata, base_url
    operations: List[OperationDecl],   // Shorten, Resolve, Delete, ListAll
    invariants: List[InvariantDecl],   // global invariants
    temporals: List[TemporalDecl],
    facts: List[FactDecl],
    functions: List[FunctionDecl],
    predicates: List[PredicateDecl],
    transitions: List[TransitionDecl],
    conventions: Option[ConventionsDecl]   // user-specified overrides
)

final case class OperationDecl(
    name: String,                      // "Shorten"
    inputs: List[ParamDecl] = Nil,     // url: LongURL
    outputs: List[ParamDecl] = Nil,    // code: ShortCode, short_url: String
    requires: List[Expr] = Nil,        // isValidURI(url)
    ensures: List[Expr] = Nil,         // code not in pre(store), ...
    span: Option[Span] = None
)

// HTTP-shape annotation produced by the convention engine and added by
// modules/profile/.../Annotate.buildProfiledService before templates render.
final case class EndpointSpec(
    operationName: String,             // "Shorten"
    method: HttpMethod,                // POST
    path: String,                      // "/shorten"
    pathParams: List[ParamSpec],
    queryParams: List[ParamSpec],
    bodyParams: List[ParamSpec],
    successStatus: Int                 // 201
)
```

The convention engine annotates each `OperationIR` with HTTP mapping decisions before templates are
rendered. Templates then access these annotations directly:

```text
@router.{{ op.http_method | lower }}(
    "{{ op.http_path }}",
    status_code={{ op.http_success_status }},
)
async def {{ op.name | snake_case }}(
    {%- for input in op.inputs %}
    {{ input.name }}: {{ input | python_type }},
    {%- endfor %}
    session: AsyncSession = Depends(get_session),
) -> {{ op | python_response_type }}:
```

### 2.3 Convention engine feed-in

The convention engine makes decisions that templates consume:

| Convention Decision | Template Variable          | Example Value                 |
| ------------------- | -------------------------- | ----------------------------- |
| HTTP method         | `op.http_method`           | `"POST"`                      |
| URL path            | `op.http_path`             | `"/shorten"`                  |
| Success status code | `op.http_success_status`   | `201`                         |
| Error mappings      | `op.http_error_responses`  | `[(422, "Validation error")]` |
| DB table name       | `state.table_name`         | `"store"`                     |
| Column types        | `field.db_type`            | `"VARCHAR(10)"`               |
| Index decisions     | `state.indexes`            | `[("created_at", "btree")]`   |
| Constraint SQL      | `entity.check_constraints` | `["length(code) >= 6"]`       |

Convention decisions are made once, stored on the IR, and consumed by every target's templates. This
means adding a new convention (e.g., "all list endpoints support cursor pagination") only requires
changing the convention engine, all targets inherit the new behavior.

### 2.4 Dafny output splicing

When the LLM synthesis engine produces verified Dafny code for an operation, it is compiled to the
target language and spliced into the service layer:

```text
Dafny source        ->  dafny build --target py  ->  Python module
                                                       |
Template renders    ->  service layer template    ->  imports Dafny module
                                                       |
Adapter wraps       ->  convert Dafny types       ->  native Python types
```

The template detects whether an operation has Dafny output available:

```text
{% if op.dafny_module %}
from {{ op.dafny_module }} import {{ op.dafny_function }} as _dafny_{{ op.name | snake_case }}
{% endif %}

class {{ service.name }}Service:
    async def {{ op.name | snake_case }}(self, ...) -> ...:
        {% if op.dafny_module %}
        # Verified implementation from Dafny
        result = _dafny_{{ op.name | snake_case }}({{ op.inputs | dafny_args }})
        return {{ op | adapt_dafny_result("result") }}
        {% else %}
        # Convention-generated CRUD implementation
        {{ op | crud_implementation }}
        {% endif %}
```

### 2.5 Cross-cutting concerns

Cross-cutting concerns (logging, error handling, auth, rate limiting) are handled via template
composition rather than inheritance:

```tree
templates/
├── _base/
│   ├── logging.py.j2          # import logging; logger = ...
│   ├── error_handler.py.j2    # @app.exception_handler(...)
│   └── health_check.py.j2     # @app.get("/health")
├── python-fastapi/
│   ├── main.py.j2             # {% include "_base/logging.py.j2" %}
│   ├── router.py.j2
│   ├── service.py.j2
│   └── ...
├── go-chi/
│   ├── main.go.j2
│   ├── handler.go.j2
│   └── ...
└── typescript-express/
    ├── app.ts.j2
    ├── route.ts.j2
    └── ...
```

Each target's `main` template includes the relevant cross-cutting fragments. The base fragments are
parameterized to work across targets, they receive the same IR context but produce
language-specific output.

### 2.6 Template organization and quality

#### Structure

```tree
templates/
├── _shared/                        # Language-agnostic partials
│   ├── openapi.yaml.j2            # OpenAPI generation (shared across all targets)
│   ├── docker-compose.yml.j2      # Docker composition
│   └── makefile.j2                # Common Makefile targets
├── python-fastapi-postgres/
│   ├── manifest.json              # Lists all files to generate + their templates
│   ├── pyproject.toml.j2
│   ├── dockerfile.j2
│   ├── alembic_env.py.j2
│   ├── migration.py.j2
│   ├── main.py.j2
│   ├── config.py.j2
│   ├── database.py.j2
│   ├── model.py.j2                # One per entity/state relation
│   ├── schema.py.j2               # Pydantic models
│   ├── router.py.j2               # Route handlers
│   ├── service.py.j2              # Business logic
│   ├── validator.py.j2            # From requires clauses
│   ├── conftest.py.j2             # Test fixtures
│   ├── test_api.py.j2             # API integration tests
│   └── test_properties.py.j2     # Property-based tests
├── go-chi-postgres/
│   ├── manifest.json
│   ├── ...
└── typescript-express-prisma/
    ├── manifest.json
    ├── ...
```

**`manifest.json`** drives the generation process:

```json
{
  "target": "python-fastapi-postgres",
  "files": [
    { "template": "pyproject.toml.j2", "output": "pyproject.toml", "per": "service" },
    { "template": "dockerfile.j2", "output": "Dockerfile", "per": "service" },
    { "template": "main.py.j2", "output": "app/main.py", "per": "service" },
    { "template": "config.py.j2", "output": "app/config.py", "per": "service" },
    { "template": "database.py.j2", "output": "app/database.py", "per": "service" },
    { "template": "model.py.j2", "output": "app/models/{{ svc }}.py", "per": "service" },
    { "template": "schema.py.j2", "output": "app/schemas/{{ svc }}.py", "per": "service" },
    { "template": "router.py.j2", "output": "app/routers/{{ svc }}.py", "per": "service" },
    { "template": "service.py.j2", "output": "app/services/{{ svc }}.py", "per": "service" },
    { "template": "validator.py.j2", "output": "app/validators/{{ svc }}.py", "per": "service" },
    {
      "template": "migration.py.j2",
      "output": "alembic/versions/001_initial_schema.py",
      "per": "service"
    },
    { "template": "test_api.py.j2", "output": "tests/test_api.py", "per": "service" },
    { "template": "test_properties.py.j2", "output": "tests/test_properties.py", "per": "service" }
  ]
}
```

#### Preventing template rot

1. Template tests: Each template has a golden-file test. The test renders the template with the
   URL shortener IR and compares the output to the checked-in golden file. Any change to a template
   that alters output triggers a diff review.

2. Compilation gate: After rendering, the generated project is compiled/type-checked as a CI
   step. For Python: `ruff check + mypy`. For Go: `go build + go vet`. For TypeScript:
   `tsc --noEmit`. Templates that produce code with lint errors fail CI.

3. Integration gate: The generated project is started in Docker, and the generated tests are run
   against it. Templates that produce code with test failures fail CI.
