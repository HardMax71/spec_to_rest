theory TranslateDirect
  imports Translate IR_Lower
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

lemma identName_smt_translate [simp]:
  "identName_smt (translate e) = identName e"
  by (cases e rule: translate.cases) auto

lemma peel_relation_ref_smt_translate [simp]:
  "peel_relation_ref_smt (translate e) = peel_relation_ref e"
  by (cases e rule: translate.cases) auto

lemma direct_dom_eq_translate [simp]:
  "translate (lower_dom_eq x y sp) = direct_dom_eq x y"
  by (simp add: lower_dom_eq_def direct_dom_eq_def)

lemma direct_beq_dom_or_none_translate [simp]:
  "map_option translate (lower_beq_dom_or_none l r sp) = direct_beq_dom_or_none l r"
  by (simp add: lower_beq_dom_or_none_def direct_beq_dom_or_none_def split: option.splits)

lemma direct_set_comp_eq_translate [simp]:
  "translate (lower_set_comp_eq enums var dnm setE predE sp)
     = direct_set_comp_eq enums var dnm (translate setE) (translate predE)"
  by (simp add: Let_def split: if_splits)

lemma direct_forall_step_translate [simp]:
  "map_option translate (lower_forall_step enums b body sp)
     = direct_forall_step enums b (translate body)"
proof (cases b)
  case (QuantifierBindingFull v coll knd sp2)
  then show ?thesis by (cases coll) auto
qed

lemma direct_forall_bindings_translate [simp]:
  "map_option translate (lower_forall_bindings enums bs body sp)
     = direct_forall_bindings enums bs (translate body)"
proof (induction bs)
  case Nil
  thus ?case by simp
