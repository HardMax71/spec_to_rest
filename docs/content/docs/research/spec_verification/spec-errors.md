---
title: "What can go wrong"
description: "The classes of design defect a spec can hide, and why verification exists"
---

## 1. What can Go wrong in a spec

Every specification error falls into one of five categories: inconsistency, incompleteness,
unreachability, type errors, or semantic warnings. We enumerate them exhaustively here so the
verification engine can be designed to catch each one.

### 1.1 Inconsistency errors

An inconsistency means two or more parts of the spec contradict each other. No implementation can
simultaneously satisfy both. These are the most dangerous errors because they silently produce
impossible requirements.

**1.1.1 Invariant contradicts an operation's postcondition**

```text
entity ShortCode {
  value: String
  invariant: len(value) >= 6
}

state {
  store: ShortCode -> lone LongURL
}

operation Shorten {
  input:  url: LongURL
  output: code: ShortCode, short_url: String

  requires: isValidURI(url.value)

  ensures:
    store'[code] = url
    short_url = base_url + "/" + code.value
}
```

The `ensures` clause places no constraint on `code.value`'s length. It is legal under the
postcondition for `code.value` to be `"ab"` (length 2), but this violates the entity invariant
`len(value) >= 6`. The operation's postcondition is consistent with states that the invariant
forbids.

This is insidious because the spec author likely _intended_ the invariant to apply, but the ensures
clause does not explicitly reference it. The verification engine must synthesize the proof
obligation: does the postcondition, together with the precondition, guarantee the invariant holds in
the post-state?

**1.1.2 Two operations' postconditions are mutually exclusive**

```text
operation SetStatus {
  input: order_id: OrderId, status: Status
  ensures: orders'[order_id].status = status
}

operation FreezeOrder {
  input: order_id: OrderId
  ensures: orders'[order_id].status = FROZEN
           and all o in orders' | o.status != CANCELLED
}
```

If `SetStatus` is invoked with `status = CANCELLED` and then `FreezeOrder` is invoked,
`FreezeOrder`'s postcondition demands that no order has status CANCELLED. But the spec does not
require `SetStatus`'s effect to be undone. If both operations are in the same service, their
postconditions impose conflicting constraints on reachable states.

**1.1.3 An invariant is unsatisfiable (no valid state exists)**

```text
state {
  items: Item -> lone Price
}

invariant: all i in items | items[i] > 100
invariant: all i in items | items[i] < 50
```

No price can be simultaneously greater than 100 and less than 50. No valid state exists. Every
operation trivially preserves the invariant (vacuous truth), but the service can never be
initialized.

**1.1.4 Initial state violates an invariant**

```text
state {
  counter: Int
}

invariant: counter > 0

// No operation creates the initial state; counter defaults to 0.
```

The implicit initial state (empty store, zero counters) may not satisfy the invariant. The spec must
either define an explicit init block or the invariant must hold for the default values of all state
components.

**1.1.5 Conflicting cardinality constraints**

```text
state {
  assignments: Student -> one Course    // every student has exactly one course
}

operation Unenroll {
  input: s: Student
  ensures: s not in assignments'         // student has no course
}
```

After `Unenroll`, the student has zero courses, but the `one` multiplicity requires exactly one. The
operation postcondition contradicts the state declaration's multiplicity.

### 1.2 Incompleteness errors

An incomplete spec has gaps: situations it does not address. While a spec need not describe every
possible behavior, certain gaps indicate likely design errors.

**1.2.1 Operation requires clause does not cover a case needed by ensures**

```text
operation Withdraw {
  input: account_id: AccountId, amount: Money

  requires: account_id in accounts

  ensures:
    accounts'[account_id].balance = accounts[account_id].balance - amount
}
```

The requires clause checks that the account exists but does not check that `balance >= amount`. The
ensures clause allows negative balances. If the service invariant demands `balance >= 0`, the
operation can violate it. The requires clause is insufficient to guarantee the postcondition
preserves the invariant.

**1.2.2 Missing state transition**

```text
operation CreateOrder { ... ensures: orders'[id].status = PENDING }
operation CancelOrder { ... requires: orders[id].status = PENDING ... }
operation ShipOrder   { ... requires: orders[id].status = PAID ... }

// Missing: no operation transitions from PENDING to PAID.
```

The state machine has a gap: orders can be created (PENDING) and cancelled, and can be shipped if
PAID, but nothing transitions an order from PENDING to PAID. The PAID state is unreachable.
ShipOrder can never be invoked.

**1.2.3 Entity defined but never used in any operation**

```text
entity AuditLog {
  timestamp: DateTime
  action: String
  user: UserId
}

// No operation reads or writes AuditLog. It is dead specification.
```

**1.2.4 State relation is read but never written (always empty)**

```text
state {
  cache: ShortCode -> lone LongURL
}

operation Resolve {
  input: code: ShortCode
  requires: code in cache           // reads cache
  ensures: url = cache[code]
}

// No operation ever writes to cache. It is always empty.
// Therefore: Resolve's requires clause is always false.
```

**1.2.5 Operation input field is never constrained**

```text
operation Transfer {
  input: from: AccountId, to: AccountId, amount: Money, memo: String

  requires: from in accounts and to in accounts and amount > 0
  ensures: ...
}
```

The `memo` field is accepted as input but never appears in the requires or ensures clauses, nor in
any invariant. It is carried through without purpose. This may be intentional (pass-through
metadata) or may indicate a forgotten constraint.

**1.2.6 No operation produces a particular output entity**

```text
entity Receipt {
  order_id: OrderId
  total: Money
  issued_at: DateTime
}

// No operation has output: Receipt. The entity is defined but never emitted.
```

