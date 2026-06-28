---
title: "The mismatch"
description: "What MLIR offers against what a DSL compiler actually needs"
---

## 5. Non-ML uses of MLIR

MLIR is genuinely used far beyond machine learning. Notable examples:

### 5.1 CIRCT, hardware design

The CIRCT project (Circuit IR Compilers and Tools) applies MLIR to hardware design, replacing
traditional RTL tools with MLIR-based compilation. Modern hardware DSLs like Chisel are moving their
backends to MLIR. This is a major non-ML success story, demonstrating MLIR's generality for
representing hardware description languages.

### 5.2 Flang, fortran compiler

LLVM's new Fortran compiler (Flang) uses MLIR for its high-level IR (FIR, Fortran IR). This
enables powerful transformations for array operations, loop optimizations, and OpenMP parallelism.
Flang already achieves performance on par with GCC's Fortran compiler. In 2024, AMD announced its
next-gen Fortran compiler will be based on Flang/MLIR.

### 5.3 Other non-ML uses

From the official MLIR users page:

| Project             | Domain                                         |
| ------------------- | ---------------------------------------------- |
| **CIRCT**           | Hardware design (EDA)                          |
| **Flang**           | Fortran HPC compiler                           |
| **ClangIR**         | C/C++ intermediate representation              |
| **Concrete / HEIR** | Fully homomorphic encryption                   |
| **P4HIR**           | Network packet processor programming           |
| **JSIR**            | JavaScript analysis / malicious code detection |
| **MARCO**           | Modelica language compiler                     |
| **Substrait MLIR**  | Database query plan representation             |
| **DSP-MLIR**        | Digital signal processing                      |
| **Mojo**            | Python-compatible systems language             |
| **Firefly**         | Erlang/Elixir to WebAssembly compiler          |
| **Pylir**           | Python ahead-of-time compiler                  |
| **Verona**          | Concurrent ownership research language         |
| **Zaozi**           | Hardware eDSL in Scala 3                       |

### 5.4 Pattern

Every successful non-ML use of MLIR shares a common trait: **the domain involves computational
operations that benefit from optimization, lowering, and eventually code generation to machine
instructions.** Hardware synthesis, HPC loop nests, cryptographic circuits, database query plans,
these all have optimization-rich compilation pipelines where multi-level IR is genuinely valuable.

## 6. What MLIR gives that simpler approaches don't

### What MLIR provides

1. Multi-level IR coexistence: different abstraction levels in one representation, with
   well-defined lowering between them
2. Verification infrastructure: built-in operation verification, type checking, and trait-based
   constraints
3. Pass infrastructure: pass management, scheduling, and dependency tracking for
   IR transformations
4. Rewrite pattern system: declarative (DRR/PDLL) and programmatic pattern matching for IR
   transformations
5. SSA form: automatic SSA construction and dominance analysis
6. Serialization: textual and bytecode IR formats with round-tripping
7. Ecosystem: access to LLVM backend for native code generation
8. Community: active development, conferences, weekly public meetings

### What our compiler actually needs

| Compiler stage         | What we need                                    | MLIR helps?             |
| ---------------------- | ----------------------------------------------- | ----------------------- |
| **Parsing**            | Lexer + parser for spec DSL                     | No                      |
| **AST construction**   | Typed AST from parse tree                       | No                      |
| **Semantic analysis**  | Type checking, scope resolution                 | Partially (type system) |
| **IR construction**    | Service-specific IR (entities, ops, invariants) | Partially (generic IR)  |
| **Constraint solving** | Z3 for invariant checking                       | No                      |
| **Convention mapping** | Entities -> HTTP routes, DB schemas             | No                      |
| **LLM integration**    | Synthesis of operation bodies                   | No                      |
| **Code generation**    | Template-based multi-target emission            | No (overkill)           |
| **Test generation**    | Property-based test synthesis                   | No                      |

MLIR would only partially help with two stages (semantic analysis and IR construction), and in both
cases our domain-specific needs (relational constraints, pre/postconditions, REST-specific concepts
like HTTP methods, status codes, pagination) don't map naturally to MLIR's computation-oriented IR
model.

### The core mismatch

MLIR is designed for **computational IRs**, representations of programs that compute values
through sequences of operations with data dependencies. Its SSA form, dominance trees, and region
structure are designed for analyzing and optimizing computation.

Our spec language is **declarative and structural**, it describes entities, their relationships,
behavioral contracts (pre/postconditions), and invariants. There is no "computation" to optimize.
The IR is a structured data model that drives template-based code generation, rather than a computational
graph that gets progressively lowered to machine instructions.

Concretely, our IR looks like this:

```python
@dataclass
class ServiceIR:
    entities: List[EntityDecl]        # Sig-like declarations
    operations: List[OperationDecl]   # With requires/ensures
    invariants: List[InvariantDecl]   # Global constraints
    state: StateDecl                  # Mutable state definition
```

This is a data structure. MLIR's infrastructure for operation scheduling, memory analysis, loop
transformations, vectorization, and LLVM lowering is irrelevant here.

## 7. REST/web service dsls on MLIR

**None found.** After extensive searching, there are zero examples of REST API, web service, or
HTTP-related DSLs built on MLIR. This is not an oversight, it reflects the core mismatch
described in Section 6.

The closest adjacent projects:

- Substrait MLIR represents database query plans (data processing, rather than web services)
- JSIR analyzes JavaScript code (code analysis, rather than service specification)
- P4HIR handles network packet processing (low-level networking, not HTTP APIs)

Existing REST/API specification tools (OpenAPI, TypeSpec, Smithy, Ballerina) all use their own
purpose-built parsers and IRs. None use MLIR or any general-purpose compiler IR framework, because
API specifications are structural/declarative, rather than computational.

## 8. Learning curve

The learning curve for MLIR is widely acknowledged as steep.

### Community assessment

- Stephen Diehl's introduction to MLIR opens with "You probably shouldn't" learn MLIR, acknowledging
  it serves a niche audience
- The MLIR ecosystem "has a steep learning curve, which can intimidate new developers and hinders
  adoption"
- "Building a new dialect or pass often means delving into MLIR's internals (C++ templates, TableGen
  definitions, etc.) with sparse documentation"
- Google's engineers writing ML kernels in MLIR found it "a productivity challenge," leading to the
  creation of the Mojo language for a higher-level syntax

### Specific pain points

1. TableGen is its own language. You must learn ODS, which is a DSL embedded in TableGen,
   itself a record-based DSL. So you are learning a DSL-within-a-DSL to define your DSL.
2. MLIR's C++ layer uses heavy template metaprogramming
3. CMake + TableGen code generation adds complexity
4. Many intermediate topics lack documentation; you often read source
   code
5. MLIR APIs evolve rapidly; code from tutorials may not compile against
   current HEAD

### Estimated timeline

For a developer experienced in compilers but new to MLIR:

- 1-2 weeks: complete Toy tutorial, understand basic concepts
- 2-4 weeks: build a trivial custom dialect with a few operations
- 1-3 months: build a useful dialect with custom types, lowering passes, and transformations
- 3-6 months: become productive at debugging MLIR issues and extending the dialect

For a developer NOT experienced in compilers or C++:

- Add 2-4 months to each estimate above
