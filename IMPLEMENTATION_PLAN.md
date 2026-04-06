# Implementation Plan

Concrete milestones for building the spec-to-rest compiler. Each milestone has acceptance criteria,
key deliverables, and links to research docs with design details.

**Stack:** TypeScript, ANTLR4 (via antlr-ng), Z3 (WASM + native fallback), Dafny CLI, Handlebars
templates.

**Repo layout (target):**

```text
src/
├── parser/           # ANTLR4 grammar + AST builder
├── ir/               # IR types, builder, printer, validator
├── conventions/      # HTTP/DB mapping engine, profiles, overrides
├── verify/           # Z3 backend, invariant/preservation checks
├── synthesis/        # CEGIS loop, Dafny generation, LLM integration
├── emit/             # Code emitter, templates, OpenAPI, SQL, Docker
├── testgen/          # Test generation (property, stateful, schemathesis)
└── cli.ts            # CLI entry point (commander)
tests/
├── unit/
├── integration/
├── golden/           # Snapshot tests vs expected/ outputs
└── fixtures/         # .spec files + expected outputs
examples/
├── url_shortener.spec
├── todo_list.spec
└── ecommerce.spec
```

---

## Phase 1: Parser + IR (weeks 1-3)

**Goal:** `spec-to-rest inspect url_shortener.spec` parses a spec and prints the IR.

### M1.1 — ANTLR4 Grammar

Write the ANTLR4 `.g4` grammar for the core DSL subset: service, entity, enum, state, operation
(requires/ensures), invariant, convention overrides, type expressions.

- Input: DSL source text
- Output: Parse tree (CST)
- Acceptance: All 4 worked examples parse without error

**Design reference:** [Spec Language Design](docs/content/docs/research/01_spec_language_design.md)
— EBNF grammar (line ~885), type system (line ~2516), worked examples (line ~1430).
[DSL Compiler Frameworks](docs/content/docs/research/09_dsl_compiler_frameworks.md) — ANTLR4
recommendation and rationale.

### M1.2 — AST and IR Types

Define TypeScript discriminated unions for the IR. Every spec construct gets a typed node: services,
entities, fields, operations, expressions, types, invariants.

Key types:

```typescript
interface ServiceIR {
  name: string;
  entities: EntityDecl[];
  state: StateDecl;
  operations: OperationDecl[];
  invariants: InvariantDecl[];
  conventions?: ConventionsDecl;
  annotations: Record<string, unknown>; // added by convention engine
  span?: Span;
}
```

- Acceptance: IR round-trips through JSON serialization without loss

**Design reference:**
[Implementation Architecture](docs/content/docs/research/07_implementation_architecture.md) — IR
type definitions (line ~1088), expression types (line ~1192).

### M1.3 — CST-to-IR Builder

ANTLR4 visitor that walks the parse tree and constructs IR nodes. Handles desugaring (e.g.,
`x not in S` → `not(contains(S, x))`), default values, and source span tracking.

- Acceptance: `inspect --format json` produces correct IR for all 4 examples
- Acceptance: Malformed specs produce errors with line/column info

### M1.4 — CLI Skeleton

`spec-to-rest` CLI with `inspect` and `check` commands (check = parse-only for now). Uses
`commander` for arg parsing.

```text
spec-to-rest inspect [--format ir|json|summary] <spec-file>
spec-to-rest check <spec-file>
```

**Design reference:**
[Implementation Architecture](docs/content/docs/research/07_implementation_architecture.md) — CLI
design (line ~2512).

---

## Phase 2: Convention Engine (weeks 4-6)

**Goal:** `spec-to-rest inspect --format openapi url_shortener.spec` produces a valid OpenAPI 3.1
spec. The engine annotates the IR with HTTP methods, paths, status codes, and DB schema decisions.

### M2.1 — Operation Classification

Implement rules M1-M10 for classifying operations into HTTP methods based on their requires/ensures
clauses. Detect: creates (new key in state'), reads (no mutation), updates (PUT vs PATCH based on
field coverage), deletes, actions, sub-resources.

- Acceptance: URL shortener operations classified correctly (Shorten→POST, Resolve→GET,
  Delete→DELETE)
- Acceptance: E-commerce operations with state machine transitions classified as POST to action
  sub-resources

