theory Soundness
  imports Translate Semantics
begin

section \<open>Value \<leftrightarrow> SmtVal correlation\<close>

fun value_to_smt :: "ir_value \<Rightarrow> smt_val" where
  "value_to_smt (VBool b)        = SBool b"
| "value_to_smt (VInt n)         = SInt n"
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

lemma soundness_bool_lit:
  "value_to_smt_opt (eval s st env (BoolLit b sp))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (BoolLit b sp))"
  by (force split: option.splits smt_val.splits)

lemma soundness_int_lit:
  "value_to_smt_opt (eval s st env (IntLit n sp))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (IntLit n sp))"
  by (force split: option.splits smt_val.splits)

lemma soundness_ident:
  "value_to_smt_opt (eval s st env (Ident x sp))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (Ident x sp))"
  by (simp split: option.splits)

section \<open>Per-case soundness — propositional / arithmetic\<close>

lemma soundness_un_not:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNot e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNot e sp))"
  using assms
  by (cases "eval s st env e")
     (auto split: ir_value.splits option.splits smt_val.splits)

lemma soundness_un_neg:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNeg e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNeg e sp))"
  using assms
  by (cases "eval s st env e")
     (auto split: ir_value.splits option.splits smt_val.splits)

section \<open>Per-case soundness — propositional / arithmetic / comparison\<close>

text \<open>Following the Lean pattern (`Soundness.lean` lines 596-1010): per-(op,
shape) success-path theorems take CONCRETE hypotheses about `eval` returning
`Some (VBool _)` / `Some (VInt _)` and close trivially. The aggregate
`soundness_bool_bin` etc. (which case-split on every shape) close in Isabelle
with sufficient case-bashing, but per-op forms are cleaner and mirror the
Lean track's structure.\<close>

text \<open>Helper: an IH derives the smt_eval result directly given a concrete
eval result. Used by every binary success-path lemma below.\<close>

lemma smt_eval_of_eval_VInt:
  assumes "eval s st env e = Some (VInt n)"
      and "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smt_eval (correlate_model s st) (correlate_env env) (translate e) = Some (SInt n)"
  using assms by simp

lemma smt_eval_of_eval_VBool:
  assumes "eval s st env e = Some (VBool b)"
      and "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smt_eval (correlate_model s st) (correlate_env env) (translate e) = Some (SBool b)"
  using assms by simp

lemma smt_eval_of_eval_Some:
  assumes "eval s st env e = Some v"
      and "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "smt_eval (correlate_model s st) (correlate_env env) (translate e) = Some (value_to_smt v)"
  using assms by simp

section \<open>LetIn binder\<close>

lemma soundness_let_in:
  assumes "eval s st env value_e = Some va"
      and "value_to_smt_opt (eval s st env value_e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate value_e)"
      and "\<And>env'.
            value_to_smt_opt (eval s st env' body)
              = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (LetIn x value_e body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (LetIn x value_e body sp))"
  using assms by (force split: option.splits smt_val.splits)

section \<open>EnumAccess + Prime + Pre (single-state) + WithRec\<close>

lemma soundness_enum_access_known:
  assumes "schema_lookup_enum s en = Some d"
      and "List.member (enm_members d) mem"
  shows "value_to_smt_opt (eval s st env (EnumAccess en mem sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (EnumAccess en mem sp))"
  using assms correlate_model_lookup_sort_members[OF assms(1), of st]
  by simp

lemma soundness_prime:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (Prime e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Prime e sp))"
  using assms by simp

lemma soundness_pre:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (Pre e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Pre e sp))"
  using assms by simp

lemma soundness_with_rec:
  assumes hb: "eval s st env base = Some bv"
      and hv: "eval s st env value_e = Some v"
      and ihb: "value_to_smt_opt (eval s st env base)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate base)"
      and ihv: "value_to_smt_opt (eval s st env value_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate value_e)"
  shows "value_to_smt_opt (eval s st env (WithRec base fld value_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (WithRec base fld value_e sp))"
  using hb hv smt_eval_of_eval_Some[OF hb ihb] smt_eval_of_eval_Some[OF hv ihv]
  by simp

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

