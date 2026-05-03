import SpecRest.IR

namespace SpecRest

inductive Value where
  | vBool (b : Bool)
  | vInt (n : Int)
  | vEnum (enumName memberName : String)
  | vEntity (entityName id : String)
  | vSet (members : List Value)
  /-- Entity with a single-field override, recursive on `base : Value`. Multi-field
      `e with { a := va, b := vb }` chains: `vEntityWith (vEntityWith eBase a va) b vb`.
      M_L.4.b-ext Phase 4b (issue #194) — Skolem encoding for `Expr.withRec`.
      `Value.fieldLookup` walks the chain: matches override on `fld` first, falls
      back to `base` recursively until reaching a `vEntity` (terminal) or a non-entity. -/
  | vEntityWith (base : Value) (fld : String) (value : Value)
  deriving Repr, Inhabited

mutual
  def decEqValue : (a b : Value) → Decidable (a = b)
    | .vBool a, .vBool b =>
        if h : a = b then isTrue (by cases h; rfl)
        else isFalse (by intro h'; cases h'; exact h rfl)
    | .vInt a, .vInt b =>
        if h : a = b then isTrue (by cases h; rfl)
        else isFalse (by intro h'; cases h'; exact h rfl)
    | .vEnum en mem, .vEnum en' mem' =>
        if hEn : en = en' then
          if hMem : mem = mem' then isTrue (by cases hEn; cases hMem; rfl)
          else isFalse (by intro h'; cases h'; exact hMem rfl)
        else isFalse (by intro h'; cases h'; exact hEn rfl)
    | .vEntity en id, .vEntity en' id' =>
        if hEn : en = en' then
          if hId : id = id' then isTrue (by cases hEn; cases hId; rfl)
          else isFalse (by intro h'; cases h'; exact hId rfl)
        else isFalse (by intro h'; cases h'; exact hEn rfl)
    | .vSet xs, .vSet ys =>
        match decEqValueList xs ys with
        | isTrue h  => isTrue (by cases h; rfl)
        | isFalse h => isFalse (by intro h'; cases h'; exact h rfl)
    | .vEntityWith ba fa va, .vEntityWith bb fb vb =>
        match decEqValue ba bb with
        | isFalse hB => isFalse (by intro h'; cases h'; exact hB rfl)
        | isTrue hB =>
          if hF : fa = fb then
            match decEqValue va vb with
            | isFalse hV => isFalse (by intro h'; cases h'; exact hV rfl)
            | isTrue hV => isTrue (by cases hB; cases hF; cases hV; rfl)
          else isFalse (by intro h'; cases h'; exact hF rfl)
    | .vBool _, .vInt _ => isFalse (by intro h; cases h)
    | .vBool _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vBool _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vBool _, .vSet _ => isFalse (by intro h; cases h)
    | .vBool _, .vEntityWith _ _ _ => isFalse (by intro h; cases h)
    | .vInt _, .vBool _ => isFalse (by intro h; cases h)
    | .vInt _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vInt _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vInt _, .vSet _ => isFalse (by intro h; cases h)
    | .vInt _, .vEntityWith _ _ _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vBool _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vInt _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vSet _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vEntityWith _ _ _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vBool _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vInt _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vSet _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vEntityWith _ _ _ => isFalse (by intro h; cases h)
    | .vSet _, .vBool _ => isFalse (by intro h; cases h)
    | .vSet _, .vInt _ => isFalse (by intro h; cases h)
    | .vSet _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vSet _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vSet _, .vEntityWith _ _ _ => isFalse (by intro h; cases h)
    | .vEntityWith _ _ _, .vBool _ => isFalse (by intro h; cases h)
    | .vEntityWith _ _ _, .vInt _ => isFalse (by intro h; cases h)
    | .vEntityWith _ _ _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vEntityWith _ _ _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vEntityWith _ _ _, .vSet _ => isFalse (by intro h; cases h)

  def decEqValueList : (xs ys : List Value) → Decidable (xs = ys)
    | [], [] => isTrue rfl
    | [], _ :: _ => isFalse (by intro h; cases h)
    | _ :: _, [] => isFalse (by intro h; cases h)
    | x :: xs, y :: ys =>
        match decEqValue x y, decEqValueList xs ys with
        | isTrue hHead, isTrue hTail => isTrue (by cases hHead; cases hTail; rfl)
        | isFalse hHead, _ => isFalse (by intro h; cases h; exact hHead rfl)
        | _, isFalse hTail => isFalse (by intro h; cases h; exact hTail rfl)
end

instance : DecidableEq Value := decEqValue

def containsValueForBeq : List Value → Value → Bool
  | [], _ => false
  | x :: xs, v => decide (x = v) || containsValueForBeq xs v

def subsetValueList : List Value → List Value → Bool
  | [], _ => true
  | x :: xs, ys => containsValueForBeq ys x && subsetValueList xs ys

def setEqValueList (xs ys : List Value) : Bool :=
  subsetValueList xs ys && subsetValueList ys xs

def beqValue : Value → Value → Bool
  | .vBool a, .vBool b => a == b
  | .vInt a, .vInt b => a == b
  | .vEnum en mem, .vEnum en' mem' => en == en' && mem == mem'
  | .vEntity en id, .vEntity en' id' => en == en' && id == id'
  | .vSet xs, .vSet ys => setEqValueList xs ys
  | .vEntityWith ba fa va, .vEntityWith bb fb vb =>
      -- Recursive `beqValue` so set equality stays extensional inside
      -- record-update chains: two overrides that differ only by set element
      -- order (e.g., `vSet [1, 2]` vs `vSet [2, 1]`) compare equal here, just
      -- as plain `vSet` does. Mirrors `valueEq` in EvalIR.scala.
      beqValue ba bb && fa == fb && beqValue va vb
  | _, _ => false

instance : BEq Value where
  beq := beqValue

abbrev Env := List (String × Value)

structure Schema where
  enums : List EnumDecl
  entities : List EntityDecl
  deriving Repr, Inhabited

def Schema.empty : Schema := { enums := [], entities := [] }

def Schema.lookupEnum (s : Schema) (name : String) : Option EnumDecl :=
  s.enums.find? (·.name == name)

def Schema.lookupEntity (s : Schema) (name : String) : Option EntityDecl :=
  s.entities.find? (·.name == name)

structure State where
  scalars : List (String × Value)
  relations : List (String × List Value)
  lookups : List (String × List (Value × Value))
  entityFields : List (String × List (String × Value))
  deriving Repr, Inhabited

def State.empty : State :=
  { scalars := [], relations := [], lookups := [], entityFields := [] }

def State.lookupScalar (st : State) (name : String) : Option Value :=
  List.lookup name st.scalars

def State.relationDomain (st : State) (name : String) : Option (List Value) :=
  List.lookup name st.relations

def State.relationPairs (st : State) (name : String) : Option (List (Value × Value)) :=
  List.lookup name st.lookups

def State.lookupKey (st : State) (relName : String) (key : Value) : Option Value :=
  match List.lookup relName st.lookups with
  | some pairs => (pairs.find? (fun p => p.1 == key)).map Prod.snd
  | none       => none

/-- Lookup a field on an entity instance keyed by entity ID. M_L.4.k changed
    the key semantics from scalar-name (M_L.4.h) to entity-ID so the same
    eval arm covers (a) bare-Identifier `state_scalar.field`, (b)
    Index-result `users[uid].field`, (c) chained `current_user.profile.email`,
    and (d) quantifier-bound `forall t in tasks, t.field`. The shape of
    `State.entityFields` is unchanged; only the meaning of the outer key
    moved from scalar-name to entity-ID. Demo-state seeding mints fresh
    IDs for entity-typed scalars so the legacy bare-Identifier path remains
    closed.

    Why the key is `id` alone (not `(entityName, id)`): translator soundness
    only requires that `eval`'s lookup and `smtEval`'s lookup agree on every
    State, which they do regardless of the carrier's key shape (both sides
    use the same `id` after `valueToSmt`). Demo-state seeding mints unique
    ids per scalar (`<scalarName>__id`) and per nested entity-typed field
    (`<entityId>__<fieldName>`), so distinct entity instances never alias in
    the table. A user-constructed State that reuses an id across entity
    types would conflate them in both Lean `eval` and the correlated SMT
    model — soundness still holds (both sides agree on the conflation), but
    the cert would no longer claim what production Z3 (which keys per-(entity,
    field) UF) computes. The `(entityName, id)` carrier is a future option if
    spec-vs-cert modeling fidelity becomes load-bearing; for the M_L.4.k
    closure it does not. -/
