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

| `Expr` case                                       | Profile stage | Lean status                                          |
| ------------------------------------------------- | ------------- | ---------------------------------------------------- |
| `BoolLit`                                         | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `IntLit`                                          | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `Identifier` (env-hit path)                       | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `Identifier` (env-miss / state-scalar-hit path)   | `bootstrap`   | `translated` (state-scalar-correlation lemma queued) |
| `BinaryOp(And)`                                   | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `BinaryOp(Or \| Implies \| Iff)`                  | `bootstrap`   | `translated` (queued for M_L.2 closure)              |
| `BinaryOp(Eq \| Neq)` (polymorphic over `Value`)  | `bootstrap`   | `translated`                                         |
| `BinaryOp(Lt \| Le \| Gt \| Ge)` (Int)            | `bootstrap`   | `translated`                                         |
| `BinaryOp(In)` (state-relation domain membership) | `bootstrap`   | `translated`                                         |
| `UnaryOp(Not)`                                    | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `UnaryOp(Negate)` (Int)                           | `bootstrap`   | `sound` (M_L.2 closed)                               |
| `Quantifier(All)` over enums                      | `bootstrap`   | `translated` (mutual-recursion lemma queued)         |
| `Let`                                             | `bootstrap`   | `translated`                                         |
| `EnumAccess`                                      | `bootstrap`   | `translated`                                         |

Per-operator denotation lemmas (the M_L.2 building blocks) live in `SpecRest/Lemmas.lean`. The
`safe_counter` invariant (`count ≥ 0`) is closed as a named theorem
(`safeCounter_invariant_holds_initially`) under a hand-built initial state, satisfying the §8.2
acceptance criterion.

M_L.2 (research doc §8.3): the `translate : Expr → SmtTerm` function, the shallow SMT embedding
(`SmtTerm`, `SmtVal`, `SmtModel`, `smtEval`), and the correlation functions (`valueToSmt`,
`correlateEnv`, `correlateModel`) ship in `SpecRest/Smt.lean`, `SpecRest/Translate.lean`, and
`SpecRest/Soundness.lean`. Per-case soundness theorems are proved for `BoolLit`, `IntLit`,
`Identifier` (env-hit), `UnaryOp(Not)`, `UnaryOp(Negate)`, and `BinaryOp(And)`. The overall
`soundness` meta-theorem stands as a `sorry`-gated placeholder; the remaining cases (Or/Implies/Iff,
all `cmp`, `letIn`, `enumAccess`, `member`, `forallEnum`, plus the state-scalar identifier path)
follow the same shape and are queued for follow-up M_L.2 closure PRs.

**M_L.3** (research doc §8.4): the per-run translation-validation certificate emitter ships in the
Scala module under `modules/verify/src/main/scala/specrest/verify/cert/` (`Emit.scala`,
`VerifiedSubset.scala`); the supporting Lean tactic library lives in
`proofs/lean/SpecRest/Cert.lean`. For every invariant in a `ServiceIR`, the emitter produces a Lean
source file with one `theorem cert_invariant_<idx>_<name>` per invariant. In-subset cases discharge
via `cert_decide` (an alias for `native_decide`); out-of-subset cases emit a `trivial`-stub with a
`TODO[M_L.4]` marker naming the offending operator. The CLI `verify --emit-cert <dir>` flag and the
per-fixture CI matrix are follow-up tickets.

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

## 3. Profile-deferred (later M_L.4 slices)

`BinaryOp(Add | Sub | Mul | Div)`, `BinaryOp(Subset | Union | Intersect | Diff)`, `SomeWrap`, `The`,
`Call`, `If`, `Lambda`, `Constructor`, `SetLiteral`, `MapLiteral`, `SetComprehension`, `SeqLiteral`,
`Matches`, `FloatLit`, `StringLit`, `NoneLit` — all `deferred`.

## 4. Permanently excluded

`UnaryOp(Power)` — translator already raises `TranslatorError`; outside FOL. `TemporalDecl` —
Alloy-routed, outside the Z3 theorem track.

## 5. Update rule

When a row moves between sections — or when `modules/ir/src/main/scala/specrest/ir/Types.scala`'s
`Expr` ADT changes shape — update this file in the same PR. The PR template in
`.github/PULL_REQUEST_TEMPLATE.md` carries the reminder.

If the move also changes the profile (`bootstrap` ↔ `first ship` ↔ `defer` ↔ `exclude`), the
matching row in `docs/content/docs/research/13_global_proof_profile.md` must move too.
