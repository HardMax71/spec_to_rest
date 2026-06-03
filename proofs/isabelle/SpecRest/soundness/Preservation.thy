theory Preservation
  imports Soundness
begin


text \<open>Phase 9j (dual of 9i). \<open>requiresAlloy_imp_lower_none\<close> proved the
  Alloy-routed fragment maps to \<open>None\<close>; here a syntactic \<open>wf_z3\<close>
  predicate carves out the Z3-verifiable subset and we prove
  \<open>wf_z3 e \<Longrightarrow> lower enums e \<noteq> None\<close>. This upgrades the
  \<open>Trust.classify\<close> runtime oracle (\<open>lower(e).isDefined \<Longrightarrow>
  Sound\<close>) to a proven syntactic guarantee: in-fragment specs are Sound,
  never best-effort. \<open>IndexF\<close> requires a relation-ref base shape (the
  \<open>peel_relation_ref\<close> side-condition); quantifier bodies require every
  binding domain to be an identifier (the \<open>lower_forall_bindings\<close>
  totality condition, dual to \<open>lfb_none_of_alloy\<close>).\<close>

fun is_ident_dom :: "quantifier_binding_full \<Rightarrow> bool" where
  "is_ident_dom (QuantifierBindingFull _ (IdentifierF _ _) _ _) = True"
| "is_ident_dom _ = False"

fun wf_z3_bindings :: "quantifier_binding_full list \<Rightarrow> bool" where
  "wf_z3_bindings [] = False"
| "wf_z3_bindings [b] = is_ident_dom b"
| "wf_z3_bindings (b # rest) = (is_ident_dom b \<and> wf_z3_bindings rest)"

fun rel_ref_shape :: "expr_full \<Rightarrow> bool" where
  "rel_ref_shape (IdentifierF _ _) = True"
| "rel_ref_shape (PreF b _)        = (identNameFull b \<noteq> None)"
| "rel_ref_shape (PrimeF b _)      = (identNameFull b \<noteq> None)"
| "rel_ref_shape _                 = False"

lemma identNameFull_SomeD:
  "identNameFull e = Some rel \<Longrightarrow> \<exists>sp. e = IdentifierF rel sp"
  by (cases e rule: identNameFull.cases) auto

fun wf_z3 :: "expr_full \<Rightarrow> bool"
and wf_z3_list :: "expr_full list \<Rightarrow> bool"
and wf_z3_fields :: "field_assign_full list \<Rightarrow> bool"
and wf_z3_entries :: "map_entry_full list \<Rightarrow> bool"
where
  "wf_z3 (BoolLitF _ _)            = True"
| "wf_z3 (IntLitF _ _)             = True"
| "wf_z3 (IdentifierF _ _)         = True"
| "wf_z3 (UnaryOpF op e _)         =
     (case op of UNot \<Rightarrow> wf_z3 e | UNegate \<Rightarrow> wf_z3 e
        | UCardinality \<Rightarrow> (\<exists>x s. e = IdentifierF x s)
        | UPower \<Rightarrow> False)"
| "wf_z3 (BinaryOpF op l r _)      =
     (case op of BSubset \<Rightarrow> False
        | BIn \<Rightarrow> (wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r))
        | BNotIn \<Rightarrow> (wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r))
        | _ \<Rightarrow> wf_z3 l \<and> wf_z3 r)"
| "wf_z3 (LetF _ v b _)            = (wf_z3 v \<and> wf_z3 b)"
| "wf_z3 (EnumAccessF base _ _)    = (\<exists>en s. base = IdentifierF en s)"
| "wf_z3 (FieldAccessF base _ _)   = wf_z3 base"
| "wf_z3 (IndexF base key _)       = (rel_ref_shape base \<and> wf_z3 key)"
| "wf_z3 (PrimeF e _)              = wf_z3 e"
| "wf_z3 (PreF e _)                = wf_z3 e"
| "wf_z3 (WithF base ups _)        = (wf_z3 base \<and> wf_z3_fields ups)"
| "wf_z3 (SetLiteralF es _)        = wf_z3_list es"
| "wf_z3 (QuantifierF _ bs body _) = (wf_z3_bindings bs \<and> wf_z3 body)"
| "wf_z3 (FloatLitF s _)           = (decimalToRat s \<noteq> None)"
| "wf_z3 (StringLitF _ _)          = True"
| "wf_z3 (NoneLitF _)              = True"
| "wf_z3 (LambdaF _ _ _)           = False"
| "wf_z3 (CallF _ _ _)             = False"
| "wf_z3 (ConstructorF _ _ _)      = False"
| "wf_z3 (MapLiteralF entries _)   = wf_z3_entries entries"
| "wf_z3 (SeqLiteralF es _)        = wf_z3_list es"
| "wf_z3 (SetComprehensionF _ _ _ _) = False"
| "wf_z3 (SomeWrapF e _)           = wf_z3 e"
| "wf_z3 (TheF _ _ _ _)            = False"
| "wf_z3 (MatchesF _ _ _)          = False"
| "wf_z3 (IfF c a b _)             = (wf_z3 c \<and> wf_z3 a \<and> wf_z3 b)"
| "wf_z3_list []                   = True"
| "wf_z3_list (e # rest)           = (wf_z3 e \<and> wf_z3_list rest)"
| "wf_z3_fields []                 = True"
| "wf_z3_fields (FieldAssignFull _ v _ # rest) = (wf_z3 v \<and> wf_z3_fields rest)"
| "wf_z3_entries []                = True"
| "wf_z3_entries (MapEntryFull k v _ # rest) = (wf_z3 k \<and> wf_z3 v \<and> wf_z3_entries rest)"

lemma wf_z3_fields_iff:
  "wf_z3_fields updates
     \<longleftrightarrow> (\<forall>fld v sp. FieldAssignFull fld v sp \<in> set updates \<longrightarrow> wf_z3 v)"
proof (induction updates)
  case Nil thus ?case by simp
next
  case (Cons hd rest)
  thus ?case by (cases hd) auto
qed

lemma lower_forall_step_some:
  "is_ident_dom b \<Longrightarrow> \<exists>r. lower_forall_step enums b body sp = Some r"
  by (cases b rule: is_ident_dom.cases) auto

lemma lfb_some_of_wf:
  "wf_z3_bindings bs \<Longrightarrow> \<exists>r. lower_forall_bindings enums bs body sp = Some r"
proof (induction bs arbitrary: body)
  case Nil
  then show ?case by simp
