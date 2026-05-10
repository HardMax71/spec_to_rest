# spec-to-rest

Compile formal behavioral specs (`.spec` DSL) into verified REST services.

Given a `.spec` file describing entities, operations, invariants, and preconditions, `spec-to-rest`
runs a Z3-backed verification pass against the spec itself and — if verification passes — generates
a complete Python/FastAPI/PostgreSQL service: SQLAlchemy models, Pydantic schemas, FastAPI routers,
Alembic migrations, Docker / docker-compose / Makefile / CI workflow, and an OpenAPI 3.1 document.

## Install

Requires JDK 21 and sbt 1.10+. The fastest path on Linux/macOS:

```bash
# Install coursier (manages JDK + sbt + scala toolchain)
curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz \
  | gzip -d > cs && chmod +x cs && ./cs setup --yes

# Or directly via your package manager
#   macOS:  brew install sbt
#   Debian: see https://www.scala-sbt.org/download/
```

## Build + run

Everything is driven through sbt:

```bash
# Run every subcommand via `sbt cli/run`
sbt "cli/run check     fixtures/spec/url_shortener.spec"
sbt "cli/run inspect -f json fixtures/spec/url_shortener.spec"
sbt "cli/run verify    fixtures/spec/url_shortener.spec"
sbt "cli/run compile   --target python-fastapi-postgres --out /tmp/my-service fixtures/spec/url_shortener.spec"
sbt "cli/run compile   --target go-chi-postgres --out /tmp/my-go-service fixtures/spec/url_shortener.spec"
sbt "cli/run compile   --target ts-express-postgres --out /tmp/my-ts-service fixtures/spec/url_shortener.spec"

# Generate a service AND a Hypothesis property test suite (M5.1)
sbt "cli/run compile   --with-tests --out /tmp/my-service fixtures/spec/url_shortener.spec"

# Run the full test suite
sbt test

# Run one module's tests
sbt ir/test
sbt verify/test
```

### Native binary (single static executable)

With GraalVM CE 21 installed (e.g. via `cs java --jvm graalvm-community:21` or SDKMAN):

```bash
sbt cli/nativeImage
./modules/cli/target/native-image/spec-to-rest verify fixtures/spec/url_shortener.spec
```

The binary is ~30 MB with ~50 ms cold start, no JVM required at runtime.

## Mechanically verified translator soundness

The `verify` command's correctness is **mechanically validated in Isabelle/HOL**. The universal
soundness meta-theorem `SpecRest.soundness` in `proofs/isabelle/SpecRest/Soundness.thy` closes with
**zero `sorry`** for the verified subset: atoms, identifiers, all logical/arithmetic/comparison
operators, state-relation membership/cardinality/lookup/subset, set literals and set-valued
`In`/`NotIn`/`Union`/`Intersect`/`Diff`, FieldAccess on entity-valued expressions, single-state
`Prime`/`Pre` collapse, quantifiers over enums and state-relations, and `With` record-update
(Skolem-encoded).

What this means concretely: when `verify` returns UNSAT for an in-subset obligation, that verdict
reflects a property of the spec — not just a coincidence between the translator and Z3. The abstract
translator is `proofs/isabelle/SpecRest/Translate.thy`; the soundness theorem ties it to the spec's
denotational semantics via correlation lemmas between Isabelle's `eval` and the shallow `smt_eval`
embedding.

