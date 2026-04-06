---
title: "Parser Reuse Analysis"
description: "Evaluating reuse of existing language parsers vs building new"
---

> Research document evaluating whether the spec-to-REST compiler should reuse or extend an existing
> formal specification language's parser instead of building a new DSL from scratch. Covers Alloy 6,
> Quint, Dafny, TypeSpec, Smithy, and Ballerina with detailed architectural analysis and a final
> recommendation.

---

## Table of Contents

1. [What Our Spec Language Needs](#1-what-our-spec-language-needs)
2. [Option 1: Extend Alloy 6](#2-option-1-extend-alloy-6)
3. [Option 2: Extend Quint](#3-option-2-extend-quint)
4. [Option 3: Use Dafny's Parser](#4-option-3-use-dafnys-parser)
5. [Option 4: Use TypeSpec's Parser](#5-option-4-use-typespecs-parser)
6. [Option 5: Use Smithy's Parser](#6-option-5-use-smithys-parser)
7. [Option 6: Use Ballerina](#7-option-6-use-ballerina)
8. [Comparative Matrix](#8-comparative-matrix)
9. [Recommendation](#9-recommendation)

---

## 1. What Our Spec Language Needs

From the grammar design in `01_spec_language_design.md`, our DSL requires these core constructs that
any reused parser must handle or be extended to handle:

| Construct                     | Description                                             | Example                                             |
| ----------------------------- | ------------------------------------------------------- | --------------------------------------------------- |
| **Entity declarations**       | Typed fields with multiplicities and inline constraints | `entity User { name: String where len(name) >= 1 }` |
| **State declarations**        | Mutable service state as typed collections              | `state { users: Set[User] }`                        |
| **Operations with contracts** | Input/output + requires/ensures clauses                 | `operation Create { requires: ... ensures: ... }`   |
| **Invariants and facts**      | Global constraints that must always hold                | `invariant: all u in users => len(u.email) > 0`     |
| **Transition declarations**   | Entity lifecycle state machines                         | `Active -> Suspended via Suspend when ...`          |
| **Convention blocks**         | HTTP mapping overrides                                  | `conventions { Resolve.http_method = "GET" }`       |
| **Multiplicity annotations**  | `one`, `lone`, `some`, `set` on relations               | `owner: one User`                                   |
| **Quantified expressions**    | `all`, `exists`, `no` with set comprehension            | `all u in users => u.email != ""`                   |
| **Primed variables**          | After-state references                                  | `users' = users + {newUser}`                        |
| **Relational types**          | Typed relations with multiplicity                       | `mapping: ShortCode -> lone LongURL`                |
| **Refinement types**          | Types with constraint predicates                        | `type Email = String where matches(email_regex)`    |
| **Cardinality operator**      | `#` for collection sizes                                | `#users' = #users + 1`                              |
| **Set/Map/Seq collections**   | Parameterized collection types                          | `Set[User]`, `Map[String, Int]`, `Seq[Event]`       |
| **Enum declarations**         | Finite value types for states                           | `enum Status { Active, Suspended, Deleted }`        |

The key insight from the prior survey: **no existing language provides all of these**. Our DSL
uniquely combines Alloy's relational modeling and multiplicities, VDM/Dafny's pre/postcondition
syntax, Quint's developer-friendly syntax, and TypeSpec/Smithy's HTTP override capability.

---

## 2. Option 1: Extend Alloy 6

### Architecture Overview

- **Language:** Java (90.1%), C++ (5.0% for SAT solver natives)
- **Parser technology:** CUP (Java-based LALR parser generator) + JFlex lexer
  - Grammar file: `org.alloytools.alloy.core/parser/Alloy.cup`
  - Lexer file: `org.alloytools.alloy.core/parser/Alloy.lex`
- **License:** Apache 2.0 (migrated from MIT)
- **Latest release:** Alloy 6.2.0 (January 2025)
- **Repository:**
  [AlloyTools/org.alloytools.alloy](https://github.com/AlloyTools/org.alloytools.alloy)

### Parser Accessibility

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

### What Alloy Already Handles (of our 14 constructs)

| Our Construct             | Alloy Equivalent                            | Coverage                                        |
| ------------------------- | ------------------------------------------- | ----------------------------------------------- |
| Entity declarations       | `sig` with fields                           | **Full** -- sigs are entities with typed fields |
| Multiplicity annotations  | `one`, `lone`, `some`, `set`                | **Full** -- native to the language              |
| Relational types          | First-class relations                       | **Full** -- Alloy's core strength               |
| Quantified expressions    | `all`, `some`, `no`                         | **Full** -- native quantifiers                  |
| Primed variables          | Alloy 6 `var` + prime                       | **Full** -- temporal extension                  |
| Invariants/facts          | `fact`, `assert`                            | **Full** -- native constructs                   |
| Cardinality operator      | `#`                                         | **Full** -- native operator                     |
| Enum declarations         | `enum` keyword                              | **Full** -- supported                           |
| State declarations        | `var sig` / `var` fields                    | **Partial** -- no dedicated `state` block       |
| Operations with contracts | `pred` with pre/post comments               | **Partial** -- no `requires`/`ensures` keywords |
| Refinement types          | Fact constraints on sigs                    | **Partial** -- no inline `where` syntax         |
| Set/Map/Seq types         | Relations (sets native, maps via relations) | **Partial** -- no `Map[K,V]` or `Seq[T]` syntax |
| Transition declarations   | Can be modeled with pred chains             | **None** -- no dedicated syntax                 |
| Convention blocks         | Not applicable                              | **None** -- no HTTP concept                     |

**Coverage: ~8.5 of 14 constructs (61%)**

### What Would Need to Be Added/Modified

1. **`operation` blocks with `requires`/`ensures`** -- Alloy has predicates but no structured
   operation declaration. Would need new grammar productions in `Alloy.cup` for `operation`,
   `input`, `output`, `requires`, `ensures`.

2. **`state` blocks** -- Alloy 6 has `var` fields but no dedicated state declaration block. Minor
   syntactic addition.

3. **`transition` declarations** -- Entirely new. No Alloy equivalent for declarative state machine
   transitions with `via` and `when` clauses.

4. **`conventions` blocks** -- Entirely new. HTTP mapping is a foreign concept.

5. **`Map[K,V]` and `Seq[T]` type syntax** -- Alloy uses relations for everything. Would need to add
   parameterized collection types on top of Alloy's relational foundation.

6. **Refinement types with `where`** -- Alloy constrains via facts, not inline on type declarations.
   Would need new syntax.

### Integration Effort: HIGH

- CUP is an older parser generator (LALR), less flexible than ANTLR or PEG for adding new
  productions. Grammar conflicts are likely when adding keyword-heavy blocks alongside Alloy's
  existing syntax.
- The Alloy AST (`Expr`, `Sig`, `Func` hierarchy) is tightly coupled to the analyzer and SAT solver.
  Adding new node types requires changes deep in the compiler pipeline.
- The Java ecosystem locks us into JVM deployment, which may conflict with desired target platforms.
- Alloy's type system is relational (everything is a relation of atoms), which does not map cleanly
  to REST data types like `String`, `DateTime`, `Map[K,V]`.

### What We Gain

- Bounded model checking via Kodkod/SAT for free -- can verify invariants.
- Temporal operators (`always`, `eventually`) for state property verification.
- Mature relational reasoning (transitive closure, join, etc.).
- Academic credibility and a well-understood formal foundation.

### What We Lose

- **Syntax familiarity:** `sig`/`fact`/`pred` vocabulary is alien to most developers. The
  comparative survey rates Alloy syntax as "Medium" friendliness.
- **String handling:** Alloy's string support is extremely weak. It cannot reason about string
  operations, regex matching, or URL validation -- critical for REST services.
- **Scalability:** Bounded model checking means Alloy cannot prove properties for all sizes. The
  "small scope hypothesis" is useful but not a guarantee.
- **No code generation pipeline:** Alloy is purely analytical. It finds counterexamples but cannot
  generate implementations.
- **Heavyweight dependency:** The Alloy jar bundles SAT solvers, Kodkod, and a GUI application.
  Extracting just the parser is possible but messy.

### Verdict: NOT RECOMMENDED

The impedance mismatch between Alloy's relational atom-based universe and REST service types is too
large. Adding HTTP concepts, structured operations, and developer-friendly syntax to Alloy would
effectively mean rewriting most of the parser and building a new AST on top, gaining only the
bounded checking backend -- which we can invoke separately without coupling to Alloy's parser.

---

## 3. Option 2: Extend Quint

### Architecture Overview

- **Language:** TypeScript (67.1%), Rust (16.3%)
- **Parser technology:** ANTLR 4 (generates TypeScript parser from `.g4` grammar)
- **License:** Apache 2.0
- **Repository:** [informalsystems/quint](https://github.com/informalsystems/quint)
- **Ecosystem:** VSCode extension, LSP server (npm: `@informalsystems/quint-language-server`), REPL,
  Apalache integration for model checking

### Parser Architecture

Quint's compiler pipeline has clearly separated phases:

1. **Parsing:** ANTLR 4 grammar -> ANTLR parse tree -> Quint IR (JSON-serializable)
2. **Name resolution:** Resolves identifiers to their declarations
3. **Type checking:** Constraint-based type inference with unification
4. **Effect checking:** Tracks read/write effects on state variables
5. **Simulation:** Random execution for rapid testing
6. **Model checking:** Translation to TLA+ for Apalache SMT-based checking

The IR uses a visitor pattern with well-documented ADRs (Architecture Decision Records). The IR is
JSON-serializable, meaning external tools can consume it easily.

### What Quint Already Handles

| Our Construct             | Quint Equivalent                               | Coverage                                                                        |
| ------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------------- |
| State declarations        | `var x: int`                                   | **Full** -- native state variables                                              |
| Quantified expressions    | `all`, `exists`                                | **Full** -- native quantifiers                                                  |
| Primed variables          | `x' = expr`                                    | **Full** -- core of the language                                                |
| Invariants/facts          | `val inv = ...` / temporal properties          | **Full** -- native                                                              |
| Enum declarations         | Sum types: `type Status = Active \| Suspended` | **Full** -- discriminated unions                                                |
| Cardinality operator      | `size()` method                                | **Full** -- via standard library                                                |
| Operations with contracts | `action` with boolean pre/postconditions       | **Partial** -- actions can encode pre/post but no `requires`/`ensures` keywords |
| Entity declarations       | No direct equivalent                           | **Partial** -- can use `type` + record, but no multiplicity annotations         |
| Set/Map/Seq types         | `Set[T]`, `Map[K,V]`, `List[T]`                | **Partial** -- sets and maps native, but `List` not `Seq`                       |
| Refinement types          | No inline constraint syntax                    | **None** -- types are not refinable                                             |
| Multiplicity annotations  | Not applicable                                 | **None** -- no relational multiplicities                                        |
| Relational types          | Not applicable                                 | **None** -- no first-class relations                                            |
| Transition declarations   | Can be encoded in actions                      | **None** -- no declarative state machine syntax                                 |
| Convention blocks         | Not applicable                                 | **None** -- no HTTP concept                                                     |

**Coverage: ~6.5 of 14 constructs (46%)**

### What Would Need to Be Added/Modified

1. **`entity` declarations with fields and multiplicities** -- Quint has record types but no
   `entity` keyword, no field-level multiplicity, and no inline `where` constraints. Would need new
   ANTLR grammar rules.

2. **`service` top-level block** -- Quint uses `module`. Would need a new top-level construct or
   treat `service` as a special module.

3. **`operation` blocks with `requires`/`ensures`** -- Quint actions are boolean expressions that
   update state. They can encode pre/postconditions but lack structured input/output declarations
   and explicit contract keywords.

4. **`transition` declarations** -- Entirely new. No Quint equivalent.

5. **`conventions` blocks** -- Entirely new.

6. **Multiplicity annotations** (`one`, `lone`, `some`, `set`) -- Foreign to Quint's type system.
   Would require extending the type checker significantly.

7. **Relational types** (`A -> lone B`) -- Quint uses `Map[A, B]` or `Set[A]`, not relations with
   multiplicity. Fundamental type system difference.

8. **Refinement types** -- Would need `where` clause support on type aliases.

### Integration Effort: MEDIUM-HIGH

- ANTLR 4 grammar is straightforward to extend with new productions. Grammar files are typically
  200-500 lines; adding new rules is well-documented.
- The IR visitor architecture (documented in ADR003) provides a clean extension point. New node
  types can be added to the IR and handled by visitors.
- TypeScript codebase is accessible and well-structured. The compiler phases are cleanly separated
  (unlike Alloy's monolithic parser-to-SAT pipeline).
- However, Quint's type system fundamentally lacks relations and multiplicities. Adding them would
  mean extending the type checker, effect checker, and all downstream consumers -- a substantial
  change.
- Quint's value proposition is TLA+-based temporal reasoning. Our DSL uses temporal concepts
  minimally (mostly for invariants). Much of Quint's complexity (non-determinism, temporal formulas,
  Apalache translation) would be dead weight.

### What We Gain

- **ANTLR grammar** we can extend -- saves parser bootstrapping effort.
- **TypeScript ecosystem** -- VSCode extension, LSP server, REPL.
- **Simulation engine** -- random execution for rapid spec testing.
- **Apalache backend** -- SMT-based model checking for invariant verification.
- **Developer-friendly syntax** -- rated "High" in comparative survey.
- **Active development** -- regular releases, npm packages, growing community.

### What We Lose

- **Relational modeling** -- Quint's set/map types cannot express Alloy-style relations with
  multiplicities. This is our DSL's most distinctive feature.
- **Entity declarations** -- Would need to bolt on an entirely foreign concept.
- **HTTP mapping** -- Still needs to be built from scratch.
- **Fork maintenance burden** -- Forking Quint means maintaining a divergent codebase against
  upstream changes. Quint is actively evolving, so merge conflicts would be constant.
- **No code generation** -- Quint is a specification language, not a generator. The implementation
  pipeline must still be built.

### Verdict: INTERESTING BUT POOR FIT

Quint is the closest existing language to our needs in terms of state management and developer
ergonomics. But the lack of relational types and multiplicities -- the core of our entity modeling
-- means we would need to add so much that the result would be a different language wearing Quint's
skin. The fork maintenance burden is the killer: we would get Quint's tooling for free initially but
pay for it indefinitely in merge conflicts.

---

## 4. Option 3: Use Dafny's Parser

### Architecture Overview

- **Language:** C# (56.9%), Dafny (33.0%), plus Rust, Java, Go, F#
- **Parser technology:** Hand-written recursive descent parser in C#
- **License:** MIT
- **Latest releases:** Active development with frequent releases (Dafny 4.x series)
- **Repository:** [dafny-lang/dafny](https://github.com/dafny-lang/dafny)
- **Verification backend:** Boogie -> Z3 SMT solver

### Parser Architecture

Dafny's parser is a hand-written recursive descent parser in C#, tightly integrated with the
compiler pipeline. The compiler phases are:

1. Parsing -> AST construction
2. Name resolution and type checking
3. Translation to Boogie (intermediate verification language)
4. Boogie generates verification conditions
5. Z3 SMT solver discharges proof obligations
6. Code generation to C#, Java, Go, JavaScript, Python

### What Dafny Already Handles

| Our Construct             | Dafny Equivalent                             | Coverage                                                            |
| ------------------------- | -------------------------------------------- | ------------------------------------------------------------------- |
| Operations with contracts | `method` with `requires`/`ensures`           | **Full** -- Dafny's primary purpose                                 |
| Invariants/facts          | `invariant` in loops, class invariants       | **Full** -- native                                                  |
| Quantified expressions    | `forall`, `exists`                           | **Full** -- native                                                  |
| Enum declarations         | `datatype` with constructors                 | **Full** -- algebraic data types                                    |
| Set/Map/Seq types         | `set<T>`, `map<K,V>`, `seq<T>`               | **Full** -- native collection types                                 |
| Refinement types          | Subset types with `\|` constraint            | **Full** -- `type Pos = x: int \| x > 0`                           |
| Cardinality operator      | `\|s\|` for collection size                  | **Full** -- native                                                  |
| Primed variables          | `old()` for pre-state reference              | **Partial** -- `old()` refers to pre-state, not `x'` for post-state |
| Entity declarations       | `class` with fields                          | **Partial** -- classes but no multiplicities                        |
| State declarations        | Class fields                                 | **Partial** -- no dedicated `state` block                           |
| Multiplicity annotations  | Not applicable                               | **None** -- no relational multiplicities                            |
| Relational types          | Not applicable                         | **None** -- no first-class relations                                |
| Transition declarations   | Can be encoded in methods              | **None** -- no declarative syntax                                   |
| Convention blocks         | Not applicable                         | **None** -- no HTTP concept                                         |

**Coverage: ~8 of 14 constructs (57%)**

### What Would Need to Be Added/Modified

1. **`entity` with multiplicities** -- Dafny classes have fields but no multiplicity annotations.
   Would need new syntax.

2. **`service` top-level block** -- Dafny uses `module`. Would need a new construct.

3. **Relational types** -- Foreign to Dafny's type system.

4. **`transition` declarations** -- Entirely new.

5. **`conventions` blocks** -- Entirely new.

6. **Primed variables** -- Dafny uses `old()` for pre-state, not `x'` for post-state. Different
   paradigm (method-level vs. state-level).

### Integration Effort: VERY HIGH

- The parser is hand-written in C# -- no grammar file to extend. Every new construct requires
  manually coding parser functions, AST nodes, and all downstream handling.
- Dafny's compiler is deeply integrated: parser, resolver, translator, and verifier are tightly
  coupled. Adding new AST node types propagates changes through the entire pipeline to Boogie
  translation.
- C# ecosystem may conflict with deployment targets.
- Dafny is a substantial project (~2M lines). Understanding the codebase enough to safely extend the
  parser is a multi-month investment.
- The smithy-dafny project demonstrates that it is feasible to _generate_ Dafny from external
  models, but not to _extend_ Dafny's parser itself.

### What We Gain

- **Proven verification** -- Z3/Boogie backend provides mathematical proof of correctness, not just
  bounded checking.
- **Code generation** -- Dafny compiles to C#, Java, Go, JS, Python.
- **`requires`/`ensures` syntax** -- Exactly what we want for operation contracts.
- **Mature type system** -- Subset types, generic collections, algebraic data types.
- **smithy-dafny precedent** -- AWS has already mapped Smithy constraint traits to Dafny
  specifications, proving the Smithy-to-Dafny pipeline works.

### What We Lose

- **Relational modeling** -- Dafny thinks in terms of classes, methods, and mathematical types. No
  first-class relations.
- **Multiplicities** -- Cannot express `one`, `lone`, `some`, `set` on fields.
- **Developer ergonomics** -- Dafny syntax is rated "High" for developers familiar with
  verification, but the verification concepts (loop invariants, decreases clauses, ghost variables)
  are intimidating for REST API developers.
- **Implementation body requirement** -- Dafny requires the programmer to write the method body, not
  just the contract. For our use case, the compiler should synthesize the body from the contract.

### Verdict: USE AS BACKEND, NOT AS PARSER

Dafny is extremely valuable as a _verification backend_ (the smithy-dafny pattern: generate Dafny
from our IR, verify with Z3, then generate target code). But extending Dafny's hand-written C#
parser to accommodate our entity/state/ convention syntax is the wrong approach. The parser is not
designed for extension, and the impedance mismatch on relational types and multiplicities is too
large.

---

## 5. Option 4: Use TypeSpec's Parser

### Architecture Overview

- **Language:** TypeScript/JavaScript (primary), Java (58.3% in repo includes Java emitter code), C#
- **Parser technology:** Custom recursive descent parser in TypeScript
- **License:** MIT
- **Stars:** 5.7k GitHub stars
- **Repository:** [microsoft/typespec](https://github.com/microsoft/typespec)
- **Extension model:** Libraries (npm packages) with decorators and emitters

### Extension Architecture

TypeSpec has the most mature extension model of all candidates:

1. **Custom decorators:** Define `@myDecorator` in TypeSpec, implement `$myDecorator` in JavaScript.
   Decorators store metadata via `context.program.stateMap`.

2. **Custom emitters:** TypeSpec emitters traverse the compiled type model using
   `navigateProgram()`, `navigateType()`, `listServices()` and generate arbitrary output.

3. **Typekits:** Provide convenient compiler API access for examining type relationships and
   extracting decorator metadata.

4. **Library packages:** Decorators, types, and emitters are packaged as npm libraries that can be
   imported.

### What TypeSpec Already Handles

| Our Construct             | TypeSpec Equivalent                               | Coverage                                                                         |
| ------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------- |
| Entity declarations       | `model`                                           | **Partial** -- models define shapes but no multiplicities, no inline constraints |
| Enum declarations         | `enum`                                            | **Full** -- native                                                               |
| Set/Map/Seq types         | Array types, `Record<K,V>`                        | **Partial** -- arrays but not `Set` or `Seq` semantics                           |
| Convention blocks         | `@route`, `@get`, `@post`, `@query`, `@path`      | **Full** -- native HTTP decorators                                               |
| Operations with contracts | `op`                                              | **Partial** -- operations define signatures but NO pre/postconditions            |
| Refinement types          | `@minLength`, `@maxLength`, `@pattern` decorators | **Partial** -- structural constraints only                                       |
| State declarations        | Not applicable                                    | **None** -- TypeSpec is stateless                                                |
| Invariants/facts          | Not applicable                                    | **None** -- no constraint language                                               |
| Quantified expressions    | Not applicable                                    | **None** -- no expression language                                               |
| Primed variables          | Not applicable                                    | **None** -- no state concept                                                     |
| Multiplicity annotations  | Not applicable                                    | **None** -- no relational multiplicities                                         |
| Relational types          | Not applicable                                    | **None** -- no first-class relations                                             |
| Transition declarations   | Not applicable                                    | **None** -- no state machines                                                    |
| Cardinality operator      | Not applicable                                    | **None** -- no expression language                                               |

**Coverage: ~3.5 of 14 constructs (25%)**

### What Would Need to Be Added/Modified

Nearly everything behavioral:

1. **An entire expression language** -- TypeSpec has no expression language at all. It describes
   shapes, not behavior. Adding `requires`, `ensures`, quantified expressions, arithmetic, set
   operations, primed variables would require building a complete expression parser and evaluator.

2. **State concept** -- TypeSpec is fundamentally stateless. Adding mutable state variables requires
   a paradigm shift in the type model.

3. **Invariants** -- TypeSpec has no concept of global constraints.

4. **Multiplicity annotations** -- Foreign to TypeSpec's type system.

5. **Relational types** -- Not expressible.

6. **Transition declarations** -- Entirely new.

### Could We Use Custom Decorators Instead?

One might imagine encoding behavioral specs as decorators:

```typespec
@requires("code not in store.keys()")
@ensures("store'[code] = url")
@ensures("#store' = #store + 1")
op shorten(@body request: ShortenRequest): ShortenResponse;
```

This approach fails because:

- Decorator values are opaque strings -- TypeSpec cannot parse, typecheck, or verify them. They are
  just metadata.
- No expression resolution: `store`, `code`, `#store'` have no meaning in TypeSpec's type system.
- We would need to build our own expression parser to interpret these strings, at which point we are
  building a new parser anyway, just with TypeSpec as an awkward wrapper.

### Integration Effort: VERY HIGH (for behavioral specs)

- TypeSpec's extension model is excellent for _structural_ extensions (new decorators on shapes,
  custom emitters that generate different output formats).
- But adding a behavioral specification language to a fundamentally structural tool requires
  building all the hard parts from scratch: expression parser, type checker for expressions, state
  model, verification semantics.
- The TypeSpec parser itself is not designed to be extended with new syntax productions -- only with
  new decorators and types.

### What We Gain

- **HTTP modeling** -- TypeSpec's `@typespec/http` and `@typespec/rest` packages provide the best
  HTTP API modeling of any option.
- **Emitter ecosystem** -- OpenAPI 3, JSON Schema, Protobuf emitters exist.
- **Developer experience** -- VSCode extension, playground, excellent docs.
- **Active community** -- 5.7k stars, Microsoft backing, Linux Foundation.
- **npm ecosystem** -- Easy distribution as packages.

### What We Lose

- **All behavioral specification** -- Must be built from scratch.
- **Verification** -- TypeSpec has no verification capability.
- **The core value proposition** -- Our compiler's differentiator is behavioral specification.
  TypeSpec provides exactly the part we _do not_ need to innovate on (structural API modeling),
  while providing nothing for the part we do.

### Verdict: WRONG DIRECTION

TypeSpec solves the opposite problem. It excels at structural API description but has zero
behavioral specification capability. Extending it to add behavior would mean building our entire
specification language as strings inside decorators or fundamentally restructuring TypeSpec's parser
-- either way, we get almost no benefit from starting with TypeSpec.

However, TypeSpec is an excellent **output target**: our compiler could emit TypeSpec (or OpenAPI
via TypeSpec) as part of the code generation pipeline.

---

## 6. Option 5: Use Smithy's Parser

### Architecture Overview

- **Language:** Java
- **Parser technology:** Hand-written parser for Smithy IDL
- **License:** Apache 2.0
- **Repository:** [smithy-lang/smithy](https://github.com/smithy-lang/smithy)
- **Extension model:** Custom traits (shapes), validators (Java SPI), code generators (plugins)

### Semantic Model and Extension Architecture

Smithy has a sophisticated, well-documented extension model:

1. **Custom traits:** Define new trait shapes in Smithy IDL, implement in Java with `TraitService`
   (discovered via Java SPI). Traits carry metadata that code generators can consume.

2. **Validators:** Implement the `Validator` interface, register via SPI. Run automatically during
   model validation.

3. **Knowledge indexes:** Pre-computed caches on the `Model` object: `TopDownIndex`,
   `HttpBindingIndex`, `PaginatedIndex`, etc.

4. **Model transformers:** `ModelTransformer` for preprocessing (flatten mixins, prune shapes, copy
   error shapes).

5. **Code generators:** `ShapeVisitor` pattern for type-specific dispatch.

### What Smithy Already Handles

| Our Construct             | Smithy Equivalent                           | Coverage                                                                    |
| ------------------------- | ------------------------------------------- | --------------------------------------------------------------------------- |
| Entity declarations       | `resource` + `structure` shapes             | **Partial** -- resources with identifiers/properties, but no multiplicities |
| Enum declarations         | `enum` shape                                | **Full** -- native                                                          |
| Convention blocks         | `@http`, `@readonly`, `@idempotent` traits  | **Full** -- native HTTP binding                                             |
| Operations with contracts | `operation` with input/output shapes        | **Partial** -- structural only, no pre/postconditions                       |
| Refinement types          | `@pattern`, `@length`, `@range` constraints | **Partial** -- structural constraints only                                  |
| Set/Map/Seq types         | `list`, `map` shapes                        | **Partial** -- list and map but not set semantics                           |
| State declarations        | Not applicable                              | **None** -- Smithy is stateless                                             |
| Invariants/facts          | Not applicable                              | **None** -- no constraint language                                          |
| Quantified expressions    | Not applicable                              | **None** -- no expression language                                          |
| Primed variables          | Not applicable                              | **None** -- no state concept                                                |
| Multiplicity annotations  | `@required` only                            | **Minimal** -- binary required/optional only                                |
| Relational types          | Not applicable                              | **None** -- no first-class relations                                        |
| Transition declarations   | Not applicable                              | **None** -- no state machines                                               |
| Cardinality operator      | Not applicable                              | **None** -- no expression language                                          |

**Coverage: ~4 of 14 constructs (29%)**

### The smithy-dafny Precedent

AWS's [smithy-dafny](https://github.com/smithy-lang/smithy-dafny) project is the most relevant
precedent. Its pipeline:

1. Write a Smithy model describing a service API
2. `smithy-dafny-codegen-cli` generates a Dafny API skeleton
3. Developer writes Dafny implementation with `requires`/`ensures`
4. Dafny verifier proves correctness
5. `smithy-dafny-codegen-cli` generates glue code for C#, Java, JS, Go, Python

**Key insight:** smithy-dafny maps Smithy constraint traits (`@pattern`, `@length`, `@range`,
`@required`) to Dafny specifications that are statically checked. This proves the Smithy -> Dafny
verification pipeline works.

**But:** In smithy-dafny, the developer still writes the Dafny implementation body. Our compiler
needs to _synthesize_ the body from the specification. And Smithy's constraints are purely
structural -- no behavioral pre/postconditions.

### Could We Add Pre/Postcondition Traits?

We could define custom Smithy traits:

```text
@trait(selector: "operation")
structure requires {
    conditions: ConditionList
}

@trait(selector: "operation")
structure ensures {
    conditions: ConditionList
}

list ConditionList {
    member: String
}
```

Applied:

```text
@requires(conditions: ["code not in store.keys()"])
@ensures(conditions: ["store'[code] = url", "#store' = #store + 1"])
operation Shorten { ... }
```

This has the same fundamental problem as TypeSpec decorators: the condition strings are opaque.
Smithy cannot parse, typecheck, or verify them. We would need to build a complete expression parser
to interpret these strings, and Smithy's Java-based validator SPI would need to invoke our custom
parser/checker.

### Integration Effort: HIGH

- Custom traits are easy to define in Smithy IDL.
- But traits with opaque expression strings gain nothing from Smithy's type system.
- Building expression parsing, type checking, and verification inside Smithy's Java validator
  framework means writing our entire specification engine in Java, constrained by Smithy's
  validation lifecycle.
- Smithy's parser is hand-written and not designed for syntax extensions.

### What We Gain

- **Service/resource/operation modeling** -- Smithy's resource lifecycle (CRUD) maps well to REST.
- **Code generation ecosystem** -- smithy-codegen generates clients/servers for 7+ languages.
- **AWS battle-testing** -- Used for all AWS services.
- **smithy-dafny pipeline** -- Proven Smithy -> Dafny verification path.
- **Trait extensibility** -- Can add custom metadata to any shape.

### What We Lose

- **All behavioral specification** -- Must be built as opaque strings inside traits.
- **Expression language** -- No benefit from Smithy's parser.
- **State modeling** -- Smithy is stateless.
- **Java lock-in** -- The entire Smithy ecosystem is Java-based.

### Verdict: VALUABLE AS PIPELINE COMPONENT, NOT AS PARSER BASE

Like TypeSpec, Smithy excels at structural service modeling but provides nothing for behavioral
specification. The smithy-dafny precedent is architecturally instructive: it demonstrates that the
right approach is to _generate_ Dafny from a service model, not to cram specifications into service
model annotations.

The optimal use of Smithy in our pipeline would be as an **output format**: our compiler generates
Smithy models (for SDK/client generation) alongside the implementation code.

---

## 7. Option 6: Use Ballerina

### Architecture Overview

- **Language:** Java (23.4% compiler), Ballerina (76.1% standard library)
- **Parser technology:** Originally ANTLR-generated, now custom hand-written parser in Java
- **License:** Apache 2.0
- **Stars:** 3.8k GitHub
- **Repository:**
  [ballerina-platform/ballerina-lang](https://github.com/ballerina-platform/ballerina-lang)
- **Latest release:** Swan Lake 2201.13.2 (March 2026)

### Compiler Architecture

Ballerina has a three-phase compiler:

1. **Front-end (Java):** Lexer -> Parser -> AST -> Symbol creation -> Type checking (structural
   subtyping) -> Desugaring -> BIR generation
2. **Optimizer:** Analysis and transformation on BIR (Ballerina Intermediate Representation, a CFG)
3. **Back-end:** JVM bytecode generation (primary), LLVM native (experimental), WebAssembly
   (planned)

The compiler supports a plugin interface (`CompilerPlugin.java`) that processes annotations on
service nodes, enabling custom validation and code generation.

### What Ballerina Already Handles

| Our Construct             | Ballerina Equivalent                   | Coverage                                                  |
| ------------------------- | -------------------------------------- | --------------------------------------------------------- |
| Entity declarations       | `type` records with structural typing  | **Partial** -- records but no multiplicities              |
| Enum declarations         | `enum` type                            | **Full** -- native                                        |
| Convention blocks         | Native HTTP service declaration        | **Full** -- first-class HTTP                              |
| Operations with contracts | `resource function` with HTTP bindings | **Partial** -- operations but no formal contracts         |
| Set/Map/Seq types         | `map<V>`, arrays, tables               | **Partial** -- maps and arrays but not `Set`              |
| Refinement types          | Not applicable                         | **None** -- no constraint types                           |
| State declarations        | Service-level variables                | **Partial** -- mutable state in services                  |
| Invariants/facts          | Not applicable                         | **None** -- no constraint language                        |
| Quantified expressions    | Not applicable                         | **None** -- no quantifiers (it is a programming language) |
| Primed variables          | Not applicable                         | **None** -- imperative assignment, not spec               |
| Multiplicity annotations  | `?` for optional                       | **Minimal** -- optional/required only                     |
| Relational types          | Not applicable                         | **None** -- no first-class relations                      |
| Transition declarations   | Not applicable                         | **None** -- no state machines                             |
| Cardinality operator      | `.length()` method                     | **Partial** -- method, not operator                       |

**Coverage: ~4.5 of 14 constructs (32%)**

### The Fundamental Mismatch

Ballerina is an _implementation language_, not a _specification language_. This is a category error.
Our compiler needs a language to _describe what_ a service should do (declarative specification),
not _how_ it does it (imperative implementation).

Using Ballerina would mean:

- Writing REST services directly in Ballerina (eliminating the need for our compiler entirely, but
  also eliminating all formal specification benefits)
- OR embedding a specification sublanguage in Ballerina (which means building our parser inside
  Ballerina's ecosystem)

### Integration Effort: VERY HIGH

- Ballerina's parser is hand-written in Java and deeply coupled to its compilation pipeline.
- Adding specification constructs (quantifiers, pre/postconditions, invariants, multiplicities) to
  an imperative language requires fundamental changes to the type checker and semantic analysis.
- The compiler plugin mechanism is designed for annotation processing, not syntax extension.
- 127k+ commits across the master branch means the codebase is massive.

### What We Gain

- **First-class HTTP** -- Best HTTP support of any option.
  `resource function get users() returns User[]` is native syntax.
- **Structural typing** -- Type compatibility by structure, not name.
- **Sequence diagrams** -- Ballerina can visualize service interactions as sequence diagrams.
- **JVM deployment** -- Compiles to JVM bytecode.
- **Active community** -- WSO2 backing, regular releases.

### What We Lose

- **Everything that makes our project distinctive** -- Formal specification, behavioral
  verification, contract-driven synthesis.
- **Declarative semantics** -- Ballerina is imperative. Our specs must be declarative.
- **The entire compiler value proposition** -- If developers write Ballerina, they do not need our
  tool.

### Verdict: NOT APPLICABLE

Ballerina solves a different problem (implementation) than ours (specification). However, Ballerina
is an excellent **compilation target**: our compiler could emit Ballerina service code as one of its
output formats, leveraging Ballerina's native HTTP support.

---

## 8. Comparative Matrix

### Coverage of Our 14 Required Constructs

| Construct                | Alloy 6  | Quint    | Dafny    | TypeSpec | Smithy   | Ballerina |
| ------------------------ | -------- | -------- | -------- | -------- | -------- | --------- |
| Entity declarations      | **Full** | Partial  | Partial  | Partial  | Partial  | Partial   |
| State declarations       | Partial  | **Full** | Partial  | None     | None     | Partial   |
| Operations w/ contracts  | Partial  | Partial  | **Full** | Partial  | Partial  | Partial   |
| Invariants/facts         | **Full** | **Full** | **Full** | None     | None     | None      |
| Transition declarations  | None     | None     | None     | None     | None     | None      |
| Convention blocks        | None     | None     | None     | **Full** | **Full** | **Full**  |
| Multiplicity annotations | **Full** | None     | None     | None     | Minimal  | Minimal   |
| Quantified expressions   | **Full** | **Full** | **Full** | None     | None     | None      |
| Primed variables         | **Full** | **Full** | Partial  | None     | None     | None      |
| Relational types         | **Full** | None     | None     | None     | None     | None      |
| Refinement types         | Partial  | None     | **Full** | Partial  | Partial  | None      |
| Cardinality operator     | **Full** | **Full** | **Full** | None     | None     | Partial   |
| Set/Map/Seq types        | Partial  | Partial  | **Full** | Partial  | Partial  | Partial   |
| Enum declarations        | **Full** | **Full** | **Full** | **Full** | **Full** | **Full**  |

### Summary Scores

| Criterion                   | Alloy 6        | Quint          | Dafny                 | TypeSpec              | Smithy                  | Ballerina               |
| --------------------------- | -------------- | -------------- | --------------------- | --------------------- | ----------------------- | ----------------------- |
| **Constructs covered**      | 8.5/14 (61%)   | 6.5/14 (46%)   | 8/14 (57%)            | 3.5/14 (25%)          | 4/14 (29%)              | 4.5/14 (32%)            |
| **Integration effort**      | High           | Med-High       | Very High             | Very High             | High                    | Very High               |
| **Parser extensibility**    | Low (CUP)      | Medium (ANTLR) | Low (hand-written C#) | Low (hand-written TS) | Low (hand-written Java) | Low (hand-written Java) |
| **Ecosystem value**         | Low (academic) | Medium         | High                  | High                  | High                    | Medium                  |
| **License**                 | Apache 2.0     | Apache 2.0     | MIT                   | MIT                   | Apache 2.0              | Apache 2.0              |
| **Fork maintenance burden** | High           | High           | Very High             | Very High             | High                    | Very High               |
| **Verification backend**    | Kodkod/SAT     | Apalache/SMT   | Z3/Boogie             | None                  | None                    | None                    |
| **Code generation**         | None           | None           | C#/Java/Go/JS/Py      | Via emitters          | Via smithy-codegen      | JVM bytecode            |
| **HTTP awareness**          | None           | None           | None                  | Excellent             | Excellent               | Excellent               |

### The Core Tension

The analysis reveals a fundamental split:

- **Languages strong on behavior** (Alloy, Quint, Dafny) have **no HTTP awareness** and weak/no code
  generation to services.
- **Languages strong on HTTP/API modeling** (TypeSpec, Smithy, Ballerina) have **no behavioral
  specification** capability.

No existing language bridges this gap -- which is exactly why our project exists.

---

## 9. Recommendation

### The Verdict: BUILD A NEW PARSER

**Build a new parser from scratch, but strategically reuse existing tools as pipeline components
rather than as parser bases.**

### Why Not Extend an Existing Parser

1. **No language covers more than 61% of our constructs.** The best candidate (Alloy at 61%) still
   requires adding 5-6 entirely new construct types plus modifying 2-3 existing ones.

2. **The missing constructs are not additive -- they are foundational.** Adding HTTP convention
   blocks to Alloy is not like adding a new decorator; it requires rethinking what the language _is
   for_. Similarly, adding behavioral specifications to TypeSpec/Smithy requires building an entire
   expression language from scratch.

3. **Fork maintenance is a project killer.** Every candidate is under active development. Forking
   creates a permanent maintenance obligation to merge upstream changes -- which will inevitably
   conflict with our extensions. Quint, TypeSpec, and Dafny release frequently and make breaking
   changes.

4. **Parser technology mismatch.** The two most extensible parser technologies (ANTLR for Quint, CUP
   for Alloy) still require deep changes to the AST, type checker, and all downstream phases.
   Extending a grammar file is 5% of the work; the other 95% is extending the semantic analysis
   pipeline.

5. **Language ecosystem lock-in.** Alloy locks us into JVM + SAT solvers. Dafny locks us into C# +
   Boogie/Z3. Quint locks us into TypeScript + Apalache. Building our own parser lets us choose the
   optimal technology stack.

6. **Impedance mismatch on the core differentiator.** Our DSL's unique value is combining relational
   entity modeling (from Alloy), pre/postcondition contracts (from Dafny/VDM), state transitions,
   and HTTP conventions in one language. No existing parser handles this combination, and the pieces
   do not compose: Alloy's relational types are incompatible with Dafny's class-based types, which
   are incompatible with TypeSpec's shape-based types.

### The Recommended Architecture

```
                    OUR NEW PARSER
                    (ANTLR 4 grammar)
                         |
                         v
                    OUR IR (AST)
                    /    |    \
                   /     |     \
                  v      v      v
           Dafny     Smithy    Implementation
           Backend   Export    Code Generator
           (verify)  (SDKs)   (Spring/Express/etc.)
                              |
                              v
                         TypeSpec/OpenAPI
                         (API docs)
```

Instead of extending one parser, **use each existing tool where it excels**:

| Tool                 | Role in Our Pipeline             | Integration Point                                                     |
| -------------------- | -------------------------------- | --------------------------------------------------------------------- |
| **ANTLR 4**          | Parser generator for our grammar | Build time -- generates TypeScript/Java parser from our `.g4` grammar |
| **Dafny**            | Verification backend             | Our IR -> Dafny translation -> Z3 proof discharge                     |
| **Smithy**           | Service model export             | Our IR -> Smithy model -> SDK generation via smithy-codegen           |
| **TypeSpec/OpenAPI** | API documentation                | Our IR -> TypeSpec/OpenAPI -> docs, client stubs                      |
| **Quint/Apalache**   | Alternative model checking       | Our IR -> Quint translation -> Apalache SMT checking                  |
| **Ballerina**        | Optional compilation target      | Our IR -> Ballerina service code (leveraging native HTTP)             |

### Why ANTLR 4 for the New Parser

1. **Our grammar is already designed in EBNF** (in `01_spec_language_design.md`). Converting to
   ANTLR `.g4` format is mechanical.

2. **ANTLR 4 generates TypeScript, Java, Python, C#, Go, and more.** This avoids locking into any
   single ecosystem.

3. **Quint uses ANTLR 4** for exactly the same reasons -- it is the standard tool for DSL parsers
   in 2026.

4. **ANTLR 4's ALL(\*) parsing algorithm** handles the grammar complexity we need (quantified
   expressions, operator precedence, primed variables) without the ambiguity issues of LALR parsers
   like CUP.

5. **Excellent tooling:** ANTLR 4 has VSCode extensions, grammar visualization, test rigs, and a
   large community.

### Effort Comparison

| Approach               | Estimated Effort                                                   | Risk                                              |
| ---------------------- | ------------------------------------------------------------------ | ------------------------------------------------- |
| Extend Alloy 6 parser  | 4-6 months (parser + AST + type checker + drop SAT dependency)     | High (CUP conflicts, relational type mismatch)    |
| Fork and extend Quint  | 3-5 months (grammar + IR + type checker + ongoing merge conflicts) | High (fork maintenance, missing relational types) |
| Extend Dafny parser    | 6-9 months (hand-written C# parser, massive codebase)              | Very High (complexity, C# lock-in)                |
| Build new ANTLR parser | 2-3 months (grammar + AST + type checker, purpose-built)           | Low (clean design, no legacy constraints)         |

Building new is **faster** than extending any existing parser because:

- No time spent understanding a foreign codebase
- No time fighting the existing parser's design assumptions
- No time resolving grammar conflicts with existing productions
- No ongoing maintenance burden from upstream changes
- Complete control over the AST shape for our downstream pipeline

### What to Borrow (Without Forking)

Even though we build a new parser, we should study and borrow _design patterns_ from these
languages:

| From             | Borrow                                                                                               |
| ---------------- | ---------------------------------------------------------------------------------------------------- |
| **Quint**        | ANTLR grammar structure, IR visitor pattern (ADR003), JSON-serializable IR, effect checking approach |
| **Dafny**        | `requires`/`ensures` semantics, refinement type syntax, the concept of "old()" for pre-state         |
| **Alloy**        | Relational type semantics, multiplicity constraint checking, bounded analysis algorithms             |
| **TypeSpec**     | Decorator/convention syntax patterns, emitter architecture for code generation                       |
| **Smithy**       | Resource/operation lifecycle modeling, trait metadata system, knowledge index pattern                |
| **smithy-dafny** | The pipeline pattern: spec model -> Dafny skeleton -> verification -> target code                    |

### Bottom Line

The research confirms the finding from the prior survey: **"No existing language provides all of
[our required constructs]."** The parser reuse question has a clear answer: building a purpose-built
ANTLR 4 parser is faster, lower risk, and more maintainable than extending any existing parser,
while strategically reusing Dafny (verification), Smithy (SDK generation), and TypeSpec/OpenAPI
(documentation) as pipeline components achieves the benefits of each ecosystem without the costs of
parser-level coupling.

### Note on the Langium Evaluation

After this analysis recommended ANTLR 4, a separate evaluation (`09_dsl_compiler_frameworks.md`)
explored whether **Langium** -- a TypeScript-based language workbench by TypeFox -- could replace
ANTLR by providing an integrated parser + LSP + AST generation stack. Langium was initially
recommended as the primary framework.

A subsequent devil's advocate audit reversed that recommendation and confirmed ANTLR 4 (specifically
**antlr-ng**, the production-ready TypeScript port) as the parser technology. The key reasons:

1. **Langium's advantages were overstated for our use case.** Its companion type system library
   (Typir) cannot handle refinement types, relation types, generics, or quantified expressions --
   all of which our DSL requires. We must build ~80% of the type checker ourselves regardless of
   framework choice, eliminating Langium's biggest productivity argument.

2. **ANTLR's community is orders of magnitude larger.** ANTLR has 17k+ GitHub stars and thousands of
   contributors vs. Langium's 985 stars and 22 contributors (mostly TypeFox employees). For a
   multi-year project, community depth matters for bug fixes, documentation, and long-term support.

3. **No framework lock-in.** Langium couples grammar, AST types, scoping, and LSP into a proprietary
   framework. ANTLR4 generates a parser; everything else is built on our own abstractions, making
   each component independently replaceable.

4. **Strategic risk.** TypeFox's announcement of Fastbelt (a Go-based successor 21-33x faster than
   Langium) signals that Langium may enter de facto maintenance mode as TypeFox's best engineers
   shift focus.

The implementation uses **antlr-ng** targeting **TypeScript**, with Z3 via native subprocess for
performance-critical verification and WASM as a deployment fallback. Langium remains a viable future
option if VS Code IDE support with rich completions becomes a top-priority requirement and the
framework matures further.

---

## Sources

### Alloy 6

- [AlloyTools GitHub Repository](https://github.com/AlloyTools/org.alloytools.alloy)
- [Alloy 6 Documentation](https://alloytools.org/documentation.html)
- [CompUtil API Documentation](https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/parser/CompUtil.html)
- [Alloy API Discussion](https://alloytools.discourse.group/t/alloy-api-again/221)
- [Alloy License Discussion](https://github.com/AlloyTools/org.alloytools.alloy/issues/104)

### Quint

- [Quint GitHub Repository](https://github.com/informalsystems/quint)
- [Quint Language Basics](https://quint-lang.org/docs/language-basics)
- [Quint IR Visitor Architecture (ADR003)](https://quint-lang.org/docs/architecture-decision-records/adr003-visiting-ir-components)
- [Quint Type System (ADR005)](https://quint-lang.org/docs/development-docs/architecture-decision-records/adr005-type-system)
- [Quint Language Server (npm)](https://www.npmjs.com/package/@informalsystems/quint-language-server)

### Dafny

- [Dafny GitHub Repository](https://github.com/dafny-lang/dafny)
- [Dafny Documentation](https://dafny.org/dafny/DafnyRef/DafnyRef)
- [Dafny Annotator](https://dafny.org/blog/2025/06/21/dafny-annotator/)
- [DafnyPro at POPL 2026](https://popl26.sigplan.org/details/dafny-2026-papers/12/DafnyPro-LLM-Assisted-Automated-Verification-for-Dafny-Programs)

### TypeSpec

- [TypeSpec GitHub Repository](https://github.com/microsoft/typespec)
- [TypeSpec Emitter Framework](https://typespec.io/docs/extending-typespec/emitter-framework/)
- [Creating TypeSpec Decorators](https://typespec.io/docs/extending-typespec/create-decorators/)
- [TypeSpec Overview (Microsoft Learn)](https://learn.microsoft.com/en-us/azure/developer/typespec/overview)

### Smithy

- [Smithy 2.0 Specification](https://smithy.io/2.0/spec/model.html)
- [Smithy Behavior Traits](https://smithy.io/2.0/spec/behavior-traits.html)
- [Smithy Constraint Traits](https://smithy.io/2.0/spec/constraint-traits.html)
- [Using Smithy's Semantic Model](https://smithy.io/2.0/guides/building-codegen/using-the-semantic-model.html)
- [smithy-dafny GitHub Repository](https://github.com/smithy-lang/smithy-dafny)

### Ballerina

- [Ballerina GitHub Repository](https://github.com/ballerina-platform/ballerina-lang)
- [Ballerina Compiler Architecture](https://github.com/ballerina-platform/ballerina-lang/blob/master/docs/compiler/compiler-architecture.md)
- [Ballerina Language Specification](https://ballerina.io/spec/lang/master/)
- [Ballerina Compiler Plugin](https://github.com/ballerina-platform/ballerina-lang/blob/master/compiler/ballerina-lang/src/main/java/org/ballerinalang/compiler/plugins/CompilerPlugin.java)

### Comparative / General

- [TypeSpec vs Smithy Comparison (Language-Oriented Approach)](https://smizell.com/language-oriented-approach/case-studies)
- [AWS Formally Verified Cloud-Scale Authorization](https://www.amazon.science/publications/formally-verified-cloud-scale-authorization)
- [Formal Software Design with Alloy 6](https://haslab.github.io/formal-software-design/overview/index.html)
