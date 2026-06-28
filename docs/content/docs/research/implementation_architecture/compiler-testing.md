---
title: "Testing the compiler"
description: "How the compiler itself is held to a standard"
---

Trusting the compiler is a ladder. Ordinary tests cover the plumbing, golden files pin the output,
property tests probe the edges, a mutation run keeps the tests honest, and at the top the part that
matters most is not tested at all but proved.

## Tests on the real IR

The compiler is Scala, so its tests are munit: every suite extends `CatsEffectSuite` and returns
`IO`, asserting with `.assertEquals` rather than unsafe-running. There are around a hundred and twenty
test files, weighted toward the stages with the most behavior, the verifier, the code generator, and
the test generator. What is worth noticing is that they build and match on the extracted IR directly,
positional fields and all:

```scala
class FlattenInheritanceTest extends CatsEffectSuite:
  test("a child entity inherits its parent's invariants"):
    val flat = flattenInheritance(ir)
    assertEquals(identCount(childInvs(flat), "p_inv"), 1)
```

## Goldens

A large share of the compiler's output is checked by golden file: render it for the canonical
fixtures, diff against the committed expected output, and any change that moves a byte has to be
reviewed. The tree covers most of what the compiler emits.

```text
fixtures/golden/
├── codegen/        generated project files per target
├── ir/             the extracted IR as JSON
├── smt/            SMT-LIB the verifier hands to Z3
├── smt-errors/     diagnostics for unsatisfiable specs
├── vc/             verification conditions
├── alloy/          Alloy models for the relational checks
├── dafny/          synthesized Dafny kernels
└── verify_report/  end-to-end verification output
```

## Breadth, and keeping the tests honest

Example-based tests only cover the cases someone thought to write. Property tests widen that: with
scalacheck-effect, a suite like the translator's generates random IR and checks a property holds
across all of it, instead of on a handful of hand-picked inputs. And to check the tests themselves
earn their keep, stryker4s mutates the Scala modules and reports how many mutants the suite kills, a
health signal run out of band rather than a per-commit gate. That hard gate, the 90% mutmut run, sits
on the generated app instead, covered under [mutation testing](/pipelines/mutation-testing).

## The proof on top

The one check that outranks the rest is not a test. The translation from spec to verification
condition is proved correct, once, as the `soundness` theorem in Isabelle/HOL, and the Scala the
compiler runs is extracted from that proof rather than written alongside it. So the core of the
compiler is not trusted because its tests pass; it is trusted because it cannot diverge from the
theorem it was extracted from. CI enforces this by building the `SpecRest_Soundness` and
`SpecRest_Codegen` sessions on every change to the proofs, and a pre-commit hook refuses a commit
whose proofs still contain a `sorry`. The [soundness track](/research/translator_soundness) is the
full account.
