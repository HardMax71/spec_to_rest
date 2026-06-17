theory Smt
  imports IR "HOL.Rat"
begin

datatype (plugins only: code size) smt_sort =
    SortBool
  | SortInt
  | SortReal
  | SortUninterp "String.literal"

datatype (plugins only: code size) smt_val =
    SBool bool
  | SInt int
  | SReal rat
  | SEnumElem "String.literal" "String.literal"
  | SEntityElem "String.literal" "String.literal"
  | SSet "smt_val list"
  | SEntityWith "smt_val" "String.literal" "smt_val"
  | SNone
  | SSome "smt_val"
  | SStr "String.literal"
  | SSeq "smt_val list"
  | SMap "(smt_val \<times> smt_val) list"

datatype (plugins only: code size) smt_term =
    BLit bool
  | ILit int
  | RLit rat
  | TVar "String.literal"
  | EnumElemConst "String.literal" "String.literal"
  | TNot "smt_term"
  | TAnd "smt_term" "smt_term"
  | TOr "smt_term" "smt_term"
  | TImplies "smt_term" "smt_term"
  | TEq "smt_term" "smt_term"
  | TLt "smt_term" "smt_term"
  | TNeg "smt_term"
  | TAdd "smt_term" "smt_term"
  | TSub "smt_term" "smt_term"
  | TMul "smt_term" "smt_term"
  | TDiv "smt_term" "smt_term"
  | TInDom "String.literal" "smt_term"
  | TCardRel "String.literal"
  | TCard "smt_term"
  | TLetIn "String.literal" "smt_term" "smt_term"
  | TForallEnum "String.literal" "String.literal" "smt_term"
  | TForallRel "String.literal" "String.literal" "smt_term"
  | TExistsRel "String.literal" "String.literal" "smt_term"
  | TTheRel "String.literal" "String.literal" "smt_term"
  | TEntityBase "String.literal"
  | TForallSet "String.literal" "smt_term" "smt_term"
  | TTheSet "String.literal" "smt_term" "smt_term"
  | TIndexRel "smt_term" "smt_term"
  | TFieldAccess "smt_term" "String.literal"
  | TSetEmpty
  | TSetInsert "smt_term" "smt_term"
  | TSetMember "smt_term" "smt_term"
  | TSetUnion "smt_term" "smt_term"
  | TSetIntersect "smt_term" "smt_term"
  | TSetDiff "smt_term" "smt_term"
  | TPrime "smt_term"
  | TPre "smt_term"
  | TWithRec "smt_term" "String.literal" "smt_term"
  | TIte "smt_term" "smt_term" "smt_term"
  | TNone
  | TSome "smt_term"
  | TStrLit "String.literal"
  | TMatches "smt_term" "String.literal"
  | TUStrPred "String.literal" "smt_term"
  | TUStrFunc "String.literal" "smt_term"
  | TUIntFunc "String.literal" "smt_term"
  | TStrLen "smt_term"
  | TUConst "String.literal"
  | TSeqEmpty
  | TSeqCons "smt_term" "smt_term"
  | TMapEmpty
  | TMapCons "smt_term" "smt_term" "smt_term"
  | TSum "smt_term" "String.literal"

text \<open>\<open>smt_var_list\<close>: every variable name occurring anywhere in a term,
  binders included. Over-approximates the free variables, so a name not in
  this list can neither be captured by nor capture any binder of the term.\<close>

fun smt_var_list :: "smt_term \<Rightarrow> String.literal list" where
  "smt_var_list (BLit _)              = []"
