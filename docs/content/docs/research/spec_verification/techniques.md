---
title: "Techniques"
description: "SMT and bounded model checking, and which backend handles what"
---

## 2. Verification techniques

### 2.1 SAT/SMT checking (Z3-Based)

SMT (Satisfiability Modulo Theories) solvers like Z3 can check whether logical formulas over
integers, strings, arrays, and uninterpreted functions are satisfiable. We translate spec elements
to SMT-LIB formulas and use Z3 to check key properties.

#### What it catches

- Unsatisfiable invariants (Section 1.1.3)
- Invariant-postcondition inconsistencies (Section 1.1.1)
- Requires clause insufficiency (Section 1.2.1)
- Trivially true/false conditions (Section 1.5)

#### Core checks

1. **Invariant satisfiability.** Translate all invariants to a conjunction. Ask Z3: is the
   conjunction satisfiable? If UNSAT, no valid state exists.

2. **Invariant preservation.** For each operation O and invariant I, check:
   `I(state) AND O.requires(input, state) AND O.ensures(input, state, state', output) => I(state')`
   Negate the implication and ask Z3 for satisfiability. If SAT, the solver returns a counterexample
   showing how the operation violates the invariant.

3. **Requires sufficiency.** Check that the precondition is strong enough to make the postcondition
   achievable:
   `O.requires(input, state) => EXISTS state', output. O.ensures(input, state, state', output)` If
   this fails, the precondition allows inputs for which no valid post-state exists.

4. **Dead condition detection.** Check `O.requires(input, state)` for satisfiability on its own. If
   UNSAT, the operation can never be invoked.

#### Complete SMT-LIB translation for the URL shortener spec

```lisp
; ============================================================
; URL Shortener Spec -> SMT-LIB2 Translation
; ============================================================

; --- Sorts ---
(declare-sort ShortCode 0)
(declare-sort LongURL 0)

; --- Entity field accessors ---
(declare-fun sc_value (ShortCode) String)
(declare-fun lu_value (LongURL) String)

; --- State: store is a partial function ShortCode -> LongURL ---
; We model the store as a set of ShortCode (the domain) plus a function
; for the mapping.
(declare-fun store_domain (ShortCode) Bool)          ; pre-state domain membership
(declare-fun store_map (ShortCode) LongURL)          ; pre-state mapping
(declare-fun store_domain_post (ShortCode) Bool)     ; post-state domain membership
(declare-fun store_map_post (ShortCode) LongURL)     ; post-state mapping

; --- Entity Invariants ---
; invariant: len(sc.value) >= 6 and len(sc.value) <= 10
(define-fun shortcode_inv ((c ShortCode)) Bool
  (and (>= (str.len (sc_value c)) 6)
       (<= (str.len (sc_value c)) 10)))

; invariant: sc.value matches /^[a-zA-Z0-9]+$/
(define-fun shortcode_alphanum ((c ShortCode)) Bool
  (str.in_re (sc_value c) (re.+ (re.union
    (re.range "a" "z") (re.range "A" "Z") (re.range "0" "9")))))

; invariant: valid_uri(lu.value)
; We approximate valid_uri as: starts with "http://" or "https://"
(define-fun valid_uri ((u LongURL)) Bool
  (or (str.prefixof "http://" (lu_value u))
      (str.prefixof "https://" (lu_value u))))

; --- Global Invariant: all codes in store map to valid URIs ---
(declare-const global_inv_valid_urls Bool)
(assert (= global_inv_valid_urls
  (forall ((c ShortCode))
    (=> (store_domain c)
        (valid_uri (store_map c))))))

; --- Global Invariant: all codes in store satisfy entity invariants ---
(define-fun global_inv_codes () Bool
  (forall ((c ShortCode))
    (=> (store_domain c)
        (and (shortcode_inv c) (shortcode_alphanum c)))))

; --- Combined pre-state invariant ---
(define-fun all_invariants () Bool
  (and global_inv_valid_urls global_inv_codes))

; --- Combined post-state invariant ---
(define-fun all_invariants_post () Bool
  (and
    (forall ((c ShortCode))
      (=> (store_domain_post c)
          (valid_uri (store_map_post c))))
    (forall ((c ShortCode))
      (=> (store_domain_post c)
          (and (shortcode_inv c) (shortcode_alphanum c))))))

; ============================================================
; CHECK 1: Are the invariants satisfiable?
; ============================================================
; (push)
; (assert all_invariants)
; (check-sat)  ; expect SAT -- a valid state exists
; (pop)

; ============================================================
; CHECK 2: Does Shorten preserve invariants?
; ============================================================
; Operation Shorten:
;   input:  url: LongURL
;   output: code: ShortCode
;   requires: isValidURI(url)
;   ensures:  code not in pre(store)
;             store'[code] = url
;             for all other c: store'[c] = store[c]
;             code in store'

(declare-const input_url LongURL)
(declare-const output_code ShortCode)

; Pre-state invariants hold
(assert all_invariants)

; Precondition
(assert (valid_uri input_url))

; Postcondition: code was not in store
(assert (not (store_domain output_code)))

; Postcondition: store' = store + {output_code -> input_url}
(assert (store_domain_post output_code))
(assert (= (store_map_post output_code) input_url))

; Frame condition: everything else unchanged
(assert (forall ((c ShortCode))
  (=> (not (= c output_code))
      (and (= (store_domain_post c) (store_domain c))
           (= (store_map_post c) (store_map c))))))

; Now check: does the post-state invariant hold?
; We NEGATE the post-invariant and check for SAT.
; If SAT, the solver found a counterexample (invariant violation).
(assert (not all_invariants_post))

(check-sat)
; If SAT: VIOLATION FOUND. The model shows a counterexample.
;   The output_code might have sc_value of length < 6, violating shortcode_inv.
;   This is because the ensures clause does not constrain output_code's value length.
;
; If UNSAT: The operation provably preserves the invariant.

; (get-model)  ; in SAT case, shows the counterexample

; ============================================================
; CHECK 3: Does Resolve preserve invariants?
; ============================================================
; Resolve is read-only (store' = store), so it trivially preserves
; invariants. The check is:
;   all_invariants AND store' = store => all_invariants_post
; This is always UNSAT (when negated), confirming preservation.

; ============================================================
; CHECK 4: Does Delete preserve invariants?
; ============================================================
; Delete removes an entry. The frame is: store' = store - {code}.
; Since we only remove, and invariants are universally quantified
; over store contents, removing an entry preserves them. The check
; confirms this automatically.
```

