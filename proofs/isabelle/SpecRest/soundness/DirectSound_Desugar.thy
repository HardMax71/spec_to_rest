theory DirectSound_Desugar
  imports
    DirectSound_Smt
begin

text \<open>Soundness of the two non-1:1 desugars: the dom-equality term
  evaluates to the domains' set equality, and the comprehension-equality
  term evaluates to set equality (capture-free by the \<open>fresh_var\<close>
  construction).\<close>
section \<open>The dom-equality term evaluates to the domains' set equality\<close>

lemma vts_mem_image [simp]:
  "(value_to_smt x \<in> value_to_smt ` S) = (x \<in> S)"
  by (auto simp: image_iff)

lemma smtEval_forall_rel_indom:
  "smt_model_lookup_rel m rel = Some d
     \<Longrightarrow> smtEval_forall_rel m env x0 svs (TInDom rel (TVar x0))
         = Some (SBool (list_all (contains_smt_val d) svs))"
proof (induction svs)
  case Nil
  show ?case by simp
next
  case (Cons sv rest)
  thus ?case by (auto simp: smt_env_lookup_def)
qed

lemma translate_dom_eq_sound:
  assumes dx: "state_relation_domain st rx = Some dx"
      and dy: "state_relation_domain st ry = Some dy"
  shows "smtEval (correlate_model s st) ce (translate_dom_eq rx ry)
           = Some (SBool (set dx = set dy))"
proof -
  define k where "k = fresh_var (STR ''x'') [rx, ry]"
  have un: "translate_dom_eq rx ry
              = TAnd (TForallRel k rx (TInDom ry (TVar k)))
                     (TForallRel k ry (TInDom rx (TVar k)))"
    by (simp add: translate_dom_eq_def Let_def k_def)
  have lx: "smt_model_lookup_rel (correlate_model s st) rx = Some (map value_to_smt dx)"
    using dx by simp
  have ly: "smt_model_lookup_rel (correlate_model s st) ry = Some (map value_to_smt dy)"
    using dy by simp
  have d1: "smtEval (correlate_model s st) ce
              (TForallRel k rx (TInDom ry (TVar k)))
              = Some (SBool (set dx \<subseteq> set dy))"
    using lx ly smtEval_forall_rel_indom[OF ly]
    by (auto simp: list_all_iff subset_iff)
  have d2: "smtEval (correlate_model s st) ce
              (TForallRel k ry (TInDom rx (TVar k)))
              = Some (SBool (set dy \<subseteq> set dx))"
    using lx ly smtEval_forall_rel_indom[OF lx]
    by (auto simp: list_all_iff subset_iff)
  show ?thesis unfolding un using d1 d2 by (simp add: set_eq_subset)
qed

section \<open>The comprehension-equality term evaluates to set equality\<close>

lemma inj_value_to_smt: "inj value_to_smt"
  by (simp add: inj_def)

lemma set_map_vts_subset [simp]:
  "(value_to_smt ` A \<subseteq> value_to_smt ` B) = (A \<subseteq> B)"
  by (auto simp: subset_iff)

lemma set_map_vts_eq [simp]:
  "(value_to_smt ` A = value_to_smt ` B) = (A = B)"
  by (simp add: set_eq_subset)

