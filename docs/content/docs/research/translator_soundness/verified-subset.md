---
title: "The verified subset"
description: "Which constructs the soundness theorem covers, and translator coverage"
---

## 6. The verified subset

Anchored to the [codebase analysis](#a-codebase-translator-coverage-april-2026).
Per April 2026, `Translator.scala` covers 13/25 `Expr` cases fully, 8 partially,
4 with `TranslatorError`. The verified subset for M_L.1 picks the smallest set
that exercises the four core SMT pillars:

- Boolean reasoning (∧, ∨, ⇒, ¬)
- Linear integer arithmetic (=, &lt;)
- Uninterpreted predicates (membership in a state relation)
- Bounded quantification (over enums; over fixed-size entity collections)

### 6.1 Operators in scope (M_L.1)

| Expr case | Why included |
|---|---|
| `BinaryOp(And, _, _)` | Boolean conjunction |
| `BinaryOp(Or, _, _)` | Boolean disjunction |
| `BinaryOp(Implies, _, _)` | Required for invariants and ensures |
| `BinaryOp(Eq, _, _)` | On `Int` and on entity-typed values |
| `BinaryOp(Lt, _, _)` | LIA comparison |
| `BinaryOp(In, _, _)` | Membership in a state relation domain |
| `UnaryOp(Not, _)` | Boolean negation |
| `UnaryOp(Negate, _)` | LIA negation (`0 - x`) |
| `Quantifier(All, _, _)` over enums | Universal binding |
| `Let(_, _, _)` | Local binding |
| `IntLit`, `BoolLit`, `Identifier` | Atoms |

Plus the IR top-level shells:

- `EntityDecl` (flat, no inheritance, no per-field constraints)
- `EnumDecl`
- `StateDecl` with **scalar fields and simple domain-typed relations** only
- `OperationDecl` with `requires` and `ensures` (no state mutation in M_L.1; M_L.2
  adds `Prime`/`Pre`)
- `InvariantDecl` (single conjunctive predicate)

### 6.2 Operators explicitly out of scope (deferred)

| Excluded | Rationale |
|---|---|
| `BinaryOp(Add\|Sub\|Mul\|Div, ...)` | Defer to M_L.2; need carrier-set proof for Int |
| `BinaryOp(Subset, ...)` | State-relation identifier subset is in subset; arbitrary set subset remains deferred |
| `BinaryOp(Union\|Intersect\|Diff, ...)` | Closed for set-valued expressions in issue #195 |
| `UnaryOp(Cardinality)` | Currently only on state relations; defer to state-mutation milestone |
| `UnaryOp(Power)` | Translator already raises `TranslatorError` (undecidable in FOL), permanent exclusion |
| `Quantifier(_, _, _)` over entity collections | Defer to M_L.2 (needs frame axioms) |
| `SetComprehension`, `MapLiteral`, `SeqLiteral` | Out of scope until collections milestone |
| `SetLiteral` | Closed for finite literals in issue #195 |
| `If`, `Lambda`, `Constructor`, `SomeWrap`, `The`, `NoneLit` | Translator already raises `TranslatorError` for most |
| `Index`, `Call`, `EnumAccess` (dynamic), `With`, `Matches` | Defer; require advanced encoding |
| `Prime`, `Pre` | M_L.2 adds two-state coupling |
| `FieldAccess` | M_L.2 (records subsumed by entity decls) |
| `TransitionDecl`, `TemporalDecl`, `FunctionDecl`, `PredicateDecl` | Out of scope; lives in separate temporal/derived-logic milestones |

This subset hits **~10 operators**, **~20 LOC of Lean per operator** for the
denotation, and admits an automatic-decidability story (every quantifier in scope
is over a finite domain, so `eval` returns `Bool` instead of `Option Bool` for
many branches).

### 6.3 Sort encoding in M_L.1's translator

Mirroring `Translator.scala` choices, simplified:

- Bool, Int: SMT-LIB native sorts.
- Enums: uninterpreted sort + member constants + distinctness axiom.
  Cardinality finite and known.
- Entities: uninterpreted sort + per-field accessor function (`Entity_field :
  Entity → FieldSort`). No datatype constructors in M_L.1 (avoids cvc5-Alethe
  blocker; matches Cohen's "ADT axiomatization" pattern).
- State: tuple of scalar functions (no maps, no relations beyond domain
  membership predicates) in M_L.1. M_L.2 expands.

## A. codebase translator coverage (april 2026)

Snapshot (April 2026) of `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala`
(1917 LOC) measured against the 25-case `Expr` ADT that lived in the (now-deleted) hand-written
`modules/ir/src/main/scala/specrest/ir/Types.scala`. Used to define the verified subset in §6.
Post-#202 the canonical IR is the extracted `expr_full` (27 ctors) in
`modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala`, with the verified
subset surfaced as `expr` in the same file; this appendix is preserved as a historical
snapshot of the pre-canonicalization translator coverage.

| `Expr` case | Translator status | Notes |
|---|---|---|
| `BinaryOp(And\|Or\|Implies\|Iff)` | full | direct mapping (lines 629-633) |
| `BinaryOp(Eq\|Neq)` | full | dom/set-comprehension equality special-cased (616-621), fallback `Z3Expr.Cmp` (643) |
| `BinaryOp(Lt\|Gt\|Le\|Ge)` | full | `CmpOp` mapping (634-643) |
| `BinaryOp(In\|NotIn)` | full | membership via state relation `dom`, set literal, set membership (622-757) |
| `BinaryOp(Subset\|Union\|Intersect\|Diff)` | full | with sort validation; fails on non-`SetOf` (661-693) |
| `BinaryOp(Add\|Sub\|Mul\|Div)` | partial | integers only; fails on string/set arithmetic (650-653) |
| `UnaryOp(Not)` | full | direct `Z3Expr.Not` (866) |
| `UnaryOp(Negate)` | full | as `0 - operand` (867-868) |
| `UnaryOp(Cardinality)` | partial | only on state-relation identifiers (876-881) |
| `UnaryOp(Power)` | errored | "powerset operator is not decidable in first-order SMT" (870-874) |
| `Quantifier` | full | ∀/∃ supported; ∄ encoded as ¬∃ (905-930) |
| `SomeWrap` | errored | catchall (597-601) |
| `The` | errored | catchall (597-601) |
| `FieldAccess` | full | via entity field functions (971-995) |
| `EnumAccess` | full | via enum member constants (1132-1142) |
| `Index` | partial | only on state-relation references (997-1009) |
| `Call` | partial | only identifier callee; hardcoded builtins (`len`, `isValidURI`); higher-order fails (1011-1032) |
| `Prime` / `Pre` | full | state-mode switching for post/pre-state (589-592) |
| `With` | full | record update via Skolem constant + equality constraints (1061-1098) |
| `SetComprehension` | errored | only allowed inside membership; standalone fails (1100-1105) |
| `SetLiteral` | partial | non-empty literals; empty literals require type context and are covered in membership contexts |
| `MapLiteral` | errored | catchall (597-601) |
| `SeqLiteral` | errored | catchall (597-601) |
| `Matches` | full | as uninterpreted predicate, mangled by pattern + arg sort (1037-1049) |
| `IntLit`, `BoolLit`, `StringLit` | full | direct literals or constants (577-579) |
| `NoneLit` | errored | catchall (597-601) |
| `Constructor` | errored | catchall (597-601) |
| `If` | errored | catchall (597-601) |
| `Lambda` | errored | catchall (597-601) |
| `Let` | full | environment extension (1051-1059) |

**Summary**: 13 fully handled, 8 partial, 4 errored. The verified subset (§6.1) draws
exclusively from the "fully handled" column, restricted further to operators whose
Lean denotation is &lt;30 LOC each.

**Sort-encoding decisions** mirrored in M_L.1's `IR.lean`:

- Entities: uninterpreted sort `U:EntityName` + accessor functions
  `EntityName_fieldName : EntityName → FieldSort` (319-328).
- Enums: uninterpreted sort + member constants + distinctness axioms (278-283).
- Options: flattened to inner type (`OptionType(T)` → `T`, line 541).
- Sets: SMT-LIB `(Set elemSort)` with store/select (`SmtLib.scala:66-72`).
- Strings: uninterpreted sort + fresh constants per literal + distinctness (529-537).
- State relations (RelationType/MapType): pair of functions
  `<field>_dom : K → Bool` and `<field>_map : K → V` (465-495).
- Scalar state fields: single constant function `state_<field> : () → T` (497-501).
- Post-state: same encoding with `_post` suffix; toggled via `ctx.stateMode`
  (39-40, 477-478, 503-510, 589-592, 603-607).
