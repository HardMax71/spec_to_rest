theory DirectSound_Smt
  imports
    Soundness_Framework
    SpecRest_Semantics.Translate
begin

text \<open>Smt-side plumbing for the direct soundness proof: value-level
  binop correspondence, free-variable irrelevance of \<open>smtEval\<close>, and the
  helpers relating the smt-side folds to their eval-side analogues.\<close>
section \<open>Plumbing: smt-side analogues of the eval-side helpers\<close>

lemma identName_smt_eq_relRefVarName [simp]:
  "identName_smt t = relRefVarName t"
  by (cases t) auto

lemma peel_relation_ref_smt_eq:
  "peel_relation_ref_smt t = peelSmtRelationRef t"
  by (cases t) auto

lemma peelSmt_tfd:
  "peelRelationRef base = Some rel \<Longrightarrow> translate enums base = Some bt
     \<Longrightarrow> peelSmtRelationRef bt = Some rel"
  by (cases base rule: peelRelationRef.cases) (auto dest!: identName_SomeD)

lemma expr_case_no_ident:
  "\<nexists>rel rsp. r = IdentifierF rel rsp
     \<Longrightarrow> (case r of IdentifierF rel _ \<Rightarrow> f rel | _ \<Rightarrow> y) = y"
  by (cases r) auto

lemma contains_value_set [simp]:
  "contains_value xs v = (v \<in> set xs)"
  by (induction xs) auto

lemma contains_smt_val_set [simp]:
  "contains_smt_val xs v = (v \<in> set xs)"
  by (induction xs) auto

lemma smtEval_forall_enum_eq_rel:
  "smtEval_forall_enum m env var srt members body
     = smtEval_forall_rel m env var (map (SEnumElem srt) members) body"
proof (induction members)
  case Nil
  show ?case by simp
next
  case (Cons mem rest)
  show ?case
  proof (cases "smtEval m ((var, SEnumElem srt mem) # env) body")
    case None
    then show ?thesis by simp
  next
    case (Some bv)
    show ?thesis using Some by (cases bv) (simp_all add: Cons.IH)
  qed
qed

