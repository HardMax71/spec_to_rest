---
title: "What can go wrong"
description: "The classes of design defect a spec can hide, and why verification exists"
---

A spec can typecheck cleanly and still describe something impossible, contradictory, or dead. Closing
that gap, the one between a well-formed spec and a satisfiable one, is what the verification engine is
for. It checks that the invariants can hold at all and that every operation preserves them, and when
something is off it reports one of four design-error categories defined in
[`modules/verify`](https://github.com/HardMax71/spec_to_rest/tree/main/modules/verify). How it proves
these is the [techniques](/research/spec_verification/techniques) page; the exact diagnostics and the
CLI are the live [verification pipeline](/pipelines/verification).

## An operation that breaks an invariant

The central check. For each operation the engine asks whether the precondition and the operation's
effect together guarantee the invariants still hold afterward. When they do not, the operation can
drive the service into a state the spec forbids.

```spec
operation Withdraw {
  input: account_id: AccountId, amount: Money
  requires: account_id in accounts
  ensures:
    accounts'[account_id].balance = accounts[account_id].balance - amount
}
```

If a service invariant says `balance >= 0`, this operation violates it: the `requires` checks only
that the account exists, not that it holds enough, so a large `amount` drives the balance negative.
The engine reports `invariant_violation_by_operation` with the offending operation and a
counterexample state. The fix is in the spec, tighten the precondition to
`amount <= accounts[account_id].balance`.

## Invariants that contradict each other

```spec
state { items: Item -> lone Price }

invariant: all i in items | items[i] > 100
invariant: all i in items | items[i] < 50
```

No price is both above 100 and below 50, so no valid state exists at all. Every operation preserves
the invariants vacuously, and the service can never even be initialized. The engine reports
`contradictory_invariants` rather than letting the contradiction hide behind vacuous proofs.

## A precondition that can never hold

```spec
operation Refund {
  input: order_id: OrderId
  requires:
    orders[order_id].status = SHIPPED
    and orders[order_id].status = CANCELLED
}
```

An order cannot be SHIPPED and CANCELLED at once, so the conjunction is unsatisfiable and `Refund` is
dead, no input ever passes its gate. This is `unsatisfiable_precondition`, found by checking each
`requires` for satisfiability on its own.

## An operation that can never fire

The subtler cousin: a precondition that is fine in isolation but contradicts the invariants on every
reachable state.

```spec
invariant: all o in orders | o.status != FROZEN

operation Thaw {
  input: order_id: OrderId
  requires: orders[order_id].status = FROZEN
  ensures: orders'[order_id].status = ACTIVE
}
```

Nothing is wrong with requiring a frozen order in the abstract, but the invariant guarantees no order
is ever frozen, so no valid pre-state satisfies the precondition and `Thaw` can never run. The engine
reports `unreachable_operation`, kept separate from the always-false case because the cause and the
fix differ: relax the invariant or drop the operation.

## What other tools catch

These four are what the solver reports, and they are not the whole story. Type and scope errors, a
multiplicity mismatch, an undeclared field, a primed variable in a global invariant, a quantifier
over the wrong set, never reach the solver, because the typechecker rejects them first. The obvious
smells are lint warnings rather than solver findings: an `ensures: true` that forgets to say
anything, a tautological invariant like `all c in store | c = c`, an entity no operation ever touches.
And some defects the engine does not catch at all yet, two operations that are secretly identical, an
invariant strictly subsumed by another, a precondition stronger than the invariants require. Those
need the cross-operation equivalence reasoning the current four checks do not attempt, so they stay
design goals rather than shipped diagnostics. When the engine cannot decide a construct rather than
disprove it, it says so outright, as a `translator_limitation` or `soundness_limitation`, never a
false all-clear.
