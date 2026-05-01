import SpecRest.IR

namespace SpecRest

inductive Value where
  | vBool (b : Bool)
  | vInt (n : Int)
  | vEnum (enumName memberName : String)
  | vEntity (entityName id : String)
  deriving DecidableEq, Repr, Inhabited

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
  deriving Repr, Inhabited

def State.empty : State := { scalars := [], relations := [] }

def State.lookupScalar (st : State) (name : String) : Option Value :=
  List.lookup name st.scalars

def State.relationDomain (st : State) (name : String) : Option (List Value) :=
  List.lookup name st.relations

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
    | .prime e => eval s st env e
    | .pre   e => eval s st env e
    | .cardRel relName =>
        match st.relationDomain relName with
        | some dom => some (.vInt (Int.ofNat dom.length))
        | none     => none
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
