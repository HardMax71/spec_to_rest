# GitHub Workflows & Project Management

Reference for how this repo uses GitHub features. Use `gh` CLI for all operations.

## Repository Settings

- **Default branch:** `main`
- **Merge strategy:** Squash-only (no merge commits, no rebases)
- **Auto-delete branches:** On after merge
- **Branch naming:** Must match `^(feature|fix|docs|chore|refactor)/.+$` (enforced by CI)

## Branch Protection (Ruleset: "Main branch protection")

- PRs required to merge into `main` (no direct push)
- 1 approving review required (repo admin can bypass)
- Stale reviews dismissed on new push
- Review threads must be resolved
- Required status check: `check-branch-name`

## CI Workflows

| Workflow          | File                                | Triggers                           | Purpose                                       |
| ----------------- | ----------------------------------- | ---------------------------------- | --------------------------------------------- |
| Branch name check | `.github/workflows/branch-name.yml` | PR open/sync/reopen/edit           | Validates branch naming convention            |
| Deploy docs       | `.github/workflows/docs.yml`        | Push to `main` (docs/\*\*), manual | Builds Fumadocs site, deploys to GitHub Pages |

## GitHub Pages

- **URL:** https://hardmax71.github.io/spec_to_rest/
- **Source:** GitHub Actions (not branch-based)
- **Build:** `docs/` directory, Next.js static export
- Deploys automatically when `docs/**` changes land on `main`

## Project Board

- **Project:** [spec-to-rest Compiler](https://github.com/users/HardMax71/projects/4)
- **Custom field:** "Phase" (single-select, 7 options matching milestones)
- All implementation issues are tracked here

## Milestones

7 milestones matching the implementation phases:

| #   | Milestone                      | Weeks |
| --- | ------------------------------ | ----- |
| 1   | Phase 1: Parser + IR           | 1-3   |
| 2   | Phase 2: Convention Engine     | 4-6   |
| 3   | Phase 3: Code Generation MVP   | 7-9   |
| 4   | Phase 4: Spec Verification     | 10-12 |
| 5   | Phase 5: Test Generation       | 13-15 |
| 6   | Phase 6: LLM Synthesis         | 16-19 |
| 7   | Phase 7: Multi-Target + Polish | 20-22 |

## Labels

Phase labels (applied to issues):

- `phase-1` through `phase-7` — maps to the corresponding milestone

Standard labels (GitHub defaults): `bug`, `enhancement`, `documentation`, `question`, etc.

## Common gh CLI Operations

### Branches & PRs

```bash
# Create branch
git checkout -b feature/my-feature

# Push and create PR
git push -u origin feature/my-feature
gh pr create --title "Add feature X" --body "## Summary\n- ..."

# Check PR status / reviews
gh pr view 42
gh pr checks 42
gh api repos/HardMax71/spec_to_rest/pulls/42/comments
```

### Issues

```bash
# Create issue with milestone and label
gh issue create --title "M1.1 — ANTLR4 Grammar" \
  --label "phase-1" \
  --milestone "Phase 1: Parser + IR" \
  --body "Description..."

# Close with reference
gh issue close 42 --reason completed
```

### Milestones

```bash
# List milestones
gh api repos/HardMax71/spec_to_rest/milestones --jq '.[] | "\(.number): \(.title) (\(.open_issues) open)"'

# Create milestone
gh api repos/HardMax71/spec_to_rest/milestones --method POST \
  -f title="Phase N: Name" -f description="..."

# Assign issue to milestone
gh issue edit 42 --milestone "Phase 1: Parser + IR"
```

### Project Board

```bash
# List project items
gh project item-list 4 --owner HardMax71

# Add issue to project
gh project item-add 4 --owner HardMax71 --url https://github.com/HardMax71/spec_to_rest/issues/42

# Set custom field on project item
gh project item-edit --project-id "PVT_kwHOA_SQ084BT1_r" \
  --id "<item-id>" --field-id "<field-id>" --single-select-option-id "<option-id>"

# Get field IDs
gh project field-list 4 --owner HardMax71 --format json
```

### Releases (future)

```bash
# Create release from tag
git tag v0.1.0
git push origin v0.1.0
gh release create v0.1.0 --title "v0.1.0" --notes "First release" --generate-notes
```

### Deployments

Docs deploy automatically via the `docs.yml` workflow. To trigger manually:

```bash
gh workflow run docs.yml
```

### Repo Settings

```bash
# View rulesets
gh api repos/HardMax71/spec_to_rest/rulesets

# Update ruleset (e.g., add bypass actor)
gh api repos/HardMax71/spec_to_rest/rulesets/<id> --method PUT --input ruleset.json

# Pages config
gh api repos/HardMax71/spec_to_rest/pages
```
