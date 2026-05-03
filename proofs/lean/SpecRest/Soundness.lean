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

mutual
  def valueToSmt : Value → SmtVal
    | .vBool b       => .sBool b
    | .vInt n        => .sInt n
    | .vEnum en mem  => .sEnumElem en mem
    | .vEntity en id => .sEntityElem en id
    | .vSet members  => .sSet (valuesToSmt members)

  def valuesToSmt : List Value → List SmtVal
    | []        => []
    | v :: rest => valueToSmt v :: valuesToSmt rest
end

@[simp] theorem valuesToSmt_eq_map (members : List Value) :
    valuesToSmt members = members.map valueToSmt := by
  induction members with
  | nil => rfl
  | cons _ rest ih => simp only [valuesToSmt, List.map, ih]

@[simp] theorem valueToSmt_vBool (b : Bool) :
    valueToSmt (.vBool b) = .sBool b := rfl

@[simp] theorem valueToSmt_vInt (n : Int) :
    valueToSmt (.vInt n) = .sInt n := rfl

@[simp] theorem valueToSmt_vEnum (en mem : String) :
    valueToSmt (.vEnum en mem) = .sEnumElem en mem := rfl

@[simp] theorem valueToSmt_vEntity (en id : String) :
    valueToSmt (.vEntity en id) = .sEntityElem en id := rfl

@[simp] theorem valueToSmt_vSet (members : List Value) :
    valueToSmt (.vSet members) = .sSet (members.map valueToSmt) := by
  simp only [valueToSmt, valuesToSmt_eq_map]

def valueToSmt? : Option Value → Option SmtVal
  | some v => some (valueToSmt v)
  | none   => none

@[simp] theorem valueToSmt?_some (v : Value) :
    valueToSmt? (some v) = some (valueToSmt v) := rfl

@[simp] theorem valueToSmt?_none :
    valueToSmt? (none : Option Value) = none := rfl

mutual
  theorem valueToSmt_inj : ∀ {a b : Value}, valueToSmt a = valueToSmt b → a = b
    | .vBool a, .vBool b, h => by cases h; rfl
    | .vBool _, .vInt _, h => by cases h
    | .vBool _, .vEnum _ _, h => by cases h
    | .vBool _, .vEntity _ _, h => by cases h
    | .vBool _, .vSet _, h => by cases h
    | .vInt _, .vBool _, h => by cases h
    | .vInt a, .vInt b, h => by cases h; rfl
    | .vInt _, .vEnum _ _, h => by cases h
    | .vInt _, .vEntity _ _, h => by cases h
    | .vInt _, .vSet _, h => by cases h
    | .vEnum _ _, .vBool _, h => by cases h
    | .vEnum _ _, .vInt _, h => by cases h
    | .vEnum en mem, .vEnum en' mem', h => by cases h; rfl
    | .vEnum _ _, .vEntity _ _, h => by cases h
    | .vEnum _ _, .vSet _, h => by cases h
    | .vEntity _ _, .vBool _, h => by cases h
    | .vEntity _ _, .vInt _, h => by cases h
    | .vEntity _ _, .vEnum _ _, h => by cases h
    | .vEntity en id, .vEntity en' id', h => by cases h; rfl
    | .vEntity _ _, .vSet _, h => by cases h
    | .vSet _, .vBool _, h => by cases h
    | .vSet _, .vInt _, h => by cases h
    | .vSet _, .vEnum _ _, h => by cases h
    | .vSet _, .vEntity _ _, h => by cases h
    | .vSet xs, .vSet ys, h => by
        injection h with hList
        have hValues : xs = ys := valuesToSmt_inj hList
        cases hValues
        rfl

  theorem valuesToSmt_inj :
      ∀ {xs ys : List Value}, valuesToSmt xs = valuesToSmt ys → xs = ys
    | [], [], _ => rfl
    | [], _ :: _, h => by cases h
    | _ :: _, [], h => by cases h
    | x :: xs, y :: ys, h => by
        simp only [valuesToSmt, List.cons.injEq] at h
        have hHead : x = y := valueToSmt_inj h.1
        have hTail : xs = ys := valuesToSmt_inj h.2
        cases hHead
        cases hTail
        rfl
end

/-- `SmtVal.sInt` boolean equality unfolds to `Int` boolean equality. -/
theorem sInt_beq (a b : Int) : (SmtVal.sInt a == SmtVal.sInt b) = (a == b) := by
  rfl

theorem valueToSmt_decide_eq (a b : Value) :
    (decide (valueToSmt a = valueToSmt b) : Bool) = decide (a = b) := by
  by_cases h : a = b
  · subst h
    simp
  · have hMapped : valueToSmt a ≠ valueToSmt b := by
      intro hb
      exact h (valueToSmt_inj hb)
    simp [h, hMapped]

theorem containsValueForBeq_map_valueToSmt (members : List Value) (v : Value) :
    containsSmtValForBeq (members.map valueToSmt) (valueToSmt v) =
      containsValueForBeq members v := by
  induction members with
  | nil => rfl
  | cons hd rest ih =>
      simp only [List.map, containsSmtValForBeq, containsValueForBeq]
      rw [valueToSmt_decide_eq hd v, ih]

theorem subsetValueList_map_valueToSmt (xs ys : List Value) :
    subsetSmtValList (xs.map valueToSmt) (ys.map valueToSmt) =
      subsetValueList xs ys := by
  induction xs with
  | nil => rfl
  | cons hd rest ih =>
      simp only [List.map, subsetSmtValList, subsetValueList]
      rw [containsValueForBeq_map_valueToSmt ys hd, ih]

theorem setEqValueList_map_valueToSmt (xs ys : List Value) :
    setEqSmtValList (xs.map valueToSmt) (ys.map valueToSmt) =
      setEqValueList xs ys := by
  simp only [setEqSmtValList, setEqValueList]
  rw [subsetValueList_map_valueToSmt xs ys, subsetValueList_map_valueToSmt ys xs]

/-- Boolean equality on `Value` agrees with boolean equality on `SmtVal` after `valueToSmt`. -/
theorem valueToSmt_beq (a b : Value) : (valueToSmt a == valueToSmt b) = (a == b) := by
  cases a <;> cases b <;> try rfl
  simp [valueToSmt, valuesToSmt_eq_map]
  exact setEqValueList_map_valueToSmt _ _

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

theorem containsValue_map_valueToSmt (members : List Value) (v : Value) :
    containsSmtVal (members.map valueToSmt) (valueToSmt v) = containsValue members v := by
  induction members with
  | nil => rfl
  | cons hd rest ih =>
      simp only [List.map, containsSmtVal, containsValue]
      rw [valueToSmt_beq hd v, ih]

theorem dedupeValues_map_valueToSmt (members : List Value) :
    (dedupeValues members).map valueToSmt = dedupeSmtVals (members.map valueToSmt) := by
  induction members with
  | nil => rfl
  | cons hd rest ih =>
      simp only [dedupeValues, dedupeSmtVals, List.map]
      rw [← ih, containsValue_map_valueToSmt]
      cases containsValue (dedupeValues rest) hd <;> rfl

theorem filter_containsValue_map_valueToSmt (l r : List Value) :
    (l.filter (fun v => containsValue r v)).map valueToSmt =
      (l.map valueToSmt).filter (fun v => containsSmtVal (r.map valueToSmt) v) := by
  induction l with
  | nil => rfl
  | cons hd rest ih =>
      simp only [List.filter, List.map]
      rw [containsValue_map_valueToSmt r hd]
      cases containsValue r hd <;> simp only [List.map, ih]

theorem filter_not_containsValue_map_valueToSmt (l r : List Value) :
    (l.filter (fun v => !containsValue r v)).map valueToSmt =
      (l.map valueToSmt).filter (fun v => !containsSmtVal (r.map valueToSmt) v) := by
  induction l with
  | nil => rfl
  | cons hd rest ih =>
      simp only [List.filter, List.map]
      rw [containsValue_map_valueToSmt r hd]
      cases containsValue r hd <;> simp only [Bool.not_true, Bool.not_false, List.map, ih]

theorem setUnionValues_map_valueToSmt (l r : List Value) :
    (setUnionValues l r).map valueToSmt =
      setUnionSmtVals (l.map valueToSmt) (r.map valueToSmt) := by
  simp only [setUnionValues, setUnionSmtVals, ← List.map_append, dedupeValues_map_valueToSmt]

theorem setIntersectValues_map_valueToSmt (l r : List Value) :
    (setIntersectValues l r).map valueToSmt =
      setIntersectSmtVals (l.map valueToSmt) (r.map valueToSmt) := by
  simp only [setIntersectValues, setIntersectSmtVals]
  rw [dedupeValues_map_valueToSmt, filter_containsValue_map_valueToSmt]

theorem setDiffValues_map_valueToSmt (l r : List Value) :
    (setDiffValues l r).map valueToSmt =
      setDiffSmtVals (l.map valueToSmt) (r.map valueToSmt) := by
  simp only [setDiffValues, setDiffSmtVals]
  rw [dedupeValues_map_valueToSmt, filter_not_containsValue_map_valueToSmt]

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

/-- `lookupField` correlates: looking up a field in the Lean state yields the SmtVal corresponding
    to looking up the same (entity-id, field) pair in the correlated SmtModel's `predFields` table.
    M_L.4.k changed the outer key semantics from scalar-name to entity-id; the proof is unchanged
    because both `entityFields` and `predFields` parallel-share the same shape. -/
theorem lookupField_correlated (s : Schema) (st : State) (entityId fieldName : String) :
    valueToSmt? (st.lookupField entityId fieldName)
      = (correlateModel s st).lookupField entityId fieldName := by
  unfold State.lookupField SmtModel.lookupField
  unfold correlateModel
  simp only []
  rw [lookup_map_entityFields]
  cases hLookup : List.lookup entityId st.entityFields with
  | none => simp only [Option.map_none, valueToSmt?_none]
  | some fields =>
    simp only [Option.map_some]
    rw [lookup_map_value]
    cases List.lookup fieldName fields with
    | none   => rfl
    | some _ => rfl

/-! ## Value-shape lifting helpers.

These lift `Value`-level "not constructor X" hypotheses to their `SmtVal` mirror,
i.e. `(∀ n, lv ≠ .vInt n) → (∀ n, valueToSmt lv ≠ .sInt n)`. Used by every
boolBin/cmp/arith/setBin/unNot/unNeg failure-path helper. -/

private theorem hSmtLvNotBool (lv : Value) (hNotBool : ∀ b, lv ≠ .vBool b) :
    ∀ b, valueToSmt lv ≠ .sBool b := by
  intro b heq
  cases lv with
  | vBool k =>
    have : k = b := by injection heq
    subst this; exact hNotBool k rfl
  | _ => simp [valueToSmt] at heq

private theorem hSmtLvNotInt (lv : Value) (hNotInt : ∀ n, lv ≠ .vInt n) :
    ∀ n, valueToSmt lv ≠ .sInt n := by
  intro n heq
  cases lv with
  | vInt k =>
    have : k = n := by injection heq
    subst this; exact hNotInt k rfl
  | _ => simp [valueToSmt] at heq

private theorem hSmtValNotSet (v : Value) (hNotSet : ∀ members, v ≠ .vSet members) :
    ∀ members, valueToSmt v ≠ .sSet members := by
  intro members heq
  cases v with
  | vSet xs => exact hNotSet xs rfl
  | _ => simp [valueToSmt] at heq

/-! ## Soundness — case-by-case -/

variable (s : Schema) (st : State) (env : Env)

/-- Atomic: boolean literal. -/
theorem soundness_boolLit (b : Bool) :
    valueToSmt? (eval s st env (.boolLit b))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.boolLit b)) := by
  rw [eval_boolLit, valueToSmt?_some]
  show some (valueToSmt (.vBool b)) = _
  show some (SmtVal.sBool b) = _
  simp only [translate]
  rw [smtEval_bLit]

/-- Atomic: integer literal. -/
theorem soundness_intLit (n : Int) :
    valueToSmt? (eval s st env (.intLit n))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.intLit n)) := by
  rw [eval_intLit, valueToSmt?_some]
  show some (SmtVal.sInt n) = _
  simp only [translate]
  rw [smtEval_iLit]

/-- Identifier — env hit. The local-binding path is the easier of the two. -/
theorem soundness_ident_local {x : String} {v : Value}
    (h : Env.lookup env x = some v) :
    valueToSmt? (eval s st env (.ident x))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.ident x)) := by
  rw [eval_ident_local s st env h, valueToSmt?_some]
  simp only [translate]
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
  simp only [translate]
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
  simp only [translate]
  rw [hEval] at hSub
  rw [valueToSmt?_some] at hSub
  show some (SmtVal.sInt (-n)) = smtEval _ _ (.neg (translate e))
  rw [smtEval_neg_int _ _ (translate e) n hSub.symm]

/-- Unary `not` over a non-boolean sub-result — both sides yield `none`. -/
private theorem soundness_unNot_nonBool (e : Expr) (v : Value)
    (hNotBool : ∀ b, v ≠ .vBool b)
    (ih : valueToSmt? (eval s st env e)
            = smtEval (correlateModel s st) (correlateEnv env) (translate e))
    (h : eval s st env e = some v) :
    valueToSmt? (eval s st env (.unNot e))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.unNot e)) := by
  have hEvalNone : eval s st env (.unNot e) = none := by
    simp only [eval, h]
    cases v with
    | vBool b => exact absurd rfl (hNotBool b)
    | _ => rfl
  rw [hEvalNone]
  simp only [translate]
  rw [h] at ih; rw [valueToSmt?_some] at ih
  simp only [valueToSmt?]
  exact (smtEval_not_nonBool _ _ ih.symm (hSmtLvNotBool v hNotBool)).symm