lemma soundness_set_empty:
  "value_to_smt_opt (eval s st env (SetEmpty sp))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (SetEmpty sp))"
  by simp

lemma soundness_set_insert_resolved:
  assumes he: "eval s st env elem = Some v"
      and hs: "eval s st env set_e = Some (VSet members)"
      and ihe: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetInsert elem set_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetInsert elem set_e sp))"
  using he hs smt_eval_of_eval_Some[OF he ihe] smt_eval_of_eval_Some[OF hs ihs]
        dedupe_values_map_value_to_smt[of "v # members"]
  by (simp add: Let_def)

lemma soundness_set_member_resolved:
  assumes he: "eval s st env elem = Some v"
      and hs: "eval s st env set_e = Some (VSet members)"
      and ihe: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetMember elem set_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetMember elem set_e sp))"
  using he hs smt_eval_of_eval_Some[OF he ihe] smt_eval_of_eval_Some[OF hs ihs]
  by simp

lemma soundness_set_bin_sets:
  assumes hl: "eval s st env l = Some (VSet ls)"
      and hr: "eval s st env r = Some (VSet rs)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (SetBin op l r sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetBin op l r sp))"
  using hl hr smt_eval_of_eval_Some[OF hl ihl] smt_eval_of_eval_Some[OF hr ihr]
  by (cases op)
     (simp_all add: set_union_values_map_value_to_smt
                    set_intersect_values_map_value_to_smt
                    set_diff_values_map_value_to_smt)

section \<open>State-touching: Member, CardRel, IndexRel\<close>

lemma soundness_member_resolved:
  assumes he: "eval s st env elem = Some v"
      and hd: "state_relation_domain st rel_name = Some d"
      and ihe: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
  shows "value_to_smt_opt (eval s st env (Member elem rel_name sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Member elem rel_name sp))"
  using he hd smt_eval_of_eval_Some[OF he ihe]
  by simp

lemma soundness_card_rel_resolved:
  assumes hd: "state_relation_domain st rel_name = Some d"
  shows "value_to_smt_opt (eval s st env (CardRel rel_name sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (CardRel rel_name sp))"
  using hd by simp

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

text \<open>Issue #210 (M_L.4.l): \<open>peel_smt_relation_ref\<close> commutes with
  \<open>translate\<close>. Trivial structural induction over the four shapes
  \<open>peel_relation_ref\<close> recognises (\<open>Ident\<close>, \<open>Pre Ident\<close>, \<open>Prime Ident\<close>,
  wildcard); the wildcard case relies on \<open>translate\<close>'s totality and the
  fact that \<open>peel_smt_relation_ref\<close> only fires on \<open>TVar\<close>/\<open>TPre TVar\<close>/
  \<open>TPrime TVar\<close>.\<close>

lemma peel_smt_translate_BoolBin [simp]:
  "peel_smt_relation_ref (translate (BoolBin op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_Arith [simp]:
  "peel_smt_relation_ref (translate (Arith op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_Cmp [simp]:
  "peel_smt_relation_ref (translate (Cmp op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_SetBin [simp]:
  "peel_smt_relation_ref (translate (SetBin op l r sp)) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_BoolBin [simp]:
  "peel_smt_relation_ref (TPrime (translate (BoolBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_Arith [simp]:
  "peel_smt_relation_ref (TPrime (translate (Arith op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_Cmp [simp]:
  "peel_smt_relation_ref (TPrime (translate (Cmp op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPrime_SetBin [simp]:
  "peel_smt_relation_ref (TPrime (translate (SetBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_BoolBin [simp]:
  "peel_smt_relation_ref (TPre (translate (BoolBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_Arith [simp]:
  "peel_smt_relation_ref (TPre (translate (Arith op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_Cmp [simp]:
  "peel_smt_relation_ref (TPre (translate (Cmp op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_translate_TPre_SetBin [simp]:
  "peel_smt_relation_ref (TPre (translate (SetBin op l r sp))) = None"
  by (cases op) simp_all

