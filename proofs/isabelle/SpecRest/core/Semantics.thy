theory Semantics
  imports IR IR_Helpers IR_Analysis "HOL.Rat"
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

lemma eval_arith_some_imp_numeric:
  "eval_arith op x y = Some v \<Longrightarrow>
     ((\<exists>a. x = Some (VInt a)) \<or> (\<exists>a. x = Some (VReal a)))
     \<and> ((\<exists>b. y = Some (VInt b)) \<or> (\<exists>b. y = Some (VReal b)))"
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

lemma eval_cmp_order_imp_numeric:
  "op \<in> {LtOp, LeOp, GtOp, GeOp} \<Longrightarrow> eval_cmp op x y = Some v \<Longrightarrow>
     ((\<exists>a. x = Some (VInt a)) \<or> (\<exists>a. x = Some (VReal a)))
     \<and> ((\<exists>b. y = Some (VInt b)) \<or> (\<exists>b. y = Some (VReal b)))"
  by (auto split: option.splits ir_value.splits cmp_op.splits if_splits)

text \<open>Category H Phase H1 — value types and value typing.
  The first concrete brick of the missing soundness front-half:
  a value-type ADT \<open>ty\<close> mirroring \<open>ir_value\<close>'s constructors and
  an inductive \<open>value_has_ty\<close> relation. Together with the
  Phase H0 partiality-source lemmas above (\<open>eval_arith_*\<close>,
  \<open>eval_cmp_*\<close>) this lets us state arith/cmp preservation in
  typed form (\<open>eval_arith_preservation\<close>,
  \<open>eval_cmp_preservation\<close>), which is the per-arm shape that a
  later progress theorem for \<open>eval\<close> will dispatch to.\<close>

datatype (plugins only: code size) ty =
    TBool
  | TInt
  | TReal
  | TEnum "String.literal"
  | TEntity "String.literal"
  | TSet ty

definition numeric_ty :: "ty \<Rightarrow> bool" where
  "numeric_ty t \<longleftrightarrow> t = TInt \<or> t = TReal"

definition numeric_join :: "ty \<Rightarrow> ty \<Rightarrow> ty" where
  "numeric_join t1 t2 = (if t1 = TReal \<or> t2 = TReal then TReal else TInt)"

text \<open>The typing context \<open>tyctx\<close> and its sub-records are declared
  here (before \<open>value_has_ty\<close>) because \<open>vt_entity_with\<close> needs to
  consult \<open>schemaFieldType \<Gamma>\<close> when constraining the override.
  Pre-\<open>value_has_ty\<close>-Γ this whole block lived ~200 lines below;
  hoisting only the type / record / lookup declarations keeps the
  proof-helper lemmas in their original location.\<close>

type_synonym tyenv = "(String.literal \<times> ty) list"

record state_schema =
  ss_scalars :: "(String.literal \<times> ty) list"

record tyctx =
  tc_env       :: tyenv
  tc_schema    :: state_schema
  tc_entities  :: "entity_decl_full list"
  tc_relations :: "state_field_decl_full list"
  tc_enums     :: "String.literal list"

fun typeExprFullToTy ::
  "String.literal list \<Rightarrow> String.literal list
     \<Rightarrow> type_expr_full \<Rightarrow> ty option" where
  "typeExprFullToTy enums entities (NamedTypeF n _) =
     (if n = STR ''Bool'' then Some TBool
      else if n = STR ''Int'' then Some TInt
      else if n = STR ''Float'' \<or> n = STR ''Double''
              \<or> n = STR ''Decimal'' \<or> n = STR ''Money'' then Some TReal
      else if n \<in> set enums then Some (TEnum n)
      else if n \<in> set entities then Some (TEntity n)
      else None)"
| "typeExprFullToTy enums entities (SetTypeF inner _) =
     map_option TSet (typeExprFullToTy enums entities inner)"
| "typeExprFullToTy _ _ _ = None"

definition schemaFieldType ::
  "tyctx \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> ty option" where
  "schemaFieldType \<Gamma> ename fname \<equiv>
     case List.find (\<lambda>ed. entityNameFull ed = ename) (tc_entities \<Gamma>) of
       None    \<Rightarrow> None
     | Some ed \<Rightarrow>
         (case List.find (\<lambda>fd. fieldNameFull fd = fname)
                          (entityFieldsFull ed) of
            None    \<Rightarrow> None
          | Some fd \<Rightarrow>
              typeExprFullToTy
                (tc_enums \<Gamma>)
                (map entityNameFull (tc_entities \<Gamma>))
                (fieldTypeFull fd))"

text \<open>\<open>value_has_ty\<close> is parameterised by the typing context
  \<open>\<Gamma>\<close> so the \<open>vt_entity_with\<close> rule can require its override to
  have the field's declared type. Without \<open>\<Gamma>\<close> the rule was
  \<open>base ::v TEntity ename \<Longrightarrow> VEntityWith base fld override ::v
  TEntity ename\<close> for any \<open>override\<close>; a derivation could pick
  \<open>override = VInt 0\<close> for an entity whose \<open>fld\<close> is declared
  \<open>TBool\<close>, then \<open>entity_field_well_typed\<close> (universally
  quantified over values) failed for any non-\<open>TInt\<close> schema —
  blocking the H3 callers from discharging \<open>agrees_strict\<close>.
  Flagged on PR #285 by cubic-dev-ai + coderabbitai; this is the
  follow-up.

  Notation: the old infix \<open>v ::v t\<close> would not extend to a third
  argument without losing readability, so callers now write
  \<open>value_has_ty \<Gamma> v t\<close>.\<close>

inductive value_has_ty :: "tyctx \<Rightarrow> ir_value \<Rightarrow> ty \<Rightarrow> bool" where
  vt_bool:        "value_has_ty \<Gamma> (VBool b) TBool"
| vt_int:         "value_has_ty \<Gamma> (VInt n) TInt"
| vt_real:        "value_has_ty \<Gamma> (VReal r) TReal"
| vt_enum:        "value_has_ty \<Gamma> (VEnum ename ev) (TEnum ename)"
| vt_entity:      "value_has_ty \<Gamma> (VEntity ename eid) (TEntity ename)"
| vt_set:         "(\<forall>v \<in> set vs. value_has_ty \<Gamma> v t)
                     \<Longrightarrow> value_has_ty \<Gamma> (VSet vs) (TSet t)"
| vt_entity_with: "value_has_ty \<Gamma> base (TEntity ename)
                     \<Longrightarrow> schemaFieldType \<Gamma> ename fld = Some ft
                     \<Longrightarrow> value_has_ty \<Gamma> override ft
                     \<Longrightarrow> value_has_ty \<Gamma> (VEntityWith base fld override)
                                       (TEntity ename)"

inductive_cases value_has_ty_bool_cases [elim!]: "value_has_ty \<Gamma> (VBool b) t"
inductive_cases value_has_ty_int_cases [elim!]: "value_has_ty \<Gamma> (VInt n) t"
inductive_cases value_has_ty_real_cases [elim!]: "value_has_ty \<Gamma> (VReal r) t"
inductive_cases value_has_ty_enum_cases [elim!]:
  "value_has_ty \<Gamma> (VEnum ename ev) t"
inductive_cases value_has_ty_entity_cases [elim!]:
  "value_has_ty \<Gamma> (VEntity ename eid) t"
inductive_cases value_has_ty_set_cases [elim!]: "value_has_ty \<Gamma> (VSet vs) t"
inductive_cases value_has_ty_entity_with_cases [elim!]:
  "value_has_ty \<Gamma> (VEntityWith base fld override) t"
inductive_cases value_has_ty_none_cases [elim!]: "value_has_ty \<Gamma> VNone t"
inductive_cases value_has_ty_some_cases [elim!]: "value_has_ty \<Gamma> (VSome v) t"
inductive_cases value_has_ty_str_cases [elim!]: "value_has_ty \<Gamma> (VStr s) t"
inductive_cases value_has_ty_seq_cases [elim!]: "value_has_ty \<Gamma> (VSeq vs) t"
inductive_cases value_has_ty_map_cases [elim!]: "value_has_ty \<Gamma> (VMap ps) t"

lemma value_has_ty_VBool_iff [simp]:
  "value_has_ty \<Gamma> (VBool b) t \<longleftrightarrow> t = TBool"
  by (auto intro: vt_bool)

lemma value_has_ty_VInt_iff [simp]:
  "value_has_ty \<Gamma> (VInt n) t \<longleftrightarrow> t = TInt"
  by (auto intro: vt_int)

lemma value_has_ty_VReal_iff [simp]:
  "value_has_ty \<Gamma> (VReal r) t \<longleftrightarrow> t = TReal"
  by (auto intro: vt_real)

lemma value_has_ty_VEnum_iff [simp]:
  "value_has_ty \<Gamma> (VEnum ename ev) t \<longleftrightarrow> t = TEnum ename"
  by (auto intro: vt_enum)

lemma value_has_ty_VEntity_iff [simp]:
  "value_has_ty \<Gamma> (VEntity ename eid) t \<longleftrightarrow> t = TEntity ename"
  by (auto intro: vt_entity)

lemma schemaFieldType_tc_env_update [simp]:
  "schemaFieldType (\<Gamma>\<lparr>tc_env := xs\<rparr>) ename fname
     = schemaFieldType \<Gamma> ename fname"
  by (auto simp: schemaFieldType_def split: option.splits)

text \<open>Invariance of \<open>value_has_ty\<close> under \<open>tc_env\<close> updates.
  Required because the existing \<open>*_tc_env_update [simp]\<close> lemmas
  for \<open>entity_field_well_typed\<close> and \<open>relation_value_well_typed\<close>
  unfold to bodies that contain \<open>value_has_ty\<close> calls — and after
  parameterisation those need to know the predicate doesn't read
  \<open>tc_env\<close>. \<open>vt_entity_with\<close> consults \<open>schemaFieldType \<Gamma>\<close>
  (which depends only on \<open>tc_entities\<close> and \<open>tc_enums\<close>) and not
  \<open>tc_env\<close>, so the predicate is tc_env-invariant.\<close>

lemma value_has_ty_tc_env_update [simp]:
  "value_has_ty (\<Gamma>\<lparr>tc_env := xs\<rparr>) v t \<longleftrightarrow> value_has_ty \<Gamma> v t"
proof
  show "value_has_ty (\<Gamma>\<lparr>tc_env := xs\<rparr>) v t \<Longrightarrow> value_has_ty \<Gamma> v t"
    by (induction "\<Gamma>\<lparr>tc_env := xs\<rparr>" v t rule: value_has_ty.induct)
       (auto intro: value_has_ty.intros)
  show "value_has_ty \<Gamma> v t \<Longrightarrow> value_has_ty (\<Gamma>\<lparr>tc_env := xs\<rparr>) v t"
    by (induction \<Gamma> v t rule: value_has_ty.induct)
       (auto intro: value_has_ty.intros)
qed