/-- Unary `negate` over a non-integer sub-result — both sides yield `none`. -/
private theorem soundness_unNeg_nonInt (e : Expr) (v : Value)
    (hNotInt : ∀ n, v ≠ .vInt n)
    (ih : valueToSmt? (eval s st env e)
            = smtEval (correlateModel s st) (correlateEnv env) (translate e))
    (h : eval s st env e = some v) :
    valueToSmt? (eval s st env (.unNeg e))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.unNeg e)) := by
  have hEvalNone : eval s st env (.unNeg e) = none := by
    simp only [eval, h]
    cases v with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  simp only [translate]
  rw [h] at ih; rw [valueToSmt?_some] at ih
  simp only [valueToSmt?]
  exact (smtEval_neg_nonInt _ _ ih.symm (hSmtLvNotInt v hNotInt)).symm

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
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a && b)) = _
  simp only [translate]
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
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a || b)) = _
  simp only [translate]
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
  simp only [evalBoolBin]
  show some (SmtVal.sBool (!a || b)) = _
  simp only [translate]
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
  simp only [translate]
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
  rw [valueToSmt_vInt]
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [valueToSmt_vInt] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubR
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
  rw [valueToSmt_vInt]
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [valueToSmt_vInt] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubR
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
  rw [valueToSmt_vInt]
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [valueToSmt_vInt] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubR
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
  rw [valueToSmt_vInt]
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [valueToSmt_vInt] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [valueToSmt_vInt] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
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
  simp only [translate]
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
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a == b)) = _
  simp only [translate]
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
  simp only [translate]
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
  simp only [translate]
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
  simp only [translate]
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
    rw [valueToSmt_vEnum] at hBody
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
        rw [valueToSmt_vBool] at hBody
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
            rw [valueToSmt_vBool] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [valueToSmt_vInt] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [valueToSmt_vEnum] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [valueToSmt_vEntity] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vSet members =>
            rw [valueToSmt_vSet members] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [valueToSmt_vInt] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [valueToSmt_vEnum] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [valueToSmt_vEntity] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vSet members =>
        rw [valueToSmt_vSet members] at hBody
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
  simp only [translate]
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
    rw [List.map_nil]
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
        rw [valueToSmt_vBool] at hBody
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
            rw [valueToSmt_vBool] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [valueToSmt_vInt] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [valueToSmt_vEnum] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [valueToSmt_vEntity] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vSet members =>
            rw [valueToSmt_vSet members] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [valueToSmt_vInt] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [valueToSmt_vEnum] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [valueToSmt_vEntity] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vSet members =>
        rw [valueToSmt_vSet members] at hBody
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
  simp only [translate]
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
  simp only [translate]
  rw [hV] at hSubV; rw [valueToSmt?_some] at hSubV
  rw [smtEval_letIn_some _ _ x (translate value) (translate body) (valueToSmt v) hSubV.symm]
  simp only [correlateEnv]
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
    | vSet _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotBool : ∀ b, valueToSmt lv ≠ .sBool b := hSmtLvNotBool lv hNotBool
  cases op with
  | and =>
    simp only [translate]
    exact (smtEval_and_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | or =>
    simp only [translate]
    exact (smtEval_or_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | implies =>
    simp only [translate]
    exact (smtEval_implies_lhs_nonBool _ _ ihL.symm hSmtNotBool).symm
  | iff =>
    simp only [translate]
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
    | vSet _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [valueToSmt_vBool] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotBool : ∀ b, valueToSmt rv ≠ .sBool b := hSmtLvNotBool rv hNotBool
  cases op with
  | and =>
    simp only [translate]
    exact (smtEval_and_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | or =>
    simp only [translate]
    exact (smtEval_or_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | implies =>
    simp only [translate]
    exact (smtEval_implies_rhs_nonBool _ _ ihL.symm ihR.symm hSmtNotBool).symm
  | iff =>
    simp only [translate]
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
    simp only [translate]
    exact (smtEval_eq_lhs_none _ _ ihL.symm).symm
  | neq =>
    simp only [translate]
    exact (smtEval_not_none _ _ _ (smtEval_eq_lhs_none _ _ ihL.symm)).symm
  | lt =>
    simp only [translate]
    exact (smtEval_lt_lhs_none _ _ ihL.symm).symm
  | le =>
    simp only [translate]
    have hLt : smtEval _ _ (.lt (translate l) (translate r)) = none :=
      smtEval_lt_lhs_none _ _ ihL.symm
    exact (smtEval_or_lhs_none _ _ hLt).symm
  | gt =>
    simp only [translate]
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
    simp only [translate]
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
    simp only [translate]
    exact (smtEval_eq_rhs_none _ _ ihL.symm ihR.symm).symm
  | neq =>
    simp only [translate]
    exact (smtEval_not_none _ _ _ (smtEval_eq_rhs_none _ _ ihL.symm ihR.symm)).symm
  | lt =>
    simp only [translate]
    -- LHS is some valueToSmt lv. Need to show smtEval_lt = none.
    -- If valueToSmt lv is sInt n, use smtEval_lt_rhs_none. Otherwise use smtEval_lt_lhs_nonInt.
    cases lv with
    | vInt a =>
      rw [valueToSmt_vInt] at ihL
      exact (smtEval_lt_rhs_none _ _ ihL.symm ihR.symm).symm
    | vBool b =>
      rw [valueToSmt_vBool] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm nofun).symm
    | vEnum en' m' =>
      rw [valueToSmt_vEnum] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm nofun).symm
    | vEntity en' i' =>
      rw [valueToSmt_vEntity] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm nofun).symm
    | vSet members =>
      rw [valueToSmt_vSet members] at ihL
      exact (smtEval_lt_lhs_nonInt _ _ ihL.symm nofun).symm
  | le =>
    simp only [translate]
    have hEq : smtEval _ _ (.eq (translate l) (translate r)) = none :=
      smtEval_eq_rhs_none _ _ ihL.symm ihR.symm
    have hLt : smtEval (correlateModel s st) (correlateEnv env)
                 (.lt (translate l) (translate r)) = none := by
      cases lv with
      | vInt a =>
        rw [valueToSmt_vInt] at ihL
        exact smtEval_lt_rhs_none _ _ ihL.symm ihR.symm
      | vBool b =>
        rw [valueToSmt_vBool] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm nofun
      | vEnum en' m' =>
        rw [valueToSmt_vEnum] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm nofun
      | vEntity en' i' =>
        rw [valueToSmt_vEntity] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm nofun
      | vSet members =>
        rw [valueToSmt_vSet members] at ihL
        exact smtEval_lt_lhs_nonInt _ _ ihL.symm nofun
    rw [smtEval, hLt, hEq]
  | gt =>
    simp only [translate]
    -- lhs of .lt is translate r, which evals to none. Use smtEval_lt_lhs_none.
    exact (smtEval_lt_lhs_none _ _ ihR.symm).symm
  | ge =>
    simp only [translate]
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
    | _ => cases rv <;> rfl
  rw [hEvalNone]
  simp only [translate]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt lv ≠ .sInt n := hSmtLvNotInt lv hNotInt
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
  simp only [translate]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [valueToSmt_vInt] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt rv ≠ .sInt n := hSmtLvNotInt rv hNotInt
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
  have hSmtNotInt : ∀ n, valueToSmt lv ≠ .sInt n := hSmtLvNotInt lv hNotInt
  cases op with
  | add =>
    simp only [translate]
    exact (smtEval_add_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | sub =>
    simp only [translate]
    exact (smtEval_sub_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | mul =>
    simp only [translate]
    exact (smtEval_mul_lhs_nonInt _ _ ihL.symm hSmtNotInt).symm
  | div =>
    simp only [translate]
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
  rw [valueToSmt_vInt] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt rv ≠ .sInt n := hSmtLvNotInt rv hNotInt
  cases op with
  | add =>
    simp only [translate]
    exact (smtEval_add_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | sub =>
    simp only [translate]
    exact (smtEval_sub_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | mul =>
    simp only [translate]
    exact (smtEval_mul_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm
  | div =>
    simp only [translate]
    exact (smtEval_div_rhs_nonInt _ _ ihL.symm ihR.symm hSmtNotInt).symm

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
    | _ => cases rv <;> rfl
  rw [hEvalNone]
  simp only [translate]
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
  simp only [translate]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [valueToSmt_vInt] at ihL
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
    | _ => cases rv <;> rfl
  rw [hEvalNone]
  simp only [translate]
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
      | sSet _ => rfl
    | sBool _ => rfl
    | sEnumElem _ _ => rfl
    | sEntityElem _ _ => rfl
    | sSet _ => rfl

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
  simp only [translate]
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
    | _ => cases rv <;> rfl
  rw [hEvalNone]
  simp only [translate]
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
        | sSet _ => rfl
      | sBool _ => rfl
      | sEnumElem _ _ => rfl
      | sEntityElem _ _ => rfl
      | sSet _ => rfl
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
  simp only [translate]
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

/-! ## Set-valued expression soundness helpers. -/

theorem soundness_setEmpty :
    valueToSmt? (eval s st env .setEmpty)
      = smtEval (correlateModel s st) (correlateEnv env) (translate .setEmpty) := by
  rw [eval_setEmpty, valueToSmt?_some, valueToSmt_vSet]
  simp only [translate]
  rw [smtEval_setEmpty]
  rfl

theorem soundness_setInsert_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hSubElem : valueToSmt? (eval s st env elem)
      = smtEval (correlateModel s st) (correlateEnv env) (translate elem))
    (hSubSet : valueToSmt? (eval s st env set)
      = smtEval (correlateModel s st) (correlateEnv env) (translate set))
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some (.vSet members)) :
    valueToSmt? (eval s st env (.setInsert elem set))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.setInsert elem set)) := by
  rw [eval_setInsert_resolved s st env elem set v members hElem hSet,
      valueToSmt?_some, valueToSmt_vSet]
  simp only [translate]
  have hElemSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate elem) = some (valueToSmt v) := by
    rw [← hSubElem, hElem, valueToSmt?_some]
  have hSetSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate set)
        = some (.sSet (members.map valueToSmt)) := by
    rw [← hSubSet, hSet, valueToSmt?_some, valueToSmt_vSet]
  rw [smtEval_setInsert_resolved _ _ (translate elem) (translate set)
        (valueToSmt v) (members.map valueToSmt) hElemSmt hSetSmt]
  simp only [dedupeValues_map_valueToSmt, List.map]

theorem soundness_setMember_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hSubElem : valueToSmt? (eval s st env elem)
      = smtEval (correlateModel s st) (correlateEnv env) (translate elem))
    (hSubSet : valueToSmt? (eval s st env set)
      = smtEval (correlateModel s st) (correlateEnv env) (translate set))
    (hElem : eval s st env elem = some v)
    (hSet : eval s st env set = some (.vSet members)) :
    valueToSmt? (eval s st env (.setMember elem set))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.setMember elem set)) := by
  rw [eval_setMember_resolved s st env elem set v members hElem hSet,
      valueToSmt?_some]
  show some (SmtVal.sBool (containsValue members v)) = _
  simp only [translate]
  have hElemSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate elem) = some (valueToSmt v) := by
    rw [← hSubElem, hElem, valueToSmt?_some]
  have hSetSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate set)
        = some (.sSet (members.map valueToSmt)) := by
    rw [← hSubSet, hSet, valueToSmt?_some, valueToSmt_vSet]
  rw [smtEval_setMember_resolved _ _ (translate elem) (translate set)
        (valueToSmt v) (members.map valueToSmt) hElemSmt hSetSmt]
  rw [containsValue_map_valueToSmt]

