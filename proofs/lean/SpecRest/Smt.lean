namespace SpecRest

/-! # Shallow embedding of the SMT-LIB fragment emitted by `z3.Translator`.

Audits the §6.1-restricted target language. We support what the Scala
translator actually emits for the verified subset: Bool, Int, propositional
ops, equality, less-than, integer negation, bounded universal quantification
over uninterpreted-sort members, one-place uninterpreted predicates (for
state-relation domains), and `let`. All operators are total over the SMT
value space and yield `Option SmtVal` so partiality can be reflected back
into the soundness theorem. -/

inductive SmtSort where
  | bool
  | int
  | uninterp (name : String)
  deriving DecidableEq, Repr, Inhabited

inductive SmtVal where
  | sBool (b : Bool)
  | sInt (n : Int)
  | sEnumElem (enumName memberName : String)
  | sEntityElem (entityName id : String)
  deriving DecidableEq, Repr, Inhabited

inductive SmtTerm where
  | bLit (b : Bool)
  | iLit (n : Int)
  | var (name : String)
  | enumElemConst (enumName memberName : String)
  | not (t : SmtTerm)
  | and (l r : SmtTerm)
  | or (l r : SmtTerm)
  | implies (l r : SmtTerm)
  | eq (l r : SmtTerm)
  | lt (l r : SmtTerm)
  | neg (t : SmtTerm)
  | add (l r : SmtTerm)
  | sub (l r : SmtTerm)
  | mul (l r : SmtTerm)
  | div (l r : SmtTerm)
  | inDom (relName : String) (arg : SmtTerm)
  | cardRel (relName : String)
  | letIn (var : String) (value body : SmtTerm)
  | forallEnum (var : String) (sortName : String) (body : SmtTerm)
  | forallRel (var : String) (relName : String) (body : SmtTerm)
  | indexRel (relName : String) (key : SmtTerm)
  | fieldAccess (base : SmtTerm) (fieldName : String)
  deriving Repr, Inhabited

/-- An SMT model resolves the free symbols left by the translator: the
    finite-domain enum/entity sorts, the state scalars + enum-member
    constants, and the state-relation domain predicates. -/
structure SmtModel where
  sortMembers : List (String × List String)
  constVals : List (String × SmtVal)
  predDomain : List (String × List SmtVal)
  predLookup : List (String × List (SmtVal × SmtVal))
  predFields : List (String × List (String × SmtVal))
  deriving Repr, Inhabited

def SmtModel.empty : SmtModel :=
  { sortMembers := [], constVals := [], predDomain := [], predLookup := [], predFields := [] }

def SmtModel.lookupConst (m : SmtModel) (name : String) : Option SmtVal :=
  List.lookup name m.constVals

def SmtModel.lookupSortMembers (m : SmtModel) (sortName : String) : Option (List String) :=
  List.lookup sortName m.sortMembers

def SmtModel.lookupRel (m : SmtModel) (name : String) : Option (List SmtVal) :=
  List.lookup name m.predDomain

def SmtModel.lookupPairs (m : SmtModel) (name : String) : Option (List (SmtVal × SmtVal)) :=
  List.lookup name m.predLookup

def SmtModel.lookupKey (m : SmtModel) (relName : String) (key : SmtVal) : Option SmtVal :=
  match List.lookup relName m.predLookup with
  | some pairs => (pairs.find? (fun p => p.1 == key)).map Prod.snd
  | none       => none

/-- M_L.4.k: keys are entity IDs (the `id` carried by `vEntity`/`sEntityElem`),
    not scalar names. The shape and lookup body are unchanged from M_L.4.h. -/
def SmtModel.lookupField (m : SmtModel) (entityId fieldName : String) : Option SmtVal :=
  match List.lookup entityId m.predFields with
  | some fields => List.lookup fieldName fields
  | none        => none

abbrev SmtEnv := List (String × SmtVal)

def SmtEnv.lookup (env : SmtEnv) (name : String) : Option SmtVal :=
  List.lookup name env