#### What the Z3 output looks like

For Check 2 (Shorten), Z3 returns SAT with a model like:

```text
sat
(model
  (define-fun output_code () ShortCode ShortCode!val!0)
  (define-fun sc_value ((x!0 ShortCode)) String
    (ite (= x!0 ShortCode!val!0) "ab" "abcdef"))
  (define-fun input_url () LongURL LongURL!val!0)
  (define-fun lu_value ((x!0 LongURL)) String
    "https://example.com")
)
```

This counterexample shows that `output_code` has `sc_value = "ab"` (length 2), which violates the
entity invariant. The ensures clause must be strengthened.

### 2.2 Alloy-based bounded model checking

Alloy uses SAT-based bounded model checking via the Kodkod solver. We translate the spec to Alloy
and use the Alloy Analyzer to search for counterexamples within finite bounds.

#### What it catches

- All inconsistency errors (Sections 1.1.x)
- Reachability errors within bounds (Section 1.3)
- Cardinality constraint violations (Section 1.1.5)
- State machine properties within bounded traces

#### Strengths over SMT

- Alloy's relational logic maps naturally to our spec's data model
- Bounded search is decidable and terminates
- Alloy's visualizer produces human-readable counterexamples
- Handles transitive closure and relational composition natively

#### Limitations

- Bounded: only checks up to N instances (misses bugs requiring N+1)
- No string operations (cannot check `len(value) >= 6` directly)
- No arithmetic beyond small integers

#### Complete Alloy translation for the URL shortener spec