theorem soundness_setBin_sets (op : SetOp) (l r : Expr) (ls rs : List Value)
    (hSubL : valueToSmt? (eval s st env l)
      = smtEval (correlateModel s st) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (eval s st env r)
      = smtEval (correlateModel s st) (correlateEnv env) (translate r))
    (hL : eval s st env l = some (.vSet ls))
    (hR : eval s st env r = some (.vSet rs)) :
    valueToSmt? (eval s st env (.setBin op l r))
      = smtEval (correlateModel s st) (correlateEnv env) (translate (.setBin op l r)) := by
  have hLSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate l)
        = some (.sSet (ls.map valueToSmt)) := by
    rw [← hSubL, hL, valueToSmt?_some, valueToSmt_vSet]
  have hRSmt :
      smtEval (correlateModel s st) (correlateEnv env) (translate r)
        = some (.sSet (rs.map valueToSmt)) := by
    rw [← hSubR, hR, valueToSmt?_some, valueToSmt_vSet]
  cases op with
  | union =>
      rw [show eval s st env (.setBin .union l r)
            = some (.vSet (setUnionValues ls rs)) from by simp only [eval, hL, hR, evalSetBin]]
      rw [valueToSmt?_some, valueToSmt_vSet]
      simp only [translate]
      rw [smtEval_setUnion_sets _ _ (translate l) (translate r)
            (ls.map valueToSmt) (rs.map valueToSmt) hLSmt hRSmt]
      rw [setUnionValues_map_valueToSmt]
  | intersect =>
      rw [show eval s st env (.setBin .intersect l r)
            = some (.vSet (setIntersectValues ls rs)) from by simp only [eval, hL, hR, evalSetBin]]
      rw [valueToSmt?_some, valueToSmt_vSet]
      simp only [translate]
      rw [smtEval_setIntersect_sets _ _ (translate l) (translate r)
            (ls.map valueToSmt) (rs.map valueToSmt) hLSmt hRSmt]
      rw [setIntersectValues_map_valueToSmt]
  | diff =>
      rw [show eval s st env (.setBin .diff l r)
            = some (.vSet (setDiffValues ls rs)) from by simp only [eval, hL, hR, evalSetBin]]
      rw [valueToSmt?_some, valueToSmt_vSet]
      simp only [translate]
      rw [smtEval_setDiff_sets _ _ (translate l) (translate r)
            (ls.map valueToSmt) (rs.map valueToSmt) hLSmt hRSmt]
      rw [setDiffValues_map_valueToSmt]

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
        simp only [translate]
        have henv' : SmtEnv.lookup (correlateEnv env) name = none := by
          rw [correlateEnv_lookup, hEnv]; rfl
        have hconst' : (correlateModel s st).lookupConst name = none :=
          correlateModel_lookupConst_none_of_state_miss s st name hSt
        simp only [valueToSmt?]
        simp only [smtEval, henv', hconst']
  | unNot e ih =>
    have ih := ih env
    cases h : eval s st env e with
    | none =>
      rw [show eval s st env (.unNot e) = none from by simp only [eval, h]]
      simp only [translate]
      rw [h] at ih; simp only [valueToSmt?] at ih
      simp only [valueToSmt?]
      exact (smtEval_not_none _ _ _ ih.symm).symm
    | some v =>
      cases v with
      | vBool b => exact soundness_unNot_bool s st env e b ih h
      | vInt n => exact soundness_unNot_nonBool s st env e (.vInt n) nofun ih h
      | vEnum en' m' => exact soundness_unNot_nonBool s st env e (.vEnum en' m') nofun ih h
      | vEntity en' i' => exact soundness_unNot_nonBool s st env e (.vEntity en' i') nofun ih h
      | vSet members => exact soundness_unNot_nonBool s st env e (.vSet members) nofun ih h
  | unNeg e ih =>
    have ih := ih env
    cases h : eval s st env e with
    | none =>
      rw [show eval s st env (.unNeg e) = none from by simp only [eval, h]]
      simp only [translate]
      rw [h] at ih; simp only [valueToSmt?] at ih
      simp only [valueToSmt?]
      exact (smtEval_neg_none _ _ _ ih.symm).symm
    | some v =>
      cases v with
      | vInt n => exact soundness_unNeg_int s st env e n ih h
      | vBool b => exact soundness_unNeg_nonInt s st env e (.vBool b) nofun ih h
      | vEnum en' m' => exact soundness_unNeg_nonInt s st env e (.vEnum en' m') nofun ih h
      | vEntity en' i' => exact soundness_unNeg_nonInt s st env e (.vEntity en' i') nofun ih h
      | vSet members => exact soundness_unNeg_nonInt s st env e (.vSet members) nofun ih h
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
        simp only [translate]
        exact (smtEval_and_lhs_none _ _ ihLE.symm).symm
      | or =>
        simp only [translate]
        exact (smtEval_or_lhs_none _ _ ihLE.symm).symm
      | implies =>
        simp only [translate]
        exact (smtEval_implies_lhs_none _ _ ihLE.symm).symm
      | iff =>
        simp only [translate]
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
          rw [valueToSmt_vBool] at ihLE
          rw [hR] at ihRE; simp only [valueToSmt?] at ihRE
          simp only [valueToSmt?]
          cases op with
          | and =>
            simp only [translate]
            exact (smtEval_and_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | or =>
            simp only [translate]
            exact (smtEval_or_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | implies =>
            simp only [translate]
            exact (smtEval_implies_rhs_none _ _ ihLE.symm ihRE.symm).symm
          | iff =>
            simp only [translate]
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
                              nofun ihLE ihRE hL hR
          | vEnum en' m' => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a
                                    (.vEnum en' m') nofun ihLE ihRE hL hR
          | vEntity en' i' => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a
                                      (.vEntity en' i') nofun
                                      ihLE ihRE hL hR
          | vSet members => exact boolBin_rhs_nonBool_lhs_bool s st env op l r a
                                      (.vSet members) nofun
                                      ihLE ihRE hL hR
      | vInt n =>
        exact boolBin_lhs_nonBool s st env op l r (.vInt n)
                nofun ihLE ihRE hL
      | vEnum en' m' =>
        exact boolBin_lhs_nonBool s st env op l r (.vEnum en' m')
                nofun ihLE ihRE hL
      | vEntity en' i' =>
        exact boolBin_lhs_nonBool s st env op l r (.vEntity en' i')
                nofun ihLE ihRE hL
      | vSet members =>
        exact boolBin_lhs_nonBool s st env op l r (.vSet members)
                nofun ihLE ihRE hL
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
        simp only [translate]
        exact (smtEval_add_lhs_none _ _ ihLE.symm).symm
      | sub =>
        simp only [translate]
        exact (smtEval_sub_lhs_none _ _ ihLE.symm).symm
      | mul =>
        simp only [translate]
        exact (smtEval_mul_lhs_none _ _ ihLE.symm).symm
      | div =>
        simp only [translate]
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
          rw [valueToSmt_vInt] at ihLE'
          rw [hR] at ihRE'; simp only [valueToSmt?] at ihRE'
          rw [show eval s st env (.arith op l r) = none from by
                simp only [eval, hL, hR]; cases op <;> rfl]
          simp only [valueToSmt?]
          cases op with
          | add =>
            simp only [translate]
            exact (smtEval_add_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | sub =>
            simp only [translate]
            exact (smtEval_sub_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | mul =>
            simp only [translate]
            exact (smtEval_mul_rhs_none _ _ ihLE'.symm ihRE'.symm).symm
          | div =>
            simp only [translate]
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
                          nofun ihLE ihRE hL hR
          | vEnum en m => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vEnum en m)
                            nofun ihLE ihRE hL hR
          | vEntity en i => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vEntity en i)
                              nofun ihLE ihRE hL hR
          | vSet members => exact arith_rhs_nonInt_lhs_int s st env op l r a (.vSet members)
                              nofun ihLE ihRE hL hR
      | vBool b => exact arith_lhs_nonInt s st env op l r (.vBool b)
                    nofun ihLE ihRE hL
      | vEnum en m => exact arith_lhs_nonInt s st env op l r (.vEnum en m)
                       nofun ihLE ihRE hL
      | vEntity en i => exact arith_lhs_nonInt s st env op l r (.vEntity en i)
                          nofun ihLE ihRE hL
      | vSet members => exact arith_lhs_nonInt s st env op l r (.vSet members)
                          nofun ihLE ihRE hL
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
                          nofun ihLE ihRE hL hR
            | vEnum en m => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              nofun ihLE ihRE hL hR
            | vEntity en i => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                nofun ihLE ihRE hL hR
            | vSet members => exact cmp_lt_rhs_nonInt_lhs_int s st env l r a (.vSet members)
                                nofun ihLE ihRE hL hR
          | vBool b => exact cmp_lt_lhs_nonInt s st env l r (.vBool b)
                        nofun ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_lt_lhs_nonInt s st env l r (.vEnum en m)
                            nofun ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_lt_lhs_nonInt s st env l r (.vEntity en i)
                              nofun ihLE ihRE hL rv hR
          | vSet members => exact cmp_lt_lhs_nonInt s st env l r (.vSet members)
                              nofun ihLE ihRE hL rv hR
        | le =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_le_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          nofun ihLE ihRE hL hR
            | vEnum en m => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              nofun ihLE ihRE hL hR
            | vEntity en i => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                nofun ihLE ihRE hL hR
            | vSet members => exact cmp_le_rhs_nonInt_lhs_int s st env l r a (.vSet members)
                                nofun ihLE ihRE hL hR
          | vBool b => exact cmp_le_lhs_nonInt s st env l r (.vBool b)
                        nofun ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_le_lhs_nonInt s st env l r (.vEnum en m)
                            nofun ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_le_lhs_nonInt s st env l r (.vEntity en i)
                              nofun ihLE ihRE hL rv hR
          | vSet members => exact cmp_le_lhs_nonInt s st env l r (.vSet members)
                              nofun ihLE ihRE hL rv hR
        | gt =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_gt_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          nofun ihLE ihRE hL hR
            | vEnum en m => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              nofun ihLE ihRE hL hR
            | vEntity en i => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                nofun ihLE ihRE hL hR
            | vSet members => exact cmp_gt_rhs_nonInt_lhs_int s st env l r a (.vSet members)
                                nofun ihLE ihRE hL hR
          | vBool b => exact cmp_gt_lhs_nonInt s st env l r (.vBool b)
                        nofun ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_gt_lhs_nonInt s st env l r (.vEnum en m)
                            nofun ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_gt_lhs_nonInt s st env l r (.vEntity en i)
                              nofun ihLE ihRE hL rv hR
          | vSet members => exact cmp_gt_lhs_nonInt s st env l r (.vSet members)
                              nofun ihLE ihRE hL rv hR
        | ge =>
          cases lv with
          | vInt a =>
            cases rv with
            | vInt b => exact soundness_cmp_ge_ints s st env l r a b ihLE ihRE hL hR
            | vBool b => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vBool b)
                          nofun ihLE ihRE hL hR
            | vEnum en m => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vEnum en m)
                              nofun ihLE ihRE hL hR
            | vEntity en i => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vEntity en i)
                                nofun ihLE ihRE hL hR
            | vSet members => exact cmp_ge_rhs_nonInt_lhs_int s st env l r a (.vSet members)
                                nofun ihLE ihRE hL hR
          | vBool b => exact cmp_ge_lhs_nonInt s st env l r (.vBool b)
                        nofun ihLE ihRE hL rv hR
          | vEnum en m => exact cmp_ge_lhs_nonInt s st env l r (.vEnum en m)
                            nofun ihLE ihRE hL rv hR
          | vEntity en i => exact cmp_ge_lhs_nonInt s st env l r (.vEntity en i)
                              nofun ihLE ihRE hL rv hR
          | vSet members => exact cmp_ge_lhs_nonInt s st env l r (.vSet members)
                              nofun ihLE ihRE hL rv hR
  | letIn x value body ihV ihB =>
    have ihV := ihV env
    cases hV : eval s st env value with
    | none =>
      rw [show eval s st env (.letIn x value body) = none from by simp only [eval, hV]]
      simp only [translate]
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
        simp only [translate]
        have hSort : (correlateModel s st).lookupSortMembers en = some d.members :=
          correlateModel_lookupSortMembers s st en d hSchema
        simp only [valueToSmt?]
        exact (smtEval_enumElemConst_nonMember _ _ hSort hMember').symm
    | none =>
      rw [show eval s st env (.enumAccess en m) = none from by simp only [eval, hSchema]]
      simp only [translate]
      have hSort : (correlateModel s st).lookupSortMembers en = none :=
        correlateModel_lookupSortMembers_none s st en hSchema
      simp only [valueToSmt?]
      exact (smtEval_enumElemConst_unknown _ _ hSort).symm
  | member elem relName ihE =>
    have ihE := ihE env
    cases hElem : eval s st env elem with
    | none =>
      rw [show eval s st env (.member elem relName) = none from by simp only [eval, hElem]]
      simp only [translate]
      rw [hElem] at ihE; simp only [valueToSmt?] at ihE
      simp only [valueToSmt?]
      exact (smtEval_inDom_arg_none _ _ ihE.symm).symm
    | some v =>
      cases hDom : st.relationDomain relName with
      | some dom => exact soundness_member_resolved s st env elem relName v dom ihE hElem hDom
      | none =>
        rw [show eval s st env (.member elem relName) = none from by
          simp only [eval, hElem, hDom]]
        simp only [translate]
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
      rw [valueToSmt_vInt]
      simp only [translate]
      have hRel : (correlateModel s st).lookupRel relName
                = some (dom.map valueToSmt) :=
        correlateModel_lookupRel s st relName dom hDom
      rw [smtEval_cardRel_resolved _ _ relName (dom.map valueToSmt) hRel]
      rw [List.length_map]
    | none =>
      have hEval : eval s st env (.cardRel relName) = none := by
        simp only [eval, hDom]
      rw [hEval]
      simp only [translate]
      have hRel : (correlateModel s st).lookupRel relName = none :=
        correlateModel_lookupRel_none s st relName hDom
      simp only [valueToSmt?]
      exact (smtEval_cardRel_unknown _ _ relName hRel).symm
  | prime e ih =>
    -- Single-state collapse: `eval` is mode-flat so Prime is identity on the Lean side.
    -- Phase 2 of M_L.4.b-ext (issue #194) gave `translate` a `.prime` wrapper on the
    -- SMT side; `smtEval` treats it as identity (the universal soundness theorem
    -- remains a single-state claim about `eval` / `smtEval`). True two-state
    -- semantics is `evalAt` / `smtEvalAt` and lives in `soundnessAt_diagonal` and
    -- (later) `soundnessAt`.
    have ih := ih env
    rw [eval_prime]
    simp only [translate]
    rw [smtEval_prime]
    exact ih
  | pre e ih =>
    -- Single-state collapse: see `prime` arm above. Phase 2 wraps the SMT side in
    -- `.pre` (identity in `smtEval`); mode-flip semantics lives in `smtEvalAt`.
    have ih := ih env
    rw [eval_pre]
    simp only [translate]
    rw [smtEval_pre]
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
      simp only [translate]
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
      simp only [translate]
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
      simp only [translate]
      rw [hKey] at ihKey
      simp only [valueToSmt?] at ihKey
      simp only [valueToSmt?]
      exact (smtEval_indexRel_key_none _ _ ihKey.symm).symm
    | some kv =>
      have hEval : eval s st env (.indexRel relName key)
                  = st.lookupKey relName kv := by
        simp only [eval, hKey]
      rw [hEval]
      simp only [translate]
      rw [hKey] at ihKey
      simp only [valueToSmt?_some] at ihKey
      rw [smtEval_indexRel_resolved _ _ relName (translate key) (valueToSmt kv) ihKey.symm]
      exact lookupKey_correlated s st relName kv
  | fieldAccess base fieldName ihBase =>
    -- M_L.4.k: FieldAccess on an arbitrary entity-valued sub-expression. The base IH lifts to
    -- the correlated env via structural induction. Once the base reduces to `.vEntity en id`,
    -- both sides are parallel lookups in `entityFields` / `predFields` keyed by entity id;
    -- `lookupField_correlated` bridges them. Non-entity / `none` paths propagate via the
    -- `*_base_none` / `*_nonEntity` characterizations on each side.
    have ihBase := ihBase env
    cases hBase : eval s st env base with
    | none =>
      have hEval : eval s st env (.fieldAccess base fieldName) = none :=
        eval_fieldAccess_base_none s st env base fieldName hBase
      rw [hEval]
      simp only [translate]
      rw [hBase] at ihBase; simp only [valueToSmt?] at ihBase
      simp only [valueToSmt?]
      exact (smtEval_fieldAccess_base_none _ _ (translate base) fieldName ihBase.symm).symm
    | some v =>
      cases v with
      | vEntity en id =>
        rw [eval_fieldAccess_resolved s st env base en id fieldName hBase]
        simp only [translate]
        rw [hBase] at ihBase
        simp only [valueToSmt?_some] at ihBase
        rw [valueToSmt_vEntity] at ihBase
        rw [smtEval_fieldAccess_resolved _ _ (translate base) en id fieldName ihBase.symm]
        exact lookupField_correlated s st id fieldName
      | vBool b =>
        have hEval : eval s st env (.fieldAccess base fieldName) = none :=
          eval_fieldAccess_nonEntity s st env hBase
            (fun en id => by intro h; cases h)
        rw [hEval]
        simp only [translate]
        rw [hBase] at ihBase; simp only [valueToSmt?_some] at ihBase
        rw [valueToSmt_vBool] at ihBase
        simp only [valueToSmt?]
        exact (smtEval_fieldAccess_nonEntity _ _ ihBase.symm
                (fun en id => by intro h; cases h)).symm
      | vInt n =>
        have hEval : eval s st env (.fieldAccess base fieldName) = none :=
          eval_fieldAccess_nonEntity s st env hBase
            (fun en id => by intro h; cases h)
        rw [hEval]
        simp only [translate]
        rw [hBase] at ihBase; simp only [valueToSmt?_some] at ihBase
        rw [valueToSmt_vInt] at ihBase
        simp only [valueToSmt?]
        exact (smtEval_fieldAccess_nonEntity _ _ ihBase.symm
                (fun en id => by intro h; cases h)).symm
      | vEnum en m =>
        have hEval : eval s st env (.fieldAccess base fieldName) = none :=
          eval_fieldAccess_nonEntity s st env hBase
            (fun en id => by intro h; cases h)
        rw [hEval]
        simp only [translate]
        rw [hBase] at ihBase; simp only [valueToSmt?_some] at ihBase
        rw [valueToSmt_vEnum] at ihBase
        simp only [valueToSmt?]
        exact (smtEval_fieldAccess_nonEntity _ _ ihBase.symm
                (fun en id => by intro h; cases h)).symm
      | vSet members =>
        have hEval : eval s st env (.fieldAccess base fieldName) = none :=
          eval_fieldAccess_nonEntity s st env hBase
            (fun en id => by intro h; cases h)
        rw [hEval]
        simp only [translate]
        rw [hBase] at ihBase; simp only [valueToSmt?_some] at ihBase
        rw [valueToSmt_vSet members] at ihBase
        simp only [valueToSmt?]
        exact (smtEval_fieldAccess_nonEntity _ _ ihBase.symm
                (fun en id => by intro h; cases h)).symm
  | setEmpty => exact soundness_setEmpty s st env
  | setInsert elem set ihElem ihSet =>
    have ihElem := ihElem env
    have ihSet := ihSet env
    simp only [translate]
    cases hElem : eval s st env elem with
    | none =>
      rw [eval_setInsert_elem_none s st env elem set hElem]
      rw [hElem] at ihElem; simp only [valueToSmt?] at ihElem
      simp only [valueToSmt?]
      exact (smtEval_setInsert_elem_none _ _ (translate elem) (translate set) ihElem.symm).symm
    | some v =>
      cases hSet : eval s st env set with
      | none =>
        rw [eval_setInsert_set_none s st env elem set v hElem hSet]
        rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
        rw [hSet] at ihSet; simp only [valueToSmt?] at ihSet
        simp only [valueToSmt?]
        exact (smtEval_setInsert_set_none _ _ (translate elem) (translate set)
                  (valueToSmt v) ihElem.symm ihSet.symm).symm
      | some setVal =>
        cases setVal with
        | vSet members =>
          exact soundness_setInsert_resolved s st env elem set v members ihElem ihSet hElem hSet
        | vBool b =>
          have hNotSet : ∀ members, Value.vBool b ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setInsert_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setInsert_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vBool b) hNotSet)).symm
        | vInt n =>
          have hNotSet : ∀ members, Value.vInt n ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setInsert_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setInsert_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vInt n) hNotSet)).symm
        | vEnum en mem =>
          have hNotSet : ∀ members, Value.vEnum en mem ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setInsert_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setInsert_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
        | vEntity en id =>
          have hNotSet : ∀ members, Value.vEntity en id ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setInsert_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setInsert_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vEntity en id) hNotSet)).symm
  | setMember elem set ihElem ihSet =>
    have ihElem := ihElem env
    have ihSet := ihSet env
    simp only [translate]
    cases hElem : eval s st env elem with
    | none =>
      rw [eval_setMember_elem_none s st env elem set hElem]
      rw [hElem] at ihElem; simp only [valueToSmt?] at ihElem
      simp only [valueToSmt?]
      exact (smtEval_setMember_elem_none _ _ (translate elem) (translate set) ihElem.symm).symm
    | some v =>
      cases hSet : eval s st env set with
      | none =>
        rw [eval_setMember_set_none s st env elem set v hElem hSet]
        rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
        rw [hSet] at ihSet; simp only [valueToSmt?] at ihSet
        simp only [valueToSmt?]
        exact (smtEval_setMember_set_none _ _ (translate elem) (translate set)
                  (valueToSmt v) ihElem.symm ihSet.symm).symm
      | some setVal =>
        cases setVal with
        | vSet members =>
          exact soundness_setMember_resolved s st env elem set v members ihElem ihSet hElem hSet
        | vBool b =>
          have hNotSet : ∀ members, Value.vBool b ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setMember_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setMember_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vBool b) hNotSet)).symm
        | vInt n =>
          have hNotSet : ∀ members, Value.vInt n ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setMember_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setMember_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vInt n) hNotSet)).symm
        | vEnum en mem =>
          have hNotSet : ∀ members, Value.vEnum en mem ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setMember_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setMember_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
        | vEntity en id =>
          have hNotSet : ∀ members, Value.vEntity en id ≠ .vSet members := by
            intro members h; cases h
          rw [eval_setMember_set_nonSet s st env hElem hSet hNotSet]
          rw [hElem] at ihElem; rw [valueToSmt?_some] at ihElem
          rw [hSet] at ihSet; rw [valueToSmt?_some] at ihSet
          simp only [valueToSmt?]
          exact (smtEval_setMember_set_nonSet _ _ ihElem.symm ihSet.symm
                    (hSmtValNotSet (.vEntity en id) hNotSet)).symm
  | setBin op l r ihL ihR =>
    have ihL := ihL env
    have ihR := ihR env
    cases hL : eval s st env l with
    | none =>
      rw [eval_setBin_lhs_none s st env op l r hL]
      rw [hL] at ihL; simp only [valueToSmt?] at ihL
      simp only [valueToSmt?]
      cases op with
      | union =>
        simp only [translate]
        exact (smtEval_setUnion_lhs_none _ _ ihL.symm).symm
      | intersect =>
        simp only [translate]
        exact (smtEval_setIntersect_lhs_none _ _ ihL.symm).symm
      | diff =>
        simp only [translate]
        exact (smtEval_setDiff_lhs_none _ _ ihL.symm).symm
    | some lv =>
      cases lv with
      | vSet ls =>
        cases hR : eval s st env r with
        | none =>
          rw [eval_setBin_rhs_none s st env op l r (.vSet ls) hL hR]
          rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vSet] at ihL
          rw [hR] at ihR; simp only [valueToSmt?] at ihR
          simp only [valueToSmt?]
          cases op with
          | union =>
            simp only [translate]
            exact (smtEval_setUnion_rhs_none _ _ ihL.symm ihR.symm).symm
          | intersect =>
            simp only [translate]
            exact (smtEval_setIntersect_rhs_none _ _ ihL.symm ihR.symm).symm
          | diff =>
            simp only [translate]
            exact (smtEval_setDiff_rhs_none _ _ ihL.symm ihR.symm).symm
        | some rv =>
          cases rv with
          | vSet rs =>
            exact soundness_setBin_sets s st env op l r ls rs ihL ihR hL hR
          | vBool b =>
            have hNotSet : ∀ members, Value.vBool b ≠ .vSet members := by intro members h; cases h
            rw [eval_setBin_rhs_nonSet s st env op l r ls (.vBool b) hNotSet hL hR]
            rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vSet] at ihL
            rw [hR] at ihR; rw [valueToSmt?_some] at ihR
            simp only [valueToSmt?]
            cases op with
            | union =>
              simp only [translate]
              exact (smtEval_setUnion_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vBool b) hNotSet)).symm
            | intersect =>
              simp only [translate]
              exact (smtEval_setIntersect_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vBool b) hNotSet)).symm
            | diff =>
              simp only [translate]
              exact (smtEval_setDiff_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vBool b) hNotSet)).symm
          | vInt n =>
            have hNotSet : ∀ members, Value.vInt n ≠ .vSet members := by intro members h; cases h
            rw [eval_setBin_rhs_nonSet s st env op l r ls (.vInt n) hNotSet hL hR]
            rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vSet] at ihL
            rw [hR] at ihR; rw [valueToSmt?_some] at ihR
            simp only [valueToSmt?]
            cases op with
            | union =>
              simp only [translate]
              exact (smtEval_setUnion_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vInt n) hNotSet)).symm
            | intersect =>
              simp only [translate]
              exact (smtEval_setIntersect_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vInt n) hNotSet)).symm
            | diff =>
              simp only [translate]
              exact (smtEval_setDiff_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vInt n) hNotSet)).symm
          | vEnum en mem =>
            have hNotSet : ∀ members, Value.vEnum en mem ≠ .vSet members := by intro members h; cases h
            rw [eval_setBin_rhs_nonSet s st env op l r ls (.vEnum en mem) hNotSet hL hR]
            rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vSet] at ihL
            rw [hR] at ihR; rw [valueToSmt?_some] at ihR
            simp only [valueToSmt?]
            cases op with
            | union =>
              simp only [translate]
              exact (smtEval_setUnion_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
            | intersect =>
              simp only [translate]
              exact (smtEval_setIntersect_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
            | diff =>
              simp only [translate]
              exact (smtEval_setDiff_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
          | vEntity en id =>
            have hNotSet : ∀ members, Value.vEntity en id ≠ .vSet members := by intro members h; cases h
            rw [eval_setBin_rhs_nonSet s st env op l r ls (.vEntity en id) hNotSet hL hR]
            rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vSet] at ihL
            rw [hR] at ihR; rw [valueToSmt?_some] at ihR
            simp only [valueToSmt?]
            cases op with
            | union =>
              simp only [translate]
              exact (smtEval_setUnion_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEntity en id) hNotSet)).symm
            | intersect =>
              simp only [translate]
              exact (smtEval_setIntersect_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEntity en id) hNotSet)).symm
            | diff =>
              simp only [translate]
              exact (smtEval_setDiff_rhs_nonSet _ _ ihL.symm ihR.symm
                        (hSmtValNotSet (.vEntity en id) hNotSet)).symm
      | vBool b =>
        have hNotSet : ∀ members, Value.vBool b ≠ .vSet members := by intro members h; cases h
        rw [eval_setBin_lhs_nonSet s st env op l r (.vBool b) hNotSet hL]
        rw [hL] at ihL; rw [valueToSmt?_some] at ihL
        simp only [valueToSmt?]
        cases op with
        | union =>
          simp only [translate]
          exact (smtEval_setUnion_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vBool b) hNotSet)).symm
        | intersect =>
          simp only [translate]
          exact (smtEval_setIntersect_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vBool b) hNotSet)).symm
        | diff =>
          simp only [translate]
          exact (smtEval_setDiff_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vBool b) hNotSet)).symm
      | vInt n =>
        have hNotSet : ∀ members, Value.vInt n ≠ .vSet members := by intro members h; cases h
        rw [eval_setBin_lhs_nonSet s st env op l r (.vInt n) hNotSet hL]
        rw [hL] at ihL; rw [valueToSmt?_some] at ihL
        simp only [valueToSmt?]
        cases op with
        | union =>
          simp only [translate]
          exact (smtEval_setUnion_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vInt n) hNotSet)).symm
        | intersect =>
          simp only [translate]
          exact (smtEval_setIntersect_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vInt n) hNotSet)).symm
        | diff =>
          simp only [translate]
          exact (smtEval_setDiff_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vInt n) hNotSet)).symm
      | vEnum en mem =>
        have hNotSet : ∀ members, Value.vEnum en mem ≠ .vSet members := by intro members h; cases h
        rw [eval_setBin_lhs_nonSet s st env op l r (.vEnum en mem) hNotSet hL]
        rw [hL] at ihL; rw [valueToSmt?_some] at ihL
        simp only [valueToSmt?]
        cases op with
        | union =>
          simp only [translate]
          exact (smtEval_setUnion_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
        | intersect =>
          simp only [translate]
          exact (smtEval_setIntersect_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
        | diff =>
          simp only [translate]
          exact (smtEval_setDiff_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEnum en mem) hNotSet)).symm
      | vEntity en id =>
        have hNotSet : ∀ members, Value.vEntity en id ≠ .vSet members := by intro members h; cases h
        rw [eval_setBin_lhs_nonSet s st env op l r (.vEntity en id) hNotSet hL]
        rw [hL] at ihL; rw [valueToSmt?_some] at ihL
        simp only [valueToSmt?]
        cases op with
        | union =>
          simp only [translate]
          exact (smtEval_setUnion_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEntity en id) hNotSet)).symm
        | intersect =>
          simp only [translate]
          exact (smtEval_setIntersect_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEntity en id) hNotSet)).symm
        | diff =>
          simp only [translate]
          exact (smtEval_setDiff_lhs_nonSet _ _ ihL.symm (hSmtValNotSet (.vEntity en id) hNotSet)).symm

