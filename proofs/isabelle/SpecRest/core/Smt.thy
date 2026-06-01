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

datatype (plugins only: code size) smt_term =
    BLit bool
  | ILit int
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
  | TLetIn "String.literal" "smt_term" "smt_term"
  | TForallEnum "String.literal" "String.literal" "smt_term"
  | TForallRel "String.literal" "String.literal" "smt_term"
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

fun peelSmtRelationRef :: "smt_term \<Rightarrow> String.literal option" where
  "peelSmtRelationRef (TVar rel)            = Some rel"
| "peelSmtRelationRef (TPre (TVar rel))     = Some rel"
| "peelSmtRelationRef (TPrime (TVar rel))   = Some rel"
| "peelSmtRelationRef _                      = None"

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

function (sequential) smtEval :: "smt_model \<Rightarrow> smt_env \<Rightarrow> smt_term \<Rightarrow> smt_val option"
and smtEval_forall_enum ::
  "smt_model \<Rightarrow> smt_env \<Rightarrow> String.literal \<Rightarrow> String.literal
   \<Rightarrow> String.literal list \<Rightarrow> smt_term \<Rightarrow> smt_val option"
and smtEval_forall_rel ::
  "smt_model \<Rightarrow> smt_env \<Rightarrow> String.literal \<Rightarrow> smt_val list
   \<Rightarrow> smt_term \<Rightarrow> smt_val option"
where
  "smtEval m env (BLit b) = Some (SBool b)"
| "smtEval m env (ILit n) = Some (SInt n)"
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
        (Some a, Some b) \<Rightarrow> Some (SBool (a = b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TLt l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SBool (a < b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SBool (a < b))
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
      | _ \<Rightarrow> None)"
| "smtEval m env (TSub l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SInt (a - b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SReal (a - b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TMul l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow> Some (SInt (a * b))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow> Some (SReal (a * b))
      | _ \<Rightarrow> None)"
| "smtEval m env (TDiv l r) =
     (case (smtEval m env l, smtEval m env r) of
        (Some (SInt a), Some (SInt b)) \<Rightarrow>
          (if b = 0 then None else Some (SInt (a div b)))
      | (Some (SReal a), Some (SReal b)) \<Rightarrow>
          (if b = 0 then None else Some (SReal (a / b)))
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
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (_, _, t) \<Rightarrow> size t
             | Inr (Inl (_, _, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inr (_, _, _, _, body)) \<Rightarrow> size body),
        (\<lambda>p. case p of
               Inl _ \<Rightarrow> 0
             | Inr (Inl (_, _, _, _, members, _)) \<Rightarrow> Suc (length members)
             | Inr (Inr (_, _, _, d, _)) \<Rightarrow> Suc (length d))
       ]")
     auto

end