| "smt_var_list (ILit _)              = []"
| "smt_var_list (RLit _)              = []"
| "smt_var_list (TVar x)              = [x]"
| "smt_var_list (EnumElemConst _ _)   = []"
| "smt_var_list (TNot t)              = smt_var_list t"
| "smt_var_list (TAnd l r)            = smt_var_list l @ smt_var_list r"
| "smt_var_list (TOr l r)             = smt_var_list l @ smt_var_list r"
| "smt_var_list (TImplies l r)        = smt_var_list l @ smt_var_list r"
| "smt_var_list (TEq l r)             = smt_var_list l @ smt_var_list r"
| "smt_var_list (TLt l r)             = smt_var_list l @ smt_var_list r"
| "smt_var_list (TNeg t)              = smt_var_list t"
| "smt_var_list (TAdd l r)            = smt_var_list l @ smt_var_list r"
| "smt_var_list (TSub l r)            = smt_var_list l @ smt_var_list r"
| "smt_var_list (TMul l r)            = smt_var_list l @ smt_var_list r"
| "smt_var_list (TDiv l r)            = smt_var_list l @ smt_var_list r"
| "smt_var_list (TInDom _ t)          = smt_var_list t"
| "smt_var_list (TCardRel _)          = []"
| "smt_var_list (TCard t)             = smt_var_list t"
| "smt_var_list (TLetIn v a b)        = v # smt_var_list a @ smt_var_list b"
| "smt_var_list (TForallEnum v _ b)   = v # smt_var_list b"
| "smt_var_list (TForallRel v _ b)    = v # smt_var_list b"
| "smt_var_list (TExistsRel v _ b)    = v # smt_var_list b"
| "smt_var_list (TTheRel v _ b)       = v # smt_var_list b"
| "smt_var_list (TEntityBase _)       = []"
| "smt_var_list (TForallSet v d b)    = v # smt_var_list d @ smt_var_list b"
| "smt_var_list (TTheSet v d b)       = v # smt_var_list d @ smt_var_list b"
| "smt_var_list (TIndexRel b k)       = smt_var_list b @ smt_var_list k"
| "smt_var_list (TFieldAccess b _)    = smt_var_list b"
| "smt_var_list TSetEmpty             = []"
| "smt_var_list (TSetInsert e s)      = smt_var_list e @ smt_var_list s"
| "smt_var_list (TSetMember e s)      = smt_var_list e @ smt_var_list s"
| "smt_var_list (TSetUnion l r)       = smt_var_list l @ smt_var_list r"
| "smt_var_list (TSetIntersect l r)   = smt_var_list l @ smt_var_list r"
| "smt_var_list (TSetDiff l r)        = smt_var_list l @ smt_var_list r"
| "smt_var_list (TPrime t)            = smt_var_list t"
| "smt_var_list (TPre t)              = smt_var_list t"
| "smt_var_list (TWithRec b _ v)      = smt_var_list b @ smt_var_list v"
| "smt_var_list (TIte c a b)          = smt_var_list c @ smt_var_list a @ smt_var_list b"
| "smt_var_list TNone                 = []"
| "smt_var_list (TSome t)             = smt_var_list t"
| "smt_var_list (TStrLit _)           = []"
| "smt_var_list (TMatches t _)        = smt_var_list t"
| "smt_var_list (TUStrPred _ t)       = smt_var_list t"
| "smt_var_list (TUStrFunc _ t)       = smt_var_list t"
| "smt_var_list (TUIntFunc _ t)       = smt_var_list t"
| "smt_var_list (TStrLen t)           = smt_var_list t"
| "smt_var_list (TUConst _)           = []"
| "smt_var_list TSeqEmpty             = []"
| "smt_var_list (TSeqCons e r)        = smt_var_list e @ smt_var_list r"
| "smt_var_list TMapEmpty             = []"
| "smt_var_list (TMapCons k v r)      = smt_var_list k @ smt_var_list v @ smt_var_list r"
| "smt_var_list (TSum c _)            = smt_var_list c"

text \<open>\<open>fresh_var base avoid\<close>: the first of \<open>base\<close>, \<open>base_\<close>, \<open>base__\<close>, ...
  not in \<open>avoid\<close>. The candidates have strictly increasing lengths, so among
  the first \<open>length avoid + 1\<close> of them one is always free (pigeonhole);
  \<open>fresh_var_fresh\<close> below turns that into an unconditional guarantee. This
  is what lets the translator introduce binders without reserving any
  identifier in the source language.\<close>

