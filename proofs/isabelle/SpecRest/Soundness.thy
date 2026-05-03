theory Soundness
  imports Translate
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

text \<open>FIXME: VSet recursive injectivity needs more careful induction.
Lean's `valueToSmt_inj` (Soundness.lean:89-186) handles this with a 100-LoC
mutual structural induction; the Isabelle equivalent requires either
custom set-based induction or a pre-proved auxiliary on map equality with
per-element injectivity. Proven for the atom cases below; full proof is
queued for the next pass.\<close>

lemma value_to_smt_inj_VBool:
  "(value_to_smt (VBool a) = value_to_smt (VBool b)) = (a = b)"
  by auto

lemma value_to_smt_inj_VInt:
  "(value_to_smt (VInt a) = value_to_smt (VInt b)) = (a = b)"
  by auto

lemma value_to_smt_inj [simp]:
  "(value_to_smt v1 = value_to_smt v2) = (v1 = v2)"
  sorry

section \<open>Correlation lemmas\<close>

lemma correlate_env_lookup [simp]:
  "smt_env_lookup (correlate_env env) x = map_option value_to_smt (env_lookup env x)"
  by (induction env)
     (auto simp: smt_env_lookup_def env_lookup_def split: if_splits)

lemma map_of_map_value_eq:
  "map_of (map (\<lambda>(k, v). (k, f v)) xs) x = map_option f (map_of xs x)"
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

section \<open>Per-case soundness — atoms\<close>

lemma soundness_bool_lit:
  "value_to_smt_opt (eval s st env (BoolLit b))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (BoolLit b))"
  by (force split: option.splits smt_val.splits)

lemma soundness_int_lit:
  "value_to_smt_opt (eval s st env (IntLit n))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (IntLit n))"
  by (force split: option.splits smt_val.splits)

lemma soundness_ident:
  "value_to_smt_opt (eval s st env (Ident x))
     = smt_eval (correlate_model s st) (correlate_env env) (translate (Ident x))"
  by (simp split: option.splits)

section \<open>Per-case soundness — propositional / arithmetic\<close>

lemma soundness_un_not:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNot e))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNot e))"
  using assms
  by (cases "eval s st env e")
     (auto split: ir_value.splits option.splits smt_val.splits)

lemma soundness_un_neg:
  assumes "value_to_smt_opt (eval s st env e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate e)"
  shows "value_to_smt_opt (eval s st env (UnNeg e))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (UnNeg e))"
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

lemma soundness_bool_bin_bools:
  assumes hl: "eval s st env l = Some (VBool a)"
      and hr: "eval s st env r = Some (VBool b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (BoolBin op l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (BoolBin op l r))"
  using hl hr smt_eval_of_eval_VBool[OF hl ihl] smt_eval_of_eval_VBool[OF hr ihr]
  by (cases op) auto

lemma soundness_arith_add_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith AddOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith AddOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_arith_sub_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith SubOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith SubOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_arith_mul_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith MulOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith MulOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_arith_div_ints_nonzero:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and hbz: "b \<noteq> 0"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith DivOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith DivOp l r))"
  using hl hr hbz smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr] by simp

lemma soundness_arith_div_ints_zero:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt 0)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Arith DivOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Arith DivOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_cmp_eq_vals:
  assumes hl: "eval s st env l = Some va"
      and hr: "eval s st env r = Some vb"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp EqOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp EqOp l r))"
  text \<open>Depends on the global value_to_smt_inj, which itself is sorry-stubbed
  pending the VSet recursive injectivity proof. Once that lands, this proof
  closes via `by (cases va; cases vb) auto`.\<close>
  sorry

lemma soundness_cmp_lt_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp LtOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp LtOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_cmp_le_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp LeOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp LeOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_cmp_gt_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp GtOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp GtOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by (simp add: order_le_less)

lemma soundness_cmp_ge_ints:
  assumes hl: "eval s st env l = Some (VInt a)"
      and hr: "eval s st env r = Some (VInt b)"
      and ihl: "value_to_smt_opt (eval s st env l)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate l)"
      and ihr: "value_to_smt_opt (eval s st env r)
                  = smt_eval (correlate_model s st) (correlate_env env) (translate r)"
  shows "value_to_smt_opt (eval s st env (Cmp GeOp l r))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (Cmp GeOp l r))"
  using hl hr smt_eval_of_eval_VInt[OF hl ihl] smt_eval_of_eval_VInt[OF hr ihr]
  by simp linarith

section \<open>LetIn binder\<close>

lemma soundness_let_in:
  assumes "eval s st env value_e = Some va"
      and "value_to_smt_opt (eval s st env value_e)
            = smt_eval (correlate_model s st) (correlate_env env) (translate value_e)"
      and "\<And>env'.
            value_to_smt_opt (eval s st env' body)
              = smt_eval (correlate_model s st) (correlate_env env') (translate body)"
  shows "value_to_smt_opt (eval s st env (LetIn x value_e body))
           = smt_eval (correlate_model s st) (correlate_env env) (translate (LetIn x value_e body))"
  using assms by (force split: option.splits smt_val.splits)

end