text \<open>Executable equivalent of \<open>value_has_ty\<close>. The \<open>inductive\<close>
  form expresses the typing relation in HOL but is not by-default
  extractable by \<open>Code_Target_Scala\<close>; \<open>check_value_has_ty\<close>
  enumerates the six rules as a mutual structural \<open>fun\<close>, so
  Scala consumers (Z3 counterexample decoding, etc.) can decide
  typing at runtime. \<open>check_value_has_ty_iff\<close> bridges back to the
  inductive so proofs reading the \<open>value_has_ty\<close>-shaped form can
  use either.\<close>

fun check_value_has_ty :: "tyctx \<Rightarrow> ir_value \<Rightarrow> ty \<Rightarrow> bool"
and check_value_has_ty_list ::
  "tyctx \<Rightarrow> ir_value list \<Rightarrow> ty \<Rightarrow> bool"
where
  "check_value_has_ty \<Gamma> (VBool _) t = (t = TBool)"
| "check_value_has_ty \<Gamma> (VInt _) t = (t = TInt)"
| "check_value_has_ty \<Gamma> (VReal _) t = (t = TReal)"
| "check_value_has_ty \<Gamma> (VEnum ename _) t = (t = TEnum ename)"
| "check_value_has_ty \<Gamma> (VEntity ename _) t = (t = TEntity ename)"
| "check_value_has_ty \<Gamma> (VSet vs) (TSet t) = check_value_has_ty_list \<Gamma> vs t"
| "check_value_has_ty \<Gamma> (VSet _) TBool = False"
| "check_value_has_ty \<Gamma> (VSet _) TInt = False"
| "check_value_has_ty \<Gamma> (VSet _) TReal = False"
| "check_value_has_ty \<Gamma> (VSet _) (TEnum _) = False"
| "check_value_has_ty \<Gamma> (VSet _) (TEntity _) = False"
| "check_value_has_ty \<Gamma> (VEntityWith base fld override) (TEntity ename) =
     (check_value_has_ty \<Gamma> base (TEntity ename) \<and>
      (case schemaFieldType \<Gamma> ename fld of
         None    \<Rightarrow> False
       | Some ft \<Rightarrow> check_value_has_ty \<Gamma> override ft))"
| "check_value_has_ty \<Gamma> (VEntityWith _ _ _) TBool = False"
| "check_value_has_ty \<Gamma> (VEntityWith _ _ _) TInt = False"
| "check_value_has_ty \<Gamma> (VEntityWith _ _ _) TReal = False"
| "check_value_has_ty \<Gamma> (VEntityWith _ _ _) (TEnum _) = False"
| "check_value_has_ty \<Gamma> (VEntityWith _ _ _) (TSet _) = False"
| "check_value_has_ty \<Gamma> VNone _ = False"
| "check_value_has_ty \<Gamma> (VSome _) _ = False"
| "check_value_has_ty \<Gamma> (VStr _) _ = False"
| "check_value_has_ty \<Gamma> (VSeq _) _ = False"
| "check_value_has_ty \<Gamma> (VMap _) _ = False"
| "check_value_has_ty_list _ [] _ = True"
| "check_value_has_ty_list \<Gamma> (v # vs) t =
     (check_value_has_ty \<Gamma> v t \<and> check_value_has_ty_list \<Gamma> vs t)"

lemma check_value_has_ty_list_all:
  "check_value_has_ty_list \<Gamma> vs t \<longleftrightarrow> (\<forall>v \<in> set vs. check_value_has_ty \<Gamma> v t)"
  by (induction vs) auto

lemma check_value_has_ty_sound_mutual:
  shows
    check_imp_vty:
      "check_value_has_ty \<Gamma> v t \<Longrightarrow> value_has_ty \<Gamma> v t"
  and check_list_imp_vty:
      "check_value_has_ty_list \<Gamma> vs t
         \<Longrightarrow> (\<forall>v \<in> set vs. value_has_ty \<Gamma> v t)"
  by (induction rule: check_value_has_ty_check_value_has_ty_list.induct)
     (auto intro: vt_bool vt_int vt_enum vt_entity vt_set vt_entity_with
           split: option.splits)

lemma vty_imp_check_value_has_ty:
  "value_has_ty \<Gamma> v t \<Longrightarrow> check_value_has_ty \<Gamma> v t"
proof (induction \<Gamma> v t rule: value_has_ty.induct)
  case (vt_set vs \<Gamma> t)
  thus ?case by (simp add: check_value_has_ty_list_all)
qed auto

lemma check_value_has_ty_iff:
  "check_value_has_ty \<Gamma> v t \<longleftrightarrow> value_has_ty \<Gamma> v t"
  using check_imp_vty vty_imp_check_value_has_ty by blast

text \<open>Phase H2b (schema typing). A partial translation from
  spec-side \<open>type_expr\<close> (the declared form on fields / state
  scalars / relation positions) to the value-side \<open>ty\<close> ADT used
  by \<open>value_has_ty\<close>. Partial: \<open>RelationT\<close> has no direct \<open>ty\<close>
  counterpart (relations are kept structurally separate in this
  fragment); field declarations referring to it map to \<open>None\<close>
  and the field is not typeable via the H3 chain.\<close>

fun typeExprToTy :: "type_expr \<Rightarrow> ty option" where
  "typeExprToTy BoolT           = Some TBool"
| "typeExprToTy IntT            = Some TInt"
| "typeExprToTy (EnumT n)       = Some (TEnum n)"
| "typeExprToTy (EntityT n)     = Some (TEntity n)"
| "typeExprToTy (RelationT _ _) = None"

text \<open>The spec-level analogue of \<open>peel_relation_ref :: expr \<Rightarrow>
  String.literal option\<close>, lifted to \<open>expr_full\<close>. Recognises the
  three syntactic shapes \<open>rel_ref_shape\<close> accepts (bare ident,
  pre-ident, prime-ident) and extracts the relation name. Used
  by \<open>T_Index\<close> to bind \<open>rel_name\<close> from the base subterm.

  \<open>typeExprFullToTy\<close> hoisted up to the \<open>value_has_ty\<close> block since
  \<open>schemaFieldType\<close> (referenced by \<open>vt_entity_with\<close>) depends on it.\<close>

fun identNameFull :: "expr_full \<Rightarrow> String.literal option" where
  "identNameFull (IdentifierF rel _) = Some rel"
| "identNameFull _ = None"

fun peelRelationRefFull :: "expr_full \<Rightarrow> String.literal option" where
  "peelRelationRefFull (IdentifierF rel _) = Some rel"
| "peelRelationRefFull (PreF b _)          = identNameFull b"
| "peelRelationRefFull (PrimeF b _)        = identNameFull b"
| "peelRelationRefFull _                   = None"

lemma eval_arith_preservation:
  assumes "eval_arith op x y = Some v"
      and "\<And>a. x = Some a \<Longrightarrow> value_has_ty \<Gamma> a t1"
      and "\<And>b. y = Some b \<Longrightarrow> value_has_ty \<Gamma> b t2"
      and "numeric_ty t1" and "numeric_ty t2"
  shows "value_has_ty \<Gamma> v (numeric_join t1 t2)"
  using assms
  by (cases op;
      auto split: option.splits ir_value.splits if_splits
           simp: numeric_ty_def numeric_join_def intro: vt_int vt_real)

lemma eval_cmp_preservation:
  assumes "eval_cmp op x y = Some v"
  shows "value_has_ty \<Gamma> v TBool"
  using assms
  by (cases op;
      auto split: option.splits ir_value.splits if_splits intro: vt_bool)

text \<open>Phase H2 (start) - typing context and environment agreement.
  The H2 phase introduces a typing context \<open>tyenv\<close> for lexical
  binders and the \<open>env_agrees\<close> predicate witnessing that the
  runtime environment satisfies the context. This is the lexical
  half of the typing context; state-schema typing (relations /
  entity fields) is the next sub-phase. Together they will support
  the typing relation \<open>Gamma |- e : t\<close> over \<open>expr_full\<close> and the
  rule-induction type-safety theorem.\<close>

definition tyenv_lookup :: "tyenv \<Rightarrow> String.literal \<Rightarrow> ty option" where
  "tyenv_lookup G x = map_of G x"

definition env_agrees :: "tyctx \<Rightarrow> env \<Rightarrow> tyenv \<Rightarrow> bool" where
  "env_agrees \<Gamma> env G =
     (\<forall>x t. tyenv_lookup G x = Some t \<longrightarrow>
            (\<exists>v. map_of env x = Some v \<and> value_has_ty \<Gamma> v t))"

lemma env_agrees_lookup:
  assumes "env_agrees \<Gamma> env G" and "tyenv_lookup G x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> value_has_ty \<Gamma> v t"
  using assms by (simp add: env_agrees_def)

lemma env_agrees_empty [simp]: "env_agrees \<Gamma> env []"
  by (simp add: env_agrees_def tyenv_lookup_def)

lemma env_agrees_cons:
  assumes "env_agrees \<Gamma> env G" and "value_has_ty \<Gamma> v t"
  shows "env_agrees \<Gamma> ((x, v) # env) ((x, t) # G)"
  using assms
  by (auto simp: env_agrees_def tyenv_lookup_def split: if_splits)

text \<open>Phase H2 (state schema). The companion to \<open>env_agrees\<close>:
  \<open>state_schema\<close> assigns types to state-resident scalars, and
  \<open>state_agrees_scalars\<close> witnesses that the runtime \<open>state\<close>
  satisfies that typing. The false naive \<open>free_vars \<subseteq> dom env\<close>
  scope lemma proved earlier showed Ident must read from state
  too - this is the typed half of that reading. Relation /
  entity-field schema typing extends this record in subsequent
  sub-phases.\<close>

definition state_agrees_scalars :: "tyctx \<Rightarrow> state \<Rightarrow> state_schema \<Rightarrow> bool" where
  "state_agrees_scalars \<Gamma> st sch =
     (\<forall>x t. map_of (ss_scalars sch) x = Some t \<longrightarrow>
            (\<exists>v. state_lookup_scalar st x = Some v \<and> value_has_ty \<Gamma> v t))"

lemma state_agrees_scalars_lookup:
  assumes "state_agrees_scalars \<Gamma> st sch"
      and "map_of (ss_scalars sch) x = Some t"
  shows "\<exists>v. state_lookup_scalar st x = Some v \<and> value_has_ty \<Gamma> v t"
  using assms by (simp add: state_agrees_scalars_def)

lemma state_agrees_scalars_empty [simp]:
  "state_agrees_scalars \<Gamma> st \<lparr> ss_scalars = [] \<rparr>"
  by (simp add: state_agrees_scalars_def)

text \<open>The state-typing semantic invariant: any value typed at
  \<open>TEntity ename\<close>, when accessed via \<open>value_field_lookup\<close> for a
  field declared in the entity's schema, yields a value of the
  declared type. Universally quantified over values (covering
  \<open>VEntity\<close> records keyed by id in state and \<open>VEntityWith\<close>
  overrides); this is a precondition that callers of the H3
  umbrella supply.\<close>

