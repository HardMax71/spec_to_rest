import SpecRest.IR
import SpecRest.Semantics
import SpecRest.Lemmas
import SpecRest.Smt
import SpecRest.Translate

namespace SpecRest

/-! # M_L.2 — Translator soundness theorem.

Statement (research doc §8.3, in this file's actual API shape):

  ∀ (s : Schema) (st : State) (env : Env) (e : Expr),
    valueToSmt? (eval s st env e)
      = smtEval (correlateModel s st) (correlateEnv env) (translate e)

The `valueToSmt?` bridge maps `Option Value` ↔ `Option SmtVal`, and
`correlateEnv` / `correlateModel` derive the canonical SMT model and
environment from the Lean state and lexical scope. The §8.3 shorthand
`eval e = smtEval (translate e)` elides those bridges; this file's
theorems use the explicit form above.

Proved by structural induction on `Expr`. Each constructor uses
M_L.1's per-arm characterization lemma to unfold `eval`, `translate`'s
structural definition, and M_L.2's `smtEval_*` characterization
lemmas to unfold `smtEval`.

This file ships the universal `soundness` theorem CLOSED with zero
`sorry` for the §6.1 verified subset. The closure dispatches each
constructor to its per-case theorem (success path) or to a failure
helper (eval-none / wrong-shape paths) that propagates `none` on both
sides via the `smtEval_*_none/_nonBool/_nonInt` characterizations
in `Smt.lean`. -/

/-! ## Value ↔ SmtVal correlation -/

def valueToSmt : Value → SmtVal
  | .vBool b       => .sBool b
  | .vInt n        => .sInt n
  | .vEnum en mem  => .sEnumElem en mem
  | .vEntity en id => .sEntityElem en id

def valueToSmt? : Option Value → Option SmtVal
  | some v => some (valueToSmt v)
  | none   => none

@[simp] theorem valueToSmt?_some (v : Value) :
    valueToSmt? (some v) = some (valueToSmt v) := rfl

@[simp] theorem valueToSmt?_none :
    valueToSmt? (none : Option Value) = none := rfl

theorem valueToSmt_inj : ∀ {a b : Value}, valueToSmt a = valueToSmt b → a = b := by
  intro a b h
  cases a <;> cases b <;> (try cases h) <;> simp_all

/-- `SmtVal.sInt` boolean equality unfolds to `Int` boolean equality. -/
theorem sInt_beq (a b : Int) : (SmtVal.sInt a == SmtVal.sInt b) = (a == b) := by
  by_cases h : a = b
  · subst h
    have ha : (a == a) = true := by rw [beq_iff_eq]
    have hsa : (SmtVal.sInt a == SmtVal.sInt a) = true := by rw [beq_iff_eq]
    rw [ha, hsa]
  · have h1 : (a == b) = false := by
      apply Bool.eq_false_iff.mpr
      intro hb; exact h (eq_of_beq hb)
    have h2 : (SmtVal.sInt a == SmtVal.sInt b) = false := by
      apply Bool.eq_false_iff.mpr
      intro hb
      have : SmtVal.sInt a = SmtVal.sInt b := eq_of_beq hb
      cases this; exact h rfl
    rw [h1, h2]

/-- Boolean equality on `Value` agrees with boolean equality on `SmtVal` after `valueToSmt`. -/
theorem valueToSmt_beq (a b : Value) : (valueToSmt a == valueToSmt b) = (a == b) := by
  by_cases h : a = b
  · subst h
    have ha : (a == a) = true := by rw [beq_iff_eq]
    have hav : (valueToSmt a == valueToSmt a) = true := by rw [beq_iff_eq]
    rw [ha, hav]
  · have h1 : (a == b) = false := by
      apply Bool.eq_false_iff.mpr
      intro hb
      exact h (eq_of_beq hb)
    have h2 : (valueToSmt a == valueToSmt b) = false := by
      apply Bool.eq_false_iff.mpr
      intro hb
      exact h (valueToSmt_inj (eq_of_beq hb))
    rw [h1, h2]

/-! ## Env / State / Schema ↔ SmtModel correlation -/

def correlateEnv : Env → SmtEnv
  | []            => []
  | (k, v) :: rest => (k, valueToSmt v) :: correlateEnv rest

def correlateModel (s : Schema) (st : State) : SmtModel where
  sortMembers := s.enums.map (fun d => (d.name, d.members))
  constVals := st.scalars.map (fun (k, v) => (k, valueToSmt v))
  predDomain :=
    st.relations.map (fun (k, vs) => (k, vs.map valueToSmt))
  predLookup :=
    st.lookups.map (fun (k, ps) =>
      (k, ps.map (fun p => (valueToSmt p.1, valueToSmt p.2))))
  predFields :=
    st.entityFields.map (fun (k, fs) =>
      (k, fs.map (fun f => (f.1, valueToSmt f.2))))

@[simp] theorem correlateEnv_nil : correlateEnv [] = [] := rfl

@[simp] theorem correlateEnv_cons (x : String) (v : Value) (env : Env) :
    correlateEnv ((x, v) :: env) = (x, valueToSmt v) :: correlateEnv env := rfl

theorem correlateEnv_lookup (env : Env) (x : String) :
    SmtEnv.lookup (correlateEnv env) x = (Env.lookup env x).map valueToSmt := by
  induction env with
  | nil => rfl
  | cons hd tl ih =>
      obtain ⟨k, v⟩ := hd
      show SmtEnv.lookup ((k, valueToSmt v) :: correlateEnv tl) x
            = (Env.lookup ((k, v) :: tl) x).map valueToSmt
      unfold SmtEnv.lookup Env.lookup
      simp only [List.lookup]
      split
      · rfl
      · exact ih

/-! ## Auxiliary lookup lemmas for `correlateModel`. -/

theorem lookup_map_value (xs : List (String × Value)) (x : String) :
    (xs.map (fun p : String × Value => (p.1, valueToSmt p.2))).lookup x
      = (xs.lookup x).map valueToSmt := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    obtain ⟨k, v⟩ := hd
    simp only [List.map, List.lookup_cons]
    by_cases hkx : x == k
    · simp [hkx]
    · simp [hkx]; exact ih

/-- A state-scalar binding correlates to a `lookupConst` constant. -/
theorem correlateModel_const_state_scalar (s : Schema) (st : State) (x : String) (v : Value)
    (h : st.lookupScalar x = some v) :
    (correlateModel s st).lookupConst x = some (valueToSmt v) := by
  unfold SmtModel.lookupConst correlateModel
  simp only []
  rw [lookup_map_value]
  unfold State.lookupScalar at h
  rw [h]
  rfl

theorem lookup_map_listValue (xs : List (String × List Value)) (x : String) :
    (xs.map (fun p : String × List Value => (p.1, p.2.map valueToSmt))).lookup x
      = (xs.lookup x).map (fun vs => vs.map valueToSmt) := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    obtain ⟨k, vs⟩ := hd
    simp only [List.map, List.lookup_cons]
    by_cases hkx : x == k
    · simp [hkx]
    · simp [hkx]; exact ih

theorem lookup_map_listValuePair (xs : List (String × List (Value × Value))) (x : String) :
    (xs.map (fun p : String × List (Value × Value) =>
        (p.1, p.2.map (fun q => (valueToSmt q.1, valueToSmt q.2))))).lookup x
      = (xs.lookup x).map
          (fun ps => ps.map (fun q => (valueToSmt q.1, valueToSmt q.2))) := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    obtain ⟨k, ps⟩ := hd
    simp only [List.map, List.lookup_cons]
    by_cases hkx : x == k
    · simp [hkx]
    · simp [hkx]; exact ih

theorem lookup_map_stringValue (xs : List (String × Value)) (x : String) :
    (xs.map (fun p : String × Value => (p.1, valueToSmt p.2))).lookup x
      = (xs.lookup x).map valueToSmt := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    obtain ⟨k, v⟩ := hd
    simp only [List.map, List.lookup_cons]
    by_cases hkx : x == k
    · simp [hkx]
    · simp [hkx]; exact ih

theorem lookup_map_entityFields
    (xs : List (String × List (String × Value))) (x : String) :
    (xs.map (fun p : String × List (String × Value) =>
        (p.1, p.2.map (fun f => (f.1, valueToSmt f.2))))).lookup x
      = (xs.lookup x).map (fun fs => fs.map (fun f => (f.1, valueToSmt f.2))) := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    obtain ⟨k, fs⟩ := hd
    simp only [List.map, List.lookup_cons]
    by_cases hkx : x == k
    · simp [hkx]
    · simp [hkx]; exact ih

private theorem lookup_map_pair_enumDecl (xs : List EnumDecl) (en : String) :
    (xs.map (fun d => (d.name, d.members))).lookup en
      = (xs.find? (·.name == en)).map (·.members) := by
  induction xs with
  | nil => rfl
  | cons hd tl ih =>
    simp only [List.map, List.find?, List.lookup_cons]
    by_cases h : en == hd.name
    · have hsym : hd.name == en := by
        have := h
        cases hd
        simp only [beq_iff_eq] at this ⊢
        exact this.symm
      simp [h, hsym]
    · have hsym : ¬ (hd.name == en) := by
        intro hb; apply h
        simp only [beq_iff_eq] at hb ⊢
        exact hb.symm
      simp [h, hsym]; exact ih

/-- An enum declaration in the schema correlates to its `sortMembers` entry. -/
theorem correlateModel_lookupSortMembers (s : Schema) (st : State) (en : String) (d : EnumDecl)
    (h : s.lookupEnum en = some d) :
    (correlateModel s st).lookupSortMembers en = some d.members := by
  unfold SmtModel.lookupSortMembers correlateModel
  simp only []
  rw [lookup_map_pair_enumDecl]
  unfold Schema.lookupEnum at h
  rw [h]
  rfl

/-- A state-relation domain correlates to a `lookupRel` predicate domain. -/
theorem correlateModel_lookupRel (s : Schema) (st : State) (relName : String) (dom : List Value)
    (h : st.relationDomain relName = some dom) :
    (correlateModel s st).lookupRel relName = some (dom.map valueToSmt) := by
  unfold SmtModel.lookupRel correlateModel
  simp only []
  rw [lookup_map_listValue]
  unfold State.relationDomain at h
  rw [h]
  rfl

/-- A state-relation key/value pair table correlates to a `lookupPairs` predicate-pair list. -/
theorem correlateModel_lookupPairs (s : Schema) (st : State) (relName : String)
    (pairs : List (Value × Value))
    (h : st.relationPairs relName = some pairs) :
    (correlateModel s st).lookupPairs relName
      = some (pairs.map (fun p => (valueToSmt p.1, valueToSmt p.2))) := by
  unfold SmtModel.lookupPairs correlateModel
  simp only []
  rw [lookup_map_listValuePair]
  unfold State.relationPairs at h
  rw [h]
  rfl

/-- `List.contains` commutes with `valueToSmt` via `valueToSmt_beq`. -/
theorem contains_map_valueToSmt (dom : List Value) (v : Value) :
    (dom.map valueToSmt).contains (valueToSmt v) = dom.contains v := by
  induction dom with
  | nil => rfl
  | cons hd tl ih =>
    rw [List.map]
    rw [← List.elem_eq_contains, ← List.elem_eq_contains]
    show (List.elem (valueToSmt v) (valueToSmt hd :: List.map valueToSmt tl))
            = List.elem v (hd :: tl)
    rw [List.elem, List.elem]
    by_cases h : valueToSmt v == valueToSmt hd
    · have h' : v == hd := by
        have hb : (valueToSmt v == valueToSmt hd) = (v == hd) := valueToSmt_beq v hd
        rw [hb] at h; exact h
      rw [h, h']
    · have h' : ¬ (v == hd) := by
        intro hb
        apply h
        have hb2 : (valueToSmt v == valueToSmt hd) = (v == hd) := valueToSmt_beq v hd
        rw [hb2]; exact hb
      have hbeq_false : (valueToSmt v == valueToSmt hd) = false := Bool.eq_false_iff.mpr h
      have hbeq_false' : (v == hd) = false := Bool.eq_false_iff.mpr h'
      rw [hbeq_false, hbeq_false']
      rw [List.elem_eq_contains, List.elem_eq_contains]
      exact ih

/-- `find?`-then-`map Prod.snd` commutes with `valueToSmt` mapping under `valueToSmt_beq`. -/
theorem find_map_valueToSmt (pairs : List (Value × Value)) (key : Value) :
    valueToSmt? ((pairs.find? (fun p => p.1 == key)).map Prod.snd)
      = ((pairs.map (fun q => (valueToSmt q.1, valueToSmt q.2))).find?
            (fun p => p.1 == valueToSmt key)).map Prod.snd := by
  induction pairs with
  | nil => rfl
  | cons hd rest ih =>
    obtain ⟨hk, hv⟩ := hd
    simp only [List.map, List.find?]
    by_cases h : hk == key
    · have hMapped : (valueToSmt hk == valueToSmt key) = true := by
        rw [valueToSmt_beq hk key]; exact h
      rw [h, hMapped]
      simp only [Option.map_some, valueToSmt?_some]
    · have hbeq : (hk == key) = false := Bool.eq_false_iff.mpr (fun hb => by rw [hb] at h; exact h rfl)
      have hMapped : (valueToSmt hk == valueToSmt key) = false := by
        rw [valueToSmt_beq hk key]; exact hbeq
      rw [hbeq, hMapped]; exact ih

/-- `lookupKey` correlates: looking up a key in the Lean state yields the SmtVal corresponding to
    looking up the same key (under `valueToSmt`) in the correlated SmtModel. -/
theorem lookupKey_correlated (s : Schema) (st : State) (relName : String) (key : Value) :
    valueToSmt? (st.lookupKey relName key)
      = (correlateModel s st).lookupKey relName (valueToSmt key) := by
  unfold State.lookupKey SmtModel.lookupKey
  unfold correlateModel
  simp only []
  rw [lookup_map_listValuePair]
  cases hLookup : List.lookup relName st.lookups with
  | none => simp only [Option.map_none, valueToSmt?_none]
  | some pairs =>
    simp only [Option.map_some]
    exact find_map_valueToSmt pairs key

/-- `lookupField` correlates: looking up an entity-scalar's field value in the Lean state yields
    the SmtVal corresponding to looking up the same (scalar, field) pair in the correlated
    SmtModel's `predFields` table. -/
theorem lookupField_correlated (s : Schema) (st : State) (scalarName fieldName : String) :
    valueToSmt? (st.lookupField scalarName fieldName)
      = (correlateModel s st).lookupField scalarName fieldName := by
  unfold State.lookupField SmtModel.lookupField
  unfold correlateModel
  simp only []
  rw [lookup_map_entityFields]
  cases hLookup : List.lookup scalarName st.entityFields with
  | none => simp only [Option.map_none, valueToSmt?_none]
  | some fields =>
    simp only [Option.map_some]
    rw [lookup_map_stringValue]
    cases List.lookup fieldName fields with
    | none   => rfl
    | some _ => rfl

/-! ## Soundness — case-by-case -/

variable (s : Schema) (st : State) (env : Env)

/-- Atomic: boolean literal. -/
theorem soundness_boolLit (b : Bool) :
    valueToSmt? (eval s st env (.boolLit b))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolLit b)) := by
  rw [eval_boolLit, valueToSmt?_some]
  show some (valueToSmt (.vBool b)) = _
  show some (SmtVal.sBool b) = _
  rw [show translate (.boolLit b) = .bLit b from rfl]
  rw [smtEval_bLit]

/-- Atomic: integer literal. -/
theorem soundness_intLit (n : Int) :
    valueToSmt? (eval s st env (.intLit n))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.intLit n)) := by
  rw [eval_intLit, valueToSmt?_some]
  show some (SmtVal.sInt n) = _
  rw [show translate (.intLit n) = .iLit n from rfl]
  rw [smtEval_iLit]