def State.lookupField (st : State) (entityId fieldName : String) : Option Value :=
  match List.lookup entityId st.entityFields with
  | some fields => List.lookup fieldName fields
  | none        => none

/-- Value-level field lookup that walks `vEntityWith` chains (Phase 4b). For a
    `vEntity en id`, looks up `fld` in `st.entityFields[id]`. For
    `vEntityWith base ovFld ovValue`, returns `ovValue` if `fld == ovFld`, else
    recurses on `base`. For other Value shapes (vBool/vInt/vEnum/vSet),
    returns none. Termination: structural recursion on Value via the `base`
    field of `vEntityWith`. -/
def Value.fieldLookup (st : State) : Value → String → Option Value
  | .vEntity _ id, fld => st.lookupField id fld
  | .vEntityWith base ovFld ovValue, fld =>
      if fld = ovFld then some ovValue
      else Value.fieldLookup st base fld
  | _, _ => none

def Env.lookup (env : Env) (name : String) : Option Value :=
  List.lookup name env

/-! ## Two-state carrier (M_L.4.b-ext, issue #194 — Phase 1 scaffolding).

The `StatePair` / `StateMode` pair lets specs talk about pre- and post-state
references separately, which is what an ensures clause like `count' = count + 1`
actually means (`post.count = pre.count + 1`). This file ships only the carrier
and a mode-aware `evalAt` parallel to `eval`; the universal soundness theorem,
the SMT-side mode threading, and the off-diagonal claim are deliberately left
for follow-up phases. The diagonal-collapse theorem `evalAt_diagonal_eq_eval`
proves both functions agree when `sp.pre = sp.post`, so every existing per-case
soundness theorem (which is stated against `eval`) continues to hold unchanged.

