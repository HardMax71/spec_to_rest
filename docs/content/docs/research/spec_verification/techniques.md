---
title: "Techniques"
description: "SMT and bounded model checking, and which backend handles what"
---

The engine runs on two solvers, and the split between them is the whole technique. Z3 decides
first-order formulas over integers, strings, and arrays; Alloy searches a finite universe for the
sets, relations, and reachability that first-order logic cannot pin down decidably. Each check is
routed to whichever backend can actually decide it, with no overlap.

## The proof obligation

Almost everything reduces to one question, asked per operation: given that the invariants hold
before, does the operation's contract guarantee they hold after? Written out, that is the implication

`invariants(s) and requires(in, s) and ensures(in, s, s', out)  =>  invariants(s')`

and the trick is to check it by trying to break it. The engine asserts the negation, that the
pre-state is valid, the operation runs, and an invariant still fails afterward, then hands it to Z3:

```spec
entity Account {
  balance: Money
  invariant: balance >= 0
}

operation Withdraw {
  input: account_id: AccountId, amount: Money
  requires: account_id in accounts
  ensures: accounts'[account_id].balance = accounts[account_id].balance - amount
}
```

```lisp
; assert the negation of "Withdraw preserves balance >= 0" and look for a model
(assert (>= (balance acc) 0))                          ; invariant holds before
(assert (member acc accounts))                         ; requires
(assert (= (balance_post acc) (- (balance acc) amt)))  ; ensures
(assert (not (>= (balance_post acc) 0)))               ; invariant broken after
(check-sat)
```

If Z3 finds a model, that model is a counterexample: a concrete balance and amount that drive the
account negative, reported as `invariant_violation_by_operation`. If it cannot, no such state exists
and the operation is safe. The same negate-and-search shape produces the other three findings, an
unsatisfiable invariant set (`contradictory_invariants`), a self-contradictory precondition
(`unsatisfiable_precondition`), and a precondition no invariant-respecting state can satisfy
(`unreachable_operation`).

## Z3, for first-order facts

Z3's value is that on its decidable fragments, linear arithmetic, strings, arrays, uninterpreted
functions, an `unsat` result is a proof, not a sample. That covers the bulk of a spec: scalar and
refinement invariants, arithmetic in postconditions, equality and membership. The translation models
each entity field as an accessor function and the state as a domain predicate plus a mapping
function, with pre-state and post-state as separate symbols, then states the invariants as
definitions and the verification condition as an assertion. The exact SMT-LIB the engine emits, and
how to dump and read it, are on the live [verification pipeline](/pipelines/verification) page.

## Alloy, for sets and time

Some properties sit outside what Z3 can decide: quantifying over subsets, transitive closure, whether
a state is reachable through some sequence of operations. Those go to Alloy 6, which does bounded
model checking, it searches a finite universe of a bounded number of entities and steps for a
counterexample. The tradeoff is worth stating plainly: a clean Alloy result means no counterexample
exists up to that scope, strong evidence rather than a universal proof, whereas Z3's `unsat` on a
decidable fragment holds for all sizes. State-machine and temporal questions run through Alloy too in
this pragmatic first version, whether some sequence of operations can reach a forbidden state, or
deadlock.

## Picking a backend

The router sends each check to the one backend that can decide it: first-order and arithmetic
obligations to Z3, powerset, transitive-closure, and temporal ones to Alloy, with the two sets
disjoint so nothing is checked twice or slips between them. A fuller temporal logic like TLA+ was
considered for the time-based properties and left out as too heavy for this stage, the
[comparison](/research/spec_verification/comparison) page covers why. The exact routing rules are on
the [verification pipeline](/pipelines/verification) page.