lemma smtEval_the_rel_defined:
  "smtEval_the_rel m env var d body = Some ms \<Longrightarrow> v \<in> set d
     \<Longrightarrow> \<exists>b. smtEval m ((var, v) # env) body = Some (SBool b)"
proof (induction d arbitrary: ms)
  case Nil
  thus ?case by simp
next
  case (Cons x rest)
  from Cons.prems(1) obtain b matches
    where hb: "smtEval m ((var, x) # env) body = Some (SBool b)"
      and hr: "smtEval_the_rel m env var rest body = Some matches"
    by (auto split: option.splits smt_val.splits)
  show ?case using Cons.prems(2) hb Cons.IH[OF hr] by auto
qed

lemma smtEval_the_rel_filter:
  "smtEval_the_rel m env var d body = Some ms
     \<Longrightarrow> ms = filter (\<lambda>v. smtEval m ((var, v) # env) body = Some (SBool True)) d"
proof (induction d arbitrary: ms)
  case Nil
  thus ?case by simp
next
  case (Cons x rest)
  from Cons.prems obtain b matches
    where hb: "smtEval m ((var, x) # env) body = Some (SBool b)"
      and hr: "smtEval_the_rel m env var rest body = Some matches"
      and mseq: "ms = (if b then x # matches else matches)"
    by (auto split: option.splits smt_val.splits)
  show ?case using hb Cons.IH[OF hr] mseq by (cases b) auto
qed

section \<open>Value-level binop and unop correspondence\<close>

lemma TEq_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
  shows "smtEval m env (TEq lt rt) = Some (SBool (ir_val_eq a b))"
  using assms by (cases a; cases b) (auto simp: ir_val_eq_def)

lemma TLt_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_cmp LtOp (Some a) (Some b) = Some v"
  shows "smtEval m env (TLt lt rt) = Some (value_to_smt v)"
  using assms by (cases a; cases b) auto

lemma TGt_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_cmp GtOp (Some a) (Some b) = Some v"
  shows "smtEval m env (TLt rt lt) = Some (value_to_smt v)"
  using assms by (cases a; cases b) auto

lemma TLe_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_cmp LeOp (Some a) (Some b) = Some v"
  shows "smtEval m env (TOr (TLt lt rt) (TEq lt rt)) = Some (value_to_smt v)"
  using assms by (cases a; cases b) (auto simp: le_less)

lemma TGe_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_cmp GeOp (Some a) (Some b) = Some v"
  shows "smtEval m env (TOr (TLt rt lt) (TEq lt rt)) = Some (value_to_smt v)"
  using assms by (cases a; cases b) (auto simp: le_less)

lemma TAdd_sound:
  "smtEval m env lt = Some (value_to_smt a) \<Longrightarrow> smtEval m env rt = Some (value_to_smt b)
     \<Longrightarrow> eval_arith AddOp (Some a) (Some b) = Some v
     \<Longrightarrow> smtEval m env (TAdd lt rt) = Some (value_to_smt v)"
  by (cases a; cases b) (auto split: if_splits)

lemma TSub_sound:
  "smtEval m env lt = Some (value_to_smt a) \<Longrightarrow> smtEval m env rt = Some (value_to_smt b)
     \<Longrightarrow> eval_arith SubOp (Some a) (Some b) = Some v
     \<Longrightarrow> smtEval m env (TSub lt rt) = Some (value_to_smt v)"
  by (cases a; cases b) (auto split: if_splits)

lemma TMul_sound:
  "smtEval m env lt = Some (value_to_smt a) \<Longrightarrow> smtEval m env rt = Some (value_to_smt b)
     \<Longrightarrow> eval_arith MulOp (Some a) (Some b) = Some v
     \<Longrightarrow> smtEval m env (TMul lt rt) = Some (value_to_smt v)"
  by (cases a; cases b) (auto split: if_splits)

lemma TDiv_sound:
  "smtEval m env lt = Some (value_to_smt a) \<Longrightarrow> smtEval m env rt = Some (value_to_smt b)
     \<Longrightarrow> eval_arith DivOp (Some a) (Some b) = Some v
     \<Longrightarrow> smtEval m env (TDiv lt rt) = Some (value_to_smt v)"
  by (cases a; cases b) (auto split: if_splits)

lemma TArith_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_arith aop (Some a) (Some b) = Some v"
  shows "smtEval m env (case aop of AddOp \<Rightarrow> TAdd lt rt | SubOp \<Rightarrow> TSub lt rt
                          | MulOp \<Rightarrow> TMul lt rt | DivOp \<Rightarrow> TDiv lt rt)
           = Some (value_to_smt v)"
proof (cases aop)
  case AddOp
  hence ea: "eval_arith AddOp (Some a) (Some b) = Some v" using assms(3) by simp
  show ?thesis unfolding AddOp using TAdd_sound[OF assms(1) assms(2) ea] by simp
next
  case SubOp
  hence ea: "eval_arith SubOp (Some a) (Some b) = Some v" using assms(3) by simp
  show ?thesis unfolding SubOp using TSub_sound[OF assms(1) assms(2) ea] by simp
next
  case MulOp
  hence ea: "eval_arith MulOp (Some a) (Some b) = Some v" using assms(3) by simp
  show ?thesis unfolding MulOp using TMul_sound[OF assms(1) assms(2) ea] by simp
next
  case DivOp
  hence ea: "eval_arith DivOp (Some a) (Some b) = Some v" using assms(3) by simp
  show ?thesis unfolding DivOp using TDiv_sound[OF assms(1) assms(2) ea] by simp
qed

section \<open>Free-variable irrelevance on the smt side\<close>

fun smt_uses_var :: "String.literal \<Rightarrow> smt_term \<Rightarrow> bool" where
  "smt_uses_var x (TVar y)               = (y = x)"
| "smt_uses_var x (BLit _)               = False"
| "smt_uses_var x (ILit _)               = False"
| "smt_uses_var x (RLit _)               = False"
| "smt_uses_var x (EnumElemConst _ _)    = False"
| "smt_uses_var x (TNot t)               = smt_uses_var x t"
| "smt_uses_var x (TAnd l r)             = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TOr l r)              = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TImplies l r)         = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TEq l r)              = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TLt l r)              = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TNeg t)               = smt_uses_var x t"
| "smt_uses_var x (TAdd l r)             = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TSub l r)             = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TMul l r)             = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TDiv l r)             = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TInDom _ t)           = smt_uses_var x t"
| "smt_uses_var x (TCardRel _)           = False"
| "smt_uses_var x (TCard t)              = smt_uses_var x t"
| "smt_uses_var x (TLetIn y v b)         = (smt_uses_var x v \<or> (y \<noteq> x \<and> smt_uses_var x b))"
| "smt_uses_var x (TForallEnum y _ b)    = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TForallRel y _ b)     = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TExistsRel y _ b)     = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TTheRel y _ b)        = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TEntityBase _)        = False"
| "smt_uses_var x (TForallSet y setT b)  = (smt_uses_var x setT \<or> (y \<noteq> x \<and> smt_uses_var x b))"
| "smt_uses_var x (TTheSet y setT b)     = (smt_uses_var x setT \<or> (y \<noteq> x \<and> smt_uses_var x b))"
| "smt_uses_var x (TIndexRel b k)        = (smt_uses_var x b \<or> smt_uses_var x k)"
| "smt_uses_var x (TFieldAccess b _)     = smt_uses_var x b"
| "smt_uses_var x TSetEmpty              = False"
| "smt_uses_var x (TSetInsert e s)       = (smt_uses_var x e \<or> smt_uses_var x s)"
| "smt_uses_var x (TSetMember e s)       = (smt_uses_var x e \<or> smt_uses_var x s)"
| "smt_uses_var x (TSetUnion l r)        = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TSetIntersect l r)    = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TSetDiff l r)         = (smt_uses_var x l \<or> smt_uses_var x r)"
| "smt_uses_var x (TPrime t)             = smt_uses_var x t"
| "smt_uses_var x (TPre t)               = smt_uses_var x t"
| "smt_uses_var x (TWithRec b _ v)       = (smt_uses_var x b \<or> smt_uses_var x v)"
| "smt_uses_var x (TIte c a b)           = (smt_uses_var x c \<or> smt_uses_var x a \<or> smt_uses_var x b)"
| "smt_uses_var x TNone                  = False"
| "smt_uses_var x (TSome t)              = smt_uses_var x t"
| "smt_uses_var x (TStrLit _)            = False"
| "smt_uses_var x (TMatches t _)         = smt_uses_var x t"
| "smt_uses_var x (TUStrPred _ t)        = smt_uses_var x t"
| "smt_uses_var x (TUStrFunc _ t)        = smt_uses_var x t"
| "smt_uses_var x (TUIntFunc _ t)        = smt_uses_var x t"
| "smt_uses_var x (TStrLen t)            = smt_uses_var x t"
| "smt_uses_var x (TUConst _)            = False"
| "smt_uses_var x TSeqEmpty              = False"
| "smt_uses_var x (TSeqCons e r)         = (smt_uses_var x e \<or> smt_uses_var x r)"
| "smt_uses_var x TMapEmpty              = False"
| "smt_uses_var x (TMapCons k v r)       = (smt_uses_var x k \<or> smt_uses_var x v \<or> smt_uses_var x r)"
| "smt_uses_var x (TSum c _)             = smt_uses_var x c"

lemma smtEval_drop_irrelevant:
  "\<not> smt_uses_var x t \<or> map_of pre x \<noteq> None
     \<Longrightarrow> smtEval m (pre @ (x, xv) # post) t = smtEval m (pre @ post) t"
proof (induction t arbitrary: pre)
  case (TVar y)
  thus ?case by (auto simp: smt_env_lookup_def map_add_def split: option.splits)
next
  case (TLetIn y v b)
  have hv: "smtEval m (pre @ (x, xv) # post) v = smtEval m (pre @ post) v"
    using TLetIn.prems by (auto intro: TLetIn.IH(1))
  show ?case
  proof (cases "smtEval m (pre @ post) v")
    case None
    thus ?thesis using hv by simp
  next
    case (Some va)
    have "smtEval m (((y, va) # pre) @ (x, xv) # post) b = smtEval m (((y, va) # pre) @ post) b"
      using TLetIn.prems by (intro TLetIn.IH(2)) auto
    thus ?thesis using hv Some by simp
  qed
next
  case (TForallEnum y srt b)
  have "\<And>members. smtEval_forall_enum m (pre @ (x, xv) # post) y srt members b
          = smtEval_forall_enum m (pre @ post) y srt members b"
  proof -
    fix members
    show "smtEval_forall_enum m (pre @ (x, xv) # post) y srt members b
            = smtEval_forall_enum m (pre @ post) y srt members b"
    proof (induction members)
      case Nil
      show ?case by simp
    next
      case (Cons mem rest)
      have hb: "smtEval m (((y, SEnumElem srt mem) # pre) @ (x, xv) # post) b
              = smtEval m (((y, SEnumElem srt mem) # pre) @ post) b"
        using TForallEnum.prems by (intro TForallEnum.IH) auto
      show ?case
      proof (cases "smtEval m ((y, SEnumElem srt mem) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case by (simp split: option.splits)
next
  case (TForallRel y rel b)
  have "\<And>d. smtEval_forall_rel m (pre @ (x, xv) # post) y d b
          = smtEval_forall_rel m (pre @ post) y d b"
  proof -
    fix d
    show "smtEval_forall_rel m (pre @ (x, xv) # post) y d b
            = smtEval_forall_rel m (pre @ post) y d b"
    proof (induction d)
      case Nil
      show ?case by simp
    next
      case (Cons v rest)
      have hb: "smtEval m (((y, v) # pre) @ (x, xv) # post) b
              = smtEval m (((y, v) # pre) @ post) b"
        using TForallRel.prems by (intro TForallRel.IH) auto
      show ?case
      proof (cases "smtEval m ((y, v) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case by (simp split: option.splits)
next
  case (TTheRel y rel b)
  have "\<And>d. smtEval_the_rel m (pre @ (x, xv) # post) y d b
          = smtEval_the_rel m (pre @ post) y d b"
  proof -
    fix d
    show "smtEval_the_rel m (pre @ (x, xv) # post) y d b
            = smtEval_the_rel m (pre @ post) y d b"
    proof (induction d)
      case Nil
      show ?case by simp
    next
      case (Cons v rest)
      have hb: "smtEval m (((y, v) # pre) @ (x, xv) # post) b
              = smtEval m (((y, v) # pre) @ post) b"
        using TTheRel.prems by (intro TTheRel.IH) auto
      show ?case
      proof (cases "smtEval m ((y, v) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case by (simp split: option.splits)
next
  case (TExistsRel y rel b)
  have "\<And>d. smtEval_the_rel m (pre @ (x, xv) # post) y d b
          = smtEval_the_rel m (pre @ post) y d b"
  proof -
    fix d
    show "smtEval_the_rel m (pre @ (x, xv) # post) y d b
            = smtEval_the_rel m (pre @ post) y d b"
    proof (induction d)
      case Nil
      show ?case by simp
    next
      case (Cons v rest)
      have hb: "smtEval m (((y, v) # pre) @ (x, xv) # post) b
              = smtEval m (((y, v) # pre) @ post) b"
        using TExistsRel.prems by (intro TExistsRel.IH) auto
      show ?case
      proof (cases "smtEval m ((y, v) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case by (simp split: option.splits)
next
  case (TForallSet y setT b)
  have hs: "smtEval m (pre @ (x, xv) # post) setT = smtEval m (pre @ post) setT"
    using TForallSet.prems by (auto intro: TForallSet.IH(1))
  have "\<And>d. smtEval_forall_rel m (pre @ (x, xv) # post) y d b
          = smtEval_forall_rel m (pre @ post) y d b"
  proof -
    fix d
    show "smtEval_forall_rel m (pre @ (x, xv) # post) y d b
            = smtEval_forall_rel m (pre @ post) y d b"
    proof (induction d)
      case Nil
      show ?case by simp
    next
      case (Cons v rest)
      have hb: "smtEval m (((y, v) # pre) @ (x, xv) # post) b
              = smtEval m (((y, v) # pre) @ post) b"
        using TForallSet.prems by (intro TForallSet.IH(2)) auto
      show ?case
      proof (cases "smtEval m ((y, v) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case using hs by (simp split: option.splits smt_val.splits)
next
  case (TTheSet y setT b)
  have hs: "smtEval m (pre @ (x, xv) # post) setT = smtEval m (pre @ post) setT"
    using TTheSet.prems by (auto intro: TTheSet.IH(1))
  have "\<And>d. smtEval_the_rel m (pre @ (x, xv) # post) y d b
          = smtEval_the_rel m (pre @ post) y d b"
  proof -
    fix d
    show "smtEval_the_rel m (pre @ (x, xv) # post) y d b
            = smtEval_the_rel m (pre @ post) y d b"
    proof (induction d)
      case Nil
      show ?case by simp
    next
      case (Cons v rest)
      have hb: "smtEval m (((y, v) # pre) @ (x, xv) # post) b
              = smtEval m (((y, v) # pre) @ post) b"
        using TTheSet.prems by (intro TTheSet.IH(2)) auto
      show ?case
      proof (cases "smtEval m ((y, v) # pre @ post) b")
        case None
        thus ?thesis using hb by simp
      next
        case (Some bv)
        show ?thesis using hb Some Cons.IH by (cases bv) simp_all
      qed
    qed
  qed
  thus ?case using hs by (simp split: option.splits smt_val.splits)
next
  case (TIte c a b)
  have hc: "smtEval m (pre @ (x, xv) # post) c = smtEval m (pre @ post) c"
    using TIte.prems by (auto intro: TIte.IH(1))
  have ha: "smtEval m (pre @ (x, xv) # post) a = smtEval m (pre @ post) a"
    using TIte.prems by (auto intro: TIte.IH(2))
  have hb: "smtEval m (pre @ (x, xv) # post) b = smtEval m (pre @ post) b"
    using TIte.prems by (auto intro: TIte.IH(3))
  show ?case
  proof (cases "smtEval m (pre @ post) c")
    case None
    thus ?thesis using hc by simp
  next
    case (Some cv)
    show ?thesis using hc ha hb Some by (cases cv) (simp_all split: bool.splits)
  qed
qed (auto cong: option.case_cong smt_val.case_cong split: option.splits)

lemma smtEval_drop_head_irrelevant:
  "\<not> smt_uses_var x t
     \<Longrightarrow> smtEval m ((y, yv) # (x, xv) # env) t = smtEval m ((y, yv) # env) t"
  using smtEval_drop_irrelevant[where pre = "[(y, yv)]" and x = x and xv = xv and post = env]
  by simp

lemma smt_var_list_covers:
  "x \<notin> set (smt_var_list t) \<Longrightarrow> \<not> smt_uses_var x t"
  by (induction t) auto

end
