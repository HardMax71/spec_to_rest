theory Soundness
  imports
    SpecRest_Core.Translate
    SpecRest_Core.Semantics
    SpecRest_Core.IR_Helpers
    SpecRest_Core.IR_Analysis
    SpecRest_Core.IR_Lower
begin

section \<open>Value \<leftrightarrow> SmtVal correlation\<close>

fun value_to_smt :: "ir_value \<Rightarrow> smt_val" where
  "value_to_smt (VBool b)        = SBool b"
| "value_to_smt (VInt n)         = SInt n"
| "value_to_smt (VReal r)        = SReal r"
| "value_to_smt (VEnum en mem)   = SEnumElem en mem"
| "value_to_smt (VEntity en eid) = SEntityElem en eid"
| "value_to_smt (VSet members)   = SSet (map value_to_smt members)"
| "value_to_smt (VEntityWith base fld v) =
     SEntityWith (value_to_smt base) fld (value_to_smt v)"

abbreviation value_to_smt_opt :: "ir_value option \<Rightarrow> smt_val option" where
  "value_to_smt_opt \<equiv> map_option value_to_smt"

section \<open>Env / State / Schema \<leftrightarrow> SmtModel correlation\<close>

fun correlate_env :: "env \<Rightarrow> smt_env" where
  "correlate_env []            = []"
| "correlate_env ((k, v) # rest) = (k, value_to_smt v) # correlate_env rest"

definition correlate_model :: "schema \<Rightarrow> state \<Rightarrow> smt_model" where
  "correlate_model s st \<equiv>
     \<lparr> sm_sort_members =
         map (\<lambda>d. (enm_name d, enm_members d)) (sch_enums s),
       sm_const_vals =
         map (\<lambda>(k, v). (k, value_to_smt v)) (rt_scalars st),
       sm_pred_domain =
         map (\<lambda>(k, vs). (k, map value_to_smt vs)) (rt_relations st),
       sm_pred_lookup =
         map (\<lambda>(k, ps). (k, map (\<lambda>(a, b). (value_to_smt a, value_to_smt b)) ps))
             (rt_lookups st),
       sm_pred_fields =
         map (\<lambda>(k, fs). (k, map (\<lambda>(f, v). (f, value_to_smt v)) fs))
             (rt_entity_fields st)
     \<rparr>"

section \<open>value_to_smt shape lemmas\<close>

lemma value_to_smt_eq_SBool [simp]:
  "(value_to_smt v = SBool b) = (v = VBool b)"
  by (cases v) auto

lemma value_to_smt_eq_SInt [simp]:
  "(value_to_smt v = SInt n) = (v = VInt n)"
  by (cases v) auto

lemma value_to_smt_eq_SReal [simp]:
  "(value_to_smt v = SReal r) = (v = VReal r)"
  by (cases v) auto

lemma value_to_smt_eq_SEnumElem [simp]:
  "(value_to_smt v = SEnumElem en mem) = (v = VEnum en mem)"
  by (cases v) auto

lemma value_to_smt_eq_SEntityElem [simp]:
  "(value_to_smt v = SEntityElem en eid) = (v = VEntity en eid)"
  by (cases v) auto

lemma value_to_smt_eq_SSet:
  "value_to_smt v = SSet xs \<Longrightarrow> \<exists>ms. v = VSet ms \<and> xs = map value_to_smt ms"
  by (cases v) auto

lemma SBool_eq_value_to_smt [simp]:
  "(SBool b = value_to_smt v) = (v = VBool b)"
  by (cases v) auto

lemma SInt_eq_value_to_smt [simp]:
  "(SInt n = value_to_smt v) = (v = VInt n)"
  by (cases v) auto

lemma SReal_eq_value_to_smt [simp]:
  "(SReal r = value_to_smt v) = (v = VReal r)"
  by (cases v) auto

lemma map_value_to_smt_inj:
  assumes "\<And>x. x \<in> set xs \<Longrightarrow> (\<forall>y. value_to_smt x = value_to_smt y \<longrightarrow> x = y)"
  shows "(map value_to_smt xs = map value_to_smt ys) = (xs = ys)"
  using assms
proof (induction xs arbitrary: ys)
  case Nil thus ?case by (cases ys) auto
next
  case (Cons x xs)
  thus ?case by (cases ys) auto
qed

lemma value_to_smt_inj [simp]:
  "(value_to_smt v1 = value_to_smt v2) = (v1 = v2)"
proof (induction v1 arbitrary: v2)
  case (VBool b) thus ?case by (cases v2) auto
next
  case (VInt n) thus ?case by (cases v2) auto
next
  case (VReal r) thus ?case by (cases v2) auto
next
  case (VEnum en mem) thus ?case by (cases v2) auto
next
  case (VEntity en eid) thus ?case by (cases v2) auto
next
  case (VSet xs)
  show ?case
  proof (cases v2)
    case (VSet ys)
    have "(map value_to_smt xs = map value_to_smt ys) = (xs = ys)"
    proof (rule map_value_to_smt_inj)
      fix x assume "x \<in> set xs"
      thus "\<forall>y. value_to_smt x = value_to_smt y \<longrightarrow> x = y"
        using VSet.IH by blast
    qed
    thus ?thesis using \<open>v2 = VSet ys\<close> by simp
  qed auto
