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

inductive ArithOp where
  | add
  | sub
  | mul
  | div
  deriving DecidableEq, Repr, Inhabited

inductive SetOp where
  | union
  | intersect
  | diff
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
  | arith (op : ArithOp) (l r : Expr)
  | cmp (op : CmpOp) (l r : Expr)
  | letIn (var : String) (value body : Expr)
  | enumAccess (enumName memberName : String)
  | member (elem : Expr) (relName : String)
  | forallEnum (var : String) (enumName : String) (body : Expr)
  | forallRel (var : String) (relName : String) (body : Expr)
  | prime (e : Expr)
  | pre (e : Expr)
  | cardRel (relName : String)
  | indexRel (relName : String) (key : Expr)
  | fieldAccess (base : Expr) (fieldName : String)
  | setEmpty
  | setInsert (elem set : Expr)
  | setMember (elem set : Expr)
  | setBin (op : SetOp) (l r : Expr)
  /-- Record-update: single-field shape `base with { fld := value }`. Multi-
      field `with { f1 := v1, f2 := v2 }` lowers to chained applications
      `(base.withRec f1 v1).withRec f2 v2`. Phase 4 of M_L.4.b-ext (issue
      #194). Currently embedded with always-fail semantics: `eval` and
      `evalAt` return `none`, `translate` emits a bottom SmtTerm whose
      `smtEval` is `none`. `VerifiedSubset.classify` continues to reject
      `With` so the cert emitter never translates it — no false claims.
      Phase 4b will land proper Skolem semantics per
      `Translator.scala:1061-1098`. The single-field shape avoids nested
      induction (List × Expr would prevent the `induction` tactic from
      handling Expr). -/
  | withRec (base : Expr) (fld : String) (value : Expr)
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
