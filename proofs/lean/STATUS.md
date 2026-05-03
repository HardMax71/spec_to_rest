# SpecRest Proof-State Ledger

> **First-ship gate met (2026-05-02).** The universal `soundness` meta-theorem is closed with **zero
> `sorry`** for the §6.1 verified subset extended through M_L.4.a-k and issue #195 set algebra. The
> Z3 translator's output is mechanically validated against the Lean `translate` function for every
> in-subset `Expr`. See `docs/content/docs/research/10_translator_soundness.md` §13.1 for the formal
> claim and §16.6 for the activation closure record.

Mirrors `docs/content/docs/research/10_translator_soundness.md` §14 (proof-safe profile) at
expression-case granularity. Each row records what the prover-side embedding actually covers right
now, distinct from the proof-program intent.

Status meanings, aligned with `docs/content/docs/research/10_translator_soundness.md` §13:

| Label        | Meaning                                                                   |
| ------------ | ------------------------------------------------------------------------- |
| `embedded`   | The Lean `Expr` constructor exists, `eval` covers it, named lemmas exist. |
| `translated` | `translate` mirrors the Scala translator on this case (pre-soundness).    |
| `mirrored`   | A prover-side mirror exists, awaiting an `M_L.2` soundness theorem.       |
| `sound`      | An `M_L.2` per-case soundness theorem closes for this constructor.        |
| `deferred`   | Not yet embedded; queued in `SpecRest/IR.lean.todo`.                      |
| `excluded`   | Permanently outside the Z3 global-theorem track.                          |

Last sync with the consolidated profile (now §14 of `10_translator_soundness.md`): M_L.4.a-k shipped
batch (post-2026-05-02).

## 1. M_L.1 verified subset (research doc §6.1)

| `Expr` case                                       | Profile stage | Lean status             |
| ------------------------------------------------- | ------------- | ----------------------- |
| `BoolLit`                                         | `bootstrap`   | `sound` (M_L.2 closed)  |
| `IntLit`                                          | `bootstrap`   | `sound` (M_L.2 closed)  |
| `Identifier` (env-hit path)                       | `bootstrap`   | `sound` (M_L.2 closed)  |
| `Identifier` (env-miss / state-scalar-hit path)   | `bootstrap`   | `sound` (M_L.2 closure) |
| `BinaryOp(And)`                                   | `bootstrap`   | `sound` (M_L.2 closed)  |
| `BinaryOp(Or \| Implies \| Iff)`                  | `bootstrap`   | `sound` (M_L.2 closure) |
| `BinaryOp(Eq \| Neq)` (polymorphic over `Value`)  | `bootstrap`   | `sound` (M_L.2 closure) |
| `BinaryOp(Lt \| Le \| Gt \| Ge)` (Int)            | `bootstrap`   | `sound` (M_L.2 closure) |
| `BinaryOp(In)` (state-relation domain membership) | `bootstrap`   | `sound` (M_L.2 closure) |
| `BinaryOp(In \| NotIn)` over set expressions      | `issue #195`  | `sound`                 |
| `BinaryOp(NotIn)` (composition `¬In`)             | `first ship`  | `sound` (M_L.4.e)       |
| `BinaryOp(Subset)` over rel-identifiers           | `first ship`  | `sound` (M_L.4.i)       |
| `BinaryOp(Union \| Intersect \| Diff)`            | `issue #195`  | `sound`                 |
| `SetLiteral`                                      | `issue #195`  | `sound`                 |
| `UnaryOp(Not)`                                    | `bootstrap`   | `sound` (M_L.2 closed)  |
| `UnaryOp(Negate)` (Int)                           | `bootstrap`   | `sound` (M_L.2 closed)  |
| `Quantifier(All)` over enums                      | `bootstrap`   | `sound` (M_L.2 closure) |
| `Quantifier(All)` over state-relation domains     | `first ship`  | `sound` (M_L.4.f)       |
| `Index` over state-relation pairs                 | `first ship`  | `sound` (M_L.4.g)       |
| `FieldAccess` on entity-typed state scalars       | `first ship`  | `sound` (M_L.4.h)       |
| `FieldAccess` (nested: Index/chain/quant-bound)   | `first ship`  | `sound` (M_L.4.k)       |
| `Let`                                             | `bootstrap`   | `sound` (M_L.2 closure) |
| `EnumAccess`                                      | `bootstrap`   | `sound` (M_L.2 closure) |

Per-operator denotation lemmas (the M_L.2 building blocks) live in `SpecRest/Lemmas.lean`. The
`safe_counter` invariant (`count ≥ 0`) is closed as a named theorem
(`safeCounter_invariant_holds_initially`) under a hand-built initial state, satisfying the §8.2
acceptance criterion.

