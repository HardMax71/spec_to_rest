# SpecRest Proof-State Ledger: Isabelle/HOL track

> **Pivot complete (2026-05-04).** The Lean track at `proofs/lean/` is retired. Isabelle/HOL is the
> canonical proof track. Universal soundness theorem closes with zero sorries; per-run cert
> infrastructure was deleted post-pivot (Option 2 of #193 review: Phase 4 universal soundness made
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

| Phase   | Status                       | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 0       | **shipped**                  | `ROOT` + `SpecRest.thy` + `IR.thy`; `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 1       | **shipped**                  | `Semantics.thy` (data + `eval` mutual block) + `Smt.thy` (data + `smt_eval` mutual block). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2       | **shipped**                  | `Translate.thy` (`expr → smt_term` total function, all 23 `expr` arms). `isabelle build` clean.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| 3       | **shipped**                  | `Soundness.thy` ships 94 lemmas/theorems: correlation infra + value_to_smt injectivity + 17 per-case lemmas + 14 binary `*_step` lemmas. Zero sorrys. Build 1m 30s.                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| 4       | **shipped**                  | Universal `soundness` theorem closed via structural induction `(induction e arbitrary: env)` dispatching to per-Expr-shape `*_step` lemmas. All 23 `expr` arms covered. Zero sorrys, no quick_and_dirty.                                                                                                                                                                                                                                                                                                                                                                                                                |
| 5       | **shipped**                  | `Codegen.thy` extracts `translate` + `eval` + `smt_eval` to idiomatic Scala 3 (BigInt-mapped via `HOL-Library.Code_Target_Int`, `Code_Target_Numeral`). Records use `[code]`-marked `defs` to bypass the polymorphic-scheme codegen barrier. `value_to_smt` alone remains blocked (separable phantom-type-variable issue, hand-wrapper-able in Phase 6).                                                                                                                                                                                                                                                                |
| 6       | **deleted (Option 2)**       | Per-run cert emission deleted. With Phase 4's universal soundness theorem closing every in-subset translation, per-run certs are vestigial. Removed: `cert/Emit.scala` (Lean target, ~558 LoC), `cert/EmitIsabelle.scala` (Isabelle target, ~184 LoC), `cert/EvalIR.scala` (Lean-track Scala mirror of `eval`, ~1052 LoC), test files, the `--emit-cert` CLI flag, and `.github/workflows/lean-certs.yml`. Net delete: ~3.7 kLoC. Trust closure shrinks correspondingly.                                                                                                                                                |
| 7       | **shipped**                  | `A8RoundTripOracleTest` exercises every in-subset shape end-to-end via extracted `lower → translate → SmtTerm`. Post-#202 close-out: the hand-written `toExtracted` walker is gone. The test calls `lower(enumNames, e)` directly. `lower` v2 covers `QuantifierF` (4 kinds, multi-binding) + multi-field `WithF`; the `enums` parameter disambiguates `ForallEnum` vs `ForallRel`. 24 in-subset probes translate cleanly, 0 skipped. `sbt test` 592/592 tests passing.                                                                                                                                                 |
| 8       | **deleted (with cert path)** | `ProofDriftAuditTest` (A1-A8 audit, ~445 LoC) was Lean-track-specific and depended on `cert.Emit`. Deleted alongside the cert path. Audit helpers `CanonicalProbes` + `SourceParsers` deleted with it. If a future signal triggers the need for a structural audit between Scala `Expr` and Isabelle `expr`, it will be a fresh test on different foundations (covered by #202 if scheduled).                                                                                                                                                                                                                           |
| 9       | **shipped**                  | Documentation migration: the README, the research and pipeline docs, the proof READMEs, and the PR template all updated for the post-pivot state. Lean-only references either repointed to Isabelle or marked as historical context.                                                                                                                                                                                                                                                                                                                                                                                    |
| 10      | **shipped**                  | Lean-track retirement: `proofs/lean/` removed (~9.4 kLoC of Lean across 9 .lean files plus lakefile/manifest/toolchain/.last-release-sha/.cert-sha), `.github/workflows/lean.yml` removed, pre-commit `lake-build` hook replaced with `isabelle-build` hook gating `proofs/isabelle/SpecRest/**/*.thy` edits.                                                                                                                                                                                                                                                                                                           |
| M_L.4.l | **shipped (#210)**           | `IndexRel` carrier widened from `String.literal × expr` to `expr × expr`. New `peel_relation_ref :: expr ⇒ String.literal option` (and SMT mirror `peel_smt_relation_ref`) recognise the three relation-reference shapes (`Ident`, `Pre Ident`, `Prime Ident`); `eval`/`smt_eval`/`translate` peel syntactically (no `ir_value` cascade). `index_rel_step` rewritten with both base and key IHs; universal `soundness` closes with zero sorrys. Unblocks `pre(rel)[k]` and `rel'[k]` shapes (`auth_service`, `broken_url_shortener` Tamper-style operations). Trust classifier flips them from `BestEffort` to `Sound`. |

### Phase 3 lessons (added in this session)

- `fun` proof tactics that work for binary operators:
  `using assms by (cases op; auto split: option.splits smt_val.splits)`, but only when the IH gives
  the `smt_eval` result _forward_ (i.e., `smt_eval ... = Some _`), not _backward_
  (`Some _ = smt_eval ...`). The fix is the `smt_eval_of_eval_*` helper lemmas: take an IH and a
  concrete eval result and produce the forward-direction `smt_eval` equation.
- Per-op success-path lemmas mirror Lean's structure (`Soundness.lean` lines 596-1894). Each takes
  concrete `eval ... = Some (VInt _)` style hypotheses and closes by
  `using ... helper[OF ... ...] by simp`.
- `order_le_less` + `eq_commute` for `≤` and `≥` cmp ops (where the goal reduces to
  `(b ≤ a) = (b < a ∨ a = b)`).
- `quick_and_dirty = true` in ROOT permits `sorry` for honest in-progress checkpoints. Drop this
  option once Phase 4 closes everything.
- `value_to_smt` injectivity (the load-bearing global lemma) requires careful list-induction due to
  the recursive `VSet "ir_value list"` constructor. Lean's `valueToSmt_inj` (Soundness.lean:89-186)
  is a 100-LoC mutual structural induction; the Isabelle equivalent needs either a custom induction
  principle or the `map_value_to_smt_inj` helper (already shipped) plugged into a structured
  per-case proof. Queued for the next pass.

### Phase 0-2 lessons

Captured for future ports / for the eventual contributor onboarding doc:

- `value` is a reserved word (the `value` command). Renamed to `ir_value`.
- `dom` shadows `Map.dom` (partial-function domain). Renamed pattern variables to `rel_dom`.
- `fields` shadows record auto-generated builder. Use shorter names like `fs` for pattern vars.
- `id` shadows `HOL.Fun.id` (identity function). Renamed to `eid`.
- Integer literals can't be `fun` patterns (sequential mode rejects `(VInt 0)`). Use guards instead:
  `(if b = 0 then None else …)`.
- Mutual `fun ... and ...` syntax handles termination automatically when recursion is structural;
  matches Lean's mutual blocks one-to-one.

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
single-sourced from proven-total functions, several with proven properties: `stripSpans`
idempotence, `flattenAnd` decomposition, `flatten_entity` parent-less identity). The remaining
opportunities are about _proving more_, not _moving code_, and are scoped as research initiatives,
not incremental PRs:

