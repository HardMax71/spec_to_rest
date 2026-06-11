theory DirectSound
  imports
    Soundness_Framework
    SpecRest_Core.LowerMeaning
    SpecRest_Core.TranslateDirect
begin

section \<open>Plumbing: smt-side analogues of the eval-side helpers\<close>

lemma identName_smt_eq_relRefVarName [simp]:
  "identName_smt t = relRefVarName t"
  by (cases t) auto

lemma peel_relation_ref_smt_eq:
  "peel_relation_ref_smt t = peelSmtRelationRef t"
  by (cases t) auto

lemma peelSmt_tfd:
  "peelRelationRefFull base = Some rel \<Longrightarrow> translate_full_direct enums base = Some bt
     \<Longrightarrow> peelSmtRelationRef bt = Some rel"
  by (cases base rule: peelRelationRefFull.cases) (auto dest!: identNameFull_SomeD)

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

lemma no_cmp_var_list_member:
  "no_cmp_var_list es \<Longrightarrow> x \<in> set es \<Longrightarrow> no_cmp_var x"
  by (induction es) auto

lemma no_cmp_var_fields_member:
  "no_cmp_var_fields fas \<Longrightarrow> FieldAssignFull fld v sp3 \<in> set fas \<Longrightarrow> no_cmp_var v"
proof (induction fas)
  case Nil
  thus ?case by simp
