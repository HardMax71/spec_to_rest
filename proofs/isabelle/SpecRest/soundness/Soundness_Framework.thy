theory Soundness_Framework
  imports
    SpecRest_Core.Translate
    SpecRest_Core.Semantics
    SpecRest_Core.IR_Helpers
    SpecRest_Core.IR_Analysis
    SpecRest_Core.IR_Lower
begin


section \<open>Value \<leftrightarrow> SmtVal correlation\<close>

function (sequential) value_to_smt :: "ir_value \<Rightarrow> smt_val"
and value_to_smt_entries :: "(ir_value \<times> ir_value) list \<Rightarrow> (smt_val \<times> smt_val) list" where
  "value_to_smt (VBool b)        = SBool b"
| "value_to_smt (VInt n)         = SInt n"
| "value_to_smt (VReal r)        = SReal r"
| "value_to_smt (VEnum en mem)   = SEnumElem en mem"
| "value_to_smt (VEntity en eid) = SEntityElem en eid"
| "value_to_smt (VSet members)   = SSet (map value_to_smt members)"
| "value_to_smt (VEntityWith base fld v) =
     SEntityWith (value_to_smt base) fld (value_to_smt v)"
| "value_to_smt VNone            = SNone"
| "value_to_smt (VSome v)        = SSome (value_to_smt v)"
| "value_to_smt (VStr s)         = SStr s"
| "value_to_smt (VSeq vs)        = SSeq (map value_to_smt vs)"
| "value_to_smt (VMap ps)        = SMap (value_to_smt_entries ps)"
| "value_to_smt_entries []            = []"
| "value_to_smt_entries ((k, v) # ps) = (value_to_smt k, value_to_smt v) # value_to_smt_entries ps"
  by pat_completeness auto
