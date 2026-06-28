---
title: "Verdict"
description: "The fit assessment, the xDSL alternative, and the decision"
---

## xDSL, the Python escape hatch

The one alternative that deserves a real look is xDSL, a Python-native toolkit that is one-to-one
compatible with MLIR's textual IR and needs no C++ at all: `pip install xdsl`, dialects defined in
Python, and a setup that builds in seconds where MLIR from source takes the better part of an hour. It
genuinely removes the C++ barrier. It does not remove the two problems that matter. xDSL is Python,
and this compiler is Scala on the JVM, so it trades a C++ component for a Python one running beside
the JVM, the same cross-language seam. And the core mismatch survives the language change: it is still
SSA-based infrastructure built for computational optimization, pointed at a declarative spec. Of what
it offers, SSA form tracks a data flow the IR does not have, pass scheduling is wasted on a fixed
pipeline, and operation verification is something the typed ADT already does. The question was never
whether MLIR or xDSL could be made to work; it is whether either earns its dependency, and neither
does.

## The fit, scored

Laying the criteria out makes the gap concrete.

| Criterion             | Score   | Why                                                          |
| --------------------- | ------- | ------------------------------------------------------------ |
| Parsing support       | 1/5     | none; a parser is built separately regardless                |
| IR suitability        | 2/5     | SSA computational IR against a declarative spec              |
| Code-generation model | 1/5     | progressive lowering to LLVM IR, not template emission       |
| Language fit          | 1/5     | C++, or Python via xDSL; the compiler is Scala on the JVM    |
| Learning curve        | 2/5     | steep, one to three months to productive                     |
| Ecosystem value       | 2/5     | rich but irrelevant: LLVM backend, affine analysis           |
| Distribution          | 1/5     | adds a native toolchain dependency                           |
| Domain precedent      | 1/5     | no REST or web-service DSL uses MLIR                          |
| Overall               | 1.4/5   | poor fit                                                     |

The pattern underneath the numbers is simple. MLIR pays off where there is a computational domain, an
optimization-rich pipeline, hardware to target, and genuine multi-level abstraction. This compiler has
none of them: it parses a spec, types it, checks it with Z3, maps conventions, and emits templates.
That is a translation pipeline, not a compilation one, with no computations to optimize, no
abstraction levels to lower through, and no hardware to reach.

## The decision

Do not use MLIR. It is an impressive piece of engineering, proven in hardware design, HPC, and
encryption, and it is the wrong tool for this one. The stack that shipped is the right shape: an
ANTLR4 grammar for the parser, the [verified IR](/research/implementation_architecture/host-language)
extracted from Isabelle as a plain Scala ADT with no SSA, regions, or blocks, ordinary Scala functions
that walk it and apply convention rules, Handlebars templates for emission, and Z3 through z3-turnkey
with Alloy for the checking.

MLIR would start to make sense only if the project grew a genuine computational core: runtime
optimization of generated services, hardware-accelerated request processing, or a general-purpose
language as the spec input. Short of that, it adds complexity out of proportion to its value.

One idea is worth taking, though, and it costs nothing. MLIR's dialects show the power of multi-level
IR with explicit abstraction boundaries, and the compiler adopts exactly that without any of the
machinery: the spec IR is one level, the convention-annotated profile that adds HTTP and schema
decisions is a second, and the per-target shape handed to the templates is a third. Three levels and a
clear lowering between them, in plain Scala.
