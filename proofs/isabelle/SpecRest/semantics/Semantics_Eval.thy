theory Semantics_Eval
  imports SpecRest_IR.IR SpecRest_IR.IR_Helpers SpecRest_IR.IR_Analysis "HOL.Rat"
begin

datatype (plugins only: code size) ir_value =
    VBool bool
  | VInt int
  | VReal rat
  | VEnum "String.literal" "String.literal"
  | VEntity "String.literal" "String.literal"
  | VSet "ir_value list"
  | VEntityWith "ir_value" "String.literal" "ir_value"
  | VNone
  | VSome "ir_value"
  | VStr "String.literal"
  | VSeq "ir_value list"
  | VMap "(ir_value \<times> ir_value) list"

type_synonym env = "(String.literal \<times> ir_value) list"

record schema =
  sch_enums    :: "schema_enum_decl list"
  sch_entities :: "schema_entity_decl list"

definition schema_empty :: schema where
  "schema_empty \<equiv> \<lparr> sch_enums = [], sch_entities = [] \<rparr>"

definition schema_lookup_enum :: "schema \<Rightarrow> String.literal \<Rightarrow> schema_enum_decl option" where
  "schema_lookup_enum s name \<equiv> find (\<lambda>d. enm_name d = name) (sch_enums s)"

definition schema_lookup_entity :: "schema \<Rightarrow> String.literal \<Rightarrow> schema_entity_decl option" where
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

fun int_arith :: "arith_op \<Rightarrow> int \<Rightarrow> int \<Rightarrow> ir_value option" where
  "int_arith AddOp a b = Some (VInt (a + b))"
| "int_arith SubOp a b = Some (VInt (a - b))"
| "int_arith MulOp a b = Some (VInt (a * b))"
| "int_arith DivOp a b = (if b = 0 then None else Some (VInt (a div b)))"

fun real_arith :: "arith_op \<Rightarrow> rat \<Rightarrow> rat \<Rightarrow> ir_value option" where
  "real_arith AddOp a b = Some (VReal (a + b))"
| "real_arith SubOp a b = Some (VReal (a - b))"
| "real_arith MulOp a b = Some (VReal (a * b))"
| "real_arith DivOp a b = (if b = 0 then None else Some (VReal (a / b)))"

fun str_arith :: "arith_op \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "str_arith AddOp a b = Some (VStr (a + b))"
| "str_arith _ _ _ = None"

fun seq_arith :: "arith_op \<Rightarrow> ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value option" where
  "seq_arith AddOp a b = Some (VSeq (a @ b))"
| "seq_arith _ _ _ = None"

fun eval_arith :: "arith_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_arith op x y =
     (case x of
        Some (VInt a) \<Rightarrow>
          (case y of
             Some (VInt b)  \<Rightarrow> int_arith op a b
           | Some (VReal b) \<Rightarrow> real_arith op (of_int a) b
           | _              \<Rightarrow> None)
      | Some (VReal a) \<Rightarrow>
          (case y of
             Some (VInt b)  \<Rightarrow> real_arith op a (of_int b)
           | Some (VReal b) \<Rightarrow> real_arith op a b
           | _              \<Rightarrow> None)
      | Some (VStr a) \<Rightarrow>
          (case y of
             Some (VStr b) \<Rightarrow> str_arith op a b
           | _             \<Rightarrow> None)
      | Some (VSeq a) \<Rightarrow>
          (case y of
             Some (VSeq b) \<Rightarrow> seq_arith op a b
           | _             \<Rightarrow> None)
      | _ \<Rightarrow> None)"

definition ir_val_eq :: "ir_value \<Rightarrow> ir_value \<Rightarrow> bool" where
  "ir_val_eq x y =
     (case (x, y) of
        (VInt a, VReal b) \<Rightarrow> of_int a = b
      | (VReal a, VInt b) \<Rightarrow> a = of_int b
      | _ \<Rightarrow> x = y)"

fun int_cmp :: "cmp_op \<Rightarrow> int \<Rightarrow> int \<Rightarrow> ir_value option" where
  "int_cmp LtOp a b = Some (VBool (a < b))"
| "int_cmp LeOp a b = Some (VBool (a \<le> b))"
| "int_cmp GtOp a b = Some (VBool (a > b))"
| "int_cmp GeOp a b = Some (VBool (a \<ge> b))"
| "int_cmp _ _ _ = None"

fun real_cmp :: "cmp_op \<Rightarrow> rat \<Rightarrow> rat \<Rightarrow> ir_value option" where
  "real_cmp LtOp a b = Some (VBool (a < b))"
