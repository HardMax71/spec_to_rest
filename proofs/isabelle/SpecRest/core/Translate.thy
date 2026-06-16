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

definition map2_opt ::
  "(smt_term \<Rightarrow> smt_term \<Rightarrow> smt_term)
     \<Rightarrow> smt_term option \<Rightarrow> smt_term option \<Rightarrow> smt_term option"
where
  "map2_opt f a b = (case (a, b) of (Some x, Some y) \<Rightarrow> Some (f x y) | _ \<Rightarrow> None)"

lemma map2_opt_simps [simp]:
  "map2_opt f (Some x) (Some y) = Some (f x y)"
  "map2_opt f None b = None"
  "map2_opt f a None = None"
  by (auto simp: map2_opt_def split: option.splits)

lemma map2_opt_eq_Some [simp]:
  "(map2_opt f a b = Some t) = (\<exists>x y. a = Some x \<and> b = Some y \<and> t = f x y)"
  by (auto simp: map2_opt_def split: option.splits)

fun comp_parts ::
  "expr \<Rightarrow> (String.literal \<times> String.literal \<times> expr) option"
where
  "comp_parts (SetComprehensionF var d p _) =
     (case d of IdentifierF dnm _ \<Rightarrow> Some (var, dnm, p) | _ \<Rightarrow> None)"
| "comp_parts _ = None"

lemma comp_parts_size:
  "comp_parts r = Some (var, dnm, p) \<Longrightarrow> size p < size r"
  by (cases r rule: comp_parts.cases) (auto split: expr.splits)

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

fun range_arg :: "expr \<Rightarrow> String.literal option" where
  "range_arg (CallF (IdentifierF nm _) [IdentifierF rel _] _) =
     (if nm = STR ''range'' \<or> nm = STR ''ran'' then Some rel else None)"
| "range_arg _ = None"

