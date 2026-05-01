namespace SpecRest

inductive TypeExpr where
  | boolT
  | intT
  | enumT (name : String)
  | entityT (name : String)
  | relationT (key value : TypeExpr)
  deriving DecidableEq, Repr, Inhabited

inductive BoolBinOp where
  | and
  | or
  | implies
  | iff
  deriving DecidableEq, Repr, Inhabited

inductive CmpOp where
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
  | cmp (op : CmpOp) (l r : Expr)
  | letIn (var : String) (value body : Expr)
  | enumAccess (enumName memberName : String)
  | member (elem : Expr) (relName : String)
  | forallEnum (var : String) (enumName : String) (body : Expr)
  deriving Repr, Inhabited

structure FieldDecl where
  name : String
  ty : TypeExpr
  deriving Repr, Inhabited

structure EntityDecl where
  name : String
  fields : List FieldDecl
  deriving Repr, Inhabited

structure EnumDecl where
  name : String
  members : List String
  deriving Repr, Inhabited

structure StateScalar where
  name : String
  ty : TypeExpr
  deriving Repr, Inhabited

structure StateRelation where
  name : String
  key : TypeExpr
  value : TypeExpr
  deriving Repr, Inhabited

structure StateDecl where
  scalars : List StateScalar
  relations : List StateRelation
  deriving Repr, Inhabited

structure InvariantDecl where
  name : String
  body : Expr
  deriving Repr, Inhabited

structure OperationDecl where
  name : String
  requires : List Expr
  ensures : List Expr
  deriving Repr, Inhabited

structure ServiceIR where
  name : String
  enums : List EnumDecl
  entities : List EntityDecl
  state : StateDecl
  invariants : List InvariantDecl
  operations : List OperationDecl
  deriving Repr, Inhabited

end SpecRest
