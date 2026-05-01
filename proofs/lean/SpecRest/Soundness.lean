import SpecRest.IR
import SpecRest.Semantics
import SpecRest.Lemmas
import SpecRest.Smt
import SpecRest.Translate

namespace SpecRest

/-! # M_L.2 — Translator soundness theorem.

Statement (research doc §8.3):

  ∀ e, eval e = smtEval (translate e)

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
