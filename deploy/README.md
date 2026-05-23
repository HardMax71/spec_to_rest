# Site deploy (docs + playground on Fly.io)

The docs site is a Next.js (standalone) app served by a Fly machine. The same image bundles the
`spec-to-rest` native binary so the `/playground` page's `/api/compile` route can `exec` the CLI
directly — no separate backend, no CORS, no env-var dance.

## Layout

```
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

## Initial setup (one-time, manual)

1. **Create the Fly app.** From the repo root:

   ```bash
   fly launch --no-deploy --copy-config --name spec-to-rest --region iad
   ```

   Pick a different `--name` if `spec-to-rest` is already taken on Fly (app names are global).
   Update `fly.toml`'s `app = "..."` to match.

2. **Generate a Fly API token + add as a GitHub secret.**

   ```bash
   fly tokens create deploy --name "github-actions"
   # Output: FlyV1 ...
   ```

   In the repo's GitHub Settings → Secrets and variables → Actions → New repository secret:
   - Name: `FLY_API_TOKEN`
   - Value: the token from `fly tokens create`

   This IS a secret (deploy credentials). The playground backend URL is NOT a secret — it's now
   same-origin and lives in the user's browser address bar, so nothing to configure there.

3. **First deploy** (optional, can also wait for the first `push` to `main`):

   ```bash
   fly deploy --remote-only --build-arg SPEC_TO_REST_VERSION=latest
   ```

4. **Retire GitHub Pages** (optional). With `.github/workflows/docs.yml` removed, Pages will keep
   serving the last-published HTML until you disable it. In repo Settings → Pages → set source to
   "None" if you want the old `hardmax71.github.io/spec_to_rest/` URL to 404.

5. **Custom domain** (optional). Fly assigns `<app>.fly.dev`. To use e.g. `spec-to-rest.io`:

   ```bash
   fly certs add spec-to-rest.io
   # Add the displayed A/AAAA + ACME challenge records at your registrar
   ```

## Cost expectation

- Free tier: 3 × 256 MB shared-cpu VMs are free across the account. This app uses 1 × 512 MB by
  default (`fly.toml`'s `[[vm]]` block), which is ~$2/mo if it stays running 24/7.
- `auto_stop_machines = "stop"` shuts the machine down after idle, so an empty docs site costs
  near-zero. Cold start: ~2-4 s (Node + Next.js standalone boot + first request through CLI binary).
- Hard cap on concurrent requests: `hard_limit = 50` per machine in `fly.toml`. Adjust if you
  outgrow it.

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