def asSmtBool : SmtVal → Option Bool
  | .sBool b => some b
  | _        => none

def asSmtInt : SmtVal → Option Int
  | .sInt n => some n
  | _       => none

mutual

  def smtEval (m : SmtModel) (env : SmtEnv) : SmtTerm → Option SmtVal
    | .bLit b => some (.sBool b)
    | .iLit n => some (.sInt n)
    | .var x =>
        match env.lookup x with
        | some v => some v
        | none   => m.lookupConst x
    | .enumElemConst en mem =>
        match m.lookupSortMembers en with
        | some members => if members.contains mem then some (.sEnumElem en mem) else none
        | none         => none
    | .not t =>
        match smtEval m env t with
        | some (.sBool b) => some (.sBool (!b))
        | _               => none
    | .and l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sBool a), some (.sBool b) => some (.sBool (a && b))
        | _, _                             => none
    | .or l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sBool a), some (.sBool b) => some (.sBool (a || b))
        | _, _                             => none
    | .implies l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sBool a), some (.sBool b) => some (.sBool (!a || b))
        | _, _                             => none
    | .eq l r =>
        match smtEval m env l, smtEval m env r with
        | some a, some b => some (.sBool (a == b))
        | _, _           => none
    | .lt l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sInt a), some (.sInt b) => some (.sBool (decide (a < b)))
        | _, _                           => none
    | .neg t =>
        match smtEval m env t with
        | some (.sInt n) => some (.sInt (-n))
        | _              => none
    | .add l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sInt a), some (.sInt b) => some (.sInt (a + b))
        | _, _                           => none
    | .sub l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sInt a), some (.sInt b) => some (.sInt (a - b))
        | _, _                           => none
    | .mul l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sInt a), some (.sInt b) => some (.sInt (a * b))
        | _, _                           => none
    | .div l r =>
        match smtEval m env l, smtEval m env r with
        | some (.sInt _), some (.sInt 0) => none
        -- Euclidean division (matches SMT-LIB `(div a b)` and `Semantics.lean`'s evalArith).
        | some (.sInt a), some (.sInt b) => some (.sInt (a.ediv b))
        | _, _                           => none
    | .inDom relName arg =>
        match smtEval m env arg with
        | some v =>
            match m.lookupRel relName with
            | some dom => some (.sBool (dom.contains v))
            | none     => none
        | none => none
    | .cardRel relName =>
        match m.lookupRel relName with
        | some dom => some (.sInt (Int.ofNat dom.length))
        | none     => none
    | .letIn x value body =>
        match smtEval m env value with
        | some v => smtEval m ((x, v) :: env) body
        | none   => none
    | .forallEnum var sortName body =>
        match m.lookupSortMembers sortName with
        | some members => smtEvalForallEnum m env var sortName members body
        | none         => none
    | .forallRel var relName body =>
        match m.lookupRel relName with
        | some dom => smtEvalForallRel m env var dom body
        | none     => none
    | .indexRel relName key =>
        match smtEval m env key with
        | some kv => m.lookupKey relName kv
        | none    => none
    | .fieldAccess base fieldName =>
        match smtEval m env base with
        | some (.sEntityElem _ id) => m.lookupField id fieldName
        | _                        => none
  termination_by t => (sizeOf t, 0)

  def smtEvalForallEnum (m : SmtModel) (env : SmtEnv)
      (var : String) (sortName : String)
      (members : List String) (body : SmtTerm) : Option SmtVal :=
    match members with
    | [] => some (.sBool true)
    | mem :: rest =>
        match smtEval m ((var, .sEnumElem sortName mem) :: env) body with
        | some (.sBool b) =>
            match smtEvalForallEnum m env var sortName rest body with
            | some (.sBool acc) => some (.sBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, members.length)

  def smtEvalForallRel (m : SmtModel) (env : SmtEnv)
      (var : String) (dom : List SmtVal) (body : SmtTerm) : Option SmtVal :=
    match dom with
    | [] => some (.sBool true)
    | v :: rest =>
        match smtEval m ((var, v) :: env) body with
        | some (.sBool b) =>
            match smtEvalForallRel m env var rest body with
            | some (.sBool acc) => some (.sBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, dom.length)

end

/-! ## Per-constructor characterization lemmas for `smtEval`.

Mirrors the M_L.1 pattern: mutual `smtEval` doesn't reduce via `rfl`,
so we expose named equations for each constructor. M_L.2's soundness
proofs use these. -/

variable (m : SmtModel) (env : SmtEnv)

theorem smtEval_bLit (b : Bool) :
    smtEval m env (.bLit b) = some (.sBool b) := by
  simp only [smtEval]

theorem smtEval_iLit (n : Int) :
    smtEval m env (.iLit n) = some (.sInt n) := by
  simp only [smtEval]

theorem smtEval_var (x : String) :
    smtEval m env (.var x) =
      (match env.lookup x with
        | some v => some v
        | none   => m.lookupConst x) := by
  simp only [smtEval]

theorem smtEval_var_local {x : String} {v : SmtVal} (h : env.lookup x = some v) :
    smtEval m env (.var x) = some v := by
  simp only [smtEval, h]

theorem smtEval_var_const {x : String} {v : SmtVal}
    (hEnv : env.lookup x = none) (hConst : m.lookupConst x = some v) :
    smtEval m env (.var x) = some v := by
  simp only [smtEval, hEnv, hConst]

theorem smtEval_enumElemConst_known {en mem : String} {members : List String}
    (hSort : m.lookupSortMembers en = some members)
    (hMember : members.contains mem = true) :
    smtEval m env (.enumElemConst en mem) = some (.sEnumElem en mem) := by
  simp only [smtEval, hSort, hMember, if_true]

theorem smtEval_not_bool (t : SmtTerm) (b : Bool)
    (h : smtEval m env t = some (.sBool b)) :
    smtEval m env (.not t) = some (.sBool (!b)) := by
  simp only [smtEval, h]

theorem smtEval_and_bools (l r : SmtTerm) (a b : Bool)
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some (.sBool b)) :
    smtEval m env (.and l r) = some (.sBool (a && b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_or_bools (l r : SmtTerm) (a b : Bool)
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some (.sBool b)) :
    smtEval m env (.or l r) = some (.sBool (a || b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_implies_bools (l r : SmtTerm) (a b : Bool)
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some (.sBool b)) :
    smtEval m env (.implies l r) = some (.sBool (!a || b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_eq_vals (l r : SmtTerm) (a b : SmtVal)
    (hl : smtEval m env l = some a)
    (hr : smtEval m env r = some b) :
    smtEval m env (.eq l r) = some (.sBool (a == b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_lt_ints (l r : SmtTerm) (a b : Int)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt b)) :
    smtEval m env (.lt l r) = some (.sBool (decide (a < b))) := by
  simp only [smtEval, hl, hr]

theorem smtEval_neg_int (t : SmtTerm) (n : Int)
    (h : smtEval m env t = some (.sInt n)) :
    smtEval m env (.neg t) = some (.sInt (-n)) := by
  simp only [smtEval, h]

theorem smtEval_add_ints (l r : SmtTerm) (a b : Int)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt b)) :
    smtEval m env (.add l r) = some (.sInt (a + b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_sub_ints (l r : SmtTerm) (a b : Int)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt b)) :
    smtEval m env (.sub l r) = some (.sInt (a - b)) := by
  simp only [smtEval, hl, hr]

theorem smtEval_mul_ints (l r : SmtTerm) (a b : Int)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt b)) :
    smtEval m env (.mul l r) = some (.sInt (a * b)) := by
  simp only [smtEval, hl, hr]