M_L.2 (research doc §8.3): the `translate : Expr → SmtTerm` function, the shallow SMT embedding
(`SmtTerm`, `SmtVal`, `SmtModel`, `smtEval`), and the correlation functions (`valueToSmt`,
`correlateEnv`, `correlateModel`) ship in `SpecRest/Smt.lean`, `SpecRest/Translate.lean`, and
`SpecRest/Soundness.lean`. **The universal `soundness` meta-theorem is closed with zero `sorry` for
the §6.1 verified subset.** Per-case theorems for every constructor (atoms, unary, polymorphic and
int comparisons, boolean binops with all four ops, letIn, enumAccess, member, forallEnum,
state-scalar identifier path) are proven; the universal theorem dispatches success paths to per-case
theorems and discharges failure paths (eval none, wrong-shape values) via the smtEval failure-case
helpers in `Smt.lean`. The enumAccess collision concern was resolved structurally by introducing
`SmtTerm.enumElemConst` (separate from `.var`) so `Translate.lean` emits `.enumElemConst en m`
instead of `.var (en ++ "." ++ m)`; this makes `correlateModel.constVals` need only state scalars,
with enum members handled via `lookupSortMembers`. The mutual-induction `evalForallEnum_correlated`
lemma threads body soundness through every member of the enum.

### M_L.3 — Per-run translation-validation certificates (closed in this PR)

Research doc §8.4. The certificate emitter ships under
`modules/verify/src/main/scala/specrest/verify/cert/` and the supporting Lean tactic library is
`proofs/lean/SpecRest/Cert.lean`.

- Files: `cert/Emit.scala`, `cert/EvalIR.scala`, `cert/VerifiedSubset.scala`.
- For every invariant AND every operation requires clause, the emitter computes the closed
  evaluation result via the Scala-side reducer `EvalIR` (a one-to-one mirror of `Semantics.lean`
  `eval` over the verified subset) and embeds it in
  `theorem cert ... = some actual := by cert_decide`.
- Drift between Scala-side `EvalIR` and Lean-side `eval` is detected at `lake build` time of the
  emitted bundle. That cross-check is the M_L.3 translation-validation guarantee.
- Out-of-subset cases emit `theorem cert : True := trivial` with a `TODO[M_L.4]` marker in the
  docstring naming the offending operator. The emitted bundle is `sorry`-free; `lake build` is
  silent-clean even on mixed-subset bundles.
- CLI plumbing: the `verify --emit-cert` flag writes a self-contained Lake project (a
  `lakefile.toml` with a path-based `require` against the in-repo `proofs/lean/` workspace, a copy
  of `lean-toolchain`, and the generated module).
- `.github/workflows/lean-certs.yml` runs the emit + lake-build flow per fixture in a sidecar matrix
  off the critical path.

Deep IR shells embedded with carrier shape: `EnumDecl`, `EntityDecl` (flat, no inheritance),
`FieldDecl`, `StateScalar`, `StateRelation`, `StateDecl`, `OperationDecl` (`requires` + `ensures`;
ensures stubbed with `true` until `Prime`/`Pre` land in M_L.2), `InvariantDecl`, `ServiceIR`.
`TypeExpr` covers `Bool`, `Int`, `enumT`, `entityT`, `relationT`.

## 2. First-ship targets — remaining queue

The post-M_L.2 first-ship table is now down to two items. Items previously listed here (`Prime`,
`Pre`, `UnaryOp(Cardinality)`, `Quantifier(Some)` over enums) closed via M_L.4.b/c/d; `Index` closed
in M_L.4.g; `FieldAccess` (bare-Identifier base) closed in M_L.4.h (this PR). `Quantifier(Some)`
over state relations closed in M_L.4.f. The remaining two items both require the StatePair carrier
refactor (M_L.4.b-ext); Phase 1 of that refactor lands the carrier scaffolding without changing
single-state behavior — see §M_L.4.b-ext below.

| `Expr` case                                    | Profile stage | Status                   |
| ---------------------------------------------- | ------------- | ------------------------ |
| `With`                                         | `first ship`  | `deferred (M_L.4.b-ext)` |
| Two-state coupling via `OperationDecl.ensures` | `first ship`  | `deferred (M_L.4.b-ext)` |

### M_L.4.b-ext — True two-state Prime/Pre, phased ship (issue #194)

