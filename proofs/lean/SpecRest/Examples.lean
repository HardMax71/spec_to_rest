import SpecRest.IR
import SpecRest.Semantics

namespace SpecRest.Examples

open SpecRest

def safeCounterEnv : Env := [("count", .vInt 0)]

def safeCounterInvariant : Expr :=
  .intCmp .ge (.ident "count") (.intLit 0)

example :
    eval Schema.empty safeCounterEnv safeCounterInvariant
      = some (.vBool true) := by
  native_decide

example :
    eval Schema.empty [("count", .vInt (-1))] safeCounterInvariant
      = some (.vBool false) := by
  native_decide

example :
    eval Schema.empty []
        (.boolBin .and (.boolLit true) (.boolLit true))
      = some (.vBool true) := by
  decide

example :
    eval Schema.empty []
        (.boolBin .implies (.boolLit false) (.boolLit false))
      = some (.vBool true) := by
  decide

example :
    eval Schema.empty []
        (.unNot (.boolLit false))
      = some (.vBool true) := by
  decide

example :
    eval Schema.empty []
        (.unNeg (.intLit 5))
      = some (.vInt (-5)) := by
  decide

example :
    eval Schema.empty []
        (.letIn "x" (.intLit 7)
          (.intCmp .eq (.ident "x") (.intLit 7)))
      = some (.vBool true) := by
  native_decide

def colorSchema : Schema :=
  { enums := [{ name := "Color", members := ["Red", "Green", "Blue"] }] }

example :
    eval colorSchema [] (.enumAccess "Color" "Green")
      = some (.vEnum "Color" "Green") := by
  native_decide

example :
    eval colorSchema [] (.enumAccess "Color" "Magenta") = none := by
  native_decide

def safeCounterIR : ServiceIR :=
  { name       := "SafeCounter"
    enums      := []
    state      := { fields := [{ name := "count", isInt := true }] }
    invariants := [{ name := "non_negative", body := safeCounterInvariant }]
    operations := []
  }

example :
    evalInvariant Schema.empty safeCounterEnv
        (safeCounterIR.invariants.head!)
      = some true := by
  native_decide

end SpecRest.Examples
