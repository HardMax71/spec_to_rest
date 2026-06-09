theory TranslateFull
  imports Preservation_Lower SpecRest_Core.LowerMeaning
begin

text \<open>Stage 2 of the IR unification (#391): a total, fail-closed
  \<open>translate_full\<close> on the surface IR \<open>expr_full\<close>, obtained by composing the
  \<open>lower\<close> projection with the verified-subset \<open>translate\<close>. Partiality is
  conserved as a \<open>None\<close> result (= Unknown / not-verified); the gate must read
  \<open>None\<close> as not-verified, NEVER as verified. The end-to-end soundness theorem
  composes \<open>lower_preserves_eval_full\<close> (lower preserves the reference meaning,
  proven in \<open>LowerMeaning\<close>) with \<open>soundness\<close> (translate matches \<open>eval\<close>), so
  the emitted SMT term faithfully represents the source \<open>expr_full\<close>'s meaning
  rather than merely \<open>lower\<close>'s output. \<open>wf_z3\<close> characterises the inputs on
  which \<open>translate_full\<close> is guaranteed to succeed.\<close>

definition translate_full :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> smt_term option" where
  "translate_full enums e \<equiv> map_option translate (lower enums e)"

lemma translate_full_wf_some:
  assumes "wf_z3 e"
  shows "\<exists>t. translate_full enums e = Some t"
proof -
  from wf_z3_imp_lower_some(1)[OF assms] obtain e' where "lower enums e = Some e'"
    by blast
  thus ?thesis by (simp add: translate_full_def)
qed

theorem translate_full_soundness:
  assumes ef: "eval_full fs ps fuel s st env e = Some v"
      and tf: "translate_full enums e = Some t"
      and ew: "enums_wf s enums"
      and nc: "no_cmp_var e"
  shows "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
proof -
  from tf obtain e' where le: "lower enums e = Some e'" and te: "t = translate e'"
    by (auto simp: translate_full_def)
  have "eval s st env e' = Some v"
    by (rule lower_preserves_eval_full(1)[OF ef le ew nc])
  thus ?thesis
    using soundness[where e = e' and s = s and st = st and env = env] te by simp
qed

corollary translate_full_gate_soundness:
  assumes "wf_z3 e" and "enums_wf s enums" and "no_cmp_var e"
      and "eval_full fs ps fuel s st env e = Some v"
  obtains t where "translate_full enums e = Some t"
    and "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
proof -
  obtain t where t: "translate_full enums e = Some t"
    using translate_full_wf_some[OF assms(1)] by blast
  moreover have "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
    by (rule translate_full_soundness[OF assms(4) t assms(2,3)])
  ultimately show ?thesis ..
qed

end
