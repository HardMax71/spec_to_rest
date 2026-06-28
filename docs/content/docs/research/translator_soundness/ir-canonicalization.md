---
title: "IR canonicalization"
description: "One IR defined in Isabelle, extracted to Scala, and the trust dimension built on it"
---

Issue [#202](https://github.com/HardMax71/spec_to_rest/issues/202) made the IR canonical. It is
defined once, as a datatype family in Isabelle/HOL
([`core/IR.thy`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/SpecRest/core/IR.thy)),
and `Code_Target_Scala` extracts it into
[`SpecRestGenerated.scala`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala),
which the parser, code generator, test generator, and verifier all consume. There is no second
hand-written IR to drift against the proof. (#202 first split the full input language and the verified
subset into two ADTs; a later collapse merged them into the single `expr` the IR uses today.)

## The subset as a projection

Because the IR carries the full input language, the verified subset is a projection of it rather than
a separate type. An extracted, verified function lowers an expression onto the subset, returning the
lowered form when it succeeds and nothing when the expression falls outside. That is what makes "in
the subset" a decision the proof owns rather than a list maintained by hand.

## Sound or best-effort

That projection drives a per-check trust label, the production trust boundary, on by default since
[#192](https://github.com/HardMax71/spec_to_rest/issues/192). A check is `Sound` when its expressions
lower to the verified subset and `BestEffort` when they do not, and the two paths diverge sharply.

- A `Sound` check routes through the extracted translator: `ExpressionEncoder` hands each body to
  `SpecRestGenerated.translate`, then `SmtTermBridge` carries the resulting SMT term to Z3. If the
  extracted translator returns `None`, the check fails the translation rather than falling back.
- A `BestEffort` check skips before it reaches a backend, recorded with category
  `soundness_limitation`, so it can never be reported as verified.

The label shows where it matters: a `[sound]` or `[best-effort]` tag on the CLI line, a `"trust"`
field per check in the JSON report, and exit code 4 when soundness skips are the only thing keeping a
run from clean. The single ledger of what the verdicts actually rest on is the
[TCB audit](/research/tcb_audit). And the classifier itself is extracted from Isabelle, so the line
between sound and best-effort is drawn by the proof, not by hand.

## What lowers, and what does not

| Shape                                                                                                       | Trust       |
| ----------------------------------------------------------------------------------------------------------- | ----------- |
| literals, identifiers, enum access, arithmetic, comparison, boolean ops, `let`, field access, `prime`, `pre` | sound       |
| cardinality on an identifier, membership against an identifier                                              | sound       |
| all four quantifier kinds over an enum or relation domain, multi-binding                                    | sound       |
| a multi-field record update over an identifier base                                                         | sound       |
| indexing a relation reference: `users[uid]`, `pre(orders)[id]`, `orders'[id]`                               | sound       |
| indexing a non-relation base, set subset, calls, `if`, definite description, map and set-comprehension literals, constructors | best-effort |

Issue [#210](https://github.com/HardMax71/spec_to_rest/issues/210) widened the relation-indexing
carrier so the three relation-reference shapes lower sound, which flipped the tamper-style operations
in `auth_service` and `broken_url_shortener` from skipped to real Z3 verdicts. Every IR constructor
also carries an optional source span, erased at the proof level since the soundness theorems never
read it, so adding it cost only mechanical wildcard updates to the per-case lemmas.