/-- Identifier — env hit. The local-binding path is the easier of the two. -/
theorem soundness_ident_local {x : String} {v : Value}
    (h : Env.lookup env x = some v) :
    valueToSmt? (eval s st env (.ident x))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.ident x)) := by
  rw [eval_ident_local s st env h, valueToSmt?_some]
  rw [show translate (.ident x) = .var x from rfl]
  have hCorr : SmtEnv.lookup (correlateEnv env) x = some (valueToSmt v) := by
    rw [correlateEnv_lookup, h]; rfl
  rw [smtEval_var_local _ _ hCorr]

/-- Unary `not` over a boolean sub-result. -/
theorem soundness_unNot_bool (e : Expr) (b : Bool)
    (hSub : valueToSmt? (eval s st env e)
              = smtEval (correlateModel s st) (correlateEnv env) (translate e))
    (hEval : eval s st env e = some (.vBool b)) :
    valueToSmt? (eval s st env (.unNot e))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.unNot e)) := by
  rw [eval_unNot_bool s st env e b hEval, valueToSmt?_some]
  show some (SmtVal.sBool (!b)) = _
  rw [show translate (.unNot e) = .not (translate e) from rfl]
  rw [hEval] at hSub
  rw [valueToSmt?_some] at hSub
  show some (SmtVal.sBool (!b)) = smtEval _ _ (.not (translate e))
  rw [smtEval_not_bool _ _ (translate e) b hSub.symm]

/-- Unary `negate` over an integer sub-result. -/
theorem soundness_unNeg_int (e : Expr) (n : Int)
    (hSub : valueToSmt? (eval s st env e)
              = smtEval (correlateModel s st) (correlateEnv env) (translate e))
    (hEval : eval s st env e = some (.vInt n)) :
    valueToSmt? (eval s st env (.unNeg e))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.unNeg e)) := by
  rw [eval_unNeg_int s st env e n hEval, valueToSmt?_some]
  show some (SmtVal.sInt (-n)) = _
  rw [show translate (.unNeg e) = .neg (translate e) from rfl]
  rw [hEval] at hSub
  rw [valueToSmt?_some] at hSub
  show some (SmtVal.sInt (-n)) = smtEval _ _ (.neg (translate e))
  rw [smtEval_neg_int _ _ (translate e) n hSub.symm]

