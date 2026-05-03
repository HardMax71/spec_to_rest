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

/-! ### Index over state-relation pairs. -/

theorem eval_indexRel_key (relName : String) (key : Expr) (kv : Value)
    (hKey : eval s st env key = some kv) :
    eval s st env (.indexRel relName key) = st.lookupKey relName kv := by
  simp only [eval, hKey]

theorem eval_indexRel_key_none (relName : String) (key : Expr)
    (hKey : eval s st env key = none) :
    eval s st env (.indexRel relName key) = none := by
  simp only [eval, hKey]

/-! ### FieldAccess on an entity-valued sub-expression.

M_L.4.k generalised the constructor to take an arbitrary `Expr` base.
The eval arm is a two-step lookup: evaluate the base to a `vEntity _ id`,
then look up `id` in the entity-fields table. -/

theorem eval_fieldAccess_resolved (base : Expr) (en id fieldName : String)
    (hBase : eval s st env base = some (.vEntity en id)) :
    eval s st env (.fieldAccess base fieldName)
      = st.lookupField id fieldName := by
  simp only [eval, hBase]

theorem eval_fieldAccess_base_none (base : Expr) (fieldName : String)
    (hBase : eval s st env base = none) :
    eval s st env (.fieldAccess base fieldName) = none := by
  simp only [eval, hBase]

theorem eval_fieldAccess_nonEntity {base : Expr} {fieldName : String} {v : Value}
    (hBase : eval s st env base = some v)
    (hNotEntity : ∀ en id, v ≠ .vEntity en id) :
    eval s st env (.fieldAccess base fieldName) = none := by
  simp only [eval, hBase]
  cases v with
  | vEntity en id => exact absurd rfl (hNotEntity en id)
  | vBool _ => rfl
  | vInt _ => rfl
  | vEnum _ _ => rfl
  | vSet _ => rfl

/-! ### Set literals, set membership, and set-valued binary operations. -/

theorem eval_setEmpty :
    eval s st env .setEmpty = some (.vSet []) := by
  simp only [eval]

theorem eval_setInsert_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some (.vSet members)) :
    eval s st env (.setInsert elem set)
      = some (.vSet (dedupeValues (v :: members))) := by
  simp only [eval, hElem, hSet]

theorem eval_setInsert_elem_none (elem set : Expr)
    (hElem : eval s st env elem = none) :
    eval s st env (.setInsert elem set) = none := by
  simp only [eval, hElem]

theorem eval_setInsert_set_none (elem set : Expr) (v : Value)
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = none) :
    eval s st env (.setInsert elem set) = none := by
  simp only [eval, hElem, hSet]

theorem eval_setInsert_set_nonSet {elem set : Expr} {v setVal : Value}
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some setVal)
    (hNotSet : ∀ members, setVal ≠ .vSet members) :
    eval s st env (.setInsert elem set) = none := by
  simp only [eval, hElem, hSet]
  cases setVal with
  | vSet members => exact absurd rfl (hNotSet members)
  | _ => rfl

theorem eval_setMember_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some (.vSet members)) :
    eval s st env (.setMember elem set) = some (.vBool (containsValue members v)) := by
  simp only [eval, hElem, hSet]

theorem eval_setMember_elem_none (elem set : Expr)
    (hElem : eval s st env elem = none) :
    eval s st env (.setMember elem set) = none := by
  simp only [eval, hElem]

theorem eval_setMember_set_none (elem set : Expr) (v : Value)
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = none) :
    eval s st env (.setMember elem set) = none := by
  simp only [eval, hElem, hSet]

theorem eval_setMember_set_nonSet {elem set : Expr} {v setVal : Value}
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some setVal)
    (hNotSet : ∀ members, setVal ≠ .vSet members) :
    eval s st env (.setMember elem set) = none := by
  simp only [eval, hElem, hSet]
  cases setVal with
  | vSet members => exact absurd rfl (hNotSet members)
  | _ => rfl

theorem eval_setBin_sets (op : SetOp) (l r : Expr) (ls rs : List Value)
    (hL : eval s st env l = some (.vSet ls))
    (hR : eval s st env r = some (.vSet rs)) :
    eval s st env (.setBin op l r) = evalSetBin op (some (.vSet ls)) (some (.vSet rs)) := by
  simp only [eval, hL, hR]

theorem eval_setBin_lhs_none (op : SetOp) (l r : Expr)
    (hL : eval s st env l = none) :
    eval s st env (.setBin op l r) = none := by
  simp only [eval, hL]
  cases op <;> rfl

theorem eval_setBin_rhs_none (op : SetOp) (l r : Expr) (lv : Value)
    (hL : eval s st env l = some lv)
    (hR : eval s st env r = none) :
    eval s st env (.setBin op l r) = none := by
  simp only [eval, hL, hR]
  cases op <;> cases lv <;> rfl

theorem eval_setBin_lhs_nonSet (op : SetOp) (l r : Expr) (lv : Value)
    (hNotSet : ∀ members, lv ≠ .vSet members)
    (hL : eval s st env l = some lv) :
    eval s st env (.setBin op l r) = none := by
  simp only [eval, hL]
  cases op <;> cases lv with
  | vSet members => exact absurd rfl (hNotSet members)
  | _ => rfl