/-! ## Two-state diagonal-collapse soundness (M_L.4.b-ext Phase 2, issue #194).

`correlateModelPair s sp` lifts the schema-and-state-pair correlation to the
SMT side. `soundnessAt_diagonal` is the diagonal-mode-aware soundness: for
every mode and every Expr in the verified subset, evaluating against the
diagonal `StatePair.diag st` agrees with the existing single-state soundness
claim. The corollary is derived by composing the two diagonal-collapse
lemmas (`evalAt_diagonal_eq_eval` / `smtEvalAt_diagonal_eq_smtEval`) with
the single-state `soundness` theorem — no fresh structural induction.

The off-diagonal (true two-state) soundness `soundnessAt` is queued for
follow-up phases per `STATUS.md` §M_L.4.b-ext: it requires a fresh structural
induction whose mode-flip arms exercise the new mode-aware machinery, plus
a per-case cascade through `Soundness.lean`'s ~1900 LOC. -/

def correlateModelPair (s : Schema) (sp : StatePair) : SmtModelPair where
  pre  := correlateModel s sp.pre
  post := correlateModel s sp.post

@[simp] theorem correlateModelPair_pre (s : Schema) (sp : StatePair) :
    (correlateModelPair s sp).pre = correlateModel s sp.pre := rfl

