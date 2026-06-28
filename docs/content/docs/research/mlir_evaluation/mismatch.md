---
title: "The mismatch"
description: "What MLIR offers against what a DSL compiler actually needs"
---

The case against MLIR here is not that it is niche or ML-only; it is genuinely general and genuinely
good. The case is that nearly everything it is good at, this compiler does not do.

## Where it fits

MLIR runs well beyond machine learning. CIRCT brings it to hardware design, and modern HDLs like
Chisel are moving their backends onto it. LLVM's Fortran compiler, Flang, builds on an MLIR-based
high-level IR and already matches GCC on performance. The published users list runs further still, to
query-plan representation, fully homomorphic encryption circuits, C and C++ IR, packet processors,
digital signal processing, and Mojo's systems language, among others. The common thread is
unmistakable: every one of them is a domain with computation to optimize, sequences of operations,
data dependencies, loop nests, lowering toward machine instructions, where a multi-level IR with
passes and rewrites earns its keep.

## Why not here

A spec-to-REST compiler has none of that. Walk its stages and ask at each one whether MLIR helps:

| Compiler stage     | What it needs                          | MLIR helps? |
| ------------------ | -------------------------------------- | ----------- |
| Parsing            | lexer and parser for the spec DSL      | no          |
| AST construction   | typed AST from the parse tree          | no          |
| Semantic analysis  | type checking, scope resolution        | partially   |
| IR construction    | entities, operations, invariants       | partially   |
| Constraint solving | Z3 for invariant checking              | no          |
| Convention mapping | entities to HTTP routes and DB schemas | no          |
| Synthesis          | LLM-generated operation bodies         | no          |
| Code generation    | template-based multi-target emission   | no          |
| Test generation    | property-based test synthesis          | no          |

Two stages get a partial yes, semantic analysis and IR construction, and even there the fit is poor:
relational constraints, pre- and postconditions, and REST concepts like HTTP methods and status codes
do not map onto a computation-oriented IR. The deeper reason is what the IR is. MLIR represents
programs that compute values; this compiler's
[IR](/research/implementation_architecture/ir-design) is declarative and structural, entities, their
relationships, behavioral contracts, and invariants, a data model that drives template-based code
generation rather than a computation graph that lowers to machine code. There is nothing to schedule,
vectorize, or lower, so MLIR's dominance trees, memory analysis, and LLVM backend are beside the
point.

That is also why no one has built a REST or web-service DSL on MLIR; an extended search turns up
zero. The adjacent projects are all data processing or code analysis rather than service
specification: Substrait for query plans, JSIR for JavaScript, P4HIR for packet processing. The
established API tools, OpenAPI, TypeSpec, Smithy, Ballerina, all use purpose-built parsers and IRs,
for the same reason, an API spec is structural, not computational.

## The learning curve

Even setting fit aside, the cost of entry is steep, and widely acknowledged to be. Stephen Diehl's
introduction to MLIR opens with "you probably shouldn't." Defining a dialect means learning ODS, a
DSL embedded in TableGen, which is itself a record-based DSL, so it is a DSL within a DSL to define
your DSL, on top of heavy C++ template metaprogramming and sparse documentation that sends you to the
source. Google's own engineers found writing MLIR kernels enough of a productivity drag to spawn the
Mojo language as a higher-level front. The rough timeline for a compiler engineer new to MLIR runs one
to three months to a useful dialect and three to six to real fluency; for someone new to C++ as well,
add a few months to each. That is a large bet on infrastructure that, by the table above, would touch
almost none of the actual work.