### 1.3 Reachability errors

Reachability errors mean some part of the spec describes situations that can never actually occur
during execution.

**1.3.1 Operation can never be invoked (requires always false)**

```text
operation Refund {
  input: order_id: OrderId

  requires:
    orders[order_id].status = SHIPPED
    and orders[order_id].status = CANCELLED
}
```

No order can be simultaneously SHIPPED and CANCELLED. The conjunction is always false. The operation
is dead code in the spec.

**1.3.2 State unreachable from initial state**

If the initial state is the empty state (no entries in any relation), and the only operation that
writes to `archive` requires entries in `archive` to exist:

```text
state {
  archive: Document -> lone DateTime
}

operation ArchiveDocument {
  input: doc: Document
  requires: doc in archive              // BUG: should be doc NOT in archive
  ensures: archive'[doc] = now()
}
```

Nothing can ever enter the archive because the only write operation requires the document to already
be archived. The archive is permanently empty.

**1.3.3 State machine deadlock**

```text
operation CreateOrder { ensures: orders'[id].status = PENDING }
operation PayOrder    { requires: status = PENDING, ensures: status' = PAID }
operation ShipOrder   { requires: status = PAID, ensures: status' = SHIPPED }
// SHIPPED is terminal -- no outgoing transitions. This is intentional.

operation HoldOrder   { requires: status = REVIEW, ensures: status' = HELD }
// HELD has no outgoing transitions AND no operation transitions INTO REVIEW.
```

REVIEW is unreachable, HELD is unreachable, and HoldOrder is dead. Additionally, if an operation
erroneously transitions into REVIEW, HELD becomes a deadlock because no operation exits it.

**1.3.4 Invariant makes a state component useless**

```text
state {
  discounts: Customer -> lone Percent
}

invariant: discounts = {}     // no customer ever has a discount
```

The invariant forces the discounts relation to always be empty. Any operation that reads or writes
discounts is either dead or will violate the invariant.

### 1.4 Type errors

Type errors are syntactic or structural mismatches in how spec elements reference each other.

**1.4.1 Relation multiplicity mismatch**

```text
state {
  owner: Car -> one Person         // each car has exactly one owner
}

operation TransferOwnership {
  input: car: Car, new_owner: Person
  ensures: owner'[car] = {new_owner, old_owner}  // set of two people
}
```

The `one` multiplicity means `owner[car]` is exactly one Person, but the ensures clause assigns a
set of two Persons. This is a type/multiplicity mismatch.

**1.4.2 Type incompatibility in expressions**

```text
entity Product {
  price: Money
  name: String
}

invariant: all p in products | p.price > p.name   // comparing Money to String
```

**1.4.3 Undeclared entities or fields**

```text
operation Checkout {
  input: cart_id: CartId
  ensures: receipt.total = carts[cart_id].total_price
}
// "receipt" is not declared as output.
// "total_price" is not a declared field of Cart.
```

**1.4.4 Primed variable used outside ensures clause**

```text
invariant: all c in store' | isValidURI(store'[c].value)
// store' (post-state) has no meaning in a global invariant,
// which describes a static property of any reachable state.
```

**1.4.5 Scope errors in quantifiers**

```text
operation Shorten {
  input: url: LongURL
  output: code: ShortCode

  ensures:
    all c in store | store'[c] = store[c]    // frame: all EXISTING entries preserved
    store'[code] = url
    all x in store' | isValidURI(store'[x].value)  // uses x but also references code
}
```

If the quantifier variable shadows the output variable name, confusion arises. More generally, any
free variable in an ensures clause that is neither an input, an output, a state component, nor a
bound quantifier variable is an error.

### 1.5 Semantic warnings (not errors but likely bugs)

**1.5.1 Operation has empty postcondition**

```text
operation Ping {
  input: none
  output: msg: String
  requires: true
  ensures: true          // does literally nothing observable
}
```

An ensures clause of `true` means the operation is allowed to do anything or nothing. This is almost
certainly a mistake, the author forgot to write the postcondition.

**1.5.2 Invariant is trivially true**

```text
invariant: all c in store | c = c
```

This is a tautology. It holds for every state, so it provides no constraint. Likely the author meant
to write something more specific.

**1.5.3 Requires clause is trivially true**

```text
operation Delete {
  input: code: ShortCode
  requires: true                    // accepts every input unconditionally
  ensures: code not in store'
}
```

If `code` is not in the store, the operation tries to delete a non-existent entry. The author likely
forgot to write `requires: code in store`.

**1.5.4 Two operations are functionally identical**

```text
operation GetUrl {
  input: code: ShortCode
  output: url: LongURL
  requires: code in store
  ensures: url = store[code] and store' = store
}

operation Resolve {
  input: code: ShortCode
  output: url: LongURL
  requires: code in store
  ensures: url = store[code] and store' = store
}
```

The two operations have identical signatures, preconditions, and postconditions. One is redundant.

**1.5.5 Invariant is subsumed by another invariant**

```text
invariant: all c in store | len(c.value) >= 6
invariant: all c in store | len(c.value) >= 1
```

The second invariant is strictly weaker than the first. If the first holds, the second automatically
holds. This is not wrong but suggests redundancy.

**1.5.6 Precondition is stronger than necessary**

```text
operation Resolve {
  input: code: ShortCode
  requires: code in store and len(code.value) >= 6

  ensures: url = store[code] and store' = store
}
```

If the entity invariant already guarantees `len(code.value) >= 6` for all ShortCodes in the store,
then the length check in the requires clause is redundant. The verification engine can detect this
and suggest simplification.