Mirrors `modules/verify/src/main/scala/specrest/verify/z3/Translator.scala:17-18`
where the production translator uses the same `StateMode { Pre, Post }` enum
and threads it through `Translator.translateExpr` via mutable `ctx.stateMode`. -/

inductive StateMode where
  | pre
  | post
  deriving DecidableEq, Repr, Inhabited

structure StatePair where
  pre  : State
  post : State
  deriving Repr, Inhabited

def StatePair.at : StatePair → StateMode → State
  | sp, .pre  => sp.pre
  | sp, .post => sp.post

@[simp] theorem StatePair.at_pre (sp : StatePair) :
    sp.at .pre = sp.pre := rfl

@[simp] theorem StatePair.at_post (sp : StatePair) :
    sp.at .post = sp.post := rfl

/-- Diagonal `StatePair` — both projections collapse to the same `State`.
    Every existing single-state caller maps to this case. -/
def StatePair.diag (st : State) : StatePair := { pre := st, post := st }

@[simp] theorem StatePair.diag_pre (st : State) :
    (StatePair.diag st).pre = st := rfl

@[simp] theorem StatePair.diag_post (st : State) :
    (StatePair.diag st).post = st := rfl

@[simp] theorem StatePair.at_diag (st : State) (mode : StateMode) :
    (StatePair.diag st).at mode = st := by
  cases mode <;> rfl

def evalBoolBin : BoolBinOp → Bool → Bool → Bool
  | .and,     a, b => a && b
  | .or,      a, b => a || b
  | .implies, a, b => !a || b
  | .iff,     a, b => a == b

def evalArith : ArithOp → Option Value → Option Value → Option Value
  | .add, some (.vInt a), some (.vInt b) => some (.vInt (a + b))
  | .sub, some (.vInt a), some (.vInt b) => some (.vInt (a - b))
  | .mul, some (.vInt a), some (.vInt b) => some (.vInt (a * b))
  | .div, some (.vInt _), some (.vInt 0) => none
  -- Euclidean division (`Int.ediv`) — matches SMT-LIB `(div a b)` where
  -- `0 ≤ (mod a b) < |b|`. Differs from `Int./` (truncating) on negative
  -- operands; Scala-side `EvalIR.evalArith` mirrors this.
  | .div, some (.vInt a), some (.vInt b) => some (.vInt (a.ediv b))
  | _,    _,              _              => none

