import SpecRest.IR
import SpecRest.Semantics

namespace SpecRest

variable (s : Schema) (st : State) (env : Env)

/-! ## Per-operator denotation lemmas.
    Each lemma characterizes `eval` on one `Expr` constructor. M_L.2's
    translator-soundness theorem reuses these as rewriting / hypothesis
    steps in a structural induction over `Expr`. Match-with-wildcard
    equations don't close definitionally for mutually-recursive `eval`
    (the wf-fix wrapper survives `simp`), so we state these as
    case-specific characterizations driven by hypotheses on the
    sub-expression results. -/

/-! ### Atoms and identifiers -/

theorem eval_boolLit (b : Bool) :
    eval s st env (.boolLit b) = some (.vBool b) := by
  simp only [eval]

theorem eval_intLit (n : Int) :
    eval s st env (.intLit n) = some (.vInt n) := by
  simp only [eval]

theorem eval_ident_local {x : String} {v : Value}
    (h : Env.lookup env x = some v) :
    eval s st env (.ident x) = some v := by
  simp only [eval, h]

theorem eval_ident_state {x : String} {v : Value}
    (hEnv : Env.lookup env x = none)
    (hSt : st.lookupScalar x = some v) :
    eval s st env (.ident x) = some v := by
  simp only [eval, hEnv, hSt]

/-! ### Unary operators -/

theorem eval_unNot_bool (e : Expr) (b : Bool)
    (h : eval s st env e = some (.vBool b)) :
    eval s st env (.unNot e) = some (.vBool (!b)) := by
  simp only [eval, h]

theorem eval_unNot_none (e : Expr) (h : eval s st env e = none) :
    eval s st env (.unNot e) = none := by
  simp only [eval, h]

theorem eval_unNeg_int (e : Expr) (n : Int)
    (h : eval s st env e = some (.vInt n)) :
    eval s st env (.unNeg e) = some (.vInt (-n)) := by
  simp only [eval, h]

theorem eval_unNeg_none (e : Expr) (h : eval s st env e = none) :
    eval s st env (.unNeg e) = none := by
  simp only [eval, h]

/-! ### Binary boolean -/

theorem eval_boolBin_bools (op : BoolBinOp) (l r : Expr) (a b : Bool)
    (hl : eval s st env l = some (.vBool a))
    (hr : eval s st env r = some (.vBool b)) :
    eval s st env (.boolBin op l r) = some (.vBool (evalBoolBin op a b)) := by
  simp only [eval, hl, hr]

/-! ### Comparison -/

theorem eval_cmp_app (op : CmpOp) (l r : Expr) :
    eval s st env (.cmp op l r) = evalCmp op (eval s st env l) (eval s st env r) := by
  simp only [eval]

/-! ### Let / EnumAccess / Member -/

theorem eval_letIn_some (x : String) (value body : Expr) (v : Value)
    (h : eval s st env value = some v) :
    eval s st env (.letIn x value body) = eval s st ((x, v) :: env) body := by
  simp only [eval, h]

theorem eval_letIn_none (x : String) (value body : Expr)
    (h : eval s st env value = none) :
    eval s st env (.letIn x value body) = none := by
  simp only [eval, h]

theorem eval_enumAccess_known (en mem : String) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d) (hMember : d.members.contains mem = true) :
    eval s st env (.enumAccess en mem) = some (.vEnum en mem) := by
  simp only [eval, hSchema, hMember, if_true]

theorem eval_enumAccess_unknown (en mem : String) (hSchema : s.lookupEnum en = none) :
    eval s st env (.enumAccess en mem) = none := by
  simp only [eval, hSchema]

theorem eval_member_resolved (elem : Expr) (relName : String) (v : Value) (dom : List Value)
    (hElem : eval s st env elem = some v)
    (hDom : st.relationDomain relName = some dom) :
    eval s st env (.member elem relName) = some (.vBool (dom.contains v)) := by
  simp only [eval, hElem, hDom]

theorem eval_member_no_elem (elem : Expr) (relName : String)
    (h : eval s st env elem = none) :
    eval s st env (.member elem relName) = none := by
  simp only [eval, h]