**Phase 1 — Lean-side carrier (PR #200, merged).** Landed `StatePair` / `StateMode` / `StatePair.at`
/ `StatePair.diag` and a mode-aware evaluator `evalAt` in `Semantics.lean`. The diagonal-collapse
theorem `evalAt_diagonal_eq_eval` proves `evalAt mode s (StatePair.diag st) env e = eval s st env e`
for every mode and every Expr, so every existing per-case soundness theorem about `eval` continues
to hold verbatim.

**Phase 2 — SMT-side mirror (this PR).** Strictly additive on the SmtTerm / SmtModel data; the only
change to existing proofs is a one-line update to the universal soundness `prime` / `pre` arms
(still single-state, still zero `sorry`). Adds:

```text
SmtTerm.prime / SmtTerm.pre        -- mode-tagging wrappers; smtEval treats as identity
SmtModelPair { pre, post : SmtModel }, .at, .diag, simp lemmas
smtEvalAt : StateMode → SmtModelPair → SmtEnv → SmtTerm → Option SmtVal
                                     mutual with smtEvalAtForallEnum / smtEvalAtForallRel
                                     state lookups read through (mp.at mode)
                                     .prime t flips mode to .post; .pre t flips to .pre
smtEvalAt_diagonal_eq_smtEval      smtEvalAt mode (SmtModelPair.diag m) env t = smtEval m env t
smtEvalAt_prime / smtEvalAt_pre    mode-flip characterizations
correlateModelPair s sp            { pre := correlateModel s sp.pre; post := correlateModel s sp.post }
soundnessAt_diagonal               diagonal-mode-aware soundness — derived corollary,
                                     no fresh structural induction (composes the two diagonal
                                     collapses with the existing single-state soundness)
```

Translate.lean now emits `.prime (translate e)` / `.pre (translate e)` for `Expr.prime` /
`Expr.pre`. The universal `soundness` theorem's two affected arms (`prime e ih` / `pre e ih`) gain a
one-line `rw [smtEval_prime]` / `rw [smtEval_pre]` to peel the new identity wrapper; single-state
collapse claim unchanged.

**Phase 3a — per-case off-diagonal `soundnessAt_*` theorems (this PR).** Strictly additive; the
existing universal `soundness` theorem is unchanged. Adds 22 per-case `soundnessAt_*` theorems
covering leaf / propositional / arithmetic / comparison / `letIn` / `enumAccess_known` /
`ident_local` / `Prime` / `Pre` constructors, plus the load-bearing bridge:

```text
correlateModelPair_at  (correlateModelPair s sp).at mode = correlateModel s (sp.at mode)
```

This bridge lets state-touching cases (Phase 3b) lift via the existing `correlateModel_*` lemmas
without rewriting them. Each per-case theorem mirrors the single-state `soundness_*` shape:

```text
eval s st           ↦  evalAt mode s sp
smtEval m           ↦  smtEvalAt mode mp
correlateModel s st ↦  correlateModelPair s sp
```

Foundational helpers added: `evalAt_*` characterizations in `Lemmas.lean` (atomic / propositional /
arithmetic / comparison / letIn / enumAccess); `smtEvalAt_*` characterizations in `Smt.lean`
(parallel set, including arithmetic-failure cases for `div`).

**Phase 3b — state-touching / set-op / quantifier per-case theorems (this PR).** Adds 9 per-case
`soundnessAt_*` theorems plus 2 parallel mutual-induction lemmas:

```text
soundnessAt_ident_state       state-scalar lookup; lifts via correlateModelPair_at
soundnessAt_member_resolved   state-relation membership
soundnessAt_cardRel_resolved  state-relation cardinality
soundnessAt_indexRel_resolved state-relation pair lookup
soundnessAt_fieldAccess_resolved entity-field lookup
soundnessAt_setEmpty / _setInsert_resolved / _setMember_resolved / _setBin_sets
evalAtForallEnum_correlated   parallel mutual lemma (members induction)
soundnessAt_forallEnum_known  enum-quantifier closure
evalAtForallRel_correlated    parallel mutual lemma (domain induction)
soundnessAt_forallRel_known   state-relation-quantifier closure
```

State-touching cases reuse the existing single-state correlateModel lemmas via
`correlateModelPair_at`'s rewrite:
`(correlateModelPair s sp).at mode = correlateModel s (sp.at mode)`. Set-op cases reuse
`dedupeValues_map_valueToSmt`, `setUnionValues_map_valueToSmt`, etc. Quantifier mutual lemmas are
structurally identical to their single-state counterparts with carriers swapped.

All 31 success-path per-case `soundnessAt_*` theorems for the verified subset now exist.

**Phase 3c.1 — off-diagonal failure-path helpers (this PR).** Strictly additive: ~33 new
`smtEvalAt_*` failure-path lemmas in `Smt.lean`, parallel to the single-state
`smtEval_*_none/ _nonBool/_nonInt/_nonSet` helpers used by the universal `soundness` theorem. Each
is a 2-7 line `simp only [smtEvalAt, h]` proof. Coverage: unary (not/neg × none/nonBool|nonInt),
boolean (and/or/implies × lhs/rhs × none/nonBool), comparison (eq/lt × lhs/rhs × none/nonInt),
arithmetic (add/sub/mul/div × lhs/rhs × none/nonInt), letIn, set ops
(insert/member/union/intersect/diff × elem|lhs|rhs × none/nonSet).

These helpers exist to be consumed by Phase 3c.2's universal `soundnessAt` structural induction.
Each constructor's failure arm will dispatch to these helpers (parallel to how single-state
`soundness` dispatches to `smtEval_*_none` etc.).

