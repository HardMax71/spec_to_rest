theory TranslateDirect
  imports IR Smt
begin

fun identName_smt :: "smt_term \<Rightarrow> String.literal option" where
  "identName_smt (TVar rel) = Some rel"
| "identName_smt _ = None"

fun peel_relation_ref_smt :: "smt_term \<Rightarrow> String.literal option" where
  "peel_relation_ref_smt (TVar rel) = Some rel"
| "peel_relation_ref_smt (TPre b)   = identName_smt b"
| "peel_relation_ref_smt (TPrime b) = identName_smt b"
| "peel_relation_ref_smt _          = None"

fun direct_forall_step ::
    "String.literal list \<Rightarrow> quantifier_binding_full \<Rightarrow> smt_term \<Rightarrow> smt_term option"
where
  "direct_forall_step enums (QuantifierBindingFull v (IdentifierF dnm _) _ _) body =
     (if string_in_list dnm enums
        then Some (TForallEnum v dnm body)
        else Some (TForallRel v dnm body))"
| "direct_forall_step _ _ _ = None"

fun direct_forall_bindings ::
    "String.literal list \<Rightarrow> quantifier_binding_full list \<Rightarrow> smt_term \<Rightarrow> smt_term option"
where
  "direct_forall_bindings _ [] _ = None"
| "direct_forall_bindings enums [b] body = direct_forall_step enums b body"
| "direct_forall_bindings enums (b # rest) body =
     (case direct_forall_bindings enums rest body of
        None \<Rightarrow> None
      | Some inner \<Rightarrow> direct_forall_step enums b inner)"

fun direct_set_comp_eq ::
    "String.literal list \<Rightarrow> String.literal \<Rightarrow> String.literal
       \<Rightarrow> smt_term \<Rightarrow> smt_term \<Rightarrow> smt_term"
where
  "direct_set_comp_eq enums var dnm setE predE =
     (let memX = TSetMember (TVar var) (TVar (STR ''0cmp''));
          memD = (if string_in_list dnm enums then BLit True else TInDom dnm (TVar var));
          dir1 = (if string_in_list dnm enums
                    then TForallEnum var dnm (TImplies predE memX)
                    else TForallRel var dnm (TImplies predE memX));
          dir2 = TForallSet var (TVar (STR ''0cmp'')) (TAnd memD predE)
      in TLetIn (STR ''0cmp'') setE (TAnd dir1 dir2))"

definition direct_dom_eq :: "String.literal \<Rightarrow> String.literal \<Rightarrow> smt_term" where
  "direct_dom_eq xrel yrel =
     TAnd (TForallRel (STR ''0cmp'') xrel (TInDom yrel (TVar (STR ''0cmp''))))
          (TForallRel (STR ''0cmp'') yrel (TInDom xrel (TVar (STR ''0cmp''))))"

definition direct_beq_dom_or_none :: "expr_full \<Rightarrow> expr_full \<Rightarrow> smt_term option" where
  "direct_beq_dom_or_none l r =
     (case (dom_arg l, dom_arg r) of
        (Some x, Some y) \<Rightarrow> Some (direct_dom_eq x y) | _ \<Rightarrow> None)"

function (sequential) translate_full_direct ::
    "String.literal list \<Rightarrow> expr_full \<Rightarrow> smt_term option"
and directSetList ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> smt_term option"
and direct_with_assigns ::
    "String.literal list \<Rightarrow> field_assign_full list \<Rightarrow> smt_term \<Rightarrow> smt_term option"
and directSeqList ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> smt_term option"
and directMapEntries ::
    "String.literal list \<Rightarrow> map_entry_full list \<Rightarrow> smt_term option"
where
  "translate_full_direct _ (BoolLitF b _)    = Some (BLit b)"
| "translate_full_direct _ (IntLitF n _)     = Some (ILit n)"
| "translate_full_direct _ (IdentifierF x _) = Some (TVar x)"
| "translate_full_direct _ (FloatLitF s _)   = map_option RLit (decimalToRat s)"
| "translate_full_direct _ (StringLitF v _)  = Some (TStrLit v)"
| "translate_full_direct _ (NoneLitF _)      = Some TNone"
| "translate_full_direct _ (LambdaF _ _ _)   = None"
| "translate_full_direct enums (CallF callee args _) =
     (case (callee, args) of
        (IdentifierF nm _, [arg]) \<Rightarrow>
          (if is_builtin_pred nm
             then map_option (\<lambda>a'. TUStrPred nm a') (translate_full_direct enums arg)
             else None)
      | _ \<Rightarrow> None)"
| "translate_full_direct enums (ConstructorF name fas _) =
     direct_with_assigns enums fas (TEntityBase name)"
| "translate_full_direct _ (SetComprehensionF _ _ _ _) = None"
| "translate_full_direct enums (TheF var dm body _) =
     (case dm of
        IdentifierF rel _ \<Rightarrow>
          (if string_in_list rel enums then None
           else map_option (\<lambda>b. TTheRel var rel b) (translate_full_direct enums body))
      | _ \<Rightarrow> None)"
