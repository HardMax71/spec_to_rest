---
title: "Parsers and language extension"
description: "Racket with Rosette, tree-sitter, and ANTLR4"
---

## 5. Racket #lang + rosette

**What it is**: Racket is a language-oriented programming environment where creating new languages
(#lang) is a first-class capability. Rosette extends Racket with solver-aided features backed by Z3.

**GitHub**: Rosette, 688 stars, 81 forks

### Building a DSL as a #lang

In Racket, a "language" is defined by providing a reader (parser) and a module expander. You can
create `#lang spec-rest` and have it be a fully custom syntax. What you get:

- DrRacket IDE support: Syntax coloring, REPL, debugging, documentation
- Macro system: The most powerful macro system in any language, can implement arbitrary syntax
  transformations
- Module system: First-class language composition
- Test framework: Built-in

### The rosette/Z3 opportunity

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

### Practical concerns

| Factor              | Assessment                                                               |
| ------------------- | ------------------------------------------------------------------------ |
| **IDE support**     | DrRacket (specialized IDE) + limited VS Code via racket-langserver       |
| **LSP**             | Basic, racket-langserver exists but is not feature-rich                |
| **Distribution**    | Requires Racket installation (~200MB)                                    |
| **Learning curve**  | Steep, Racket, macros, Rosette, S-expressions                          |
| **User syntax**     | S-expression based unless you build a custom reader (significant effort) |
| **Community**       | Active but academic-leaning (~5K GitHub stars for Racket)                |
| **Error messages**  | Good within Racket; custom readers need custom error handling            |
| **Code generation** | Manual, Racket has string templating but nothing like Langium's infra  |
| **LLM integration** | No ecosystem support; LLMs struggle with S-expressions                   |
| **Performance**     | Rosette/Z3 verification can be slow for complex specs                    |

### Verdict on racket/rosette

**The wrong primary framework but the right verification backend.** Building our entire compiler in
Racket would mean: (a) forcing an S-expression syntax or building a custom reader from scratch, (b)
losing LSP/VS Code integration quality, (c) requiring all users to install Racket, (d) making LLM
integration much harder. However, Rosette's solver-aided capabilities are directly relevant to our
verification needs. The optimal approach: **build the compiler in Langium, generate Z3 constraints
in TypeScript using the z3-solver npm package**, and consider Rosette only if we need synthesis or
symbolic execution capabilities that are hard to replicate directly.

## 6. Tree-sitter + custom tooling

**What it is**: tree-sitter is an incremental parsing library used by many editors (VS Code, Neovim,
Helix, Zed, GitHub). You define a grammar in JavaScript, and it generates a C parser.

**GitHub**: 24,500 stars, 2,544 forks (by far the most popular tool in this comparison)

### What tree-sitter gives you

- Incremental parsing: Re-parses only changed regions; sub-millisecond updates
- Error recovery: Always produces a valid tree, even with syntax errors
- Syntax highlighting: Query-based highlighting that editors consume directly
- Code folding: Structure-based folding
- Indentation: Can derive indentation rules
- Basic structural navigation: Parent/child/sibling traversal

### What tree-sitter does NOT give you

- No AST types: The parse tree is a generic CST; you must define and build your own typed AST
- No cross-reference resolution: No name binding, no scoping
- No type checking: Zero semantic analysis
- No LSP server: tree-sitter is NOT a language server, it provides parsing only
- No validation framework: You must build all diagnostics from scratch
- No code generation infrastructure: Nothing
- No code completion: Beyond syntax-driven suggestions

### How much work is "everything else"?

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

**Not appropriate as the primary framework.** tree-sitter is a parser, rather than a language workbench. For
our DSL, we would spend months building infrastructure that Langium provides out of the box.
However, tree-sitter could be useful as a **secondary artifact**: we could generate a tree-sitter
grammar from our Langium grammar to provide syntax highlighting in editors that use tree-sitter
natively (Neovim, Helix, Zed).

## 7. ANTLR4 + custom tooling

**What it is**: The most widely-used parser generator in the world. Generates parsers in Java, C#,
Python, JavaScript, TypeScript, Go, C++, and more from a single grammar.

**GitHub**: 18,809 stars, 3,430 forks

### What ANTLR4 provides

- Parser + lexer generation from a `.g4` grammar file
- Visitor and listener patterns for AST traversal
- Multiple target languages (11+)
- Excellent error reporting with error recovery strategies
- Large grammar repository (grammars-v4 on GitHub)
- Mature, battle-tested (20+ years of development)
- ALL(\*) parsing algorithm (handles most grammars without ambiguity issues)

### ANTLR4 vs Langium: The key difference

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

### When ANTLR4 still makes sense

- You need the parser in a non-TypeScript language (Java, C#, Python)
- You already have an ANTLR grammar and want to reuse it
- You want maximum control over every component
- You need to generate parsers for multiple target platforms

### Effort comparison

For our DSL with full IDE support:

- ANTLR4 + custom everything: ~20-28 weeks
- Langium: ~8-12 weeks (grammar + custom type checker + code generators)

### Verdict on ANTLR4

**Choose Langium instead.** ANTLR4 is an excellent tool, but Langium wraps an equally capable parser
with all the additional infrastructure we need. The only scenario where ANTLR4 would be preferable
is if we needed multi-language parser targets, but our compiler will be TypeScript.