next
  case (VEntityWith base fld v)
  show ?case
  proof (cases v2)
    case (VEntityWith base' fld' v')
    thus ?thesis using VEntityWith.IH(1)[of base'] VEntityWith.IH(2)[of v'] by simp
  qed auto
qed

lemma map_value_to_smt_inj_simp [simp]:
  "(map value_to_smt xs = map value_to_smt ys) = (xs = ys)"
proof (induction xs arbitrary: ys)
  case Nil show ?case by (cases ys) auto
next
  case (Cons x xs')
  show ?case by (cases ys) (auto simp: Cons.IH)
qed

section \<open>Correlation lemmas\<close>

lemma correlate_env_lookup [simp]:
  "smt_env_lookup (correlate_env env) x = map_option value_to_smt (env_lookup env x)"
  by (induction env)
     (auto simp: smt_env_lookup_def env_lookup_def split: if_splits)

lemma map_of_map_value_eq:
  "map_of (map (\<lambda>(k, v). (k, f v)) xs) x = map_option f (map_of xs x)"
  by (induction xs) (auto split: if_splits)

lemma map_of_map_value_eq_eta:
  "map_of (map (\<lambda>p. (fst p, f (snd p))) xs) x = map_option f (map_of xs x)"
  by (induction xs) (auto split: if_splits)

lemma correlate_model_lookup_const [simp]:
  "smt_model_lookup_const (correlate_model s st) x
     = map_option value_to_smt (state_lookup_scalar st x)"
  by (simp add: smt_model_lookup_const_def correlate_model_def
                state_lookup_scalar_def map_of_map_value_eq)

lemma correlate_model_lookup_rel [simp]:
  "smt_model_lookup_rel (correlate_model s st) name
     = map_option (map value_to_smt) (state_relation_domain st name)"
  by (simp add: smt_model_lookup_rel_def correlate_model_def
                state_relation_domain_def map_of_map_value_eq)

lemma map_of_map_pair_enum [simp]:
  "map_of (map (\<lambda>d. (enm_name d, enm_members d)) xs) en
     = map_option enm_members (find (\<lambda>d. enm_name d = en) xs)"
  by (induction xs) auto

lemma correlate_model_lookup_sort_members:
  "schema_lookup_enum s en = Some d
   \<Longrightarrow> smt_model_lookup_sort_members (correlate_model s st) en = Some (enm_members d)"
  by (simp add: smt_model_lookup_sort_members_def correlate_model_def schema_lookup_enum_def)

lemma correlate_model_lookup_sort_members_eq:
  "smt_model_lookup_sort_members (correlate_model s st) en
     = map_option enm_members (schema_lookup_enum s en)"
  by (simp add: smt_model_lookup_sort_members_def correlate_model_def schema_lookup_enum_def)

section \<open>Per-case soundness — atoms\<close>

section \<open>Per-case soundness — propositional / arithmetic\<close>

section \<open>Per-case soundness — propositional / arithmetic / comparison\<close>

text \<open>Following the Lean pattern (`Soundness.lean` lines 596-1010): per-(op,
shape) success-path theorems take CONCRETE hypotheses about `eval` returning
`Some (VBool _)` / `Some (VInt _)` and close trivially. The aggregate
`soundness_bool_bin` etc. (which case-split on every shape) close in Isabelle
with sufficient case-bashing, but per-op forms are cleaner and mirror the
Lean track's structure.\<close>

text \<open>Helper: an IH derives the smtEval result directly given a concrete
eval result. Used by every binary success-path lemma below.\<close>

lemma smtEval_of_eval_VInt:
  assumes "eval s st env e = Some (VInt n)"
      and "value_to_smt_opt (eval s st env e)
            = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smtEval (correlate_model s st) (correlate_env env) (translate e) = Some (SInt n)"
  using assms by simp

lemma smtEval_of_eval_VBool:
  assumes "eval s st env e = Some (VBool b)"
      and "value_to_smt_opt (eval s st env e)
            = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smtEval (correlate_model s st) (correlate_env env) (translate e) = Some (SBool b)"
  using assms by simp

lemma smtEval_of_eval_Some:
  assumes "eval s st env e = Some v"
      and "value_to_smt_opt (eval s st env e)
            = smtEval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smtEval (correlate_model s st) (correlate_env env) (translate e) = Some (value_to_smt v)"
  using assms by simp

section \<open>LetIn binder\<close>

section \<open>EnumAccess + Prime + Pre (single-state) + WithRec\<close>

section \<open>Helper correlations: list/set/find map\<close>

lemma contains_value_map_value_to_smt [simp]:
  "contains_smt_val (map value_to_smt vs) (value_to_smt v) = contains_value vs v"
  by (induction vs) auto

lemma dedupe_values_map_value_to_smt:
  "map value_to_smt (dedupe_values vs) = dedupe_smt_vals (map value_to_smt vs)"
proof (induction vs)
  case Nil show ?case by simp
next
  case (Cons v vs)
  have ih: "map value_to_smt (dedupe_values vs) = dedupe_smt_vals (map value_to_smt vs)"
    by (rule Cons.IH)
  have eq: "contains_smt_val (dedupe_smt_vals (map value_to_smt vs)) (value_to_smt v)
              = contains_value (dedupe_values vs) v"
    using ih[symmetric] contains_value_map_value_to_smt by metis
  show ?case
    by (simp add: Let_def ih eq)
qed

lemma dedupe_smt_vals_map_value_to_smt:
  "dedupe_smt_vals (map value_to_smt vs) = map value_to_smt (dedupe_values vs)"
  using dedupe_values_map_value_to_smt[symmetric] by simp

lemma contains_value_dedupe_eq [simp]:
  "contains_value (dedupe_values xs) v = contains_value xs v"
  by (induction xs) (auto simp: Let_def)

lemma contains_smt_val_dedupe_eq [simp]:
  "contains_smt_val (dedupe_smt_vals xs) v = contains_smt_val xs v"
  by (induction xs) (auto simp: Let_def)

lemma contains_smt_val_map_VSet [simp]:
  "contains_smt_val (map value_to_smt vs) (SSet (map value_to_smt xs))
     = contains_value vs (VSet xs)"
  using contains_value_map_value_to_smt[of vs "VSet xs"] by simp

lemma contains_smt_val_map_VEntityWith [simp]:
  "contains_smt_val (map value_to_smt vs)
                    (SEntityWith (value_to_smt b) f (value_to_smt v))
     = contains_value vs (VEntityWith b f v)"
  using contains_value_map_value_to_smt[of vs "VEntityWith b f v"] by simp

lemma contains_smt_val_map_SBool [simp]:
  "contains_smt_val (map value_to_smt vs) (SBool b) = contains_value vs (VBool b)"
  using contains_value_map_value_to_smt[of vs "VBool b"] by simp

lemma contains_smt_val_map_SInt [simp]:
  "contains_smt_val (map value_to_smt vs) (SInt n) = contains_value vs (VInt n)"
  using contains_value_map_value_to_smt[of vs "VInt n"] by simp

lemma contains_smt_val_map_SReal [simp]:
  "contains_smt_val (map value_to_smt vs) (SReal r) = contains_value vs (VReal r)"
  using contains_value_map_value_to_smt[of vs "VReal r"] by simp

lemma contains_smt_val_map_SEnumElem [simp]:
  "contains_smt_val (map value_to_smt vs) (SEnumElem en mem) = contains_value vs (VEnum en mem)"
  using contains_value_map_value_to_smt[of vs "VEnum en mem"] by simp

lemma contains_smt_val_map_SEntityElem [simp]:
  "contains_smt_val (map value_to_smt vs) (SEntityElem en eid) = contains_value vs (VEntity en eid)"
  using contains_value_map_value_to_smt[of vs "VEntity en eid"] by simp


lemma set_union_values_map_value_to_smt:
  "map value_to_smt (set_union_values l r)
     = set_union_smt_vals (map value_to_smt l) (map value_to_smt r)"
  unfolding set_union_values_def set_union_smt_vals_def
  by (simp add: dedupe_values_map_value_to_smt)

lemma filter_contains_map_value_to_smt:
  "filter (\<lambda>v. contains_smt_val (map value_to_smt r) v) (map value_to_smt l)
     = map value_to_smt (filter (\<lambda>v. contains_value r v) l)"
  by (induction l) auto

lemma filter_not_contains_map_value_to_smt:
  "filter (\<lambda>v. \<not> contains_smt_val (map value_to_smt r) v) (map value_to_smt l)
     = map value_to_smt (filter (\<lambda>v. \<not> contains_value r v) l)"
  by (induction l) auto

lemma set_intersect_values_map_value_to_smt:
  "map value_to_smt (set_intersect_values l r)
     = set_intersect_smt_vals (map value_to_smt l) (map value_to_smt r)"
  unfolding set_intersect_values_def set_intersect_smt_vals_def
  by (simp add: dedupe_values_map_value_to_smt filter_contains_map_value_to_smt)

lemma set_diff_values_map_value_to_smt:
  "map value_to_smt (set_diff_values l r)
     = set_diff_smt_vals (map value_to_smt l) (map value_to_smt r)"
  unfolding set_diff_values_def set_diff_smt_vals_def
  by (simp add: dedupe_values_map_value_to_smt filter_not_contains_map_value_to_smt)

section \<open>Set op soundness\<close>

section \<open>State-touching: Member, CardRel, IndexRel\<close>

lemma find_map_value_to_smt:
  "find (\<lambda>p. fst p = value_to_smt kv)
        (map (\<lambda>p. (value_to_smt (fst p), value_to_smt (snd p))) ps)
     = map_option (\<lambda>p. (value_to_smt (fst p), value_to_smt (snd p)))
                   (find (\<lambda>p. fst p = kv) ps)"
  by (induction ps) auto

lemma correlate_model_lookup_key:
  "smt_model_lookup_key (correlate_model s st) rel_name (value_to_smt kv)
     = map_option value_to_smt (state_lookup_key st rel_name kv)"
proof (cases "map_of (rt_lookups st) rel_name")
  case None
  thus ?thesis
    unfolding smt_model_lookup_key_def state_lookup_key_def correlate_model_def
    by (simp add: map_of_map_value_eq)
next
  case (Some pairs)
  have "map_of (map (\<lambda>(k, ps). (k, map (\<lambda>(a, b). (value_to_smt a, value_to_smt b)) ps))
                    (rt_lookups st)) rel_name
          = Some (map (\<lambda>(a, b). (value_to_smt a, value_to_smt b)) pairs)"
    using Some by (simp add: map_of_map_value_eq)
  thus ?thesis using Some
    unfolding smt_model_lookup_key_def state_lookup_key_def correlate_model_def
    by (simp add: find_map_value_to_smt option.map_comp comp_def split_def)
qed

text \<open>Issue #210 (M_L.4.l): \<open>peelSmtRelationRef\<close> commutes with
  \<open>translate\<close>. Trivial structural induction over the four shapes
  \<open>peel_relation_ref\<close> recognises (\<open>Ident\<close>, \<open>Pre Ident\<close>, \<open>Prime Ident\<close>,
  wildcard); the wildcard case relies on \<open>translate\<close>'s totality and the
  fact that \<open>peelSmtRelationRef\<close> only fires on \<open>TVar\<close>/\<open>TPre TVar\<close>/
  \<open>TPrime TVar\<close>.\<close>

lemma peel_smt_translate_BoolBin [simp]:
  "peelSmtRelationRef (translate (BoolBin op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_Arith [simp]:
  "peelSmtRelationRef (translate (Arith op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_Cmp [simp]:
  "peelSmtRelationRef (translate (Cmp op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_SetBin [simp]:
  "peelSmtRelationRef (translate (SetBin op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_BoolBin [simp]:
  "peelSmtRelationRef (TPrime (translate (BoolBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_Arith [simp]:
  "peelSmtRelationRef (TPrime (translate (Arith op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_Cmp [simp]:
  "peelSmtRelationRef (TPrime (translate (Cmp op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_SetBin [simp]:
  "peelSmtRelationRef (TPrime (translate (SetBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_BoolBin [simp]:
  "peelSmtRelationRef (TPre (translate (BoolBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_Arith [simp]:
  "peelSmtRelationRef (TPre (translate (Arith op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_Cmp [simp]:
  "peelSmtRelationRef (TPre (translate (Cmp op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_SetBin [simp]:
  "peelSmtRelationRef (TPre (translate (SetBin op l r sp))) = None"
  by (cases op) simp_all

lemma peelSmtRelationRef_translate:
  "peelSmtRelationRef (translate base) = peel_relation_ref base"
proof (induction base rule: peel_relation_ref.induct)
qed simp_all

section \<open>FieldAccess\<close>

lemma map_of_map_inner_value_to_smt:
  "map_of (map (\<lambda>(f, v). (f, value_to_smt v)) fs) fld
     = map_option value_to_smt (map_of fs fld)"
  by (induction fs) (auto split: if_splits)

lemma correlate_model_lookup_field:
  "smt_model_lookup_field (correlate_model s st) eid fld
     = map_option value_to_smt (state_lookup_field st eid fld)"
proof (cases "map_of (rt_entity_fields st) eid")
  case None
  thus ?thesis
    unfolding smt_model_lookup_field_def state_lookup_field_def correlate_model_def
    by (simp add: map_of_map_value_eq_eta split_def)
next
  case (Some fs)
  thus ?thesis
    unfolding smt_model_lookup_field_def state_lookup_field_def correlate_model_def
    by (simp add: map_of_map_value_eq_eta map_of_map_inner_value_to_smt split_def)
qed

lemma value_field_lookup_correlated:
  "smt_val_field_lookup (correlate_model s st) (value_to_smt v) fld
     = map_option value_to_smt (value_field_lookup st v fld)"
proof (induction v)
  case (VEntity en eid)
  show ?case by (simp add: correlate_model_lookup_field)
next
  case (VEntityWith base ovf ovv)
  thus ?case by simp
qed (simp_all)

section \<open>Quantifier soundness\<close>

text \<open>Mutual-induction style: we induct on the quantifier domain (members for
forall_enum, value list for forall_rel), threading the body soundness as a
universally quantified assumption (covers every env extended by a fresh bind).\<close>

lemma eval_forall_enum_correlated:
  assumes ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smtEval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval_forall_enum s st env var en members body)
           = smtEval_forall_enum (correlate_model s st) (correlate_env env) var en members
                                   (translate body)"