**Phase 3c.2 — off-diagonal private failure-path helpers (this PR).** Strictly additive: 8 new
private helpers in `Soundness.lean`, parallel to the existing single-state private helpers used by
`soundness`'s case dispatch:

```text
soundnessAt_unNot_nonBool         unNot operand ≠ vBool
soundnessAt_unNeg_nonInt          unNeg operand ≠ vInt
boolBin_lhs_nonBool_at            boolBin lhs ≠ vBool (4 ops × 4 shapes)
boolBin_rhs_nonBool_lhs_bool_at   boolBin rhs ≠ vBool given lhs = vBool
arith_lhs_nonInt_at               arith lhs ≠ vInt (4 ops × 4 shapes)
arith_rhs_nonInt_lhs_int_at       arith rhs ≠ vInt given lhs = vInt
cmp_lhs_eval_none_at              cmp lhs evaluates to none (6 ops, per-op SmtTerm shape)
cmp_rhs_eval_none_at              cmp rhs evaluates to none (6 ops, per-op SmtTerm shape)
cmp_lt_lhs_nonInt_at              cmp .lt lhs ≠ vInt
cmp_lt_rhs_nonInt_lhs_int_at      cmp .lt rhs ≠ vInt given lhs = vInt
```

The cmp helpers are intricate because the translator emits different SmtTerm shapes per op (eq →
`.eq`, neq → `.not (.eq)`, lt → `.lt`, le → `.or (.lt) (.eq)`, gt → `.lt` (swapped), ge →
`.or (.lt swapped) (.eq)`); each op's failure path handles the specific SmtTerm structure.

**Phase 3c.3 — universal `soundnessAt` theorem closes (this PR).** This is the structural-induction
hookup that ties every per-case `soundnessAt_*` (Phases 3a/3b) and every failure-path helper (Phases
3c.1/3c.2) into a single universal claim:

```text
theorem soundnessAt (mode : StateMode) (e : Expr) :
    valueToSmt? (evalAt mode s sp env e)
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate e)
```

for every Expr `e`, every mode, every StatePair, every env. Mirrors the single-state universal
`soundness` (lines 1819-2655 of `Soundness.lean`) with carriers substituted (`eval` → `evalAt`,
`smtEval` → `smtEvalAt`, `correlateModel` → `correlateModelPair`).

**This closes #194's first acceptance criterion (off-diagonal soundness).**

Phase 3c.3 additions:

- 6 remaining cmp shape-failure helpers in `Soundness.lean`: `cmp_le_lhs_nonInt_at`,
  `cmp_le_rhs_nonInt_lhs_int_at`, `cmp_gt_lhs_nonInt_at`, `cmp_gt_rhs_nonInt_lhs_int_at`,
  `cmp_ge_lhs_nonInt_at`, `cmp_ge_rhs_nonInt_lhs_int_at`.
- `smtEvalAt_enumElemConst_nonMember` failure helper in `Smt.lean`.
- 6 `evalAt_*` set-op failure characterizations in `Lemmas.lean`: `setInsert_elem_none`,
  `setInsert_set_none`, `setInsert_set_nonSet`, `setMember_elem_none`, `setMember_set_none`,
  `setMember_set_nonSet`.
- **The universal `theorem soundnessAt` itself** (~600 LOC structural induction on `Expr`
  generalizing `env` and `mode`).

**Phase 4a — `Expr.withRec` joins the Lean IR shell.** Single-field shape
`Expr.withRec (base : Expr) (fld : String) (value : Expr)` — multi-field updates lower to chained
applications. The fixed-arity shape avoids the nested-induction issue that `List (String × Expr)`
would introduce.

**Phase 4b — Skolem encoding for `withRec` (this PR).** Real semantics for `withRec`, mirroring
`Translator.scala:1061-1098`'s Skolem record-update encoding:

- `Value` extends with `vEntityWith (base : Value) (fld : String) (value : Value)` (chain carrier).
- `SmtVal` extends with `sEntityWith` (parallel SMT-side carrier).
- `Value.fieldLookup` / `SmtVal.fieldLookup` walk the chain: matches override on `fld` first, falls
  back to base.
- `eval`/`evalAt` for `withRec` evaluate base + value, wrap as `vEntityWith`.
- `eval`/`evalAt` for `fieldAccess` route through `Value.fieldLookup`.
- `translate (.withRec base fld value) = .withRec (translate base) fld (translate value)` — the SMT
  side gets a parallel `SmtTerm.withRec` constructor.
- `smtEval`/`smtEvalAt` mirror the Lean evaluator on `withRec` and `fieldAccess`.
- New correlation lemma `fieldLookup_correlated` bridges `valueToSmt? ∘ Value.fieldLookup` and
  `SmtVal.fieldLookup ∘ valueToSmt`. Recurses on the chain; reuses `lookupField_correlated` at the
  `vEntity` base.