**Design reference:** [Convention Engine](docs/content/docs/research/02_convention_engine.md) —
M1-M10 rules (line ~76), PUT vs PATCH disambiguation (line ~87), state machine transitions (line
~1363).

### M2.2 — Path and Schema Derivation

- Entity names → pluralized snake_case table names and URL path segments
- Fields → DB columns with type mapping (String→TEXT, Int→INTEGER, etc.)
- Relation multiplicities → foreign keys / junction tables
- Invariants with field constraints → CHECK constraints
- Refinement types → validation rules

- Acceptance: Generated DB schema for URL shortener has correct tables, columns, constraints

**Design reference:** [Convention Engine](docs/content/docs/research/02_convention_engine.md) — DB
schema mapping (line ~700), naming conventions (line ~850), validation extraction (line ~950).

### M2.3 — Override System

Parse `conventions {}` blocks. Resolution order: user overrides > profile defaults > engine rules.
Validate overrides against allowed values.

- Acceptance: `Resolve.http_method = "GET"` override works and is reflected in OpenAPI output

**Design reference:** [Convention Engine](docs/content/docs/research/02_convention_engine.md) —
override system (line ~565).

### M2.4 — Deployment Profiles

Implement at least the `python-fastapi-postgres` profile. Profile determines: ORM choice, migration
tool, validation library, naming conventions, async strategy.

**Design reference:** [Convention Engine](docs/content/docs/research/02_convention_engine.md) —
deployment profiles (line ~1948), all 3 profiles fully specified.

---

## Phase 3: Code Generation MVP (weeks 7-9)

**Goal:** `spec-to-rest generate --target python-fastapi url_shortener.spec` produces a running
service. Business logic is stubbed with TODOs — no LLM synthesis yet.

### M3.1 — Template Engine Setup

Handlebars template engine with helpers for: snake_case, pluralize, type mapping (spec type → Python
type), indent. Templates receive the annotated IR (with convention annotations).

**Design reference:**
[Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) — template
architecture (line ~2047), IR-to-template mapping.

### M3.2 — Python/FastAPI Templates

Complete template set:

| Template                   | Generates                                              |
| -------------------------- | ------------------------------------------------------ |
| `main.py.hbs`              | FastAPI app, CORS, exception handlers, router includes |
| `config.py.hbs`            | Pydantic settings from environment                     |
| `database.py.hbs`          | Async SQLAlchemy session factory                       |
| `models/{entity}.py.hbs`   | SQLAlchemy ORM model per entity                        |
| `schemas/{entity}.py.hbs`  | Pydantic request/response schemas                      |
| `routers/{entity}.py.hbs`  | FastAPI router with endpoint functions                 |
| `services/{entity}.py.hbs` | Business logic layer (TODO stubs for complex ops)      |

- Acceptance: Generated code passes `ruff check` and `mypy --strict`
- Acceptance: `docker-compose up` starts the service and responds to health check

**Design reference:**
[Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) — Python file
manifest (line ~48), complete generated code examples (line ~93).

### M3.3 — OpenAPI Generation

Generate OpenAPI 3.1 spec from annotated IR. Includes: paths, operations, request/response schemas,
error responses, security schemes (if auth model present).

- Acceptance: Generated spec validates with `swagger-cli validate`
- Acceptance: Spec matches the actual generated endpoints

### M3.4 — SQL Migration Generation

Generate initial migration (Alembic format for Python target). Tables, columns, constraints,
indexes, foreign keys derived from convention engine output.

- Acceptance: `alembic upgrade head` creates correct schema

### M3.5 — Infrastructure Generation

- `Dockerfile` (multi-stage build, non-root user, health check)
- `docker-compose.yml` (app + PostgreSQL)
- `.env.example`
- `Makefile` with common targets
- GitHub Actions CI workflow

- Acceptance: Full `docker-compose up && curl /health` works end-to-end

**Design reference:**
[Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) — Docker
generation (line ~2800), CI generation (line ~3100).

### M3.6 — Golden File Tests

Snapshot tests for each example spec. Capture all generated files as expected output. Diffs on
regeneration catch regressions.

- Acceptance: `npm test` includes golden file comparison for URL shortener

---

## Phase 4: Spec Verification (weeks 10-12)

