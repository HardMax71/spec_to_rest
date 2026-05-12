theory Semantics
  imports IR
begin

datatype (plugins only: code size) ir_value =
    VBool bool
  | VInt int
  | VEnum "String.literal" "String.literal"
  | VEntity "String.literal" "String.literal"
  | VSet "ir_value list"
  | VEntityWith "ir_value" "String.literal" "ir_value"

type_synonym env = "(String.literal \<times> ir_value) list"

record schema =
  sch_enums    :: "enum_decl list"
  sch_entities :: "entity_decl list"

definition schema_empty :: schema where
  "schema_empty \<equiv> \<lparr> sch_enums = [], sch_entities = [] \<rparr>"

definition schema_lookup_enum :: "schema \<Rightarrow> String.literal \<Rightarrow> enum_decl option" where
  "schema_lookup_enum s name \<equiv> find (\<lambda>d. enm_name d = name) (sch_enums s)"

definition schema_lookup_entity :: "schema \<Rightarrow> String.literal \<Rightarrow> entity_decl option" where
  "schema_lookup_entity s name \<equiv> find (\<lambda>d. ed_name d = name) (sch_entities s)"

record state =
  rt_scalars       :: "(String.literal \<times> ir_value) list"
  rt_relations     :: "(String.literal \<times> ir_value list) list"
  rt_lookups       :: "(String.literal \<times> (ir_value \<times> ir_value) list) list"
  rt_entity_fields :: "(String.literal \<times> (String.literal \<times> ir_value) list) list"

definition state_empty :: state where
  "state_empty \<equiv>
     \<lparr> rt_scalars = [], rt_relations = [], rt_lookups = [], rt_entity_fields = [] \<rparr>"

definition state_lookup_scalar :: "state \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "state_lookup_scalar st name \<equiv> map_of (rt_scalars st) name"

definition state_relation_domain :: "state \<Rightarrow> String.literal \<Rightarrow> ir_value list option" where
  "state_relation_domain st name \<equiv> map_of (rt_relations st) name"

definition state_relation_pairs ::
  "state \<Rightarrow> String.literal \<Rightarrow> (ir_value \<times> ir_value) list option" where
  "state_relation_pairs st name \<equiv> map_of (rt_lookups st) name"

definition state_lookup_key :: "state \<Rightarrow> String.literal \<Rightarrow> ir_value \<Rightarrow> ir_value option" where
  "state_lookup_key st rel_name key \<equiv>
     case map_of (rt_lookups st) rel_name of
       None       \<Rightarrow> None
     | Some pairs \<Rightarrow> map_option snd (find (\<lambda>p. fst p = key) pairs)"

definition state_lookup_field ::
  "state \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "state_lookup_field st entity_id field_name \<equiv>
     case map_of (rt_entity_fields st) entity_id of
       None     \<Rightarrow> None
     | Some fs  \<Rightarrow> map_of fs field_name"

fun value_field_lookup :: "state \<Rightarrow> ir_value \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "value_field_lookup st (VEntity _ eid) fld = state_lookup_field st eid fld"
| "value_field_lookup st (VEntityWith base ov_fld ov_val) fld =
     (if fld = ov_fld then Some ov_val else value_field_lookup st base fld)"
| "value_field_lookup _ _ _ = None"

definition env_lookup :: "env \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "env_lookup env name \<equiv> map_of env name"

record state_pair =
  sp_pre  :: state
  sp_post :: state

fun state_pair_at :: "state_pair \<Rightarrow> state_mode \<Rightarrow> state" where
  "state_pair_at sp SmPre = sp_pre sp"
| "state_pair_at sp SmPost = sp_post sp"

definition state_pair_diag :: "state \<Rightarrow> state_pair" where
  "state_pair_diag st \<equiv> \<lparr> sp_pre = st, sp_post = st \<rparr>"

lemma state_pair_at_diag [simp]:
  "state_pair_at (state_pair_diag st) mode = st"
  by (cases mode; simp add: state_pair_diag_def)

fun eval_bool_bin :: "bool_bin_op \<Rightarrow> bool \<Rightarrow> bool \<Rightarrow> bool" where
  "eval_bool_bin AndOp a b = (a \<and> b)"