- Universal `soundness` and `soundnessAt` carry full `withRec` arms; case analysis on `eval base` ×
  `eval value` covers the three cases (none/none, some/none, some/some).
- All non-entity arms in `cases v with` blocks (boolBin, arith, cmp, setBin, setInsert, setMember,
  fieldAccess) extended with a `vEntityWith` clause via the existing failure-helper machinery.

Closes with **zero `sorry`**, structural induction unchanged in shape; depth grows by one per arm.

**Out of Phase 4b, queued for Phase 5:**

- Phase 5 — Scala-side `EvalIR.State` extends to `StatePair`; `VerifiedSubset.classify` accepts
  `With`; `Emit.scala` renders `StatePair` literals; demo-state synthesis produces per-mode
  defaults. `safe_counter` invariant-preservation cert flips from stub to `cert_decide`.

The `single-state collapse` notes elsewhere in this file remain factually correct for the current
shipped `eval` / `smtEval` / `soundness` API. Phase 3c is what removes them.

### M_L.4.k — Nested FieldAccess (entity-id-keyed carrier) (closed in this PR)

`Expr.FieldAccess(base, field)` accepts an arbitrary entity-valued sub-expression as the base — the
bare-Identifier `state_scalar.field` (M_L.4.h) generalises to:

- `users[uid].email` — `FieldAccess(Index(rel, key), field)`
- `current_user.profile.email` — `FieldAccess(FieldAccess(base, f1), f2)` chained
- `forall t in tasks, t.priority > 0` — `FieldAccess(Identifier(quant_var), field)` in a
  forallEnum/forallRel body

Carrier semantics shifts: `State.entityFields` shape is unchanged
(`List (String × List (String × Value))`) but the outer key is now an **entity ID** (the `id`
carried by `vEntity`) rather than a scalar name. `SmtModel.predFields` parallels this. Demo-state
seeding mints fresh entity IDs (`<scalarName>__id`) for every entity-typed scalar so the M_L.4.h
bare-Identifier path stays closed (eval scalar → `vEntity name id` → field table keyed by `id`).

Eval / smtEval get a recursive arm: evaluate the base, pattern-match on `vEntity _ id`, look up the
field by id. The `lookupField_correlated` lemma keeps the same shape as M_L.4.h with the entity-id
parameter; the universal `soundness` arm threads the base IH and propagates `none` on non-entity /
`none` paths via fresh `*_base_none` / `*_nonEntity` characterizations on each side. Universal
`soundness` still **closed with zero `sorry`** post-extension.

`Expr.fieldAccess` and `SmtTerm.fieldAccess` both grow a recursive base parameter; `translate`
recurses (`.fieldAccess (translate base) fn`). Mirrors `Translator.scala:981-1005` where the
production translator applies a per-(entity, field) UF to the entity-instance argument computed by
recursive translation of the base expression.

Scala mirror: `cert/VerifiedSubset.scala` accepts FieldAccess on any in-subset base;
`cert/EvalIR.scala` recurses on the base Expr and pattern-matches `Value.VEntity`; `cert/Emit.scala`
renders `(.fieldAccess <translate base> "field")`. Demo-state seeds entity-typed state scalars with
a fresh `vEntity name <id>` so the M_L.4.h `current_user.email` path remains closed end-to-end.

Coverage uplift on the three nested-FieldAccess fixtures (`auth_service`, `todo_list`,
`url_shortener`): `cert_decide` count rises from 5 to 8 across 45 obligations. The carrier change
unblocks every `OutOfSubset("FieldAccess: only state_scalar.field …")` rejection (28 → 0); the
remaining stubs reduce to non-FieldAccess reasons (Call builtins, multi-binding quantifiers,
operation-input env binding — M_L.4.b-ext and beyond).

### M_L.4.i — BinaryOp(Subset) via composition (closed in this PR)

`BinaryOp(Subset, r1, r2)` over two state-relation identifiers joins the verified subset. Encoding
is **purely emitter-side composition** — no new Lean constructors:

- `Subset(r1, r2)` → `(.forallRel "_subset_x" r1 (.member (.ident "_subset_x") r2))`

This composes M_L.4.f `forallRel` with M_L.2 `member` arms; the universal `soundness` meta-theorem
covers the composition without a new dispatch arm. `EvalIR.eval` mirrors the value
(`dom1.forall(dom2.contains)`).

Restricted to two state-relation identifiers (`Subset(r1, r2)` where both are bare identifiers).
Subset over set-literals is collections-deferred.

Scala mirror: `cert/VerifiedSubset.scala` accepts `BinaryOp(Subset, Identifier, Identifier)`;
`cert/EvalIR.scala` adds a direct eval arm; `cert/Emit.scala` renders the composition.

### M_L.4.h — FieldAccess on entity-typed state scalars (closed in PR #189)

