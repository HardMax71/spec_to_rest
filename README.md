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

The `verify` command's correctness is **mechanically validated in Lean 4**. The universal soundness
meta-theorem `SpecRest.soundness` in `proofs/lean/SpecRest/Soundness.lean` closes with **zero
`sorry`** for the §6.1 verified subset (extended through M_L.4.i, 2026-05-02): atoms, identifiers,
all logical/arithmetic/comparison operators, state-relation membership/cardinality/lookup/subset,
FieldAccess on entity-typed state scalars, single-state `Prime`/`Pre` collapse, and quantifiers over
enums and state-relations.

What this means concretely: when `verify` returns UNSAT for an in-subset obligation, that verdict
reflects a property of the spec — not just a coincidence between the translator and Z3. The Z3
translator (`modules/verify/.../z3/Translator.scala`) is mirrored case-for-case by
`proofs/lean/SpecRest/Translate.lean`; the soundness theorem ties the two via correlation lemmas
between Lean's `eval` and the shallow `smtEval` embedding.

Per-run translation-validation certificates (M_L.3) are emitted on demand:

```bash
sbt "cli/run verify fixtures/spec/safe_counter.spec --emit-cert /tmp/cert"
cd /tmp/cert && lake build  # native-decide each in-subset cert; out-of-subset → trivial stub
```

CI checks all six fixture certs every build (`.github/workflows/lean-certs.yml`).

The remaining out-of-scope shapes (true two-state `Prime`/`Pre` preservation, `With` record-update,
set algebra over set values, `Call` builtins, nested `FieldAccess` on `Index`, strings) emit trivial
stubs with `TODO[M_L.4]` markers. See
[10_translator_soundness.md §13.1](docs/content/docs/research/10_translator_soundness.md) for the
formal claim, full trust closure, and roadmap.

## Subcommands

| Command                                         | Description                                                                                                                                                                                                 |
| ----------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `check <spec>`                                  | Parse and validate spec file structure. Exit 0 if valid.                                                                                                                                                    |
| `inspect -f json \| summary \| ir <spec>`       | Print the IR. `-f json` produces the canonical serialized form used by golden tests.                                                                                                                        |
| `verify <spec>`                                 | Run Z3-backed consistency + invariant-preservation checks. Emits counterexamples on failure. `--dump-smt` prints the SMT-LIB encoding without solver run.                                                   |
| `compile --target <profile> --out <dir> <spec>` | Emit the full target-language service (models, schemas, routers, migrations, OpenAPI spec).                                                                                                                 |
| `compile --with-tests --out <dir> <spec>`       | Additionally emit Hypothesis property tests, strategies, conftest, and a `/__test_admin__` router gated by `ENABLE_TEST_ADMIN`. See [test-generation.mdx](docs/content/docs/pipelines/test-generation.mdx). |

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
  profile/    — Deployment profiles (python-fastapi-postgres), type mapping
  verify/     — IR → SMT translator, Java Z3 backend (z3-turnkey), consistency checker, counterexample decoding, diagnostic formatter
  codegen/    — Handlebars engine (handlebars.java), Emit orchestrator, OpenAPI subsystem, Alembic migration builder
  cli/        — decline-effect CommandIOApp: check / inspect / verify / compile
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