**Goal:** `spec-to-rest check url_shortener.spec` catches design errors before generation.

### M4.1 — Z3 Backend

Translate IR to Z3 SMT-LIB constraints via `z3-solver` WASM bindings (with native CLI fallback).
Encode: entity types as sorts, fields as functions, state as maps, invariants as assertions.

- Acceptance: Contradictory invariants detected (SAT → model shown as counterexample)

**Design reference:** [Spec Verification](docs/content/docs/research/06_spec_verification.md) — Z3
translation approach (line ~455), SMT-LIB encoding examples (line ~640).

### M4.2 — Invariant Consistency

Check: conjunction of all invariants is satisfiable. If UNSAT, no valid state can exist → error with
explanation.

- Acceptance: Spec with `x > 10` and `x < 5` invariants → clear error

### M4.3 — Invariant Preservation

For each (operation, invariant) pair: verify that if the invariant holds in pre-state and the
operation's requires/ensures hold, then the invariant holds in post-state. Uses frame condition
inference (fields not mentioned in ensures are unchanged).

- Acceptance: Operation that sets `clicks = -1` caught as violating `clicks >= 0` invariant
- Acceptance: Counterexample shows specific values that break the invariant

**Design reference:** [Spec Verification](docs/content/docs/research/06_spec_verification.md) —
preservation checking (line ~1000), frame condition inference (line ~1150).

### M4.4 — Error Reporting

Rich error messages with: source location (file:line:col), error category, human-readable
explanation, counterexample (when available from Z3 model), suggested fix.

26 error categories defined. Priority: implement the 10 most common first.

**Design reference:** [Spec Verification](docs/content/docs/research/06_spec_verification.md) —
error categories (line ~182), error reporting templates (line ~2500).

---

## Phase 5: Test Generation (weeks 13-15)

**Goal:** Generated service includes 3 layers of automated tests.

### M5.1 — Property Tests from Ensures

Each `ensures` clause → one Hypothesis property test. Each `requires` clause → one negative test
(violating precondition should return 422). Each invariant → post-operation check.

- Acceptance: `pytest tests/test_properties.py` passes against running service

**Design reference:** [Test Generation](docs/content/docs/research/05_test_generation.md) — property
test generation (line ~200), ensures-to-property translation (line ~334).

### M5.2 — Stateful Tests via Hypothesis

Generate `RuleBasedStateMachine` subclass. Each operation → `@rule`. Each `requires` →
`@precondition`. Each invariant → `@invariant`. Cross-operation data flow via Hypothesis bundles
(e.g., created IDs fed to resolve/delete rules).

- Acceptance: `pytest tests/test_stateful.py` discovers multi-step violations

**Design reference:** [Test Generation](docs/content/docs/research/05_test_generation.md) — state
machine generation (line ~334), bundle strategy (line ~400), complete worked examples (line ~470).

### M5.3 — Schemathesis Config

Generate Schemathesis configuration from OpenAPI spec. Custom checks derived from spec invariants.
Three test profiles (smoke/thorough/exhaustive) with configurable depth.

- Acceptance: `schemathesis run openapi.yaml --checks all` passes

**Design reference:** [Test Generation](docs/content/docs/research/05_test_generation.md) —
Schemathesis integration (line ~53), test profiles (line ~79).
[Test Tools Research](docs/content/docs/research/spec_to_test_tools_research.md) — Schemathesis deep
dive (line ~30), Hypothesis deep dive (line ~180).

### M5.4 — Conformance Runner

Shell script / Makefile target that orchestrates the full test pipeline: start services → health
check → reset state → run structural tests → reset → run property tests → reset → run stateful tests
→ collect results → stop services.

- Acceptance: `make test-conformance` runs all 3 layers and produces JUnit XML report

**Design reference:** [Test Generation](docs/content/docs/research/05_test_generation.md) —
conformance runner pipeline (line ~3069).

---

## Phase 6: LLM Synthesis (weeks 16-19)

**Goal:** Complex operations get verified implementation bodies via CEGIS loop.

### M6.1 — Operation Classification

Classify each operation as DIRECT_EMIT or LLM_SYNTHESIS based on ensures clause analysis:

- Pure CRUD (state updated to match input) → direct emit
- Computation beyond map/set update → LLM synthesis

