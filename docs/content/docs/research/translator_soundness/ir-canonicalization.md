---
title: "IR canonicalization"
description: "Canonicalizing the IR in Isabelle (issue #202)"
---

## 17. IR canonicalization in Isabelle (issue #202, post-#193)

**Status (2026-05-06):** shipped. PR #204 (Phases 0-7 + 9 + 10a) merged
2026-05-06; this close-out PR extends `lower` v2 over `QuantifierF` (4
kinds, multi-binding) + multi-field `WithF`, deletes the hand-rolled
`VerifiedSubset.classify` and `A8RoundTripOracleTest.toExtracted` walker,
refreshes docs. The Scala IR is now canonically extracted from
`proofs/isabelle/SpecRest/IR.thy`; the audit oracle calls extracted `lower`
directly. Issue #202 closed.

### 17.1 Decision: C-hybrid

We are migrating to a single canonical IR ADT in Isabelle, with Scala-side types
regenerated via `Code_Target_Scala`. The chosen architecture is **C-hybrid** rather than
pure C ("regenerate Scala from existing Isabelle `expr`"):

- Existing `proofs/isabelle/SpecRest/IR.thy` `expr` (23-ctor verified subset) **stays
  unchanged at the proof level**, the 94 lemmas in `Soundness.thy` are not re-opened.
- A **second ADT `expr_full`** (27 ctors mirroring the full Scala input language including
  Float/String/None/Lambda/Call/Constructor/MapLiteral/SeqLiteral/Matches/SomeWrap/The/
  SetComprehension and the broader `bin_op_full`/`un_op_full` enums) is added to the
  **same** `IR.thy`.
