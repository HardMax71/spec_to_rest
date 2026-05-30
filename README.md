<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/logo-dark.svg" />
    <img src="assets/logo.svg" alt="spec-to-rest" width="540" />
  </picture>
</p>

<h1 align="center">spec-to-rest</h1>

<p align="center">Compile formal behavioral specs (<code>.spec</code> DSL) into verified REST services.</p>

Given a `.spec` file describing entities, operations, invariants, and preconditions, `spec-to-rest`
runs a Z3-backed verification pass against the spec itself and — if verification passes — generates
a complete, runnable service: models, schemas, routers, migrations, Docker / docker-compose /
Makefile / CI workflow, and an OpenAPI 3.1 document. Three target stacks are supported
(Python/FastAPI, Go/chi, TypeScript/Express), each across PostgreSQL, SQLite, and MySQL.

The `verify` command is **mechanically validated in Isabelle/HOL**: a zero-`sorry` soundness
meta-theorem ties the SMT translator to the spec's denotational semantics, and the Scala translator
is code-extracted from that proof rather than hand-written. Every target additionally gets a native
conformance suite in its own language by default (pass `--no-tests` to opt out): pytest +
Hypothesis + Schemathesis / Vitest + fast-check / `go test` + rapid, single-sourced from the spec
with the Python path held byte-identical as a differential oracle.

## Documentation

Full documentation lives under [`docs/content/docs/`](docs/content/docs/) (Fumadocs). Start with
[`index.mdx`](docs/content/docs/index.mdx), then:

- [Install](docs/content/docs/install.mdx) — native binary, Docker image, GitHub Action, build from
  source
- [Spec language reference](docs/content/docs/spec-language.mdx)
- [CLI reference](docs/content/docs/cli.mdx) — every subcommand, flags, exit codes
- [Architecture](docs/content/docs/design/architecture.mdx) — compiler internals and module layout
- [Verification](docs/content/docs/pipelines/verification.mdx) and
  [translator soundness](docs/content/docs/research/10_translator_soundness.md)
- [Test generation](docs/content/docs/pipelines/test-generation.mdx) — native multi-target
  conformance

## License

MIT. See [LICENSE](LICENSE).
