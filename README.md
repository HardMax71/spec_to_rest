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

## Subcommands

| Command                                         | Description                                                                                                                                               |
| ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `check <spec>`                                  | Parse and validate spec file structure. Exit 0 if valid.                                                                                                  |
| `inspect -f json \| summary \| ir <spec>`       | Print the IR. `-f json` produces the canonical serialized form used by golden tests.                                                                      |
| `verify <spec>`                                 | Run Z3-backed consistency + invariant-preservation checks. Emits counterexamples on failure. `--dump-smt` prints the SMT-LIB encoding without solver run. |
| `compile --target <profile> --out <dir> <spec>` | Emit the full target-language service (models, schemas, routers, migrations, OpenAPI spec).                                                               |

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
