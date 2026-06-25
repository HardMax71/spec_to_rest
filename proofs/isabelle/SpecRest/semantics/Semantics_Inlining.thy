theory Semantics_Inlining
  imports Semantics_Reference
begin

lemma eval_coincidence:
  "(\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval fs ps fuel s st env1 e = eval fs ps fuel s st env2 e"
  "(\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_list fs ps fuel s st env1 es = eval_list fs ps fuel s st env2 es"
  "(\<forall>y. string_in_list y (free_vars_entries ents) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_entries fs ps fuel s st env1 ents = eval_entries fs ps fuel s st env2 ents"
  "(\<forall>y. string_in_list y (free_vars_fields fas) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_fields fs ps fuel s st env1 fas = eval_fields fs ps fuel s st env2 fas"
  "(\<forall>y. string_in_list y (remove_name var (free_vars body)) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_the fs ps fuel s st env1 var dmv body = eval_the fs ps fuel s st env2 var dmv body"
  "(\<forall>y. string_in_list y (remove_name var (free_vars body)) \<longrightarrow> env_lookup env1 y = env_lookup env2 y)
     \<Longrightarrow> eval_forall fs ps fuel s st env1 var dmv body = eval_forall fs ps fuel s st env2 var dmv body"
proof (induction fs ps fuel s st env1 e and fs ps fuel s st env1 es and fs ps fuel s st env1 ents
        and fs ps fuel s st env1 fas and fs ps fuel s st env1 var dmv body and fs ps fuel s st env1 var dmv body
        arbitrary: env2 and env2 and env2 and env2 and env2 and env2
        rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (6 fs ps fuel s st env bop l r sp env2)
  have al: "\<forall>y. string_in_list y (free_vars l) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "6.prems" by auto
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    hence de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some by simp
    show ?thesis by (simp only: eval_dom_eq[OF de])
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
    case None
    have ar: "\<forall>y. string_in_list y (free_vars r) \<longrightarrow> env_lookup env y = env_lookup env2 y"
      using "6.prems" by auto
    show ?thesis using "6.IH"(1)[OF deN None al] "6.IH"(2)[OF deN None ar] deN None by simp
  next
    case (Some t)
    obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
    have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
    have apred: "\<forall>y. string_in_list y (remove_name var (free_vars pred)) \<longrightarrow> env_lookup env y = env_lookup env2 y"
      using "6.prems" beq_comp_free_vars[OF bc] by auto
    show ?thesis
    proof (cases "schema_lookup_enum s dnm")
      case (Some d)
      thus ?thesis using bc deN by simp
    next
      case None
      have enr: "\<not> schema_lookup_enum s dnm \<noteq> None" using None by simp
      show ?thesis
      proof (cases "state_relation_domain st dnm")
        case None
        thus ?thesis using bc enr deN by simp
      next
        case (Some dvs)
        note srd = this
        have ethe: "eval_the fs ps fuel s st env var dvs pred = eval_the fs ps fuel s st env2 var dvs pred"
          using "6.IH"(3)[OF deN bc refl refl enr srd apred] .
        show ?thesis
        proof (cases "eval_the fs ps fuel s st env var dvs pred")
          case None
          thus ?thesis using bc enr srd ethe deN by simp
        next
          case (Some ms)
          note etm = this
          have el: "eval fs ps fuel s st env l = eval fs ps fuel s st env2 l"
            using "6.IH"(4)[OF deN bc refl refl enr srd etm al] .
          show ?thesis using bc enr srd ethe el deN by (simp split: option.split ir_value.split)
        qed
      qed
    qed
    qed
  qed
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
  have c_eq: "eval fs ps fuel s st env c = eval fs ps fuel s st env2 c"
    using "8.IH"(1)[OF ac] .
  show ?case
  proof (cases "eval fs ps fuel s st env c")
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
        have cT: "eval fs ps fuel s st env c = Some (VBool True)"
          using Some VBool True by simp
        have "eval fs ps fuel s st env a = eval fs ps fuel s st env2 a"
          using "8.IH"(2)[OF cT refl refl aa] .
        then show ?thesis using cT c_eq by simp
      next
        case False
        have cF: "eval fs ps fuel s st env c = Some (VBool False)"
          using Some VBool False by simp
        have "eval fs ps fuel s st env b = eval fs ps fuel s st env2 b"
          using "8.IH"(3)[OF cF refl refl ab] .
        then show ?thesis using cF c_eq by simp
      qed
    qed (use Some c_eq in simp_all)
  qed
next
  case (9 fs ps fuel s st env x v body sp env2)
  have av: "\<forall>y. string_in_list y (free_vars v) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "9.prems" by auto
  have v_eq: "eval fs ps fuel s st env v = eval fs ps fuel s st env2 v"
    using "9.IH"(1)[OF av] .
  show ?case
  proof (cases "eval fs ps fuel s st env v")
    case None
    then show ?thesis using v_eq by simp
  next
    case (Some va)
    have v2: "eval fs ps fuel s st env2 v = Some va" using v_eq Some by simp
    have abd: "\<forall>y. string_in_list y (free_vars body)
                 \<longrightarrow> env_lookup ((x, va) # env) y = env_lookup ((x, va) # env2) y"
      using "9.prems" by (auto simp: env_lookup_def)
    have body_eq: "eval fs ps fuel s st ((x, va) # env) body
            = eval fs ps fuel s st ((x, va) # env2) body"
      using "9.IH"(2)[OF Some abd] .
    show ?thesis using Some v2 body_eq by simp
  qed
next
  case (10 fs ps fuel s st env base f sp env2)
  have ab: "\<forall>y. string_in_list y (free_vars base) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "10.prems" by auto
  show ?case using "10.IH"[OF ab] by simp
next
  case (23 fs ps fuel s st env base key sp env2)
  have ak: "\<forall>y. string_in_list y (free_vars key) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "23.prems" by auto
  show ?case using "23.IH"[OF ak] by simp
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
        show ?thesis
        proof (cases args)
          case Nil then show ?thesis using Suc idc None by simp
        next
          case (Cons a as)
          note ac = Cons
          show ?thesis
          proof (cases as)
            case Nil
            show ?thesis
            proof (cases "is_builtin_pred nm")
              case True
              have "eval fs ps fuel s st env a = eval fs ps fuel s st env2 a"
                using "15.IH" Suc idc None ac Nil agr True by (auto simp: env_lookup_def)
              then show ?thesis using Suc idc None ac Nil True
                by (auto split: option.splits ir_value.splits)
            next
              case False
              show ?thesis
              proof (cases "is_builtin_func nm")
                case True
                have "eval fs ps fuel s st env a = eval fs ps fuel s st env2 a"
                  using "15.IH" Suc idc None ac Nil agr \<open>\<not> is_builtin_pred nm\<close> True
                  by (auto simp: env_lookup_def)
                then show ?thesis using Suc idc None ac Nil \<open>\<not> is_builtin_pred nm\<close> True
                  by (auto split: option.splits ir_value.splits)
              next
                case False
                show ?thesis
                proof (cases "is_builtin_int_func nm")
                  case True
                  have "eval fs ps fuel s st env a = eval fs ps fuel s st env2 a"
                    using "15.IH" Suc idc None ac Nil agr \<open>\<not> is_builtin_pred nm\<close> \<open>\<not> is_builtin_func nm\<close> True
                    by (auto simp: env_lookup_def)
                  then show ?thesis using Suc idc None ac Nil \<open>\<not> is_builtin_pred nm\<close> \<open>\<not> is_builtin_func nm\<close> True
                    by (auto split: option.splits ir_value.splits)
                next
                  case False
                  thus ?thesis
                    using Suc idc None ac Nil \<open>\<not> is_builtin_pred nm\<close> \<open>\<not> is_builtin_func nm\<close> by simp
                qed
              qed
            qed
          next
            case (Cons b bs)
            then show ?thesis using Suc idc None ac by simp
          qed
        qed
      next
        case (Some pb)
        obtain params body where pb: "pb = (params, body)" by (cases pb) auto
        show ?thesis
        proof (cases "length params = length args \<and> distinct params")
          case False
          then have "eval fs ps fuel s st env (CallF callee args sp) = None"
            and "eval fs ps fuel s st env2 (CallF callee args sp) = None"
            using Suc idc Some pb by auto
          then show ?thesis by simp
        next
          case True
          have args_eq: "eval_list fs ps fuel s st env args
                           = eval_list fs ps fuel s st env2 args"
            using "15.IH" Suc idc Some pb True agr by (auto simp: env_lookup_def)
          then show ?thesis using Suc idc Some pb True by (simp split: option.splits)
        qed
      qed
    qed
  qed
next
  case (28 fs ps fuel s st env e es env2)
  have agr_e: "\<forall>y. string_in_list y (free_vars e) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "28.prems" by auto
  have agr_es: "\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "28.prems" by auto
  have e_eq: "eval fs ps fuel s st env e = eval fs ps fuel s st env2 e"
    using "28.IH"(1)[OF agr_e] .
  show ?case
  proof (cases "eval fs ps fuel s st env e")
    case None
    then show ?thesis using e_eq by simp
  next
    case (Some v0)
    have e2: "eval fs ps fuel s st env2 e = Some v0" using e_eq Some by simp
    have es_eq: "eval_list fs ps fuel s st env es = eval_list fs ps fuel s st env2 es"
      using "28.IH"(2)[OF Some agr_es] .
    show ?thesis using Some e2 es_eq by simp
  qed
next
  case (17 fs ps fuel s st env es sp env2)
  have agr: "\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "17.prems" by auto
  show ?case using "17.IH"[OF agr] by simp
next
  case (18 fs ps fuel s st env es sp env2)
  have agr: "\<forall>y. string_in_list y (free_vars_list es) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "18.prems" by auto
  show ?case using "18.IH"[OF agr] by simp
next
  case (19 fs ps fuel s st env entries sp env2)
  have agr: "\<forall>y. string_in_list y (free_vars_entries entries) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "19.prems" by auto
  show ?case using "19.IH"[OF agr] by simp
next
  case (30 fs ps fuel s st env k v msp rest env2)
  have agk: "\<forall>y. string_in_list y (free_vars k) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "30.prems" by auto
  have agv: "\<forall>y. string_in_list y (free_vars v) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "30.prems" by auto
  have agr2: "\<forall>y. string_in_list y (free_vars_entries rest) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "30.prems" by auto
  show ?case using "30.IH"(1)[OF agk] "30.IH"(2)[OF agv] "30.IH"(3)[OF agr2] by simp
next
  case (20 fs ps fuel s st env name fas sp env2)
  have agr: "\<forall>y. string_in_list y (free_vars_fields fas) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "20.prems" by auto
  show ?case using "20.IH"[OF agr] by simp
next
  case (21 fs ps fuel s st env base fas sp env2)
  have agb: "\<forall>y. string_in_list y (free_vars base) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "21.prems" by auto
  have agf: "\<forall>y. string_in_list y (free_vars_fields fas) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "21.prems" by auto
  show ?case using "21.IH"(1)[OF agb] "21.IH"(2)[OF agf] by simp
next
  case (32 fs ps fuel s st env fld v fsp rest env2)
  have agv: "\<forall>y. string_in_list y (free_vars v) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "32.prems" by auto
  have agr3: "\<forall>y. string_in_list y (free_vars_fields rest) \<longrightarrow> env_lookup env y = env_lookup env2 y"
    using "32.prems" by auto
  show ?case using "32.IH"(1)[OF agv] "32.IH"(2)[OF agr3] by simp
next
  case (24 fs ps fuel s st env1 var dm body sp env2)
  have ha: "\<forall>y. string_in_list y (remove_name var (free_vars body))
              \<longrightarrow> env_lookup env1 y = env_lookup env2 y"
    using "24.prems" by auto
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    show ?thesis
    proof (cases "state_relation_domain st rel")
      case None
      then show ?thesis using IdentifierF by simp
    next
      case (Some dmv)
      have "eval_the fs ps fuel s st env1 var dmv body
              = eval_the fs ps fuel s st env2 var dmv body"
        using "24.IH"[OF IdentifierF Some ha] .
      then show ?thesis using IdentifierF Some by simp
    qed
  qed simp_all
next
  case (34 fs ps fuel s st env1 var v rest body env2)
  have ext: "\<forall>y. string_in_list y (free_vars body)
               \<longrightarrow> env_lookup ((var, v) # env1) y = env_lookup ((var, v) # env2) y"
    using "34.prems" by (auto simp: env_lookup_def)
  show ?case
  proof (cases "eval fs ps fuel s st ((var, v) # env1) body")
    case None
    then show ?thesis using "34.IH"(1)[OF ext] by simp
  next
    case (Some bv)
    have e2: "eval fs ps fuel s st ((var, v) # env2) body = Some bv"
      using "34.IH"(1)[OF ext] Some by metis
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have rest_eq: "eval_the fs ps fuel s st env1 var rest body
              = eval_the fs ps fuel s st env2 var rest body"
        using "34.IH"(2)[OF Some VBool "34.prems"] .
      show ?thesis using Some e2 VBool rest_eq by simp
    qed (use Some e2 in simp_all)
  qed
next
  case (36 fs ps fuel s st env1 var v rest body env2)
  have ext: "\<forall>y. string_in_list y (free_vars body)
               \<longrightarrow> env_lookup ((var, v) # env1) y = env_lookup ((var, v) # env2) y"
    using "36.prems" by (auto simp: env_lookup_def)
  show ?case
  proof (cases "eval fs ps fuel s st ((var, v) # env1) body")
    case None
    then show ?thesis using "36.IH"(1)[OF ext] by simp
  next
    case (Some bv)
    have e2: "eval fs ps fuel s st ((var, v) # env2) body = Some bv"
      using "36.IH"(1)[OF ext] Some by metis
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have rest_eq: "eval_forall fs ps fuel s st env1 var rest body
              = eval_forall fs ps fuel s st env2 var rest body"
        using "36.IH"(2)[OF Some VBool "36.prems"] .
      show ?thesis using Some e2 VBool rest_eq by simp
    qed (use Some e2 in simp_all)
  qed
next
  case (25 fs ps fuel s st env1 k bs body sp env2)
  show ?case
  proof (cases "quant_dom s st k bs")
    case None
    then show ?thesis by simp
  next
    case (Some vd)
    obtain var dmv where vdeq: "vd = (var, dmv)" by (cases vd) auto
    have qn: "qb_names bs = [var]" using quant_dom_qb_names[OF Some[unfolded vdeq]] .
    have ha: "\<forall>y. string_in_list y (remove_name var (free_vars body))
                \<longrightarrow> env_lookup env1 y = env_lookup env2 y"
      using "25.prems" qn by auto
    have "eval_forall fs ps fuel s st env1 var dmv body
            = eval_forall fs ps fuel s st env2 var dmv body"
      using "25.IH"[OF Some[unfolded vdeq] refl ha] .
    then show ?thesis using Some vdeq by simp
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
    and "eval_list fs ps fuel s st env as = Some vals"
  shows "eval fs ps fuel s st env (bind_params pms as body)
           = eval fs ps fuel s st (zip pms vals @ env) body"
  using assms
proof (induction pms arbitrary: as vals env)
  case Nil
  then show ?case by simp
next
  case (Cons p pms')
  obtain a as' where as_eq: "as = a # as'"
    using Cons.prems(2) by (cases as) auto
  obtain v0 vs0 where v0: "eval fs ps fuel s st env a = Some v0"
    and vs0: "eval_list fs ps fuel s st env as' = Some vs0"
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
  have vs0_ext: "eval_list fs ps fuel s st ((p, v0) # env) as' = Some vs0"
    using eval_coincidence(2)[OF agr1] vs0 by simp
  have IH: "eval fs ps fuel s st ((p, v0) # env) (bind_params pms' as' body)
              = eval fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body"
    using Cons.IH[OF pf' len' d' vs0_ext] .
  have keys: "p \<notin> fst ` set (zip pms' vs0)"
    using p_notin by (fastforce dest: set_zip_leftD)
  have mEq: "map_of (zip pms' vs0 @ (p, v0) # env) = map_of ((p, v0) # zip pms' vs0 @ env)"
    by (rule map_of_swap_head[OF keys])
  have reorder: "eval fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body
                   = eval fs ps fuel s st ((p, v0) # zip pms' vs0 @ env) body"
  proof (rule eval_coincidence(1), intro allI impI)
    fix y :: "String.literal"
    assume "string_in_list y (free_vars body)"
    show "env_lookup (zip pms' vs0 @ (p, v0) # env) y = env_lookup ((p, v0) # zip pms' vs0 @ env) y"
      unfolding env_lookup_def by (rule fun_cong[OF mEq])
  qed
  have "eval fs ps fuel s st env (bind_params (p # pms') (a # as') body)
          = eval fs ps fuel s st ((p, v0) # env) (bind_params pms' as' body)"
    using v0 by simp
  also have "\<dots> = eval fs ps fuel s st (zip pms' vs0 @ (p, v0) # env) body"
    using IH .
  also have "\<dots> = eval fs ps fuel s st ((p, v0) # zip pms' vs0 @ env) body"
    using reorder .
  also have "\<dots> = eval fs ps fuel s st (zip (p # pms') (v0 # vs0) @ env) body"
    by simp
  finally show ?case using as_eq vals_eq by simp
qed

lemma eval_callfree_fuel:
  "\<not> list_ex is_call (allSubexprs e)
     \<Longrightarrow> eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
  "\<not> list_ex is_call (allSubexprs_list es)
     \<Longrightarrow> eval_list fs ps fuel1 s st env es = eval_list fs ps fuel2 s st env es"
  "\<not> list_ex is_call (allSubexprs_entries ents)
     \<Longrightarrow> eval_entries fs ps fuel1 s st env ents = eval_entries fs ps fuel2 s st env ents"
  "\<not> list_ex is_call (allSubexprs_fields fas)
     \<Longrightarrow> eval_fields fs ps fuel1 s st env fas = eval_fields fs ps fuel2 s st env fas"
  "\<not> list_ex is_call (allSubexprs body)
     \<Longrightarrow> eval_the fs ps fuel1 s st env var dmv body = eval_the fs ps fuel2 s st env var dmv body"
  "\<not> list_ex is_call (allSubexprs body)
     \<Longrightarrow> eval_forall fs ps fuel1 s st env var dmv body = eval_forall fs ps fuel2 s st env var dmv body"
proof (induction fs ps fuel1 s st env e and fs ps fuel1 s st env es and fs ps fuel1 s st env ents
        and fs ps fuel1 s st env fas and fs ps fuel1 s st env var dmv body and fs ps fuel1 s st env var dmv body
        arbitrary: fuel2 and fuel2 and fuel2 and fuel2 and fuel2 and fuel2
        rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (6 fs ps fuel1 s st env bop l r sp fuel2)
  have cfl: "\<not> list_ex is_call (allSubexprs l)" using "6.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    hence de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some by simp
    show ?thesis by (simp only: eval_dom_eq[OF de])
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
    case None
    have cfr: "\<not> list_ex is_call (allSubexprs r)" using "6.prems" by (auto simp: list_ex_iff)
    have "eval fs ps fuel1 s st env l = eval fs ps fuel2 s st env l"
      using "6.IH"(1)[OF deN None cfl] .
    moreover have "eval fs ps fuel1 s st env r = eval fs ps fuel2 s st env r"
      using "6.IH"(2)[OF deN None cfr] .
    ultimately show ?thesis using deN None by simp
  next
    case (Some t)
    obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
    have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
    have cfp: "\<not> list_ex is_call (allSubexprs pred)"
      using "6.prems" beq_comp_allsub[OF bc] by (auto simp: list_ex_iff)
    show ?thesis
    proof (cases "schema_lookup_enum s dnm")
      case (Some d)
      thus ?thesis using bc deN by simp
    next
      case None
      have enr: "\<not> schema_lookup_enum s dnm \<noteq> None" using None by simp
      show ?thesis
      proof (cases "state_relation_domain st dnm")
        case None
        thus ?thesis using bc enr deN by simp
      next
        case (Some dvs)
        note srd = this
        have ethe: "eval_the fs ps fuel1 s st env var dvs pred = eval_the fs ps fuel2 s st env var dvs pred"
          using "6.IH"(3)[OF deN bc refl refl enr srd cfp] .
        show ?thesis
        proof (cases "eval_the fs ps fuel1 s st env var dvs pred")
          case None
          thus ?thesis using bc enr srd ethe deN by simp
        next
          case (Some ms)
          note etm = this
          have el: "eval fs ps fuel1 s st env l = eval fs ps fuel2 s st env l"
            using "6.IH"(4)[OF deN bc refl refl enr srd etm cfl] .
          show ?thesis using bc enr srd ethe el deN by (simp split: option.split ir_value.split)
        qed
      qed
    qed
    qed
  qed
next
  case (7 fs ps fuel1 s st env uop e sp fuel2)
  have "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "7.IH" "7.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (8 fs ps fuel1 s st env c a b sp fuel2)
  have c_eq: "eval fs ps fuel1 s st env c = eval fs ps fuel2 s st env c"
    using "8.IH"(1) "8.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval fs ps fuel1 s st env c")
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
        have cT: "eval fs ps fuel1 s st env c = Some (VBool True)"
          using Some VBool True by simp
        have "eval fs ps fuel1 s st env a = eval fs ps fuel2 s st env a"
          using "8.IH"(2)[OF cT refl refl] "8.prems" by (auto simp: list_ex_iff)
        then show ?thesis using cT c_eq by simp
      next
        case False
        have cF: "eval fs ps fuel1 s st env c = Some (VBool False)"
          using Some VBool False by simp
        have "eval fs ps fuel1 s st env b = eval fs ps fuel2 s st env b"
          using "8.IH"(3)[OF cF refl refl] "8.prems" by (auto simp: list_ex_iff)
        then show ?thesis using cF c_eq by simp
      qed
    qed (use Some c_eq in simp_all)
  qed
next
  case (9 fs ps fuel1 s st env x v body sp fuel2)
  have v_eq: "eval fs ps fuel1 s st env v = eval fs ps fuel2 s st env v"
    using "9.IH"(1) "9.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval fs ps fuel1 s st env v")
    case None
    then show ?thesis using v_eq by simp
  next
    case (Some va)
    have v2: "eval fs ps fuel2 s st env v = Some va" using v_eq Some by simp
    have "eval fs ps fuel1 s st ((x, va) # env) body
            = eval fs ps fuel2 s st ((x, va) # env) body"
      using "9.IH"(2)[OF Some] "9.prems" by (auto simp: list_ex_iff)
    then show ?thesis using Some v2 by simp
  qed
next
  case (10 fs ps fuel1 s st env base f sp fuel2)
  have "eval fs ps fuel1 s st env base = eval fs ps fuel2 s st env base"
    using "10.IH" "10.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (23 fs ps fuel1 s st env base key sp fuel2)
  have "eval fs ps fuel1 s st env key = eval fs ps fuel2 s st env key"
    using "23.IH" "23.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (11 fs ps fuel1 s st env e sp fuel2)
  have "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "11.IH" "11.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (12 fs ps fuel1 s st env e sp fuel2)
  have "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "12.IH" "12.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (13 fs ps fuel1 s st env e sp fuel2)
  have "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "13.IH" "13.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (14 fs ps fuel1 s st env e pat sp fuel2)
  have "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "14.IH" "14.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (15 fs ps fuel1 s st env callee args sp fuel2)
  have False using "15.prems" by simp
  then show ?case by simp
next
  case (28 fs ps fuel1 s st env e es fuel2)
  have e_eq: "eval fs ps fuel1 s st env e = eval fs ps fuel2 s st env e"
    using "28.IH"(1) "28.prems" by (auto simp: list_ex_iff)
  show ?case
  proof (cases "eval fs ps fuel1 s st env e")
    case None
    then show ?thesis using e_eq by simp
  next
    case (Some v0)
    have e2: "eval fs ps fuel2 s st env e = Some v0" using e_eq Some by simp
    have "eval_list fs ps fuel1 s st env es = eval_list fs ps fuel2 s st env es"
      using "28.IH"(2)[OF Some] "28.prems" by (auto simp: list_ex_iff)
    then show ?thesis using Some e2 by simp
  qed
next
  case (17 fs ps fuel1 s st env es sp fuel2)
  have "eval_list fs ps fuel1 s st env es = eval_list fs ps fuel2 s st env es"
    using "17.IH" "17.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (18 fs ps fuel1 s st env es sp fuel2)
  have "eval_list fs ps fuel1 s st env es = eval_list fs ps fuel2 s st env es"
    using "18.IH" "18.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (19 fs ps fuel1 s st env entries sp fuel2)
  have "eval_entries fs ps fuel1 s st env entries = eval_entries fs ps fuel2 s st env entries"
    using "19.IH" "19.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (30 fs ps fuel1 s st env k v msp rest fuel2)
  have "eval fs ps fuel1 s st env k = eval fs ps fuel2 s st env k"
      and "eval fs ps fuel1 s st env v = eval fs ps fuel2 s st env v"
      and "eval_entries fs ps fuel1 s st env rest = eval_entries fs ps fuel2 s st env rest"
    using "30.IH"(1) "30.IH"(2) "30.IH"(3) "30.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (20 fs ps fuel1 s st env name fas sp fuel2)
  have "eval_fields fs ps fuel1 s st env fas = eval_fields fs ps fuel2 s st env fas"
    using "20.IH" "20.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (21 fs ps fuel1 s st env base fas sp fuel2)
  have "eval fs ps fuel1 s st env base = eval fs ps fuel2 s st env base"
      and "eval_fields fs ps fuel1 s st env fas = eval_fields fs ps fuel2 s st env fas"
    using "21.IH"(1) "21.IH"(2) "21.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (32 fs ps fuel1 s st env fld v fsp rest fuel2)
  have "eval fs ps fuel1 s st env v = eval fs ps fuel2 s st env v"
      and "eval_fields fs ps fuel1 s st env rest = eval_fields fs ps fuel2 s st env rest"
    using "32.IH"(1) "32.IH"(2) "32.prems" by (auto simp: list_ex_iff)
  then show ?case by simp
next
  case (24 fs ps fuel1 s st env var dm body sp fuel2)
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    show ?thesis
    proof (cases "state_relation_domain st rel")
      case None
      then show ?thesis using IdentifierF by simp
    next
      case (Some dmv)
      have cf: "\<not> list_ex is_call (allSubexprs body)"
        using "24.prems" by (auto simp: list_ex_iff)
      have "eval_the fs ps fuel1 s st env var dmv body
              = eval_the fs ps fuel2 s st env var dmv body"
        using "24.IH"[OF IdentifierF Some cf] .
      then show ?thesis using IdentifierF Some by simp
    qed
  qed simp_all
next
  case (34 fs ps fuel1 s st env var v rest body fuel2)
  show ?case
  proof (cases "eval fs ps fuel1 s st ((var, v) # env) body")
    case None
    then show ?thesis using "34.IH"(1)[OF "34.prems"] by simp
  next
    case (Some bv)
    have e2: "eval fs ps fuel2 s st ((var, v) # env) body = Some bv"
      using "34.IH"(1)[OF "34.prems"] Some by metis
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have rest_eq: "eval_the fs ps fuel1 s st env var rest body
              = eval_the fs ps fuel2 s st env var rest body"
        using "34.IH"(2)[OF Some VBool "34.prems"] .
      show ?thesis using Some e2 VBool rest_eq by simp
    qed (use Some e2 in simp_all)
  qed
next
  case (36 fs ps fuel1 s st env var v rest body fuel2)
  show ?case
  proof (cases "eval fs ps fuel1 s st ((var, v) # env) body")
    case None
    then show ?thesis using "36.IH"(1)[OF "36.prems"] by simp
  next
    case (Some bv)
    have e2: "eval fs ps fuel2 s st ((var, v) # env) body = Some bv"
      using "36.IH"(1)[OF "36.prems"] Some by metis
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have rest_eq: "eval_forall fs ps fuel1 s st env var rest body
              = eval_forall fs ps fuel2 s st env var rest body"
        using "36.IH"(2)[OF Some VBool "36.prems"] .
      show ?thesis using Some e2 VBool rest_eq by simp
    qed (use Some e2 in simp_all)
  qed
next
  case (25 fs ps fuel1 s st env k bs body sp fuel2)
  show ?case
  proof (cases "quant_dom s st k bs")
    case None
    then show ?thesis by simp
  next
    case (Some vd)
    obtain var dmv where vdeq: "vd = (var, dmv)" by (cases vd) auto
    have cf: "\<not> list_ex is_call (allSubexprs body)"
      using "25.prems" by (auto simp: list_ex_iff)
    have "eval_forall fs ps fuel1 s st env var dmv body
            = eval_forall fs ps fuel2 s st env var dmv body"
      using "25.IH"[OF Some[unfolded vdeq] refl cf] .
    then show ?thesis using Some vdeq by simp
  qed
qed (auto simp: list_ex_iff)

lemma string_in_list_iff: "string_in_list y xs = (y \<in> set xs)"
  by (induction xs) auto

lemma inline_calls_list_length [simp]:
  "length (inline_calls_list fs ps es) = length es"
  by (induction es) auto

lemma eval_bin_someD:
  "eval_bin bop x y = Some v \<Longrightarrow> \<exists>a b. x = Some a \<and> y = Some b"
  by (cases bop; cases x; cases y; auto split: option.splits ir_value.splits)

lemma eval_un_someD:
  "eval_un uop x = Some v \<Longrightarrow> \<exists>a. x = Some a"
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

lemma inline_calls_callfree_id:
  "\<not> list_ex is_call (allSubexprs e) \<Longrightarrow> inline_calls fs ps e = e"
  "\<not> list_ex is_call (allSubexprs_list es) \<Longrightarrow> inline_calls_list fs ps es = es"
  "\<not> list_ex is_call (allSubexprs_fields fas) \<Longrightarrow> inline_calls_fields fs ps fas = fas"
  "\<not> list_ex is_call (allSubexprs_entries ents) \<Longrightarrow> inline_calls_entries fs ps ents = ents"
  "\<not> list_ex is_call (allSubexprs_bindings bs) \<Longrightarrow> inline_calls_bindings fs ps bs = bs"
proof (induction fs ps e and fs ps es and fs ps fas and fs ps ents and fs ps bs
        rule: inline_calls_inline_calls_list_inline_calls_fields_inline_calls_entries_inline_calls_bindings.induct)
  case (1 fs ps callee args sp)
  from "1.prems" have False by simp
  thus ?case by blast
qed simp_all

lemma identNameFull_inline_calls:
  "identName b = Some x \<Longrightarrow> identName (inline_calls fs ps b) = Some x"
  by (cases b) auto

lemma inline_calls_peelRelationRefFull:
  "peelRelationRef base = Some rel
     \<Longrightarrow> peelRelationRef (inline_calls fs ps base) = Some rel"
  by (cases base rule: peelRelationRef.cases) (auto simp: identNameFull_inline_calls)

lemma dom_arg_inline_Some:
  assumes "lookup_callee fs ps (STR ''dom'') = None" and "dom_arg e = Some x"
  shows "dom_arg (inline_calls fs ps e) = Some x"
proof -
  obtain sp1 sp2 sp where e: "e = CallF (IdentifierF (STR ''dom'') sp1) [IdentifierF x sp2] sp"
    using dom_arg_SomeD[OF assms(2)] by blast
  have "List.find (\<lambda>f. fncName f = STR ''dom'') fs = None"
       "List.find (\<lambda>q. prdName q = STR ''dom'') ps = None"
    using assms(1) by (auto simp: lookup_callee_def split: option.splits)
  hence "inline_calls fs ps e = CallF (IdentifierF (STR ''dom'') sp1) [IdentifierF x sp2] sp"
    using e by simp
  thus ?thesis by simp
qed

lemma inline_calls_eval:
  "eval fs ps fuel s st env e = Some w
     \<Longrightarrow> eval fs ps fuel s st env (inline_calls fs ps e) = Some w"
  "eval_list fs ps fuel s st env es = Some ws
     \<Longrightarrow> eval_list fs ps fuel s st env (inline_calls_list fs ps es) = Some ws"
  "eval_entries fs ps fuel s st env ents = Some wes
     \<Longrightarrow> eval_entries fs ps fuel s st env (inline_calls_entries fs ps ents) = Some wes"
  "eval_fields fs ps fuel s st env fas = Some wfs
     \<Longrightarrow> eval_fields fs ps fuel s st env (inline_calls_fields fs ps fas) = Some wfs"
  "eval_the fs ps fuel s st env var dmv body = Some tms
     \<Longrightarrow> eval_the fs ps fuel s st env var dmv (inline_calls fs ps body) = Some tms"
  "eval_forall fs ps fuel s st env var dmv body = Some fr
     \<Longrightarrow> eval_forall fs ps fuel s st env var dmv (inline_calls fs ps body) = Some fr"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body and fs ps fuel s st env var dmv body
        arbitrary: w and ws and wes and wfs and tms and fr
        rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (6 fs ps fuel s st env bop l r sp w)
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    hence de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some by simp
    from dom_eq_domains_SomeD[OF de] have f0: "bop = BEq"
        and f1: "lookup_callee fs ps (STR ''dom'') = None"
        and "\<exists>rx. dom_arg l = Some rx \<and> state_relation_domain st rx = Some dx"
        and "\<exists>ry. dom_arg r = Some ry \<and> state_relation_domain st ry = Some dy" by auto
    then obtain rx ry where f2: "dom_arg l = Some rx" and f2d: "state_relation_domain st rx = Some dx"
        and f3: "dom_arg r = Some ry" and f3d: "state_relation_domain st ry = Some dy" by auto
    have dei: "dom_eq_domains fs ps st bop (inline_calls fs ps l) (inline_calls fs ps r) = Some (dx, dy)"
      using f0 f1 dom_arg_inline_Some[OF f1 f2] dom_arg_inline_Some[OF f1 f3] f2d f3d
      by (simp add: dom_eq_domains_def)
    have inlBin: "inline_calls fs ps (BinaryOpF bop l r sp)
                    = BinaryOpF bop (inline_calls fs ps l) (inline_calls fs ps r) sp" by simp
    show ?thesis using "6.prems"[unfolded eval_dom_eq[OF de]]
      unfolding inlBin eval_dom_eq[OF dei] by simp
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
    case None
    have g: "eval_bin bop (eval fs ps fuel s st env l)
               (eval fs ps fuel s st env r) = Some w"
      using "6.prems" deN None by simp
    obtain vl vr where l_s: "eval fs ps fuel s st env l = Some vl"
      and r_s: "eval fs ps fuel s st env r = Some vr"
      using eval_bin_someD[OF g] by blast
    have il: "eval fs ps fuel s st env (inline_calls fs ps l) = Some vl"
      using "6.IH"(1)[OF deN None l_s] .
    have ir: "eval fs ps fuel s st env (inline_calls fs ps r) = Some vr"
      using "6.IH"(2)[OF deN None r_s] .
    have deiN: "dom_eq_domains fs ps st bop (inline_calls fs ps l) (inline_calls fs ps r) = None"
    proof (cases "dom_eq_domains fs ps st bop (inline_calls fs ps l) (inline_calls fs ps r)")
      case (Some q)
      obtain dx dy where "q = (dx, dy)" by (cases q)
      with Some have sq: "dom_eq_domains fs ps st bop (inline_calls fs ps l) (inline_calls fs ps r) = Some (dx, dy)" by simp
      from dom_eq_domains_SomeD[OF sq] obtain rx where lkn: "lookup_callee fs ps (STR ''dom'') = None"
        and da: "dom_arg (inline_calls fs ps l) = Some rx" by auto
      obtain a b c where "inline_calls fs ps l = CallF (IdentifierF (STR ''dom'') a) [IdentifierF rx b] c"
        using dom_arg_SomeD[OF da] by blast
      hence "eval fs ps fuel s st env (inline_calls fs ps l) = None"
        using eval_dom_CallF[OF lkn] by simp
      thus ?thesis using il by simp
    qed simp
    show ?thesis
    proof (cases "beq_comp bop (inline_calls fs ps r)")
      case None
      thus ?thesis using il ir g l_s r_s deiN by simp
    next
      case (Some t')
      obtain v d p sp' where "inline_calls fs ps r = SetComprehensionF v d p sp'"
        using beq_comp_SetComp[OF Some] by blast
      hence "eval fs ps fuel s st env (inline_calls fs ps r) = None" by simp
      thus ?thesis using ir by simp
    qed
  next
    case (Some t)
    obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
    have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
    have bci: "beq_comp bop (inline_calls fs ps r) = Some (var, dnm, inline_calls fs ps pred)"
      using beq_comp_inline_Some[OF bc] .
    have deiN: "dom_eq_domains fs ps st bop (inline_calls fs ps l) (inline_calls fs ps r) = None"
    proof -
      obtain v' d' p' s' where "inline_calls fs ps r = SetComprehensionF v' d' p' s'"
        using beq_comp_SetComp[OF bci] by blast
      hence "dom_arg (inline_calls fs ps r) = None" by simp
      thus ?thesis by (simp add: dom_eq_domains_def split: option.splits prod.splits)
    qed
    from "6.prems" deN bc obtain dvs ms xs where
        enr: "\<not> schema_lookup_enum s dnm \<noteq> None"
        and srd: "state_relation_domain st dnm = Some dvs"
        and etm: "eval_the fs ps fuel s st env var dvs pred = Some ms"
        and els: "eval fs ps fuel s st env l = Some (VSet xs)"
        and lag: "list_all (\<lambda>x. contains_value dvs x) xs"
        and weq: "w = VBool (set xs = set ms)"
      by (auto split: option.splits ir_value.splits if_splits)
    have ethe: "eval_the fs ps fuel s st env var dvs (inline_calls fs ps pred) = Some ms"
      using "6.IH"(3)[OF deN bc refl refl enr srd etm] .
    have el: "eval fs ps fuel s st env (inline_calls fs ps l) = Some (VSet xs)"
      using "6.IH"(4)[OF deN bc refl refl enr srd etm els] .
    show ?thesis using bci enr srd ethe el lag weq deiN by simp
    qed
  qed
next
  case (7 fs ps fuel s st env uop e sp w)
  have g: "eval_un uop (eval fs ps fuel s st env e) = Some w"
    using "7.prems" by simp
  obtain ve where e_s: "eval fs ps fuel s st env e = Some ve"
    using eval_un_someD[OF g] by blast
  show ?case using "7.IH"[OF e_s] g e_s by simp
next
  case (8 fs ps fuel s st env c a b sp w)
  obtain vc where c_s: "eval fs ps fuel s st env c = Some vc"
    using "8.prems" by (cases "eval fs ps fuel s st env c") auto
  have ic: "eval fs ps fuel s st env (inline_calls fs ps c) = Some vc"
    using "8.IH"(1)[OF c_s] .
  show ?case
  proof (cases vc)
    case (VBool bb)
    show ?thesis
    proof (cases bb)
      case True
      have cT: "eval fs ps fuel s st env c = Some (VBool True)"
        using c_s VBool True by simp
      have av: "eval fs ps fuel s st env a = Some w" using "8.prems" cT by simp
      have "eval fs ps fuel s st env (inline_calls fs ps a) = Some w"
        using "8.IH"(2)[OF cT refl refl av] .
      then show ?thesis using ic cT VBool True by simp
    next
      case False
      have cF: "eval fs ps fuel s st env c = Some (VBool False)"
        using c_s VBool False by simp
      have bv: "eval fs ps fuel s st env b = Some w" using "8.prems" cF by simp
      have "eval fs ps fuel s st env (inline_calls fs ps b) = Some w"
        using "8.IH"(3)[OF cF refl refl bv] .
      then show ?thesis using ic cF VBool False by simp
    qed
  qed (use "8.prems" c_s in simp_all)
next
  case (9 fs ps fuel s st env x v body sp w)
  obtain va where va: "eval fs ps fuel s st env v = Some va"
    using "9.prems" by (cases "eval fs ps fuel s st env v") auto
  have iv: "eval fs ps fuel s st env (inline_calls fs ps v) = Some va"
    using "9.IH"(1)[OF va] .
  have bw: "eval fs ps fuel s st ((x, va) # env) body = Some w"
    using "9.prems" va by simp
  have "eval fs ps fuel s st ((x, va) # env) (inline_calls fs ps body) = Some w"
    using "9.IH"(2)[OF va bw] .
  then show ?case using iv va by simp
next
  case (10 fs ps fuel s st env base f sp w)
  obtain vb where vb: "eval fs ps fuel s st env base = Some vb"
    using "10.prems" by (cases "eval fs ps fuel s st env base") auto
  have "eval fs ps fuel s st env (inline_calls fs ps base) = Some vb"
    using "10.IH"[OF vb] .
  then show ?case using "10.prems" vb by simp
next
  case (11 fs ps fuel s st env e sp w)
  have e_s: "eval fs ps fuel s st env e = Some w" using "11.prems" by simp
  show ?case using "11.IH"[OF e_s] by simp
next
  case (12 fs ps fuel s st env e sp w)
  have e_s: "eval fs ps fuel s st env e = Some w" using "12.prems" by simp
  show ?case using "12.IH"[OF e_s] by simp
next
  case (13 fs ps fuel s st env e sp w)
  obtain ve where e_s: "eval fs ps fuel s st env e = Some ve"
    using "13.prems" by (cases "eval fs ps fuel s st env e") auto
  have "eval fs ps fuel s st env (inline_calls fs ps e) = Some ve"
    using "13.IH"[OF e_s] .
  then show ?case using "13.prems" e_s by simp
next
  case (14 fs ps fuel s st env e pat sp w)
  obtain ve where e_s: "eval fs ps fuel s st env e = Some ve"
    using "14.prems" by (cases "eval fs ps fuel s st env e") auto
  have "eval fs ps fuel s st env (inline_calls fs ps e) = Some ve"
    using "14.IH"[OF e_s] .
  then show ?case using "14.prems" e_s by simp
next
  case (15 fs ps fuel s st env callee args sp w)
  obtain fuel' where fuel: "fuel = Suc fuel'"
    using "15.prems" by (cases fuel) auto
  obtain nm sp1 where idc: "callee = IdentifierF nm sp1"
    using "15.prems" fuel by (cases callee) auto
  show ?case
  proof (cases "lookup_callee fs ps nm")
    case None
    show ?thesis
    proof (cases args)
      case Nil
      have inl: "inline_calls fs ps (CallF callee args sp) = CallF callee args sp"
        using idc None Nil by (auto simp: lookup_callee_def split: option.splits)
      show ?thesis using inl "15.prems" by simp
    next
      case (Cons arg rest)
      have aeq: "args = [arg]"
        using "15.prems" fuel idc None Cons
        by (cases rest) (auto split: option.splits ir_value.splits if_splits)
      have inl: "inline_calls fs ps (CallF callee args sp)
                   = CallF callee [inline_calls fs ps arg] sp"
        using idc None aeq by (auto simp: lookup_callee_def split: option.splits)
      show ?thesis
      proof (cases "is_builtin_pred nm")
        case True
        obtain str where ea: "eval fs ps fuel s st env arg = Some (VStr str)"
            and w_eq: "w = VBool (str_predicate nm str)"
          using "15.prems" fuel idc None aeq True
          by (auto split: option.splits ir_value.splits if_splits)
        have ea': "eval fs ps fuel s st env (inline_calls fs ps arg) = Some (VStr str)"
          using "15.IH" fuel idc None aeq ea True by (auto split: if_splits)
        show ?thesis using inl ea' w_eq fuel idc None aeq True
          by (simp split: option.splits ir_value.splits)
      next
        case False
        note npred = False
        show ?thesis
        proof (cases "is_builtin_func nm")
          case True
          note bif = True
          obtain str where ea: "eval fs ps fuel s st env arg = Some (VStr str)"
              and w_eq: "w = VStr (builtin_str_func nm str)"
            using "15.prems" fuel idc None aeq npred bif
            by (auto split: option.splits ir_value.splits if_splits)
          have ea': "eval fs ps fuel s st env (inline_calls fs ps arg) = Some (VStr str)"
            using "15.IH" fuel idc None aeq ea npred bif by (auto split: if_splits)
          show ?thesis using inl ea' w_eq fuel idc None aeq npred bif
            by (simp split: option.splits ir_value.splits)
        next
          case False
          note nfunc = False
          show ?thesis
          proof (cases "is_builtin_int_func nm")
            case True
            note bif = True
            obtain n where ea: "eval fs ps fuel s st env arg = Some (VInt n)"
                and w_eq: "w = VInt (builtin_int_func nm n)"
              using "15.prems" fuel idc None aeq npred nfunc bif
              by (auto split: option.splits ir_value.splits if_splits)
            have ea': "eval fs ps fuel s st env (inline_calls fs ps arg) = Some (VInt n)"
              using "15.IH" fuel idc None aeq ea npred nfunc bif by (auto split: if_splits)
            show ?thesis using inl ea' w_eq fuel idc None aeq npred nfunc bif
              by (simp split: option.splits ir_value.splits)
          next
            case False
            \<comment> \<open>nm = len: eval has no len case, so eval (CallF ...) = None contradicts the premise.\<close>
            note nint = False
            show ?thesis
              using "15.prems" fuel idc None aeq npred nfunc nint
              by (auto split: option.splits ir_value.splits if_splits)
          qed
        qed
      qed
    qed
  next
    case (Some pb)
    obtain params body where lc: "lookup_callee fs ps nm = Some (params, body)"
      using Some by (cases pb) auto
  have lenpa: "length params = length args"
    using "15.prems" fuel idc lc by (simp split: if_splits)
  have dpe: "distinct params"
    using "15.prems" fuel idc lc by (simp split: if_splits)
  obtain vals where vals: "eval_list fs ps fuel s st env args = Some vals"
    using "15.prems" fuel idc lc lenpa dpe
    by (cases "eval_list fs ps fuel s st env args") auto
  have body_w: "eval fs ps fuel' s st (zip params vals) body = Some w"
    using "15.prems" fuel idc lc lenpa dpe vals by simp
  have args_eq: "eval_list fs ps fuel s st env (inline_calls_list fs ps args) = Some vals"
    using "15.IH" fuel idc lc lenpa dpe vals by (auto split: if_splits)
  have lenpa': "length params = length (inline_calls_list fs ps args)"
    using lenpa by simp
  have lenv: "length params = length vals"
    using vals lenpa by (simp add: eval_list_length)
  have inl: "inline_calls fs ps (CallF callee args sp)
               = (if capture_safe body params (inline_calls_list fs ps args)
                  then bind_params params (inline_calls_list fs ps args) body
                  else CallF callee (inline_calls_list fs ps args) sp)"
    using inline_calls_CallF_lookup[OF lc] idc lenpa' by simp
  show ?thesis
  proof (cases "capture_safe body params (inline_calls_list fs ps args)")
    case True
    have pf: "list_all (\<lambda>a. list_all (\<lambda>q. \<not> string_in_list q (free_vars a)) params)
                 (inline_calls_list fs ps args)"
      using True by (simp add: capture_safe_def)
    have dp: "distinct params" using True by (simp add: capture_safe_def)
    have fvb: "list_all (\<lambda>x. string_in_list x params) (free_vars body)"
      using True by (simp add: capture_safe_def)
    have cfb: "\<not> list_ex is_call (allSubexprs body)"
      using True by (simp add: capture_safe_def)
    have step1: "eval fs ps fuel s st env (bind_params params (inline_calls_list fs ps args) body)
                   = eval fs ps fuel s st (zip params vals @ env) body"
      using bind_params_eval[OF pf lenpa' dp args_eq] .
    have step2: "eval fs ps fuel s st (zip params vals @ env) body
                   = eval fs ps fuel s st (zip params vals) body"
    proof (rule eval_coincidence(1), intro allI impI)
      fix y assume "string_in_list y (free_vars body)"
      then have yin: "y \<in> set params" using fvb by (auto simp: list_all_iff string_in_list_iff)
      show "env_lookup (zip params vals @ env) y = env_lookup (zip params vals) y"
        unfolding env_lookup_def by (rule map_of_zip_prefix[OF lenv yin])
    qed
    have step3: "eval fs ps fuel s st (zip params vals) body
                   = eval fs ps fuel' s st (zip params vals) body"
      using eval_callfree_fuel(1)[OF cfb] .
    show ?thesis using inl True step1 step2 step3 body_w by simp
  next
    case False
    have "eval fs ps fuel s st env (CallF callee (inline_calls_list fs ps args) sp) = Some w"
      using fuel idc lc lenpa' dpe args_eq body_w by simp
    then show ?thesis using inl False by simp
  qed
  qed
next
  case (22 fs ps fuel s st env base mem sp w)
  show ?case
  proof (cases base)
    case (IdentifierF en sp')
    with "22.prems" show ?thesis by simp
  qed (use "22.prems" in simp_all)
next
  case (23 fs ps fuel s st env base key sp w)
  obtain rel kv where pk: "peelRelationRef base = Some rel"
      and vk: "eval fs ps fuel s st env key = Some kv"
    using "23.prems" by (auto split: option.splits)
  have ik: "eval fs ps fuel s st env (inline_calls fs ps key) = Some kv"
    using "23.IH"[OF vk] .
  show ?case
    using "23.prems" pk vk ik inline_calls_peelRelationRefFull[OF pk] by simp
next
  case (28 fs ps fuel s st env e es ws)
  obtain v0 vs0 where v0: "eval fs ps fuel s st env e = Some v0"
    and vs0: "eval_list fs ps fuel s st env es = Some vs0"
    and ws_eq: "ws = v0 # vs0"
    using "28.prems" by (auto split: option.splits)
  have ie: "eval fs ps fuel s st env (inline_calls fs ps e) = Some v0"
    using "28.IH"(1)[OF v0] .
  have ies: "eval_list fs ps fuel s st env (inline_calls_list fs ps es) = Some vs0"
    using "28.IH"(2)[OF v0 vs0] .
  show ?case using ie ies ws_eq by simp
next
  case (19 fs ps fuel s st env entries sp w)
  from "19.prems" obtain wes where e: "eval_entries fs ps fuel s st env entries = Some wes"
      and weq: "w = VMap wes"
    by (auto split: option.splits)
  show ?case using "19.IH"[OF e] weq by simp
next
  case (30 fs ps fuel s st env k v msp rest wes)
  from "30.prems" obtain kv vv ps' where ek: "eval fs ps fuel s st env k = Some kv"
      and ev: "eval fs ps fuel s st env v = Some vv"
      and er: "eval_entries fs ps fuel s st env rest = Some ps'" and weq: "wes = (kv, vv) # ps'"
    by (auto split: option.splits)
  show ?case using "30.IH"(1)[OF ek] "30.IH"(2)[OF ev] "30.IH"(3)[OF er] weq by simp
next
  case (20 fs ps fuel s st env name fas sp w)
  from "20.prems" obtain fvs where e: "eval_fields fs ps fuel s st env fas = Some fvs"
      and weq: "w = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  show ?case using "20.IH"[OF e] weq by simp
next
  case (21 fs ps fuel s st env base fas sp w)
  from "21.prems" obtain bv fvs where eb: "eval fs ps fuel s st env base = Some bv"
      and ef: "eval_fields fs ps fuel s st env fas = Some fvs"
      and weq: "w = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  show ?case using "21.IH"(1)[OF eb] "21.IH"(2)[OF ef] weq by simp
next
  case (32 fs ps fuel s st env fld v fsp rest wfs)
  from "32.prems" obtain fv fvs0 where ev: "eval fs ps fuel s st env v = Some fv"
      and er: "eval_fields fs ps fuel s st env rest = Some fvs0" and weq: "wfs = (fld, fv) # fvs0"
    by (auto split: option.splits)
  show ?case using "32.IH"(1)[OF ev] "32.IH"(2)[OF er] weq by simp
next
  case (24 fs ps fuel s st env var dm body sp w)
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    show ?thesis
    proof (cases "state_relation_domain st rel")
      case None
      then show ?thesis using IdentifierF "24.prems" by simp
    next
      case (Some dmv)
      obtain x rest where et: "eval_the fs ps fuel s st env var dmv body = Some (x # rest)"
          and uniq: "list_all (\<lambda>y. y = x) rest" and w_eq: "w = x"
        using "24.prems" IdentifierF Some by (auto split: option.splits list.splits if_splits)
      have "eval_the fs ps fuel s st env var dmv (inline_calls fs ps body) = Some (x # rest)"
        using "24.IH"[OF IdentifierF Some et] .
      then show ?thesis using IdentifierF Some uniq w_eq by simp
    qed
  qed (use "24.prems" in simp_all)
next
  case (34 fs ps fuel s st env var v rest body tms)
  show ?case
  proof (cases "eval fs ps fuel s st ((var, v) # env) body")
    case None
    then show ?thesis using "34.prems" by simp
  next
    case (Some bv)
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have evb: "eval fs ps fuel s st ((var, v) # env) body = Some (VBool b)"
        using Some VBool by simp
      obtain matches where mr: "eval_the fs ps fuel s st env var rest body = Some matches"
          and tms_eq: "tms = (if b then v # matches else matches)"
        using "34.prems" evb by (auto split: option.splits)
      have ib: "eval fs ps fuel s st ((var, v) # env) (inline_calls fs ps body) = Some (VBool b)"
        using "34.IH"(1)[OF evb] .
      have im: "eval_the fs ps fuel s st env var rest (inline_calls fs ps body) = Some matches"
        using "34.IH"(2)[OF Some VBool mr] .
      show ?thesis using ib im evb tms_eq by simp
    qed (use "34.prems" Some in simp_all)
  qed
next
  case (36 fs ps fuel s st env var v rest body fr)
  show ?case
  proof (cases "eval fs ps fuel s st ((var, v) # env) body")
    case None
    then show ?thesis using "36.prems" by simp
  next
    case (Some bv)
    show ?thesis
    proof (cases bv)
      case (VBool b)
      have evb: "eval fs ps fuel s st ((var, v) # env) body = Some (VBool b)"
        using Some VBool by simp
      obtain acc where mr: "eval_forall fs ps fuel s st env var rest body = Some (VBool acc)"
          and fr_eq: "fr = VBool (b \<and> acc)"
        using "36.prems" evb by (auto split: option.splits ir_value.splits)
      have ib: "eval fs ps fuel s st ((var, v) # env) (inline_calls fs ps body) = Some (VBool b)"
        using "36.IH"(1)[OF evb] .
      have im: "eval_forall fs ps fuel s st env var rest (inline_calls fs ps body) = Some (VBool acc)"
        using "36.IH"(2)[OF Some VBool mr] .
      show ?thesis using ib im evb fr_eq by simp
    qed (use "36.prems" Some in simp_all)
  qed
next
  case (25 fs ps fuel s st env k bs body sp w)
  obtain var dmv where qd: "quant_dom s st k bs = Some (var, dmv)"
      and ef: "eval_forall fs ps fuel s st env var dmv body = Some w"
    using "25.prems" by (auto split: option.splits prod.splits)
  have qd': "quant_dom s st k (inline_calls_bindings fs ps bs) = Some (var, dmv)"
    using quant_dom_inline_calls[OF qd] .
  have "eval_forall fs ps fuel s st env var dmv (inline_calls fs ps body) = Some w"
    using "25.IH"[OF qd refl ef] .
  then show ?case using qd' by simp
qed (auto split: option.splits)

end