lemma peel_smt_relation_ref_translate:
  "peel_smt_relation_ref (translate base) = peel_relation_ref base"
proof (induction base rule: peel_relation_ref.induct)
qed simp_all

lemma soundness_index_rel_resolved:
  assumes hpeel: "peel_relation_ref base = Some rel"
      and hk: "eval s st env key = Some kv"
      and ihk: "value_to_smt_opt (eval s st env key)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate key)"
  shows "value_to_smt_opt (eval s st env (IndexRel base key sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (IndexRel base key sp))"
  using hpeel hk smt_eval_of_eval_Some[OF hk ihk]
        correlate_model_lookup_key[of s st rel kv]
        peel_smt_relation_ref_translate[of base]
  by simp

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

lemma soundness_field_access:
  assumes hb: "eval s st env base = Some bv"
      and ihb: "value_to_smt_opt (eval s st env base)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate base)"
  shows "value_to_smt_opt (eval s st env (FieldAccess base fname sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (FieldAccess base fname sp))"
  using hb smt_eval_of_eval_Some[OF hb ihb] value_field_lookup_correlated[of s st bv fname]
  by simp

section \<open>Quantifier soundness\<close>

text \<open>Mutual-induction style: we induct on the quantifier domain (members for
forall_enum, value list for forall_rel), threading the body soundness as a
universally quantified assumption (covers every env extended by a fresh bind).\<close>

lemma eval_forall_enum_correlated:
  assumes ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval_forall_enum s st env var en members body)
           = smt_eval_forall_enum (correlate_model s st) (correlate_env env) var en members
                                   (translate body)"
proof (induction members)
  case Nil show ?case by simp
next
  case (Cons mem rest)
  let ?env' = "(var, VEnum en mem) # env"
  let ?senv' = "(var, SEnumElem en mem) # correlate_env env"
  have body: "smt_eval (correlate_model s st) ?senv' (translate body)
                = value_to_smt_opt (eval s st ?env' body)"
    using ihbody[of ?env'] by simp
  have ih_sym: "smt_eval_forall_enum (correlate_model s st) (correlate_env env) var en rest
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
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval_forall_rel s st env var rd body)
           = smt_eval_forall_rel (correlate_model s st) (correlate_env env) var
                                  (map value_to_smt rd) (translate body)"
proof (induction rd)
  case Nil show ?case by simp
next
  case (Cons v rest)
  let ?env' = "(var, v) # env"
  let ?senv' = "(var, value_to_smt v) # correlate_env env"
  have body: "smt_eval (correlate_model s st) ?senv' (translate body)
                = value_to_smt_opt (eval s st ?env' body)"
    using ihbody[of ?env'] by simp
  have ih_sym: "smt_eval_forall_rel (correlate_model s st) (correlate_env env) var
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
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallEnum var en body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (ForallEnum var en body sp))"
  using correlate_model_lookup_sort_members[OF hd, of st]
        eval_forall_enum_correlated[OF ihbody, where members="enm_members d" and var=var and en=en and env=env]
  by (simp add: hd)

lemma soundness_forall_rel_known:
  assumes hd: "state_relation_domain st rel_name = Some d"
      and ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallRel var rel_name body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (ForallRel var rel_name body sp))"
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

lemma un_not_step:
  assumes ih: "value_to_smt_opt (eval s st env e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNot e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNot e sp))"
proof -
  have ih': "smt_eval (correlate_model s st) (correlate_env env) (translate e)
                = value_to_smt_opt (eval s st env e)" using ih by simp
  show ?thesis
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
qed

lemma un_neg_step:
  assumes ih: "value_to_smt_opt (eval s st env e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNeg e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNeg e sp))"
proof -
  have ih': "smt_eval (correlate_model s st) (correlate_env env) (translate e)
                = value_to_smt_opt (eval s st env e)" using ih by simp
  show ?thesis
  proof (cases "eval s st env e")
    case None thus ?thesis using ih' by simp
  next
    case (Some a) thus ?thesis using ih' by (cases a) simp_all
  qed
qed

lemma bool_bin_step:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (BoolBin op l r sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (BoolBin op l r sp))"
proof -
  have ihl': "smt_eval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smt_eval (correlate_model s st) (correlate_env env) (translate r)
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

lemma arith_step:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith op l r sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith op l r sp))"
proof -
  have ihl': "smt_eval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smt_eval (correlate_model s st) (correlate_env env) (translate r)
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

