---
title: "Language workbenches"
description: "Langium, Xtext, Spoofax, and JetBrains MPS"
---

## Context

We are building a compiler for a formal specification DSL for REST services. The DSL requires:
entities, state declarations, operations with pre/postconditions, invariants, and a type system. The
compiler needs: a parser, type checker, IDE support (LSP), error reporting, and code generation (to
OpenAPI, test harnesses, Z3 constraints, etc.).

This document evaluates seven language engineering frameworks for building this compiler.

## 1. Langium (TypeScript-based)

**What it is**: An open-source language engineering framework built entirely in TypeScript by
TypeFox (the creators of Xtext). It is the spiritual successor to Xtext, designed from the ground up
for the web and Node.js ecosystem. Currently at version 4.0, released mid-2025.

**GitHub**: 985 stars, 92 forks, actively maintained (last update April 2026) **npm**: ~254K weekly
downloads; 82+ dependent packages

### Grammar definition

Langium uses an EBNF-like grammar language (`.langium` files) that simultaneously defines:

- The **concrete syntax** (what the user types)
- The **abstract syntax tree** (TypeScript interfaces generated automatically)
- Cross-references (name resolution linkage)

Key grammar features:

- Parser rules: `Person: 'person' name=ID;`
- Assignments: `=` (single), `+=` (array), `?=` (boolean flag)
- Cross-references: `person=[Person:ID]`, resolves references by name
- Cardinalities: `?` (optional), `*` (zero-or-more), `+` (one-or-more)
- Alternatives: `|` for choice
- Unordered groups: `&` operator for properties in any order
- Tree-rewriting actions: For left-recursive expression patterns
- Infix operators (4.0): Declarative precedence/associativity definitions that parse ~50% faster
- Guard conditions: Parameterized rules with conditional sections
- Data type rules: `QualifiedName returns string: ID ('.' ID)*;`
- Rule fragments: Reusable grammar patterns

Example grammar sketch for a service DSL:

```text
grammar ServiceSpec

entry Specification:
    (entities+=Entity | services+=Service | invariants+=Invariant)*;

Entity:
    'entity' name=ID '{'
        (fields+=Field)*
    '}';

Field:
    name=ID ':' type=TypeRef;

TypeRef:
    primitive=PrimitiveType | reference=[Entity:QualifiedName]
    | collection=CollectionType;

CollectionType:
    kind=('List' | 'Set' | 'Map') '<' typeArgs+=TypeRef (',' typeArgs+=TypeRef)* '>';

PrimitiveType:
    name=('String' | 'Integer' | 'Boolean' | 'DateTime' | 'UUID');

Service:
    'service' name=ID '{'
        ('state' '{' (stateDecls+=StateDecl)* '}')?
        (operations+=Operation)*
    '}';

StateDecl:
    name=ID ':' type=TypeRef ('=' init=Expression)?;

Operation:
    'operation' name=ID '(' (params+=Parameter (',' params+=Parameter)*)? ')'
    (':' returnType=TypeRef)?
    '{'
        ('requires' '{' preconditions+=Condition* '}')?
        ('ensures' '{' postconditions+=Condition* '}')?
    '}';

Parameter:
    name=ID ':' type=TypeRef;

Condition:
    expression=Expression ';';

Invariant:
    'invariant' name=ID 'on' target=[Entity:QualifiedName]
    '{' expression=Expression '}';

// Expressions would use infix operators in Langium 4.0
infix BinaryExpr on PrimaryExpr:
    '&&' | '||'
    > '==' | '!=' | '<' | '>' | '<=' | '>='
    > '+' | '-'
    > '*' | '/';
```

### What you get for free

| Capability                    | Quality   | Notes                                                                                       |
| ----------------------------- | --------- | ------------------------------------------------------------------------------------------- |
| **Parser**                    | Excellent | Chevrotain-based, outperforms ANTLR in JS benchmarks                                        |
| **Type-safe AST**             | Excellent | TypeScript interfaces generated from grammar rules                                          |
| **Linking & scoping**         | Good      | Cross-reference resolution with customizable scoping                                        |
| **LSP server**                | Excellent | Deeply integrated; code completion, diagnostics, find references, hover, rename, formatting |
| **VS Code extension**         | Scaffold  | Yeoman generator creates complete extension project                                         |
| **CLI**                       | Scaffold  | Generated alongside the language server                                                     |
| **Web worker**                | Scaffold  | For browser-based editors                                                                   |
| **Workspace management**      | Good      | Multi-file projects, incremental updates                                                    |
| **Validation framework**      | Good      | Register custom checks per AST node type                                                    |
| **Code generation utilities** | Basic     | Text generation helpers with source-map support                                             |

### What you must build yourself

- Type checker: Langium does not include a built-in type system engine. However, **Typir** (also
  by TypeFox) is a companion library (`typir-langium` on npm) providing type inference,
  assignability checking, and validation hooks that integrate into Langium's lifecycle. It is usable
  but still maturing.
