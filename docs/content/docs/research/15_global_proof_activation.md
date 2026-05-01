---
title: "Global Proof Activation and M_L Kickoff"
description: "Gate review, execution-track activation, and first proof-implementation shape for the global soundness program"
---

> Activation doc for issue [#175](https://github.com/HardMax71/spec_to_rest/issues/175).
> Closes the gap between readiness planning (`M_G.*`) and active theorem work (`M_L.*`).

## 1. Activation Decision

`M_G.4` activates the `M_L.*` execution track.

As of `2026-05-01`, the repo no longer treats `#126`-`#130` as blocked on program activation.
The activation gate itself is now satisfied. What remains is the execution order inside the
`M_L.*` chain.

The decision is:

- `M_G.0` through `M_G.3` are satisfied,
- [HardMax71](https://github.com/HardMax71) is the active owner of the proof program and the first
  `M_L.1` contributor,
- `#126` is unblocked as the first implementation issue,
- `#127`-`#130` remain gated only by their predecessor milestones, not by activation uncertainty,
- and the first proof PR must combine theorem-prover scaffolding with real semantics work.

This does **not** claim that theorem work is easy or short. It claims the repo has done enough
planning discipline that active proof work can begin without lying about scope, trust, or
priority.

## 2. Gate Review

The activation gate is satisfied because each prerequisite now has a concrete artifact:

| Gate | Artifact | Why it is sufficient |
| --- | --- | --- |
| `M_G.0` theorem statement and TCB | [`10_translator_soundness`](/research/10_translator_soundness), issue [#171](https://github.com/HardMax71/spec_to_rest/issues/171) | The first honest theorem target is written down, and the first-ship TCB is explicit. |
| `M_G.1` governed proof surfaces | [`11_global_proof_governance`](/research/11_global_proof_governance), [`12_global_proof_status`](/research/12_global_proof_status) | Target movement is visible instead of implicit. |
| `M_G.2` proof-safe profile | [`13_global_proof_profile`](/research/13_global_proof_profile) | The first theorem scope is smaller than the full language and bound to the Z3 path. |
| `M_G.3` owner, runway, and fallback | [`14_global_proof_runway`](/research/14_global_proof_runway) | The work now has a named owner, a bounded runway, paused competing lanes, and a circuit breaker. |

No additional planning-layer blocker remains that would justify keeping `M_L.*` in suspended
state.

## 3. What Is Unblocked

Activation does **not** flatten the `M_L.*` dependency chain. It changes what the chain is blocked
on.

After `M_G.4`:

- `#126` is active implementation work.
- `#127` is still blocked on `#126`, but no longer blocked on "is the proof program real?"
- `#128` and `#129` stay blocked on `#127` in the normal milestone sense.
- `#130` stays blocked on `#128`.

This keeps the work serialized where it needs to be serialized, without preserving the old
"planning is not done yet" ambiguity.

## 4. First M_L PR Shape

The first `M_L` implementation PR must be a **combined `M_L.0 + first M_L.1 slice`** change.

It should not be a standalone scaffolding PR.

The minimum honest kickoff shape is:

- `proofs/lean/lean-toolchain` pinned to `leanprover/lean4:v4.29.1`
- `proofs/lean/lakefile.toml`
- `proofs/lean/README.md`
- `proofs/lean/STATUS.md`
- `proofs/lean/SpecRest/IR.lean`
- `proofs/lean/SpecRest/Semantics.lean`
- `proofs/lean/SpecRest/Examples.lean`
- `proofs/lean/SpecRest/IR.lean.todo`
- `.github/workflows/lean.yml` as a non-blocking proof-workflow job

The required substance in that PR is:

- a real deep embedding for the `Z3-Core-1S` bootstrap fragment,
- a real semantic domain for the first values / environments / states it needs,
- at least one checked example or lemma that demonstrates the workspace is already doing proof
  work,
- and a status table that makes future partial completion legible.

What is explicitly **not** acceptable as the first PR:

- namespace-only scaffolding,
- a toolchain pin plus empty files,
- or a CI-only Lean setup with no semantics artifact.

This is the concrete anti-decay rule for the execution track.

## 5. Owner and Kickoff Rule

[HardMax71](https://github.com/HardMax71) is the named first `M_L.1` contributor.

The implementing PR for the kickoff slice should say that plainly in the PR body or linked issue
comment so `#126`'s ownership requirement is satisfied in the work artifact itself, not only in a
planning doc.

The intended work order is:

1. Open the proof workspace together with the first semantics files.
2. Land the bootstrap `Z3-Core-1S` semantic kernel.
3. Expand that kernel until `#127` has a real machine-checked subset semantics.
4. Only then branch into the `#128` universal theorem and `#129` translation-validation paths.

## 6. Activation Invariants

The following rules remain in force after activation:

- `proofs/lean/` must still not appear in `main` until it lands with active proof content.
- Changes to proof-governed Scala surfaces still require status updates in the matching proof
  docs.
- If the first six-week runway fails to produce meaningful theorem artifacts, follow the circuit
  breaker in [`14_global_proof_runway`](/research/14_global_proof_runway) instead of quietly
  extending scope.

Activation is therefore a commitment to start, not permission to drift.

## 7. References

- Scoping and milestone plan: [`10_translator_soundness`](/research/10_translator_soundness)
- Governance: [`11_global_proof_governance`](/research/11_global_proof_governance)
- Status ledger: [`12_global_proof_status`](/research/12_global_proof_status)
- Proof-safe profile: [`13_global_proof_profile`](/research/13_global_proof_profile)
- Runway and fallback: [`14_global_proof_runway`](/research/14_global_proof_runway)
- Umbrella: [#170](https://github.com/HardMax71/spec_to_rest/issues/170)
- This activation issue: [#175](https://github.com/HardMax71/spec_to_rest/issues/175)
- First execution milestone: [#126](https://github.com/HardMax71/spec_to_rest/issues/126)
