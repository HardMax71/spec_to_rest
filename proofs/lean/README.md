# SpecRest Lean Workspace

This is the prover-side sidecar for the global translator-soundness program (see the master doc
`docs/content/docs/research/10_translator_soundness.md`, which now consolidates the former `11`-`15`
global-proof governance/status/profile/runway/activation docs into §12-§16).

The workspace is rooted under `proofs/lean/` and is **not** wired into the Scala SBT build. It runs
through its own Lake build and a separate GitHub Actions workflow (`.github/workflows/lean.yml`).

## Layout

| Path                      | Purpose                                                        |
| ------------------------- | -------------------------------------------------------------- |
| `lean-toolchain`          | Pinned Lean release.                                           |
| `lakefile.toml`           | Library-only Lake config (no mathlib).                         |
| `SpecRest.lean`           | Library root; re-exports the library.                          |
| `SpecRest/IR.lean`        | Deep embedding of the verified-subset `Expr` and IR shells.    |
| `SpecRest/Semantics.lean` | Total `eval : Schema → State → Env → Expr → Option Value`.     |
| `SpecRest/Lemmas.lean`    | Per-operator denotation lemmas (M_L.2 building blocks).        |
| `SpecRest/Smt.lean`       | Shallow SMT-LIB embedding + `smtEval` characterization lemmas. |
| `SpecRest/Translate.lean` | `translate : Expr → SmtTerm` mirroring `z3.Translator.scala`.  |
| `SpecRest/Soundness.lean` | M_L.2 soundness theorem statement + per-case proven theorems.  |
| `SpecRest/Examples.lean`  | Checked lemmas (closed evaluation examples).                   |
| `SpecRest/IR.lean.todo`   | TODO ledger for `Expr` drift in `Types.scala`.                 |
| `STATUS.md`               | Per-`Expr`-case proof-state ledger mirroring §6.1.             |

## Scope (post-M_L.4.a-g)

The library implements the verified subset shipped through M_L.4.a-g:

- propositional ops (`and`, `or`, `implies`, `iff`),
- integer comparisons (`=`, `!=`, `<`, `≤`, `>`, `≥`),
- LIA arithmetic (`+`, `-`, `*`, `/` with `Div`-by-zero `none` policy),
- `Not` / `Negate`,
- state-relation membership (`In`, `NotIn` via emitter-side `¬In` composition),
- state-relation cardinality (`#rel`),
- state-relation indexed lookup (`rel[key]`) over the strictly-additive `lookups` pair table,
- `Let`, `EnumAccess`,
- `Prime` / `Pre` (single-state collapse — true two-state semantics is M_L.4.b-ext),
- universal/existential/no/exists quantifiers over enum **and state-relation** identifiers
  (`Some`/`No`/`Exists` lower to `forallEnum/forallRel + unNot` compositions emitter-side; emit-time
  disambiguation via `ir.enums`),
- `IntLit`, `BoolLit`, `Identifier`.

The universal `soundness` theorem closes for this slice with **zero `sorry`**.

Still **out of scope**: `With`, `FieldAccess`, `Call`, `Matches`, set algebra
(`Subset`/`Union`/`Intersect`/`Diff`), collection literals, strings, true two-state preservation
reasoning. See `STATUS.md` for the full ledger and `IR.lean.todo` for the queued expansions; see
`docs/content/docs/research/10_translator_soundness.md` §14 for the proof-safe profile and §16.3 for
closure status.

## Building

```bash
cd proofs/lean
elan toolchain install "$(cat lean-toolchain)"
elan override set "$(cat lean-toolchain)"
lake build
```

`elan override set` pins the toolchain inside `proofs/lean/` only — it does not change the global
`elan default` toolchain you may have in use for other Lean projects. CI uses
`leanprover/lean-action`, which performs the equivalent project-local install via the pinned
`lean-toolchain` file.

## Avoiding mathlib

The first scaffold deliberately depends only on Lean core. `mathlib4` materially expands toolchain
churn (it can pull the pinned `lean-toolchain` forward through `lake update`) and adds compile cost
that is not justified for the first slice. Add it later only when a specific lemma forces the
choice.

## Audit appendix

The Lean `translate` function in `SpecRest/Translate.lean` mirrors the Scala translator in
`modules/verify/src/main/scala/specrest/verify/z3/Translator.scala`, case-by-case, for the **M_L.1
verified subset** (the §6.1 minimum plus the M_L.1 extras: `Iff` / `Neq` / `Le` / `Gt` / `Ge`).
M_L.2's per-case soundness theorems (`SpecRest/Soundness.lean`) tie the two together via equations
of the shape
`valueToSmt? (eval ...) = smtEval (correlateModel ...) (correlateEnv ...) (translate ...)`.