@[simp] theorem correlateModelPair_post (s : Schema) (sp : StatePair) :
    (correlateModelPair s sp).post = correlateModel s sp.post := rfl

@[simp] theorem correlateModelPair_diag (s : Schema) (st : State) :
    correlateModelPair s (StatePair.diag st) = SmtModelPair.diag (correlateModel s st) := rfl

/-- `correlateModelPair` commutes with `at`: projecting the SmtModelPair at a mode is the same as
    correlating the StatePair's projection at that mode. The bridge for off-diagonal soundness:
    every per-case `correlateModel_*` lemma in this file lifts unchanged via this rewrite. -/
@[simp] theorem correlateModelPair_at (s : Schema) (sp : StatePair) (mode : StateMode) :
    (correlateModelPair s sp).at mode = correlateModel s (sp.at mode) := by
  cases mode <;> rfl

theorem soundnessAt_diagonal (mode : StateMode) (s : Schema) (st : State)
    (env : Env) (e : Expr) :
    valueToSmt? (evalAt mode s (StatePair.diag st) env e)
      = smtEvalAt mode (correlateModelPair s (StatePair.diag st))
          (correlateEnv env) (translate e) := by
  rw [evalAt_diagonal_eq_eval mode s st env e]
  rw [correlateModelPair_diag]
  rw [smtEvalAt_diagonal_eq_smtEval mode (correlateModel s st) (correlateEnv env) (translate e)]
  exact soundness s st env e

/-! ## Per-case off-diagonal `soundnessAt_*` (Phase 3a, advances #194).

Phase 3a covers the cases that don't read state and don't introduce mode flips
beyond `Prime`/`Pre`: atoms, propositional, arithmetic, comparison, `letIn`,
`enumAccess`, env-hit identifier, plus the mode-flipping `Prime`/`Pre` arms.
Each per-case theorem mirrors the existing single-state `soundness_*` shape,
threaded through the mode-aware evaluators. The IH is taken as a hypothesis
on each subexpression — the universal `soundnessAt` theorem (Phase 3b)
discharges the IH via structural induction on `Expr`.

State-touching cases (state-scalar identifier, member, cardRel, indexRel,
fieldAccess), quantifier cases (forallEnum, forallRel — need parallel mutual
lemmas to `evalForallEnum_correlated` / `evalForallRel_correlated`), and
set-op cases (setEmpty / setInsert / setMember / setBin) are queued for
Phase 3b per `STATUS.md` §M_L.4.b-ext. -/

variable (mode : StateMode) (sp : StatePair)

/-- Atomic: boolean literal. -/
theorem soundnessAt_boolLit (b : Bool) :
    valueToSmt? (evalAt mode s sp env (.boolLit b))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolLit b)) := by
  rw [evalAt_boolLit, valueToSmt?_some]
  show some (SmtVal.sBool b) = _
  simp only [translate]
  rw [smtEvalAt_bLit]

/-- Atomic: integer literal. -/
theorem soundnessAt_intLit (n : Int) :
    valueToSmt? (evalAt mode s sp env (.intLit n))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.intLit n)) := by
  rw [evalAt_intLit, valueToSmt?_some]
  show some (SmtVal.sInt n) = _
  simp only [translate]
  rw [smtEvalAt_iLit]

/-- Identifier — env hit (local-binding path). The state-scalar miss path
    needs `correlateModelPair_at` + the existing single-state state-scalar
    correlation; deferred to Phase 3b. -/
theorem soundnessAt_ident_local {x : String} {v : Value}
    (h : Env.lookup env x = some v) :
    valueToSmt? (evalAt mode s sp env (.ident x))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.ident x)) := by
  rw [evalAt_ident_local mode s sp env h, valueToSmt?_some]
  simp only [translate]
  have hCorr : SmtEnv.lookup (correlateEnv env) x = some (valueToSmt v) := by
    rw [correlateEnv_lookup, h]; rfl
  rw [smtEvalAt_var_local _ _ _ hCorr]

/-- Unary `not` over a boolean sub-result. The IH on `e` is taken as a hypothesis. -/
theorem soundnessAt_unNot_bool (e : Expr) (b : Bool)
    (hSub : valueToSmt? (evalAt mode s sp env e)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate e))
    (hEval : evalAt mode s sp env e = some (.vBool b)) :
    valueToSmt? (evalAt mode s sp env (.unNot e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.unNot e)) := by
  rw [evalAt_unNot_bool mode s sp env e b hEval, valueToSmt?_some]
  show some (SmtVal.sBool (!b)) = _
  simp only [translate]
  rw [hEval] at hSub
  rw [valueToSmt?_some] at hSub
  show some (SmtVal.sBool (!b)) = smtEvalAt _ _ _ (.not (translate e))
  rw [smtEvalAt_not_bool _ _ _ (translate e) b hSub.symm]

/-- Unary `negate` over an integer sub-result. -/
theorem soundnessAt_unNeg_int (e : Expr) (n : Int)
    (hSub : valueToSmt? (evalAt mode s sp env e)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate e))
    (hEval : evalAt mode s sp env e = some (.vInt n)) :
    valueToSmt? (evalAt mode s sp env (.unNeg e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.unNeg e)) := by
  rw [evalAt_unNeg_int mode s sp env e n hEval, valueToSmt?_some]
  show some (SmtVal.sInt (-n)) = _
  simp only [translate]
  rw [hEval] at hSub
  rw [valueToSmt?_some] at hSub
  show some (SmtVal.sInt (-n)) = smtEvalAt _ _ _ (.neg (translate e))
  rw [smtEvalAt_neg_int _ _ _ (translate e) n hSub.symm]

/-- Boolean `and` — both sides reduce to booleans. -/
theorem soundnessAt_boolBin_and_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vBool a))
    (hR : evalAt mode s sp env r = some (.vBool b)) :
    valueToSmt? (evalAt mode s sp env (.boolBin .and l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin .and l r)) := by
  rw [evalAt_boolBin_bools mode s sp env .and l r a b hL hR, valueToSmt?_some]
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a && b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  show some (SmtVal.sBool (a && b)) = smtEvalAt _ _ _ (.and (translate l) (translate r))
  rw [smtEvalAt_and_bools _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Boolean `or`. -/
theorem soundnessAt_boolBin_or_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vBool a))
    (hR : evalAt mode s sp env r = some (.vBool b)) :
    valueToSmt? (evalAt mode s sp env (.boolBin .or l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin .or l r)) := by
  rw [evalAt_boolBin_bools mode s sp env .or l r a b hL hR, valueToSmt?_some]
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a || b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  show some (SmtVal.sBool (a || b)) = smtEvalAt _ _ _ (.or (translate l) (translate r))
  rw [smtEvalAt_or_bools _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Boolean `implies`. -/
theorem soundnessAt_boolBin_implies_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vBool a))
    (hR : evalAt mode s sp env r = some (.vBool b)) :
    valueToSmt? (evalAt mode s sp env (.boolBin .implies l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin .implies l r)) := by
  rw [evalAt_boolBin_bools mode s sp env .implies l r a b hL hR, valueToSmt?_some]
  simp only [evalBoolBin]
  show some (SmtVal.sBool (!a || b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  show some (SmtVal.sBool (!a || b))
        = smtEvalAt _ _ _ (.implies (translate l) (translate r))
  rw [smtEvalAt_implies_bools _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Polymorphic equality (`Eq`). -/
theorem soundnessAt_cmp_eq_vals (l r : Expr) (va vb : Value)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some va)
    (hR : evalAt mode s sp env r = some vb) :
    valueToSmt? (evalAt mode s sp env (.cmp .eq l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .eq l r)) := by
  rw [evalAt_cmp_app, hL, hR]
  rw [evalCmp_eq_value]
  rw [valueToSmt?_some]
  show some (SmtVal.sBool (va == vb)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_eq_vals _ _ _ (translate l) (translate r) (valueToSmt va) (valueToSmt vb)
        hSubL.symm hSubR.symm]
  rw [valueToSmt_beq]

/-- Inequality (`Neq`) — composes `Eq` with `Not`. -/
theorem soundnessAt_cmp_neq_vals (l r : Expr) (va vb : Value)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some va)
    (hR : evalAt mode s sp env r = some vb) :
    valueToSmt? (evalAt mode s sp env (.cmp .neq l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .neq l r)) := by
  rw [evalAt_cmp_app, hL, hR]
  rw [evalCmp_neq_value]
  rw [valueToSmt?_some]
  show some (SmtVal.sBool (va != vb)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_not_bool _ _ _ (.eq (translate l) (translate r)) (va == vb)]
  · simp only [bne]
  · rw [smtEvalAt_eq_vals _ _ _ (translate l) (translate r) (valueToSmt va) (valueToSmt vb)
          hSubL.symm hSubR.symm]
    rw [valueToSmt_beq]

/-- Integer comparison `<`. -/
theorem soundnessAt_cmp_lt_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.cmp .lt l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .lt l r)) := by
  rw [evalAt_cmp_app, hL, hR, evalCmp_int_lt, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a < b))) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_lt_ints _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Integer comparison `>` (translated to swapped `<`). -/
theorem soundnessAt_cmp_gt_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.cmp .gt l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .gt l r)) := by
  rw [evalAt_cmp_app, hL, hR, evalCmp_int_gt, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a > b))) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_lt_ints _ _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm]

/-- Integer arithmetic `+`. -/
theorem soundnessAt_arith_add_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.arith .add l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith .add l r)) := by
  have hEval : evalAt mode s sp env (.arith .add l r) = some (.vInt (a + b)) := by
    simp only [evalAt, hL, hR, evalArith]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sInt (a + b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_add_ints _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Integer arithmetic `-`. -/
theorem soundnessAt_arith_sub_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.arith .sub l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith .sub l r)) := by
  have hEval : evalAt mode s sp env (.arith .sub l r) = some (.vInt (a - b)) := by
    simp only [evalAt, hL, hR, evalArith]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sInt (a - b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_sub_ints _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- Integer comparison `≤` (translated as `(l < r) ∨ (l = r)`). -/
theorem soundnessAt_cmp_le_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.cmp .le l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .le l r)) := by
  rw [evalAt_cmp_app, hL, hR, evalCmp_int_le, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a ≤ b))) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
  have hlt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                (.lt (translate l) (translate r))
              = some (.sBool (decide (a < b))) :=
    smtEvalAt_lt_ints _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm
  have heq : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                (.eq (translate l) (translate r))
              = some (.sBool (SmtVal.sInt a == SmtVal.sInt b)) :=
    smtEvalAt_eq_vals _ _ _ (translate l) (translate r)
        (SmtVal.sInt a) (SmtVal.sInt b) hSubL.symm hSubR.symm
  rw [smtEvalAt_or_bools _ _ _ _ _ _ _ hlt heq, sInt_beq]
  congr 2
  by_cases hab : a ≤ b
  · by_cases hlt2 : a < b
    · simp [hab, hlt2]
    · have heq2 : a = b := by omega
      simp [heq2]
  · have hlt2 : ¬ a < b := by omega
    have hne : a ≠ b := by omega
    simp [hab, hlt2, hne]

