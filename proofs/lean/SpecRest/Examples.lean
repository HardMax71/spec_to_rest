import SpecRest.IR
import SpecRest.Semantics
import SpecRest.Lemmas

namespace SpecRest.Examples

open SpecRest

/-! ## Closed-evaluation sanity examples (boolean / arithmetic / `Let`) -/

example :
    eval Schema.empty State.empty []
        (.boolBin .and (.boolLit true) (.boolLit true))
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.boolBin .implies (.boolLit false) (.boolLit false))
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.unNot (.boolLit false))
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.unNeg (.intLit 5))
      = some (.vInt (-5)) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.cmp .eq (.intLit 7) (.intLit 7))
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.cmp .lt (.intLit 3) (.intLit 5))
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty State.empty []
        (.letIn "x" (.intLit 7)
          (.cmp .eq (.ident "x") (.intLit 7)))
      = some (.vBool true) := by
  native_decide

/-! ## Enums + polymorphic equality -/

def colorSchema : Schema :=
  { enums := [{ name := "Color", members := ["Red", "Green", "Blue"] }]
    entities := [] }

example :
    eval colorSchema State.empty [] (.enumAccess "Color" "Green")
      = some (.vEnum "Color" "Green") := by
  native_decide

example :
    eval colorSchema State.empty [] (.enumAccess "Color" "Magenta") = none := by
  native_decide

example :
    eval colorSchema State.empty []
        (.cmp .eq (.enumAccess "Color" "Red") (.enumAccess "Color" "Red"))
      = some (.vBool true) := by
  native_decide

example :
    eval colorSchema State.empty []
        (.cmp .eq (.enumAccess "Color" "Red") (.enumAccess "Color" "Blue"))
      = some (.vBool false) := by
  native_decide

/-! ## Universal quantification over a finite enum domain -/

example :
    eval colorSchema State.empty []
        (.forallEnum "c" "Color"
          (.cmp .eq (.ident "c") (.ident "c")))
      = some (.vBool true) := by
  native_decide

example :
    eval colorSchema State.empty []
        (.forallEnum "c" "Color"
          (.cmp .eq (.ident "c") (.enumAccess "Color" "Red")))
      = some (.vBool false) := by
  native_decide

/-! ## Entity-typed values + polymorphic equality -/

def usersSchema : Schema :=
  { enums := []
    entities := [{ name := "User", fields := [{ name := "id", ty := .intT }] }] }

example :
    eval usersSchema State.empty
        [("u", .vEntity "User" "u1"), ("v", .vEntity "User" "u1")]
        (.cmp .eq (.ident "u") (.ident "v"))
      = some (.vBool true) := by
  native_decide

example :
    eval usersSchema State.empty
        [("u", .vEntity "User" "u1"), ("v", .vEntity "User" "u2")]
        (.cmp .eq (.ident "u") (.ident "v"))
      = some (.vBool false) := by
  native_decide

/-! ## State-relation membership (`In` over relation domains) -/

def membersState : State :=
  { scalars := []
    relations := [("active", [.vEntity "User" "u1", .vEntity "User" "u2"])]
    lookups := []
    entityFields := [] }

example :
    eval usersSchema membersState [("u", .vEntity "User" "u1")]
        (.member (.ident "u") "active")
      = some (.vBool true) := by
  native_decide

example :
    eval usersSchema membersState [("u", .vEntity "User" "u3")]
        (.member (.ident "u") "active")
      = some (.vBool false) := by
  native_decide

/-! Membership against an unknown relation reports `none`, not `false`. -/
example :
    eval usersSchema membersState [("u", .vEntity "User" "u1")]
        (.member (.ident "u") "nonexistent") = none := by
  native_decide

/-! ## safe_counter — research doc §8.2 acceptance fixture -/

/-- IR encoding of `count >= 0`, mirroring `fixtures/spec/safe_counter.spec`. -/
def safeCounterInvariantBody : Expr :=
  .cmp .ge (.ident "count") (.intLit 0)

def safeCounterInvariant : InvariantDecl :=
  { name := "countNonNegative", body := safeCounterInvariantBody }

/-- The full safe_counter service. M_L.1 cannot express `count' = count + 1`
    (`Prime` is M_L.2), so operation `ensures` lists are stubbed with `true`. -/
def safeCounterIR : ServiceIR :=
  { name := "SafeCounter"
    enums := []
    entities := []
    state := { scalars := [{ name := "count", ty := .intT }], relations := [] }
    invariants := [safeCounterInvariant]
    operations :=
      [{ name := "Increment"
         requires := [.boolLit true]
         ensures := [.boolLit true] }
      ,{ name := "Decrement"
         requires := [.cmp .gt (.ident "count") (.intLit 0)]
         ensures := [.boolLit true] }] }

/-- Hand-built initial state for the M_L.1 acceptance check: `count = 0`. -/
def safeCounterInitialState : State :=
  { scalars := [("count", .vInt 0)], relations := [], lookups := [], entityFields := [] }

/-- §8.2 acceptance: the `count ≥ 0` invariant evaluates to `True` under the
    hand-built initial state. Stated as a named theorem (not an `example`) so
    later milestones can rely on the lemma symbol. -/
theorem safeCounter_invariant_holds_initially :
    evalInvariant Schema.empty safeCounterInitialState []
        safeCounterInvariant
      = some true := by
  native_decide

/-- The `Decrement` precondition fails when `count = 0`. -/
theorem safeCounter_decrement_disabled_at_zero :
    operationEnabled Schema.empty safeCounterInitialState []
        safeCounterIR.operations[1]!
      = some false := by
  native_decide

/-- `Increment` is enabled regardless of state — closed at the initial state. -/
theorem safeCounter_increment_enabled_initially :
    operationEnabled Schema.empty safeCounterInitialState []
        safeCounterIR.operations[0]!
      = some true := by
  native_decide

end SpecRest.Examples