| "eval_bool_bin OrOp a b = (a \<or> b)"
| "eval_bool_bin ImpliesOp a b = ((\<not> a) \<or> b)"
| "eval_bool_bin IffOp a b = (a = b)"

fun eval_arith :: "arith_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_arith AddOp (Some (VInt a)) (Some (VInt b)) = Some (VInt (a + b))"
| "eval_arith SubOp (Some (VInt a)) (Some (VInt b)) = Some (VInt (a - b))"
| "eval_arith MulOp (Some (VInt a)) (Some (VInt b)) = Some (VInt (a * b))"
| "eval_arith DivOp (Some (VInt a)) (Some (VInt b)) =
     (if b = 0 then None else Some (VInt (a div b)))"
| "eval_arith _ _ _ = None"

fun eval_cmp :: "cmp_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_cmp EqOp  (Some a) (Some b) = Some (VBool (a = b))"
| "eval_cmp NeqOp (Some a) (Some b) = Some (VBool (a \<noteq> b))"
| "eval_cmp LtOp  (Some (VInt a)) (Some (VInt b)) = Some (VBool (a < b))"
| "eval_cmp LeOp  (Some (VInt a)) (Some (VInt b)) = Some (VBool (a \<le> b))"
| "eval_cmp GtOp  (Some (VInt a)) (Some (VInt b)) = Some (VBool (a > b))"
| "eval_cmp GeOp  (Some (VInt a)) (Some (VInt b)) = Some (VBool (a \<ge> b))"
| "eval_cmp _ _ _ = None"

fun as_bool :: "ir_value \<Rightarrow> bool option" where
  "as_bool (VBool b) = Some b"
| "as_bool _ = None"

fun as_int :: "ir_value \<Rightarrow> int option" where
  "as_int (VInt n) = Some n"
| "as_int _ = None"

primrec contains_value :: "ir_value list \<Rightarrow> ir_value \<Rightarrow> bool" where
  "contains_value [] v = False"
| "contains_value (x # xs) v = (x = v \<or> contains_value xs v)"

primrec dedupe_values :: "ir_value list \<Rightarrow> ir_value list" where
  "dedupe_values [] = []"
