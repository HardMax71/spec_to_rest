theory Translate
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

fun translate_forall_step ::
    "String.literal list \<Rightarrow> quantifier_binding \<Rightarrow> smt_term \<Rightarrow> smt_term option"
where
  "translate_forall_step enums (QuantifierBindingFull v d _ _) body =
     (case d of
        IdentifierF dnm _ \<Rightarrow>
          (if string_in_list dnm enums
             then Some (TForallEnum v dnm body)
             else Some (TForallRel v dnm body))
      | _ \<Rightarrow> None)"

fun translate_forall_bindings ::
    "String.literal list \<Rightarrow> quantifier_binding list \<Rightarrow> smt_term \<Rightarrow> smt_term option"
where
  "translate_forall_bindings _ [] _ = None"
| "translate_forall_bindings enums [b] body = translate_forall_step enums b body"
| "translate_forall_bindings enums (b # rest) body =
     (case translate_forall_bindings enums rest body of
        None \<Rightarrow> None
      | Some inner \<Rightarrow> translate_forall_step enums b inner)"

fun translate_set_comp_eq ::
    "String.literal list \<Rightarrow> String.literal \<Rightarrow> String.literal
       \<Rightarrow> smt_term \<Rightarrow> smt_term \<Rightarrow> smt_term"
