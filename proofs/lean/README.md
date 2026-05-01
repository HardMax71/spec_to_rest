# SpecRest Lean Workspace

This is the prover-side sidecar for the global translator-soundness program (see
`docs/content/docs/research/10_translator_soundness.md`, `13_global_proof_profile.md`, and
`15_global_proof_activation.md`).

The workspace is rooted under `proofs/lean/` and is **not** wired into the Scala SBT build. It runs
through its own Lake build and a separate GitHub Actions workflow (`.github/workflows/lean.yml`).

## Layout

| Path                      | Purpose                                                |
| ------------------------- | ------------------------------------------------------ |
| `lean-toolchain`          | Pinned Lean release.                                   |
| `lakefile.toml`           | Library-only Lake config (no mathlib).                 |
| `SpecRest.lean`           | Library root; re-exports the library.                  |
| `SpecRest/IR.lean`        | Deep embedding of the Z3-Core-1S `Expr` and IR shells. |
| `SpecRest/Semantics.lean` | Total `eval : Schema → Env → Expr → Option Value`.     |
| `SpecRest/Examples.lean`  | Checked lemmas (closed evaluation examples).           |
| `SpecRest/IR.lean.todo`   | TODO ledger for `Expr` drift in `Types.scala`.         |
| `STATUS.md`               | Per-`Expr`-case proof-state ledger mirroring §6.1.     |

## Scope

The library implements the **`Z3-Core-1S` bootstrap fragment**:

- propositional ops (`and`, `or`, `implies`, `iff`),
- integer comparisons (`=`, `!=`, `<`, `≤`, `>`, `≥`),
- `Not` / `Negate`,
- `Let`,
- `EnumAccess`,
- `IntLit`, `BoolLit`, `Identifier`.

State / `Prime` / `Pre` / `With` / `Cardinality` / quantifiers / collections are intentionally **out
of scope** for this slice; see `STATUS.md` for the full ledger and `IR.lean.todo` for the queued
expansions.

## Building

```bash
cd proofs/lean
elan default $(cat lean-toolchain)
lake build
```

`elan` installs the toolchain pinned in `lean-toolchain`. CI uses `leanprover/lean-action` to do the
same.

## Avoiding mathlib

The first scaffold deliberately depends only on Lean core. `mathlib4` materially expands toolchain
churn (it can pull the pinned `lean-toolchain` forward through `lake update`) and adds compile cost
that is not justified for the first slice. Add it later only when a specific lemma forces the
choice.

## Audit appendix

Each in-scope case in `IR.lean` corresponds to a Scala translator clause in
`modules/verify/src/main/scala/specrest/verify/z3/Translator.scala`. The case-by-case mapping
arrives in `M_L.2` (issue #128); for now the high-level correspondence is:

| Lean (`SpecRest`)                      | Scala (`Translator.scala`)                 |
| -------------------------------------- | ------------------------------------------ |
| `Expr.boolLit`                         | `IExpr.BoolLit` → `Z3Expr.BoolLit`         |
| `Expr.intLit`                          | `IExpr.IntLit` → `Z3Expr.IntLit`           |
| `Expr.ident`                           | `IExpr.Identifier` → `Z3Expr.Var`          |
| `Expr.unNot`                           | `IExpr.UnaryOp(Not, _)` → `Z3Expr.Not`     |
| `Expr.unNeg`                           | `IExpr.UnaryOp(Negate, _)`                 |
| `Expr.boolBin .and/.or/.implies/.iff`  | `IExpr.BinaryOp(And/Or/Implies/Iff, _, _)` |
| `Expr.intCmp .eq/.neq/.lt/.le/.gt/.ge` | `IExpr.BinaryOp(Eq/Neq/Lt/Le/Gt/Ge, _, _)` |
| `Expr.letIn`                           | `IExpr.Let`                                |
| `Expr.enumAccess`                      | `IExpr.EnumAccess`                         |

## References

- Scope and milestone plan: `docs/content/docs/research/10_translator_soundness.md`
- Profile and backend contract: `docs/content/docs/research/13_global_proof_profile.md`
- Activation record: `docs/content/docs/research/15_global_proof_activation.md`
- Live status ledger: `docs/content/docs/research/12_global_proof_status.md`