definition translate_range_eq :: "smt_term \<Rightarrow> String.literal \<Rightarrow> smt_term" where
  "translate_range_eq setE rel =
     (let k    = fresh_var (STR ''k'') (smt_var_list setE);
          vv   = fresh_var (STR ''v'') (k # smt_var_list setE);
          valK = TIndexRel (TVar rel) (TVar k)
      in TAnd (TForallRel k rel (TSetMember valK setE))
              (TForallSet vv setE (TExistsRel k rel (TEq valK (TVar vv)))))"

fun prime_rel_name :: "expr \<Rightarrow> String.literal option" where
  "prime_rel_name (PrimeF e _) = identName e"
| "prime_rel_name _ = None"

fun pre_rel_name :: "expr \<Rightarrow> String.literal option" where
  "pre_rel_name (PreF e _) = identName e"
| "pre_rel_name _ = None"

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
           else if is_builtin_func nm
             then map_option (\<lambda>a'. TUStrFunc nm a') (translate enums arg)
             else None)
      | (IdentifierF nm _, []) \<Rightarrow>
          (if is_builtin_const nm then Some (TUConst nm) else None)
      | (IdentifierF nm _, [coll, LambdaF p (FieldAccessF (IdentifierF q _) field _) _]) \<Rightarrow>
          (if nm = STR ''sum'' \<and> p = q
             then map_option (\<lambda>c. TSum c field) (translate enums coll)
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
             QAll \<Rightarrow>
               (case bs of
                  [QuantifierBindingFull v d _ _] \<Rightarrow>
                    (case d of
                       IdentifierF _ _ \<Rightarrow> translate_forall_bindings enums bs body'
                     | _ \<Rightarrow> map_option (\<lambda>d'. TForallSet v d' body') (translate enums d))
                | _ \<Rightarrow> translate_forall_bindings enums bs body')
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
           | PrimeF (IdentifierF x _) _ \<Rightarrow> Some (TPrime (TCardRel x))
           | PreF (IdentifierF x _) _ \<Rightarrow> Some (TPre (TCardRel x))
           | _ \<Rightarrow> map_option TCard (translate enums e))
      | UPower \<Rightarrow> None)"
| "translate enums (BinaryOpF op l r _) =
     (case op of
        BAnd \<Rightarrow> map2_opt TAnd (translate enums l) (translate enums r)
      | BOr \<Rightarrow> map2_opt TOr (translate enums l) (translate enums r)
      | BImplies \<Rightarrow> map2_opt TImplies (translate enums l) (translate enums r)
      | BIff \<Rightarrow>
          map2_opt (\<lambda>lt rt. TAnd (TImplies lt rt) (TImplies rt lt))
            (translate enums l) (translate enums r)
      | BEq \<Rightarrow>
          (case translate_beq_dom_or_none l r of
             Some t \<Rightarrow> Some t
           | None \<Rightarrow>
               (case rel_insert_parts BEq l r of
                  Some (rel, kn, vn) \<Rightarrow>
                    Some (TEq (TIndexRel (TPrime (TVar rel)) (TVar kn)) (TVar vn))
                | None \<Rightarrow>
                  (case range_arg r of
                     Some rel \<Rightarrow>
                       map_option (\<lambda>lt. translate_range_eq lt rel) (translate enums l)
                   | None \<Rightarrow>
                  (case comp_parts r of
                     Some (var, dnm, p) \<Rightarrow>
                       map2_opt (translate_set_comp_eq enums var dnm)
                         (translate enums l) (translate enums p)
                   | None \<Rightarrow> map2_opt TEq (translate enums l) (translate enums r)))))
      | BNeq \<Rightarrow>
          map2_opt (\<lambda>lt rt. TNot (TEq lt rt)) (translate enums l) (translate enums r)
      | BLt \<Rightarrow> map2_opt TLt (translate enums l) (translate enums r)
      | BGt \<Rightarrow> map2_opt (\<lambda>lt rt. TLt rt lt) (translate enums l) (translate enums r)
      | BLe \<Rightarrow>
          map2_opt (\<lambda>lt rt. TOr (TLt lt rt) (TEq lt rt))
            (translate enums l) (translate enums r)
      | BGe \<Rightarrow>
          map2_opt (\<lambda>lt rt. TOr (TLt rt lt) (TEq lt rt))
            (translate enums l) (translate enums r)
      | BAdd \<Rightarrow> map2_opt TAdd (translate enums l) (translate enums r)
      | BSub \<Rightarrow> map2_opt TSub (translate enums l) (translate enums r)
      | BMul \<Rightarrow> map2_opt TMul (translate enums l) (translate enums r)
      | BDiv \<Rightarrow> map2_opt TDiv (translate enums l) (translate enums r)
      | BUnion \<Rightarrow> map2_opt TSetUnion (translate enums l) (translate enums r)
      | BIntersect \<Rightarrow> map2_opt TSetIntersect (translate enums l) (translate enums r)
      | BDiff \<Rightarrow> map2_opt TSetDiff (translate enums l) (translate enums r)
      | BIn \<Rightarrow>
          (case translate enums l of
             None \<Rightarrow> None
           | Some lt \<Rightarrow>
               (case prime_rel_name r of
                  Some rel \<Rightarrow> Some (TPrime (TInDom rel lt))
                | None \<Rightarrow>
                    (case pre_rel_name r of
                       Some rel \<Rightarrow> Some (TPre (TInDom rel lt))
                     | None \<Rightarrow>
                         (case identName r of
                            Some rel \<Rightarrow> Some (TInDom rel lt)
                          | None \<Rightarrow>
                              (case comp_parts r of
                                 Some (var, dnm, p) \<Rightarrow>
                                   map_option (\<lambda>pt.
                                       TLetIn var lt
                                         (if string_in_list dnm enums then pt
                                          else TAnd (TInDom dnm (TVar var)) pt))
                                     (translate enums p)
                               | None \<Rightarrow>
                                   (case range_arg r of
                                      Some rel \<Rightarrow>
                                        (let k = fresh_var (STR ''k'') (rel # smt_var_list lt)
                                         in Some (TExistsRel k rel
                                                    (TEq (TIndexRel (TVar rel) (TVar k)) lt)))
                                    | None \<Rightarrow>
                                        map_option (\<lambda>rt. TSetMember lt rt)
                                          (translate enums r)))))))
      | BNotIn \<Rightarrow>
          (case translate enums l of
             None \<Rightarrow> None
           | Some lt \<Rightarrow>
               (case prime_rel_name r of
                  Some rel \<Rightarrow> Some (TNot (TPrime (TInDom rel lt)))
                | None \<Rightarrow>
                    (case pre_rel_name r of
                       Some rel \<Rightarrow> Some (TNot (TPre (TInDom rel lt)))
                     | None \<Rightarrow>
                         (case identName r of
                            Some rel \<Rightarrow> Some (TNot (TInDom rel lt))
                          | None \<Rightarrow>
                              map_option (\<lambda>rt. TNot (TSetMember lt rt))
                                (translate enums r)))))
      | BSubset \<Rightarrow>
          map2_opt (\<lambda>lt rt. TEq (TSetDiff lt rt) TSetEmpty)
            (translate enums l) (translate enums r))"
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
     (auto dest!: comp_parts_size)

lemma comp_parts_None:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "comp_parts r = None"
  using assms by (cases r rule: comp_parts.cases) (auto split: expr.splits)

lemma translate_BEq_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
      and "dom_arg l = None \<or> dom_arg r = None"
      and "rel_insert_parts BEq l r = None"
      and "range_arg r = None"
  shows "translate enums (BinaryOpF BEq l r sp)
           = map2_opt TEq (translate enums l) (translate enums r)"
proof -
  have dn: "translate_beq_dom_or_none l r = None"
    using assms(2) by (auto simp: translate_beq_dom_or_none_def split: option.splits)
  show ?thesis using dn comp_parts_None[OF assms(1)] assms(3) assms(4) by simp
qed

lemma translate_BIn_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "translate enums (BinaryOpF BIn l r sp)
           = (case translate enums l of
                None \<Rightarrow> None
              | Some lt \<Rightarrow>
                  (case prime_rel_name r of
                     Some rel \<Rightarrow> Some (TPrime (TInDom rel lt))
                   | None \<Rightarrow>
                       (case pre_rel_name r of
                          Some rel \<Rightarrow> Some (TPre (TInDom rel lt))
                        | None \<Rightarrow>
                            (case identName r of
                               Some rel \<Rightarrow> Some (TInDom rel lt)
                             | None \<Rightarrow>
                                 (case range_arg r of
                                    Some rel \<Rightarrow>
                                      (let k = fresh_var (STR ''k'') (rel # smt_var_list lt)
                                       in Some (TExistsRel k rel
                                                  (TEq (TIndexRel (TVar rel) (TVar k)) lt)))
                                  | None \<Rightarrow>
                                      map_option (\<lambda>rt. TSetMember lt rt) (translate enums r))))))"
  using comp_parts_None[OF assms] by (auto split: option.splits)

end
