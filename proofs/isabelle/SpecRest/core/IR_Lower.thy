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

text \<open>\<open>X = {var in dnm | predE}\<close> lowers to the FULL set extensionality — both
  subset directions \<open>(\<forall>var\<in>dnm. predE \<longrightarrow> var\<in>X) \<and> (\<forall>var\<in>X. var\<in>dnm \<and> predE)\<close>.
  (The bounded \<open>\<forall>var\<in>dnm. var\<in>X \<longleftrightarrow> predE\<close> alone is too weak: it omits \<open>X \<subseteq> dnm\<close>;
  the second conjunct supplies it.) The second direction quantifies over the set
  \<^emph>\<open>value\<close> \<open>X\<close> with \<open>ForallSet\<close>. \<open>X\<close> is let-bound to \<open>0cmp\<close> first so it is evaluated
  in the outer scope (capture-free); the binder \<open>0cmp\<close> starts with a digit, which
  the lexer (\<open>[A-Za-z_][A-Za-z0-9_]*\<close>) forbids for source identifiers, so it never
  collides, and lexical scoping keeps it correct under nesting. An enum domain
  makes its membership trivially true.\<close>
fun lower_set_comp_eq ::
    "String.literal list \<Rightarrow> String.literal \<Rightarrow> String.literal
       \<Rightarrow> expr \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr"
where
  "lower_set_comp_eq enums var dnm setE predE sp =
     (let memX = SetMember (Ident var None) (Ident (STR ''0cmp'') None) sp;
          memD = (if string_in_list dnm enums then BoolLit True sp
                  else Member (Ident var None) dnm sp);
          dir1 = (if string_in_list dnm enums
                    then ForallEnum var dnm (BoolBin ImpliesOp predE memX sp) sp
                    else ForallRel var dnm (BoolBin ImpliesOp predE memX sp) sp);
          dir2 = ForallSet var (Ident (STR ''0cmp'') None) (BoolBin AndOp memD predE sp) sp
      in LetIn (STR ''0cmp'') setE (BoolBin AndOp dir1 dir2 sp) sp)"

function (sequential) lower :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> expr option"
and lowerSetList ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> option_span \<Rightarrow> expr option"
and lower_with_assigns ::
    "String.literal list \<Rightarrow> field_assign_full list
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
and lowerSeqList ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> option_span \<Rightarrow> expr option"
and lowerMapEntries ::
    "String.literal list \<Rightarrow> map_entry_full list \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower _ (BoolLitF b sp)     = Some (BoolLit b sp)"
| "lower _ (IntLitF n sp)      = Some (IntLit n sp)"
| "lower _ (IdentifierF x sp)  = Some (Ident x sp)"
| "lower _ (FloatLitF s sp)    = map_option (\<lambda>r. RealLit r sp) (decimalToRat s)"
| "lower _ (StringLitF v sp)   = Some (StrLit v sp)"
| "lower _ (NoneLitF sp)       = Some (NoneE sp)"
| "lower _ (LambdaF _ _ _)     = None"
| "lower _ (CallF _ _ _)       = None"
| "lower _ (ConstructorF _ _ _)     = None"
| "lower _ (SetComprehensionF _ _ _ _) = None"
| "lower _ (TheF _ _ _ _)      = None"
| "lower enums (MatchesF e pat sp) = map_option (\<lambda>e'. Matches e' pat sp) (lower enums e)"

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
          (case r of
             SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
               (case (lower enums l, lower enums p) of
                  (Some l', Some p') \<Rightarrow> Some (lower_set_comp_eq enums var dnm l' p' sp)
                | _ \<Rightarrow> None)
           | _ \<Rightarrow>
               (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp)
                | _ \<Rightarrow> None))
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
           | SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
               (case (lower enums l, lower enums p) of
                  (Some l', Some p') \<Rightarrow>
                    Some (LetIn var l'
                           (if string_in_list dnm enums
                              then p'
                              else BoolBin AndOp (Member (Ident var None) dnm sp) p' sp)
                           sp)
                | _ \<Rightarrow> None)
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
      | BSubset \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow>
               Some (Cmp EqOp (SetBin DiffOp l' r' sp) (SetEmpty sp) sp)
           | _ \<Rightarrow> None))"

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
| "lower enums (SeqLiteralF elems sp) = lowerSeqList enums elems sp"
| "lower enums (MapLiteralF entries sp) = lowerMapEntries enums entries sp"
| "lower enums (IfF c a b sp) =
     (case (lower enums c, lower enums a, lower enums b) of
        (Some c', Some a', Some b') \<Rightarrow> Some (Ite c' a' b' sp)
      | _ \<Rightarrow> None)"
| "lower enums (SomeWrapF e sp) = map_option (\<lambda>e'. SomeE e' sp) (lower enums e)"

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

| "lowerSeqList _ [] sp = Some (SeqEmpty sp)"
| "lowerSeqList enums (e # rest) sp =
     (case (lower enums e, lowerSeqList enums rest sp) of
        (Some e', Some s') \<Rightarrow> Some (SeqCons e' s' sp)
      | _ \<Rightarrow> None)"

| "lowerMapEntries _ [] sp = Some (MapEmpty sp)"
| "lowerMapEntries enums (MapEntryFull k v _ # rest) sp =
     (case (lower enums k, lower enums v, lowerMapEntries enums rest sp) of
        (Some k', Some v', Some m') \<Rightarrow> Some (MapCons k' v' m' sp)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (Inl (_, e)) \<Rightarrow> size e
             | Inl (Inr (_, elems, _)) \<Rightarrow> size_list size elems
             | Inr (Inl (_, updates, _, _)) \<Rightarrow> size_list size updates
             | Inr (Inr (Inl (_, elems, _))) \<Rightarrow> size_list size elems
             | Inr (Inr (Inr (_, entries, _))) \<Rightarrow> size_list size entries),
        (\<lambda>p. case p of
               Inl (Inl _) \<Rightarrow> 0
             | Inl (Inr (_, elems, _)) \<Rightarrow> Suc (length elems)
             | Inr (Inl (_, updates, _, _)) \<Rightarrow> Suc (length updates)
             | Inr (Inr (Inl (_, elems, _))) \<Rightarrow> Suc (length elems)
             | Inr (Inr (Inr (_, entries, _))) \<Rightarrow> Suc (length entries))
       ]")
     auto

lemma lower_BEq_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "lower enums (BinaryOpF BEq l r sp)
           = (case (lower enums l, lower enums r) of
                (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp)
              | _ \<Rightarrow> None)"
proof (cases r)
  case (SetComprehensionF v dom pr s)
  with assms show ?thesis by (cases dom) auto
qed auto

lemma lower_BIn_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "lower enums (BinaryOpF BIn l r sp)
           = (case r of
                IdentifierF rel _ \<Rightarrow> map_option (\<lambda>l'. Member l' rel sp) (lower enums l)
              | _ \<Rightarrow> (case (lower enums l, lower enums r) of
                        (Some l', Some r') \<Rightarrow> Some (SetMember l' r' sp)
                      | _ \<Rightarrow> None))"
proof (cases r)
  case (SetComprehensionF v dom pr s)
  with assms show ?thesis by (cases dom) auto
qed auto

end