Isabelle's `Code_Target_Scala` extracts `translate`, `eval`, and `smt_eval` plus the canonical IR
ADT to ~2.4 kLoC of idiomatic Scala 3 (BigInt-mapped) at
`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala`. The Scala layer's
`translate` is no longer hand-written — it is the extracted Isabelle definition. Since
[#202](https://github.com/HardMax71/spec_to_rest/issues/202) the IR ADT consumed by every module is
also extracted (no hand-written wrapper). CI builds the proofs every PR via
`.github/workflows/isabelle-build.yml`.

See [10_translator_soundness.md](docs/content/docs/research/10_translator_soundness.md) for the
formal claim, full trust closure, and roadmap.

## Subcommands

| Command                                                            | Description                                                                                                                                                                                                 |
| ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `check <spec>`                                                     | Parse and validate spec file structure. Exit 0 if valid.                                                                                                                                                    |
| `inspect -f json \| summary \| ir \| dafny \| dafny-prompt <spec>` | Print the IR. `-f json` produces the canonical serialized form; `-f dafny` lifts to a Dafny skeleton; `-f dafny-prompt` renders the LLM prompt the synth pipeline uses.                                     |
| `verify <spec>`                                                    | Run Z3-backed consistency + invariant-preservation checks. Emits counterexamples on failure. `--dump-smt` prints the SMT-LIB encoding without solver run.                                                   |
| `compile --target <profile> --out <dir> <spec>`                    | Emit the full target-language service (models, schemas, routers, migrations, OpenAPI spec).                                                                                                                 |
| `compile --with-tests --out <dir> <spec>`                          | Additionally emit Hypothesis property tests, strategies, conftest, and a `/__test_admin__` router gated by `ENABLE_TEST_ADMIN`. See [test-generation.mdx](docs/content/docs/pipelines/test-generation.mdx). |
| `synth try --operation <op> <spec>`                                | Phase 6 (experimental). One-shot LLM call: builds the Dafny prompt for `<op>`, calls Anthropic / OpenAI, prints the parsed body. Diff-checks against the spec-derived contract. No verification.            |
| `synth verify --operation <op> <spec>`                             | Phase 6. Runs the CEGIS loop: generate → diff-check → splice → `dafny verify` → repair → repeat, until the body verifies or the budget aborts. Requires a `dafny` binary on `$PATH` (or `--dafny-bin`).     |

## Exit codes (for `verify`)

| Code | Meaning                                                                                                                                              |
| ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0    | All consistency checks passed.                                                                                                                       |
| 1    | Violations: unsat invariants, unsatisfiable preconditions, unreachable operations, or preservation failures.                                         |
| 2    | Translator coverage gap: the spec uses a construct the verifier doesn't yet support. Other checks may still pass; affected checks show as `skipped`. |
| 3    | Solver backend crashed.                                                                                                                              |

## Project layout

```text
modules/
  ir/         — IR types + JSON serializer (circe)
  parser/     — ANTLR4 grammar (Spec.g4) + parse tree → IR builder
  convention/ — M1-M10 operation classifier, naming, path, schema, validate
  profile/    — Deployment profiles (python-fastapi-postgres, go-chi-postgres, ts-express-postgres), type mapping
  verify/     — IR → SMT translator, Java Z3 backend (z3-turnkey), consistency checker, counterexample decoding, diagnostic formatter
  codegen/    — Handlebars engine (handlebars.java), Emit orchestrator, OpenAPI subsystem, Alembic migration builder
  synth/      — Phase 6 LLM + Dafny synthesis: PromptBuilder, ResponseParser, DiffChecker, FileAssembly, DafnyVerifier (--log-format json), CegisLoop, Cache, Tracker
  cli/        — decline-effect CommandIOApp: check / inspect / verify / compile / synth try / synth verify
  bench/      — JMH benchmarks (parallel verify CSV golden)
fixtures/     — .spec inputs + golden IR JSON + golden SMT-LIB outputs + golden JMH CSV, used by tests and CI
docs/         — Fumadocs site with the spec-language reference + architecture + verification + concurrency docs
```

Each module is an independent sbt subproject — run `sbt <module>/test` to test just one.

## Documentation

Full docs live under [`docs/content/docs/`](docs/content/docs/) and render via Fumadocs. Start with
[`index.mdx`](docs/content/docs/index.mdx) and
[`spec-language.mdx`](docs/content/docs/spec-language.mdx) for the DSL, then
[`architecture.mdx`](docs/content/docs/design/architecture.mdx) for the compiler internals. For the
Cats Effect 3 pipeline, `--parallel` semantics, and the cancellation contract, see
[`concurrency.mdx`](docs/content/docs/pipelines/concurrency.mdx).

## License

MIT. See [LICENSE](LICENSE).