```text
-- ============================================================
-- URL Shortener Spec -> Alloy Translation
-- ============================================================

module UrlShortener

open util/ordering[State] as StateOrder

-- --- Entities ---

sig ShortCode {
  value: one Value
}

sig LongURL {
  url_value: one Value
}

-- Abstract value type (Alloy lacks native strings; we model lengths abstractly)
sig Value {
  length: one Int
}

-- --- State ---

sig State {
  store: ShortCode -> lone LongURL
}

-- --- Entity Invariants ---

-- All ShortCodes have value length between 6 and 10
fact ShortCodeInvariant {
  all c: ShortCode | c.value.length >= 6 and c.value.length <= 10
}

-- All LongURLs are "valid" (modeled as having positive length)
fact LongURLInvariant {
  all u: LongURL | u.url_value.length > 0
}

-- --- Global Invariants ---

-- All stored URLs are valid (subsumed by entity invariant, but stated explicitly)
fact GlobalValidURLs {
  all s: State, c: s.store.LongURL |
    let u = s.store[c] |
      u.url_value.length > 0
}

-- --- Initial State ---

fact InitialState {
  let s0 = StateOrder/first |
    no s0.store
}

-- --- Operations ---

-- Shorten: adds a new mapping
pred Shorten[s, s': State, url: LongURL, code: ShortCode] {
  -- Precondition: code is fresh
  code not in s.store.LongURL

  -- Postcondition: store gains exactly this mapping
  s'.store = s.store + code -> url

  -- Frame: nothing else changes (implicit in the equality above)
}

-- Resolve: reads a mapping (no state change)
pred Resolve[s, s': State, code: ShortCode, url: LongURL] {
  -- Precondition: code exists
  code in s.store.LongURL

  -- Postcondition: url is the stored value
  url = s.store[code]

  -- Frame: state unchanged
  s'.store = s.store
}

-- Delete: removes a mapping
pred Delete[s, s': State, code: ShortCode] {
  -- Precondition: code exists
  code in s.store.LongURL

  -- Postcondition: code removed
  s'.store = s.store - code -> s.store[code]
}

-- --- Transition System ---

fact Traces {
  all s: State - StateOrder/last |
    let s' = s.next |
      (some url: LongURL, code: ShortCode | Shorten[s, s', url, code])
      or
      (some code: ShortCode, url: LongURL | Resolve[s, s', code, url])
      or
      (some code: ShortCode | Delete[s, s', code])
}

-- ============================================================
-- Verification Commands
-- ============================================================

-- Check 1: Can a valid trace exist?
-- (Run finds an instance if one exists within bounds)
run TraceExists {} for 5 but 4 State, 5 ShortCode, 5 LongURL, 5 Value, 5 Int

-- Check 2: Does Shorten preserve the store integrity?
-- (Assert negation; if counterexample found, the property is violated)
assert ShortenPreservesIntegrity {
  all s, s': State, url: LongURL, code: ShortCode |
    Shorten[s, s', url, code] implies
      (all c: s'.store.LongURL | c.value.length >= 6)
}
check ShortenPreservesIntegrity for 5 but 4 State, 5 ShortCode, 5 LongURL, 5 Value, 6 Int

-- Check 3: No deadlocks (from any non-empty state, at least one op is enabled)
assert NoDeadlock {
  all s: State - StateOrder/last |
    (some url: LongURL, code: ShortCode | code not in s.store.LongURL)
    or
    (some code: ShortCode | code in s.store.LongURL)
}
check NoDeadlock for 5 but 4 State, 5 ShortCode, 5 LongURL, 5 Value, 6 Int

-- Check 4: Delete undoes Shorten
assert DeleteUndoesShorten {
  all s1, s2, s3: State, url: LongURL, code: ShortCode |
    (Shorten[s1, s2, url, code] and Delete[s2, s3, code])
      implies s3.store = s1.store
}
check DeleteUndoesShorten for 5 but 4 State, 5 ShortCode, 5 LongURL, 5 Value, 6 Int
```

#### Alloy analyzer output for a valid spec

```text
Executing "Check ShortenPreservesIntegrity for 5"
   Solver=sat4j Bitwidth=6 MaxSeq=5 Symmetry=20
   12,345 vars. 678 primary vars. 23,456 clauses. 150ms.
   No counterexample found. Assertion may be valid.
```

#### Alloy analyzer output for an invalid spec (when shortcode invariant is removed)

```text
Executing "Check ShortenPreservesIntegrity for 5"
   Solver=sat4j Bitwidth=6 MaxSeq=5 Symmetry=20
   12,345 vars. 678 primary vars. 23,456 clauses. 85ms.
   Counterexample found.

   State$0:
     store = { ShortCode$0 -> LongURL$0 }
   State$1:
     store = { ShortCode$0 -> LongURL$0, ShortCode$1 -> LongURL$1 }

   ShortCode$1.value = Value$2
   Value$2.length = 2          <-- VIOLATION: length 2 < 6

   Shorten[State$0, State$1, LongURL$1, ShortCode$1]
```