/-- Boolean `and` — both sides must reduce to booleans. -/
theorem soundness_boolBin_and_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vBool a))
    (hR : eval s st env r = some (.vBool b)) :
    valueToSmt? (eval s st env (.boolBin .and l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin .and l r)) := by
  rw [eval_boolBin_bools s st env .and l r a b hL hR, valueToSmt?_some]
  rw [show evalBoolBin .and a b = (a && b) from rfl]
  show some (SmtVal.sBool (a && b)) = _
  rw [show translate (.boolBin .and l r) = .and (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEval_and_bools _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Boolean `or`. -/
theorem soundness_boolBin_or_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vBool a))
    (hR : eval s st env r = some (.vBool b)) :
    valueToSmt? (eval s st env (.boolBin .or l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin .or l r)) := by
  rw [eval_boolBin_bools s st env .or l r a b hL hR, valueToSmt?_some]
  rw [show evalBoolBin .or a b = (a || b) from rfl]
  show some (SmtVal.sBool (a || b)) = _
  rw [show translate (.boolBin .or l r) = .or (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEval_or_bools _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Boolean `implies`. -/
theorem soundness_boolBin_implies_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vBool a))
    (hR : eval s st env r = some (.vBool b)) :
    valueToSmt? (eval s st env (.boolBin .implies l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin .implies l r)) := by
  rw [eval_boolBin_bools s st env .implies l r a b hL hR, valueToSmt?_some]
  rw [show evalBoolBin .implies a b = (!a || b) from rfl]
  show some (SmtVal.sBool (!a || b)) = _
  rw [show translate (.boolBin .implies l r) = .implies (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEval_implies_bools _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Polymorphic `cmp .eq` over `Value`. Uses `valueToSmt_beq` to bridge `Value`/`SmtVal` `==`. -/
theorem soundness_cmp_eq_vals (l r : Expr) (va vb : Value)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some va)
    (hR : eval s st env r = some vb) :
    valueToSmt? (eval s st env (.cmp .eq l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .eq l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_eq_value, valueToSmt?_some]
  show some (SmtVal.sBool (va == vb)) = _
  rw [show translate (.cmp .eq l r) = .eq (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEval_eq_vals _ _ (translate l) (translate r)
        (valueToSmt va) (valueToSmt vb) hSubL.symm hSubR.symm]
  rw [valueToSmt_beq]

/-! ## Arithmetic per-case soundness theorems. -/

theorem soundness_arith_add_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.arith .add l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith .add l r)) := by
  rw [show eval s st env (.arith .add l r) = some (Value.vInt (a + b)) from by
        simp only [eval, hL, hR]; rfl]
  rw [valueToSmt?_some]
  rw [show valueToSmt (Value.vInt (a + b)) = SmtVal.sInt (a + b) from rfl]
  rw [show translate (.arith .add l r) = SmtTerm.add (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (Value.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_add_ints _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

theorem soundness_arith_sub_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.arith .sub l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith .sub l r)) := by
  rw [show eval s st env (.arith .sub l r) = some (Value.vInt (a - b)) from by
        simp only [eval, hL, hR]; rfl]
  rw [valueToSmt?_some]
  rw [show valueToSmt (Value.vInt (a - b)) = SmtVal.sInt (a - b) from rfl]
  rw [show translate (.arith .sub l r) = SmtTerm.sub (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (Value.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_sub_ints _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

theorem soundness_arith_mul_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.arith .mul l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith .mul l r)) := by
  rw [show eval s st env (.arith .mul l r) = some (Value.vInt (a * b)) from by
        simp only [eval, hL, hR]; rfl]
  rw [valueToSmt?_some]
  rw [show valueToSmt (Value.vInt (a * b)) = SmtVal.sInt (a * b) from rfl]
  rw [show translate (.arith .mul l r) = SmtTerm.mul (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (Value.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_mul_ints _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

theorem soundness_arith_div_ints_nonZero (l r : Expr) (a b : Int) (hbz : b ≠ 0)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.arith .div l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith .div l r)) := by
  have hEval : eval s st env (.arith .div l r) = some (Value.vInt (a.ediv b)) := by
    simp only [eval, hL, hR]
    cases b with
    | ofNat k =>
      cases k with
      | zero => exact absurd rfl hbz
      | succ _ => rfl
    | negSucc _ => rfl
  rw [hEval]
  rw [valueToSmt?_some]
  rw [show valueToSmt (Value.vInt (a.ediv b)) = SmtVal.sInt (a.ediv b) from rfl]
  rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (Value.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_div_ints_nonZero _ _ (translate l) (translate r) a b hbz hSubL.symm hSubR.symm]

theorem soundness_arith_div_ints_zero (l r : Expr) (a : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt 0)) :
    valueToSmt? (eval s st env (.arith .div l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith .div l r)) := by
  rw [show eval s st env (.arith .div l r) = none from by simp only [eval, hL, hR]; rfl]
  rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (Value.vInt 0) = SmtVal.sInt 0 from rfl] at hSubR
  simp only [valueToSmt?]
  exact (smtEval_div_zero _ _ (translate l) (translate r) a hSubL.symm hSubR.symm).symm

/-- Integer `cmp .lt` — direct mirror of SMT `<`. -/
theorem soundness_cmp_lt_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.cmp .lt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .lt l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_int_lt, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a < b))) = _
  rw [show translate (.cmp .lt l r) = .lt (translate l) (translate r) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [show valueToSmt (.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_lt_ints _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Integer `cmp .gt` — `Translate.lean` swaps to `lt r l`; semantically `a > b ↔ b < a`. -/
theorem soundness_cmp_gt_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.cmp .gt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .gt l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_int_gt, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a > b))) = _
  rw [show translate (.cmp .gt l r) = .lt (translate r) (translate l) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [show valueToSmt (.vInt b) = SmtVal.sInt b from rfl] at hSubR
  rw [smtEval_lt_ints _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm]

/-- Integer `cmp .le` — `Translate.lean` encodes as `or (lt l r) (eq l r)`. -/
theorem soundness_cmp_le_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.cmp .le l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .le l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_int_le, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a ≤ b))) = _
  rw [show translate (.cmp .le l r)
        = .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r))
      from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [show valueToSmt (.vInt b) = SmtVal.sInt b from rfl] at hSubR
  have hlt : smtEval (correlateModel s st) (correlateEnv env)
                (.lt (translate l) (translate r))
              = some (.sBool (decide (a < b))) :=
    smtEval_lt_ints _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm
  have heq : smtEval (correlateModel s st) (correlateEnv env)
                (.eq (translate l) (translate r))
              = some (.sBool (SmtVal.sInt a == SmtVal.sInt b)) :=
    smtEval_eq_vals _ _ (translate l) (translate r)
        (SmtVal.sInt a) (SmtVal.sInt b) hSubL.symm hSubR.symm
  rw [smtEval_or_bools _ _ _ _ _ _ hlt heq, sInt_beq]
  congr 2
  by_cases hab : a ≤ b
  · by_cases hlt2 : a < b
    · simp [hab, hlt2]
    · have heq2 : a = b := by omega
      simp [heq2]
  · have hlt2 : ¬ a < b := by omega
    have hne : a ≠ b := by omega
    simp [hab, hlt2, hne]

/-- Integer `cmp .ge` — `Translate.lean` encodes as `or (lt r l) (eq l r)`. -/
theorem soundness_cmp_ge_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some (.vInt b)) :
    valueToSmt? (eval s st env (.cmp .ge l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .ge l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_int_ge, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a ≥ b))) = _
  rw [show translate (.cmp .ge l r)
        = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r))
      from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [show valueToSmt (.vInt a) = SmtVal.sInt a from rfl] at hSubL
  rw [show valueToSmt (.vInt b) = SmtVal.sInt b from rfl] at hSubR
  have hlt : smtEval (correlateModel s st) (correlateEnv env)
                (.lt (translate r) (translate l))
              = some (.sBool (decide (b < a))) :=
    smtEval_lt_ints _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm
  have heq : smtEval (correlateModel s st) (correlateEnv env)
                (.eq (translate l) (translate r))
              = some (.sBool (SmtVal.sInt a == SmtVal.sInt b)) :=
    smtEval_eq_vals _ _ (translate l) (translate r)
        (SmtVal.sInt a) (SmtVal.sInt b) hSubL.symm hSubR.symm
  rw [smtEval_or_bools _ _ _ _ _ _ hlt heq, sInt_beq]
  congr 2
  by_cases hab : a ≥ b
  · by_cases hlt2 : b < a
    · simp [hab, hlt2]
    · have heq2 : a = b := by omega
      simp [heq2]
  · have hlt2 : ¬ b < a := by omega
    have hne : a ≠ b := by omega
    simp [hab, hlt2, hne]

/-- Polymorphic `cmp .neq`. Translate.lean encodes `neq` as `not (eq l r)`. -/
theorem soundness_cmp_neq_vals (l r : Expr) (va vb : Value)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some va)
    (hR : eval s st env r = some vb) :
    valueToSmt? (eval s st env (.cmp .neq l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .neq l r)) := by
  rw [eval_cmp_app, hL, hR, evalCmp_neq_value, valueToSmt?_some]
  show some (SmtVal.sBool (va != vb)) = _
  rw [show translate (.cmp .neq l r) = .not (.eq (translate l) (translate r)) from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  have heq : smtEval (correlateModel s st) (correlateEnv env)
                (.eq (translate l) (translate r))
              = some (.sBool (valueToSmt va == valueToSmt vb)) :=
    smtEval_eq_vals _ _ (translate l) (translate r)
        (valueToSmt va) (valueToSmt vb) hSubL.symm hSubR.symm
  rw [smtEval_not_bool _ _ _ _ heq, valueToSmt_beq]
  rfl

/-- Boolean `iff`. `Translate.lean` encodes `iff l r` as `and (implies l r) (implies r l)`,
    so soundness factors through `_and` + `_implies` and a literal `Bool` identity. -/
theorem soundness_boolBin_iff_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (eval s st env l)
              = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
              = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vBool a))
    (hR : eval s st env r = some (.vBool b)) :
    valueToSmt? (eval s st env (.boolBin .iff l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin .iff l r)) := by
  rw [eval_boolBin_bools s st env .iff l r a b hL hR, valueToSmt?_some]
  rw [show evalBoolBin .iff a b = (a == b) from rfl]
  show some (SmtVal.sBool (a == b)) = _
  rw [show translate (.boolBin .iff l r)
        = .and (.implies (translate l) (translate r)) (.implies (translate r) (translate l))
      from rfl]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  have h1 : smtEval (correlateModel s st) (correlateEnv env)
              ((translate l).implies (translate r))
            = some (.sBool (!a || b)) :=
    smtEval_implies_bools _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm
  have h2 : smtEval (correlateModel s st) (correlateEnv env)
              ((translate r).implies (translate l))
            = some (.sBool (!b || a)) :=
    smtEval_implies_bools _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm
  rw [smtEval_and_bools _ _ _ _ (!a || b) (!b || a) h1 h2]
  congr 1
  congr 1
  cases a <;> cases b <;> rfl

/-- Identifier — state-scalar path. Env miss + state hit ⇒ correlateModel.lookupConst hit. -/
theorem soundness_ident_state {x : String} {v : Value}
    (hEnv : Env.lookup env x = none)
    (hSt : st.lookupScalar x = some v) :
    valueToSmt? (eval s st env (.ident x))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.ident x)) := by
  rw [eval_ident_state s st env hEnv hSt, valueToSmt?_some]
  rw [show translate (.ident x) = SmtTerm.var x from rfl]
  have henv : SmtEnv.lookup (correlateEnv env) x = none := by
    rw [correlateEnv_lookup, hEnv]; rfl
  have hconst : (correlateModel s st).lookupConst x = some (valueToSmt v) :=
    correlateModel_const_state_scalar s st x v hSt
  rw [smtEval_var_const _ _ henv hconst]

/-- `enumAccess` — clean closure now that `Translate.lean` emits `.enumElemConst` (disjoint
    from `.var` lookup), so no naming-collision invariant is needed. -/
theorem soundness_enumAccess_known (en m : String) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d) (hMember : d.members.contains m = true) :
    valueToSmt? (eval s st env (.enumAccess en m))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.enumAccess en m)) := by
  rw [eval_enumAccess_known s st env en m d hSchema hMember, valueToSmt?_some]
  show some (SmtVal.sEnumElem en m) = _
  rw [show translate (.enumAccess en m) = SmtTerm.enumElemConst en m from rfl]
  have hSort : (correlateModel s st).lookupSortMembers en = some d.members :=
    correlateModel_lookupSortMembers s st en d hSchema
  rw [smtEval_enumElemConst_known _ _ hSort hMember]

/-- `member` — state-relation domain membership. Uses `correlateModel_lookupRel` plus
    `contains_map_valueToSmt` to bridge `dom.contains v` ↔ `(dom.map valueToSmt).contains (valueToSmt v)`. -/