fun fresh_in :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal" where
  "fresh_in 0 base avoid = base"
| "fresh_in (Suc n) base avoid =
     (if string_in_list base avoid
        then fresh_in n (base + STR ''_'') avoid
        else base)"

definition fresh_var :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal" where
  "fresh_var base avoid = fresh_in (Suc (length avoid)) base avoid"

lemma string_in_list_iff: "string_in_list y xs = (y \<in> set xs)"
  by (induction xs) auto

definition underscored :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal" where
  "underscored n base = ((\<lambda>s. s + STR ''_'') ^^ n) base"

lemma size_plus_literal:
  "size (s + t) = size s + size t" for s t :: String.literal
  including literal.lifting by transfer simp

lemma size_underscore_lit: "size (STR ''_'') = 1"
  including literal.lifting by transfer simp

lemma underscored_size:
  "size (underscored n base) = size base + n"
  by (induction n)
     (simp_all add: underscored_def size_plus_literal size_underscore_lit)

lemma underscored_inj: "inj (\<lambda>n. underscored n base)"
  by (rule injI) (metis underscored_size add_left_cancel)

lemma underscored_Suc_shift:
  "underscored (Suc n) base = underscored n (base + STR ''_'')"
  unfolding underscored_def by (induction n) simp_all

lemma fresh_in_finds:
  "\<exists>k<n. underscored k base \<notin> set avoid \<Longrightarrow> fresh_in n base avoid \<notin> set avoid"
proof (induction n arbitrary: base)
  case 0
  thus ?case by blast
next
  case (Suc n)
  show ?case
  proof (cases "string_in_list base avoid")
    case False
    thus ?thesis by (simp add: string_in_list_iff)
  next
    case True
    hence b_in: "base \<in> set avoid" by (simp add: string_in_list_iff)
    obtain k where kn: "k < Suc n" and kf: "underscored k base \<notin> set avoid"
      using Suc.prems by blast
    have "k \<noteq> 0" using kf b_in by (cases k) (auto simp: underscored_def)
    then obtain k' where keq: "k = Suc k'" by (cases k) auto
    have "underscored k' (base + STR ''_'') \<notin> set avoid"
      using kf keq underscored_Suc_shift by simp
    moreover have "k' < n" using kn keq by simp
    ultimately have "fresh_in n (base + STR ''_'') avoid \<notin> set avoid"
      using Suc.IH by blast
    thus ?thesis using True by simp
  qed
qed

lemma fresh_var_fresh:
  "fresh_var base avoid \<notin> set avoid"
proof -
  have inj: "inj_on (\<lambda>k. underscored k base) {..length avoid}"
    using underscored_inj inj_on_subset by blast
  have "\<not> (\<lambda>k. underscored k base) ` {..length avoid} \<subseteq> set avoid"
  proof
    assume "(\<lambda>k. underscored k base) ` {..length avoid} \<subseteq> set avoid"
    hence "card ((\<lambda>k. underscored k base) ` {..length avoid}) \<le> card (set avoid)"
      by (intro card_mono) auto
    hence "Suc (length avoid) \<le> card (set avoid)"
      by (simp add: card_image inj)
    thus False using card_length[of avoid] by simp
  qed
  then obtain k where "k \<le> length avoid" and "underscored k base \<notin> set avoid"
    by auto
  hence "\<exists>k<Suc (length avoid). underscored k base \<notin> set avoid"
    using le_imp_less_Suc by blast
  thus ?thesis unfolding fresh_var_def by (rule fresh_in_finds)
qed