### 2.3 Complete Alloy translation for e-commerce order service

```text
-- ============================================================
-- E-Commerce Order Service -> Alloy Translation
-- ============================================================

module OrderService

open util/ordering[State] as StateOrder

abstract sig OrderStatus {}
one sig PENDING, PAID, SHIPPED, DELIVERED, CANCELLED extends OrderStatus {}

sig OrderId {}
sig ProductId {}
sig CustomerId {}

sig OrderItem {
  product: one ProductId,
  quantity: one Int
} {
  quantity > 0
}

sig Order {
  id: one OrderId,
  customer: one CustomerId,
  items: set OrderItem,
  status: one OrderStatus,
  total: one Int
} {
  total >= 0
  #items > 0
}

sig State {
  orders: set Order,
  order_status: Order -> one OrderStatus,
  inventory: ProductId -> one Int
}

-- Inventory is never negative
fact InventoryNonNegative {
  all s: State, p: ProductId |
    let qty = s.inventory[p] |
      qty >= 0
}

-- Initial state: no orders, all inventory positive
fact InitialState {
  let s0 = StateOrder/first |
    no s0.orders
}

-- --- Operations ---

pred PlaceOrder[s, s': State, o: Order] {
  -- Pre: order not already placed
  o not in s.orders

  -- Pre: sufficient inventory for all items
  all item: o.items |
    s.inventory[item.product] >= item.quantity

  -- Post: order added with PENDING status
  s'.orders = s.orders + o
  s'.order_status = s.order_status ++ (o -> PENDING)

  -- Post: inventory decremented
  all p: ProductId |
    (some item: o.items | item.product = p)
      implies s'.inventory[p] = sub[s.inventory[p],
        (sum item: o.items & product.p | item.quantity)]
      else s'.inventory[p] = s.inventory[p]
}

pred CancelOrder[s, s': State, o: Order] {
  -- Pre: order exists and is PENDING
  o in s.orders
  s.order_status[o] = PENDING

  -- Post: status changed to CANCELLED
  s'.orders = s.orders
  s'.order_status = s.order_status ++ (o -> CANCELLED)

  -- Post: inventory restored
  all p: ProductId |
    (some item: o.items | item.product = p)
      implies s'.inventory[p] = add[s.inventory[p],
        (sum item: o.items & product.p | item.quantity)]
      else s'.inventory[p] = s.inventory[p]
}

pred PayOrder[s, s': State, o: Order] {
  o in s.orders
  s.order_status[o] = PENDING
  -- Post: order becomes PAID, everything else unchanged
  s'.orders = s.orders
  s'.order_status = s.order_status ++ (o -> PAID)
  s'.inventory = s.inventory
}

pred ShipOrder[s, s': State, o: Order] {
  o in s.orders
  s.order_status[o] = PAID
  s'.orders = s.orders
  s'.order_status = s.order_status ++ (o -> SHIPPED)
  s'.inventory = s.inventory
}

-- Verification: PlaceOrder preserves non-negative inventory
assert PlaceOrderPreservesInventory {
  all s, s': State, o: Order |
    PlaceOrder[s, s', o] implies
      (all p: ProductId | s'.inventory[p] >= 0)
}
check PlaceOrderPreservesInventory for 4 but 3 State, 4 Order, 3 ProductId, 5 Int

-- Verification: An order can go from PENDING to DELIVERED
-- (reachability -- run to see if a trace exists)
run CanDeliver {
  some s: State | some o: s.orders | o.status = DELIVERED
} for 6 but 5 State, 3 Order, 2 ProductId, 6 Int
```

### 2.4 Quint / TLA+ model checking (for temporal properties)

Quint is a modern syntax for TLA+ that reads like TypeScript. We use it for temporal properties:
liveness (something good eventually happens), deadlock freedom (system can always make progress),
and reachability.

#### What it catches

- State machine deadlocks (Section 1.3.3)
- Unreachable states (Section 1.3.2)
- Liveness violations (e.g., "an order is eventually delivered or cancelled")
- Fairness violations (e.g., "the system does not starve an operation")

