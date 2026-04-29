---
title: "Mechanically Verified Translator Soundness"
description: "Scoping and milestone plan for proving spec-IR → SMT-LIB translation sound in a proof assistant"
---

> Scoping doc for issue [#88](https://github.com/HardMax71/spec_to_rest/issues/88).
> Establishes the trust-chain framing, surveys 2024-2026 prior art, picks a proof
> assistant, defines the verified subset, and decomposes the work into shippable
> milestones (M_L.0 → M_L.4). Does **not** ship code — that lands per milestone.

---

## Table of Contents

1. [Status and Framing](#1-status-and-framing)
2. [The Trust Chain Today](#2-the-trust-chain-today)
3. [Why "Reconstruct Z3 Proofs" Does Not Work in 2026](#3-why-reconstruct-z3-proofs-does-not-work-in-2026)
4. [Prior Art: 2024-2026 Snapshot](#4-prior-art-2024-2026-snapshot)
5. [Two Paths and Why We Recommend Both, Sequenced](#5-two-paths-and-why-we-recommend-both-sequenced)
6. [The Verified Subset](#6-the-verified-subset)
7. [Picking a Proof Assistant](#7-picking-a-proof-assistant)
8. [Milestone Decomposition](#8-milestone-decomposition)
9. [Risks](#9-risks)
10. [Non-Goals](#10-non-goals)
11. [References](#11-references)

---

## 1. Status and Framing

Issue #88 asks for a mechanically checked correctness proof of spec_to_rest's
`spec → IR → Z3 SMT-LIB` translation. The issue itself flags this as **unscheduled,
research-flavored, easily one person-year of work**, filed primarily to give the
capability a concrete home so it isn't absorbed into other milestones.

This doc updates that framing for 2026:

- **The Z3-proof-replay path that #77 partly assumed has not materialized.** Z3
  4.13 (the version this project pins via `z3-turnkey`) emits only its undocumented
  2008-era natural-deduction term tree; `:proof_format alethe` was never shipped in
  any Z3 release, and quantifier instantiations remain opaque (see §3).
- **The closest published prior art (Cohen, Princeton PhD, 2025) is a 5-person-year
  effort** that verifies five Why3 IVL transformations in Coq — and even Cohen left
  monomorphization, the SMT-LIB printer, and end-to-end SMT soundness in the trusted
  computing base (TCB).
- **A cheaper, more recent template exists**: per-run *translation validation* à la
  Parthasarathy et al. (PLDI 2024 / POPL 2025) emits an Isabelle proof certificate
  *for each verifier run* showing "if the IVL output is correct then the source is
  correct." This is the realistic 2026 path for a project our size.

The recommendation in this doc is therefore: **decompose #88 into a tractable
five-milestone plan (M_L.0 → M_L.4) that ships translation validation first, then
moves toward meta-soundness only for the verified subset (§6) once a contributor
signs up for the M_L.2 commitment**.

**Status: research-flavored, opportunistic.** No deliverable in this doc is currently
blocking any user. Land partial work as opportunities arise (intern projects, guest
contributors); update this doc as milestones complete.

---

## 2. The Trust Chain Today

Today, "we proved this spec correct" is shorthand for the following chain:

```mermaid
flowchart TD
  Prose["English-prose semantics<br/>docs/content/docs/spec-language.mdx"]
  Parser["Parser CST<br/>modules/parser/.../Parse.scala"]
  Builder["IR Builder<br/>modules/parser/.../Builder.scala"]
  IR["Typed IR<br/>modules/ir/.../Types.scala (25 Expr cases)"]
  ZTrans["Z3 Translator<br/>modules/verify/.../z3/Translator.scala (1917 LOC)"]
  ATrans["Alloy Translator<br/>modules/verify/.../alloy/Translator.scala (429 LOC)"]
  Z3["Z3 4.13<br/>via z3-turnkey JNI"]
  Verdict["sat / unsat / unknown"]

  Prose -.-> Parser
  Parser --> Builder --> IR --> ZTrans --> Z3
  IR --> ATrans
  Z3 --> Verdict
```

Each link is a potential silent-failure point:

| Link | Failure mode |
|---|---|
| Prose semantics → IR | Spec language has only English-prose semantics; nothing to refine the IR builder against. |
| IR builder | Hand-written; tested via fixtures, not proven. |
| Z3 Translator | 1917 LOC of Scala. 13 of 25 `Expr` cases are fully translated, 8 partial, 4 raise `TranslatorError`. The encoding choices for entities (uninterpreted sort + field functions), state (pre/post functions), and quantifier domains are defensible but unverified (see [codebase-analysis appendix below](#a-codebase-translator-coverage-april-2026)). |
| Z3 itself | Has had soundness CVEs historically. Pinning at 4.13 deters silent verdict flips on upgrade but does not eliminate the trust assumption. |

Mechanically verifying *every* link is far beyond a project our size. The smallest
useful target is the **IR → Z3 step**: it's the largest hand-written piece, the one
whose semantic behaviour we control, and the one closest to user-visible verdicts.

---

## 3. Why "Reconstruct Z3 Proofs" Does Not Work in 2026

Issue #77 (closed) sketched a "verify-as-gate" path that gestured at proof export and
Alethe-via-Z3 as a future direction. Research as of April 2026 says that path is
blocked at the Z3 side:

### 3.1 Z3 has no Alethe export

Direct search of the Z3 [release notes](https://github.com/Z3Prover/z3/blob/master/RELEASE_NOTES.md)
finds no mention of "alethe" in any release. Issue search `alethe repo:Z3Prover/z3`
returns zero results. The release notes mention only:

- 4.11.2: *"change proof logging format for the new core to use SMTLIB commands. The
  format was so far an extension of DRAT used by SAT solvers"*
- 4.12.0: *"sat.smt.proof.check_rup ... apply forward RUP proof checking"*

Z3 4.13 (the version pinned by `z3-turnkey % 4.13.0.1` in `build.sbt`) emits only its
undocumented [IWIL 2008 natural-deduction proof](https://ceur-ws.org/Vol-418/paper10.pdf)
format. Quantifier instantiation steps (`quant-inst`) appear with no machine-readable
witness justification — the very steps that dominate spec_to_rest's preservation
checks (5 invariants × 10 ops = 50 quantifier scopes per service).

### 3.2 cvc5-Alethe does not cover datatypes

The cvc5 [Alethe documentation](https://cvc5.github.io/docs/latest/proofs/output_alethe.html)
says verbatim:

> Currently, the theories of equality with uninterpreted functions, linear
> arithmetic, bit-vectors and parts of the theory of strings (with or without
> quantifiers) are supported in cvc5's Alethe proofs.

Datatypes are not in this list. spec_to_rest's IR translator emits
`declare-datatypes` for entity records, sums, and option types (see
[codebase analysis](#a-codebase-translator-coverage-april-2026)). A cvc5-as-proof-certifier
backend therefore requires re-engineering the SMT encoding to be datatype-free
(records as parallel UF arrays, sums as tag+payload via UF) — a non-trivial rewrite,
not a flag flip.

### 3.3 Quantifier-instantiation proof bloat

E-matching with non-trivial trigger sets routinely yields 10³–10⁵ ground instances
per quantifier (see [Reynolds, SMT 2023](http://homepage.divms.uiowa.edu/~ajreynol/smt2023.pdf)
and [DSLab "Conjecture Regarding SMT Instability"](https://ceur-ws.org/Vol-4008/SMT_paper21.pdf)).
For a typical preservation suite, expect Alethe proof files in the tens to hundreds
of MB, with [Carcara](https://github.com/ufmg-smite/carcara) check times in minutes.
This is workable for one-off audits, prohibitive for CI-on-every-PR.

### 3.4 What this means

The "Z3 emits a checkable proof + Carcara/ITP replays it" architecture from #77 is
not viable in 2026. Three implications:

1. **Translator soundness must be proven as a meta-theorem about our `translate`
   function**, not by replaying each Z3 run's proof.
2. **The Z3 verdict remains an oracle in our trust base.** This is the same posture
   as F\*, Dafny, Verus, and Why3-O — none of them check Z3 proofs; they verify the
   *encoder*.
3. **An "external solver agreement" CI job is still useful** as cheap defense in
   depth: emit our SMT-LIB, run cvc5 in parallel, alert on disagreement. Doesn't need
   proof export. Belongs in #77 follow-up, not here.

---

## 4. Prior Art: 2024-2026 Snapshot

A 12-month survey produced the following landscape. Each entry tagged with how
applicable it is to spec_to_rest's IR→Z3 translator.

### 4.1 Cohen, "A Foundationally Verified Intermediate Verification Language" (Princeton PhD, 2025)

Closest match to #88. Defines [Why3's P-FOLDR](https://joscoh.github.io/docs/thesis.pdf)
(Polymorphic First-Order Logic with Datatypes and Recursion) in Coq, gives it a
denotational semantics, and proves five Why3 transformations sound:

- `eliminate_definition` (recursive funs → unfolding axioms)
- elimination of inductive predicates
- `compile_match` (the first machine-checked pattern-matching compiler)
- ADT axiomatization
- a few smaller passes

**Five person-years**, single PhD student plus advisor and Sandia mentor. Coq 8.20,
extracted to OCaml as `Why3-O` (plug-compatible with the real Why3 OCaml API).

**What stays in the TCB even after this PhD**: monomorphization, the SMT-LIB printer,
the SMT solver itself.

**Pitfalls Cohen called out, all of which apply to us**:
- Well-typedness preservation under context-modifying transformations is harder than
  soundness.
- Pattern-matching compilation termination needs a non-obvious well-founded measure.
- Mixed record-inductive types fight Coq's positivity checker.
- ADT axiomatization (with non-uniform constructors and metadata) is the single
  hardest transformation.
- The semantics layer (the formal language definition) absorbed more time than the
  translator transformations.

**Applicability to spec_to_rest**: the same architectural skeleton transfers — we'd
build a deep-embedded `Expr` ADT in Lean/Coq/Isabelle, a denotational semantics, and
prove `eval e = smtEval (translate e)` for our subset. We would *not* attempt to
match Cohen's depth (polymorphism, recursive funs, inductive preds); the verified
subset (§6) is intentionally smaller.

### 4.2 Parthasarathy et al., "Towards Trustworthy Automated Program Verifiers" (PLDI 2024)

Different design: instead of proving the *language-to-IVL* translator sound once and
for all, **emit an Isabelle proof certificate for each verifier run** that shows
"if the IVL program is correct then the source program is correct."
[arXiv 2404.03614](https://arxiv.org/abs/2404.03614).

POPL 2025 sequel ([Formal Foundations for Translational Separation Logic Verifiers](https://dl.acm.org/doi/10.1145/3704856))
extends to Viper-style separation logic.

**Why this matters**: per-run translation validation is **dramatically cheaper** than
per-language meta-soundness — no need to formalize the entire source language
semantics, only enough to express each instance. The certificate kernel can be small
(hundreds of LOC), checkable quickly, and updates automatically as the translator
evolves.

**Trade-off**: certificates cover only inputs we've actually translated. Meta-
soundness covers the universe of well-typed inputs.

### 4.3 lean-smt and Isabelle/HOL Alethe Reconstruction

[lean-smt (CAV 2025)](https://arxiv.org/abs/2505.15796): a Lean 4 tactic that translates
a Lean goal to SMT-LIB, hands it to **cvc5** (not Z3), and replays the resulting
[Alethe proof](https://verit.gitlabpages.uliege.be/alethe/) in the Lean kernel. ~71%
of cvc5 proofs reconstruct (15,271 of 21,595 benchmarks); 98% of successful
reconstructions complete in &lt;1s.

[Isabelle Alethe pipeline (ITP 2025)](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.ITP.2025.26):
extends Sledgehammer's veriT-only reconstruction (Schurr/Fleury, 2019) to also
support cvc5. Five years on, both veriT and cvc5 reconstruct in Isabelle/HOL.

**Wrong direction for us**. These tools answer: *given a goal in Lean/Isabelle, can
we discharge it via SMT and replay the proof?* Our question is the inverse: *given
our hand-written translator from spec-IR to SMT-LIB, is it sound?* lean-smt and
Sledgehammer would only help us **as authoring tools** for the meta-soundness
proof — we can use them to discharge sub-lemmas, not to validate the translator.

Z3 is also absent from both pipelines: Sledgehammer's Z3 oracle uses the legacy proof
format which is unmaintained, and lean-smt is cvc5-only.

### 4.4 Verified VCG for Dafny (Nezamabadi/Myreen/Tan, Dec 2025)

[arXiv 2512.05262](https://arxiv.org/abs/2512.05262). Big-step semantics for an
imperative Dafny subset (mutually-recursive methods, while loops, arrays — no
records, sets, quantifiers, or partial functions) plus a verified VCG plus a
verified compiler to CakeML. HOL4. The VCG produces verification conditions in
HOL4's logic, not SMT-LIB — they short-circuit the SMT step entirely.

**Useful as a template for the *VCG side*** (preservation obligations as HOL/Lean
propositions), less useful for the SMT-encoding side because they don't generate
SMT.

### 4.5 F\* → SMT Encoding (Aguirre/Hriţcu, 2016 → ongoing)

Negative result. [Towards a Provably Correct Encoding from F\* to SMT](https://catalin-hritcu.github.io/students/alejandro/report.pdf)
formalized a fragment in Coq; ten years later no completion. Reason: refinement
types + monadic effects too rich for the formalization to remain tractable.

**Lesson for us**: keep the source language *rigidly small*. Resist scope creep
into the rich corners of the IR (lambdas, set comprehension, the-operator) until
the simple core works.

### 4.6 No Mechanized Alloy or Dafny Surface Semantics

Search did not surface a Coq/Isabelle/Lean formalization of Alloy's relational
semantics ([Daniel Jackson, *Software Abstractions*](https://mitpress.mit.edu/9780262528900/software-abstractions/))
nor of Dafny's reference manual. Astra (UWaterloo, 2019) evaluated Alloy→SMT-LIB
*empirically* but did not prove it. The most actively developed comparable
formalization is **TLA\* in Isabelle/HOL** by [Grov &amp; Merz (AFP, 2011-2025)](https://www.isa-afp.org/entries/TLA.html),
shallow-embedded — covers the temporal layer, not a translator to SMT.

**Lesson**: any spec language semantics we mechanize is novel work.

### 4.7 Older Prior Art Worth Knowing

- **CompCert** (~100 kLOC Coq, 6 person-years) sets the *upper bound* on this kind
  of work and is the architectural inspiration.
- **AliveInLean** (CAV 2019) verifies a peephole-optimization checker for LLVM in
  Lean 4 — closest to "verified SMT encoder" in the LLVM space, but limited to
  bit-vector and array peepholes.
- **HOL-Boogie** (TPHOLs 2008) embeds Boogie's *output* into Isabelle/HOL for
  interactive VC discharge — does not verify the translator itself.
- **SMTCoq** ([smtcoq.github.io](https://smtcoq.github.io/)) verifies *proofs
  returned by* SMT solvers (veriT/CVC4/cvc5), not encodings into them.
  Quantifier-free. Different problem.

### 4.8 Closest-prior-art summary table

| Project | Source | Prover | What they proved | Pitfall |
|---|---|---|---|---|
| Cohen, Why3-in-Coq (2025) | Why3 P-FOLDR | Coq | 5 IVL transformations sound | 5 person-years; monomorphization & SMT-LIB printer in TCB |
| Nezamabadi et al., Dafny VCG (2025) | Dafny subset | HOL4 | VCG sound; CakeML compiler correct | Bypasses SMT; no records/sets/quantifiers |
| Parthasarathy et al., Trustworthy Verifiers (2024-25) | Viper | Isabelle | Per-run forward-simulation cert | Cert covers only translated inputs, not all inputs |
| Aguirre/Hriţcu, F\*→SMT (2016-) | F\* fragment | Coq | Soundness of fragment encoding | Stalled 10+ years; F\* too rich |
| AliveInLean (2019) | LLVM IR peepholes | Lean 4 | Encoder verified | BV/array only |
| Grov/Merz TLA\* in Isabelle (2011-25) | TLA\* | Isabelle | Embedding + derived rules | No translator to SMT |

---

## 5. Two Paths and Why We Recommend Both, Sequenced

### 5.1 Path A: Translation validation (cheap, recommended first)

Per-run certificates following Parthasarathy 2024. For each verifier run, emit a
proof object (in Lean/Isabelle/Rocq) showing that *for this specific IR*, the SMT
output is a correct encoding.

**Cost**: ~3-6 person-months for the certificate kernel + emission glue. The
certificate kernel is small (hundreds of LOC) and stable; the per-run emission is
mechanical (one proof step per `translate` case).

**Coverage**: only inputs we've actually translated.

**Trust assumption**: the certificate-checking kernel + its embedding of the
semantic domain.

**Why first**: ships verifiable trust improvement in months, not years. Acts as a
forcing function for documenting the IR's intended semantics (which doesn't exist
in any machine-checkable form today).

### 5.2 Path B: Meta-soundness (expensive, optional follow-up)

Cohen-style. Deep-embed `Expr` and `TypeExpr` in a proof assistant; define a
denotational semantics; define `translate` as a function in the proof assistant;
prove `denote_smt(translate(e)) = denote_ir(e)` for every well-typed `e` in the
verified subset.

**Cost**: 6-12 person-months for the verified subset (§6) at expert rates;
double-to-triple at non-expert rates. Per A5's analysis: ~1,350 LOC for the
semantics layer (M_L.1) and ~3-5× that for the soundness theorem itself (M_L.2).

**Coverage**: all well-typed inputs in the verified subset.

**Trust assumption**: the proof assistant's kernel + the embedding's accuracy
(deep `Expr`, shallow semantic domain — see §7.2).

**Why deferred**: only worth doing if a contributor signs up for the multi-month
commitment, and only after Path A has documented the IR semantics rigorously
enough to enable it. Without Path A, M_L.1 is starting from scratch on the
semantics; with Path A, M_L.1 reuses a Path-A-validated semantic skeleton.

### 5.3 Why both, sequenced

Path A's certificate kernel **is** Path B's meta-soundness skeleton, scoped to a
single input. Building A first means:

1. Faster initial trust improvement (months, not years).
2. Forcing-function for writing the IR semantics in a machine-checkable form.
3. The artifact built in A is reusable: M_L.2's "translation soundness" is the
   universal-quantifier version of A's per-input certificate.

The alternative — start with B — risks Cohen's outcome at smaller scale: 9 months
into the semantics layer with no end-to-end deliverable.

---

## 6. The Verified Subset

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
| `BinaryOp(Subset\|Union\|Intersect\|Diff, ...)` | Defer; finite-set Mathlib lemmas non-trivial |
| `UnaryOp(Cardinality)` | Currently only on state relations; defer to state-mutation milestone |
| `UnaryOp(Power)` | Translator already raises `TranslatorError` (undecidable in FOL) — permanent exclusion |
| `Quantifier(_, _, _)` over entity collections | Defer to M_L.2 (needs frame axioms) |
| `SetComprehension`, `SetLiteral`, `MapLiteral`, `SeqLiteral` | Out of scope until collections milestone |
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

- **Bool, Int**: SMT-LIB native sorts.
- **Enums**: uninterpreted sort + member constants + distinctness axiom.
  Cardinality finite and known.
- **Entities**: uninterpreted sort + per-field accessor function (`Entity_field :
  Entity → FieldSort`). No datatype constructors in M_L.1 (avoids cvc5-Alethe
  blocker; matches Cohen's "ADT axiomatization" pattern).
- **State**: tuple of scalar functions (no maps, no relations beyond domain
  membership predicates) in M_L.1. M_L.2 expands.

---

## 7. Picking a Proof Assistant

### 7.1 Three candidates compared

| Criterion | Lean 4 + mathlib4 | Isabelle/HOL + AFP | Coq/Rocq |
|---|---|---|---|
| Z3 reconstruction available? | No (lean-smt is cvc5-only) | No (Sledgehammer Z3 is legacy; ITP 2025 work targets cvc5/veriT) | No (SMTCoq targets veriT/cvc5) |
| Closest prior-art language | AliveInLean (LLVM peepholes) | TLA\*, Z, Object-Z, Cohen's framework if ported | Cohen's Why3-O (the Coq variant) |
| Toolchain churn | Quarterly (Lean 4.27→4.28→4.29→4.30 in 14 weeks Feb-Apr 2026) | Yearly Isabelle releases; AFP push-through | Yearly Coq → Rocq transition; stable post-2025 |
| Long-lived single-author projects | Few (ecosystem &lt;5 years) | CryptHOL (9 yrs), TLA\* (14 yrs), HOL-Z (25 yrs) | CompCert (20 yrs) |
| Scala-team learning curve | Lowest (most syntactically similar to Scala 3) | Medium (Isar prose-style proofs unfamiliar) | Medium-high (tactic style, ssreflect) |
| Mathlib4 record / Finset support | Excellent (`structure`, `Finset α`, decidability) | Excellent (`record`, `Set`, fset library) | Good (`Record`, `MSet`, but more boilerplate) |
| Risk of mathlib churn breaking proofs | High | Low (definitional shallow embeddings rarely break) | Low |

### 7.2 Recommendation: Lean 4, with explicit mathlib avoidance

**Recommend Lean 4** for spec_to_rest #88, on these grounds:

1. **Smallest learning-curve gap from Scala 3.** The team is fluent in Scala 3 +
   Cats Effect; Lean 4's syntax (do-notation, type classes, structural records,
   pattern matching) is the closest of the three.
2. **Self-contained core suffices.** The 10-operator M_L.1 subset needs `Int`,
   `Bool`, `Option`, `List`, basic finite sets — all in Lean core, no mathlib4
   dependency. Avoiding mathlib4 sidesteps the quarterly-churn risk (the dominant
   maintenance liability).
3. **AliveInLean precedent.** [AliveInLean (CAV 2019)](https://link.springer.com/chapter/10.1007/978-3-030-25543-5_25)
   demonstrated a verified SMT-encoding-style checker in Lean for LLVM peepholes —
   smaller scope than ours, same shape.

**Conditions on this recommendation**:

- Pin `lean-toolchain` to a specific Lean release. Bump quarterly at most.
- Avoid mathlib4 unless a specific lemma forces it. Re-evaluate per-milestone.
- Ship M_L.0 (scaffolding) only when a contributor has signed up for M_L.1
  delivery — the directory exists to support work, not to advertise intent.

**Fallback if the M_L.0 contributor prefers Isabelle**: switching to Isabelle/HOL
adds ~25% on the schedule (Isar verbosity offsets AFP stability) but is otherwise
acceptable. Cohen's Coq framework is also reusable as-is if a contributor with
deep Coq/Rocq expertise materializes — the framework, not the prover, dominates the
work.

### 7.3 Embedding shape (locked across all three candidates)

- **Source IR (`Expr`, `TypeExpr`, declarations)**: deep embedding as inductive
  types mirroring `modules/ir/.../Types.scala` 1:1. Required because the soundness
  theorem `∀ e. denote(translate(e)) = eval(e)` quantifies over `e` syntactically.
- **Semantic domain**: shallow. `Int → Lean Int`, `Bool → Lean Bool`, `Set α →
  Mathlib Finset α` (or core `List` if we sidestep mathlib), entity sorts as opaque
  type variables.
- **SMT-LIB target**: shallow. Interpret SMT terms directly as `Prop` —
  no need for meta-reasoning over SMT syntax, only over IR syntax.

This is the standard hybrid Why3-in-Coq, AliveInLean, and Concrete Semantics IMP
all use ([Gibbons & Wu](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/embedding.pdf);
[Annenkov & Spitters](https://cs.au.dk/~spitters/TYPES19.pdf)).

---

## 8. Milestone Decomposition

Five milestones. Each has a contributor sign-off gate before the next opens.

### 8.1 M_L.0 — Scope, scaffolding, contributor handoff

**Effort**: ~1 week part-time. Lands when a contributor signs up for M_L.1.

**Deliverables**:
- This research doc (you're reading it).
- A `proofs/lean/` directory with a minimal `lakefile.toml`, an empty `SpecRest`
  namespace, and a one-page `README.md` linking back here.
- A pinned `lean-toolchain` (recommend latest stable Lean 4 release as of M_L.0
  start, e.g., `leanprover/lean4:v4.30.0`).
- A `.github/workflows/lean.yml` job that runs `lake build`. Off the critical
  path (separate matrix); failure does not block PRs.
- An initial PR-template note that anyone touching `modules/ir/.../Types.scala`'s
  `Expr` ADT should add a TODO entry to `proofs/lean/SpecRest/IR.lean.todo`.

**Acceptance**: empty Lake project compiles green in CI.

### 8.2 M_L.1 — IR denotational semantics for the verified subset

**Effort**: 6-10 person-weeks at expert rates; 12-20 at non-expert.

**Deliverables**:
- `proofs/lean/SpecRest/IR.lean`: deep `Expr`, `TypeExpr`, `EntityDecl`,
  `EnumDecl`, `StateDecl`, `OperationDecl`, `InvariantDecl` — restricted to the
  §6.1 subset.
- `proofs/lean/SpecRest/Semantics.lean`: `Value` ADT, `Env`, `State`,
  `eval : Env → State → Expr → Option Value`. Per-operator denotation lemmas.
- `proofs/lean/SpecRest/Examples.lean`: round-trip tests for `safe_counter.spec`
  fragments (parsed by hand for now; M_L.4 wires the parser).

**Acceptance**:
- All denotation lemmas closed (no `sorry`).
- `safe_counter.spec` invariant `count ≥ 0` evaluates to `True` under a hand-built
  initial state.

**LOC estimate** (per A5 calibration, anchored to Concrete Semantics IMP):

| Component | Lean LOC |
|---|---|
| Inductive `Expr` + `TypeExpr` | 80 |
| `Value` + `Env`, `State` | 80 |
| `eval` core | 150 |
| Quantifier + decidability | 120 |
| OperationDecl + InvariantDecl | 80 |
| Per-operator denotation lemmas (10 × ~30) | 300 |
| Round-trip examples | 100 |
| **Total** | **~900** |

(A5 reported ~1,350 with collections+records; M_L.1 trims those, dropping ~450 LOC.)

### 8.3 M_L.2 — Translator soundness theorem for the verified subset

**Effort**: 3-5× M_L.1, i.e. ~6-12 person-months.

**Deliverables**:
- `proofs/lean/SpecRest/Smt.lean`: shallow embedding of the SMT-LIB fragment we
  emit (Bool ops, LIA, UF, bounded quantifiers).
- `proofs/lean/SpecRest/Translate.lean`: Lean version of the Scala translator,
  function-by-function mirror of the §6.1 cases in `z3.Translator.scala`.
- `proofs/lean/SpecRest/Soundness.lean`: theorem
  `∀ e, well_typed e → eval e = smtEval (translate e)`.
- An audit appendix in `proofs/lean/README.md` mapping each Lean translation case
  to the corresponding Scala line range for human cross-checking.

**Acceptance**:
- `Soundness.lean` closes with no `sorry` for the §6.1 subset.
- A Scala test (`modules/verify/.../TranslatorAuditTest.scala`) checks that any
  `Expr` case marked "in the verified subset" still has a counterpart in
  `Translate.lean`.

**Trust assumption (after M_L.2)**:
- Lean's kernel.
- The shallow SMT-LIB embedding's accuracy (auditable in &lt;100 LOC).
- The hand-written Scala translator matches the Lean `translate` function
  case-for-case (audited; if it diverges the Scala test fires).
- Z3 itself.

### 8.4 M_L.3 — Translation validation (per-run certificate emission)

**Effort**: ~3-6 person-months, parallel to M_L.2.

**Deliverables**:
- `modules/verify/src/main/scala/specrest/verify/cert/Emit.scala`: emits a
  Lean source file `<spec>.cert.lean` per `verify` run, containing one
  `theorem cert_<id> : eval ir = smtEval smt_ir := by ...` per check.
- `proofs/lean/SpecRest/Cert.lean`: tactic library (or simp set) that closes a
  `cert_<id>` goal via a fixed proof script when the IR is in the verified
  subset.
- `verify --emit-cert <dir>` CLI flag, mirroring the existing `--dump-vc`.

**Acceptance**:
- For every fixture in `fixtures/spec/` whose `Expr` is in the verified subset,
  `verify --emit-cert /tmp/c && lake build /tmp/c` succeeds.
- Out-of-subset fixtures emit a `cert_<id> := sorry` placeholder with a comment
  pointing at the offending case.

**Why parallel to M_L.2**: M_L.3 only needs M_L.1's semantics, not M_L.2's
universal-quantifier theorem. M_L.3 ships earlier trust gain; M_L.2 generalizes
it.

### 8.5 M_L.4 — Subset expansion

**Open-ended.** Add `Add/Sub/Mul/Div`, then collections, then state mutation,
then quantifiers over entity collections. Each expansion follows the M_L.1 + M_L.2
template: extend the deep IR, extend `eval`, extend `translate`, extend the
soundness theorem.

**Sequencing recommendation**:

1. LIA arithmetic (`Add`, `Sub`, `Mul`, `Div`) — needed by ~70% of real specs.
2. State mutation (`Prime`, `Pre`, `OperationDecl` with state updates).
3. Quantifiers over fixed-size entity collections (with frame axioms).
4. Finite collections (`Set`, `Map`, `Seq` with bounded ops).
5. Records / `With` updates / `FieldAccess`.
6. `Constructor`, `EnumAccess` (dynamic).

Each item is its own multi-month milestone; punt indefinitely if the §5.1 path-A
certificates make it unnecessary for any real consumer.

---

## 9. Risks

### 9.1 Doc rot

This doc anchors to `modules/ir/.../Types.scala`'s 25-case `Expr` ADT. If `Expr`
evolves (new cases, renamed cases, restructured sums), the verified subset table
in §6 drifts. **Mitigation**: a PR-template note (added in M_L.0) reminding
contributors to update §6 when touching `Expr`. A lint pass in M_L.2 flags
`Expr` cases without a corresponding `proofs/lean/SpecRest/IR.lean` mirror.

### 9.2 Z3 verdict drift across versions

Path B (meta-soundness) does not bind us to a Z3 version — the soundness theorem
says "if Z3 returns `unsat`, the IR property holds." Path A (per-run certs) is
similarly version-agnostic. **However**, an "external solver agreement" CI job
(running cvc5 in parallel and alerting on disagreement) is cheap defense in depth
and belongs in a #77 follow-up. Out of scope here.

### 9.3 Lean toolchain churn

Lean shipped 4.27, 4.28, 4.29, 4.30 in 14 weeks (Feb-Apr 2026). Each release can
break dependent projects. **Mitigation**: pin `lean-toolchain`; bump only
quarterly; avoid mathlib4 dependency (M_L.1 needs none). The "every 6-12 months,
1 day to bump" maintenance cost is acceptable; the "every 3 weeks, scramble
because mathlib reorganized" cost is not.

### 9.4 Contributor abandonment mid-milestone

The work is part-time, opportunistic. A contributor disappearing mid-M_L.1
strands ~500 LOC of half-finished Lean. **Mitigation**:
- M_L.0 only opens the scaffolding when a contributor commits to M_L.1.
- M_L.1 is broken into 8 phases (per A5 §4) each independently mergeable.
- A `proofs/lean/STATUS.md` file tracks per-operator completion so a successor
  can resume.

### 9.5 The semantics-layer trap (Cohen)

Cohen's PhD spent more time on the semantics layer than on the translator
transformations. We mitigate by **deliberately picking the smallest non-trivial
subset (§6.1)** and resisting expansion until M_L.1 + M_L.2 ship. A "verified
subset" at 10 operators is more valuable than a half-finished verified subset at
25 operators.

### 9.6 The F\* trap

F\*'s SMT encoder has been "open" for 10+ years. Their failure mode: refinement
types + monadic effects too rich for the formalization to remain tractable. We
avoid this by **excluding rich corners of the IR upfront** (§6.2 lists every
deferred operator with a why) and by treating `TranslatorError`-raising operators
as permanent exclusions (`Power`, `Lambda`, `If` — see §6.2).

### 9.7 Mismatch between Lean `translate` and Scala `Translator.scala`

Path B proves the *Lean* `translate` function sound. Production uses *Scala*
`Translator.scala`. **Mitigation**: M_L.2 ships an audit appendix mapping each
Lean case to the corresponding Scala line range; `TranslatorAuditTest.scala`
machine-checks that the verified subset's case set in Lean equals that in Scala.
If they diverge (Scala adds a case Lean lacks, or vice versa), the test fires.

The full "extract Scala from Lean" approach (Why3-O, Cohen 2025) is out of scope —
it would more than triple the M_L.2 cost. We accept the audit-by-test mitigation
as good-enough.

---

## 10. Non-Goals

Inherits #88's non-goals, plus:

- **Verifying the Alloy translator.** Same approach, separate effort. Alloy
  semantics in any prover does not exist, would be novel work twice over.
- **Verifying the parser.** Treat the parser CST as given; this work targets
  IR → solver only.
- **Verifying Z3 itself.** Z3 remains in the trust base. SMTCoq-style proof
  replay is blocked at the Z3 side (see §3) and a switch to cvc5 is a separate
  multi-month rewrite (§3.2).
- **Verifying the SMT-LIB serializer (`SmtLib.scala`).** Trivially small, audited
  by inspection, in the TCB. Cohen made the same call.
- **Ahead-of-time correctness for every spec construct.** Aim for a verified
  subset that grows; full coverage is M_L.4-and-beyond, possibly never.
- **Human-readable proof narration of soundness violations.** That's #20's
  territory ("operation X violates invariant Y because…"). Soundness proof
  failures should not happen in production after M_L.2; if they do, the bug is in
  Lean, not in user output.

---

## 11. References

### 11.1 Closest prior art

- [Cohen, *A Foundationally Verified Intermediate Verification Language* (Princeton PhD, May 2025)](https://joscoh.github.io/docs/thesis.pdf) — code at [github.com/joscoh/why3-semantics](https://github.com/joscoh/why3-semantics)
- [Cohen, *A Formalization of Core Why3 in Coq* (POPL 2024)](https://dl.acm.org/doi/10.1145/3632902)
- [Parthasarathy et al., *Towards Trustworthy Automated Program Verifiers* (PLDI 2024)](https://arxiv.org/abs/2404.03614)
- [Parthasarathy et al., *Formal Foundations for Translational Separation Logic Verifiers* (POPL 2025)](https://dl.acm.org/doi/10.1145/3704856)
- [Nezamabadi/Myreen/Tan, *Verified VCG and Verified Compiler for Dafny* (Dec 2025)](https://arxiv.org/abs/2512.05262)

### 11.2 SMT proof reconstruction in proof assistants

- [Lachnitt et al., *Improving the SMT Proof Reconstruction Pipeline in Isabelle/HOL* (ITP 2025)](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.ITP.2025.26)
- [Schurr/Fleury, *Reconstructing veriT Proofs in Isabelle/HOL* (PxTP 2019)](https://arxiv.org/abs/1908.09480)
- [lean-smt: An SMT Tactic for Discharging Proof Goals in Lean (CAV 2025)](https://arxiv.org/abs/2505.15796)
- [cvc5 → Isabelle reconstruction blog (2024)](https://cvc5.github.io/blog/2024/03/15/isabelle-reconstruction.html)
- [Lachnitt et al., *IsaRare: Automatic Verification of SMT Rewrites in Isabelle/HOL* (TACAS 2024)](https://link.springer.com/chapter/10.1007/978-3-031-57246-3_17)
- [Böhme, *Proof Reconstruction for Z3 in Isabelle/HOL* (PhD)](https://www21.in.tum.de/~boehmes/proofrec.pdf)
- [SMTCoq](https://smtcoq.github.io/)

### 11.3 Z3 proof formats

- [de Moura, Bjørner, *Proofs and Refutations, and Z3* (IWIL 2008)](https://ceur-ws.org/Vol-418/paper10.pdf)
- [Z3 release notes](https://github.com/Z3Prover/z3/blob/master/RELEASE_NOTES.md)
- [Z3 discussion #4881 — On proof generation and proof checking](https://github.com/Z3Prover/z3/discussions/4881)
- [Carcara: Proof Checker for Alethe (TACAS 2023)](https://link.springer.com/chapter/10.1007/978-3-031-30823-9_19) and [github.com/ufmg-smite/carcara](https://github.com/ufmg-smite/carcara)
- [Alethe specification](https://verit.gitlabpages.uliege.be/alethe/) and [arXiv 2107.02354](https://arxiv.org/pdf/2107.02354)
- [cvc5 Alethe proof output](https://cvc5.github.io/docs/latest/proofs/output_alethe.html)
- [Reynolds, *Selecting Quantifiers for Instantiation in SMT* (SMT 2023)](http://homepage.divms.uiowa.edu/~ajreynol/smt2023.pdf)

### 11.4 Spec-language semantics formalizations

- [Grov & Merz, *TLA in Isabelle/HOL* (AFP, 2011-2025)](https://www.isa-afp.org/entries/TLA.html)
- [Brucker/Rittinger/Wolff, *HOL-Z* (Z notation in Isabelle)](https://link.springer.com/chapter/10.1007/BFb0105411)
- [Lamport/Merz/Newcombe, *The Future of TLA+* (2024)](https://lamport.azurewebsites.net/tla/future.pdf)
- [Aguirre/Hriţcu, *Towards a Provably Correct Encoding from F\* to SMT* (MS thesis)](https://catalin-hritcu.github.io/students/alejandro/report.pdf)
- [Astra, *Evaluating Translations from Alloy to SMT-LIB* (UWaterloo, 2019)](https://cs.uwaterloo.ca/~nday/pdf/techreps/2019-06-AbDa-arxiv-1906.05881.pdf)
- [Pierce et al., *Software Foundations* — Hoare](https://softwarefoundations.cis.upenn.edu/plf-current/Hoare.html)
- [Nipkow & Klein, *Concrete Semantics*](http://concrete-semantics.org/)

### 11.5 Embedding-style references

- [Gibbons & Wu, *Folding Domain-Specific Languages: Deep and Shallow Embeddings*](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/embedding.pdf)
- [Annenkov & Spitters, *Deep and Shallow Embeddings in Coq*](https://cs.au.dk/~spitters/TYPES19.pdf)
- [Verifying Programs with Logic and Extended Proof Rules: Deep vs. Shallow Embedding](https://arxiv.org/abs/2310.17616)

### 11.6 Lean 4 / mathlib4 ecosystem

- [Mathlib4](https://github.com/leanprover-community/mathlib4)
- [AliveInLean (CAV 2019)](https://link.springer.com/chapter/10.1007/978-3-030-25543-5_25)
- [`bv_decide` / `Std.Tactic.BVDecide` (Lean 4.12, Oct 2024)](https://lean-lang.org/blog/2024-10-3-lean-4120/)

### 11.7 spec_to_rest cross-references

- [`docs/content/docs/research/06_spec_verification.md`](/research/06_spec_verification) — verification pipeline design
- [`docs/content/docs/spec-language.mdx`](/spec-language) — current English-prose semantics
- [Issue #88 — Mechanically verified translator soundness](https://github.com/HardMax71/spec_to_rest/issues/88)
- [Issue #77 — Proof-certificate / unsat-core export (closed)](https://github.com/HardMax71/spec_to_rest/issues/77)
- [Issue #20 — M4.4 error reporting + spans](https://github.com/HardMax71/spec_to_rest/issues/20)

---

## A. Codebase Translator Coverage (April 2026)

Snapshot of `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala`
(1917 LOC) measured against the 25-case `Expr` ADT in
`modules/ir/src/main/scala/specrest/ir/Types.scala`. Used to define the verified
subset in §6.

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
| `SetLiteral` | partial | non-empty only; empty fails (1113-1116) |
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
