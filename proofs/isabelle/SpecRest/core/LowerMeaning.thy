theory LowerMeaning
  imports Semantics_Reference IR_Lower
begin

text \<open>Stage 1 of the \<open>expr\<close>/\<open>expr_full\<close> unification (#391): \<open>lower\<close> is meaning-preserving
  against the reference semantics \<open>eval_full\<close>. Today \<open>lower_soundness\<close> only relates
  \<open>translate\<close> to \<open>eval\<close> of lower's \<^emph>\<open>output\<close> (trusted); this closes the gap on lower's
  \<^emph>\<open>input\<close> (the #386 comprehension-bug class). The statement quantifies over all \<open>e\<close> and
  is vacuous wherever \<open>eval_full\<close> is still \<open>None\<close> (binders/collections), so it widens for
  free as \<open>eval_full\<close>'s coverage grows.\<close>

lemma identNameFull_SomeD:
  "identNameFull e = Some x \<Longrightarrow> (\<exists>sp. e = IdentifierF x sp)"
  by (cases e) auto

lemma peelRelationRefFull_lower:
  "peelRelationRefFull base = Some rel \<Longrightarrow> lower enums base = Some base'
     \<Longrightarrow> peel_relation_ref base' = Some rel"
  by (cases base rule: peelRelationRefFull.cases) (auto dest!: identNameFull_SomeD)

definition enums_wf :: "schema \<Rightarrow> String.literal list \<Rightarrow> bool" where
  "enums_wf s enums = (\<forall>en. string_in_list en enums = (schema_lookup_enum s en \<noteq> None))"

lemma eval_forall_enum_eq_rel:
  "eval_forall_enum s st env var en members body
     = eval_forall_rel s st env var (map (\<lambda>m. VEnum en m) members) body"
proof (induction members)
  case Nil
  show ?case by simp
next
  case (Cons mem rest)
  show ?case
  proof (cases "eval s st ((var, VEnum en mem) # env) body")
    case None
    then show ?thesis by simp
  next
    case (Some bv)
    show ?thesis using Some by (cases bv) (simp_all add: Cons.IH)
  qed
qed

lemma quant_dom_some_shape:
  "quant_dom s st k bs = Some (var, dmv) \<Longrightarrow>
     k = QAll \<and> (\<exists>dnm sp1 dty a. bs = [QuantifierBindingFull var (IdentifierF dnm sp1) dty a] \<and>
       ((\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
        \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)))"
  by (erule quant_dom.elims; auto split: option.splits)

lemma sil_enum:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = Some d \<Longrightarrow> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma sil_none:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = None \<Longrightarrow> \<not> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma dmrel_enum:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = Some d
   \<Longrightarrow> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
  by auto

lemma dmrel_rel:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = None
   \<Longrightarrow> state_relation_domain st dnm = Some dmv"
  by auto

lemma eval_ForallEnum_Some:
  "schema_lookup_enum s dnm = Some d \<Longrightarrow>
   eval s st env (ForallEnum var dnm body sp) = eval_forall_enum s st env var dnm (enm_members d) body"
  by simp

lemma eval_ForallRel_Some:
  "state_relation_domain st dnm = Some rd \<Longrightarrow>
   eval s st env (ForallRel var dnm body sp) = eval_forall_rel s st env var rd body"
  by simp

lemma eval_the_rel_mem:
  "eval_the_rel s st env var dmv body = Some ms
     \<Longrightarrow> (x \<in> set ms) = (x \<in> set dmv \<and> eval s st ((var, x) # env) body = Some (VBool True))"
proof (induction dmv arbitrary: ms)
  case Nil
  thus ?case by simp
next
  case (Cons w rest)
  from Cons.prems obtain b matches where
      wb: "eval s st ((var, w) # env) body = Some (VBool b)"
      and mr: "eval_the_rel s st env var rest body = Some matches"
      and ms_eq: "ms = (if b then w # matches else matches)"
    by (auto split: option.splits ir_value.splits)
  have IH: "(x \<in> set matches) = (x \<in> set rest \<and> eval s st ((var, x) # env) body = Some (VBool True))"
    using Cons.IH[OF mr] .
  show ?case using ms_eq wb IH by (cases "x = w") auto
qed

lemma eval_the_rel_defined:
  "eval_the_rel s st env var dmv body = Some ms
     \<Longrightarrow> v \<in> set dmv \<Longrightarrow> \<exists>b. eval s st ((var, v) # env) body = Some (VBool b)"
proof (induction dmv arbitrary: ms)
  case Nil
  thus ?case by simp
next
  case (Cons w rest)
  from Cons.prems(1) obtain b matches where
      wb: "eval s st ((var, w) # env) body = Some (VBool b)"
      and mr: "eval_the_rel s st env var rest body = Some matches"
    by (auto split: option.splits ir_value.splits)
  show ?case using Cons.prems(2) wb Cons.IH[OF mr] by auto
qed

lemma eval_env_cong:
  "(\<forall>y. env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval s st env1 e = eval s st env2 e"
  "(\<forall>y. env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_forall_enum s st env1 var en members body
          = eval_forall_enum s st env2 var en members body"
  "(\<forall>y. env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_forall_rel s st env1 var rd body = eval_forall_rel s st env2 var rd body"
  "(\<forall>y. env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_the_rel s st env1 var rd body = eval_the_rel s st env2 var rd body"
proof (induction s st env1 e and s st env1 var en members body
        and s st env1 var rd body and s st env1 var rd body
        arbitrary: env2 and env2 and env2 and env2
        rule: eval_eval_forall_enum_eval_forall_rel_eval_the_rel.induct)
  case 1 show ?case using "1.prems" by (simp add: env_lookup_def)
next
  case 2 show ?case using "2.prems" by (simp add: env_lookup_def)
next
  case 3 show ?case using "3.prems" by (simp add: env_lookup_def)
next
  case 4 show ?case using "4.prems" by (simp add: env_lookup_def split: option.splits)
next
  case 5 show ?case using "5.IH"[OF "5.prems"] "5.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 6 show ?case using "6.IH"[OF "6.prems"] "6.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 7 show ?case using "7.IH"[OF "7.prems"] "7.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 8 show ?case using "8.IH"[OF "8.prems"] "8.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 9 show ?case using "9.IH"[OF "9.prems"] "9.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case (10 s st env x v body sp env2)
  have v_eq: "eval s st env v = eval s st env2 v" using "10.IH"(1)[OF "10.prems"] .
  show ?case
  proof (cases "eval s st env v")
    case None thus ?thesis using v_eq by simp
  next
    case (Some va)
    have v2: "eval s st env2 v = Some va" using v_eq Some by simp
    have ext: "\<forall>y. env_lookup ((x, va) # env) y = env_lookup ((x, va) # env2) y"
      using "10.prems" by (simp add: env_lookup_def)
    have "eval s st ((x, va) # env) body = eval s st ((x, va) # env2) body"
      using "10.IH"(2)[OF Some ext] .
    thus ?thesis using Some v2 by simp
  qed
next
  case 11 show ?case using "11.prems" by (simp add: env_lookup_def split: option.splits)
next
  case 12 show ?case using "12.IH"[OF "12.prems"] "12.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 13 show ?case using "13.IH" "13.prems" by (auto simp: env_lookup_def split: option.splits ir_value.splits)
next
  case 14 show ?case using "14.IH" "14.prems" by (auto simp: env_lookup_def split: option.splits ir_value.splits)
next
  case (15 s st env var setE body sp env2)
  have e1: "eval s st env setE = eval s st env2 setE" using "15.IH"(1)[OF "15.prems"] .
  have f: "eval_forall_rel s st env var elems body = eval_forall_rel s st env2 var elems body"
    if a: "eval s st env setE = Some (VSet elems)" for elems
    using "15.IH"(2)[OF a refl "15.prems"] .
  show ?case using e1 f by (auto split: option.splits ir_value.splits)
next
  case (16 s st env var rel body sp env2)
  show ?case
  proof (cases "state_relation_domain st rel")
    case None thus ?thesis by simp
  next
    case (Some rd)
    have "eval_the_rel s st env var rd body = eval_the_rel s st env2 var rd body"
      using "16.IH"[OF Some "16.prems"] .
    thus ?thesis using Some by simp
  qed
next
  case 17 show ?case using "17.prems" by (simp add: env_lookup_def)
next
  case 18 show ?case using "18.IH"[OF "18.prems"] "18.prems" by (simp add: env_lookup_def)
next
  case 19 show ?case using "19.IH"[OF "19.prems"] "19.prems" by (simp add: env_lookup_def)
next
  case 20 show ?case using "20.prems" by (simp add: env_lookup_def split: option.splits)
next
  case 21 show ?case using "21.IH"[OF "21.prems"] "21.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 22 show ?case using "22.IH"[OF "22.prems"] "22.prems" by (simp add: env_lookup_def split: option.splits)
next
  case 23 show ?case using "23.prems" by (simp add: env_lookup_def)
next
  case 24 show ?case using "24.IH"(1)[OF "24.prems"] "24.IH"(2)[OF "24.prems"] "24.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 25 show ?case using "25.IH"(1)[OF "25.prems"] "25.IH"(2)[OF "25.prems"] "25.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 26 show ?case using "26.IH"(1)[OF "26.prems"] "26.IH"(2)[OF "26.prems"] "26.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 27 show ?case using "27.IH"(1)[OF "27.prems"] "27.IH"(2)[OF "27.prems"] "27.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case (28 s st env c a b sp env2)
  have c_eq: "eval s st env c = eval s st env2 c" using "28.IH"(1)[OF "28.prems"] .
  have fa: "eval s st env a = eval s st env2 a" if "eval s st env c = Some (VBool True)"
    using "28.IH"(2)[OF that refl refl "28.prems"] .
  have fb: "eval s st env b = eval s st env2 b" if "eval s st env c = Some (VBool False)"
    using "28.IH"(3)[OF that refl refl "28.prems"] .
  show ?case using c_eq fa fb by (auto split: option.splits ir_value.splits bool.split)
next
  case 29 show ?case using "29.prems" by (simp add: env_lookup_def)
next
  case 30 show ?case using "30.IH"[OF "30.prems"] "30.prems" by (simp add: env_lookup_def)
next
  case 31 show ?case using "31.prems" by (simp add: env_lookup_def)
next
  case 32 show ?case using "32.IH"[OF "32.prems"] "32.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 33 show ?case using "33.prems" by (simp add: env_lookup_def)
next
  case 34 show ?case using "34.IH"(1)[OF "34.prems"] "34.IH"(2)[OF "34.prems"] "34.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 35 show ?case using "35.prems" by (simp add: env_lookup_def)
next
  case 36 show ?case using "36.IH"(1)[OF "36.prems"] "36.IH"(2)[OF "36.prems"] "36.IH"(3)[OF "36.prems"] "36.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 37 show ?case using "37.IH"[OF "37.prems"] "37.prems" by (simp add: env_lookup_def split: option.splits ir_value.splits)
next
  case 38 show ?case by simp
next
  case (39 s st env var en mem rest body env2)
  have ext: "\<forall>y. env_lookup ((var, VEnum en mem) # env) y = env_lookup ((var, VEnum en mem) # env2) y"
    using "39.prems" by (simp add: env_lookup_def)
  have b_eq: "eval s st ((var, VEnum en mem) # env) body = eval s st ((var, VEnum en mem) # env2) body"
    using "39.IH"(1)[OF ext] .
  have r_eq: "eval_forall_enum s st env var en rest body = eval_forall_enum s st env2 var en rest body"
    if "eval s st ((var, VEnum en mem) # env) body = Some (VBool b)" for b
    using "39.IH"(2)[OF that refl "39.prems"] .
  show ?case using b_eq r_eq by (auto split: option.splits ir_value.splits)
next
  case 40 show ?case by simp
next
  case (41 s st env var v rest body env2)
  have ext: "\<forall>y. env_lookup ((var, v) # env) y = env_lookup ((var, v) # env2) y"
    using "41.prems" by (simp add: env_lookup_def)
  have b_eq: "eval s st ((var, v) # env) body = eval s st ((var, v) # env2) body"
    using "41.IH"(1)[OF ext] .
  have r_eq: "eval_forall_rel s st env var rest body = eval_forall_rel s st env2 var rest body"
    if "eval s st ((var, v) # env) body = Some (VBool b)" for b
    using "41.IH"(2)[OF that refl "41.prems"] .
  show ?case using b_eq r_eq by (auto split: option.splits ir_value.splits)
next
  case 42 show ?case by simp
next
  case (43 s st env var v rest body env2)
  have ext: "\<forall>y. env_lookup ((var, v) # env) y = env_lookup ((var, v) # env2) y"
    using "43.prems" by (simp add: env_lookup_def)
  have b_eq: "eval s st ((var, v) # env) body = eval s st ((var, v) # env2) body"
    using "43.IH"(1)[OF ext] .
  have r_eq: "eval_the_rel s st env var rest body = eval_the_rel s st env2 var rest body"
    if "eval s st ((var, v) # env) body = Some (VBool b)" for b
    using "43.IH"(2)[OF that refl "43.prems"] .
  show ?case using b_eq r_eq by (auto split: option.splits ir_value.splits)
qed

lemma eval_forall_rel_cong:
  assumes "\<And>d. d \<in> set dvs \<Longrightarrow> eval s st ((var, d) # env1) body = eval s st ((var, d) # env2) body"
  shows "eval_forall_rel s st env1 var dvs body = eval_forall_rel s st env2 var dvs body"
  using assms by (induction dvs) (auto split: option.splits ir_value.splits)

lemma eval_the_rel_cong:
  assumes "\<And>d. d \<in> set dvs \<Longrightarrow> eval s st ((var, d) # env1) body = eval s st ((var, d) # env2) body"
  shows "eval_the_rel s st env1 var dvs body = eval_the_rel s st env2 var dvs body"
  using assms by (induction dvs) (auto split: option.splits ir_value.splits)

lemma eval_forall_enum_cong:
  assumes "\<And>m. m \<in> set members
              \<Longrightarrow> eval s st ((var, VEnum en m) # env1) body = eval s st ((var, VEnum en m) # env2) body"
  shows "eval_forall_enum s st env1 var en members body = eval_forall_enum s st env2 var en members body"
  using assms by (induction members) (auto split: option.splits ir_value.splits)

lemma lower_forall_step_eval_0cmp:
  assumes b: "lower_forall_step enums bnd body sp = Some e'"
    and inv: "\<And>pre' va' E. eval s st (pre' @ (STR ''0cmp'', va') # E) body = eval s st (pre' @ E) body"
  shows "eval s st (pre @ (STR ''0cmp'', va) # env) e' = eval s st (pre @ env) e'"
proof -
  have fr: "eval_forall_rel s st (pre @ (STR ''0cmp'', va) # env) v dvs body
              = eval_forall_rel s st (pre @ env) v dvs body" for v dvs
  proof (rule eval_forall_rel_cong)
    fix d
    have "eval s st (((v, d) # pre) @ (STR ''0cmp'', va) # env) body
            = eval s st (((v, d) # pre) @ env) body" by (rule inv)
    thus "eval s st ((v, d) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((v, d) # (pre @ env)) body" by simp
  qed
  have fe: "eval_forall_enum s st (pre @ (STR ''0cmp'', va) # env) v en ms body
              = eval_forall_enum s st (pre @ env) v en ms body" for v en ms
  proof (rule eval_forall_enum_cong)
    fix m
    have "eval s st (((v, VEnum en m) # pre) @ (STR ''0cmp'', va) # env) body
            = eval s st (((v, VEnum en m) # pre) @ env) body" by (rule inv)
    thus "eval s st ((v, VEnum en m) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((v, VEnum en m) # (pre @ env)) body" by simp
  qed
  from b show ?thesis
  proof (cases bnd)
    case (QuantifierBindingFull v dm dty a)
    with b show ?thesis
      by (cases dm) (auto simp: fr fe split: if_splits option.splits)
  qed
qed

lemma lower_forall_bindings_eval_0cmp:
  "lower_forall_bindings enums bs body sp = Some e' \<Longrightarrow>
     (\<And>pre' va' E. eval s st (pre' @ (STR ''0cmp'', va') # E) body = eval s st (pre' @ E) body) \<Longrightarrow>
     eval s st (pre @ (STR ''0cmp'', va) # env) e' = eval s st (pre @ env) e'"
proof (induction bs arbitrary: e' pre va env)
  case Nil
  thus ?case by simp
next
  case (Cons b rest)
  show ?case
  proof (cases rest)
    case Nil
    with Cons.prems(1) have st: "lower_forall_step enums b body sp = Some e'" by simp
    show ?thesis by (rule lower_forall_step_eval_0cmp[OF st Cons.prems(2)])
  next
    case (Cons b2 rest2)
    with Cons.prems(1) obtain inner where inner: "lower_forall_bindings enums rest body sp = Some inner"
        and step: "lower_forall_step enums b inner sp = Some e'"
      by (auto split: option.splits)
    have inner_inv: "\<And>pre' va' E. eval s st (pre' @ (STR ''0cmp'', va') # E) inner = eval s st (pre' @ E) inner"
      using Cons.IH[OF inner Cons.prems(2)] .
    show ?thesis by (rule lower_forall_step_eval_0cmp[OF step inner_inv])
  qed
qed

lemma map_of_ins_0cmp:
  "x \<noteq> (STR ''0cmp'') \<Longrightarrow> map_of (pre @ (STR ''0cmp'', va) # env) x = map_of (pre @ env) x"
  by (induction pre) auto

lemma eval_Ident_ins_0cmp:
  assumes "x \<noteq> (STR ''0cmp'')"
  shows "eval s st (pre @ (STR ''0cmp'', va) # env) (Ident x sp) = eval s st (pre @ env) (Ident x sp)"
proof -
  have "env_lookup (pre @ (STR ''0cmp'', va) # env) x = env_lookup (pre @ env) x"
    unfolding env_lookup_def by (rule map_of_ins_0cmp[OF assms])
  thus ?thesis by simp
qed

fun free_vars_e :: "expr \<Rightarrow> String.literal list" where
  "free_vars_e (BoolLit _ _) = []"
| "free_vars_e (IntLit _ _) = []"
| "free_vars_e (RealLit _ _) = []"
| "free_vars_e (Ident x _) = [x]"
| "free_vars_e (UnNot e _) = free_vars_e e"
| "free_vars_e (UnNeg e _) = free_vars_e e"
| "free_vars_e (BoolBin _ l r _) = free_vars_e l @ free_vars_e r"
| "free_vars_e (Arith _ l r _) = free_vars_e l @ free_vars_e r"
| "free_vars_e (Cmp _ l r _) = free_vars_e l @ free_vars_e r"
| "free_vars_e (LetIn x v body _) = free_vars_e v @ remove_name x (free_vars_e body)"
| "free_vars_e (EnumAccess _ _ _) = []"
| "free_vars_e (Member elem _ _) = free_vars_e elem"
| "free_vars_e (ForallEnum var _ body _) = remove_name var (free_vars_e body)"
| "free_vars_e (ForallRel var _ body _) = remove_name var (free_vars_e body)"
| "free_vars_e (ForallSet var setE body _) = free_vars_e setE @ remove_name var (free_vars_e body)"
| "free_vars_e (TheRel var _ body _) = remove_name var (free_vars_e body)"
| "free_vars_e (EntityBase _ _) = []"
| "free_vars_e (Prime e _) = free_vars_e e"
| "free_vars_e (Pre e _) = free_vars_e e"
| "free_vars_e (CardRel _ _) = []"
| "free_vars_e (IndexRel b k _) = free_vars_e b @ free_vars_e k"
| "free_vars_e (FieldAccess b _ _) = free_vars_e b"
| "free_vars_e (SetEmpty _) = []"
| "free_vars_e (SetInsert e s _) = free_vars_e e @ free_vars_e s"
| "free_vars_e (SetMember e s _) = free_vars_e e @ free_vars_e s"
| "free_vars_e (SetBin _ l r _) = free_vars_e l @ free_vars_e r"
| "free_vars_e (WithRec b _ v _) = free_vars_e b @ free_vars_e v"
| "free_vars_e (Ite c a b _) = free_vars_e c @ free_vars_e a @ free_vars_e b"
| "free_vars_e (NoneE _) = []"
| "free_vars_e (SomeE e _) = free_vars_e e"
| "free_vars_e (StrLit _ _) = []"
| "free_vars_e (Matches e _ _) = free_vars_e e"
| "free_vars_e (UStrPred _ e _) = free_vars_e e"
| "free_vars_e (SeqEmpty _) = []"
| "free_vars_e (SeqCons e r _) = free_vars_e e @ free_vars_e r"
| "free_vars_e (MapEmpty _) = []"
| "free_vars_e (MapCons k v r _) = free_vars_e k @ free_vars_e v @ free_vars_e r"

lemma eval_shadow_0cmp:
  assumes "k = STR ''0cmp''"
  shows "eval s st ((k, vv) # pre @ (STR ''0cmp'', va) # env) e = eval s st ((k, vv) # pre @ env) e"
proof (intro eval_env_cong(1) allI)
  fix y
  show "env_lookup ((k, vv) # pre @ (STR ''0cmp'', va) # env) y = env_lookup ((k, vv) # pre @ env) y"
    using assms map_of_ins_0cmp[of y "(k, vv) # pre" va env] by (auto simp: env_lookup_def)
qed

lemma eval_ins_0cmp:
  "\<not> string_in_list (STR ''0cmp'') (free_vars_e e)
     \<Longrightarrow> eval s st (pre @ (STR ''0cmp'', va) # env) e = eval s st (pre @ env) e"
proof (induction e arbitrary: pre va env)
  case (LetIn x v body sp)
  have vfr: "\<not> string_in_list (STR ''0cmp'') (free_vars_e v)" using LetIn.prems by simp
  have v_eq: "eval s st (pre @ (STR ''0cmp'', va) # env) v = eval s st (pre @ env) v"
    using LetIn.IH(1)[OF vfr] .
  have body_eq: "eval s st ((x, vv) # (pre @ (STR ''0cmp'', va) # env)) body
                   = eval s st ((x, vv) # (pre @ env)) body" for vv
  proof (cases "x = STR ''0cmp''")
    case True show ?thesis by (rule eval_shadow_0cmp[OF True])
  next
    case False
    hence "\<not> string_in_list (STR ''0cmp'') (free_vars_e body)"
      using LetIn.prems by (auto simp: string_in_list_remove_name)
    from LetIn.IH(2)[OF this, of "(x, vv) # pre" va env] show ?thesis by simp
  qed
  show ?case using v_eq body_eq by (simp split: option.splits)
next
  case (ForallEnum var en body sp)
  have inv: "eval_forall_enum s st (pre @ (STR ''0cmp'', va) # env) var en ms body
               = eval_forall_enum s st (pre @ env) var en ms body" for ms
  proof (rule eval_forall_enum_cong)
    fix m
    show "eval s st ((var, VEnum en m) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((var, VEnum en m) # (pre @ env)) body"
    proof (cases "var = STR ''0cmp''")
      case True show ?thesis by (rule eval_shadow_0cmp[OF True])
    next
      case False
      hence "\<not> string_in_list (STR ''0cmp'') (free_vars_e body)"
        using ForallEnum.prems by (auto simp: string_in_list_remove_name)
      from ForallEnum.IH[OF this, of "(var, VEnum en m) # pre" va env] show ?thesis by simp
    qed
  qed
  show ?case using inv by (simp split: option.splits)
next
  case (ForallRel var rel body sp)
  have inv: "eval_forall_rel s st (pre @ (STR ''0cmp'', va) # env) var rd body
               = eval_forall_rel s st (pre @ env) var rd body" for rd
  proof (rule eval_forall_rel_cong)
    fix d
    show "eval s st ((var, d) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((var, d) # (pre @ env)) body"
    proof (cases "var = STR ''0cmp''")
      case True show ?thesis by (rule eval_shadow_0cmp[OF True])
    next
      case False
      hence "\<not> string_in_list (STR ''0cmp'') (free_vars_e body)"
        using ForallRel.prems by (auto simp: string_in_list_remove_name)
      from ForallRel.IH[OF this, of "(var, d) # pre" va env] show ?thesis by simp
    qed
  qed
  show ?case using inv by (simp split: option.splits)
next
  case (ForallSet var setE body sp)
  have setfr: "\<not> string_in_list (STR ''0cmp'') (free_vars_e setE)" using ForallSet.prems by simp
  have setE_eq: "eval s st (pre @ (STR ''0cmp'', va) # env) setE = eval s st (pre @ env) setE"
    using ForallSet.IH(1)[OF setfr] .
  have inv: "eval_forall_rel s st (pre @ (STR ''0cmp'', va) # env) var elems body
               = eval_forall_rel s st (pre @ env) var elems body" for elems
  proof (rule eval_forall_rel_cong)
    fix d
    show "eval s st ((var, d) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((var, d) # (pre @ env)) body"
    proof (cases "var = STR ''0cmp''")
      case True show ?thesis by (rule eval_shadow_0cmp[OF True])
    next
      case False
      hence "\<not> string_in_list (STR ''0cmp'') (free_vars_e body)"
        using ForallSet.prems by (auto simp: string_in_list_remove_name)
      from ForallSet.IH(2)[OF this, of "(var, d) # pre" va env] show ?thesis by simp
    qed
  qed
  show ?case using setE_eq inv by (simp split: option.splits ir_value.split)
next
  case (TheRel var rel body sp)
  have inv: "eval_the_rel s st (pre @ (STR ''0cmp'', va) # env) var rd body
               = eval_the_rel s st (pre @ env) var rd body" for rd
  proof (rule eval_the_rel_cong)
    fix d
    show "eval s st ((var, d) # (pre @ (STR ''0cmp'', va) # env)) body
            = eval s st ((var, d) # (pre @ env)) body"
    proof (cases "var = STR ''0cmp''")
      case True show ?thesis by (rule eval_shadow_0cmp[OF True])
    next
      case False
      hence "\<not> string_in_list (STR ''0cmp'') (free_vars_e body)"
        using TheRel.prems by (auto simp: string_in_list_remove_name)
      from TheRel.IH[OF this, of "(var, d) # pre" va env] show ?thesis by simp
    qed
  qed
  show ?case using inv by (simp split: option.splits ir_value.split list.split)
qed (auto simp: map_of_ins_0cmp env_lookup_def eval_Ident_ins_0cmp split: option.splits ir_value.splits bool.split)

lemma lower_forall_step_fv:
  assumes "lower_forall_step enums bnd body sp = Some e'"
      and "string_in_list y (qb_names [bnd]) \<or> \<not> string_in_list y (free_vars_e body)"
  shows "\<not> string_in_list y (free_vars_e e')"
proof (cases bnd)
  case (QuantifierBindingFull v dm dty a)
  show ?thesis using assms unfolding QuantifierBindingFull
    by (cases dm) (auto split: if_splits simp: string_in_list_remove_name)
qed

lemma lower_forall_bindings_fv:
  "lower_forall_bindings enums bs body sp = Some e'
     \<Longrightarrow> string_in_list y (qb_names bs) \<or> \<not> string_in_list y (free_vars_e body)
     \<Longrightarrow> \<not> string_in_list y (free_vars_e e')"
proof (induction bs arbitrary: e')
  case Nil
  thus ?case by simp
next
  case (Cons b rest)
  show ?case
  proof (cases rest)
    case Nil
    with Cons.prems(1) have st: "lower_forall_step enums b body sp = Some e'" by simp
    from Cons.prems(2) Nil have "string_in_list y (qb_names [b]) \<or> \<not> string_in_list y (free_vars_e body)" by simp
    thus ?thesis using lower_forall_step_fv[OF st] by blast
  next
    case (Cons b2 rest2)
    with Cons.prems(1) obtain inner where inner: "lower_forall_bindings enums rest body sp = Some inner"
        and step: "lower_forall_step enums b inner sp = Some e'"
      by (auto split: option.splits)
    have qn: "string_in_list y (qb_names (b # rest))
                = (string_in_list y (qb_names [b]) \<or> string_in_list y (qb_names rest))"
      by (cases b) auto
    show ?thesis
    proof (cases "string_in_list y (qb_names [b])")
      case True thus ?thesis using lower_forall_step_fv[OF step] by blast
    next
      case False
      hence "string_in_list y (qb_names rest) \<or> \<not> string_in_list y (free_vars_e body)"
        using Cons.prems(2) qn by auto
      hence "\<not> string_in_list y (free_vars_e inner)" using Cons.IH[OF inner] by blast
      thus ?thesis using lower_forall_step_fv[OF step] by blast
    qed
  qed
qed

lemma lower_set_comp_eq_fv:
  "\<not> string_in_list (STR ''0cmp'') (free_vars_e setE)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e (lower_set_comp_eq enums var dnm setE predE sp))"
  by (simp add: Let_def string_in_list_remove_name)

lemma lowerSetList_fv_le:
  assumes "\<And>x e''. x \<in> set es \<Longrightarrow> lower enums x = Some e'' \<Longrightarrow> set (free_vars_e e'') \<subseteq> set (free_vars x)"
      and "lowerSetList enums es sp = Some es'"
  shows "set (free_vars_e es') \<subseteq> set (free_vars_list es)"
  using assms
proof (induction es arbitrary: es')
  case Nil thus ?case by auto
next
  case (Cons a rest)
  from Cons.prems(2) obtain e'' s' where lx: "lower enums a = Some e''"
      and lr: "lowerSetList enums rest sp = Some s'" and es': "es' = SetInsert e'' s' sp"
    by (auto split: option.splits)
  have rest_hyp: "\<And>y e3. y \<in> set rest \<Longrightarrow> lower enums y = Some e3 \<Longrightarrow> set (free_vars_e e3) \<subseteq> set (free_vars y)"
    using Cons.prems(1) by auto
  have "set (free_vars_e e'') \<subseteq> set (free_vars a)" using Cons.prems(1) lx by simp
  moreover have "set (free_vars_e s') \<subseteq> set (free_vars_list rest)" using Cons.IH[OF rest_hyp lr] .
  ultimately show ?case using es' by auto
qed

lemma lowerSeqList_fv_le:
  assumes "\<And>x e''. x \<in> set es \<Longrightarrow> lower enums x = Some e'' \<Longrightarrow> set (free_vars_e e'') \<subseteq> set (free_vars x)"
      and "lowerSeqList enums es sp = Some es'"
  shows "set (free_vars_e es') \<subseteq> set (free_vars_list es)"
  using assms
proof (induction es arbitrary: es')
  case Nil thus ?case by auto
next
  case (Cons a rest)
  from Cons.prems(2) obtain e'' s' where lx: "lower enums a = Some e''"
      and lr: "lowerSeqList enums rest sp = Some s'" and es': "es' = SeqCons e'' s' sp"
    by (auto split: option.splits)
  have rest_hyp: "\<And>y e3. y \<in> set rest \<Longrightarrow> lower enums y = Some e3 \<Longrightarrow> set (free_vars_e e3) \<subseteq> set (free_vars y)"
    using Cons.prems(1) by auto
  have "set (free_vars_e e'') \<subseteq> set (free_vars a)" using Cons.prems(1) lx by simp
  moreover have "set (free_vars_e s') \<subseteq> set (free_vars_list rest)" using Cons.IH[OF rest_hyp lr] .
  ultimately show ?case using es' by auto
qed

lemma lowerMapEntries_fv_le:
  assumes "\<And>k v sp2 e''. MapEntryFull k v sp2 \<in> set ents \<Longrightarrow> lower enums k = Some e'' \<Longrightarrow> set (free_vars_e e'') \<subseteq> set (free_vars k)"
      and "\<And>k v sp2 e''. MapEntryFull k v sp2 \<in> set ents \<Longrightarrow> lower enums v = Some e'' \<Longrightarrow> set (free_vars_e e'') \<subseteq> set (free_vars v)"
      and "lowerMapEntries enums ents sp = Some me'"
  shows "set (free_vars_e me') \<subseteq> set (free_vars_entries ents)"
  using assms
proof (induction ents arbitrary: me')
  case Nil from Nil.prems(3) show ?case by auto
next
  case (Cons ent rest)
  obtain k v sp2 where ent: "ent = MapEntryFull k v sp2" by (cases ent)
  from Cons.prems(3) ent obtain k' v' m' where lk: "lower enums k = Some k'"
      and lv: "lower enums v = Some v'" and lm: "lowerMapEntries enums rest sp = Some m'"
      and me': "me' = MapCons k' v' m' sp"
    by (auto split: option.splits)
  have hk: "set (free_vars_e k') \<subseteq> set (free_vars k)" using Cons.prems(1)[of k v sp2 k'] ent lk by simp
  have hv: "set (free_vars_e v') \<subseteq> set (free_vars v)" using Cons.prems(2)[of k v sp2 v'] ent lv by simp
  have rk: "\<And>ka va sp3 e3. MapEntryFull ka va sp3 \<in> set rest \<Longrightarrow> lower enums ka = Some e3 \<Longrightarrow> set (free_vars_e e3) \<subseteq> set (free_vars ka)"
  proof -
    fix ka va sp3 e3 assume m: "MapEntryFull ka va sp3 \<in> set rest" and l: "lower enums ka = Some e3"
    have "MapEntryFull ka va sp3 \<in> set (ent # rest)" using m by simp
    thus "set (free_vars_e e3) \<subseteq> set (free_vars ka)" using Cons.prems(1) l by blast
  qed
  have rv: "\<And>ka va sp3 e3. MapEntryFull ka va sp3 \<in> set rest \<Longrightarrow> lower enums va = Some e3 \<Longrightarrow> set (free_vars_e e3) \<subseteq> set (free_vars va)"
  proof -
    fix ka va sp3 e3 assume m: "MapEntryFull ka va sp3 \<in> set rest" and l: "lower enums va = Some e3"
    have "MapEntryFull ka va sp3 \<in> set (ent # rest)" using m by simp
    thus "set (free_vars_e e3) \<subseteq> set (free_vars va)" using Cons.prems(2) l by blast
  qed
  have "set (free_vars_e m') \<subseteq> set (free_vars_entries rest)" using Cons.IH[OF rk rv lm] .
  thus ?case using hk hv me' ent by auto
qed

lemma lower_with_assigns_fv_le:
  assumes "\<And>fld v sp2 e''. FieldAssignFull fld v sp2 \<in> set fas \<Longrightarrow> lower enums v = Some e'' \<Longrightarrow> set (free_vars_e e'') \<subseteq> set (free_vars v)"
      and "lower_with_assigns enums fas base sp = Some fe'"
  shows "set (free_vars_e fe') \<subseteq> set (free_vars_e base) \<union> set (free_vars_fields fas)"
  using assms
proof (induction fas arbitrary: base fe')
  case Nil thus ?case by auto
next
  case (Cons fa rest)
  obtain fld v sp2 where fa: "fa = FieldAssignFull fld v sp2" by (cases fa)
  from Cons.prems(2) fa obtain v' where lv: "lower enums v = Some v'"
      and rec: "lower_with_assigns enums rest (WithRec base fld v' sp) sp = Some fe'"
    by (auto split: option.splits)
  have hv: "set (free_vars_e v') \<subseteq> set (free_vars v)" using Cons.prems(1)[of fld v sp2 v'] lv fa by simp
  have rest_hyp: "\<And>fld2 w sp3 e3. FieldAssignFull fld2 w sp3 \<in> set rest \<Longrightarrow> lower enums w = Some e3
        \<Longrightarrow> set (free_vars_e e3) \<subseteq> set (free_vars w)"
  proof -
    fix fld2 w sp3 e3 assume m: "FieldAssignFull fld2 w sp3 \<in> set rest" and l: "lower enums w = Some e3"
    have "FieldAssignFull fld2 w sp3 \<in> set (fa # rest)" using m by simp
    thus "set (free_vars_e e3) \<subseteq> set (free_vars w)" using Cons.prems(1) l by blast
  qed
  have "set (free_vars_e fe') \<subseteq> set (free_vars_e (WithRec base fld v' sp)) \<union> set (free_vars_fields rest)"
    using Cons.IH[OF rest_hyp rec] .
  thus ?case using hv fa by auto
qed

lemma string_in_list_set: "string_in_list y xs = (y \<in> set xs)"
  by (induction xs) auto

lemma set_remove_name [simp]: "set (remove_name n xs) = set xs - {n}"
  by (induction xs) auto

lemma set_remove_names [simp]: "set (remove_names ns xs) = set xs - set ns"
  by (induction ns) auto

lemma lower_forall_step_fv_subset:
  assumes "lower_forall_step enums b body sp = Some e'"
  shows "set (free_vars_e e') \<subseteq> set (free_vars_e body) - set (qb_names [b])"
proof (cases b)
  case (QuantifierBindingFull v dm dty a)
  with assms show ?thesis by (cases dm) (auto split: if_splits)
qed

lemma lower_forall_bindings_fv_subset:
  "lower_forall_bindings enums bs body sp = Some e'
     \<Longrightarrow> set (free_vars_e e') \<subseteq> set (free_vars_e body) - set (qb_names bs)"
proof (induction bs arbitrary: e')
  case Nil thus ?case by simp
next
  case (Cons b rest)
  show ?case
  proof (cases rest)
    case Nil
    with Cons.prems have "lower_forall_step enums b body sp = Some e'" by simp
    with lower_forall_step_fv_subset[of enums b body sp e'] Nil show ?thesis by simp
  next
    case (Cons b2 rest2)
    with Cons.prems obtain inner where inner: "lower_forall_bindings enums rest body sp = Some inner"
        and step: "lower_forall_step enums b inner sp = Some e'"
      by (auto split: option.splits)
    have "set (free_vars_e e') \<subseteq> set (free_vars_e inner) - set (qb_names [b])"
      using lower_forall_step_fv_subset[OF step] .
    moreover have "set (free_vars_e inner) \<subseteq> set (free_vars_e body) - set (qb_names rest)"
      using Cons.IH[OF inner] .
    moreover have "set (qb_names (b # rest)) = set (qb_names [b]) \<union> set (qb_names rest)"
      by (cases b) auto
    ultimately show ?thesis by auto
  qed
qed

lemma lower_BNotIn_char:
  "lower enums (BinaryOpF BNotIn l r sp)
     = (case r of IdentifierF rel _ \<Rightarrow> map_option (\<lambda>l'. UnNot (Member l' rel sp) sp) (lower enums l)
        | _ \<Rightarrow> (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (UnNot (SetMember l' r' sp) sp) | _ \<Rightarrow> None))"
  by (simp split: expr_full.splits)

lemma lower_BIn_noncomp_fv:
  assumes lo: "lower enums (BinaryOpF BIn l r sp) = Some e'"
      and nc: "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
      and hl: "\<And>l'. lower enums l = Some l' \<Longrightarrow> set (free_vars_e l') \<subseteq> set (free_vars l)"
      and hr: "\<And>r'. lower enums r = Some r' \<Longrightarrow> set (free_vars_e r') \<subseteq> set (free_vars r)"
  shows "set (free_vars_e e') \<subseteq> set (free_vars l) \<union> set (free_vars r)"
  using lo[unfolded lower_BIn_noncomp[OF nc]] hl hr
  by (cases r) (auto split: option.splits del: lower.simps)

lemma lower_BNotIn_fv:
  assumes lo: "lower enums (BinaryOpF BNotIn l r sp) = Some e'"
      and hl: "\<And>l'. lower enums l = Some l' \<Longrightarrow> set (free_vars_e l') \<subseteq> set (free_vars l)"
      and hr: "\<And>r'. lower enums r = Some r' \<Longrightarrow> set (free_vars_e r') \<subseteq> set (free_vars r)"
  shows "set (free_vars_e e') \<subseteq> set (free_vars l) \<union> set (free_vars r)"
  using lo[unfolded lower_BNotIn_char] hl hr
  by (cases r) (auto split: option.splits del: lower.simps)

lemma lower_dom_eq_fv: "free_vars_e (lower_dom_eq xrel yrel sp) = []"
  by (simp add: lower_dom_eq_def)

lemma lower_fv_le: "lower enums e = Some e' \<Longrightarrow> set (free_vars_e e') \<subseteq> set (free_vars e)"
proof (induction e arbitrary: enums e' rule: measure_induct_rule[where f = size])
  case (less e)
  show ?case
  proof (cases e)
    case (IdentifierF x sp2) with less.prems show ?thesis by auto
  next
    case (IntLitF n sp2) with less.prems show ?thesis by auto
  next
    case (FloatLitF s sp2) with less.prems show ?thesis by (auto split: option.splits)
  next
    case (StringLitF v sp2) with less.prems show ?thesis by auto
  next
    case (BoolLitF b sp2) with less.prems show ?thesis by auto
  next
    case (NoneLitF sp2) with less.prems show ?thesis by auto
  next
    case (LambdaF p b sp2) with less.prems show ?thesis by simp
  next
    case (CallF c args sp2)
    show ?thesis
    proof (cases "\<exists>nm sp1 arg. c = IdentifierF nm sp1 \<and> args = [arg] \<and> is_builtin_pred nm")
      case True
      then obtain nm sp1 arg where ceq: "c = IdentifierF nm sp1" and aeq: "args = [arg]"
          and bip: "is_builtin_pred nm" by blast
      from less.prems CallF ceq aeq bip obtain a' where la: "lower enums arg = Some a'"
          and eq: "e' = UStrPred nm a' sp2"
        by (auto split: option.splits)
      have s: "size arg < size e" using CallF aeq by simp
      have ha: "set (free_vars_e a') \<subseteq> set (free_vars arg)" using less.IH[OF s la] .
      show ?thesis using ha eq CallF ceq aeq by auto
    next
      case False
      then have "lower enums (CallF c args sp2) = None"
        by (auto split: expr_full.splits list.splits if_splits)
      then show ?thesis using less.prems CallF by simp
    qed
  next
    case (SetComprehensionF v d p sp2) with less.prems show ?thesis by simp
  next
    case (PrimeF c sp2)
    from less.prems PrimeF obtain c' where lc: "lower enums c = Some c'" and eq: "e' = Prime c' sp2"
      by (auto split: option.splits)
    have s: "size c < size e" using PrimeF by simp
    show ?thesis using less.IH[OF s lc] eq PrimeF by simp
  next
    case (PreF c sp2)
    from less.prems PreF obtain c' where lc: "lower enums c = Some c'" and eq: "e' = Pre c' sp2"
      by (auto split: option.splits)
    have s: "size c < size e" using PreF by simp
    show ?thesis using less.IH[OF s lc] eq PreF by simp
  next
    case (SomeWrapF c sp2)
    from less.prems SomeWrapF obtain c' where lc: "lower enums c = Some c'" and eq: "e' = SomeE c' sp2"
      by (auto split: option.splits)
    have s: "size c < size e" using SomeWrapF by simp
    show ?thesis using less.IH[OF s lc] eq SomeWrapF by simp
  next
    case (MatchesF c pat sp2)
    from less.prems MatchesF obtain c' where lc: "lower enums c = Some c'" and eq: "e' = Matches c' pat sp2"
      by (auto split: option.splits)
    have s: "size c < size e" using MatchesF by simp
    show ?thesis using less.IH[OF s lc] eq MatchesF by simp
  next
    case (FieldAccessF c f sp2)
    from less.prems FieldAccessF obtain c' where lc: "lower enums c = Some c'" and eq: "e' = FieldAccess c' f sp2"
      by (auto split: option.splits)
    have s: "size c < size e" using FieldAccessF by simp
    show ?thesis using less.IH[OF s lc] eq FieldAccessF by simp
  next
    case (EnumAccessF base mem sp2)
    with less.prems show ?thesis by (cases base) (auto split: option.splits)
  next
    case (UnaryOpF op2 c sp2)
    show ?thesis
    proof (cases op2)
      case UNot
      from less.prems UnaryOpF UNot obtain c' where lc: "lower enums c = Some c'" and eq: "e' = UnNot c' sp2"
        by (auto split: option.splits)
      have s: "size c < size e" using UnaryOpF by simp
      show ?thesis using less.IH[OF s lc] eq UnaryOpF by simp
    next
      case UNegate
      from less.prems UnaryOpF UNegate obtain c' where lc: "lower enums c = Some c'" and eq: "e' = UnNeg c' sp2"
        by (auto split: option.splits)
      have s: "size c < size e" using UnaryOpF by simp
      show ?thesis using less.IH[OF s lc] eq UnaryOpF by simp
    next
      case UCardinality with less.prems UnaryOpF show ?thesis by (cases c) (auto split: option.splits)
    next
      case UPower with less.prems UnaryOpF show ?thesis by (auto split: option.splits)
    qed
  next
    case (IndexF base key sp2)
    from less.prems IndexF obtain b' k' where lb: "lower enums base = Some b'" and lk: "lower enums key = Some k'"
        and eq: "e' = IndexRel b' k' sp2"
      by (auto split: option.splits)
    have sb: "size base < size e" and sk: "size key < size e" using IndexF by simp_all
    show ?thesis using less.IH[OF sb lb] less.IH[OF sk lk] eq IndexF by auto
  next
    case (IfF c a b sp2)
    from less.prems IfF obtain c' a' b' where lc: "lower enums c = Some c'" and la: "lower enums a = Some a'"
        and lb: "lower enums b = Some b'" and eq: "e' = Ite c' a' b' sp2"
      by (auto split: option.splits)
    have "size c < size e" "size a < size e" "size b < size e" using IfF by simp_all
    thus ?thesis using less.IH[OF _ lc] less.IH[OF _ la] less.IH[OF _ lb] eq IfF by auto
  next
    case (LetF x v body sp2)
    from less.prems LetF obtain v' b' where lv: "lower enums v = Some v'" and lb: "lower enums body = Some b'"
        and eq: "e' = LetIn x v' b' sp2"
      by (auto split: option.splits)
    have "size v < size e" "size body < size e" using LetF by simp_all
    thus ?thesis using less.IH[OF _ lv] less.IH[OF _ lb] eq LetF by auto
  next
    case (TheF var dm body sp2)
    show ?thesis
    proof (cases dm)
      case (IdentifierF rel rsp)
      from less.prems TheF IdentifierF obtain b' where lb: "lower enums body = Some b'"
          and eq: "e' = TheRel var rel b' sp2"
        by (auto split: option.splits if_splits)
      have s: "size body < size e" using TheF by simp
      show ?thesis using less.IH[OF s lb] eq TheF IdentifierF by auto
    qed (use less.prems TheF in \<open>auto split: option.splits\<close>)
  next
    case (BinaryOpF op2 l2 r2 sp2)
    have sl: "size l2 < size e" and sr: "size r2 < size e" using BinaryOpF by simp_all
    show ?thesis
    proof (cases op2)
      case BAnd
      from less.prems BinaryOpF BAnd obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = BoolBin AndOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BOr
      from less.prems BinaryOpF BOr obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = BoolBin OrOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BImplies
      from less.prems BinaryOpF BImplies obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = BoolBin ImpliesOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BIff
      from less.prems BinaryOpF BIff obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = BoolBin IffOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BNeq
      from less.prems BinaryOpF BNeq obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp NeqOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BLt
      from less.prems BinaryOpF BLt obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp LtOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BGt
      from less.prems BinaryOpF BGt obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp GtOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BLe
      from less.prems BinaryOpF BLe obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp LeOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BGe
      from less.prems BinaryOpF BGe obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp GeOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BAdd
      from less.prems BinaryOpF BAdd obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Arith AddOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BSub
      from less.prems BinaryOpF BSub obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Arith SubOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BMul
      from less.prems BinaryOpF BMul obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Arith MulOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BDiv
      from less.prems BinaryOpF BDiv obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Arith DivOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BUnion
      from less.prems BinaryOpF BUnion obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = SetBin UnionOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BIntersect
      from less.prems BinaryOpF BIntersect obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = SetBin IntersectOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BDiff
      from less.prems BinaryOpF BDiff obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = SetBin DiffOp l' r' sp2" by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BSubset
      from less.prems BinaryOpF BSubset obtain l' r' where ll: "lower enums l2 = Some l'"
          and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp EqOp (SetBin DiffOp l' r' sp2) (SetEmpty sp2) sp2"
        by (auto split: option.splits)
      show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
    next
      case BEq
      show ?thesis
      proof (cases "\<exists>xrel yrel. dom_arg l2 = Some xrel \<and> dom_arg r2 = Some yrel")
        case True
        then obtain xrel yrel where da: "dom_arg l2 = Some xrel" "dom_arg r2 = Some yrel" by blast
        have e'eq: "e' = lower_dom_eq xrel yrel sp2"
          using less.prems[unfolded BinaryOpF BEq lower_BEq_dom[OF da(1) da(2)]] by simp
        show ?thesis using e'eq by (simp add: lower_dom_eq_fv)
      next
        case False
        hence dnone: "dom_arg l2 = None \<or> dom_arg r2 = None" by auto
        show ?thesis
        proof (cases "beq_comp BEq r2")
        case (Some t)
        then obtain cvar dnm cpred where bc: "beq_comp BEq r2 = Some (cvar, dnm, cpred)" by (cases t) auto
        from bc obtain s2 s3 where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3"
          by (cases r2) (auto split: expr_full.splits)
        from less.prems BinaryOpF BEq req obtain l' p' where ll: "lower enums l2 = Some l'"
            and lp: "lower enums cpred = Some p'" and eq: "e' = lower_set_comp_eq enums cvar dnm l' p' sp2"
          by (auto split: option.splits)
        have spred: "size cpred < size e" using BinaryOpF req by simp
        have hl: "set (free_vars_e l') \<subseteq> set (free_vars l2)" using less.IH[OF sl ll] .
        have hp: "set (free_vars_e p') \<subseteq> set (free_vars cpred)" using less.IH[OF spred lp] .
        show ?thesis using hl hp eq BinaryOpF req by (auto simp: Let_def split: if_splits)
      next
        case None
        hence nc: "\<nexists>v d s2 p s3. r2 = SetComprehensionF v (IdentifierF d s2) p s3" by (cases r2) auto
        have leq: "lower enums (BinaryOpF BEq l2 r2 sp2)
                     = (case (lower enums l2, lower enums r2) of (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp2) | _ \<Rightarrow> None)"
          by (rule lower_BEq_noncomp[OF nc dnone])
        from less.prems[unfolded BinaryOpF BEq leq] obtain l' r' where ll: "lower enums l2 = Some l'"
            and lr: "lower enums r2 = Some r'" and eq: "e' = Cmp EqOp l' r' sp2"
          by (auto split: option.splits del: lower.simps)
        show ?thesis using less.IH[OF sl ll] less.IH[OF sr lr] eq BinaryOpF by auto
      qed
      qed
    next
      case BIn
      show ?thesis
      proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
        case True
        then obtain cvar dnm s2 cpred s3 where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
        from less.prems BinaryOpF BIn req obtain l' p' where ll: "lower enums l2 = Some l'"
            and lp: "lower enums cpred = Some p'"
            and eq: "e' = LetIn cvar l' (if string_in_list dnm enums then p'
                        else BoolBin AndOp (Member (Ident cvar None) dnm sp2) p' sp2) sp2"
          by (auto split: option.splits)
        have spred: "size cpred < size e" using BinaryOpF req by simp
        have hl: "set (free_vars_e l') \<subseteq> set (free_vars l2)" using less.IH[OF sl ll] .
        have hp: "set (free_vars_e p') \<subseteq> set (free_vars cpred)" using less.IH[OF spred lp] .
        show ?thesis using hl hp eq BinaryOpF req by (auto split: if_splits)
      next
        case False
        hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4" by blast
        have "set (free_vars_e e') \<subseteq> set (free_vars l2) \<union> set (free_vars r2)"
          by (rule lower_BIn_noncomp_fv[OF less.prems[unfolded BinaryOpF BIn] nc less.IH[OF sl] less.IH[OF sr]])
        thus ?thesis using BinaryOpF by simp
      qed
    next
      case BNotIn
      have "set (free_vars_e e') \<subseteq> set (free_vars l2) \<union> set (free_vars r2)"
        by (rule lower_BNotIn_fv[OF less.prems[unfolded BinaryOpF BNotIn] less.IH[OF sl] less.IH[OF sr]])
      thus ?thesis using BinaryOpF by simp
    qed
  next
    case (QuantifierF k bs body sp2)
    from less.prems QuantifierF obtain body' where lbody: "lower enums body = Some body'"
      by (auto split: option.splits)
    have sbody: "size body < size e" using QuantifierF by simp
    have hbody: "set (free_vars_e body') \<subseteq> set (free_vars body)" using less.IH[OF sbody lbody] .
    have "set (free_vars_e e') \<subseteq> set (free_vars_e body') - set (qb_names bs)"
    proof (cases k)
      case QAll
      with less.prems QuantifierF lbody have "lower_forall_bindings enums bs body' sp2 = Some e'" by simp
      thus ?thesis using lower_forall_bindings_fv_subset by simp
    next
      case QNo
      with less.prems QuantifierF lbody have "lower_forall_bindings enums bs (UnNot body' sp2) sp2 = Some e'" by simp
      thus ?thesis using lower_forall_bindings_fv_subset[of enums bs "UnNot body' sp2" sp2 e'] by simp
    next
      case QSome
      with less.prems QuantifierF lbody obtain inner where
          bnd: "lower_forall_bindings enums bs (UnNot body' sp2) sp2 = Some inner" and eq: "e' = UnNot inner sp2"
        by (auto split: option.splits)
      from lower_forall_bindings_fv_subset[OF bnd] eq show ?thesis by simp
    next
      case QExists
      with less.prems QuantifierF lbody obtain inner where
          bnd: "lower_forall_bindings enums bs (UnNot body' sp2) sp2 = Some inner" and eq: "e' = UnNot inner sp2"
        by (auto split: option.splits)
      from lower_forall_bindings_fv_subset[OF bnd] eq show ?thesis by simp
    qed
    thus ?thesis using hbody QuantifierF by auto
  next
    case (ConstructorF name fas sp2)
    have "set (free_vars_e e') \<subseteq> set (free_vars_e (EntityBase name sp2)) \<union> set (free_vars_fields fas)"
    proof (rule lower_with_assigns_fv_le)
      fix fld v sp3 e'' assume fin: "FieldAssignFull fld v sp3 \<in> set fas" and lv: "lower enums v = Some e''"
      have "size v < size e" using ConstructorF size_list_estimation'[OF fin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars v)" using less.IH lv by blast
    next
      show "lower_with_assigns enums fas (EntityBase name sp2) sp2 = Some e'" using less.prems ConstructorF by simp
    qed
    thus ?thesis using ConstructorF by simp
  next
    case (WithF base upds sp2)
    from less.prems WithF obtain base' where lbase: "lower enums base = Some base'"
        and lwa: "lower_with_assigns enums upds base' sp2 = Some e'"
      by (auto split: option.splits)
    have sbase: "size base < size e" using WithF by simp
    have hbase: "set (free_vars_e base') \<subseteq> set (free_vars base)" using less.IH[OF sbase lbase] .
    have "set (free_vars_e e') \<subseteq> set (free_vars_e base') \<union> set (free_vars_fields upds)"
    proof (rule lower_with_assigns_fv_le[OF _ lwa])
      fix fld v sp3 e'' assume fin: "FieldAssignFull fld v sp3 \<in> set upds" and lv: "lower enums v = Some e''"
      have "size v < size e" using WithF size_list_estimation'[OF fin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars v)" using less.IH lv by blast
    qed
    thus ?thesis using hbase WithF by auto
  next
    case (SetLiteralF elems sp2)
    have "set (free_vars_e e') \<subseteq> set (free_vars_list elems)"
    proof (rule lowerSetList_fv_le)
      fix x e'' assume xin: "x \<in> set elems" and lx: "lower enums x = Some e''"
      have "size x < size e" using SetLiteralF size_list_estimation'[OF xin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars x)" using less.IH lx by blast
    next
      show "lowerSetList enums elems sp2 = Some e'" using less.prems SetLiteralF by simp
    qed
    thus ?thesis using SetLiteralF by simp
  next
    case (SeqLiteralF elems sp2)
    have "set (free_vars_e e') \<subseteq> set (free_vars_list elems)"
    proof (rule lowerSeqList_fv_le)
      fix x e'' assume xin: "x \<in> set elems" and lx: "lower enums x = Some e''"
      have "size x < size e" using SeqLiteralF size_list_estimation'[OF xin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars x)" using less.IH lx by blast
    next
      show "lowerSeqList enums elems sp2 = Some e'" using less.prems SeqLiteralF by simp
    qed
    thus ?thesis using SeqLiteralF by simp
  next
    case (MapLiteralF entries sp2)
    have "set (free_vars_e e') \<subseteq> set (free_vars_entries entries)"
    proof (rule lowerMapEntries_fv_le)
      fix k v sp3 e'' assume kin: "MapEntryFull k v sp3 \<in> set entries" and lk: "lower enums k = Some e''"
      have "size k < size e" using MapLiteralF size_list_estimation'[OF kin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars k)" using less.IH lk by blast
    next
      fix k v sp3 e'' assume kin: "MapEntryFull k v sp3 \<in> set entries" and lv: "lower enums v = Some e''"
      have "size v < size e" using MapLiteralF size_list_estimation'[OF kin order_refl, where f = size] by simp
      thus "set (free_vars_e e'') \<subseteq> set (free_vars v)" using less.IH lv by blast
    next
      show "lowerMapEntries enums entries sp2 = Some e'" using less.prems MapLiteralF by simp
    qed
    thus ?thesis using MapLiteralF by simp
  qed
qed

lemma lower_no_free_0cmp:
  "lower enums e = Some e' \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars e)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e e')"
  "lowerSetList enums es spx = Some es' \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_list es)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e es')"
  "lower_with_assigns enums fas base spx = Some fe' \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_fields fas)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e base)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e fe')"
  "lowerSeqList enums es spx = Some se' \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_list es)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e se')"
  "lowerMapEntries enums ents spx = Some me' \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_entries ents)
     \<Longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_e me')"
proof (induction enums e and enums es spx and enums fas base spx and enums es spx and enums ents spx
        arbitrary: e' and es' and fe' and se' and me'
        rule: lower_lowerSetList_lower_with_assigns_lowerSeqList_lowerMapEntries.induct)
  case (13 enums k bs body sp e')
  from "13.prems"(1) obtain body' where lb: "lower enums body = Some body'"
    by (auto split: option.splits)
  have key: "string_in_list (STR ''0cmp'') (qb_names bs)
               \<or> \<not> string_in_list (STR ''0cmp'') (free_vars_e body')"
  proof (cases "string_in_list (STR ''0cmp'') (qb_names bs)")
    case False
    hence "\<not> string_in_list (STR ''0cmp'') (free_vars body)" using "13.prems"(2) by simp
    with lb "13.IH" show ?thesis by blast
  qed simp
  have keyN: "string_in_list (STR ''0cmp'') (qb_names bs)
               \<or> \<not> string_in_list (STR ''0cmp'') (free_vars_e (UnNot body' sp))"
    using key by simp
  show ?case
  proof (cases k)
    case QAll
    with "13.prems"(1) lb have "lower_forall_bindings enums bs body' sp = Some e'" by simp
    thus ?thesis using lower_forall_bindings_fv key by blast
  next
    case QNo
    with "13.prems"(1) lb have "lower_forall_bindings enums bs (UnNot body' sp) sp = Some e'" by simp
    thus ?thesis using lower_forall_bindings_fv keyN by blast
  next
    case QSome
    with "13.prems"(1) lb obtain inner where
        bnd: "lower_forall_bindings enums bs (UnNot body' sp) sp = Some inner" and e'eq: "e' = UnNot inner sp"
      by (auto split: option.splits)
    have "\<not> string_in_list (STR ''0cmp'') (free_vars_e inner)"
      using lower_forall_bindings_fv[OF bnd] keyN by blast
    thus ?thesis using e'eq by simp
  next
    case QExists
    with "13.prems"(1) lb obtain inner where
        bnd: "lower_forall_bindings enums bs (UnNot body' sp) sp = Some inner" and e'eq: "e' = UnNot inner sp"
      by (auto split: option.splits)
    have "\<not> string_in_list (STR ''0cmp'') (free_vars_e inner)"
      using lower_forall_bindings_fv[OF bnd] keyN by blast
    thus ?thesis using e'eq by simp
  qed
next
  case (14 enums op e sp e')
  then show ?case by (cases op) (auto split: option.splits expr_full.splits)
next
  case (15 enums op l r sp e')
  have "set (free_vars_e e') \<subseteq> set (free_vars (BinaryOpF op l r sp))" using lower_fv_le[OF "15.prems"(1)] .
  thus ?case using "15.prems"(2) by (auto simp: string_in_list_set)
next
  case (8 enums callee args sp e')
  have "set (free_vars_e e') \<subseteq> set (free_vars (CallF callee args sp))"
    using lower_fv_le[OF "8.prems"(1)] .
  thus ?case using "8.prems"(2) by (auto simp: string_in_list_set)
qed (auto split: option.splits expr_full.splits if_splits simp: string_in_list_remove_name)

fun no_cmp_var :: "expr_full \<Rightarrow> bool"
and no_cmp_var_list :: "expr_full list \<Rightarrow> bool"
and no_cmp_var_fields :: "field_assign_full list \<Rightarrow> bool"
and no_cmp_var_entries :: "map_entry_full list \<Rightarrow> bool"
and no_cmp_var_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "no_cmp_var (IdentifierF n _)           = (n \<noteq> STR ''0cmp'')"
| "no_cmp_var (BinaryOpF _ l r _)         = (no_cmp_var l \<and> no_cmp_var r)"
| "no_cmp_var (UnaryOpF _ e _)            = no_cmp_var e"
| "no_cmp_var (FieldAccessF b _ _)        = no_cmp_var b"
| "no_cmp_var (EnumAccessF b _ _)         = no_cmp_var b"
| "no_cmp_var (IndexF b i _)              = (no_cmp_var b \<and> no_cmp_var i)"
| "no_cmp_var (CallF c args _)            = (no_cmp_var c \<and> no_cmp_var_list args)"
| "no_cmp_var (PrimeF e _)                = no_cmp_var e"
| "no_cmp_var (PreF e _)                  = no_cmp_var e"
| "no_cmp_var (WithF b upds _)            = (no_cmp_var b \<and> no_cmp_var_fields upds)"
| "no_cmp_var (IfF c t e _)               = (no_cmp_var c \<and> no_cmp_var t \<and> no_cmp_var e)"
| "no_cmp_var (LetF v val body _)         = (v \<noteq> STR ''0cmp'' \<and> no_cmp_var val \<and> no_cmp_var body)"
| "no_cmp_var (LambdaF p b _)             = (p \<noteq> STR ''0cmp'' \<and> no_cmp_var b)"
| "no_cmp_var (ConstructorF _ fs _)       = no_cmp_var_fields fs"
| "no_cmp_var (SetLiteralF xs _)          = no_cmp_var_list xs"
| "no_cmp_var (MapLiteralF es _)          = no_cmp_var_entries es"
| "no_cmp_var (SetComprehensionF v d p _) = (v \<noteq> STR ''0cmp'' \<and> no_cmp_var d \<and> no_cmp_var p)"
| "no_cmp_var (SeqLiteralF xs _)          = no_cmp_var_list xs"
| "no_cmp_var (MatchesF x _ _)            = no_cmp_var x"
| "no_cmp_var (SomeWrapF x _)             = no_cmp_var x"
| "no_cmp_var (TheF v d b _)              = (v \<noteq> STR ''0cmp'' \<and> no_cmp_var d \<and> no_cmp_var b)"
| "no_cmp_var (QuantifierF _ bs body _)   =
     (\<not> string_in_list (STR ''0cmp'') (qb_names bs) \<and> no_cmp_var_bindings bs \<and> no_cmp_var body)"
| "no_cmp_var (IntLitF _ _)               = True"
| "no_cmp_var (FloatLitF _ _)             = True"
| "no_cmp_var (StringLitF _ _)            = True"
| "no_cmp_var (BoolLitF _ _)              = True"
| "no_cmp_var (NoneLitF _)                = True"
| "no_cmp_var_list []                     = True"
| "no_cmp_var_list (x # xs)               = (no_cmp_var x \<and> no_cmp_var_list xs)"
| "no_cmp_var_fields []                   = True"
| "no_cmp_var_fields (FieldAssignFull _ v _ # fs) = (no_cmp_var v \<and> no_cmp_var_fields fs)"
| "no_cmp_var_entries []                  = True"
| "no_cmp_var_entries (MapEntryFull k v _ # es)   = (no_cmp_var k \<and> no_cmp_var v \<and> no_cmp_var_entries es)"
| "no_cmp_var_bindings []                 = True"
| "no_cmp_var_bindings (QuantifierBindingFull _ d _ _ # bs) = (no_cmp_var d \<and> no_cmp_var_bindings bs)"

lemma no_cmp_var_free_vars:
  "no_cmp_var e \<longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars e)"
  "no_cmp_var_list es \<longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_list es)"
  "no_cmp_var_fields fas \<longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_fields fas)"
  "no_cmp_var_entries ents \<longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_entries ents)"
  "no_cmp_var_bindings bs \<longrightarrow> \<not> string_in_list (STR ''0cmp'') (free_vars_bindings bs)"
  by (induction e and es and fas and ents and bs
      rule: no_cmp_var_no_cmp_var_list_no_cmp_var_fields_no_cmp_var_entries_no_cmp_var_bindings.induct)
     (auto simp: string_in_list_remove_name string_in_list_remove_names)

lemma contains_value_set: "contains_value xs v = (v \<in> set xs)"
  by (induction xs) auto

lemma eval_forall_member_rel:
  assumes "state_relation_domain st rel = Some d2"
  shows "eval_forall_rel s st env var dvs (Member (Ident var None) rel sp)
           = Some (VBool (list_all (\<lambda>v. contains_value d2 v) dvs))"
proof (induction dvs)
  case Nil
  show ?case by simp
next
  case (Cons v rest)
  have ev: "eval s st ((var, v) # env) (Member (Ident var None) rel sp)
              = Some (VBool (contains_value d2 v))"
    using assms by (simp add: env_lookup_def)
  show ?case using ev Cons.IH by simp
qed

lemma eval_BoolBin_AndOp:
  assumes "eval s st env a = Some (VBool p)" and "eval s st env b = Some (VBool q)"
  shows "eval s st env (BoolBin AndOp a b sp) = Some (VBool (p \<and> q))"
  using assms by simp

lemma lower_dom_eq_meaning:
  assumes "state_relation_domain st xrel = Some dx"
      and "state_relation_domain st yrel = Some dy"
  shows "eval s st env (lower_dom_eq xrel yrel sp) = Some (VBool (set dx = set dy))"
proof -
  have dir1: "eval s st env
                (ForallRel (STR ''0cmp'') xrel (Member (Ident (STR ''0cmp'') None) yrel sp) sp)
              = Some (VBool (set dx \<subseteq> set dy))"
  proof -
    have "eval s st env
            (ForallRel (STR ''0cmp'') xrel (Member (Ident (STR ''0cmp'') None) yrel sp) sp)
          = eval_forall_rel s st env (STR ''0cmp'') dx (Member (Ident (STR ''0cmp'') None) yrel sp)"
      by (rule eval_ForallRel_Some[OF assms(1)])
    also have "\<dots> = Some (VBool (list_all (\<lambda>v. contains_value dy v) dx))"
      by (rule eval_forall_member_rel[OF assms(2)])
    also have "\<dots> = Some (VBool (set dx \<subseteq> set dy))"
      by (simp add: list_all_iff contains_value_set subset_eq)
    finally show ?thesis .
  qed
  have dir2: "eval s st env
                (ForallRel (STR ''0cmp'') yrel (Member (Ident (STR ''0cmp'') None) xrel sp) sp)
              = Some (VBool (set dy \<subseteq> set dx))"
  proof -
    have "eval s st env
            (ForallRel (STR ''0cmp'') yrel (Member (Ident (STR ''0cmp'') None) xrel sp) sp)
          = eval_forall_rel s st env (STR ''0cmp'') dy (Member (Ident (STR ''0cmp'') None) xrel sp)"
      by (rule eval_ForallRel_Some[OF assms(2)])
    also have "\<dots> = Some (VBool (list_all (\<lambda>v. contains_value dx v) dy))"
      by (rule eval_forall_member_rel[OF assms(1)])
    also have "\<dots> = Some (VBool (set dy \<subseteq> set dx))"
      by (simp add: list_all_iff contains_value_set subset_eq)
    finally show ?thesis .
  qed
  have "eval s st env (lower_dom_eq xrel yrel sp)
          = Some (VBool ((set dx \<subseteq> set dy) \<and> (set dy \<subseteq> set dx)))"
    unfolding lower_dom_eq_def by (rule eval_BoolBin_AndOp[OF dir1 dir2])
  thus ?thesis by (simp add: set_eq_subset)
qed

lemma comp_dir1:
  assumes ethe: "eval_the_rel s st env var dvs p' = Some ms"
      and dropp: "\<And>d. eval s st ((var, d) # (STR ''0cmp'', VSet xs) # env) p'
                       = eval s st ((var, d) # env) p'"
      and vne: "var \<noteq> STR ''0cmp''"
  shows "eval_forall_rel s st ((STR ''0cmp'', VSet xs) # env) var dvs
            (BoolBin ImpliesOp p' (SetMember (Ident var None) (Ident (STR ''0cmp'') None) sp) sp)
           = Some (VBool (set ms \<subseteq> set xs))"
  using ethe
proof (induction dvs arbitrary: ms)
  case Nil
  thus ?case by simp
next
  case (Cons w rest)
  from Cons.prems obtain b matches where
      wb: "eval s st ((var, w) # env) p' = Some (VBool b)"
      and mr: "eval_the_rel s st env var rest p' = Some matches"
      and ms_eq: "ms = (if b then w # matches else matches)"
    by (auto split: option.splits ir_value.splits)
  have ev: "eval s st ((var, w) # (STR ''0cmp'', VSet xs) # env)
              (BoolBin ImpliesOp p' (SetMember (Ident var None) (Ident (STR ''0cmp'') None) sp) sp)
            = Some (VBool (\<not> b \<or> w \<in> set xs))"
    using wb dropp[of w] vne by (simp add: env_lookup_def contains_value_set)
  have IH: "eval_forall_rel s st ((STR ''0cmp'', VSet xs) # env) var rest
              (BoolBin ImpliesOp p' (SetMember (Ident var None) (Ident (STR ''0cmp'') None) sp) sp)
            = Some (VBool (set matches \<subseteq> set xs))"
    using Cons.IH[OF mr] .
  show ?case using ev IH ms_eq by auto
qed

lemma comp_dir2:
  assumes ethe: "eval_the_rel s st env var dvs p' = Some ms"
      and dropp: "\<And>d. eval s st ((var, d) # (STR ''0cmp'', VSet xsv) # env) p'
                       = eval s st ((var, d) # env) p'"
      and vne: "var \<noteq> STR ''0cmp''"
      and srd: "state_relation_domain st dnm = Some dvs"
      and la: "list_all (\<lambda>x. contains_value dvs x) ys"
  shows "eval_forall_rel s st ((STR ''0cmp'', VSet xsv) # env) var ys
            (BoolBin AndOp (Member (Ident var None) dnm sp) p' sp)
           = Some (VBool (set ys \<subseteq> set ms))"
  using la
proof (induction ys)
  case Nil
  thus ?case by simp
next
  case (Cons x rest)
  from Cons.prems have xdvs: "x \<in> set dvs"
      and larest: "list_all (\<lambda>x. contains_value dvs x) rest"
    by (auto simp: contains_value_set)
  obtain b where bx: "eval s st ((var, x) # env) p' = Some (VBool b)"
    using eval_the_rel_defined[OF ethe xdvs] by blast
  have xms: "(x \<in> set ms) = b"
    using eval_the_rel_mem[OF ethe, of x] xdvs bx by auto
  have ev: "eval s st ((var, x) # (STR ''0cmp'', VSet xsv) # env)
              (BoolBin AndOp (Member (Ident var None) dnm sp) p' sp) = Some (VBool b)"
    using bx dropp[of x] srd xdvs by (simp add: env_lookup_def contains_value_set)
  have IH: "eval_forall_rel s st ((STR ''0cmp'', VSet xsv) # env) var rest
              (BoolBin AndOp (Member (Ident var None) dnm sp) p' sp)
            = Some (VBool (set rest \<subseteq> set ms))"
    using Cons.IH[OF larest] .
  show ?case using ev IH xms by auto
qed

lemma comp_assembly:
  assumes el': "eval s st env l' = Some (VSet xs)"
      and ethe: "eval_the_rel s st env var dvs p' = Some ms"
      and srd: "state_relation_domain st dnm = Some dvs"
      and la: "list_all (\<lambda>x. contains_value dvs x) xs"
      and vne: "var \<noteq> STR ''0cmp''"
      and fp: "\<not> string_in_list (STR ''0cmp'') (free_vars_e p')"
      and dne: "\<not> string_in_list dnm enums"
  shows "eval s st env (lower_set_comp_eq enums var dnm l' p' sp) = Some (VBool (set xs = set ms))"
proof -
  have dropp: "\<And>d. eval s st ((var, d) # (STR ''0cmp'', VSet xs) # env) p'
                    = eval s st ((var, d) # env) p'"
  proof -
    fix d
    show "eval s st ((var, d) # (STR ''0cmp'', VSet xs) # env) p'
            = eval s st ((var, d) # env) p'"
      using eval_ins_0cmp[OF fp, where pre = "[(var, d)]" and va = "VSet xs"] by simp
  qed
  have d1: "eval s st ((STR ''0cmp'', VSet xs) # env)
              (ForallRel var dnm
                 (BoolBin ImpliesOp p'
                    (SetMember (Ident var None) (Ident (STR ''0cmp'') None) sp) sp) sp)
            = Some (VBool (set ms \<subseteq> set xs))"
    using comp_dir1[OF ethe dropp vne] srd by simp
  have d2: "eval s st ((STR ''0cmp'', VSet xs) # env)
              (ForallSet var (Ident (STR ''0cmp'') None)
                 (BoolBin AndOp (Member (Ident var None) dnm sp) p' sp) sp)
            = Some (VBool (set xs \<subseteq> set ms))"
    using comp_dir2[OF ethe dropp vne srd la] by (simp add: env_lookup_def)
  show ?thesis
    using el' d1 d2 dne by (auto simp: Let_def env_lookup_def)
qed

lemma lower_preserves_eval_full:
    "eval_full fs ps fuel s st env e = Some v \<Longrightarrow> lower enums e = Some e'
       \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var e \<Longrightarrow> builtins_reserved fs ps \<Longrightarrow> eval s st env e' = Some v"
    "eval_full_list fs ps fuel s st env es = Some vs \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_list es \<Longrightarrow>
       builtins_reserved fs ps \<Longrightarrow>
       (\<forall>sp e'. lowerSeqList enums es sp = Some e' \<longrightarrow> eval s st env e' = Some (VSeq vs)) \<and>
       (\<forall>sp e'. lowerSetList enums es sp = Some e' \<longrightarrow>
          eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])))"
    "eval_full_entries fs ps fuel s st env ents = Some mps \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_entries ents \<Longrightarrow>
       builtins_reserved fs ps \<Longrightarrow>
       (\<forall>sp e'. lowerMapEntries enums ents sp = Some e' \<longrightarrow> eval s st env e' = Some (VMap mps))"
    "eval_full_fields fs ps fuel s st env fas = Some fvs \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_fields fas \<Longrightarrow>
       builtins_reserved fs ps \<Longrightarrow>
       (\<forall>b sp e' bv. eval s st env b = Some bv \<longrightarrow> lower_with_assigns enums fas b sp = Some e'
          \<longrightarrow> eval s st env e' = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs))"
    "eval_full_the fs ps fuel s st env var dmv body = Some tms \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var body \<Longrightarrow>
       builtins_reserved fs ps \<Longrightarrow>
       (\<forall>body'. lower enums body = Some body' \<longrightarrow> eval_the_rel s st env var dmv body' = Some tms)"
    "eval_full_forall fs ps fuel s st env var dmv body = Some fr \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var body \<Longrightarrow>
       builtins_reserved fs ps \<Longrightarrow>
       (\<forall>body'. lower enums body = Some body' \<longrightarrow> eval_forall_rel s st env var dmv body' = Some fr)"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body
        and fs ps fuel s st env var dmv body
        arbitrary: v e' and vs and mps and fvs and tms and fr
        rule: eval_full_eval_full_list_eval_full_entries_eval_full_fields_eval_full_the_eval_full_forall.induct)
  case (6 fs ps fuel s st env bop l r sp v e')
  note IHl = "6.IH"(1) and IHr = "6.IH"(2)
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    hence de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some by simp
    from dom_eq_domains_SomeD[OF de] have f0: "bop = BEq"
        and "\<exists>rx. dom_arg l = Some rx \<and> state_relation_domain st rx = Some dx"
        and "\<exists>ry. dom_arg r = Some ry \<and> state_relation_domain st ry = Some dy" by auto
    then obtain rx ry where f2: "dom_arg l = Some rx" and f2d: "state_relation_domain st rx = Some dx"
        and f3: "dom_arg r = Some ry" and f3d: "state_relation_domain st ry = Some dy" by auto
    have veq: "v = VBool (set dx = set dy)"
      using "6.prems"(1)[unfolded eval_full_dom_eq[OF de]] by simp
    have e'eq: "e' = lower_dom_eq rx ry sp"
      using "6.prems"(2)[unfolded f0 lower_BEq_dom[OF f2 f3]] by simp
    show ?thesis using lower_dom_eq_meaning[OF f2d f3d] e'eq veq by simp
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
    case None
    show ?thesis
    proof (cases bop)
      case BAnd
    from "6.prems" BAnd obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BAnd obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin AndOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BAnd efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BOr
    from "6.prems" BOr obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BOr obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin OrOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BOr efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BImplies
    from "6.prems" BImplies obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BImplies obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin ImpliesOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BImplies efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BIff
    from "6.prems" BIff obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BIff obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin IffOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BIff efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BEq
    from "6.prems" deN BEq None obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    have rnc: "\<nexists>var dnm s2 p s3. r = SetComprehensionF var (IdentifierF dnm s2) p s3"
      using efr by (cases r) auto
    have lcdom: "lookup_callee fs ps (STR ''dom'') = None"
      using "6.prems"(5) by (simp add: builtins_reserved_def)
    have dnone: "dom_arg l = None \<or> dom_arg r = None"
    proof (rule ccontr)
      assume "\<not> (dom_arg l = None \<or> dom_arg r = None)"
      then obtain rx where "dom_arg l = Some rx" by auto
      then obtain a b c where "l = CallF (IdentifierF (STR ''dom'') a) [IdentifierF rx b] c"
        using dom_arg_SomeD by blast
      hence "eval_full fs ps fuel s st env l = None" using eval_full_dom_CallF[OF lcdom] by simp
      thus False using efl by simp
    qed
    from "6.prems"(2)[unfolded BEq lower_BEq_noncomp[OF rnc dnone]] obtain l' r'
        where ll: "lower enums l = Some l'" and lr: "lower enums r = Some r'"
          and e'eq: "e' = Cmp EqOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BEq None deN efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BNeq
    from "6.prems" BNeq obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BNeq obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp NeqOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BNeq efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BLt
    from "6.prems" BLt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BLt obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp LtOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BLt efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BGt
    from "6.prems" BGt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BGt obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp GtOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BGt efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BLe
    from "6.prems" BLe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BLe obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp LeOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BLe efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BGe
    from "6.prems" BGe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BGe obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp GeOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BGe efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BAdd
    from "6.prems" BAdd obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BAdd obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith AddOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BAdd efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BSub
    from "6.prems" BSub obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BSub obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith SubOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BSub efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BMul
    from "6.prems" BMul obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BMul obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith MulOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BMul efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BDiv
    from "6.prems" BDiv obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BDiv obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith DivOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BDiv efl efr IHl[OF deN None efl ll] IHr[OF deN None efr lr] by simp
  next
    case BUnion
    with "6.prems" show ?thesis by simp
  next
    case BIntersect
    with "6.prems" show ?thesis by simp
  next
    case BDiff
    with "6.prems" show ?thesis by simp
  next
    case BSubset
    with "6.prems" show ?thesis by simp
  next
    case BIn
    with "6.prems" show ?thesis by simp
  next
    case BNotIn
    with "6.prems" show ?thesis by simp
    qed
  next
    case (Some t)
    then obtain var dnm pred where bc: "beq_comp bop r = Some (var, dnm, pred)"
      by (cases t) auto
    have bopeq: "bop = BEq" using bc by (cases bop) auto
    from bc bopeq obtain s2 s3 where req: "r = SetComprehensionF var (IdentifierF dnm s2) pred s3"
      by (cases r) (auto split: expr_full.splits)
    from "6.prems"(1) deN bc obtain dvs ms xs where
        enr: "schema_lookup_enum s dnm = None"
        and srd: "state_relation_domain st dnm = Some dvs"
        and etm: "eval_full_the fs ps fuel s st env var dvs pred = Some ms"
        and efl: "eval_full fs ps fuel s st env l = Some (VSet xs)"
        and la: "list_all (\<lambda>x. contains_value dvs x) xs"
        and veq: "v = VBool (set xs = set ms)"
      by (auto split: if_splits option.splits ir_value.splits)
    from "6.prems"(2) bopeq req obtain l' p' where
        ll: "lower enums l = Some l'"
        and lp: "lower enums pred = Some p'"
        and e'eq: "e' = lower_set_comp_eq enums var dnm l' p' sp"
      by (auto split: option.splits)
    have ncl: "no_cmp_var l" using "6.prems"(4) bopeq by simp
    have ncp: "no_cmp_var pred" using "6.prems"(4) req by simp
    have vne: "var \<noteq> STR ''0cmp''" using "6.prems"(4) req by simp
    have dne: "\<not> string_in_list dnm enums" by (rule sil_none[OF "6.prems"(3) enr])
    have fpred: "\<not> string_in_list (STR ''0cmp'') (free_vars pred)"
      using no_cmp_var_free_vars(1)[of pred] ncp by simp
    have fp: "\<not> string_in_list (STR ''0cmp'') (free_vars_e p')"
      by (rule lower_no_free_0cmp(1)[OF lp fpred])
    have enr': "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
    have etr: "eval_the_rel s st env var dvs p' = Some ms"
      using "6.IH"(3)[OF deN bc refl refl enr' srd etm "6.prems"(3) ncp "6.prems"(5)] lp by blast
    have el': "eval s st env l' = Some (VSet xs)"
      using "6.IH"(4)[OF deN bc refl refl enr' srd etm efl ll "6.prems"(3) ncl "6.prems"(5)] .
    show ?thesis
      using e'eq veq comp_assembly[OF el' etr srd la vne fp dne] by simp
  qed
  qed
next
  case (7 fs ps fuel s st env uop e sp v e')
  show ?case
  proof (cases uop)
    case UNot
    from "7.prems" UNot obtain b where ef: "eval_full fs ps fuel s st env e = Some (VBool b)"
        and veq: "v = VBool (\<not> b)"
      by (auto split: option.splits ir_value.splits)
    from "7.prems" UNot obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNot e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some (VBool b)" using "7.IH"[OF ef le "7.prems"(3)] "7.prems"(4) "7.prems"(5) by simp
    then show ?thesis using e'eq veq by simp
  next
    case UNegate
    from "7.prems" UNegate obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      by (auto split: option.splits)
    from "7.prems" UNegate obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNeg e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some v0" using "7.IH"[OF ef le "7.prems"(3)] "7.prems"(4) "7.prems"(5) by simp
    then show ?thesis using e'eq ef "7.prems" UNegate by (auto split: ir_value.splits)
  next
    case UCardinality
    with "7.prems" show ?thesis by (auto split: option.splits)
  next
    case UPower
    with "7.prems" show ?thesis by (auto split: option.splits)
  qed
next
  case (8 fs ps fuel s st env c a b sp v e')
  from "8.prems" obtain c' a' b' where lc: "lower enums c = Some c'"
      and la: "lower enums a = Some a'" and lb: "lower enums b = Some b'"
      and e'eq: "e' = Ite c' a' b' sp"
    by (auto split: option.splits)
  from "8.prems" obtain bb where ec: "eval_full fs ps fuel s st env c = Some (VBool bb)"
    by (auto split: option.splits ir_value.splits)
  have ec': "eval s st env c' = Some (VBool bb)" using "8.IH"(1)[OF ec lc "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
  show ?case
  proof (cases bb)
    case True
    with ec have cT: "eval_full fs ps fuel s st env c = Some (VBool True)" by simp
    have ea: "eval_full fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    have "eval s st env a' = Some v" using "8.IH"(2)[OF cT refl refl ea la "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
    then show ?thesis using e'eq ec' True by simp
  next
    case False
    with ec have cF: "eval_full fs ps fuel s st env c = Some (VBool False)" by simp
    have eb: "eval_full fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    have "eval s st env b' = Some v" using "8.IH"(3)[OF cF refl refl eb lb "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
    then show ?thesis using e'eq ec' False by simp
  qed
next
  case (9 fs ps fuel s st env x ve body sp v e')
  from "9.prems" obtain va where eve: "eval_full fs ps fuel s st env ve = Some va"
      and ebody: "eval_full fs ps fuel s st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  from "9.prems" obtain v' b' where lve: "lower enums ve = Some v'"
      and lbody: "lower enums body = Some b'" and e'eq: "e' = LetIn x v' b' sp"
    by (auto split: option.splits)
  have ev': "eval s st env v' = Some va" using "9.IH"(1)[OF eve lve "9.prems"(3)] "9.prems"(4) "9.prems"(5) by simp
  have eb': "eval s st ((x, va) # env) b' = Some v" using "9.IH"(2)[OF eve ebody lbody "9.prems"(3)] "9.prems"(4) "9.prems"(5) by simp
  show ?case using e'eq ev' eb' by simp
next
  case (10 fs ps fuel s st env base f sp v e')
  from "10.prems" obtain v0 where ef: "eval_full fs ps fuel s st env base = Some v0"
      and vfl: "value_field_lookup st v0 f = Some v"
    by (auto split: option.splits)
  from "10.prems" obtain b' where lb: "lower enums base = Some b'"
      and e'eq: "e' = FieldAccess b' f sp"
    by (auto split: option.splits)
  have "eval s st env b' = Some v0" using "10.IH"[OF ef lb "10.prems"(3)] "10.prems"(4) "10.prems"(5) by simp
  then show ?case using e'eq vfl by simp
next
  case (11 fs ps fuel s st env e sp v e')
  from "11.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "11.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Prime e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "11.IH"[OF ef le "11.prems"(3)] "11.prems"(4) "11.prems"(5) by simp
  then show ?case using e'eq by simp
next
  case (12 fs ps fuel s st env e sp v e')
  from "12.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "12.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Pre e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "12.IH"[OF ef le "12.prems"(3)] "12.prems"(4) "12.prems"(5) by simp
  then show ?case using e'eq by simp
next
  case (13 fs ps fuel s st env e sp v e')
  from "13.prems" obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      and veq: "v = VSome v0"
    by (auto split: option.splits)
  from "13.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = SomeE e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v0" using "13.IH"[OF ef le "13.prems"(3)] "13.prems"(4) "13.prems"(5) by simp
  then show ?case using e'eq veq by simp
next
  case (14 fs ps fuel s st env e pat sp v e')
  from "14.prems" obtain str where ef: "eval_full fs ps fuel s st env e = Some (VStr str)"
      and veq: "v = VBool (string_matches str pat)"
    by (auto split: option.splits ir_value.splits)
  from "14.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Matches e'' pat sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some (VStr str)" using "14.IH"[OF ef le "14.prems"(3)] "14.prems"(4) "14.prems"(5) by simp
  then show ?case using e'eq veq by simp
next
  case (15 fs ps fuel s st env callee args sp v e')
  obtain fuel' where fuel: "fuel = Suc fuel'" using "15.prems"(1) by (cases fuel) auto
  from "15.prems"(2) obtain nm sp1 arg arg' where
      ceq: "callee = IdentifierF nm sp1" and aeq: "args = [arg]" and bip: "is_builtin_pred nm"
      and la: "lower enums arg = Some arg'" and e'eq: "e' = UStrPred nm arg' sp"
    by (cases callee; cases args) (auto split: if_splits list.splits option.splits)
  have lc_none: "lookup_callee fs ps nm = None"
    using "15.prems"(5) bip by (simp add: builtins_reserved_def)
  from "15.prems"(1) fuel ceq aeq lc_none bip obtain str where
      ea: "eval_full fs ps fuel s st env arg = Some (VStr str)"
      and veq: "v = VBool (str_predicate nm str)"
    by (auto split: option.splits ir_value.splits if_splits)
  have ncarg: "no_cmp_var arg" using "15.prems"(4) ceq aeq by simp
  have ev: "eval s st env arg' = Some (VStr str)"
    using "15.IH" fuel ceq aeq lc_none bip ea la "15.prems"(3) ncarg "15.prems"(5)
    by (auto split: if_splits)
  show ?case using e'eq veq ev by simp
next
  case (17 fs ps fuel s st env es sp v e')
  from "17.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSeq vs"
    by (auto split: option.splits)
  from "17.prems"(2) have ls: "lowerSeqList enums es sp = Some e'" by simp
  show ?case using "17.IH"[OF efl "17.prems"(3)] ls veq "17.prems"(4) "17.prems"(5) by fastforce
next
  case (18 fs ps fuel s st env es sp v e')
  from "18.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])"
    by (auto split: option.splits)
  from "18.prems"(2) have ls: "lowerSetList enums es sp = Some e'" by simp
  show ?case using "18.IH"[OF efl "18.prems"(3)] ls veq "18.prems"(4) "18.prems"(5) by fastforce
next
  case (19 fs ps fuel s st env entries sp v e')
  from "19.prems"(1) obtain mps where e: "eval_full_entries fs ps fuel s st env entries = Some mps"
      and veq: "v = VMap mps"
    by (auto split: option.splits)
  from "19.prems"(2) have ls: "lowerMapEntries enums entries sp = Some e'" by simp
  show ?case using "19.IH"[OF e "19.prems"(3)] ls veq "19.prems"(4) "19.prems"(5) by fastforce
next
  case (22 fs ps fuel s st env base mem sp v e')
  show ?case
  proof (cases base)
    case (IdentifierF en sp')
    have e': "e' = EnumAccess en mem sp" using "22.prems"(2) IdentifierF by simp
    have "eval s st env (EnumAccess en mem sp) = Some v" using "22.prems"(1) IdentifierF by simp
    thus ?thesis using e' by simp
  qed (use "22.prems" in simp_all)
next
  case (23 fs ps fuel s st env base key sp v e')
  from "23.prems"(1) obtain rel kv where pk: "peelRelationRefFull base = Some rel"
      and ek: "eval_full fs ps fuel s st env key = Some kv"
      and slk: "state_lookup_key st rel kv = Some v"
    by (auto split: option.splits)
  from "23.prems"(2) obtain base' key' where lb: "lower enums base = Some base'"
      and lk: "lower enums key = Some key'" and e'eq: "e' = IndexRel base' key' sp"
    by (auto split: option.splits)
  have pr: "peel_relation_ref base' = Some rel" using peelRelationRefFull_lower[OF pk lb] .
  have "eval s st env key' = Some kv" using "23.IH"[OF ek lk "23.prems"(3)] "23.prems"(4) "23.prems"(5) by simp
  then show ?case using e'eq pr slk by simp
next
  case (27 fs ps fuel s st env vs)
  then show ?case by (auto split: option.splits)
next
  case (28 fs ps fuel s st env e es vs)
  from "28.prems" obtain v0 vs0 where ev0: "eval_full fs ps fuel s st env e = Some v0"
      and evs0: "eval_full_list fs ps fuel s st env es = Some vs0" and vseq: "vs = v0 # vs0"
    by (auto split: option.splits)
  have nclE: "no_cmp_var_list es" using "28.prems"(3) "28.prems"(4) by simp
  show ?case
  proof (intro conjI allI impI)
    fix sp e'
    assume lse: "lowerSeqList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSeqList enums es sp = Some s0'" and e'eq: "e' = SeqCons e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "28.IH"(1)[OF ev0 le0 "28.prems"(2)] "28.prems"(3) "28.prems"(4) by simp
    moreover have "eval s st env s0' = Some (VSeq vs0)"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) nclE "28.prems"(4)] ls0 by blast
    ultimately show "eval s st env e' = Some (VSeq vs)" using e'eq vseq by simp
  next
    fix sp e'
    assume lse: "lowerSetList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSetList enums es sp = Some s0'" and e'eq: "e' = SetInsert e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "28.IH"(1)[OF ev0 le0 "28.prems"(2)] "28.prems"(3) "28.prems"(4) by simp
    moreover have "eval s st env s0' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs0 []))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) nclE "28.prems"(4)] ls0 by blast
    ultimately show "eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))"
      using e'eq vseq by simp
  qed
next
  case (29 fs ps fuel s st env mps)
  then show ?case by (auto split: option.splits)
next
  case (30 fs ps fuel s st env k v msp rest mps)
  from "30.prems" obtain kv vv mps0 where ek: "eval_full fs ps fuel s st env k = Some kv"
      and ev: "eval_full fs ps fuel s st env v = Some vv"
      and er: "eval_full_entries fs ps fuel s st env rest = Some mps0" and mpeq: "mps = (kv, vv) # mps0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix sp e'
    assume lme: "lowerMapEntries enums (MapEntryFull k v msp # rest) sp = Some e'"
    from lme obtain k' v' m' where lk: "lower enums k = Some k'" and lv: "lower enums v = Some v'"
        and lm: "lowerMapEntries enums rest sp = Some m'" and e'eq: "e' = MapCons k' v' m' sp"
      by (auto split: option.splits)
    have "eval s st env k' = Some kv" using "30.IH"(1)[OF ek lk "30.prems"(2)] "30.prems"(3) "30.prems"(4) by simp
    moreover have "eval s st env v' = Some vv" using "30.IH"(2)[OF ev lv "30.prems"(2)] "30.prems"(3) "30.prems"(4) by simp
    moreover have "eval s st env m' = Some (VMap mps0)"
      using "30.IH"(3)[OF er "30.prems"(2)] lm "30.prems"(3) "30.prems"(4) by fastforce
    ultimately show "eval s st env e' = Some (VMap mps)" using e'eq mpeq by simp
  qed
next
  case (20 fs ps fuel s st env name fas sp v e')
  from "20.prems"(1) obtain fvs where e: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  from "20.prems"(2) have lw: "lower_with_assigns enums fas (EntityBase name sp) sp = Some e'" by simp
  have eb: "eval s st env (EntityBase name sp) = Some (VEntity name (STR ''''))" by simp
  show ?case using "20.IH"[OF e "20.prems"(3)] eb lw veq "20.prems"(4) "20.prems"(5) by fastforce
next
  case (21 fs ps fuel s st env base fas sp v e')
  from "21.prems"(1) obtain bv fvs where eb: "eval_full fs ps fuel s st env base = Some bv"
      and ef: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  from "21.prems"(2) obtain base' where lb: "lower enums base = Some base'"
      and lw: "lower_with_assigns enums fas base' sp = Some e'"
    by (auto split: option.splits)
  have eb': "eval s st env base' = Some bv" using "21.IH"(1)[OF eb lb "21.prems"(3)] "21.prems"(4) "21.prems"(5) by simp
  show ?case using "21.IH"(2)[OF ef "21.prems"(3)] eb' lw veq "21.prems"(4) "21.prems"(5) by fastforce
next
  case (31 fs ps fuel s st env fvs)
  then show ?case by (auto split: option.splits)
next
  case (32 fs ps fuel s st env fld v fsp rest fvs)
  from "32.prems" obtain fv fvs0 where ev: "eval_full fs ps fuel s st env v = Some fv"
      and er: "eval_full_fields fs ps fuel s st env rest = Some fvs0" and fveq: "fvs = (fld, fv) # fvs0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix b sp e' bv
    assume eb: "eval s st env b = Some bv"
    assume lw: "lower_with_assigns enums (FieldAssignFull fld v fsp # rest) b sp = Some e'"
    from lw obtain v' where lv: "lower enums v = Some v'"
        and lwr: "lower_with_assigns enums rest (WithRec b fld v' sp) sp = Some e'"
      by (auto split: option.splits)
    have ev': "eval s st env v' = Some fv" using "32.IH"(1)[OF ev lv "32.prems"(2)] "32.prems"(3) "32.prems"(4) by simp
    have ebw: "eval s st env (WithRec b fld v' sp) = Some (VEntityWith bv fld fv)"
      using eb ev' by simp
    have "eval s st env e'
            = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntityWith bv fld fv) fvs0)"
      using "32.IH"(2)[OF er "32.prems"(2)] ebw lwr "32.prems"(3) "32.prems"(4) by fastforce
    then show "eval s st env e'
                 = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)"
      using fveq by simp
  qed
next
  case (24 fs ps fuel s st env var dm body sp v e')
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    from "24.prems"(2) IdentifierF obtain body' where lb: "lower enums body = Some body'"
        and e'eq: "e' = TheRel var rel body' sp"
      by (auto split: if_splits option.splits)
    from "24.prems"(1) IdentifierF obtain dmv x rest where srd: "state_relation_domain st rel = Some dmv"
        and eft: "eval_full_the fs ps fuel s st env var dmv body = Some (x # rest)"
        and uniq: "list_all (\<lambda>y. y = x) rest" and v_eq: "v = x"
      by (auto split: option.splits list.splits if_splits)
    have etr: "eval_the_rel s st env var dmv body' = Some (x # rest)"
      using "24.IH"[OF IdentifierF srd eft "24.prems"(3)] lb "24.prems"(4) "24.prems"(5) by fastforce
    show ?thesis using e'eq srd etr uniq v_eq by simp
  qed (use "24.prems"(2) in simp_all)
next
  case (25 fs ps fuel s st env k bs body sp v e')
  note wf = "25.prems"(3)
  show ?case
  proof (cases "quant_dom s st k bs")
    case None
    then show ?thesis using "25.prems"(1) by simp
  next
    case (Some vd)
    obtain var dmv where vdeq: "vd = (var, dmv)" by (cases vd) auto
    have qd: "quant_dom s st k bs = Some (var, dmv)" using Some vdeq by simp
    have eff: "eval_full_forall fs ps fuel s st env var dmv body = Some v"
      using "25.prems"(1) qd by simp
    from quant_dom_some_shape[OF qd] obtain dnm sp1 dty a where
        kq: "k = QAll"
        and bseq: "bs = [QuantifierBindingFull var (IdentifierF dnm sp1) dty a]"
        and dmrel: "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
                     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)"
      by blast
    obtain body' where lbody: "lower enums body = Some body'"
      using "25.prems"(2) kq bseq by (auto split: option.splits)
    have ih: "eval_forall_rel s st env var dmv body' = Some v"
      using "25.IH"[OF Some[unfolded vdeq] refl eff wf] lbody "25.prems"(4) "25.prems"(5) by fastforce
    show ?thesis
    proof (cases "schema_lookup_enum s dnm")
      case (Some d)
      note sd = this
      have si: "string_in_list dnm enums"
      proof (rule sil_enum)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = Some d" by (rule sd)
      qed
      have e'eq: "e' = ForallEnum var dnm body' sp"
        using "25.prems"(2) kq bseq lbody si by (auto split: option.splits)
      have dmv_eq: "dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
        using dmrel sd by (rule dmrel_enum)
      show ?thesis
        using e'eq sd dmv_eq ih
        by (simp add: eval_ForallEnum_Some eval_forall_enum_eq_rel)
    next
      case None
      note sn = this
      have nsi: "\<not> string_in_list dnm enums"
      proof (rule sil_none)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = None" by (rule sn)
      qed
      have e'eq: "e' = ForallRel var dnm body' sp"
        using "25.prems"(2) kq bseq lbody nsi by (auto split: option.splits)
      have sr: "state_relation_domain st dnm = Some dmv"
        using dmrel sn by (rule dmrel_rel)
      show ?thesis
        using e'eq sr ih
        by (simp add: eval_ForallRel_Some)
    qed
  qed
next
  case (33 fs ps fuel s st env var body tms)
  show ?case using "33.prems" by auto
next
  case (34 fs ps fuel s st env var v rest body tms)
  show ?case
  proof (intro allI impI)
    fix body'
    assume lb: "lower enums body = Some body'"
    show "eval_the_rel s st env var (v # rest) body' = Some tms"
    proof (cases "eval_full fs ps fuel s st ((var, v) # env) body")
      case None
      then show ?thesis using "34.prems" by simp
    next
      case (Some bv)
      show ?thesis
      proof (cases bv)
        case (VBool b)
        have evb: "eval_full fs ps fuel s st ((var, v) # env) body = Some (VBool b)"
          using Some VBool by simp
        obtain matches where mr: "eval_full_the fs ps fuel s st env var rest body = Some matches"
            and tms_eq: "tms = (if b then v # matches else matches)"
          using "34.prems" evb by (auto split: option.splits)
        have eb: "eval s st ((var, v) # env) body' = Some (VBool b)"
          using "34.IH"(1)[OF evb lb "34.prems"(2)] "34.prems"(3) "34.prems"(4) by simp
        have er: "eval_the_rel s st env var rest body' = Some matches"
          using "34.IH"(2)[OF Some VBool mr "34.prems"(2)] lb "34.prems"(3) "34.prems"(4) by fastforce
        show ?thesis using eb er tms_eq by simp
      qed (use "34.prems" Some in simp_all)
    qed
  qed
next
  case (35 fs ps fuel s st env var body fr)
  show ?case using "35.prems" by auto
next
  case (36 fs ps fuel s st env var v rest body fr)
  show ?case
  proof (intro allI impI)
    fix body'
    assume lb: "lower enums body = Some body'"
    show "eval_forall_rel s st env var (v # rest) body' = Some fr"
    proof (cases "eval_full fs ps fuel s st ((var, v) # env) body")
      case None
      then show ?thesis using "36.prems" by simp
    next
      case (Some bv)
      show ?thesis
      proof (cases bv)
        case (VBool b)
        have evb: "eval_full fs ps fuel s st ((var, v) # env) body = Some (VBool b)"
          using Some VBool by simp
        obtain acc where mr: "eval_full_forall fs ps fuel s st env var rest body = Some (VBool acc)"
            and fr_eq: "fr = VBool (b \<and> acc)"
          using "36.prems" evb by (auto split: option.splits ir_value.splits)
        have eb: "eval s st ((var, v) # env) body' = Some (VBool b)"
          using "36.IH"(1)[OF evb lb "36.prems"(2)] "36.prems"(3) "36.prems"(4) by simp
        have er: "eval_forall_rel s st env var rest body' = Some (VBool acc)"
          using "36.IH"(2)[OF Some VBool mr "36.prems"(2)] lb "36.prems"(3) "36.prems"(4) by fastforce
        show ?thesis using eb er fr_eq by simp
      qed (use "36.prems" Some in simp_all)
    qed
  qed
qed (auto split: option.splits ir_value.splits)

end