/-- Integer comparison `≥` (translated as `(r < l) ∨ (l = r)`). -/
theorem soundnessAt_cmp_ge_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.cmp .ge l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .ge l r)) := by
  rw [evalAt_cmp_app, hL, hR, evalCmp_int_ge, valueToSmt?_some]
  show some (SmtVal.sBool (decide (a ≥ b))) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [valueToSmt_vInt] at hSubL
  rw [valueToSmt_vInt] at hSubR
  have hlt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                (.lt (translate r) (translate l))
              = some (.sBool (decide (b < a))) :=
    smtEvalAt_lt_ints _ _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm
  have heq : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                (.eq (translate l) (translate r))
              = some (.sBool (SmtVal.sInt a == SmtVal.sInt b)) :=
    smtEvalAt_eq_vals _ _ _ (translate l) (translate r)
        (SmtVal.sInt a) (SmtVal.sInt b) hSubL.symm hSubR.symm
  rw [smtEvalAt_or_bools _ _ _ _ _ _ _ hlt heq, sInt_beq]
  congr 2
  by_cases hab : a ≥ b
  · by_cases hlt2 : b < a
    · simp [hab, hlt2]
    · have heq2 : a = b := by omega
      simp [heq2]
  · have hlt2 : ¬ b < a := by omega
    have hne : a ≠ b := by omega
    simp [hab, hlt2, hne]

/-- Boolean `iff` — translated as `(l → r) ∧ (r → l)`. -/
theorem soundnessAt_boolBin_iff_bools (l r : Expr) (a b : Bool)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vBool a))
    (hR : evalAt mode s sp env r = some (.vBool b)) :
    valueToSmt? (evalAt mode s sp env (.boolBin .iff l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin .iff l r)) := by
  rw [evalAt_boolBin_bools mode s sp env .iff l r a b hL hR, valueToSmt?_some]
  simp only [evalBoolBin]
  show some (SmtVal.sBool (a == b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  have himpLR : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.implies (translate l) (translate r))
                = some (.sBool (!a || b)) :=
    smtEvalAt_implies_bools _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm
  have himpRL : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.implies (translate r) (translate l))
                = some (.sBool (!b || a)) :=
    smtEvalAt_implies_bools _ _ _ (translate r) (translate l) b a hSubR.symm hSubL.symm
  rw [smtEvalAt_and_bools _ _ _ _ _ _ _ himpLR himpRL]
  congr 2
  cases a <;> cases b <;> rfl

/-- Integer division (non-zero divisor). -/
theorem soundnessAt_arith_div_ints_nonZero (l r : Expr) (a b : Int) (hbz : b ≠ 0)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.arith .div l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith .div l r)) := by
  have hEval : evalAt mode s sp env (.arith .div l r) = some (.vInt (a.ediv b)) := by
    cases b with
    | ofNat k =>
      cases k with
      | zero => exact absurd rfl hbz
      | succ _ => simp only [evalAt, hL, hR, evalArith]
    | negSucc _ => simp only [evalAt, hL, hR, evalArith]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sInt (a.ediv b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_div_ints_nonZero _ _ _ (translate l) (translate r) a b hbz hSubL.symm hSubR.symm]

/-- Integer division by zero — both sides yield `none`. -/
theorem soundnessAt_arith_div_ints_zero (l r : Expr) (a : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt 0)) :
    valueToSmt? (evalAt mode s sp env (.arith .div l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith .div l r)) := by
  have hEval : evalAt mode s sp env (.arith .div l r) = none := by
    simp only [evalAt, hL, hR, evalArith]
  rw [hEval]
  simp only [translate, valueToSmt?]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  exact (smtEvalAt_div_zero _ _ _ (translate l) (translate r) a hSubL.symm hSubR.symm).symm

/-- Integer arithmetic `*`. -/
theorem soundnessAt_arith_mul_ints (l r : Expr) (a b : Int)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some (.vInt b)) :
    valueToSmt? (evalAt mode s sp env (.arith .mul l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith .mul l r)) := by
  have hEval : evalAt mode s sp env (.arith .mul l r) = some (.vInt (a * b)) := by
    simp only [evalAt, hL, hR, evalArith]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sInt (a * b)) = _
  simp only [translate]
  rw [hL] at hSubL; rw [valueToSmt?_some] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some] at hSubR
  rw [smtEvalAt_mul_ints _ _ _ (translate l) (translate r) a b hSubL.symm hSubR.symm]

/-- `let x = v in body`. The IH on `value` is `hSub`; the IH on `body` is the
    extended-env hypothesis. -/
theorem soundnessAt_letIn (x : String) (value body : Expr) (v : Value)
    (hSub : valueToSmt? (evalAt mode s sp env value)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate value))
    (hBodyIH : valueToSmt? (evalAt mode s sp ((x, v) :: env) body)
                = smtEvalAt mode (correlateModelPair s sp) ((x, valueToSmt v) :: correlateEnv env)
                    (translate body))
    (hValue : evalAt mode s sp env value = some v) :
    valueToSmt? (evalAt mode s sp env (.letIn x value body))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.letIn x value body)) := by
  rw [evalAt_letIn_some mode s sp env x value body v hValue]
  simp only [translate]
  rw [hValue] at hSub; rw [valueToSmt?_some] at hSub
  rw [smtEvalAt_letIn_some _ _ _ x (translate value) (translate body) (valueToSmt v) hSub.symm]
  show valueToSmt? (evalAt mode s sp ((x, v) :: env) body)
        = smtEvalAt mode (correlateModelPair s sp) ((x, valueToSmt v) :: correlateEnv env)
            (translate body)
  exact hBodyIH

/-- EnumAccess on a known enum with a known member. -/
theorem soundnessAt_enumAccess_known (en m : String) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d)
    (hMember : d.members.contains m = true) :
    valueToSmt? (evalAt mode s sp env (.enumAccess en m))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.enumAccess en m)) := by
  have hEval : evalAt mode s sp env (.enumAccess en m) = some (.vEnum en m) := by
    simp only [evalAt, hSchema, hMember, if_true]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sEnumElem en m) = _
  simp only [translate]
  have hSort : ((correlateModelPair s sp).at mode).lookupSortMembers en = some d.members := by
    rw [correlateModelPair_at]
    exact correlateModel_lookupSortMembers s (sp.at mode) en d hSchema
  rw [smtEvalAt_enumElemConst_known _ _ _ hSort hMember]

/-- `Prime e` — flips mode to `.post` and applies IH at the flipped mode. The
    IH is parameterised over mode in the universal `soundnessAt` theorem
    (Phase 3b), so taking `hSubAtPost` as a hypothesis here is faithful. -/
theorem soundnessAt_prime (e : Expr)
    (hSubAtPost : valueToSmt? (evalAt .post s sp env e)
                    = smtEvalAt .post (correlateModelPair s sp) (correlateEnv env) (translate e)) :
    valueToSmt? (evalAt mode s sp env (.prime e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.prime e)) := by
  rw [evalAt_prime]
  simp only [translate]
  rw [smtEvalAt_prime]
  exact hSubAtPost

/-- `Pre e` — flips mode to `.pre` and applies IH at the flipped mode. -/
theorem soundnessAt_pre (e : Expr)
    (hSubAtPre : valueToSmt? (evalAt .pre s sp env e)
                    = smtEvalAt .pre (correlateModelPair s sp) (correlateEnv env) (translate e)) :
    valueToSmt? (evalAt mode s sp env (.pre e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.pre e)) := by
  rw [evalAt_pre]
  simp only [translate]
  rw [smtEvalAt_pre]
  exact hSubAtPre

/-! ## Phase 3b — state-touching, set-op, and quantifier `soundnessAt_*` cases. -/

/-- Identifier — env-miss / state-scalar-hit path. State scalar lookup goes through
    `(sp.at mode)`; the existing single-state `correlateModel_const_state_scalar` lifts
    via `correlateModelPair_at`. -/
theorem soundnessAt_ident_state {x : String} {v : Value}
    (hEnv : Env.lookup env x = none)
    (hSt : (sp.at mode).lookupScalar x = some v) :
    valueToSmt? (evalAt mode s sp env (.ident x))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.ident x)) := by
  rw [evalAt_ident_state mode s sp env hEnv hSt, valueToSmt?_some]
  simp only [translate]
  have hCorrEnv : SmtEnv.lookup (correlateEnv env) x = none := by
    rw [correlateEnv_lookup, hEnv]; rfl
  have hConst : ((correlateModelPair s sp).at mode).lookupConst x = some (valueToSmt v) := by
    rw [correlateModelPair_at]
    exact correlateModel_const_state_scalar s (sp.at mode) x v hSt
  rw [smtEvalAt_var_const _ _ _ hCorrEnv hConst]

/-- State-relation membership (`In`). The IH on the element comes from the
    structural induction; the relation domain lifts via `correlateModelPair_at`. -/
theorem soundnessAt_member_resolved (elem : Expr) (relName : String) (v : Value) (dom : List Value)
    (hSub : valueToSmt? (evalAt mode s sp env elem)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate elem))
    (hElem : evalAt mode s sp env elem = some v)
    (hDom : (sp.at mode).relationDomain relName = some dom) :
    valueToSmt? (evalAt mode s sp env (.member elem relName))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.member elem relName)) := by
  rw [evalAt_member_resolved mode s sp env elem relName v dom hElem hDom, valueToSmt?_some]
  show some (SmtVal.sBool (dom.contains v)) = _
  simp only [translate]
  rw [hElem] at hSub; rw [valueToSmt?_some] at hSub
  have hRel : ((correlateModelPair s sp).at mode).lookupRel relName = some (dom.map valueToSmt) := by
    rw [correlateModelPair_at]
    exact correlateModel_lookupRel s (sp.at mode) relName dom hDom
  rw [smtEvalAt_inDom_resolved _ _ _ relName (translate elem) (valueToSmt v) (dom.map valueToSmt)
        hSub.symm hRel]
  rw [contains_map_valueToSmt]

/-- State-relation cardinality. The relation domain lifts via `correlateModelPair_at`. -/
theorem soundnessAt_cardRel_resolved (relName : String) (dom : List Value)
    (hDom : (sp.at mode).relationDomain relName = some dom) :
    valueToSmt? (evalAt mode s sp env (.cardRel relName))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cardRel relName)) := by
  have hEval : evalAt mode s sp env (.cardRel relName)
              = some (.vInt (Int.ofNat dom.length)) := by
    simp only [evalAt, hDom]
  rw [hEval, valueToSmt?_some]
  show some (SmtVal.sInt (Int.ofNat dom.length)) = _
  simp only [translate]
  have hRel : ((correlateModelPair s sp).at mode).lookupRel relName = some (dom.map valueToSmt) := by
    rw [correlateModelPair_at]
    exact correlateModel_lookupRel s (sp.at mode) relName dom hDom
  rw [smtEvalAt_cardRel_resolved _ _ _ relName (dom.map valueToSmt) hRel]
  rw [List.length_map]

/-- State-relation pair lookup (Index). Bridges `lookupKey_correlated` via
    `correlateModelPair_at`. -/
theorem soundnessAt_indexRel_resolved (relName : String) (key : Expr) (kv : Value)
    (hSub : valueToSmt? (evalAt mode s sp env key)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate key))
    (hKey : evalAt mode s sp env key = some kv) :
    valueToSmt? (evalAt mode s sp env (.indexRel relName key))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.indexRel relName key)) := by
  rw [evalAt_indexRel_key mode s sp env relName key kv hKey]
  simp only [translate]
  rw [hKey] at hSub; rw [valueToSmt?_some] at hSub
  rw [smtEvalAt_indexRel_resolved _ _ _ relName (translate key) (valueToSmt kv) hSub.symm]
  rw [correlateModelPair_at]
  exact lookupKey_correlated s (sp.at mode) relName kv

/-- FieldAccess on a resolved entity-id. Bridges `lookupField_correlated` via
    `correlateModelPair_at`. -/
theorem soundnessAt_fieldAccess_resolved (base : Expr) (en id fieldName : String)
    (hSub : valueToSmt? (evalAt mode s sp env base)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate base))
    (hBase : evalAt mode s sp env base = some (.vEntity en id)) :
    valueToSmt? (evalAt mode s sp env (.fieldAccess base fieldName))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.fieldAccess base fieldName)) := by
  rw [evalAt_fieldAccess_resolved mode s sp env base en id fieldName hBase]
  simp only [translate]
  rw [hBase] at hSub
  rw [valueToSmt?_some, valueToSmt_vEntity] at hSub
  rw [smtEvalAt_fieldAccess_resolved _ _ _ (translate base) en id fieldName hSub.symm]
  rw [correlateModelPair_at]
  exact lookupField_correlated s (sp.at mode) id fieldName

/-! ### Set-op `soundnessAt_*` cases. -/

/-- Empty set literal. Both sides yield `vSet []` / `sSet []`. -/
theorem soundnessAt_setEmpty :
    valueToSmt? (evalAt mode s sp env .setEmpty)
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate .setEmpty) := by
  rw [evalAt_setEmpty, valueToSmt?_some]
  simp only [translate]
  rw [smtEvalAt_setEmpty]
  rfl

