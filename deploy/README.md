# Site deploy (docs + playground on Fly.io)

The docs site is a Next.js (standalone) app served by a Fly machine. The same image bundles the
`spec-to-rest` native binary so the `/playground` page's `/api/compile` route can `exec` the CLI
directly — no separate backend, no CORS, no env-var dance.

## Layout

```text
deploy/
  Dockerfile               multi-stage: Next.js standalone + CLI binary
  README.md                this file
.dockerignore              shared exclude list (CLI image + this site image)
fly.toml                   Fly app config (machine size, concurrency, healthcheck)
.github/workflows/deploy-fly.yml
                           CI on PR + push + release; deploys on push + release
docs/                      Next.js source
docs/app/api/compile/      the playground API route (runs spec-to-rest)
docs/app/api/health/       Fly healthcheck target
```

## Triggers

| Event                | What happens                                                   |
| -------------------- | -------------------------------------------------------------- |
| `pull_request`       | `validate` job only: snippet freshness check + Next.js build   |
| `push` to `main`     | `validate` + `deploy` (CLI version = latest published release) |
| `release: published` | `deploy` only, with the new release's tag baked into the image |
| `workflow_dispatch`  | `deploy` with optional `cli_version` input                     |

Releases trigger redeploys automatically — when `release-please` publishes `vX.Y.Z`, the `Site`
workflow fires with that tag, rebuilds the image (CLI binary pulled from
`releases/download/vX.Y.Z/spec-to-rest-linux-amd64.tar.gz`), and Fly does a rolling deploy.

## Playground subcommands

The `/api/compile` route exposes every spec-to-rest subcommand: `check`, `inspect` (summary / IR /
Dafny), `verify`, `compile` (per-framework and per-DB output), and `synth` (bring-your-own LLM key).
Per-target hard limits live in `docs/app/api/compile/route.ts` — currently 50 KB spec, 256 KB
output, and per-target wall-clock caps (8 s for inspect, 60 s for verify, 30 s for compile, 600 s
for synth).

## Building locally

```bash
docker build -f deploy/Dockerfile -t spec-to-rest-site:dev .
docker run --rm -p 8080:8080 spec-to-rest-site:dev
# → http://localhost:8080
```

To pin a specific CLI release in the image:

```bash
docker build -f deploy/Dockerfile \
  --build-arg SPEC_TO_REST_VERSION=v2.1.0 \
  -t spec-to-rest-site:v2.1.0 .
```

## Why one container instead of separate docs + playground services?

Earlier iterations had a separate Go backend on Cloud Run. That required CORS, an env var
(`NEXT_PUBLIC_PLAYGROUND_API`) baked into the docs build, two deploys per release, and a "Backend
not configured" placeholder when the URL was unset. Bundling the CLI into the docs container removes
every line of that machinery: the `/api/compile` route is just another Next.js Route Handler that
shells out via `child_process.spawn`. Same trust boundary, same lifecycle, half the surface area.
