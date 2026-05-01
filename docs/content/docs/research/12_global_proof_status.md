---
title: "Global Proof Status"
description: "Live ledger for proof-governed surfaces, proof-state labels, and drift-control checkpoints"
---

> Live ledger for issues [#172](https://github.com/HardMax71/spec_to_rest/issues/172),
> [#174](https://github.com/HardMax71/spec_to_rest/issues/174), and
> [#175](https://github.com/HardMax71/spec_to_rest/issues/175).
> Update this file whenever a proof-governed surface moves.

## 1. Current Baseline

- Governance mode: **execution track active, proof workspace covers the full
  `M_L.1` verified subset (research doc §6.1) plus `M_L.2` foundations
  (research doc §8.3)**
- Initialized against `origin/main` commit `3aa6938` on `2026-05-01`; refreshed
  against `010f9b8` for the `M_L.0` kickoff and against `2f8d659` for the
  `M_L.1` merge. The `M_L.2` foundations slice builds on top of `2f8d659`; the
  baseline is re-pinned post-merge after `M_L.2` lands.
- First theorem target: in-memory `ServiceIR → Z3Script` path used by
  `Consistency.runConsistencyChecks`
- Active proof-safe profile: [`13_global_proof_profile`](/research/13_global_proof_profile)
- Proof owner: [HardMax71](https://github.com/HardMax71)
- Runway mode: one six-week proof-priority cycle with fixed time / variable scope, per
  [`14_global_proof_runway`](/research/14_global_proof_runway)
- Execution-track activation: `M_L.*` activated by
  [`15_global_proof_activation`](/research/15_global_proof_activation)
- Still outside the first ship claim: `SmtLib.scala`, dump/export paths, Alloy-routed checks,
  proof replay, and full-source semantics refinement

## 2. Status Labels

| Label | Meaning |
| --- | --- |
| `tracked` | The surface is governed and changes must be logged now. |
| `mirrored` | A prover-side mirror exists, but the corresponding theorem is not complete. |
| `fragment-proved` | A bounded fragment is mechanically proved for that surface. |
| `ship-claim-ready` | The surface is covered by the first honest public proof claim. |
| `reserved` | The surface is part of the planned proof toolchain but is not active yet. |

## 3. Governed Surface Ledger

| Surface | Class | Status | Current note |
| --- | --- | --- | --- |
| `modules/ir/src/main/scala/specrest/ir/Types.scala` | Proof-owned core | `tracked` | Any AST change can invalidate semantics, mirror coverage, or the proof-safe profile. |
| `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala` | Proof-owned core | `tracked` | Main proof target. New cases or changed encodings must be logged even before prover work starts. |
| `modules/verify/src/main/scala/specrest/verify/z3/Types.scala` | Proof-owned core | `tracked` | `Z3Expr` / `Z3Script` shape is part of the first theorem target. |
| `docs/content/docs/research/13_global_proof_profile.md` | Proof-scope artifact | `tracked` | This is the committed first-scope boundary for `M_G.2`; scope drift must be explicit. |
| `docs/content/docs/research/14_global_proof_runway.md` | Program-commitment artifact | `tracked` | Owner, priority runway, paused work, and stall policy must stay explicit while the proof program is active. |
| `docs/content/docs/research/15_global_proof_activation.md` | Program-commitment artifact | `tracked` | Activation state and first kickoff shape must stay aligned with the actual `M_L.*` chain. |
| `modules/verify/src/main/scala/specrest/verify/Classifier.scala` | Obligation contract | `tracked` | Routing changes can move checks in or out of the Z3 proof scope. |
| `modules/verify/src/main/scala/specrest/verify/Consistency.scala` | Obligation contract | `tracked` | Changes here can redefine the meaning of global, requires, enabled, or preservation checks. |
| `modules/parser/src/main/scala/specrest/parser/Parse.scala` | TCB-sensitive | `tracked` | Parser remains trusted for first ship; changes alter the honest source-to-IR trust story. |
| `modules/parser/src/main/scala/specrest/parser/Builder.scala` | TCB-sensitive | `tracked` | IR builder remains trusted for first ship; changes can move the boundary under the theorem. |
| `modules/verify/src/main/scala/specrest/verify/z3/Backend.scala` | TCB-sensitive | `tracked` | Runtime Z3 AST rendering is in the first-ship TCB. |
| `proofs/lean/**` | Active proof workspace | `mirrored` | Full M_L.1 verified subset embedded: `BinaryOp(In)`, `Quantifier(All)` over enums, polymorphic `Eq`/`Neq` over `Value`, entity-typed values, and explicit `State` carrier. Per-operator denotation lemmas in `SpecRest/Lemmas.lean`. `safe_counter` invariant proved as a named theorem. M_L.2 foundations (Smt embedding + translator + per-case soundness for atoms/Not/Negate/And) ship in `SpecRest/{Smt,Translate,Soundness}.lean`. The universal `soundness` theorem is `sorry`-gated; closure follow-ups land the remaining cases (Or/Implies/Iff, full cmp, letIn, enumAccess, member, forallEnum). |
| `proofs/lean/STATUS.md` | Proof-state ledger | `tracked` | Per-`Expr`-case mirror of `13_global_proof_profile.md`; PR template requires re-sync on `Expr` changes. |
| `.github/PULL_REQUEST_TEMPLATE.md` | Proof-program contract | `tracked` | Carries the `Expr`-touch reminder that fans out to `IR.lean.todo`, `STATUS.md`, profile, and this ledger. |
| `proofs/lean/SpecRest/Lemmas.lean` | Proof-owned core | `tracked` | Per-operator denotation lemmas (the `M_L.2` building blocks). New `Expr` cases must add a corresponding lemma here. |
| `proofs/lean/SpecRest/Smt.lean` | Proof-owned core | `tracked` | Shallow SMT-LIB embedding with `smtEval` + per-constructor characterization lemmas. New `Expr` cases that translate to new `SmtTerm` shapes must extend the ADT here. |
| `proofs/lean/SpecRest/Translate.lean` | Proof-owned core | `tracked` | `translate : Expr → SmtTerm` mirror of `z3.Translator.scala`. Must stay aligned case-for-case with the Scala translator on the verified subset. |
| `proofs/lean/SpecRest/Soundness.lean` | Proof-owned core | `tracked` | Per-case soundness theorems + universal `soundness` meta-theorem. New translation cases must add a per-case soundness theorem before being declared `sound` in `STATUS.md`. |
| `proofs/lean/SpecRest/Cert.lean` | Proof-owned core | `tracked` | M_L.3 certificate-tactic library. The `cert_decide` macro is what every emitted certificate calls; changing it changes the trust closure of every per-run cert. |
| `modules/verify/src/main/scala/specrest/verify/cert/Emit.scala` | Proof-owned core | `tracked` | M_L.3 per-run certificate emitter. Mirrors the M_L.1 IR shape into Lean source; new IR `Expr` cases that join the verified subset must extend `renderExpr` here. |
| `modules/verify/src/main/scala/specrest/verify/cert/VerifiedSubset.scala` | Proof-owned core | `tracked` | Per-`Expr`-case predicate driving `Emit.scala`'s subset gating. Must stay aligned with `13_global_proof_profile.md` and `proofs/lean/STATUS.md`. |
| Scala↔prover mirror coverage table | Live proof artifact | `tracked` | Audit appendix in `proofs/lean/README.md` lists Lean ↔ Scala translator mappings; M_L.2 closure PRs may add line-range pins to specific Scala translator clauses. |

## 4. Update Rules

When this file changes, the entry should say at least:

1. which governed surface moved,
2. whether the move changed syntax, semantics, routing, or only the trusted boundary,
3. whether the first `M_G.0` theorem statement still reads honestly afterward.

If the answer to `3` is "no", the matching PR must also update
[`11_global_proof_governance`](/research/11_global_proof_governance) or the governing issue.

If the change moves a feature between `bootstrap`, `first ship`, `defer`, or `exclude`, it must
also update [`13_global_proof_profile`](/research/13_global_proof_profile).

If the change alters owner, runway, paused roadmap lanes, or the stall rule, it must also update
[`14_global_proof_runway`](/research/14_global_proof_runway).

If the change alters activation state or the first kickoff shape, it must also update
[`15_global_proof_activation`](/research/15_global_proof_activation).