definition entity_field_well_typed ::
  "tyctx \<Rightarrow> state \<Rightarrow> bool" where
  "entity_field_well_typed \<Gamma> st \<longleftrightarrow>
     (\<forall>v ename fname ft fv.
        value_has_ty \<Gamma> v (TEntity ename)
        \<and> schemaFieldType \<Gamma> ename fname = Some ft
        \<and> value_field_lookup st v fname = Some fv
        \<longrightarrow> value_has_ty \<Gamma> fv ft)"

lemma entity_field_well_typed_lookup:
  assumes "entity_field_well_typed \<Gamma> st"
      and "value_has_ty \<Gamma> vb (TEntity ename)"
      and "schemaFieldType \<Gamma> ename fname = Some ft"
      and "value_field_lookup st vb fname = Some fv"
  shows "value_has_ty \<Gamma> fv ft"
  using assms by (auto simp: entity_field_well_typed_def)

text \<open>Relation-schema lookup + state-typing invariant. Mirrors
  the entity-field design at one level up: \<open>tc_relations\<close>
  declares state fields whose type is a \<open>RelationTypeF\<close>; the
  value-side type (the result of an indexed lookup) is what
  \<open>T_Index\<close> reads. \<open>relation_value_well_typed\<close> is the
  universally-quantified semantic precondition that any value
  retrieved via \<open>state_lookup_key\<close> matches the declared value
  type.\<close>

definition schemaRelationValueType ::
  "tyctx \<Rightarrow> String.literal \<Rightarrow> ty option" where
  "schemaRelationValueType \<Gamma> rel_name \<equiv>
     case List.find (\<lambda>sf. state_fieldNameFull sf = rel_name)
                    (tc_relations \<Gamma>) of
       None    \<Rightarrow> None
     | Some sf \<Rightarrow>
         (case state_fieldTypeFull sf of
            RelationTypeF _ _ v _ \<Rightarrow>
              typeExprFullToTy
                (tc_enums \<Gamma>)
                (map entityNameFull (tc_entities \<Gamma>))
                v
          | _ \<Rightarrow> None)"

definition relation_value_well_typed ::
  "tyctx \<Rightarrow> state \<Rightarrow> bool" where
  "relation_value_well_typed \<Gamma> st \<longleftrightarrow>
     (\<forall>rel_name tv k v.
        schemaRelationValueType \<Gamma> rel_name = Some tv
        \<and> state_lookup_key st rel_name k = Some v
        \<longrightarrow> value_has_ty \<Gamma> v tv)"

lemma relation_value_well_typed_lookup:
  assumes "relation_value_well_typed \<Gamma> st"
      and "schemaRelationValueType \<Gamma> rel_name = Some tv"
      and "state_lookup_key st rel_name k = Some v"
  shows "value_has_ty \<Gamma> v tv"
  using assms by (auto simp: relation_value_well_typed_def)

text \<open>The schema-side predicates depend only on \<open>tc_entities\<close>,
  \<open>tc_relations\<close>, and \<open>tc_enums\<close> — not on \<open>tc_env\<close> — so they're
  invariant under \<open>tc_env\<close>-updates. These [simp] rules let the
  \<open>T_Let\<close> cons step close automatically.
  (\<open>schemaFieldType_tc_env_update\<close> is hoisted up beside
  \<open>value_has_ty_tc_env_update\<close>, since \<open>vt_entity_with\<close>'s
  override-typing premise consults it.)\<close>

lemma schemaRelationValueType_tc_env_update [simp]:
  "schemaRelationValueType (\<Gamma>\<lparr>tc_env := xs\<rparr>) rel_name
     = schemaRelationValueType \<Gamma> rel_name"
  by (auto simp: schemaRelationValueType_def
           split: option.splits type_expr_full.splits)

lemma entity_field_well_typed_tc_env_update [simp]:
  "entity_field_well_typed (\<Gamma>\<lparr>tc_env := xs\<rparr>) st
     = entity_field_well_typed \<Gamma> st"
  by (simp add: entity_field_well_typed_def)

lemma relation_value_well_typed_tc_env_update [simp]:
  "relation_value_well_typed (\<Gamma>\<lparr>tc_env := xs\<rparr>) st
     = relation_value_well_typed \<Gamma> st"
  by (simp add: relation_value_well_typed_def)

text \<open>Companion invariance lemmas for the \<open>agrees\<close>-side
  predicates: the lexical / scalar agreement bodies reference
  \<open>value_has_ty \<Gamma> v t\<close> as their only \<open>\<Gamma>\<close>-dependent piece, and
  \<open>value_has_ty_tc_env_update\<close> handles that, so the wrapper
  predicates are also invariant. \<open>env_agrees_strict\<close>'s lemma is
  declared after its definition further below.\<close>

lemma env_agrees_tc_env_update [simp]:
  "env_agrees (\<Gamma>\<lparr>tc_env := xs\<rparr>) env G = env_agrees \<Gamma> env G"
  by (simp add: env_agrees_def)

lemma state_agrees_scalars_tc_env_update [simp]:
  "state_agrees_scalars (\<Gamma>\<lparr>tc_env := xs\<rparr>) st sch
     = state_agrees_scalars \<Gamma> st sch"
  by (simp add: state_agrees_scalars_def)

definition agrees :: "env \<Rightarrow> state \<Rightarrow> tyctx \<Rightarrow> bool" where
  "agrees env st \<Gamma> \<longleftrightarrow>
     env_agrees \<Gamma> env (tc_env \<Gamma>) \<and> state_agrees_scalars \<Gamma> st (tc_schema \<Gamma>)"

lemma agrees_env_lookup:
  assumes "agrees env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> value_has_ty \<Gamma> v t"
  using assms by (auto simp: agrees_def dest: env_agrees_lookup)

lemma agrees_state_lookup:
  assumes "agrees env st \<Gamma>"
      and "map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t"
  shows "\<exists>v. state_lookup_scalar st x = Some v \<and> value_has_ty \<Gamma> v t"
  using assms by (auto simp: agrees_def dest: state_agrees_scalars_lookup)

text \<open>Strict env agreement: every \<open>env\<close>-binding is also typed in \<open>tyenv\<close>
  (no untyped runtime bindings shadowing state-resident scalars). Needed
  for the preservation argument at \<open>T_Ident_State\<close>: the typing rule fires
  when \<open>x\<close> is NOT in \<open>tyenv\<close>, and strict agreement guarantees that means
  \<open>env_lookup x = None\<close> so eval's Ident arm falls through to the state.\<close>

definition env_agrees_strict :: "tyctx \<Rightarrow> env \<Rightarrow> tyenv \<Rightarrow> bool" where
  "env_agrees_strict \<Gamma> env G \<longleftrightarrow>
     env_agrees \<Gamma> env G \<and>
     (\<forall>x v. map_of env x = Some v \<longrightarrow> (\<exists>t. tyenv_lookup G x = Some t))"

lemma env_agrees_strict_tc_env_update [simp]:
  "env_agrees_strict (\<Gamma>\<lparr>tc_env := xs\<rparr>) env G = env_agrees_strict \<Gamma> env G"
  by (simp add: env_agrees_strict_def)

definition agrees_strict :: "env \<Rightarrow> state \<Rightarrow> tyctx \<Rightarrow> bool" where
  "agrees_strict env st \<Gamma> \<longleftrightarrow>
     env_agrees_strict \<Gamma> env (tc_env \<Gamma>) \<and>
     state_agrees_scalars \<Gamma> st (tc_schema \<Gamma>) \<and>
     entity_field_well_typed \<Gamma> st \<and>
     relation_value_well_typed \<Gamma> st"

lemma agrees_strict_imp_agrees:
  "agrees_strict env st \<Gamma> \<Longrightarrow> agrees env st \<Gamma>"
  by (auto simp: agrees_strict_def agrees_def env_agrees_strict_def)

lemma env_agrees_strict_lookup_none:
  assumes "env_agrees_strict \<Gamma> env G" and "tyenv_lookup G x = None"
  shows "map_of env x = None"
  using assms by (auto simp: env_agrees_strict_def)

lemma env_agrees_strict_empty [simp]:
  "env_agrees_strict \<Gamma> [] []"
  by (simp add: env_agrees_strict_def)

lemma agrees_strict_env_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> value_has_ty \<Gamma> v t"
  using assms agrees_strict_imp_agrees agrees_env_lookup by blast

lemma agrees_strict_state_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = None"
      and "map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t"
  shows "map_of env x = None
         \<and> (\<exists>v. state_lookup_scalar st x = Some v \<and> value_has_ty \<Gamma> v t)"
proof -
  have es: "env_agrees_strict \<Gamma> env (tc_env \<Gamma>)"
    using assms(1) by (simp add: agrees_strict_def)
  have ss: "state_agrees_scalars \<Gamma> st (tc_schema \<Gamma>)"
    using assms(1) by (simp add: agrees_strict_def)
  have "map_of env x = None"
    by (rule env_agrees_strict_lookup_none[OF es assms(2)])
  moreover have "\<exists>v. state_lookup_scalar st x = Some v \<and> value_has_ty \<Gamma> v t"
    by (rule state_agrees_scalars_lookup[OF ss assms(3)])
  ultimately show ?thesis ..
qed

lemma env_agrees_strict_cons:
  assumes "env_agrees_strict \<Gamma> env G" and "value_has_ty \<Gamma> v t"
  shows "env_agrees_strict \<Gamma> ((x, v) # env) ((x, t) # G)"
  using assms
  by (auto simp: env_agrees_strict_def env_agrees_def tyenv_lookup_def
           split: if_splits)

