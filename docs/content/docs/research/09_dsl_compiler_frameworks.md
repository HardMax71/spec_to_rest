---
title: "DSL Compiler Frameworks"
description: "Parser and IDE framework evaluation (ANTLR4 vs Langium vs others)"
---

> **Post-Audit Update (April 2026):** The original analysis below recommended Langium as the primary
> parser/IDE framework. After a devil's advocate audit (documented in `audit/06_langium_risks.md`
> before it was folded into this update), the recommendation has changed to **ANTLR4 via antlr-ng**
> in TypeScript. The Langium analysis is preserved in full as valuable reference material -- the
> framework remains a viable future option if IDE support becomes the top priority and the project
> matures further. See the [Updated Recommendation](#updated-recommendation) section below for the
> revised decision and rationale.
>
> **Key audit findings that drove the change:**
>
> 1. **Typir cannot handle our type system.** It lacks refinement types, relation types, generics,
>    and quantified expressions. We must build ~80% of our type checker ourselves regardless,
>    eliminating Typir as a load-bearing advantage.
> 2. **Langium AI is effectively vaporware.** Version 0.0.2, 20 GitHub stars, incompatible with
>    Langium 4.0, and the "AI features that leverage LLMs directly" are listed as "coming soon." Not
>    a differentiating factor.
> 3. **Fastbelt (Go-based successor) signals strategic drift.** TypeFox is building a Go-based
>    high-performance successor that is 21-33x faster than Langium. This raises questions about
>    where TypeFox's best engineers will focus over the next 2-3 years.
> 4. **Community is very thin.** 985 stars, 22 contributors (mostly TypeFox employees). If we hit a
>    framework bug, we may need to debug and patch Langium ourselves.
> 5. **Z3 WASM works but is 2-5x slower than native.** For performance-critical constraint solving,
>    a native Z3 subprocess is preferable, with WASM as a fallback for environments where native
>    binaries are unavailable.
> 6. **ANTLR4/antlr-ng provides a stronger foundation.** 17k+ GitHub stars, battle-tested ALL(\*)
>    parsing, massive community, no framework lock-in, and the antlr-ng TypeScript port is
>    production-ready and blessed by Terence Parr. We build our own type checker (which we had to do
>    anyway) and add LSP support incrementally when needed.

---

## Context

We are building a compiler for a formal specification DSL for REST services. The DSL requires:
entities, state declarations, operations with pre/postconditions, invariants, and a type system. The
compiler needs: a parser, type checker, IDE support (LSP), error reporting, and code generation (to
OpenAPI, test harnesses, Z3 constraints, etc.).

This document evaluates seven language engineering frameworks for building this compiler.

---

## 1. Langium (TypeScript-based)

**What it is**: An open-source language engineering framework built entirely in TypeScript by
TypeFox (the creators of Xtext). It is the spiritual successor to Xtext, designed from the ground up
for the web and Node.js ecosystem. Currently at version 4.0, released mid-2025.

**GitHub**: 985 stars, 92 forks, actively maintained (last update April 2026) **npm**: ~254K weekly
downloads; 82+ dependent packages

### Grammar Definition

Langium uses an EBNF-like grammar language (`.langium` files) that simultaneously defines:

- The **concrete syntax** (what the user types)
- The **abstract syntax tree** (TypeScript interfaces generated automatically)
- **Cross-references** (name resolution linkage)

Key grammar features:

- **Parser rules**: `Person: 'person' name=ID;`
- **Assignments**: `=` (single), `+=` (array), `?=` (boolean flag)
- **Cross-references**: `person=[Person:ID]` -- resolves references by name
- **Cardinalities**: `?` (optional), `*` (zero-or-more), `+` (one-or-more)
- **Alternatives**: `|` for choice
- **Unordered groups**: `&` operator for properties in any order
- **Tree-rewriting actions**: For left-recursive expression patterns
- **Infix operators (4.0)**: Declarative precedence/associativity definitions that parse ~50% faster
- **Guard conditions**: Parameterized rules with conditional sections
- **Data type rules**: `QualifiedName returns string: ID ('.' ID)*;`
- **Rule fragments**: Reusable grammar patterns

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

### What You Get For Free

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

### What You Must Build Yourself

- **Type checker**: Langium does not include a built-in type system engine. However, **Typir** (also
  by TypeFox) is a companion library (`typir-langium` on npm) providing type inference,
  assignability checking, and validation hooks that integrate into Langium's lifecycle. It is usable
  but still maturing.
- **Code generators**: You write TypeScript functions that traverse the AST. Langium provides
  utilities for text generation with traceability.
- **Solver integration**: No built-in support, but `z3-solver` npm package (official Microsoft Z3
  WASM bindings) integrates naturally into the same TypeScript project.

### Maturity Assessment

- Version 1.0 released 2023; version 4.0 released mid-2025 with significant features
- Built by TypeFox, the same company behind Xtext, Theia, and Eclipse Sprotty
- Eclipse Foundation project since 2023
- Used in production across multiple companies (specific names not publicly listed)
- **Langium AI** (announced April 2025, updated June 2025): A toolbox for grounding LLMs on DSL
  knowledge -- provides evaluation pipelines, document splitting respecting syntactic boundaries,
  and BNF-derived constrained decoding for LLM token output. Directly relevant to our LLM
  integration needs.

### Learning Curve

Moderate. Any developer fluent in TypeScript can be productive within 1-2 weeks. The grammar
language is intuitive for anyone familiar with BNF/EBNF. The Yeoman generator scaffolds a complete
project in minutes.

---

## 2. Xtext (Eclipse/JVM-based)

**What it is**: The mature predecessor to Langium, built on Java/Eclipse/EMF. Has been the industry
standard for DSL engineering since ~2010. Still maintained (requires Java 17+, Eclipse 2024-03+).

**GitHub**: 823 stars, 330 forks

### What Xtext Gives You vs Langium

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

### Is Eclipse Still Required?

For language development: practically yes. The Xtext tooling (grammar editor, generator) runs inside
Eclipse. You can build standalone language servers and CLIs that don't require Eclipse at runtime,
but the development workflow is Eclipse-centric. Attempts to decouple fully from Equinox have faced
architectural challenges (the `xtext.ide` bundle still depends on `org.eclipse.core.runtime`).

### LSP Support Quality

Functional but not native. Xtext's architecture was designed for Eclipse's own editor framework; LSP
was retrofitted. LSP4J is used but has dependency entanglements with IDE-specific code. Works for
common operations but can have gaps compared to the native Eclipse experience.

### Performance for Large Files

This is a critical weakness. Xtext's CST consumes ~80% of memory. Full workspace builds require
loading every resource. EMF objects lack thread-safe guarantees, limiting parallelization. For files
exceeding 1MB, response times can exceed 1000ms.

### Recommendation for Xtext

**Do not choose Xtext for a new project in 2026.** TypeFox (who created both) explicitly recommends
Langium for new projects. Xtext is in maintenance mode. The technology stack (Java, EMF, Eclipse)
adds overhead without proportional benefits for our use case. The only reason to choose Xtext would
be if you needed Xbase (Java-like type system integration), but Typir is filling that gap for
Langium.

---

## 3. Spoofax (TU Delft)

**What it is**: An academic language workbench from the Programming Languages group at TU Delft.
Unique in offering declarative meta-languages for every aspect of language definition.

**GitHub**: 163 stars (Spoofax 2), 14 stars (Spoofax 3/PIE); last Spoofax 2 release: v2.5.23
(April 2025)

### Architecture: Three Declarative Meta-Languages

1. **SDF3** (Syntax Definition Formalism 3): Declarative syntax specification with disambiguation,
   layout sensitivity, and error recovery. More powerful than BNF-based approaches -- supports
   scannerless parsing (no separate lexer), which means it can handle language composition without
   ambiguity.

2. **Statix**: The most unique component. A constraint-based meta-language for static semantics
   using **scope graphs**.
   - You declare type-checking rules as constraints over terms
   - Name binding is modeled via scope graphs -- a formalism where scopes are nodes and edges
     represent containment, import, and inheritance relationships
   - Type-checking and name resolution are unified: resolving a name and checking its type happen in
     the same constraint-solving framework
   - Supports generics (demonstrated with Featherweight Generic Java), structural records,
     parametric polymorphism
   - The specification is itself statically checked

3. **Stratego**: A term-rewriting language for transformations. Used for code generation and
   interpretation through pattern-matching rewrite rules with strategy combinators.

### What Makes It Unique

Statix/scope graphs is genuinely novel. Instead of hand-coding a type checker, you declaratively
specify what your type system _is_, and the solver handles the checking. For a DSL with entities,
operations, pre/postconditions, and invariants, Statix would let you express the type rules very
naturally:

```
typeOfExpr(s, FieldAccess(e, f)) = T :-
    typeOfExpr(s, e) == ENTITY(entityScope),
    resolveField(entityScope, f) == T.
```

This is intellectually elegant and would produce very high-quality error messages since the
constraint solver knows exactly which constraint failed.

### Practical Concerns

| Aspect               | Assessment                                                          |
| -------------------- | ------------------------------------------------------------------- |
| **IDE support**      | Eclipse plugins only (generated from specs)                         |
| **LSP**              | No standalone LSP server                                            |
| **Distribution**     | Eclipse plugin or standalone JVM application                        |
| **Learning curve**   | Steep -- SDF3 + Statix + Stratego are three separate meta-languages |
| **Community**        | Small, primarily academic (~20-30 active contributors)              |
| **Documentation**    | Improving but still patchy; Spoofax 3 docs are incomplete           |
| **Spoofax 3 status** | "Experimental, work-in-progress, not recommended for production"    |
| **Z3 integration**   | Difficult -- JVM-based, would need JNI bindings                     |
| **LLM integration**  | No ecosystem support                                                |
| **Web deployment**   | Not supported                                                       |

### Industrial Use

One notable case study: **OIL (Open Interaction Language)**, an industrial DSL for control software.
Research found Spoofax more productive than Python for implementing this DSL, especially for editor
services. However, this remains exceptional.

### Verdict on Spoofax

The Statix type system specification approach is the most powerful and theoretically sound of any
framework evaluated here. However, the practical tradeoffs are severe: Eclipse-only IDE, no LSP,
tiny community, steep learning curve across three meta-languages, no web or VS Code story, and
Spoofax 3 is not production-ready. We should **study Statix's scope graph approach as intellectual
inspiration** for our type checker design but **not adopt Spoofax as our framework**.

---

## 4. JetBrains MPS

**What it is**: A projectional editing environment for DSLs. Instead of text-based editing with
parsing, users directly edit the AST, which is _projected_ as text, tables, diagrams, or mixed
notations.

**GitHub**: 1,644 stars, 311 forks

### How Projectional Editing Differs

In traditional DSL tools: User types text -> parser converts to AST -> tools operate on AST. In MPS:
User edits AST directly -> MPS projects it as whatever notation you choose.

Key consequences:

- **No parsing required**: The AST is the source of truth
- **No ambiguity**: Multiple languages can be freely composed without grammar conflicts
- **Rich notations**: Tables, images, math notation, GUI widgets can be part of the syntax
- **No syntax errors**: The editor only allows structurally valid edits

### Git Integration

This is the major practical problem. Since code is stored as XML (not human-readable text):

- Standard `git diff` and `git merge` are nearly useless on MPS model files
- MPS provides custom diff/merge tools that work on the AST level using UUIDs
- Merge conflicts must be resolved inside MPS, not in any text editor or GitHub UI
- Code review on GitHub/GitLab is impractical -- you cannot read the XML diffs
- Teams must use MPS's built-in VCS integration for effective collaboration

### Scalability

- Single-root elements handle up to ~4,000 lines without issues
- Tested with ~100,000 lines of C code
- Adoption curve: "a few days for most users to become accustomed"

### Practical Assessment for Our Use Case

| Factor              | Assessment                                                     |
| ------------------- | -------------------------------------------------------------- |
| **User experience** | Users must install MPS or a standalone MPS-based IDE (~500MB+) |
| **Distribution**    | Standalone IDE or MPS plugin (heavyweight)                     |
| **Learning curve**  | Steep for language designers; moderate for end users           |
| **Git workflow**    | Severely impacted -- no standard code review                   |
| **Web deployment**  | Not supported natively                                         |
| **LSP**             | Not applicable (no text-based editing)                         |
| **Z3 integration**  | Possible via Java/Kotlin, but unconventional                   |
| **LLM integration** | Difficult -- LLMs generate text, not AST operations            |
| **Community**       | Moderate but niche; heavily JetBrains-dependent                |

### Verdict on MPS

**Not suitable for our use case.** Our DSL will be text-based (to integrate with standard developer
workflows, version control, code review, CI/CD, LLM generation). The Git story alone is
disqualifying. MPS excels for DSLs used by non-programmers (business analysts, domain experts) who
benefit from rich visual notations, but that is not our target audience.

---

## 5. Racket #lang + Rosette

**What it is**: Racket is a language-oriented programming environment where creating new languages
(#lang) is a first-class capability. Rosette extends Racket with solver-aided features backed by Z3.

**GitHub**: Rosette -- 688 stars, 81 forks

### Building a DSL as a #lang

In Racket, a "language" is defined by providing a reader (parser) and a module expander. You can
create `#lang spec-rest` and have it be a fully custom syntax. What you get:

- **DrRacket IDE support**: Syntax coloring, REPL, debugging, documentation
- **Macro system**: The most powerful macro system in any language -- can implement arbitrary syntax
  transformations
- **Module system**: First-class language composition
- **Test framework**: Built-in

### The Rosette/Z3 Opportunity

This is where Racket becomes uniquely interesting for our project:

- Write an interpreter for our spec language in Rosette
- Automatically get **verification** ("does this precondition ever fail?"), **synthesis** ("generate
  an implementation satisfying the postcondition"), and **debugging** ("find a counterexample")
- Z3 integration is built-in and deeply optimized
- Cloudflare uses Rosette in production for DNS policy verification (topaz-lang)

Cloudflare's experience is instructive:

- Built a Lisp-like DSL (topaz-lang) for DNS policies
- Rosette verifies satisfiability, reachability, and exclusivity of policies
- 7 programs verify in ~6 seconds; 50 programs in ~300 seconds
- Limitation: Must maintain a Racket interpreter synchronized with the production Go interpreter
- Limitation: String manipulation beyond equality is unsupported in verification mode

### Practical Concerns

| Factor              | Assessment                                                               |
| ------------------- | ------------------------------------------------------------------------ |
| **IDE support**     | DrRacket (specialized IDE) + limited VS Code via racket-langserver       |
| **LSP**             | Basic -- racket-langserver exists but is not feature-rich                |
| **Distribution**    | Requires Racket installation (~200MB)                                    |
| **Learning curve**  | Steep -- Racket, macros, Rosette, S-expressions                          |
| **User syntax**     | S-expression based unless you build a custom reader (significant effort) |
| **Community**       | Active but academic-leaning (~5K GitHub stars for Racket)                |
| **Error messages**  | Good within Racket; custom readers need custom error handling            |
| **Code generation** | Manual -- Racket has string templating but nothing like Langium's infra  |
| **LLM integration** | No ecosystem support; LLMs struggle with S-expressions                   |
| **Performance**     | Rosette/Z3 verification can be slow for complex specs                    |

### Verdict on Racket/Rosette

**The wrong primary framework but the right verification backend.** Building our entire compiler in
Racket would mean: (a) forcing an S-expression syntax or building a custom reader from scratch, (b)
losing LSP/VS Code integration quality, (c) requiring all users to install Racket, (d) making LLM
integration much harder. However, Rosette's solver-aided capabilities are directly relevant to our
verification needs. The optimal approach: **build the compiler in Langium, generate Z3 constraints
in TypeScript using the z3-solver npm package**, and consider Rosette only if we need synthesis or
symbolic execution capabilities that are hard to replicate directly.

---

## 6. tree-sitter + Custom Tooling

**What it is**: tree-sitter is an incremental parsing library used by many editors (VS Code, Neovim,
Helix, Zed, GitHub). You define a grammar in JavaScript, and it generates a C parser.

**GitHub**: 24,500 stars, 2,544 forks (by far the most popular tool in this comparison)

### What tree-sitter Gives You

- **Incremental parsing**: Re-parses only changed regions; sub-millisecond updates
- **Error recovery**: Always produces a valid tree, even with syntax errors
- **Syntax highlighting**: Query-based highlighting that editors consume directly
- **Code folding**: Structure-based folding
- **Indentation**: Can derive indentation rules
- **Basic structural navigation**: Parent/child/sibling traversal

### What tree-sitter Does NOT Give You

- **No AST types**: The parse tree is a generic CST; you must define and build your own typed AST
- **No cross-reference resolution**: No name binding, no scoping
- **No type checking**: Zero semantic analysis
- **No LSP server**: tree-sitter is NOT a language server -- it provides parsing only
- **No validation framework**: You must build all diagnostics from scratch
- **No code generation infrastructure**: Nothing
- **No code completion**: Beyond syntax-driven suggestions

### How Much Work Is "Everything Else"?

This is the critical question. Estimated effort for a DSL of our complexity:

| Component                                                      | tree-sitter provides | Effort to build             |
| -------------------------------------------------------------- | -------------------- | --------------------------- |
| Grammar / parser                                               | Yes                  | 1-2 weeks                   |
| Typed AST from CST                                             | No                   | 2-3 weeks                   |
| Name resolution / scoping                                      | No                   | 3-4 weeks                   |
| Type checker                                                   | No                   | 4-6 weeks                   |
| Validation / error reporting                                   | No                   | 2-3 weeks                   |
| LSP server (completion, hover, go-to-def, diagnostics, rename) | No                   | 6-8 weeks                   |
| Code generation                                                | No                   | (Same regardless of parser) |
| **Total additional effort beyond parser**                      |                      | **17-24 weeks**             |

With Langium, most of the "No" items above are provided or scaffolded, reducing the effort to
customization rather than implementation.

### Verdict on tree-sitter

**Not appropriate as the primary framework.** tree-sitter is a parser, not a language workbench. For
our DSL, we would spend months building infrastructure that Langium provides out of the box.
However, tree-sitter could be useful as a **secondary artifact**: we could generate a tree-sitter
grammar from our Langium grammar to provide syntax highlighting in editors that use tree-sitter
natively (Neovim, Helix, Zed).

---

## 7. ANTLR4 + Custom Tooling

**What it is**: The most widely-used parser generator in the world. Generates parsers in Java, C#,
Python, JavaScript, TypeScript, Go, C++, and more from a single grammar.

**GitHub**: 18,809 stars, 3,430 forks

### What ANTLR4 Provides

- **Parser + lexer generation** from a `.g4` grammar file
- **Visitor and listener patterns** for AST traversal
- **Multiple target languages** (11+)
- **Excellent error reporting** with error recovery strategies
- **Large grammar repository** (grammars-v4 on GitHub)
- **Mature, battle-tested** (20+ years of development)
- **ALL(\*) parsing algorithm** (handles most grammars without ambiguity issues)

### ANTLR4 vs Langium: The Key Difference

ANTLR4 gives you a parser. Langium gives you a parser + typed AST + scoping + linking + LSP server +
VS Code extension + validation framework + code generation utilities.

| Component                  | ANTLR4                  | Langium                               |
| -------------------------- | ----------------------- | ------------------------------------- |
| Parser                     | Yes                     | Yes (Chevrotain, equally capable)     |
| Typed AST                  | No (generic parse tree) | Yes (generated TypeScript interfaces) |
| Cross-reference resolution | No                      | Yes                                   |
| Scoping                    | No                      | Yes (customizable)                    |
| LSP server                 | No                      | Yes (comprehensive)                   |
| VS Code extension          | No                      | Yes (scaffolded)                      |
| Validation framework       | No                      | Yes                                   |
| Code generation helpers    | No                      | Yes (with source maps)                |
| Type system library        | No                      | Typir (companion)                     |
| LLM integration toolkit    | No                      | Langium AI                            |

Langium uses Chevrotain (not ANTLR) internally but provides a grammar language very similar to
ANTLR's. The key philosophical difference: ANTLR is a parser generator; Langium is a language
workbench.

### When ANTLR4 Still Makes Sense

- You need the parser in a non-TypeScript language (Java, C#, Python)
- You already have an ANTLR grammar and want to reuse it
- You want maximum control over every component
- You need to generate parsers for multiple target platforms

### Effort Comparison

For our DSL with full IDE support:

- **ANTLR4 + custom everything**: ~20-28 weeks
- **Langium**: ~8-12 weeks (grammar + custom type checker + code generators)

### Verdict on ANTLR4

**Choose Langium instead.** ANTLR4 is an excellent tool, but Langium wraps an equally capable parser
with all the additional infrastructure we need. The only scenario where ANTLR4 would be preferable
is if we needed multi-language parser targets, but our compiler will be TypeScript.

---

## Comparative Assessment Matrix

| Criterion                    | Langium                       | Xtext                           | Spoofax                       | MPS                          | Racket/Rosette                | tree-sitter            | ANTLR4                 |
| ---------------------------- | ----------------------------- | ------------------------------- | ----------------------------- | ---------------------------- | ----------------------------- | ---------------------- | ---------------------- |
| **Time to working parser**   | 1-2 days                      | 2-3 days                        | 3-5 days                      | 1-2 weeks                    | 1-2 weeks                     | 1-2 weeks              | 3-5 days               |
| **Time to full IDE support** | 2-3 weeks                     | 3-4 weeks                       | 4-6 weeks                     | Built-in (but MPS-only)      | Months                        | Months                 | Months                 |
| **Error message quality**    | Good (customizable)           | Good                            | Excellent (constraint-based)  | N/A (no parse errors)        | Variable                      | Basic                  | Good                   |
| **IDE support OOB**          | Excellent (VS Code)           | Good (Eclipse native)           | Good (Eclipse only)           | Excellent (MPS only)         | Fair (DrRacket)               | Syntax only            | None                   |
| **Z3/solver integration**    | Good (z3-solver npm)          | Possible (JNI)                  | Difficult                     | Possible (Java)              | Excellent (native)            | Manual                 | Depends on target      |
| **LLM integration**          | Excellent (Langium AI)        | None                            | None                          | Poor                         | None                          | None                   | None                   |
| **Distribution**             | npm package / VS Code ext     | JVM jar / Eclipse plugin        | Eclipse plugin                | Standalone IDE (~500MB)      | Racket install                | Native binary          | Depends on target      |
| **Learning curve**           | Moderate (TypeScript)         | Moderate-High (Java/EMF)        | Steep (3 meta-languages)      | Steep (projectional)         | Steep (Racket/macros)         | Low (JS grammar)       | Low-Moderate           |
| **Community size**           | Medium-growing (985 GH stars) | Medium-declining (823 GH stars) | Small-academic (163 GH stars) | Medium-niche (1.6K GH stars) | Small-academic (688 GH stars) | Large (24.5K GH stars) | Large (18.8K GH stars) |
| **Maintenance status**       | Active (v4.0, 2025)           | Maintenance mode                | Active but academic           | Active (JetBrains)           | Active but slow               | Very active            | Active                 |

---

## Updated Recommendation

### Primary Choice: ANTLR4 via antlr-ng (TypeScript)

> This section supersedes the original Langium recommendation below, which is preserved for
> reference. The change was driven by a devil's advocate audit that stress-tested every Langium
> advantage against primary sources.

**ANTLR4 via antlr-ng is the chosen parser framework.** The implementation language is TypeScript.
We build our own type checker. Z3 integration uses native subprocess for performance-critical
checks, with WASM (`z3-solver` npm) as fallback.

**Why ANTLR4/antlr-ng over Langium:**

1. **Community and ecosystem stability**: ANTLR has 17k+ GitHub stars, thousands of contributors,
   extensive documentation and books, and a 20+ year track record. Langium has 985 stars and 22
   contributors, mostly TypeFox employees. If we hit a parser bug in ANTLR, we have a massive
   community to draw on.

2. **No framework lock-in**: Langium couples grammar, AST, scoping, LSP, and validation into a
   single proprietary framework. Migration away from Langium requires rewriting all of these. With
   ANTLR4, each concern is a separate, replaceable component.

3. **Typir cannot carry our type system**: The audit confirmed that Typir (Langium's companion type
   system library) lacks refinement types, relation types, generics, and quantified expressions --
   all of which our DSL requires. We must build ~80% of the type checker ourselves regardless,
   eliminating Typir as a meaningful advantage.

4. **Langium AI is not a factor**: At v0.0.2 with 20 GitHub stars and incompatible with Langium 4.0,
   Langium AI cannot be counted as a differentiating feature. Framework-agnostic tools (Outlines,
   LMQL, Guidance) provide grammar-constrained LLM generation without parser framework coupling.

5. **Strategic risk from Fastbelt**: TypeFox's announcement of Fastbelt (a Go-based successor that
   is 21-33x faster than Langium) signals that Langium has fundamental architectural limitations
   TypeFox themselves acknowledge. The best TypeFox engineers may shift focus to Fastbelt.

6. **antlr-ng is production-ready for TypeScript**: The antlr-ng project is a full TypeScript port
   of ANTLR4, blessed by Terence Parr, with a runtime that is 9-35% faster than other JS/TS ANTLR
   runtimes.

7. **Z3 via native subprocess is faster**: Rather than accepting the 2-5x WASM overhead for all Z3
   operations, we use native Z3 as the primary backend (via subprocess) and fall back to WASM only
   when native binaries are unavailable. This gives us full native performance for verification.

**What we give up (and how we mitigate it):**

| Langium advantage lost     | Mitigation                                                                                                                           |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Integrated LSP server      | Build incrementally using `vscode-languageserver` when IDE support becomes a priority. Our primary users interact via CLI initially. |
| Cross-reference resolution | Build custom name resolution on our own IR. Study Statix scope graphs for design inspiration.                                        |
| Generated TypeScript AST   | Define our own IR types (we were planning this anyway for the abstraction boundary).                                                 |
| VS Code extension scaffold | Generate a basic extension with TextMate grammar from ANTLR; enhance later.                                                          |

### Architecture

```
spec-rest-lang/
  src/
    grammar/
      Spec.g4                    # ANTLR4 grammar definition
      generated/                 # antlr-ng generated parser + lexer
    ast/
      ir-types.ts                # Our own IR type definitions
      cst-to-ir.ts              # Parse tree -> IR conversion
    analysis/
      type-checker.ts            # Custom type checker (built from scratch)
      scope-resolver.ts          # Name resolution and scoping
      validator.ts               # Domain-specific validation rules
    cli/
      generator-openapi.ts       # OpenAPI code generation
      generator-tests.ts         # Test harness generation
      generator-z3.ts            # Z3 constraint generation
    solver/
      z3-native.ts               # Z3 via native subprocess (primary)
      z3-wasm.ts                 # Z3 via WASM (fallback)
  test/                          # Language tests
```

### Secondary Tools

- **Z3 native binary**: Primary solver backend via subprocess for verification
- **z3-solver (npm/WASM)**: Fallback solver for environments without native Z3
- **vscode-languageserver**: For future LSP server implementation
- **tree-sitter grammar** (optional, later): Generate from ANTLR grammar for Neovim/Helix/Zed users
- **Study Statix/scope graphs**: As design inspiration for our type checker's name resolution
  strategy

### Estimated Timeline

| Phase                            | Duration        | Deliverable                                        |
| -------------------------------- | --------------- | -------------------------------------------------- |
| Grammar design + ANTLR parser    | 1-2 weeks       | Parsing spec files, parse tree available           |
| IR design + CST-to-IR conversion | 1-2 weeks       | Typed IR with all DSL constructs                   |
| Name resolution + scoping        | 2-3 weeks       | Cross-references resolve (entity refs, type refs)  |
| Type checker                     | 4-5 weeks       | Full type checking with inference for expressions  |
| Validation rules                 | 1-2 weeks       | Domain-specific error messages                     |
| OpenAPI code generator           | 2-3 weeks       | Generate OpenAPI specs from DSL                    |
| Z3 constraint generator          | 2-3 weeks       | Verify pre/postconditions, invariants              |
| Test harness generator           | 2-3 weeks       | Generate test suites                               |
| LSP server (if needed)           | 3-4 weeks       | Basic IDE support (completion, hover, diagnostics) |
| **Total**                        | **16-27 weeks** | Full compiler with optional IDE support            |

The timeline is modestly longer than the original Langium estimate (14-23 weeks) because we build
name resolution and scoping ourselves. However, this is offset by eliminating framework migration
risk, annual Langium version churn, and the need to work around Langium's assumptions about AST
structure.

### When to Reconsider Langium

Langium remains a viable future option if **all** of the following conditions hold:

1. VS Code IDE support with rich completions, hover, and rename becomes a top-3 user requirement
   (not just nice-to-have).
2. Langium reaches version 5.0+ with a stable API and a clear commitment from TypeFox to continued
   investment (not superseded by Fastbelt).
3. Typir matures to support refinement types and generics, reducing our custom type checker burden.
4. The community grows beyond TypeFox employees to include independent contributors.

---

## Original Recommendation (Superseded)

> **NOTE:** The recommendation below was the original analysis before the devil's advocate audit. It
> is preserved as reference material. The active recommendation is
> [ANTLR4 via antlr-ng](#updated-recommendation) above.

### Original Primary Choice: Langium

**Langium was initially assessed as the clear winner for our project.** The reasoning:

1. **Fastest path to a complete tool**: Langium provides parser, AST, scoping, linking, LSP, VS Code
   extension, CLI, and validation out of the box. We focus engineering effort on our unique
   concerns: the type checker, code generators, and solver integration.

2. **TypeScript ecosystem alignment**: Our compiler, IDE extension, and CLI will all be TypeScript.
   Z3 is available as an npm package (`z3-solver`). LLM APIs are trivially accessible. Everything
   composes naturally.

3. **Langium AI for LLM integration**: TypeFox has built exactly the toolkit we need -- constrained
   decoding that forces LLM output to conform to our DSL grammar, evaluation pipelines that use our
   parser/validator to score LLM output quality, and document splitting that respects syntactic
   boundaries for RAG.

4. **Distribution simplicity**: Ship as an npm package (CLI) + VS Code extension (IDE). Users
   install with `npm install -g specrest` and `code --install-extension specrest`. No JVM, no
   Eclipse, no special IDE.

5. **Type system support via Typir**: While we will need to build a custom type checker for our
   specific needs (entity types, operation signatures, expression types in conditions), Typir
   provides the scaffolding for type inference and assignability that integrates into Langium's
   validation lifecycle.

6. **Performance is adequate**: For specification files (typically <10K lines), Langium's
   performance is excellent. The known performance problems (files >1MB, workspaces with thousands
   of files) do not apply to our use case.

7. **Future-proofed**: TypeFox is working on a Go-based high-performance successor for cases that
   hit Langium's limits. If we ever need it, migration will be supported. But we almost certainly
   will not need it.

### Original Architecture

```
spec-rest-lang/
  src/
    language/
      spec-rest.langium          # Grammar definition
      spec-rest-validator.ts     # Custom validation rules
      spec-rest-type-checker.ts  # Type system (using Typir)
      spec-rest-scope-provider.ts # Custom scoping rules
    cli/
      generator-openapi.ts       # OpenAPI code generation
      generator-tests.ts         # Test harness generation
      generator-z3.ts            # Z3 constraint generation
    lsp/
      spec-rest-completion.ts    # Custom completion providers
      spec-rest-hover.ts         # Custom hover information
    ai/
      langium-ai-config.ts       # LLM grounding configuration
  extension/                     # VS Code extension
  test/                          # Language tests
```

### Original Secondary Tools

- **z3-solver (npm)**: For pre/postcondition verification, invariant checking
- **Langium AI**: For LLM-assisted spec generation and validation
- **tree-sitter grammar** (optional, later): Generate from Langium grammar for Neovim/Helix/Zed
  users
- **Study Statix/scope graphs**: As design inspiration for our type checker's name resolution
  strategy

### Original Estimated Timeline

| Phase                          | Duration        | Deliverable                                       |
| ------------------------------ | --------------- | ------------------------------------------------- |
| Grammar design + basic parser  | 1-2 weeks       | Parsing spec files, AST available                 |
| Scoping + linking              | 1-2 weeks       | Cross-references resolve (entity refs, type refs) |
| Type checker                   | 3-4 weeks       | Full type checking with inference for expressions |
| Validation rules               | 1-2 weeks       | Domain-specific error messages                    |
| LSP polish (completion, hover) | 1-2 weeks       | Rich IDE experience                               |
| OpenAPI code generator         | 2-3 weeks       | Generate OpenAPI specs from DSL                   |
| Z3 constraint generator        | 2-3 weeks       | Verify pre/postconditions, invariants             |
| Test harness generator         | 2-3 weeks       | Generate test suites                              |
| Langium AI integration         | 1-2 weeks       | LLM-assisted spec writing                         |
| **Total**                      | **14-23 weeks** | Full compiler with IDE support                    |

This compared to 25-40+ weeks if we chose ANTLR4 or tree-sitter and built everything from scratch.
However, the post-audit assessment found this comparison overstated the gap: much of Langium's
"free" infrastructure (type checking, custom scoping, validation) still required substantial custom
implementation for our DSL's complexity.

---

## Sources

- [Langium official site](https://langium.org/)
- [Langium GitHub](https://github.com/eclipse-langium/langium)
- [Langium Features](https://langium.org/docs/features/)
- [Langium Grammar Language Reference](https://langium.org/docs/reference/grammar-language/)
- [Langium 4.0 Release](https://www.typefox.io/blog/langium-release-4.0/)
- [Langium AI: Fusion of DSLs and LLMs](https://www.typefox.io/blog/langium-ai-the-fusion-of-dsls-and-llms/)
- [Langium AI GitHub](https://github.com/TypeFox/langium-ai)
- [Typir-Langium (type system library)](https://github.com/TypeFox/typir/blob/main/packages/typir-langium/README.md)
- [Xtext, Langium, What Next? (TypeFox)](https://www.typefox.io/blog/xtext-langium-what-next/)
- [Xtext official site](https://eclipse.dev/Xtext/)
- [Xtext LSP Support](https://eclipse.dev/Xtext/documentation/340_lsp_support.html)
- [Spoofax official site](https://spoofax.dev/)
- [Statix Reference](https://spoofax.dev/references/statix/)
- [Statix Scope Graphs](https://spoofax.dev/references/statix/scope-graphs/)
- [Spoofax Statix Bibliography](https://spoofax.dev/background/bibliography/statix/)
- [OIL industrial case study with Spoofax](https://link.springer.com/article/10.1007/s10270-024-01185-x)
- [JetBrains MPS FAQ](https://www.jetbrains.com/help/mps/mps-faq.html)
- [MPS Standalone IDEs](https://www.jetbrains.com/help/mps/building-standalone-ides-for-your-languages.html)
- [MPS Resolve Git Conflicts](https://www.jetbrains.com/help/mps/resolve-conflicts.html)
- [Rosette official site](https://emina.github.io/rosette/)
- [Rosette GitHub](https://github.com/emina/rosette)
- [Cloudflare Topaz Policy Engine (Rosette in production)](https://blog.cloudflare.com/topaz-policy-engine-design/)
- [Racket Languages](https://racket-lang.org/languages.html)
- [racket-langserver](https://docs.racket-lang.org/racket-langserver/index.html)
- [tree-sitter vs LSP Explainer](https://lambdaland.org/posts/2026-01-21_tree-sitter_vs_lsp/)
- [tree-sitter Syntax Highlighting](https://tree-sitter.github.io/tree-sitter/3-syntax-highlighting.html)
- [ANTLR4 GitHub](https://github.com/antlr/antlr4)
- [z3-solver npm package](https://www.npmjs.com/package/z3-solver)
- [Z3 JavaScript Guide](https://microsoft.github.io/z3guide/programming/Z3%20JavaScript%20Examples/)
- [Langium npm stats](https://snyk.io/advisor/npm-package/langium)
- [Eclipse Langium Project](https://projects.eclipse.org/projects/ecd.langium)
- [Langium 1.0 Announcement](https://www.typefox.io/blog/langium-1.0-a-mature-language-toolkit/)