/-- Division: total over `Int` but `none` on divisor zero. Z3's `(div x 0)` is well-typed
    but unspecified; restricting `eval` to `none` keeps the verified subset honest. -/
theorem smtEval_div_ints_nonZero (l r : SmtTerm) (a b : Int) (hbz : b ≠ 0)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt b)) :
    smtEval m env (.div l r) = some (.sInt (a.ediv b)) := by
  cases b with
  | ofNat k =>
    cases k with
    | zero => exact absurd rfl hbz
    | succ _ => simp only [smtEval, hl, hr]
  | negSucc _ => simp only [smtEval, hl, hr]

theorem smtEval_div_zero (l r : SmtTerm) (a : Int)
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some (.sInt 0)) :
    smtEval m env (.div l r) = none := by
  simp only [smtEval, hl, hr]

/-! ### Arithmetic failure-case helpers (lhs/rhs none / non-Int). -/

theorem smtEval_add_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.add l r) = none := by simp only [smtEval, h]
theorem smtEval_sub_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.sub l r) = none := by simp only [smtEval, h]
theorem smtEval_mul_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.mul l r) = none := by simp only [smtEval, h]
theorem smtEval_div_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.div l r) = none := by simp only [smtEval, h]

theorem smtEval_add_rhs_none {l r : SmtTerm} {a : Int}
    (hl : smtEval m env l = some (.sInt a)) (hr : smtEval m env r = none) :
    smtEval m env (.add l r) = none := by simp only [smtEval, hl, hr]