next
  case (Cons b bs')
  note IH = Cons.IH
  show ?case
  proof (cases bs')
    case Nil
    thus ?thesis by (simp add: direct_forall_step_translate)
  next
    case (Cons b2 bs'')
    show ?thesis
    proof (cases "lower_forall_bindings enums bs' body sp")
      case None
      hence "direct_forall_bindings enums bs' (translate body) = None" using IH by simp
      thus ?thesis using None \<open>bs' = b2 # bs''\<close> by simp
    next
      case (Some inner)
      hence "direct_forall_bindings enums bs' (translate body) = Some (translate inner)"
        using IH by simp
      thus ?thesis using Some \<open>bs' = b2 # bs''\<close>
        by (simp add: direct_forall_step_translate)
    qed
  qed
qed

lemma directSetList_translate:
  "(\<And>x. x \<in> set es \<Longrightarrow> translate_full_direct enums x = map_option translate (lower enums x))
     \<Longrightarrow> directSetList enums es = map_option translate (lowerSetList enums es sp)"
  by (induction es) (auto split: option.splits)

lemma directSeqList_translate:
  "(\<And>x. x \<in> set es \<Longrightarrow> translate_full_direct enums x = map_option translate (lower enums x))
     \<Longrightarrow> directSeqList enums es = map_option translate (lowerSeqList enums es sp)"
  by (induction es) (auto split: option.splits)

lemma directMapEntries_translate:
  "(\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents
      \<Longrightarrow> translate_full_direct enums k = map_option translate (lower enums k))
     \<Longrightarrow> (\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents
            \<Longrightarrow> translate_full_direct enums v = map_option translate (lower enums v))
     \<Longrightarrow> directMapEntries enums ents = map_option translate (lowerMapEntries enums ents sp)"
proof (induction ents)
  case Nil
  thus ?case by simp
next
  case (Cons en ents')
  obtain k v sp3 where en: "en = MapEntryFull k v sp3" by (cases en) auto
  have hk: "translate_full_direct enums k = map_option translate (lower enums k)"
    using Cons.prems(1)[of k v sp3] en by simp
  have hv: "translate_full_direct enums v = map_option translate (lower enums v)"
    using Cons.prems(2)[of k v sp3] en by simp
  have ih: "directMapEntries enums ents' = map_option translate (lowerMapEntries enums ents' sp)"
  proof (rule Cons.IH)
    show "\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> translate_full_direct enums k = map_option translate (lower enums k)"
      using Cons.prems(1) by auto
    show "\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> translate_full_direct enums v = map_option translate (lower enums v)"
      using Cons.prems(2) by auto
  qed
  show ?case using en hk hv ih by (auto split: option.splits)
qed

lemma direct_with_assigns_translate:
  "(\<And>fld v sp3. FieldAssignFull fld v sp3 \<in> set fas
      \<Longrightarrow> translate_full_direct enums v = map_option translate (lower enums v))
     \<Longrightarrow> direct_with_assigns enums fas (translate base)
           = map_option translate (lower_with_assigns enums fas base sp)"
proof (induction fas arbitrary: base)
  case Nil
  thus ?case by simp
next
  case (Cons fa fas')
  obtain fld v sp3 where fa: "fa = FieldAssignFull fld v sp3" by (cases fa) auto
  have ev: "translate_full_direct enums v = map_option translate (lower enums v)"
    using Cons.prems(1)[of fld v sp3] fa by simp
  show ?case
  proof (cases "lower enums v")
    case None
    thus ?thesis using fa ev by simp
  next
    case (Some v')
    have ih: "direct_with_assigns enums fas' (translate (WithRec base fld v' sp))
                = map_option translate (lower_with_assigns enums fas' (WithRec base fld v' sp) sp)"
    proof (rule Cons.IH)
      show "\<And>fld v sp3. FieldAssignFull fld v sp3 \<in> set fas'
              \<Longrightarrow> translate_full_direct enums v = map_option translate (lower enums v)"
        using Cons.prems(1) by auto
    qed
    show ?thesis using fa ev Some ih by simp
  qed
qed

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

lemma translate_full_direct_eq:
  "translate_full_direct enums e = map_option translate (lower enums e)"
proof (induction e rule: measure_induct_rule[where f = size])
  case (less e)
  show ?case
  proof (cases e)
    case (PrimeF c sp2)
    have s: "size c < size e" using PrimeF by simp
    show ?thesis using PrimeF less.IH[OF s] by (simp add: option.map_comp o_def)
  next
    case (PreF c sp2)
    have s: "size c < size e" using PreF by simp
    show ?thesis using PreF less.IH[OF s] by (simp add: option.map_comp o_def)
  next
    case (SomeWrapF c sp2)
    have s: "size c < size e" using SomeWrapF by simp
    show ?thesis using SomeWrapF less.IH[OF s] by (simp add: option.map_comp o_def)
  next
    case (MatchesF c pat sp2)
    have s: "size c < size e" using MatchesF by simp
    show ?thesis using MatchesF less.IH[OF s] by (simp add: option.map_comp o_def)
  next
    case (FieldAccessF c f sp2)
    have s: "size c < size e" using FieldAccessF by simp
    show ?thesis using FieldAccessF less.IH[OF s] by (simp add: option.map_comp o_def)
  next
    case (EnumAccessF base mem sp2)
    show ?thesis using EnumAccessF by (cases base) auto
  next
    case (CallF c args sp2)
    show ?thesis
    proof (cases "\<exists>nm sp1 arg. c = IdentifierF nm sp1 \<and> args = [arg] \<and> is_builtin_pred nm")
      case True
      then obtain nm sp1 arg where ceq: "c = IdentifierF nm sp1" and aeq: "args = [arg]"
          and bip: "is_builtin_pred nm" by blast
      have s: "size arg < size e" using CallF aeq by simp
      show ?thesis using CallF ceq aeq bip less.IH[OF s] by (simp add: option.map_comp o_def)
    next
      case False
      then have "translate_full_direct enums (CallF c args sp2) = None
                   \<and> lower enums (CallF c args sp2) = None"
        by (auto split: expr_full.splits list.splits if_splits)
      thus ?thesis using CallF by simp
    qed
  next
    case (IndexF base key sp2)
    have sb: "size base < size e" and sk: "size key < size e" using IndexF by simp_all
    show ?thesis using IndexF less.IH[OF sb] less.IH[OF sk] by (auto split: option.splits)
  next
    case (IfF c a b sp2)
    have sc: "size c < size e" and sa: "size a < size e" and sb: "size b < size e"
      using IfF by simp_all
    show ?thesis using IfF less.IH[OF sc] less.IH[OF sa] less.IH[OF sb]
      by (auto split: option.splits)
  next
    case (LetF x v body sp2)
    have sv: "size v < size e" and sb: "size body < size e" using LetF by simp_all
    show ?thesis using LetF less.IH[OF sv] less.IH[OF sb] by (auto split: option.splits)
  next
    case (TheF var dm body sp2)
    show ?thesis
    proof (cases dm)
      case (IdentifierF rel rsp)
      have s: "size body < size e" using TheF by simp
      show ?thesis using TheF IdentifierF less.IH[OF s] by (auto simp: option.map_comp o_def)
    qed (use TheF in \<open>auto\<close>)
  next
    case (UnaryOpF op2 c sp2)
    have s: "size c < size e" using UnaryOpF by simp
    show ?thesis
    proof (cases op2)
      case UNot
      show ?thesis using UnaryOpF UNot less.IH[OF s] by (simp add: option.map_comp o_def)
    next
      case UNegate
      show ?thesis using UnaryOpF UNegate less.IH[OF s] by (simp add: option.map_comp o_def)
    next
      case UCardinality
      show ?thesis using UnaryOpF UCardinality by (cases c) auto
    next
      case UPower
      show ?thesis using UnaryOpF UPower by simp
    qed
  next
    case (QuantifierF k bs body sp2)
    have s: "size body < size e" using QuantifierF by simp
    have hb: "translate_full_direct enums body = map_option translate (lower enums body)"
      using less.IH[OF s] .
    show ?thesis
    proof (cases "lower enums body")
      case None
      thus ?thesis using QuantifierF hb by (cases k) simp_all
    next
      case (Some lb)
      with hb have tb: "translate_full_direct enums body = Some (translate lb)" by simp
      show ?thesis
      proof (cases k)
        case QAll
        thus ?thesis using QuantifierF Some tb
          by (simp add: direct_forall_bindings_translate)
      next
        case QNo
        thus ?thesis using QuantifierF Some tb
          by (simp add: direct_forall_bindings_translate)
      next
        case QSome
        thus ?thesis using QuantifierF Some tb
          direct_forall_bindings_translate[of enums bs "UnNot lb sp2" sp2]
          by (cases "lower_forall_bindings enums bs (UnNot lb sp2) sp2") simp_all
      next
        case QExists
        thus ?thesis using QuantifierF Some tb
          direct_forall_bindings_translate[of enums bs "UnNot lb sp2" sp2]
          by (cases "lower_forall_bindings enums bs (UnNot lb sp2) sp2") simp_all
      qed
    qed
  next
    case (ConstructorF name fas sp2)
    have "direct_with_assigns enums fas (translate (EntityBase name sp2))
            = map_option translate (lower_with_assigns enums fas (EntityBase name sp2) sp2)"
    proof (rule direct_with_assigns_translate)
      fix fld v sp3 assume m: "FieldAssignFull fld v sp3 \<in> set fas"
      hence "size v < size e"
        using ConstructorF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums v = map_option translate (lower enums v)"
        using less.IH by blast
    qed
    thus ?thesis using ConstructorF by simp
  next
    case (WithF base upds sp2)
    have sbase: "size base < size e" using WithF by simp
    have hbase: "translate_full_direct enums base = map_option translate (lower enums base)"
      using less.IH[OF sbase] .
    have wa: "\<And>b'. direct_with_assigns enums upds (translate b')
            = map_option translate (lower_with_assigns enums upds b' sp2)"
    proof (rule direct_with_assigns_translate)
      fix fld v sp3 assume m: "FieldAssignFull fld v sp3 \<in> set upds"
      hence "size v < size e"
        using WithF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums v = map_option translate (lower enums v)"
        using less.IH by blast
    qed
    show ?thesis using WithF hbase wa by (auto split: option.splits)
  next
    case (SetLiteralF elems sp2)
    have "directSetList enums elems = map_option translate (lowerSetList enums elems sp2)"
    proof (rule directSetList_translate)
      fix x assume m: "x \<in> set elems"
      hence "size x < size e"
        using SetLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums x = map_option translate (lower enums x)"
        using less.IH by blast
    qed
    thus ?thesis using SetLiteralF by simp
  next
    case (SeqLiteralF elems sp2)
    have "directSeqList enums elems = map_option translate (lowerSeqList enums elems sp2)"
    proof (rule directSeqList_translate)
      fix x assume m: "x \<in> set elems"
      hence "size x < size e"
        using SeqLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums x = map_option translate (lower enums x)"
        using less.IH by blast
    qed
    thus ?thesis using SeqLiteralF by simp
  next
    case (MapLiteralF entries sp2)
    have "directMapEntries enums entries = map_option translate (lowerMapEntries enums entries sp2)"
    proof (rule directMapEntries_translate)
      fix k v sp3 assume m: "MapEntryFull k v sp3 \<in> set entries"
      hence "size k < size e"
        using MapLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums k = map_option translate (lower enums k)"
        using less.IH by blast
    next
      fix k v sp3 assume m: "MapEntryFull k v sp3 \<in> set entries"
      hence "size v < size e"
        using MapLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      thus "translate_full_direct enums v = map_option translate (lower enums v)"
        using less.IH by blast
    qed
    thus ?thesis using MapLiteralF by simp
  next
    case (BinaryOpF op2 l2 r2 sp2)
    have sl: "size l2 < size e" and sr: "size r2 < size e" using BinaryOpF by simp_all
    have hl: "translate_full_direct enums l2 = map_option translate (lower enums l2)"
      using less.IH[OF sl] .
    have hr: "translate_full_direct enums r2 = map_option translate (lower enums r2)"
      using less.IH[OF sr] .
    show ?thesis
    proof (cases op2)
      case BEq
      show ?thesis
      proof (cases "\<exists>xrel yrel. dom_arg l2 = Some xrel \<and> dom_arg r2 = Some yrel")
        case True
        then obtain xrel yrel where da: "dom_arg l2 = Some xrel" "dom_arg r2 = Some yrel" by blast
        show ?thesis using BinaryOpF BEq
          by (simp add: lower_beq_dom_or_none_def direct_beq_dom_or_none_def da)
      next
        case False
        hence dnone: "dom_arg l2 = None \<or> dom_arg r2 = None" by auto
        have bn: "direct_beq_dom_or_none l2 r2 = None"
          using dnone by (auto simp: direct_beq_dom_or_none_def split: option.splits)
        have ln: "lower_beq_dom_or_none l2 r2 sp2 = None"
          using dnone by (auto simp: lower_beq_dom_or_none_def split: option.splits)
        show ?thesis
        proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
          case True
          then obtain cvar dnm s2 cpred s3
            where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
          have spred: "size cpred < size e" using BinaryOpF req by simp
          have hp: "translate_full_direct enums cpred = map_option translate (lower enums cpred)"
            using less.IH[OF spred] .
          show ?thesis using BinaryOpF BEq req bn ln hl hp
            by (auto split: option.splits)
        next
          case False
          hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
            by blast
          have tfd_eq: "translate_full_direct enums e
                          = (case (translate_full_direct enums l2, translate_full_direct enums r2) of
                               (Some lt, Some rt) \<Rightarrow> Some (TEq lt rt) | _ \<Rightarrow> None)"
            using BinaryOpF BEq direct_BEq_noncomp[OF nc dnone] by simp
          have low_eq: "lower enums e
                          = (case (lower enums l2, lower enums r2) of
                               (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp2) | _ \<Rightarrow> None)"
            using BinaryOpF BEq lower_BEq_noncomp[OF nc dnone] by simp
          show ?thesis using hl hr unfolding tfd_eq low_eq by (auto split: option.splits)
        qed
      qed
    next
      case BIn
      show ?thesis
      proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
        case True
        then obtain cvar dnm s2 cpred s3
          where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
        have spred: "size cpred < size e" using BinaryOpF req by simp
        have hp: "translate_full_direct enums cpred = map_option translate (lower enums cpred)"
          using less.IH[OF spred] .
        show ?thesis using BinaryOpF BIn req hl hp
          by (auto split: option.splits if_splits)
      next
        case False
        hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
          by blast
        have tfd_eq: "translate_full_direct enums e
                        = (case r2 of
                             IdentifierF rel _ \<Rightarrow>
                               map_option (\<lambda>lt. TInDom rel lt) (translate_full_direct enums l2)
                           | _ \<Rightarrow> (case (translate_full_direct enums l2, translate_full_direct enums r2) of
                                     (Some lt, Some rt) \<Rightarrow> Some (TSetMember lt rt) | _ \<Rightarrow> None))"
          using BinaryOpF BIn direct_BIn_noncomp[OF nc] by simp
        have low_eq: "lower enums e
                        = (case r2 of
                             IdentifierF rel _ \<Rightarrow>
                               map_option (\<lambda>l'. Member l' rel sp2) (lower enums l2)
                           | _ \<Rightarrow> (case (lower enums l2, lower enums r2) of
                                     (Some l', Some r') \<Rightarrow> Some (SetMember l' r' sp2) | _ \<Rightarrow> None))"
          using BinaryOpF BIn lower_BIn_noncomp[OF nc] by simp
        show ?thesis using hl hr unfolding tfd_eq low_eq
          by (cases r2) (auto simp: option.map_comp o_def
                del: lower.simps translate_full_direct.simps split: option.splits)
      qed
    next
      case BNotIn
      have tfd_eq: "translate_full_direct enums e
                      = (case r2 of
                           IdentifierF rel _ \<Rightarrow>
                             map_option (\<lambda>lt. TNot (TInDom rel lt)) (translate_full_direct enums l2)
                         | _ \<Rightarrow> (case (translate_full_direct enums l2, translate_full_direct enums r2) of
                                   (Some lt, Some rt) \<Rightarrow> Some (TNot (TSetMember lt rt)) | _ \<Rightarrow> None))"
        using BinaryOpF BNotIn by simp
      have low_eq: "lower enums e
                      = (case r2 of
                           IdentifierF rel _ \<Rightarrow>
                             map_option (\<lambda>l'. UnNot (Member l' rel sp2) sp2) (lower enums l2)
                         | _ \<Rightarrow> (case (lower enums l2, lower enums r2) of
                                   (Some l', Some r') \<Rightarrow> Some (UnNot (SetMember l' r' sp2) sp2) | _ \<Rightarrow> None))"
        using BinaryOpF BNotIn by simp
      show ?thesis
        using hl hr unfolding tfd_eq low_eq
        by (cases r2) (auto simp: option.map_comp o_def
              del: lower.simps translate_full_direct.simps split: option.splits)
    qed (use BinaryOpF hl hr in \<open>auto split: option.splits\<close>)
  qed (auto simp: option.map_comp o_def split: option.splits)
qed

end