proof (induction members)
  case Nil show ?case by simp
next
  case (Cons mem rest)
  let ?env' = "(var, VEnum en mem) # env"
  let ?senv' = "(var, SEnumElem en mem) # correlate_env env"
  have body: "smtEval (correlate_model s st) ?senv' (translate body)
                = value_to_smt_opt (eval s st ?env' body)"
    using ihbody[of ?env'] by simp
  have ih_sym: "smtEval_forall_enum (correlate_model s st) (correlate_env env) var en rest
                                      (translate body)
                  = value_to_smt_opt (eval_forall_enum s st env var en rest body)"
    using Cons.IH by simp
  show ?case
  proof (cases "eval s st ?env' body")
    case None thus ?thesis using body by simp
  next
    case (Some r)
    show ?thesis
    proof (cases r)
      case (VBool b)
      show ?thesis
      proof (cases "eval_forall_enum s st env var en rest body")
        case None thus ?thesis using Some VBool body ih_sym by simp
      next
        case (Some r')
        show ?thesis
          using \<open>eval s st ?env' body = Some r\<close> VBool Some body ih_sym
          by (cases r') simp_all
      qed
    qed (use Some body in simp_all)
  qed
qed

lemma eval_forall_rel_correlated:
  assumes ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smtEval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval_forall_rel s st env var rd body)
           = smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                                  (map value_to_smt rd) (translate body)"
proof (induction rd)
  case Nil show ?case by simp
next
  case (Cons v rest)
  let ?env' = "(var, v) # env"
  let ?senv' = "(var, value_to_smt v) # correlate_env env"
  have body: "smtEval (correlate_model s st) ?senv' (translate body)
                = value_to_smt_opt (eval s st ?env' body)"
    using ihbody[of ?env'] by simp
  have ih_sym: "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                                     (map value_to_smt rest) (translate body)
                  = value_to_smt_opt (eval_forall_rel s st env var rest body)"
    using Cons.IH by simp
  show ?case
  proof (cases "eval s st ?env' body")
    case None thus ?thesis using body by simp
  next
    case (Some r)
    show ?thesis
    proof (cases r)
      case (VBool b)
      show ?thesis
      proof (cases "eval_forall_rel s st env var rest body")
        case None thus ?thesis using Some VBool body ih_sym by simp
      next
        case (Some r')
        show ?thesis
          using \<open>eval s st ?env' body = Some r\<close> VBool Some body ih_sym
          by (cases r') simp_all
      qed
    qed (use Some body in simp_all)
  qed
qed

lemma soundness_forall_enum_known:
  assumes hd: "schema_lookup_enum s en = Some d"
      and ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smtEval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallEnum var en body sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (ForallEnum var en body sp))"
  using correlate_model_lookup_sort_members[OF hd, of st]
        eval_forall_enum_correlated[OF ihbody, where members="enm_members d" and var=var and en=en and env=env]
  by (simp add: hd)

lemma soundness_forall_rel_known:
  assumes hd: "state_relation_domain st rel_name = Some d"
      and ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smtEval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallRel var rel_name body sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (ForallRel var rel_name body sp))"
  using hd eval_forall_rel_correlated[OF ihbody, where rd=d and var=var and env=env]
  by simp

section \<open>Phase 4 — universal soundness theorem\<close>

text \<open>The universal `soundness` meta-theorem (M_L.2 in the Lean track,
research doc §8.3). Each constructor dispatches: success-path to the
per-case lemma above, failure-path closes by case-analysis on `eval`'s
result threading the IH backwards. The Isabelle proof is ~10× shorter
than Lean's 5374-LoC version due to stronger automation.\<close>

text \<open>Step lemmas: each handles a single Expr constructor's contribution to
the universal soundness theorem. Each is a top-level `lemma` so the Isar
runtime can compile their proofs in parallel — this is the load-bearing
performance trick (single-file `auto split: ir_value.splits option.splits
smt_val.splits` chains spawn 4×7×7 = 196 subgoals × deep search per Expr
case, and the universal theorem combines those serially within one `proof
... qed`; lifting to top-level lemmas with structured `cases + simp_all`
parallelizes across cases and avoids `auto`'s search explosion).\<close>

lemma int_ge_iff_lt_or_eq: "(a::int) \<ge> b \<longleftrightarrow> b < a \<or> a = b"
  by linarith

lemma int_le_iff_lt_or_eq: "(a::int) \<le> b \<longleftrightarrow> a < b \<or> a = b"
  by linarith

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
  case (UnNot e sp)
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate e) = value_to_smt_opt (eval s st env e)"
    using UnNot.IH by simp
  show ?case
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
next
  case (UnNeg e sp)
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate e) = value_to_smt_opt (eval s st env e)"
    using UnNeg.IH by simp
  show ?case
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
next
  case (BoolBin op l r sp)
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l) = value_to_smt_opt (eval s st env l)"
    using BoolBin.IH(1) by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r) = value_to_smt_opt (eval s st env r)"
    using BoolBin.IH(2) by simp
  show ?case
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
next
  case (Arith op l r sp)
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l) = value_to_smt_opt (eval s st env l)"
    using Arith.IH(1) by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r) = value_to_smt_opt (eval s st env r)"
    using Arith.IH(2) by simp
  show ?case
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
           (auto simp: int_le_iff_lt_or_eq int_ge_iff_lt_or_eq)
    qed
  qed
