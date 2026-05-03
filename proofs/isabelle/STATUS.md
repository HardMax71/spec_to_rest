# SpecRest Proof-State Ledger — Isabelle/HOL track

> **Pivot in progress (2026-05-04).** Replaces the Lean track at `proofs/lean/`. Both tracks coexist
> until Isabelle reaches feature parity with Lean's M_L.0 through M_L.4.k + issue #195; at that
> point the Lean track retires (PR #193b).

Mirrors `proofs/lean/STATUS.md`'s row format. A row appears here only after the Isabelle theory
covers the same `expr` constructor at least at `embedded` status; rows propagate through
`mirrored → translated → sound` as the port progresses.

## Status meanings

| Label        | Meaning                                                                     |
| ------------ | --------------------------------------------------------------------------- |
| `embedded`   | The Isabelle `expr` constructor exists; an `eval` / `evalAt` arm covers it. |
| `translated` | `translate` mirrors the Scala translator on this case (pre-soundness).      |
| `mirrored`   | A prover-side mirror exists, awaiting an M_L.2 soundness theorem.           |
| `sound`      | An M_L.2 per-case soundness theorem closes for this constructor.            |
| `deferred`   | Not yet ported from Lean.                                                   |
| `excluded`   | Permanently outside the Z3 global-theorem track.                            |

## 0. Phase progress

| Phase | Status      | Notes                                                                                                                                                                                                                        |
| ----- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0     | **shipped** | `ROOT` + `SpecRest.thy` + `IR.thy`; `isabelle build` clean.                                                                                                                                                                  |
| 1     | **shipped** | `Semantics.thy` (data + `eval` mutual block) + `Smt.thy` (data + `smt_eval` mutual block). `isabelle build` clean.                                                                                                           |
| 2     | **shipped** | `Translate.thy` (`expr → smt_term` total function, all 23 `expr` arms). `isabelle build` clean.                                                                                                                              |
| 3     | not started | `Lemmas.thy` port — per-case soundness scaffolding from `proofs/lean/SpecRest/Lemmas.lean`.                                                                                                                                  |
| 4     | not started | `Soundness.thy` port — the heavy lift; ~5374 LoC of Lean → expected ~6.7 kLoC Isabelle.                                                                                                                                      |
| 5     | **smoked**  | `CodegenSmoke.thy` proves `export_code … in Scala` works end-to-end (1.7 kLoC Scala emitted for `eval/smt_eval/translate`). Productionization (`HOL-Library.Code_Target_Int` for `int → BigInt`, target file path) deferred. |
| 6     | not started | `EmitIsabelle.scala` replacing `cert/Emit.scala`                                                                                                                                                                             |
| 7     | not started | A8a/A8b round-trip oracles                                                                                                                                                                                                   |
| 8     | not started | Drift-audit migration                                                                                                                                                                                                        |
| 9     | not started | Documentation migration                                                                                                                                                                                                      |
| 10    | not started | Lean-track retirement (separate PR)                                                                                                                                                                                          |

### Phase 0-2 lessons

Captured for future ports / for the eventual contributor onboarding doc:

- **`value` is a reserved word** (the `value` command). Renamed to `ir_value`.
- **`dom` shadows `Map.dom`** (partial-function domain). Renamed pattern variables to `rel_dom`.
- **`fields` shadows record auto-generated builder.** Use shorter names like `fs` for pattern vars.
- **`id` shadows `HOL.Fun.id`** (identity function). Renamed to `eid`.
- **Integer literals can't be `fun` patterns** (sequential mode rejects `(VInt 0)`). Use guards
  instead: `(if b = 0 then None else …)`.
- **Mutual `fun ... and ...`** syntax handles termination automatically when recursion is
  structural; matches Lean's mutual blocks one-to-one.

## 1. M_L.1 verified subset port status

| `expr` case                                       | Lean status                    | Isabelle status                    |
| ------------------------------------------------- | ------------------------------ | ---------------------------------- |
| `BoolLit` / `IntLit` / `Ident`                    | `sound`                        | `embedded` (Phase 0; see `IR.thy`) |
| `UnNot` / `UnNeg`                                 | `sound`                        | `embedded`                         |
| `BoolBin` (And/Or/Implies/Iff)                    | `sound`                        | `embedded`                         |
| `Arith` (Add/Sub/Mul/Div)                         | `sound` (M_L.4.a)              | `embedded`                         |
| `Cmp` (Eq/Neq/Lt/Le/Gt/Ge)                        | `sound`                        | `embedded`                         |
| `LetIn`                                           | `sound`                        | `embedded`                         |
| `EnumAccess`                                      | `sound`                        | `embedded`                         |
| `Member`                                          | `sound`                        | `embedded`                         |
| `ForallEnum`                                      | `sound`                        | `embedded`                         |
| `ForallRel`                                       | `sound` (M_L.4.f)              | `embedded`                         |
| `Prime` / `Pre`                                   | `sound` (M_L.4.b-ext Phase 3c) | `embedded`                         |
| `CardRel`                                         | `sound` (M_L.4.c)              | `embedded`                         |
| `IndexRel`                                        | `sound` (M_L.4.g)              | `embedded`                         |
| `FieldAccess`                                     | `sound` (M_L.4.k)              | `embedded`                         |
| `SetEmpty` / `SetInsert` / `SetMember` / `SetBin` | `sound` (issue #195)           | `embedded`                         |
| `WithRec`                                         | `sound` (M_L.4.b-ext Phase 4b) | `embedded`                         |

`embedded` status across the board reflects Phase 0's deliverable: every constructor has its
Isabelle `datatype` arm. Promoting any row to `translated`/`mirrored`/`sound` requires the relevant
Phase 1-4 theory.

## 2. Update rule

Every PR that lands a new `expr` arm or moves a row up the status ladder must update both this file
and the matching row in `proofs/lean/STATUS.md` (during the side-by-side period only — the Lean
STATUS freezes at retirement).
