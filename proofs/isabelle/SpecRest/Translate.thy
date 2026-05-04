theory Translate
  imports IR Smt
begin

fun translate :: "expr \<Rightarrow> smt_term" where
  "translate (BoolLit b) = BLit b"
| "translate (IntLit n)  = ILit n"
| "translate (Ident x)   = TVar x"
| "translate (UnNot e)   = TNot (translate e)"
| "translate (UnNeg e)   = TNeg (translate e)"
| "translate (BoolBin AndOp     l r) = TAnd     (translate l) (translate r)"
| "translate (BoolBin OrOp      l r) = TOr      (translate l) (translate r)"
| "translate (BoolBin ImpliesOp l r) = TImplies (translate l) (translate r)"
| "translate (BoolBin IffOp     l r) =
     TAnd (TImplies (translate l) (translate r))
          (TImplies (translate r) (translate l))"
| "translate (Arith AddOp l r) = TAdd (translate l) (translate r)"
| "translate (Arith SubOp l r) = TSub (translate l) (translate r)"
| "translate (Arith MulOp l r) = TMul (translate l) (translate r)"
| "translate (Arith DivOp l r) = TDiv (translate l) (translate r)"
| "translate (Cmp EqOp  l r) = TEq (translate l) (translate r)"
| "translate (Cmp NeqOp l r) = TNot (TEq (translate l) (translate r))"
| "translate (Cmp LtOp  l r) = TLt (translate l) (translate r)"
| "translate (Cmp LeOp  l r) =
     TOr (TLt (translate l) (translate r))
         (TEq (translate l) (translate r))"
| "translate (Cmp GtOp  l r) = TLt (translate r) (translate l)"
| "translate (Cmp GeOp  l r) =
     TOr (TLt (translate r) (translate l))
         (TEq (translate l) (translate r))"
| "translate (LetIn x v body)            = TLetIn x (translate v) (translate body)"
| "translate (EnumAccess en mem)         = EnumElemConst en mem"
| "translate (Member elem rel_name)      = TInDom rel_name (translate elem)"
| "translate (ForallEnum var en body)    = TForallEnum var en (translate body)"
| "translate (ForallRel var rel_n body)  = TForallRel  var rel_n (translate body)"
| "translate (Prime e) = TPrime (translate e)"
| "translate (Pre e)   = TPre   (translate e)"
| "translate (CardRel rel_name)          = TCardRel rel_name"
| "translate (IndexRel rel_name key)     = TIndexRel rel_name (translate key)"
| "translate (FieldAccess base fname)    = TFieldAccess (translate base) fname"
| "translate SetEmpty                    = TSetEmpty"
| "translate (SetInsert elem set_e)      = TSetInsert (translate elem) (translate set_e)"
| "translate (SetMember elem set_e)      = TSetMember (translate elem) (translate set_e)"
| "translate (SetBin UnionOp     l r)    = TSetUnion     (translate l) (translate r)"
| "translate (SetBin IntersectOp l r)    = TSetIntersect (translate l) (translate r)"
| "translate (SetBin DiffOp      l r)    = TSetDiff      (translate l) (translate r)"
| "translate (WithRec base fld val_e)    = TWithRec (translate base) fld (translate val_e)"

end
