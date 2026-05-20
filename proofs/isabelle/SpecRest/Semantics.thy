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

text \<open>Category H — first proven bricks of the spec front-half (the
  precondition the universal soundness theorem assumes via \<open>eval e = Some\<close>).
  These are NOT type-safety / progress (still an open initiative, see
  \<open>STATUS.md\<close> §3); they pin down the *exact* operand-typing preconditions
  and the *exact* source of partiality for the arithmetic / comparison
  fragment — precisely what a future spec type system must discharge. A naive
  \<open>free_vars \<subseteq> dom env\<close> scope-safety lemma is deliberately not attempted:
  it is *false* here because \<open>eval\<close>'s \<open>Ident\<close> arm legitimately resolves
  from \<open>state\<close>, not only \<open>env\<close>.\<close>

lemma eval_arith_some_imp_int:
  "eval_arith op x y = Some v \<Longrightarrow> (\<exists>a. x = Some (VInt a)) \<and> (\<exists>b. y = Some (VInt b))"
  by (induction op x y rule: eval_arith.induct) (auto split: if_splits)

lemma eval_arith_div_zero:
  "eval_arith DivOp (Some (VInt a)) (Some (VInt 0)) = None"
  by simp

lemma eval_arith_int_total:
  "op \<noteq> DivOp \<or> b \<noteq> 0 \<Longrightarrow>
     \<exists>r. eval_arith op (Some (VInt a)) (Some (VInt b)) = Some (VInt r)"
  by (cases op) auto

lemma eval_cmp_some_imp_defined:
  "eval_cmp op x y = Some v \<Longrightarrow> (\<exists>a. x = Some a) \<and> (\<exists>b. y = Some b)"
  by (induction op x y rule: eval_cmp.induct) auto

lemma eval_cmp_order_imp_int:
  "op \<in> {LtOp, LeOp, GtOp, GeOp} \<Longrightarrow> eval_cmp op x y = Some v \<Longrightarrow>
     (\<exists>a. x = Some (VInt a)) \<and> (\<exists>b. y = Some (VInt b))"
  by (induction op x y rule: eval_cmp.induct) auto

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
  | TEnum "String.literal"
  | TEntity "String.literal"
  | TSet ty

inductive value_has_ty :: "ir_value \<Rightarrow> ty \<Rightarrow> bool" (infix "::v" 50) where
  vt_bool:        "VBool b ::v TBool"
| vt_int:         "VInt n ::v TInt"
| vt_enum:        "VEnum ename ev ::v TEnum ename"
| vt_entity:      "VEntity ename eid ::v TEntity ename"
| vt_set:         "(\<forall>v \<in> set vs. v ::v t) \<Longrightarrow> VSet vs ::v TSet t"
| vt_entity_with: "base ::v TEntity ename
                     \<Longrightarrow> VEntityWith base fld override ::v TEntity ename"

inductive_cases value_has_ty_bool_cases [elim!]: "VBool b ::v t"
inductive_cases value_has_ty_int_cases [elim!]: "VInt n ::v t"
inductive_cases value_has_ty_enum_cases [elim!]: "VEnum ename ev ::v t"
inductive_cases value_has_ty_entity_cases [elim!]: "VEntity ename eid ::v t"
inductive_cases value_has_ty_set_cases [elim!]: "VSet vs ::v t"
inductive_cases value_has_ty_entity_with_cases [elim!]:
  "VEntityWith base fld override ::v t"

lemma value_has_ty_VBool_iff [simp]: "VBool b ::v t \<longleftrightarrow> t = TBool"
  by (auto intro: vt_bool)

lemma value_has_ty_VInt_iff [simp]: "VInt n ::v t \<longleftrightarrow> t = TInt"
  by (auto intro: vt_int)

lemma value_has_ty_VEnum_iff [simp]:
  "VEnum ename ev ::v t \<longleftrightarrow> t = TEnum ename"
  by (auto intro: vt_enum)

lemma value_has_ty_VEntity_iff [simp]:
  "VEntity ename eid ::v t \<longleftrightarrow> t = TEntity ename"
  by (auto intro: vt_entity)

lemma eval_arith_preservation:
  assumes "eval_arith op x y = Some v"
      and "\<And>a. x = Some a \<Longrightarrow> a ::v TInt"
      and "\<And>b. y = Some b \<Longrightarrow> b ::v TInt"
  shows "v ::v TInt"
  using assms eval_arith_some_imp_int[OF assms(1)]
  by (induction op x y rule: eval_arith.induct)
     (auto split: if_splits intro: vt_int)