> **Historical / superseded by M_L.4.k.** This section records the original M_L.4.h carrier shape
> (scalar-name-keyed `entityFields`) and the bare-Identifier base restriction. M_L.4.k (PR #197 —
> see the "M_L.4.k" section above) generalised the constructor and flipped the carrier key from
> scalar-name to entity-id. The bare-Identifier path remains closed under M_L.4.k via demo-state
> seeding (`<scalarName>__id`), so the M_L.4.h acceptance criterion still holds; the details below
> describe the as-shipped M_L.4.h slice.

`Expr.FieldAccess(Identifier(scalar), field)` — bare-Identifier `state_scalar.field` — joined the
verified subset. Strictly-additive carrier extension parallel to M_L.4.g:

- `State.entityFields : List (String × List (String × Value))` — at M_L.4.h, keyed by scalar name
  (M_L.4.k flipped the outer key to entity ID; shape unchanged).
- `SmtModel.predFields : List (String × List (String × SmtVal))` — same shape on the SMT side.

Existing `relations`, `lookups`, `predDomain`, `predLookup` fields are untouched, so no proof
cascade. New machinery: `Expr.fieldAccess` + `SmtTerm.fieldAccess` constructors, `State.lookupField`
/ `SmtModel.lookupField` direct lookups, `lookupField_correlated` mutual-induction lemma bridging
them, `lookup_map_entityFields` and `lookup_map_stringValue` list-helpers, and a new
universal-soundness arm. The universal `soundness` meta-theorem is still **closed with zero
`sorry`** for the verified subset.

Mirrors `Translator.scala:981-1005`: production translator emits `entity_field_func(base)` where
`entity_field_func` is a per-(entity, field) uninterpreted function. Our shallow Lean model
precomputes the resolution: `(scalarName, fieldName) → Value` direct lookup — observably equivalent
for cert obligations because both use the model's interpretation on specific instances.

M_L.4.h restriction (bare-Identifier base only) was lifted by M_L.4.k. `Index(...).field`,
`FieldAccess(FieldAccess(...), ...)`, and quantifier-bound `t.field` are now all in subset.

`EvalIR.State.demo` populated `entityFields` with empty bindings at M_L.4.h. M_L.4.k extended the
seeder to mint a fresh entity ID per entity-typed state scalar (recursively, for entity-typed
sub-fields), so demo-state FieldAccess closes through the chain.

Scala mirror: `cert/VerifiedSubset.scala` accepts `Expr.FieldAccess(Identifier, _)`;
`cert/EvalIR.scala` adds `lookupField` helper and a FieldAccess arm; `cert/Emit.scala` renders
`(.fieldAccess "scalar" "field")` and the rendered State literal now includes the `entityFields`
field.

### M_L.4.g — Index over state-relation pairs (closed in this PR)

`Expr.Index(Identifier(rel), key)` — keyed lookup into a state-relation's pair table — joins the
verified subset. New Lean Expr constructor `indexRel`, new `SmtTerm.indexRel`, new
`State.lookups : List (String × List (Value × Value))` field carrying the (key, value) pairs
(strictly additive — `relations` field unchanged for membership/cardinality/forall semantics), new
`correlateModel.predLookup` field, and a new universal-soundness arm. The universal `soundness`
meta-theorem is still **closed with zero `sorry`** for the verified subset.

The encoding mirrors how the production translator (`Translator.scala:1009-1018`) handles Index:
`Index(rel, key)` becomes `relationFunc(key)` — an uninterpreted function application on Z3. On the
Lean side, `eval (.indexRel rel key)` evaluates the key, then calls `State.lookupKey` which finds
the first pair with matching key and returns the paired value (or `none`).

Translation soundness rests on a new lemma `lookupKey_correlated` that bridges `State.lookupKey` and
`SmtModel.lookupKey` via `valueToSmt_beq` distributing through `find?` on pair-comparison. The
auxiliary `find_map_valueToSmt` helper closes the induction.

Restrictions match `Cardinality`/`In`: base must be a state-relation identifier (literal name).
Index-of-Index, Index-on-FieldAccess, etc. remain `OutOfSubset` until M_L.4.h (FieldAccess) extends
the entity-record carrier.

Scala mirror: `cert/VerifiedSubset.scala` accepts `Expr.Index(Identifier, keyExpr)` if the key
classifies in-subset; `cert/EvalIR.scala` adds `relationPairs` + `lookupKey` helpers and an Index
arm; `cert/Emit.scala` renders `(.indexRel "rel" $keyT)` and the rendered State literal now includes
the `lookups` field. `EvalIR.State.demo` populates `lookups` with empty pair lists for non-scalar
state-fields (parallel to the M_L.4.f relations bootstrap), so demo-state Index lookups return
`none` cleanly.

### M_L.4.f — Quantifier(All/Some/No/Exists) over state-relation domains (closed in this PR)