| "translate_full_direct enums (MatchesF e pat _) =
     map_option (\<lambda>e'. TMatches e' pat) (translate_full_direct enums e)"
| "translate_full_direct enums (QuantifierF k bs body _) =
     (case translate_full_direct enums body of
        None \<Rightarrow> None
      | Some body' \<Rightarrow>
          (case k of
             QAll \<Rightarrow> direct_forall_bindings enums bs body'
           | QNo \<Rightarrow> direct_forall_bindings enums bs (TNot body')
           | QSome \<Rightarrow>
               map_option TNot (direct_forall_bindings enums bs (TNot body'))
           | QExists \<Rightarrow>
               map_option TNot (direct_forall_bindings enums bs (TNot body'))))"
| "translate_full_direct enums (UnaryOpF op e _) =
     (case op of
        UNot \<Rightarrow> map_option TNot (translate_full_direct enums e)
      | UNegate \<Rightarrow> map_option TNeg (translate_full_direct enums e)
      | UCardinality \<Rightarrow>
          (case e of
             IdentifierF x _ \<Rightarrow> Some (TCardRel x)
           | _ \<Rightarrow> None)
      | UPower \<Rightarrow> None)"
| "translate_full_direct enums (BinaryOpF op l r _) =
     (case op of
        BAnd \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAnd lt rt) | _ \<Rightarrow> None)
      | BOr \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr lt rt) | _ \<Rightarrow> None)
      | BImplies \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TImplies lt rt) | _ \<Rightarrow> None)
      | BIff \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAnd (TImplies lt rt) (TImplies rt lt)) | _ \<Rightarrow> None)
      | BEq \<Rightarrow>
          (case direct_beq_dom_or_none l r of
             Some t \<Rightarrow> Some t
           | None \<Rightarrow>
             (case r of
                SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
                  (case (translate_full_direct enums l, translate_full_direct enums p) of
                     (Some lt, Some pt) \<Rightarrow> Some (direct_set_comp_eq enums var dnm lt pt)
                   | _ \<Rightarrow> None)
              | _ \<Rightarrow>
                  (case (translate_full_direct enums l, translate_full_direct enums r) of
                     (Some lt, Some rt) \<Rightarrow> Some (TEq lt rt)
                   | _ \<Rightarrow> None)))
      | BNeq \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TNot (TEq lt rt)) | _ \<Rightarrow> None)
      | BLt \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TLt lt rt) | _ \<Rightarrow> None)
      | BGt \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TLt rt lt) | _ \<Rightarrow> None)
      | BLe \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr (TLt lt rt) (TEq lt rt)) | _ \<Rightarrow> None)
      | BGe \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr (TLt rt lt) (TEq lt rt)) | _ \<Rightarrow> None)
      | BAdd \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAdd lt rt) | _ \<Rightarrow> None)
      | BSub \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSub lt rt) | _ \<Rightarrow> None)
      | BMul \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TMul lt rt) | _ \<Rightarrow> None)
      | BDiv \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TDiv lt rt) | _ \<Rightarrow> None)
      | BUnion \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetUnion lt rt) | _ \<Rightarrow> None)
      | BIntersect \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetIntersect lt rt) | _ \<Rightarrow> None)
      | BDiff \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetDiff lt rt) | _ \<Rightarrow> None)
      | BIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>lt. TInDom rel lt) (translate_full_direct enums l)
           | SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
               (case (translate_full_direct enums l, translate_full_direct enums p) of
                  (Some lt, Some pt) \<Rightarrow>
                    Some (TLetIn var lt
                           (if string_in_list dnm enums
                              then pt
                              else TAnd (TInDom dnm (TVar var)) pt))
                | _ \<Rightarrow> None)
           | _ \<Rightarrow>
               (case (translate_full_direct enums l, translate_full_direct enums r) of
                  (Some lt, Some rt) \<Rightarrow> Some (TSetMember lt rt)
                | _ \<Rightarrow> None))
      | BNotIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>lt. TNot (TInDom rel lt)) (translate_full_direct enums l)
           | _ \<Rightarrow>
               (case (translate_full_direct enums l, translate_full_direct enums r) of
                  (Some lt, Some rt) \<Rightarrow> Some (TNot (TSetMember lt rt))
                | _ \<Rightarrow> None))
      | BSubset \<Rightarrow>
          (case (translate_full_direct enums l, translate_full_direct enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TEq (TSetDiff lt rt) TSetEmpty)
           | _ \<Rightarrow> None))"
| "translate_full_direct enums (LetF x v body _) =
     (case (translate_full_direct enums v, translate_full_direct enums body) of
        (Some vt, Some bt) \<Rightarrow> Some (TLetIn x vt bt)
      | _ \<Rightarrow> None)"
| "translate_full_direct _ (EnumAccessF base mem _) =
     (case base of
        IdentifierF en _ \<Rightarrow> Some (EnumElemConst en mem)
      | _ \<Rightarrow> None)"
