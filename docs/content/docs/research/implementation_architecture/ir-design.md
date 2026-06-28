---
title: "IR design"
description: "The intermediate representation the compiler is built around"
---

The IR is not a data structure the compiler authors maintain by hand; it is generated. The spec's
abstract syntax is defined once as a family of datatypes in Isabelle/HOL
([`core/IR.thy`](https://github.com/HardMax71/spec_to_rest/blob/main/proofs/isabelle/SpecRest/core/IR.thy)),
and `Code_Target_Scala` extracts it, together with the verified `translate`, `eval`, and `smt_eval`
functions, into
[`SpecRestGenerated.scala`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/ir/src/main/scala/specrest/ir/generated/SpecRestGenerated.scala).
So the IR the compiler pattern-matches on is the one the [soundness
proof](/research/translator_soundness) reasons about, the same datatype, not a hand-written copy that
could drift away from it. That is the whole design decision; everything else follows from it.

## The extracted ADT

What comes out is plain Scala 3: sealed hierarchies of `final case class`es, one per syntactic form.

```scala
sealed abstract class expr
final case class BinaryOpF(a: bin_op, b: expr, c: expr, d: Option[span_t]) extends expr
final case class FieldAccessF(a: expr, b: String, c: Option[span_t])       extends expr
final case class IndexF(a: expr, b: expr, c: Option[span_t])               extends expr
final case class CallF(a: expr, b: List[expr], c: Option[span_t])          extends expr
final case class PrimeF(a: expr, b: Option[span_t])                        extends expr
final case class IdentifierF(a: String, b: Option[span_t])                 extends expr
final case class IntLitF(a: BigInt, b: Option[span_t])                     extends expr
// ...around two dozen more variants
```

Two things stand out, and both are consequences of extraction rather than choices. The constructor
names carry an `F` suffix, a leftover from collapsing the old `expr` and `expr_full` types into one.
And the fields are positional, `a`, `b`, `c`, `d`, because that is how Isabelle's extractor names
them: a `BinaryOpF`'s operands are `.b` and `.c`, not `.left` and `.right`. The file is generated and
never hand-edited, so where a field genuinely needs a readable accessor, a named selector is added on
the Isabelle side and re-extracted. Every node also carries an `Option[span_t]`, its source span, so a
verifier or lint diagnostic can point back at the exact place in the spec text.

## Immutable, annotated from outside

Because the source is Isabelle, the extracted classes are immutable; nothing mutates an IR node in
place. That rules out the tempting shortcut of having the convention engine stamp an HTTP method onto
an `OperationDeclFull` directly. Instead the convention and profile decisions live in a
[separate layer](/research/convention_engine/ruleset) that wraps the IR rather than editing it, which
keeps the pre-convention IR available for comparison and leaves the verified core untouched.

## Traversal by exhaustive match

Traversal is a Scala 3 `match` on the sealed type, and because the hierarchy is sealed the compiler
rejects a match that forgets a case, the guarantee that matters most when there are two dozen
expression variants to handle.

```scala
def render(e: expr): String = e match
  case BinaryOpF(op, l, r, _)  => s"(${render(l)} ${symbol(op)} ${render(r)})"
  case FieldAccessF(obj, f, _) => s"${render(obj)}.$f"
  case IndexF(rel, k, _)       => s"${render(rel)}[${render(k)}]"
  case PrimeF(inner, _)        => s"${render(inner)}'"
  case IdentifierF(name, _)    => name
  case IntLitF(n, _)           => n.toString
  // every remaining variant, or it does not compile
```

Matching also sidesteps the positional-field awkwardness: the operands are bound by position and
given readable local names, so a traversal reads cleanly even though the fields are `a` and `b`
underneath. For storage and the parser goldens, the IR serializes to JSON through circe's
`Mirror`-based derivation.
