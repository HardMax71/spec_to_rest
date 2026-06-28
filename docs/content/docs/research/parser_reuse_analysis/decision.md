---
title: "The decision"
description: "The comparison and why the compiler builds its own parser"
---

## 8. Comparative matrix

### Coverage of our 14 required constructs

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

### Summary scores

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

### The core tension

The analysis reveals a fundamental split:

- Languages strong on behavior (Alloy, Quint, Dafny) have **no HTTP awareness** and weak/no code
  generation to services.
- Languages strong on HTTP/API modeling (TypeSpec, Smithy, Ballerina) have **no behavioral
  specification** capability.

No existing language bridges this gap, which is exactly why our project exists.

## 9. Recommendation

### The verdict: BUILD a NEW PARSER

**Build a new parser from scratch, but reuse existing tools as pipeline components
rather than as parser bases.**

### Why not extend an existing parser

1. No language covers more than 61% of our constructs. The best candidate (Alloy at 61%) still
   requires adding 5-6 entirely new construct types plus modifying 2-3 existing ones.

2. The missing constructs are not additive, they are foundational. Adding HTTP convention
   blocks to Alloy is not like adding a new decorator; it requires rethinking what the language _is
   for_. Similarly, adding behavioral specifications to TypeSpec/Smithy requires building an entire
   expression language from scratch.

3. Fork maintenance is a project killer: Every candidate is under active development. Forking
   creates a permanent maintenance obligation to merge upstream changes, which will inevitably
   conflict with our extensions. Quint, TypeSpec, and Dafny release frequently and make breaking
   changes.

4. Parser technology mismatch: The two most extensible parser technologies (ANTLR for Quint, CUP
   for Alloy) still require deep changes to the AST, type checker, and all downstream phases.
   Extending a grammar file is 5% of the work; the other 95% is extending the semantic analysis
   pipeline.

5. Language ecosystem lock-in: Alloy locks us into JVM + SAT solvers. Dafny locks us into C# +
   Boogie/Z3. Quint locks us into TypeScript + Apalache. Building our own parser lets us choose the
   optimal technology stack.

6. Impedance mismatch on the core differentiator: Our DSL's unique value is combining relational
   entity modeling (from Alloy), pre/postcondition contracts (from Dafny/VDM), state transitions,
   and HTTP conventions in one language. No existing parser handles this combination, and the pieces
   do not compose: Alloy's relational types are incompatible with Dafny's class-based types, which
   are incompatible with TypeSpec's shape-based types.

### The recommended architecture

```text
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
| **ANTLR 4**          | Parser generator for our grammar | Build time, generates TypeScript/Java parser from our `.g4` grammar |
| **Dafny**            | Verification backend             | Our IR -> Dafny translation -> Z3 proof discharge                     |
| **Smithy**           | Service model export             | Our IR -> Smithy model -> SDK generation via smithy-codegen           |
| **TypeSpec/OpenAPI** | API documentation                | Our IR -> TypeSpec/OpenAPI -> docs, client stubs                      |
| **Quint/Apalache**   | Alternative model checking       | Our IR -> Quint translation -> Apalache SMT checking                  |
| **Ballerina**        | Optional compilation target      | Our IR -> Ballerina service code (using native HTTP)                  |

### Why ANTLR 4 for the new parser

1. Our grammar is already designed in EBNF (in `01_spec_language_design.md`). Converting to
   ANTLR `.g4` format is mechanical.

2. ANTLR 4 generates TypeScript, Java, Python, C#, Go, and more. This avoids locking into any
   single ecosystem.

3. Quint uses ANTLR 4 for exactly the same reasons, it is the standard tool for DSL parsers
   in 2026.

4. ANTLR 4's ALL(\*) parsing algorithm handles the grammar complexity we need (quantified
   expressions, operator precedence, primed variables) without the ambiguity issues of LALR parsers
   like CUP.

5. Excellent tooling: ANTLR 4 has VSCode extensions, grammar visualization, test rigs, and a
   large community.

### Effort comparison

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

### What to borrow (without forking)

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

### Bottom line

The research confirms the finding from the prior survey: **"No existing language provides all of
[our required constructs]."** The parser reuse question has a clear answer: building a purpose-built
ANTLR 4 parser is faster, lower risk, and more maintainable than extending any existing parser,
while reusing Dafny (verification), Smithy (SDK generation), and TypeSpec/OpenAPI
(documentation) as pipeline components achieves the benefits of each ecosystem without the costs of
parser-level coupling.

### Note on the Langium evaluation

After this analysis recommended ANTLR 4, a separate evaluation (`09_dsl_compiler_frameworks.md`)
explored whether **Langium**, a TypeScript-based language workbench by TypeFox, could replace
ANTLR by providing an integrated parser + LSP + AST generation stack. Langium was initially
recommended as the primary framework.

A subsequent devil's advocate audit reversed that recommendation and confirmed ANTLR 4 (specifically
**antlr-ng**, the production-ready TypeScript port) as the parser technology. The key reasons:

1. Langium's advantages were overstated for our use case. Its companion type system library
   (Typir) cannot handle refinement types, relation types, generics, or quantified expressions,
   all of which our DSL requires. We must build ~80% of the type checker ourselves regardless of
   framework choice, eliminating Langium's biggest productivity argument.

2. ANTLR's community is orders of magnitude larger. ANTLR has 17k+ GitHub stars and thousands of
   contributors vs. Langium's 985 stars and 22 contributors (mostly TypeFox employees). For a
   multi-year project, community depth matters for bug fixes, documentation, and long-term support.

3. No framework lock-in: Langium couples grammar, AST types, scoping, and LSP into a proprietary
   framework. ANTLR4 generates a parser; everything else is built on our own abstractions, making
   each component independently replaceable.

4. Strategic risk: TypeFox's announcement of Fastbelt (a Go-based successor 21-33x faster than
   Langium) signals that Langium may enter de facto maintenance mode as TypeFox's best engineers
   shift focus.

The implementation uses **antlr-ng** targeting **TypeScript**, with Z3 via native subprocess for
performance-critical verification and WASM as a deployment fallback. Langium remains a viable future
option if VS Code IDE support with rich completions becomes a top-priority requirement and the
framework matures further.