next
  case (Cmp op l r sp)
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l) = value_to_smt_opt (eval s st env l)"
    using Cmp.IH(1) by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r) = value_to_smt_opt (eval s st env r)"
    using Cmp.IH(2) by simp
  show ?case
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
           (auto simp: int_le_iff_lt_or_eq int_ge_iff_lt_or_eq)
    qed
  qed
next
  case (LetIn x v body sp)
  show ?case
  proof (cases "eval s st env v")
    case None
    have ihv': "smtEval (correlate_model s st) (correlate_env env) (translate v) = None"
      using LetIn.IH(1)[of env] None by simp
    thus ?thesis using None by simp
  next
    case (Some a)
    have ihv': "smtEval (correlate_model s st) (correlate_env env) (translate v) = Some (value_to_smt a)"
      using LetIn.IH(1)[of env] Some by simp
    have ihb': "value_to_smt_opt (eval s st ((x, a) # env) body)
                  = smtEval (correlate_model s st) (correlate_env ((x, a) # env)) (translate body)"
      using LetIn.IH(2)[of "(x, a) # env"] .
    thus ?thesis using ihv' Some by simp
  qed
next
  case (EnumAccess en mem sp)
  show ?case
    by (simp add: correlate_model_lookup_sort_members_eq split: option.splits)
next
  case (Member elem rel_name sp)
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate elem) = value_to_smt_opt (eval s st env elem)"
    using Member.IH by simp
  show ?case using ih'
    by (cases "eval s st env elem"; cases "state_relation_domain st rel_name")
       (simp_all split: option.splits)
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
  case (IndexRel base key sp)
  have ihk': "smtEval (correlate_model s st) (correlate_env env) (translate key) = value_to_smt_opt (eval s st env key)"
    using IndexRel.IH(2) by simp
  have peel_eq: "peelSmtRelationRef (translate base) = peel_relation_ref base"
    by (rule peelSmtRelationRef_translate)
  show ?case
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
next
  case (FieldAccess base fname sp)
  have ih': "smtEval (correlate_model s st) (correlate_env env) (translate base) = value_to_smt_opt (eval s st env base)"
    using FieldAccess.IH by simp
  show ?case using ih' value_field_lookup_correlated
    by (cases "eval s st env base") simp_all
next
  case (SetEmpty sp) show ?case by simp
next
  case (SetInsert elem set_e sp)
  have ihe': "smtEval (correlate_model s st) (correlate_env env) (translate elem) = value_to_smt_opt (eval s st env elem)"
    using SetInsert.IH(1) by simp
  have ihs': "smtEval (correlate_model s st) (correlate_env env) (translate set_e) = value_to_smt_opt (eval s st env set_e)"
    using SetInsert.IH(2) by simp
  show ?case
  proof (cases "eval s st env elem")
    case None thus ?thesis using ihe' by simp
  next
    case (Some va)
    show ?thesis
    proof (cases "eval s st env set_e")
      case None thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va) simp_all
    next
      case (Some vs)
      thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va; cases vs) (auto simp: Let_def dedupe_values_map_value_to_smt)
    qed
  qed