- Acceptance: URL shortener Shorten (needs code generation) classified as LLM_SYNTHESIS; Delete
  (pure state removal) classified as DIRECT_EMIT

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — classification
decision tree (line ~1596), category definitions (line ~1690).

### M6.2 — Dafny Signature Generation

Translate operation IR to a complete Dafny file: class definitions (from entities), predicate
definitions (from invariants), method signature with requires/ensures/modifies clauses. The method
**body** is left empty — that's what the LLM fills.

- Acceptance: `inspect --format dafny` outputs valid Dafny file (parseable by `dafny verify`)

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — Dafny
translation rules (line ~83), spec-to-Dafny mapping table (line ~90), complete Dafny output example
(line ~300).

### M6.3 — LLM Integration + Prompt Engineering

Anthropic SDK client with: prompt construction (skeleton + context + few-shot examples + previous
errors), response parsing (extract Dafny code block), diff-checker (verify LLM didn't modify
immutable signature/clauses), token tracking + cost budgeting, result caching.

- Acceptance: LLM generates syntactically valid Dafny method bodies

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — prompt
engineering (line ~1200), few-shot examples (line ~1350), diff-checker (line ~1500).

### M6.4 — CEGIS Feedback Loop

The core generate-verify-repair loop (max 8 iterations):

1. Construct prompt with Dafny skeleton + context
2. LLM generates candidate body
3. Diff-check: signature/clauses unchanged? If not, reject and re-prompt
4. `dafny verify` on the complete file
5. If verified → compile to target language → done
6. If failed → parse error, extract counterexample, format repair hint → go to 1

- Acceptance: URL shortener Shorten operation verified within 3 iterations

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — CEGIS
architecture (line ~52), error parsing (line ~1800), fallback strategies (line ~1949).

### M6.5 — Dafny Compilation to Target

Invoke `dafny translate py` (or `go`/`js`) on verified `.dfy` file. Post-process output: replace
Dafny runtime types with native types at API boundaries, format with target language formatter.
Splice into generated service alongside convention-engine-emitted code.

- Acceptance: Synthesized Python code integrates with FastAPI service without modification

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — Dafny
compilation pipeline (line ~1045), target language issues (line ~1098), post-processing (line
~1147). [Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) —
Dafny output splicing (line ~2133).

### M6.6 — Graduated Fallback

When CEGIS fails: L1 retry with different prompting strategies → L2 decompose operation → L3
escalate to stronger LLM model → L4 emit skeleton with TODOs → L5 report to user.

- Acceptance: Fallback chain produces at least a compiling skeleton for any operation

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — fallback
strategy (line ~1949), decomposition approach (line ~2012).

---

## Phase 7: Multi-Target + Polish (weeks 20-22)

**Goal:** Go/chi and TypeScript/Express targets; production-ready CLI.

### M7.1 — Go/chi Target

Convention profile (Go naming, types, error handling) + Handlebars templates (main.go, handler.go,
model.go, repository.go, go.mod, Dockerfile). Dafny → Go compilation integration.

- Acceptance: `spec-to-rest generate --target go-chi url_shortener.spec && cd out && go build`

**Design reference:**
[Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) — Go file
manifest (line ~700), complete Go code examples (line ~750).
[Convention Engine](docs/content/docs/research/02_convention_engine.md) — go-chi-postgres profile
(line ~2000).

### M7.2 — TypeScript/Express Target

Convention profile + templates (app.ts, routes, models, repository, package.json, Dockerfile). Dafny
→ JavaScript compilation integration.

- Acceptance: `spec-to-rest generate --target ts-express url_shortener.spec && cd out && npm start`

**Design reference:**
[Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md) — TypeScript
file manifest (line ~850), complete TS code examples (line ~900).
[Convention Engine](docs/content/docs/research/02_convention_engine.md) — ts-express-postgres
profile (line ~2050).

### M7.3 — CLI Polish

- `spec-to-rest generate` with all flags (--target, --output, --llm, --no-synthesis, --no-verify,
  --no-tests, --dry-run)
- `spec-to-rest diff` — show what would change on regeneration
- `spec-to-rest test` — run conformance tests against running service
- Progress reporting, colored output, `--verbose` / `--quiet` modes
- Exit codes for CI integration

