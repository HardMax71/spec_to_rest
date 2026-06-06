theory Preservation
  imports Preservation_WellTyped
begin

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
      and ncr: "\<nexists>var dnm sp2 p sp3.
                  r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  have "\<exists>l' r' cop. e' = Cmp cop l' r' sp"
  proof (cases "op = BEq")
    case True
    from assms(2)[unfolded True lower_BEq_noncomp[OF ncr]] show ?thesis
      by (auto split: option.splits)
  next
    case False
    with assms(1,2) show ?thesis by (cases op) (auto split: option.splits)
  qed
  then obtain l' r' cop where e_eq: "e' = Cmp cop l' r' sp" by blast
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
  have rnc: "\<nexists>var dnm sp2 p sp3.
               r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
    using T_Cmp_Eq.hyps(2) by (auto dest: not_expr_has_ty_set_comp)
  show ?case
  proof (rule h3_pres_Cmp[OF _ T_Cmp_Eq.prems(2) T_Cmp_Eq.prems(3) rnc])
    show "op \<in> {BEq, BNeq, BLt, BLe, BGt, BGe}"
      using T_Cmp_Eq.hyps(4) by auto
  qed
next
  case (T_Cmp_Ord \<Gamma> l t1 r t2 op sp)
  have rnc: "\<nexists>var dnm sp2 p sp3.
               r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
    using T_Cmp_Ord.hyps(2) by (auto dest: not_expr_has_ty_set_comp)
  show ?case
  proof (rule h3_pres_Cmp[OF _ T_Cmp_Ord.prems(2) T_Cmp_Ord.prems(3) rnc])
    show "op \<in> {BEq, BNeq, BLt, BLe, BGt, BGe}"
      using T_Cmp_Ord.hyps(5) by auto
  qed
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
  from T_BIn_Set.prems(2) T_BIn_Set.hyps(2,3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetMember l' r' sp"
    by (cases r) (auto split: option.splits dest: not_expr_has_ty_set_comp)
  from T_BIn_Set.prems(3) e_eq obtain vl rv where
       ev_l: "eval sch st env l' = Some vl"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
   and v_eq: "v = VBool (contains_value rv vl)"
    by (auto split: option.splits ir_value.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_BNotIn_Set \<Gamma> l t r sp)
  from T_BNotIn_Set.prems(2) T_BNotIn_Set.hyps(2,3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = UnNot (SetMember l' r' sp) sp"
    by (cases r) (auto split: option.splits dest: not_expr_has_ty_set_comp)
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