record smt_model =
  sm_sort_members :: "(String.literal \<times> String.literal list) list"
  sm_const_vals   :: "(String.literal \<times> smt_val) list"
  sm_pred_domain  :: "(String.literal \<times> smt_val list) list"
  sm_pred_lookup  :: "(String.literal \<times> (smt_val \<times> smt_val) list) list"
  sm_pred_fields  :: "(String.literal \<times> (String.literal \<times> smt_val) list) list"

definition smt_model_empty :: smt_model where
  "smt_model_empty \<equiv>
     \<lparr> sm_sort_members = [], sm_const_vals = [], sm_pred_domain = [],
       sm_pred_lookup = [], sm_pred_fields = [] \<rparr>"

definition smt_model_lookup_const ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> smt_val option" where
  "smt_model_lookup_const m name \<equiv> map_of (sm_const_vals m) name"

definition smt_model_lookup_sort_members ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> String.literal list option" where
  "smt_model_lookup_sort_members m sort_name \<equiv> map_of (sm_sort_members m) sort_name"

definition smt_model_lookup_rel ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> smt_val list option" where
  "smt_model_lookup_rel m name \<equiv> map_of (sm_pred_domain m) name"

definition smt_model_lookup_pairs ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> (smt_val \<times> smt_val) list option" where
  "smt_model_lookup_pairs m name \<equiv> map_of (sm_pred_lookup m) name"

definition smt_model_lookup_key ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> smt_val \<Rightarrow> smt_val option" where
  "smt_model_lookup_key m rel_name key \<equiv>
     case map_of (sm_pred_lookup m) rel_name of
       None       \<Rightarrow> None
     | Some pairs \<Rightarrow> map_option snd (find (\<lambda>p. fst p = key) pairs)"

definition smt_model_lookup_field ::
  "smt_model \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> smt_val option" where
  "smt_model_lookup_field m entity_id field_name \<equiv>
     case map_of (sm_pred_fields m) entity_id of
       None    \<Rightarrow> None
     | Some fs \<Rightarrow> map_of fs field_name"

fun smt_val_field_lookup ::
  "smt_model \<Rightarrow> smt_val \<Rightarrow> String.literal \<Rightarrow> smt_val option" where
  "smt_val_field_lookup m (SEntityElem _ eid) fld = smt_model_lookup_field m eid fld"
| "smt_val_field_lookup m (SEntityWith base ov_fld ov_val) fld =
     (if fld = ov_fld then Some ov_val else smt_val_field_lookup m base fld)"
| "smt_val_field_lookup _ _ _ = None"

record smt_model_pair =
  smp_pre  :: smt_model
  smp_post :: smt_model

fun smt_model_pair_at ::
  "smt_model_pair \<Rightarrow> state_mode \<Rightarrow> smt_model" where
  "smt_model_pair_at mp SmPre  = smp_pre mp"
| "smt_model_pair_at mp SmPost = smp_post mp"

definition smt_model_pair_diag :: "smt_model \<Rightarrow> smt_model_pair" where
  "smt_model_pair_diag m \<equiv> \<lparr> smp_pre = m, smp_post = m \<rparr>"

lemma smt_model_pair_at_diag [simp]:
  "smt_model_pair_at (smt_model_pair_diag m) mode = m"
  by (cases mode; simp add: smt_model_pair_diag_def)

text \<open>Issue #210 (M_L.4.l): mirrors \<open>peel_relation_ref\<close> on the SMT side.
  After the \<open>TIndexRel\<close> carrier widens to \<open>smt_term\<close>, recognising the
  same three relation-reference shapes (\<open>TVar\<close>, \<open>TPre TVar\<close>, \<open>TPrime
  TVar\<close>) lets \<open>smtEval\<close> dispatch lookups to the correct relation name.
  Other shapes return \<open>None\<close>, mirroring \<open>eval\<close>'s gating.\<close>

fun relRefVarName :: "smt_term \<Rightarrow> String.literal option" where
  "relRefVarName (TVar rel) = Some rel"
| "relRefVarName _ = None"

fun peelSmtRelationRef :: "smt_term \<Rightarrow> String.literal option" where
  "peelSmtRelationRef (TVar rel)   = Some rel"