next
  case (SetMember elem set_e sp)
  have ihe': "smtEval (correlate_model s st) (correlate_env env) (translate elem) = value_to_smt_opt (eval s st env elem)"
    using SetMember.IH(1) by simp
  have ihs': "smtEval (correlate_model s st) (correlate_env env) (translate set_e) = value_to_smt_opt (eval s st env set_e)"
    using SetMember.IH(2) by simp
  show ?case
  proof (cases "eval s st env elem")
    case None thus ?thesis using ihe' by simp
  next
    case (Some va)
    show ?thesis
    proof (cases "eval s st env set_e")
      case None thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va) simp_all
    next
      case (Some vs)
      thus ?thesis using ihe' ihs' \<open>eval s st env elem = Some va\<close>
        by (cases va; cases vs) auto
    qed
  qed
next
  case (SetBin op l r sp)
  have ihl': "smtEval (correlate_model s st) (correlate_env env) (translate l) = value_to_smt_opt (eval s st env l)"
    using SetBin.IH(1) by simp
  have ihr': "smtEval (correlate_model s st) (correlate_env env) (translate r) = value_to_smt_opt (eval s st env r)"
    using SetBin.IH(2) by simp
  show ?case
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
next
  case (WithRec base fld value_e sp)
  have ihb': "smtEval (correlate_model s st) (correlate_env env) (translate base) = value_to_smt_opt (eval s st env base)"
    using WithRec.IH(1) by simp
  have ihv': "smtEval (correlate_model s st) (correlate_env env) (translate value_e) = value_to_smt_opt (eval s st env value_e)"
    using WithRec.IH(2) by simp
  show ?case using ihb' ihv'
    by (cases "eval s st env base"; cases "eval s st env value_e") simp_all
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