where
  "translate_set_comp_eq enums var dnm setE predE =
     (let f = fresh_var (STR ''s'') (var # smt_var_list predE);
          memX = TSetMember (TVar var) (TVar f);
          memD = (if string_in_list dnm enums then BLit True else TInDom dnm (TVar var));
          dir1 = (if string_in_list dnm enums
                    then TForallEnum var dnm (TImplies predE memX)
                    else TForallRel var dnm (TImplies predE memX));
          dir2 = TForallSet var (TVar f) (TAnd memD predE)
      in TLetIn f setE (TAnd dir1 dir2))"

definition translate_dom_eq :: "String.literal \<Rightarrow> String.literal \<Rightarrow> smt_term" where
  "translate_dom_eq xrel yrel =
     (let k = fresh_var (STR ''x'') [xrel, yrel]
      in TAnd (TForallRel k xrel (TInDom yrel (TVar k)))
              (TForallRel k yrel (TInDom xrel (TVar k))))"

definition translate_beq_dom_or_none :: "expr \<Rightarrow> expr \<Rightarrow> smt_term option" where
  "translate_beq_dom_or_none l r =
     (case (dom_arg l, dom_arg r) of
        (Some x, Some y) \<Rightarrow> Some (translate_dom_eq x y) | _ \<Rightarrow> None)"

function (sequential) translate ::
    "String.literal list \<Rightarrow> expr \<Rightarrow> smt_term option"
and translateSetList ::
    "String.literal list \<Rightarrow> expr list \<Rightarrow> smt_term option"
and translate_with_assigns ::
    "String.literal list \<Rightarrow> field_assign list \<Rightarrow> smt_term \<Rightarrow> smt_term option"
and translateSeqList ::
    "String.literal list \<Rightarrow> expr list \<Rightarrow> smt_term option"
and translateMapEntries ::
    "String.literal list \<Rightarrow> map_entry list \<Rightarrow> smt_term option"
where
  "translate _ (BoolLitF b _)    = Some (BLit b)"
| "translate _ (IntLitF n _)     = Some (ILit n)"
| "translate _ (IdentifierF x _) = Some (TVar x)"
| "translate _ (FloatLitF s _)   = map_option RLit (decimalToRat s)"
| "translate _ (StringLitF v _)  = Some (TStrLit v)"
| "translate _ (NoneLitF _)      = Some TNone"
| "translate _ (LambdaF _ _ _)   = None"
| "translate enums (CallF callee args _) =
     (case (callee, args) of
        (IdentifierF nm _, [arg]) \<Rightarrow>
          (if is_builtin_pred nm
             then map_option (\<lambda>a'. TUStrPred nm a') (translate enums arg)
             else None)
      | _ \<Rightarrow> None)"
| "translate enums (ConstructorF name fas _) =
     translate_with_assigns enums fas (TEntityBase name)"
| "translate _ (SetComprehensionF _ _ _ _) = None"
| "translate enums (TheF var dm body _) =
     (case dm of
        IdentifierF rel _ \<Rightarrow>
          (if string_in_list rel enums then None
           else map_option (\<lambda>b. TTheRel var rel b) (translate enums body))
      | _ \<Rightarrow> None)"
| "translate enums (MatchesF e pat _) =
     map_option (\<lambda>e'. TMatches e' pat) (translate enums e)"
| "translate enums (QuantifierF k bs body _) =
     (case translate enums body of
        None \<Rightarrow> None
      | Some body' \<Rightarrow>
          (case k of
             QAll \<Rightarrow> translate_forall_bindings enums bs body'
           | QNo \<Rightarrow> translate_forall_bindings enums bs (TNot body')
           | QSome \<Rightarrow>
               map_option TNot (translate_forall_bindings enums bs (TNot body'))
           | QExists \<Rightarrow>
               map_option TNot (translate_forall_bindings enums bs (TNot body'))))"
| "translate enums (UnaryOpF op e _) =
     (case op of
        UNot \<Rightarrow> map_option TNot (translate enums e)
      | UNegate \<Rightarrow> map_option TNeg (translate enums e)
      | UCardinality \<Rightarrow>
          (case e of
             IdentifierF x _ \<Rightarrow> Some (TCardRel x)
           | _ \<Rightarrow> None)
      | UPower \<Rightarrow> None)"
| "translate enums (BinaryOpF op l r _) =
     (case op of
        BAnd \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAnd lt rt) | _ \<Rightarrow> None)
      | BOr \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr lt rt) | _ \<Rightarrow> None)
      | BImplies \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TImplies lt rt) | _ \<Rightarrow> None)
      | BIff \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAnd (TImplies lt rt) (TImplies rt lt)) | _ \<Rightarrow> None)
      | BEq \<Rightarrow>
          (case translate_beq_dom_or_none l r of
             Some t \<Rightarrow> Some t
           | None \<Rightarrow>
             (case r of
                SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
                  (case (translate enums l, translate enums p) of
                     (Some lt, Some pt) \<Rightarrow> Some (translate_set_comp_eq enums var dnm lt pt)
                   | _ \<Rightarrow> None)
              | _ \<Rightarrow>
                  (case (translate enums l, translate enums r) of
                     (Some lt, Some rt) \<Rightarrow> Some (TEq lt rt)
                   | _ \<Rightarrow> None)))
      | BNeq \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TNot (TEq lt rt)) | _ \<Rightarrow> None)
      | BLt \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TLt lt rt) | _ \<Rightarrow> None)
      | BGt \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TLt rt lt) | _ \<Rightarrow> None)
      | BLe \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr (TLt lt rt) (TEq lt rt)) | _ \<Rightarrow> None)
      | BGe \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TOr (TLt rt lt) (TEq lt rt)) | _ \<Rightarrow> None)
      | BAdd \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TAdd lt rt) | _ \<Rightarrow> None)
      | BSub \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSub lt rt) | _ \<Rightarrow> None)
      | BMul \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TMul lt rt) | _ \<Rightarrow> None)
      | BDiv \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TDiv lt rt) | _ \<Rightarrow> None)
      | BUnion \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetUnion lt rt) | _ \<Rightarrow> None)
      | BIntersect \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetIntersect lt rt) | _ \<Rightarrow> None)
      | BDiff \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TSetDiff lt rt) | _ \<Rightarrow> None)
      | BIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>lt. TInDom rel lt) (translate enums l)
           | SetComprehensionF var (IdentifierF dnm _) p _ \<Rightarrow>
               (case (translate enums l, translate enums p) of
                  (Some lt, Some pt) \<Rightarrow>
                    Some (TLetIn var lt
                           (if string_in_list dnm enums
                              then pt
                              else TAnd (TInDom dnm (TVar var)) pt))
                | _ \<Rightarrow> None)
           | _ \<Rightarrow>
               (case (translate enums l, translate enums r) of
                  (Some lt, Some rt) \<Rightarrow> Some (TSetMember lt rt)
                | _ \<Rightarrow> None))
      | BNotIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>lt. TNot (TInDom rel lt)) (translate enums l)
           | _ \<Rightarrow>
               (case (translate enums l, translate enums r) of
                  (Some lt, Some rt) \<Rightarrow> Some (TNot (TSetMember lt rt))
                | _ \<Rightarrow> None))
      | BSubset \<Rightarrow>
          (case (translate enums l, translate enums r) of
             (Some lt, Some rt) \<Rightarrow> Some (TEq (TSetDiff lt rt) TSetEmpty)
           | _ \<Rightarrow> None))"