next
  case (Cons fa fas')
  obtain f2 v2 s2 where "fa = FieldAssignFull f2 v2 s2" by (cases fa) auto
  thus ?case using Cons by auto
qed

lemma no_cmp_var_entries_member:
  "no_cmp_var_entries ents \<Longrightarrow> MapEntryFull k v sp3 \<in> set ents
     \<Longrightarrow> no_cmp_var k \<and> no_cmp_var v"
proof (induction ents)
  case Nil
  thus ?case by simp
next
  case (Cons en ents')
  obtain k2 v2 s2 where "en = MapEntryFull k2 v2 s2" by (cases en) auto
  thus ?case using Cons by auto
qed

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

lemma TArith_sound:
  assumes "smtEval m env lt = Some (value_to_smt a)"
      and "smtEval m env rt = Some (value_to_smt b)"
      and "eval_arith aop (Some a) (Some b) = Some v"
  shows "smtEval m env (case aop of AddOp \<Rightarrow> TAdd lt rt | SubOp \<Rightarrow> TSub lt rt
                          | MulOp \<Rightarrow> TMul lt rt | DivOp \<Rightarrow> TDiv lt rt)
           = Some (value_to_smt v)"
  using assms by (cases aop; cases a; cases b) (auto split: if_splits)

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
| "smt_uses_var x (TLetIn y v b)         = (smt_uses_var x v \<or> (y \<noteq> x \<and> smt_uses_var x b))"
| "smt_uses_var x (TForallEnum y _ b)    = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TForallRel y _ b)     = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TTheRel y _ b)        = (y \<noteq> x \<and> smt_uses_var x b)"
| "smt_uses_var x (TEntityBase _)        = False"
| "smt_uses_var x (TForallSet y setT b)  = (smt_uses_var x setT \<or> (y \<noteq> x \<and> smt_uses_var x b))"
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
| "smt_uses_var x TSeqEmpty              = False"
| "smt_uses_var x (TSeqCons e r)         = (smt_uses_var x e \<or> smt_uses_var x r)"
| "smt_uses_var x TMapEmpty              = False"
| "smt_uses_var x (TMapCons k v r)       = (smt_uses_var x k \<or> smt_uses_var x v \<or> smt_uses_var x r)"

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

section \<open>translate_full_direct introduces no free 0cmp\<close>

lemma direct_forall_step_no_0cmp:
  assumes "direct_forall_step enums b body = Some t"
      and "\<not> smt_uses_var (STR ''0cmp'') body"
  shows "\<not> smt_uses_var (STR ''0cmp'') t"
proof (cases b)
  case (QuantifierBindingFull v coll knd sp2)
  thus ?thesis using assms by (cases coll) (auto split: if_splits)
qed

lemma direct_forall_bindings_no_0cmp:
  "direct_forall_bindings enums bs body = Some t
     \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') body
     \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') t"
proof (induction bs arbitrary: t)
  case Nil
  thus ?case by simp
next
  case (Cons b bs')
  note IH = Cons.IH
  show ?case
  proof (cases bs')
    case Nil
    thus ?thesis using Cons.prems direct_forall_step_no_0cmp by simp
  next
    case (Cons b2 bs'')
    from Cons.prems(1) \<open>bs' = b2 # bs''\<close> obtain inner
      where hi: "direct_forall_bindings enums bs' body = Some inner"
        and hs: "direct_forall_step enums b inner = Some t"
      by (auto split: option.splits)
    have "\<not> smt_uses_var (STR ''0cmp'') inner"
      using IH[OF hi Cons.prems(2)] .
    thus ?thesis using direct_forall_step_no_0cmp[OF hs] by simp
  qed
qed

lemma direct_dom_eq_no_0cmp [simp]:
  "\<not> smt_uses_var (STR ''0cmp'') (direct_dom_eq rx ry)"
  by (simp add: direct_dom_eq_def)

lemma direct_set_comp_eq_no_0cmp:
  "\<not> smt_uses_var (STR ''0cmp'') lt
     \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') (direct_set_comp_eq enums var dnm lt pt)"
  by (simp add: Let_def)

lemma directSetList_no_0cmp:
  "(\<And>x tx. x \<in> set es \<Longrightarrow> translate_full_direct enums x = Some tx
      \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx)
     \<Longrightarrow> directSetList enums es = Some ts \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') ts"
proof (induction es arbitrary: ts)
  case Nil
  thus ?case by simp
next
  case (Cons a es')
  from Cons.prems(2) obtain at2 st2 where ha: "translate_full_direct enums a = Some at2"
      and hs: "directSetList enums es' = Some st2" and tseq: "ts = TSetInsert at2 st2"
    by (auto split: option.splits)
  have na: "\<not> smt_uses_var (STR ''0cmp'') at2" using Cons.prems(1)[of a at2] ha by simp
  have ns: "\<not> smt_uses_var (STR ''0cmp'') st2"
  proof (rule Cons.IH[OF _ hs])
    show "\<And>x tx. x \<in> set es' \<Longrightarrow> translate_full_direct enums x = Some tx
            \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
      using Cons.prems(1) by auto
  qed
  show ?case using tseq na ns by simp
qed

lemma directSeqList_no_0cmp:
  "(\<And>x tx. x \<in> set es \<Longrightarrow> translate_full_direct enums x = Some tx
      \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx)
     \<Longrightarrow> directSeqList enums es = Some ts \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') ts"
proof (induction es arbitrary: ts)
  case Nil
  thus ?case by simp
next
  case (Cons a es')
  from Cons.prems(2) obtain at2 st2 where ha: "translate_full_direct enums a = Some at2"
      and hs: "directSeqList enums es' = Some st2" and tseq: "ts = TSeqCons at2 st2"
    by (auto split: option.splits)
  have na: "\<not> smt_uses_var (STR ''0cmp'') at2" using Cons.prems(1)[of a at2] ha by simp
  have ns: "\<not> smt_uses_var (STR ''0cmp'') st2"
  proof (rule Cons.IH[OF _ hs])
    show "\<And>x tx. x \<in> set es' \<Longrightarrow> translate_full_direct enums x = Some tx
            \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
      using Cons.prems(1) by auto
  qed
  show ?case using tseq na ns by simp
qed

lemma directMapEntries_no_0cmp:
  "(\<And>k v sp3 tx. MapEntryFull k v sp3 \<in> set ents \<Longrightarrow> translate_full_direct enums k = Some tx
      \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx)
     \<Longrightarrow> (\<And>k v sp3 tx. MapEntryFull k v sp3 \<in> set ents \<Longrightarrow> translate_full_direct enums v = Some tx
            \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx)
     \<Longrightarrow> directMapEntries enums ents = Some mt \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') mt"
proof (induction ents arbitrary: mt)
  case Nil
  thus ?case by simp
next
  case (Cons en ents')
  obtain k v sp3 where en: "en = MapEntryFull k v sp3" by (cases en) auto
  from Cons.prems(3) en obtain kt vt mt'
    where hk: "translate_full_direct enums k = Some kt"
      and hv: "translate_full_direct enums v = Some vt"
      and hm: "directMapEntries enums ents' = Some mt'"
      and meq: "mt = TMapCons kt vt mt'"
    by (auto split: option.splits)
  have nk: "\<not> smt_uses_var (STR ''0cmp'') kt" using Cons.prems(1)[of k v sp3 kt] en hk by simp
  have nv: "\<not> smt_uses_var (STR ''0cmp'') vt" using Cons.prems(2)[of k v sp3 vt] en hv by simp
  have nm: "\<not> smt_uses_var (STR ''0cmp'') mt'"
  proof (rule Cons.IH[OF _ _ hm])
    show "\<And>k v sp3 tx. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> translate_full_direct enums k = Some tx \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
      using Cons.prems(1) by auto
    show "\<And>k v sp3 tx. MapEntryFull k v sp3 \<in> set ents'
            \<Longrightarrow> translate_full_direct enums v = Some tx \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
      using Cons.prems(2) by auto
  qed
  show ?case using meq nk nv nm by simp
qed

lemma direct_with_assigns_no_0cmp:
  "(\<And>fld v sp3 tv. FieldAssignFull fld v sp3 \<in> set fas \<Longrightarrow> translate_full_direct enums v = Some tv
      \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tv)
     \<Longrightarrow> direct_with_assigns enums fas bt = Some ft
     \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') bt \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') ft"
proof (induction fas arbitrary: bt ft)
  case Nil
  thus ?case by simp
next
  case (Cons fa fas')
  obtain fld v sp3 where fa: "fa = FieldAssignFull fld v sp3" by (cases fa) auto
  from Cons.prems(2) fa obtain vt
    where hv: "translate_full_direct enums v = Some vt"
      and hrest: "direct_with_assigns enums fas' (TWithRec bt fld vt) = Some ft"
    by (auto split: option.splits)
  have nv: "\<not> smt_uses_var (STR ''0cmp'') vt" using Cons.prems(1)[of fld v sp3 vt] fa hv by simp
  have nb: "\<not> smt_uses_var (STR ''0cmp'') (TWithRec bt fld vt)" using Cons.prems(3) nv by simp
  show ?case
  proof (rule Cons.IH[OF _ hrest nb])
    show "\<And>fld v sp3 tv. FieldAssignFull fld v sp3 \<in> set fas'
            \<Longrightarrow> translate_full_direct enums v = Some tv \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tv"
      using Cons.prems(1) by auto
  qed
qed

lemma tfd_no_0cmp:
  "translate_full_direct enums e = Some t \<Longrightarrow> no_cmp_var e
     \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') t"
proof (induction e arbitrary: t rule: measure_induct_rule[where f = size])
  case (less e)
  show ?case
  proof (cases e)
    case (IdentifierF x sp2)
    thus ?thesis using less.prems by auto
  next
    case (PrimeF c sp2)
    have s: "size c < size e" using PrimeF by simp
    show ?thesis using less.prems PrimeF less.IH[OF s] by (auto split: option.splits)
  next
    case (PreF c sp2)
    have s: "size c < size e" using PreF by simp
    show ?thesis using less.prems PreF less.IH[OF s] by (auto split: option.splits)
  next
    case (SomeWrapF c sp2)
    have s: "size c < size e" using SomeWrapF by simp
    show ?thesis using less.prems SomeWrapF less.IH[OF s] by (auto split: option.splits)
  next
    case (MatchesF c pat sp2)
    have s: "size c < size e" using MatchesF by simp
    show ?thesis using less.prems MatchesF less.IH[OF s] by (auto split: option.splits)
  next
    case (FieldAccessF c f sp2)
    have s: "size c < size e" using FieldAccessF by simp
    show ?thesis using less.prems FieldAccessF less.IH[OF s] by (auto split: option.splits)
  next
    case (EnumAccessF base mem sp2)
    thus ?thesis using less.prems by (cases base) (auto split: option.splits)
  next
    case (CallF c args sp2)
    show ?thesis
    proof (cases "\<exists>nm sp1 arg. c = IdentifierF nm sp1 \<and> args = [arg] \<and> is_builtin_pred nm")
      case True
      then obtain nm sp1 arg where ceq: "c = IdentifierF nm sp1" and aeq: "args = [arg]"
          and bip: "is_builtin_pred nm" by blast
      have s: "size arg < size e" using CallF aeq by simp
      show ?thesis using less.prems CallF ceq aeq bip less.IH[OF s]
        by (auto split: option.splits)
    next
      case False
      then have "translate_full_direct enums (CallF c args sp2) = None"
        by (auto split: expr_full.splits list.splits if_splits)
      thus ?thesis using less.prems CallF by simp
    qed
  next
    case (IndexF base key sp2)
    have sb: "size base < size e" and sk: "size key < size e" using IndexF by simp_all
    show ?thesis using less.prems IndexF less.IH[OF sb] less.IH[OF sk]
      by (auto split: option.splits)
  next
    case (IfF c a b sp2)
    have sc: "size c < size e" and sa: "size a < size e" and sb: "size b < size e"
      using IfF by simp_all
    show ?thesis using less.prems IfF less.IH[OF sc] less.IH[OF sa] less.IH[OF sb]
      by (auto split: option.splits)
  next
    case (LetF x v body sp2)
    have sv: "size v < size e" and sb: "size body < size e" using LetF by simp_all
    show ?thesis using less.prems LetF less.IH[OF sv] less.IH[OF sb]
      by (auto split: option.splits)
  next
    case (TheF var dm body sp2)
    show ?thesis
    proof (cases dm)
      case (IdentifierF rel rsp)
      have s: "size body < size e" using TheF by simp
      show ?thesis using less.prems TheF IdentifierF less.IH[OF s]
        by (auto split: option.splits if_splits)
    qed (use less.prems TheF in \<open>auto\<close>)
  next
    case (UnaryOpF op2 c sp2)
    have s: "size c < size e" using UnaryOpF by simp
    show ?thesis using less.prems UnaryOpF less.IH[OF s]
      by (cases op2) (auto split: option.splits expr_full.splits)
  next
    case (QuantifierF k bs body sp2)
    have s: "size body < size e" using QuantifierF by simp
    from less.prems QuantifierF obtain bt where hb: "translate_full_direct enums body = Some bt"
      by (auto split: option.splits)
    have nb: "\<not> smt_uses_var (STR ''0cmp'') bt"
      using less.IH[OF s hb] less.prems(2) QuantifierF by simp
    show ?thesis
    proof (cases k)
      case QAll
      with less.prems(1) QuantifierF hb show ?thesis
        using direct_forall_bindings_no_0cmp[OF _ nb] by (auto split: option.splits)
    next
      case QNo
      with less.prems(1) QuantifierF hb show ?thesis
        using direct_forall_bindings_no_0cmp nb by (auto split: option.splits)
    next
      case QSome
      with less.prems(1) QuantifierF hb obtain inner
        where bnd: "direct_forall_bindings enums bs (TNot bt) = Some inner"
          and teq: "t = TNot inner"
        by (auto split: option.splits)
      have "\<not> smt_uses_var (STR ''0cmp'') inner"
        using direct_forall_bindings_no_0cmp[OF bnd] nb by simp
      thus ?thesis using teq by simp
    next
      case QExists
      with less.prems(1) QuantifierF hb obtain inner
        where bnd: "direct_forall_bindings enums bs (TNot bt) = Some inner"
          and teq: "t = TNot inner"
        by (auto split: option.splits)
      have "\<not> smt_uses_var (STR ''0cmp'') inner"
        using direct_forall_bindings_no_0cmp[OF bnd] nb by simp
      thus ?thesis using teq by simp
    qed
  next
    case (ConstructorF name fas sp2)
    have wa: "direct_with_assigns enums fas (TEntityBase name) = Some t"
      using less.prems(1) ConstructorF by simp
    show ?thesis
    proof (rule direct_with_assigns_no_0cmp[OF _ wa])
      show "\<not> smt_uses_var (STR ''0cmp'') (TEntityBase name)" by simp
      fix fld v sp3 tv assume m: "FieldAssignFull fld v sp3 \<in> set fas"
        and hv: "translate_full_direct enums v = Some tv"
      have "size v < size e"
        using ConstructorF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "no_cmp_var v"
        using no_cmp_var_fields_member[OF _ m] less.prems(2) ConstructorF by simp
      ultimately show "\<not> smt_uses_var (STR ''0cmp'') tv" using less.IH hv by blast
    qed
  next
    case (WithF base upds sp2)
    from less.prems(1) WithF obtain bt
      where hbase: "translate_full_direct enums base = Some bt"
        and hwa: "direct_with_assigns enums upds bt = Some t"
      by (auto split: option.splits)
    have sbase: "size base < size e" using WithF by simp
    have nb: "\<not> smt_uses_var (STR ''0cmp'') bt"
      using less.IH[OF sbase hbase] less.prems(2) WithF by simp
    show ?thesis
    proof (rule direct_with_assigns_no_0cmp[OF _ hwa nb])
      fix fld v sp3 tv assume m: "FieldAssignFull fld v sp3 \<in> set upds"
        and hv: "translate_full_direct enums v = Some tv"
      have "size v < size e"
        using WithF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "no_cmp_var v"
        using no_cmp_var_fields_member[OF _ m] less.prems(2) WithF by simp
      ultimately show "\<not> smt_uses_var (STR ''0cmp'') tv" using less.IH hv by blast
    qed
  next
    case (SetLiteralF elems sp2)
    show ?thesis
    proof (rule directSetList_no_0cmp)
      show "directSetList enums elems = Some t" using less.prems(1) SetLiteralF by simp
      fix x tx assume m: "x \<in> set elems" and hx: "translate_full_direct enums x = Some tx"
      have "size x < size e"
        using SetLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "no_cmp_var x"
        using no_cmp_var_list_member[OF _ m] less.prems(2) SetLiteralF by simp
      ultimately show "\<not> smt_uses_var (STR ''0cmp'') tx" using less.IH hx by blast
    qed
  next
    case (SeqLiteralF elems sp2)
    show ?thesis
    proof (rule directSeqList_no_0cmp)
      show "directSeqList enums elems = Some t" using less.prems(1) SeqLiteralF by simp
      fix x tx assume m: "x \<in> set elems" and hx: "translate_full_direct enums x = Some tx"
      have "size x < size e"
        using SeqLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp
      moreover have "no_cmp_var x"
        using no_cmp_var_list_member[OF _ m] less.prems(2) SeqLiteralF by simp
      ultimately show "\<not> smt_uses_var (STR ''0cmp'') tx" using less.IH hx by blast
    qed
  next
    case (MapLiteralF entries sp2)
    show ?thesis
    proof (rule directMapEntries_no_0cmp)
      show "directMapEntries enums entries = Some t" using less.prems(1) MapLiteralF by simp
      fix k v sp3 tx assume m: "MapEntryFull k v sp3 \<in> set entries"
      have sk: "size k < size e" and sv: "size v < size e"
        using MapLiteralF size_list_estimation'[OF m order_refl, where f = size] by simp_all
      have nk: "no_cmp_var k" and nv: "no_cmp_var v"
        using no_cmp_var_entries_member[OF _ m] less.prems(2) MapLiteralF by simp_all
      show "translate_full_direct enums k = Some tx \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
        using less.IH[OF sk _ nk] by blast
      show "translate_full_direct enums v = Some tx \<Longrightarrow> \<not> smt_uses_var (STR ''0cmp'') tx"
        using less.IH[OF sv _ nv] by blast
    qed
  next
    case (BinaryOpF op2 l2 r2 sp2)
    have sl: "size l2 < size e" and sr: "size r2 < size e" using BinaryOpF by simp_all
    have ncl: "no_cmp_var l2" and ncr: "no_cmp_var r2"
      using less.prems(2) BinaryOpF by simp_all
    show ?thesis
    proof (cases op2)
      case BEq
      show ?thesis
      proof (cases "direct_beq_dom_or_none l2 r2")
        case (Some dt)
        hence teq: "t = dt" using less.prems(1) BinaryOpF BEq by simp
        have "\<not> smt_uses_var (STR ''0cmp'') dt"
          using Some by (auto simp: direct_beq_dom_or_none_def split: option.splits)
        thus ?thesis using teq by simp
      next
        case None
        show ?thesis
        proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
          case True
          then obtain cvar dnm s2 cpred s3
            where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
          from less.prems(1) BinaryOpF BEq None req obtain lt pt
            where hl: "translate_full_direct enums l2 = Some lt"
              and teq: "t = direct_set_comp_eq enums cvar dnm lt pt"
            by (auto split: option.splits)
          have "\<not> smt_uses_var (STR ''0cmp'') lt" using less.IH[OF sl hl ncl] .
          thus ?thesis using teq direct_set_comp_eq_no_0cmp by simp
        next
          case False
          hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
            by blast
          have dnone: "dom_arg l2 = None \<or> dom_arg r2 = None"
            using None by (auto simp: direct_beq_dom_or_none_def split: option.splits)
          from less.prems(1)[unfolded BinaryOpF BEq direct_BEq_noncomp[OF nc dnone]]
          obtain lt rt where hl: "translate_full_direct enums l2 = Some lt"
              and hr: "translate_full_direct enums r2 = Some rt" and teq: "t = TEq lt rt"
            by (auto split: option.splits)
          show ?thesis using less.IH[OF sl hl ncl] less.IH[OF sr hr ncr] teq by simp
        qed
      qed
    next
      case BIn
      show ?thesis
      proof (cases "\<exists>cvar dnm s2 cpred s3. r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3")
        case True
        then obtain cvar dnm s2 cpred s3
          where req: "r2 = SetComprehensionF cvar (IdentifierF dnm s2) cpred s3" by blast
        have spred: "size cpred < size e" using BinaryOpF req by simp
        have ncp: "no_cmp_var cpred" and cv: "cvar \<noteq> STR ''0cmp''"
          using ncr req by simp_all
        from less.prems(1) BinaryOpF BIn req obtain lt pt
          where hl: "translate_full_direct enums l2 = Some lt"
            and hp: "translate_full_direct enums cpred = Some pt"
            and teq: "t = TLetIn cvar lt
                          (if string_in_list dnm enums then pt
                           else TAnd (TInDom dnm (TVar cvar)) pt)"
          by (auto split: option.splits)
        have nl: "\<not> smt_uses_var (STR ''0cmp'') lt" using less.IH[OF sl hl ncl] .
        have np: "\<not> smt_uses_var (STR ''0cmp'') pt" using less.IH[OF spred hp ncp] .
        show ?thesis using teq nl np cv by (auto split: if_splits)
      next
        case False
        hence nc: "\<nexists>var dnm sp3 p sp4. r2 = SetComprehensionF var (IdentifierF dnm sp3) p sp4"
          by blast
        show ?thesis
        proof (cases "\<exists>rel rsp. r2 = IdentifierF rel rsp")
          case True
          then obtain rel rsp where rid: "r2 = IdentifierF rel rsp" by blast
          from less.prems(1) BinaryOpF BIn rid obtain lt
            where hl: "translate_full_direct enums l2 = Some lt" and teq: "t = TInDom rel lt"
            by (auto split: option.splits)
          show ?thesis using less.IH[OF sl hl ncl] teq by simp
        next
          case False
          from less.prems(1)[unfolded BinaryOpF BIn direct_BIn_noncomp[OF nc]
                              expr_case_no_ident[OF False]]
          obtain lt rt where hl: "translate_full_direct enums l2 = Some lt"
              and hr: "translate_full_direct enums r2 = Some rt" and teq: "t = TSetMember lt rt"
            by (auto split: option.splits)
          show ?thesis using less.IH[OF sl hl ncl] less.IH[OF sr hr ncr] teq by simp
        qed
      qed
    next
      case BNotIn
      show ?thesis
      proof (cases "\<exists>rel rsp. r2 = IdentifierF rel rsp")
        case True
        then obtain rel rsp where rid: "r2 = IdentifierF rel rsp" by blast
        from less.prems(1) BinaryOpF BNotIn rid obtain lt
          where hl: "translate_full_direct enums l2 = Some lt"
            and teq: "t = TNot (TInDom rel lt)"
          by (auto split: option.splits)
        show ?thesis using less.IH[OF sl hl ncl] teq by simp
      next
        case False
        have beq: "translate_full_direct enums e
                     = (case r2 of
                          IdentifierF rel _ \<Rightarrow>
                            map_option (\<lambda>lt. TNot (TInDom rel lt)) (translate_full_direct enums l2)
                        | _ \<Rightarrow> (case (translate_full_direct enums l2, translate_full_direct enums r2) of
                                  (Some lt, Some rt) \<Rightarrow> Some (TNot (TSetMember lt rt)) | _ \<Rightarrow> None))"
          using BinaryOpF BNotIn by simp
        from less.prems(1)[unfolded beq expr_case_no_ident[OF False]]
        obtain lt rt where hl: "translate_full_direct enums l2 = Some lt"
            and hr: "translate_full_direct enums r2 = Some rt"
            and teq: "t = TNot (TSetMember lt rt)"
          by (auto split: option.splits)
        show ?thesis using less.IH[OF sl hl ncl] less.IH[OF sr hr ncr] teq by simp
      qed
    qed (use less.prems(1) BinaryOpF less.IH[OF sl] less.IH[OF sr] ncl ncr
          in \<open>auto split: option.splits\<close>)
  qed (use less.prems in \<open>auto split: option.splits\<close>)
qed

section \<open>The dom-equality term evaluates to the domains' set equality\<close>

lemma vts_mem_image [simp]:
  "(value_to_smt x \<in> value_to_smt ` S) = (x \<in> S)"
  by (auto simp: image_iff)

lemma smtEval_forall_rel_indom:
  "smt_model_lookup_rel m rel = Some d
     \<Longrightarrow> smtEval_forall_rel m env x0 svs (TInDom rel (TVar x0))
         = Some (SBool (list_all (contains_smt_val d) svs))"
proof (induction svs)
  case Nil
  show ?case by simp
next
  case (Cons sv rest)
  thus ?case by (auto simp: smt_env_lookup_def)
qed

lemma direct_dom_eq_sound:
  assumes dx: "state_relation_domain st rx = Some dx"
      and dy: "state_relation_domain st ry = Some dy"
  shows "smtEval (correlate_model s st) ce (direct_dom_eq rx ry)
           = Some (SBool (set dx = set dy))"
proof -
  have lx: "smt_model_lookup_rel (correlate_model s st) rx = Some (map value_to_smt dx)"
    using dx by simp
  have ly: "smt_model_lookup_rel (correlate_model s st) ry = Some (map value_to_smt dy)"
    using dy by simp
  have d1: "smtEval (correlate_model s st) ce
              (TForallRel (STR ''0cmp'') rx (TInDom ry (TVar (STR ''0cmp''))))
              = Some (SBool (set dx \<subseteq> set dy))"
    using lx ly smtEval_forall_rel_indom[OF ly]
    by (auto simp: list_all_iff subset_iff)
  have d2: "smtEval (correlate_model s st) ce
              (TForallRel (STR ''0cmp'') ry (TInDom rx (TVar (STR ''0cmp''))))
              = Some (SBool (set dy \<subseteq> set dx))"
    using lx ly smtEval_forall_rel_indom[OF lx]
    by (auto simp: list_all_iff subset_iff)
  show ?thesis using d1 d2 by (simp add: direct_dom_eq_def set_eq_subset)
qed

section \<open>The comprehension-equality term evaluates to set equality\<close>

lemma inj_value_to_smt: "inj value_to_smt"
  by (simp add: inj_def)

lemma set_map_vts_subset [simp]:
  "(value_to_smt ` A \<subseteq> value_to_smt ` B) = (A \<subseteq> B)"
  by (auto simp: subset_iff)

lemma set_map_vts_eq [simp]:
  "(value_to_smt ` A = value_to_smt ` B) = (A = B)"
  by (simp add: set_eq_subset)

lemma smtEval_the_rel_mem:
  "smtEval_the_rel m env var d body = Some ms \<Longrightarrow> v \<in> set d
     \<Longrightarrow> smtEval m ((var, v) # env) body = Some (SBool b) \<Longrightarrow> (v \<in> set ms) = b"
  using smtEval_the_rel_filter by fastforce

lemma smt_comp_dir1:
  assumes ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and dropp: "\<And>d. smtEval m ((var, d) # (STR ''0cmp'', SSet xst) # env) pt
                       = smtEval m ((var, d) # env) pt"
      and vne: "var \<noteq> STR ''0cmp''"
  shows "smtEval_forall_rel m ((STR ''0cmp'', SSet xst) # env) var svs
            (TImplies pt (TSetMember (TVar var) (TVar (STR ''0cmp''))))
           = Some (SBool (set mst \<subseteq> set xst))"
  using ethe
proof (induction svs arbitrary: mst)
  case Nil
  thus ?case by simp
next
  case (Cons w rest)
  from Cons.prems obtain b matches where
      wb: "smtEval m ((var, w) # env) pt = Some (SBool b)"
      and mr: "smtEval_the_rel m env var rest pt = Some matches"
      and ms_eq: "mst = (if b then w # matches else matches)"
    by (auto split: option.splits smt_val.splits)
  have ev: "smtEval m ((var, w) # (STR ''0cmp'', SSet xst) # env)
              (TImplies pt (TSetMember (TVar var) (TVar (STR ''0cmp''))))
            = Some (SBool (\<not> b \<or> w \<in> set xst))"
    using wb dropp[of w] vne by (simp add: smt_env_lookup_def)
  have IH: "smtEval_forall_rel m ((STR ''0cmp'', SSet xst) # env) var rest
              (TImplies pt (TSetMember (TVar var) (TVar (STR ''0cmp''))))
            = Some (SBool (set matches \<subseteq> set xst))"
    using Cons.IH[OF mr] .
  show ?case using ev IH ms_eq by auto
qed

lemma smt_comp_dir2:
  assumes ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and dropp: "\<And>d. smtEval m ((var, d) # (STR ''0cmp'', SSet xst) # env) pt
                       = smtEval m ((var, d) # env) pt"
      and vne: "var \<noteq> STR ''0cmp''"
      and srd: "smt_model_lookup_rel m dnm = Some svs"
      and la: "list_all (\<lambda>x. contains_smt_val svs x) yst"
  shows "smtEval_forall_rel m ((STR ''0cmp'', SSet xst) # env) var yst
            (TAnd (TInDom dnm (TVar var)) pt)
           = Some (SBool (set yst \<subseteq> set mst))"
  using la
proof (induction yst)
  case Nil
  thus ?case by simp
next
  case (Cons x rest)
  from Cons.prems have xsvs: "x \<in> set svs"
      and larest: "list_all (\<lambda>x. contains_smt_val svs x) rest"
    by auto
  obtain b where bx: "smtEval m ((var, x) # env) pt = Some (SBool b)"
    using smtEval_the_rel_defined[OF ethe xsvs] by blast
  have xms: "(x \<in> set mst) = b"
    using smtEval_the_rel_mem[OF ethe xsvs bx] .
  have ev: "smtEval m ((var, x) # (STR ''0cmp'', SSet xst) # env)
              (TAnd (TInDom dnm (TVar var)) pt) = Some (SBool b)"
    using bx dropp[of x] srd xsvs by (simp add: smt_env_lookup_def)
  have IH: "smtEval_forall_rel m ((STR ''0cmp'', SSet xst) # env) var rest
              (TAnd (TInDom dnm (TVar var)) pt)
            = Some (SBool (set rest \<subseteq> set mst))"
    using Cons.IH[OF larest] .
  show ?case using ev IH xms by auto
qed

lemma smt_comp_assembly:
  assumes elt: "smtEval m env lt = Some (SSet xst)"
      and ethe: "smtEval_the_rel m env var svs pt = Some mst"
      and srd: "smt_model_lookup_rel m dnm = Some svs"
      and la: "list_all (\<lambda>x. contains_smt_val svs x) xst"
      and vne: "var \<noteq> STR ''0cmp''"
      and fp: "\<not> smt_uses_var (STR ''0cmp'') pt"
      and dne: "\<not> string_in_list dnm enums"
  shows "smtEval m env (direct_set_comp_eq enums var dnm lt pt)
           = Some (SBool (set xst = set mst))"
proof -
  have dropp: "\<And>d. smtEval m ((var, d) # (STR ''0cmp'', SSet xst) # env) pt
                    = smtEval m ((var, d) # env) pt"
  proof -
    fix d
    show "smtEval m ((var, d) # (STR ''0cmp'', SSet xst) # env) pt
            = smtEval m ((var, d) # env) pt"
      by (rule smtEval_drop_head_irrelevant[OF fp])
  qed
  have d1: "smtEval m ((STR ''0cmp'', SSet xst) # env)
              (TForallRel var dnm (TImplies pt (TSetMember (TVar var) (TVar (STR ''0cmp'')))))
            = Some (SBool (set mst \<subseteq> set xst))"
    using smt_comp_dir1[OF ethe dropp vne] srd by simp
  have d2: "smtEval m ((STR ''0cmp'', SSet xst) # env)
              (TForallSet var (TVar (STR ''0cmp'')) (TAnd (TInDom dnm (TVar var)) pt))
            = Some (SBool (set xst \<subseteq> set mst))"
    using smt_comp_dir2[OF ethe dropp vne srd la] by (simp add: smt_env_lookup_def)
  show ?thesis
    using elt d1 d2 dne by (auto simp: Let_def set_eq_subset)
qed

lemma list_all_contains_map:
  "list_all (\<lambda>x. contains_smt_val (map value_to_smt svs) x) (map value_to_smt xs)
     = list_all (\<lambda>x. contains_value svs x) xs"
  by (induction xs) auto

section \<open>Direct soundness: eval_full agrees with smtEval of translate_full_direct\<close>

lemma direct_soundness:
  "eval_full fs ps fuel s st env e = Some v \<Longrightarrow> translate_full_direct enums e = Some t
     \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var e \<Longrightarrow> builtins_reserved fs ps
     \<Longrightarrow> smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
  "eval_full_list fs ps fuel s st env es = Some vs \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_list es \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>t. directSeqList enums es = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t = Some (SSeq (map value_to_smt vs))) \<and>
     (\<forall>t. directSetList enums es = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t
          = Some (SSet (map value_to_smt (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))))"
  "eval_full_entries fs ps fuel s st env ents = Some mps \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_entries ents \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>t. directMapEntries enums ents = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t = Some (SMap (value_to_smt_entries mps)))"
  "eval_full_fields fs ps fuel s st env fas = Some fvs \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var_fields fas \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt t bv. smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv) \<longrightarrow>
        direct_with_assigns enums fas bt = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t
          = Some (value_to_smt (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)))"
  "eval_full_the fs ps fuel s st env var dmv body = Some tms \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var body \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt. translate_full_direct enums body = Some bt \<longrightarrow>
        smtEval_the_rel (correlate_model s st) (correlate_env env) var (map value_to_smt dmv) bt
          = Some (map value_to_smt tms))"
  "eval_full_forall fs ps fuel s st env var dmv body = Some fr \<Longrightarrow> enums_wf s enums \<Longrightarrow> no_cmp_var body \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt. translate_full_direct enums body = Some bt \<longrightarrow>
        smtEval_forall_rel (correlate_model s st) (correlate_env env) var (map value_to_smt dmv) bt
          = Some (value_to_smt fr))"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body
        and fs ps fuel s st env var dmv body
        arbitrary: v t and vs and mps and fvs and tms and fr
        rule: eval_full_eval_full_list_eval_full_entries_eval_full_fields_eval_full_the_eval_full_forall.induct)
  case (6 fs ps fuel s st env bop l r sp v t)
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
    have teq: "t = direct_dom_eq rx ry"
      using "6.prems"(2)[unfolded f0] f2 f3
      by (simp add: direct_beq_dom_or_none_def)
    show ?thesis using teq veq direct_dom_eq_sound[OF f2d f3d] by simp
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
      from "6.prems" BAnd obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TAnd lt rt"
        by (auto split: option.splits)
      show ?thesis using teq "6.prems" BAnd efl efr
          IHl[OF deN None efl tl] IHr[OF deN None efr tr] by simp
    next
      case BOr
      from "6.prems" BOr obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
          and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BOr obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TOr lt rt"
        by (auto split: option.splits)
      show ?thesis using teq "6.prems" BOr efl efr
          IHl[OF deN None efl tl] IHr[OF deN None efr tr] by simp
    next
      case BImplies
      from "6.prems" BImplies obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
          and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BImplies obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TImplies lt rt"
        by (auto split: option.splits)
      show ?thesis using teq "6.prems" BImplies efl efr
          IHl[OF deN None efl tl] IHr[OF deN None efr tr] by simp
    next
      case BIff
      from "6.prems" BIff obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
          and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BIff obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt"
          and teq: "t = TAnd (TImplies lt rt) (TImplies rt lt)"
        by (auto split: option.splits)
      have veq: "v = VBool (a = b)" using "6.prems"(1) BIff deN None efl efr by simp
      show ?thesis using teq veq efl efr "6.prems"
          IHl[OF deN None efl tl] IHr[OF deN None efr tr] by auto
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
      from "6.prems"(2)[unfolded BEq direct_BEq_noncomp[OF rnc dnone]] obtain lt rt
          where tl: "translate_full_direct enums l = Some lt"
            and tr: "translate_full_direct enums r = Some rt"
            and teq: "t = TEq lt rt"
        by (auto split: option.splits)
      have veq: "v = VBool (ir_val_eq vl vr)"
        using "6.prems"(1) BEq deN None efl efr by simp
      show ?thesis using teq veq "6.prems"
          TEq_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr]] by simp
    next
      case BNeq
      from "6.prems" BNeq obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits)
      from "6.prems" BNeq obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TNot (TEq lt rt)"
        by (auto split: option.splits)
      have veq: "v = VBool (\<not> ir_val_eq vl vr)"
        using "6.prems"(1) BNeq deN None efl efr by simp
      show ?thesis using teq veq "6.prems"
          TEq_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr]] by simp
    next
      case BLt
      from "6.prems" BLt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits)
      from "6.prems" BLt obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TLt lt rt"
        by (auto split: option.splits)
      have ec: "eval_cmp LtOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BLt deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TLt_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BGt
      from "6.prems" BGt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits)
      from "6.prems" BGt obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TLt rt lt"
        by (auto split: option.splits)
      have ec: "eval_cmp GtOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BGt deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TGt_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BLe
      from "6.prems" BLe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits)
      from "6.prems" BLe obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt"
          and teq: "t = TOr (TLt lt rt) (TEq lt rt)"
        by (auto split: option.splits)
      have ec: "eval_cmp LeOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BLe deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TLe_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BGe
      from "6.prems" BGe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits)
      from "6.prems" BGe obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt"
          and teq: "t = TOr (TLt rt lt) (TEq lt rt)"
        by (auto split: option.splits)
      have ec: "eval_cmp GeOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BGe deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TGe_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BAdd
      from "6.prems" BAdd obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BAdd obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TAdd lt rt"
        by (auto split: option.splits)
      have ec: "eval_arith AddOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BAdd deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TArith_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BSub
      from "6.prems" BSub obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BSub obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TSub lt rt"
        by (auto split: option.splits)
      have ec: "eval_arith SubOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BSub deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TArith_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BMul
      from "6.prems" BMul obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BMul obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TMul lt rt"
        by (auto split: option.splits)
      have ec: "eval_arith MulOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BMul deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TArith_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
    next
      case BDiv
      from "6.prems" BDiv obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
          and efr: "eval_full fs ps fuel s st env r = Some vr"
        by (auto split: option.splits ir_value.splits)
      from "6.prems" BDiv obtain lt rt where tl: "translate_full_direct enums l = Some lt"
          and tr: "translate_full_direct enums r = Some rt" and teq: "t = TDiv lt rt"
        by (auto split: option.splits)
      have ec: "eval_arith DivOp (Some vl) (Some vr) = Some v"
        using "6.prems"(1) BDiv deN None efl efr by simp
      show ?thesis using teq "6.prems"
          TArith_sound[OF IHl[OF deN None efl tl] IHr[OF deN None efr tr] ec] by simp
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
    case (Some tc)
    then obtain var dnm pred where bc: "beq_comp bop r = Some (var, dnm, pred)"
      by (cases tc) auto
    have bopeq: "bop = BEq" using bc by (cases bop) auto
    from bc bopeq obtain s2 s3 where req: "r = SetComprehensionF var (IdentifierF dnm s2) pred s3"
      by (cases r) (auto split: expr_full.splits)
    from "6.prems"(1) deN bc obtain dvs xs where
        enr: "schema_lookup_enum s dnm = None"
        and srd: "state_relation_domain st dnm = Some dvs"
        and efl: "eval_full fs ps fuel s st env l = Some (VSet xs)"
        and la: "list_all (\<lambda>x. contains_value dvs x) xs"
      by (fastforce split: if_splits option.splits ir_value.splits)
    from "6.prems"(1) deN bc enr srd efl obtain ms where
        etm: "eval_full_the fs ps fuel s st env var dvs pred = Some ms"
        and veq: "v = VBool (set xs = set ms)"
      by (auto split: if_splits option.splits ir_value.splits)
    have lcdom: "lookup_callee fs ps (STR ''dom'') = None"
      using "6.prems"(5) by (simp add: builtins_reserved_def)
    have dnone: "dom_arg l = None"
    proof (rule ccontr)
      assume "dom_arg l \<noteq> None"
      then obtain rx where "dom_arg l = Some rx" by auto
      then obtain a b c where "l = CallF (IdentifierF (STR ''dom'') a) [IdentifierF rx b] c"
        using dom_arg_SomeD by blast
      hence "eval_full fs ps fuel s st env l = None" using eval_full_dom_CallF[OF lcdom] by simp
      thus False using efl by simp
    qed
    have bn: "direct_beq_dom_or_none l r = None"
      using dnone by (auto simp: direct_beq_dom_or_none_def split: option.splits)
    from "6.prems"(2) bopeq req bn obtain lt pt where
        tl: "translate_full_direct enums l = Some lt"
        and tp: "translate_full_direct enums pred = Some pt"
        and teq: "t = direct_set_comp_eq enums var dnm lt pt"
      by (auto split: option.splits)
    have ncl: "no_cmp_var l" using "6.prems"(4) bopeq by simp
    have ncp: "no_cmp_var pred" using "6.prems"(4) req by simp
    have vne: "var \<noteq> STR ''0cmp''" using "6.prems"(4) req by simp
    have dne: "\<not> string_in_list dnm enums" by (rule sil_none[OF "6.prems"(3) enr])
    have fp: "\<not> smt_uses_var (STR ''0cmp'') pt"
      by (rule tfd_no_0cmp[OF tp ncp])
    have enr': "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
    have etr: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                 (map value_to_smt dvs) pt = Some (map value_to_smt ms)"
      using "6.IH"(3)[OF deN bc refl refl enr' srd etm "6.prems"(3) ncp "6.prems"(5)] tp by blast
    have elt: "smtEval (correlate_model s st) (correlate_env env) lt
                 = Some (SSet (map value_to_smt xs))"
      using "6.IH"(4)[OF deN bc refl refl enr' srd etm efl tl "6.prems"(3) ncl "6.prems"(5)] by simp
    have srd': "smt_model_lookup_rel (correlate_model s st) dnm = Some (map value_to_smt dvs)"
      using srd by simp
    have la': "list_all (\<lambda>x. contains_smt_val (map value_to_smt dvs) x) (map value_to_smt xs)"
      using la by (auto simp: list_all_iff)
    show ?thesis
      using teq veq
        smt_comp_assembly[OF elt etr srd' la' vne fp dne]
      by simp
  qed
  qed
next
  case (7 fs ps fuel s st env uop e sp v t)
  show ?case
  proof (cases uop)
    case UNot
    from "7.prems" UNot obtain b where ef: "eval_full fs ps fuel s st env e = Some (VBool b)"
        and veq: "v = VBool (\<not> b)"
      by (auto split: option.splits ir_value.splits)
    from "7.prems" UNot obtain et where te: "translate_full_direct enums e = Some et"
        and teq: "t = TNot et"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) et = Some (SBool b)"
      using "7.IH"[OF ef te "7.prems"(3)] "7.prems"(4) "7.prems"(5) by simp
    then show ?thesis using teq veq by simp
  next
    case UNegate
    from "7.prems" UNegate obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      by (auto split: option.splits)
    from "7.prems" UNegate obtain et where te: "translate_full_direct enums e = Some et"
        and teq: "t = TNeg et"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v0)"
      using "7.IH"[OF ef te "7.prems"(3)] "7.prems"(4) "7.prems"(5) by simp
    then show ?thesis using teq ef "7.prems" UNegate by (auto split: ir_value.splits)
  next
    case UCardinality
    with "7.prems" show ?thesis by (auto split: option.splits)
  next
    case UPower
    with "7.prems" show ?thesis by (auto split: option.splits)
  qed