- Code generators: You write TypeScript functions that traverse the AST. Langium provides
  utilities for text generation with traceability.
- Solver integration: No built-in support, but `z3-solver` npm package (official Microsoft Z3
  WASM bindings) integrates naturally into the same TypeScript project.

### Maturity assessment

- Version 1.0 released 2023; version 4.0 released mid-2025 with significant features
- Built by TypeFox, the same company behind Xtext, Theia, and Eclipse Sprotty
- Eclipse Foundation project since 2023
- Used in production across multiple companies (specific names not publicly listed)
- Langium AI (announced April 2025, updated June 2025): A toolbox for grounding LLMs on DSL
  knowledge, provides evaluation pipelines, document splitting respecting syntactic boundaries,
  and BNF-derived constrained decoding for LLM token output. Directly relevant to our LLM
  integration needs.

### Learning curve

Moderate. Any developer fluent in TypeScript can be productive within 1-2 weeks. The grammar
language is intuitive for anyone familiar with BNF/EBNF. The Yeoman generator scaffolds a complete
project in minutes.

## 2. Xtext (eclipse/JVM-based)

**What it is**: The mature predecessor to Langium, built on Java/Eclipse/EMF. Has been the industry
standard for DSL engineering since ~2010. Still maintained (requires Java 17+, Eclipse 2024-03+).

**GitHub**: 823 stars, 330 forks

### What xtext gives you vs Langium

| Aspect                   | Xtext                                        | Langium                                |
| ------------------------ | -------------------------------------------- | -------------------------------------- |
| **Maturity**             | 15+ years, battle-tested                     | 5 years, rapidly maturing              |
| **Feature completeness** | More complete (formatting, serialization)    | Catching up; most features present     |
| **Grammar language**     | Very similar EBNF-like syntax                | Nearly identical, evolved from Xtext's |
| **AST**                  | EMF-based EObjects                           | Plain TypeScript objects               |
| **Type system**          | Xbase integration for Java-like type systems | Typir library (newer, less mature)     |
| **Code generation**      | Xtend templates (powerful)                   | TypeScript functions                   |
| **LSP support**          | Added later, some architectural friction     | Native, deeply integrated              |
| **Startup time**         | ~4 seconds (JVM cold start)                  | ~1 second                              |
| **IDE**                  | Eclipse-native + LSP for others              | VS Code native + LSP for others        |
| **Web deployment**       | Possible but complex                         | First-class (web workers)              |

### Is eclipse still required?

For language development: practically yes. The Xtext tooling (grammar editor, generator) runs inside
Eclipse. You can build standalone language servers and CLIs that don't require Eclipse at runtime,
but the development workflow is Eclipse-centric. Attempts to decouple fully from Equinox have faced
architectural challenges (the `xtext.ide` bundle still depends on `org.eclipse.core.runtime`).

### LSP support quality

Functional but not native. Xtext's architecture was designed for Eclipse's own editor framework; LSP
was retrofitted. LSP4J is used but has dependency entanglements with IDE-specific code. Works for
common operations but can have gaps compared to the native Eclipse experience.

### Performance for large files

This is a critical weakness. Xtext's CST consumes ~80% of memory. Full workspace builds require
loading every resource. EMF objects lack thread-safe guarantees, limiting parallelization. For files
exceeding 1MB, response times can exceed 1000ms.

### Recommendation for xtext

**Do not choose Xtext for a new project in 2026.** TypeFox (who created both) explicitly recommends
Langium for new projects. Xtext is in maintenance mode. The technology stack (Java, EMF, Eclipse)
adds overhead without proportional benefits for our use case. The only reason to choose Xtext would
be if you needed Xbase (Java-like type system integration), but Typir is filling that gap for
Langium.

## 3. Spoofax (TU delft)

**What it is**: An academic language workbench from the Programming Languages group at TU Delft.
Unique in offering declarative meta-languages for every aspect of language definition.

**GitHub**: 163 stars (Spoofax 2), 14 stars (Spoofax 3/PIE); last Spoofax 2 release: v2.5.23
(April 2025)

### Architecture: Three declarative meta-languages

1. **SDF3** (Syntax Definition Formalism 3): Declarative syntax specification with disambiguation,
   layout sensitivity, and error recovery. More powerful than BNF-based approaches, supports
   scannerless parsing (no separate lexer), which means it can handle language composition without
   ambiguity.

2. **Statix**: The most unique component. A constraint-based meta-language for static semantics
   using **scope graphs**.
   - You declare type-checking rules as constraints over terms
   - Name binding is modeled via scope graphs, a formalism where scopes are nodes and edges
     represent containment, import, and inheritance relationships
   - Type-checking and name resolution are unified: resolving a name and checking its type happen in
     the same constraint-solving framework
   - Supports generics (demonstrated with Featherweight Generic Java), structural records,
     parametric polymorphism
   - The specification is itself statically checked