next
  case (Cons b bs')
  show ?case
  proof (cases bs')
    case Nil
    with Cons.prems show ?thesis
      by (simp add: lower_forall_step_some)
  next
    case (Cons b2 bs'')
    from Cons.prems \<open>bs' = b2 # bs''\<close>
    have ib: "is_ident_dom b" and wr: "wf_z3_bindings bs'" by auto
    from Cons.IH[OF wr] obtain inner where
      "lower_forall_bindings enums bs' body sp = Some inner" by blast
    with ib \<open>bs' = b2 # bs''\<close> show ?thesis
      by (auto simp: lower_forall_step_some)
  qed
qed

lemma rel_ref_lower:
  "rel_ref_shape base
     \<Longrightarrow> \<exists>b' rel. lower enums base = Some b' \<and> peel_relation_ref b' = Some rel"
  by (cases base rule: rel_ref_shape.cases) (auto dest!: identNameFull_SomeD)

lemma lower_unop_some:
  assumes ih: "wf_z3 e \<Longrightarrow> lower enums e \<noteq> None"
      and wf: "wf_z3 (UnaryOpF op e sp)"
  shows "lower enums (UnaryOpF op e sp) \<noteq> None"
proof (cases op)
  case UCardinality
  with wf obtain x s where "e = IdentifierF x s" by auto
  thus ?thesis unfolding UCardinality by simp
next
  case UPower
  with wf show ?thesis by simp
qed (use wf ih in \<open>auto split: option.splits\<close>)

lemma lower_binop_some:
  assumes l: "wf_z3 l \<Longrightarrow> lower enums l \<noteq> None"
      and r: "wf_z3 r \<Longrightarrow> lower enums r \<noteq> None"
      and wf: "wf_z3 (BinaryOpF op l r sp)"
  shows "lower enums (BinaryOpF op l r sp) \<noteq> None"
proof -
  have inout: "lower enums (BinaryOpF op l r sp) \<noteq> None"
    if opc: "op = BIn \<or> op = BNotIn"
  proof -
    from wf opc have wl: "wf_z3 l"
        and rd: "(\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r" by auto
    from l wl obtain l' where l': "lower enums l = Some l'" by blast
    from rd show ?thesis
    proof
      assume "\<exists>rel s. r = IdentifierF rel s"
      then obtain rel s where "r = IdentifierF rel s" by blast
      thus ?thesis using l' opc by auto
    next
      assume "wf_z3 r"
      with r obtain r' where "lower enums r = Some r'" by blast
      with l' opc show ?thesis by (cases r) auto
    qed
  qed
  show ?thesis
  proof (cases op)
    case BIn
    thus ?thesis using inout by blast
  next
    case BNotIn
    thus ?thesis using inout by blast
  next
    case BSubset
    with wf show ?thesis by simp
  qed (use l r wf in \<open>auto split: option.splits\<close>)
qed

lemma lower_index_some:
  assumes "wf_z3 key \<Longrightarrow> lower enums key \<noteq> None"
      and "wf_z3 (IndexF bse key sp)"
  shows "lower enums (IndexF bse key sp) \<noteq> None"
proof -
  from assms(2) have rs: "rel_ref_shape bse" and wk: "wf_z3 key" by auto
  obtain b' rel where b': "lower enums bse = Some b'"
      and pr: "peel_relation_ref b' = Some rel"
    using rel_ref_lower[OF rs] by blast
  from assms(1) wk obtain k' where "lower enums key = Some k'" by blast
  with b' pr show ?thesis by simp
qed

lemma lower_quant_some:
  assumes "wf_z3 body \<Longrightarrow> lower enums body \<noteq> None"
      and "wf_z3 (QuantifierF k bs body sp)"
  shows "lower enums (QuantifierF k bs body sp) \<noteq> None"
proof -
  from assms(2) have wb: "wf_z3_bindings bs" and wbody: "wf_z3 body" by auto
  from assms(1) wbody obtain body' where bd: "lower enums body = Some body'"
    by blast
  show ?thesis
  proof (cases k)
    case QAll
    obtain r where "lower_forall_bindings enums bs body' sp = Some r"
      using lfb_some_of_wf[OF wb] by blast
    thus ?thesis using bd QAll by simp
  next
    case QNo
    obtain r where "lower_forall_bindings enums bs (UnNot body' sp) sp = Some r"
      using lfb_some_of_wf[OF wb] by blast
    thus ?thesis using bd QNo by simp
  next
    case QSome
    obtain r where "lower_forall_bindings enums bs (UnNot body' sp) sp = Some r"
      using lfb_some_of_wf[OF wb] by blast
    thus ?thesis using bd QSome by simp
  next
    case QExists
    obtain r where "lower_forall_bindings enums bs (UnNot body' sp) sp = Some r"
      using lfb_some_of_wf[OF wb] by blast
    thus ?thesis using bd QExists by simp
  qed
qed

lemma lowerSetList_wf_some:
  assumes "\<And>x. x \<in> set xs \<Longrightarrow> wf_z3 x \<Longrightarrow> lower enums x \<noteq> None"
      and "wf_z3_list xs"
  shows "lowerSetList enums xs sp \<noteq> None"
  using assms
proof (induction xs)
  case (Cons a xs)
  show ?case using Cons by (auto split: option.splits)
qed simp

lemma lowerSeqList_wf_some:
  assumes "\<And>x. x \<in> set xs \<Longrightarrow> wf_z3 x \<Longrightarrow> lower enums x \<noteq> None"
      and "wf_z3_list xs"
  shows "lowerSeqList enums xs sp \<noteq> None"
  using assms
proof (induction xs)
  case (Cons a xs)
  show ?case using Cons by (auto split: option.splits)
qed simp

lemma lowerMapEntries_wf_some:
  assumes "\<And>k v esp. MapEntryFull k v esp \<in> set entries
             \<Longrightarrow> (wf_z3 k \<longrightarrow> lower enums k \<noteq> None) \<and> (wf_z3 v \<longrightarrow> lower enums v \<noteq> None)"
      and "wf_z3_entries entries"
  shows "lowerMapEntries enums entries sp \<noteq> None"
  using assms
proof (induction entries)
  case (Cons e es)
  obtain k v esp where e_eq: "e = MapEntryFull k v esp" by (cases e)
  have hk: "wf_z3 k \<longrightarrow> lower enums k \<noteq> None"
   and hv: "wf_z3 v \<longrightarrow> lower enums v \<noteq> None"
    using Cons.prems(1)[of k v esp] e_eq by simp_all
  have tl: "\<And>k v esp. MapEntryFull k v esp \<in> set es \<Longrightarrow>
              (wf_z3 k \<longrightarrow> lower enums k \<noteq> None) \<and> (wf_z3 v \<longrightarrow> lower enums v \<noteq> None)"
  proof -
    fix k v esp assume "MapEntryFull k v esp \<in> set es"
    hence "MapEntryFull k v esp \<in> set (e # es)" by simp
    thus "(wf_z3 k \<longrightarrow> lower enums k \<noteq> None) \<and> (wf_z3 v \<longrightarrow> lower enums v \<noteq> None)"
      using Cons.prems(1) by blast
  qed
  from Cons.IH[OF tl] have ih: "wf_z3_entries es \<Longrightarrow> lowerMapEntries enums es sp \<noteq> None" .
  show ?case using e_eq hk hv ih Cons.prems(2) by (auto split: option.splits)
qed simp

lemma lower_with_assigns_wf_some:
  assumes "\<And>fld v fsp. FieldAssignFull fld v fsp \<in> set fs
             \<Longrightarrow> wf_z3 v \<Longrightarrow> lower enums v \<noteq> None"
      and "wf_z3_fields fs"
  shows "lower_with_assigns enums fs base sp \<noteq> None"
  using assms
proof (induction fs arbitrary: base)
  case (Cons a fs)
  obtain fld v fsp where a: "a = FieldAssignFull fld v fsp"
    by (cases a) auto
  have hv: "wf_z3 v \<Longrightarrow> lower enums v \<noteq> None"
    using Cons.prems(1)[of fld v fsp] a by simp
  have pe: "\<And>f2 v2 s2. FieldAssignFull f2 v2 s2 \<in> set fs
              \<Longrightarrow> wf_z3 v2 \<Longrightarrow> lower enums v2 \<noteq> None"
    using Cons.prems(1) by auto
  have wf2: "wf_z3_fields fs" using Cons.prems(2) a by simp
  have hrec: "\<And>b. lower_with_assigns enums fs b sp \<noteq> None"
    using Cons.IH pe wf2 by blast
  show ?case unfolding a using hv hrec Cons.prems(2) a
    by (cases "lower enums v") auto
qed simp

lemma wf_z3_imp_lower_some_expr:
  "wf_z3 e \<Longrightarrow> lower enums e \<noteq> None"
proof (induction e rule: measure_induct_rule[where f = size])
  case (less e)
  have sub: "\<And>s. size s < size e \<Longrightarrow> wf_z3 s \<Longrightarrow> lower enums s \<noteq> None"
    using less.IH by blast
  show ?case
  proof (cases e)
    case (UnaryOpF op a s)
    have ih: "wf_z3 a \<Longrightarrow> lower enums a \<noteq> None"
      using sub[of a] UnaryOpF by simp
    show ?thesis unfolding UnaryOpF
      by (rule lower_unop_some[OF ih less.prems[unfolded UnaryOpF]])
  next
    case (BinaryOpF op l r s)
    have l: "wf_z3 l \<Longrightarrow> lower enums l \<noteq> None"
      using sub[of l] BinaryOpF by simp
    have r: "wf_z3 r \<Longrightarrow> lower enums r \<noteq> None"
      using sub[of r] BinaryOpF by simp
    show ?thesis unfolding BinaryOpF
      by (rule lower_binop_some[OF l r less.prems[unfolded BinaryOpF]])
  next
    case (QuantifierF k bs body s)
    have ih: "wf_z3 body \<Longrightarrow> lower enums body \<noteq> None"
      using sub[of body] QuantifierF by simp
    show ?thesis unfolding QuantifierF
      by (rule lower_quant_some[OF ih less.prems[unfolded QuantifierF]])
  next
    case (IndexF bse key s)
    have ih: "wf_z3 key \<Longrightarrow> lower enums key \<noteq> None"
      using sub[of key] IndexF by simp
    show ?thesis unfolding IndexF
      by (rule lower_index_some[OF ih less.prems[unfolded IndexF]])
  next
    case (LetF x v bd s)
    have v: "wf_z3 v \<Longrightarrow> lower enums v \<noteq> None"
      using sub[of v] LetF by simp
    have bdh: "wf_z3 bd \<Longrightarrow> lower enums bd \<noteq> None"
      using sub[of bd] LetF by simp
    show ?thesis unfolding LetF using v bdh less.prems[unfolded LetF]
      by (auto split: option.splits)
  next
    case (WithF bse ups s)
    have b: "wf_z3 bse \<Longrightarrow> lower enums bse \<noteq> None"
      using sub[of bse] WithF by simp
    have w: "\<And>bse'. wf_z3_fields ups
               \<Longrightarrow> lower_with_assigns enums ups bse' s \<noteq> None"
    proof -
      fix bse' assume wf: "wf_z3_fields ups"
      have pe: "\<And>fld vv fsp. FieldAssignFull fld vv fsp \<in> set ups
                  \<Longrightarrow> wf_z3 vv \<Longrightarrow> lower enums vv \<noteq> None"
      proof -
        fix fld vv fsp
        assume m: "FieldAssignFull fld vv fsp \<in> set ups"
           and rv: "wf_z3 vv"
        have "size vv < size (FieldAssignFull fld vv fsp)" by simp
        also have "\<dots> \<le> size_list size ups"
          by (rule size_list_estimation'[OF m order_refl])
        also have "\<dots> < size e" using WithF by simp
        finally have "size vv < size e" .
        thus "lower enums vv \<noteq> None" using sub rv by blast
      qed
      show "lower_with_assigns enums ups bse' s \<noteq> None"
        by (rule lower_with_assigns_wf_some[OF pe wf])
    qed
    show ?thesis unfolding WithF using b w less.prems[unfolded WithF]
      by (cases "lower enums bse") auto
  next
    case (SetLiteralF elems s)
    have pe: "\<And>x. x \<in> set elems \<Longrightarrow> wf_z3 x \<Longrightarrow> lower enums x \<noteq> None"
    proof -
      fix x assume m: "x \<in> set elems" and rx: "wf_z3 x"
      have "size x \<le> size_list size elems"
        by (rule size_list_estimation'[OF m order_refl])
      also have "\<dots> < size e" using SetLiteralF by simp
      finally have "size x < size e" .
      thus "lower enums x \<noteq> None" using sub rx by blast
    qed
    have wl: "wf_z3_list elems"
      using less.prems SetLiteralF by simp
    have "lowerSetList enums elems s \<noteq> None"
      by (rule lowerSetList_wf_some[OF pe wl])
    thus ?thesis unfolding SetLiteralF by simp
  next
    case (SeqLiteralF elems s)
    have pe: "\<And>x. x \<in> set elems \<Longrightarrow> wf_z3 x \<Longrightarrow> lower enums x \<noteq> None"
    proof -
      fix x assume m: "x \<in> set elems" and rx: "wf_z3 x"
      have "size x \<le> size_list size elems"
        by (rule size_list_estimation'[OF m order_refl])
      also have "\<dots> < size e" using SeqLiteralF by simp
      finally have "size x < size e" .
      thus "lower enums x \<noteq> None" using sub rx by blast
    qed
    have wl: "wf_z3_list elems"
      using less.prems SeqLiteralF by simp
    have "lowerSeqList enums elems s \<noteq> None"
      by (rule lowerSeqList_wf_some[OF pe wl])
    thus ?thesis unfolding SeqLiteralF by simp
  next
    case (MapLiteralF entries s)
    have pe: "\<And>k v esp. MapEntryFull k v esp \<in> set entries
                \<Longrightarrow> (wf_z3 k \<longrightarrow> lower enums k \<noteq> None) \<and> (wf_z3 v \<longrightarrow> lower enums v \<noteq> None)"
    proof -
      fix k v esp assume m: "MapEntryFull k v esp \<in> set entries"
      have "size (MapEntryFull k v esp) \<le> size_list size entries"
        by (rule size_list_estimation'[OF m order_refl])
      also have "\<dots> < size e" using MapLiteralF by simp
      finally have lt: "size (MapEntryFull k v esp) < size e" .
      have "size k < size e" and "size v < size e" using lt by simp_all
      thus "(wf_z3 k \<longrightarrow> lower enums k \<noteq> None) \<and> (wf_z3 v \<longrightarrow> lower enums v \<noteq> None)"
        using sub by blast
    qed
    have wl: "wf_z3_entries entries"
      using less.prems MapLiteralF by simp
    have "lowerMapEntries enums entries s \<noteq> None"
      by (rule lowerMapEntries_wf_some[OF pe wl])
    thus ?thesis unfolding MapLiteralF by simp
  next
    case (IfF c a b s)
    have hc: "wf_z3 c \<Longrightarrow> lower enums c \<noteq> None"
      using sub[of c] IfF by simp
    have ha: "wf_z3 a \<Longrightarrow> lower enums a \<noteq> None"
      using sub[of a] IfF by simp
    have hb: "wf_z3 b \<Longrightarrow> lower enums b \<noteq> None"
      using sub[of b] IfF by simp
    show ?thesis unfolding IfF using hc ha hb less.prems[unfolded IfF]
      by (auto split: option.splits)
  qed (use less.prems sub in \<open>auto split: option.splits\<close>)
qed

lemma wf_z3_imp_lower_some:
  "wf_z3 e \<Longrightarrow> lower enums e \<noteq> None"
  "wf_z3_list xs \<Longrightarrow> lowerSetList enums xs sp \<noteq> None"
  "wf_z3_fields fs \<Longrightarrow> lower_with_assigns enums fs base sp \<noteq> None"
proof -
  show "wf_z3 e \<Longrightarrow> lower enums e \<noteq> None"
    by (rule wf_z3_imp_lower_some_expr)
next
  assume r: "wf_z3_list xs"
  show "lowerSetList enums xs sp \<noteq> None"
  proof (rule lowerSetList_wf_some[OF _ r])
    fix x assume "x \<in> set xs" and wx: "wf_z3 x"
    show "lower enums x \<noteq> None"
      using wx by (rule wf_z3_imp_lower_some_expr)
  qed
next
  assume r: "wf_z3_fields fs"
  show "lower_with_assigns enums fs base sp \<noteq> None"
  proof (rule lower_with_assigns_wf_some[OF _ r])
    fix fld v fsp
    assume "FieldAssignFull fld v fsp \<in> set fs" and wv: "wf_z3 v"
    show "lower enums v \<noteq> None"
      using wv by (rule wf_z3_imp_lower_some_expr)
  qed
qed

lemma flattenAnd_wf_z3_iff:
  "wf_z3 e \<longleftrightarrow> list_all wf_z3 (flattenAnd e)"
  by (induction e rule: flattenAnd.induct) (auto simp: list_all_iff)

text \<open>Phase H3e helper (enum-list membership). The lower-side
  uses \<open>string_in_list :: String.literal \<Rightarrow> String.literal list
  \<Rightarrow> bool\<close> for its enum-vs-relation routing decision; the
  Semantics-side typing rules express the same via \<open>\<in> set\<close>.
  This bridge lemma unifies them.\<close>

lemma string_in_list_iff_in_set:
  "string_in_list y xs \<longleftrightarrow> y \<in> set xs"
  by (induction xs) auto

text \<open>Phase H3b helpers (relation-reference shape). Bridges the
  Semantics-side \<open>peelRelationRefFull :: expr_full \<Rightarrow> _ option\<close>
  used by \<open>T_Index\<close>'s typing premise to the Soundness-side
  \<open>rel_ref_shape\<close> / \<open>peel_relation_ref :: expr \<Rightarrow> _ option\<close> used
  by \<open>wf_z3\<close> and the IndexRel eval arm.\<close>

lemma peelRelationRefFull_some_imp_rel_ref_shape:
  "peelRelationRefFull base = Some rel \<Longrightarrow> rel_ref_shape base"
  by (cases base rule: peelRelationRefFull.cases) (auto dest!: identNameFull_SomeD)

lemma peelRelationRefFull_lower:
  assumes "peelRelationRefFull base = Some rel"
      and "lower enums base = Some base'"
  shows "peel_relation_ref base' = Some rel"
  using assms by (cases base rule: peelRelationRefFull.cases) (auto dest!: identNameFull_SomeD)

text \<open>Phase H3c helpers (with-assigns chain). \<open>lower_with_assigns\<close>
  folds a \<open>FieldAssignFull\<close> list into nested \<open>WithRec\<close>s; eval
  decomposes the chain back. The two lemmas extract: that the
  chain's eval implies the base's eval (used in T_With to discharge
  the IH(1) eval-premise), and that the chain preserves the base's
  entity type (used in the T_With umbrella case directly).\<close>

lemma lower_with_assigns_eval_implies_base_eval:
  "lower_with_assigns enums updates be sp = Some e'
     \<Longrightarrow> eval sch st env e' = Some v
     \<Longrightarrow> \<exists>bv. eval sch st env be = Some bv"
proof (induction updates arbitrary: be e' v)
  case Nil
  hence "be = e'" by simp
  thus ?case using Nil.prems(2) by auto
next
  case (Cons hd rest)
  obtain fld vhd sphd where hd_eq: "hd = FieldAssignFull fld vhd sphd"
    by (cases hd) auto
  from Cons.prems(1) hd_eq obtain vhd' where
       vhd_low: "lower enums vhd = Some vhd'"
   and rest_low: "lower_with_assigns enums rest (WithRec be fld vhd' sp) sp = Some e'"
    by (auto split: option.splits)
  from Cons.IH[OF rest_low Cons.prems(2)] obtain new_bv where
    new_bv_eval: "eval sch st env (WithRec be fld vhd' sp) = Some new_bv" by blast
  thus ?case by (auto split: option.splits ir_value.splits)
qed

lemma lower_with_assigns_preserves_entity:
  assumes "lower_with_assigns enums updates be sp = Some e'"
      and "eval sch st env e' = Some v"
      and "eval sch st env be = Some bv"
      and "value_has_ty \<Gamma> bv (TEntity ename)"
      and updates_typed:
        "\<forall>fld vhd sphd. FieldAssignFull fld vhd sphd \<in> set updates
            \<longrightarrow> (\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft
                    \<and> (\<forall>vhd' vhd_val. lower enums vhd = Some vhd'
                          \<longrightarrow> eval sch st env vhd' = Some vhd_val
                          \<longrightarrow> value_has_ty \<Gamma> vhd_val ft))"
  shows "value_has_ty \<Gamma> v (TEntity ename)"
  using assms
proof (induction updates arbitrary: be e' v bv)
  case Nil
  hence "e' = be" by simp
  thus ?case using Nil.prems by simp
next
  case (Cons hd rest)
  obtain fld vhd sphd where hd_eq: "hd = FieldAssignFull fld vhd sphd"
    by (cases hd) auto
  from Cons.prems(1) hd_eq obtain vhd' where
       vhd_low: "lower enums vhd = Some vhd'"
   and rest_low: "lower_with_assigns enums rest (WithRec be fld vhd' sp) sp = Some e'"
    by (auto split: option.splits)
  from lower_with_assigns_eval_implies_base_eval[OF rest_low Cons.prems(2)]
  obtain new_bv where
    new_bv_eval: "eval sch st env (WithRec be fld vhd' sp) = Some new_bv" by blast
  from new_bv_eval Cons.prems(3) obtain vhd_val where
       new_bv_eq: "new_bv = VEntityWith bv fld vhd_val"
   and ev_vhd:   "eval sch st env vhd' = Some vhd_val"
    by (auto split: option.splits ir_value.splits)
  have hd_in: "FieldAssignFull fld vhd sphd \<in> set (hd # rest)"
    using hd_eq by simp
  from Cons.prems(5) hd_in obtain ft where
       sft:        "schemaFieldType \<Gamma> ename fld = Some ft"
   and vhd_typed: "\<forall>vhd' vhd_val. lower enums vhd = Some vhd'
                       \<longrightarrow> eval sch st env vhd' = Some vhd_val
                       \<longrightarrow> value_has_ty \<Gamma> vhd_val ft"
    by blast
  have vhd_val_ty: "value_has_ty \<Gamma> vhd_val ft"
    using vhd_typed vhd_low ev_vhd by blast
  have new_bv_ty: "value_has_ty \<Gamma> new_bv (TEntity ename)"
    using new_bv_eq Cons.prems(4) sft vhd_val_ty
    by (auto intro: vt_entity_with)
  have rest_typed:
    "\<forall>fld vhd sphd. FieldAssignFull fld vhd sphd \<in> set rest
        \<longrightarrow> (\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft
                \<and> (\<forall>vhd' vhd_val. lower enums vhd = Some vhd'
                      \<longrightarrow> eval sch st env vhd' = Some vhd_val
                      \<longrightarrow> value_has_ty \<Gamma> vhd_val ft))"
    using Cons.prems(5) by auto
  show ?case
    using Cons.IH[OF rest_low Cons.prems(2) new_bv_eval new_bv_ty rest_typed] .
qed

text \<open>Phase H2 -> 9j bridge. Every well-typed expression in the
  H2 arith / cmp / bool fragment lies in the Phase 9j \<open>wf_z3\<close>
  subset. Composed with \<open>wf_z3_imp_lower_some_expr\<close> this gives
  the first half of the progress chain:
    well-typed e ==> wf_z3 e ==> lower enums e \<noteq> None.
  The H3 type-safety theorem will then complete the chain via
  the H1 preservation lemmas on the lowered \<open>expr\<close>.\<close>

lemma well_typed_imp_wf_z3:
  "expr_has_ty \<Gamma> e t \<Longrightarrow> wf_z3 e"
proof (induction rule: expr_has_ty.induct)
  case (T_Arith \<Gamma> l t1 r t2 op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Eq \<Gamma> l t1 r t2 op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Ord \<Gamma> l t1 r t2 op sp)
  thus ?case by (cases op) auto
next
  case (T_Bool_Bin \<Gamma> l r op sp)
  thus ?case by (cases op) auto
next
  case (T_Let \<Gamma> v t1 x body t2 sp)
  thus ?case by simp
next
  case (T_Prime \<Gamma> e t sp)
  thus ?case by simp
next
  case (T_Pre \<Gamma> e t sp)
  thus ?case by simp
next
  case (T_EnumAccess \<Gamma> en sp1 mem sp)
  thus ?case by simp
next
  case (T_Card \<Gamma> x sp1 sp)
  thus ?case by simp
next
  case (T_BIn_Rel \<Gamma> l t rel sp1 sp)
  thus ?case by simp
next
  case (T_BNotIn_Rel \<Gamma> l t rel sp1 sp)
  thus ?case by simp
next
  case (T_SetLit_Empty \<Gamma> t sp)
  thus ?case by simp
next
  case (T_SetLit_Cons \<Gamma> e t rest sp)
  thus ?case by simp
next
  case (T_BUnion \<Gamma> l t r sp)
  thus ?case by simp
next
  case (T_BIntersect \<Gamma> l t r sp)
  thus ?case by simp
next
  case (T_BDiff \<Gamma> l t r sp)
  thus ?case by simp
next
  case (T_BIn_Set \<Gamma> l t r sp)
  thus ?case by simp
next
  case (T_BNotIn_Set \<Gamma> l t r sp)
  thus ?case by simp
next
  case (T_FieldAccess \<Gamma> base ename fname ft sp)
  thus ?case by simp
next
  case (T_Index base rel_name \<Gamma> key tk tv sp)
  hence "rel_ref_shape base" "wf_z3 key"
    using peelRelationRefFull_some_imp_rel_ref_shape by auto
  thus ?case by simp
next
  case (T_With \<Gamma> base ename updates sp)
  have wf_base: "wf_z3 base"
    using T_With.IH(1) .
  have wf_fields: "wf_z3_fields updates"
    using T_With.IH(2) by (auto simp: wf_z3_fields_iff)
  show ?case using wf_base wf_fields by simp
next
  case (T_Forall_QAll_Enum \<Gamma> dnm var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QAll_Rel \<Gamma> dnm tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QAll_Enum_Cons \<Gamma> dnm var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QAll_Rel_Cons \<Gamma> dnm tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QNo_Enum \<Gamma> dnm var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QNo_Rel \<Gamma> dnm tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QNo_Enum_Cons \<Gamma> dnm var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QNo_Rel_Cons \<Gamma> dnm tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QExists_Enum \<Gamma> dnm var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QExists_Rel \<Gamma> dnm tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QExists_Enum_Cons \<Gamma> dnm var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QExists_Rel_Cons \<Gamma> dnm tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QSome_Enum \<Gamma> dnm var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QSome_Rel \<Gamma> dnm tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QSome_Enum_Cons \<Gamma> dnm var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QSome_Rel_Cons \<Gamma> dnm tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
qed auto

corollary well_typed_imp_lower_some:
  "expr_has_ty \<Gamma> e t \<Longrightarrow> lower enums e \<noteq> None"
  by (rule wf_z3_imp_lower_some_expr) (rule well_typed_imp_wf_z3)

text \<open>Phase H3 (preservation, leaves). Per-typing-rule preservation
  lemmas for the leaf rules: \<open>T_BoolLit\<close>, \<open>T_IntLit\<close>,
  \<open>T_Ident_Lex\<close>, \<open>T_Ident_State\<close>. The two \<open>Ident\<close> arms use
  \<open>agrees_strict\<close>'s extraction lemmas. The recursive rules
  (\<open>T_Arith\<close> / \<open>T_Cmp_*\<close> / \<open>T_Bool_Bin\<close> / \<open>T_Not\<close> / \<open>T_Neg\<close>)
  follow in a subsequent commit; the umbrella \<open>h3_preservation\<close>
  theorem dispatches per-rule to these.\<close>

lemma h3_pres_BoolLit:
  assumes "lower enums (BoolLitF b sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  have "lower enums (BoolLitF b sp) = Some (BoolLit b sp)" by simp
  with assms(1) have "e' = BoolLit b sp" by simp
  with assms(2) show ?thesis by auto
qed

lemma h3_pres_IntLit:
  assumes "lower enums (IntLitF n sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TInt"
proof -
  have "lower enums (IntLitF n sp) = Some (IntLit n sp)" by simp
  with assms(1) have "e' = IntLit n sp" by simp
  with assms(2) show ?thesis by auto
qed

lemma h3_pres_FloatLit:
  assumes "lower enums (FloatLitF s sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TReal"
proof -
  from assms(1) obtain r where "e' = RealLit r sp"
    by (cases "decimalToRat s") auto
  with assms(2) show ?thesis by auto
qed

lemma h3_pres_Ident_Lex:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = Some t"
      and "lower enums (IdentifierF x sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v t"
proof -
  have "lower enums (IdentifierF x sp) = Some (Ident x sp)" by simp
  with assms(3) have e_eq: "e' = Ident x sp" by simp
  obtain w where ew: "map_of env x = Some w" and wt: "value_has_ty \<Gamma> w t"
    using agrees_strict_env_lookup[OF assms(1,2)] by blast
  have "eval sch st env (Ident x sp) = Some w"
    using ew by (simp add: env_lookup_def)
  with assms(4) e_eq have "v = w" by simp
  thus "value_has_ty \<Gamma> v t" using wt by simp
qed

lemma h3_pres_Ident_State:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = None"
      and "map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t"
      and "lower enums (IdentifierF x sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v t"
proof -
  have "lower enums (IdentifierF x sp) = Some (Ident x sp)" by simp
  with assms(4) have e_eq: "e' = Ident x sp" by simp
  from agrees_strict_state_lookup[OF assms(1,2,3)]
  obtain w where mn: "map_of env x = None"
             and sw: "state_lookup_scalar st x = Some w"
             and wt: "value_has_ty \<Gamma> w t" by blast
  have "eval sch st env (Ident x sp) = Some w"
    using mn sw by (simp add: env_lookup_def)
  with assms(5) e_eq have "v = w" by simp
  thus "value_has_ty \<Gamma> v t" using wt by simp
qed

text \<open>Phase H3 (preservation, recursive cases). For the unary /
  binary typing rules, eval's output type is forced by the lowered
  expression's shape and the H1 partiality-source lemmas, so each
  recursive preservation lemma is standalone (no IH parameter).\<close>

lemma h3_pres_Not:
  assumes "lower enums (UnaryOpF UNot e sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  from assms(1) obtain e_sub where
       sub_low: "lower enums e = Some e_sub"
   and e_eq: "e' = UnNot e_sub sp"
    by (auto split: option.splits)
  from assms(2) e_eq have ev: "eval sch st env (UnNot e_sub sp) = Some v"
    by simp
  then obtain b where "v = VBool (\<not> b)"
    by (cases "eval sch st env e_sub")
       (auto split: ir_value.splits)
  thus "value_has_ty \<Gamma> v TBool" by simp
qed

lemma h3_pres_Neg:
  assumes "lower enums (UnaryOpF UNegate e sp) = Some e'"
      and "eval sch st env e' = Some v"
      and "numeric_ty t"
      and "\<And>e_sub a. lower enums e = Some e_sub \<Longrightarrow> eval sch st env e_sub = Some a \<Longrightarrow> value_has_ty \<Gamma> a t"
  shows "value_has_ty \<Gamma> v t"
proof -
  from assms(1) obtain e_sub where
       sub_low: "lower enums e = Some e_sub"
   and e_eq: "e' = UnNeg e_sub sp"
    by (auto split: option.splits)
  from assms(2) e_eq have ev: "eval sch st env (UnNeg e_sub sp) = Some v"
    by simp
  then obtain a where ea: "eval sch st env e_sub = Some a"
    by (cases "eval sch st env e_sub") auto
  have at: "value_has_ty \<Gamma> a t" using assms(4)[OF sub_low ea] .
  from assms(3) have "t = TInt \<or> t = TReal" by (simp add: numeric_ty_def)
  then show "value_has_ty \<Gamma> v t"
  proof
    assume t: "t = TInt"
    with at have "value_has_ty \<Gamma> a TInt" by simp
    then obtain n where "a = VInt n" by (cases a) auto
    with ea ev have "v = VInt (- n)" by simp
    with t show ?thesis by simp
  next
    assume t: "t = TReal"
    with at have "value_has_ty \<Gamma> a TReal" by simp
    then obtain r where "a = VReal r" by (cases a) auto
    with ea ev have "v = VReal (- r)" by simp
    with t show ?thesis by simp
  qed
qed

lemma h3_pres_Arith:
  assumes "op \<in> {BAdd, BSub, BMul, BDiv}"
      and "lower enums (BinaryOpF op l r sp) = Some e'"
      and "eval sch st env e' = Some v"
      and "numeric_ty t1" and "numeric_ty t2"
      and "\<And>l' a. lower enums l = Some l' \<Longrightarrow> eval sch st env l' = Some a \<Longrightarrow> value_has_ty \<Gamma> a t1"
      and "\<And>r' b. lower enums r = Some r' \<Longrightarrow> eval sch st env r' = Some b \<Longrightarrow> value_has_ty \<Gamma> b t2"
  shows "value_has_ty \<Gamma> v (numeric_join t1 t2)"
proof -
  from assms(1,2) obtain l' r' aop where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = Arith aop l' r' sp"
    by (cases op) (auto split: option.splits)
  from assms(3) e_eq
  have ev: "eval_arith aop (eval sch st env l') (eval sch st env r') = Some v"
    by simp
  show "value_has_ty \<Gamma> v (numeric_join t1 t2)"
  proof (rule eval_arith_preservation[OF ev _ _ assms(4) assms(5)])
    fix a assume "eval sch st env l' = Some a"
    thus "value_has_ty \<Gamma> a t1" using assms(6) l_low by blast
  next
    fix b assume "eval sch st env r' = Some b"
    thus "value_has_ty \<Gamma> b t2" using assms(7) r_low by blast
  qed
qed

lemma h3_pres_Cmp:
  assumes "op \<in> {BEq, BNeq, BLt, BLe, BGt, BGe}"
      and "lower enums (BinaryOpF op l r sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  from assms(1,2) obtain l' r' cop where
       e_eq: "e' = Cmp cop l' r' sp"
    by (cases op) (auto split: option.splits)
  from assms(3) e_eq
  have ev: "eval_cmp cop (eval sch st env l') (eval sch st env r') = Some v"
    by simp
  from eval_cmp_preservation[OF ev] show "value_has_ty \<Gamma> v TBool" .
qed

lemma h3_pres_Bool_Bin:
  assumes "op \<in> {BAnd, BOr, BImplies, BIff}"
      and "lower enums (BinaryOpF op l r sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  from assms(1,2) obtain l' r' bop where
       e_eq: "e' = BoolBin bop l' r' sp"
    by (cases op) (auto split: option.splits)
  from assms(3) e_eq
  have ev: "eval sch st env (BoolBin bop l' r' sp) = Some v"
    by simp
  then obtain a b where "v = VBool (eval_bool_bin bop a b)"
    by (cases "eval sch st env l'"; cases "eval sch st env r'")
       (auto split: ir_value.splits)
  thus "value_has_ty \<Gamma> v TBool" by simp
qed

text \<open>Phase H3 (umbrella). Composes the per-rule preservation
  lemmas via induction on the typing derivation. Each case
  dispatches to the matching standalone preservation lemma.\<close>

theorem h3_preservation:
  assumes "expr_has_ty \<Gamma> e t"
      and "agrees_strict env st \<Gamma>"
      and "lower enums e = Some e'"
      and "eval sch st env e' = Some v"
      and "tc_enums \<Gamma> = enums"
  shows "value_has_ty \<Gamma> v t"
  using assms
proof (induction arbitrary: e' v env rule: expr_has_ty.induct)
  case (T_BoolLit \<Gamma> b sp)
  thus ?case using h3_pres_BoolLit by blast
next
  case (T_IntLit \<Gamma> n sp)
  thus ?case using h3_pres_IntLit by blast
next
  case (T_FloatLit \<Gamma> s sp)
  thus ?case using h3_pres_FloatLit by blast
next
  case (T_Ident_Lex \<Gamma> x t sp)
  thus ?case using h3_pres_Ident_Lex by blast
next
  case (T_Ident_State \<Gamma> x t sp)
  thus ?case using h3_pres_Ident_State by blast
next
  case (T_Arith \<Gamma> l t1 r t2 op sp)
  show ?case
  proof (rule h3_pres_Arith[OF T_Arith.hyps(5) T_Arith.prems(2) T_Arith.prems(3)
                              T_Arith.hyps(3) T_Arith.hyps(4)])
    fix l' a assume "lower enums l = Some l'" and "eval sch st env l' = Some a"
    thus "value_has_ty \<Gamma> a t1"
      using T_Arith.IH(1) T_Arith.prems(1) T_Arith.prems(4) by blast
  next
    fix r' b assume "lower enums r = Some r'" and "eval sch st env r' = Some b"
    thus "value_has_ty \<Gamma> b t2"
      using T_Arith.IH(2) T_Arith.prems(1) T_Arith.prems(4) by blast
  qed
next
  case (T_Cmp_Eq \<Gamma> l t1 r t2 op sp)
  thus ?case using h3_pres_Cmp by blast
next
  case (T_Cmp_Ord \<Gamma> l t1 r t2 op sp)
  thus ?case using h3_pres_Cmp by blast
next
  case (T_Bool_Bin \<Gamma> l r op sp)
  thus ?case using h3_pres_Bool_Bin by blast
next
  case (T_Not \<Gamma> e sp)
  thus ?case using h3_pres_Not by blast
next
  case (T_Neg \<Gamma> e t sp)
  show ?case
  proof (rule h3_pres_Neg[OF T_Neg.prems(2) T_Neg.prems(3) T_Neg.hyps(2)])
    fix e_sub a assume "lower enums e = Some e_sub" and "eval sch st env e_sub = Some a"
    thus "value_has_ty \<Gamma> a t"
      using T_Neg.IH T_Neg.prems(1) T_Neg.prems(4) by blast
  qed
next
  case (T_Let \<Gamma> vexp t1 x body t2 sp)
  from T_Let.prems(2) obtain v' body' where
       v_low: "lower enums vexp = Some v'"
   and body_low: "lower enums body = Some body'"
   and e_eq: "e' = LetIn x v' body' sp"
    by (auto split: option.splits)
  from T_Let.prems(3) e_eq obtain va where
       ev_v: "eval sch st env v' = Some va"
   and ev_body: "eval sch st ((x, va) # env) body' = Some v"
    by (auto split: option.splits)
  have va_ty: "value_has_ty \<Gamma> va t1"
    using T_Let.IH(1)[OF T_Let.prems(1) v_low ev_v T_Let.prems(4)] .
  hence agr_ext: "agrees_strict ((x, va) # env) st
                    (\<Gamma>\<lparr>tc_env := (x, t1) # tc_env \<Gamma>\<rparr>)"
    using T_Let.prems(1) agrees_strict_cons by blast
  have enums_ext: "tc_enums (\<Gamma>\<lparr>tc_env := (x, t1) # tc_env \<Gamma>\<rparr>) = enums"
    using T_Let.prems(4) by simp
  show ?case
    using T_Let.IH(2)[OF agr_ext body_low ev_body enums_ext] by simp
next
  case (T_Prime \<Gamma> e t sp)
  from T_Prime.prems(2) obtain ei where
       inner_low: "lower enums e = Some ei"
   and e_eq: "e' = Prime ei sp"
    by (auto split: option.splits)
  from T_Prime.prems(3) e_eq have ev_inner: "eval sch st env ei = Some v"
    by simp
  show ?case
    using T_Prime.IH[OF T_Prime.prems(1) inner_low ev_inner
                        T_Prime.prems(4)] .
next
  case (T_Pre \<Gamma> e t sp)
  from T_Pre.prems(2) obtain ei where
       inner_low: "lower enums e = Some ei"
   and e_eq: "e' = Pre ei sp"
    by (auto split: option.splits)
  from T_Pre.prems(3) e_eq have ev_inner: "eval sch st env ei = Some v"
    by simp
  show ?case
    using T_Pre.IH[OF T_Pre.prems(1) inner_low ev_inner T_Pre.prems(4)] .
next
  case (T_EnumAccess \<Gamma> en sp1 mem sp)
  from T_EnumAccess.prems(2) have e_eq: "e' = EnumAccess en mem sp"
    by simp
  from T_EnumAccess.prems(3) e_eq obtain d where
       sch_enum: "schema_lookup_enum sch en = Some d"
   and mem_in:  "List.member (enm_members d) mem"
   and v_eq:   "v = VEnum en mem"
    by (auto split: option.splits if_splits)
  show ?case
    by (simp add: v_eq vt_enum)
next
  case (T_Card \<Gamma> x sp1 sp)
  from T_Card.prems(2) have e_eq: "e' = CardRel x sp"
    by simp
  from T_Card.prems(3) e_eq obtain rel_dom where
       rel_some: "state_relation_domain st x = Some rel_dom"
   and v_eq:    "v = VInt (int (length rel_dom))"
    by (auto split: option.splits)
  show ?case
    by (simp add: v_eq vt_int)
next
  case (T_BIn_Rel \<Gamma> l t rel sp1 sp)
  from T_BIn_Rel.prems(2) obtain l' where
       l_low: "lower enums l = Some l'"
   and e_eq: "e' = Member l' rel sp"
    by (auto split: option.splits)
  from T_BIn_Rel.prems(3) e_eq obtain vl rel_dom where
       ev_l:     "eval sch st env l' = Some vl"
   and rel_some: "state_relation_domain st rel = Some rel_dom"
   and v_eq:    "v = VBool (contains_value rel_dom vl)"
    by (auto split: option.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_BNotIn_Rel \<Gamma> l t rel sp1 sp)
  from T_BNotIn_Rel.prems(2) obtain l' where
       l_low: "lower enums l = Some l'"
   and e_eq: "e' = UnNot (Member l' rel sp) sp"
    by (auto split: option.splits)
  from T_BNotIn_Rel.prems(3) e_eq obtain vl rel_dom where
       ev_l:     "eval sch st env l' = Some vl"
   and rel_some: "state_relation_domain st rel = Some rel_dom"
   and v_eq:    "v = VBool (\<not> contains_value rel_dom vl)"
    by (auto split: option.splits ir_value.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_SetLit_Empty \<Gamma> sp t)
  from T_SetLit_Empty.prems(2) have e_eq: "e' = SetEmpty sp"
    by simp
  from T_SetLit_Empty.prems(3) e_eq have v_eq: "v = VSet []"
    by simp
  show ?case
    by (simp add: v_eq vt_set)
next
  case (T_SetLit_Cons \<Gamma> e t rest sp)
  from T_SetLit_Cons.prems(2) obtain eL sL where
       e_low: "lower enums e = Some eL"
   and sl_low: "lowerSetList enums rest sp = Some sL"
   and e_eq: "e' = SetInsert eL sL sp"
    by (auto split: option.splits)
  have rest_low: "lower enums (SetLiteralF rest sp) = Some sL"
    using sl_low by simp
  from T_SetLit_Cons.prems(3) e_eq obtain va rest_vs where
       ev_e:    "eval sch st env eL = Some va"
   and ev_sl:   "eval sch st env sL = Some (VSet rest_vs)"
   and v_eq:   "v = VSet (dedupe_values (va # rest_vs))"
    by (auto split: option.splits ir_value.splits)
  have va_ty: "value_has_ty \<Gamma> va t"
    using T_SetLit_Cons.IH(1)[OF T_SetLit_Cons.prems(1) e_low ev_e
                                  T_SetLit_Cons.prems(4)] .
  have rest_ty: "value_has_ty \<Gamma> (VSet rest_vs) (TSet t)"
    using T_SetLit_Cons.IH(2)[OF T_SetLit_Cons.prems(1) rest_low ev_sl
                                  T_SetLit_Cons.prems(4)] .
  hence rest_all: "\<forall>v \<in> set rest_vs. value_has_ty \<Gamma> v t"
    by (auto elim: value_has_ty_set_cases)
  have all_ty: "\<forall>v \<in> set (va # rest_vs). value_has_ty \<Gamma> v t"
    using va_ty rest_all by simp
  hence "\<forall>v \<in> set (dedupe_values (va # rest_vs)). value_has_ty \<Gamma> v t"
    by (rule dedupe_values_preserves_value_ty)
  thus ?case
    by (simp add: v_eq vt_set)
next
  case (T_BUnion \<Gamma> l t r sp)
  from T_BUnion.prems(2) obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetBin UnionOp l' r' sp"
    by (auto split: option.splits)
  have h: "eval_set_bin UnionOp (eval sch st env l') (eval sch st env r') = Some v"
    using T_BUnion.prems(3) e_eq by simp
  from eval_set_bin_some_imp_set[OF h] obtain lv rv where
       ev_l: "eval sch st env l' = Some (VSet lv)"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
    by blast
  have v_eq: "v = VSet (set_union_values lv rv)"
    using h ev_l ev_r by simp
  have l_all: "\<forall>v \<in> set lv. value_has_ty \<Gamma> v t"
    using T_BUnion.IH(1)[OF T_BUnion.prems(1) l_low ev_l T_BUnion.prems(4)]
    by (auto elim: value_has_ty_set_cases)
  have r_all: "\<forall>v \<in> set rv. value_has_ty \<Gamma> v t"
    using T_BUnion.IH(2)[OF T_BUnion.prems(1) r_low ev_r T_BUnion.prems(4)]
    by (auto elim: value_has_ty_set_cases)
  have "\<forall>v \<in> set (set_union_values lv rv). value_has_ty \<Gamma> v t"
    by (rule set_union_values_preserves_value_ty[OF l_all r_all])
  thus ?case
    by (simp add: v_eq vt_set)
next
  case (T_BIntersect \<Gamma> l t r sp)
  from T_BIntersect.prems(2) obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetBin IntersectOp l' r' sp"
    by (auto split: option.splits)
  have h: "eval_set_bin IntersectOp (eval sch st env l') (eval sch st env r') = Some v"
    using T_BIntersect.prems(3) e_eq by simp
  from eval_set_bin_some_imp_set[OF h] obtain lv rv where
       ev_l: "eval sch st env l' = Some (VSet lv)"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
    by blast
  have v_eq: "v = VSet (set_intersect_values lv rv)"
    using h ev_l ev_r by simp
  have l_all: "\<forall>v \<in> set lv. value_has_ty \<Gamma> v t"
    using T_BIntersect.IH(1)[OF T_BIntersect.prems(1) l_low ev_l
                                 T_BIntersect.prems(4)]
    by (auto elim: value_has_ty_set_cases)
  hence "\<forall>v \<in> set (set_intersect_values lv rv). value_has_ty \<Gamma> v t"
    by (rule set_intersect_values_preserves_value_ty)
  thus ?case
    by (simp add: v_eq vt_set)
next
  case (T_BDiff \<Gamma> l t r sp)
  from T_BDiff.prems(2) obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetBin DiffOp l' r' sp"
    by (auto split: option.splits)
  have h: "eval_set_bin DiffOp (eval sch st env l') (eval sch st env r') = Some v"
    using T_BDiff.prems(3) e_eq by simp
  from eval_set_bin_some_imp_set[OF h] obtain lv rv where
       ev_l: "eval sch st env l' = Some (VSet lv)"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
    by blast
  have v_eq: "v = VSet (set_diff_values lv rv)"
    using h ev_l ev_r by simp
  have l_all: "\<forall>v \<in> set lv. value_has_ty \<Gamma> v t"
    using T_BDiff.IH(1)[OF T_BDiff.prems(1) l_low ev_l T_BDiff.prems(4)]
    by (auto elim: value_has_ty_set_cases)
  hence "\<forall>v \<in> set (set_diff_values lv rv). value_has_ty \<Gamma> v t"
    by (rule set_diff_values_preserves_value_ty)
  thus ?case
    by (simp add: v_eq vt_set)
next
  case (T_BIn_Set \<Gamma> l t r sp)
  from T_BIn_Set.prems(2) T_BIn_Set.hyps(3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetMember l' r' sp"
    by (cases r) (auto split: option.splits)
  from T_BIn_Set.prems(3) e_eq obtain vl rv where
       ev_l: "eval sch st env l' = Some vl"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
   and v_eq: "v = VBool (contains_value rv vl)"
    by (auto split: option.splits ir_value.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_BNotIn_Set \<Gamma> l t r sp)
  from T_BNotIn_Set.prems(2) T_BNotIn_Set.hyps(3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = UnNot (SetMember l' r' sp) sp"
    by (cases r) (auto split: option.splits)
  from T_BNotIn_Set.prems(3) e_eq obtain vl rv where
       ev_l: "eval sch st env l' = Some vl"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
   and v_eq: "v = VBool (\<not> contains_value rv vl)"
    by (auto split: option.splits ir_value.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_FieldAccess \<Gamma> base ename fname ft sp)
  from T_FieldAccess.prems(2) obtain b' where
       base_low: "lower enums base = Some b'"
   and e_eq: "e' = FieldAccess b' fname sp"
    by (auto split: option.splits)
  from T_FieldAccess.prems(3) e_eq obtain vb where
       ev_base: "eval sch st env b' = Some vb"
   and v_eq:   "value_field_lookup st vb fname = Some v"
    by (auto split: option.splits)
  have vb_ty: "value_has_ty \<Gamma> vb (TEntity ename)"
    using T_FieldAccess.IH[OF T_FieldAccess.prems(1) base_low ev_base
                              T_FieldAccess.prems(4)] .
  show ?case
    using agrees_strict_field_lookup[OF T_FieldAccess.prems(1) vb_ty
                                        T_FieldAccess.hyps(2) v_eq] .
next
  case (T_Index base rel_name \<Gamma> key tk tv sp)
  from T_Index.prems(2) obtain base' key' where
       base_low: "lower enums base = Some base'"
   and key_low: "lower enums key = Some key'"
   and e_eq: "e' = IndexRel base' key' sp"
    by (auto split: option.splits)
  have peel': "peel_relation_ref base' = Some rel_name"
    using peelRelationRefFull_lower[OF T_Index.hyps(1) base_low] .
  from T_Index.prems(3) e_eq peel' obtain kv where
       ev_key: "eval sch st env key' = Some kv"
   and v_lookup: "state_lookup_key st rel_name kv = Some v"
    by (auto split: option.splits)
  show ?case
    using agrees_strict_relation_lookup[OF T_Index.prems(1)
                                           T_Index.hyps(3) v_lookup] .
next
  case (T_With \<Gamma> base ename updates sp)
  from T_With.prems(2) obtain base' where
       base_low: "lower enums base = Some base'"
   and lwa: "lower_with_assigns enums updates base' sp = Some e'"
    by (auto split: option.splits)
  from lower_with_assigns_eval_implies_base_eval[OF lwa T_With.prems(3)]
  obtain bv where ev_base: "eval sch st env base' = Some bv" by blast
  have bv_ty: "value_has_ty \<Gamma> bv (TEntity ename)"
    using T_With.IH(1)[OF T_With.prems(1) base_low ev_base T_With.prems(4)] .
  have updates_typed:
    "\<forall>fld vhd sphd. FieldAssignFull fld vhd sphd \<in> set updates
        \<longrightarrow> (\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft
                \<and> (\<forall>vhd' vhd_val. lower enums vhd = Some vhd'
                      \<longrightarrow> eval sch st env vhd' = Some vhd_val
                      \<longrightarrow> value_has_ty \<Gamma> vhd_val ft))"
    using T_With.IH(2) T_With.prems(1) T_With.prems(4)
    by blast
  show ?case
    using lower_with_assigns_preserves_entity[OF lwa T_With.prems(3)
                                                 ev_base bv_ty updates_typed] .
next
  case (T_Forall_QAll_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QAll_Enum.hyps(1) T_Forall_QAll_Enum.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QAll_Enum.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = ForallEnum var dnm body' sp"
    by (auto split: option.splits)
  from T_Forall_QAll_Enum.prems(3) e_eq obtain d where
       sch_enum: "schema_lookup_enum sch dnm = Some d"
   and ev_fe:   "eval_forall_enum sch st env var dnm
                    (enm_members d) body' = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_enum_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QAll_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QAll_Rel.hyps(1) T_Forall_QAll_Rel.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QAll_Rel.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = ForallRel var dnm body' sp"
    by (auto split: option.splits)
  from T_Forall_QAll_Rel.prems(3) e_eq obtain rel_dom where
       rel_some: "state_relation_domain st dnm = Some rel_dom"
   and ev_fr:   "eval_forall_rel sch st env var rel_dom body' = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_rel_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QAll_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QAll_Enum_Cons.hyps(1) T_Forall_QAll_Enum_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QAll_Enum_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs) body' sp
                     = Some inner"
   and e_eq:     "e' = ForallEnum var dnm inner sp"
    by (auto split: option.splits)
  from T_Forall_QAll_Enum_Cons.prems(3) e_eq obtain d where
       sch_enum: "schema_lookup_enum sch dnm = Some d"
   and ev_fe:    "eval_forall_enum sch st env var dnm
                    (enm_members d) inner = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_enum_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QAll_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QAll_Rel_Cons.hyps(1) T_Forall_QAll_Rel_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QAll_Rel_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs) body' sp
                     = Some inner"
   and e_eq:     "e' = ForallRel var dnm inner sp"
    by (auto split: option.splits)
  from T_Forall_QAll_Rel_Cons.prems(3) e_eq obtain rel_dom where
       rel_some: "state_relation_domain st dnm = Some rel_dom"
   and ev_fr:    "eval_forall_rel sch st env var rel_dom inner = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_rel_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QNo_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QNo_Enum.hyps(1) T_Forall_QNo_Enum.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QNo_Enum.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = ForallEnum var dnm (UnNot body' sp) sp"
    by (auto split: option.splits)
  from T_Forall_QNo_Enum.prems(3) e_eq obtain d where
       sch_enum: "schema_lookup_enum sch dnm = Some d"
   and ev_fe:    "eval_forall_enum sch st env var dnm
                    (enm_members d) (UnNot body' sp) = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_enum_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QNo_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QNo_Rel.hyps(1) T_Forall_QNo_Rel.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QNo_Rel.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = ForallRel var dnm (UnNot body' sp) sp"
    by (auto split: option.splits)
  from T_Forall_QNo_Rel.prems(3) e_eq obtain rel_dom where
       rel_some: "state_relation_domain st dnm = Some rel_dom"
   and ev_fr:    "eval_forall_rel sch st env var rel_dom
                    (UnNot body' sp) = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_rel_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QNo_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QNo_Enum_Cons.hyps(1) T_Forall_QNo_Enum_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QNo_Enum_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = ForallEnum var dnm inner sp"
    by (auto split: option.splits)
  from T_Forall_QNo_Enum_Cons.prems(3) e_eq obtain d where
       sch_enum: "schema_lookup_enum sch dnm = Some d"
   and ev_fe:    "eval_forall_enum sch st env var dnm
                    (enm_members d) inner = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_enum_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QNo_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QNo_Rel_Cons.hyps(1) T_Forall_QNo_Rel_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QNo_Rel_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = ForallRel var dnm inner sp"
    by (auto split: option.splits)
  from T_Forall_QNo_Rel_Cons.prems(3) e_eq obtain rel_dom where
       rel_some: "state_relation_domain st dnm = Some rel_dom"
   and ev_fr:    "eval_forall_rel sch st env var rel_dom inner = Some v"
    by (auto split: option.splits)
  hence "\<exists>b. v = VBool b"
    using eval_forall_rel_some_imp_bool by blast
  thus ?case by auto
next
  case (T_Forall_QExists_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QExists_Enum.hyps(1) T_Forall_QExists_Enum.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QExists_Enum.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = UnNot
                        (ForallEnum var dnm (UnNot body' sp) sp) sp"
    by (auto split: option.splits)
  from T_Forall_QExists_Enum.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QExists_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QExists_Rel.hyps(1) T_Forall_QExists_Rel.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QExists_Rel.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = UnNot
                        (ForallRel var dnm (UnNot body' sp) sp) sp"
    by (auto split: option.splits)
  from T_Forall_QExists_Rel.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QExists_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QExists_Enum_Cons.hyps(1) T_Forall_QExists_Enum_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QExists_Enum_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = UnNot (ForallEnum var dnm inner sp) sp"
    by (auto split: option.splits)
  from T_Forall_QExists_Enum_Cons.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QExists_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QExists_Rel_Cons.hyps(1) T_Forall_QExists_Rel_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QExists_Rel_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = UnNot (ForallRel var dnm inner sp) sp"
    by (auto split: option.splits)
  from T_Forall_QExists_Rel_Cons.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QSome_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QSome_Enum.hyps(1) T_Forall_QSome_Enum.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QSome_Enum.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = UnNot
                        (ForallEnum var dnm (UnNot body' sp) sp) sp"
    by (auto split: option.splits)
  from T_Forall_QSome_Enum.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QSome_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QSome_Rel.hyps(1) T_Forall_QSome_Rel.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QSome_Rel.prems(2) obtain body' where
       body_low: "lower enums body = Some body'"
   and e_eq:    "e' = UnNot
                        (ForallRel var dnm (UnNot body' sp) sp) sp"
    by (auto split: option.splits)
  from T_Forall_QSome_Rel.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QSome_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  have in_enums: "string_in_list dnm enums"
    using T_Forall_QSome_Enum_Cons.hyps(1) T_Forall_QSome_Enum_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QSome_Enum_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = UnNot (ForallEnum var dnm inner sp) sp"
    by (auto split: option.splits)
  from T_Forall_QSome_Enum_Cons.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Forall_QSome_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  have not_in_enums: "\<not> string_in_list dnm enums"
    using T_Forall_QSome_Rel_Cons.hyps(1) T_Forall_QSome_Rel_Cons.prems(4)
    by (simp add: string_in_list_iff_in_set)
  with T_Forall_QSome_Rel_Cons.prems(2) obtain body' inner where
       body_low:  "lower enums body = Some body'"
   and inner_low: "lower_forall_bindings enums (b2 # rest_bs)
                     (UnNot body' sp) sp = Some inner"
   and e_eq:     "e' = UnNot (ForallRel var dnm inner sp) sp"
    by (auto split: option.splits)
  from T_Forall_QSome_Rel_Cons.prems(3) e_eq obtain b where
     "v = VBool (\<not> b)"
    by (auto split: option.splits ir_value.splits)
  thus ?case by auto
qed

text \<open>Phase H3 capstone. The full Cat-H story in one statement:
  every well-typed expression lowers successfully, and any
  eval-defined result of the lowered form is typed at the declared
  type. Composes \<open>well_typed_imp_lower_some\<close> (the progress half,
  coming from \<open>well_typed_imp_wf_z3\<close> \<circ>
  \<open>wf_z3_imp_lower_some_expr\<close>) with \<open>h3_preservation\<close> (the
  preservation half, by induction on the typing derivation across
  all 34 in-fragment rules).\<close>

theorem cat_h_progress_and_preservation:
  assumes "expr_has_ty \<Gamma> e t"
      and "agrees_strict env st \<Gamma>"
      and "tc_enums \<Gamma> = enums"
  shows "\<exists>e'. lower enums e = Some e'
              \<and> (\<forall>v. eval sch st env e' = Some v \<longrightarrow> value_has_ty \<Gamma> v t)"
proof -
  from well_typed_imp_lower_some[OF assms(1)] have ne: "lower enums e \<noteq> None" .
  then obtain e' where e'_eq: "lower enums e = Some e'"
    by (auto simp: not_None_eq)
  show ?thesis
  proof (intro exI conjI allI impI)
    show "lower enums e = Some e'" by (rule e'_eq)
  next
    fix v assume "eval sch st env e' = Some v"
    thus "value_has_ty \<Gamma> v t"
      using assms e'_eq h3_preservation by metis
  qed
qed


end