#### Complete Quint translation for an order state machine spec

```typescript
// ============================================================
// Order State Machine -> Quint Translation
// ============================================================

module OrderStateMachine {

  // --- Types ---
  type OrderId = str
  type ProductId = str
  type CustomerId = str

  type OrderStatus =
    | PENDING
    | PAID
    | SHIPPED
    | DELIVERED
    | CANCELLED

  type OrderItem = {
    product: ProductId,
    quantity: int
  }

  type Order = {
    id: OrderId,
    customer: CustomerId,
    items: List[OrderItem],
    status: OrderStatus,
    total: int
  }

  // --- State ---
  var orders: OrderId -> Order
  var inventory: ProductId -> int

  // --- Initial state ---
  action init = all {
    orders' = Map(),
    inventory' = Map("prod1" -> 100, "prod2" -> 50, "prod3" -> 200),
  }

  // --- Operations ---

  action placeOrder(id: OrderId, customer: CustomerId, items: List[OrderItem], total: int): bool = all {
    not(orders.has(id)),
    items.length() > 0,
    total > 0,
    // Check inventory
    items.forall(item => inventory.get(item.product) >= item.quantity),
    // Update state
    orders' = orders.put(id, {
      id: id,
      customer: customer,
      items: items,
      status: PENDING,
      total: total
    }),
    // Decrement inventory
    inventory' = items.foldl(inventory, (inv, item) =>
      inv.put(item.product, inv.get(item.product) - item.quantity)
    ),
  }

  action payOrder(id: OrderId): bool = all {
    orders.has(id),
    orders.get(id).status == PENDING,
    orders' = orders.put(id, { ...orders.get(id), status: PAID }),
    inventory' = inventory,
  }

  action shipOrder(id: OrderId): bool = all {
    orders.has(id),
    orders.get(id).status == PAID,
    orders' = orders.put(id, { ...orders.get(id), status: SHIPPED }),
    inventory' = inventory,
  }

  action deliverOrder(id: OrderId): bool = all {
    orders.has(id),
    orders.get(id).status == SHIPPED,
    orders' = orders.put(id, { ...orders.get(id), status: DELIVERED }),
    inventory' = inventory,
  }

  action cancelOrder(id: OrderId): bool = all {
    orders.has(id),
    orders.get(id).status == PENDING,
    // Restore inventory
    val items = orders.get(id).items
    orders' = orders.put(id, { ...orders.get(id), status: CANCELLED }),
    inventory' = items.foldl(inventory, (inv, item) =>
      inv.put(item.product, inv.get(item.product) + item.quantity)
    ),
  }

  // --- Step: nondeterministic choice of operation ---
  action step = any {
    nondet id = oneOf(Set("o1", "o2", "o3"))
    nondet cust = oneOf(Set("c1", "c2"))
    nondet items = oneOf(Set(
      [{ product: "prod1", quantity: 1 }],
      [{ product: "prod2", quantity: 2 }],
    ))
    nondet total = oneOf(Set(10, 20, 50))
    placeOrder(id, cust, items, total),

    nondet id = oneOf(Set("o1", "o2", "o3"))
    payOrder(id),

    nondet id = oneOf(Set("o1", "o2", "o3"))
    shipOrder(id),

    nondet id = oneOf(Set("o1", "o2", "o3"))
    deliverOrder(id),

    nondet id = oneOf(Set("o1", "o2", "o3"))
    cancelOrder(id),
  }

  // ============================================================
  // Temporal Properties
  // ============================================================

  // Safety: inventory is never negative
  val inventoryNonNegative: bool =
    inventory.keys().forall(p => inventory.get(p) >= 0)

  // Safety: order status transitions are valid
  //   PENDING -> PAID, PENDING -> CANCELLED
  //   PAID -> SHIPPED
  //   SHIPPED -> DELIVERED
  // No backwards transitions
  val validTransitions: bool =
    orders.keys().forall(id =>
      val order = orders.get(id)
      order.status != DELIVERED or order.status != CANCELLED
      // (terminal states have no further transitions)
    )

  // Deadlock freedom: at least one operation is always enabled
  // (Either we can place a new order, or we can advance an existing one)
  temporal deadlockFree = always(enabled(step))

  // Liveness: every PENDING order is eventually PAID, DELIVERED, or CANCELLED
  // (requires fairness assumption on step)
  temporal eventualResolution =
    orders.keys().forall(id =>
      orders.get(id).status == PENDING implies
        eventually(
          orders.get(id).status == PAID or
          orders.get(id).status == DELIVERED or
          orders.get(id).status == CANCELLED
        )
    )

  // Ordering: an order must be PAID before it can be SHIPPED
  // Track payment history using an auxiliary variable
  var was_paid: OrderId -> bool

  // (In payOrder action, set was_paid' = was_paid.put(id, true))

  temporal payBeforeShip =
    always(orders.keys().forall(id =>
      orders.get(id).status == SHIPPED implies was_paid.get(id)
    ))
}
```

