namespace SpecRest

inductive BoolBinOp where
  | and
  | or
  | implies
  | iff
  deriving DecidableEq, Repr, Inhabited

inductive IntCmpOp where
  | eq
  | neq
  | lt
  | le
  | gt
  | ge
  deriving DecidableEq, Repr, Inhabited

inductive Expr where
  | boolLit (b : Bool)
  | intLit (n : Int)
  | ident (name : String)
  | unNot (e : Expr)
  | unNeg (e : Expr)
  | boolBin (op : BoolBinOp) (l r : Expr)
  | intCmp (op : IntCmpOp) (l r : Expr)
  | letIn (var : String) (value body : Expr)
  | enumAccess (enumName memberName : String)
  deriving Repr, Inhabited

structure EnumDecl where
  name : String
  members : List String
  deriving Repr, Inhabited

structure InvariantDecl where
  name : String
  body : Expr
  deriving Repr, Inhabited

structure StateField where
  name : String
  isInt : Bool
  deriving Repr, Inhabited

structure StateDecl where
  fields : List StateField
  deriving Repr, Inhabited

structure OperationDecl where
  name : String
  requires : List Expr
  deriving Repr, Inhabited

structure ServiceIR where
  name : String
  enums : List EnumDecl
  state : StateDecl
  invariants : List InvariantDecl
  operations : List OperationDecl
  deriving Repr, Inhabited

end SpecRest
