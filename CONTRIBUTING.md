# Contributing

## Branch names and PR titles

Both are gated by the required `check-branch-name` job (`.github/workflows/branch-name.yml`,
token-free):

- **Branch:** `<type>/<slug>`, e.g. `refactor/decouple-model-schema`.
- **PR title:** a [Conventional Commit](https://www.conventionalcommits.org/), e.g.
  `refactor: decouple model from schema`.

`<type>` is one of `feat`, `fix`, `docs`, `chore`, `refactor`, `perf`, `ci`, `build`, `test`,
`style`, `revert` (`feature/` is accepted as an alias of `feat`). The repo squash-merges with the
**PR title as the commit subject**, and release-please builds `CHANGELOG.md` from those
Conventional-Commit subjects, so a non-conventional title is silently dropped from the release
notes. `feat` / `fix` / `refactor` / `perf` / `docs` are surfaced in the changelog; `chore` / `ci` /
`build` / `test` / `style` go to hidden sections. Bot branches (`dependabot/...`,
`release-please--...`) are exempt.

## Architecture enforcement

The module dependency graph in `build.sbt` (`dependsOn`) _is_ the architecture — an illegal
cross-module import simply won't compile. On top of that, [`modules/arch`](modules/arch) is a
test-only module whose [ArchUnit](https://www.archunit.org/) test (`ArchitectureTest`) turns the
architecture into an **explicit, executable spec** and catches what the build graph alone can't.

`sbt arch/test` asserts, against every module's compiled bytecode:

- **module layering** — a `layeredArchitecture` mirroring the `dependsOn` graph (each module may
  only be accessed by its declared dependents);
- **package-level cycle freedom** (sbt only forbids _module_ cycles);
- **`verify`** (the trusted soundness core) depends on no downstream layer;
- **`convention`** does not reach into downstream layers (so it can't re-accrete into a grab-bag).

**When you add a module or change a `dependsOn`, update the `layeredArchitecture` rule in
`ArchitectureTest.scala`** (the `.layer(...)` definitions and the `.mayOnlyBeAccessedByLayers(...)`
lists), or `arch/test` will fail. Those lists are **production** dependents only: the test analyzes
main bytecode (`DO_NOT_INCLUDE_TESTS`), so a test-scope dependency (e.g. `convention % Test`) is
_not_ an accessor and must not be listed.

## Dependency updates

Two bots own the update PR stream, split by ecosystem:

- **Dependabot** (`.github/dependabot.yml`) — GitHub Actions and the `/docs` npm workspace. Weekly
  cadence, grouped per ecosystem.
- **scala-steward** (`.scala-steward.conf`, `.github/workflows/scala-steward.yml`) — sbt:
  `build.sbt` library deps, `project/plugins.sbt`, and `project/build.properties`. Weekly cron
  (Mondays, 08:00 UTC) plus `workflow_dispatch` for ad-hoc runs.

The domains don't overlap (Dependabot never sees sbt; scala-steward never sees GHA), so the two-bot
setup doesn't produce duplicate PRs. The decision and trade-off vs Renovate is recorded on issue
#163.

### Grouping rationale

Three sbt families are grouped so a single PR bumps all sibling artifacts together:

- **circe** (`io.circe:*`) — `circe-core`, `circe-generic`, `circe-parser` share a version pin
  (`circeVersion` in `build.sbt`); split bumps wedge compilation.
- **alloy** (`org.alloytools:*`) — `alloy.application`, `alloy.core`, `pardinus.core`,
  `pardinus.native` likewise share `alloyVersion` and ship as one upstream release.
- **munit** (artifact `munit*`) — `munit` and `munit-cats-effect` are separate group IDs but version
  in lockstep with Cats Effect / munit releases; grouping by artifact glob covers both.

Pre-releases (`-RC`, `-M`, `-alpha`, `-beta`) are filtered out — we only track stable.

### Token

scala-steward runs with the workflow's default `GITHUB_TOKEN`. Trade-off: PRs created by
`GITHUB_TOKEN` do **not** trigger downstream workflows (GitHub's loop-protection rule). Concretely,
when a steward PR lands, the `CI`, `native`, and `quality` workflows will not run automatically on
it.

To run CI on a steward PR, the reviewer pushes an empty commit (or closes and reopens the PR):

```bash
gh pr checkout <PR-number>
git commit --allow-empty -m "ci: trigger"
git push
```

The trade-off is intentional: this avoids provisioning a fine-grained PAT or GitHub App for a
low-volume bot. If the manual step becomes annoying, swap `secrets.GITHUB_TOKEN` for a fine-grained
PAT (`contents:write` + `pull-requests:write`, scoped to this repo).
