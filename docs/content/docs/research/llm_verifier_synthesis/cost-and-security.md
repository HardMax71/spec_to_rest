---
title: "Cost and security"
description: "When synthesis fires, what a run costs, and where the trust boundary actually sits"
---

## When synthesis fires

Most operations never reach the LLM. The classifier in [the convention engine](/design/convention-engine)
emits a body directly for the shapes it can prove from a template: a state preservation, a delete, a
cardinality delta, a map insert, a pointwise field update, and an output bound to a pure lookup.
Anything else, arithmetic on prior values, string building, a set comprehension, an unknown function
call, falls to synthesis, because the body is no longer implied by the contract. The split is
deliberately conservative: when in doubt the operation goes to the LLM, so direct emission fires only
where the result is provably correct from the template. For the URL shortener that leaves `Delete`
direct-emitted and the other three synthesized.

## What it costs

Direct-emit operations are free: no model, no tokens. A synthesized operation costs whatever its
tokens cost. The price is the token count times the model's per-million rate, from the
[`Pricing`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/Pricing.scala)
table (Sonnet at \$3 and \$15 per million tokens in and out, Haiku at \$0.80 and \$4, Opus at \$15
and \$75), summed across the loop's iterations. A scalar operation that verifies in a couple of
Sonnet iterations runs to a few cents; a hard one that escalates to a stronger model and iterates
more costs more, but never without bound. [`CegisBudget`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/synth/src/main/scala/specrest/synth/CegisBudget.scala)
caps a run at one US dollar by default, and the run aborts the moment the running total would cross
`--cost-cap-usd`. The cap is set conservatively for unpriced models, so a missing price row fails
safe by aborting early rather than overspending.

Time goes to two places: the model's latency per iteration and Dafny's verification per candidate,
the second bounded by the same `--verification-time-limit` the verifier already runs under. A
direct-emit operation adds nothing measurable.

## The trust boundary

The security story here is not sandboxing; it is verification. A synthesized body ships only if Dafny
proves it against the spec's `requires` and `ensures`, so the model cannot smuggle in behavior that
violates the contract, since a body that did would not verify. The diff-checker closes the obvious
escape by rejecting any candidate that weakened a clause or added a new `{:extern}`, so the model can
neither relax the obligation it was asked to meet nor introduce a side-effecting call. And the body is
only verified and compiled during synthesis, never executed, so there is no untrusted-execution step
to sandbox in the first place.

What the proof rests on is the axiomatized edge. An unknown function like `isValidURI` is assumed to
behave as its trivially-true predicate claims, and the guarantee holds only as far as that assumption
does. That is the honest line between the verified kernel and the unverified world.

A security property is just a postcondition. An ownership rule, that a caller may touch only its own
record, is an `ensures` like any other, and the verifier discharges it the same way it discharges a
cardinality bound. The injection vectors that worry a typical service, SQL and HTML, live in the
convention engine's generated layer, which parameterizes queries and escapes responses, not in the
synthesized kernel, which works on abstract state and contains neither SQL nor markup.

The original design also sketched prompt-injection sanitization and a `bubblewrap` sandbox around the
verifier. Neither is built, and the static-checking point above is why the sandbox was never needed:
Dafny inspects the candidate, it does not run it.