3. **Stratego**: A term-rewriting language for transformations. Used for code generation and
   interpretation through pattern-matching rewrite rules with strategy combinators.

### What makes it unique

Statix/scope graphs is genuinely novel. Instead of hand-coding a type checker, you declaratively
specify what your type system _is_, and the solver handles the checking. For a DSL with entities,
operations, pre/postconditions, and invariants, Statix would let you express the type rules very
naturally:

```text
typeOfExpr(s, FieldAccess(e, f)) = T :-
    typeOfExpr(s, e) == ENTITY(entityScope),
    resolveField(entityScope, f) == T.
```

This is intellectually elegant and would produce very high-quality error messages since the
constraint solver knows exactly which constraint failed.

### Practical concerns

| Aspect               | Assessment                                                          |
| -------------------- | ------------------------------------------------------------------- |
| **IDE support**      | Eclipse plugins only (generated from specs)                         |
| **LSP**              | No standalone LSP server                                            |
| **Distribution**     | Eclipse plugin or standalone JVM application                        |
| **Learning curve**   | Steep, SDF3 + Statix + Stratego are three separate meta-languages |
| **Community**        | Small, primarily academic (~20-30 active contributors)              |
| **Documentation**    | Improving but still patchy; Spoofax 3 docs are incomplete           |
| **Spoofax 3 status** | "Experimental, work-in-progress, rather than recommended for production"    |
| **Z3 integration**   | Difficult, JVM-based, would need JNI bindings                     |
| **LLM integration**  | No ecosystem support                                                |
| **Web deployment**   | Not supported                                                       |

### Industrial use

One notable case study: **OIL (Open Interaction Language)**, an industrial DSL for control software.
Research found Spoofax more productive than Python for implementing this DSL, especially for editor
services. However, this remains exceptional.

### Verdict on spoofax

The Statix type system specification approach is the most powerful and theoretically sound of any
framework evaluated here. However, the practical tradeoffs are severe: Eclipse-only IDE, no LSP,
tiny community, steep learning curve across three meta-languages, no web or VS Code story, and
Spoofax 3 is not production-ready. We should **study Statix's scope graph approach as intellectual
inspiration** for our type checker design but **not adopt Spoofax as our framework**.

## 4. Jetbrains MPS

**What it is**: A projectional editing environment for DSLs. Instead of text-based editing with
parsing, users directly edit the AST, which is _projected_ as text, tables, diagrams, or mixed
notations.

**GitHub**: 1,644 stars, 311 forks

### How projectional editing differs

In traditional DSL tools: User types text -> parser converts to AST -> tools operate on AST. In MPS:
User edits AST directly -> MPS projects it as whatever notation you choose.

Key consequences:

- No parsing required: The AST is the source of truth
- No ambiguity: Multiple languages can be freely composed without grammar conflicts
- Rich notations: Tables, images, math notation, GUI widgets can be part of the syntax
- No syntax errors: The editor only allows structurally valid edits

### Git integration

This is the major practical problem. Since code is stored as XML (not human-readable text):

- Standard `git diff` and `git merge` are nearly useless on MPS model files
- MPS provides custom diff/merge tools that work on the AST level using UUIDs
- Merge conflicts must be resolved inside MPS, rather than in any text editor or GitHub UI
- Code review on GitHub/GitLab is impractical, you cannot read the XML diffs
- Teams must use MPS's built-in VCS integration for effective collaboration

### Scalability

- Single-root elements handle up to ~4,000 lines without issues
- Tested with ~100,000 lines of C code
- Adoption curve: "a few days for most users to become accustomed"

### Practical assessment for our use case

| Factor              | Assessment                                                     |
| ------------------- | -------------------------------------------------------------- |
| **User experience** | Users must install MPS or a standalone MPS-based IDE (~500MB+) |
| **Distribution**    | Standalone IDE or MPS plugin (heavyweight)                     |
| **Learning curve**  | Steep for language designers; moderate for end users           |
| **Git workflow**    | Severely impacted, no standard code review                   |
| **Web deployment**  | Not supported natively                                         |
| **LSP**             | Not applicable (no text-based editing)                         |
| **Z3 integration**  | Possible via Java/Kotlin, but unconventional                   |
| **LLM integration** | Difficult, LLMs generate text, not AST operations            |
| **Community**       | Moderate but niche; heavily JetBrains-dependent                |

### Verdict on MPS

**Not suitable for our use case.** Our DSL will be text-based (to integrate with standard developer
workflows, version control, code review, CI/CD, LLM generation). The Git story alone is
disqualifying. MPS excels for DSLs used by non-programmers (business analysts, domain experts) who
benefit from rich visual notations, but that is not our target audience.