| "dedupe_values (x # xs) =
     (let rest = dedupe_values xs
      in if contains_value rest x then rest else x # rest)"

definition set_union_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_union_values l r \<equiv> dedupe_values (l @ r)"

definition set_intersect_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_intersect_values l r \<equiv> dedupe_values (filter (\<lambda>v. contains_value r v) l)"

definition set_diff_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_diff_values l r \<equiv> dedupe_values (filter (\<lambda>v. \<not> contains_value r v) l)"

fun eval_set_bin ::
  "set_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_set_bin UnionOp     (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_union_values l r))"
| "eval_set_bin IntersectOp (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_intersect_values l r))"
| "eval_set_bin DiffOp      (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_diff_values l r))"
| "eval_set_bin _ _ _ = None"

function (sequential) eval :: "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_forall_enum ::
  "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> String.literal
   \<Rightarrow> String.literal list \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_forall_rel ::
  "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> ir_value list \<Rightarrow> expr \<Rightarrow> ir_value option"
where
  "eval s st env (BoolLit b _) = Some (VBool b)"
| "eval s st env (IntLit n _) = Some (VInt n)"
| "eval s st env (Ident x _) =
     (case env_lookup env x of
        Some v \<Rightarrow> Some v
      | None   \<Rightarrow> state_lookup_scalar st x)"
| "eval s st env (UnNot e _) =
     (case eval s st env e of
        Some (VBool b) \<Rightarrow> Some (VBool (\<not> b))
      | _              \<Rightarrow> None)"
| "eval s st env (UnNeg e _) =
     (case eval s st env e of
        Some (VInt n) \<Rightarrow> Some (VInt (- n))
      | _             \<Rightarrow> None)"
| "eval s st env (BoolBin op l r _) =
     (case (eval s st env l, eval s st env r) of
        (Some (VBool a), Some (VBool b)) \<Rightarrow> Some (VBool (eval_bool_bin op a b))
      | _ \<Rightarrow> None)"
| "eval s st env (Arith op l r _) = eval_arith op (eval s st env l) (eval s st env r)"
| "eval s st env (Cmp op l r _)   = eval_cmp op (eval s st env l) (eval s st env r)"
| "eval s st env (LetIn x v body _) =
     (case eval s st env v of
        Some va \<Rightarrow> eval s st ((x, va) # env) body
      | None    \<Rightarrow> None)"
| "eval s st env (EnumAccess en mem _) =
     (case schema_lookup_enum s en of
        Some d \<Rightarrow>
          (if List.member (enm_members d) mem
             then Some (VEnum en mem)
             else None)
      | None \<Rightarrow> None)"
| "eval s st env (Member elem rel_name _) =
     (case eval s st env elem of
        Some v \<Rightarrow>
          (case state_relation_domain st rel_name of
             Some rel_dom \<Rightarrow> Some (VBool (contains_value rel_dom v))
           | None     \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "eval s st env (ForallEnum var en body _) =
     (case schema_lookup_enum s en of
        Some d \<Rightarrow> eval_forall_enum s st env var en (enm_members d) body
      | None   \<Rightarrow> None)"
| "eval s st env (ForallRel var rel_name body _) =
     (case state_relation_domain st rel_name of
        Some rel_dom \<Rightarrow> eval_forall_rel s st env var rel_dom body
      | None     \<Rightarrow> None)"
| "eval s st env (Prime e _) = eval s st env e"
| "eval s st env (Pre e _)   = eval s st env e"
| "eval s st env (CardRel rel_name _) =
     (case state_relation_domain st rel_name of
        Some rel_dom \<Rightarrow> Some (VInt (int (length rel_dom)))
      | None     \<Rightarrow> None)"
| "eval s st env (IndexRel base key _) =
     (case (peel_relation_ref base, eval s st env key) of
        (Some rel, Some kv) \<Rightarrow> state_lookup_key st rel kv
      | _                   \<Rightarrow> None)"
| "eval s st env (FieldAccess base fname _) =
     (case eval s st env base of
        Some v \<Rightarrow> value_field_lookup st v fname
      | None   \<Rightarrow> None)"
| "eval s st env (SetEmpty _) = Some (VSet [])"
| "eval s st env (SetInsert elem set_e _) =
     (case (eval s st env elem, eval s st env set_e) of
        (Some v, Some (VSet members)) \<Rightarrow> Some (VSet (dedupe_values (v # members)))
      | _ \<Rightarrow> None)"
| "eval s st env (SetMember elem set_e _) =
     (case (eval s st env elem, eval s st env set_e) of
        (Some v, Some (VSet members)) \<Rightarrow> Some (VBool (contains_value members v))
      | _ \<Rightarrow> None)"
| "eval s st env (SetBin op l r _) = eval_set_bin op (eval s st env l) (eval s st env r)"
| "eval s st env (WithRec base fld value_e _) =
     (case (eval s st env base, eval s st env value_e) of
        (Some bv, Some v) \<Rightarrow> Some (VEntityWith bv fld v)
      | _ \<Rightarrow> None)"

| "eval_forall_enum s st env var en [] body = Some (VBool True)"
| "eval_forall_enum s st env var en (mem # rest) body =
     (case eval s st ((var, VEnum en mem) # env) body of
        Some (VBool b) \<Rightarrow>
          (case eval_forall_enum s st env var en rest body of
             Some (VBool acc) \<Rightarrow> Some (VBool (b \<and> acc))
           | _                \<Rightarrow> None)
      | _ \<Rightarrow> None)"

| "eval_forall_rel s st env var [] body = Some (VBool True)"
| "eval_forall_rel s st env var (v # rest) body =
     (case eval s st ((var, v) # env) body of
        Some (VBool b) \<Rightarrow>
          (case eval_forall_rel s st env var rest body of
             Some (VBool acc) \<Rightarrow> Some (VBool (b \<and> acc))
           | _                \<Rightarrow> None)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (_, _, _, e) \<Rightarrow> size e
             | Inr (Inl (_, _, _, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inr (_, _, _, _, _, body)) \<Rightarrow> size body),
        (\<lambda>p. case p of
               Inl _ \<Rightarrow> 0
             | Inr (Inl (_, _, _, _, _, members, _)) \<Rightarrow> Suc (length members)
             | Inr (Inr (_, _, _, _, rel_dom, _)) \<Rightarrow> Suc (length rel_dom))
       ]")
     auto

end
