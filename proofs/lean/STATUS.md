# SpecRest Proof-State Ledger

Mirrors `docs/content/docs/research/13_global_proof_profile.md` at expression-case granularity. Each
row records what the prover-side embedding actually covers right now, distinct from the
proof-program intent.

Status meanings, aligned with `docs/content/docs/research/12_global_proof_status.md`:

| Label        | Meaning                                                                   |
| ------------ | ------------------------------------------------------------------------- |
| `embedded`   | The Lean `Expr` constructor exists, `eval` covers it, named lemmas exist. |
| `translated` | `translate` mirrors the Scala translator on this case (pre-soundness).    |
| `mirrored`   | A prover-side mirror exists, awaiting an `M_L.2` soundness theorem.       |
| `sound`      | An `M_L.2` per-case soundness theorem closes for this constructor.        |
| `deferred`   | Not yet embedded; queued in `SpecRest/IR.lean.todo`.                      |
| `excluded`   | Permanently outside the Z3 global-theorem track.                          |

Last sync with `13_global_proof_profile.md`: commit `a430ddc` (2026-05-01, M_L.0 merge).

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
| `UnaryOp(Not)`                                    | `bootstrap`   | `sound` (M_L.2 closed)  |
| `UnaryOp(Negate)` (Int)                           | `bootstrap`   | `sound` (M_L.2 closed)  |
| `Quantifier(All)` over enums                      | `bootstrap`   | `sound` (M_L.2 closure) |
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

## 2. First-ship targets queued for M_L.2

| `Expr` case                                    | Profile stage | Status     |
| ---------------------------------------------- | ------------- | ---------- |
| `Prime`                                        | `first ship`  | `deferred` |
| `Pre`                                          | `first ship`  | `deferred` |
| `With`                                         | `first ship`  | `deferred` |
| `UnaryOp(Cardinality)` over state relations    | `first ship`  | `deferred` |
| `FieldAccess` (entity-valued)                  | `first ship`  | `deferred` |
| `Index` (state-relation reference)             | `first ship`  | `deferred` |
| `Quantifier(Some)` over enums                  | `first ship`  | `deferred` |
| Two-state coupling via `OperationDecl.ensures` | `first ship`  | `deferred` |

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

`BinaryOp(Subset | Union | Intersect | Diff)`, `SomeWrap`, `The`, `Call`, `If`, `Lambda`,
`Constructor`, `SetLiteral`, `MapLiteral`, `SetComprehension`, `SeqLiteral`, `Matches`, `FloatLit`,
`StringLit`, `NoneLit` — all `deferred`.

## 4. Permanently excluded

`UnaryOp(Power)` — translator already raises `TranslatorError`; outside FOL. `TemporalDecl` —
Alloy-routed, outside the Z3 theorem track.

## 5. Update rule

When a row moves between sections — or when `modules/ir/src/main/scala/specrest/ir/Types.scala`'s
`Expr` ADT changes shape — update this file in the same PR. The PR template in
`.github/PULL_REQUEST_TEMPLATE.md` carries the reminder.

If the move also changes the profile (`bootstrap` ↔ `first ship` ↔ `defer` ↔ `exclude`), the
matching row in `docs/content/docs/research/13_global_proof_profile.md` must move too.