text \<open>Phase 9j (dual of 9i). \<open>requiresAlloy_imp_lower_none\<close> proved the
  Alloy-routed fragment maps to \<open>None\<close>; here a syntactic \<open>wf_z3\<close>
  predicate carves out the Z3-verifiable subset and we prove
  \<open>wf_z3 e \<Longrightarrow> lower enums e \<noteq> None\<close>. This upgrades the
  \<open>Trust.classify\<close> runtime oracle (\<open>lower(e).isDefined \<Longrightarrow>
  Sound\<close>) to a proven syntactic guarantee: in-fragment specs are Sound,
  never best-effort. \<open>IndexF\<close> requires a relation-ref base shape (the
  \<open>peel_relation_ref\<close> side-condition); quantifier bodies require every
  binding domain to be an identifier (the \<open>lower_forall_bindings\<close>
  totality condition, dual to \<open>lfb_none_of_alloy\<close>).\<close>

fun is_ident_dom :: "quantifier_binding_full \<Rightarrow> bool" where
  "is_ident_dom (QuantifierBindingFull _ (IdentifierF _ _) _ _) = True"
| "is_ident_dom _ = False"

fun wf_z3_bindings :: "quantifier_binding_full list \<Rightarrow> bool" where
  "wf_z3_bindings [] = False"
| "wf_z3_bindings [b] = is_ident_dom b"
| "wf_z3_bindings (b # rest) = (is_ident_dom b \<and> wf_z3_bindings rest)"

fun rel_ref_shape :: "expr_full \<Rightarrow> bool" where
  "rel_ref_shape (IdentifierF _ _)            = True"
| "rel_ref_shape (PreF (IdentifierF _ _) _)   = True"
| "rel_ref_shape (PrimeF (IdentifierF _ _) _) = True"
| "rel_ref_shape _                            = False"

fun wf_z3 :: "expr_full \<Rightarrow> bool"
and wf_z3_list :: "expr_full list \<Rightarrow> bool"
and wf_z3_fields :: "field_assign_full list \<Rightarrow> bool"
where
  "wf_z3 (BoolLitF _ _)            = True"
| "wf_z3 (IntLitF _ _)             = True"
| "wf_z3 (IdentifierF _ _)         = True"
| "wf_z3 (UnaryOpF op e _)         =
     (case op of UNot \<Rightarrow> wf_z3 e | UNegate \<Rightarrow> wf_z3 e
        | UCardinality \<Rightarrow> (\<exists>x s. e = IdentifierF x s)
        | UPower \<Rightarrow> False)"