#### Running the Quint model checker

```bash
$ quint run OrderStateMachine.qnt --max-steps=20 --max-samples=10000

# Check safety invariant
$ quint verify OrderStateMachine.qnt --invariant=inventoryNonNegative --max-steps=15

[ok] inventoryNonNegative satisfied in 10000 traces (max depth 15).

# Check temporal property
$ quint verify OrderStateMachine.qnt --temporal=deadlockFree --max-steps=15

[ok] deadlockFree satisfied in 10000 traces (max depth 15).

# If a violation is found:
$ quint verify OrderStateMachine.qnt --invariant=inventoryNonNegative --max-steps=15

[violation] inventoryNonNegative violated after 3 steps.
Trace:
  State 0: { orders: {}, inventory: { "prod1": 1 } }
  Step 1 (placeOrder): { orders: { "o1": { status: PENDING, items: [{ product: "prod1", quantity: 1 }] } },
                         inventory: { "prod1": 0 } }
  Step 2 (placeOrder): FAILED -- inventory for "prod1" is 0, but quantity 1 requested
  // The precondition should prevent this, but if it doesn't, the invariant catches it.
```

### 2.5 Quint translation for the URL shortener

```typescript
// ============================================================
// URL Shortener -> Quint Translation
// ============================================================

module UrlShortener {

  type ShortCode = str
  type LongURL = str

  var store: ShortCode -> LongURL
  var created_at: ShortCode -> int  // timestamp as integer

  // --- Helpers ---
  pure def validCode(c: ShortCode): bool =
    c.length() >= 6 and c.length() <= 10

  pure def validUri(u: LongURL): bool =
    u.length() > 0  // simplified; real check would use regex

  // --- Initial state ---
  action init = all {
    store' = Map(),
    created_at' = Map(),
  }

  // --- Operations ---

  action shorten(url: LongURL, code: ShortCode, timestamp: int): bool = all {
    // Precondition
    validUri(url),
    not(store.has(code)),
    validCode(code),
    // Postcondition
    store' = store.put(code, url),
    created_at' = created_at.put(code, timestamp),
  }

  action resolve(code: ShortCode): bool = all {
    // Precondition
    store.has(code),
    // Frame: no state change
    store' = store,
    created_at' = created_at,
  }

  action delete(code: ShortCode): bool = all {
    // Precondition
    store.has(code),
    // Postcondition
    store' = store.mapRemove(code),
    created_at' = created_at.mapRemove(code),
  }

  action step = any {
    nondet url = oneOf(Set("https://example.com", "https://test.org", "invalid"))
    nondet code = oneOf(Set("abcdef", "ghijkl", "ab", "mnopqrstuv"))
    nondet ts = oneOf(Set(1000, 2000, 3000))
    shorten(url, code, ts),

    nondet code = oneOf(Set("abcdef", "ghijkl", "ab"))
    resolve(code),

    nondet code = oneOf(Set("abcdef", "ghijkl"))
    delete(code),
  }

  // --- Invariants ---

  val allStoredUrlsValid: bool =
    store.keys().forall(c => validUri(store.get(c)))

  val allCodesValid: bool =
    store.keys().forall(c => validCode(c))

  val storeAndCreatedAtConsistent: bool =
    store.keys().forall(c => created_at.has(c)) and
    created_at.keys().forall(c => store.has(c))

  // Deadlock freedom: we can always do something
  // (we can always shorten with a fresh code, or resolve/delete existing ones,
  //  or at worst the store is empty and we can shorten)
  temporal alwaysProgress = always(enabled(step))
}
```
