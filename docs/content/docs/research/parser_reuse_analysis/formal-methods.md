---
title: "Reusing a formal-methods parser"
description: "Alloy, Quint, and Dafny as the parser front end"
---

## 2. Option 1: Extend Alloy 6

### Architecture overview

- Language: Java (90.1%), C++ (5.0% for SAT solver natives)
- Parser technology: CUP (Java-based LALR parser generator) + JFlex lexer
  - Grammar file: `org.alloytools.alloy.core/parser/Alloy.cup`
  - Lexer file: `org.alloytools.alloy.core/parser/Alloy.lex`
- License: Apache 2.0 (migrated from MIT)
- Latest release: Alloy 6.2.0 (January 2025)
- Repository:
  [AlloyTools/org.alloytools.alloy](https://github.com/AlloyTools/org.alloytools.alloy)

### Parser accessibility

The Alloy jar is explicitly designed to be used as an embeddable library. The `CompUtil` class
provides a clean programmatic API:

```java
// Parse a single module from a string
CompModule module = CompUtil.parseOneModule("sig User { name: one String }");

// Parse a complete specification with all submodules
CompModule module = CompUtil.parseEverything_fromFile(reporter, cache, "spec.als");

// Parse and typecheck an expression in a module context
Expr expr = CompUtil.parseOneExpression_fromString(module, "all u: User | #u.name > 0");
```

The `CompModule` return type provides access to all signatures (`getAllReachableSigs()`), facts,
predicates, assertions, and commands.

### What Alloy already handles (of our 14 constructs)

| Our Construct             | Alloy Equivalent                            | Coverage                                        |
| ------------------------- | ------------------------------------------- | ----------------------------------------------- |
| Entity declarations       | `sig` with fields                           | **Full**, sigs are entities with typed fields |
| Multiplicity annotations  | `one`, `lone`, `some`, `set`                | **Full**, native to the language              |
| Relational types          | First-class relations                       | **Full**, Alloy's core strength               |
| Quantified expressions    | `all`, `some`, `no`                         | **Full**, native quantifiers                  |
| Primed variables          | Alloy 6 `var` + prime                       | **Full**, temporal extension                  |
| Invariants/facts          | `fact`, `assert`                            | **Full**, native constructs                   |
| Cardinality operator      | `#`                                         | **Full**, native operator                     |
| Enum declarations         | `enum` keyword                              | **Full**, supported                           |
| State declarations        | `var sig` / `var` fields                    | **Partial**, no dedicated `state` block       |
| Operations with contracts | `pred` with pre/post comments               | **Partial**, no `requires`/`ensures` keywords |
| Refinement types          | Fact constraints on sigs                    | **Partial**, no inline `where` syntax         |
| Set/Map/Seq types         | Relations (sets native, maps via relations) | **Partial**, no `Map[K,V]` or `Seq[T]` syntax |
| Transition declarations   | Can be modeled with pred chains             | **None**, no dedicated syntax                 |
| Convention blocks         | Not applicable                              | **None**, no HTTP concept                     |

**Coverage: ~8.5 of 14 constructs (61%)**

### What would need to be added/modified

1. `operation` blocks with `requires`/`ensures`: Alloy has predicates but no structured
   operation declaration. Would need new grammar productions in `Alloy.cup` for `operation`,
   `input`, `output`, `requires`, `ensures`.

2. `state` blocks: Alloy 6 has `var` fields but no dedicated state declaration block. Minor
   syntactic addition.

3. `transition` declarations: Entirely new. No Alloy equivalent for declarative state machine
   transitions with `via` and `when` clauses.

4. `conventions` blocks: Entirely new. HTTP mapping is a foreign concept.

5. `Map[K,V]` and `Seq[T]` type syntax: Alloy uses relations for everything. Would need to add
   parameterized collection types on top of Alloy's relational foundation.

6. Refinement types with `where`: Alloy constrains via facts, rather than inline on type declarations.
   Would need new syntax.

### Integration effort: HIGH

- CUP is an older parser generator (LALR), less flexible than ANTLR or PEG for adding new
  productions. Grammar conflicts are likely when adding keyword-heavy blocks alongside Alloy's
  existing syntax.
- The Alloy AST (`Expr`, `Sig`, `Func` hierarchy) is tightly coupled to the analyzer and SAT solver.
  Adding new node types requires changes deep in the compiler pipeline.
- The Java ecosystem locks us into JVM deployment, which may conflict with desired target platforms.
- Alloy's type system is relational (everything is a relation of atoms), which does not map cleanly
  to REST data types like `String`, `DateTime`, `Map[K,V]`.

### What we gain

- Bounded model checking via Kodkod/SAT for free, can verify invariants.
- Temporal operators (`always`, `eventually`) for state property verification.
- Mature relational reasoning (transitive closure, join, etc.).
- Academic credibility and a well-understood formal foundation.

### What we lose

- Syntax familiarity: `sig`/`fact`/`pred` vocabulary is alien to most developers. The
  comparative survey rates Alloy syntax as "Medium" friendliness.
- String handling: Alloy's string support is extremely weak. It cannot reason about string
  operations, regex matching, or URL validation, critical for REST services.
- Scalability: Bounded model checking means Alloy cannot prove properties for all sizes. The
  "small scope hypothesis" is useful but not a guarantee.
- No code generation pipeline: Alloy is purely analytical. It finds counterexamples but cannot
  generate implementations.
- Heavyweight dependency: The Alloy jar bundles SAT solvers, Kodkod, and a GUI application.
  Extracting just the parser is possible but messy.

### Verdict: NOT RECOMMENDED

The impedance mismatch between Alloy's relational atom-based universe and REST service types is too
large. Adding HTTP concepts, structured operations, and developer-friendly syntax to Alloy would
effectively mean rewriting most of the parser and building a new AST on top, gaining only the
bounded checking backend, which we can invoke separately without coupling to Alloy's parser.

## 3. Option 2: Extend Quint

### Architecture overview

- Language: TypeScript (67.1%), Rust (16.3%)
- Parser technology: ANTLR 4 (generates TypeScript parser from `.g4` grammar)
- License: Apache 2.0
- Repository: [informalsystems/quint](https://github.com/informalsystems/quint)
- Ecosystem: VSCode extension, LSP server (npm: `@informalsystems/quint-language-server`), REPL,
  Apalache integration for model checking

### Parser architecture

Quint's compiler pipeline has clearly separated phases:

1. Parsing: ANTLR 4 grammar -> ANTLR parse tree -> Quint IR (JSON-serializable)
2. Name resolution: Resolves identifiers to their declarations
3. Type checking: Constraint-based type inference with unification
4. Effect checking: Tracks read/write effects on state variables
5. Simulation: Random execution for rapid testing
6. Model checking: Translation to TLA+ for Apalache SMT-based checking

The IR uses a visitor pattern with well-documented ADRs (Architecture Decision Records). The IR is
JSON-serializable, meaning external tools can consume it easily.

### What Quint already handles

| Our Construct             | Quint Equivalent                               | Coverage                                                                        |
| ------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------------- |
| State declarations        | `var x: int`                                   | **Full**, native state variables                                              |
| Quantified expressions    | `all`, `exists`                                | **Full**, native quantifiers                                                  |
| Primed variables          | `x' = expr`                                    | **Full**, core of the language                                                |
| Invariants/facts          | `val inv = ...` / temporal properties          | **Full**, native                                                              |
| Enum declarations         | Sum types: `type Status = Active \| Suspended` | **Full**, discriminated unions                                                |
| Cardinality operator      | `size()` method                                | **Full**, via standard library                                                |
| Operations with contracts | `action` with boolean pre/postconditions       | **Partial**, actions can encode pre/post but no `requires`/`ensures` keywords |
| Entity declarations       | No direct equivalent                           | **Partial**, can use `type` + record, but no multiplicity annotations         |
| Set/Map/Seq types         | `Set[T]`, `Map[K,V]`, `List[T]`                | **Partial**, sets and maps native, but `List` not `Seq`                       |
| Refinement types          | No inline constraint syntax                    | **None**, types are not refinable                                             |
| Multiplicity annotations  | Not applicable                                 | **None**, no relational multiplicities                                        |
| Relational types          | Not applicable                                 | **None**, no first-class relations                                            |
| Transition declarations   | Can be encoded in actions                      | **None**, no declarative state machine syntax                                 |
| Convention blocks         | Not applicable                                 | **None**, no HTTP concept                                                     |

**Coverage: ~6.5 of 14 constructs (46%)**

### What would need to be added/modified

1. `entity` declarations with fields and multiplicities: Quint has record types but no
   `entity` keyword, no field-level multiplicity, and no inline `where` constraints. Would need new
   ANTLR grammar rules.

2. `service` top-level block: Quint uses `module`. Would need a new top-level construct or
   treat `service` as a special module.

3. `operation` blocks with `requires`/`ensures`: Quint actions are boolean expressions that
   update state. They can encode pre/postconditions but lack structured input/output declarations
   and explicit contract keywords.

4. `transition` declarations: Entirely new. No Quint equivalent.

5. `conventions` blocks: Entirely new.

6. Multiplicity annotations (`one`, `lone`, `some`, `set`), Foreign to Quint's type system.
   Would require extending the type checker significantly.

7. Relational types (`A -> lone B`), Quint uses `Map[A, B]` or `Set[A]`, rather than relations with
   multiplicity. Fundamental type system difference.

8. Refinement types: Would need `where` clause support on type aliases.

### Integration effort: MEDIUM-HIGH

- ANTLR 4 grammar is straightforward to extend with new productions. Grammar files are typically
  200-500 lines; adding new rules is well-documented.
- The IR visitor architecture (documented in ADR003) provides a clean extension point. New node
  types can be added to the IR and handled by visitors.
- TypeScript codebase is accessible and well-structured. The compiler phases are cleanly separated
  (unlike Alloy's monolithic parser-to-SAT pipeline).
- However, Quint's type system fundamentally lacks relations and multiplicities. Adding them would
  mean extending the type checker, effect checker, and all downstream consumers, a substantial
  change.
- Quint's value proposition is TLA+-based temporal reasoning. Our DSL uses temporal concepts
  minimally (mostly for invariants). Much of Quint's complexity (non-determinism, temporal formulas,
  Apalache translation) would be dead weight.

### What we gain

- ANTLR grammar we can extend, saves parser bootstrapping effort.
- TypeScript ecosystem: VSCode extension, LSP server, REPL.
- Simulation engine: random execution for rapid spec testing.
- Apalache backend: SMT-based model checking for invariant verification.
- Developer-friendly syntax: rated "High" in comparative survey.
- Active development: regular releases, npm packages, growing community.

### What we lose

- Relational modeling: Quint's set/map types cannot express Alloy-style relations with
  multiplicities. This is our DSL's most distinctive feature.
- Entity declarations: Would need to bolt on an entirely foreign concept.
- HTTP mapping: Still needs to be built from scratch.
- Fork maintenance burden: Forking Quint means maintaining a divergent codebase against
  upstream changes. Quint is actively evolving, so merge conflicts would be constant.
- No code generation: Quint is a specification language, rather than a generator. The implementation
  pipeline must still be built.

### Verdict: INTERESTING BUT POOR FIT

Quint is the closest existing language to our needs for state management and developer
ergonomics. But the lack of relational types and multiplicities (the core of our entity modeling)
means we would need to add so much that the result would be a different language wearing Quint's
skin. The fork maintenance burden is the killer: we would get Quint's tooling for free initially but
pay for it indefinitely in merge conflicts.

## 4. Option 3: Use dafny's parser

### Architecture overview

- Language: C# (56.9%), Dafny (33.0%), plus Rust, Java, Go, F#
- Parser technology: Hand-written recursive descent parser in C#
- License: MIT
- Latest releases: Active development with frequent releases (Dafny 4.x series)
- Repository: [dafny-lang/dafny](https://github.com/dafny-lang/dafny)
- Verification backend: Boogie -> Z3 SMT solver

### Parser architecture

Dafny's parser is a hand-written recursive descent parser in C#, tightly integrated with the
compiler pipeline. The compiler phases are:

1. Parsing -> AST construction
2. Name resolution and type checking
3. Translation to Boogie (intermediate verification language)
4. Boogie generates verification conditions
5. Z3 SMT solver discharges proof obligations
6. Code generation to C#, Java, Go, JavaScript, Python

### What Dafny already handles

| Our Construct             | Dafny Equivalent                             | Coverage                                                            |
| ------------------------- | -------------------------------------------- | ------------------------------------------------------------------- |
| Operations with contracts | `method` with `requires`/`ensures`           | **Full**, Dafny's primary purpose                                 |
| Invariants/facts          | `invariant` in loops, class invariants       | **Full**, native                                                  |
| Quantified expressions    | `forall`, `exists`                           | **Full**, native                                                  |
| Enum declarations         | `datatype` with constructors                 | **Full**, algebraic data types                                    |
| Set/Map/Seq types         | `set<T>`, `map<K,V>`, `seq<T>`               | **Full**, native collection types                                 |
| Refinement types          | Subset types with `\|` constraint            | **Full**, `type Pos = x: int \| x > 0`                           |
| Cardinality operator      | `\|s\|` for collection size                  | **Full**, native                                                  |
| Primed variables          | `old()` for pre-state reference              | **Partial**, `old()` refers to pre-state, not `x'` for post-state |
| Entity declarations       | `class` with fields                          | **Partial**, classes but no multiplicities                        |
| State declarations        | Class fields                                 | **Partial**, no dedicated `state` block                           |
| Multiplicity annotations  | Not applicable                               | **None**, no relational multiplicities                            |
| Relational types          | Not applicable                         | **None**, no first-class relations                                |
| Transition declarations   | Can be encoded in methods              | **None**, no declarative syntax                                   |
| Convention blocks         | Not applicable                         | **None**, no HTTP concept                                         |

**Coverage: ~8 of 14 constructs (57%)**

### What would need to be added/modified

1. `entity` with multiplicities: Dafny classes have fields but no multiplicity annotations.
   Would need new syntax.

2. `service` top-level block: Dafny uses `module`. Would need a new construct.

3. Relational types: Foreign to Dafny's type system.

4. `transition` declarations: Entirely new.

5. `conventions` blocks: Entirely new.

6. Primed variables: Dafny uses `old()` for pre-state, not `x'` for post-state. Different
   paradigm (method-level vs. state-level).

### Integration effort: VERY HIGH

- The parser is hand-written in C#, no grammar file to extend. Every new construct requires
  manually coding parser functions, AST nodes, and all downstream handling.
- Dafny's compiler is deeply integrated: parser, resolver, translator, and verifier are tightly
  coupled. Adding new AST node types propagates changes through the entire pipeline to Boogie
  translation.
- C# ecosystem may conflict with deployment targets.
- Dafny is a substantial project (~2M lines). Understanding the codebase enough to safely extend the
  parser is a multi-month investment.
- The smithy-dafny project demonstrates that it is feasible to _generate_ Dafny from external
  models, but not to _extend_ Dafny's parser itself.

### What we gain

- Proven verification: Z3/Boogie backend provides mathematical proof of correctness, rather than just
  bounded checking.
- Code generation: Dafny compiles to C#, Java, Go, JS, Python.
- `requires`/`ensures` syntax: Exactly what we want for operation contracts.
- Mature type system: Subset types, generic collections, algebraic data types.
- smithy-dafny precedent: AWS has already mapped Smithy constraint traits to Dafny
  specifications, proving the Smithy-to-Dafny pipeline works.

### What we lose

- Relational modeling: Dafny thinks in terms of classes, methods, and mathematical types. No
  first-class relations.
- Multiplicities: Cannot express `one`, `lone`, `some`, `set` on fields.
- Developer ergonomics: Dafny syntax is rated "High" for developers familiar with
  verification, but the verification concepts (loop invariants, decreases clauses, ghost variables)
  are intimidating for REST API developers.
- Implementation body requirement: Dafny requires the programmer to write the method body, not
  just the contract. For our use case, the compiler should synthesize the body from the contract.

### Verdict: USE AS BACKEND, NOT AS PARSER

Dafny is extremely valuable as a _verification backend_ (the smithy-dafny pattern: generate Dafny
from our IR, verify with Z3, then generate target code). But extending Dafny's hand-written C#
parser to accommodate our entity/state/ convention syntax is the wrong approach. The parser is not
designed for extension, and the impedance mismatch on relational types and multiplicities is too
large.
