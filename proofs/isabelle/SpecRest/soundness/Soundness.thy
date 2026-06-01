theory Soundness
  imports Soundness_Scalar Soundness_Collection
begin

theorem soundness:
  "value_to_smt_opt (eval s st env e)
     = smtEval (correlate_model s st) (correlate_env env) (translate e)"
proof (induction e arbitrary: env)
  case (BoolLit b sp) show ?case by simp
next
  case (IntLit n sp) show ?case by simp
next
  case (RealLit r sp) show ?case by simp
next
  case (Ident x sp) show ?case by (simp split: option.splits)
next
  case (UnNot e sp) show ?case using soundness_UnNot[OF UnNot.IH] .
next
  case (UnNeg e sp) show ?case using soundness_UnNeg[OF UnNeg.IH] .
next
  case (BoolBin op l r sp) show ?case using soundness_BoolBin[OF BoolBin.IH(1) BoolBin.IH(2)] .
next
  case (Arith op l r sp) show ?case using soundness_Arith[OF Arith.IH(1) Arith.IH(2)] .
next
  case (Cmp op l r sp) show ?case using soundness_Cmp[OF Cmp.IH(1) Cmp.IH(2)] .
next
  case (LetIn x v body sp) show ?case using soundness_LetIn[OF LetIn.IH(1) LetIn.IH(2)] .
next
  case (EnumAccess en mem sp)
  show ?case
    by (simp add: correlate_model_lookup_sort_members_eq split: option.splits)
next
  case (Member elem rel_name sp) show ?case using soundness_Member[OF Member.IH] .
next
  case (ForallEnum var en body sp)
  show ?case
  proof (cases "schema_lookup_enum s en")
    case None thus ?thesis by (simp add: correlate_model_lookup_sort_members_eq)
  next
    case (Some d)
    thus ?thesis
      using soundness_forall_enum_known[OF Some, where var=var and env=env] ForallEnum.IH
      by simp
  qed
next
  case (ForallRel var rel_name body sp)
  show ?case
  proof (cases "state_relation_domain st rel_name")
    case None thus ?thesis by simp
  next
    case (Some d)
    thus ?thesis
      using soundness_forall_rel_known[OF Some, where var=var and env=env] ForallRel.IH
      by simp
  qed
next
  case (Prime e sp) thus ?case by simp
next
  case (Pre e sp) thus ?case by simp
next
  case (CardRel rel_name sp) show ?case by (simp split: option.splits)
next
  case (IndexRel base key sp) show ?case using soundness_IndexRel[OF IndexRel.IH(2)] .
next
  case (FieldAccess base fname sp) show ?case using soundness_FieldAccess[OF FieldAccess.IH] .
next
  case (SetEmpty sp) show ?case by simp
next
  case (SetInsert elem set_e sp) show ?case using soundness_SetInsert[OF SetInsert.IH(1) SetInsert.IH(2)] .
next
  case (SetMember elem set_e sp) show ?case using soundness_SetMember[OF SetMember.IH(1) SetMember.IH(2)] .
next
  case (SetBin op l r sp) show ?case using soundness_SetBin[OF SetBin.IH(1) SetBin.IH(2)] .
next
  case (WithRec base fld value_e sp) show ?case using soundness_WithRec[OF WithRec.IH(1) WithRec.IH(2)] .
next
  case (Ite c a b sp) show ?case using soundness_Ite[OF Ite.IH(1) Ite.IH(2) Ite.IH(3)] .
qed

section \<open>Issue #202 Phase 3 — lower-soundness corollary\<close>

text \<open>Composing the universal soundness theorem with the \<open>lower\<close> projection
  from \<open>expr_full\<close> to the verified subset \<open>expr\<close>: any successfully lowered
  full-IR expression's verified-subset image satisfies translator soundness.
  The corollary is a thin instance of \<open>soundness\<close> — \<open>soundness\<close> is already
  universal in \<open>e :: expr\<close>, and \<open>lower enums e_full = Some e\<close> witnesses
  that \<open>e\<close> is in the verified subset. The hypothesis is not needed by the
  proof; it
  documents the architectural intent that the full input ADT obtains
  end-to-end soundness via \<open>lower\<close> + \<open>soundness\<close>.\<close>

corollary lower_soundness:
  assumes "lower enums e_full = Some e"
  shows "value_to_smt_opt (eval s st env e)
           = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  by (rule soundness)

section \<open>Phase 9b — classifier soundness: requiresAlloy excludes lower\<close>