lemma smtEval_the_rel_mem:
  "smtEval_the_rel m env var d body = Some ms \<Longrightarrow> v \<in> set d
     \<Longrightarrow> smtEval m ((var, v) # env) body = Some (SBool b) \<Longrightarrow> (v \<in> set ms) = b"
  using smtEval_the_rel_filter by fastforce

lemma smt_comp_dir1:
  assumes ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and dropp: "\<And>d. smtEval m ((var, d) # (f, SSet xst) # env) pt
                       = smtEval m ((var, d) # env) pt"
      and vne: "var \<noteq> f"
  shows "smtEval_forall_rel m ((f, SSet xst) # env) var svs
            (TImplies pt (TSetMember (TVar var) (TVar f)))
           = Some (SBool (set mst \<subseteq> set xst))"
  using ethe
proof (induction svs arbitrary: mst)
  case Nil
  thus ?case by simp
next
  case (Cons w rest)
  from Cons.prems obtain b matches where
      wb: "smtEval m ((var, w) # env) pt = Some (SBool b)"
      and mr: "smtEval_the_rel m env var rest pt = Some matches"
      and ms_eq: "mst = (if b then w # matches else matches)"
    by (auto split: option.splits smt_val.splits)
  have ev: "smtEval m ((var, w) # (f, SSet xst) # env)
              (TImplies pt (TSetMember (TVar var) (TVar f)))
            = Some (SBool (\<not> b \<or> w \<in> set xst))"
    using wb dropp[of w] vne by (simp add: smt_env_lookup_def)
  have IH: "smtEval_forall_rel m ((f, SSet xst) # env) var rest
              (TImplies pt (TSetMember (TVar var) (TVar f)))
            = Some (SBool (set matches \<subseteq> set xst))"
    using Cons.IH[OF mr] .
  show ?case using ev IH ms_eq by auto
qed

lemma smt_comp_dir2:
  assumes ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and dropp: "\<And>d. smtEval m ((var, d) # (f, SSet xst) # env) pt
                       = smtEval m ((var, d) # env) pt"
      and vne: "var \<noteq> f"
      and srd: "smt_model_lookup_rel m dnm = Some svs"
      and la: "list_all (\<lambda>x. contains_smt_val svs x) yst"
  shows "smtEval_forall_rel m ((f, SSet xst) # env) var yst
            (TAnd (TInDom dnm (TVar var)) pt)
           = Some (SBool (set yst \<subseteq> set mst))"
  using la
proof (induction yst)
  case Nil
  thus ?case by simp
next
  case (Cons x rest)
  from Cons.prems have xsvs: "x \<in> set svs"
      and larest: "list_all (\<lambda>x. contains_smt_val svs x) rest"
    by auto
  obtain b where bx: "smtEval m ((var, x) # env) pt = Some (SBool b)"
    using smtEval_the_rel_defined[OF ethe xsvs] by blast
  have xms: "(x \<in> set mst) = b"
    using smtEval_the_rel_mem[OF ethe xsvs bx] .
  have ev: "smtEval m ((var, x) # (f, SSet xst) # env)
              (TAnd (TInDom dnm (TVar var)) pt) = Some (SBool b)"
    using bx dropp[of x] srd xsvs by (simp add: smt_env_lookup_def)
  have IH: "smtEval_forall_rel m ((f, SSet xst) # env) var rest
              (TAnd (TInDom dnm (TVar var)) pt)
            = Some (SBool (set rest \<subseteq> set mst))"
    using Cons.IH[OF larest] .
  show ?case using ev IH xms by auto
qed

lemma smt_comp_assembly:
  assumes elt: "smtEval m env lt = Some (SSet xst)"
      and ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and srd: "smt_model_lookup_rel m dnm = Some svs"
      and la: "list_all (\<lambda>x. contains_smt_val svs x) xst"
      and dne: "\<not> string_in_list dnm enums"
  shows "smtEval m env (translate_set_comp_eq enums var dnm lt pt)
           = Some (SBool (set xst = set mst))"
proof -
  define f where "f = fresh_var (STR ''s'') (var # smt_var_list pt)"
  have ff: "f \<notin> set (var # smt_var_list pt)"
    unfolding f_def by (rule fresh_var_fresh)
  have vne: "var \<noteq> f" using ff by auto
  have fp: "\<not> smt_uses_var f pt" using ff smt_var_list_covers by auto
  have un: "translate_set_comp_eq enums var dnm lt pt
              = TLetIn f lt
                  (TAnd (TForallRel var dnm
                           (TImplies pt (TSetMember (TVar var) (TVar f))))
                        (TForallSet var (TVar f) (TAnd (TInDom dnm (TVar var)) pt)))"
    using dne by (simp add: Let_def f_def)
  have dropp: "\<And>d. smtEval m ((var, d) # (f, SSet xst) # env) pt
                    = smtEval m ((var, d) # env) pt"
  proof -
    fix d
    show "smtEval m ((var, d) # (f, SSet xst) # env) pt
            = smtEval m ((var, d) # env) pt"
      by (rule smtEval_drop_head_irrelevant[OF fp])
  qed
  have d1: "smtEval m ((f, SSet xst) # env)
              (TForallRel var dnm (TImplies pt (TSetMember (TVar var) (TVar f))))
            = Some (SBool (set mst \<subseteq> set xst))"
    using smt_comp_dir1[OF ethe dropp vne] srd by simp
  have d2: "smtEval m ((f, SSet xst) # env)
              (TForallSet var (TVar f) (TAnd (TInDom dnm (TVar var)) pt))
            = Some (SBool (set xst \<subseteq> set mst))"
    using smt_comp_dir2[OF ethe dropp vne srd la] by (simp add: smt_env_lookup_def)
  show ?thesis unfolding un using elt d1 d2 by (auto simp: set_eq_subset conj_commute)
qed

lemma list_all_contains_map:
  "list_all (\<lambda>x. contains_smt_val (map value_to_smt svs) x) (map value_to_smt xs)
     = list_all (\<lambda>x. contains_value svs x) xs"
  by (induction xs) auto

end