| "translate_full_direct enums (FieldAccessF base fname _) =
     map_option (\<lambda>b'. TFieldAccess b' fname) (translate_full_direct enums base)"
| "translate_full_direct enums (IndexF base key _) =
     (case (translate_full_direct enums base, translate_full_direct enums key) of
        (Some base', Some key') \<Rightarrow>
          (case peel_relation_ref_smt base' of
             Some _ \<Rightarrow> Some (TIndexRel base' key')
           | None   \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "translate_full_direct enums (PrimeF e _) =
     map_option TPrime (translate_full_direct enums e)"
| "translate_full_direct enums (PreF e _) =
     map_option TPre (translate_full_direct enums e)"
| "translate_full_direct enums (WithF base updates _) =
     (case translate_full_direct enums base of
        None \<Rightarrow> None
      | Some base' \<Rightarrow> direct_with_assigns enums updates base')"
| "translate_full_direct enums (SetLiteralF elems _) = directSetList enums elems"
| "translate_full_direct enums (SeqLiteralF elems _) = directSeqList enums elems"
| "translate_full_direct enums (MapLiteralF entries _) = directMapEntries enums entries"
| "translate_full_direct enums (IfF c a b _) =
     (case (translate_full_direct enums c, translate_full_direct enums a,
            translate_full_direct enums b) of
        (Some ct, Some at_, Some bt) \<Rightarrow> Some (TIte ct at_ bt)
      | _ \<Rightarrow> None)"
| "translate_full_direct enums (SomeWrapF e _) =
     map_option TSome (translate_full_direct enums e)"

| "directSetList _ [] = Some TSetEmpty"
| "directSetList enums (e # rest) =
     (case (translate_full_direct enums e, directSetList enums rest) of
        (Some et, Some st) \<Rightarrow> Some (TSetInsert et st) | _ \<Rightarrow> None)"

| "direct_with_assigns _ [] base = Some base"
| "direct_with_assigns enums (FieldAssignFull fld v _ # rest) base =
     (case translate_full_direct enums v of
        None \<Rightarrow> None
      | Some vt \<Rightarrow> direct_with_assigns enums rest (TWithRec base fld vt))"

| "directSeqList _ [] = Some TSeqEmpty"
| "directSeqList enums (e # rest) =
     (case (translate_full_direct enums e, directSeqList enums rest) of
        (Some et, Some st) \<Rightarrow> Some (TSeqCons et st) | _ \<Rightarrow> None)"

| "directMapEntries _ [] = Some TMapEmpty"
| "directMapEntries enums (MapEntryFull k v _ # rest) =
     (case (translate_full_direct enums k, translate_full_direct enums v,
            directMapEntries enums rest) of
        (Some kt, Some vt, Some mt) \<Rightarrow> Some (TMapCons kt vt mt) | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (Inl (_, e)) \<Rightarrow> size e
             | Inl (Inr (_, elems)) \<Rightarrow> size_list size elems
             | Inr (Inl (_, updates, _)) \<Rightarrow> size_list size updates
             | Inr (Inr (Inl (_, elems))) \<Rightarrow> size_list size elems
             | Inr (Inr (Inr (_, entries))) \<Rightarrow> size_list size entries),
        (\<lambda>p. case p of
               Inl (Inl _) \<Rightarrow> 0
             | Inl (Inr (_, elems)) \<Rightarrow> Suc (length elems)
             | Inr (Inl (_, updates, _)) \<Rightarrow> Suc (length updates)
             | Inr (Inr (Inl (_, elems))) \<Rightarrow> Suc (length elems)
             | Inr (Inr (Inr (_, entries))) \<Rightarrow> Suc (length entries))
       ]")
     auto

lemma direct_BEq_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
      and "dom_arg l = None \<or> dom_arg r = None"
  shows "translate_full_direct enums (BinaryOpF BEq l r sp)
           = (case (translate_full_direct enums l, translate_full_direct enums r) of
                (Some lt, Some rt) \<Rightarrow> Some (TEq lt rt) | _ \<Rightarrow> None)"
proof -
  have dn: "direct_beq_dom_or_none l r = None"
    using assms(2) by (auto simp: direct_beq_dom_or_none_def split: option.splits)
  show ?thesis
  proof (cases r)
    case (SetComprehensionF v dm pr s)
    with assms dn show ?thesis by (cases dm) (auto split: option.splits)
  qed (use assms dn in \<open>auto split: option.splits\<close>)
qed

lemma direct_BIn_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "translate_full_direct enums (BinaryOpF BIn l r sp)
           = (case r of
                IdentifierF rel _ \<Rightarrow> map_option (\<lambda>lt. TInDom rel lt) (translate_full_direct enums l)
              | _ \<Rightarrow> (case (translate_full_direct enums l, translate_full_direct enums r) of
                        (Some lt, Some rt) \<Rightarrow> Some (TSetMember lt rt) | _ \<Rightarrow> None))"
proof (cases r)
  case (SetComprehensionF v dm pr s)
  with assms show ?thesis by (cases dm) auto
qed auto

end