termination
  by (relation "measure (\<lambda>x. case x of
        Inl v \<Rightarrow> size v
      | Inr ps \<Rightarrow> size_list (size_prod size size) ps)")
     (auto dest: size_list_estimation'[OF _ order_refl, where f = size])

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

lemma value_to_smt_entries_inj:
  assumes "\<And>k v. (k, v) \<in> set xs \<Longrightarrow>
             (\<forall>k'. value_to_smt k = value_to_smt k' \<longrightarrow> k = k')
             \<and> (\<forall>v'. value_to_smt v = value_to_smt v' \<longrightarrow> v = v')"
  shows "(value_to_smt_entries xs = value_to_smt_entries ys) = (xs = ys)"
  using assms
proof (induction xs arbitrary: ys)
  case Nil
  show ?case
  proof (cases ys)
    case Nil thus ?thesis by simp
  next
    case (Cons q ys')
    obtain a b where "q = (a, b)" by (cases q)
    thus ?thesis using \<open>ys = q # ys'\<close> by simp
  qed
next
  case (Cons p xs)
  obtain k v where p: "p = (k, v)" by (cases p)
  have inj_kv: "(\<forall>k'. value_to_smt k = value_to_smt k' \<longrightarrow> k = k')
                \<and> (\<forall>v'. value_to_smt v = value_to_smt v' \<longrightarrow> v = v')"
    using Cons.prems[of k v] p by simp
  have tail: "\<And>k' v'. (k', v') \<in> set xs \<Longrightarrow>
                (\<forall>k''. value_to_smt k' = value_to_smt k'' \<longrightarrow> k' = k'')
                \<and> (\<forall>v''. value_to_smt v' = value_to_smt v'' \<longrightarrow> v' = v'')"
  proof -
    fix k' v' assume "(k', v') \<in> set xs"
    hence "(k', v') \<in> set (p # xs)" by simp
    thus "(\<forall>k''. value_to_smt k' = value_to_smt k'' \<longrightarrow> k' = k'')
          \<and> (\<forall>v''. value_to_smt v' = value_to_smt v'' \<longrightarrow> v' = v'')"
      using Cons.prems by blast
  qed
  show ?case
  proof (cases ys)
    case Nil
    thus ?thesis using p by simp
  next
    case (Cons q ys')
    obtain a b where q: "q = (a, b)" by (cases q)
    have ih: "(value_to_smt_entries xs = value_to_smt_entries ys') = (xs = ys')"
      using Cons.IH[OF tail] .
    from inj_kv have ka: "(value_to_smt k = value_to_smt a) = (k = a)"
                 and vb: "(value_to_smt v = value_to_smt b) = (v = b)" by blast+
    show ?thesis using p q \<open>ys = q # ys'\<close> ka vb ih by simp
  qed
qed

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
next
  case VNone
  show ?case by (cases v2) auto
next
  case (VSome v)
  show ?case
  proof (cases v2)
    case (VSome v')
    thus ?thesis using VSome.IH[of v'] by simp
  qed auto
next
  case (VStr s)
  show ?case by (cases v2) auto
next
  case (VSeq xs)
  show ?case
  proof (cases v2)
    case (VSeq ys)
    have "(map value_to_smt xs = map value_to_smt ys) = (xs = ys)"
    proof (rule map_value_to_smt_inj)
      fix x assume "x \<in> set xs"
      thus "\<forall>y. value_to_smt x = value_to_smt y \<longrightarrow> x = y" using VSeq.IH by blast
    qed
    thus ?thesis using \<open>v2 = VSeq ys\<close> by simp
  qed auto
next
  case (VMap xs)
  show ?case
  proof (cases v2)
    case (VMap ys)
    have "(value_to_smt_entries xs = value_to_smt_entries ys) = (xs = ys)"
    proof (rule value_to_smt_entries_inj)
      fix k v assume m: "(k, v) \<in> set xs"
      have "(\<forall>v2. (value_to_smt k = value_to_smt v2) = (k = v2))
            \<and> (\<forall>v2. (value_to_smt v = value_to_smt v2) = (v = v2))"
        using VMap.IH[OF m] by simp
      thus "(\<forall>k'. value_to_smt k = value_to_smt k' \<longrightarrow> k = k')
            \<and> (\<forall>v'. value_to_smt v = value_to_smt v' \<longrightarrow> v = v')"
        by blast
    qed
    thus ?thesis using \<open>v2 = VMap ys\<close> by simp
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

lemma value_to_smt_entries_inj_simp [simp]:
  "(value_to_smt_entries xs = value_to_smt_entries ys) = (xs = ys)"
proof (induction xs arbitrary: ys)
  case Nil
  show ?case
  proof (cases ys)
    case Nil thus ?thesis by simp
  next
    case (Cons q ys')
    obtain a b where "q = (a, b)" by (cases q)
    thus ?thesis using \<open>ys = q # ys'\<close> by simp
  qed
next
  case (Cons p xs')
  obtain k v where p: "p = (k, v)" by (cases p)
  show ?case
  proof (cases ys)
    case Nil
    thus ?thesis using p by simp
  next
    case (Cons q ys')
    obtain a b where q: "q = (a, b)" by (cases q)
    show ?thesis using p q \<open>ys = q # ys'\<close> Cons.IH by simp
  qed
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

lemma contains_smt_val_map_SNone [simp]:
  "contains_smt_val (map value_to_smt vs) SNone = contains_value vs VNone"
  using contains_value_map_value_to_smt[of vs VNone] by simp

lemma contains_smt_val_map_SSome [simp]:
  "contains_smt_val (map value_to_smt vs) (SSome (value_to_smt v)) = contains_value vs (VSome v)"
  using contains_value_map_value_to_smt[of vs "VSome v"] by simp

lemma contains_smt_val_map_SStr [simp]:
  "contains_smt_val (map value_to_smt vs) (SStr s) = contains_value vs (VStr s)"
  using contains_value_map_value_to_smt[of vs "VStr s"] by simp

lemma contains_smt_val_map_SSeq [simp]:
  "contains_smt_val (map value_to_smt vs) (SSeq (map value_to_smt xs))
     = contains_value vs (VSeq xs)"
  using contains_value_map_value_to_smt[of vs "VSeq xs"] by simp

lemma contains_smt_val_map_SMap [simp]:
  "contains_smt_val (map value_to_smt vs) (SMap (value_to_smt_entries ps))
     = contains_value vs (VMap ps)"
  using contains_value_map_value_to_smt[of vs "VMap ps"] by simp


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

lemma relRefVarName_translate_BoolBin [simp]:
  "relRefVarName (translate (BoolBin op l r sp)) = None"
  by (cases op) simp_all

lemma relRefVarName_translate_Arith [simp]:
  "relRefVarName (translate (Arith op l r sp)) = None"
  by (cases op) simp_all

lemma relRefVarName_translate_Cmp [simp]:
  "relRefVarName (translate (Cmp op l r sp)) = None"
  by (cases op) simp_all

lemma relRefVarName_translate_SetBin [simp]:
  "relRefVarName (translate (SetBin op l r sp)) = None"
  by (cases op) simp_all

lemma relRefVarName_translate_eq [simp]:
  "relRefVarName (translate b) = identName b"
  by (cases b) simp_all

lemma peelSmtRelationRef_translate:
  "peelSmtRelationRef (translate base) = peel_relation_ref base"
  by (cases base rule: peel_relation_ref.cases) simp_all

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

lemma soundness_ForallSet:
  assumes ihset: "value_to_smt_opt (eval s st env setE)
                    = smtEval (correlate_model s st) (correlate_env env) (translate setE)"
      and ihbody: "\<And>env'.
                    value_to_smt_opt (eval s st env' body)
                      = smtEval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (ForallSet var setE body sp))
           = smtEval (correlate_model s st) (correlate_env env) (translate (ForallSet var setE body sp))"
proof (cases "eval s st env setE")
  case None
  thus ?thesis using ihset by simp
next
  case (Some sv)
  hence smtset: "smtEval (correlate_model s st) (correlate_env env) (translate setE)
                   = Some (value_to_smt sv)"
    using ihset by simp
  show ?thesis
  proof (cases sv)
    case (VSet elems)
    show ?thesis
      using Some VSet smtset
            eval_forall_rel_correlated[OF ihbody, where rd=elems and var=var and env=env]
      by simp
  qed (use Some smtset in simp_all)
qed

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

lemma rat_ge_iff_lt_or_eq: "(a::rat) \<ge> b \<longleftrightarrow> b < a \<or> a = b"
  by (auto simp: order_le_less)

lemma rat_le_iff_lt_or_eq: "(a::rat) \<le> b \<longleftrightarrow> a < b \<or> a = b"
  by (auto simp: order_le_less)


end