text \<open>The Phase 8 \<open>requiresAlloy\<close> predicate identifies expressions whose
  shape contains a \<open>UPower\<close> (set-power) somewhere in the tree. The verifier
  routes such expressions to the Alloy backend instead of Z3.

  Phase 9i (RESOLVED): the full disjointness claim
  \<open>requiresAlloy e \<Longrightarrow> lower enums e = None\<close> (with the
  \<open>lowerSetList / lower_with_assigns\<close> companions) is proven in full as
  \<open>requiresAlloy_imp_lower_none\<close>. The obstacle was the induction
  principle, not proof difficulty: the \<open>function\<close>-derived \<open>lower.induct\<close>
  computation induction sequentially splits the nested \<open>case op of \<dots>\<close>
  inside the \<open>BinaryOpF\<close> (20-way) / \<open>UnaryOpF\<close> / \<open>QuantifierF\<close>
  equations into dozens of op-guarded partial subgoals whose IHs no longer
  match the clean collapse lemmas, and blanket \<open>simp_all\<close>/\<open>auto\<close> over
  that exploded set does not terminate within budget (>15 min). The standard
  remedy (Krauss, \<^emph>\<open>Defining Recursive Functions in Isabelle/HOL\<close>, on
  pattern splitting making generated induction rules too specific): do not
  induct on the function's recursion. \<open>requiresAlloy_imp_lower_none_expr\<close>
  uses well-founded \<open>size\<close> induction (\<open>measure_induct_rule\<close>) with a plain
  \<open>cases e\<close> constructor split — \<open>op\<close> is non-recursive so never split —
  giving one clean unguarded case per constructor (~25, vs ~60 op-exploded);
  the two list-recursive constructors go through standalone list lemmas that
  take the per-element fact as a hypothesis (no circular dependency).
  Whole-session build stays ~2.5 min.

  Proven frontier: the \<open>UnaryOpF UPower\<close> leaf
  (\<open>lower_unary_upower_none\<close>); an alloy-tainted expression is never an
  \<open>IdentifierF\<close> (\<open>ra_not_ident\<close>); single-step binding-domain rejection
  (\<open>lower_forall_step_none_of_alloy\<close>); its \<open>lower_forall_bindings\<close>
  propagation (\<open>lfb_none_of_alloy\<close>, structural induction on the binding
  list); the per-constructor collapse lemmas (\<open>lower_unop / binop / quant /
  let / index / fieldaccess / prime / pre / with / setlist / wassign\<close>
  collapses, \<open>lower_ucard / enumaccess_ra_none\<close>) proven self-contained so
  each heavy \<open>cases\<close> happens once, not multiplied through the recursion;
  and the capstone glue \<open>requiresAlloy_imp_lower_none\<close> over \<open>size\<close>
  induction. The Z3-routed and Alloy-routed expression fragments are now
  provably disjoint.\<close>

lemma lower_unary_upower_none [simp]:
  "lower enums (UnaryOpF UPower e sp) = None"
  by simp

lemma lower_some_top_not_upower:
  assumes "lower enums e = Some e'"
  shows "\<nexists> inner sp. e = UnaryOpF UPower inner sp"
  using assms lower_unary_upower_none by fastforce

lemma ra_not_ident:
  "requiresAlloy e \<Longrightarrow> e \<noteq> IdentifierF x sp"
  by (cases e) auto

lemma lower_forall_step_none_of_alloy:
  "requiresAlloy d \<Longrightarrow>
     lower_forall_step enums (QuantifierBindingFull v d k bsp) body sp = None"
  by (cases d) auto

lemma lfb_none_of_alloy:
  "requiresAlloy_bindings bs \<Longrightarrow> lower_forall_bindings enums bs body sp = None"
proof (induction bs)
  case Nil
  then show ?case by simp