lemma agrees_strict_cons:
  assumes "agrees_strict env st \<Gamma>" and "value_has_ty \<Gamma> v t"
  shows "agrees_strict ((x, v) # env) st
           (\<Gamma>\<lparr>tc_env := (x, t) # tc_env \<Gamma>\<rparr>)"
  using assms env_agrees_strict_cons by (auto simp: agrees_strict_def)

lemma agrees_strict_field_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "value_has_ty \<Gamma> vb (TEntity ename)"
      and "schemaFieldType \<Gamma> ename fname = Some ft"
      and "value_field_lookup st vb fname = Some fv"
  shows "value_has_ty \<Gamma> fv ft"
  using assms entity_field_well_typed_lookup
  by (auto simp: agrees_strict_def)

lemma agrees_strict_relation_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "schemaRelationValueType \<Gamma> rel_name = Some tv"
      and "state_lookup_key st rel_name k = Some v"
  shows "value_has_ty \<Gamma> v tv"
  using assms relation_value_well_typed_lookup
  by (auto simp: agrees_strict_def)

text \<open>Phase H3 init-friendliness. \<open>tyctxEmpty\<close> is the smallest
  possible typing context; \<open>agrees_strict_empty\<close> establishes
  that this trivial context is satisfied by the empty environment
  and empty state. Useful as the base for callers establishing
  \<open>agrees_strict\<close> from scratch (e.g., when verifying an empty-
  initial-state spec).\<close>

definition tyctxEmpty :: tyctx where
  "tyctxEmpty \<equiv>
     \<lparr> tc_env = [],
       tc_schema = \<lparr> ss_scalars = [] \<rparr>,
       tc_entities = [],
       tc_relations = [],
       tc_enums = [] \<rparr>"

text \<open>\<open>tyctxFromService\<close> bootstraps the schema-typed half of a
  \<open>tyctx\<close> directly from a parsed \<open>ServiceIRFull\<close>. Scala consumers
  (Z3 counterexample decoder, runtime type-check assertions) call this
  so the construction lives in proofs, not duplicated per call-site.
  Lexical env + scalar schema stay empty here \<mdash> they're for the H3
  preservation case for binders, set by the verifier at use.\<close>

fun serviceEntities :: "service_ir_full \<Rightarrow> entity_decl_full list" where
  "serviceEntities (ServiceIRFull _ _ es _ _ _ _ _ _ _ _ _ _ _ _) = es"

fun serviceEnums :: "service_ir_full \<Rightarrow> enum_decl_full list" where
  "serviceEnums (ServiceIRFull _ _ _ en _ _ _ _ _ _ _ _ _ _ _) = en"

fun serviceStateFields ::
  "service_ir_full \<Rightarrow> state_field_decl_full list" where
  "serviceStateFields (ServiceIRFull _ _ _ _ _ st _ _ _ _ _ _ _ _ _) =
     (case st of None \<Rightarrow> []
               | Some (StateDeclFull fs _) \<Rightarrow> fs)"

definition tyctxFromService :: "service_ir_full \<Rightarrow> tyctx" where
  "tyctxFromService ir \<equiv> tyctxEmpty
     \<lparr> tc_entities := serviceEntities ir,
       tc_enums    := map enumNameFull (serviceEnums ir),
       tc_relations := serviceStateFields ir \<rparr>"

lemma schemaFieldType_no_entities [simp]:
  "tc_entities \<Gamma> = [] \<Longrightarrow> schemaFieldType \<Gamma> ename fname = None"
  by (simp add: schemaFieldType_def)

lemma schemaRelationValueType_no_relations [simp]:
  "tc_relations \<Gamma> = [] \<Longrightarrow> schemaRelationValueType \<Gamma> rel_name = None"
  by (simp add: schemaRelationValueType_def)

lemma entity_field_well_typed_no_entities [simp]:
  "tc_entities \<Gamma> = [] \<Longrightarrow> entity_field_well_typed \<Gamma> st"
  by (simp add: entity_field_well_typed_def)

lemma relation_value_well_typed_no_relations [simp]:
  "tc_relations \<Gamma> = [] \<Longrightarrow> relation_value_well_typed \<Gamma> st"
  by (simp add: relation_value_well_typed_def)

lemma agrees_strict_empty:
  "agrees_strict [] state_empty tyctxEmpty"
  by (simp add: agrees_strict_def tyctxEmpty_def)

text \<open>Phase H2 (typing relation, arith fragment). The H2 design
  centrepiece: an inductive typing judgement \<open>expr_has_ty \<Gamma> e t\<close>
  over \<open>expr_full\<close>, scoped to the arith/cmp/bool fragment whose
  per-arm preservation is already proven in H1
  (\<open>eval_arith_preservation\<close>, \<open>eval_cmp_preservation\<close>). Two
  \<open>IdentifierF\<close> rules - lexical-first then state-fallback - encode
  eval's two-step Ident resolution and align with the
  \<open>env_agrees\<close> / \<open>state_agrees_scalars\<close> agreement halves so the
  H3 progress theorem dispatches per-arm to H1.\<close>

inductive expr_has_ty :: "tyctx \<Rightarrow> expr_full \<Rightarrow> ty \<Rightarrow> bool" where
  T_BoolLit:
    "expr_has_ty \<Gamma> (BoolLitF b sp) TBool"
| T_IntLit:
    "expr_has_ty \<Gamma> (IntLitF n sp) TInt"
| T_FloatLit:
    "decimalToRat s \<noteq> None
       \<Longrightarrow> expr_has_ty \<Gamma> (FloatLitF s sp) TReal"
| T_Ident_Lex:
    "tyenv_lookup (tc_env \<Gamma>) x = Some t
       \<Longrightarrow> expr_has_ty \<Gamma> (IdentifierF x sp) t"
| T_Ident_State:
    "tyenv_lookup (tc_env \<Gamma>) x = None
       \<Longrightarrow> map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t
       \<Longrightarrow> expr_has_ty \<Gamma> (IdentifierF x sp) t"
| T_Arith:
    "expr_has_ty \<Gamma> l t1
       \<Longrightarrow> expr_has_ty \<Gamma> r t2
       \<Longrightarrow> numeric_ty t1
       \<Longrightarrow> numeric_ty t2
       \<Longrightarrow> op \<in> {BAdd, BSub, BMul, BDiv}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) (numeric_join t1 t2)"
| T_Cmp_Eq:
    "expr_has_ty \<Gamma> l t1
       \<Longrightarrow> expr_has_ty \<Gamma> r t2
       \<Longrightarrow> t1 = t2 \<or> numeric_ty t1 \<and> numeric_ty t2
       \<Longrightarrow> op \<in> {BEq, BNeq}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) TBool"
| T_Cmp_Ord:
    "expr_has_ty \<Gamma> l t1
       \<Longrightarrow> expr_has_ty \<Gamma> r t2
       \<Longrightarrow> numeric_ty t1
       \<Longrightarrow> numeric_ty t2
       \<Longrightarrow> op \<in> {BLt, BLe, BGt, BGe}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) TBool"
| T_Bool_Bin:
    "expr_has_ty \<Gamma> l TBool
       \<Longrightarrow> expr_has_ty \<Gamma> r TBool
       \<Longrightarrow> op \<in> {BAnd, BOr, BImplies, BIff}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) TBool"
| T_Not:
    "expr_has_ty \<Gamma> e TBool
       \<Longrightarrow> expr_has_ty \<Gamma> (UnaryOpF UNot e sp) TBool"
| T_Neg:
    "expr_has_ty \<Gamma> e t
       \<Longrightarrow> numeric_ty t
       \<Longrightarrow> expr_has_ty \<Gamma> (UnaryOpF UNegate e sp) t"
| T_Let:
    "expr_has_ty \<Gamma> v t1
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (x, t1) # tc_env \<Gamma>\<rparr>) body t2
       \<Longrightarrow> expr_has_ty \<Gamma> (LetF x v body sp) t2"
| T_Prime:
    "expr_has_ty \<Gamma> e t
       \<Longrightarrow> expr_has_ty \<Gamma> (PrimeF e sp) t"
| T_Pre:
    "expr_has_ty \<Gamma> e t
       \<Longrightarrow> expr_has_ty \<Gamma> (PreF e sp) t"
| T_EnumAccess:
    "expr_has_ty \<Gamma> (EnumAccessF (IdentifierF en sp1) mem sp) (TEnum en)"
| T_Card:
    "expr_has_ty \<Gamma>
       (UnaryOpF UCardinality (IdentifierF x sp1) sp) TInt"
| T_BIn_Rel:
    "expr_has_ty \<Gamma> l t
       \<Longrightarrow> expr_has_ty \<Gamma>
             (BinaryOpF BIn l (IdentifierF rel sp1) sp) TBool"
| T_BNotIn_Rel:
    "expr_has_ty \<Gamma> l t
       \<Longrightarrow> expr_has_ty \<Gamma>
             (BinaryOpF BNotIn l (IdentifierF rel sp1) sp) TBool"
| T_SetLit_Empty:
    "expr_has_ty \<Gamma> (SetLiteralF [] sp) (TSet t)"
| T_SetLit_Cons:
    "expr_has_ty \<Gamma> e t
       \<Longrightarrow> expr_has_ty \<Gamma> (SetLiteralF rest sp) (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> (SetLiteralF (e # rest) sp) (TSet t)"
| T_BUnion:
    "expr_has_ty \<Gamma> l (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> r (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF BUnion l r sp) (TSet t)"
| T_BIntersect:
    "expr_has_ty \<Gamma> l (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> r (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF BIntersect l r sp) (TSet t)"
| T_BDiff:
    "expr_has_ty \<Gamma> l (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> r (TSet t)
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF BDiff l r sp) (TSet t)"
| T_BIn_Set:
    "expr_has_ty \<Gamma> l t
       \<Longrightarrow> expr_has_ty \<Gamma> r (TSet t)
       \<Longrightarrow> (\<forall>rel s. r \<noteq> IdentifierF rel s)
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF BIn l r sp) TBool"
| T_BNotIn_Set:
    "expr_has_ty \<Gamma> l t
       \<Longrightarrow> expr_has_ty \<Gamma> r (TSet t)
       \<Longrightarrow> (\<forall>rel s. r \<noteq> IdentifierF rel s)
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF BNotIn l r sp) TBool"
| T_FieldAccess:
    "expr_has_ty \<Gamma> base (TEntity ename)
       \<Longrightarrow> schemaFieldType \<Gamma> ename fname = Some ft
       \<Longrightarrow> expr_has_ty \<Gamma> (FieldAccessF base fname sp) ft"
| T_Index:
    "peelRelationRefFull base = Some rel_name
       \<Longrightarrow> expr_has_ty \<Gamma> key tk
       \<Longrightarrow> schemaRelationValueType \<Gamma> rel_name = Some tv
       \<Longrightarrow> expr_has_ty \<Gamma> (IndexF base key sp) tv"
| T_With:
    "expr_has_ty \<Gamma> base (TEntity ename)
       \<Longrightarrow> (\<forall>fld v sp'. FieldAssignFull fld v sp' \<in> set updates
            \<longrightarrow> (\<exists>ft. schemaFieldType \<Gamma> ename fld = Some ft
                  \<and> expr_has_ty \<Gamma> v ft))
       \<Longrightarrow> expr_has_ty \<Gamma> (WithF base updates sp) (TEntity ename)"