def evalCmp : CmpOp → Option Value → Option Value → Option Value
  | .eq,  some a,            some b            => some (.vBool (a == b))
  | .neq, some a,            some b            => some (.vBool (a != b))
  | .lt,  some (.vInt a),    some (.vInt b)    => some (.vBool (decide (a < b)))
  | .le,  some (.vInt a),    some (.vInt b)    => some (.vBool (decide (a ≤ b)))
  | .gt,  some (.vInt a),    some (.vInt b)    => some (.vBool (decide (a > b)))
  | .ge,  some (.vInt a),    some (.vInt b)    => some (.vBool (decide (a ≥ b)))
  | _,    _,                 _                 => none

def asBool : Value → Option Bool
  | .vBool b => some b
  | _        => none

def asInt : Value → Option Int
  | .vInt n => some n
  | _       => none

def containsValue : List Value → Value → Bool
  | [], _ => false
  | x :: xs, v => (x == v) || containsValue xs v

def dedupeValues : List Value → List Value
  | [] => []
  | x :: xs =>
      let rest := dedupeValues xs
      if containsValue rest x then rest else x :: rest

def setUnionValues (l r : List Value) : List Value :=
  dedupeValues (l ++ r)

def setIntersectValues (l r : List Value) : List Value :=
  dedupeValues (l.filter (fun v => containsValue r v))

def setDiffValues (l r : List Value) : List Value :=
  dedupeValues (l.filter (fun v => !containsValue r v))

def evalSetBin : SetOp → Option Value → Option Value → Option Value
  | .union,     some (.vSet l), some (.vSet r) => some (.vSet (setUnionValues l r))
  | .intersect, some (.vSet l), some (.vSet r) => some (.vSet (setIntersectValues l r))
  | .diff,      some (.vSet l), some (.vSet r) => some (.vSet (setDiffValues l r))
  | _,          _,              _              => none

