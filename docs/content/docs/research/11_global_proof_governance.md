---
title: "Global Proof Governance"
description: "Proof-governed surfaces, change rules, and churn policy for the translator soundness program"
---

> Governance doc for issues [#170](https://github.com/HardMax71/spec_to_rest/issues/170),
> [#171](https://github.com/HardMax71/spec_to_rest/issues/171),
> [#172](https://github.com/HardMax71/spec_to_rest/issues/172), and
> [#174](https://github.com/HardMax71/spec_to_rest/issues/174).
> Defines which repo surfaces are proof-governed before the `M_L.*` execution track starts.

## 1. Why this exists

`#88` is only tractable if the proof target stops moving invisibly.

For this repo, the failure mode is not "someone merges a bad theorem." The failure mode is
earlier: `Types.scala`, the Z3 translator, or the routing/orchestration contract changes while
the proof effort still assumes the old shape. That is how a global-proof project turns into a
long-lived side quest with no honest ship claim.

`M_G.1` therefore governs the proof target before `proofs/lean/` exists. The goal is not to
freeze the whole repo. The goal is to make target movement explicit.

## 2. Governance Mode

The current mode is **controlled churn**.

- The repo is not in a hard IR freeze yet.
- Changes to proof-governed surfaces are still allowed.
- Those changes should carry their proof impact in the same PR.
- `proofs/lean/` stays unopened until `M_G.4` activates the `M_L.*` execution track.

This is intentionally more explicit than "remember to mention it in an issue" and weaker than "no
one touches the IR until the proof ships."

## 3. Proof-Governed Surfaces

The surfaces below are governed now. They are split by why they matter.

| Class | Surface | Why it is governed |
| --- | --- | --- |
| Proof-owned core | `modules/ir/src/main/scala/specrest/ir/Types.scala` | Defines `Expr`, `TypeExpr`, `ServiceIR`, and the AST shape the proof will mirror. |
| Proof-owned core | `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala` | Main translation function the prover-side mirror will track case-for-case. |
| Proof-owned core | `modules/verify/src/main/scala/specrest/verify/z3/Types.scala` | Defines `Z3Script`, `Z3Expr`, and artifact structures in the first theorem target. |
| Proof-scope artifact | `docs/content/docs/research/13_global_proof_profile.md` | Declares which fragment and backend contract the first ship claim actually covers. |
| Program-commitment artifact | `docs/content/docs/research/14_global_proof_runway.md` | Records owner, priority runway, paused work, and the stall rule that gate activation of `M_L.*`. |
| Obligation contract | `modules/verify/src/main/scala/specrest/verify/Classifier.scala` | Decides which checks are even in the Z3 proof scope versus routed to Alloy. |
| Obligation contract | `modules/verify/src/main/scala/specrest/verify/Consistency.scala` | Defines the operational meaning of `global`, `requires`, `enabled`, and `preservation` checks. |
| TCB-sensitive | `modules/parser/src/main/scala/specrest/parser/Parse.scala` | Remains trusted in the first ship claim; parser changes can narrow or widen what that claim honestly covers. |
| TCB-sensitive | `modules/parser/src/main/scala/specrest/parser/Builder.scala` | IR construction remains trusted; builder changes can change the source-to-IR boundary under the theorem. |
| TCB-sensitive | `modules/verify/src/main/scala/specrest/verify/z3/Backend.scala` | The first shipped theorem still trusts the runtime renderer from `Z3Script` to Z3 ASTs. |

Two important non-members:

- `modules/verify/src/main/scala/specrest/verify/z3/SmtLib.scala` is **not** proof-governed for
  the first ship. The main runtime path does not use it; it is export-only today.
- `modules/verify/src/main/scala/specrest/verify/certificates/Dump.scala` is **not**
  proof-governed for the first ship. It affects artifacts, not the theorem target.

Reserved future surfaces:

- `proofs/lean/**` becomes proof-owned the moment `M_G.4` opens the workspace.
- The Scala↔prover mirror table becomes proof-owned as soon as it exists.

## 4. Required Change Process

If a PR touches any proof-governed surface listed above, it must do all of the following:

1. Update [`12_global_proof_status`](/research/12_global_proof_status) in the same PR.
2. State whether the change is one of:
   - proof-target shape change,
   - obligation/routing change,
   - TCB-only change,
   - or program-commitment change.
3. State whether the current `M_G.0` theorem statement or TCB summary changed.
4. If the proof-safe profile membership changed, update
   [`13_global_proof_profile`](/research/13_global_proof_profile) before merge.
5. If the owner, priority statement, paused-work set, or stall rule changed, update
   [`14_global_proof_runway`](/research/14_global_proof_runway) before merge.
6. If the theorem boundary, TCB, or governed-surface set changed, update this doc or the
   governing issue before merge.

This stays as a documented working rule, not a repo-level automation gate.

## 5. Freeze Policy

The proof program uses three practical states:

- **Ungoverned**: normal repo churn. No proof-specific process.
- **Governed**: target can still move, but every move must be logged and classified.
- **Frozen**: a surface may only change together with the matching proof update or an explicit
  de-scoping decision.

Today, every surface in §3 is in the **governed** state.

The expected transition is:

1. `M_G.1` lands with governed surfaces and a live ledger.
2. `M_G.2` defines the proof-safe profile.
3. `M_G.4` opens `proofs/lean/` and moves the proof-owned core plus the new prover files into a
   semi-frozen state.
4. A short hard-freeze window is allowed later for the `M_L.2` universal-soundness push.

## 6. Solo-Contributor Rule

Single-contributor does not mean no governance. It means the governance must be cheap and local.

That is why this repo uses:

- one source-of-truth governance doc,
- and one live status ledger.

Nothing here assumes a review committee. It assumes future-you is allowed to forget details unless
the written record brings them back into view.

## 7. References

- Translator soundness scoping: [`10_translator_soundness`](/research/10_translator_soundness)
- Live ledger: [`12_global_proof_status`](/research/12_global_proof_status)
- Proof-safe profile: [`13_global_proof_profile`](/research/13_global_proof_profile)
- Runway and priority: [`14_global_proof_runway`](/research/14_global_proof_runway)
- Umbrella: [#170](https://github.com/HardMax71/spec_to_rest/issues/170)
- Theorem statement / TCB: [#171](https://github.com/HardMax71/spec_to_rest/issues/171)
- This governance milestone: [#172](https://github.com/HardMax71/spec_to_rest/issues/172)