The first six rows below are `sound` in this PR; the remainder are `translated` and become `sound`
as M_L.2 closure follow-up PRs land their per-case soundness proofs. See `STATUS.md` for the live
per-case proof-state ledger.

| Lean `Expr` constructor       | Scala `Expr` case                           | Lean `translate` output                      | Scala translator (line range, approx)    |
| ----------------------------- | ------------------------------------------- | -------------------------------------------- | ---------------------------------------- |
| `Expr.boolLit b`              | `IExpr.BoolLit(v, _)`                       | `SmtTerm.bLit b`                             | `Translator.scala:588`                   |
| `Expr.intLit n`               | `IExpr.IntLit(v, _)`                        | `SmtTerm.iLit n`                             | `Translator.scala:587`                   |
| `Expr.ident x`                | `IExpr.Identifier(name, _)`                 | `SmtTerm.var x`                              | `Translator.scala:590`                   |
| `Expr.unNot`                  | `IExpr.UnaryOp(Not, _)`                     | `SmtTerm.not (translate ...)`                | unary-op section                         |
| `Expr.unNeg`                  | `IExpr.UnaryOp(Negate, _)`                  | `SmtTerm.neg (translate ...)`                | unary-op section                         |
| `Expr.boolBin .and`           | `IExpr.BinaryOp(And, _, _)`                 | `SmtTerm.and l r`                            | bool-bin section                         |
| `Expr.boolBin .or`            | `IExpr.BinaryOp(Or, _, _)`                  | `SmtTerm.or l r`                             | bool-bin section                         |
| `Expr.boolBin .implies`       | `IExpr.BinaryOp(Implies, _, _)`             | `SmtTerm.implies l r`                        | bool-bin section                         |
| `Expr.boolBin .iff`           | `IExpr.BinaryOp(Iff, _, _)`                 | `SmtTerm.and (.implies l r) (.implies r l)`  | bool-bin section                         |
| `Expr.cmp .eq`                | `IExpr.BinaryOp(Eq, _, _)`                  | `SmtTerm.eq l r`                             | `Translator.scala:1338`                  |
| `Expr.cmp .neq`               | `IExpr.BinaryOp(Neq, _, _)`                 | `SmtTerm.not (.eq l r)`                      | cmp section                              |
| `Expr.cmp .lt`                | `IExpr.BinaryOp(Lt, _, _)`                  | `SmtTerm.lt l r`                             | cmp section                              |
| `Expr.cmp .le`                | `IExpr.BinaryOp(Le, _, _)`                  | `SmtTerm.or (.lt l r) (.eq l r)`             | cmp section                              |
| `Expr.cmp .gt`                | `IExpr.BinaryOp(Gt, _, _)`                  | `SmtTerm.lt r l`                             | cmp section                              |
| `Expr.cmp .ge`                | `IExpr.BinaryOp(Ge, _, _)`                  | `SmtTerm.or (.lt r l) (.eq l r)`             | cmp section                              |
| `Expr.letIn`                  | `IExpr.Let(_, _, _)`                        | `SmtTerm.letIn x v body`                     | let section                              |
| `Expr.enumAccess en mem`      | `IExpr.EnumAccess(name, member, _)`         | `SmtTerm.var (en ++ "." ++ mem)`             | enum-access section                      |
| `Expr.member elem rel`        | `IExpr.BinaryOp(In, elem, ident-rel, _)`    | `SmtTerm.inDom rel (translate elem)`         | `In`-membership section                  |
| `(.unNot (.member elem rel))` | `IExpr.BinaryOp(NotIn, elem, ident-rel, _)` | `SmtTerm.not (.inDom rel (translate elem))`  | `NotIn`-membership section (composition) |
| `Expr.forallEnum`             | `IExpr.Quantifier(All, …)` over enum-named  | `SmtTerm.forallEnum var en (translate body)` | quantifier section                       |
| `Expr.forallRel`              | `IExpr.Quantifier(All, …)` over rel-named   | `SmtTerm.forallRel var rel (translate body)` | quantifier section (state-rel domain)    |
| `Expr.indexRel`               | `IExpr.Index(Identifier(rel), key, _)`      | `SmtTerm.indexRel rel (translate key)`       | `Translator.scala:1009-1018`             |

## References

- Scope and milestone plan: `docs/content/docs/research/10_translator_soundness.md`
- Profile and backend contract: `docs/content/docs/research/10_translator_soundness.md` §14
- Activation record: `docs/content/docs/research/10_translator_soundness.md` §16
- Live status ledger: `docs/content/docs/research/10_translator_soundness.md` §13
