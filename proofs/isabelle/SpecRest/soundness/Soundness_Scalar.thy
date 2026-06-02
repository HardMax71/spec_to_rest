theory Soundness_Scalar
  imports Soundness_Framework
begin

lemma soundness_UnNot:
  assumes ih: "value_to_smt_opt (eval s st env e)
                 = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNot e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (UnNot e sp))"
proof -
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate e)
               = value_to_smt_opt (eval s st env e)"
    using ih by simp
  show ?thesis
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
qed

lemma soundness_UnNeg:
  assumes ih: "value_to_smt_opt (eval s st env e)
                 = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNeg e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (UnNeg e sp))"
proof -
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate e)
               = value_to_smt_opt (eval s st env e)"
    using ih by simp
  show ?thesis
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
qed

lemma soundness_BoolBin:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smtEval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smtEval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (BoolBin op l r sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (BoolBin op l r sp))"
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
        by (cases op; cases a; cases b) auto
    qed
  qed
qed

lemma soundness_Arith:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smtEval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smtEval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith op l r sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (Arith op l r sp))"
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
           (auto simp: int_le_iff_lt_or_eq int_ge_iff_lt_or_eq
                       rat_le_iff_lt_or_eq rat_ge_iff_lt_or_eq ir_val_eq_def)
    qed
  qed
qed

lemma soundness_Cmp:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smtEval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smtEval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp op l r sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (Cmp op l r sp))"
proof -
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r)
                = value_to_smt_opt (eval s st env r)" using ihr by simp
  show ?thesis
  proof (cases "eval s st env l")
    case None
    show ?thesis
    proof (cases "eval s st env r")
      case None thus ?thesis using ihl' ihr' \<open>eval s st env l = None\<close> by (cases op) simp_all
    next
      case (Some b) thus ?thesis using ihl' ihr' \<open>eval s st env l = None\<close> by (cases op; cases b) simp_all
    qed
  next
    case (Some a)
    show ?thesis
    proof (cases "eval s st env r")
      case None thus ?thesis using ihl' ihr' Some by (cases op; cases a) simp_all
    next
      case (Some b)
      thus ?thesis using ihl' ihr' \<open>eval s st env l = Some a\<close>
        by (cases op; cases a; cases b)
           (auto simp: int_le_iff_lt_or_eq int_ge_iff_lt_or_eq
                       rat_le_iff_lt_or_eq rat_ge_iff_lt_or_eq ir_val_eq_def)
    qed
  qed
qed

lemma soundness_LetIn:
  assumes ihv: "\<And>e2. value_to_smt_opt (eval s st e2 v)
                       = smtEval (correlate_model s st) (correlate_env e2) (translate v)"
      and ihb: "\<And>e2. value_to_smt_opt (eval s st e2 body)
                       = smtEval (correlate_model s st) (correlate_env e2) (translate body)"
  shows "value_to_smt_opt (eval s st env (LetIn x v body sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (LetIn x v body sp))"
proof (cases "eval s st env v")
  case None
  have "smtEval (correlate_model s st) (correlate_env env) (translate v) = None"
    using ihv[of env] None by simp
  thus ?thesis using None by simp
next
  case (Some a)
  have ihv': "smtEval (correlate_model s st) (correlate_env env) (translate v) = Some (value_to_smt a)"
    using ihv[of env] Some by simp
  have ihb': "value_to_smt_opt (eval s st ((x, a) # env) body)
                = smtEval (correlate_model s st) (correlate_env ((x, a) # env)) (translate body)"
    using ihb[of "(x, a) # env"] .
  thus ?thesis using ihv' Some by simp
qed

lemma soundness_Ite:
  assumes ihc: "value_to_smt_opt (eval s st env c)
                  = smtEval (correlate_model s st) (correlate_env env) (translate c)"
      and iha: "value_to_smt_opt (eval s st env a)
                  = smtEval (correlate_model s st) (correlate_env env) (translate a)"
      and ihb: "value_to_smt_opt (eval s st env b)
                  = smtEval (correlate_model s st) (correlate_env env) (translate b)"
  shows "value_to_smt_opt (eval s st env (Ite c a b sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (Ite c a b sp))"
proof -
  have ihc': "smtEval (correlate_model s st) (correlate_env env) (translate c)
                = value_to_smt_opt (eval s st env c)" using ihc by simp
  show ?thesis
  proof (cases "eval s st env c")
    case None thus ?thesis using ihc' by simp
  next
    case (Some cv)
    thus ?thesis using ihc' iha ihb by (cases cv) (auto split: bool.splits)
  qed
qed

lemma soundness_NoneE:
  "value_to_smt_opt (eval s st env (NoneE sp))
     = smtEval (correlate_model s st) (correlate_env env) (translate (NoneE sp))"
  by simp

lemma soundness_SomeE:
  assumes ih: "value_to_smt_opt (eval s st env e)
                 = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (SomeE e sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (SomeE e sp))"
proof -
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate e)
               = value_to_smt_opt (eval s st env e)" using ih by simp
  show ?thesis using ih' by (cases "eval s st env e") simp_all
qed

lemma soundness_StrLit:
  "value_to_smt_opt (eval s st env (StrLit v sp))
     = smtEval (correlate_model s st) (correlate_env env) (translate (StrLit v sp))"
  by simp

end