mutual

  def eval (s : Schema) (st : State) (env : Env) : Expr → Option Value
    | .boolLit b => some (.vBool b)
    | .intLit n  => some (.vInt n)
    | .ident x =>
        match Env.lookup env x with
        | some v => some v
        | none   => st.lookupScalar x
    | .unNot e =>
        match eval s st env e with
        | some (.vBool b) => some (.vBool (!b))
        | _               => none
    | .unNeg e =>
        match eval s st env e with
        | some (.vInt n) => some (.vInt (-n))
        | _              => none
    | .boolBin op l r =>
        match eval s st env l, eval s st env r with
        | some (.vBool a), some (.vBool b) => some (.vBool (evalBoolBin op a b))
        | _, _                             => none
    | .arith op l r => evalArith op (eval s st env l) (eval s st env r)
    | .cmp op l r => evalCmp op (eval s st env l) (eval s st env r)
    | .letIn x value body =>
        match eval s st env value with
        | some v => eval s st ((x, v) :: env) body
        | none   => none
    | .enumAccess enumName memberName =>
        match s.lookupEnum enumName with
        | some d =>
            if d.members.contains memberName
              then some (.vEnum enumName memberName)
              else none
        | none => none
    | .member elem relName =>
        match eval s st env elem with
        | some v =>
            match st.relationDomain relName with
            | some dom => some (.vBool (dom.contains v))
            | none     => none
        | none => none
    | .forallEnum var enumName body =>
        match s.lookupEnum enumName with
        | some d => evalForallEnum s st env var enumName d.members body
        | none   => none
    | .forallRel var relName body =>
        match st.relationDomain relName with
        | some dom => evalForallRel s st env var dom body
        | none     => none
    | .prime e => eval s st env e
    | .pre   e => eval s st env e
    | .cardRel relName =>
        match st.relationDomain relName with
        | some dom => some (.vInt (Int.ofNat dom.length))
        | none     => none
    | .indexRel relName key =>
        match eval s st env key with
        | some kv => st.lookupKey relName kv
        | none    => none
    | .fieldAccess base fieldName =>
        match eval s st env base with
        | some v => Value.fieldLookup st v fieldName
        | none   => none
    | .setEmpty => some (.vSet [])
    | .setInsert elem set =>
        match eval s st env elem, eval s st env set with
        | some v, some (.vSet members) => some (.vSet (dedupeValues (v :: members)))
        | _,      _                    => none
    | .setMember elem set =>
        match eval s st env elem, eval s st env set with
        | some v, some (.vSet members) => some (.vBool (containsValue members v))
        | _,      _                    => none
    | .setBin op l r => evalSetBin op (eval s st env l) (eval s st env r)
    -- M_L.4.b-ext Phase 4b: With (record-update) Skolem encoding. Evaluates
    -- base; if the base reduces to a Value, wrap it in `vEntityWith`. Field
    -- access then unwinds via `Value.fieldLookup`.
    | .withRec base fld valueExpr =>
        match eval s st env base, eval s st env valueExpr with
        | some bv, some v => some (.vEntityWith bv fld v)
        | _, _ => none
  termination_by e => (sizeOf e, 0)

  def evalForallEnum (s : Schema) (st : State) (env : Env)
      (var : String) (enumName : String)
      (members : List String) (body : Expr) : Option Value :=
    match members with
    | [] => some (.vBool true)
    | m :: rest =>
        match eval s st ((var, .vEnum enumName m) :: env) body with
        | some (.vBool b) =>
            match evalForallEnum s st env var enumName rest body with
            | some (.vBool acc) => some (.vBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, members.length)

  def evalForallRel (s : Schema) (st : State) (env : Env)
      (var : String) (dom : List Value) (body : Expr) : Option Value :=
    match dom with
    | [] => some (.vBool true)
    | v :: rest =>
        match eval s st ((var, v) :: env) body with
        | some (.vBool b) =>
            match evalForallRel s st env var rest body with
            | some (.vBool acc) => some (.vBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, dom.length)

end

/-! ## Mode-aware evaluator (`evalAt`).

`evalAt` is identical to `eval` for every constructor except `.prime` / `.pre`
and the state-scalar / state-relation / entity-field lookups, which now read
through `sp.at mode`. `Prime e` flips the mode to `.post`; `Pre e` flips it
to `.pre`. Phase 1 leaves SMT-side mode threading and the universal `soundness`
theorem against `evalAt` for follow-up work; the diagonal-collapse theorem
proven below shows `evalAt mode s (StatePair.diag st) env e = eval s st env e`,
so every existing soundness theorem about `eval` lifts unchanged for the
`pre = post` case. -/