`forall x in rel, P` (and `∃`/`No`/`Exists` aliases) now joins the verified subset where `rel` is a
state-relation identifier. New Lean Expr constructor `forallRel`, new `SmtTerm.forallRel`, new
per-case soundness theorem `soundness_forallRel_known`, new mutual-induction correlation lemma
`evalForallRel_correlated`, and a new universal-soundness arm. The universal `soundness`
meta-theorem is still **closed with zero `sorry`** for the verified subset.

The encoding is structurally parallel to `forallEnum`:

- `eval s st env (.forallRel var rel body)` — looks up `st.relationDomain rel`, iterates
  `evalForallRel` over each `Value` in the domain (no enum-name wrapping).
- `translate (.forallRel var rel body) = SmtTerm.forallRel var rel (translate body)`.
- `smtEval` on `.forallRel` looks up `m.lookupRel relName` and iterates the body over each `SmtVal`
  in the domain.
- The `∃`/`No`/`Exists` aliases go through emitter-side `unNot`-composition (mirrors M_L.4.d for the
  enum quantifier), so no new Lean constructors for those four kinds collectively.

Disambiguation between `forallEnum` and `forallRel` happens at emit time: `cert/Emit.scala` threads
the IR's `enums.map(_.name).toSet` through `renderExpr`. If the binding identifier is in that set,
render `.forallEnum`; otherwise render `.forallRel`. The classifier accepts both shapes
(single-binding-over-identifier).

`EvalIR.State.demo` was extended to populate `relations` with empty domains for every state-field
whose typeExpr is non-scalar (`SetType`/`MapType`/`SeqType`/`RelationType`). This unblocks
`forallRel` certs on demo state (vacuously-true over empty `dom`) — previously relations were always
`Nil`, which left M_L.4.c (Cardinality) certs as stubs on real fixtures.

Scala mirror: `cert/VerifiedSubset.scala` accepts both enum and relation identifiers as the binding
domain; `cert/EvalIR.scala` extends the `Quantifier(All)` arm to fall back to `relationDomain` when
the name isn't an enum; `cert/Emit.scala` emits `.forallEnum` / `.forallRel` based on the threaded
`enumNames` set.

### M_L.4.e — BinaryOp(NotIn) via composition (closed in this PR)

`BinaryOp(NotIn, elem, rel)` is now in the verified subset. Encoding is **purely emitter-side
composition** — no new Lean constructors:

- `NotIn(elem, rel)` → `(.unNot (.member elem rel))`

This mirrors `Translator.scala:632-636` exactly: the production translator renders `In/NotIn`
through the same `membership(...)` helper and only differs by an outer `Z3Expr.Not`. The Lean
meta-theorem covers NotIn via the existing `member` and `unNot` arms; no new soundness theorem or
mutual-induction lemma is needed. `EvalIR.eval` reduces the same way (`Not(In(...))`), so Scala and
Lean compute identical values.

Restrictions match `In`: rhs must be a state-relation identifier (literal name).

Scala mirror: `cert/VerifiedSubset.scala` accepts `BinOp.NotIn` with the same identifier-rhs
restriction; `cert/EvalIR.scala` desugars to `Not(In(...))`; `cert/Emit.scala` renders
`(.unNot (.member $lT $rel))`.

### M_L.4.d — Quantifier(Some/No/Exists) over enums via composition (closed in this PR)

`∃ x ∈ enum, P` and `No x ∈ enum, P` and `Exists x ∈ enum, P` are now in the verified subset.
Encoding is **purely emitter-side composition** — no new Lean constructors:

- `∃ x, P` → `(.unNot (.forallEnum x en (.unNot translate(P))))` (canonical ¬∀¬ encoding)
- `No x, P` → `(.forallEnum x en (.unNot translate(P)))`
- `Exists` aliases `Some`.

The Lean meta-theorem covers these via the existing `forallEnum` and `unNot` arms; no new
mutual-induction lemma is needed. `EvalIR.eval` reduces the same way, so Scala and Lean compute
identical values for any quantifier shape.

Restrictions match `Quantifier(All)`: single binding over an enum-name identifier.

### M_L.4.c — UnaryOp(Cardinality) for state relations (closed in this PR)

`Expr.cardRel relName` is now in the verified subset. `eval` returns
`some (vInt (Int.ofNat dom.length))` when the relation resolves; `none` otherwise. The Smt mirror
`SmtTerm.cardRel` and per-case soundness theorem land in `Smt.lean` /`Soundness.lean`. The universal
`soundness` theorem covers the new arm via the existing `correlateModel_lookupRel` correlation lemma
plus `List.length_map`. Zero `sorry` maintained.

Scala mirror: classifier accepts `UnaryOp(Cardinality, Identifier(_))` only (mirrors
Translator.scala:876-881); `cert/EvalIR.scala` returns `BigInt(dom.length)`; `cert/Emit.scala`
renders `(.cardRel "rel")`.

