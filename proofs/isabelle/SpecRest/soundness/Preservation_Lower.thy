theory Preservation_Lower
  imports Preservation_Wf
begin

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

lemma lower_eq_comp_some:
  assumes "lower enums l \<noteq> None" and "lower enums p \<noteq> None"
  shows "lower enums
           (BinaryOpF BEq l (SetComprehensionF var (IdentifierF dnm sp2) p sp3) sp)
           \<noteq> None"
  using assms by (auto split: option.splits)

lemma lower_in_comp_some:
  assumes "lower enums l \<noteq> None" and "lower enums p \<noteq> None"
  shows "lower enums
           (BinaryOpF BIn l (SetComprehensionF var (IdentifierF dnm sp2) p sp3) sp)
           \<noteq> None"
  using assms by (auto split: option.splits)

lemma lower_binop_some:
  assumes l: "wf_z3 l \<Longrightarrow> lower enums l \<noteq> None"
      and r: "wf_z3 r \<Longrightarrow> lower enums r \<noteq> None"
      and wf: "wf_z3 (BinaryOpF op l r sp)"
      and ncr: "\<nexists>var dnm sp2 p sp3.
                  r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "lower enums (BinaryOpF op l r sp) \<noteq> None"
proof -
  have inout: "lower enums (BinaryOpF op l r sp) \<noteq> None"
    if opc: "op = BIn \<or> op = BNotIn"
  proof -
    have wlrd: "wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r)"
    proof (rule disjE[OF opc])
      assume oi: "op = BIn"
      show ?thesis using wf[unfolded oi wf_z3_BIn_noncomp[OF ncr]] .
    next
      assume oi: "op = BNotIn"
      show ?thesis using wf[unfolded oi] by simp
    qed
    from wlrd have wl: "wf_z3 l"
        and rd: "(\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r" by simp_all
    from l wl obtain l' where l': "lower enums l = Some l'" by blast
    from rd show ?thesis
    proof
      assume "\<exists>rel s. r = IdentifierF rel s"
      then obtain rel s where "r = IdentifierF rel s" by blast
      thus ?thesis using l' opc by auto
    next
      assume wr: "wf_z3 r"
      with r obtain r' where r': "lower enums r = Some r'" by blast
      from opc show ?thesis
      proof
        assume oi: "op = BIn"
        show ?thesis using l' r' ncr by (cases r) (auto simp: oi split: option.splits)
      next
        assume oi: "op = BNotIn"
        show ?thesis using l' r' ncr by (cases r) (auto simp: oi split: option.splits)
      qed
    qed
  qed
  show ?thesis
  proof (cases op)
    case BEq
    from wf[unfolded BEq wf_z3_BEq_noncomp[OF ncr]] have wl: "wf_z3 l" and wr: "wf_z3 r"
      by simp_all
    from l wl obtain l' where l': "lower enums l = Some l'" by blast
    from r wr obtain r' where r': "lower enums r = Some r'" by blast
    from l' r' BEq show ?thesis
      by (cases r) (auto simp: lower_beq_dom_or_none_def split: option.splits prod.splits)
  next
    case BIn
    thus ?thesis using inout by blast
  next
    case BNotIn
    thus ?thesis using inout by blast
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
    proof (cases "\<exists>var dnm sp2 p sp3.
                    r = SetComprehensionF var (IdentifierF dnm sp2) p sp3")
      case True
      then obtain var dnm sp2 p sp3
        where rc: "r = SetComprehensionF var (IdentifierF dnm sp2) p sp3" by blast
      have opeq: "op = BEq \<or> op = BIn"
        using less.prems[unfolded BinaryOpF] rc by (cases op) auto
      have wlp: "wf_z3 l \<and> wf_z3 p"
      proof (rule disjE[OF opeq])
        assume "op = BEq"
        thus ?thesis using less.prems[unfolded BinaryOpF] rc by simp
      next
        assume "op = BIn"
        thus ?thesis using less.prems[unfolded BinaryOpF] rc by simp
      qed
      from wlp have wl: "wf_z3 l" and wp: "wf_z3 p" by simp_all
      have szp: "size p < size e" using BinaryOpF rc by simp
      from l wl have lne: "lower enums l \<noteq> None" by blast
      from sub[OF szp] wp have pne: "lower enums p \<noteq> None" by blast
      from opeq show "lower enums (BinaryOpF op l r s) \<noteq> None"
      proof
        assume oe: "op = BEq"
        show "lower enums (BinaryOpF op l r s) \<noteq> None"
          unfolding oe rc by (rule lower_eq_comp_some[OF lne pne])
      next
        assume oe: "op = BIn"
        show "lower enums (BinaryOpF op l r s) \<noteq> None"
          unfolding oe rc by (rule lower_in_comp_some[OF lne pne])
      qed
    next
      case False
      hence ncr: "\<nexists>var dnm sp2 p sp3.
                    r = SetComprehensionF var (IdentifierF dnm sp2) p sp3" by blast
      show "lower enums (BinaryOpF op l r s) \<noteq> None"
        by (rule lower_binop_some[OF l r less.prems[unfolded BinaryOpF] ncr])
    qed
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
  next
    case (ConstructorF nm fas s)
    have pe: "\<And>fld vv fsp. FieldAssignFull fld vv fsp \<in> set fas
                \<Longrightarrow> wf_z3 vv \<Longrightarrow> lower enums vv \<noteq> None"
    proof -
      fix fld vv fsp
      assume m: "FieldAssignFull fld vv fsp \<in> set fas" and rv: "wf_z3 vv"
      have "size vv < size (FieldAssignFull fld vv fsp)" by simp
      also have "\<dots> \<le> size_list size fas"
        by (rule size_list_estimation'[OF m order_refl])
      also have "\<dots> < size e" using ConstructorF by simp
      finally have "size vv < size e" .
      thus "lower enums vv \<noteq> None" using sub rv by blast
    qed
    have wf: "wf_z3_fields fas" using less.prems ConstructorF by simp
    have "lower_with_assigns enums fas (EntityBase nm s) s \<noteq> None"
      by (rule lower_with_assigns_wf_some[OF pe wf])
    thus ?thesis unfolding ConstructorF by simp
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


end
