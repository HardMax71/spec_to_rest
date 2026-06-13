theory Preservation_WellTyped
  imports Preservation_Wf
begin

text \<open>Phase H2 -> 9j bridge. Every well-typed expression in the
  H2 arith / cmp / bool fragment lies in the Phase 9j \<open>wf_z3\<close>
  subset. Composed with \<open>wf_z3_imp_tfd_some\<close> this gives
  the first half of the progress chain:
    well-typed e ==> wf_z3 e ==> translate enums e \<noteq> None.
  \<open>h3_preservation\<close> completes the chain via the H1
  preservation lemmas against \<open>eval\<close>.\<close>

lemma well_typed_imp_wf_z3:
  "expr_has_ty \<Gamma> e t \<Longrightarrow> wf_z3 e"
proof (induction rule: expr_has_ty.induct)
  case (T_Arith \<Gamma> l t1 r t2 op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Eq \<Gamma> l t1 r t2 op sp)
  have rnc: "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
    using T_Cmp_Eq.hyps(2) by (auto dest: not_expr_has_ty_set_comp)
  from T_Cmp_Eq.hyps(4) have d2: "op = BEq \<or> op = BNeq" by auto
  show ?case
  proof (rule disjE[OF d2])
    assume oe: "op = BEq"
    show ?case unfolding oe
      by (subst wf_z3_BEq_noncomp[OF rnc]) (simp add: T_Cmp_Eq.IH)
  next
    assume oe: "op = BNeq"
    show ?case unfolding oe using T_Cmp_Eq.IH by simp
  qed
next
  case (T_Cmp_Ord \<Gamma> l t1 r t2 op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Str_Ord \<Gamma> l r op sp)
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
  have rnc: "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
    using T_BIn_Set.hyps(2) by (auto dest: not_expr_has_ty_set_comp)
  show ?case
    by (subst wf_z3_BIn_noncomp[OF rnc]) (simp add: T_BIn_Set.IH)
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


end
