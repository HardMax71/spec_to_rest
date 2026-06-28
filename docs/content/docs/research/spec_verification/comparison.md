---
title: "How it compares"
description: "The verification engine against TLA+, Alloy, and other spec checkers"
---

The engine settled on two solvers, Z3 and Alloy. That pairing is a choice out of a crowded field of
spec checkers, and the reasons it left the others out are as telling as the two it kept.

## Alloy

Alloy is the natural fit for the relational half of the work, and it is the one picked for it. Its
sigs and relations mirror the spec's entities, relations, and multiplicities almost one to one, so
the translation is short, and transitive closure, relational composition, and set operations are
native rather than encoded. The Kodkod engine compiles all of that to SAT, and the small-scope
hypothesis, that most bugs surface in small instances, makes bounded search a sound bet. The price is
that Alloy is weak exactly where Z3 is strong: no real string theory, integers only at a bounded
bitwidth, and no proof beyond the scope it searched. That division is the reason the engine runs
both, Alloy for the relational and reachability questions, Z3 for arithmetic, strings, and the
first-order proofs.

## The TLA+ family

TLA+ is the obvious thing to reach for on temporal properties, and three tools check it: TLC
enumerates the state space explicitly (and has found real bugs in S3, DynamoDB, and EBS at AWS),
Apalache checks it symbolically through Z3 to dodge state explosion, and Quint wraps TLA+ in a
friendlier syntax. They are genuinely good at liveness and fairness, which Alloy only approximates.
The engine still does not use them, because adopting TLA+ means a second specification language, a JVM
subprocess, and the state-explosion tax, for temporal needs this stage meets with Alloy's bounded
search. So TLA+ stays the considered-and-declined option rather than a dependency; if the temporal
demands outgrow bounded checking, this is the family to revisit.

## Dafny

Dafny is a deductive verifier: Z3 underneath, but proving pre- and postconditions for all inputs
rather than searching a bounded space, and compiling the verified code to several languages. It does
not verify specs here, that is Z3 and Alloy's job, but it is not absent from the project either. It
is the backend of the [synthesis loop](/research/llm_verifier_synthesis/cegis-loop), where a
generated operation body is proved against its contract before it ships. The boundary is worth
keeping straight: specs are model-checked with Z3 and Alloy; synthesized implementations are
deductively verified with Dafny.

## The niche checkers

Two more come up and get ruled out on domain rather than capability. Spin is excellent for concurrent
protocols expressed in Promela, and NuSMV and nuXmv are built for hardware-like finite-state systems
and branching-time CTL. Neither maps cleanly onto a REST service's data model, strings, and
per-operation contracts, so translating a spec into either would be a fight against the tool. They are
the right instruments for a different problem.

| Tool                   | Best at                             | Role here                                       |
| ---------------------- | ----------------------------------- | ----------------------------------------------- |
| Z3                     | first-order SMT                     | core: preservation, satisfiability, diagnostics |
| Alloy 6                | relational and bounded reachability | sets, relations, pragmatic temporal             |
| Dafny                  | unbounded deductive proof           | synthesis backend, not spec verification        |
| TLC / Apalache / Quint | temporal logic over TLA+            | considered, not adopted                         |
| Spin, NuSMV / nuXmv    | concurrent protocols, finite-state CTL | wrong domain for REST specs                  |
