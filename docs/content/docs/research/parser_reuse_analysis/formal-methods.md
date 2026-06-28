---
title: "Reusing a formal-methods parser"
description: "Alloy, Quint, and Dafny as the parser front end"
---

The three formal-specification languages closest to this DSL each already parse about half of what it
needs, and each falls down on a different half. Lined up against the fourteen constructs:

| Construct                 | Alloy           | Quint             | Dafny             |
| ------------------------- | --------------- | ----------------- | ----------------- |
| Entity declarations       | full, `sig`     | partial           | partial, `class`  |
| State declarations        | partial, `var`  | full              | partial           |
| Operations with contracts | partial, `pred` | partial, `action` | full, `method`    |
| Invariants and facts      | full            | full              | full              |
| Transition declarations   | none            | none              | none              |
| Convention blocks         | none            | none              | none              |
| Multiplicity annotations  | full            | none              | none              |
| Quantified expressions    | full            | full              | full              |
| Primed and pre-state      | full            | full              | partial, `old()`  |
| Relational types          | full            | none              | none              |
| Refinement types          | partial         | none              | full, subset      |
| Cardinality operator      | full            | full              | full              |
| Collection types          | partial         | partial           | full              |
| Enum declarations         | full            | full              | full              |

## Alloy 6

Alloy is the natural first look: relational modeling and multiplicities are its native tongue, and
they are this DSL's most distinctive feature. The parser is CUP plus JFlex, in Java, in
[AlloyTools/org.alloytools.alloy](https://github.com/AlloyTools/org.alloytools.alloy), and it is
genuinely embeddable, `CompUtil.parseOneModule(...)` returns a typed `CompModule` with every
signature, fact, and predicate:

```java
CompModule module = CompUtil.parseOneModule("sig User { name: one String }");
```

The table tells the story. Signatures, multiplicities, relations, facts, `#`, quantifiers, and `enum`
are all native, but there is no structured operation with `requires` and `ensures` (only commented
predicates), no transition or convention syntax, no `Map` or `Seq`, and no inline `where`. Bolting
those onto an older LALR grammar that conflicts easily, against an AST welded to the SAT analyzer, is
most of a parser rewrite, and Alloy's string support is too weak to reason about URLs or regexes at
all. The bounded-checking backend it would buy can be invoked separately. Not recommended.

## Quint

Quint is the closest on ergonomics and state. It is TypeScript, parsed with ANTLR4 into a
JSON-serializable IR through a cleanly phased pipeline (parse, resolve, typecheck, effect-check,
simulate, model-check via Apalache), and lives in
[informalsystems/quint](https://github.com/informalsystems/quint). Primed state, quantifiers,
sum-type enums, and `Set` and `Map` are native, and the readable syntax is its selling point. But it
has no relations and no multiplicities, the center of this DSL's entity modeling, and no entity
concept; adding them means extending the type checker, the effect checker, and every downstream
consumer, after which Quint's non-determinism, temporal formulas, and TLA+ translation are mostly
dead weight here. Forking an actively-evolving language is the deciding cost: free tooling now, paid
back indefinitely in merge conflicts. Interesting, but a poor fit.

## Dafny

Dafny's contracts match exactly. A `method` with `requires` and `ensures` is the operation shape,
subset types give refinement directly, and `set`, `map`, `seq`, quantifiers, and `|s|` cardinality
are native, the best behavioral coverage of the three.

```csharp
type Pos = x: int | x > 0
method Shorten(url: string) returns (code: string)
  requires |url| > 0
  ensures  |code| >= 6
```

Its parser, though, is hand-written C# in the two-million-line
[dafny-lang/dafny](https://github.com/dafny-lang/dafny), with no grammar to extend, so every new
construct would be hand-coded all the way through resolver, translator, and Boogie. It also has no
relations or multiplicities, uses `old()` for the pre-state rather than a primed post-state, and
expects the programmer to write the method body, where this compiler wants to synthesize it from the
contract. The telling precedent is smithy-dafny: AWS generates Dafny from an external model and
verifies it, but does not extend Dafny's parser. That is the lesson this project took, Dafny is the
verification and synthesis backend, never the front end.