- Spec well-formedness / type system (highest value, highest effort). The universal soundness
  theorem is _conditional_ on `eval e env = Some v`, i.e. it only states translation faithfulness
  where the deep semantics is defined. There is no `wf_expr` / typing judgement and no progress
  theorem (`wf Γ e ⟹ ∃v. eval e env = Some v`), so nothing proves the compiler only ever feeds
  soundness a well-formed, in-subset expression. This is the missing front-half of the soundness
  story. _Phase H1 landed (`Semantics.thy` §Phase 9n):_ value-type ADT `ty`
  (TBool/TInt/TEnum/TEntity/TSet) + inductive value typing `value_has_ty` (prefix
  `value_has_ty Γ v t`; previously infix `::v` pre-Phase-9ww-followup, see below) +
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
  preservation lemmas: leaves (`h3_pres_BoolLit/IntLit/Ident_Lex/Ident_State`, 9t) and recursive
  (`h3_pres_Not/Neg/Arith/Cmp/Bool_Bin`, 9u); and the umbrella **`h3_preservation`** theorem (9u)
  closing by induction on the typing derivation:
  `expr_has_ty Γ e t ⟹ agrees_strict env st Γ ⟹ lower enums e = Some e' ⟹ eval sch st env e' = Some v ⟹ v ::v t`.
  **Combined with `well_typed_imp_lower_some` the full Cat-H progress chain holds for the arith /
  cmp / bool fragment**: well-typed expression ⟹ lowers ⟹ eval-defined result is typed. A naive
  `free_vars ⊆ dom env` scope-safety lemma was found _false_ (Ident resolves from `state` too). The
  two-rule `T_Ident_Lex` / `T_Ident_State` typing design + `env_agrees_strict` encodes that. _Phase
  9v extension:_ `T_Let` rule for `LetF x v body` covering local-binding scope, paired with
  `env_agrees_strict_cons` / `agrees_strict_cons` (Semantics.thy) showing that extending env by a
  typed value preserves agreement with the extended context. The umbrella generalises
  `arbitrary: e' v env` so the body-IH instantiates at the extended env `(x, va) # env`. `T_Let` is
  the first scope-extending rule, validating the design for future binder-introducing rules
  (`T_Quantifier`, `T_With`, etc.). _Phase 9w extension:_ three more rules: `T_Prime` and `T_Pre`
  (type-propagating temporal wrappers; eval is the identity, lower wraps with `Prime` / `Pre`
  constructors, the umbrella case delegates to the inner IH after stripping the wrapper) and
  `T_EnumAccess` (the leaf rule for `EnumAccessF (IdentifierF en _) mem` typing as `TEnum en`; the
  preservation case extracts the eval result `VEnum en mem` via the `schema_lookup_enum` /
  `List.member` guard cascade and closes by `vt_enum`). _Phase 9x extension:_ three more relation /
  membership rules: `T_Card` (the leaf rule for `UnaryOpF UCardinality (IdentifierF x _)` typing as
  `TInt`; lowers to `CardRel x`, eval gives `VInt (length rel_dom)` via `state_relation_domain`),
  `T_BIn_Rel` and `T_BNotIn_Rel` (the relation-identifier specialisations of `BIn` / `BNotIn`; the
  LHS is typeable at any `t`, the RHS is syntactically pinned to `IdentifierF rel _` matching
  `lower`'s relation arm which yields `Member l' rel sp` resp. `UnNot (Member l' rel sp) sp`, both
  `TBool`). _Phase 9y extension:_ set-literal typing: `T_SetLit_Empty` (polymorphic leaf
  `SetLiteralF [] : TSet t` for any `t`; lowers to `SetEmpty`, evaluates to `VSet []` whose typing
  is vacuous via `vt_set`) and `T_SetLit_Cons` (inductive
  `e : t ∧ SetLiteralF rest : TSet t ⟹ SetLiteralF (e # rest) : TSet t`; lowers via `lower_set_list`
  to `SetInsert eL sL`, eval dedupes `va # rest_vs`). Supporting lemmas in Semantics.thy:
  `set_dedupe_values_subset` (the subset law for `dedupe_values`, by `Cons` structural induction
  with `insert v` upper-bound step) and `dedupe_values_preserves_value_ty` (the typing-preservation
  corollary). _Phase 9z extension:_ three set-binary-op rules: `T_BUnion`, `T_BIntersect`, `T_BDiff`
  (all `(TSet t, TSet t) ⟹ TSet t`; lower to `SetBin UnionOp / IntersectOp / DiffOp`, evaluate via
  `eval_set_bin`). Supporting inversion lemma `eval_set_bin_some_imp_set`
  (`eval_set_bin op x y = Some v ⟹ both args are Some (VSet _)`, proven by `eval_set_bin.induct`)
  lets each umbrella case extract `lv`, `rv` cleanly; per-op result-shape closes by simp. Three
  element-preservation lemmas (`set_union_values_preserves_value_ty` and the intersect / diff
  analogues) lift the `dedupe_values_subset` law through `@` / `filter` to give `∀v. v ::v t` on the
  result. _Phase 9aa extension:_ set-RHS membership: `T_BIn_Set` / `T_BNotIn_Set`. Both rules carry
  a syntactic disjointness premise `∀rel s. r ≠ IdentifierF rel s` so they don't overlap with
  `T_BIn_Rel` / `T_BNotIn_Rel` on the IdentifierF shape (which routes to `Member` / `UnNot Member`
  instead of `SetMember`). The umbrella case uses `cases r` over `expr_full` to fan out one subgoal
  per ctor; the IdentifierF subgoal is contradicted by the disjointness hyp, the remaining catch-all
  ctors collapse via `auto split: option.splits` extracting `e' = SetMember l' r' sp` (resp.
  `UnNot (SetMember l' r' sp) sp` for the not variant). The eval extraction is then standard
  `ir_value.splits` to obtain `Some (VSet rv)` for the RHS; the result is
  `VBool (contains_value rv vl)`, closed by `vt_bool`. No IH dependency: the eval shape determines
  the type directly. _Phase 9bb extension:_ schema enrichment + `T_FieldAccess`. The `tyctx` record
  gains a third field `tc_entities :: entity_decl list` (the per-spec entity-decl schema). New
  primitives: `type_expr_to_ty` (partial translation `BoolT / IntT / EnumT / EntityT` → `ty`;
  `RelationT _ _` ⇒ `None` so relation-typed fields are not in the H3 chain),
  `schema_field_type entities ename fname` (twin-`List.find` cascade over entity decls then field
  decls, lifted through `type_expr_to_ty`), `entity_field_well_typed entities st` (the semantic
  state-typing invariant: for every value typed at `TEntity ename` and every schema-declared field,
  `value_field_lookup` returns a value of the declared type; covers both `VEntity` records keyed by
  `eid` in state and `VEntityWith` overrides). `agrees_strict` gains a third conjunct asserting
  `entity_field_well_typed (tc_entities Γ) st`; the existing `agrees_strict_cons` still closes
  because record updates touching only `tc_env` preserve `tc_entities`. New helper
  `agrees_strict_field_lookup` is the clean extractor used by the umbrella. `T_FieldAccess` fires
  when `base : TEntity ename` and `schema_field_type` lookup succeeds; the umbrella case decomposes
  lower to `FieldAccess b' fname sp`, threads eval through `value_field_lookup`, and closes via
  `agrees_strict_field_lookup` (no per-rule preservation lemma needed). _Phase 9cc extension:_
  relation-schema enrichment + `T_Index`. `tyctx` gains a fourth field
  `tc_relations :: state_relation list`. New primitives: `schema_relation_value_type`,
  `relation_value_well_typed` (semantic invariant: every `state_lookup_key` result matches the
  declared `sr_value` type), and `peel_relation_ref_full :: expr_full ⇒ String.literal option` (the
  spec-level analogue of `peel_relation_ref` recognising the three `rel_ref_shape` arms: bare ident,
  pre-ident, prime-ident). Two bridge lemmas in Soundness.thy connect these:
  `peel_relation_ref_full_some_imp_rel_ref_shape` (for `well_typed_imp_wf_z3`) and
  `peel_relation_ref_full_lower` (for the umbrella case: lowering preserves the peeled rel name).
  The `T_Index` rule fires when `peel_relation_ref_full base = Some rel_name`,
  `expr_has_ty Γ key tk` (for any key type), and `schema_relation_value_type` succeeds. The umbrella
  case decomposes lower to `IndexRel base' key' sp`, lifts the peeled name through lowering,
  extracts the inner key eval + `state_lookup_key`, and closes via the new
  `agrees_strict_relation_lookup` extractor. _Phase 9dd extension:_ `T_With` (entity update). The
  typing rule uses a universal-set-membership premise,
  `(∀fld v sp'. FieldAssignFull fld v sp' ∈ set updates ⟶ ∃ft. schema_field_type … ename fld = Some ft ∧ expr_has_ty Γ v ft)`,
  which avoids mutual induction with a separate `with_assigns_well_typed` predicate: the inductive
  package generates a per-update IH automatically (each `v` in updates gets its own preservation IH
  at its declared field type `ft`). Three helper lemmas in Soundness.thy: `wf_z3_fields_iff` (the
  equivalence `wf_z3_fields updates ↔ ∀(_, v, _) ∈ updates. wf_z3 v`, cheaper than the mutual
  `.induct` which doesn't exist standalone in the `wf_z3` / `wf_z3_list` / `wf_z3_fields` mutual
  group); `lower_with_assigns_eval_implies_base_eval` (the chain's outer eval succeeds ⟹ the base's
  eval also succeeds, used to discharge the IH(1) base-eval premise);
  `lower_with_assigns_preserves_entity` (the chain preserves `TEntity ename`, by structural
  induction on updates, each `WithRec` wrap re-applies `vt_entity_with` to preserve the entity type;
  post-Phase-9ww-followup the rule now requires the override be typed at the declared field type, so
  the lemma takes a per-update `updates_typed` premise threaded from `T_With.IH(2)`). The umbrella
  case composes the three helpers and closes via the tightened `vt_entity_with`.

  _Phase 9ww-followup (PR #286):_ tightened `vt_entity_with` per the H3-blocker raised by
  coderabbitai on PR #285. Old rule:
  `base ::v TEntity ename ⟹ VEntityWith base fld override ::v TEntity ename` (override
  unconstrained), which let a derivation type any garbage value as an entity field override and so
  `entity_field_well_typed Γ st` was unprovable for non-TInt schemas. New rule:
  `value_has_ty Γ base (TEntity ename) ⟹ schemaFieldType Γ ename fld = Some ft ⟹ value_has_ty Γ override ft ⟹ value_has_ty Γ (VEntityWith base fld override) (TEntity ename)`.
  Required parameterising `value_has_ty` by `tyctx` (the override-typing premise consults
  `schemaFieldType Γ`), dropping the infix `::v`, and threading `Γ` through `env_agrees`,
  `state_agrees_scalars`, `env_agrees_strict` and their tc_env-update simp companions.
  `lower_with_assigns_preserves_entity` took the per-update typing premise (a universally quantified
  IH-shaped predicate), and the H3 `T_With` case feeds it from `T_With.IH(2)`. ~100 references
  rewritten; Isabelle build 5:49 → 6:02, no Scala-side diff.

  _Phase 9ww-followup runtime integration (PR #287):_ `value_has_ty` made executable via mutual-fun
  `check_value_has_ty` + `check_value_has_ty_list` with `check_value_has_ty_iff` bridging back to
  the inductive. Added `tyctxFromService :: service_ir_full ⇒ tyctx` + `enumNameFull` so Scala
  consumers construct the typing context directly from the IR via autogen. Wired into
  `Z3CounterExample.decode`: each decoded entity-field / state-relation entry / state-constant /
  input runs through `check_value_has_ty`; failures collect into
  `DecodedCounterExample.typingFailures` (asserted empty on the real `broken_url_shortener.spec`
  counterexample, demonstrating the proof-extracted typing predicate agrees with the runtime Scala
  decoder). `IrValueDecoder.decodeZ3` is the irreducible Z3-string → `ir_value` parser; everything
  else (tyctx construction, type-check, sortToTy) calls autogen directly without Scala-side
  wrappers.

  _Phase 9ee extension:_ `T_Forall_QAll` (single-binding universal quantifier). The rule binds the
  body in a context extended by `(var, t_dom)` for any `t_dom`. The body must type at `TBool`, no
  constraint on whether `dnm` is an enum or relation at the typing level (lowering's
  `string_in_list dnm enums` decides the routing, and the umbrella case handles both `ForallEnum`
  and `ForallRel` branches). Two helper lemmas in Semantics.thy: `eval_forall_enum_some_imp_bool`
  and `eval_forall_rel_some_imp_bool` (both: whenever the fold-evaluator returns `Some v`, `v` is
  `VBool`, by structural list induction on members / domain, the body-eval is gated through the
  `VBool b` pattern). The umbrella case splits on `string_in_list dnm enums` and dispatches per
  branch; each branch extracts the inner schema / relation lookup, applies the appropriate helper,
  and closes via `vt_bool`. Notably the preservation does **not** need the body IH: the result-type
  is forced to `TBool` by the eval structure regardless of body's typing (body's role is to make the
  typing rule semantically meaningful; preservation rides on the eval wrapper alone). The umbrella
  now covers 27 typing rules. _Phase 9ff extension:_ `T_Forall_QAll_Cons` (multi-binding universal
  quantifier). The rule peels one head binding at a time, recursively typing the inner
  `QuantifierF QAll (b2 # rest_bs) body sp` (still non-empty) in env extended by `(var, t_dom)`.
  Composes with `T_Forall_QAll` (single-binding) to cover all non-empty binding lists:
  single-binding chains as the base case of the recursion. The umbrella case mirrors
  `T_Forall_QAll`'s structure: split on `string_in_list dnm enums`, decompose lower one level deeper
  (now obtaining `inner = lower_forall_bindings enums (b2 # rest_bs) body' sp` rather than just
  `body'`), then the same `eval_forall_*_some_imp_bool` extractor closes by `vt_bool`. The umbrella
  now covers 28 typing rules. _Phase 9gg extension:_ `T_Forall_QNo` (single + multi-binding). `QNo`
  lowers to `lower_forall_bindings enums bs (UnNot body' sp) sp`: the body is wrapped in `UnNot`
  before being folded into the binder chain (no outer `UnNot`). The umbrella case is structurally
  identical to `T_Forall_QAll` / `T_Forall_QAll_Cons`: the inner body slot is `UnNot body' sp`
  instead of just `body'`, but the `eval_forall_*_some_imp_bool` extractor doesn't care what the
  inner body is; it just confirms the fold-evaluator's output is `VBool`. Both single and cons
  variants close by the same `vt_bool` pattern. _Phase 9hh extension:_ `T_Forall_QExists` /
  `T_Forall_QSome` (single + multi each, four new rules). Both kinds have identical lower behavior:
  outer `UnNot` wrap around `ForallEnum/Rel` whose inner body is itself wrapped in `UnNot`. The
  umbrella case has one extra unwrap step: extract
  `e' = UnNot (ForallEnum/Rel … (UnNot body' sp) sp) sp`, then
  `auto split: option.splits ir_value.splits` on the eval gives `v = VBool (¬b)` for some `b` (the
  outer `UnNot`'s eval inverts the inner `VBool b`). Closes by `vt_bool`. **The wf_z3-fragment
  quantifier coverage is now complete:** all four kinds (QAll, QNo, QExists, QSome) × both binding
  cardinalities (single, multi). The umbrella now covers 34 typing rules. _Phase 9ii capstone:_
  `cat_h_progress_and_preservation`: composes `well_typed_imp_lower_some` (progress: every
  well-typed expr lowers) with `h3_preservation` (preservation: lowered + eval-defined ⟹ typed
  result) into one statement:
  `expr_has_ty Γ e t ⟹ agrees_strict env st Γ ⟹ ∃e'. lower enums e = Some e' ∧ (∀v. eval sch st env e' = Some v ⟶ v ::v t)`.
  The full Cat-H story in one theorem. _Phase 9jj enrichment:_ tighter quantifier typing:
  `T_Forall_QAll_Enum` (requires `dnm ∈ set (tc_enums Γ)`, binds `var` at `TEnum dnm`) and
  `T_Forall_QAll_Rel` (requires `dnm ∉ set (tc_enums Γ)` + relation schema lookup, binds `var` at
  `tv = schema_relation_value_type`). Schema infra: `tc_enums :: String.literal list` added to
  `tyctx` (fifth field); new bridge lemma `string_in_list_iff_in_set` connects the lower-side
  `string_in_list` routing test with the typing-side `∈ set` predicate. The umbrella gains a fifth
  premise `tc_enums Γ = enums` (last position so existing `prems(1..3)` references stay stable); all
  twelve existing `T_X.IH[OF ...]` chains updated to pass the new premise; the `T_Let` cons case
  uses a `tc_enums (Γ⦇tc_env := ...⦈) = enums` by simp step because the record-update preserves
  `tc_enums`. The capstone `cat_h_progress_and_preservation` likewise takes the new premise.
  Umbrella at 37 typing rules. _Phase 9kk extension:_ tight cons rules for QAll + full tight family
  for QNo. `T_Forall_QAll_Enum_Cons` / `T_Forall_QAll_Rel_Cons` extend 9jj's single-binding rules to
  multi-binding chains (recursive inner Q in extended env); `T_Forall_QNo_Enum` / `T_Forall_QNo_Rel`
  / `T_Forall_QNo_Enum_Cons` / `T_Forall_QNo_Rel_Cons` give the same tight enum / rel discrimination
  for the `QNo` kind. All six umbrella cases follow the same template: extract
  `string_in_list dnm enums = True/False` from the enum-membership hypothesis +
  `tc_enums Γ = enums`, deterministically pin the lower route (no inner case-split needed unlike the
  loose rules), decompose lower + eval, close via `vt_bool`. _Phase 9ll extension:_ full tight
  family for `QExists` and `QSome`: 4 rules each, eight new rules total. Both kinds have identical
  lower behavior (outer `UnNot` wrap around inner `ForallEnum/Rel` whose body is wrapped in
  `UnNot`); the eight umbrella cases reuse the outer-`UnNot` unwrap pattern from 9hh (extract
  `v = VBool (¬b)` via `auto split: option.splits ir_value.splits`). **The tight quantifier coverage
  is now complete:** all four kinds (QAll, QNo, QExists, QSome) × both routes (enum, relation) ×
  both cardinalities (single, multi), 16 tight rules total. _Phase 9ww-followup:_ removed the 8
  loose generic `T_Forall_*` rules (`T_Forall_QAll` / `_Cons` and the analogous `QNo` / `QExists` /
  `QSome` variants) that left `t_dom` as a free meta-variable bypassing schema lookup. PR #285
  review (cubic + coderabbitai) flagged them as strict over-generalisations; the 16 tight `_Enum` /
  `_Rel` rules already provide sound coverage. Soundness proofs lose their generic umbrella cases (8
  trivial `by simp` + 8 ~30-line `string_in_list dnm enums` case-splits, ~265 lines net deletion);
  the tight Enum/Rel proofs are untouched and discharge every quantifier derivation post-removal.
  Umbrella now covers 43 typing rules. _Phase 9mm init-friendliness:_ small family of empty / init
  lemmas to ease caller verification: `schema_field_type_empty`, `schema_relation_value_type_empty`,
  `entity_field_well_typed_empty`, `relation_value_well_typed_empty`, `env_agrees_strict_empty` (all
  `[simp]`), plus a `tyctx_empty` definition and an `agrees_strict_empty` theorem closing
  `agrees_strict [] state_empty tyctx_empty`. These let a spec author bootstrap `agrees_strict` from
  a trivial-initial scenario without manually unfolding the four well-typedness conjuncts. _Phase
  9nn extension (issue #383):_ the typing layer covers the lifted native sorts. `ty` gains `TStr` /
  `TOption ty` / `TSeq ty` / `TMap ty ty`; `value_has_ty` gains `vt_str` / `vt_none` (polymorphic:
  `VNone : TOption t` for any `t`) / `vt_some` / `vt_seq` / `vt_map` (pairwise key+value premises).
  `check_value_has_ty` gains a `check_value_has_ty_pairs` mutual companion and is now a `function`
  with a hand `measure`: the pairs branch recurses into both components of each pair, which the
  lexicographic-order search cannot synthesise; the measure reuses the datatype's `size_prod`
  component so the `VMap` descent matches `ir_value.size` verbatim. The ctor-matched value arms
  dispatch on `ty` via a `case` RHS (non-recursive `False` default), so future `ty` growth does not
  multiply equation rows. `typeExprFullToTy` covers `String` / `OptionTypeF` / `SeqTypeF` /
  `MapTypeF` and aligns its primitive-name policy with the verify layer's `primitiveSortOf`
  (`Boolean`; `DateTime` / `Date` as epoch ints; the phantom `Double` alias dropped, nothing
  produces it); `schemaFieldType` / `schemaRelationValueType` inherit the coverage for free.
  `schema_type` gains the matching `RealT` / `StrT` / `OptionT` / `SeqT` / `MapT` forms with
  `typeExprToTy` cases. `expr_has_ty` gains nine rules: `T_StrLit`, `T_NoneLit` (polymorphic option
  type, mirroring `T_SetLit_Empty`), `T_SomeWrap`, `T_SeqLit_Empty` / `T_SeqLit_Cons`,
  `T_MapLit_Empty` / `T_MapLit_Cons`, plus `T_If` and `T_Matches` closing the remaining batch-3 lift
  gaps (#378's `Ite` and the natural `TStr` consumer). String equality typing was already free via
  `T_Cmp_Eq`'s `t1 = t2` branch. `well_typed_imp_wf_z3` absorbs all nine new cases in its trailing
  `auto` (`wf_z3` was already `True` for these constructs); `h3_preservation` gets nine explicit
  cases (`T_SeqLit_Cons` mirrors `T_SetLit_Cons` minus the dedupe fold; `T_MapLit_Cons` threads
  per-entry IHs directly from the Cons-style rule, no universally-quantified helper needed; `T_If`
  dispatches on the scrutinee's `VBool`). Umbrella now covers 52 typing rules. The verify-layer
  stopgap removal (`lacksVerifiedTy` skip, `sortToTy` native-sort `None`s) is the follow-up PR on
  #383.

- `wf_z3` syntactic subset proven sufficient for `lower` (`Soundness.thy` §Phase 9j, dual of 9i): a
  syntactic predicate `wf_z3` carves out the Z3-verifiable fragment of `expr_full` and the capstone
  `wf_z3_imp_lower_some` proves `wf_z3 e ⟹ lower enums e ≠ None`. This upgrades `Trust.classify`'s
  runtime oracle (`lower(e).isDefined ⟹ Sound`) to a proven syntactic guarantee: in-fragment specs
  are Sound, never best-effort. The two side-conditions baked into the predicate are the
  `peel_relation_ref` shape for `IndexF` bases and the `lower_forall_bindings` totality condition
  for `QuantifierF` bindings. Mirrors the size-induction backbone of 9i exactly; build stays ~3 min.
- `flatten_and` structural preservation (§Phase 9l): `flatten_and_nonempty`,
  `flatten_and_requires_alloy_iff`, `flatten_and_wf_z3_iff`. The decomposition of a
  conjunction-of-invariants preserves both the Alloy routing decision (any conjunct triggers Alloy)
  and the `wf_z3` in-fragment guarantee (all conjuncts in-fragment). Full semantic preservation
  needs `expr_full` semantics (= Cat-H), out of scope here.
- IR enum codec round-trips (`IR.thy` §Phase 9m, proof-only): `bin_op_to_ts_inverse`,
  `un_op_to_ts_inverse`, `quant_kind_to_ts_inverse`, `multiplicity_to_ts_inverse`,
  `binding_kind_to_ts_inverse`. `bin_op_to_ts` (already extracted) and the four new sibling encoders
  use the exact string tokens of `modules/ir/.../Serialize.scala`, so their decoders + round-trip
  theorems enable the Phase-9m extraction PR that replaces those circe codecs with calls to
  extracted `SpecRestGenerated.*` functions. The full `expr_full`/`type_expr_full` codec is the next
  size-induction proof.
- Scalar direct-emit recognisers (`Classify.thy`, issue #407): `scalarRhsOf` / `scalarUpdateOf`
  parse an ensures clause `x' = <arithmetic over x / pre(x) / int literals>` into a structured
  `scalar_rhs` tree; `scalarGuardOf` parses requires atoms into `scalar_guard`
  (`SgCmp name scalar_cmp int`, where `scalar_cmp` is a dedicated closed comparator datatype so
  Scala consumers render guards with an exhaustive match instead of a defensive catch-all over
  `bin_op`). `classifyStrategy` gained `requires` and `scalarFieldNames` parameters: if ANY
  flattened ensures clause is a scalar update, ALL must be (a mixed entity/scalar operation would
  otherwise be emitted by the entity-CRUD path with the scalar clauses silently dropped), and every
  requires atom must be guardable: the emitters fold guards into the atomic UPDATE's WHERE clause,
  so an unguardable precondition would let an HTTP caller break a verified invariant at runtime. The
  same extracted extractors feed both the classifier gate and all three language emitters (single
  source of truth: the recognised fragment and the emitted SQL cannot drift). Proof-free executable
  code, like the rest of the classifier.
- Alloy backend unverified. Only the router `requires_alloy` is proven. The `expr_full ⇒ Alloy`
  translation and Alloy semantics have no Isabelle analog: a full second trusted backend. A
  `translate_alloy` + Alloy-semantics + soundness theorem mirrors the entire Z3 effort.
  **Routing-disjointness RESOLVED (`Soundness.thy` §Phase 9i):** the full theorem
  `requires_alloy e ⟹ lower enums e = None` (with the `lower_set_list / lower_with_assigns`
  companions) is now proven as `requires_alloy_imp_lower_none`: the Z3-routed and Alloy-routed
  expression fragments are provably disjoint. The blocker was the induction principle, not proof
  difficulty: the `function`-derived `lower.induct` (computation induction) sequentially splits the
  nested `case op of …` inside `BinaryOpF` (20-way) / `UnaryOpF` / `QuantifierF` into dozens of
  op-guarded partial subgoals whose IHs no longer match the clean collapse lemmas; blanket
  `simp_all`/`auto` over that exploded set does not terminate within budget (>15 min, confirmed via
  `parallel_proofs=0` + per-region `Timing` instrumentation, the diagnostic showed every other
  region is ~seconds and the cost is wholly the monolithic `simp_all` over the op-exploded
  induction). Standard remedy (Krauss functions manual, pattern-splitting makes generated induction
  rules too specific): do not induct on the function's recursion:
  `requires_alloy_imp_lower_none_expr` uses well-founded `size` induction (`measure_induct_rule`) +
  plain `cases e` (op is non-recursive, never split → ~25 clean cases vs ~60 op-exploded), each arm
  delegating to its self-contained collapse lemma with the size-IH; the two list-recursive
  constructors go through standalone list lemmas (`lower_set_list_ra_none`,
  `lower_with_assigns_ra_none`) parameterised by a per-element hypothesis (no circularity).
  Whole-session build stays ~2.5 min. The remaining Cat-I gap is only the full `expr_full ⇒ Alloy`
  translation + Alloy semantics (a second trusted backend), unrelated to routing disjointness.
- `flatten_inheritance` non-idempotence (proof landed, Scala rewire pending). v1 retains the parent
  ref and concatenates inherited invariants → second application duplicates. Harmless today
  (`buildIRCore` applies it once; locked by `parser.FlattenInheritanceTest`). **Phase 9k:** the
  hardened `flatten_inheritance2` (which clears the parent ref) is now proven idempotent
  (`flatten_inheritance2_idem`) and equivalent to v1 on parentless input
  (`flatten_inheritance2_eq_on_parentless`). The Scala-side switch (`parser.Builder.buildIRCore` →
  v2, plus `lint.UnusedEntity` reachability + IR-JSON `extends_` adjustments) is the deferred
  validated PR, now riding on proof rather than a risk argument.
