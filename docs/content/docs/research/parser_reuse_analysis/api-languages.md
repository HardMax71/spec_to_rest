---
title: "Reusing an API-language parser"
description: "TypeSpec, Smithy, and Ballerina as the parser front end"
---

## 5. Option 4: Use typespec's parser

### Architecture overview

- Language: TypeScript/JavaScript (primary), Java (58.3% in repo includes Java emitter code), C#
- Parser technology: Custom recursive descent parser in TypeScript
- License: MIT
- Stars: 5.7k GitHub stars
- Repository: [microsoft/typespec](https://github.com/microsoft/typespec)
- Extension model: Libraries (npm packages) with decorators and emitters

### Extension architecture

TypeSpec has the most mature extension model of all candidates:

1. Custom decorators: Define `@myDecorator` in TypeSpec, implement `$myDecorator` in JavaScript.
   Decorators store metadata via `context.program.stateMap`.

2. Custom emitters: TypeSpec emitters traverse the compiled type model using
   `navigateProgram()`, `navigateType()`, `listServices()` and generate arbitrary output.

3. Typekits: Provide convenient compiler API access for examining type relationships and
   extracting decorator metadata.

4. Library packages: Decorators, types, and emitters are packaged as npm libraries that can be
   imported.

### What TypeSpec already handles

| Our Construct             | TypeSpec Equivalent                               | Coverage                                                                         |
| ------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------- |
| Entity declarations       | `model`                                           | **Partial**, models define shapes but no multiplicities, no inline constraints |
| Enum declarations         | `enum`                                            | **Full**, native                                                               |
| Set/Map/Seq types         | Array types, `Record<K,V>`                        | **Partial**, arrays but not `Set` or `Seq` semantics                           |
| Convention blocks         | `@route`, `@get`, `@post`, `@query`, `@path`      | **Full**, native HTTP decorators                                               |
| Operations with contracts | `op`                                              | **Partial**, operations define signatures but NO pre/postconditions            |
| Refinement types          | `@minLength`, `@maxLength`, `@pattern` decorators | **Partial**, structural constraints only                                       |
| State declarations        | Not applicable                                    | **None**, TypeSpec is stateless                                                |
| Invariants/facts          | Not applicable                                    | **None**, no constraint language                                               |
| Quantified expressions    | Not applicable                                    | **None**, no expression language                                               |
| Primed variables          | Not applicable                                    | **None**, no state concept                                                     |
| Multiplicity annotations  | Not applicable                                    | **None**, no relational multiplicities                                         |
| Relational types          | Not applicable                                    | **None**, no first-class relations                                             |
| Transition declarations   | Not applicable                                    | **None**, no state machines                                                    |
| Cardinality operator      | Not applicable                                    | **None**, no expression language                                               |

**Coverage: ~3.5 of 14 constructs (25%)**

### What would need to be added/modified

Nearly everything behavioral:

1. An entire expression language: TypeSpec has no expression language at all. It describes
   shapes, rather than behavior. Adding `requires`, `ensures`, quantified expressions, arithmetic, set
   operations, primed variables would require building a complete expression parser and evaluator.

2. State concept: TypeSpec is fundamentally stateless. Adding mutable state variables requires
   a paradigm shift in the type model.

3. Invariants: TypeSpec has no concept of global constraints.

4. Multiplicity annotations: Foreign to TypeSpec's type system.

5. Relational types: Not expressible.

6. Transition declarations: Entirely new.

### Could we use custom decorators instead?

One might imagine encoding behavioral specs as decorators:

```typespec
@requires("code not in store.keys()")
@ensures("store'[code] = url")
@ensures("#store' = #store + 1")
op shorten(@body request: ShortenRequest): ShortenResponse;
```

This approach fails because:

- Decorator values are opaque strings, TypeSpec cannot parse, typecheck, or verify them. They are
  just metadata.
- No expression resolution: `store`, `code`, `#store'` have no meaning in TypeSpec's type system.
- We would need to build our own expression parser to interpret these strings, at which point we are
  building a new parser anyway, just with TypeSpec as an awkward wrapper.

### Integration effort: VERY HIGH (for behavioral specs)

- TypeSpec's extension model is excellent for _structural_ extensions (new decorators on shapes,
  custom emitters that generate different output formats).
- But adding a behavioral specification language to a fundamentally structural tool requires
  building all the hard parts from scratch: expression parser, type checker for expressions, state
  model, verification semantics.
- The TypeSpec parser itself is not designed to be extended with new syntax productions, only with
  new decorators and types.

### What we gain

- HTTP modeling: TypeSpec's `@typespec/http` and `@typespec/rest` packages provide the best
  HTTP API modeling of any option.