| "wf_z3 (BinaryOpF op l r _)      =
     (case op of BSubset \<Rightarrow> False
        | BIn \<Rightarrow> (wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r))
        | BNotIn \<Rightarrow> (wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r))
        | _ \<Rightarrow> wf_z3 l \<and> wf_z3 r)"
| "wf_z3 (LetF _ v b _)            = (wf_z3 v \<and> wf_z3 b)"
| "wf_z3 (EnumAccessF base _ _)    = (\<exists>en s. base = IdentifierF en s)"
| "wf_z3 (FieldAccessF base _ _)   = wf_z3 base"
| "wf_z3 (IndexF base key _)       = (rel_ref_shape base \<and> wf_z3 key)"
| "wf_z3 (PrimeF e _)              = wf_z3 e"
| "wf_z3 (PreF e _)                = wf_z3 e"
| "wf_z3 (WithF base ups _)        = (wf_z3 base \<and> wf_z3_fields ups)"
| "wf_z3 (SetLiteralF es _)        = wf_z3_list es"
| "wf_z3 (QuantifierF _ bs body _) = (wf_z3_bindings bs \<and> wf_z3 body)"
| "wf_z3 (FloatLitF s _)           = (decimalToRat s \<noteq> None)"
| "wf_z3 (StringLitF _ _)          = False"
| "wf_z3 (NoneLitF _)              = False"
| "wf_z3 (LambdaF _ _ _)           = False"
| "wf_z3 (CallF _ _ _)             = False"
| "wf_z3 (ConstructorF _ _ _)      = False"
| "wf_z3 (MapLiteralF _ _)         = False"
| "wf_z3 (SeqLiteralF _ _)         = False"
| "wf_z3 (SetComprehensionF _ _ _ _) = False"
| "wf_z3 (SomeWrapF _ _)           = False"
| "wf_z3 (TheF _ _ _ _)            = False"
| "wf_z3 (MatchesF _ _ _)          = False"
| "wf_z3 (IfF _ _ _ _)             = False"
| "wf_z3_list []                   = True"
| "wf_z3_list (e # rest)           = (wf_z3 e \<and> wf_z3_list rest)"
| "wf_z3_fields []                 = True"
| "wf_z3_fields (FieldAssignFull _ v _ # rest) = (wf_z3 v \<and> wf_z3_fields rest)"

lemma wf_z3_fields_iff:
  "wf_z3_fields updates
     \<longleftrightarrow> (\<forall>fld v sp. FieldAssignFull fld v sp \<in> set updates \<longrightarrow> wf_z3 v)"
proof (induction updates)
  case Nil thus ?case by simp
next
  case (Cons hd rest)
  thus ?case by (cases hd) auto
qed

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
  by (cases base rule: rel_ref_shape.cases) auto

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

lemma lower_binop_some:
  assumes l: "wf_z3 l \<Longrightarrow> lower enums l \<noteq> None"
      and r: "wf_z3 r \<Longrightarrow> lower enums r \<noteq> None"
      and wf: "wf_z3 (BinaryOpF op l r sp)"
  shows "lower enums (BinaryOpF op l r sp) \<noteq> None"
proof -
  have inout: "lower enums (BinaryOpF op l r sp) \<noteq> None"
    if opc: "op = BIn \<or> op = BNotIn"
  proof -
    from wf opc have wl: "wf_z3 l"
        and rd: "(\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r" by auto
    from l wl obtain l' where l': "lower enums l = Some l'" by blast
    from rd show ?thesis
    proof
      assume "\<exists>rel s. r = IdentifierF rel s"
      then obtain rel s where "r = IdentifierF rel s" by blast
      thus ?thesis using l' opc by auto
    next
      assume "wf_z3 r"
      with r obtain r' where "lower enums r = Some r'" by blast
      with l' opc show ?thesis by (cases r) auto
    qed
  qed
  show ?thesis
  proof (cases op)
    case BIn
    thus ?thesis using inout by blast
  next
    case BNotIn
    thus ?thesis using inout by blast
  next
    case BSubset
    with wf show ?thesis by simp
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
      by (rule lower_binop_some[OF l r less.prems[unfolded BinaryOpF]])
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
  by (cases base rule: peelRelationRefFull.cases) auto

lemma peelRelationRefFull_lower:
  assumes "peelRelationRefFull base = Some rel"
      and "lower enums base = Some base'"
  shows "peel_relation_ref base' = Some rel"
  using assms by (cases base rule: peelRelationRefFull.cases) auto

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

text \<open>Phase H2 -> 9j bridge. Every well-typed expression in the
  H2 arith / cmp / bool fragment lies in the Phase 9j \<open>wf_z3\<close>
  subset. Composed with \<open>wf_z3_imp_lower_some_expr\<close> this gives
  the first half of the progress chain:
    well-typed e ==> wf_z3 e ==> lower enums e \<noteq> None.
  The H3 type-safety theorem will then complete the chain via
  the H1 preservation lemmas on the lowered \<open>expr\<close>.\<close>

lemma well_typed_imp_wf_z3:
  "expr_has_ty \<Gamma> e t \<Longrightarrow> wf_z3 e"
proof (induction rule: expr_has_ty.induct)
  case (T_Arith \<Gamma> l r op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Eq \<Gamma> l t r op sp)
  thus ?case by (cases op) auto
next
  case (T_Cmp_Ord \<Gamma> l r op sp)
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
  thus ?case by simp
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