lemma int_ge_iff_lt_or_eq: "(a::int) \<ge> b \<longleftrightarrow> b < a \<or> a = b"
  by linarith

lemma int_le_iff_lt_or_eq: "(a::int) \<le> b \<longleftrightarrow> a < b \<or> a = b"
  by linarith

lemma cmp_step:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp op l r sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp op l r sp))"
proof -
  have ihl': "smt_eval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smt_eval (correlate_model s st) (correlate_env env) (translate r)
                = value_to_smt_opt (eval s st env r)" using ihr by simp
  show ?thesis
  proof (cases "eval s st env l")
    case None
    show ?thesis
    proof (cases "eval s st env r")
      case None thus ?thesis using ihl' ihr' \<open>eval s st env l = None\<close>
        by (cases op) simp_all
    next
      case (Some b) thus ?thesis using ihl' ihr' \<open>eval s st env l = None\<close>
        by (cases op; cases b) simp_all
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
qed

lemma let_in_step:
  assumes ihv: "value_to_smt_opt (eval s st env v)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate v)"
      and ihb: "\<And>env'. value_to_smt_opt (eval s st env' body)
                          = smt_eval (correlate_model s st) (correlate_env env')
                                      (translate body)"
  shows "value_to_smt_opt (eval s st env (LetIn x v body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (LetIn x v body sp))"
proof (cases "eval s st env v")
  case None
  have ihv': "smt_eval (correlate_model s st) (correlate_env env) (translate v) = None"
    using ihv None by simp
  thus ?thesis using None by simp
next
  case (Some a)
  have ihv': "smt_eval (correlate_model s st) (correlate_env env) (translate v) = Some (value_to_smt a)"
    using ihv Some by simp
  have ihb': "value_to_smt_opt (eval s st ((x, a) # env) body)
               = smt_eval (correlate_model s st) (correlate_env ((x, a) # env)) (translate body)"
    using ihb[of "(x, a) # env"] .
  thus ?thesis using ihv' Some by simp
qed

lemma member_step:
  assumes ih: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
  shows "value_to_smt_opt (eval s st env (Member elem rel_name sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Member elem rel_name sp))"
proof -
  have ih': "smt_eval (correlate_model s st) (correlate_env env) (translate elem)
              = value_to_smt_opt (eval s st env elem)" using ih by simp
  show ?thesis
    using ih'
    by (cases "eval s st env elem"; cases "state_relation_domain st rel_name")
       (simp_all split: option.splits)
qed

lemma index_rel_step:
  assumes ihk: "value_to_smt_opt (eval s st env key)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate key)"
  shows "value_to_smt_opt (eval s st env (IndexRel base key sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (IndexRel base key sp))"
proof -
  have ihk': "smt_eval (correlate_model s st) (correlate_env env) (translate key)
                = value_to_smt_opt (eval s st env key)" using ihk by simp
  have peel_eq: "peel_smt_relation_ref (translate base) = peel_relation_ref base"
    by (rule peel_smt_relation_ref_translate)
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
      thus ?thesis
        using \<open>peel_relation_ref base = Some rel\<close> peel_eq ihk'
              correlate_model_lookup_key[of s st rel kv]
        by simp
    qed
  qed
qed

lemma field_access_step:
  assumes ih: "value_to_smt_opt (eval s st env base)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate base)"
  shows "value_to_smt_opt (eval s st env (FieldAccess base fname sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (FieldAccess base fname sp))"
proof -
  have ih': "smt_eval (correlate_model s st) (correlate_env env) (translate base)
              = value_to_smt_opt (eval s st env base)" using ih by simp
  show ?thesis
    using ih' value_field_lookup_correlated
    by (cases "eval s st env base") simp_all
qed

lemma set_insert_step:
  assumes ihe: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetInsert elem set_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetInsert elem set_e sp))"
proof -
  have ihe': "smt_eval (correlate_model s st) (correlate_env env) (translate elem)
                = value_to_smt_opt (eval s st env elem)" using ihe by simp
  have ihs': "smt_eval (correlate_model s st) (correlate_env env) (translate set_e)
                = value_to_smt_opt (eval s st env set_e)" using ihs by simp
  show ?thesis
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
qed

lemma set_member_step:
  assumes ihe: "value_to_smt_opt (eval s st env elem)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate elem)"
      and ihs: "value_to_smt_opt (eval s st env set_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate set_e)"
  shows "value_to_smt_opt (eval s st env (SetMember elem set_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetMember elem set_e sp))"
proof -
  have ihe': "smt_eval (correlate_model s st) (correlate_env env) (translate elem)
                = value_to_smt_opt (eval s st env elem)" using ihe by simp
  have ihs': "smt_eval (correlate_model s st) (correlate_env env) (translate set_e)
                = value_to_smt_opt (eval s st env set_e)" using ihs by simp
  show ?thesis
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
qed

lemma set_bin_step:
  assumes ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (SetBin op l r sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (SetBin op l r sp))"
proof -
  have ihl': "smt_eval (correlate_model s st) (correlate_env env) (translate l)
                = value_to_smt_opt (eval s st env l)" using ihl by simp
  have ihr': "smt_eval (correlate_model s st) (correlate_env env) (translate r)
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

lemma with_rec_step:
  assumes ihb: "value_to_smt_opt (eval s st env base)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate base)"
      and ihv: "value_to_smt_opt (eval s st env value_e)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate value_e)"
  shows "value_to_smt_opt (eval s st env (WithRec base fld value_e sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (WithRec base fld value_e sp))"
proof -
  have ihb': "smt_eval (correlate_model s st) (correlate_env env) (translate base)
                = value_to_smt_opt (eval s st env base)" using ihb by simp
  have ihv': "smt_eval (correlate_model s st) (correlate_env env) (translate value_e)
                = value_to_smt_opt (eval s st env value_e)" using ihv by simp
  show ?thesis
    using ihb' ihv'
    by (cases "eval s st env base"; cases "eval s st env value_e") simp_all
qed

lemma forall_enum_step:
  assumes ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallEnum var en body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (ForallEnum var en body sp))"
