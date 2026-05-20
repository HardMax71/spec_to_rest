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

| Phase   | Status                       | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 0       | **shipped**                  | `ROOT` + `SpecRest.thy` + `IR.thy`; `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 1       | **shipped**                  | `Semantics.thy` (data + `eval` mutual block) + `Smt.thy` (data + `smt_eval` mutual block). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 2       | **shipped**                  | `Translate.thy` (`expr → smt_term` total function, all 23 `expr` arms). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 3       | **shipped**                  | `Soundness.thy` ships 94 lemmas/theorems: correlation infra + value_to_smt injectivity + 17 per-case lemmas + 14 binary `*_step` lemmas. Zero sorrys. Build 1m 30s.                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 4       | **shipped**                  | Universal `soundness` theorem closed via structural induction `(induction e arbitrary: env)` dispatching to per-Expr-shape `*_step` lemmas. All 23 `expr` arms covered. Zero sorrys, no quick_and_dirty.                                                                                                                                                                                                                                                                                                                                                                                                                 |
| 5       | **shipped**                  | `Codegen.thy` extracts `translate` + `eval` + `smt_eval` to 1444 LoC of idiomatic Scala 3 (BigInt-mapped via `HOL-Library.Code_Target_Int`, `Code_Target_Numeral`). Records use `[code]`-marked `defs` to bypass the polymorphic-scheme codegen barrier. `value_to_smt` alone remains blocked (separable phantom-type-variable issue, hand-wrapper-able in Phase 6).                                                                                                                                                                                                                                                     |
| 6       | **deleted (Option 2)**       | Per-run cert emission deleted. With Phase 4's universal soundness theorem closing every in-subset translation, per-run certs are vestigial. Removed: `cert/Emit.scala` (Lean target, ~558 LoC), `cert/EmitIsabelle.scala` (Isabelle target, ~184 LoC), `cert/EvalIR.scala` (Lean-track Scala mirror of `eval`, ~1052 LoC), test files, the `--emit-cert` CLI flag, and `.github/workflows/lean-certs.yml`. Net delete: ~3.7 kLoC. Trust closure shrinks correspondingly.                                                                                                                                                 |
| 7       | **shipped**                  | `A8RoundTripOracleTest` exercises every in-subset shape end-to-end via extracted `lower → translate → SmtTerm`. Post-#202 close-out: the hand-written `toExtracted` walker is gone — the test calls `lower(enumNames, e)` directly. `lower` v2 covers `QuantifierF` (4 kinds, multi-binding) + multi-field `WithF`; the `enums` parameter disambiguates `ForallEnum` vs `ForallRel`. 24 in-subset probes translate cleanly, 0 skipped. `sbt test` 592/592 tests passing.                                                                                                                                                 |
| 8       | **deleted (with cert path)** | `ProofDriftAuditTest` (A1-A8 audit, ~445 LoC) was Lean-track-specific and depended on `cert.Emit`. Deleted alongside the cert path. Audit helpers `CanonicalProbes` + `SourceParsers` deleted with it. If a future signal triggers the need for a structural audit between Scala `Expr` and Isabelle `expr`, it will be a fresh test on different foundations (covered by #202 if scheduled).                                                                                                                                                                                                                            |
| 9       | **shipped**                  | Documentation migration: README.md, `docs/research/{06,07,10}_*.md`, `docs/content/docs/{index,pipelines/verification,design/architecture}.mdx`, `proofs/isabelle/{README,STATUS}.md`, and `.github/PULL_REQUEST_TEMPLATE.md` all updated for the post-pivot state. Lean-only references either repointed to Isabelle or marked as historical context.                                                                                                                                                                                                                                                                   |
| 10      | **shipped**                  | Lean-track retirement: `proofs/lean/` removed (~9.4 kLoC of Lean across 9 .lean files plus lakefile/manifest/toolchain/.last-release-sha/.cert-sha), `.github/workflows/lean.yml` removed, pre-commit `lake-build` hook replaced with `isabelle-build` hook gating `proofs/isabelle/SpecRest/**/*.thy` edits.                                                                                                                                                                                                                                                                                                            |
| M_L.4.l | **shipped (#210)**           | `IndexRel` carrier widened from `String.literal × expr` to `expr × expr`. New `peel_relation_ref :: expr ⇒ String.literal option` (and SMT mirror `peel_smt_relation_ref`) recognise the three relation-reference shapes (`Ident`, `Pre Ident`, `Prime Ident`); `eval`/`smt_eval`/`translate` peel syntactically (no `ir_value` cascade). `index_rel_step` rewritten with both base and key IHs; universal `soundness` closes with zero sorrys. Unblocks `pre(rel)[k]` and `rel'[k]` shapes (`auth_service`, `broken_url_shortener` Tamper-style operations) — Trust classifier flips them from `BestEffort` to `Sound`. |

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