### M7.4 — Distribution

- npm package: `npm install -g spec-to-rest`
- Single binary via esbuild/bun compile
- Docker image: `docker run spec-to-rest generate ...`
- GitHub Action for CI/CD integration

---

## Risk Mitigations

| Risk                                    | Mitigation                                                              |
| --------------------------------------- | ----------------------------------------------------------------------- |
| Grammar keeps changing                  | Freeze after Phase 1; iterate on examples first                         |
| Z3 WASM too slow                        | Fall back to native Z3 CLI subprocess                                   |
| Dafny generates non-idiomatic code      | Post-process with target formatter; replace runtime types at boundaries |
| LLM costs too high                      | Cache results; skip CRUD (direct emit); cost budget cap                 |
| Convention engine makes wrong decisions | Every decision overridable; `inspect` shows reasoning                   |
| Spec language design wrong              | Write 5+ example specs before finalizing grammar                        |

**Design reference:**
[Implementation Architecture](docs/content/docs/research/07_implementation_architecture.md) — risk
table (line ~3591).

---

## Cost Estimates (LLM Synthesis)

| Service complexity     | Synthesized ops | LLM calls | Est. cost  |
| ---------------------- | --------------- | --------- | ---------- |
| URL shortener (3 ops)  | 1               | 3-8       | $0.05-0.20 |
| Todo list (5 ops)      | 2               | 6-16      | $0.10-0.40 |
| E-commerce (15 ops)    | 5               | 15-40     | $0.30-1.20 |
| Large service (30 ops) | 10              | 30-80     | $0.60-2.50 |

**Design reference:**
[LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md) — cost analysis
(line ~2518).

---

## Research Documents Index

| Doc                                                                                            | Topic                                   | Key sections                                                     |
| ---------------------------------------------------------------------------------------------- | --------------------------------------- | ---------------------------------------------------------------- |
| [00 Comprehensive Analysis](docs/content/docs/research/00_comprehensive_analysis.md)           | Landscape, architecture, build plan     | 5-stage architecture, key decisions, gap analysis                |
| [01 Spec Language Design](docs/content/docs/research/01_spec_language_design.md)               | Grammar, types, semantics, examples     | EBNF grammar, type system, 4 worked examples, error messages     |
| [02 Convention Engine](docs/content/docs/research/02_convention_engine.md)                     | HTTP/DB mapping, profiles, overrides    | M1-M10 rules, 3 deployment profiles, override system             |
| [03 LLM+Verifier Synthesis](docs/content/docs/research/03_llm_verifier_synthesis.md)           | CEGIS loop, Dafny, prompts, costs       | Loop architecture, Dafny integration, classification, fallbacks  |
| [04 Code Generation Pipeline](docs/content/docs/research/04_code_generation_pipeline.md)       | Templates, targets, file manifests      | 3 complete file manifests, template architecture, infrastructure |
| [05 Test Generation](docs/content/docs/research/05_test_generation.md)                         | Property/stateful/structural tests      | 3 test layers, Schemathesis, Hypothesis state machines           |
| [06 Spec Verification](docs/content/docs/research/06_spec_verification.md)                     | Z3, invariant checking, error reporting | SMT encoding, preservation proofs, 26 error categories           |
| [07 Implementation Architecture](docs/content/docs/research/07_implementation_architecture.md) | Stack, IR, CLI, project structure       | TypeScript + ANTLR4, IR types, CLI design, 6-phase build plan    |
| [08 MLIR Evaluation](docs/content/docs/research/08_mlir_evaluation.md)                         | Why not MLIR                            | Scored 1.4/5, wrong abstraction level                            |
| [08 Parser Reuse Analysis](docs/content/docs/research/08_parser_reuse_analysis.md)             | Why not reuse existing parsers          | Alloy best at 61% coverage, insufficient for our DSL             |
| [09 DSL Compiler Frameworks](docs/content/docs/research/09_dsl_compiler_frameworks.md)         | ANTLR4 vs Langium vs others             | Langium rejected (audit), ANTLR4/antlr-ng chosen                 |
| [Test Tools Research](docs/content/docs/research/spec_to_test_tools_research.md)               | Schemathesis, Hypothesis, QuickCheck    | Tool deep dives, integration patterns                            |
