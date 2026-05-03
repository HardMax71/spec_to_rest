import SpecRest.IR

namespace SpecRest

inductive Value where
  | vBool (b : Bool)
  | vInt (n : Int)
  | vEnum (enumName memberName : String)
  | vEntity (entityName id : String)
  | vSet (members : List Value)
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
    | .vBool _, .vInt _ => isFalse (by intro h; cases h)
    | .vBool _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vBool _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vBool _, .vSet _ => isFalse (by intro h; cases h)
    | .vInt _, .vBool _ => isFalse (by intro h; cases h)
    | .vInt _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vInt _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vInt _, .vSet _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vBool _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vInt _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vEntity _ _ => isFalse (by intro h; cases h)
    | .vEnum _ _, .vSet _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vBool _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vInt _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vEntity _ _, .vSet _ => isFalse (by intro h; cases h)
    | .vSet _, .vBool _ => isFalse (by intro h; cases h)
    | .vSet _, .vInt _ => isFalse (by intro h; cases h)
    | .vSet _, .vEnum _ _ => isFalse (by intro h; cases h)
    | .vSet _, .vEntity _ _ => isFalse (by intro h; cases h)

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

def Env.lookup (env : Env) (name : String) : Option Value :=
  List.lookup name env

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
        | some (.vEntity _ id) => st.lookupField id fieldName
        | _                    => none
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

end SpecRest