proof (cases "schema_lookup_enum s en")
  case None thus ?thesis
    by (simp add: correlate_model_lookup_sort_members_eq)
next
  case (Some d)
  thus ?thesis
    using soundness_forall_enum_known[OF Some, where var=var and env=env]
          ihbody
    by simp
qed

lemma forall_rel_step:
  assumes ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallRel var rel_name body sp))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (ForallRel var rel_name body sp))"
proof (cases "state_relation_domain st rel_name")
  case None thus ?thesis by simp
next
  case (Some d)
  thus ?thesis
    using soundness_forall_rel_known[OF Some, where var=var and env=env]
          ihbody
    by simp
qed

text \<open>Universal soundness theorem. Each case dispatches to the matching
`*_step` lemma above; the proof is a thin shell whose subgoals close in
under a second per case.\<close>

theorem soundness:
  "value_to_smt_opt (eval s st env e)
     = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
proof (induction e arbitrary: env)
  case (BoolLit b sp) show ?case by simp
next
  case (IntLit n sp) show ?case by simp
next
  case (Ident x sp) show ?case by (simp split: option.splits)
next
  case (UnNot e sp) show ?case using un_not_step UnNot.IH by blast
next
  case (UnNeg e sp) show ?case using un_neg_step UnNeg.IH by blast
