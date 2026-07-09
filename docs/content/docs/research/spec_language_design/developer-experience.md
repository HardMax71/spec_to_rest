---
title: "Errors and developer experience"
description: "What check and verify catch, and the state of editor support"
---

Two commands sit between a spec and generated code. `check` parses the spec and runs fast structural
lints over the IR; `verify` hands the operation contracts to a solver. They catch different things.

## Structural lints (`check`)

Once a parse succeeds, `check` runs seven lints over the IR. Each carries a stable code. An error
exits non-zero; a warning exits zero.

| Code | Level   | Catches                                                                                  |
| ---- | ------- | ---------------------------------------------------------------------------------------- |
| L01  | error   | a non-boolean literal used as a logical operand, or a Bool or None literal in arithmetic, comparison, or `in` |
| L02  | error   | an identifier referenced with no in-scope binding                                        |
| L03  | warning | an operation that declares an `output` but no `ensures`                                  |
| L04  | warning | two operations with the same input and output signature and equivalent `requires`       |
| L05  | warning | an entity declared but never referenced                                                  |
| L06  | error   | mutually recursive predicates or functions, which the verifier's inlining cannot unfold  |
| L07  | warning | an operation whose resolved `204 No Content` success status would drop its declared outputs |

The lints are deliberately narrow. L01 fires only on literals whose type admits no operator at all,
so it catches obvious mistakes without standing in for a full type checker.

## Verification diagnostics (`verify`)

`verify` routes each proof obligation to Z3 or Alloy, and when one fails it reports a typed
diagnostic in one of eight categories:

| Category                            | Meaning                                                                       |
| ----------------------------------- | ----------------------------------------------------------------------------- |
| `contradictory_invariants`          | no state satisfies all the invariants at once                                 |
| `unsatisfiable_precondition`        | an operation's `requires` contradicts itself                                  |
| `unreachable_operation`             | the `requires` holds on its own but conflicts with the invariants on every valid pre-state |
| `invariant_violation_by_operation`  | an operation's `ensures` can leave an invariant broken                        |
| `solver_timeout`                    | a check did not finish within the timeout                                     |
| `translator_limitation`             | the spec uses a construct the verifier cannot translate                       |
| `backend_error`                     | the solver crashed on the check                                               |
| `soundness_limitation`              | the check touches a construct outside the formally verified subset            |

Each diagnostic prints as a location, a level, and a message, followed by the contributing spans
(the unsat core), a counterexample with concrete pre- and post-state values where the solver
produced one, and a plain-English hint:

```text
url_shortener.spec:30:7: error: <message>
  unsat core (contributing assertions):
    url_shortener.spec:20:7  <note>
  Counterexample:
    <pre and post values from the solver model>
  hint: <suggestion tailored to the category>
```

The hint is specific to the failure. For a violated invariant it names the operation, the invariant,
and the offending fields; for a timeout it suggests raising `--timeout` or splitting a heavy
quantifier; for a soundness limit it points at the verified-subset ledger in
`proofs/isabelle/STATUS.md`.

## Editor support

There is no language server or editor extension yet. The only spec-aware tooling today is the syntax
highlighting on this docs site, driven by a small Shiki grammar. The working loop is the two commands
above, both of which print spans back into the source. A future language server could surface the
same lints and verification diagnostics inline, with completion, hover, and go-to-definition, but
none of that is built.