| "translate enums (LetF x v body _) =
     (case (translate enums v, translate enums body) of
        (Some vt, Some bt) \<Rightarrow> Some (TLetIn x vt bt)
      | _ \<Rightarrow> None)"
| "translate _ (EnumAccessF base mem _) =
     (case base of
        IdentifierF en _ \<Rightarrow> Some (EnumElemConst en mem)
      | _ \<Rightarrow> None)"
| "translate enums (FieldAccessF base fname _) =
     map_option (\<lambda>b'. TFieldAccess b' fname) (translate enums base)"
| "translate enums (IndexF base key _) =
     (case (translate enums base, translate enums key) of
        (Some base', Some key') \<Rightarrow>
          (case peel_relation_ref_smt base' of
             Some _ \<Rightarrow> Some (TIndexRel base' key')
           | None   \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "translate enums (PrimeF e _) =
     map_option TPrime (translate enums e)"
| "translate enums (PreF e _) =
     map_option TPre (translate enums e)"
| "translate enums (WithF base updates _) =
     (case translate enums base of
        None \<Rightarrow> None
      | Some base' \<Rightarrow> translate_with_assigns enums updates base')"
| "translate enums (SetLiteralF elems _) = translateSetList enums elems"
| "translate enums (SeqLiteralF elems _) = translateSeqList enums elems"
| "translate enums (MapLiteralF entries _) = translateMapEntries enums entries"
| "translate enums (IfF c a b _) =
     (case (translate enums c, translate enums a,
            translate enums b) of
        (Some ct, Some at_, Some bt) \<Rightarrow> Some (TIte ct at_ bt)
      | _ \<Rightarrow> None)"
| "translate enums (SomeWrapF e _) =
     map_option TSome (translate enums e)"

| "translateSetList _ [] = Some TSetEmpty"
| "translateSetList enums (e # rest) =
     (case (translate enums e, translateSetList enums rest) of
        (Some et, Some st) \<Rightarrow> Some (TSetInsert et st) | _ \<Rightarrow> None)"

| "translate_with_assigns _ [] base = Some base"
| "translate_with_assigns enums (FieldAssignFull fld v _ # rest) base =
     (case translate enums v of
        None \<Rightarrow> None
      | Some vt \<Rightarrow> translate_with_assigns enums rest (TWithRec base fld vt))"

| "translateSeqList _ [] = Some TSeqEmpty"
| "translateSeqList enums (e # rest) =
     (case (translate enums e, translateSeqList enums rest) of
        (Some et, Some st) \<Rightarrow> Some (TSeqCons et st) | _ \<Rightarrow> None)"

| "translateMapEntries _ [] = Some TMapEmpty"
| "translateMapEntries enums (MapEntryFull k v _ # rest) =
     (case (translate enums k, translate enums v,
            translateMapEntries enums rest) of
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

lemma translate_BEq_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
      and "dom_arg l = None \<or> dom_arg r = None"
  shows "translate enums (BinaryOpF BEq l r sp)
           = (case (translate enums l, translate enums r) of
                (Some lt, Some rt) \<Rightarrow> Some (TEq lt rt) | _ \<Rightarrow> None)"
proof -
  have dn: "translate_beq_dom_or_none l r = None"
    using assms(2) by (auto simp: translate_beq_dom_or_none_def split: option.splits)
  show ?thesis
  proof (cases r)
    case (SetComprehensionF v dm pr s)
    with assms dn show ?thesis by (cases dm) (auto split: option.splits)
  qed (use assms dn in \<open>auto split: option.splits\<close>)
qed

lemma translate_BIn_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "translate enums (BinaryOpF BIn l r sp)
           = (case r of
                IdentifierF rel _ \<Rightarrow> map_option (\<lambda>lt. TInDom rel lt) (translate enums l)
              | _ \<Rightarrow> (case (translate enums l, translate enums r) of
                        (Some lt, Some rt) \<Rightarrow> Some (TSetMember lt rt) | _ \<Rightarrow> None))"
proof (cases r)
  case (SetComprehensionF v dm pr s)
  with assms show ?thesis by (cases dm) auto
qed auto

end