lemma eval_cmp_preservation:
  assumes "eval_cmp op x y = Some v"
  shows "v ::v TBool"
  using assms by (induction op x y rule: eval_cmp.induct) (auto intro: vt_bool)

text \<open>Phase H2 (start) - typing context and environment agreement.
  The H2 phase introduces a typing context \<open>tyenv\<close> for lexical
  binders and the \<open>env_agrees\<close> predicate witnessing that the
  runtime environment satisfies the context. This is the lexical
  half of the typing context; state-schema typing (relations /
  entity fields) is the next sub-phase. Together they will support
  the typing relation \<open>Gamma |- e : t\<close> over \<open>expr_full\<close> and the
  rule-induction type-safety theorem.\<close>

type_synonym tyenv = "(String.literal \<times> ty) list"

definition tyenv_lookup :: "tyenv \<Rightarrow> String.literal \<Rightarrow> ty option" where
  "tyenv_lookup G x = map_of G x"

definition env_agrees :: "env \<Rightarrow> tyenv \<Rightarrow> bool" where
  "env_agrees env G =
     (\<forall>x t. tyenv_lookup G x = Some t \<longrightarrow>
            (\<exists>v. map_of env x = Some v \<and> v ::v t))"

lemma env_agrees_lookup:
  assumes "env_agrees env G" and "tyenv_lookup G x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> v ::v t"
  using assms by (simp add: env_agrees_def)

lemma env_agrees_empty [simp]: "env_agrees env []"
  by (simp add: env_agrees_def tyenv_lookup_def)

lemma env_agrees_cons:
  assumes "env_agrees env G" and "v ::v t"
  shows "env_agrees ((x, v) # env) ((x, t) # G)"
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

record state_schema =
  ss_scalars :: "(String.literal \<times> ty) list"

definition state_agrees_scalars :: "state \<Rightarrow> state_schema \<Rightarrow> bool" where
  "state_agrees_scalars st sch =
     (\<forall>x t. map_of (ss_scalars sch) x = Some t \<longrightarrow>
            (\<exists>v. state_lookup_scalar st x = Some v \<and> v ::v t))"

lemma state_agrees_scalars_lookup:
  assumes "state_agrees_scalars st sch"
      and "map_of (ss_scalars sch) x = Some t"
  shows "\<exists>v. state_lookup_scalar st x = Some v \<and> v ::v t"
  using assms by (simp add: state_agrees_scalars_def)

lemma state_agrees_scalars_empty [simp]:
  "state_agrees_scalars st \<lparr> ss_scalars = [] \<rparr>"
  by (simp add: state_agrees_scalars_def)

text \<open>Phase H2 (joint context). \<open>tyctx\<close> bundles the lexical and
  state-schema halves; \<open>agrees env st \<Gamma>\<close> is the predicate that
  the H3 type-safety theorem will require on any well-typed
  evaluation.\<close>

record tyctx =
  tc_env     :: tyenv
  tc_schema  :: state_schema

definition agrees :: "env \<Rightarrow> state \<Rightarrow> tyctx \<Rightarrow> bool" where
  "agrees env st \<Gamma> \<longleftrightarrow>
     env_agrees env (tc_env \<Gamma>) \<and> state_agrees_scalars st (tc_schema \<Gamma>)"

lemma agrees_env_lookup:
  assumes "agrees env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> v ::v t"
  using assms by (auto simp: agrees_def dest: env_agrees_lookup)

lemma agrees_state_lookup:
  assumes "agrees env st \<Gamma>"
      and "map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t"
  shows "\<exists>v. state_lookup_scalar st x = Some v \<and> v ::v t"
  using assms by (auto simp: agrees_def dest: state_agrees_scalars_lookup)

text \<open>Strict env agreement: every \<open>env\<close>-binding is also typed in \<open>tyenv\<close>
  (no untyped runtime bindings shadowing state-resident scalars). Needed
  for the preservation argument at \<open>T_Ident_State\<close>: the typing rule fires
  when \<open>x\<close> is NOT in \<open>tyenv\<close>, and strict agreement guarantees that means
  \<open>env_lookup x = None\<close> so eval's Ident arm falls through to the state.\<close>

definition env_agrees_strict :: "env \<Rightarrow> tyenv \<Rightarrow> bool" where
  "env_agrees_strict env G \<longleftrightarrow>
     env_agrees env G \<and>
     (\<forall>x v. map_of env x = Some v \<longrightarrow> (\<exists>t. tyenv_lookup G x = Some t))"

