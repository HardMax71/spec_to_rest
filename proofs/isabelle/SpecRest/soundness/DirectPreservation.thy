theory DirectPreservation
  imports Preservation_WellTyped DirectSound
begin

text \<open>Typing preservation re-targeted at the surface semantics (#391 Stage 5d):
  \<open>h3_preservation\<close> states that any \<open>eval\<close>-defined result of a
  well-typed expression carries the declared type, with no detour through the
  \<open>lower\<close>/\<open>eval\<close> verified-subset pipeline. Many typing rules are vacuous here
  because \<open>eval\<close> does not yet model their constructs (set operations,
  cardinality, membership, non-QAll and multi-binding quantifiers all evaluate
  to \<open>None\<close>); the substantial cases reuse the value-level H1 lemmas
  (\<open>eval_arith_preservation\<close>, \<open>eval_cmp_preservation\<close>) unchanged. The capstone
  \<open>cat_h_progress_and_preservation_direct\<close> replaces the lower-based original:
  progress through \<open>translate\<close>, preservation through
  \<open>eval\<close>.\<close>

lemma not_expr_has_ty_CallF:
  "expr_has_ty \<Gamma> (CallF c args sp) t \<Longrightarrow> False"
  by (auto elim: expr_has_ty.cases)

lemma typed_dom_arg_None:
  "expr_has_ty \<Gamma> l t \<Longrightarrow> dom_arg l = None"
  using dom_arg_SomeD not_expr_has_ty_CallF by (cases "dom_arg l") blast+

lemma typed_dom_eq_domains_None:
  "expr_has_ty \<Gamma> l t \<Longrightarrow> dom_eq_domains fs ps st op l r = None"
  using typed_dom_arg_None
  by (auto simp: dom_eq_domains_def split: option.splits)

lemma typed_beq_comp_None:
  "expr_has_ty \<Gamma> r t \<Longrightarrow> beq_comp op r = None"
  by (cases "beq_comp op r")
     (auto dest!: beq_comp_SetComp not_expr_has_ty_set_comp)

lemma eval_forall_VBool:
  "eval_forall fs ps fuel s st env var dmv body = Some v \<Longrightarrow> \<exists>b. v = VBool b"
proof (induction dmv arbitrary: v)
  case Nil
  thus ?case by auto
next
  case (Cons w rest)
  thus ?case by (auto split: option.splits ir_value.splits)
qed

lemma eval_fields_typed:
  "eval_fields fs ps fuel sch st env fas = Some fvs
     \<Longrightarrow> (\<And>fld vexp sp' vv. FieldAssignFull fld vexp sp' \<in> set fas
            \<Longrightarrow> eval fs ps fuel sch st env vexp = Some vv
            \<Longrightarrow> \<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> vv ft)
     \<Longrightarrow> \<forall>(fld, fv) \<in> set fvs.
           \<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> fv ft"
proof (induction fas arbitrary: fvs)
  case Nil
  thus ?case by simp
next
  case (Cons fa fas')
  obtain fld vexp sp' where fa: "fa = FieldAssignFull fld vexp sp'" by (cases fa) auto
  from Cons.prems(1) fa obtain fv fvs0
    where ev: "eval fs ps fuel sch st env vexp = Some fv"
      and er: "eval_fields fs ps fuel sch st env fas' = Some fvs0"
      and fvseq: "fvs = (fld, fv) # fvs0"
    by (auto split: option.splits)
  have hd_ty: "\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> fv ft"
    using Cons.prems(2)[of fld vexp sp' fv] fa ev by simp
  have rest_ty: "\<forall>(fld, fv) \<in> set fvs0.
                   \<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> fv ft"
  proof (rule Cons.IH[OF er])
    fix fld vexp sp' vv assume m: "FieldAssignFull fld vexp sp' \<in> set fas'"
      and ev2: "eval fs ps fuel sch st env vexp = Some vv"
    show "\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> vv ft"
      using Cons.prems(2)[of fld vexp sp' vv] m ev2 by simp
  qed
  show ?case using fvseq hd_ty rest_ty by simp
qed

lemma fold_entity_with_typed:
  "value_has_ty \<Gamma> bv (TEntity ename)
     \<Longrightarrow> \<forall>(fld, fv) \<in> set fvs.
           \<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> fv ft
     \<Longrightarrow> value_has_ty \<Gamma> (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)
                       (TEntity ename)"
proof (induction fvs arbitrary: bv)
  case Nil
  thus ?case by simp
next
  case (Cons p fvs')
  obtain fld fv where peq: "p = (fld, fv)" by (cases p)
  obtain ft where ftl: "schemaFieldType \<Gamma> ename fld = Some ft"
      and fvt: "value_has_ty \<Gamma> fv ft"
    using Cons.prems(2) peq by auto
  have step: "value_has_ty \<Gamma> (VEntityWith bv fld fv) (TEntity ename)"
    using vt_entity_with[OF Cons.prems(1) ftl fvt] .
  show ?case using Cons.IH[OF step] Cons.prems(2) peq by simp
qed

theorem h3_preservation:
  assumes "expr_has_ty \<Gamma> e t"
      and "agrees_strict env st \<Gamma>"
      and "eval fs ps fuel sch st env e = Some v"
  shows "value_has_ty \<Gamma> v t"
  using assms
proof (induction arbitrary: v env rule: expr_has_ty.induct)
  case (T_BoolLit \<Gamma> b sp)
  thus ?case by auto
next
  case (T_IntLit \<Gamma> n sp)
  thus ?case by auto
next
  case (T_FloatLit s \<Gamma> sp)
  thus ?case by (cases "decimalToRat s") auto
next
  case (T_Ident_Lex \<Gamma> x t sp)
  obtain w where ew: "map_of env x = Some w" and wt: "value_has_ty \<Gamma> w t"
    using agrees_strict_env_lookup[OF T_Ident_Lex.prems(1) T_Ident_Lex.hyps] by blast
  have "v = w" using T_Ident_Lex.prems(2) ew by (simp add: env_lookup_def)
  thus ?case using wt by simp
next
  case (T_Ident_State \<Gamma> x t sp)
  from agrees_strict_state_lookup[OF T_Ident_State.prems(1) T_Ident_State.hyps]
  obtain w where mn: "map_of env x = None"
             and sw: "state_lookup_scalar st x = Some w"
             and wt: "value_has_ty \<Gamma> w t" by blast
  have "v = w" using T_Ident_State.prems(2) mn sw by (simp add: env_lookup_def)
  thus ?case using wt by simp
next
  case (T_Arith \<Gamma> l t1 r t2 op sp)
  have deN: "dom_eq_domains fs ps st op l r = None"
    by (rule typed_dom_eq_domains_None[OF T_Arith.hyps(1)])
  have bcN: "beq_comp op r = None"
    by (rule typed_beq_comp_None[OF T_Arith.hyps(2)])
  have ev: "eval_bin op (eval fs ps fuel sch st env l)
              (eval fs ps fuel sch st env r) = Some v"
    using T_Arith.prems(2) deN bcN by simp
  have eva: "eval_arith (case op of BAdd \<Rightarrow> AddOp | BSub \<Rightarrow> SubOp
                           | BMul \<Rightarrow> MulOp | BDiv \<Rightarrow> DivOp)
               (eval fs ps fuel sch st env l)
               (eval fs ps fuel sch st env r) = Some v"
    using ev T_Arith.hyps(5) by (cases op) auto
  show ?case
  proof (rule eval_arith_preservation[OF eva _ _ T_Arith.hyps(3) T_Arith.hyps(4)])
    fix a assume "eval fs ps fuel sch st env l = Some a"
    thus "value_has_ty \<Gamma> a t1" using T_Arith.IH(1)[OF T_Arith.prems(1)] by blast
  next
    fix b assume "eval fs ps fuel sch st env r = Some b"
    thus "value_has_ty \<Gamma> b t2" using T_Arith.IH(2)[OF T_Arith.prems(1)] by blast
  qed
next
  case (T_Cmp_Eq \<Gamma> l t1 r t2 op sp)
  have deN: "dom_eq_domains fs ps st op l r = None"
    by (rule typed_dom_eq_domains_None[OF T_Cmp_Eq.hyps(1)])
  have bcN: "beq_comp op r = None"
    by (rule typed_beq_comp_None[OF T_Cmp_Eq.hyps(2)])
  have ev: "eval_bin op (eval fs ps fuel sch st env l)
              (eval fs ps fuel sch st env r) = Some v"
    using T_Cmp_Eq.prems(2) deN bcN by simp
  have evc: "eval_cmp (case op of BEq \<Rightarrow> EqOp | BNeq \<Rightarrow> NeqOp)
               (eval fs ps fuel sch st env l)
               (eval fs ps fuel sch st env r) = Some v"
    using ev T_Cmp_Eq.hyps(4) by (cases op) auto
  from eval_cmp_preservation[OF evc] show ?case .
next
  case (T_Cmp_Ord \<Gamma> l t1 r t2 op sp)
  have deN: "dom_eq_domains fs ps st op l r = None"
    by (rule typed_dom_eq_domains_None[OF T_Cmp_Ord.hyps(1)])
  have bcN: "beq_comp op r = None"
    by (rule typed_beq_comp_None[OF T_Cmp_Ord.hyps(2)])
  have ev: "eval_bin op (eval fs ps fuel sch st env l)
              (eval fs ps fuel sch st env r) = Some v"
    using T_Cmp_Ord.prems(2) deN bcN by simp
  have evc: "eval_cmp (case op of BLt \<Rightarrow> LtOp | BLe \<Rightarrow> LeOp
                         | BGt \<Rightarrow> GtOp | BGe \<Rightarrow> GeOp)
               (eval fs ps fuel sch st env l)
               (eval fs ps fuel sch st env r) = Some v"
    using ev T_Cmp_Ord.hyps(5) by (cases op) auto
  from eval_cmp_preservation[OF evc] show ?case .
next
  case (T_Bool_Bin \<Gamma> l r op sp)
  have deN: "dom_eq_domains fs ps st op l r = None"
    by (rule typed_dom_eq_domains_None[OF T_Bool_Bin.hyps(1)])
  have bcN: "beq_comp op r = None"
    by (rule typed_beq_comp_None[OF T_Bool_Bin.hyps(2)])
  have ev: "eval_bin op (eval fs ps fuel sch st env l)
              (eval fs ps fuel sch st env r) = Some v"
    using T_Bool_Bin.prems(2) deN bcN by simp
  hence "\<exists>b. v = VBool b"
    using T_Bool_Bin.hyps(3)
    by (cases op) (auto split: option.splits ir_value.splits)
  thus ?case by auto
next
  case (T_Not \<Gamma> e sp)
  from T_Not.prems(2) obtain b where "v = VBool b"
    by (cases "eval fs ps fuel sch st env e") (auto split: ir_value.splits)
  thus ?case by simp
next
  case (T_Neg \<Gamma> e t sp)
  from T_Neg.prems(2) obtain a where
      ea: "eval fs ps fuel sch st env e = Some a"
    by (cases "eval fs ps fuel sch st env e") auto
  have at: "value_has_ty \<Gamma> a t" using T_Neg.IH[OF T_Neg.prems(1) ea] .
  from T_Neg.hyps(2) have "t = TInt \<or> t = TReal" by (simp add: numeric_ty_def)
  then show ?case
  proof
    assume t: "t = TInt"
    with at obtain n where "a = VInt n" by (cases a) auto
    with ea T_Neg.prems(2) have "v = VInt (- n)" by simp
    with t show ?thesis by simp
  next
    assume t: "t = TReal"
    with at obtain r where "a = VReal r" by (cases a) auto
    with ea T_Neg.prems(2) have "v = VReal (- r)" by simp
    with t show ?thesis by simp
  qed
next
  case (T_Let \<Gamma> vexp t1 x body t2 sp)
  from T_Let.prems(2) obtain va where
      ev_v: "eval fs ps fuel sch st env vexp = Some va"
      and ev_body: "eval fs ps fuel sch st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  have va_ty: "value_has_ty \<Gamma> va t1"
    using T_Let.IH(1)[OF T_Let.prems(1) ev_v] .
  hence agr_ext: "agrees_strict ((x, va) # env) st
                    (\<Gamma>\<lparr>tc_env := (x, t1) # tc_env \<Gamma>\<rparr>)"
    using T_Let.prems(1) agrees_strict_cons by blast
  show ?case using T_Let.IH(2)[OF agr_ext ev_body] by simp
next
  case (T_Prime \<Gamma> e t sp)
  thus ?case by simp
next
  case (T_Pre \<Gamma> e t sp)
  thus ?case by simp
next
  case (T_EnumAccess \<Gamma> en sp1 mem sp)
  from T_EnumAccess.prems(2) have "v = VEnum en mem"
    by (auto split: option.splits if_splits)
  thus ?case by (simp add: vt_enum)
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
  case (T_SetLit_Empty \<Gamma> sp t)
  from T_SetLit_Empty.prems(2) have "v = VSet []" by simp
  thus ?case by (simp add: vt_set)
next
  case (T_SetLit_Cons \<Gamma> e t rest sp)
  from T_SetLit_Cons.prems(2) obtain va rest_vs where
      ev_e: "eval fs ps fuel sch st env e = Some va"
      and ev_rest: "eval_list fs ps fuel sch st env rest = Some rest_vs"
      and v_eq: "v = VSet (dedupe_values
                    (va # foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs []))"
    by (auto split: option.splits)
  have va_ty: "value_has_ty \<Gamma> va t"
    using T_SetLit_Cons.IH(1)[OF T_SetLit_Cons.prems(1) ev_e] .
  have ev_rest_set: "eval fs ps fuel sch st env (SetLiteralF rest sp)
                       = Some (VSet (foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs []))"
    using ev_rest by simp
  have rest_ty: "value_has_ty \<Gamma>
                   (VSet (foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs [])) (TSet t)"
    using T_SetLit_Cons.IH(2)[OF T_SetLit_Cons.prems(1) ev_rest_set] .
  hence rest_all: "\<forall>w \<in> set (foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs []).
                     value_has_ty \<Gamma> w t"
    by (auto elim: value_has_ty_set_cases)
  have "\<forall>w \<in> set (va # foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs []).
          value_has_ty \<Gamma> w t"
    using va_ty rest_all by simp
  hence "\<forall>w \<in> set (dedupe_values
                    (va # foldr (\<lambda>w acc. dedupe_values (w # acc)) rest_vs [])).
           value_has_ty \<Gamma> w t"
    by (rule dedupe_values_preserves_value_ty)
  thus ?case by (simp add: v_eq vt_set)
next
  case (T_BUnion \<Gamma> l t r sp)
  thus ?case using typed_dom_eq_domains_None[OF T_BUnion.hyps(1)]
      typed_beq_comp_None[OF T_BUnion.hyps(2)] by simp
next
  case (T_BIntersect \<Gamma> l t r sp)
  thus ?case using typed_dom_eq_domains_None[OF T_BIntersect.hyps(1)]
      typed_beq_comp_None[OF T_BIntersect.hyps(2)] by simp
next
  case (T_BDiff \<Gamma> l t r sp)
  thus ?case using typed_dom_eq_domains_None[OF T_BDiff.hyps(1)]
      typed_beq_comp_None[OF T_BDiff.hyps(2)] by simp
next
  case (T_BIn_Set \<Gamma> l t r sp)
  thus ?case using typed_dom_eq_domains_None[OF T_BIn_Set.hyps(1)]
      typed_beq_comp_None[OF T_BIn_Set.hyps(2)] by simp
next
  case (T_BNotIn_Set \<Gamma> l t r sp)
  thus ?case using typed_dom_eq_domains_None[OF T_BNotIn_Set.hyps(1)]
      typed_beq_comp_None[OF T_BNotIn_Set.hyps(2)] by simp
next
  case (T_FieldAccess \<Gamma> base ename fname ft sp)
  from T_FieldAccess.prems(2) obtain vb where
      ev_base: "eval fs ps fuel sch st env base = Some vb"
      and v_eq: "value_field_lookup st vb fname = Some v"
    by (auto split: option.splits)
  have vb_ty: "value_has_ty \<Gamma> vb (TEntity ename)"
    using T_FieldAccess.IH[OF T_FieldAccess.prems(1) ev_base] .
  show ?case
    using agrees_strict_field_lookup[OF T_FieldAccess.prems(1) vb_ty
                                        T_FieldAccess.hyps(2) v_eq] .
next
  case (T_Index base rel_name \<Gamma> key tk tv sp)
  from T_Index.prems(2) T_Index.hyps(1) obtain kv where
      v_lookup: "state_lookup_key st rel_name kv = Some v"
    by (auto split: option.splits)
  show ?case
    using agrees_strict_relation_lookup[OF T_Index.prems(1)
                                           T_Index.hyps(3) v_lookup] .
next
  case (T_With \<Gamma> base ename updates sp)
  from T_With.prems(2) obtain bv fvs where
      ev_base: "eval fs ps fuel sch st env base = Some bv"
      and ev_fields: "eval_fields fs ps fuel sch st env updates = Some fvs"
      and v_eq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  have bv_ty: "value_has_ty \<Gamma> bv (TEntity ename)"
    using T_With.IH(1)[OF T_With.prems(1) ev_base] .
  have fvs_ty: "\<forall>(fld, fv) \<in> set fvs.
                  \<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> fv ft"
  proof (rule eval_fields_typed[OF ev_fields])
    fix fld vexp sp' vv assume m: "FieldAssignFull fld vexp sp' \<in> set updates"
      and ev: "eval fs ps fuel sch st env vexp = Some vv"
    obtain ft where ftl: "schemaFieldType \<Gamma> ename fld = Some ft"
        and vt: "\<forall>v env. agrees_strict env st \<Gamma>
                   \<longrightarrow> eval fs ps fuel sch st env vexp = Some v
                   \<longrightarrow> value_has_ty \<Gamma> v ft"
      using T_With.IH(2) m by blast
    show "\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft \<and> value_has_ty \<Gamma> vv ft"
      using ftl vt T_With.prems(1) ev by blast
  qed
  show ?case using fold_entity_with_typed[OF bv_ty fvs_ty] v_eq by simp
next
  case (T_Forall_QAll_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  from T_Forall_QAll_Enum.prems(2) obtain var' dmv where
      eff: "eval_forall fs ps fuel sch st env var' dmv body = Some v"
    by (auto split: option.splits)
  from eval_forall_VBool[OF eff] show ?case by auto
next
  case (T_Forall_QAll_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  from T_Forall_QAll_Rel.prems(2) obtain var' dmv where
      eff: "eval_forall fs ps fuel sch st env var' dmv body = Some v"
    by (auto split: option.splits)
  from eval_forall_VBool[OF eff] show ?case by auto
next
  case (T_Forall_QAll_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QAll_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QNo_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QNo_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QNo_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QNo_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QExists_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QExists_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QExists_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QExists_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QSome_Enum dnm \<Gamma> var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QSome_Rel dnm \<Gamma> tv var body sp_id m sp_b sp)
  thus ?case by simp
next
  case (T_Forall_QSome_Enum_Cons dnm \<Gamma> var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
next
  case (T_Forall_QSome_Rel_Cons dnm \<Gamma> tv var b2 rest_bs body sp sp_id m sp_b)
  thus ?case by simp
qed

theorem cat_h_progress_and_preservation_direct:
  assumes "expr_has_ty \<Gamma> e t"
      and "agrees_strict env st \<Gamma>"
  shows "\<exists>tm. translate enums e = Some tm
              \<and> (\<forall>fs ps fuel sch v. eval fs ps fuel sch st env e = Some v
                   \<longrightarrow> value_has_ty \<Gamma> v t)"
proof -
  obtain tm where tm: "translate enums e = Some tm"
    using wf_z3_imp_tfd_some[OF well_typed_imp_wf_z3[OF assms(1)]] by blast
  show ?thesis
  proof (intro exI conjI allI impI)
    show "translate enums e = Some tm" by (rule tm)
  next
    fix fs ps fuel sch v assume "eval fs ps fuel sch st env e = Some v"
    thus "value_has_ty \<Gamma> v t"
      using h3_preservation[OF assms(1) assms(2)] by blast
  qed
qed

end