theorem eval_member_no_relation (elem : Expr) (relName : String) (v : Value)
    (hElem : eval s st env elem = some v)
    (hDom : st.relationDomain relName = none) :
    eval s st env (.member elem relName) = none := by
  simp only [eval, hElem, hDom]

/-! ### Prime and Pre — single-state collapse (identity). -/

theorem eval_prime (e : Expr) :
    eval s st env (.prime e) = eval s st env e := by
  simp only [eval]

theorem eval_pre (e : Expr) :
    eval s st env (.pre e) = eval s st env e := by
  simp only [eval]

/-! ### Universal quantifier over enums -/

theorem eval_forallEnum_known (var en : String) (body : Expr) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d) :
    eval s st env (.forallEnum var en body)
      = evalForallEnum s st env var en d.members body := by
  simp only [eval, hSchema]

theorem eval_forallEnum_unknown (var en : String) (body : Expr)
    (h : s.lookupEnum en = none) :
    eval s st env (.forallEnum var en body) = none := by
  simp only [eval, h]

theorem evalForallEnum_nil (var en : String) (body : Expr) :
    evalForallEnum s st env var en [] body = some (.vBool true) := by
  simp only [evalForallEnum]

theorem evalForallEnum_cons_acc (var en m : String) (rest : List String) (body : Expr)
    (b acc : Bool)
    (hHead : eval s st ((var, .vEnum en m) :: env) body = some (.vBool b))
    (hRest : evalForallEnum s st env var en rest body = some (.vBool acc)) :
    evalForallEnum s st env var en (m :: rest) body
      = some (.vBool (b && acc)) := by
  simp only [evalForallEnum, hHead, hRest]

/-! ### Universal quantifier over state-relation domains -/

theorem eval_forallRel_known (var rel : String) (body : Expr) (dom : List Value)
    (hDom : st.relationDomain rel = some dom) :
    eval s st env (.forallRel var rel body)
      = evalForallRel s st env var dom body := by
  simp only [eval, hDom]

theorem eval_forallRel_unknown (var rel : String) (body : Expr)
    (h : st.relationDomain rel = none) :
    eval s st env (.forallRel var rel body) = none := by
  simp only [eval, h]

theorem evalForallRel_nil (var : String) (body : Expr) :
    evalForallRel s st env var [] body = some (.vBool true) := by
  simp only [evalForallRel]

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
  simp only [evalRequiresAll]

theorem evalRequiresAll_cons_true (r : Expr) (rs : List Expr)
    (h : eval s st env r = some (.vBool true)) :
    evalRequiresAll s st env (r :: rs) = evalRequiresAll s st env rs := by
  simp only [evalRequiresAll, h, Option.bind, asBool]

theorem evalRequiresAll_cons_false (r : Expr) (rs : List Expr)
    (h : eval s st env r = some (.vBool false)) :
    evalRequiresAll s st env (r :: rs) = some false := by
  simp only [evalRequiresAll, h, Option.bind, asBool]

theorem evalEnsuresAll_nil :
    evalEnsuresAll s st env [] = some true := by
  simp only [evalEnsuresAll]

theorem evalEnsuresAll_cons_true (r : Expr) (rs : List Expr)
    (h : eval s st env r = some (.vBool true)) :
    evalEnsuresAll s st env (r :: rs) = evalEnsuresAll s st env rs := by
  simp only [evalEnsuresAll, h, Option.bind, asBool]

theorem evalEnsuresAll_cons_false (r : Expr) (rs : List Expr)
    (h : eval s st env r = some (.vBool false)) :
    evalEnsuresAll s st env (r :: rs) = some false := by
  simp only [evalEnsuresAll, h, Option.bind, asBool]

theorem evalInvariant_def (inv : InvariantDecl) :
    evalInvariant s st env inv = (eval s st env inv.body).bind asBool := by
  simp only [evalInvariant]

theorem operationEnabled_def (op : OperationDecl) :
    operationEnabled s st env op = evalRequiresAll s st env op.requires := by
  simp only [operationEnabled]

theorem operationEnsures_def (op : OperationDecl) :
    operationEnsures s st env op = evalEnsuresAll s st env op.ensures := by
  simp only [operationEnsures]

end SpecRest