next
  case (Cons b bs')
  obtain v d k bsp where b: "b = QuantifierBindingFull v d k bsp"
    by (cases b) auto
  show ?case
  proof (cases bs')
    case Nil
    with Cons.prems b show ?thesis
      by (simp add: lower_forall_step_none_of_alloy)
  next
    case (Cons b2 bs'')
    show ?thesis
    proof (cases "requiresAlloy_bindings bs'")
      case True
      with Cons.IH have "lower_forall_bindings enums bs' body sp = None" by simp
      with \<open>bs' = b2 # bs''\<close> show ?thesis by simp
    next
      case False
      with Cons.prems b have "requiresAlloy d" by simp
      with b \<open>bs' = b2 # bs''\<close> show ?thesis
        by (simp add: lower_forall_step_none_of_alloy split: option.splits)
    qed
  qed
qed

lemma lower_ucard_ra_none:
  "requiresAlloy e \<Longrightarrow> lower enums (UnaryOpF UCardinality e sp) = None"
  by (cases e) auto

lemma lower_enumaccess_ra_none:
  "requiresAlloy base \<Longrightarrow> lower enums (EnumAccessF base mem sp) = None"
  by (cases base) auto

lemma lower_unop_collapse:
  assumes ih: "requiresAlloy e \<Longrightarrow> lower enums e = None"
      and ra: "requiresAlloy (UnaryOpF op e sp)"
  shows "lower enums (UnaryOpF op e sp) = None"
proof (cases op)
  case UCardinality
  with ra have "requiresAlloy e" by simp
  from lower_ucard_ra_none[OF this] show ?thesis unfolding UCardinality .
next
  case UPower
  show ?thesis unfolding UPower by simp
qed (use ra ih in simp_all)

lemma lower_binop_collapse:
  assumes l: "requiresAlloy l \<Longrightarrow> lower enums l = None"
      and r: "requiresAlloy r \<Longrightarrow> lower enums r = None"
      and ra: "requiresAlloy (BinaryOpF op l r sp)"
  shows "lower enums (BinaryOpF op l r sp) = None"
proof -
  from ra have d: "requiresAlloy l \<or> requiresAlloy r" by simp
  have inout: "lower enums (BinaryOpF op l r sp) = None"
    if opc: "op = BIn \<or> op = BNotIn"
    using opc l r d by (elim disjE; cases r) (auto split: option.splits)
  show ?thesis
  proof (cases op)
    case BIn
    thus ?thesis by (rule inout[OF disjI1])
  next
    case BNotIn
    thus ?thesis by (rule inout[OF disjI2])
  next
    case BSubset
    thus ?thesis by simp
  qed (use l r d in \<open>auto split: option.splits\<close>)
qed

lemma lower_quant_collapse:
  assumes b: "requiresAlloy body \<Longrightarrow> lower enums body = None"
      and ra: "requiresAlloy (QuantifierF k bs body sp)"
  shows "lower enums (QuantifierF k bs body sp) = None"
proof -
  from ra have "requiresAlloy_bindings bs \<or> requiresAlloy body" by simp
  thus ?thesis
  proof
    assume "requiresAlloy body"
    with b show ?thesis by simp
  next
    assume rb: "requiresAlloy_bindings bs"
    show ?thesis
      by (cases "lower enums body"; cases k)
         (simp_all add: lfb_none_of_alloy[OF rb])
  qed
qed

lemma lowerSetList_ra_none:
  assumes "\<And>x. x \<in> set xs \<Longrightarrow> requiresAlloy x \<Longrightarrow> lower enums x = None"
      and "requiresAlloy_list xs"
  shows "lowerSetList enums xs sp = None"
  using assms
proof (induction xs)
  case (Cons a xs)
  show ?case using Cons by (auto split: option.splits)
qed simp

lemma lower_with_assigns_ra_none:
  assumes "\<And>fld v fsp. FieldAssignFull fld v fsp \<in> set fs
             \<Longrightarrow> requiresAlloy v \<Longrightarrow> lower enums v = None"
      and "requiresAlloy_fields fs"
  shows "lower_with_assigns enums fs base sp = None"
  using assms
proof (induction fs arbitrary: base)
  case (Cons a fs)
  obtain fld v fsp where a: "a = FieldAssignFull fld v fsp"
    by (cases a) auto
  have hv: "requiresAlloy v \<Longrightarrow> lower enums v = None"
    using Cons.prems(1)[of fld v fsp] a by simp
  have pe: "\<And>f2 v2 s2. FieldAssignFull f2 v2 s2 \<in> set fs
              \<Longrightarrow> requiresAlloy v2 \<Longrightarrow> lower enums v2 = None"
    using Cons.prems(1) by auto
  have hrec: "\<And>b. requiresAlloy_fields fs
                \<Longrightarrow> lower_with_assigns enums fs b sp = None"
    using Cons.IH pe by blast
  show ?case unfolding a using hv hrec Cons.prems(2) a
    by (cases "lower enums v") auto
qed simp

lemma requiresAlloy_imp_lower_none_expr:
  "requiresAlloy e \<Longrightarrow> lower enums e = None"
proof (induction e rule: measure_induct_rule[where f = size])
  case (less e)
  note IH = less.IH
  have sub: "\<And>s. size s < size e \<Longrightarrow> requiresAlloy s \<Longrightarrow> lower enums s = None"
    using IH by blast
  show ?case
  proof (cases e)
    case (UnaryOpF op a s)
    have ih: "requiresAlloy a \<Longrightarrow> lower enums a = None"
      using sub[of a] UnaryOpF by simp
    show ?thesis unfolding UnaryOpF
      by (rule lower_unop_collapse[OF ih less.prems[unfolded UnaryOpF]])
  next
    case (BinaryOpF op l r s)
    have l: "requiresAlloy l \<Longrightarrow> lower enums l = None"
      using sub[of l] BinaryOpF by simp
    have r: "requiresAlloy r \<Longrightarrow> lower enums r = None"
      using sub[of r] BinaryOpF by simp
    show ?thesis unfolding BinaryOpF
      by (rule lower_binop_collapse[OF l r less.prems[unfolded BinaryOpF]])
  next
    case (QuantifierF k bs body s)
    have ih: "requiresAlloy body \<Longrightarrow> lower enums body = None"
      using sub[of body] QuantifierF by simp
    show ?thesis unfolding QuantifierF
      by (rule lower_quant_collapse[OF ih less.prems[unfolded QuantifierF]])
  next
    case (EnumAccessF bse mem s)
    have rb: "requiresAlloy bse"
      using less.prems EnumAccessF by simp
    show ?thesis unfolding EnumAccessF
      by (rule lower_enumaccess_ra_none[OF rb])
  next
    case (WithF bse ups s)
    have b: "requiresAlloy bse \<Longrightarrow> lower enums bse = None"
      using sub[of bse] WithF by simp
    have w: "\<And>bse'. lower enums bse = Some bse'
               \<Longrightarrow> requiresAlloy_fields ups
               \<Longrightarrow> lower_with_assigns enums ups bse' s = None"
    proof -
      fix bse'
      assume "lower enums bse = Some bse'" and rf: "requiresAlloy_fields ups"
      have pe: "\<And>fld vv fsp. FieldAssignFull fld vv fsp \<in> set ups
                  \<Longrightarrow> requiresAlloy vv \<Longrightarrow> lower enums vv = None"
      proof -
        fix fld vv fsp
        assume m: "FieldAssignFull fld vv fsp \<in> set ups"
           and rv: "requiresAlloy vv"
        have "size vv < size (FieldAssignFull fld vv fsp)" by simp
        also have "\<dots> \<le> size_list size ups"
          by (rule size_list_estimation'[OF m order_refl])
        also have "\<dots> < size e" using WithF by simp
        finally have "size vv < size e" .
        thus "lower enums vv = None" using sub rv by blast
      qed
      show "lower_with_assigns enums ups bse' s = None"
        by (rule lower_with_assigns_ra_none[OF pe rf])
    qed
    show ?thesis unfolding WithF using b w less.prems[unfolded WithF]
      by (cases "lower enums bse") auto
  next
    case (SetLiteralF elems s)
    have pe: "\<And>x. x \<in> set elems \<Longrightarrow> requiresAlloy x \<Longrightarrow> lower enums x = None"
    proof -
      fix x assume m: "x \<in> set elems" and rx: "requiresAlloy x"
      have "size x \<le> size_list size elems"
        by (rule size_list_estimation'[OF m order_refl])
      also have "\<dots> < size e" using SetLiteralF by simp
      finally have "size x < size e" .
      thus "lower enums x = None" using sub rx by blast
    qed
    have rl: "requiresAlloy_list elems"
      using less.prems SetLiteralF by simp
    have "lowerSetList enums elems s = None"
      by (rule lowerSetList_ra_none[OF pe rl])
    thus ?thesis unfolding SetLiteralF by simp
  qed (use less.prems sub in \<open>auto split: option.splits\<close>)
qed

lemma requiresAlloy_imp_lower_none:
  "requiresAlloy e \<Longrightarrow> lower enums e = None"
  "requiresAlloy_list xs \<Longrightarrow> lowerSetList enums xs sp = None"
  "requiresAlloy_fields fs \<Longrightarrow> lower_with_assigns enums fs base sp = None"
proof -
  show "requiresAlloy e \<Longrightarrow> lower enums e = None"
    by (rule requiresAlloy_imp_lower_none_expr)
next
  assume r: "requiresAlloy_list xs"
  show "lowerSetList enums xs sp = None"
    by (rule lowerSetList_ra_none[OF _ r])
       (blast intro: requiresAlloy_imp_lower_none_expr)
next
  assume r: "requiresAlloy_fields fs"
  show "lower_with_assigns enums fs base sp = None"
    by (rule lower_with_assigns_ra_none[OF _ r])
       (blast intro: requiresAlloy_imp_lower_none_expr)
qed

end