`With` (`base with { f := v }`) is **deferred**: identity-collapse semantics would emit cert claims
like `eval (user with {name := "alice"}) = some user` which is false under any reasonable
record-update semantics. True `With` requires the StatePair refactor (M_L.4.b-ext) plus a Skolem
mirror of `Translator.scala:1061-1098`.

### M_L.4.b — Prime/Pre under single-state-collapse (closed in this PR)

`Expr.prime` and `Expr.pre` are now embedded in `SpecRest/IR.lean`. Single-state semantics: both
eval and translate treat them as identity wrappers (`eval s st env (.prime e) = eval s st env e`,
similarly for `.pre`; `translate (.prime e) = translate e`). Per-case soundness theorems for both
are trivial (delegate to the inner IH); the universal `soundness` theorem extends with two new arms.
Zero `sorry` maintained.

This is **honest single-state coverage**: it lets specs that use `pre(x)` or `x'` in
invariants/requires (where pre = post = current state) flow through the cert emitter without falling
out of subset. **Two-state preservation semantics** (`count' = count + 1` claiming
`count_post = count_pre + 1`) require the `StatePair { pre, post }` carrier refactor and a
mode-aware `evalAt` function. That work is **M_L.4.b-ext**, deferred — it cascades through every
existing per-case soundness theorem (~6-8 weeks). Until then, ensures-clause certs that reference
Prime/Pre will compile with a "current-state" interpretation that doesn't claim preservation.

Scala mirror: `cert/VerifiedSubset.scala` accepts `Prime`/`Pre` (recurses on inner);
`cert/EvalIR.scala` mirrors as identity; `cert/Emit.scala` renders `(.prime $inner)` /
`(.pre $inner)`.

### M_L.4.a — LIA arithmetic (closed in this PR)

`BinaryOp(Add | Sub | Mul | Div)` are now in the verified subset. Per-case soundness theorems ship
in `SpecRest/Soundness.lean` (`soundness_arith_{add,sub,mul}_ints`,
`soundness_arith_div_ints_{nonZero,zero}`). The universal `soundness` theorem dispatches the `arith`
case across all four ops and propagates `none` correctly on lhs/rhs eval failures and non-int
operands. `Div` semantics: `eval` returns `none` on divisor zero; Z3's `(div x 0)` is unspecified,
so the Lean theorem covers only runs where `eval` produces some.

Scala mirror: `cert/VerifiedSubset.scala` accepts `Add/Sub/Mul/Div`; `cert/EvalIR.scala` mirrors
`evalArith` with the same div-by-zero policy; `cert/Emit.scala` renders
`(.arith .{add|sub|mul|div} $lT $rT)`. Coverage uplift on real fixtures: `safe_counter`'s
`count + 1` and `count - 1` move from `trivial` stub to `cert_decide`; `broken_decrement`,
`auth_service.next_user_id + 1`, etc.

### Issue #195 — Set-valued algebra (closed)

Set-valued literals and algebra join the verified subset:

- `Value.vSet` / `SmtVal.sSet` carriers with list-backed, duplicate-normalised operations.
- `Expr.setEmpty`, `Expr.setInsert`, `Expr.setMember`, and `Expr.setBin` (`union`, `intersect`,
  `diff`) mirror the Scala cert emitter.
- `soundness_setInsert_resolved`, `soundness_setMember_resolved`, and `soundness_setBin_sets` close
  the success paths; universal `soundness` propagates `none` for non-set operands.

Scala mirror: `cert/VerifiedSubset.scala` accepts non-empty standalone `SetLiteral`, empty
set-literal membership, `Union`, `Intersect`, `Diff`, and set-expression `In`/`NotIn`;
`cert/EvalIR.scala` adds `Value.VSet`; `cert/Emit.scala` renders set literals as nested `.setInsert`
over `.setEmpty` and set algebra as `.setBin`.

## 3. Profile-deferred (later M_L.4 slices)

`SomeWrap`, `The`, `Call`, `If`, `Lambda`, `Constructor`, `MapLiteral`, `SetComprehension`,
`SeqLiteral`, `Matches`, `FloatLit`, `StringLit`, `NoneLit` — all `deferred`. (`BinaryOp(Subset)`
over rel identifiers closed in M_L.4.i.)

## 4. Permanently excluded

`UnaryOp(Power)` — translator already raises `TranslatorError`; outside FOL. `TemporalDecl` —
Alloy-routed, outside the Z3 theorem track.

## 5. Update rule

When a row moves between sections — or when `modules/ir/src/main/scala/specrest/ir/Types.scala`'s
`Expr` ADT changes shape — update this file in the same PR. The PR template in
`.github/PULL_REQUEST_TEMPLATE.md` carries the reminder.

If the move also changes the profile (`bootstrap` ↔ `first ship` ↔ `defer` ↔ `exclude`), the
matching row in `docs/content/docs/research/10_translator_soundness.md` §14 must move too.