mutual

  def evalAt (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) :
      Expr → Option Value
    | .boolLit b => some (.vBool b)
    | .intLit n  => some (.vInt n)
    | .ident x =>
        match Env.lookup env x with
        | some v => some v
        | none   => (sp.at mode).lookupScalar x
    | .unNot e =>
        match evalAt mode s sp env e with
        | some (.vBool b) => some (.vBool (!b))
        | _               => none
    | .unNeg e =>
        match evalAt mode s sp env e with
        | some (.vInt n) => some (.vInt (-n))
        | _              => none
    | .boolBin op l r =>
        match evalAt mode s sp env l, evalAt mode s sp env r with
        | some (.vBool a), some (.vBool b) => some (.vBool (evalBoolBin op a b))
        | _, _                             => none
    | .arith op l r => evalArith op (evalAt mode s sp env l) (evalAt mode s sp env r)
    | .cmp op l r => evalCmp op (evalAt mode s sp env l) (evalAt mode s sp env r)
    | .letIn x value body =>
        match evalAt mode s sp env value with
        | some v => evalAt mode s sp ((x, v) :: env) body
        | none   => none
    | .enumAccess enumName memberName =>
        match s.lookupEnum enumName with
        | some d =>
            if d.members.contains memberName
              then some (.vEnum enumName memberName)
              else none
        | none => none
    | .member elem relName =>
        match evalAt mode s sp env elem with
        | some v =>
            match (sp.at mode).relationDomain relName with
            | some dom => some (.vBool (dom.contains v))
            | none     => none
        | none => none
    | .forallEnum var enumName body =>
        match s.lookupEnum enumName with
        | some d => evalAtForallEnum mode s sp env var enumName d.members body
        | none   => none
    | .forallRel var relName body =>
        match (sp.at mode).relationDomain relName with
        | some dom => evalAtForallRel mode s sp env var dom body
        | none     => none
    | .prime e => evalAt .post s sp env e
    | .pre   e => evalAt .pre  s sp env e
    | .cardRel relName =>
        match (sp.at mode).relationDomain relName with
        | some dom => some (.vInt (Int.ofNat dom.length))
        | none     => none
    | .indexRel relName key =>
        match evalAt mode s sp env key with
        | some kv => (sp.at mode).lookupKey relName kv
        | none    => none
    | .fieldAccess base fieldName =>
        match evalAt mode s sp env base with
        | some v => Value.fieldLookup (sp.at mode) v fieldName
        | none   => none
    | .setEmpty => some (.vSet [])
    | .setInsert elem set =>
        match evalAt mode s sp env elem, evalAt mode s sp env set with
        | some v, some (.vSet members) => some (.vSet (dedupeValues (v :: members)))
        | _,      _                    => none
    | .setMember elem set =>
        match evalAt mode s sp env elem, evalAt mode s sp env set with
        | some v, some (.vSet members) => some (.vBool (containsValue members v))
        | _,      _                    => none
    | .setBin op l r => evalSetBin op (evalAt mode s sp env l) (evalAt mode s sp env r)
    -- M_L.4.b-ext Phase 4b: With (record-update) Skolem encoding (mode-aware).
    | .withRec base fld valueExpr =>
        match evalAt mode s sp env base, evalAt mode s sp env valueExpr with
        | some bv, some v => some (.vEntityWith bv fld v)
        | _, _ => none
  termination_by e => (sizeOf e, 0)

  def evalAtForallEnum (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
      (var : String) (enumName : String)
      (members : List String) (body : Expr) : Option Value :=
    match members with
    | [] => some (.vBool true)
    | m :: rest =>
        match evalAt mode s sp ((var, .vEnum enumName m) :: env) body with
        | some (.vBool b) =>
            match evalAtForallEnum mode s sp env var enumName rest body with
            | some (.vBool acc) => some (.vBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, members.length)

  def evalAtForallRel (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env)
      (var : String) (dom : List Value) (body : Expr) : Option Value :=
    match dom with
    | [] => some (.vBool true)
    | v :: rest =>
        match evalAt mode s sp ((var, v) :: env) body with
        | some (.vBool b) =>
            match evalAtForallRel mode s sp env var rest body with
            | some (.vBool acc) => some (.vBool (b && acc))
            | _                 => none
        | _ => none
  termination_by (sizeOf body, dom.length)

end

/-! ## Diagonal-collapse theorem.

When `sp.pre = sp.post = st`, `evalAt mode s sp env e = eval s st env e` for
every mode and every Expr. Both Prime and Pre arms flip the mode but the
diagonal projection collapses, leaving the body identical to `eval`. The proof
goes by mutual structural induction on `Expr` × forall-domain shapes. -/

mutual

  theorem evalAt_diagonal_eq_eval :
      ∀ (mode : StateMode) (s : Schema) (st : State) (env : Env) (e : Expr),
        evalAt mode s (StatePair.diag st) env e = eval s st env e
    | mode, s, st, env, .boolLit b => by simp only [evalAt, eval]
    | mode, s, st, env, .intLit n  => by simp only [evalAt, eval]
    | mode, s, st, env, .ident x   => by
        simp only [evalAt, eval, StatePair.at_diag]
    | mode, s, st, env, .unNot e   => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env e]
    | mode, s, st, env, .unNeg e   => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env e]
    | mode, s, st, env, .boolBin op l r => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env l,
            evalAt_diagonal_eq_eval mode s st env r]
    | mode, s, st, env, .arith op l r => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env l,
            evalAt_diagonal_eq_eval mode s st env r]
    | mode, s, st, env, .cmp op l r => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env l,
            evalAt_diagonal_eq_eval mode s st env r]
    | mode, s, st, env, .letIn x value body => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env value]
        cases eval s st env value with
        | none => rfl
        | some v => exact evalAt_diagonal_eq_eval mode s st ((x, v) :: env) body
    | mode, s, st, env, .enumAccess en mem => by simp only [evalAt, eval]
    | mode, s, st, env, .member elem relName => by
        simp only [evalAt, eval, StatePair.at_diag]
        rw [evalAt_diagonal_eq_eval mode s st env elem]
    | mode, s, st, env, .forallEnum var en body => by
        simp only [evalAt, eval]
        cases s.lookupEnum en with
        | none => rfl
        | some d =>
            exact evalAtForallEnum_diagonal_eq mode s st env var en d.members body
    | mode, s, st, env, .forallRel var rel body => by
        simp only [evalAt, eval, StatePair.at_diag]
        cases st.relationDomain rel with
        | none => rfl
        | some dom =>
            exact evalAtForallRel_diagonal_eq mode s st env var dom body
    | mode, s, st, env, .prime e => by
        simp only [evalAt, eval]
        exact evalAt_diagonal_eq_eval .post s st env e
    | mode, s, st, env, .pre e => by
        simp only [evalAt, eval]
        exact evalAt_diagonal_eq_eval .pre s st env e
    | mode, s, st, env, .cardRel relName => by
        simp only [evalAt, eval, StatePair.at_diag]
    | mode, s, st, env, .indexRel relName key => by
        simp only [evalAt, eval, StatePair.at_diag]
        rw [evalAt_diagonal_eq_eval mode s st env key]
    | mode, s, st, env, .fieldAccess base fieldName => by
        simp only [evalAt, eval, StatePair.at_diag]
        rw [evalAt_diagonal_eq_eval mode s st env base]
    | mode, s, st, env, .setEmpty => by simp only [evalAt, eval]
    | mode, s, st, env, .setInsert elem set => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env elem,
            evalAt_diagonal_eq_eval mode s st env set]
    | mode, s, st, env, .setMember elem set => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env elem,
            evalAt_diagonal_eq_eval mode s st env set]
    | mode, s, st, env, .setBin op l r => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env l,
            evalAt_diagonal_eq_eval mode s st env r]
    | mode, s, st, env, .withRec base fld value => by
        simp only [evalAt, eval]
        rw [evalAt_diagonal_eq_eval mode s st env base,
            evalAt_diagonal_eq_eval mode s st env value]
  termination_by _ _ _ _ e => (sizeOf e, 0)

  theorem evalAtForallEnum_diagonal_eq :
      ∀ (mode : StateMode) (s : Schema) (st : State) (env : Env)
        (var : String) (en : String) (members : List String) (body : Expr),
        evalAtForallEnum mode s (StatePair.diag st) env var en members body
          = evalForallEnum s st env var en members body
    | _, _, _, _, _, _, [], _ => by
        simp only [evalAtForallEnum, evalForallEnum]
    | mode, s, st, env, var, en, m :: rest, body => by
        simp only [evalAtForallEnum, evalForallEnum]
        rw [evalAt_diagonal_eq_eval mode s st ((var, .vEnum en m) :: env) body,
            evalAtForallEnum_diagonal_eq mode s st env var en rest body]
  termination_by _ _ _ _ _ _ members body => (sizeOf body, members.length)

  theorem evalAtForallRel_diagonal_eq :
      ∀ (mode : StateMode) (s : Schema) (st : State) (env : Env)
        (var : String) (dom : List Value) (body : Expr),
        evalAtForallRel mode s (StatePair.diag st) env var dom body
          = evalForallRel s st env var dom body
    | _, _, _, _, _, [], _ => by
        simp only [evalAtForallRel, evalForallRel]
    | mode, s, st, env, var, v :: rest, body => by
        simp only [evalAtForallRel, evalForallRel]
        rw [evalAt_diagonal_eq_eval mode s st ((var, v) :: env) body,
            evalAtForallRel_diagonal_eq mode s st env var rest body]
  termination_by _ _ _ _ _ dom body => (sizeOf body, dom.length)