- A **lower function `lower :: String.literal list ⇒ expr_full ⇒ expr option`** projects
  the full IR onto the verified subset; out-of-subset constructors become `None`. The
  first argument is the list of declared enum names, needed because a
  `QuantifierBindingFull v (IdentifierF dom _) _ _` could resolve to either
  `ForallEnum` (enum domain) or `ForallRel` (relation domain), and `lower` cannot
  decide without schema context. `lower` is code-extracted; it replaced the hand-written
  Scala `VerifiedSubset.classify` and the test-side
  `A8RoundTripOracleTest.toExtracted` walker (both deleted in the #202 close-out).
- `expr_full` is the canonical input-language ADT for all Scala consumers (parser,
  codegen, testgen, translator). They consume it via a thin Scala wrapper layer in
  `modules/ir/.../Types.scala` (type aliases + `apply`/`unapply` + extensions) that
  preserves the existing ergonomics.

### 17.2 Why not pure c

The verified-subset `expr` is not a renaming of the Scala input ADT, it is a strict
subset (23 vs 27 ctors, op-specialized vs op-parametric, no
Float/String/None/Lambda/Call/Constructor/MapLiteral/SeqLiteral/Matches/SomeWrap/The/
SetComprehension/In/NotIn/Subset). Replacing the Scala IR wholesale with the verified
subset would delete input-language features. C-hybrid keeps the verified subset stable
while introducing `expr_full` for the input language and `lower` as the proven
projection.

### 17.3 Span handling, option (a) inline

Every ctor of both `expr` and `expr_full` carries a final `option_span` (= `span_t
option`) field, where `span_t` is a 4-int datatype. Span is `Prop`-erased, soundness
theorems do not inspect it; existing per-case lemmas absorb the new field as a wildcard.
No proof content changes; ~94 mechanical wildcard updates in `Soundness.thy` (Phase 2).

Rejected alternatives:
- Wrapper `Spanned[A]`: flattens inner-subexpression spans, regressing diagnostics.
- Parametric `'a expr`: re-triggers #193 Phase-5 record-polymorphism crash (`Illegal
  fixed variable 'a'`), well-known landmine.

### 17.4 No-new-files constraint

- All new Isabelle types and `lower`/`lower_reason` functions land in the existing
  `proofs/isabelle/SpecRest/IR.thy` (104 → ~750 LoC).
- `Soundness.thy` ripple (option_span wildcards) edits in place across `Semantics.thy`,
  `Smt.thy`, `Translate.thy`, `Soundness.thy`.
- `Codegen.thy` `export_code` list extends in place.
- `modules/verify/.../cert/generated/SpecRestGenerated.scala` was **moved** (not created)
  to `modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala` in #202
  Phase 4, required to avoid an `ir → verify` dep cycle when `ir` consumers import the
  extracted types.
- `modules/ir/.../Types.scala` is rewritten in place into a wrapper layer (~600-800 LoC:
  type aliases, `apply`/`unapply`, extensions, given `CanEqual`, hand pretty-printer).

### 17.5 Trigger

The 2026-05-02 trigger conditions in #202 §"Trigger conditions" (drift bug, second
emitter, ADT growth, dedicated infra contributor) are not met. This is being executed
as a user-directed architectural cleanup.

### 17.6 Phase status

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Decision document (this §17) | shipped (PR #204) |
| 1 | `expr_full` + 20 records + `span_t` in `IR.thy`; extraction smoke | shipped (PR #204) |
| 2 | `option_span` ripple across `IR`/`Semantics`/`Translate`/`Soundness` | shipped (PR #204) |
| 3 | `lower` v1 + `lower_set_list` + `lower_soundness` corollary | shipped (PR #204) |
| 4 | `Codegen.thy` export extension; relocate `SpecRestGenerated.scala` | shipped (PR #204) |
| 5 | Scala wrapper layer in `Types.scala` (extracted types used directly; thin layer) | shipped (PR #204) |
| 6 | Parser migration | shipped (PR #204) |
| 7 | Codegen + testgen migration | shipped (PR #204) |
| 8 | Verify migration | shipped (PR #204); `lower`-backed Trust dim. + `--strict-soundness` flag landed under #205 (Phase C-flagged); strict-soundness flipped to default-on and extracted-translator routing landed under #192 |
| 9 | CLI migration; delete legacy `Types.scala` enum | shipped (PR #204) |
| 10 | Audit cleanup; doc/CI sweep | 10a doc sweep shipped (PR #204); 10b audit cleanup + `lower` v2 (`QuantifierF` 4 kinds, multi-binding, multi-field `WithF`) shipped in close-out PR |

### 17.7 Trust surface (issues #205 → #192)

`lower` shipped as a runtime projection in #202; #205 wired it into the verifier surface
as a per-check **Trust dimension** behind a `--strict-soundness` flag; #192 promoted strict
soundness to the default and routed in-subset checks through the verified extracted
translator on the production verify path.

- `CheckResult.trust: TrustLevel`: `Sound` if every source `expr_full` for the check
  lowers to the verified subset; `BestEffort` otherwise. Visible in:
  - CLI line: `[sound]` / `[best-effort]` tag between the tool tag and the check id.
  - JSON report: `"trust": "sound" | "best-effort"` per check object. Schema bumped 1→2.
- Best-effort checks always skip with `category = soundness_limitation` before backend
  dispatch (no flag, this is the default since #192). The `--strict-soundness` flag and
  its CLI surface were retired.
- Sound checks route via extracted translator: `ExpressionEncoder.translateCheckedExpr`
  routes each check body through `SpecRestGenerated.translate` (the Isabelle-extracted
  function) and then `SmtTermBridge.encodeFromSmtTerm` to reach Z3. If the extracted
  translator returns `None`, the checked path fails the translation instead of falling back.
  The only fallback entrypoint is `translateDeclarationExpr`, used for declaration-level
  expressions such as entity field constraints and type-alias `where` clauses. Out-of-subset
  check-body shapes are filtered to skip by the Trust classifier before they reach the
  translator.
- Exit code 4 (`ExitStatus.Trust`): emitted when soundness skips are the only reason
  the run isn't clean. Subordinate to Backend (3), Violations (1), Translator (2).
- TCB audit lives at `docs/research/tcb_audit.md`, the single ledger of what
  `verify`'s verdicts actually depend on.

| Coverage v3 (post-#210) | `lower` returns | Trust |
|---|---|---|
| BoolLit / IntLit / Ident / EnumAccess / arithmetic / compare / boolean / Let / FieldAccess / Prime / Pre / cardinality on Ident / In/NotIn against Ident | `Some` | `Sound` |
| `QuantifierF` over enum or relation domain (4 kinds, multi-binding) | `Some` | `Sound` |
| Multi-field `WithF` (folded) over Identifier base | `Some` | `Sound` |
| `IndexF` over `IdentifierF` base (e.g. `users[uid]`) | `Some` | `Sound` |
| `IndexF` over `PreF (IdentifierF rel)` (e.g. `pre(orders)[id]`), M_L.4.l carrier widening | `Some` | `Sound` |
| `IndexF` over `PrimeF (IdentifierF rel)` (e.g. `orders'[id]`), M_L.4.l carrier widening | `Some` | `Sound` |
| `IndexF` over a non-relation-reference base (arithmetic, nested IndexF, etc.) | `None` | `BestEffort` |
| `BSubset`, `CallF`, `IfF`, `TheF`, `MapLiteralF`, `ConstructorF`, `SetComprehensionF`, etc. | `None` | `BestEffort` |

Issue [#210](https://github.com/HardMax71/spec_to_rest/issues/210) (M_L.4.l) widened the
verified-subset `IndexRel` carrier from `String.literal × expr` to `expr × expr` and
added a syntactic `peel_relation_ref` recogniser for the three
relation-reference shapes (`Ident`, `Pre Ident`, `Prime Ident`). The widening avoids the
`ir_value` cascade that a value-level `VRelation` would have triggered: `eval`/`smt_eval`
peel syntactically, the universal `soundness` theorem closes with one rewritten per-case
lemma (`index_rel_step`) plus a `peel_smt_relation_ref` ↔ `peel_relation_ref` correlation
lemma, and the production bridge resolves the relation name + state-mode at the
`TIndexRel` site (rather than inheriting from the surrounding `withStateMode` for bare
`IndexRel` shapes). `auth_service` and `broken_url_shortener` Tamper-style operations
flip from skipped (`category=soundness_limitation`) to real Z3 verdicts.

### 17.8 Risks tracked

- R1, Code-extraction quality on records: #193 Phase 5 hit `Illegal fixed variable
  'a'` on polymorphic record-scheme. The fix was `[code]`-marked `defs`. ~20 new records
  in this issue re-test the workaround. Phase 1 smoke-build is the trip-wire.
- R2, Wrapper-layer drift: ~600-800 LoC of `apply`/`unapply` in the Scala wrapper
  trades A1-A8 drift for wrapper-level drift. Mitigation: round-trip wrapper test
  exercising all 27 ctors.
- R3, Span-noise volume: ~94 `_` wildcard insertions across `Soundness.thy`. Buffer
  +1 day if `cases ... simp_all` patterns break.
- R4, `In/NotIn/Subset` desugar moves from Scala to Isabelle. Phase 3's `lower`
  performs the rewrite Isabelle-side. Defer proof obligation to a follow-up; keep the
  rewrite as a definition only in v1.
- R5, Diagnostic regression on `e.toString`: Hand pretty-printer in Phase 5
  mitigates; snapshot-diff vs current parser-error fixtures.
- R6, Native-image breakage: Wrapper layer + extracted types may need new
  reflect-config. Phase 10 sbt nativeImage smoke.