theorem soundness_member_resolved (elem : Expr) (relName : String) (v : Value) (dom : List Value)
    (hSubElem : valueToSmt? (eval s st env elem)
              = smtEval (correlateModel s st) (correlateEnv env) (translate elem))
    (hElem : eval s st env elem = some v)
    (hDom : st.relationDomain relName = some dom) :
    valueToSmt? (eval s st env (.member elem relName))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.member elem relName)) := by
  rw [eval_member_resolved s st env elem relName v dom hElem hDom, valueToSmt?_some]
  show some (SmtVal.sBool (dom.contains v)) = _
  rw [show translate (.member elem relName) = .inDom relName (translate elem) from rfl]
  rw [hElem] at hSubElem; rw [valueToSmt?_some] at hSubElem
  have hRel : (correlateModel s st).lookupRel relName = some (dom.map valueToSmt) :=
    correlateModel_lookupRel s st relName dom hDom
  rw [smtEval_inDom_resolved _ _ relName (translate elem) (valueToSmt v) (dom.map valueToSmt)
      hSubElem.symm hRel]
  rw [contains_map_valueToSmt]

/-- Mutual-induction correlation lemma between `evalForallEnum` and `smtEvalForallEnum`.
    Threads the body's soundness IH through every member of the enum. -/
theorem evalForallEnum_correlated
    (var en : String) (members : List String) (body : Expr)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (eval s st ((var, val) :: env) body)
        = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalForallEnum s st env var en members body)
      = smtEvalForallEnum (correlateModel s st) (correlateEnv env) var en members
          (translate body) := by
  induction members with
  | nil =>
    rw [evalForallEnum_nil, smtEvalForallEnum_nil]
    rfl
  | cons mem rest ih =>
    have hBody := hBodyIH (.vEnum en mem)
    rw [show valueToSmt (Value.vEnum en mem) = SmtVal.sEnumElem en mem from rfl] at hBody
    -- Unfold both sides via simp on the recursive defs.
    simp only [evalForallEnum, smtEvalForallEnum]
    -- Now case-split on the body's evaluation Lean-side.
    cases hb : eval s st ((var, .vEnum en mem) :: env) body with
    | none =>
      rw [hb] at hBody
      simp only [valueToSmt?] at hBody
      simp only [valueToSmt?, ← hBody]
    | some bv =>
      rw [hb] at hBody
      simp only [valueToSmt?_some] at hBody
      cases bv with
      | vBool b =>
        rw [show valueToSmt (Value.vBool b) = SmtVal.sBool b from rfl] at hBody
        rw [← hBody]
        cases hr : evalForallEnum s st env var en rest body with
        | none =>
          rw [hr] at ih
          simp only [valueToSmt?] at ih
          simp only [valueToSmt?, ← ih]
        | some rv =>
          rw [hr] at ih
          simp only [valueToSmt?_some] at ih
          cases rv with
          | vBool acc =>
            rw [show valueToSmt (Value.vBool acc) = SmtVal.sBool acc from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [show valueToSmt (Value.vInt n) = SmtVal.sInt n from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [show valueToSmt (Value.vInt n) = SmtVal.sInt n from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]

/-- `forallEnum` — universal quantifier over a known enum domain. -/
theorem soundness_forallEnum_known (var en : String) (body : Expr) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (eval s st ((var, val) :: env) body)
        = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (eval s st env (.forallEnum var en body))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.forallEnum var en body)) := by
  rw [eval_forallEnum_known s st env var en body d hSchema]
  rw [show translate (.forallEnum var en body) = SmtTerm.forallEnum var en (translate body) from rfl]
  have hSort : (correlateModel s st).lookupSortMembers en = some d.members :=
    correlateModel_lookupSortMembers s st en d hSchema
  rw [smtEval_forallEnum_known _ _ var en (translate body) d.members hSort]
  exact evalForallEnum_correlated s st env var en d.members body hBodyIH

/-- Mutual-induction correlation lemma between `evalForallRel` and `smtEvalForallRel`.
    Threads the body's soundness IH through every element of the relation's domain. -/
theorem evalForallRel_correlated
    (var : String) (dom : List Value) (body : Expr)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (eval s st ((var, val) :: env) body)
        = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalForallRel s st env var dom body)
      = smtEvalForallRel (correlateModel s st) (correlateEnv env) var (dom.map valueToSmt)
          (translate body) := by
  induction dom with
  | nil =>
    rw [evalForallRel_nil]
    rw [show ([] : List Value).map valueToSmt = [] from rfl]
    rw [smtEvalForallRel_nil]
    rfl
  | cons hd rest ih =>
    have hBody := hBodyIH hd
    simp only [evalForallRel, List.map, smtEvalForallRel]
    cases hb : eval s st ((var, hd) :: env) body with
    | none =>
      rw [hb] at hBody
      simp only [valueToSmt?] at hBody
      simp only [valueToSmt?, ← hBody]
    | some bv =>
      rw [hb] at hBody
      simp only [valueToSmt?_some] at hBody
      cases bv with
      | vBool b =>
        rw [show valueToSmt (Value.vBool b) = SmtVal.sBool b from rfl] at hBody
        rw [← hBody]
        cases hr : evalForallRel s st env var rest body with
        | none =>
          rw [hr] at ih
          simp only [valueToSmt?] at ih
          simp only [valueToSmt?, ← ih]
        | some rv =>
          rw [hr] at ih
          simp only [valueToSmt?_some] at ih
          cases rv with
          | vBool acc =>
            rw [show valueToSmt (Value.vBool acc) = SmtVal.sBool acc from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [show valueToSmt (Value.vInt n) = SmtVal.sInt n from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [show valueToSmt (Value.vInt n) = SmtVal.sInt n from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at hBody
        rw [← hBody]; simp only [valueToSmt?]

/-- `forallRel` — universal quantifier over a known state-relation domain. -/
theorem soundness_forallRel_known (var rel : String) (body : Expr) (dom : List Value)
    (hDom : st.relationDomain rel = some dom)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (eval s st ((var, val) :: env) body)
        = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (eval s st env (.forallRel var rel body))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.forallRel var rel body)) := by
  rw [eval_forallRel_known s st env var rel body dom hDom]
  rw [show translate (.forallRel var rel body) = SmtTerm.forallRel var rel (translate body) from rfl]
  have hRel : (correlateModel s st).lookupRel rel = some (dom.map valueToSmt) :=
    correlateModel_lookupRel s st rel dom hDom
  rw [smtEval_forallRel_known _ _ var rel (translate body) (dom.map valueToSmt) hRel]
  exact evalForallRel_correlated s st env var dom body hBodyIH

/-- `letIn` — env extension. Threads IH through the extended environment via `correlateEnv_cons`. -/
theorem soundness_letIn (x : String) (value body : Expr) (v : Value)
    (hSubV : valueToSmt? (eval s st env value)
              = smtEval (correlateModel s st) (correlateEnv env) (translate value))
    (hV : eval s st env value = some v)
    (hSubBody : valueToSmt? (eval s st ((x, v) :: env) body)
              = smtEval (correlateModel s st) (correlateEnv ((x, v) :: env)) (translate body)) :
    valueToSmt? (eval s st env (.letIn x value body))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.letIn x value body)) := by
  rw [eval_letIn_some s st env x value body v hV]
  rw [show translate (.letIn x value body) = .letIn x (translate value) (translate body) from rfl]
  rw [hV] at hSubV; rw [valueToSmt?_some] at hSubV
  rw [smtEval_letIn_some _ _ x (translate value) (translate body) (valueToSmt v) hSubV.symm]
  rw [show correlateEnv ((x, v) :: env) = (x, valueToSmt v) :: correlateEnv env from rfl]
    at hSubBody
  exact hSubBody

/-! ## boolBin/cmp universal-soundness helpers (failure paths). -/

