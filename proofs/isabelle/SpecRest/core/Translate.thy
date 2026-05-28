theory Translate
  imports IR Smt
begin

fun translate :: "expr \<Rightarrow> smt_term" where
  "translate (BoolLit b _) = BLit b"
| "translate (IntLit n _)  = ILit n"
| "translate (Ident x _)   = TVar x"
| "translate (UnNot e _)   = TNot (translate e)"
| "translate (UnNeg e _)   = TNeg (translate e)"
| "translate (BoolBin AndOp     l r _) = TAnd     (translate l) (translate r)"
| "translate (BoolBin OrOp      l r _) = TOr      (translate l) (translate r)"
| "translate (BoolBin ImpliesOp l r _) = TImplies (translate l) (translate r)"
| "translate (BoolBin IffOp     l r _) =
     TAnd (TImplies (translate l) (translate r))
          (TImplies (translate r) (translate l))"
| "translate (Arith AddOp l r _) = TAdd (translate l) (translate r)"
| "translate (Arith SubOp l r _) = TSub (translate l) (translate r)"
| "translate (Arith MulOp l r _) = TMul (translate l) (translate r)"
| "translate (Arith DivOp l r _) = TDiv (translate l) (translate r)"
| "translate (Cmp EqOp  l r _) = TEq (translate l) (translate r)"
| "translate (Cmp NeqOp l r _) = TNot (TEq (translate l) (translate r))"
| "translate (Cmp LtOp  l r _) = TLt (translate l) (translate r)"
| "translate (Cmp LeOp  l r _) =
     TOr (TLt (translate l) (translate r))
         (TEq (translate l) (translate r))"
| "translate (Cmp GtOp  l r _) = TLt (translate r) (translate l)"
| "translate (Cmp GeOp  l r _) =
     TOr (TLt (translate r) (translate l))
         (TEq (translate l) (translate r))"
| "translate (LetIn x v body _)            = TLetIn x (translate v) (translate body)"
| "translate (EnumAccess en mem _)         = EnumElemConst en mem"
| "translate (Member elem rel_name _)      = TInDom rel_name (translate elem)"
| "translate (ForallEnum var en body _)    = TForallEnum var en (translate body)"
| "translate (ForallRel var rel_n body _)  = TForallRel  var rel_n (translate body)"
| "translate (Prime e _) = TPrime (translate e)"
| "translate (Pre e _)   = TPre   (translate e)"
| "translate (CardRel rel_name _)          = TCardRel rel_name"
| "translate (IndexRel base key _)         = TIndexRel (translate base) (translate key)"
| "translate (FieldAccess base fname _)    = TFieldAccess (translate base) fname"
| "translate (SetEmpty _)                  = TSetEmpty"
| "translate (SetInsert elem set_e _)      = TSetInsert (translate elem) (translate set_e)"
| "translate (SetMember elem set_e _)      = TSetMember (translate elem) (translate set_e)"
| "translate (SetBin UnionOp     l r _)    = TSetUnion     (translate l) (translate r)"
| "translate (SetBin IntersectOp l r _)    = TSetIntersect (translate l) (translate r)"
| "translate (SetBin DiffOp      l r _)    = TSetDiff      (translate l) (translate r)"
| "translate (WithRec base fld val_e _)    = TWithRec (translate base) fld (translate val_e)"

end
