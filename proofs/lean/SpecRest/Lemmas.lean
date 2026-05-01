import SpecRest.IR
import SpecRest.Semantics

namespace SpecRest

variable (s : Schema) (st : State) (env : Env)

/-! ## Per-operator denotation equations.
    Each lemma states that `eval` on a given `Expr` constructor reduces to the
    semantic-domain expression that defines the case. M_L.2's translator
    soundness theorem will reuse these as rewriting steps in a structural
    induction over `Expr`. -/

theorem eval_boolLit (b : Bool) :
    eval s st env (.boolLit b) = some (.vBool b) := by
  simp [eval]

theorem eval_intLit (n : Int) :
    eval s st env (.intLit n) = some (.vInt n) := by
  simp [eval]

theorem eval_ident (x : String) :
    eval s st env (.ident x) =
      (match Env.lookup env x with
        | some v => some v
        | none   => st.lookupScalar x) := by
  simp [eval]

theorem eval_unNot (e : Expr) :
    eval s st env (.unNot e) =
      (match eval s st env e with
        | some (.vBool b) => some (.vBool (!b))
        | _               => none) := by
  simp [eval]

theorem eval_unNeg (e : Expr) :
    eval s st env (.unNeg e) =
      (match eval s st env e with
        | some (.vInt n) => some (.vInt (-n))
        | _              => none) := by
  simp [eval]

theorem eval_boolBin (op : BoolBinOp) (l r : Expr) :
    eval s st env (.boolBin op l r) =
      (match eval s st env l, eval s st env r with
        | some (.vBool a), some (.vBool b) => some (.vBool (evalBoolBin op a b))
        | _, _                             => none) := by
  simp [eval]

theorem eval_cmp (op : CmpOp) (l r : Expr) :
    eval s st env (.cmp op l r) =
      evalCmp op (eval s st env l) (eval s st env r) := by
  simp [eval]

theorem eval_letIn (x : String) (v body : Expr) :
    eval s st env (.letIn x v body) =
      (match eval s st env v with
        | some r => eval s st ((x, r) :: env) body
        | none   => none) := by
  simp [eval]

theorem eval_enumAccess (en mem : String) :
    eval s st env (.enumAccess en mem) =
      (match s.lookupEnum en with
        | some d =>
            if d.members.contains mem
              then some (.vEnum en mem)
              else none
        | none => none) := by
  simp [eval]

theorem eval_member (elem : Expr) (relName : String) :
    eval s st env (.member elem relName) =
      (match eval s st env elem with
        | some v =>
            (match st.relationDomain relName with
              | some dom => some (.vBool (dom.contains v))
              | none     => none)
        | none => none) := by
  simp [eval]

theorem eval_forallEnum (var en : String) (body : Expr) :
    eval s st env (.forallEnum var en body) =
      (match s.lookupEnum en with
        | some d => evalForallEnum s st env var en d.members body
        | none   => none) := by
  simp [eval]

theorem evalForallEnum_nil (var en : String) (body : Expr) :
    evalForallEnum s st env var en [] body = some (.vBool true) := by
  simp [evalForallEnum]

theorem evalForallEnum_cons (var en m : String) (rest : List String) (body : Expr) :
    evalForallEnum s st env var en (m :: rest) body =
      (match eval s st ((var, .vEnum en m) :: env) body with
        | some (.vBool b) =>
            (match evalForallEnum s st env var en rest body with
              | some (.vBool acc) => some (.vBool (b && acc))
              | _                 => none)
        | _ => none) := by
  simp [evalForallEnum]

/-! ## Helper-function equations -/

theorem evalBoolBin_and (a b : Bool) :
    evalBoolBin .and a b = (a && b) := rfl

theorem evalBoolBin_or (a b : Bool) :
    evalBoolBin .or a b = (a || b) := rfl

theorem evalBoolBin_implies (a b : Bool) :
    evalBoolBin .implies a b = (!a || b) := rfl

theorem evalBoolBin_iff (a b : Bool) :
    evalBoolBin .iff a b = (a == b) := rfl

theorem evalCmp_int_lt (a b : Int) :
    evalCmp .lt (some (.vInt a)) (some (.vInt b))
      = some (.vBool (decide (a < b))) := rfl

theorem evalCmp_int_le (a b : Int) :
    evalCmp .le (some (.vInt a)) (some (.vInt b))
      = some (.vBool (decide (a ≤ b))) := rfl

theorem evalCmp_int_gt (a b : Int) :
    evalCmp .gt (some (.vInt a)) (some (.vInt b))
      = some (.vBool (decide (a > b))) := rfl

theorem evalCmp_int_ge (a b : Int) :
    evalCmp .ge (some (.vInt a)) (some (.vInt b))
      = some (.vBool (decide (a ≥ b))) := rfl

theorem evalCmp_eq_value (a b : Value) :
    evalCmp .eq (some a) (some b) = some (.vBool (a == b)) := rfl

theorem evalCmp_neq_value (a b : Value) :
    evalCmp .neq (some a) (some b) = some (.vBool (a != b)) := rfl

/-! ## Aggregate evaluation equations -/

theorem evalRequiresAll_nil :
    evalRequiresAll s st env [] = some true := by
  simp [evalRequiresAll]

theorem evalRequiresAll_cons (r : Expr) (rs : List Expr) :
    evalRequiresAll s st env (r :: rs) =
      (match (eval s st env r).bind asBool with
        | some true  => evalRequiresAll s st env rs
        | some false => some false
        | none       => none) := by
  simp [evalRequiresAll]

theorem evalEnsuresAll_nil :
    evalEnsuresAll s st env [] = some true := by
  simp [evalEnsuresAll]

theorem evalEnsuresAll_cons (r : Expr) (rs : List Expr) :
    evalEnsuresAll s st env (r :: rs) =
      (match (eval s st env r).bind asBool with
        | some true  => evalEnsuresAll s st env rs
        | some false => some false
        | none       => none) := by
  simp [evalEnsuresAll]

theorem evalInvariant_def (inv : InvariantDecl) :
    evalInvariant s st env inv = (eval s st env inv.body).bind asBool := by
  simp [evalInvariant]

theorem operationEnabled_def (op : OperationDecl) :
    operationEnabled s st env op = evalRequiresAll s st env op.requires := by
  simp [operationEnabled]

theorem operationEnsures_def (op : OperationDecl) :
    operationEnsures s st env op = evalEnsuresAll s st env op.ensures := by
  simp [operationEnsures]

end SpecRest