next
  case (BoolBin op l r sp) show ?case using bool_bin_step BoolBin.IH by blast
next
  case (Arith op l r sp) show ?case using arith_step Arith.IH by blast
next
  case (Cmp op l r sp) show ?case using cmp_step Cmp.IH by blast
next
  case (LetIn x v body sp) show ?case using let_in_step LetIn.IH by blast
next
  case (EnumAccess en mem sp)
  show ?case
    by (simp add: correlate_model_lookup_sort_members_eq split: option.splits)
next
  case (Member elem rel_name sp) show ?case using member_step Member.IH by blast
next
  case (ForallEnum var en body sp) show ?case using forall_enum_step ForallEnum.IH by blast
next
  case (ForallRel var rel_name body sp) show ?case using forall_rel_step ForallRel.IH by blast
next
  case (Prime e sp) thus ?case by simp
next
  case (Pre e sp) thus ?case by simp
next
  case (CardRel rel_name sp) show ?case by (simp split: option.splits)
next
  case (IndexRel base key sp) show ?case using index_rel_step IndexRel.IH(2) by blast
next
  case (FieldAccess base fname sp) show ?case using field_access_step FieldAccess.IH by blast
next
  case (SetEmpty sp) show ?case by simp
next
  case (SetInsert elem set_e sp) show ?case using set_insert_step SetInsert.IH by blast
next
  case (SetMember elem set_e sp) show ?case using set_member_step SetMember.IH by blast
next
  case (SetBin op l r sp) show ?case using set_bin_step SetBin.IH by blast
next
  case (WithRec base fld value_e sp) show ?case using with_rec_step WithRec.IH by blast
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
           = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  by (rule soundness)

section \<open>Phase 9b — classifier soundness: requires_alloy excludes lower\<close>

text \<open>The Phase 8 \<open>requires_alloy\<close> predicate identifies expressions whose
  shape contains a \<open>UPower\<close> (set-power) somewhere in the tree. The verifier
  routes such expressions to the Alloy backend instead of Z3.

  The full claim — \<open>requires_alloy e \<Longrightarrow> lower enums e = None\<close> for the
  recursive \<open>UPower\<close>-anywhere reading — requires multi-statement induction
  over the mutually recursive \<open>lower / lower_set_list / lower_with_assigns\<close>
  triple, threaded against the parallel \<open>requires_alloy / requires_alloy_list
  / requires_alloy_fields\<close> structure. Closing it via blanket \<open>auto\<close> with
  splits exceeds the build budget; a structured per-case proof with
  \<open>(cases op) auto\<close> per arm is the realistic shape and is queued as
  follow-up.

  Shipped here: the direct top-level shape \<open>UnaryOpF UPower\<close>, which is the
  base case the recursive lemma will dispatch to and which already documents
  the load-bearing fact (\<open>lower\<close> has an explicit \<open>UPower \<Rightarrow> None\<close> arm).
  This is the smallest fact whose absence would silently break the
  classifier's Z3-vs-Alloy routing.\<close>

lemma lower_unary_upower_none [simp]:
  "lower enums (UnaryOpF UPower e sp) = None"
  by simp

lemma lower_some_top_not_upower:
  assumes "lower enums e = Some e'"
  shows "\<nexists> inner sp. e = UnaryOpF UPower inner sp"
  using assms lower_unary_upower_none by fastforce

end