definition agrees_strict :: "env \<Rightarrow> state \<Rightarrow> tyctx \<Rightarrow> bool" where
  "agrees_strict env st \<Gamma> \<longleftrightarrow>
     env_agrees_strict env (tc_env \<Gamma>) \<and>
     state_agrees_scalars st (tc_schema \<Gamma>)"

lemma agrees_strict_imp_agrees:
  "agrees_strict env st \<Gamma> \<Longrightarrow> agrees env st \<Gamma>"
  by (auto simp: agrees_strict_def agrees_def env_agrees_strict_def)

lemma env_agrees_strict_lookup_none:
  assumes "env_agrees_strict env G" and "tyenv_lookup G x = None"
  shows "map_of env x = None"
  using assms by (auto simp: env_agrees_strict_def)

lemma agrees_strict_env_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = Some t"
  shows "\<exists>v. map_of env x = Some v \<and> v ::v t"
  using assms agrees_strict_imp_agrees agrees_env_lookup by blast

lemma agrees_strict_state_lookup:
  assumes "agrees_strict env st \<Gamma>"
      and "tyenv_lookup (tc_env \<Gamma>) x = None"
      and "map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t"
  shows "map_of env x = None
         \<and> (\<exists>v. state_lookup_scalar st x = Some v \<and> v ::v t)"
proof -
  have es: "env_agrees_strict env (tc_env \<Gamma>)"
    using assms(1) by (simp add: agrees_strict_def)
  have ss: "state_agrees_scalars st (tc_schema \<Gamma>)"
    using assms(1) by (simp add: agrees_strict_def)
  have "map_of env x = None"
    by (rule env_agrees_strict_lookup_none[OF es assms(2)])
  moreover have "\<exists>v. state_lookup_scalar st x = Some v \<and> v ::v t"
    by (rule state_agrees_scalars_lookup[OF ss assms(3)])
  ultimately show ?thesis ..
qed

lemma env_agrees_strict_cons:
  assumes "env_agrees_strict env G" and "v ::v t"
  shows "env_agrees_strict ((x, v) # env) ((x, t) # G)"
  using assms
  by (auto simp: env_agrees_strict_def env_agrees_def tyenv_lookup_def
           split: if_splits)

lemma agrees_strict_cons:
  assumes "agrees_strict env st \<Gamma>" and "v ::v t"
  shows "agrees_strict ((x, v) # env) st
           (\<Gamma>\<lparr>tc_env := (x, t) # tc_env \<Gamma>\<rparr>)"
  using assms env_agrees_strict_cons by (auto simp: agrees_strict_def)

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
| T_Ident_Lex:
    "tyenv_lookup (tc_env \<Gamma>) x = Some t
       \<Longrightarrow> expr_has_ty \<Gamma> (IdentifierF x sp) t"
| T_Ident_State:
    "tyenv_lookup (tc_env \<Gamma>) x = None
       \<Longrightarrow> map_of (ss_scalars (tc_schema \<Gamma>)) x = Some t
       \<Longrightarrow> expr_has_ty \<Gamma> (IdentifierF x sp) t"
| T_Arith:
    "expr_has_ty \<Gamma> l TInt
       \<Longrightarrow> expr_has_ty \<Gamma> r TInt
       \<Longrightarrow> op \<in> {BAdd, BSub, BMul, BDiv}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) TInt"
| T_Cmp_Eq:
    "expr_has_ty \<Gamma> l t
       \<Longrightarrow> expr_has_ty \<Gamma> r t
       \<Longrightarrow> op \<in> {BEq, BNeq}
       \<Longrightarrow> expr_has_ty \<Gamma> (BinaryOpF op l r sp) TBool"
| T_Cmp_Ord:
    "expr_has_ty \<Gamma> l TInt
       \<Longrightarrow> expr_has_ty \<Gamma> r TInt
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
    "expr_has_ty \<Gamma> e TInt
       \<Longrightarrow> expr_has_ty \<Gamma> (UnaryOpF UNegate e sp) TInt"
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

lemmas expr_has_ty_intros [intro] =
  T_BoolLit T_IntLit T_Ident_Lex T_Ident_State
  T_Arith T_Cmp_Eq T_Cmp_Ord T_Bool_Bin T_Not T_Neg T_Let
  T_Prime T_Pre T_EnumAccess

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
