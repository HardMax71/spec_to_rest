# SpecRest Proof-State Ledger

> **First-ship gate met (2026-05-02).** The universal `soundness` meta-theorem is closed with **zero
> `sorry`** for the §6.1 verified subset extended through M_L.4.a-i. The Z3 translator's output is
> mechanically validated against the Lean `translate` function for every in-subset `Expr`. See
> `docs/content/docs/research/10_translator_soundness.md` §13.1 for the formal claim and §16.6 for
> the activation closure record.

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

Last sync with the consolidated profile (now §14 of `10_translator_soundness.md`): M_L.4.a-i shipped
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
| `BinaryOp(NotIn)` (composition `¬In`)             | `first ship`  | `sound` (M_L.4.e)       |
| `BinaryOp(Subset)` over rel-identifiers           | `first ship`  | `sound` (M_L.4.i)       |
| `UnaryOp(Not)`                                    | `bootstrap`   | `sound` (M_L.2 closed)  |
| `UnaryOp(Negate)` (Int)                           | `bootstrap`   | `sound` (M_L.2 closed)  |
| `Quantifier(All)` over enums                      | `bootstrap`   | `sound` (M_L.2 closure) |
| `Quantifier(All)` over state-relation domains     | `first ship`  | `sound` (M_L.4.f)       |
| `Index` over state-relation pairs                 | `first ship`  | `sound` (M_L.4.g)       |
| `FieldAccess` on entity-typed state scalars       | `first ship`  | `sound` (M_L.4.h)       |
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
refactor (M_L.4.b-ext).

| `Expr` case                                    | Profile stage | Status     |
| ---------------------------------------------- | ------------- | ---------- |
| `With`                                         | `first ship`  | `deferred` |
| Two-state coupling via `OperationDecl.ensures` | `first ship`  | `deferred` |

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

### M_L.4.h — FieldAccess on entity-typed state scalars (closed in this PR)

`Expr.FieldAccess(Identifier(scalar), field)` — bare-Identifier `state_scalar.field` — joins the
verified subset. Strictly-additive carrier extension parallel to M_L.4.g:

- `State.entityFields : List (String × List (String × Value))` — keyed by scalar name, carries that
  instance's field-value bindings.
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

Restricted to bare-Identifier base (`current_user.email`). `Index(...).field`,
`FieldAccess(FieldAccess(...), ...)`, etc. remain `OutOfSubset` until a future slice extends the
carrier (or composes via Index lookup of an entity-valued pair).

`EvalIR.State.demo` populates `entityFields` with empty bindings for entity-typed state scalars
(parallel to lookups bootstrap), so demo-state FieldAccess returns `none` cleanly.

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

## 3. Profile-deferred (later M_L.4 slices)

`BinaryOp(Union | Intersect | Diff)` (set-valued, need `Value.VSet` extension), `SomeWrap`, `The`,
`Call`, `If`, `Lambda`, `Constructor`, `SetLiteral`, `MapLiteral`, `SetComprehension`, `SeqLiteral`,
`Matches`, `FloatLit`, `StringLit`, `NoneLit` — all `deferred`. (`BinaryOp(Subset)` over rel
identifiers closed in M_L.4.i.)

## 4. Permanently excluded

`UnaryOp(Power)` — translator already raises `TranslatorError`; outside FOL. `TemporalDecl` —
Alloy-routed, outside the Z3 theorem track.

## 5. Update rule

When a row moves between sections — or when `modules/ir/src/main/scala/specrest/ir/Types.scala`'s
`Expr` ADT changes shape — update this file in the same PR. The PR template in
`.github/PULL_REQUEST_TEMPLATE.md` carries the reminder.

If the move also changes the profile (`bootstrap` ↔ `first ship` ↔ `defer` ↔ `exclude`), the
matching row in `docs/content/docs/research/10_translator_soundness.md` §14 must move too.
