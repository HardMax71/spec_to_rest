---
title: "MLIR Evaluation"
description: "Assessment of LLVM MLIR for the spec-to-REST DSL compiler"
---

> Research assessment of LLVM's MLIR (Multi-Level Intermediate Representation) as infrastructure for
> building the spec-to-REST compiler. Covers architecture, dialect creation, parsing story,
> boilerplate costs, learning curve, non-ML uses, alternatives, and a final recommendation.

---

## Table of Contents

1. [What MLIR Actually Is](#1-what-mlir-actually-is)
2. [Does MLIR Help with Parsing?](#2-does-mlir-help-with-parsing)
3. [Custom Dialect Creation: What It Takes](#3-custom-dialect-creation-what-it-takes)
4. [The C++ Requirement](#4-the-c-requirement)
5. [Non-ML Uses of MLIR](#5-non-ml-uses-of-mlir)
6. [What MLIR Gives That Simpler Approaches Don't](#6-what-mlir-gives-that-simpler-approaches-dont)
7. [REST/Web Service DSLs on MLIR](#7-restweb-service-dsls-on-mlir)
8. [Learning Curve](#8-learning-curve)
9. [xDSL: The Python Alternative](#9-xdsl-the-python-alternative)
10. [Fit Assessment for Spec-to-REST](#10-fit-assessment-for-spec-to-rest)
11. [Recommendation](#11-recommendation)
12. [Sources](#12-sources)

---

## 1. What MLIR Actually Is

MLIR is a **compiler infrastructure framework** -- not a compiler, not a language, not a parser. It
provides:

- **A generic SSA-based IR format** with operations, regions, blocks, and values
- **An extensible dialect system** where you define your own operations, types, and attributes as
  first-class IR constructs
- **A transformation/pass infrastructure** for writing optimization and lowering passes
- **A progressive lowering model** where high-level domain operations are incrementally lowered
  through intermediate dialects down to machine code (via LLVM IR)
- **Built-in dialects** for common patterns: `arith`, `func`, `scf` (structured control flow),
  `affine` (loop nests), `memref` (memory), `llvm` (LLVM IR)

The key architectural insight: MLIR lets multiple levels of abstraction coexist in a single IR. A
program can contain `toy.transpose` next to `affine.for` next to `llvm.call` -- each from a
different dialect, at a different abstraction level.

Despite the name, the "ML" in MLIR does **not** stand for Machine Learning. Chris Lattner (MLIR's
creator) and the LLVM community have explicitly stated it was conceived as general-purpose compiler
infrastructure. The official MLIR website describes it as "a novel approach to building reusable and
extensible compiler infrastructure" with no ML-specific limitation.

---

## 2. Does MLIR Help with Parsing?

**No. MLIR provides zero parsing infrastructure for custom DSLs.**

This is the single most important finding for our use case. MLIR's role begins _after_ parsing is
complete:

```
Source text --> [Parser/Lexer] --> AST --> [AST-to-MLIR] --> MLIR IR --> [Passes] --> Output
                ^^^^^^^^^^^^^^^^^^^^^^^^^
                You build this yourself.
                MLIR does not help here.
```

Evidence from the official Toy language tutorial (the canonical MLIR learning path):

> "The code for the lexer is fairly straightforward; it is all in a single header:
> `examples/toy/Ch1/include/toy/Lexer.h`. The parser can be found in
> `examples/toy/Ch1/include/toy/Parser.h`."

The Toy tutorial uses a **hand-written recursive descent parser** in C++. MLIR provides no lexer
generator, no parser generator, no grammar specification mechanism, and no AST construction
utilities. The MLIR Toy tutorial Chapter 1 is entirely about the hand-written parser; Chapter 2 is
titled "Emitting Basic MLIR" -- this is where MLIR begins.

### ANTLR + MLIR Bridge

There is an experimental community project that generates MLIR dialects from ANTLR4 grammars
(discussed on LLVM Discourse, author: leothaud). It automatically:

- Transforms an ANTLR4 grammar into an MLIR dialect representing the AST
- Generates an ANTLR4-based frontend that targets this MLIR dialect

However, this project supports only a subset of ANTLR4 features and is experimental. The community
reception noted it could be useful but suggested IRDL (Intermediate Representation Definition
Language) for broader portability.

### Implication for Spec-to-REST

Our spec language needs a parser regardless. Whether we use MLIR or not, we still need to build a
lexer, parser, and AST -- using ANTLR, tree-sitter, pest, hand-written recursive descent, or
similar. MLIR does not reduce this work at all.

---

## 3. Custom Dialect Creation: What It Takes

Creating an MLIR dialect requires a substantial amount of scaffolding across multiple files, build
systems, and languages.

### 3.1 Files Required for a Minimal Dialect

```
mlir/include/mlir/Dialect/Foo/
    FooDialect.td          # Dialect declaration (TableGen)
    FooOps.td              # Operation definitions (TableGen)
    FooTypes.td            # Custom type definitions (TableGen, optional)
    FooDialect.h           # C++ header (partly generated)
    FooOps.h               # C++ header (partly generated)

mlir/lib/Dialect/Foo/IR/
    FooDialect.cpp         # C++ implementation
    FooOps.cpp             # Operation implementations
    CMakeLists.txt         # Build configuration

mlir/lib/Dialect/Foo/Transforms/
    FooTransforms.td       # Rewrite rules (TableGen, optional)
    CMakeLists.txt         # Build configuration
```

### 3.2 TableGen (ODS) Definitions

The dialect is declared in TableGen's ODS (Operation Definition Specification) format:

```text
// FooDialect.td
def Foo_Dialect : Dialect {
  let name = "foo";
  let cppNamespace = "foo";
}
```

Operations are defined declaratively:

```text
// FooOps.td
def ConstantOp : Foo_Op<"constant"> {
  let summary = "constant operation";
  let arguments = (ins F64ElementsAttr:$value);
  let results = (outs F64Tensor);
  let hasVerifier = 1;
  let builders = [
    OpBuilder<(ins "DenseElementsAttr":$value)>
  ];
  let assemblyFormat = "$value attr-dict `:` type($input)";
}
```

Custom types require additional definitions:

```text
// FooTypes.td -- a parameterized type
def Foo_PolyType : TypeDef<Foo_Dialect, "Polynomial"> {
  let parameters = (ins "int":$degreeBound);
  let assemblyFormat = "`<` $degreeBound `>`";
}
```

### 3.3 CMake Configuration

```cmake
add_mlir_dialect(FooOps foo)
add_mlir_doc(FooOps FooDialect Dialects/ -gen-dialect-doc)

add_mlir_dialect_library(MLIRFoo
  FooDialect.cpp
  FooOps.cpp
  DEPENDS
  MLIRFooOpsIncGen
  LINK_LIBS PUBLIC
  MLIRIR
  MLIRSupport
)
```

### 3.4 C++ Implementation

Even with TableGen generating most boilerplate, you still write C++ for:

- Dialect initialization and registration
- Custom verification logic (if `hasVerifier = 1`)
- Custom builders beyond what TableGen generates
- Lowering passes (the `ConversionPattern` implementations)
- Custom type/attribute storage classes

### 3.5 Effort Estimate

Based on multiple tutorials and real-world examples:

| Component                         | Estimated lines | Language     |
| --------------------------------- | --------------- | ------------ |
| TableGen dialect + ops (5-10 ops) | 100-300         | ODS/TableGen |
| C++ dialect implementation        | 200-500         | C++          |
| CMake build configuration         | 30-60           | CMake        |
| Lowering pass (per target)        | 300-1000        | C++          |
| **Total minimum viable dialect**  | **~700-2000**   | **Mixed**    |

The MLIR-Forge project found that individual dialect components required 56-1,519 lines of code,
with each taking a developer less than one week to implement -- but these developers already knew
MLIR.

Jeremy Kun's tutorial on building a polynomial dialect noted that progression sped up over time but
acknowledged confusion with TableGen's `class` vs `def` distinction and uncertainty about optimal
file organization. He described the generated files as "multi-thousand line implementation files."

---

## 4. The C++ Requirement

**MLIR is written in C++ and requires C++ for dialect definitions.** This is non-negotiable in
upstream MLIR.

### What This Means Practically

- **Build times:** Compiling MLIR from source takes ~1 hour on a laptop, ~10 minutes on a desktop.
  This is a one-time cost, but iterating on dialect changes requires incremental rebuilds of
  generated C++ code.
- **Toolchain:** Requires a full LLVM/Clang toolchain, CMake, and TableGen.
- **Developer profile:** The official MLIR introduction page assumes "knowledge of C++ and advanced
  Python, along with passing familiarity with NVIDIA CUDA."
- **Integration with Python/Rust/TS:** Our spec-to-REST compiler is designed around Python
  (primary), Rust (alternative), or TypeScript as implementation languages. Using MLIR means either
  (a) rewriting in C++, (b) maintaining a C++ MLIR component that communicates with our main
  codebase via IPC/FFI, or (c) using xDSL (Python, see Section 9).

### Is It a Dealbreaker?

For this project: **almost certainly yes.** The implementation architecture document (07) evaluated
five languages and chose Python as the primary implementation language for its Z3 integration, LLM
API support, development velocity, and distribution story. Introducing a mandatory C++ component for
MLIR would:

1. Add a second implementation language and build system (CMake alongside pip/poetry)
2. Require C++ expertise on the team
3. Slow iteration speed for IR changes
4. Complicate distribution (native binaries vs. pure Python)
5. Provide no benefit for our actual bottleneck (parsing, constraint solving, code generation via
   templates)

---

## 5. Non-ML Uses of MLIR

MLIR is genuinely used far beyond machine learning. Notable examples:

### 5.1 CIRCT -- Hardware Design

The CIRCT project (Circuit IR Compilers and Tools) applies MLIR to hardware design, replacing
traditional RTL tools with MLIR-based compilation. Modern hardware DSLs like Chisel are moving their
backends to MLIR. This is a major non-ML success story, demonstrating MLIR's generality for
representing hardware description languages.

### 5.2 Flang -- Fortran Compiler

LLVM's new Fortran compiler (Flang) uses MLIR for its high-level IR (FIR -- Fortran IR). This
enables powerful transformations for array operations, loop optimizations, and OpenMP parallelism.
Flang already achieves performance on par with GCC's Fortran compiler. In 2024, AMD announced its
next-gen Fortran compiler will be based on Flang/MLIR.

### 5.3 Other Non-ML Uses

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
instructions.** Hardware synthesis, HPC loop nests, cryptographic circuits, database query plans --
these all have optimization-rich compilation pipelines where multi-level IR is genuinely valuable.

---

## 6. What MLIR Gives That Simpler Approaches Don't

### What MLIR Provides

1. **Multi-level IR coexistence:** Different abstraction levels in one representation, with
   well-defined lowering between them
2. **Verification infrastructure:** Built-in operation verification, type checking, and trait-based
   constraints
3. **Pass infrastructure:** Sophisticated pass management, scheduling, and dependency tracking for
   IR transformations
4. **Rewrite pattern system:** Declarative (DRR/PDLL) and programmatic pattern matching for IR
   transformations
5. **SSA form:** Automatic SSA construction and dominance analysis
6. **Serialization:** Textual and bytecode IR formats with round-tripping
7. **Ecosystem:** Access to LLVM backend for native code generation
8. **Community:** Active development, conferences, weekly public meetings

### What Our Compiler Actually Needs

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

### The Core Mismatch

MLIR is designed for **computational IRs** -- representations of programs that compute values
through sequences of operations with data dependencies. Its SSA form, dominance trees, and region
structure are designed for analyzing and optimizing computation.

Our spec language is **declarative and structural** -- it describes entities, their relationships,
behavioral contracts (pre/postconditions), and invariants. There is no "computation" to optimize.
The IR is a structured data model that drives template-based code generation, not a computational
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

---

## 7. REST/Web Service DSLs on MLIR

**None found.** After extensive searching, there are zero examples of REST API, web service, or
HTTP-related DSLs built on MLIR. This is not an oversight -- it reflects the fundamental mismatch
described in Section 6.

The closest adjacent projects:

- **Substrait MLIR** represents database query plans (data processing, not web services)
- **JSIR** analyzes JavaScript code (code analysis, not service specification)
- **P4HIR** handles network packet processing (low-level networking, not HTTP APIs)

Existing REST/API specification tools (OpenAPI, TypeSpec, Smithy, Ballerina) all use their own
purpose-built parsers and IRs. None use MLIR or any general-purpose compiler IR framework, because
API specifications are structural/declarative, not computational.

---

## 8. Learning Curve

The learning curve for MLIR is widely acknowledged as steep.

### Community Assessment

- Stephen Diehl's introduction to MLIR opens with "You probably shouldn't" learn MLIR, acknowledging
  it serves a niche audience
- The MLIR ecosystem "has a steep learning curve, which can intimidate new developers and hinders
  adoption"
- "Building a new dialect or pass often means delving into MLIR's internals (C++ templates, TableGen
  definitions, etc.) with sparse documentation"
- Google's engineers writing ML kernels in MLIR found it "a productivity challenge," leading to the
  creation of the Mojo language for a higher-level syntax

### Specific Pain Points

1. **TableGen is its own language** -- you must learn ODS, which is a DSL embedded in TableGen,
   itself a record-based DSL. So you are learning a DSL-within-a-DSL to define your DSL.
2. **C++ templates** -- MLIR's C++ layer uses heavy template metaprogramming
3. **Build system** -- CMake + TableGen code generation adds complexity
4. **Sparse documentation** -- many intermediate topics lack documentation; you often read source
   code
5. **Moving target** -- MLIR APIs evolve rapidly; code from tutorials may not compile against
   current HEAD

### Estimated Timeline

For a developer experienced in compilers but new to MLIR:

- **1-2 weeks:** Complete Toy tutorial, understand basic concepts
- **2-4 weeks:** Build a trivial custom dialect with a few operations
- **1-3 months:** Build a useful dialect with custom types, lowering passes, and transformations
- **3-6 months:** Become productive at debugging MLIR issues and extending the dialect

For a developer NOT experienced in compilers or C++:

- **Add 2-4 months** to each estimate above

---

## 9. xDSL: The Python Alternative

xDSL is a Python-native compiler toolkit that is 1:1 compatible with MLIR's textual IR format. It
deserves separate consideration because it removes the C++ barrier.

### What xDSL Offers

- **Pure Python:** `pip install xdsl` -- no C++ compilation needed
- **MLIR-compatible IR format:** Same textual representation, can exchange IR with MLIR
- **Python dialect definitions:** Define operations, types, and transformations in Python instead of
  TableGen/C++
- **Rapid prototyping:** Designed for "fast prototyping of MLIR concepts before upstreaming to MLIR
  itself"
- **IRDL support:** Dialects can be defined using IRDL (Intermediate Representation Definition
  Language) for portability

### Compilation Speed

> "Compiling MLIR requires two orders of magnitude more time than xDSL, taking almost 1 hour on a
> laptop and 10 minutes on a desktop, compared to the few seconds the xDSL setup needs on both
> machines."

### Does xDSL Change the Recommendation?

xDSL removes the C++ barrier but not the fundamental mismatch. Even with Python dialect definitions,
we would still be using SSA-based IR infrastructure designed for computational optimization on a
declarative specification language. The infrastructure would provide:

- SSA form (irrelevant -- our IR has no data flow to track)
- Pass scheduling (marginally useful -- we have a fixed pipeline)
- Operation verification (somewhat useful -- but Python dataclasses + Pydantic do this)
- Textual IR format (nice-to-have for debugging, but not essential)

The question isn't "can we make it work" but "does it earn its keep." xDSL adds a dependency and a
conceptual framework (dialects, operations, regions, blocks) that maps awkwardly to our domain.

---

## 10. Fit Assessment for Spec-to-REST

### Scoring (1-5, where 5 = perfect fit)

| Criterion              | Score     | Reasoning                                                   |
| ---------------------- | --------- | ----------------------------------------------------------- |
| Parsing support        | 1/5       | Zero. Must build parser separately regardless               |
| IR suitability         | 2/5       | SSA-based computational IR for a declarative spec language  |
| Code generation model  | 1/5       | Progressive lowering to LLVM IR vs. template-based emission |
| Language compatibility | 1/5       | C++ required; our compiler is Python                        |
| Learning curve         | 2/5       | Steep, 1-3 months to productive                             |
| Ecosystem value        | 2/5       | Rich but irrelevant (LLVM backend, affine analysis, etc.)   |
| Distribution impact    | 1/5       | Adds native compilation dependency to Python project        |
| Domain precedent       | 1/5       | Zero REST/web service DSLs use MLIR                         |
| **Overall**            | **1.4/5** | **Poor fit**                                                |

### Why MLIR Works for Others but Not for Us

Projects that benefit from MLIR share these characteristics:

1. **Computational domain:** Operations transform data through computation (arithmetic, memory
   access, control flow)
2. **Optimization-rich pipeline:** Multiple optimization passes that benefit from SSA analysis,
   dominance, loop analysis
3. **Hardware targeting:** Need to lower to specific hardware (GPUs, TPUs, FPGAs, ASICs)
4. **Multi-level abstraction:** Genuine benefit from representing the same program at different
   abstraction levels simultaneously

Our spec-to-REST compiler has **none of these characteristics.** Our pipeline is:

```
Spec text -> Parse -> Typed IR -> Constraint check (Z3) -> Convention mapping -> Template emission
```

This is a **translation pipeline**, not a **compilation pipeline.** We translate specifications into
code using conventions and templates. We don't optimize computations, we don't lower through
abstraction levels, and we don't target hardware.

---

## 11. Recommendation

**Do not use MLIR for the spec-to-REST compiler.**

MLIR is an impressive piece of engineering, genuinely useful for computational compiler
infrastructure, and has proven its value in hardware design (CIRCT), HPC (Flang), encryption
(Concrete/HEIR), and many other domains. But it is the wrong tool for this job.

### What to Use Instead

The existing architecture in document 07 is correct:

1. **Parser:** ANTLR, tree-sitter, pest (Rust), or hand-written recursive descent -- all are
   appropriate for our grammar complexity
2. **IR:** Python dataclasses (or Rust structs / TypeScript interfaces) forming a typed AST and
   service IR. No SSA, no regions, no blocks -- just a clean data model
3. **Transformations:** Straightforward Python functions that walk the IR and apply convention rules
4. **Code generation:** Jinja2 (Python) / askama (Rust) / EJS (TypeScript) template engines
5. **Verification:** Z3 via its Python bindings (the primary Z3 interface)

### When MLIR _Would_ Make Sense

If the spec-to-REST project evolved to include:

- **Runtime optimization** of generated service code (JIT compilation of hot paths)
- **Hardware targeting** (generating FPGA-accelerated request processing)
- **Formal verification via compilation** (lowering specs through proof-carrying code)
- **A general-purpose programming language** as the spec language

...then MLIR might become relevant. But for a specification-to-code translation tool, it adds
complexity without proportionate value.

### The One Lesson Worth Taking from MLIR

MLIR's dialect system demonstrates that **multi-level IR with explicit abstraction boundaries** is a
powerful design pattern. We can adopt this idea cheaply:

```python
# Three IR levels, plain Python, no MLIR needed
@dataclass
class SpecIR:
    """Level 1: Direct representation of spec language constructs"""
    entities: List[EntityDecl]
    operations: List[OperationDecl]
    invariants: List[InvariantDecl]

@dataclass
class ServiceIR:
    """Level 2: REST-aware intermediate representation"""
    routes: List[RouteDecl]
    models: List[ModelDecl]
    db_schema: List[TableDecl]
    validations: List[ValidationRule]

@dataclass
class TargetIR:
    """Level 3: Language-specific, ready for template emission"""
    files: List[FileDecl]
    dependencies: List[Dependency]
    config: Dict[str, Any]
```

This gives us explicit abstraction levels and a clear lowering pipeline -- the architectural insight
from MLIR -- without the C++ build system, TableGen DSL, SSA infrastructure, or months of learning
curve.

---

## 12. Sources

- [Creating a Dialect - MLIR Official Tutorial](https://mlir.llvm.org/docs/Tutorials/CreatingADialect/)
- [Chapter 2: Emitting Basic MLIR - Toy Tutorial](https://mlir.llvm.org/docs/Tutorials/Toy/Ch-2/)
- [Chapter 1: Toy Language and AST - Toy Tutorial](https://mlir.llvm.org/docs/Tutorials/Toy/Ch-1/)
- [Operation Definition Specification (ODS)](https://mlir.llvm.org/docs/DefiningDialects/Operations/)
- [Defining Dialects - MLIR](https://mlir.llvm.org/docs/DefiningDialects/)
- [Users of MLIR](https://mlir.llvm.org/users/)
- [MLIR Part 1 - Introduction to MLIR - Stephen Diehl](https://www.stephendiehl.com/posts/mlir_introduction/)
- [MLIR -- Defining a New Dialect - Jeremy Kun](https://www.jeremykun.com/2023/08/21/mlir-defining-a-new-dialect/)
- [MLIR Tutorial: Custom Dialect and Lowering - Dhamo Dharan](https://medium.com/sniper-ai/mlir-tutorial-create-your-custom-dialect-lowering-to-llvm-ir-dialect-system-1-1f125a6a3008)
- [BrilIR: An MLIR Dialect for Bril - Cornell CS 6120](https://www.cs.cornell.edu/courses/cs6120/2025fa/blog/brilir/)
- [ANTLR4-to-MLIR Bridge - LLVM Discourse](https://discourse.llvm.org/t/an-automatic-dialect-and-frontend-generator-from-antlr4-grammars/75323)
- [DSP-MLIR: A DSL and MLIR Dialect for Digital Signal Processing - PLDI 2025](https://pldi25.sigplan.org/details/LCTES-2025-main/7/DSP-MLIR-A-Domain-Specific-Language-and-MLIR-Dialect-for-Digital-Signal-Processing)
- [CIRCT - Circuit IR Compilers and Tools](https://circt.llvm.org/)
- [Flang and MLIR - arXiv:2409.18824](https://arxiv.org/abs/2409.18824)
- [xDSL - Python Compiler Toolkit](https://xdsl.dev/)
- [xDSL GitHub Repository](https://github.com/xdslproject/xdsl)
- [MLIR-Forge: A Modular Framework for Language Smiths](https://arxiv.org/html/2601.09583v1)
- [Modular: What About the MLIR Compiler Infrastructure](https://www.modular.com/blog/democratizing-ai-compute-part-8-what-about-the-mlir-compiler-infrastructure)
- [MLIR: A Compiler Infrastructure for the End of Moore's Law - Lattner et al.](https://arxiv.org/pdf/2002.11054)
- [MLIR: Scaling Compiler Infrastructure for Domain Specific Computation](https://rcs.uwaterloo.ca/~ali/cs842-s23/papers/mlir.pdf)
- [Hands-on Practical: Creating a Custom Dialect](https://apxml.com/courses/compiler-optimizations-machine-learning/chapter-4-mlir-infrastructure/hands-on-creating-custom-dialect)
- [How to Build Your Own MLIR Dialect - FOSDEM 2023](https://archive.fosdem.org/2023/schedule/event/mlirdialect/attachments/slides/5740/export/events/attachments/mlirdialect/slides/5740/How_to_Build_your_own_MLIR_Dialect.pdf)
