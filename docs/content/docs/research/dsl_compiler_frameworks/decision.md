---
title: "The decision"
description: "The assessment matrix, ANTLR4, and the Langium reversal"
---

## The matrix

The seven candidates, side by side on the criteria that decide a framework choice:

| Criterion                | Langium                       | Xtext                           | Spoofax                       | MPS                          | Racket/Rosette                | tree-sitter            | ANTLR4                 |
| ------------------------ | ----------------------------- | ------------------------------- | ----------------------------- | ---------------------------- | ----------------------------- | ---------------------- | ---------------------- |
| Time to working parser   | 1-2 days                      | 2-3 days                        | 3-5 days                      | 1-2 weeks                    | 1-2 weeks                     | 1-2 weeks              | 3-5 days               |
| Time to full IDE support | 2-3 weeks                     | 3-4 weeks                       | 4-6 weeks                     | built-in (MPS-only)          | months                        | months                 | months                 |
| Error message quality    | good (customizable)           | good                            | excellent (constraint-based)  | n/a (no parse errors)        | variable                      | basic                  | good                   |
| IDE support out of box   | excellent (VS Code)           | good (Eclipse native)           | good (Eclipse only)           | excellent (MPS only)         | fair (DrRacket)               | syntax only            | none                   |
| Z3 / solver integration  | good (z3-solver npm)          | possible (JNI)                  | difficult                     | possible (Java)              | excellent (native)            | manual                 | depends on target      |
| LLM integration          | Langium AI                    | none                            | none                          | poor                         | none                          | none                   | none                   |
| Distribution             | npm / VS Code ext             | JVM jar / Eclipse plugin        | Eclipse plugin                | standalone IDE (~500MB)      | Racket install                | native binary          | depends on target      |
| Learning curve           | moderate (TypeScript)         | moderate-high (Java/EMF)        | steep (3 meta-languages)      | steep (projectional)         | steep (Racket/macros)         | low (JS grammar)       | low-moderate           |
| Community size           | 985 stars                     | 823 stars                       | 163 stars                     | 1.6K stars                   | 688 stars                     | 24.5K stars            | 18.8K stars            |
| Maintenance status       | active (v4.0, 2025)           | maintenance mode                | active but academic           | active (JetBrains)           | active but slow               | very active            | active                 |

## Why ANTLR4 won

The survey first recommended Langium, the integrated workbench, and a devil's advocate audit reversed
it to ANTLR4 after stress-testing each Langium advantage against primary sources. The reasons that
held up:

- Community and stability: ANTLR has on the order of 18,000 stars, thousands of contributors, books,
  and a twenty-year track record; Langium has under a thousand stars and roughly two dozen
  contributors, mostly TypeFox staff. A parser bug has a very different support story behind each.
- No framework lock-in: Langium couples grammar, AST, scoping, LSP, and validation into one
  framework, so leaving it means rewriting all of them; with ANTLR4 each concern is a separate,
  replaceable component.
- The type system was the decider: Langium's companion type library, Typir, cannot express refinement
  types, relation types, generics, or quantified expressions, all of which this DSL needs. About 80%
  of the type checker has to be hand-built either way, which erased Langium's main productivity
  argument.
- The softer advantages did not survive: Langium AI was at v0.0.2, twenty stars, and incompatible
  with Langium 4.0, and framework-agnostic tools (Outlines, LMQL, Guidance) give grammar-constrained
  LLM decoding without coupling to a parser; meanwhile TypeFox's own announcement of Fastbelt, a Go
  successor many times faster than Langium, signaled architectural limits its makers acknowledge.

What shipped is ANTLR4 inside the Scala 3 compiler: the grammar is compiled through `sbt-antlr4` to a
JVM parser, not the antlr-ng TypeScript port the audit assumed, and Z3 is reached through
`z3-turnkey`'s bundled native bindings rather than a subprocess-and-WASM arrangement. The cost of
passing on the workbench is real but bounded: there is no integrated LSP (editor tooling is deferred,
the tool is CLI-first), name resolution is built on the compiler's own IR, the IR types are defined
directly (the plan regardless), and a TextMate grammar covers syntax highlighting.

## Why Langium was the first pick

The original case for Langium was the fastest path to a complete tool: parser, AST, scoping, linking,
LSP, a VS Code extension, a CLI, and validation out of the box, leaving the team to focus on the type
checker, the generators, and the solver, all in one TypeScript stack with Z3 as an npm package and
Langium AI for LLM grounding, shipped as an npm package plus an extension with no JVM or Eclipse. The
audit's finding was that the effort gap this implied was overstated: most of the free infrastructure,
the type checker, custom scoping, and validation, still needed substantial custom work for a DSL of
this complexity, so the saving never materialized at the size claimed.

## When to reconsider Langium

It stays a viable future option, but only if a few conditions hold at once: rich VS Code IDE support
(completion, hover, rename) becomes a top-tier requirement rather than a nice-to-have; Langium reaches
a stable 5.0-plus with a clear TypeFox commitment not superseded by Fastbelt; Typir matures to cover
refinement types and generics, shrinking the custom type-checker burden; and the contributor base
grows beyond TypeFox employees.