/-- Set insertion when both sub-results resolve. -/
theorem soundnessAt_setInsert_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hSubE : valueToSmt? (evalAt mode s sp env elem)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate elem))
    (hSubS : valueToSmt? (evalAt mode s sp env set)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate set))
    (hElem : evalAt mode s sp env elem = some v)
    (hSet : evalAt mode s sp env set = some (.vSet members)) :
    valueToSmt? (evalAt mode s sp env (.setInsert elem set))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.setInsert elem set)) := by
  rw [evalAt_setInsert_resolved mode s sp env elem set v members hElem hSet, valueToSmt?_some]
  simp only [translate]
  rw [hElem] at hSubE; rw [valueToSmt?_some] at hSubE
  rw [hSet] at hSubS; rw [valueToSmt?_some, valueToSmt_vSet] at hSubS
  rw [smtEvalAt_setInsert_resolved _ _ _ (translate elem) (translate set)
        (valueToSmt v) (members.map valueToSmt) hSubE.symm hSubS.symm]
  -- valueToSmt (vSet (dedupeValues (v :: members)))
  --    = sSet (dedupeSmtVals (valueToSmt v :: members.map valueToSmt))
  rw [valueToSmt_vSet]
  congr 2
  -- (dedupeValues (v :: members)).map valueToSmt
  --    = dedupeSmtVals (valueToSmt v :: members.map valueToSmt)
  exact dedupeValues_map_valueToSmt (v :: members)

/-- Set membership when both sub-results resolve. -/
theorem soundnessAt_setMember_resolved (elem set : Expr) (v : Value) (members : List Value)
    (hSubE : valueToSmt? (evalAt mode s sp env elem)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate elem))
    (hSubS : valueToSmt? (evalAt mode s sp env set)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate set))
    (hElem : evalAt mode s sp env elem = some v)
    (hSet : evalAt mode s sp env set = some (.vSet members)) :
    valueToSmt? (evalAt mode s sp env (.setMember elem set))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.setMember elem set)) := by
  rw [evalAt_setMember_resolved mode s sp env elem set v members hElem hSet, valueToSmt?_some]
  show some (SmtVal.sBool (containsValue members v)) = _
  simp only [translate]
  rw [hElem] at hSubE; rw [valueToSmt?_some] at hSubE
  rw [hSet] at hSubS; rw [valueToSmt?_some, valueToSmt_vSet] at hSubS
  rw [smtEvalAt_setMember_resolved _ _ _ (translate elem) (translate set)
        (valueToSmt v) (members.map valueToSmt) hSubE.symm hSubS.symm]
  rw [containsValue_map_valueToSmt]

/-! ### Quantifier `soundnessAt_*` cases (parallel mutual lemmas). -/

/-- Mutual-induction correlation between `evalAtForallEnum` and `smtEvalAtForallEnum`,
    threading the body's IH through every member of the enum. Mirrors
    `evalForallEnum_correlated` with carriers swapped. -/
theorem evalAtForallEnum_correlated
    (var en : String) (members : List String) (body : Expr)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (evalAt mode s sp ((var, val) :: env) body)
        = smtEvalAt mode (correlateModelPair s sp) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalAtForallEnum mode s sp env var en members body)
      = smtEvalAtForallEnum mode (correlateModelPair s sp) (correlateEnv env) var en members
          (translate body) := by
  induction members with
  | nil =>
    rw [evalAtForallEnum_nil, smtEvalAtForallEnum_nil]
    rfl
  | cons mem rest ih =>
    have hBody := hBodyIH (.vEnum en mem)
    rw [valueToSmt_vEnum] at hBody
    simp only [evalAtForallEnum, smtEvalAtForallEnum]
    cases hb : evalAt mode s sp ((var, .vEnum en mem) :: env) body with
    | none =>
      rw [hb] at hBody
      simp only [valueToSmt?] at hBody
      simp only [valueToSmt?, ← hBody]
    | some bv =>
      rw [hb] at hBody
      simp only [valueToSmt?_some] at hBody
      cases bv with
      | vBool b =>
        rw [valueToSmt_vBool] at hBody
        rw [← hBody]
        cases hr : evalAtForallEnum mode s sp env var en rest body with
        | none =>
          rw [hr] at ih
          simp only [valueToSmt?] at ih
          simp only [valueToSmt?, ← ih]
        | some rv =>
          rw [hr] at ih
          simp only [valueToSmt?_some] at ih
          cases rv with
          | vBool acc =>
            rw [valueToSmt_vBool] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [valueToSmt_vInt] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [valueToSmt_vEnum] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [valueToSmt_vEntity] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vSet members =>
            rw [valueToSmt_vSet members] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [valueToSmt_vInt] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [valueToSmt_vEnum] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [valueToSmt_vEntity] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vSet members =>
        rw [valueToSmt_vSet members] at hBody
        rw [← hBody]; simp only [valueToSmt?]

/-- Universal quantifier over a known enum domain. -/
theorem soundnessAt_forallEnum_known (var en : String) (body : Expr) (d : EnumDecl)
    (hSchema : s.lookupEnum en = some d)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (evalAt mode s sp ((var, val) :: env) body)
        = smtEvalAt mode (correlateModelPair s sp) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalAt mode s sp env (.forallEnum var en body))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.forallEnum var en body)) := by
  rw [evalAt_forallEnum_known mode s sp env var en body d hSchema]
  simp only [translate]
  have hSort : ((correlateModelPair s sp).at mode).lookupSortMembers en = some d.members := by
    rw [correlateModelPair_at]
    exact correlateModel_lookupSortMembers s (sp.at mode) en d hSchema
  rw [smtEvalAt_forallEnum_known _ _ _ var en (translate body) d.members hSort]
  exact evalAtForallEnum_correlated s env mode sp var en d.members body hBodyIH

/-- Mutual-induction correlation between `evalAtForallRel` and `smtEvalAtForallRel`,
    threading the body's IH through every value in the relation domain. -/
theorem evalAtForallRel_correlated
    (var : String) (dom : List Value) (body : Expr)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (evalAt mode s sp ((var, val) :: env) body)
        = smtEvalAt mode (correlateModelPair s sp) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalAtForallRel mode s sp env var dom body)
      = smtEvalAtForallRel mode (correlateModelPair s sp) (correlateEnv env) var
          (dom.map valueToSmt) (translate body) := by
  induction dom with
  | nil =>
    rw [evalAtForallRel_nil]
    rw [List.map_nil]
    rw [smtEvalAtForallRel_nil]
    rfl
  | cons hd rest ih =>
    have hBody := hBodyIH hd
    simp only [evalAtForallRel, List.map, smtEvalAtForallRel]
    cases hb : evalAt mode s sp ((var, hd) :: env) body with
    | none =>
      rw [hb] at hBody
      simp only [valueToSmt?] at hBody
      simp only [valueToSmt?, ← hBody]
    | some bv =>
      rw [hb] at hBody
      simp only [valueToSmt?_some] at hBody
      cases bv with
      | vBool b =>
        rw [valueToSmt_vBool] at hBody
        rw [← hBody]
        cases hr : evalAtForallRel mode s sp env var rest body with
        | none =>
          rw [hr] at ih
          simp only [valueToSmt?] at ih
          simp only [valueToSmt?, ← ih]
        | some rv =>
          rw [hr] at ih
          simp only [valueToSmt?_some] at ih
          cases rv with
          | vBool acc =>
            rw [valueToSmt_vBool] at ih
            rw [← ih]; simp only [valueToSmt?]; rfl
          | vInt n =>
            rw [valueToSmt_vInt] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEnum en' m' =>
            rw [valueToSmt_vEnum] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vEntity en' i' =>
            rw [valueToSmt_vEntity] at ih
            rw [← ih]; simp only [valueToSmt?]
          | vSet members =>
            rw [valueToSmt_vSet members] at ih
            rw [← ih]; simp only [valueToSmt?]
      | vInt n =>
        rw [valueToSmt_vInt] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEnum en' m' =>
        rw [valueToSmt_vEnum] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vEntity en' i' =>
        rw [valueToSmt_vEntity] at hBody
        rw [← hBody]; simp only [valueToSmt?]
      | vSet members =>
        rw [valueToSmt_vSet members] at hBody
        rw [← hBody]; simp only [valueToSmt?]

/-- Universal quantifier over a known state-relation domain. -/
theorem soundnessAt_forallRel_known (var rel : String) (body : Expr) (dom : List Value)
    (hDom : (sp.at mode).relationDomain rel = some dom)
    (hBodyIH : ∀ (val : Value),
      valueToSmt? (evalAt mode s sp ((var, val) :: env) body)
        = smtEvalAt mode (correlateModelPair s sp) ((var, valueToSmt val) :: correlateEnv env)
            (translate body)) :
    valueToSmt? (evalAt mode s sp env (.forallRel var rel body))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.forallRel var rel body)) := by
  rw [evalAt_forallRel_known mode s sp env var rel body dom hDom]
  simp only [translate]
  have hRel : ((correlateModelPair s sp).at mode).lookupRel rel = some (dom.map valueToSmt) := by
    rw [correlateModelPair_at]
    exact correlateModel_lookupRel s (sp.at mode) rel dom hDom
  rw [smtEvalAt_forallRel_known _ _ _ var rel (translate body) (dom.map valueToSmt) hRel]
  exact evalAtForallRel_correlated s env mode sp var dom body hBodyIH

/-- Set binary operations (union/intersect/diff) when both sub-results are sets. -/
theorem soundnessAt_setBin_sets (op : SetOp) (l r : Expr) (ls rs : List Value)
    (hSubL : valueToSmt? (evalAt mode s sp env l)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (hSubR : valueToSmt? (evalAt mode s sp env r)
              = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vSet ls))
    (hR : evalAt mode s sp env r = some (.vSet rs)) :
    valueToSmt? (evalAt mode s sp env (.setBin op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.setBin op l r)) := by
  rw [evalAt_setBin_sets mode s sp env op l r ls rs hL hR]
  rw [hL] at hSubL; rw [valueToSmt?_some, valueToSmt_vSet] at hSubL
  rw [hR] at hSubR; rw [valueToSmt?_some, valueToSmt_vSet] at hSubR
  cases op with
  | union =>
    simp only [evalSetBin, translate]
    rw [smtEvalAt_setUnion_sets _ _ _ (translate l) (translate r)
          (ls.map valueToSmt) (rs.map valueToSmt) hSubL.symm hSubR.symm]
    rw [valueToSmt?_some, valueToSmt_vSet]
    congr 2
    exact setUnionValues_map_valueToSmt ls rs
  | intersect =>
    simp only [evalSetBin, translate]
    rw [smtEvalAt_setIntersect_sets _ _ _ (translate l) (translate r)
          (ls.map valueToSmt) (rs.map valueToSmt) hSubL.symm hSubR.symm]
    rw [valueToSmt?_some, valueToSmt_vSet]
    congr 2
    exact setIntersectValues_map_valueToSmt ls rs
  | diff =>
    simp only [evalSetBin, translate]
    rw [smtEvalAt_setDiff_sets _ _ _ (translate l) (translate r)
          (ls.map valueToSmt) (rs.map valueToSmt) hSubL.symm hSubR.symm]
    rw [valueToSmt?_some, valueToSmt_vSet]
    congr 2
    exact setDiffValues_map_valueToSmt ls rs

/-! ## Phase 3c — universal `soundnessAt` theorem (issue #194).

Off-diagonal closure of the issue's first acceptance criterion: for every
`Expr` in the verified subset, every mode / StatePair / env, evaluating
under `evalAt` and `smtEvalAt` agree under the correlated `SmtModelPair`.
Mirrors the existing universal `soundness` theorem with carriers substituted:

  eval s st           ↦  evalAt mode s sp
  smtEval m           ↦  smtEvalAt mode mp
  correlateModel s st ↦  correlateModelPair s sp

Structural induction on `Expr` generalizing `env` and `mode`. Each
constructor's success path dispatches to its `soundnessAt_*` per-case theorem
(Phases 3a/3b) and its failure paths use the `smtEvalAt_*_none/_nonBool/
_nonInt/_nonSet` helpers (Phase 3c.1). Closes with no `sorry`. -/

private theorem soundnessAt_unNot_nonBool (e : Expr) (v : Value)
    (hNotBool : ∀ b, v ≠ .vBool b)
    (ih : valueToSmt? (evalAt mode s sp env e)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate e))
    (h : evalAt mode s sp env e = some v) :
    valueToSmt? (evalAt mode s sp env (.unNot e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.unNot e)) := by
  have hEvalNone : evalAt mode s sp env (.unNot e) = none := by
    simp only [evalAt, h]
    cases v with
    | vBool b => exact absurd rfl (hNotBool b)
    | _ => rfl
  rw [hEvalNone]
  simp only [translate]
  rw [h] at ih; rw [valueToSmt?_some] at ih
  simp only [valueToSmt?]
  exact (smtEvalAt_not_nonBool _ _ _ ih.symm (hSmtLvNotBool v hNotBool)).symm

private theorem soundnessAt_unNeg_nonInt (e : Expr) (v : Value)
    (hNotInt : ∀ n, v ≠ .vInt n)
    (ih : valueToSmt? (evalAt mode s sp env e)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate e))
    (h : evalAt mode s sp env e = some v) :
    valueToSmt? (evalAt mode s sp env (.unNeg e))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.unNeg e)) := by
  have hEvalNone : evalAt mode s sp env (.unNeg e) = none := by
    simp only [evalAt, h]
    cases v with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  simp only [translate]
  rw [h] at ih; rw [valueToSmt?_some] at ih
  simp only [valueToSmt?]
  exact (smtEvalAt_neg_nonInt _ _ _ ih.symm (hSmtLvNotInt v hNotInt)).symm