| "real_cmp LeOp a b = Some (VBool (a \<le> b))"
| "real_cmp GtOp a b = Some (VBool (a > b))"
| "real_cmp GeOp a b = Some (VBool (a \<ge> b))"
| "real_cmp _ _ _ = None"

fun str_cmp :: "cmp_op \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> ir_value option" where
  "str_cmp LtOp a b = Some (VBool (a < b))"
| "str_cmp LeOp a b = Some (VBool (a \<le> b))"
| "str_cmp GtOp a b = Some (VBool (a > b))"
| "str_cmp GeOp a b = Some (VBool (a \<ge> b))"
| "str_cmp _ _ _ = None"

fun eval_cmp :: "cmp_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_cmp op x y =
     (case x of
        Some a \<Rightarrow>
          (case y of
             Some b \<Rightarrow>
               (case op of
                  EqOp  \<Rightarrow> Some (VBool (ir_val_eq a b))
                | NeqOp \<Rightarrow> Some (VBool (\<not> ir_val_eq a b))
                | _ \<Rightarrow>
                    (case a of
                       VInt ai \<Rightarrow>
                         (case b of
                            VInt bi  \<Rightarrow> int_cmp op ai bi
                          | VReal br \<Rightarrow> real_cmp op (of_int ai) br
                          | _        \<Rightarrow> None)
                     | VReal ar \<Rightarrow>
                         (case b of
                            VInt bi  \<Rightarrow> real_cmp op ar (of_int bi)
                          | VReal br \<Rightarrow> real_cmp op ar br
                          | _        \<Rightarrow> None)
                     | VStr sa \<Rightarrow>
                         (case b of
                            VStr sb \<Rightarrow> str_cmp op sa sb
                          | _        \<Rightarrow> None)
                     | _ \<Rightarrow> None))
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

text \<open>Category H — first proven bricks of the spec front-half (the
  precondition the universal soundness theorem assumes via \<open>eval e = Some\<close>).
  These are NOT type-safety / progress (still an open initiative, see
  \<open>STATUS.md\<close> §3); they pin down the *exact* operand-typing preconditions
  and the *exact* source of partiality for the arithmetic / comparison
  fragment — precisely what a future spec type system must discharge. A naive
  \<open>free_vars \<subseteq> dom env\<close> scope-safety lemma is deliberately not attempted:
  it is *false* here because \<open>eval\<close>'s \<open>Ident\<close> arm legitimately resolves
  from \<open>state\<close>, not only \<open>env\<close>.\<close>

lemma eval_arith_some_imp_numeric_or_str:
  "eval_arith op x y = Some v \<Longrightarrow>
     (((\<exists>a. x = Some (VInt a)) \<or> (\<exists>a. x = Some (VReal a)))
      \<and> ((\<exists>b. y = Some (VInt b)) \<or> (\<exists>b. y = Some (VReal b))))
     \<or> ((\<exists>a. x = Some (VStr a)) \<and> (\<exists>b. y = Some (VStr b)))
     \<or> ((\<exists>a. x = Some (VSeq a)) \<and> (\<exists>b. y = Some (VSeq b)))"
  by (auto split: option.splits ir_value.splits if_splits)

lemma eval_arith_div_zero:
  "eval_arith DivOp (Some (VInt a)) (Some (VInt 0)) = None"
  by simp

lemma eval_arith_int_total:
  "op \<noteq> DivOp \<or> b \<noteq> 0 \<Longrightarrow>
     \<exists>r. eval_arith op (Some (VInt a)) (Some (VInt b)) = Some (VInt r)"
  by (cases op) (auto split: if_splits)

lemma eval_cmp_some_imp_defined:
  "eval_cmp op x y = Some v \<Longrightarrow> (\<exists>a. x = Some a) \<and> (\<exists>b. y = Some b)"
  by (auto split: option.splits ir_value.splits cmp_op.splits)

lemma eval_cmp_order_imp_numeric_or_str:
  "op \<in> {LtOp, LeOp, GtOp, GeOp} \<Longrightarrow> eval_cmp op x y = Some v \<Longrightarrow>
     (((\<exists>a. x = Some (VInt a)) \<or> (\<exists>a. x = Some (VReal a)))
      \<and> ((\<exists>b. y = Some (VInt b)) \<or> (\<exists>b. y = Some (VReal b))))
     \<or> ((\<exists>a. x = Some (VStr a)) \<and> (\<exists>b. y = Some (VStr b)))"
  by (auto split: option.splits ir_value.splits cmp_op.splits if_splits)

end
