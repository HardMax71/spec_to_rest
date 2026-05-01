import SpecRest.IR

namespace SpecRest

inductive Value where
  | vBool (b : Bool)
  | vInt (n : Int)
  | vEnum (enumName memberName : String)
  deriving DecidableEq, Repr, Inhabited

abbrev Env := List (String × Value)

structure Schema where
  enums : List EnumDecl
  deriving Repr, Inhabited

def Schema.empty : Schema := { enums := [] }

def Schema.lookupEnum (s : Schema) (name : String) : Option EnumDecl :=
  s.enums.find? (·.name == name)

def evalBoolBin : BoolBinOp → Bool → Bool → Bool
  | .and,     a, b => a && b
  | .or,      a, b => a || b
  | .implies, a, b => !a || b
  | .iff,     a, b => a == b

def evalIntCmp : IntCmpOp → Int → Int → Bool
  | .eq,  a, b => a == b
  | .neq, a, b => a != b
  | .lt,  a, b => decide (a < b)
  | .le,  a, b => decide (a ≤ b)
  | .gt,  a, b => decide (a > b)
  | .ge,  a, b => decide (a ≥ b)

def asBool : Value → Option Bool
  | .vBool b => some b
  | _        => none

def asInt : Value → Option Int
  | .vInt n => some n
  | _       => none

def Env.lookup (env : Env) (name : String) : Option Value :=
  List.lookup name env

def eval (s : Schema) (env : Env) : Expr → Option Value
  | .boolLit b => some (.vBool b)
  | .intLit n  => some (.vInt n)
  | .ident x   => Env.lookup env x
  | .unNot e =>
      match eval s env e with
      | some (.vBool b) => some (.vBool (!b))
      | _               => none
  | .unNeg e =>
      match eval s env e with
      | some (.vInt n) => some (.vInt (-n))
      | _              => none
  | .boolBin op l r =>
      match eval s env l, eval s env r with
      | some (.vBool a), some (.vBool b) => some (.vBool (evalBoolBin op a b))
      | _, _                             => none
  | .intCmp op l r =>
      match eval s env l, eval s env r with
      | some (.vInt a), some (.vInt b) => some (.vBool (evalIntCmp op a b))
      | _, _                           => none
  | .letIn x value body =>
      match eval s env value with
      | some v => eval s ((x, v) :: env) body
      | none   => none
  | .enumAccess enumName memberName =>
      match s.lookupEnum enumName with
      | some d => if d.members.contains memberName
                    then some (.vEnum enumName memberName)
                    else none
      | none   => none

def evalInvariant (s : Schema) (env : Env) (inv : InvariantDecl) : Option Bool :=
  (eval s env inv.body).bind asBool

def evalRequiresAll (s : Schema) (env : Env) : List Expr → Option Bool
  | []      => some true
  | r :: rs =>
      match (eval s env r).bind asBool with
      | some true  => evalRequiresAll s env rs
      | some false => some false
      | none       => none

def operationEnabled (s : Schema) (env : Env) (op : OperationDecl) : Option Bool :=
  evalRequiresAll s env op.requires

end SpecRest
