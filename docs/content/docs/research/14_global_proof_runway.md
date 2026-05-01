---
title: "Global Proof Runway and Roadmap Priority"
description: "Named owner, bounded runway, paused work, and stall policy for the global translator-soundness program"
---

> Runway doc for issues [#170](https://github.com/HardMax71/spec_to_rest/issues/170) and
> [#174](https://github.com/HardMax71/spec_to_rest/issues/174).
> Commits ownership, time budget, roadmap trade-offs, and the stall rule that must hold before
> the `M_L.*` execution track starts. Activation and kickoff are recorded in
> [`15_global_proof_activation`](/research/15_global_proof_activation).

## 1. Decision Summary

The global-proof program is now an **active, bounded priority**, not background research.

This repo currently has one active contributor. That makes the main risk scheduling, not only
theorem difficulty. A credible proof program therefore needs an explicit owner, an explicit time
budget, and an explicit list of work that will not continue at full speed while proof work is
active.

The committed `M_G.3` decision is:

- **Owner:** [HardMax71](https://github.com/HardMax71)
- **Runway:** one uninterrupted six-week proof-priority cycle, starting when `M_G.4` activates
  `M_L.*`
- **Scoping rule:** fixed time, variable scope
- **Competing work:** explicitly paused or demoted while the cycle is active
- **Fallback:** if the global-proof push stalls, shrink to `Z3-Core-1S`; if that still fails,
  revert the primary trust-improvement path to `M_L.3`-style translation validation instead of
  pretending the universal theorem is still the active track

This is a commitment to a real program, not a promise that the full global theorem ships inside
one cycle.

## 2. Named Owner and Priority Statement

[HardMax71](https://github.com/HardMax71) is the named owner of the global-proof program.

While the proof runway is active:

- proof work is the default priority for discretionary project time,
- work that directly supports `M_G.4`, `M_L.0`, `M_L.1`, and the early `M_L.2` theorem path takes
  precedence over new feature lanes,
- and the repo should not describe global soundness as "background research" or "opportunistic"
  work anymore.

This does **not** mean every non-proof change stops. It means non-proof work must justify itself
as either:

- critical maintenance,
- proof-enabling support work,
- or explicitly secondary work accepted as slower.

## 3. Runway Model

The proof program adopts a bounded runway modeled on appetite-first planning: protect a fixed
amount of uninterrupted time, then shrink scope rather than quietly extending the schedule.

The first committed runway is:

- **length:** six weeks,
- **start condition:** `M_G.4` closes and unblocks `#126`-`#130`,
- **mode:** one active primary lane, not parallel proof work plus full-speed roadmap work,
- **review point:** end of cycle; no automatic renewal.

The first cycle is expected to produce active theorem work, not repo decoration. At minimum, the
cycle should land all of the following:

- `proofs/lean/` opened together with active proof files,
- a live prover-side semantics kernel for the `Z3-Core-1S` fragment,
- and a concrete Scala↔prover mirror artifact or checked theorem fragment that covers real current
  translator cases.

If those artifacts are not materializing, the correct response is to cut scope or switch tracks,
not to silently turn the runway into an open-ended research queue.

## 4. Reprioritized Roadmap While the Runway Is Active

The following lanes are **paused** while the proof runway is active:

| Lane | Issues | Runway status | Reason |
| --- | --- | --- | --- |
| LLM synthesis track | `#31`, `#32`, `#28`, `#29`, `#27`, `#30` | `paused` | Valuable, but orthogonal to the proof objective and too large to run in parallel in a solo-contributor repo. |
| New target expansion and distribution | `#33`, `#35`, `#34`, `#36`, `#56` | `paused` | New targets, packaging, and CLI expansion widen the surface area without helping the proof program ship. |

The following lanes are **secondary-only** while the proof runway is active:

| Lane | Issues | Runway status | Rule |
| --- | --- | --- | --- |
| Auth/security feature lane | `#53`, `#54`, `#55` | `secondary` | May move only if work is urgent, tightly scoped, or blocks another near-term external need. |
| Maintenance and experiments | `#149`, `#150`, `#161`, `#163` | `secondary` | Nice to have, but not allowed to displace proof time. |

The following work is still allowed normally:

- build-break and CI-fix work needed to keep `main` usable,
- correctness or security regressions in already-shipped behavior,
- narrowly scoped doc fixes,
- and parser/IR/translator/verification changes that directly support the proof track and update
  the governed proof docs honestly.

## 5. Interrupt Policy

The proof runway is supposed to provide uninterrupted time, but not at the cost of leaving the
repo broken.

Interrupts are allowed for:

- red CI or release-blocking build failures,
- correctness bugs in the current verification path,
- security issues in shipped code,
- or narrowly bounded maintenance tasks that can be completed without reopening multiple feature
  lanes.

Interrupts are **not** a license to resume paused roadmap themes "for a day" and then let them
sprawl. If an interrupt turns into a second active lane, the runway is no longer honest.

## 6. Stall Rule and Circuit Breaker

The proof program must have a visible stall rule before `M_L.*` starts.

The circuit breaker is:

1. At the end of the first six-week cycle, do **not** auto-renew by default.
2. If the cycle produced active proof artifacts but the scope was too broad, shrink explicitly to
   `Z3-Core-1S` and continue only with the narrower target.
3. If the program is already at `Z3-Core-1S` and still fails to produce meaningful theorem
   progress, pause the universal-theorem push (`M_L.2`-style expansion) and switch the primary
   trust-improvement goal to `M_L.3`-style per-run translation validation / certificate work.
4. Keep any reusable semantics kernel or mirror artifacts already produced; do not discard useful
   formalization work just because the full theorem stalls.

For this repo, "meaningful theorem progress" means at least one of:

- a merged prover-side semantics artifact that is clearly connected to live Scala structures,
- a merged mirror artifact that covers real translator cases,
- or a checked proof fragment stronger than setup-only scaffolding.

What does **not** count:

- empty prover scaffolding,
- pinned toolchains without live proof code,
- or long-lived "proof soon" status notes with no checked artifact.

## 7. Exit Condition into `M_G.4`

`M_G.3` exists to make `M_G.4` honest.

After this decision lands, `M_G.4` can activate the execution track because the repo will already
have:

- a named owner,
- an explicit priority statement,
- an explicit trade-off against competing roadmap work,
- and a written fallback if the proof effort stalls.

That is the missing readiness layer between "interesting research direction" and "open the prover
workspace now."

That readiness layer is now consumed by `M_G.4`; the next work is execution, not another planning
round.

## 8. References

- Scoping and milestone plan: [`10_translator_soundness`](/research/10_translator_soundness)
- Governance: [`11_global_proof_governance`](/research/11_global_proof_governance)
- Status ledger: [`12_global_proof_status`](/research/12_global_proof_status)
- Proof-safe profile: [`13_global_proof_profile`](/research/13_global_proof_profile)
- Umbrella: [#170](https://github.com/HardMax71/spec_to_rest/issues/170)
- This runway issue: [#174](https://github.com/HardMax71/spec_to_rest/issues/174)
- Basecamp, *Shape Up*: [Set Boundaries](https://basecamp.com/shapeup/1.2-chapter-03),
  [The Betting Table](https://basecamp.com/shapeup/2.2-chapter-08), and
  [Place Your Bets](https://basecamp.com/shapeup/2.3-chapter-09)
- Gaurav Parthasarathy, Thibault Dardinier, Benjamin Bonneau, Peter Müller, and Alexander J.
  Summers, [*Towards Trustworthy Automated Program Verifiers*](https://arxiv.org/abs/2404.03614)
- Thibault Dardinier, Michael J. Sammler, Gaurav Parthasarathy, Alexander J. Summers, and Peter
  Müller, [*Formal Foundations for Translational Separation Logic Verifiers*](https://doi.org/10.1145/3704856)
