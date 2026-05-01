# SpecRest Proof-State Ledger

Mirrors `docs/content/docs/research/13_global_proof_profile.md` at expression- case granularity.
Each row records what the prover-side embedding actually covers right now, distinct from the
proof-program intent.

Status meanings, aligned with `docs/content/docs/research/12_global_proof_status.md`:

| Label      | Meaning                                                    |
| ---------- | ---------------------------------------------------------- |
| `embedded` | The Lean `Expr` constructor exists and `eval` covers it.   |
| `mirrored` | A prover-side mirror exists, awaiting a soundness theorem. |
| `deferred` | Not yet embedded; queued in `SpecRest/IR.lean.todo`.       |
| `excluded` | Permanently outside the Z3 global-theorem track.           |

Last sync with `13_global_proof_profile.md`: commit `010f9b8` (2026-05-01).

## 1. Bootstrap slice (this PR)

| `Expr` case                                                | Profile stage | Lean status |
| ---------------------------------------------------------- | ------------- | ----------- |
| `BoolLit`                                                  | `bootstrap`   | `embedded`  |
| `IntLit`                                                   | `bootstrap`   | `embedded`  |
| `Identifier`                                               | `bootstrap`   | `embedded`  |
| `BinaryOp(And \| Or \| Implies \| Iff)`                    | `bootstrap`   | `embedded`  |
| `BinaryOp(Eq \| Neq \| Lt \| Le \| Gt \| Ge)` (over `Int`) | `bootstrap`   | `embedded`  |
| `UnaryOp(Not)`                                             | `bootstrap`   | `embedded`  |
| `UnaryOp(Negate)`                                          | `bootstrap`   | `embedded`  |
| `Let`                                                      | `bootstrap`   | `embedded`  |
| `EnumAccess`                                               | `bootstrap`   | `embedded`  |

Declaration shells embedded as Lean structures (no semantics beyond carrier shape): `EnumDecl`,
`StateDecl`, `OperationDecl` (`requires` only), `InvariantDecl`, `ServiceIR`. State semantics are
deferred until `Prime` / `Pre` land — see queued items below.

## 2. Queued for the next M_L.1 slice

| `Expr` case                                | Profile stage | Reason deferred from this PR                                                                                                 |
| ------------------------------------------ | ------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `Quantifier(All \| Some)` over enums       | `bootstrap`   | Needs mutual recursion across `eval` and per-member fold; tracked separately to keep this slice's termination story trivial. |
| `BinaryOp(In)` over state-relation domains | `bootstrap`   | Requires a state-carrier sketch; the profile ties it to relation membership only.                                            |
| `FieldAccess` (entity-valued)              | `bootstrap`   | Needs entity-field accessor encoding before semantics.                                                                       |
| `Index` (state-relation reference)         | `bootstrap`   | Same prerequisite as `FieldAccess`.                                                                                          |

## 3. First-ship targets (M_L.2 / M_L.3)

| `Expr` case                                 | Profile stage | Status     |
| ------------------------------------------- | ------------- | ---------- |
| `Prime`                                     | `first ship`  | `deferred` |
| `Pre`                                       | `first ship`  | `deferred` |
| `With`                                      | `first ship`  | `deferred` |
| `UnaryOp(Cardinality)` over state relations | `first ship`  | `deferred` |

## 4. Profile-deferred (later M_L.4 slices)

`BinaryOp(Add | Sub | Mul | Div)`, `BinaryOp(Subset | Union | Intersect | Diff)`, `SomeWrap`, `The`,
`Call`, `If`, `Lambda`, `Constructor`, `SetLiteral`, `MapLiteral`, `SetComprehension`, `SeqLiteral`,
`Matches`, `FloatLit`, `StringLit`, `NoneLit` — all `deferred`.

## 5. Permanently excluded

`UnaryOp(Power)` — translator already raises `TranslatorError`; outside FOL. `TemporalDecl` —
Alloy-routed, outside the Z3 theorem track.

## 6. Update rule

When a row moves between sections — or when `modules/ir/src/main/scala/specrest/ir/Types.scala`'s
`Expr` ADT changes shape — update this file in the same PR. The PR template in
`.github/PULL_REQUEST_TEMPLATE.md` carries the reminder.

If the move also changes the profile (`bootstrap` ↔ `first ship` ↔ `defer` ↔ `exclude`), the
matching row in `docs/content/docs/research/13_global_proof_profile.md` must move too.