theorem smtEval_sub_rhs_none {l r : SmtTerm} {a : Int}
    (hl : smtEval m env l = some (.sInt a)) (hr : smtEval m env r = none) :
    smtEval m env (.sub l r) = none := by simp only [smtEval, hl, hr]
theorem smtEval_mul_rhs_none {l r : SmtTerm} {a : Int}
    (hl : smtEval m env l = some (.sInt a)) (hr : smtEval m env r = none) :
    smtEval m env (.mul l r) = none := by simp only [smtEval, hl, hr]
theorem smtEval_div_rhs_none {l r : SmtTerm} {a : Int}
    (hl : smtEval m env l = some (.sInt a)) (hr : smtEval m env r = none) :
    smtEval m env (.div l r) = none := by simp only [smtEval, hl, hr]

theorem smtEval_add_lhs_nonInt {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.add l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_sub_lhs_nonInt {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.sub l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_mul_lhs_nonInt {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.mul l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_div_lhs_nonInt {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.div l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl

theorem smtEval_add_rhs_nonInt {l r : SmtTerm} {a : Int} {v : SmtVal}
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.add l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_sub_rhs_nonInt {l r : SmtTerm} {a : Int} {v : SmtVal}
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.sub l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_mul_rhs_nonInt {l r : SmtTerm} {a : Int} {v : SmtVal}
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.mul l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl
theorem smtEval_div_rhs_nonInt {l r : SmtTerm} {a : Int} {v : SmtVal}
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.div l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | _ => rfl

theorem smtEval_letIn_some (x : String) (value body : SmtTerm) (v : SmtVal)
    (h : smtEval m env value = some v) :
    smtEval m env (.letIn x value body) = smtEval m ((x, v) :: env) body := by
  simp only [smtEval, h]

theorem smtEval_inDom_resolved (relName : String) (arg : SmtTerm) (v : SmtVal) (dom : List SmtVal)
    (hArg : smtEval m env arg = some v)
    (hRel : m.lookupRel relName = some dom) :
    smtEval m env (.inDom relName arg) = some (.sBool (dom.contains v)) := by
  simp only [smtEval, hArg, hRel]

theorem smtEval_cardRel_resolved (relName : String) (dom : List SmtVal)
    (hRel : m.lookupRel relName = some dom) :
    smtEval m env (.cardRel relName) = some (.sInt (Int.ofNat dom.length)) := by
  simp only [smtEval, hRel]

theorem smtEval_indexRel_resolved (relName : String) (key : SmtTerm) (kv : SmtVal)
    (hKey : smtEval m env key = some kv) :
    smtEval m env (.indexRel relName key) = m.lookupKey relName kv := by
  simp only [smtEval, hKey]

theorem smtEval_indexRel_key_none {relName : String} {key : SmtTerm}
    (hKey : smtEval m env key = none) :
    smtEval m env (.indexRel relName key) = none := by
  simp only [smtEval, hKey]

theorem smtEval_fieldAccess_resolved (base : SmtTerm) (en id fieldName : String)
    (hBase : smtEval m env base = some (.sEntityElem en id)) :
    smtEval m env (.fieldAccess base fieldName)
      = m.lookupField id fieldName := by
  simp only [smtEval, hBase]

theorem smtEval_fieldAccess_base_none (base : SmtTerm) (fieldName : String)
    (hBase : smtEval m env base = none) :
    smtEval m env (.fieldAccess base fieldName) = none := by
  simp only [smtEval, hBase]

theorem smtEval_fieldAccess_nonEntity {base : SmtTerm} {fieldName : String} {v : SmtVal}
    (hBase : smtEval m env base = some v)
    (hNotEntity : ∀ en id, v ≠ .sEntityElem en id) :
    smtEval m env (.fieldAccess base fieldName) = none := by
  simp only [smtEval, hBase]
  cases v with
  | sEntityElem en id => exact absurd rfl (hNotEntity en id)
  | sBool _ => rfl
  | sInt _ => rfl
  | sEnumElem _ _ => rfl

theorem smtEval_cardRel_unknown (relName : String)
    (h : m.lookupRel relName = none) :
    smtEval m env (.cardRel relName) = none := by
  simp only [smtEval, h]

theorem smtEval_forallEnum_known (var sortName : String) (body : SmtTerm) (members : List String)
    (h : m.lookupSortMembers sortName = some members) :
    smtEval m env (.forallEnum var sortName body)
      = smtEvalForallEnum m env var sortName members body := by
  simp only [smtEval, h]

theorem smtEvalForallEnum_nil (var sortName : String) (body : SmtTerm) :
    smtEvalForallEnum m env var sortName [] body = some (.sBool true) := by
  simp only [smtEvalForallEnum]

theorem smtEval_forallRel_known (var relName : String) (body : SmtTerm) (dom : List SmtVal)
    (h : m.lookupRel relName = some dom) :
    smtEval m env (.forallRel var relName body)
      = smtEvalForallRel m env var dom body := by
  simp only [smtEval, h]

theorem smtEvalForallRel_nil (var : String) (body : SmtTerm) :
    smtEvalForallRel m env var [] body = some (.sBool true) := by
  simp only [smtEvalForallRel]

/-! ## Failure-case characterization lemmas. -/

theorem smtEval_not_none (t : SmtTerm) (h : smtEval m env t = none) :
    smtEval m env (.not t) = none := by
  simp only [smtEval, h]

theorem smtEval_not_nonBool {t : SmtTerm} {v : SmtVal}
    (h : smtEval m env t = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.not t) = none := by
  simp only [smtEval, h]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_neg_none (t : SmtTerm) (h : smtEval m env t = none) :
    smtEval m env (.neg t) = none := by
  simp only [smtEval, h]

theorem smtEval_neg_nonInt {t : SmtTerm} {v : SmtVal}
    (h : smtEval m env t = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.neg t) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | sBool _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_and_lhs_nonBool {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.and l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_and_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.and l r) = none := by
  simp only [smtEval, h]

theorem smtEval_and_rhs_none {l r : SmtTerm} {a : Bool}
    (hl : smtEval m env l = some (.sBool a)) (hr : smtEval m env r = none) :
    smtEval m env (.and l r) = none := by
  simp only [smtEval, hl, hr]

theorem smtEval_and_rhs_nonBool {l r : SmtTerm} {a : Bool} {v : SmtVal}
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.and l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_or_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.or l r) = none := by
  simp only [smtEval, h]

theorem smtEval_or_lhs_nonBool {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.or l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_or_rhs_none {l r : SmtTerm} {a : Bool}
    (hl : smtEval m env l = some (.sBool a)) (hr : smtEval m env r = none) :
    smtEval m env (.or l r) = none := by
  simp only [smtEval, hl, hr]

theorem smtEval_or_rhs_nonBool {l r : SmtTerm} {a : Bool} {v : SmtVal}
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.or l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_implies_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.implies l r) = none := by
  simp only [smtEval, h]

theorem smtEval_implies_lhs_nonBool {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.implies l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_implies_rhs_none {l r : SmtTerm} {a : Bool}
    (hl : smtEval m env l = some (.sBool a)) (hr : smtEval m env r = none) :
    smtEval m env (.implies l r) = none := by
  simp only [smtEval, hl, hr]

theorem smtEval_implies_rhs_nonBool {l r : SmtTerm} {a : Bool} {v : SmtVal}
    (hl : smtEval m env l = some (.sBool a))
    (hr : smtEval m env r = some v) (hNotBool : ∀ b, v ≠ .sBool b) :
    smtEval m env (.implies l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sBool b => exact absurd rfl (hNotBool b)
  | sInt _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_eq_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.eq l r) = none := by
  simp only [smtEval, h]

theorem smtEval_eq_rhs_none {l r : SmtTerm} {a : SmtVal}
    (hl : smtEval m env l = some a) (hr : smtEval m env r = none) :
    smtEval m env (.eq l r) = none := by
  simp only [smtEval, hl, hr]

theorem smtEval_lt_lhs_none {l r : SmtTerm} (h : smtEval m env l = none) :
    smtEval m env (.lt l r) = none := by
  simp only [smtEval, h]

theorem smtEval_lt_lhs_nonInt {l r : SmtTerm} {v : SmtVal}
    (h : smtEval m env l = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.lt l r) = none := by
  simp only [smtEval, h]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | sBool _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_lt_rhs_none {l r : SmtTerm} {a : Int}
    (hl : smtEval m env l = some (.sInt a)) (hr : smtEval m env r = none) :
    smtEval m env (.lt l r) = none := by
  simp only [smtEval, hl, hr]

theorem smtEval_lt_rhs_nonInt {l r : SmtTerm} {a : Int} {v : SmtVal}
    (hl : smtEval m env l = some (.sInt a))
    (hr : smtEval m env r = some v) (hNotInt : ∀ n, v ≠ .sInt n) :
    smtEval m env (.lt l r) = none := by
  simp only [smtEval, hl, hr]
  cases v with
  | sInt n => exact absurd rfl (hNotInt n)
  | sBool _ => rfl
  | sEnumElem _ _ => rfl
  | sEntityElem _ _ => rfl

theorem smtEval_letIn_none {x : String} {value body : SmtTerm}
    (h : smtEval m env value = none) :
    smtEval m env (.letIn x value body) = none := by
  simp only [smtEval, h]

theorem smtEval_inDom_arg_none {relName : String} {arg : SmtTerm}
    (h : smtEval m env arg = none) :
    smtEval m env (.inDom relName arg) = none := by
  simp only [smtEval, h]

theorem smtEval_inDom_rel_none {relName : String} {arg : SmtTerm} {v : SmtVal}
    (hArg : smtEval m env arg = some v) (hRel : m.lookupRel relName = none) :
    smtEval m env (.inDom relName arg) = none := by
  simp only [smtEval, hArg, hRel]

theorem smtEval_forallEnum_unknown {var sortName : String} {body : SmtTerm}
    (h : m.lookupSortMembers sortName = none) :
    smtEval m env (.forallEnum var sortName body) = none := by
  simp only [smtEval, h]

theorem smtEval_forallRel_unknown {var relName : String} {body : SmtTerm}
    (h : m.lookupRel relName = none) :
    smtEval m env (.forallRel var relName body) = none := by
  simp only [smtEval, h]

theorem smtEval_enumElemConst_unknown {en mem : String}
    (h : m.lookupSortMembers en = none) :
    smtEval m env (.enumElemConst en mem) = none := by
  simp only [smtEval, h]

theorem smtEval_enumElemConst_nonMember {en mem : String} {members : List String}
    (hSort : m.lookupSortMembers en = some members) (hMember : members.contains mem = false) :
    smtEval m env (.enumElemConst en mem) = none := by
  simp only [smtEval, hSort, hMember]
  rfl

end SpecRest
