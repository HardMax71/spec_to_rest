---
title: "Verdict"
description: "The fit assessment, the xDSL alternative, and the decision"
---

## 9. Xdsl: The Python alternative

xDSL is a Python-native compiler toolkit that is 1:1 compatible with MLIR's textual IR format. It
deserves separate consideration because it removes the C++ barrier.

### What xdsl offers

- Pure Python: `pip install xdsl`, no C++ compilation needed
- MLIR-compatible IR format: same textual representation, can exchange IR with MLIR
- Python dialect definitions: define operations, types, and transformations in Python instead of
  TableGen/C++
- Rapid prototyping: designed for "fast prototyping of MLIR concepts before upstreaming to MLIR
  itself"
- IRDL support: dialects can be defined using IRDL (Intermediate Representation Definition
  Language) for portability

### Compilation speed

> "Compiling MLIR requires two orders of magnitude more time than xDSL, taking almost 1 hour on a
> laptop and 10 minutes on a desktop, compared to the few seconds the xDSL setup needs on both
> machines."

### Does xdsl change the recommendation?

xDSL removes the C++ barrier but not the core mismatch. Even with Python dialect definitions,
we would still be using SSA-based IR infrastructure designed for computational optimization on a
declarative specification language. The infrastructure would provide:

- SSA form (irrelevant, our IR has no data flow to track)
- Pass scheduling (marginally useful, we have a fixed pipeline)
- Operation verification (somewhat useful, but Python dataclasses + Pydantic do this)
- Textual IR format (nice-to-have for debugging, but not essential)

The question isn't "can we make it work" but "does it earn its keep." xDSL adds a dependency and a
conceptual framework (dialects, operations, regions, blocks) that maps awkwardly to our domain.

## 10. Fit assessment for spec-to-REST

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

### Why MLIR works for others but not for us

Projects that benefit from MLIR share these characteristics:

1. Computational domain: operations transform data through computation (arithmetic, memory
   access, control flow)
2. Optimization-rich pipeline: multiple optimization passes that benefit from SSA analysis,
   dominance, loop analysis
3. Hardware targeting: need to lower to specific hardware (GPUs, TPUs, FPGAs, ASICs)
4. Multi-level abstraction: genuine benefit from representing the same program at different
   abstraction levels simultaneously

Our spec-to-REST compiler has **none of these characteristics.** Our pipeline is:

```text
Spec text -> Parse -> Typed IR -> Constraint check (Z3) -> Convention mapping -> Template emission
```

This is a **translation pipeline**, rather than a **compilation pipeline.** We translate specifications into
code using conventions and templates. We don't optimize computations, we don't lower through
abstraction levels, and we don't target hardware.

## 11. Recommendation

**Do not use MLIR for the spec-to-REST compiler.**

MLIR is an impressive piece of engineering, genuinely useful for computational compiler
infrastructure, and has proven its value in hardware design (CIRCT), HPC (Flang), encryption
(Concrete/HEIR), and many other domains. But it is the wrong tool for this job.

### What to use instead

The existing architecture in document 07 is correct:

1. Parser: ANTLR, tree-sitter, pest (Rust), or hand-written recursive descent, all are
   appropriate for our grammar complexity
2. IR: Python dataclasses (or Rust structs / TypeScript interfaces) forming a typed AST and
   service IR. No SSA, no regions, no blocks, just a clean data model
3. Transformations: straightforward Python functions that walk the IR and apply convention rules
4. Code generation: Jinja2 (Python) / askama (Rust) / EJS (TypeScript) template engines
5. Verification: Z3 via its Python bindings (the primary Z3 interface)

### When MLIR _would_ make sense

If the spec-to-REST project evolved to include:

- Runtime optimization of generated service code (JIT compilation of hot paths)
- Hardware targeting (generating FPGA-accelerated request processing)
- Formal verification via compilation (lowering specs through proof-carrying code)
- A general-purpose programming language as the spec language

...then MLIR might become relevant. But for a specification-to-code translation tool, it adds
complexity without proportionate value.

### The one lesson worth taking from MLIR

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

This gives us explicit abstraction levels and a clear lowering pipeline, the architectural insight
from MLIR, without the C++ build system, TableGen DSL, SSA infrastructure, or months of learning
curve.