- Emitter ecosystem: OpenAPI 3, JSON Schema, Protobuf emitters exist.
- Developer experience: VSCode extension, playground, excellent docs.
- Active community: 5.7k stars, Microsoft backing, Linux Foundation.
- npm ecosystem: Easy distribution as packages.

### What we lose

- All behavioral specification: Must be built from scratch.
- Verification: TypeSpec has no verification capability.
- The core value proposition: Our compiler's differentiator is behavioral specification.
  TypeSpec provides exactly the part we _do not_ need to innovate on (structural API modeling),
  while providing nothing for the part we do.

### Verdict: WRONG DIRECTION

TypeSpec solves the opposite problem. It excels at structural API description but has zero
behavioral specification capability. Extending it to add behavior would mean building our entire
specification language as strings inside decorators or fundamentally restructuring TypeSpec's parser.
Either way, we get almost no benefit from starting with TypeSpec.

However, TypeSpec is an excellent **output target**: our compiler could emit TypeSpec (or OpenAPI
via TypeSpec) as part of the code generation pipeline.

## 6. Option 5: Use smithy's parser

### Architecture overview

- Language: Java
- Parser technology: Hand-written parser for Smithy IDL
- License: Apache 2.0
- Repository: [smithy-lang/smithy](https://github.com/smithy-lang/smithy)
- Extension model: Custom traits (shapes), validators (Java SPI), code generators (plugins)

### Semantic model and extension architecture

Smithy has a well-documented extension model:

1. Custom traits: Define new trait shapes in Smithy IDL, implement in Java with `TraitService`
   (discovered via Java SPI). Traits carry metadata that code generators can consume.

2. Validators: Implement the `Validator` interface, register via SPI. Run automatically during
   model validation.

3. Knowledge indexes: Pre-computed caches on the `Model` object: `TopDownIndex`,
   `HttpBindingIndex`, `PaginatedIndex`, etc.

4. Model transformers: `ModelTransformer` for preprocessing (flatten mixins, prune shapes, copy
   error shapes).

5. Code generators: `ShapeVisitor` pattern for type-specific dispatch.

### What Smithy already handles

| Our Construct             | Smithy Equivalent                           | Coverage                                                                    |
| ------------------------- | ------------------------------------------- | --------------------------------------------------------------------------- |
| Entity declarations       | `resource` + `structure` shapes             | **Partial**, resources with identifiers/properties, but no multiplicities |
| Enum declarations         | `enum` shape                                | **Full**, native                                                          |
| Convention blocks         | `@http`, `@readonly`, `@idempotent` traits  | **Full**, native HTTP binding                                             |
| Operations with contracts | `operation` with input/output shapes        | **Partial**, structural only, no pre/postconditions                       |
| Refinement types          | `@pattern`, `@length`, `@range` constraints | **Partial**, structural constraints only                                  |
| Set/Map/Seq types         | `list`, `map` shapes                        | **Partial**, list and map but not set semantics                           |
| State declarations        | Not applicable                              | **None**, Smithy is stateless                                             |
| Invariants/facts          | Not applicable                              | **None**, no constraint language                                          |
| Quantified expressions    | Not applicable                              | **None**, no expression language                                          |
| Primed variables          | Not applicable                              | **None**, no state concept                                                |
| Multiplicity annotations  | `@required` only                            | **Minimal**, binary required/optional only                                |
| Relational types          | Not applicable                              | **None**, no first-class relations                                        |
| Transition declarations   | Not applicable                              | **None**, no state machines                                               |
| Cardinality operator      | Not applicable                              | **None**, no expression language                                          |

**Coverage: ~4 of 14 constructs (29%)**

### The smithy-dafny precedent

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

**But.** In smithy-dafny, the developer still writes the Dafny implementation body. Our compiler
needs to _synthesize_ the body from the specification. And Smithy's constraints are purely
structural, no behavioral pre/postconditions.

### Could we add pre/postcondition traits?

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

### Integration effort: HIGH

- Custom traits are easy to define in Smithy IDL.
- But traits with opaque expression strings gain nothing from Smithy's type system.
- Building expression parsing, type checking, and verification inside Smithy's Java validator
  framework means writing our entire specification engine in Java, constrained by Smithy's
  validation lifecycle.
- Smithy's parser is hand-written and not designed for syntax extensions.

### What we gain

- Service/resource/operation modeling: Smithy's resource lifecycle (CRUD) maps well to REST.
- Code generation ecosystem: smithy-codegen generates clients/servers for 7+ languages.
- AWS battle-testing: Used for all AWS services.
- smithy-dafny pipeline: Proven Smithy -> Dafny verification path.
- Trait extensibility: Can add custom metadata to any shape.

### What we lose

- All behavioral specification: Must be built as opaque strings inside traits.
- Expression language: No benefit from Smithy's parser.
- State modeling: Smithy is stateless.
- Java lock-in: The entire Smithy ecosystem is Java-based.

### Verdict: VALUABLE AS PIPELINE COMPONENT, NOT AS PARSER BASE

Like TypeSpec, Smithy excels at structural service modeling but provides nothing for behavioral
specification. The smithy-dafny precedent is architecturally instructive: it demonstrates that the
right approach is to _generate_ Dafny from a service model, rather than to cram specifications into service
model annotations.

The optimal use of Smithy in our pipeline would be as an **output format**: our compiler generates
Smithy models (for SDK/client generation) alongside the implementation code.

## 7. Option 6: Use ballerina

### Architecture overview

- Language: Java (23.4% compiler), Ballerina (76.1% standard library)
- Parser technology: Originally ANTLR-generated, now custom hand-written parser in Java
- License: Apache 2.0
- Stars: 3.8k GitHub
- Repository:
  [ballerina-platform/ballerina-lang](https://github.com/ballerina-platform/ballerina-lang)
- Latest release: Swan Lake 2201.13.2 (March 2026)

### Compiler architecture

Ballerina has a three-phase compiler:

1. Front-end (Java): Lexer -> Parser -> AST -> Symbol creation -> Type checking (structural
   subtyping) -> Desugaring -> BIR generation
2. Optimizer: Analysis and transformation on BIR (Ballerina Intermediate Representation, a CFG)
3. Back-end: JVM bytecode generation (primary), LLVM native (experimental), WebAssembly
   (planned)

The compiler supports a plugin interface (`CompilerPlugin.java`) that processes annotations on
service nodes, enabling custom validation and code generation.

### What ballerina already handles

| Our Construct             | Ballerina Equivalent                   | Coverage                                                  |
| ------------------------- | -------------------------------------- | --------------------------------------------------------- |
| Entity declarations       | `type` records with structural typing  | **Partial**, records but no multiplicities              |
| Enum declarations         | `enum` type                            | **Full**, native                                        |
| Convention blocks         | Native HTTP service declaration        | **Full**, first-class HTTP                              |
| Operations with contracts | `resource function` with HTTP bindings | **Partial**, operations but no formal contracts         |
| Set/Map/Seq types         | `map<V>`, arrays, tables               | **Partial**, maps and arrays but not `Set`              |
| Refinement types          | Not applicable                         | **None**, no constraint types                           |
| State declarations        | Service-level variables                | **Partial**, mutable state in services                  |
| Invariants/facts          | Not applicable                         | **None**, no constraint language                        |
| Quantified expressions    | Not applicable                         | **None**, no quantifiers (it is a programming language) |
| Primed variables          | Not applicable                         | **None**, imperative assignment, rather than spec               |
| Multiplicity annotations  | `?` for optional                       | **Minimal**, optional/required only                     |
| Relational types          | Not applicable                         | **None**, no first-class relations                      |
| Transition declarations   | Not applicable                         | **None**, no state machines                             |
| Cardinality operator      | `.length()` method                     | **Partial**, method, rather than operator                       |

**Coverage: ~4.5 of 14 constructs (32%)**

### The fundamental mismatch

Ballerina is an _implementation language_, rather than a _specification language_. This is a category error.
Our compiler needs a language to _describe what_ a service should do (declarative specification),
not _how_ it does it (imperative implementation).

Using Ballerina would mean:

- Writing REST services directly in Ballerina (eliminating the need for our compiler entirely, but
  also eliminating all formal specification benefits)
- OR embedding a specification sublanguage in Ballerina (which means building our parser inside
  Ballerina's ecosystem)

### Integration effort: VERY HIGH

- Ballerina's parser is hand-written in Java and deeply coupled to its compilation pipeline.
- Adding specification constructs (quantifiers, pre/postconditions, invariants, multiplicities) to
  an imperative language requires fundamental changes to the type checker and semantic analysis.
- The compiler plugin mechanism is designed for annotation processing, rather than syntax extension.
- 127k+ commits across the master branch means the codebase is massive.

### What we gain

- First-class HTTP: Best HTTP support of any option.
  `resource function get users() returns User[]` is native syntax.
- Structural typing: Type compatibility by structure, rather than name.
- Sequence diagrams: Ballerina can visualize service interactions as sequence diagrams.
- JVM deployment: Compiles to JVM bytecode.
- Active community: WSO2 backing, regular releases.

### What we lose

- Everything that makes our project distinctive: Formal specification, behavioral
  verification, contract-driven synthesis.
- Declarative semantics: Ballerina is imperative. Our specs must be declarative.
- The entire compiler value proposition: If developers write Ballerina, they do not need our
  tool.

### Verdict: NOT APPLICABLE

Ballerina solves a different problem (implementation) than ours (specification). However, Ballerina
is an excellent **compilation target**: our compiler could emit Ballerina service code as one of its
output formats, using Ballerina's native HTTP support.
