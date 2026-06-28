---
title: "The decision"
description: "The assessment matrix, ANTLR4, and the Langium reversal"
---

## Comparative assessment matrix

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

## Updated recommendation

### Primary choice: ANTLR4 via antlr-ng (typescript)

> This section supersedes the original Langium recommendation below, which is preserved for
> reference. The change was driven by a devil's advocate audit that stress-tested every Langium
> advantage against primary sources.

**ANTLR4 via antlr-ng is the chosen parser framework.** The implementation language is TypeScript.
We build our own type checker. Z3 integration uses native subprocess for performance-critical
checks, with WASM (`z3-solver` npm) as fallback.

#### Why ANTLR4/antlr-ng over Langium

1. **Community and ecosystem stability**: ANTLR has 17k+ GitHub stars, thousands of contributors,
   extensive documentation and books, and a 20+ year track record. Langium has 985 stars and 22
   contributors, mostly TypeFox employees. If we hit a parser bug in ANTLR, we have a massive
   community to draw on.

2. **No framework lock-in**: Langium couples grammar, AST, scoping, LSP, and validation into a
   single proprietary framework. Migration away from Langium requires rewriting all of these. With
   ANTLR4, each concern is a separate, replaceable component.

3. **Typir cannot carry our type system**: The audit confirmed that Typir (Langium's companion type
   system library) lacks refinement types, relation types, generics, and quantified expressions,
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

#### What we give up (and how we mitigate it)

| Langium advantage lost     | Mitigation                                                                                                                           |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Integrated LSP server      | Build incrementally using `vscode-languageserver` when IDE support becomes a priority. Our primary users interact via CLI initially. |
| Cross-reference resolution | Build custom name resolution on our own IR. Study Statix scope graphs for design inspiration.                                        |
| Generated TypeScript AST   | Define our own IR types (we were planning this anyway for the abstraction boundary).                                                 |
| VS Code extension scaffold | Generate a basic extension with TextMate grammar from ANTLR; enhance later.                                                          |

### Architecture

```text
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

### Secondary tools

- Z3 native binary: Primary solver backend via subprocess for verification
- z3-solver (npm/WASM): Fallback solver for environments without native Z3
- vscode-languageserver: For future LSP server implementation
- tree-sitter grammar (optional, later): Generate from ANTLR grammar for Neovim/Helix/Zed users
- Study Statix/scope graphs: As design inspiration for our type checker's name resolution
  strategy

### Estimated timeline

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

### When to reconsider Langium

Langium remains a viable future option if **all** of the following conditions hold:

1. VS Code IDE support with rich completions, hover, and rename becomes a top-3 user requirement
   (not just nice-to-have).
2. Langium reaches version 5.0+ with a stable API and a clear commitment from TypeFox to continued
   investment (not superseded by Fastbelt).
3. Typir matures to support refinement types and generics, reducing our custom type checker burden.
4. The community grows beyond TypeFox employees to include independent contributors.

## Original recommendation (superseded)

> **NOTE:** The recommendation below was the original analysis before the devil's advocate audit. It
> is preserved as reference material. The active recommendation is
> [ANTLR4 via antlr-ng](#updated-recommendation) above.

### Original primary choice: Langium

**Langium was initially assessed as the clear winner for our project.** The reasoning:

1. **Fastest path to a complete tool**: Langium provides parser, AST, scoping, linking, LSP, VS Code
   extension, CLI, and validation out of the box. We focus engineering effort on our unique
   concerns: the type checker, code generators, and solver integration.

2. **TypeScript ecosystem alignment**: Our compiler, IDE extension, and CLI will all be TypeScript.
   Z3 is available as an npm package (`z3-solver`). LLM APIs are trivially accessible. Everything
   composes naturally.

3. **Langium AI for LLM integration**: TypeFox has built exactly the toolkit we need, constrained
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

### Original architecture

```text
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

### Original secondary tools

- z3-solver (npm): For pre/postcondition verification, invariant checking
- Langium AI: For LLM-assisted spec generation and validation
- tree-sitter grammar (optional, later): Generate from Langium grammar for Neovim/Helix/Zed
  users
- Study Statix/scope graphs: As design inspiration for our type checker's name resolution
  strategy

### Original estimated timeline

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
