# Contributing

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