| "peelSmtRelationRef (TPre t)     = relRefVarName t"
| "peelSmtRelationRef (TPrime t)   = relRefVarName t"
| "peelSmtRelationRef _            = None"

type_synonym smt_env = "(String.literal \<times> smt_val) list"

definition smt_env_lookup :: "smt_env \<Rightarrow> String.literal \<Rightarrow> smt_val option" where
  "smt_env_lookup env name \<equiv> map_of env name"

fun as_smt_bool :: "smt_val \<Rightarrow> bool option" where
  "as_smt_bool (SBool b) = Some b"
| "as_smt_bool _ = None"

fun as_smt_int :: "smt_val \<Rightarrow> int option" where
  "as_smt_int (SInt n) = Some n"
| "as_smt_int _ = None"

primrec contains_smt_val :: "smt_val list \<Rightarrow> smt_val \<Rightarrow> bool" where
  "contains_smt_val [] v = False"
| "contains_smt_val (x # xs) v = (x = v \<or> contains_smt_val xs v)"

primrec dedupe_smt_vals :: "smt_val list \<Rightarrow> smt_val list" where
  "dedupe_smt_vals [] = []"
| "dedupe_smt_vals (x # xs) =
     (let rest = dedupe_smt_vals xs
      in if contains_smt_val rest x then rest else x # rest)"

definition set_union_smt_vals ::
  "smt_val list \<Rightarrow> smt_val list \<Rightarrow> smt_val list" where
  "set_union_smt_vals l r \<equiv> dedupe_smt_vals (l @ r)"

definition set_intersect_smt_vals ::
  "smt_val list \<Rightarrow> smt_val list \<Rightarrow> smt_val list" where
  "set_intersect_smt_vals l r \<equiv> dedupe_smt_vals (filter (\<lambda>v. contains_smt_val r v) l)"

definition set_diff_smt_vals ::
  "smt_val list \<Rightarrow> smt_val list \<Rightarrow> smt_val list" where
  "set_diff_smt_vals l r \<equiv> dedupe_smt_vals (filter (\<lambda>v. \<not> contains_smt_val r v) l)"

text \<open>\<open>agg_sum coll field\<close> is the uninterpreted value of \<open>sum(coll, i => i.field)\<close>: abstract, like
  \<open>str_predicate\<close>, so the trusted translator emits it as a Z3 uninterpreted function of the
  collection (same collection + field \<Rightarrow> same sum). It captures the functional dependency of the
  aggregate on its collection, not the arithmetic of summation (finite sums are not expressible in
  first-order SMT); the obligation is vacuous on the reference \<open>eval\<close> (a 2-arg \<open>CallF\<close> evaluates to
  \<open>None\<close>), so any realisation is sound.\<close>
consts agg_sum :: "smt_val \<Rightarrow> String.literal \<Rightarrow> int"

function (sequential) smtEval :: "smt_model \<Rightarrow> smt_env \<Rightarrow> smt_term \<Rightarrow> smt_val option"
and smtEval_forall_enum ::
  "smt_model \<Rightarrow> smt_env \<Rightarrow> String.literal \<Rightarrow> String.literal
   \<Rightarrow> String.literal list \<Rightarrow> smt_term \<Rightarrow> smt_val option"
and smtEval_forall_rel ::
  "smt_model \<Rightarrow> smt_env \<Rightarrow> String.literal \<Rightarrow> smt_val list
   \<Rightarrow> smt_term \<Rightarrow> smt_val option"
and smtEval_the_rel ::
  "smt_model \<Rightarrow> smt_env \<Rightarrow> String.literal \<Rightarrow> smt_val list
   \<Rightarrow> smt_term \<Rightarrow> smt_val list option"
where
  "smtEval m env (BLit b) = Some (SBool b)"
| "smtEval m env (ILit n) = Some (SInt n)"
| "smtEval m env (RLit r) = Some (SReal r)"
| "smtEval m env (TVar x) =
     (case smt_env_lookup env x of
        Some v \<Rightarrow> Some v
      | None   \<Rightarrow> smt_model_lookup_const m x)"
| "smtEval m env (EnumElemConst en mem) =
     (case smt_model_lookup_sort_members m en of
        Some members \<Rightarrow>
          (if List.member members mem
             then Some (SEnumElem en mem)
             else None)
      | None \<Rightarrow> None)"
| "smtEval m env (TNot t) =
     (case smtEval m env t of
        Some (SBool b) \<Rightarrow> Some (SBool (\<not> b))
      | _              \<Rightarrow> None)"
| "smtEval m env (TAnd l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SBool a), Some (SBool b)) \<Rightarrow> Some (SBool (a \<and> b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TOr l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SBool a), Some (SBool b)) \<Rightarrow> Some (SBool (a \<or> b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TImplies l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SBool a), Some (SBool b)) \<Rightarrow> Some (SBool ((\<not> a) \<or> b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TEq l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SReal b)) \<Rightarrow> Some (SBool (of_int a = b))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow> Some (SBool (a = of_int b))
      | (Some a, Some b) \<Rightarrow> Some (SBool (a = b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TLt l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SBool (a < b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SBool (a < b))
      | (Some (SInt a), Some (SReal b)) \<Rightarrow> Some (SBool (of_int a < b))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow> Some (SBool (a < of_int b))
      | (Some (SStr a), Some (SStr b)) \<Rightarrow> Some (SBool (a < b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TNeg t) =
     (case smtEval m env t of
        Some (SInt n) \<Rightarrow> Some (SInt (- n))
      | Some (SReal n) \<Rightarrow> Some (SReal (- n))
      | _             \<Rightarrow> None)"
| "smtEval m env (TAdd l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SInt (a + b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SReal (a + b))
      | (Some (SInt a), Some (SReal b)) \<Rightarrow> Some (SReal (of_int a + b))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow> Some (SReal (a + of_int b))
      | (Some (SStr a), Some (SStr b)) \<Rightarrow> Some (SStr (a + b))
      | (Some (SSeq a), Some (SSeq b)) \<Rightarrow> Some (SSeq (a @ b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSub l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SInt (a - b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SReal (a - b))
      | (Some (SInt a), Some (SReal b)) \<Rightarrow> Some (SReal (of_int a - b))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow> Some (SReal (a - of_int b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TMul l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SInt (a * b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SReal (a * b))
      | (Some (SInt a), Some (SReal b)) \<Rightarrow> Some (SReal (of_int a * b))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow> Some (SReal (a * of_int b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TDiv l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow>
          (if b = 0 then None else Some (SInt (a div b)))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow>
          (if b = 0 then None else Some (SReal (a / b)))
      | (Some (SInt a), Some (SReal b)) \<Rightarrow>
          (if b = 0 then None else Some (SReal (of_int a / b)))
      | (Some (SReal a), Some (SInt b)) \<Rightarrow>
          (if b = 0 then None else Some (SReal (a / of_int b)))
      | _ \<Rightarrow> None)"
| "smtEval m env (TInDom rel_name arg) =
     (case smtEval m env arg of
        Some v \<Rightarrow>
          (case smt_model_lookup_rel m rel_name of
             Some d \<Rightarrow> Some (SBool (contains_smt_val d v))
           | None   \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "smtEval m env (TCardRel rel_name) =
     (case smt_model_lookup_rel m rel_name of
        Some d \<Rightarrow> Some (SInt (int (length d)))
      | None   \<Rightarrow> None)"
| "smtEval m env (TCard t) =
     (case smtEval m env t of
        Some (SSet xs) \<Rightarrow> Some (SInt (int (length xs)))
      | _              \<Rightarrow> None)"
| "smtEval m env (TLetIn x v body) =
     (case smtEval m env v of
        Some va \<Rightarrow> smtEval m ((x, va) # env) body
      | None    \<Rightarrow> None)"
| "smtEval m env (TForallEnum var sort_name body) =
     (case smt_model_lookup_sort_members m sort_name of
        Some members \<Rightarrow> smtEval_forall_enum m env var sort_name members body
      | None         \<Rightarrow> None)"
| "smtEval m env (TForallRel var rel_name body) =
     (case smt_model_lookup_rel m rel_name of
        Some d \<Rightarrow> smtEval_forall_rel m env var d body
      | None   \<Rightarrow> None)"
| "smtEval m env (TExistsRel var rel_name body) =
     (case smt_model_lookup_rel m rel_name of
        Some d \<Rightarrow>
          (case smtEval_the_rel m env var d body of
             Some matches \<Rightarrow> Some (SBool (matches \<noteq> []))
           | None         \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "smtEval m env (TTheRel var rel_name body) =
     (case smt_model_lookup_rel m rel_name of
        Some d \<Rightarrow>
          (case smtEval_the_rel m env var d body of
             Some (x # rest) \<Rightarrow> (if list_all (\<lambda>y. y = x) rest then Some x else None)
           | _               \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "smtEval m env (TEntityBase name) = Some (SEntityElem name (STR ''''))"
| "smtEval m env (TForallSet var setT body) =
     (case smtEval m env setT of
        Some (SSet elems) \<Rightarrow> smtEval_forall_rel m env var elems body
      | _ \<Rightarrow> None)"
| "smtEval m env (TTheSet var setT body) =
     (case smtEval m env setT of
        Some (SSet elems) \<Rightarrow>
          (case smtEval_the_rel m env var elems body of
             Some (x # rest) \<Rightarrow> (if list_all (\<lambda>y. y = x) rest then Some x else None)
           | _               \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "smtEval m env (TIndexRel base key) =
     (case (peelSmtRelationRef base, smtEval m env key) of
        (Some rel, Some kv) \<Rightarrow> smt_model_lookup_key m rel kv
      | _                   \<Rightarrow> None)"
| "smtEval m env (TFieldAccess base fname) =
     (case smtEval m env base of
        Some v \<Rightarrow> smt_val_field_lookup m v fname
      | None   \<Rightarrow> None)"
| "smtEval m env TSetEmpty = Some (SSet [])"
| "smtEval m env (TSetInsert elem set_t) =
     (case (smtEval m env elem, smtEval m env set_t) of
        (Some v, Some (SSet members)) \<Rightarrow> Some (SSet (dedupe_smt_vals (v # members)))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSetMember elem set_t) =
     (case (smtEval m env elem, smtEval m env set_t) of
        (Some v, Some (SSet members)) \<Rightarrow> Some (SBool (contains_smt_val members v))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSetUnion l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SSet a), Some (SSet b)) \<Rightarrow> Some (SSet (set_union_smt_vals a b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSetIntersect l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SSet a), Some (SSet b)) \<Rightarrow> Some (SSet (set_intersect_smt_vals a b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSetDiff l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SSet a), Some (SSet b)) \<Rightarrow> Some (SSet (set_diff_smt_vals a b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TPrime t) = smtEval m env t"
| "smtEval m env (TPre t)   = smtEval m env t"
| "smtEval m env (TWithRec base fld value_t) =
     (case (smtEval m env base, smtEval m env value_t) of
        (Some bv, Some v) \<Rightarrow> Some (SEntityWith bv fld v)
      | _ \<Rightarrow> None)"
| "smtEval m env (TIte c a b) =
     (case smtEval m env c of
        Some (SBool True)  \<Rightarrow> smtEval m env a
      | Some (SBool False) \<Rightarrow> smtEval m env b
      | _ \<Rightarrow> None)"
| "smtEval m env TNone     = Some SNone"
| "smtEval m env (TSome t) = map_option SSome (smtEval m env t)"
| "smtEval m env (TStrLit v) = Some (SStr v)"
| "smtEval m env (TMatches t pat) =
     (case smtEval m env t of
        Some (SStr str) \<Rightarrow> Some (SBool (string_matches str pat))
      | _ \<Rightarrow> None)"
| "smtEval m env (TUStrPred name t) =
     (case smtEval m env t of
        Some (SStr str) \<Rightarrow> Some (SBool (str_predicate name str))
      | _ \<Rightarrow> None)"
| "smtEval m env (TUStrFunc name t) =
     (case smtEval m env t of
        Some (SStr str) \<Rightarrow> Some (SStr (builtin_str_func name str))
      | _ \<Rightarrow> None)"
| "smtEval m env (TUIntFunc name t) =
     (case smtEval m env t of
        Some (SInt n) \<Rightarrow> Some (SInt (builtin_int_func name n))
      | _ \<Rightarrow> None)"
| "smtEval m env (TStrLen t) =
     (case smtEval m env t of
        Some (SStr s) \<Rightarrow> Some (SInt (str_length s))
      | _ \<Rightarrow> None)"
| "smtEval m env (TUConst nm) = Some (SInt (builtin_const_val nm))"
| "smtEval m env TSeqEmpty = Some (SSeq [])"
| "smtEval m env (TSeqCons e rest) =
     (case (smtEval m env e, smtEval m env rest) of
        (Some v, Some (SSeq vs)) \<Rightarrow> Some (SSeq (v # vs))
      | _ \<Rightarrow> None)"
| "smtEval m env TMapEmpty = Some (SMap [])"
| "smtEval m env (TMapCons k v rest) =
     (case (smtEval m env k, smtEval m env v, smtEval m env rest) of
        (Some kv, Some vv, Some (SMap ps)) \<Rightarrow> Some (SMap ((kv, vv) # ps))
      | _ \<Rightarrow> None)"
| "smtEval m env (TSum c f) =
     (case smtEval m env c of
        Some cv \<Rightarrow> Some (SInt (agg_sum cv f))
      | None    \<Rightarrow> None)"

| "smtEval_forall_enum m env var sort_name [] body = Some (SBool True)"
| "smtEval_forall_enum m env var sort_name (mem # rest) body =
     (case smtEval m ((var, SEnumElem sort_name mem) # env) body of
        Some (SBool b) \<Rightarrow>
          (case smtEval_forall_enum m env var sort_name rest body of
             Some (SBool acc) \<Rightarrow> Some (SBool (b \<and> acc))
           | _                \<Rightarrow> None)
      | _ \<Rightarrow> None)"

| "smtEval_forall_rel m env var [] body = Some (SBool True)"
| "smtEval_forall_rel m env var (v # rest) body =
     (case smtEval m ((var, v) # env) body of
        Some (SBool b) \<Rightarrow>
          (case smtEval_forall_rel m env var rest body of
             Some (SBool acc) \<Rightarrow> Some (SBool (b \<and> acc))
           | _                \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "smtEval_the_rel m env var [] body = Some []"
| "smtEval_the_rel m env var (v # rest) body =
     (case smtEval m ((var, v) # env) body of
        Some (SBool b) \<Rightarrow>
          (case smtEval_the_rel m env var rest body of
             Some matches \<Rightarrow> Some (if b then v # matches else matches)
           | None         \<Rightarrow> None)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (Inl (_, _, t)) \<Rightarrow> size t
             | Inl (Inr (_, _, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inl (_, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inr (_, _, _, _, body)) \<Rightarrow> size body),
        (\<lambda>p. case p of
               Inl (Inl _) \<Rightarrow> 0
             | Inl (Inr (_, _, _, _, members, _)) \<Rightarrow> Suc (length members)
             | Inr (Inl (_, _, _, d, _)) \<Rightarrow> Suc (length d)
             | Inr (Inr (_, _, _, d, _)) \<Rightarrow> Suc (length d))
       ]")
     auto

end