private theorem boolBin_lhs_nonBool (s : Schema) (st : State) (env : Env)
    (op : BoolBinOp) (l r : Expr) (lv : Value)
    (hNotBool : ∀ b, lv ≠ .vBool b)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) :
    valueToSmt? (eval s st env (.boolBin op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin op l r)) := by
  have hEvalNone : eval s st env (.boolBin op l r) = none := by
    simp only [eval, hL]
    cases lv with
    | vBool b => exact absurd rfl (hNotBool b)
    | vInt _ => rfl
    | vEnum _ _ => rfl
    | vEntity _ _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotBool : ∀ b, valueToSmt lv ≠ .sBool b := by
    intro b heq
    cases lv with
    | vBool k =>
      have : k = b := by injection heq
      subst this; exact hNotBool k rfl
    | vInt _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  cases op with
  | and =>
    rw [show translate (.boolBin .and l r) = .and (translate l) (translate r) from rfl]
    exact (smtEval_and_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | or =>
    rw [show translate (.boolBin .or l r) = .or (translate l) (translate r) from rfl]
    exact (smtEval_or_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | implies =>
    rw [show translate (.boolBin .implies l r) = .implies (translate l) (translate r) from rfl]
    exact (smtEval_implies_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | iff =>
    rw [show translate (.boolBin .iff l r)
          = .and (.implies (translate l) (translate r))
                 (.implies (translate r) (translate l)) from rfl]
    have h_lr : smtEval (correlateModel s st) (correlateEnv env)
                  (.implies (translate l) (translate r)) = none :=
      smtEval_implies_lhs_nonBool _ _ ihL.symm hSmtNotBool
    exact (smtEval_and_lhs_none _ _ h_lr).symm

private theorem boolBin_rhs_nonBool_lhs_bool (s : Schema) (st : State) (env : Env)
    (op : BoolBinOp) (l r : Expr) (a : Bool) (rv : Value)
    (hNotBool : ∀ b, rv ≠ .vBool b)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vBool a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.boolBin op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolBin op l r)) := by
  have hEvalNone : eval s st env (.boolBin op l r) = none := by
    simp only [eval, hL, hR]
    cases rv with
    | vBool b => exact absurd rfl (hNotBool b)
    | vInt _ => rfl
    | vEnum _ _ => rfl
    | vEntity _ _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [show valueToSmt (Value.vBool a) = SmtVal.sBool a from rfl] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotBool : ∀ b, valueToSmt rv ≠ .sBool b := by
    intro b heq
    cases rv with
    | vBool k =>
      have : k = b := by injection heq
      subst this; exact hNotBool k rfl
    | vInt _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  cases op with
  | and =>
    rw [show translate (.boolBin .and l r) = .and (translate l) (translate r) from rfl]
    exact (smtEval_and_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | or =>
    rw [show translate (.boolBin .or l r) = .or (translate l) (translate r) from rfl]
    exact (smtEval_or_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | implies =>
    rw [show translate (.boolBin .implies l r) = .implies (translate l) (translate r) from rfl]
    exact (smtEval_implies_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | iff =>
    rw [show translate (.boolBin .iff l r)
          = .and (.implies (translate l) (translate r))
                 (.implies (translate r) (translate l)) from rfl]
    have h_lr : smtEval (correlateModel s st) (correlateEnv env)
                  (.implies (translate l) (translate r)) = none :=
      smtEval_implies_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool
    exact (smtEval_and_lhs_none _ _ h_lr).symm

/-- All cmp ops fail when the LHS doesn't evaluate at all. -/
private theorem cmp_lhs_eval_none (s : Schema) (st : State) (env : Env)
    (op : CmpOp) (l r : Expr)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = none) :
    valueToSmt? (eval s st env (.cmp op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp op l r)) := by
  have hEvalNone : eval s st env (.cmp op l r) = none := by
    rw [eval_cmp_app]
    rw [hL]
    cases op <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; simp only [valueToSmt?] at ihL
  simp only [valueToSmt?]
  cases op with
  | eq =>
    rw [show translate (.cmp .eq l r) = .eq (translate l) (translate r) from rfl]
    exact (smtEval_eq_lhs_none _ _ ihL.symm).symm
  | neq =>
    rw [show translate (.cmp .neq l r) = .not (.eq (translate l) (translate r)) from rfl]
    exact (smtEval_not_none _ _ _ (smtEval_eq_lhs_none _ _ ihL.symm)).symm
  | lt =>
    rw [show translate (.cmp .lt l r) = .lt (translate l) (translate r) from rfl]
    exact (smtEval_lt_lhs_none _ _ ihL.symm).symm
  | le =>
    rw [show translate (.cmp .le l r)
          = .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r)) from rfl]
    have hLt : smtEval _ _ (.lt (translate l) (translate r)) = none :=
      smtEval_lt_lhs_none _ _ ihL.symm
    exact (smtEval_or_lhs_none _ _ hLt).symm
  | gt =>
    rw [show translate (.cmp .gt l r) = .lt (translate r) (translate l) from rfl]
    -- For gt the lhs of .lt is r, not l; we need ihR. But we don't know eval r.
    -- Instead use rhs_none: the rhs of .lt is translate l, which has smtEval = none.
    -- But smtEval_lt has no rhs_none lemma directly. Use the def.
    show none = smtEval (correlateModel s st) (correlateEnv env) (.lt (translate r) (translate l))
    -- We don't have smtEval_lt_rhs_none for an unknown lhs. But we know smtEval (translate l) = none,
    -- so regardless of smtEval (translate r), the lt match falls through.
    simp only [smtEval]
    rw [← ihL]
    cases (smtEval (correlateModel s st) (correlateEnv env) (translate r)) with
    | none => rfl
    | some sv => cases sv <;> rfl
  | ge =>
    rw [show translate (.cmp .ge l r)
          = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r)) from rfl]
    -- The .eq lhs is (translate l) which has smtEval = none. So smtEval_eq_lhs_none.
    have hEq : smtEval _ _ (.eq (translate l) (translate r)) = none :=
      smtEval_eq_lhs_none _ _ ihL.symm
    -- The .lt lhs is (translate r); we don't know its result. Compute manually.
    have hLt : smtEval (correlateModel s st) (correlateEnv env) (.lt (translate r) (translate l))
             = none := by
      simp only [smtEval]
      rw [← ihL]
      cases (smtEval (correlateModel s st) (correlateEnv env) (translate r)) with
      | none => rfl
      | some sv => cases sv <;> rfl
    rw [smtEval]
    rw [hLt, hEq]

/-- All cmp ops fail when the RHS doesn't evaluate, given LHS eval succeeded. -/
private theorem cmp_rhs_eval_none (s : Schema) (st : State) (env : Env)
    (op : CmpOp) (l r : Expr) (lv : Value)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv)
    (hR : eval s st env r = none) :
    valueToSmt? (eval s st env (.cmp op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp op l r)) := by
  have hEvalNone : eval s st env (.cmp op l r) = none := by
    rw [eval_cmp_app]; rw [hL, hR]
    cases op <;> cases lv <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [hR] at ihR; simp only [valueToSmt?] at ihR
  simp only [valueToSmt?]
  cases op with
  | eq =>
    rw [show translate (.cmp .eq l r) = .eq (translate l) (translate r) from rfl]
    exact (smtEval_eq_rhs_none _ _ ihL.symm ihR.symm).symm
  | neq =>
    rw [show translate (.cmp .neq l r) = .not (.eq (translate l) (translate r)) from rfl]
    exact (smtEval_not_none _ _ _ (smtEval_eq_rhs_none _ _ ihL.symm ihR.symm)).symm
  | lt =>
    rw [show translate (.cmp .lt l r) = .lt (translate l) (translate r) from rfl]
    -- LHS is some valueToSmt lv. Need to show smtEval_lt = none.
    -- If valueToSmt lv is sInt n, use smtEval_lt_rhs_none. Otherwise use smtEval_lt_lhs_nonInt.
    cases lv with
    | vInt a =>
      rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihL
      exact (smtEval_lt_rhs_none _ _ ihL.symm ihR.symm).symm
    | vBool b =>
      rw [show valueToSmt (Value.vBool b) = SmtVal.sBool b from rfl] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)).symm
    | vEnum en' m' =>
      rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)).symm
    | vEntity en' i' =>
      rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)).symm
  | le =>
    rw [show translate (.cmp .le l r)
          = .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r)) from rfl]
    have hEq : smtEval _ _ (.eq (translate l) (translate r)) = none :=
      smtEval_eq_rhs_none _ _ ihL.symm ihR.symm
    have hLt : smtEval (correlateModel s st) (correlateEnv env)
                 (.lt (translate l) (translate r)) = none := by
      cases lv with
      | vInt a =>
        rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihL
        exact smtEval_lt_rhs_none _ _ ihL.symm ihR.symm
      | vBool b =>
        rw [show valueToSmt (Value.vBool b) = SmtVal.sBool b from rfl] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)
      | vEnum en' m' =>
        rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)
      | vEntity en' i' =>
        rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm (fun _ => by intro h; cases h)
    rw [smtEval, hLt, hEq]
  | gt =>
    rw [show translate (.cmp .gt l r) = .lt (translate r) (translate l) from rfl]
    -- lhs of .lt is translate r, which evals to none. Use smtEval_lt_lhs_none.
    exact (smtEval_lt_lhs_none _ _ ihR.symm).symm
  | ge =>
    rw [show translate (.cmp .ge l r)
          = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r)) from rfl]
    -- .lt lhs is translate r → none. .eq rhs is translate r → none.
    have hLt : smtEval _ _ (.lt (translate r) (translate l)) = none :=
      smtEval_lt_lhs_none _ _ ihR.symm
    have hEq : smtEval _ _ (.eq (translate l) (translate r)) = none :=
      smtEval_eq_rhs_none _ _ ihL.symm ihR.symm
    rw [smtEval, hLt, hEq]