| T_Forall_QAll_Enum:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QAll
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QAll_Rel:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>) body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QAll
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QAll_Enum_Cons:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QAll (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QAll
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QAll_Rel_Cons:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QAll (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QAll
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QNo_Enum:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QNo
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QNo_Rel:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>) body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QNo
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QNo_Enum_Cons:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QNo (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QNo
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QNo_Rel_Cons:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QNo (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QNo
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QExists_Enum:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QExists
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QExists_Rel:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>) body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QExists
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QExists_Enum_Cons:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QExists (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QExists
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QExists_Rel_Cons:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QExists (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QExists
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QSome_Enum:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QSome
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QSome_Rel:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>) body TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QSome
                [QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b]
                body sp) TBool"
| T_Forall_QSome_Enum_Cons:
    "dnm \<in> set (tc_enums \<Gamma>)
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, TEnum dnm) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QSome (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QSome
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"
| T_Forall_QSome_Rel_Cons:
    "dnm \<notin> set (tc_enums \<Gamma>)
       \<Longrightarrow> schemaRelationValueType \<Gamma> dnm = Some tv
       \<Longrightarrow> expr_has_ty (\<Gamma>\<lparr>tc_env := (var, tv) # tc_env \<Gamma>\<rparr>)
                       (QuantifierF QSome (b2 # rest_bs) body sp) TBool
       \<Longrightarrow> expr_has_ty \<Gamma>
             (QuantifierF QSome
                (QuantifierBindingFull var (IdentifierF dnm sp_id) m sp_b
                  # b2 # rest_bs)
                body sp) TBool"

lemmas expr_has_ty_intros [intro] =
  T_BoolLit T_IntLit T_FloatLit T_Ident_Lex T_Ident_State
  T_Arith T_Cmp_Eq T_Cmp_Ord T_Bool_Bin T_Not T_Neg T_Let
  T_Prime T_Pre T_EnumAccess T_Card T_BIn_Rel T_BNotIn_Rel
  T_SetLit_Empty T_SetLit_Cons T_BUnion T_BIntersect T_BDiff
  T_BIn_Set T_BNotIn_Set T_FieldAccess T_Index T_With
  T_Forall_QAll_Enum T_Forall_QAll_Rel
  T_Forall_QAll_Enum_Cons T_Forall_QAll_Rel_Cons
  T_Forall_QNo_Enum T_Forall_QNo_Rel
  T_Forall_QNo_Enum_Cons T_Forall_QNo_Rel_Cons
  T_Forall_QExists_Enum T_Forall_QExists_Rel
  T_Forall_QExists_Enum_Cons T_Forall_QExists_Rel_Cons
  T_Forall_QSome_Enum T_Forall_QSome_Rel
  T_Forall_QSome_Enum_Cons T_Forall_QSome_Rel_Cons

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

lemma set_dedupe_values_subset:
  shows "set (dedupe_values vs) \<subseteq> set vs"
proof (induction vs)
  case Nil thus ?case by simp
next
  case (Cons v vs)
  have "set (dedupe_values (v # vs)) \<subseteq> insert v (set (dedupe_values vs))"
    by (auto simp: Let_def)
  thus ?case using Cons.IH by auto
qed

lemma dedupe_values_preserves_value_ty:
  assumes "\<forall>v \<in> set vs. value_has_ty \<Gamma> v t"
  shows "\<forall>v \<in> set (dedupe_values vs). value_has_ty \<Gamma> v t"
  using assms set_dedupe_values_subset by blast

definition set_union_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_union_values l r \<equiv> dedupe_values (l @ r)"

definition set_intersect_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_intersect_values l r \<equiv> dedupe_values (filter (\<lambda>v. contains_value r v) l)"

definition set_diff_values :: "ir_value list \<Rightarrow> ir_value list \<Rightarrow> ir_value list" where
  "set_diff_values l r \<equiv> dedupe_values (filter (\<lambda>v. \<not> contains_value r v) l)"

lemma set_union_values_preserves_value_ty:
  assumes "\<forall>v \<in> set l. value_has_ty \<Gamma> v t" and "\<forall>v \<in> set r. value_has_ty \<Gamma> v t"
  shows "\<forall>v \<in> set (set_union_values l r). value_has_ty \<Gamma> v t"
  using assms set_dedupe_values_subset[of "l @ r"]
  unfolding set_union_values_def
  by auto

lemma set_intersect_values_preserves_value_ty:
  assumes "\<forall>v \<in> set l. value_has_ty \<Gamma> v t"
  shows "\<forall>v \<in> set (set_intersect_values l r). value_has_ty \<Gamma> v t"
  using assms set_dedupe_values_subset[of "filter (\<lambda>v. contains_value r v) l"]
  unfolding set_intersect_values_def
  by auto

lemma set_diff_values_preserves_value_ty:
  assumes "\<forall>v \<in> set l. value_has_ty \<Gamma> v t"
  shows "\<forall>v \<in> set (set_diff_values l r). value_has_ty \<Gamma> v t"
  using assms set_dedupe_values_subset[of "filter (\<lambda>v. \<not> contains_value r v) l"]
  unfolding set_diff_values_def
  by auto

fun eval_set_bin ::
  "set_op \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_set_bin UnionOp     (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_union_values l r))"
| "eval_set_bin IntersectOp (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_intersect_values l r))"
| "eval_set_bin DiffOp      (Some (VSet l)) (Some (VSet r)) = Some (VSet (set_diff_values l r))"
| "eval_set_bin _ _ _ = None"

lemma eval_set_bin_some_imp_set:
  "eval_set_bin op x y = Some v
     \<Longrightarrow> (\<exists>l r. x = Some (VSet l) \<and> y = Some (VSet r))"
  by (induction op x y rule: eval_set_bin.induct) auto

function (sequential) eval :: "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_forall_enum ::
  "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> String.literal
   \<Rightarrow> String.literal list \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_forall_rel ::
  "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> ir_value list \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_the_rel ::
  "schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> ir_value list \<Rightarrow> expr \<Rightarrow> ir_value list option"
where
  "eval s st env (BoolLit b _) = Some (VBool b)"
| "eval s st env (IntLit n _) = Some (VInt n)"
| "eval s st env (RealLit r _) = Some (VReal r)"
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
      | Some (VReal r) \<Rightarrow> Some (VReal (- r))
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
| "eval s st env (ForallSet var setE body _) =
     (case eval s st env setE of
        Some (VSet elems) \<Rightarrow> eval_forall_rel s st env var elems body
      | _ \<Rightarrow> None)"
| "eval s st env (TheRel var rel_name body _) =
     (case state_relation_domain st rel_name of
        Some rel_dom \<Rightarrow>
          (case eval_the_rel s st env var rel_dom body of
             Some (x # rest) \<Rightarrow> (if list_all (\<lambda>y. y = x) rest then Some x else None)
           | _               \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "eval s st env (EntityBase name _) = Some (VEntity name (STR ''''))"
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
| "eval s st env (Ite c a b _) =
     (case eval s st env c of
        Some (VBool True)  \<Rightarrow> eval s st env a
      | Some (VBool False) \<Rightarrow> eval s st env b
      | _ \<Rightarrow> None)"
| "eval s st env (NoneE _)   = Some VNone"
| "eval s st env (SomeE e _) = map_option VSome (eval s st env e)"
| "eval s st env (StrLit v _) = Some (VStr v)"
| "eval s st env (Matches e pat _) =
     (case eval s st env e of
        Some (VStr str) \<Rightarrow> Some (VBool (string_matches str pat))
      | _ \<Rightarrow> None)"
| "eval s st env (SeqEmpty _) = Some (VSeq [])"
| "eval s st env (SeqCons e rest _) =
     (case (eval s st env e, eval s st env rest) of
        (Some v, Some (VSeq vs)) \<Rightarrow> Some (VSeq (v # vs))
      | _ \<Rightarrow> None)"
| "eval s st env (MapEmpty _) = Some (VMap [])"
| "eval s st env (MapCons k v rest _) =
     (case (eval s st env k, eval s st env v, eval s st env rest) of
        (Some kv, Some vv, Some (VMap ps)) \<Rightarrow> Some (VMap ((kv, vv) # ps))
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
| "eval_the_rel s st env var [] body = Some []"
| "eval_the_rel s st env var (v # rest) body =
     (case eval s st ((var, v) # env) body of
        Some (VBool b) \<Rightarrow>
          (case eval_the_rel s st env var rest body of
             Some matches \<Rightarrow> Some (if b then v # matches else matches)
           | None         \<Rightarrow> None)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (Inl (_, _, _, e)) \<Rightarrow> size e
             | Inl (Inr (_, _, _, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inl (_, _, _, _, _, body)) \<Rightarrow> size body
             | Inr (Inr (_, _, _, _, _, body)) \<Rightarrow> size body),
        (\<lambda>p. case p of
               Inl (Inl _) \<Rightarrow> 0
             | Inl (Inr (_, _, _, _, _, members, _)) \<Rightarrow> Suc (length members)
             | Inr (Inl (_, _, _, _, rel_dom, _)) \<Rightarrow> Suc (length rel_dom)
             | Inr (Inr (_, _, _, _, rel_dom, _)) \<Rightarrow> Suc (length rel_dom))
       ]")
     auto

text \<open>Phase H3d helpers (quantifier evaluation). \<open>eval_forall_enum\<close>
  and \<open>eval_forall_rel\<close> always produce \<open>VBool\<close>-shaped results when
  defined: the body's evaluation is gated through \<open>(VBool b) #
  pattern\<close>, the empty case returns \<open>VBool True\<close>, and the cons-step
  conjoins through \<open>VBool acc\<close>. These extractor lemmas are what
  the \<open>T_Forall_QAll_Enum\<close> / \<open>T_Forall_QAll_Rel\<close> cases (and the
  analogous \<open>QNo\<close>/\<open>QExists\<close>/\<open>QSome\<close> variants) use to close on
  \<open>vt_bool\<close>.\<close>

lemma eval_forall_enum_some_imp_bool:
  "eval_forall_enum sch st env var en members body = Some v
     \<Longrightarrow> \<exists>b. v = VBool b"
  by (induction members arbitrary: v)
     (auto split: option.splits ir_value.splits)

lemma eval_forall_rel_some_imp_bool:
  "eval_forall_rel sch st env var rel_dom body = Some v
     \<Longrightarrow> \<exists>b. v = VBool b"
  by (induction rel_dom arbitrary: v)
     (auto split: option.splits ir_value.splits)

fun eval_full_bin :: "bin_op_full \<Rightarrow> ir_value option \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_full_bin BAnd     x y = (case (x, y) of (Some (VBool a), Some (VBool b)) \<Rightarrow> Some (VBool (eval_bool_bin AndOp a b))     | _ \<Rightarrow> None)"
| "eval_full_bin BOr      x y = (case (x, y) of (Some (VBool a), Some (VBool b)) \<Rightarrow> Some (VBool (eval_bool_bin OrOp a b))      | _ \<Rightarrow> None)"
| "eval_full_bin BImplies x y = (case (x, y) of (Some (VBool a), Some (VBool b)) \<Rightarrow> Some (VBool (eval_bool_bin ImpliesOp a b)) | _ \<Rightarrow> None)"
| "eval_full_bin BIff     x y = (case (x, y) of (Some (VBool a), Some (VBool b)) \<Rightarrow> Some (VBool (eval_bool_bin IffOp a b))     | _ \<Rightarrow> None)"
| "eval_full_bin BEq  x y = eval_cmp EqOp x y"
| "eval_full_bin BNeq x y = eval_cmp NeqOp x y"
| "eval_full_bin BLt  x y = eval_cmp LtOp x y"
| "eval_full_bin BGt  x y = eval_cmp GtOp x y"
| "eval_full_bin BLe  x y = eval_cmp LeOp x y"
| "eval_full_bin BGe  x y = eval_cmp GeOp x y"
| "eval_full_bin BAdd x y = eval_arith AddOp x y"
| "eval_full_bin BSub x y = eval_arith SubOp x y"
| "eval_full_bin BMul x y = eval_arith MulOp x y"
| "eval_full_bin BDiv x y = eval_arith DivOp x y"
| "eval_full_bin _ _ _ = None"

fun eval_full_un :: "un_op_full \<Rightarrow> ir_value option \<Rightarrow> ir_value option" where
  "eval_full_un UNot    x = (case x of Some (VBool b) \<Rightarrow> Some (VBool (\<not> b)) | _ \<Rightarrow> None)"
| "eval_full_un UNegate x = (case x of Some (VInt n) \<Rightarrow> Some (VInt (- n)) | Some (VReal r) \<Rightarrow> Some (VReal (- r)) | _ \<Rightarrow> None)"
| "eval_full_un _ _ = None"

definition lookup_callee ::
  "function_decl_full list \<Rightarrow> predicate_decl_full list \<Rightarrow> String.literal
     \<Rightarrow> (String.literal list \<times> expr_full) option" where
  "lookup_callee fs ps nm =
     (case List.find (\<lambda>f. fncName f = nm) fs of
        Some f \<Rightarrow> Some (map prmName (fncParams f), fncBody f)
      | None \<Rightarrow>
          (case List.find (\<lambda>q. prdName q = nm) ps of
             Some pr \<Rightarrow> Some (map prmName (prdParams pr), prdBody pr)
           | None \<Rightarrow> None))"

text \<open>\<open>eval_full\<close> is a reference semantics for the surface IR (\<open>expr_full\<close>): the
  meaning the trusted \<open>lower\<close> and \<open>inline_calls\<close> desugars must preserve. It reuses
  the verified-subset \<open>eval\<close>'s value operations and gives each operator a meaning
  \<^emph>\<open>independent\<close> of \<open>lower\<close>, so agreement with \<open>lower\<close> is a real theorem rather than
  a tautology. A \<open>CallF\<close> evaluates its arguments, binds their values in the
  environment (call by value, hence capture-free), then evaluates the callee body;
  this needs \<^emph>\<open>fuel\<close> because a call unfolds into a body of unrelated size, so
  termination is lexicographic on \<open>(fuel, size)\<close>. Binder and collection forms
  (\<open>QuantifierF\<close>, \<open>SetComprehensionF\<close>, \<open>TheF\<close>, \<open>ConstructorF\<close>, the list literals,
  \<open>WithF\<close>, \<open>LambdaF\<close>) return \<open>None\<close> here pending later coverage: a gap in the
  modelled fragment, not an unsoundness.\<close>

function (sequential) eval_full ::
  "function_decl_full list \<Rightarrow> predicate_decl_full list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr_full \<Rightarrow> ir_value option"
and eval_full_list ::
  "function_decl_full list \<Rightarrow> predicate_decl_full list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr_full list \<Rightarrow> ir_value list option" where
  "eval_full fs ps fuel s st env (IntLitF n _)     = Some (VInt n)"
| "eval_full fs ps fuel s st env (BoolLitF b _)    = Some (VBool b)"
| "eval_full fs ps fuel s st env (StringLitF v _)  = Some (VStr v)"
| "eval_full fs ps fuel s st env (NoneLitF _)      = Some VNone"
| "eval_full fs ps fuel s st env (IdentifierF x _) =
     (case env_lookup env x of Some v \<Rightarrow> Some v | None \<Rightarrow> state_lookup_scalar st x)"
| "eval_full fs ps fuel s st env (BinaryOpF op l r _) =
     eval_full_bin op (eval_full fs ps fuel s st env l) (eval_full fs ps fuel s st env r)"
| "eval_full fs ps fuel s st env (UnaryOpF op e _) =
     eval_full_un op (eval_full fs ps fuel s st env e)"
| "eval_full fs ps fuel s st env (IfF c a b _) =
     (case eval_full fs ps fuel s st env c of
        Some (VBool True)  \<Rightarrow> eval_full fs ps fuel s st env a
      | Some (VBool False) \<Rightarrow> eval_full fs ps fuel s st env b
      | _ \<Rightarrow> None)"
| "eval_full fs ps fuel s st env (LetF x v body _) =
     (case eval_full fs ps fuel s st env v of
        Some va \<Rightarrow> eval_full fs ps fuel s st ((x, va) # env) body
      | None    \<Rightarrow> None)"
| "eval_full fs ps fuel s st env (FieldAccessF base f _) =
     (case eval_full fs ps fuel s st env base of
        Some v \<Rightarrow> value_field_lookup st v f
      | None   \<Rightarrow> None)"
| "eval_full fs ps fuel s st env (PrimeF e _) = eval_full fs ps fuel s st env e"
| "eval_full fs ps fuel s st env (PreF e _)   = eval_full fs ps fuel s st env e"
| "eval_full fs ps fuel s st env (SomeWrapF e _) =
     map_option VSome (eval_full fs ps fuel s st env e)"
| "eval_full fs ps fuel s st env (MatchesF e pat _) =
     (case eval_full fs ps fuel s st env e of
        Some (VStr str) \<Rightarrow> Some (VBool (string_matches str pat))
      | _ \<Rightarrow> None)"
| "eval_full fs ps fuel s st env (CallF callee args _) =
     (case fuel of
        0 \<Rightarrow> None
      | Suc fuel' \<Rightarrow>
          (case callee of
             IdentifierF nm _ \<Rightarrow>
               (case lookup_callee fs ps nm of
                  Some (params, body) \<Rightarrow>
                    (if length params = length args
                       then (case eval_full_list fs ps fuel s st env args of
                               Some vals \<Rightarrow>
                                 eval_full fs ps fuel' s st (zip params vals) body
                             | None \<Rightarrow> None)
                       else None)
                | None \<Rightarrow> None)
           | _ \<Rightarrow> None))"
| "eval_full fs ps fuel s st env _ = None"
| "eval_full_list fs ps fuel s st env [] = Some []"
| "eval_full_list fs ps fuel s st env (e # es) =
     (case eval_full fs ps fuel s st env e of
        Some v \<Rightarrow>
          (case eval_full_list fs ps fuel s st env es of
             Some vs \<Rightarrow> Some (v # vs)
           | None \<Rightarrow> None)
      | None \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>x. case x of Inl (fs, ps, fuel, s, st, env, e) \<Rightarrow> fuel
                     | Inr (fs, ps, fuel, s, st, env, es) \<Rightarrow> fuel),
        (\<lambda>x. case x of Inl (fs, ps, fuel, s, st, env, e) \<Rightarrow> size e
                     | Inr (fs, ps, fuel, s, st, env, es) \<Rightarrow> size_list size es)]")
     auto

lemma string_in_list_append [simp]:
  "string_in_list y (xs @ ys) = (string_in_list y xs \<or> string_in_list y ys)"
  by (induction xs) auto

lemma string_in_list_remove_name [simp]:
  "string_in_list y (remove_name n xs) = (y \<noteq> n \<and> string_in_list y xs)"
  by (induction xs) auto

lemma string_in_list_remove_names [simp]:
  "string_in_list y (remove_names ns xs) = (\<not> string_in_list y ns \<and> string_in_list y xs)"
  by (induction ns) auto

lemma eval_full_list_length:
  "eval_full_list fs ps fuel s st env es = Some vs \<Longrightarrow> length vs = length es"
  by (induction es arbitrary: vs) (auto split: option.splits)

lemma eval_full_coincidence:
  "(\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_full fs ps fuel s st env1 e = eval_full fs ps fuel s st env2 e"
  "(\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_full_list fs ps fuel s st env1 es = eval_full_list fs ps fuel s st env2 es"
proof (induction fs ps fuel s st env1 e and fs ps fuel s st env1 es
        arbitrary: env2 and env2
        rule: eval_full_eval_full_list.induct)
  case (6 fs ps fuel s st env bop l r sp env2)
  have al: "\<forall>y. string_in_list y (free_vars l) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "6.prems" by auto
  have ar: "\<forall>y. string_in_list y (free_vars r) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "6.prems" by auto
  show ?case using "6.IH"(1)[OF al] "6.IH"(2)[OF ar] by simp
next
  case (7 fs ps fuel s st env uop e sp env2)
  have ae: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "7.prems" by auto
  show ?case using "7.IH"[OF ae] by simp
next
  case (8 fs ps fuel s st env c a b sp env2)
  have ac: "\<forall>y. string_in_list y (free_vars c) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "8.prems" by auto
  have aa: "\<forall>y. string_in_list y (free_vars a) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "8.prems" by auto
  have ab: "\<forall>y. string_in_list y (free_vars b) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "8.prems" by auto
  have c_eq: "eval_full fs ps fuel s st env c = eval_full fs ps fuel s st env2 c"
    using "8.IH"(1)[OF ac] .
  show ?case
  proof (cases "eval_full fs ps fuel s st env c")
    case None
    then show ?thesis using c_eq by simp
  next
    case (Some vc)
    show ?thesis
    proof (cases vc)
      case (VBool bb)
      show ?thesis
      proof (cases bb)
        case True
        have cT: "eval_full fs ps fuel s st env c = Some (VBool True)"
          using Some VBool True by simp
        have "eval_full fs ps fuel s st env a = eval_full fs ps fuel s st env2 a"
          using "8.IH"(2)[OF cT refl refl aa] .
        then show ?thesis using cT c_eq by simp
      next
        case False
        have cF: "eval_full fs ps fuel s st env c = Some (VBool False)"
          using Some VBool False by simp
        have "eval_full fs ps fuel s st env b = eval_full fs ps fuel s st env2 b"
          using "8.IH"(3)[OF cF refl refl ab] .
        then show ?thesis using cF c_eq by simp
      qed
    qed (use Some c_eq in simp_all)
  qed
next
  case (9 fs ps fuel s st env x v body sp env2)
  have av: "\<forall>y. string_in_list y (free_vars v) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "9.prems" by auto
  have v_eq: "eval_full fs ps fuel s st env v = eval_full fs ps fuel s st env2 v"
    using "9.IH"(1)[OF av] .
  show ?case
  proof (cases "eval_full fs ps fuel s st env v")
    case None
    then show ?thesis using v_eq by simp
  next
    case (Some va)
    have v2: "eval_full fs ps fuel s st env2 v = Some va" using v_eq Some by simp
    have abd: "\<forall>y. string_in_list y (free_vars body)
                 \<longrightarrow> env_lookup ((x, va) # env) y = env_lookup ((x, va) # env2) y"
      using "9.prems" by (auto simp: env_lookup_def)
    have body_eq: "eval_full fs ps fuel s st ((x, va) # env) body
            = eval_full fs ps fuel s st ((x, va) # env2) body"
      using "9.IH"(2)[OF Some abd] .
    show ?thesis using Some v2 body_eq by simp
  qed
next
  case (10 fs ps fuel s st env base f sp env2)
  have ab: "\<forall>y. string_in_list y (free_vars base) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "10.prems" by auto
  show ?case using "10.IH"[OF ab] by simp
next
  case (11 fs ps fuel s st env e sp env2)
  have ae: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "11.prems" by auto
  show ?case using "11.IH"[OF ae] by simp
next
  case (12 fs ps fuel s st env e sp env2)
  have ae: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "12.prems" by auto
  show ?case using "12.IH"[OF ae] by simp
next
  case (13 fs ps fuel s st env e sp env2)
  have ae: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "13.prems" by auto
  show ?case using "13.IH"[OF ae] by simp
next
  case (14 fs ps fuel s st env e pat sp env2)
  have ae: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "14.prems" by auto
  show ?case using "14.IH"[OF ae] by simp
next
  case (15 fs ps fuel s st env callee args sp env2)
  have agr: "\<forall>y. string_in_list y (free_vars_list args) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "15.prems" by (auto simp: env_lookup_def)
  show ?case
  proof (cases fuel)
    case 0
    then show ?thesis by simp
  next
    case (Suc fuel')
    show ?thesis
    proof (cases "\<exists>nm sp1. callee = IdentifierF nm sp1")
      case False
      then show ?thesis using Suc by (cases callee) auto
    next
      case True
      then obtain nm sp1 where idc: "callee = IdentifierF nm sp1" by blast
      show ?thesis
      proof (cases "lookup_callee fs ps nm")
        case None
        then show ?thesis using Suc idc by simp
      next
        case (Some pb)
        obtain params body where pb: "pb = (params, body)" by (cases pb) auto
        show ?thesis
        proof (cases "length params = length args")
          case False
          then show ?thesis using Suc idc Some pb by simp
        next
          case True
          have args_eq: "eval_full_list fs ps fuel s st env args
                           = eval_full_list fs ps fuel s st env2 args"
            using "15.IH" Suc idc Some pb True agr by (auto simp: env_lookup_def)
          then show ?thesis using Suc idc Some pb True by (simp split: option.splits)
        qed
      qed
    qed
  qed
next
  case (18 fs ps fuel s st env e es env2)
  have agr_e: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "18.prems" by auto
  have agr_es: "\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "18.prems" by auto
  have e_eq: "eval_full fs ps fuel s st env e = eval_full fs ps fuel s st env2 e"
    using "18.IH"(1)[OF agr_e] .
  show ?case
  proof (cases "eval_full fs ps fuel s st env e")
    case None
    then show ?thesis using e_eq by simp
  next
    case (Some v0)
    have e2: "eval_full fs ps fuel s st env2 e = Some v0" using e_eq Some by simp
    have es_eq: "eval_full_list fs ps fuel s st env es = eval_full_list fs ps fuel s st env2 es"
      using "18.IH"(2)[OF Some agr_es] .
    show ?thesis using Some e2 es_eq by simp
  qed
qed (auto simp: env_lookup_def)

lemma string_in_free_vars_list:
  "string_in_list y (free_vars_list es) = list_ex (\<lambda>e. string_in_list y (free_vars e)) es"
  by (induction es) auto

lemma map_of_swap_head:
  assumes "k \<notin> fst ` set xs"
  shows "map_of (xs @ (k, v) # ys) = map_of ((k, v) # xs @ ys)"
proof (rule ext)
  fix z
  have nk: "map_of xs k = None" using assms by (simp add: map_of_eq_None_iff)
  show "map_of (xs @ (k, v) # ys) z = map_of ((k, v) # xs @ ys) z"
    using nk by (cases "map_of xs z") (auto simp: map_of_append map_add_def)
qed

lemma bind_params_eval:
  assumes "list_all (\<lambda>a. list_all (\<lambda>q. \<not> string_in_list q (free_vars a)) pms) as"
    and "length pms = length as"
    and "distinct pms"
    and "eval_full_list fs ps fuel s st env as = Some vals"
  shows "eval_full fs ps fuel s st env (bind_params pms as body)
           = eval_full fs ps fuel s st (zip pms vals @ env) body"
  using assms
proof (induction pms arbitrary: as vals env)
  case Nil
  then show ?case by simp
next
  case (Cons p pms')
  obtain a as' where as_eq: "as = a # as'"
    using Cons.prems(2) by (cases as) auto
  obtain v0 vs0 where v0: "eval_full fs ps fuel s st env a = Some v0"
    and vs0: "eval_full_list fs ps fuel s st env as' = Some vs0"
    and vals_eq: "vals = v0 # vs0"
    using Cons.prems(4) as_eq by (auto split: option.splits)
  have p_notin: "p \<notin> set pms'" using Cons.prems(3) by simp
  have len': "length pms' = length as'" using Cons.prems(2) as_eq by simp
  have d': "distinct pms'" using Cons.prems(3) by simp
  have pf': "list_all (\<lambda>a. list_all (\<lambda>q. \<not> string_in_list q (free_vars a)) pms') as'"
    using Cons.prems(1) as_eq by (auto simp: list_all_iff)
  have p_nf: "\<not> string_in_list p (free_vars_list as')"
    using Cons.prems(1) as_eq by (auto simp: string_in_free_vars_list list_all_iff list_ex_iff)
  have agr1: "\<forall>y. string_in_list y (free_vars_list as')
                \<longrightarrow> env_lookup ((p, v0) # env) y = env_lookup env y"
    using p_nf by (auto simp: env_lookup_def)
  have vs0_ext: "eval_full_list fs ps fuel s st ((p, v0) # env) as' = Some vs0"
    using eval_full_coincidence(2)[OF agr1] vs0 by simp
  have IH: "eval_full fs ps fuel s st ((p, v0) # env) (bind_params pms' as' body)
              = eval_full fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body"
    using Cons.IH[OF pf' len' d' vs0_ext] .
  have keys: "p \<notin> fst ` set (zip pms' vs0)"
    using p_notin by (fastforce dest: set_zip_leftD)
  have mEq: "map_of (zip pms' vs0 @ (p, v0) # env) = map_of ((p, v0) # zip pms' vs0 @ env)"
    by (rule map_of_swap_head[OF keys])
  have reorder: "eval_full fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body
                   = eval_full fs ps fuel s st ((p, v0) # zip pms' vs0 @ env) body"
  proof (rule eval_full_coincidence(1), intro allI impI)
    fix y :: "String.literal"
    assume "string_in_list y (free_vars body)"
    show "env_lookup (zip pms' vs0 @ (p, v0) # env) y = env_lookup ((p, v0) # zip pms' vs0 @ env) y"
      unfolding env_lookup_def by (rule fun_cong[OF mEq])
  qed
  have "eval_full fs ps fuel s st env (bind_params (p # pms') (a # as') body)
          = eval_full fs ps fuel s st ((p, v0) # env) (bind_params pms' as' body)"
    using v0 by simp
  also have "\<dots> = eval_full fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body"
    using IH .
  also have "\<dots> = eval_full fs ps fuel s st ((p, v0) # zip pms' vs0 @ env) body"
    using reorder .
  also have "\<dots> = eval_full fs ps fuel s st (zip (p # pms') (v0 # vs0) @ env) body"
    by simp
  finally show ?case using as_eq vals_eq by simp
qed

lemma eval_full_callfree_fuel:
  "\<not> list_ex is_call_full (allSubexprs e)
     \<Longrightarrow> eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
  "\<not> list_ex is_call_full (allSubexprs_list es)
     \<Longrightarrow> eval_full_list fs ps fuel1 s st env es = eval_full_list fs ps fuel2 s st env es"
proof (induction fs ps fuel1 s st env e and fs ps fuel1 s st env es
        arbitrary: fuel2 and fuel2
        rule: eval_full_eval_full_list.induct)
  case (6 fs ps fuel1 s st env bop l r sp fuel2)
  have "eval_full fs ps fuel1 s st env l = eval_full fs ps fuel2 s st env l"
    using "6.IH"(1) "6.prems" by (auto simp: list_ex_iff)
  moreover have "eval_full fs ps fuel1 s st env r = eval_full fs ps fuel2 s st env r"
    using "6.IH"(2) "6.prems" by (auto simp: list_ex_iff)
  ultimately show ?case by simp
next
  case (7 fs ps fuel1 s st env uop e sp fuel2)
  have "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "7.IH" "7.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (8 fs ps fuel1 s st env c a b sp fuel2)
  have c_eq: "eval_full fs ps fuel1 s st env c = eval_full fs ps fuel2 s st env c"
    using "8.IH"(1) "8.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval_full fs ps fuel1 s st env c")
    case None
    then show ?thesis using c_eq by simp
  next
    case (Some vc)
    show ?thesis
    proof (cases vc)
      case (VBool bb)
      show ?thesis
      proof (cases bb)
        case True
        have cT: "eval_full fs ps fuel1 s st env c = Some (VBool True)"
          using Some VBool True by simp
        have "eval_full fs ps fuel1 s st env a = eval_full fs ps fuel2 s st env a"
          using "8.IH"(2)[OF cT refl refl] "8.prems" by (auto simp: list_ex_iff)
        then show ?thesis using cT c_eq by simp
      next
        case False
        have cF: "eval_full fs ps fuel1 s st env c = Some (VBool False)"
          using Some VBool False by simp
        have "eval_full fs ps fuel1 s st env b = eval_full fs ps fuel2 s st env b"
          using "8.IH"(3)[OF cF refl refl] "8.prems" by (auto simp: list_ex_iff)
        then show ?thesis using cF c_eq by simp
      qed
    qed (use Some c_eq in simp_all)
  qed
next
  case (9 fs ps fuel1 s st env x v body sp fuel2)
  have v_eq: "eval_full fs ps fuel1 s st env v = eval_full fs ps fuel2 s st env v"
    using "9.IH"(1) "9.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval_full fs ps fuel1 s st env v")
    case None
    then show ?thesis using v_eq by simp
  next
    case (Some va)
    have v2: "eval_full fs ps fuel2 s st env v = Some va" using v_eq Some by simp
    have "eval_full fs ps fuel1 s st ((x, va) # env) body
            = eval_full fs ps fuel2 s st ((x, va) # env) body"
      using "9.IH"(2)[OF Some] "9.prems" by (auto simp: list_ex_iff)
    then show ?thesis using Some v2 by simp
  qed
next
  case (10 fs ps fuel1 s st env base f sp fuel2)
  have "eval_full fs ps fuel1 s st env base = eval_full fs ps fuel2 s st env base"
    using "10.IH" "10.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (11 fs ps fuel1 s st env e sp fuel2)
  have "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "11.IH" "11.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (12 fs ps fuel1 s st env e sp fuel2)
  have "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "12.IH" "12.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (13 fs ps fuel1 s st env e sp fuel2)
  have "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "13.IH" "13.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (14 fs ps fuel1 s st env e pat sp fuel2)
  have "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "14.IH" "14.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (15 fs ps fuel1 s st env callee args sp fuel2)
  have False using "15.prems" by simp
  then show ?case by simp
next
  case (18 fs ps fuel1 s st env e es fuel2)
  have e_eq: "eval_full fs ps fuel1 s st env e = eval_full fs ps fuel2 s st env e"
    using "18.IH"(1) "18.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval_full fs ps fuel1 s st env e")
    case None
    then show ?thesis using e_eq by simp
  next
    case (Some v0)
    have e2: "eval_full fs ps fuel2 s st env e = Some v0" using e_eq Some by simp
    have "eval_full_list fs ps fuel1 s st env es = eval_full_list fs ps fuel2 s st env es"
      using "18.IH"(2)[OF Some] "18.prems" by (auto simp: list_ex_iff)
    then show ?thesis using Some e2 by simp
  qed
qed (auto simp: list_ex_iff)

lemma string_in_list_iff: "string_in_list y xs = (y \<in> set xs)"
  by (induction xs) auto

lemma inline_calls_list_length [simp]:
  "length (inline_calls_list fs ps es) = length es"
  by (induction es) auto

lemma eval_full_bin_someD:
  "eval_full_bin bop x y = Some v \<Longrightarrow> \<exists>a b. x = Some a \<and> y = Some b"
  by (cases bop; cases x; cases y; auto split: option.splits ir_value.splits)

lemma eval_full_un_someD:
  "eval_full_un uop x = Some v \<Longrightarrow> \<exists>a. x = Some a"
  by (cases uop; cases x; auto split: option.splits ir_value.splits)

lemma map_of_zip_prefix:
  assumes "length ps = length vs" and "x \<in> set ps"
  shows "map_of (zip ps vs @ env) x = map_of (zip ps vs) x"
proof -
  have "map_of (zip ps vs) x \<noteq> None" using assms by (simp add: map_of_zip_is_None)
  then show ?thesis by (auto simp: map_of_append map_add_def split: option.splits)
qed

lemma inline_calls_CallF_lookup:
  "lookup_callee fs ps nm = Some (params, body) \<Longrightarrow>
   inline_calls fs ps (CallF (IdentifierF nm sp1) args sp)
     = (if length params = length (inline_calls_list fs ps args)
            \<and> capture_safe body params (inline_calls_list fs ps args)
        then bind_params params (inline_calls_list fs ps args) body
        else CallF (IdentifierF nm sp1) (inline_calls_list fs ps args) sp)"
  by (auto simp: lookup_callee_def split: option.splits)

lemma inline_calls_eval_full:
  "eval_full fs ps fuel s st env e = Some w
     \<Longrightarrow> eval_full fs ps fuel s st env (inline_calls fs ps e) = Some w"
  "eval_full_list fs ps fuel s st env es = Some ws
     \<Longrightarrow> eval_full_list fs ps fuel s st env (inline_calls_list fs ps es) = Some ws"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es
        arbitrary: w and ws
        rule: eval_full_eval_full_list.induct)
  case (6 fs ps fuel s st env bop l r sp w)
  have g: "eval_full_bin bop (eval_full fs ps fuel s st env l)
             (eval_full fs ps fuel s st env r) = Some w"
    using "6.prems" by simp
  obtain vl vr where l_s: "eval_full fs ps fuel s st env l = Some vl"
    and r_s: "eval_full fs ps fuel s st env r = Some vr"
    using eval_full_bin_someD[OF g] by blast
  show ?case using "6.IH"(1)[OF l_s] "6.IH"(2)[OF r_s] g l_s r_s by simp
next
  case (7 fs ps fuel s st env uop e sp w)
  have g: "eval_full_un uop (eval_full fs ps fuel s st env e) = Some w"
    using "7.prems" by simp
  obtain ve where e_s: "eval_full fs ps fuel s st env e = Some ve"
    using eval_full_un_someD[OF g] by blast
  show ?case using "7.IH"[OF e_s] g e_s by simp
next
  case (8 fs ps fuel s st env c a b sp w)
  obtain vc where c_s: "eval_full fs ps fuel s st env c = Some vc"
    using "8.prems" by (cases "eval_full fs ps fuel s st env c") auto
  have ic: "eval_full fs ps fuel s st env (inline_calls fs ps c) = Some vc"
    using "8.IH"(1)[OF c_s] .
  show ?case
  proof (cases vc)
    case (VBool bb)
    show ?thesis
    proof (cases bb)
      case True
      have cT: "eval_full fs ps fuel s st env c = Some (VBool True)"
        using c_s VBool True by simp
      have av: "eval_full fs ps fuel s st env a = Some w" using "8.prems" cT by simp
      have "eval_full fs ps fuel s st env (inline_calls fs ps a) = Some w"
        using "8.IH"(2)[OF cT refl refl av] .
      then show ?thesis using ic cT VBool True by simp
    next
      case False
      have cF: "eval_full fs ps fuel s st env c = Some (VBool False)"
        using c_s VBool False by simp
      have bv: "eval_full fs ps fuel s st env b = Some w" using "8.prems" cF by simp
      have "eval_full fs ps fuel s st env (inline_calls fs ps b) = Some w"
        using "8.IH"(3)[OF cF refl refl bv] .
      then show ?thesis using ic cF VBool False by simp
    qed
  qed (use "8.prems" c_s in simp_all)
next
  case (9 fs ps fuel s st env x v body sp w)
  obtain va where va: "eval_full fs ps fuel s st env v = Some va"
    using "9.prems" by (cases "eval_full fs ps fuel s st env v") auto
  have iv: "eval_full fs ps fuel s st env (inline_calls fs ps v) = Some va"
    using "9.IH"(1)[OF va] .
  have bw: "eval_full fs ps fuel s st ((x, va) # env) body = Some w"
    using "9.prems" va by simp
  have "eval_full fs ps fuel s st ((x, va) # env) (inline_calls fs ps body) = Some w"
    using "9.IH"(2)[OF va bw] .
  then show ?case using iv va by simp
next
  case (10 fs ps fuel s st env base f sp w)
  obtain vb where vb: "eval_full fs ps fuel s st env base = Some vb"
    using "10.prems" by (cases "eval_full fs ps fuel s st env base") auto
  have "eval_full fs ps fuel s st env (inline_calls fs ps base) = Some vb"
    using "10.IH"[OF vb] .
  then show ?case using "10.prems" vb by simp
next
  case (11 fs ps fuel s st env e sp w)
  have e_s: "eval_full fs ps fuel s st env e = Some w" using "11.prems" by simp
  show ?case using "11.IH"[OF e_s] by simp
next
  case (12 fs ps fuel s st env e sp w)
  have e_s: "eval_full fs ps fuel s st env e = Some w" using "12.prems" by simp
  show ?case using "12.IH"[OF e_s] by simp
next
  case (13 fs ps fuel s st env e sp w)
  obtain ve where e_s: "eval_full fs ps fuel s st env e = Some ve"
    using "13.prems" by (cases "eval_full fs ps fuel s st env e") auto
  have "eval_full fs ps fuel s st env (inline_calls fs ps e) = Some ve"
    using "13.IH"[OF e_s] .
  then show ?case using "13.prems" e_s by simp
next
  case (14 fs ps fuel s st env e pat sp w)
  obtain ve where e_s: "eval_full fs ps fuel s st env e = Some ve"
    using "14.prems" by (cases "eval_full fs ps fuel s st env e") auto
  have "eval_full fs ps fuel s st env (inline_calls fs ps e) = Some ve"
    using "14.IH"[OF e_s] .
  then show ?case using "14.prems" e_s by simp
next
  case (15 fs ps fuel s st env callee args sp w)
  obtain fuel' where fuel: "fuel = Suc fuel'"
    using "15.prems" by (cases fuel) auto
  obtain nm sp1 where idc: "callee = IdentifierF nm sp1"
    using "15.prems" fuel by (cases callee) auto
  obtain params body where lc: "lookup_callee fs ps nm = Some (params, body)"
    using "15.prems" fuel idc by (cases "lookup_callee fs ps nm") auto
  have lenpa: "length params = length args"
    using "15.prems" fuel idc lc by (simp split: if_splits)
  obtain vals where vals: "eval_full_list fs ps fuel s st env args = Some vals"
    using "15.prems" fuel idc lc lenpa
    by (cases "eval_full_list fs ps fuel s st env args") auto
  have body_w: "eval_full fs ps fuel' s st (zip params vals) body = Some w"
    using "15.prems" fuel idc lc lenpa vals by simp
  have args_eq: "eval_full_list fs ps fuel s st env (inline_calls_list fs ps args) = Some vals"
    using "15.IH" fuel idc lc lenpa vals by (auto split: if_splits)
  have lenpa': "length params = length (inline_calls_list fs ps args)"
    using lenpa by simp
  have lenv: "length params = length vals"
    using vals lenpa by (simp add: eval_full_list_length)
  have inl: "inline_calls fs ps (CallF callee args sp)
               = (if capture_safe body params (inline_calls_list fs ps args)
                  then bind_params params (inline_calls_list fs ps args) body
                  else CallF callee (inline_calls_list fs ps args) sp)"
    using inline_calls_CallF_lookup[OF lc] idc lenpa' by simp
  show ?case
  proof (cases "capture_safe body params (inline_calls_list fs ps args)")
    case True
    have pf: "list_all (\<lambda>a. list_all (\<lambda>q. \<not> string_in_list q (free_vars a)) params)
                 (inline_calls_list fs ps args)"
      using True by (simp add: capture_safe_def)
    have dp: "distinct params" using True by (simp add: capture_safe_def)
    have fvb: "list_all (\<lambda>x. string_in_list x params) (free_vars body)"
      using True by (simp add: capture_safe_def)
    have cfb: "\<not> list_ex is_call_full (allSubexprs body)"
      using True by (simp add: capture_safe_def)
    have step1: "eval_full fs ps fuel s st env (bind_params params (inline_calls_list fs ps args) body)
                   = eval_full fs ps fuel s st (zip params vals @ env) body"
      using bind_params_eval[OF pf lenpa' dp args_eq] .
    have step2: "eval_full fs ps fuel s st (zip params vals @ env) body
                   = eval_full fs ps fuel s st (zip params vals) body"
    proof (rule eval_full_coincidence(1), intro allI impI)
      fix y assume "string_in_list y (free_vars body)"
      then have yin: "y \<in> set params" using fvb by (auto simp: list_all_iff string_in_list_iff)
      show "env_lookup (zip params vals @ env) y = env_lookup (zip params vals) y"
        unfolding env_lookup_def by (rule map_of_zip_prefix[OF lenv yin])
    qed
    have step3: "eval_full fs ps fuel s st (zip params vals) body
                   = eval_full fs ps fuel' s st (zip params vals) body"
      using eval_full_callfree_fuel(1)[OF cfb] .
    show ?thesis using inl True step1 step2 step3 body_w by simp
  next
    case False
    have "eval_full fs ps fuel s st env (CallF callee (inline_calls_list fs ps args) sp) = Some w"
      using fuel idc lc lenpa' args_eq body_w by simp
    then show ?thesis using inl False by simp
  qed
next
  case (18 fs ps fuel s st env e es ws)
  obtain v0 vs0 where v0: "eval_full fs ps fuel s st env e = Some v0"
    and vs0: "eval_full_list fs ps fuel s st env es = Some vs0"
    and ws_eq: "ws = v0 # vs0"
    using "18.prems" by (auto split: option.splits)
  have ie: "eval_full fs ps fuel s st env (inline_calls fs ps e) = Some v0"
    using "18.IH"(1)[OF v0] .
  have ies: "eval_full_list fs ps fuel s st env (inline_calls_list fs ps es) = Some vs0"
    using "18.IH"(2)[OF v0 vs0] .
  show ?case using ie ies ws_eq by simp
qed (auto split: option.splits)

end
