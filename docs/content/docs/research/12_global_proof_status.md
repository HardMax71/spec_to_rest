---
title: "Global Proof Status"
description: "Live ledger for proof-governed surfaces, proof-state labels, and drift-control checkpoints"
---

> Live ledger for issues [#172](https://github.com/HardMax71/spec_to_rest/issues/172) and
> [#174](https://github.com/HardMax71/spec_to_rest/issues/174).
> Update this file whenever a proof-governed surface moves.

## 1. Current Baseline

- Governance mode: **controlled churn**
- Initialized against `origin/main` commit `3aa6938` on `2026-05-01`
- First theorem target: in-memory `ServiceIR → Z3Script` path used by
  `Consistency.runConsistencyChecks`
- Active proof-safe profile: [`13_global_proof_profile`](/research/13_global_proof_profile)
- Proof owner: [HardMax71](https://github.com/HardMax71)
- Runway mode: one six-week proof-priority cycle with fixed time / variable scope, per
  [`14_global_proof_runway`](/research/14_global_proof_runway)
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
| `modules/verify/src/main/scala/specrest/verify/Classifier.scala` | Obligation contract | `tracked` | Routing changes can move checks in or out of the Z3 proof scope. |
| `modules/verify/src/main/scala/specrest/verify/Consistency.scala` | Obligation contract | `tracked` | Changes here can redefine the meaning of global, requires, enabled, or preservation checks. |
| `modules/parser/src/main/scala/specrest/parser/Parse.scala` | TCB-sensitive | `tracked` | Parser remains trusted for first ship; changes alter the honest source-to-IR trust story. |
| `modules/parser/src/main/scala/specrest/parser/Builder.scala` | TCB-sensitive | `tracked` | IR builder remains trusted for first ship; changes can move the boundary under the theorem. |
| `modules/verify/src/main/scala/specrest/verify/z3/Backend.scala` | TCB-sensitive | `tracked` | Runtime Z3 AST rendering is in the first-ship TCB. |
| `proofs/lean/**` | Future proof workspace | `reserved` | Becomes active only when `M_G.4` opens the execution track. |
| Scala↔prover mirror coverage table | Future proof artifact | `reserved` | Created with the first prover-side mirror. |

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
