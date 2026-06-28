---
title: "Parsers and language extension"
description: "Racket with Rosette, tree-sitter, and ANTLR4"
---

## Racket and Rosette

[Racket](https://racket-lang.org) is a language-oriented environment where defining a new language (a
`#lang`) is first-class: supply a reader and a module expander and you get fully custom syntax, with
DrRacket's IDE (syntax coloring, REPL, debugging, docs), the most powerful macro system of any
language, first-class language composition, and a built-in test framework.
[Rosette](https://emina.github.io/rosette/) extends it with solver-aided features backed by Z3 (688
stars).

That solver angle is the interesting part. Write an interpreter for the spec language in Rosette and
verification ("does this precondition ever fail?"), synthesis ("generate an implementation satisfying
the postcondition"), and counterexample-finding come for free, with Z3 deeply integrated. Cloudflare
runs Rosette in production for DNS-policy verification (topaz-lang, a Lisp-like DSL): it checks
satisfiability, reachability, and exclusivity of policies, seven programs in about six seconds and
fifty in about three hundred, at the cost of keeping a Racket interpreter in sync with the production
Go one, and with string manipulation beyond equality unsupported in verification mode.

| Factor          | Assessment                                                               |
| --------------- | ------------------------------------------------------------------------ |
| IDE support     | DrRacket (specialized IDE) plus limited VS Code via racket-langserver    |
| LSP             | basic; racket-langserver exists but is not feature-rich                  |
| Distribution    | requires a Racket install (about 200MB)                                  |
| Learning curve  | steep: Racket, macros, Rosette, S-expressions                            |
| User syntax     | S-expression based unless you build a custom reader (significant effort) |
| Community       | active but academic-leaning (about 5K stars for Racket)                  |
| Error messages  | good within Racket; custom readers need custom error handling            |
| Code generation | manual; Racket has string templating but nothing like Langium's infra    |
| LLM integration | no ecosystem support; LLMs struggle with S-expressions                   |
| Performance     | Rosette and Z3 verification can be slow for complex specs                |

As the primary framework it is the wrong fit: S-expression syntax unless you write a custom reader,
weaker LSP and VS Code support, a Racket install for every user, and LLMs that stumble on
S-expressions. The solver-aided idea is real prior art, but this project's verification went to Z3
directly and its synthesis to Dafny, not Rosette.

## tree-sitter

[tree-sitter](https://tree-sitter.github.io/tree-sitter/) is the incremental parsing library editors
lean on (VS Code, Neovim, Helix, Zed, GitHub), by far the most popular tool here at about 24,500
stars: you write a grammar in JavaScript and it generates a C parser. It gives you incremental parsing
(sub-millisecond re-parses of changed regions), error recovery (always a valid tree), query-based
syntax highlighting editors consume directly, structure-based folding and indentation, and basic
parent, child, and sibling navigation. What it does not give you is anything past the parse tree: no
typed AST (the tree is a generic CST), no name resolution or scoping, no type checking, no LSP server
(it parses, nothing more), no validation framework, no code generation, and no completion beyond
syntax-driven suggestions. For a DSL of this complexity that adds up:

| Component                                                      | tree-sitter provides | Effort to build             |
| -------------------------------------------------------------- | -------------------- | --------------------------- |
| Grammar and parser                                             | yes                  | 1-2 weeks                   |
| Typed AST from the CST                                         | no                   | 2-3 weeks                   |
| Name resolution and scoping                                    | no                   | 3-4 weeks                   |
| Type checker                                                   | no                   | 4-6 weeks                   |
| Validation and error reporting                                 | no                   | 2-3 weeks                   |
| LSP server (completion, hover, go-to-def, diagnostics, rename) | no                   | 6-8 weeks                   |
| Code generation                                                | no                   | (same regardless of parser) |
| Total additional effort beyond the parser                      |                      | 17-24 weeks                 |

So tree-sitter is a parser, not a language workbench, and the table is what you would build around it.
It still has a place as a secondary artifact: a tree-sitter grammar generated from the real one gives
syntax highlighting in the editors that use it natively (Neovim, Helix, Zed).

## ANTLR4

[ANTLR4](https://www.antlr.org/) is the most widely used parser generator there is (about 18,800
stars, more than twenty years old), turning a single `.g4` grammar into a lexer and parser in Java,
C#, Python, JavaScript, TypeScript, Go, C++, and more, with visitor and listener traversal, strong
error recovery, a large public grammar repository, and the `ALL(*)` algorithm that handles most
grammars without ambiguity trouble. Set against Langium:

| Component                  | ANTLR4                  | Langium                               |
| -------------------------- | ----------------------- | ------------------------------------- |
| Parser                     | yes                     | yes (Chevrotain, equally capable)     |
| Typed AST                  | no (generic parse tree) | yes (generated TypeScript interfaces) |
| Cross-reference resolution | no                      | yes                                   |
| Scoping                    | no                      | yes (customizable)                    |
| LSP server                 | no                      | yes (comprehensive)                   |
| VS Code extension          | no                      | yes (scaffolded)                      |
| Validation framework       | no                      | yes                                   |
| Code generation helpers    | no                      | yes (with source maps)                |
| Type system library        | no                      | Typir (companion)                     |
| LLM integration toolkit    | no                      | Langium AI                            |

The difference is philosophical: ANTLR4 gives you a parser, Langium gives you a parser plus a typed
AST, scoping, linking, an LSP server, a VS Code extension, a validation framework, and
code-generation helpers (Langium runs Chevrotain internally behind an ANTLR-like grammar language).
ANTLR4 is the better pick when you need the parser in a non-TypeScript language, want maximum control
over each component, or must target several platforms.

This is the option the project chose, and those last reasons are why: the parser is generated for the
JVM and driven from Scala, exactly the multi-language-target, no-framework-lock-in case ANTLR4 fits.
The survey first scored it the other way, around twenty to twenty-eight weeks for
ANTLR4-plus-custom-everything against eight to twelve for Langium, before an audit found Langium's
productivity edge overstated. That reversal is the
[decision](/research/dsl_compiler_frameworks/decision).