corollary well_typed_imp_lower_some:
  "expr_has_ty \<Gamma> e t \<Longrightarrow> lower enums e \<noteq> None"
  by (rule wf_z3_imp_lower_some_expr) (rule well_typed_imp_wf_z3)

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
      and "\<And>e_sub a. lower enums e = Some e_sub \<Longrightarrow> eval sch st env e_sub = Some a \<Longrightarrow> value_has_ty \<Gamma> a TInt"
  shows "value_has_ty \<Gamma> v TInt"
proof -
  from assms(1) obtain e_sub where
       sub_low: "lower enums e = Some e_sub"
   and e_eq: "e' = UnNeg e_sub sp"
    by (auto split: option.splits)
  from assms(2) e_eq have ev: "eval sch st env (UnNeg e_sub sp) = Some v"
    by simp
  then obtain a where ea: "eval sch st env e_sub = Some a"
    by (cases "eval sch st env e_sub") auto
  have "value_has_ty \<Gamma> a TInt" using assms(3)[OF sub_low ea] .
  hence "\<exists>n. a = VInt n" by (cases a) (auto elim: value_has_ty.cases)
  then obtain n where "a = VInt n" ..
  with ea ev have "v = VInt (- n)" by simp
  thus "value_has_ty \<Gamma> v TInt" by simp
qed

lemma h3_pres_Arith:
  assumes "op \<in> {BAdd, BSub, BMul, BDiv}"
      and "lower enums (BinaryOpF op l r sp) = Some e'"
      and "eval sch st env e' = Some v"
      and "\<And>l' a. lower enums l = Some l' \<Longrightarrow> eval sch st env l' = Some a \<Longrightarrow> value_has_ty \<Gamma> a TInt"
      and "\<And>r' b. lower enums r = Some r' \<Longrightarrow> eval sch st env r' = Some b \<Longrightarrow> value_has_ty \<Gamma> b TInt"
  shows "value_has_ty \<Gamma> v TInt"
proof -
  from assms(1,2) obtain l' r' aop where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = Arith aop l' r' sp"
    by (cases op) (auto split: option.splits)
  from assms(3) e_eq
  have ev: "eval_arith aop (eval sch st env l') (eval sch st env r') = Some v"
    by simp
  show "value_has_ty \<Gamma> v TInt"
  proof (rule eval_arith_preservation[OF ev])
    fix a assume "eval sch st env l' = Some a"
    thus "value_has_ty \<Gamma> a TInt" using assms(4) l_low by blast
  next
    fix b assume "eval sch st env r' = Some b"
    thus "value_has_ty \<Gamma> b TInt" using assms(5) r_low by blast
  qed
qed

lemma h3_pres_Cmp:
  assumes "op \<in> {BEq, BNeq, BLt, BLe, BGt, BGe}"
      and "lower enums (BinaryOpF op l r sp) = Some e'"
      and "eval sch st env e' = Some v"
  shows "value_has_ty \<Gamma> v TBool"
proof -
  from assms(1,2) obtain l' r' cop where
       e_eq: "e' = Cmp cop l' r' sp"
    by (cases op) (auto split: option.splits)
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
  case (T_Arith \<Gamma> l r op sp)
  show ?case
  proof (rule h3_pres_Arith[OF T_Arith.hyps(3) T_Arith.prems(2) T_Arith.prems(3)])
    fix l' a assume "lower enums l = Some l'" and "eval sch st env l' = Some a"
    thus "value_has_ty \<Gamma> a TInt"
      using T_Arith.IH(1) T_Arith.prems(1) T_Arith.prems(4) by blast
  next
    fix r' b assume "lower enums r = Some r'" and "eval sch st env r' = Some b"
    thus "value_has_ty \<Gamma> b TInt"
      using T_Arith.IH(2) T_Arith.prems(1) T_Arith.prems(4) by blast
  qed
next
  case (T_Cmp_Eq \<Gamma> l t r op sp)
  thus ?case using h3_pres_Cmp by blast
next
  case (T_Cmp_Ord \<Gamma> l r op sp)
  thus ?case using h3_pres_Cmp by blast
next
  case (T_Bool_Bin \<Gamma> l r op sp)
  thus ?case using h3_pres_Bool_Bin by blast
next
  case (T_Not \<Gamma> e sp)
  thus ?case using h3_pres_Not by blast
next
  case (T_Neg \<Gamma> e sp)
  show ?case
  proof (rule h3_pres_Neg[OF T_Neg.prems(2) T_Neg.prems(3)])
    fix e_sub a assume "lower enums e = Some e_sub" and "eval sch st env e_sub = Some a"
    thus "value_has_ty \<Gamma> a TInt"
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
  from T_BIn_Set.prems(2) T_BIn_Set.hyps(3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = SetMember l' r' sp"
    by (cases r) (auto split: option.splits)
  from T_BIn_Set.prems(3) e_eq obtain vl rv where
       ev_l: "eval sch st env l' = Some vl"
   and ev_r: "eval sch st env r' = Some (VSet rv)"
   and v_eq: "v = VBool (contains_value rv vl)"
    by (auto split: option.splits ir_value.splits)
  show ?case
    by (simp add: v_eq vt_bool)
next
  case (T_BNotIn_Set \<Gamma> l t r sp)
  from T_BNotIn_Set.prems(2) T_BNotIn_Set.hyps(3)
  obtain l' r' where
       l_low: "lower enums l = Some l'"
   and r_low: "lower enums r = Some r'"
   and e_eq: "e' = UnNot (SetMember l' r' sp) sp"
    by (cases r) (auto split: option.splits)
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
