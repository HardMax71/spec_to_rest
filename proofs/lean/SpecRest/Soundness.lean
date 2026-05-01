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

This file ships the foundations and the trivially-closing cases of
the §6.1 verified subset. The hard cases (`forallEnum`, full polymorphic
`cmp`, full `member`) require auxiliary inductive lemmas about the
correlation between Lean's state-relation domains and the SMT model's
predicate interpretation; those are queued in follow-up PRs. The
overall `soundness` theorem is therefore stated with `sorry` as a
meta-target placeholder for the M_L.2 closure work. -/

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
  constVals :=
    (st.scalars.map (fun (k, v) => (k, valueToSmt v))) ++
    (s.enums.flatMap (fun d =>
      d.members.map (fun m => (d.name ++ "." ++ m, SmtVal.sEnumElem d.name m))))
  predDomain :=
    st.relations.map (fun (k, vs) => (k, vs.map valueToSmt))

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

/-- A state-scalar binding correlates to a `lookupConst` constant. Structurally clean
    via `lookup_append` plus `lookup_map_value`. -/
theorem correlateModel_const_state_scalar (s : Schema) (st : State) (x : String) (v : Value)
    (h : st.lookupScalar x = some v) :
    (correlateModel s st).lookupConst x = some (valueToSmt v) := by
  unfold SmtModel.lookupConst correlateModel
  simp only []
  rw [List.lookup_append, lookup_map_value]
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

/-! ## Main theorem (statement)

The universal soundness theorem covers the full §6.1 verified subset
plus the M_L.1 extras (Iff / Neq / Le / Gt / Ge / Quantifier(Some)).
This slice ships the foundations and a representative set of proven
case theorems above. The remaining cases follow the same shape and
land in the M_L.2 closure follow-up PRs:

- `soundness_boolBin_or_bools`, `soundness_boolBin_implies_bools`,
  `soundness_boolBin_iff_bools` — straight analogue of `_and_`.
- `soundness_cmp_eq_vals`, `soundness_cmp_lt_ints`, etc. — cmp cases.
- `soundness_letIn` — env extension; needs to thread IH through the
  extended environment.
- `soundness_enumAccess`, `soundness_member`, `soundness_forallEnum` —
  require auxiliary lemmas about `correlateModel`'s `predDomain` and
  `sortMembers` correctness, plus a mutual-induction lemma for
  `evalForallEnum` ↔ `smtEvalForallEnum`. -/

theorem soundness (e : Expr) :
    valueToSmt? (eval s st env e)
      = smtEval (correlateModel s st) (correlateEnv env) (translate e) := by
  sorry

end SpecRest