/-- cmp lt: lhs evaluates to non-int. -/
private theorem cmp_lt_lhs_nonInt (s : Schema) (st : State) (env : Env)
    (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) (rv : Value) (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .lt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .lt l r)) := by
  have hEvalNone : eval s st env (.cmp .lt l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | vBool _ => cases rv <;> rfl
    | vEnum _ _ => cases rv <;> rfl
    | vEntity _ _ => cases rv <;> rfl
  rw [hEvalNone]
  rw [show translate (.cmp .lt l r) = .lt (translate l) (translate r) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt lv ≠ .sInt n := by
    intro n heq
    cases lv with
    | vInt k =>
      have : k = n := by injection heq
      subst this; exact hNotInt k rfl
    | vBool _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  exact (smtEval_lt_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm

/-- cmp lt: rhs is non-int (lhs is int). -/
private theorem cmp_lt_rhs_nonInt_lhs_int (s : Schema) (st : State) (env : Env)
    (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .lt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .lt l r)) := by
  have hEvalNone : eval s st env (.cmp .lt l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  rw [show translate (.cmp .lt l r) = .lt (translate l) (translate r) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt rv ≠ .sInt n := by
    intro n heq
    cases rv with
    | vInt k =>
      have : k = n := by injection heq
      subst this
      exact hNotInt k rfl
    | vBool _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  exact (smtEval_lt_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm

-- The le/gt/ge non-int variants below follow the same failure-shape pattern as lt and
-- are referenced by the universal `soundness` theorem.

/-- Arithmetic with non-int LHS — eval and smtEval both return `none`. -/
private theorem arith_lhs_nonInt (s : Schema) (st : State) (env : Env)
    (op : ArithOp) (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l)
            = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r)
            = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) :
    valueToSmt? (eval s st env (.arith op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith op l r)) := by
  have hEvalNone : eval s st env (.arith op l r) = none := by
    simp only [eval, hL]
    cases op <;> cases lv <;> first | rfl | exact absurd rfl (hNotInt _)
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt lv ≠ .sInt n := by
    intro n heq
    cases lv with
    | vInt k =>
      have : k = n := by injection heq
      subst this; exact hNotInt k rfl
    | vBool _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  cases op with
  | add =>
    rw [show translate (.arith .add l r) = SmtTerm.add (translate l) (translate r) from rfl]
    exact (smtEval_add_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | sub =>
    rw [show translate (.arith .sub l r) = SmtTerm.sub (translate l) (translate r) from rfl]
    exact (smtEval_sub_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | mul =>
    rw [show translate (.arith .mul l r) = SmtTerm.mul (translate l) (translate r) from rfl]
    exact (smtEval_mul_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | div =>
    rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
    exact (smtEval_div_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm

/-- Arithmetic with int LHS but non-int RHS — eval and smtEval both return `none`. -/
private theorem arith_rhs_nonInt_lhs_int (s : Schema) (st : State) (env : Env)
    (op : ArithOp) (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l)
            = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r)
            = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.arith op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.arith op l r)) := by
  have hEvalNone : eval s st env (.arith op l r) = none := by
    simp only [eval, hL, hR]
    cases op <;> cases rv <;> first | rfl | exact absurd rfl (hNotInt _)
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt rv ≠ .sInt n := by
    intro n heq
    cases rv with
    | vInt k =>
      have : k = n := by injection heq
      subst this; exact hNotInt k rfl
    | vBool _ => simp [valueToSmt] at heq
    | vEnum _ _ => simp [valueToSmt] at heq
    | vEntity _ _ => simp [valueToSmt] at heq
  cases op with
  | add =>
    rw [show translate (.arith .add l r) = SmtTerm.add (translate l) (translate r) from rfl]
    exact (smtEval_add_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | sub =>
    rw [show translate (.arith .sub l r) = SmtTerm.sub (translate l) (translate r) from rfl]
    exact (smtEval_sub_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | mul =>
    rw [show translate (.arith .mul l r) = SmtTerm.mul (translate l) (translate r) from rfl]
    exact (smtEval_mul_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | div =>
    rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
    exact (smtEval_div_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm

private theorem hSmtLvNotInt (lv : Value) (hNotInt : ∀ n, lv ≠ .vInt n) :
    ∀ n, valueToSmt lv ≠ .sInt n := by
  intro n heq
  cases lv with
  | vInt k =>
    have : k = n := by injection heq
    subst this; exact hNotInt k rfl
  | vBool _ => simp [valueToSmt] at heq
  | vEnum _ _ => simp [valueToSmt] at heq
  | vEntity _ _ => simp [valueToSmt] at heq

private theorem cmp_le_lhs_nonInt (s : Schema) (st : State) (env : Env)
    (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) (rv : Value) (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .le l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .le l r)) := by
  have hEvalNone : eval s st env (.cmp .le l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | vBool _ => cases rv <;> rfl
    | vEnum _ _ => cases rv <;> rfl
    | vEntity _ _ => cases rv <;> rfl
  rw [hEvalNone]
  rw [show translate (.cmp .le l r)
        = .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r)) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hLt : smtEval (correlateModel s st) (correlateEnv env)
              (.lt (translate l) (translate r)) = none :=
    smtEval_lt_lhs_nonInt _ _ ihL.symm (hSmtLvNotInt lv hNotInt)
  exact (smtEval_or_lhs_none _ _ hLt).symm

private theorem cmp_le_rhs_nonInt_lhs_int (s : Schema) (st : State) (env : Env)
    (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .le l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .le l r)) := by
  have hEvalNone : eval s st env (.cmp .le l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  rw [show translate (.cmp .le l r)
        = .or (.lt (translate l) (translate r)) (.eq (translate l) (translate r)) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hLt : smtEval (correlateModel s st) (correlateEnv env)
              (.lt (translate l) (translate r)) = none :=
    smtEval_lt_rhs_nonInt _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)
  exact (smtEval_or_lhs_none _ _ hLt).symm

private theorem cmp_gt_lhs_nonInt (s : Schema) (st : State) (env : Env)
    (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) (rv : Value) (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .gt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .gt l r)) := by
  have hEvalNone : eval s st env (.cmp .gt l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | vBool _ => cases rv <;> rfl
    | vEnum _ _ => cases rv <;> rfl
    | vEntity _ _ => cases rv <;> rfl
  rw [hEvalNone]
  rw [show translate (.cmp .gt l r) = .lt (translate r) (translate l) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  -- .lt (translate r) (translate l): rhs is translate l with smtEval = some (non-sInt).
  -- Use direct unfolding since we don't know smtEval (translate r).
  have hLvNonSInt := hSmtLvNotInt lv hNotInt
  show none = smtEval (correlateModel s st) (correlateEnv env) (.lt (translate r) (translate l))
  simp only [smtEval]
  rw [← ihL]
  cases (smtEval (correlateModel s st) (correlateEnv env) (translate r)) with
  | none => rfl
  | some sv =>
    cases sv with
    | sInt _ =>
      cases hVal : valueToSmt lv with
      | sInt n => exact absurd hVal (hLvNonSInt n)
      | sBool _ => rfl
      | sEnumElem _ _ => rfl
      | sEntityElem _ _ => rfl
    | sBool _ => rfl
    | sEnumElem _ _ => rfl
    | sEntityElem _ _ => rfl

private theorem cmp_gt_rhs_nonInt_lhs_int (s : Schema) (st : State) (env : Env)
    (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (_ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .gt l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .gt l r)) := by
  have hEvalNone : eval s st env (.cmp .gt l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  rw [show translate (.cmp .gt l r) = .lt (translate r) (translate l) from rfl]
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  exact (smtEval_lt_lhs_nonInt _ _ ihR.symm (hSmtLvNotInt rv hNotInt)).symm

private theorem cmp_ge_lhs_nonInt (s : Schema) (st : State) (env : Env)
    (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some lv) (rv : Value) (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .ge l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .ge l r)) := by
  have hEvalNone : eval s st env (.cmp .ge l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | vBool _ => cases rv <;> rfl
    | vEnum _ _ => cases rv <;> rfl
    | vEntity _ _ => cases rv <;> rfl
  rw [hEvalNone]
  rw [show translate (.cmp .ge l r)
        = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r)) from rfl]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hLvNonSInt := hSmtLvNotInt lv hNotInt
  have hLt : smtEval (correlateModel s st) (correlateEnv env)
              (.lt (translate r) (translate l)) = none := by
    simp only [smtEval]
    rw [← ihL]
    cases (smtEval (correlateModel s st) (correlateEnv env) (translate r)) with
    | none => rfl
    | some sv =>
      cases sv with
      | sInt _ =>
        cases hVal : valueToSmt lv with
        | sInt n => exact absurd hVal (hLvNonSInt n)
        | sBool _ => rfl
        | sEnumElem _ _ => rfl
        | sEntityElem _ _ => rfl
      | sBool _ => rfl
      | sEnumElem _ _ => rfl
      | sEntityElem _ _ => rfl
  exact (smtEval_or_lhs_none _ _ hLt).symm

private theorem cmp_ge_rhs_nonInt_lhs_int (s : Schema) (st : State) (env : Env)
    (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (_ihL : valueToSmt? (eval s st env l) = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (eval s st env r) = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vInt a))
    (hR : eval s st env r = some rv) :
    valueToSmt? (eval s st env (.cmp .ge l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.cmp .ge l r)) := by
  have hEvalNone : eval s st env (.cmp .ge l r) = none := by
    rw [eval_cmp_app, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  rw [show translate (.cmp .ge l r)
        = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r)) from rfl]
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hLt : smtEval (correlateModel s st) (correlateEnv env)
              (.lt (translate r) (translate l)) = none :=
    smtEval_lt_lhs_nonInt _ _ ihR.symm (hSmtLvNotInt rv hNotInt)
  exact (smtEval_or_lhs_none _ _ hLt).symm

/-! ## "Miss" correlation lemmas — failure-case ↔ none correspondences. -/

theorem correlateModel_lookupConst_none_of_state_miss (s : Schema) (st : State) (x : String)
    (h : st.lookupScalar x = none) :
    (correlateModel s st).lookupConst x = none := by
  unfold SmtModel.lookupConst correlateModel
  simp only []
  rw [lookup_map_value]
  unfold State.lookupScalar at h
  rw [h]; rfl

theorem correlateModel_lookupRel_none (s : Schema) (st : State) (relName : String)
    (h : st.relationDomain relName = none) :
    (correlateModel s st).lookupRel relName = none := by
  unfold SmtModel.lookupRel correlateModel
  simp only []
  rw [lookup_map_listValue]
  unfold State.relationDomain at h
  rw [h]; rfl

theorem correlateModel_lookupSortMembers_none (s : Schema) (st : State) (en : String)
    (h : s.lookupEnum en = none) :
    (correlateModel s st).lookupSortMembers en = none := by
  unfold SmtModel.lookupSortMembers correlateModel
  simp only []
  rw [lookup_map_pair_enumDecl]
  unfold Schema.lookupEnum at h
  rw [h]; rfl

/-! ## Universal soundness theorem.

  Structural induction over `Expr`. Each constructor delegates the success path to its
  per-case theorem and discharges the failure paths (eval returns `none`, or returns a
  value of the wrong shape for the operator) by showing both sides reduce to `none`. -/

/-! ## Universal soundness — case-by-case dispatch.

  For each `Expr` case, we specialize the `induction`-supplied IHs to the current `env`,
  then case-split on each subexpression's eval result. Success paths delegate to the
  per-case theorems. Failure paths (eval none, or wrong-shape value) reduce both sides
  to `none` via the `smtEval_*_none/_nonBool/_nonInt` characterization lemmas. -/

theorem soundness (e : Expr) :
    valueToSmt? (eval s st env e)
      = smtEval (correlateModel s st) (correlateEnv env) (translate e) := by
  induction e generalizing env with
  | boolLit b => exact soundness_boolLit s st env b
  | intLit n => exact soundness_intLit s st env n
  | ident name =>
    cases hEnv : Env.lookup env name with
    | some v => exact soundness_ident_local s st env hEnv
    | none =>
      cases hSt : st.lookupScalar name with
      | some v => exact soundness_ident_state s st env hEnv hSt
      | none =>
        rw [show eval s st env (.ident name) = none from by simp only [eval, hEnv, hSt]]
        rw [show translate (.ident name) = SmtTerm.var name from rfl]
        have henv' : SmtEnv.lookup (correlateEnv env) name = none := by
          rw [correlateEnv_lookup, hEnv]; rfl
        have hconst' : (correlateModel s st).lookupConst name = none :=
          correlateModel_lookupConst_none_of_state_miss s st name hSt
        simp only [valueToSmt?]
        simp only [smtEval, henv', hconst']
  | unNot e ih =>
    have ih := ih env
    rw [show translate (.unNot e) = SmtTerm.not (translate e) from rfl]
    cases h : eval s st env e with
    | none =>
      rw [show eval s st env (.unNot e) = none from by simp only [eval, h]]
      rw [h] at ih; simp only [valueToSmt?] at ih
      simp only [valueToSmt?]
      exact (smtEval_not_none _ _ _ ih.symm).symm
    | some v =>
      cases v with
      | vBool b => exact soundness_unNot_bool s st env e b ih h
      | vInt n =>
        rw [show eval s st env (.unNot e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vInt n) = SmtVal.sInt n from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_not_nonBool _ _ ih.symm (fun b => by intro h; cases h)).symm
      | vEnum en' m' =>
        rw [show eval s st env (.unNot e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_not_nonBool _ _ ih.symm (fun b => by intro h; cases h)).symm
      | vEntity en' i' =>
        rw [show eval s st env (.unNot e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_not_nonBool _ _ ih.symm (fun b => by intro h; cases h)).symm
  | unNeg e ih =>
    have ih := ih env
    rw [show translate (.unNeg e) = SmtTerm.neg (translate e) from rfl]
    cases h : eval s st env e with
    | none =>
      rw [show eval s st env (.unNeg e) = none from by simp only [eval, h]]
      rw [h] at ih; simp only [valueToSmt?] at ih
      simp only [valueToSmt?]
      exact (smtEval_neg_none _ _ _ ih.symm).symm
    | some v =>
      cases v with
      | vInt n => exact soundness_unNeg_int s st env e n ih h
      | vBool b =>
        rw [show eval s st env (.unNeg e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vBool b) = SmtVal.sBool b from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_neg_nonInt _ _ ih.symm (fun n => by intro h; cases h)).symm
      | vEnum en' m' =>
        rw [show eval s st env (.unNeg e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vEnum en' m') = SmtVal.sEnumElem en' m' from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_neg_nonInt _ _ ih.symm (fun n => by intro h; cases h)).symm
      | vEntity en' i' =>
        rw [show eval s st env (.unNeg e) = none from by simp only [eval, h]]
        rw [h] at ih; rw [valueToSmt?_some] at ih
        rw [show valueToSmt (Value.vEntity en' i') = SmtVal.sEntityElem en' i' from rfl] at ih
        simp only [valueToSmt?]
        exact (smtEval_neg_nonInt _ _ ih.symm (fun n => by intro h; cases h)).symm
  | boolBin op l r ihL ihR =>
    have ihLE := ihL env
    have ihRE := ihR env
    cases hL : eval s st env l with
    | none =>
      rw [show eval s st env (.boolBin op l r) = none from by simp only [eval, hL]]
      rw [hL] at ihLE; simp only [valueToSmt?] at ihLE
      simp only [valueToSmt?]
      cases op with
      | and =>
        rw [show translate (.boolBin .and l r) = .and (translate l) (translate r) from rfl]
        exact (smtEval_and_lhs_none _ _ ihLE.symm).symm
      | or =>
        rw [show translate (.boolBin .or l r) = .or (translate l) (translate r) from rfl]
        exact (smtEval_or_lhs_none _ _ ihLE.symm).symm
      | implies =>
        rw [show translate (.boolBin .implies l r) = .implies (translate l) (translate r) from rfl]
        exact (smtEval_implies_lhs_none _ _ ihLE.symm).symm
      | iff =>
        rw [show translate (.boolBin .iff l r)
              = .and (.implies (translate l) (translate r))
                     (.implies (translate r) (translate l)) from rfl]
        have h_lr : smtEval (correlateModel s st) (correlateEnv env)
                      (.implies (translate l) (translate r)) = none :=
          smtEval_implies_lhs_none _ _ ihLE.symm
        exact (smtEval_and_lhs_none _ _ h_lr).symm
    | some lv =>
      cases lv with
      | vBool a =>
        cases hR : eval s st env r with
        | none =>
          rw [show eval s st env (.boolBin op l r) = none from by simp only [eval, hL, hR]]
          rw [hL] at ihLE; rw [valueToSmt?_some] at ihLE
          rw [show valueToSmt (Value.vBool a) = SmtVal.sBool a from rfl] at ihLE
          rw [hR] at ihRE; simp only [valueToSmt?] at ihRE
          simp only [valueToSmt?]
          cases op with
          | and =>
            rw [show translate (.boolBin .and l r) = .and (translate l) (translate r) from rfl]
            exact (smtEval_and_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | or =>
            rw [show translate (.boolBin .or l r) = .or (translate l) (translate r) from rfl]
            exact (smtEval_or_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | implies =>
            rw [show translate (.boolBin .implies l r)
                  = .implies (translate l) (translate r) from rfl]
            exact (smtEval_implies_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | iff =>
            rw [show translate (.boolBin .iff l r)
                  = .and (.implies (translate l) (translate r))
                         (.implies (translate r) (translate l)) from rfl]
            have h_lr : smtEval (correlateModel s st) (correlateEnv env)
                          (.implies (translate l) (translate r)) = none :=
              smtEval_implies_rhs_none _ _ ihLE.symm ihRE.symm
            exact (smtEval_and_lhs_none _ _ h_lr).symm
        | some rv =>
          cases rv with
          | vBool b =>
            cases op with
            | and => exact soundness_boolBin_and_bools s st env l r a b ihLE ihRE hL hR
            | or  => exact soundness_boolBin_or_bools  s st env l r a b ihLE ihRE hL hR
            | implies => exact soundness_boolBin_implies_bools s st env l r a b ihLE ihRE hL hR
            | iff => exact soundness_boolBin_iff_bools s st env l r a b ihLE ihRE hL hR
          | vInt n => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a (.vInt n)
                              (fun b => by intro h; cases h) ihLE ihRE hL hR
          | vEnum en' m' => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a
                                    (.vEnum en' m') (fun b => by intro h; cases h) ihLE ihRE hL hR
          | vEntity en' i' => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a
                                      (.vEntity en' i') (fun b => by intro h; cases h)
                                      ihLE ihRE hL hR
      | vInt n =>
        exact boolBin_lhs_nonBool s st env op l r (.vInt n)
                (fun b => by intro h; cases h) ihLE ihRE hL
      | vEnum en' m' =>
        exact boolBin_lhs_nonBool s st env op l r (.vEnum en' m')
                (fun b => by intro h; cases h) ihLE ihRE hL
      | vEntity en' i' =>
        exact boolBin_lhs_nonBool s st env op l r (.vEntity en' i')
                (fun b => by intro h; cases h) ihLE ihRE hL
  | arith op l r ihL ihR =>
    have ihLE := ihL env
    have ihRE := ihR env
    cases hL : eval s st env l with
    | none =>
      rw [show eval s st env (.arith op l r) = none from by
            simp only [eval, hL]; cases op <;> rfl]
      rw [hL] at ihLE; simp only [valueToSmt?] at ihLE
      simp only [valueToSmt?]
      cases op with
      | add =>
        rw [show translate (.arith .add l r) = SmtTerm.add (translate l) (translate r) from rfl]
        exact (smtEval_add_lhs_none _ _ ihLE.symm).symm
      | sub =>
        rw [show translate (.arith .sub l r) = SmtTerm.sub (translate l) (translate r) from rfl]
        exact (smtEval_sub_lhs_none _ _ ihLE.symm).symm
      | mul =>
        rw [show translate (.arith .mul l r) = SmtTerm.mul (translate l) (translate r) from rfl]
        exact (smtEval_mul_lhs_none _ _ ihLE.symm).symm
      | div =>
        rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
        exact (smtEval_div_lhs_none _ _ ihLE.symm).symm
    | some lv =>
      cases lv with
      | vInt a =>
        cases hR : eval s st env r with
        | none =>
          -- Substitute IHs locally for the failure branch.
          have ihLE' := ihLE
          have ihRE' := ihRE
          rw [hL] at ihLE'; rw [valueToSmt?_some] at ihLE'
          rw [show valueToSmt (Value.vInt a) = SmtVal.sInt a from rfl] at ihLE'
          rw [hR] at ihRE'; simp only [valueToSmt?] at ihRE'
          rw [show eval s st env (.arith op l r) = none from by
                simp only [eval, hL, hR]; cases op <;> rfl]
          simp only [valueToSmt?]
          cases op with
          | add =>
            rw [show translate (.arith .add l r) = SmtTerm.add (translate l) (translate r) from rfl]
            exact (smtEval_add_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | sub =>
            rw [show translate (.arith .sub l r) = SmtTerm.sub (translate l) (translate r) from rfl]
            exact (smtEval_sub_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | mul =>
            rw [show translate (.arith .mul l r) = SmtTerm.mul (translate l) (translate r) from rfl]
            exact (smtEval_mul_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | div =>
            rw [show translate (.arith .div l r) = SmtTerm.div (translate l) (translate r) from rfl]
            exact (smtEval_div_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
        | some rv =>
          cases rv with
          | vInt b =>
            cases op with
            | add => exact soundness_arith_add_ints s st env l r a b ihLE ihRE hL hR
            | sub => exact soundness_arith_sub_ints s st env l r a b ihLE ihRE hL hR
            | mul => exact soundness_arith_mul_ints s st env l r a b ihLE ihRE hL hR
            | div =>
              by_cases hbz : b = 0
              · subst hbz
                exact soundness_arith_div_ints_zero s st env l r a ihLE ihRE hL hR
              · exact soundness_arith_div_ints_nonZero s st env l r a b hbz ihLE ihRE hL hR
          | vBool b => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vBool b)
                          (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vEnum en m => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vEnum en m)
                            (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vEntity en i => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vEntity en i)
                              (fun n => by intro h; cases h) ihLE ihRE hL hR
      | vBool b => exact arith_lhs_nonInt s st env op l r (.vBool b)
                    (fun n => by intro h; cases h) ihLE ihRE hL
      | vEnum en m => exact arith_lhs_nonInt s st env op l r (.vEnum en m)
                       (fun n => by intro h; cases h) ihLE ihRE hL
      | vEntity en i => exact arith_lhs_nonInt s st env op l r (.vEntity en i)
                          (fun n => by intro h; cases h) ihLE ihRE hL
  | cmp op l r ihL ihR =>
    have ihLE := ihL env
    have ihRE := ihR env
    cases hL : eval s st env l with
    | none =>
      exact cmp_lhs_eval_none s st env op l r ihLE ihRE hL
    | some lv =>
      cases hR : eval s st env r with
      | none => exact cmp_rhs_eval_none s st env op l r lv ihLE ihRE hL hR
      | some rv =>
        cases op with
        | eq => exact soundness_cmp_eq_vals s st env l r lv rv ihLE ihRE hL hR
        | neq => exact soundness_cmp_neq_vals s st env l r lv rv ihLE ihRE hL hR
        | lt =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_lt_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEnum en m => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEntity en i => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vBool b => exact cmp_lt_lhs_nonInt s st env l r (.vBool b)
                        (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_lt_lhs_nonInt s st env l r (.vEnum en m)
                            (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_lt_lhs_nonInt s st env l r (.vEntity en i)
                              (fun n => by intro h; cases h) ihLE ihRE hL rv hR
        | le =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_le_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEnum en m => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEntity en i => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vBool b => exact cmp_le_lhs_nonInt s st env l r (.vBool b)
                        (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_le_lhs_nonInt s st env l r (.vEnum en m)
                            (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_le_lhs_nonInt s st env l r (.vEntity en i)
                              (fun n => by intro h; cases h) ihLE ihRE hL rv hR
        | gt =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_gt_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEnum en m => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEntity en i => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vBool b => exact cmp_gt_lhs_nonInt s st env l r (.vBool b)
                        (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_gt_lhs_nonInt s st env l r (.vEnum en m)
                            (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_gt_lhs_nonInt s st env l r (.vEntity en i)
                              (fun n => by intro h; cases h) ihLE ihRE hL rv hR
        | ge =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_ge_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEnum en m => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              (fun n => by intro h; cases h) ihLE ihRE hL hR
            | vEntity en i => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                (fun n => by intro h; cases h) ihLE ihRE hL hR
          | vBool b => exact cmp_ge_lhs_nonInt s st env l r (.vBool b)
                        (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_ge_lhs_nonInt s st env l r (.vEnum en m)
                            (fun n => by intro h; cases h) ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_ge_lhs_nonInt s st env l r (.vEntity en i)
                              (fun n => by intro h; cases h) ihLE ihRE hL rv hR
  | letIn x value body ihV ihB =>
    have ihV := ihV env
    cases hV : eval s st env value with
    | none =>
      rw [show eval s st env (.letIn x value body) = none from by simp only [eval, hV]]
      rw [show translate (.letIn x value body)
            = SmtTerm.letIn x (translate value) (translate body) from rfl]
      rw [hV] at ihV; simp only [valueToSmt?] at ihV
      simp only [valueToSmt?]
      exact (smtEval_letIn_none _ _ ihV.symm).symm
    | some v =>
      have hSubBody : valueToSmt? (eval s st ((x, v) :: env) body)
                    = smtEval (correlateModel s st) (correlateEnv ((x, v) :: env)) (translate body) :=
        ihB ((x, v) :: env)
      rw [show correlateEnv ((x, v) :: env)
            = (x, valueToSmt v) :: correlateEnv env from rfl] at hSubBody
      exact soundness_letIn s st env x value body v ihV hV hSubBody
  | enumAccess en m =>
    cases hSchema : s.lookupEnum en with
    | some d =>
      by_cases hMember : d.members.contains m = true
      · exact soundness_enumAccess_known s st env en m d hSchema hMember
      · have hMember' : d.members.contains m = false := by
          cases hc : d.members.contains m
          · rfl
          · exact absurd hc hMember
        rw [show eval s st env (.enumAccess en m) = none from by
          simp only [eval, hSchema, hMember']
          rfl]
        rw [show translate (.enumAccess en m) = SmtTerm.enumElemConst en m from rfl]
        have hSort : (correlateModel s st).lookupSortMembers en = some d.members :=
          correlateModel_lookupSortMembers s st en d hSchema
        simp only [valueToSmt?]
        exact (smtEval_enumElemConst_nonMember _ _ hSort hMember').symm
    | none =>
      rw [show eval s st env (.enumAccess en m) = none from by simp only [eval, hSchema]]
      rw [show translate (.enumAccess en m) = SmtTerm.enumElemConst en m from rfl]
      have hSort : (correlateModel s st).lookupSortMembers en = none :=
        correlateModel_lookupSortMembers_none s st en hSchema
      simp only [valueToSmt?]
      exact (smtEval_enumElemConst_unknown _ _ hSort).symm
  | member elem relName ihE =>
    have ihE := ihE env
    cases hElem : eval s st env elem with
    | none =>
      rw [show eval s st env (.member elem relName) = none from by simp only [eval, hElem]]
      rw [show translate (.member elem relName) = SmtTerm.inDom relName (translate elem) from rfl]
      rw [hElem] at ihE; simp only [valueToSmt?] at ihE
      simp only [valueToSmt?]
      exact (smtEval_inDom_arg_none _ _ ihE.symm).symm
    | some v =>
      cases hDom : st.relationDomain relName with
      | some dom => exact soundness_member_resolved s st env elem relName v dom ihE hElem hDom
      | none =>
        rw [show eval s st env (.member elem relName) = none from by
          simp only [eval, hElem, hDom]]
        rw [show translate (.member elem relName) = SmtTerm.inDom relName (translate elem) from rfl]
        rw [hElem] at ihE; rw [valueToSmt?_some] at ihE
        have hRel : (correlateModel s st).lookupRel relName = none :=
          correlateModel_lookupRel_none s st relName hDom
        simp only [valueToSmt?]
        exact (smtEval_inDom_rel_none _ _ ihE.symm hRel).symm
  | cardRel relName =>
    cases hDom : st.relationDomain relName with
    | some dom =>
      have hEval : eval s st env (.cardRel relName)
                 = some (.vInt (Int.ofNat dom.length)) := by
        simp only [eval, hDom]
      rw [hEval, valueToSmt?_some]
      rw [show valueToSmt (Value.vInt (Int.ofNat dom.length))
            = SmtVal.sInt (Int.ofNat dom.length) from rfl]
      rw [show translate (.cardRel relName) = SmtTerm.cardRel relName from rfl]
      have hRel : (correlateModel s st).lookupRel relName
                = some (dom.map valueToSmt) :=
        correlateModel_lookupRel s st relName dom hDom
      rw [smtEval_cardRel_resolved _ _ relName (dom.map valueToSmt) hRel]
      rw [List.length_map]
    | none =>
      have hEval : eval s st env (.cardRel relName) = none := by
        simp only [eval, hDom]
      rw [hEval]
      rw [show translate (.cardRel relName) = SmtTerm.cardRel relName from rfl]
      have hRel : (correlateModel s st).lookupRel relName = none :=
        correlateModel_lookupRel_none s st relName hDom
      simp only [valueToSmt?]
      exact (smtEval_cardRel_unknown _ _ relName hRel).symm
  | prime e ih =>
    -- Single-state collapse: Prime is identity at the eval and translate levels.
    -- True two-state semantics (where Prime resolves to post-state scalars) is
    -- M_L.4.b-ext, gated on the StatePair carrier refactor.
    have ih := ih env
    rw [eval_prime]
    rw [show translate (.prime e) = translate e from rfl]
    exact ih
  | pre e ih =>
    -- Single-state collapse: Pre is identity. Two-state machinery is M_L.4.b-ext.
    have ih := ih env
    rw [eval_pre]
    rw [show translate (.pre e) = translate e from rfl]
    exact ih
  | forallEnum var en body ihB =>
    cases hSchema : s.lookupEnum en with
    | some d =>
      have hBodyIH' : ∀ (val : Value),
          valueToSmt? (eval s st ((var, val) :: env) body)
            = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
                (translate body) := by
        intro val
        have := ihB ((var, val) :: env)
        rw [show correlateEnv ((var, val) :: env)
              = (var, valueToSmt val) :: correlateEnv env from rfl] at this
        exact this
      exact soundness_forallEnum_known s st env var en body d hSchema hBodyIH'
    | none =>
      rw [show eval s st env (.forallEnum var en body) = none from by simp only [eval, hSchema]]
      rw [show translate (.forallEnum var en body)
            = SmtTerm.forallEnum var en (translate body) from rfl]
      have hSort : (correlateModel s st).lookupSortMembers en = none :=
        correlateModel_lookupSortMembers_none s st en hSchema
      simp only [valueToSmt?]
      exact (smtEval_forallEnum_unknown _ _ hSort).symm
  | forallRel var rel body ihB =>
    cases hDom : st.relationDomain rel with
    | some dom =>
      have hBodyIH' : ∀ (val : Value),
          valueToSmt? (eval s st ((var, val) :: env) body)
            = smtEval (correlateModel s st) ((var, valueToSmt val) :: correlateEnv env)
                (translate body) := by
        intro val
        have := ihB ((var, val) :: env)
        rw [show correlateEnv ((var, val) :: env)
              = (var, valueToSmt val) :: correlateEnv env from rfl] at this
        exact this
      exact soundness_forallRel_known s st env var rel body dom hDom hBodyIH'
    | none =>
      rw [show eval s st env (.forallRel var rel body) = none from by simp only [eval, hDom]]
      rw [show translate (.forallRel var rel body)
            = SmtTerm.forallRel var rel (translate body) from rfl]
      have hRel : (correlateModel s st).lookupRel rel = none :=
        correlateModel_lookupRel_none s st rel hDom
      simp only [valueToSmt?]
      exact (smtEval_forallRel_unknown _ _ hRel).symm
  | indexRel relName key ihKey =>
    -- Index over a state-relation pair table. The key sub-expression's IH lifts to the
    -- correlated env via the structural induction. lookupKey_correlated bridges the
    -- Lean/SMT lookup tables.
    have ihKey := ihKey env
    cases hKey : eval s st env key with
    | none =>
      have hEval : eval s st env (.indexRel relName key) = none := by
        simp only [eval, hKey]
      rw [hEval]
      rw [show translate (.indexRel relName key)
            = SmtTerm.indexRel relName (translate key) from rfl]
      rw [hKey] at ihKey
      simp only [valueToSmt?] at ihKey
      simp only [valueToSmt?]
      exact (smtEval_indexRel_key_none _ _ ihKey.symm).symm
    | some kv =>
      have hEval : eval s st env (.indexRel relName key)
                  = st.lookupKey relName kv := by
        simp only [eval, hKey]
      rw [hEval]
      rw [show translate (.indexRel relName key)
            = SmtTerm.indexRel relName (translate key) from rfl]
      rw [hKey] at ihKey
      simp only [valueToSmt?_some] at ihKey
      rw [smtEval_indexRel_resolved _ _ relName (translate key) (valueToSmt kv) ihKey.symm]
      exact lookupKey_correlated s st relName kv
  | fieldAccess scalarName fieldName =>
    -- FieldAccess on an entity-typed state scalar. No sub-expression IH needed: both sides are
    -- direct lookups into parallel tables. lookupField_correlated bridges them.
    rw [eval_fieldAccess]
    rw [show translate (.fieldAccess scalarName fieldName)
          = SmtTerm.fieldAccess scalarName fieldName from rfl]
    rw [smtEval_fieldAccess _ _ scalarName fieldName]
    exact lookupField_correlated s st scalarName fieldName

end SpecRest