private theorem boolBin_lhs_nonBool_at (op : BoolBinOp) (l r : Expr) (lv : Value)
    (hNotBool : ∀ b, lv ≠ .vBool b)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some lv) :
    valueToSmt? (evalAt mode s sp env (.boolBin op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin op l r)) := by
  have hEvalNone : evalAt mode s sp env (.boolBin op l r) = none := by
    simp only [evalAt, hL]
    cases lv with
    | vBool b => exact absurd rfl (hNotBool b)
    | _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  cases op with
  | and =>
    simp only [translate]
    exact (smtEvalAt_and_lhs_nonBool _ _ _ ihL.symm (hSmtLvNotBool lv hNotBool)).symm
  | or =>
    simp only [translate]
    exact (smtEvalAt_or_lhs_nonBool _ _ _ ihL.symm (hSmtLvNotBool lv hNotBool)).symm
  | implies =>
    simp only [translate]
    exact (smtEvalAt_implies_lhs_nonBool _ _ _ ihL.symm (hSmtLvNotBool lv hNotBool)).symm
  | iff =>
    simp only [translate]
    have hImp : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.implies (translate l) (translate r)) = none :=
      smtEvalAt_implies_lhs_nonBool _ _ _ ihL.symm (hSmtLvNotBool lv hNotBool)
    exact (smtEvalAt_and_lhs_none _ _ _ hImp).symm

private theorem boolBin_rhs_nonBool_lhs_bool_at (op : BoolBinOp) (l r : Expr)
    (a : Bool) (rv : Value)
    (hNotBool : ∀ b, rv ≠ .vBool b)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vBool a))
    (hR : evalAt mode s sp env r = some rv) :
    valueToSmt? (evalAt mode s sp env (.boolBin op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.boolBin op l r)) := by
  have hEvalNone : evalAt mode s sp env (.boolBin op l r) = none := by
    simp only [evalAt, hL, hR]
    cases rv with
    | vBool b => exact absurd rfl (hNotBool b)
    | _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vBool] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  cases op with
  | and =>
    simp only [translate]
    exact (smtEvalAt_and_rhs_nonBool _ _ _ ihL.symm ihR.symm
              (hSmtLvNotBool rv hNotBool)).symm
  | or =>
    simp only [translate]
    exact (smtEvalAt_or_rhs_nonBool _ _ _ ihL.symm ihR.symm
              (hSmtLvNotBool rv hNotBool)).symm
  | implies =>
    simp only [translate]
    exact (smtEvalAt_implies_rhs_nonBool _ _ _ ihL.symm ihR.symm
              (hSmtLvNotBool rv hNotBool)).symm
  | iff =>
    simp only [translate]
    have hImp : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.implies (translate l) (translate r)) = none :=
      smtEvalAt_implies_rhs_nonBool _ _ _ ihL.symm ihR.symm (hSmtLvNotBool rv hNotBool)
    exact (smtEvalAt_and_lhs_none _ _ _ hImp).symm

private theorem arith_lhs_nonInt_at (op : ArithOp) (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some lv) :
    valueToSmt? (evalAt mode s sp env (.arith op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith op l r)) := by
  have hEvalNone : evalAt mode s sp env (.arith op l r) = none := by
    simp only [evalAt, hL]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => cases op <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  cases op with
  | add =>
    simp only [translate]
    exact (smtEvalAt_add_lhs_nonInt _ _ _ ihL.symm (hSmtLvNotInt lv hNotInt)).symm
  | sub =>
    simp only [translate]
    exact (smtEvalAt_sub_lhs_nonInt _ _ _ ihL.symm (hSmtLvNotInt lv hNotInt)).symm
  | mul =>
    simp only [translate]
    exact (smtEvalAt_mul_lhs_nonInt _ _ _ ihL.symm (hSmtLvNotInt lv hNotInt)).symm
  | div =>
    simp only [translate]
    exact (smtEvalAt_div_lhs_nonInt _ _ _ ihL.symm (hSmtLvNotInt lv hNotInt)).symm

private theorem arith_rhs_nonInt_lhs_int_at (op : ArithOp) (l r : Expr)
    (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some rv) :
    valueToSmt? (evalAt mode s sp env (.arith op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.arith op l r)) := by
  have hEvalNone : evalAt mode s sp env (.arith op l r) = none := by
    simp only [evalAt, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => cases op <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vInt] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?]
  cases op with
  | add =>
    simp only [translate]
    exact (smtEvalAt_add_rhs_nonInt _ _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)).symm
  | sub =>
    simp only [translate]
    exact (smtEvalAt_sub_rhs_nonInt _ _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)).symm
  | mul =>
    simp only [translate]
    exact (smtEvalAt_mul_rhs_nonInt _ _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)).symm
  | div =>
    simp only [translate]
    exact (smtEvalAt_div_rhs_nonInt _ _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)).symm

/-- All cmp ops fail when the LHS doesn't evaluate. Mirror of `cmp_lhs_eval_none`. -/
private theorem cmp_lhs_eval_none_at (op : CmpOp) (l r : Expr)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = none) :
    valueToSmt? (evalAt mode s sp env (.cmp op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp op l r)) := by
  have hEvalNone : evalAt mode s sp env (.cmp op l r) = none := by
    rw [evalAt_cmp_app]
    rw [hL]
    cases op <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; simp only [valueToSmt?] at ihL
  simp only [valueToSmt?]
  cases op with
  | eq =>
    simp only [translate]
    exact (smtEvalAt_eq_lhs_none _ _ _ ihL.symm).symm
  | neq =>
    simp only [translate]
    exact (smtEvalAt_not_none _ _ _ _ (smtEvalAt_eq_lhs_none _ _ _ ihL.symm)).symm
  | lt =>
    simp only [translate]
    exact (smtEvalAt_lt_lhs_none _ _ _ ihL.symm).symm
  | le =>
    simp only [translate]
    have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.lt (translate l) (translate r)) = none :=
      smtEvalAt_lt_lhs_none _ _ _ ihL.symm
    exact (smtEvalAt_or_lhs_none _ _ _ hLt).symm
  | gt =>
    simp only [translate]
    show none = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.lt (translate r) (translate l))
    simp only [smtEvalAt]
    rw [← ihL]
    cases (smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r)) with
    | none => rfl
    | some sv => cases sv <;> rfl
  | ge =>
    simp only [translate]
    have hEq : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.eq (translate l) (translate r)) = none :=
      smtEvalAt_eq_lhs_none _ _ _ ihL.symm
    have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.lt (translate r) (translate l)) = none := by
      simp only [smtEvalAt]
      rw [← ihL]
      cases (smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r)) with
      | none => rfl
      | some sv => cases sv <;> rfl
    rw [smtEvalAt]
    rw [hLt, hEq]

/-- All cmp ops fail when the RHS doesn't evaluate, given LHS eval succeeded. -/
private theorem cmp_rhs_eval_none_at (op : CmpOp) (l r : Expr) (lv : Value)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some lv)
    (hR : evalAt mode s sp env r = none) :
    valueToSmt? (evalAt mode s sp env (.cmp op l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp op l r)) := by
  have hEvalNone : evalAt mode s sp env (.cmp op l r) = none := by
    rw [evalAt_cmp_app, hL, hR]
    cases op <;> cases lv <;> rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  rw [hR] at ihR; simp only [valueToSmt?] at ihR
  simp only [valueToSmt?]
  cases op with
  | eq =>
    simp only [translate]
    exact (smtEvalAt_eq_rhs_none _ _ _ ihL.symm ihR.symm).symm
  | neq =>
    simp only [translate]
    exact (smtEvalAt_not_none _ _ _ _
            (smtEvalAt_eq_rhs_none _ _ _ ihL.symm ihR.symm)).symm
  | lt =>
    simp only [translate]
    cases lv with
    | vInt a =>
      rw [valueToSmt_vInt] at ihL
      exact (smtEvalAt_lt_rhs_none _ _ _ ihL.symm ihR.symm).symm
    | vBool b =>
      rw [valueToSmt_vBool] at ihL
      simp only [smtEvalAt]; rw [← ihL]
    | vEnum e' m' =>
      rw [valueToSmt_vEnum] at ihL
      simp only [smtEvalAt]; rw [← ihL]
    | vEntity e' i' =>
      rw [valueToSmt_vEntity] at ihL
      simp only [smtEvalAt]; rw [← ihL]
    | vSet members =>
      rw [valueToSmt_vSet] at ihL
      simp only [smtEvalAt]; rw [← ihL]
  | le =>
    simp only [translate]
    cases lv with
    | vInt a =>
      rw [valueToSmt_vInt] at ihL
      have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.lt (translate l) (translate r)) = none :=
        smtEvalAt_lt_rhs_none _ _ _ ihL.symm ihR.symm
      have hEq : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.eq (translate l) (translate r)) = none :=
        smtEvalAt_eq_rhs_none _ _ _ ihL.symm ihR.symm
      rw [smtEvalAt]
      rw [hLt, hEq]
    | vBool b =>
      rw [valueToSmt_vBool] at ihL
      have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.lt (translate l) (translate r)) = none := by
        simp only [smtEvalAt]; rw [← ihL]
      rw [smtEvalAt, hLt]
    | vEnum e' m' =>
      rw [valueToSmt_vEnum] at ihL
      have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.lt (translate l) (translate r)) = none := by
        simp only [smtEvalAt]; rw [← ihL]
      rw [smtEvalAt, hLt]
    | vEntity e' i' =>
      rw [valueToSmt_vEntity] at ihL
      have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.lt (translate l) (translate r)) = none := by
        simp only [smtEvalAt]; rw [← ihL]
      rw [smtEvalAt, hLt]
    | vSet members =>
      rw [valueToSmt_vSet] at ihL
      have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                    (.lt (translate l) (translate r)) = none := by
        simp only [smtEvalAt]; rw [← ihL]
      rw [smtEvalAt, hLt]
  | gt =>
    -- translate gt = .lt (translate r) (translate l). rhs of .lt is translate l.
    simp only [translate]
    -- smtEvalAt of (translate r) is none. The .lt arm checks lhs first; lhs is none → result none.
    show none = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.lt (translate r) (translate l))
    simp only [smtEvalAt, ← ihR]
  | ge =>
    -- translate ge = .or (.lt (translate r) (translate l)) (.eq (translate l) (translate r)).
    simp only [translate]
    -- The .lt's lhs is translate r → none. The .eq's rhs is translate r → none after lhs evals.
    have hLt : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.lt (translate r) (translate l)) = none := by
      simp only [smtEvalAt, ← ihR]
    have hEq : smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
                  (.eq (translate l) (translate r)) = none :=
      smtEvalAt_eq_rhs_none _ _ _ ihL.symm ihR.symm
    rw [smtEvalAt]
    rw [hLt, hEq]

/-- `cmp .lt` failure when LHS is not vInt. Mirror of `cmp_lt_lhs_nonInt`. -/
private theorem cmp_lt_lhs_nonInt_at (l r : Expr) (lv : Value)
    (hNotInt : ∀ n, lv ≠ .vInt n)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (_ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some lv) (rv : Value)
    (hR : evalAt mode s sp env r = some rv) :
    valueToSmt? (evalAt mode s sp env (.cmp .lt l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .lt l r)) := by
  have hEvalNone : evalAt mode s sp env (.cmp .lt l r) = none := by
    rw [evalAt_cmp_app, hL, hR]
    cases lv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => cases rv <;> rfl
  rw [hEvalNone]
  simp only [translate]
  rw [hL] at ihL; rw [valueToSmt?_some] at ihL
  simp only [valueToSmt?]
  have hSmtNotInt : ∀ n, valueToSmt lv ≠ .sInt n := hSmtLvNotInt lv hNotInt
  exact (smtEvalAt_lt_lhs_nonInt _ _ _ ihL.symm hSmtNotInt).symm

/-- `cmp .lt` failure when RHS is not vInt, given LHS = vInt. -/
private theorem cmp_lt_rhs_nonInt_lhs_int_at (l r : Expr) (a : Int) (rv : Value)
    (hNotInt : ∀ n, rv ≠ .vInt n)
    (ihL : valueToSmt? (evalAt mode s sp env l)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate l))
    (ihR : valueToSmt? (evalAt mode s sp env r)
            = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env) (translate r))
    (hL : evalAt mode s sp env l = some (.vInt a))
    (hR : evalAt mode s sp env r = some rv) :
    valueToSmt? (evalAt mode s sp env (.cmp .lt l r))
      = smtEvalAt mode (correlateModelPair s sp) (correlateEnv env)
          (translate (.cmp .lt l r)) := by
  have hEvalNone : evalAt mode s sp env (.cmp .lt l r) = none := by
    rw [evalAt_cmp_app, hL, hR]
    cases rv with
    | vInt n => exact absurd rfl (hNotInt n)
    | _ => rfl
  rw [hEvalNone]
  rw [hL] at ihL; rw [valueToSmt?_some, valueToSmt_vInt] at ihL
  rw [hR] at ihR; rw [valueToSmt?_some] at ihR
  simp only [valueToSmt?, translate]
  exact (smtEvalAt_lt_rhs_nonInt _ _ _ ihL.symm ihR.symm (hSmtLvNotInt rv hNotInt)).symm

end SpecRest
