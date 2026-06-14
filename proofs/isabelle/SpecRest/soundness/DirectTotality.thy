theory DirectTotality
  imports
    Preservation_Wf
    SpecRest_Core.Translate
begin

text \<open>Totality: every \<open>wf_z3\<close> expression translates directly.
  Independent of the soundness induction - this is the progress half
  of the capstone.\<close>
section \<open>Totality: wf_z3 inputs always translate directly\<close>

lemma wf_z3_list_member:
  "wf_z3_list es \<Longrightarrow> x \<in> set es \<Longrightarrow> wf_z3 x"
  by (induction es) auto

lemma wf_z3_entries_member:
  "wf_z3_entries ents \<Longrightarrow> MapEntryFull k v sp3 \<in> set ents \<Longrightarrow> wf_z3 k \<and> wf_z3 v"
proof (induction ents)
  case Nil
  thus ?case by simp
next
  case (Cons en ents')
  obtain k2 v2 s2 where "en = MapEntryFull k2 v2 s2" by (cases en) auto
  thus ?case using Cons by auto
qed

lemma translate_forall_step_some:
  "is_ident_dom b \<Longrightarrow> \<exists>t. translate_forall_step enums b body = Some t"
  by (cases b rule: is_ident_dom.cases) auto

lemma dfb_some_of_wf:
  "wf_z3_bindings bs \<Longrightarrow> \<exists>r. translate_forall_bindings enums bs body = Some r"
proof (induction bs)
  case Nil
  thus ?case by simp
next
  case (Cons b bs')
  show ?case
  proof (cases bs')
    case Nil
    thus ?thesis using Cons.prems translate_forall_step_some by auto
  next
    case (Cons b2 bs'')
    hence ib: "is_ident_dom b" and wr: "wf_z3_bindings bs'"
      using Cons.prems by auto
    obtain inner where hi: "translate_forall_bindings enums bs' body = Some inner"
      using Cons.IH[OF wr] by blast
    obtain t where "translate_forall_step enums b inner = Some t"
      using translate_forall_step_some[OF ib] by blast
    thus ?thesis using \<open>bs' = b2 # bs''\<close> hi by auto
  qed
qed

lemma directSetList_some:
  "(\<And>x. x \<in> set es \<Longrightarrow> \<exists>tx. translate enums x = Some tx)
     \<Longrightarrow> \<exists>ts. translateSetList enums es = Some ts"
proof (induction es)
  case Nil
  thus ?case by simp
next
  case (Cons a es')
  obtain at2 where ha: "translate enums a = Some at2"
    using Cons.prems[of a] by auto
  have "\<exists>ts. translateSetList enums es' = Some ts"
  proof (rule Cons.IH)
    show "\<And>x. x \<in> set es' \<Longrightarrow> \<exists>tx. translate enums x = Some tx"
      using Cons.prems by auto
  qed
  then obtain st2 where hs: "translateSetList enums es' = Some st2" by blast
  show ?case using ha hs by simp
qed

lemma directSeqList_some:
  "(\<And>x. x \<in> set es \<Longrightarrow> \<exists>tx. translate enums x = Some tx)
     \<Longrightarrow> \<exists>ts. translateSeqList enums es = Some ts"
proof (induction es)
  case Nil
  thus ?case by simp
next
  case (Cons a es')
  obtain at2 where ha: "translate enums a = Some at2"
    using Cons.prems[of a] by auto
  have "\<exists>ts. translateSeqList enums es' = Some ts"
  proof (rule Cons.IH)
    show "\<And>x. x \<in> set es' \<Longrightarrow> \<exists>tx. translate enums x = Some tx"
      using Cons.prems by auto
  qed
  then obtain st2 where hs: "translateSeqList enums es' = Some st2" by blast
  show ?case using ha hs by simp
qed

lemma directMapEntries_some:
  "(\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents \<Longrightarrow> \<exists>tk. translate enums k = Some tk)
     \<Longrightarrow> (\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents \<Longrightarrow> \<exists>tv. translate enums v = Some tv)
     \<Longrightarrow> \<exists>mt. translateMapEntries enums ents = Some mt"
proof (induction ents)
  case Nil
  thus ?case by simp
next
  case (Cons en ents')
  obtain k v sp3 where en: "en = MapEntryFull k v sp3" by (cases en) auto
  obtain kt where hk: "translate enums k = Some kt"
    using Cons.prems(1)[of k v sp3] en by auto
  obtain vt where hv: "translate enums v = Some vt"
    using Cons.prems(2)[of k v sp3] en by auto
  have "\<exists>mt. translateMapEntries enums ents' = Some mt"
  proof (rule Cons.IH)
    show "\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> \<exists>tk. translate enums k = Some tk"
      using Cons.prems(1) by auto
    show "\<And>k v sp3. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> \<exists>tv. translate enums v = Some tv"
      using Cons.prems(2) by auto
  qed
  then obtain mt where hm: "translateMapEntries enums ents' = Some mt" by blast
  show ?case using en hk hv hm by simp
qed

lemma translate_with_assigns_some:
  "(\<And>fld v sp3. FieldAssignFull fld v sp3 \<in> set fas
      \<Longrightarrow> \<exists>tv. translate enums v = Some tv)
     \<Longrightarrow> \<exists>ft. translate_with_assigns enums fas bt = Some ft"
proof (induction fas arbitrary: bt)
  case Nil
  thus ?case by simp
next
  case (Cons fa fas')
  obtain fld v sp3 where fa: "fa = FieldAssignFull fld v sp3" by (cases fa) auto
  obtain vt where hv: "translate enums v = Some vt"
    using Cons.prems[of fld v sp3] fa by auto
  have "\<exists>ft. translate_with_assigns enums fas' (TWithRec bt fld vt) = Some ft"
  proof (rule Cons.IH)
    show "\<And>fld v sp3. FieldAssignFull fld v sp3 \<in> set fas'
            \<Longrightarrow> \<exists>tv. translate enums v = Some tv"
      using Cons.prems by auto
  qed
  thus ?case using fa hv by auto
qed

lemma wf_z3_imp_tfd_some:
  "wf_z3 e \<Longrightarrow> \<exists>t. translate enums e = Some t"
proof (induction e rule: measure_induct_rule[where f = size])
  case (less e)
  show ?case
  proof (cases e)
    case (FloatLitF d sp2)
    thus ?thesis using less.prems by (cases "decimalToRat d") auto
  next
    case (PrimeF c sp2)
    have s: "size c < size e" using PrimeF by simp
    show ?thesis using less.prems PrimeF less.IH[OF s] by auto
  next
    case (PreF c sp2)
    have s: "size c < size e" using PreF by simp
    show ?thesis using less.prems PreF less.IH[OF s] by auto
  next
    case (SomeWrapF c sp2)
    have s: "size c < size e" using SomeWrapF by simp
    show ?thesis using less.prems SomeWrapF less.IH[OF s] by auto
  next
    case (MatchesF c pat sp2)
    have s: "size c < size e" using MatchesF by simp
    show ?thesis using less.prems MatchesF less.IH[OF s] by auto
  next
    case (FieldAccessF c f sp2)
    have s: "size c < size e" using FieldAccessF by simp
    show ?thesis using less.prems FieldAccessF less.IH[OF s] by auto
  next
    case (EnumAccessF base mem sp2)
    thus ?thesis using less.prems by auto
  next
    case (IndexF base key sp2)
    have sk: "size key < size e" using IndexF by simp
    have rrs: "rel_ref_shape base" and wk: "wf_z3 key"
      using less.prems IndexF by simp_all
    obtain kt where hk: "translate enums key = Some kt"
      using less.IH[OF sk wk] by blast
    show ?thesis
    proof (cases base)
      case (IdentifierF x sp3)
      thus ?thesis using IndexF hk by auto
    next
      case (PreF b sp3)
      then obtain x sp4 where "b = IdentifierF x sp4"
        using rrs by (cases "identName b") (auto dest!: identName_SomeD)
      thus ?thesis using IndexF hk PreF by auto
    next
      case (PrimeF b sp3)
      then obtain x sp4 where "b = IdentifierF x sp4"
        using rrs by (cases "identName b") (auto dest!: identName_SomeD)
      thus ?thesis using IndexF hk PrimeF by auto
    qed (use IndexF rrs in auto)
  next
    case (IfF c a b sp2)
    have sc: "size c < size e" and sa: "size a < size e" and sb: "size b < size e"
      using IfF by simp_all
    obtain ct where "translate enums c = Some ct"
      using less.IH[OF sc] less.prems IfF by auto
    moreover obtain at2 where "translate enums a = Some at2"
      using less.IH[OF sa] less.prems IfF by auto
    moreover obtain bt2 where "translate enums b = Some bt2"
      using less.IH[OF sb] less.prems IfF by auto
    ultimately show ?thesis using IfF by auto
  next
    case (LetF x v body sp2)
    have sv: "size v < size e" and sb: "size body < size e" using LetF by simp_all
    obtain vt where "translate enums v = Some vt"
      using less.IH[OF sv] less.prems LetF by auto
    moreover obtain bt where "translate enums body = Some bt"
      using less.IH[OF sb] less.prems LetF by auto
    ultimately show ?thesis using LetF by auto
  next
    case (UnaryOpF op2 c sp2)
    have s: "size c < size e" using UnaryOpF by simp
    show ?thesis
    proof (cases op2)
      case UNot
      thus ?thesis using less.prems UnaryOpF less.IH[OF s] by auto
    next
      case UNegate
      thus ?thesis using less.prems UnaryOpF less.IH[OF s] by auto
    next
      case UCardinality
      then obtain x s3 where "c = IdentifierF x s3" using less.prems UnaryOpF by auto
      thus ?thesis using UnaryOpF UCardinality by auto
    next
      case UPower
      thus ?thesis using less.prems UnaryOpF by simp
    qed
  next
    case (QuantifierF k bs body sp2)
    have s: "size body < size e" using QuantifierF by simp
    have wb: "wf_z3_bindings bs" and wbody: "wf_z3 body"
      using less.prems QuantifierF by simp_all
    obtain bt where hb: "translate enums body = Some bt"
      using less.IH[OF s wbody] by blast
    show ?thesis
    proof (cases k)
      case QAll
      thus ?thesis using QuantifierF hb dfb_some_of_wf[OF wb] by auto
    next
      case QNo
      thus ?thesis using QuantifierF hb dfb_some_of_wf[OF wb] by auto
    next
      case QSome
      thus ?thesis using QuantifierF hb dfb_some_of_wf[OF wb] by auto
    next
      case QExists
      thus ?thesis using QuantifierF hb dfb_some_of_wf[OF wb] by auto
    qed
  next
    case (ConstructorF name fas sp2)
    have "\<exists>ft. translate_with_assigns enums fas (TEntityBase name) = Some ft"
    proof (rule translate_with_assigns_some)
      fix fld v sp3 assume m: "FieldAssignFull fld v sp3 \<in> set fas"
      have "size v < size e"
        using ConstructorF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "wf_z3 v"
        using less.prems ConstructorF m by (fastforce simp: wf_z3_fields_iff)
      ultimately show "\<exists>tv. translate enums v = Some tv"
        using less.IH by blast
    qed
    thus ?thesis using ConstructorF by simp
  next
    case (WithF base upds sp2)
    have sbase: "size base < size e" using WithF by simp
    obtain bt where hb: "translate enums base = Some bt"
      using less.IH[OF sbase] less.prems WithF by auto
    have "\<exists>ft. translate_with_assigns enums upds bt = Some ft"
    proof (rule translate_with_assigns_some)
      fix fld v sp3 assume m: "FieldAssignFull fld v sp3 \<in> set upds"
      have "size v < size e"
        using WithF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "wf_z3 v"
        using less.prems WithF m by (fastforce simp: wf_z3_fields_iff)
      ultimately show "\<exists>tv. translate enums v = Some tv"
        using less.IH by blast
    qed
    thus ?thesis using WithF hb by auto
  next
    case (SetLiteralF elems sp2)
    have "\<exists>ts. translateSetList enums elems = Some ts"
    proof (rule directSetList_some)
      fix x assume m: "x \<in> set elems"
      have "size x < size e"
        using SetLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "wf_z3 x"
        using wf_z3_list_member less.prems SetLiteralF m by simp
      ultimately show "\<exists>tx. translate enums x = Some tx"
        using less.IH by blast
    qed
    thus ?thesis using SetLiteralF by simp
  next
    case (SeqLiteralF elems sp2)
    have "\<exists>ts. translateSeqList enums elems = Some ts"
    proof (rule directSeqList_some)
      fix x assume m: "x \<in> set elems"
      have "size x < size e"
        using SeqLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "wf_z3 x"
        using wf_z3_list_member less.prems SeqLiteralF m by simp
      ultimately show "\<exists>tx. translate enums x = Some tx"
        using less.IH by blast
    qed
    thus ?thesis using SeqLiteralF by simp
  next
    case (MapLiteralF entries sp2)
    have "\<exists>mt. translateMapEntries enums entries = Some mt"
    proof (rule directMapEntries_some)
      fix k v sp3 assume m: "MapEntryFull k v sp3 \<in> set entries"
      have sk: "size k < size e" and sv: "size v < size e"
        using MapLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp_all
      have wk: "wf_z3 k" and wv: "wf_z3 v"
        using wf_z3_entries_member[OF _ m] less.prems MapLiteralF by simp_all
      show "\<exists>tk. translate enums k = Some tk"
        using less.IH[OF sk wk] by blast
      show "\<exists>tv. translate enums v = Some tv"
        using less.IH[OF sv wv] by blast
    qed
    thus ?thesis using MapLiteralF by simp
  next
    case (BinaryOpF op2 l2 r2 sp2)
    have sl: "size l2 < size e" and sr: "size r2 < size e" using BinaryOpF by simp_all
    show ?thesis
    proof (cases op2)
      case BEq
      show ?thesis
      proof (cases "translate_beq_dom_or_none l2 r2")
        case (Some dt)
        thus ?thesis using BinaryOpF BEq by auto
      next
        case None
        have dnone: "dom_arg l2 = None \<or> dom_arg r2 = None"
          using None by (auto simp: translate_beq_dom_or_none_def split: option.splits)
        show ?thesis
        proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
          case True
          then obtain cvar dnm s2 cpred s3
            where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
          have spred: "size cpred < size e" using BinaryOpF req by simp
          have wl: "wf_z3 l2" and wp: "wf_z3 cpred"
            using less.prems BinaryOpF BEq req by simp_all
          obtain lt where hl: "translate enums l2 = Some lt"
            using less.IH[OF sl wl] by blast
          obtain pt where hp: "translate enums cpred = Some pt"
            using less.IH[OF spred wp] by blast
          show ?thesis using BinaryOpF BEq None req hl hp by auto
        next
          case False
          hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
            by blast
          have wl: "wf_z3 l2" and wr: "wf_z3 r2"
            using less.prems BinaryOpF BEq
            by (simp_all add: comp_pred_or_self_noncomp[OF nc])
          obtain lt where hl: "translate enums l2 = Some lt"
            using less.IH[OF sl wl] by blast
          obtain rt where hr: "translate enums r2 = Some rt"
            using less.IH[OF sr wr] by blast
          show ?thesis
          proof (cases "rel_insert_parts BEq l2 r2")
            case (Some q)
            obtain rel kn vn where ri: "rel_insert_parts BEq l2 r2 = Some (rel, kn, vn)"
              using Some by (cases q) auto
            have tbn: "translate_beq_dom_or_none l2 r2 = None"
              using dnone by (auto simp: translate_beq_dom_or_none_def split: option.splits)
            show ?thesis using BinaryOpF BEq tbn ri by simp
          next
            case None
            note riNone = this
            show ?thesis
            proof (cases "range_arg r2")
              case (Some rel)
              have tbn: "translate_beq_dom_or_none l2 r2 = None"
                using dnone by (auto simp: translate_beq_dom_or_none_def split: option.splits)
              show ?thesis using BinaryOpF BEq tbn riNone Some hl by simp
            next
              case None
              show ?thesis
                using BinaryOpF BEq translate_BEq_noncomp[OF nc dnone riNone None] hl hr by auto
            qed
          qed
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
        have wl: "wf_z3 l2" and wp: "wf_z3 cpred"
          using less.prems BinaryOpF BIn req by simp_all
        obtain lt where hl: "translate enums l2 = Some lt"
          using less.IH[OF sl wl] by blast
        obtain pt where hp: "translate enums cpred = Some pt"
          using less.IH[OF spred wp] by blast
        show ?thesis using BinaryOpF BIn req hl hp by auto
      next
        case False
        hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
          by blast
        have wl: "wf_z3 l2"
          using less.prems BinaryOpF BIn by simp
        obtain lt where hl: "translate enums l2 = Some lt"
          using less.IH[OF sl wl] by blast
        show ?thesis
        proof (cases "\<exists>rel rsp. r2 = IdentifierF rel rsp")
          case True
          thus ?thesis using BinaryOpF BIn hl by auto
        next
          case False
          have wr: "wf_z3 r2"
            using less.prems BinaryOpF BIn False
            by (auto simp: comp_pred_or_self_noncomp[OF nc])
          obtain rt where hr: "translate enums r2 = Some rt"
            using less.IH[OF sr wr] by blast
          show ?thesis
            using hl hr translate_BIn_noncomp[OF nc]
            by (cases r2)
               (auto simp: BinaryOpF BIn del: translate.simps split: option.splits)
        qed
      qed
    next
      case BNotIn
      have beq: "translate enums e
                   = (case translate enums l2 of
                        None \<Rightarrow> None
                      | Some lt \<Rightarrow>
                          (case prime_rel_name r2 of
                             Some rel \<Rightarrow> Some (TNot (TPrime (TInDom rel lt)))
                           | None \<Rightarrow>
                               (case pre_rel_name r2 of
                                  Some rel \<Rightarrow> Some (TNot (TPre (TInDom rel lt)))
                                | None \<Rightarrow>
                                    (case identName r2 of
                                       Some rel \<Rightarrow> Some (TNot (TInDom rel lt))
                                     | None \<Rightarrow>
                                         map_option (\<lambda>rt. TNot (TSetMember lt rt))
                                           (translate enums r2)))))"
        using BinaryOpF BNotIn by simp
      have wl: "wf_z3 l2" using less.prems BinaryOpF BNotIn by simp
      obtain lt where hl: "translate enums l2 = Some lt"
        using less.IH[OF sl wl] by blast
      show ?thesis
      proof (cases "\<exists>rel rsp. r2 = IdentifierF rel rsp")
        case True
        thus ?thesis using beq hl by auto
      next
        case False
        have wr: "wf_z3 r2" using less.prems BinaryOpF BNotIn False by auto
        obtain rt where hr: "translate enums r2 = Some rt"
          using less.IH[OF sr wr] by blast
        show ?thesis using hl hr unfolding beq
          by (cases r2) (auto del: translate.simps split: option.splits)
      qed
    qed (use less.prems BinaryOpF less.IH[OF sl] less.IH[OF sr] in
          \<open>auto split: option.splits\<close>)
  qed (use less.prems in auto)
qed

theorem translate_wf_some_standalone:
  assumes "wf_z3 e"
  shows "\<exists>t. translate enums e = Some t"
  by (rule wf_z3_imp_tfd_some[OF assms])

end