next
  case (8 fs ps fuel s st env c a b sp v t)
  from "8.prems" obtain ct at2 bt2 where tc: "translate_full_direct enums c = Some ct"
      and ta: "translate_full_direct enums a = Some at2" and tb: "translate_full_direct enums b = Some bt2"
      and teq: "t = TIte ct at2 bt2"
    by (auto split: option.splits)
  from "8.prems" obtain bb where ec: "eval_full fs ps fuel s st env c = Some (VBool bb)"
    by (auto split: option.splits ir_value.splits)
  have ec': "smtEval (correlate_model s st) (correlate_env env) ct = Some (SBool bb)"
    using "8.IH"(1)[OF ec tc "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
  show ?case
  proof (cases bb)
    case True
    with ec have cT: "eval_full fs ps fuel s st env c = Some (VBool True)" by simp
    have ea: "eval_full fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    have "smtEval (correlate_model s st) (correlate_env env) at2 = Some (value_to_smt v)"
      using "8.IH"(2)[OF cT refl refl ea ta "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
    then show ?thesis using teq ec' True by simp
  next
    case False
    with ec have cF: "eval_full fs ps fuel s st env c = Some (VBool False)" by simp
    have eb: "eval_full fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    have "smtEval (correlate_model s st) (correlate_env env) bt2 = Some (value_to_smt v)"
      using "8.IH"(3)[OF cF refl refl eb tb "8.prems"(3)] "8.prems"(4) "8.prems"(5) by simp
    then show ?thesis using teq ec' False by simp
  qed
next
  case (9 fs ps fuel s st env x ve body sp v t)
  from "9.prems" obtain va where eve: "eval_full fs ps fuel s st env ve = Some va"
      and ebody: "eval_full fs ps fuel s st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  from "9.prems" obtain vt bt where tve: "translate_full_direct enums ve = Some vt"
      and tbody: "translate_full_direct enums body = Some bt" and teq: "t = TLetIn x vt bt"
    by (auto split: option.splits)
  have ev': "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt va)"
    using "9.IH"(1)[OF eve tve "9.prems"(3)] "9.prems"(4) "9.prems"(5) by simp
  have eb': "smtEval (correlate_model s st) (correlate_env ((x, va) # env)) bt = Some (value_to_smt v)"
    using "9.IH"(2)[OF eve ebody tbody "9.prems"(3)] "9.prems"(4) "9.prems"(5) by simp
  show ?case using teq ev' eb' by simp
next
  case (10 fs ps fuel s st env base f sp v t)
  from "10.prems" obtain v0 where ef: "eval_full fs ps fuel s st env base = Some v0"
      and vfl: "value_field_lookup st v0 f = Some v"
    by (auto split: option.splits)
  from "10.prems" obtain bt where tb: "translate_full_direct enums base = Some bt"
      and teq: "t = TFieldAccess bt f"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt v0)"
    using "10.IH"[OF ef tb "10.prems"(3)] "10.prems"(4) "10.prems"(5) by simp
  then show ?case using teq vfl value_field_lookup_correlated[of s st v0 f] by simp
next
  case (11 fs ps fuel s st env e sp v t)
  from "11.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "11.prems" obtain et where te: "translate_full_direct enums e = Some et"
      and teq: "t = TPrime et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v)"
    using "11.IH"[OF ef te "11.prems"(3)] "11.prems"(4) "11.prems"(5) by simp
  then show ?case using teq by simp
next
  case (12 fs ps fuel s st env e sp v t)
  from "12.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "12.prems" obtain et where te: "translate_full_direct enums e = Some et"
      and teq: "t = TPre et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v)"
    using "12.IH"[OF ef te "12.prems"(3)] "12.prems"(4) "12.prems"(5) by simp
  then show ?case using teq by simp
next
  case (13 fs ps fuel s st env e sp v t)
  from "13.prems" obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      and veq: "v = VSome v0"
    by (auto split: option.splits)
  from "13.prems" obtain et where te: "translate_full_direct enums e = Some et"
      and teq: "t = TSome et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v0)"
    using "13.IH"[OF ef te "13.prems"(3)] "13.prems"(4) "13.prems"(5) by simp
  then show ?case using teq veq by simp
next
  case (14 fs ps fuel s st env e pat sp v t)
  from "14.prems" obtain str where ef: "eval_full fs ps fuel s st env e = Some (VStr str)"
      and veq: "v = VBool (string_matches str pat)"
    by (auto split: option.splits ir_value.splits)
  from "14.prems" obtain et where te: "translate_full_direct enums e = Some et"
      and teq: "t = TMatches et pat"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (SStr str)"
    using "14.IH"[OF ef te "14.prems"(3)] "14.prems"(4) "14.prems"(5) by simp
  then show ?case using teq veq by simp
next
  case (15 fs ps fuel s st env callee args sp v t)
  obtain fuel' where fuel: "fuel = Suc fuel'" using "15.prems"(1) by (cases fuel) auto
  from "15.prems"(2) obtain nm sp1 arg argt where
      ceq: "callee = IdentifierF nm sp1" and aeq: "args = [arg]" and bip: "is_builtin_pred nm"
      and ta: "translate_full_direct enums arg = Some argt" and teq: "t = TUStrPred nm argt"
    by (cases callee; cases args) (auto split: if_splits list.splits option.splits)
  have lc_none: "lookup_callee fs ps nm = None"
    using "15.prems"(5) bip by (simp add: builtins_reserved_def)
  from "15.prems"(1) fuel ceq aeq lc_none bip obtain str where
      ea: "eval_full fs ps fuel s st env arg = Some (VStr str)"
      and veq: "v = VBool (str_predicate nm str)"
    by (auto split: option.splits ir_value.splits if_splits)
  have ncarg: "no_cmp_var arg" using "15.prems"(4) ceq aeq by simp
  have ev: "smtEval (correlate_model s st) (correlate_env env) argt = Some (SStr str)"
    using "15.IH" fuel ceq aeq lc_none bip ea ta "15.prems"(3) ncarg "15.prems"(5)
    by (auto split: if_splits)
  show ?case using teq veq ev by simp
next
  case (17 fs ps fuel s st env es sp v t)
  from "17.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSeq vs"
    by (auto split: option.splits)
  from "17.prems"(2) have ls: "directSeqList enums es = Some t" by simp
  show ?case using "17.IH"[OF efl "17.prems"(3)] ls veq "17.prems"(4) "17.prems"(5) by fastforce
next
  case (18 fs ps fuel s st env es sp v t)
  from "18.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])"
    by (auto split: option.splits)
  from "18.prems"(2) have ls: "directSetList enums es = Some t" by simp
  show ?case using "18.IH"[OF efl "18.prems"(3)] ls veq "18.prems"(4) "18.prems"(5) by fastforce
next
  case (19 fs ps fuel s st env entries sp v t)
  from "19.prems"(1) obtain mps where e: "eval_full_entries fs ps fuel s st env entries = Some mps"
      and veq: "v = VMap mps"
    by (auto split: option.splits)
  from "19.prems"(2) have ls: "directMapEntries enums entries = Some t" by simp
  show ?case using "19.IH"[OF e "19.prems"(3)] ls veq "19.prems"(4) "19.prems"(5) by fastforce
next
  case (20 fs ps fuel s st env name fas sp v t)
  from "20.prems"(1) obtain fvs where e: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  from "20.prems"(2) have lw: "direct_with_assigns enums fas (TEntityBase name) = Some t" by simp
  have eb: "smtEval (correlate_model s st) (correlate_env env) (TEntityBase name)
              = Some (value_to_smt (VEntity name (STR '''')))" by simp
  show ?case using "20.IH"[OF e "20.prems"(3)] eb lw veq "20.prems"(4) "20.prems"(5) by fastforce
next
  case (21 fs ps fuel s st env base fas sp v t)
  from "21.prems"(1) obtain bv fvs where eb: "eval_full fs ps fuel s st env base = Some bv"
      and ef: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  from "21.prems"(2) obtain bt where tb: "translate_full_direct enums base = Some bt"
      and lw: "direct_with_assigns enums fas bt = Some t"
    by (auto split: option.splits)
  have eb': "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv)"
    using "21.IH"(1)[OF eb tb "21.prems"(3)] "21.prems"(4) "21.prems"(5) by simp
  show ?case using "21.IH"(2)[OF ef "21.prems"(3)] eb' lw veq "21.prems"(4) "21.prems"(5) by fastforce
next
  case (22 fs ps fuel s st env base mem sp v t)
  show ?case
  proof (cases base)
    case (IdentifierF en sp')
    from "22.prems"(1) IdentifierF obtain d where sd: "schema_lookup_enum s en = Some d"
        and mem_in: "List.member (enm_members d) mem" and veq: "v = VEnum en mem"
      by (auto split: option.splits if_splits)
    have teq: "t = EnumElemConst en mem" using "22.prems"(2) IdentifierF by simp
    show ?thesis using teq veq correlate_model_lookup_sort_members[OF sd] mem_in by simp
  qed (use "22.prems" in simp_all)
next
  case (23 fs ps fuel s st env base key sp v t)
  from "23.prems"(1) obtain rel kv where pk: "peelRelationRefFull base = Some rel"
      and ek: "eval_full fs ps fuel s st env key = Some kv"
      and slk: "state_lookup_key st rel kv = Some v"
    by (auto split: option.splits)
  from "23.prems"(2) obtain bt kt where tb: "translate_full_direct enums base = Some bt"
      and tk: "translate_full_direct enums key = Some kt" and teq: "t = TIndexRel bt kt"
    by (auto split: option.splits)
  have pr: "peelSmtRelationRef bt = Some rel" using peelSmt_tfd[OF pk tb] .
  have "smtEval (correlate_model s st) (correlate_env env) kt = Some (value_to_smt kv)"
    using "23.IH"[OF ek tk "23.prems"(3)] "23.prems"(4) "23.prems"(5) by simp
  then show ?case using teq pr slk correlate_model_lookup_key[of s st rel kv] by simp
next
  case (24 fs ps fuel s st env var dm body sp v t)
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    from "24.prems"(2) IdentifierF obtain bt where tb: "translate_full_direct enums body = Some bt"
        and teq: "t = TTheRel var rel bt"
      by (auto split: if_splits option.splits)
    from "24.prems"(1) IdentifierF obtain dmv x rest where srd: "state_relation_domain st rel = Some dmv"
        and eft: "eval_full_the fs ps fuel s st env var dmv body = Some (x # rest)"
        and uniq: "list_all (\<lambda>y. y = x) rest" and v_eq: "v = x"
      by (auto split: option.splits list.splits if_splits)
    have etr: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                 (map value_to_smt dmv) bt = Some (map value_to_smt (x # rest))"
      using "24.IH"[OF IdentifierF srd eft "24.prems"(3)] tb "24.prems"(4) "24.prems"(5) by fastforce
    have uniq': "list_all (\<lambda>y. y = value_to_smt x) (map value_to_smt rest)"
      using uniq by (induction rest) auto
    show ?thesis using teq srd etr uniq' v_eq by simp
  qed (use "24.prems"(2) in simp_all)
next
  case (25 fs ps fuel s st env k bs body sp v t)
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
    obtain bt where tbody: "translate_full_direct enums body = Some bt"
      using "25.prems"(2) kq bseq by (auto split: option.splits)
    have ih: "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                (map value_to_smt dmv) bt = Some (value_to_smt v)"
      using "25.IH"[OF Some[unfolded vdeq] refl eff wf] tbody "25.prems"(4) "25.prems"(5) by fastforce
    show ?thesis
    proof (cases "schema_lookup_enum s dnm")
      case (Some d)
      note sd = this
      have si: "string_in_list dnm enums"
      proof (rule sil_enum)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = Some d" by (rule sd)
      qed
      have teq: "t = TForallEnum var dnm bt"
        using "25.prems"(2) kq bseq tbody si by (auto split: option.splits)
      have dmv_eq: "dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
        using dmrel sd by (rule dmrel_enum)
      have mapeq: "map value_to_smt dmv = map (SEnumElem dnm) (enm_members d)"
        by (simp add: dmv_eq)
      show ?thesis
        using teq sd ih mapeq correlate_model_lookup_sort_members[OF sd]
        by (simp add: smtEval_forall_enum_eq_rel)
    next
      case None
      note sn = this
      have nsi: "\<not> string_in_list dnm enums"
      proof (rule sil_none)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = None" by (rule sn)
      qed
      have teq: "t = TForallRel var dnm bt"
        using "25.prems"(2) kq bseq tbody nsi by (auto split: option.splits)
      have sr: "state_relation_domain st dnm = Some dmv"
        using dmrel sn by (rule dmrel_rel)
      show ?thesis using teq sr ih by simp
    qed
  qed
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
    fix t
    assume lse: "directSeqList enums (e # es) = Some t"
    from lse obtain e0t s0t where te0: "translate_full_direct enums e = Some e0t"
        and ts0: "directSeqList enums es = Some s0t" and teq: "t = TSeqCons e0t s0t"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) e0t = Some (value_to_smt v0)"
      using "28.IH"(1)[OF ev0 te0 "28.prems"(2)] "28.prems"(3) "28.prems"(4) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) s0t
                     = Some (SSeq (map value_to_smt vs0))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) nclE "28.prems"(4)] ts0 by blast
    ultimately show "smtEval (correlate_model s st) (correlate_env env) t
                       = Some (SSeq (map value_to_smt vs))" using teq vseq by simp
  next
    fix t
    assume lse: "directSetList enums (e # es) = Some t"
    from lse obtain e0t s0t where te0: "translate_full_direct enums e = Some e0t"
        and ts0: "directSetList enums es = Some s0t" and teq: "t = TSetInsert e0t s0t"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) e0t = Some (value_to_smt v0)"
      using "28.IH"(1)[OF ev0 te0 "28.prems"(2)] "28.prems"(3) "28.prems"(4) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) s0t
                     = Some (SSet (map value_to_smt (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs0 [])))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) nclE "28.prems"(4)] ts0 by blast
    ultimately show "smtEval (correlate_model s st) (correlate_env env) t
                       = Some (SSet (map value_to_smt (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])))"
      using teq vseq
      by (simp add: Let_def dedupe_values_map_value_to_smt[symmetric] split: if_splits)
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
    fix t
    assume lme: "directMapEntries enums (MapEntryFull k v msp # rest) = Some t"
    from lme obtain kt vt mt where tk: "translate_full_direct enums k = Some kt"
        and tv: "translate_full_direct enums v = Some vt"
        and tm: "directMapEntries enums rest = Some mt" and teq: "t = TMapCons kt vt mt"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) kt = Some (value_to_smt kv)"
      using "30.IH"(1)[OF ek tk "30.prems"(2)] "30.prems"(3) "30.prems"(4) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt vv)"
      using "30.IH"(2)[OF ev tv "30.prems"(2)] "30.prems"(3) "30.prems"(4) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) mt
                     = Some (SMap (value_to_smt_entries mps0))"
      using "30.IH"(3)[OF er "30.prems"(2)] tm "30.prems"(3) "30.prems"(4) by fastforce
    ultimately show "smtEval (correlate_model s st) (correlate_env env) t
                       = Some (SMap (value_to_smt_entries mps))" using teq mpeq by simp
  qed
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
    fix bt t bv
    assume sb: "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv)"
    assume dw: "direct_with_assigns enums (FieldAssignFull fld v fsp # rest) bt = Some t"
    from dw obtain vt where tv: "translate_full_direct enums v = Some vt"
        and dwr: "direct_with_assigns enums rest (TWithRec bt fld vt) = Some t"
      by (auto split: option.splits)
    have ev': "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt fv)"
      using "32.IH"(1)[OF ev tv "32.prems"(2)] "32.prems"(3) "32.prems"(4) by simp
    have ebw: "smtEval (correlate_model s st) (correlate_env env) (TWithRec bt fld vt)
                 = Some (value_to_smt (VEntityWith bv fld fv))"
      using sb ev' by simp
    have "smtEval (correlate_model s st) (correlate_env env) t
            = Some (value_to_smt (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntityWith bv fld fv) fvs0))"
      using "32.IH"(2)[OF er "32.prems"(2)] ebw dwr "32.prems"(3) "32.prems"(4) by fastforce
    then show "smtEval (correlate_model s st) (correlate_env env) t
                 = Some (value_to_smt (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs))"
      using fveq by simp
  qed
next
  case (33 fs ps fuel s st env var body tms)
  show ?case using "33.prems" by auto
next
  case (34 fs ps fuel s st env var v rest body tms)
  show ?case
  proof (intro allI impI)
    fix bt
    assume tb: "translate_full_direct enums body = Some bt"
    show "smtEval_the_rel (correlate_model s st) (correlate_env env) var
            (map value_to_smt (v # rest)) bt = Some (map value_to_smt tms)"
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
        have eb: "smtEval (correlate_model s st) ((var, value_to_smt v) # correlate_env env) bt
                    = Some (SBool b)"
          using "34.IH"(1)[OF evb tb "34.prems"(2)] "34.prems"(3) "34.prems"(4) by simp
        have er: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                    (map value_to_smt rest) bt = Some (map value_to_smt matches)"
          using "34.IH"(2)[OF Some VBool mr "34.prems"(2)] tb "34.prems"(3) "34.prems"(4) by fastforce
        show ?thesis using eb er tms_eq by (cases b) auto
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
    fix bt
    assume tb: "translate_full_direct enums body = Some bt"
    show "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
            (map value_to_smt (v # rest)) bt = Some (value_to_smt fr)"
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
        have eb: "smtEval (correlate_model s st) ((var, value_to_smt v) # correlate_env env) bt
                    = Some (SBool b)"
          using "36.IH"(1)[OF evb tb "36.prems"(2)] "36.prems"(3) "36.prems"(4) by simp
        have er: "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                    (map value_to_smt rest) bt = Some (SBool acc)"
          using "36.IH"(2)[OF Some VBool mr "36.prems"(2)] tb "36.prems"(3) "36.prems"(4) by fastforce
        show ?thesis using eb er fr_eq by simp
      qed (use "36.prems" Some in simp_all)
    qed
  qed
qed (auto split: option.splits ir_value.splits)

theorem translate_full_direct_soundness_standalone:
  assumes "eval_full fs ps fuel s st env e = Some v"
      and "translate_full_direct enums e = Some t"
      and "enums_wf s enums"
      and "no_cmp_var e"
      and "builtins_reserved fs ps"
  shows "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
  by (rule direct_soundness(1)[OF assms])

end
