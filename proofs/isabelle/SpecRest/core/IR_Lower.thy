theory IR_Lower
  imports IR
begin


fun lower_forall_step ::
    "String.literal list \<Rightarrow> quantifier_binding_full
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower_forall_step enums (QuantifierBindingFull v (IdentifierF dnm _) _ _) body sp =
     (if string_in_list dnm enums
        then Some (ForallEnum v dnm body sp)
        else Some (ForallRel v dnm body sp))"
| "lower_forall_step _ _ _ _ = None"

fun lower_forall_bindings ::
    "String.literal list \<Rightarrow> quantifier_binding_full list
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower_forall_bindings _ [] _ _ = None"
| "lower_forall_bindings enums [b] body sp = lower_forall_step enums b body sp"
| "lower_forall_bindings enums (b # rest) body sp =
     (case lower_forall_bindings enums rest body sp of
        None \<Rightarrow> None
      | Some inner \<Rightarrow> lower_forall_step enums b inner sp)"

function (sequential) lower :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> expr option"
and lowerSetList ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> option_span \<Rightarrow> expr option"
and lower_with_assigns ::
    "String.literal list \<Rightarrow> field_assign_full list
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower _ (BoolLitF b sp)     = Some (BoolLit b sp)"
| "lower _ (IntLitF n sp)      = Some (IntLit n sp)"
| "lower _ (IdentifierF x sp)  = Some (Ident x sp)"
| "lower _ (FloatLitF _ _)     = None"
| "lower _ (StringLitF _ _)    = None"
| "lower _ (NoneLitF _)        = None"
| "lower _ (LambdaF _ _ _)     = None"
| "lower _ (CallF _ _ _)       = None"
| "lower _ (ConstructorF _ _ _)     = None"
| "lower _ (MapLiteralF _ _)        = None"
| "lower _ (SeqLiteralF _ _)        = None"
| "lower _ (SetComprehensionF _ _ _ _) = None"
| "lower _ (SomeWrapF _ _)     = None"
| "lower _ (TheF _ _ _ _)      = None"
| "lower _ (MatchesF _ _ _)    = None"
| "lower _ (IfF _ _ _ _)       = None"

| "lower enums (QuantifierF k bs body sp) =
     (case lower enums body of
        None \<Rightarrow> None
      | Some body' \<Rightarrow>
          (case k of
             QAll \<Rightarrow> lower_forall_bindings enums bs body' sp
           | QNo \<Rightarrow> lower_forall_bindings enums bs (UnNot body' sp) sp
           | QSome \<Rightarrow>
               map_option (\<lambda>e. UnNot e sp)
                 (lower_forall_bindings enums bs (UnNot body' sp) sp)
           | QExists \<Rightarrow>
               map_option (\<lambda>e. UnNot e sp)
                 (lower_forall_bindings enums bs (UnNot body' sp) sp)))"

| "lower enums (UnaryOpF op e sp) =
     (case op of
        UNot \<Rightarrow> map_option (\<lambda>e'. UnNot e' sp) (lower enums e)
      | UNegate \<Rightarrow> map_option (\<lambda>e'. UnNeg e' sp) (lower enums e)
      | UCardinality \<Rightarrow>
          (case e of
             IdentifierF x _ \<Rightarrow> Some (CardRel x sp)
           | _ \<Rightarrow> None)
      | UPower \<Rightarrow> None)"

| "lower enums (BinaryOpF op l r sp) =
     (case op of
        BAnd \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin AndOp l' r' sp)
           | _ \<Rightarrow> None)
      | BOr \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin OrOp l' r' sp)
           | _ \<Rightarrow> None)
      | BImplies \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin ImpliesOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIff \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin IffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BEq \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BNeq \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp NeqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLt \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGt \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLe \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGe \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BAdd \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith AddOp l' r' sp)
           | _ \<Rightarrow> None)
      | BSub \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith SubOp l' r' sp)
           | _ \<Rightarrow> None)
      | BMul \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith MulOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiv \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith DivOp l' r' sp)
           | _ \<Rightarrow> None)
      | BUnion \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin UnionOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIntersect \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin IntersectOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiff \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin DiffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. Member l' rel sp) (lower enums l)
           | _ \<Rightarrow>
               (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (SetMember l' r' sp)
                | _ \<Rightarrow> None))
      | BNotIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. UnNot (Member l' rel sp) sp) (lower enums l)
           | _ \<Rightarrow>
               (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (UnNot (SetMember l' r' sp) sp)
                | _ \<Rightarrow> None))
      | BSubset \<Rightarrow> None)"

| "lower enums (LetF x v body sp) =
     (case (lower enums v, lower enums body) of
        (Some v', Some b') \<Rightarrow> Some (LetIn x v' b' sp)
      | _ \<Rightarrow> None)"
| "lower _ (EnumAccessF base mem sp) =
     (case base of
        IdentifierF en _ \<Rightarrow> Some (EnumAccess en mem sp)
      | _ \<Rightarrow> None)"
| "lower enums (FieldAccessF base fname sp) =
     map_option (\<lambda>b'. FieldAccess b' fname sp) (lower enums base)"
| "lower enums (IndexF base key sp) =
     (case (lower enums base, lower enums key) of
        (Some base', Some key') \<Rightarrow>
          (case peel_relation_ref base' of
             Some _ \<Rightarrow> Some (IndexRel base' key' sp)
           | None   \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "lower enums (PrimeF e sp) = map_option (\<lambda>e'. Prime e' sp) (lower enums e)"
| "lower enums (PreF e sp)   = map_option (\<lambda>e'. Pre e' sp) (lower enums e)"
| "lower enums (WithF base updates sp) =
     (case lower enums base of
        None \<Rightarrow> None
      | Some base' \<Rightarrow> lower_with_assigns enums updates base' sp)"
| "lower enums (SetLiteralF elems sp) = lowerSetList enums elems sp"

| "lowerSetList _ [] sp = Some (SetEmpty sp)"
| "lowerSetList enums (e # rest) sp =
     (case (lower enums e, lowerSetList enums rest sp) of
        (Some e', Some s') \<Rightarrow> Some (SetInsert e' s' sp)
      | _ \<Rightarrow> None)"

| "lower_with_assigns _ [] base _ = Some base"
| "lower_with_assigns enums (FieldAssignFull fld v _ # rest) base sp =
     (case lower enums v of
        None \<Rightarrow> None
      | Some v' \<Rightarrow> lower_with_assigns enums rest (WithRec base fld v' sp) sp)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (_, e) \<Rightarrow> size e
             | Inr (Inl (_, elems, _)) \<Rightarrow> size_list size elems
             | Inr (Inr (_, updates, _, _)) \<Rightarrow> size_list size updates),
        (\<lambda>p. case p of
               Inl _ \<Rightarrow> 0
             | Inr (Inl (_, elems, _)) \<Rightarrow> Suc (length elems)
             | Inr (Inr (_, updates, _, _)) \<Rightarrow> Suc (length updates))
       ]")
     auto

end
