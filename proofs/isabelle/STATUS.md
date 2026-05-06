# SpecRest Proof-State Ledger — Isabelle/HOL track

> **Pivot complete (2026-05-04).** The Lean track at `proofs/lean/` is retired. Isabelle/HOL is the
> canonical proof track. Universal soundness theorem closes with zero sorries; per-run cert
> infrastructure was deleted post-pivot (Option 2 of #193 review — Phase 4 universal soundness made
> it vestigial).

Captures the proof-state of the verified subset and the Phase 0-7 progress through the Isabelle/HOL
pivot (issue #193).

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

| Phase | Status                       | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ----- | ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0     | **shipped**                  | `ROOT` + `SpecRest.thy` + `IR.thy`; `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 1     | **shipped**                  | `Semantics.thy` (data + `eval` mutual block) + `Smt.thy` (data + `smt_eval` mutual block). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                             |
| 2     | **shipped**                  | `Translate.thy` (`expr → smt_term` total function, all 23 `expr` arms). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                |
| 3     | **shipped**                  | `Soundness.thy` ships 94 lemmas/theorems: correlation infra + value_to_smt injectivity + 17 per-case lemmas + 14 binary `*_step` lemmas. Zero sorrys. Build 1m 30s.                                                                                                                                                                                                                                                                                                                                            |
| 4     | **shipped**                  | Universal `soundness` theorem closed via structural induction `(induction e arbitrary: env)` dispatching to per-Expr-shape `*_step` lemmas. All 23 `expr` arms covered. Zero sorrys, no quick_and_dirty.                                                                                                                                                                                                                                                                                                       |
| 5     | **shipped**                  | `Codegen.thy` extracts `translate` + `eval` + `smt_eval` to 1444 LoC of idiomatic Scala 3 (BigInt-mapped via `HOL-Library.Code_Target_Int`, `Code_Target_Numeral`). Records use `[code]`-marked `defs` to bypass the polymorphic-scheme codegen barrier. `value_to_smt` alone remains blocked (separable phantom-type-variable issue, hand-wrapper-able in Phase 6).                                                                                                                                           |
| 6     | **deleted (Option 2)**       | Per-run cert emission deleted. With Phase 4's universal soundness theorem closing every in-subset translation, per-run certs are vestigial. Removed: `cert/Emit.scala` (Lean target, ~558 LoC), `cert/EmitIsabelle.scala` (Isabelle target, ~184 LoC), `cert/EvalIR.scala` (Lean-track Scala mirror of `eval`, ~1052 LoC), test files, the `--emit-cert` CLI flag, and `.github/workflows/lean-certs.yml`. Net delete: ~3.7 kLoC. Trust closure shrinks correspondingly.                                       |
| 7     | **shipped (MVP)**            | `A8RoundTripOracleTest` exercises every in-subset shape end-to-end: extracted `expr_full` → `toExtracted` (hand-written mirror of Isabelle's `lower`) → extracted `expr` (verified subset) → `translate` → `SmtTerm`. 23 in-subset probes translate cleanly (1 out-of-subset skip is honest). `sbt test` 591/591 tests passing. (Post-#202 the input side `expr_full` is itself extracted from Isabelle, so both sides of the test are extracted; only `toExtracted` remains hand-written, mirroring `lower`.) |
| 8     | **deleted (with cert path)** | `ProofDriftAuditTest` (A1-A8 audit, ~445 LoC) was Lean-track-specific and depended on `cert.Emit`. Deleted alongside the cert path. Audit helpers `CanonicalProbes` + `SourceParsers` deleted with it. If a future signal triggers the need for a structural audit between Scala `Expr` and Isabelle `expr`, it will be a fresh test on different foundations (covered by #202 if scheduled).                                                                                                                  |
| 9     | **shipped**                  | Documentation migration: README.md, `docs/research/{06,07,10}_*.md`, `docs/content/docs/{index,pipelines/verification,design/architecture}.mdx`, `proofs/isabelle/{README,STATUS}.md`, and `.github/PULL_REQUEST_TEMPLATE.md` all updated for the post-pivot state. Lean-only references either repointed to Isabelle or marked as historical context.                                                                                                                                                         |
| 10    | **shipped**                  | Lean-track retirement: `proofs/lean/` removed (~9.4 kLoC of Lean across 9 .lean files plus lakefile/manifest/toolchain/.last-release-sha/.cert-sha), `.github/workflows/lean.yml` removed, pre-commit `lake-build` hook replaced with `isabelle-build` hook gating `proofs/isabelle/SpecRest/**/*.thy` edits.                                                                                                                                                                                                  |

### Phase 3 lessons (added in this session)

- **`fun` proof tactics that work for binary operators**:
  `using assms by (cases op; auto split: option.splits smt_val.splits)` — but only when the IH gives
  the smt*eval result *forward* (i.e., `smt_eval ... = Some *`), not *backward* (`Some _ = smt_eval
  ...`). The fix is the `smt_eval_of_eval_\*` helper lemmas: take an IH and a concrete eval result
  and produce the forward-direction smt_eval equation.
- **Per-op success-path lemmas mirror Lean's structure** (`Soundness.lean` lines 596-1894). Each
  takes concrete `eval ... = Some (VInt _)` style hypotheses and closes by
  `using ... helper[OF ... ...] by simp`.
- **`order_le_less` + `eq_commute`** for `≤` and `≥` cmp ops (where the goal reduces to
  `(b ≤ a) = (b < a ∨ a = b)`).
- **`quick_and_dirty = true`** in ROOT permits `sorry` for honest in-progress checkpoints. Drop this
  option once Phase 4 closes everything.
- **`value_to_smt` injectivity** (the load-bearing global lemma) requires careful list-induction due
  to the recursive `VSet "ir_value list"` constructor. Lean's `valueToSmt_inj`
  (Soundness.lean:89-186) is a 100-LoC mutual structural induction; the Isabelle equivalent needs
  either a custom induction principle or the `map_value_to_smt_inj` helper (already shipped) plugged
  into a structured per-case proof. Queued for the next pass.

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