theorem eval_setBin_rhs_nonSet (op : SetOp) (l r : Expr) (ls : List Value) (rv : Value)
    (hNotSet : ∀ members, rv ≠ .vSet members)
    (hL : eval s st env l = some (.vSet ls))
    (hR : eval s st env r = some rv) :
    eval s st env (.setBin op l r) = none := by
  simp only [eval, hL, hR]
  cases op <;> cases rv with
  | vSet members => exact absurd rfl (hNotSet members)
  | _ => rfl

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

/-! ## Per-arm characterizations for `evalAt` (M_L.4.b-ext Phase 3, issue #194).

Mirror of the single-state `eval_*` characterizations above. Each lemma names
the equation `evalAt mode s sp env (.X args) = ...` so case-by-case `soundnessAt`
proofs can rewrite without unfolding `evalAt` directly. The state-dependent
arms read through `sp.at mode`; the recursive arms thread mode unchanged
except for `Prime`/`Pre` (already in `Semantics.lean`). Args are explicit (no
`variable` inheritance) so call sites can pass them positionally without
worrying about auto-bound order. -/

theorem evalAt_boolLit (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) (b : Bool) :
    evalAt mode s sp env (.boolLit b) = some (.vBool b) := by
  simp only [evalAt]

theorem evalAt_intLit (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) (n : Int) :
    evalAt mode s sp env (.intLit n) = some (.vInt n) := by
  simp only [evalAt]

