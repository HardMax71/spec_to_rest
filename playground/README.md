# spec-to-rest playground backend

Thin Go HTTP wrapper around the `spec-to-rest` CLI for the docs-site `/playground` page. Deploys as
a container; tested on Google Cloud Run.

## What it does

Single endpoint, `POST /api/compile`, takes a spec body + target, writes the spec to a temp file,
runs the CLI binary with a hard timeout, returns stdout/stderr as JSON. No state, no auth, no LLM
calls. Rate-limit at the platform layer (Cloud Run max-instances / concurrency, or a Cloudflare
in-front).

```json
POST /api/compile
{"spec": "service ...", "target": "ir"}

→ {"ok": true, "stdout": "...", "stderr": "..."}
```

Allowed `target` values: `check`, `summary`, `ir`, `dafny`. (Multi-file `compile` and Alloy-backed
`verify` deferred to v2 — they need a bigger image and per-output marshalling.)

## Limits

| Knob              | Default                       | Where to change               |
| ----------------- | ----------------------------- | ----------------------------- |
| Max spec size     | 50 KB                         | `maxSpecBytes` in `server.go` |
| Execution timeout | 8 s                           | `execTimeout` in `server.go`  |
| CORS origin       | `*`                           | `CORS_ALLOW_ORIGIN` env var   |
| CLI binary path   | `/usr/local/bin/spec-to-rest` | `SPEC_TO_REST_BIN` env var    |
| Listen port       | 8080                          | `PORT` env var                |

## Build

```bash
docker build -t spec-to-rest-playground:dev .
# Build a different CLI release:
docker build --build-arg SPEC_TO_REST_VERSION=v2.0.0 -t spec-to-rest-playground:v2.0.0 .
```

## Run locally

```bash
docker run --rm -p 8080:8080 spec-to-rest-playground:dev

# Smoke test:
curl -s localhost:8080/healthz
curl -s -X POST localhost:8080/api/compile \
  -H 'content-type: application/json' \
  -d '{"spec":"service Demo { state { count: Int } }","target":"ir"}'
```

## Deploy to Cloud Run

```bash
PROJECT=your-gcp-project
REGION=us-central1
IMAGE=us-central1-docker.pkg.dev/$PROJECT/playground/spec-to-rest-playground:v2.1.0

# Push image (Artifact Registry must already exist):
docker tag spec-to-rest-playground:dev "$IMAGE"
docker push "$IMAGE"

# Deploy:
gcloud run deploy spec-to-rest-playground \
  --image="$IMAGE" \
  --region="$REGION" \
  --allow-unauthenticated \
  --memory=256Mi \
  --cpu=1 \
  --max-instances=10 \
  --concurrency=5 \
  --timeout=15s \
  --set-env-vars=CORS_ALLOW_ORIGIN=https://hardmax71.github.io
```

The deploy command above caps blast radius at ~50 concurrent requests (10 instances × 5 concurrency)
— well within the free tier for typical docs-site traffic, and well below any plausible LLM-style
cost spike (which this image cannot trigger; no LLM calls happen here).

Set the playground URL in the docs build:

```bash
# In docs/.env.local or as a CI env var:
NEXT_PUBLIC_PLAYGROUND_API=https://spec-to-rest-playground-<hash>-uc.a.run.app
```

The docs `/playground` page reads `NEXT_PUBLIC_PLAYGROUND_API` at build time; if unset, the page
renders a "Backend not configured" notice and links back to the install instructions.

## Updating the CLI version

Bump `SPEC_TO_REST_VERSION` in the `docker build` invocation (or in the deploy workflow), rebuild,
push, redeploy. The Dockerfile pins to a tagged release; never tracks `latest` so deploys are
reproducible.

## Why Go for the wrapper?

100 LOC, no deps, ~6 MB static binary, no runtime needed beyond glibc (which the CLI already needs).
Python+FastAPI would add 50-80 MB of interpreter + deps and a slower cold start; Node would be in
the same ballpark. The wrapper does nothing that warrants more than this.
