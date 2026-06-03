theory Soundness_Collection
  imports Soundness_Framework
begin

lemma soundness_Member:
  assumes ih: "value_to_smt_opt (eval s st env elem)
                 = smtEval (correlate_model s st) (correlate_env env) (translate elem)"
  shows "value_to_smt_opt (eval s st env (Member elem rel_name sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (Member elem rel_name sp))"
proof -
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate elem)
               = value_to_smt_opt (eval s st env elem)" using ih by simp
  show ?thesis using ih'
    by (cases "eval s st env elem"; cases "state_relation_domain st rel_name")
       (simp_all split: option.splits)
qed

lemma soundness_IndexRel:
  assumes ihk: "value_to_smt_opt (eval s st env key)
                  = smtEval (correlate_model s st) (correlate_env env) (translate key)"
  shows "value_to_smt_opt (eval s st env (IndexRel base key sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (IndexRel base key sp))"
proof -
  have ihk': "smtEval (correlate_model s st) (correlate_env env) (translate key)
                = value_to_smt_opt (eval s st env key)" using ihk by simp
  have peel_eq: "peelSmtRelationRef (translate base) = peel_relation_ref base"
    by (rule peelSmtRelationRef_translate)
  show ?thesis
  proof (cases "peel_relation_ref base")
    case None thus ?thesis using peel_eq by simp
  next
    case (Some rel)
    show ?thesis
    proof (cases "eval s st env key")
      case None thus ?thesis using Some peel_eq ihk' by simp
    next
      case (Some kv)
      thus ?thesis using \<open>peel_relation_ref base = Some rel\<close> peel_eq ihk'
            correlate_model_lookup_key[of s st rel kv]
        by simp
    qed
  qed
qed

lemma soundness_FieldAccess:
  assumes ih: "value_to_smt_opt (eval s st env base)
                 = smtEval (correlate_model s st) (correlate_env env) (translate base)"
  shows "value_to_smt_opt (eval s st env (FieldAccess base fname sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (FieldAccess base fname sp))"
proof -
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate base)
               = value_to_smt_opt (eval s st env base)" using ih by simp
  show ?thesis using ih' value_field_lookup_correlated
    by (cases "eval s st env base") simp_all
qed

lemma soundness_SetInsert:
  assumes ihe: "value_to_smt_opt (eval s st env elem)
                  = smtEval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smtEval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetInsert elem set_e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (SetInsert elem set_e sp))"
proof -
  have ihe': "smtEval (correlate_model s st) (correlate_env env) (translate elem)
                = value_to_smt_opt (eval s st env elem)" using ihe by simp
  have ihs': "smtEval (correlate_model s st) (correlate_env env) (translate set_e)
                = value_to_smt_opt (eval s st env set_e)" using ihs by simp
  show ?thesis
  proof (cases "eval s st env elem")
    case None thus ?thesis using ihe' by simp
  next
    case (Some va)
    show ?thesis
    proof (cases "eval s st env set_e")
      case None thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close> by (cases va) simp_all
    next
      case (Some vs)
      thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va; cases vs) (auto simp: Let_def dedupe_values_map_value_to_smt)
    qed
  qed
qed

lemma soundness_SetMember:
  assumes ihe: "value_to_smt_opt (eval s st env elem)
                  = smtEval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smtEval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetMember elem set_e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (SetMember elem set_e sp))"
proof -
  have ihe': "smtEval (correlate_model s st) (correlate_env env) (translate elem)
                = value_to_smt_opt (eval s st env elem)" using ihe by simp
  have ihs': "smtEval (correlate_model s st) (correlate_env env) (translate set_e)
                = value_to_smt_opt (eval s st env set_e)" using ihs by simp
  show ?thesis
  proof (cases "eval s st env elem")
    case None thus ?thesis using ihe' by simp
  next
    case (Some va)
    show ?thesis
    proof (cases "eval s st env set_e")
      case None thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close> by (cases va) simp_all
    next
      case (Some vs)
      thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va; cases vs) auto
    qed
  qed
qed

lemma soundness_SetBin:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smtEval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smtEval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (SetBin op l r sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (SetBin op l r sp))"
proof -
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r)
                = value_to_smt_opt (eval s st env r)" using ihr by simp
  show ?thesis
  proof (cases "eval s st env l")
    case None thus ?thesis using ihl' by (cases op) simp_all
  next
    case (Some a)
    show ?thesis
    proof (cases "eval s st env r")
      case None thus ?thesis using ihl' ihr' Some by (cases op; cases a) simp_all
    next
      case (Some b)
      thus ?thesis using ihl' ihr' \<open>eval s st env l = Some a\<close>
        by (cases op; cases a; cases b)
           (simp_all add: set_union_values_map_value_to_smt
                          set_intersect_values_map_value_to_smt
                          set_diff_values_map_value_to_smt)
    qed
  qed
qed

lemma soundness_WithRec:
  assumes ihb: "value_to_smt_opt (eval s st env base)
                  = smtEval (correlate_model s st) (correlate_env env) (translate base)"
      and ihv: "value_to_smt_opt (eval s st env value_e)
                  = smtEval (correlate_model s st) (correlate_env env) (translate value_e)"
  shows "value_to_smt_opt (eval s st env (WithRec base fld value_e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (WithRec base fld value_e sp))"
proof -
  have ihb': "smtEval (correlate_model s st) (correlate_env env) (translate base)
                = value_to_smt_opt (eval s st env base)" using ihb by simp
  have ihv': "smtEval (correlate_model s st) (correlate_env env) (translate value_e)
                = value_to_smt_opt (eval s st env value_e)" using ihv by simp
  show ?thesis using ihb' ihv'
    by (cases "eval s st env base"; cases "eval s st env value_e") simp_all
qed

end