theorem evalAt_ident_local (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    {x : String} {v : Value} (h : Env.lookup env x = some v) :
    evalAt mode s sp env (.ident x) = some v := by
  simp only [evalAt, h]

theorem evalAt_ident_state (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    {x : String} {v : Value}
    (hEnv : Env.lookup env x = none)
    (hSt : (sp.at mode).lookupScalar x = some v) :
    evalAt mode s sp env (.ident x) = some v := by
  simp only [evalAt, hEnv, hSt]

theorem evalAt_unNot_bool (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (e : Expr) (b : Bool) (h : evalAt mode s sp env e = some (.vBool b)) :
    evalAt mode s sp env (.unNot e) = some (.vBool (!b)) := by
  simp only [evalAt, h]

theorem evalAt_unNot_none (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (e : Expr) (h : evalAt mode s sp env e = none) :
    evalAt mode s sp env (.unNot e) = none := by
  simp only [evalAt, h]

theorem evalAt_unNeg_int (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (e : Expr) (n : Int) (h : evalAt mode s sp env e = some (.vInt n)) :
    evalAt mode s sp env (.unNeg e) = some (.vInt (-n)) := by
  simp only [evalAt, h]

theorem evalAt_unNeg_none (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (e : Expr) (h : evalAt mode s sp env e = none) :
    evalAt mode s sp env (.unNeg e) = none := by
  simp only [evalAt, h]

theorem evalAt_boolBin_bools (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (op : BoolBinOp) (l r : Expr) (a b : Bool)
    (hl : evalAt mode s sp env l = some (.vBool a))
    (hr : evalAt mode s sp env r = some (.vBool b)) :
    evalAt mode s sp env (.boolBin op l r) = some (.vBool (evalBoolBin op a b)) := by
  simp only [evalAt, hl, hr]

theorem evalAt_cmp_app (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (op : CmpOp) (l r : Expr) :
    evalAt mode s sp env (.cmp op l r)
      = evalCmp op (evalAt mode s sp env l) (evalAt mode s sp env r) := by
  simp only [evalAt]

theorem evalAt_letIn_some (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (x : String) (value body : Expr) (v : Value)
    (h : evalAt mode s sp env value = some v) :
    evalAt mode s sp env (.letIn x value body)
      = evalAt mode s sp ((x, v) :: env) body := by
  simp only [evalAt, h]

theorem evalAt_letIn_none (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (x : String) (value body : Expr)
    (h : evalAt mode s sp env value = none) :
    evalAt mode s sp env (.letIn x value body) = none := by
  simp only [evalAt, h]

theorem evalAt_enumAccess_known (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (en mem : String) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d) (hMember : d.members.contains mem = true) :
    evalAt mode s sp env (.enumAccess en mem) = some (.vEnum en mem) := by
  simp only [evalAt, hSchema, hMember, if_true]

theorem evalAt_enumAccess_unknown (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (en mem : String) (hSchema : s.lookupEnum en = none) :
    evalAt mode s sp env (.enumAccess en mem) = none := by
  simp only [evalAt, hSchema]

theorem evalAt_member_resolved (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (elem : Expr) (relName : String) (v : Value) (dom : List Value)
    (hElem : evalAt mode s sp env elem = some v)
    (hDom : (sp.at mode).relationDomain relName = some dom) :
    evalAt mode s sp env (.member elem relName) = some (.vBool (dom.contains v)) := by
  simp only [evalAt, hElem, hDom]

theorem evalAt_member_no_elem (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (elem : Expr) (relName : String)
    (h : evalAt mode s sp env elem = none) :
    evalAt mode s sp env (.member elem relName) = none := by
  simp only [evalAt, h]

theorem evalAt_member_no_relation (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (elem : Expr) (relName : String) (v : Value)
    (hElem : evalAt mode s sp env elem = some v)
    (hDom : (sp.at mode).relationDomain relName = none) :
    evalAt mode s sp env (.member elem relName) = none := by
  simp only [evalAt, hElem, hDom]

theorem evalAt_indexRel_key (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (relName : String) (key : Expr) (kv : Value)
    (hKey : evalAt mode s sp env key = some kv) :
    evalAt mode s sp env (.indexRel relName key) = (sp.at mode).lookupKey relName kv := by
  simp only [evalAt, hKey]

theorem evalAt_indexRel_key_none (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (relName : String) (key : Expr)
    (hKey : evalAt mode s sp env key = none) :
    evalAt mode s sp env (.indexRel relName key) = none := by
  simp only [evalAt, hKey]

theorem evalAt_fieldAccess_resolved (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (base : Expr) (en id fieldName : String)
    (hBase : evalAt mode s sp env base = some (.vEntity en id)) :
    evalAt mode s sp env (.fieldAccess base fieldName)
      = (sp.at mode).lookupField id fieldName := by
  simp only [evalAt, hBase]

theorem evalAt_fieldAccess_base_none (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (base : Expr) (fieldName : String)
    (hBase : evalAt mode s sp env base = none) :
    evalAt mode s sp env (.fieldAccess base fieldName) = none := by
  simp only [evalAt, hBase]

theorem evalAt_fieldAccess_nonEntity (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    {base : Expr} {fieldName : String} {v : Value}
    (hBase : evalAt mode s sp env base = some v)
    (hNotEntity : ∀ en id, v ≠ .vEntity en id) :
    evalAt mode s sp env (.fieldAccess base fieldName) = none := by
  simp only [evalAt, hBase]
  cases v with
  | vEntity en id => exact absurd rfl (hNotEntity en id)
  | vBool _ => rfl
  | vInt _ => rfl
  | vEnum _ _ => rfl
  | vSet _ => rfl

theorem evalAt_setEmpty (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) :
    evalAt mode s sp env .setEmpty = some (.vSet []) := by
  simp only [evalAt]

theorem evalAt_setInsert_resolved (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (elem set : Expr) (v : Value) (members : List Value)
    (hElem : evalAt mode s sp env elem = some v)
    (hSet : evalAt mode s sp env set = some (.vSet members)) :
    evalAt mode s sp env (.setInsert elem set)
      = some (.vSet (dedupeValues (v :: members))) := by
  simp only [evalAt, hElem, hSet]

theorem evalAt_setMember_resolved (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (elem set : Expr) (v : Value) (members : List Value)
    (hElem : evalAt mode s sp env elem = some v)
    (hSet : evalAt mode s sp env set = some (.vSet members)) :
    evalAt mode s sp env (.setMember elem set) = some (.vBool (containsValue members v)) := by
  simp only [evalAt, hElem, hSet]

theorem evalAt_setBin_sets (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (op : SetOp) (l r : Expr) (ls rs : List Value)
    (hL : evalAt mode s sp env l = some (.vSet ls))
    (hR : evalAt mode s sp env r = some (.vSet rs)) :
    evalAt mode s sp env (.setBin op l r) = evalSetBin op (some (.vSet ls)) (some (.vSet rs)) := by
  simp only [evalAt, hL, hR]

theorem evalAt_forallEnum_known (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var en : String) (body : Expr) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d) :
    evalAt mode s sp env (.forallEnum var en body)
      = evalAtForallEnum mode s sp env var en d.members body := by
  simp only [evalAt, hSchema]

theorem evalAt_forallEnum_unknown (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var en : String) (body : Expr)
    (h : s.lookupEnum en = none) :
    evalAt mode s sp env (.forallEnum var en body) = none := by
  simp only [evalAt, h]

theorem evalAtForallEnum_nil (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var en : String) (body : Expr) :
    evalAtForallEnum mode s sp env var en [] body = some (.vBool true) := by
  simp only [evalAtForallEnum]

theorem evalAt_forallRel_known (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var rel : String) (body : Expr) (dom : List Value)
    (hDom : (sp.at mode).relationDomain rel = some dom) :
    evalAt mode s sp env (.forallRel var rel body)
      = evalAtForallRel mode s sp env var dom body := by
  simp only [evalAt, hDom]

theorem evalAt_forallRel_unknown (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var rel : String) (body : Expr)
    (h : (sp.at mode).relationDomain rel = none) :
    evalAt mode s sp env (.forallRel var rel body) = none := by
  simp only [evalAt, h]

theorem evalAtForallRel_nil (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
    (var : String) (body : Expr) :
    evalAtForallRel mode s sp env var [] body = some (.vBool true) := by
  simp only [evalAtForallRel]

end SpecRest