| `expr` case                                       | Lean status                    | Isabelle status                            |
| ------------------------------------------------- | ------------------------------ | ------------------------------------------ |
| `BoolLit` / `IntLit` / `Ident`                    | `sound`                        | `embedded` (Phase 0; see `IR.thy`)         |
| `UnNot` / `UnNeg`                                 | `sound`                        | `embedded`                                 |
| `BoolBin` (And/Or/Implies/Iff)                    | `sound`                        | `embedded`                                 |
| `Arith` (Add/Sub/Mul/Div)                         | `sound` (M_L.4.a)              | `embedded`                                 |
| `Cmp` (Eq/Neq/Lt/Le/Gt/Ge)                        | `sound`                        | `embedded`                                 |
| `LetIn`                                           | `sound`                        | `embedded`                                 |
| `EnumAccess`                                      | `sound`                        | `embedded`                                 |
| `Member`                                          | `sound`                        | `embedded`                                 |
| `ForallEnum`                                      | `sound`                        | `embedded`                                 |
| `ForallRel`                                       | `sound` (M_L.4.f)              | `embedded`                                 |
| `Prime` / `Pre`                                   | `sound` (M_L.4.b-ext Phase 3c) | `embedded`                                 |
| `CardRel`                                         | `sound` (M_L.4.c)              | `embedded`                                 |
| `IndexRel`                                        | `sound` (M_L.4.g)              | `embedded` (carrier widened, M_L.4.l/#210) |
| `FieldAccess`                                     | `sound` (M_L.4.k)              | `embedded`                                 |
| `SetEmpty` / `SetInsert` / `SetMember` / `SetBin` | `sound` (issue #195)           | `embedded`                                 |
| `WithRec`                                         | `sound` (M_L.4.b-ext Phase 4b) | `embedded`                                 |

`embedded` status across the board reflects Phase 0's deliverable: every constructor has its
Isabelle `datatype` arm. Promoting any row to `translated`/`mirrored`/`sound` requires the relevant
Phase 1-4 theory.

## 2. Update rule

Every PR that lands a new `expr` arm or moves a row up the status ladder must update this file. The
Lean track is retired post-#193, so no Lean status mirror is maintained.

## 3. Open verification gaps (research backlog)

The Isabelle-extraction-dedup program is exhausted (all worthwhile structural duplicates are now
single-sourced from proven-total functions, several with proven properties — `strip_spans`
idempotence, `flatten_and` decomposition, `flatten_entity` parent-less identity). The remaining
opportunities are about _proving more_, not _moving code_, and are scoped as research initiatives,
not incremental PRs:

- **Spec well-formedness / type system (highest value, highest effort).** The universal soundness
  theorem is _conditional_ on `eval e env = Some v` — i.e. it only states translation faithfulness
  where the deep semantics is defined. There is no `wf_expr` / typing judgement and no progress
  theorem (`wf Γ e ⟹ ∃v. eval e env = Some v`), so nothing proves the compiler only ever feeds
  soundness a well-formed, in-subset expression. This is the missing front-half of the soundness
  story. _Phase H1 landed (`Semantics.thy` §Phase 9n):_ value-type ADT `ty`
  (TBool/TInt/TEnum/TEntity/TSet) + inductive value typing `value_has_ty` (infix `::v`) +
  `eval_arith_preservation` + `eval_cmp_preservation`. _Phase H2 landed (Semantics.thy §Phase
  9o-9q + Soundness.thy §Phase 9r):_ lexical typing context `tyenv` + `env_agrees` predicate (9o);
  state-resident-scalar schema `state_schema` + `state_agrees_scalars` + joint `tyctx` +
  `agrees env st Γ` + per-half extraction lemmas (9p); inductive typing relation `expr_has_ty Γ e t`
  over `expr_full` for the arith / cmp / bool fragment (literals, identifier with both lexical-first
  and state-fallback rules, BAdd/BSub/BMul/BDiv, BEq/BNeq, BLt/BLe/BGt/BGe, BAnd/BOr/BImplies/BIff,
  UNot, UNegate) (9q); the H2→9j bridge `well_typed_imp_wf_z3` + corollary
  `well_typed_imp_lower_some` giving the first half of the progress chain
  `well-typed e ⟹ wf_z3 e ⟹ lower enums e ≠ None` (9r). _Phase H3 landed for the arith fragment
  (Soundness.thy §Phase 9s-9u):_ `env_agrees_strict` + extraction lemmas (9s); per-typing-rule
  preservation lemmas — leaves (`h3_pres_BoolLit/IntLit/Ident_Lex/Ident_State`, 9t) and recursive
  (`h3_pres_Not/Neg/Arith/Cmp/Bool_Bin`, 9u); and the umbrella **`h3_preservation`** theorem (9u)
  closing by induction on the typing derivation —
  `expr_has_ty Γ e t ⟹ agrees_strict env st Γ ⟹ lower enums e = Some e' ⟹ eval sch st env e' = Some v ⟹ v ::v t`.
  **Combined with `well_typed_imp_lower_some` the full Cat-H progress chain holds for the arith /
  cmp / bool fragment**: well-typed expression ⟹ lowers ⟹ eval-defined result is typed. A naive
  `free_vars ⊆ dom env` scope-safety lemma was found _false_ (Ident resolves from `state` too) — the
  two-rule `T_Ident_Lex` / `T_Ident_State` typing design + `env_agrees_strict` encodes that. _Phase
  9v extension:_ `T_Let` rule for `LetF x v body` covering local-binding scope, paired with
  `env_agrees_strict_cons` / `agrees_strict_cons` (Semantics.thy) showing that extending env by a
  typed value preserves agreement with the extended context. The umbrella generalises
  `arbitrary: e' v env` so the body-IH instantiates at the extended env `(x, va) # env`. T*Let is
  the first scope-extending rule, validating the design for future binder-introducing rules
  (T_Quantifier, T_With, etc.). \_Phase 9w extension:* three more rules — `T_Prime` and `T_Pre`
  (type-propagating temporal wrappers; eval is the identity, lower wraps with `Prime` / `Pre`
  constructors, the umbrella case delegates to the inner IH after stripping the wrapper) and
  `T_EnumAccess` (the leaf rule for `EnumAccessF (IdentifierF en _) mem` typing as `TEnum en`; the
  preservation case extracts the eval result `VEnum en mem` via the `schema_lookup_enum` /
  `List.member` guard cascade and closes by `vt_enum`). _Phase 9x extension:_ three more relation /
  membership rules — `T_Card` (the leaf rule for `UnaryOpF UCardinality (IdentifierF x _)` typing as
  `TInt`; lowers to `CardRel x`, eval gives `VInt (length rel_dom)` via `state_relation_domain`),
  `T_BIn_Rel` and `T_BNotIn_Rel` (the relation-identifier specialisations of `BIn` / `BNotIn`; the
  LHS is typeable at any `t`, the RHS is syntactically pinned to `IdentifierF rel _` matching
  `lower`'s relation arm which yields `Member l' rel sp` resp. `UnNot (Member l' rel sp) sp` — both
  `TBool`). _Phase 9y extension:_ set-literal typing — `T_SetLit_Empty` (polymorphic leaf
  `SetLiteralF [] : TSet t` for any `t`; lowers to `SetEmpty`, evaluates to `VSet []` whose typing
  is vacuous via `vt_set`) and `T_SetLit_Cons` (inductive
  `e : t ∧ SetLiteralF rest : TSet t ⟹ SetLiteralF (e # rest) : TSet t`; lowers via `lower_set_list`
  to `SetInsert eL sL`, eval dedupes `va # rest_vs`). Supporting lemmas in Semantics.thy:
  `set_dedupe_values_subset` (the subset law for `dedupe_values`, by `Cons` structural induction
  with `insert v` upper-bound step) and `dedupe_values_preserves_value_ty` (the typing-preservation
  corollary). _Phase 9z extension:_ three set-binary-op rules — `T_BUnion`, `T_BIntersect`,
  `T_BDiff` (all `(TSet t, TSet t) ⟹ TSet t`; lower to `SetBin UnionOp / IntersectOp / DiffOp`,
  evaluate via `eval_set_bin`). Supporting inversion lemma `eval_set_bin_some_imp_set`
  (`eval_set_bin op x y = Some v ⟹ both args are Some (VSet _)`, proven by `eval_set_bin.induct`)
  lets each umbrella case extract `lv`, `rv` cleanly; per-op result-shape closes by simp. Three
  element-preservation lemmas (`set_union_values_preserves_value_ty` and the intersect / diff
  analogues) lift the `dedupe_values_subset` law through `@` / `filter` to give `∀v. v ::v t` on the
  result. _Phase 9aa extension:_ set-RHS membership — `T_BIn_Set` / `T_BNotIn_Set`. Both rules carry
  a syntactic disjointness premise `∀rel s. r ≠ IdentifierF rel s` so they don't overlap with
  `T_BIn_Rel` / `T_BNotIn_Rel` on the IdentifierF shape (which routes to `Member` / `UnNot Member`
  instead of `SetMember`). The umbrella case uses `cases r` over `expr_full` to fan out one subgoal
  per ctor; the IdentifierF subgoal is contradicted by the disjointness hyp, the remaining catch-all
  ctors collapse via `auto split: option.splits` extracting `e' = SetMember l' r' sp` (resp.
  `UnNot (SetMember l' r' sp) sp` for the not variant). The eval extraction is then standard
  `ir_value.splits` to obtain `Some (VSet rv)` for the RHS; the result is
  `VBool (contains_value rv vl)`, closed by `vt_bool`. No IH dependency — the eval shape determines
  the type directly. The umbrella now covers 23 typing rules. **Remaining (next sessions):**
  quantifier / index / with / field-access typing rules + relation / entity-field schema typing.
  Each new constructor is one typing rule + one umbrella case — mechanical from here.
- **`wf_z3` syntactic subset proven sufficient for `lower`** (`Soundness.thy` §Phase 9j, dual of
  9i): a syntactic predicate `wf_z3` carves out the Z3-verifiable fragment of `expr_full` and the
  capstone `wf_z3_imp_lower_some` proves `wf_z3 e ⟹ lower enums e ≠ None`. This upgrades
  `Trust.classify`'s runtime oracle (`lower(e).isDefined ⟹ Sound`) to a proven syntactic guarantee:
  in-fragment specs are Sound, never best-effort. The two side-conditions baked into the predicate
  are the `peel_relation_ref` shape for `IndexF` bases and the `lower_forall_bindings` totality
  condition for `QuantifierF` bindings. Mirrors the size-induction backbone of 9i exactly; build
  stays ~3 min.
- **`flatten_and` structural preservation** (§Phase 9l): `flatten_and_nonempty`,
  `flatten_and_requires_alloy_iff`, `flatten_and_wf_z3_iff`. The decomposition of a
  conjunction-of-invariants preserves both the Alloy routing decision (any conjunct triggers Alloy)
  and the `wf_z3` in-fragment guarantee (all conjuncts in-fragment). Full semantic preservation
  needs `expr_full` semantics (= Cat-H), out of scope here.
- **IR enum codec round-trips** (`IR.thy` §Phase 9m, proof-only): `bin_op_to_ts_inverse`,
  `un_op_to_ts_inverse`, `quant_kind_to_ts_inverse`, `multiplicity_to_ts_inverse`,
  `binding_kind_to_ts_inverse`. `bin_op_to_ts` (already extracted) and the four new sibling encoders
  use the exact string tokens of `modules/ir/.../Serialize.scala`, so their decoders + round-trip
  theorems unlock the Phase-9m extraction PR that replaces those circe codecs with calls to
  extracted `SpecRestGenerated.*` functions. The full `expr_full`/`type_expr_full` codec is the next
  size-induction proof.
- **Alloy backend unverified.** Only the router `requires_alloy` is proven. The `expr_full ⇒ Alloy`
  translation and Alloy semantics have no Isabelle analog — a full second trusted backend. A
  `translate_alloy` + Alloy-semantics + soundness theorem mirrors the entire Z3 effort.
  **Routing-disjointness RESOLVED (`Soundness.thy` §Phase 9i):** the full theorem
  `requires_alloy e ⟹ lower enums e = None` (with the `lower_set_list / lower_with_assigns`
  companions) is now proven as `requires_alloy_imp_lower_none` — the Z3-routed and Alloy-routed
  expression fragments are provably disjoint. The blocker was the induction principle, not proof
  difficulty: the `function`-derived `lower.induct` (computation induction) sequentially splits the
  nested `case op of …` inside `BinaryOpF` (20-way) / `UnaryOpF` / `QuantifierF` into dozens of
  op-guarded partial subgoals whose IHs no longer match the clean collapse lemmas; blanket
  `simp_all`/`auto` over that exploded set does not terminate within budget (>15 min, confirmed via
  `parallel_proofs=0` + per-region `Timing` instrumentation — the diagnostic showed every other
  region is ~seconds and the cost is wholly the monolithic `simp_all` over the op-exploded
  induction). Standard remedy (Krauss functions manual, pattern-splitting makes generated induction
  rules too specific): do not induct on the function's recursion —
  `requires_alloy_imp_lower_none_expr` uses well-founded `size` induction (`measure_induct_rule`) +
  plain `cases e` (op is non-recursive, never split → ~25 clean cases vs ~60 op-exploded), each arm
  delegating to its self-contained collapse lemma with the size-IH; the two list-recursive
  constructors go through standalone list lemmas (`lower_set_list_ra_none`,
  `lower_with_assigns_ra_none`) parameterised by a per-element hypothesis (no circularity).
  Whole-session build stays ~2.5 min. The remaining Cat-I gap is only the full `expr_full ⇒ Alloy`
  translation + Alloy semantics (a second trusted backend), unrelated to routing disjointness.
- **`flatten_inheritance` non-idempotence (proof landed, Scala rewire pending).** v1 retains the
  parent ref and concatenates inherited invariants → second application duplicates. Harmless today
  (`buildIRCore` applies it once; locked by `parser.FlattenInheritanceTest`). **Phase 9k:** the
  hardened `flatten_inheritance2` (which clears the parent ref) is now proven idempotent
  (`flatten_inheritance2_idem`) and equivalent to v1 on parentless input
  (`flatten_inheritance2_eq_on_parentless`). The Scala-side switch (`parser.Builder.buildIRCore` →
  v2, plus `lint.UnusedEntity` reachability + IR-JSON `extends_` adjustments) is the deferred
  validated PR — now riding on proof rather than a risk argument.