end

/-! ## `evalAt` per-arm characterizations on Prime/Pre.

These name the mode-flipping behavior introduced by `Prime`/`Pre` so callers
can rewrite without unfolding `evalAt`. They are the semantic statement
behind issue #194's two-state coupling. -/

theorem evalAt_prime (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) (e : Expr) :
    evalAt mode s sp env (.prime e) = evalAt .post s sp env e := by
  simp only [evalAt]

theorem evalAt_pre (mode : StateMode) (s : Schema) (sp : StatePair) (env : Env) (e : Expr) :
    evalAt mode s sp env (.pre e) = evalAt .pre s sp env e := by
  simp only [evalAt]

def evalInvariant (s : Schema) (st : State) (env : Env) (inv : InvariantDecl) : Option Bool :=
  (eval s st env inv.body).bind asBool

def evalRequiresAll (s : Schema) (st : State) (env : Env) : List Expr → Option Bool
  | []      => some true
  | r :: rs =>
      match (eval s st env r).bind asBool with
      | some true  => evalRequiresAll s st env rs
      | some false => some false
      | none       => none

def evalEnsuresAll (s : Schema) (st : State) (env : Env) : List Expr → Option Bool
  | []      => some true
  | r :: rs =>
      match (eval s st env r).bind asBool with
      | some true  => evalEnsuresAll s st env rs
      | some false => some false
      | none       => none

def operationEnabled (s : Schema) (st : State) (env : Env) (op : OperationDecl) : Option Bool :=
  evalRequiresAll s st env op.requires

def operationEnsures (s : Schema) (st : State) (env : Env) (op : OperationDecl) : Option Bool :=
  evalEnsuresAll s st env op.ensures

def invariantsHold (s : Schema) (st : State) (env : Env) (invs : List InvariantDecl) : Option Bool :=
  let rec go : List InvariantDecl → Option Bool
    | []      => some true
    | i :: is =>
        match evalInvariant s st env i with
        | some true  => go is
        | some false => some false
        | none       => none
  go invs

/-! ## Mode-aware invariant / requires / ensures evaluators (M_L.4.b-ext Phase 5.c).

The single-state `evalInvariant` / `evalRequiresAll` / `evalEnsuresAll` above are
the historical entry points used by Phase 5.a's invariant + requires certs. The
mode-aware variants below mirror them but route through `evalAt` against a
`StatePair`, so the post-state slot is reachable for operation invariant-
preservation certs. The diagonal-collapse property holds: feeding
`StatePair.diag st` produces results identical to the single-state forms. -/

def evalInvariantAt (mode : StateMode) (s : Schema) (sp : StatePair)
    (env : Env) (inv : InvariantDecl) : Option Bool :=
  (evalAt mode s sp env inv.body).bind asBool

def evalRequiresAllAt (mode : StateMode) (s : Schema) (sp : StatePair)
    (env : Env) : List Expr → Option Bool
  | []      => some true
  | r :: rs =>
      match (evalAt mode s sp env r).bind asBool with
      | some true  => evalRequiresAllAt mode s sp env rs
      | some false => some false
      | none       => none

def evalEnsuresAllAt (mode : StateMode) (s : Schema) (sp : StatePair)
    (env : Env) : List Expr → Option Bool
  | []      => some true
  | r :: rs =>
      match (evalAt mode s sp env r).bind asBool with
      | some true  => evalEnsuresAllAt mode s sp env rs
      | some false => some false
      | none       => none

/-- Operation invariant-preservation: weak preservation form. If the operation's
    `requires` are violated in the pre-state, the operation is disabled and
    preservation is vacuously true. Otherwise the invariant must hold in the
    post-state. The pre-state's invariant value is intentionally not checked
    here — that's the standalone invariant cert's job; mixing them produces a
    weaker, harder-to-debug conjunction. -/
def evalPreservation (s : Schema) (sp : StatePair) (env : Env)
    (reqs : List Expr) (inv : InvariantDecl) : Option Bool :=
  match evalRequiresAllAt .pre s sp env reqs with
  | some true  => evalInvariantAt .post s sp env inv
  | some false => some true
  | none       => none

end SpecRest
