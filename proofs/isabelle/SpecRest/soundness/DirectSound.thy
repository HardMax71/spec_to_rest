theory DirectSound
  imports
    DirectSound_Desugar
    SpecRest_Core.Semantics_Reference
begin
section \<open>Direct soundness: eval agrees with smtEval of translate\<close>

lemma binop_noncomp_step:
  assumes deN: "dom_eq_domains fs ps st bop l r = None"
      and bcN: "beq_comp bop r = None"
      and IHl: "\<And>vl lt. eval fs ps fuel s st env l = Some vl
                  \<Longrightarrow> translate enums l = Some lt
                  \<Longrightarrow> smtEval (correlate_model s st) (correlate_env env) lt
                        = Some (value_to_smt vl)"
      and IHr: "\<And>vr rt. eval fs ps fuel s st env r = Some vr
                  \<Longrightarrow> translate enums r = Some rt
                  \<Longrightarrow> smtEval (correlate_model s st) (correlate_env env) rt
                        = Some (value_to_smt vr)"
      and ev: "eval fs ps fuel s st env (BinaryOpF bop l r sp) = Some v"
      and tt: "translate enums (BinaryOpF bop l r sp) = Some t"
      and br: "builtins_reserved fs ps"
  shows "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
proof (cases bop)
  case BAnd
  from ev tt deN bcN br BAnd obtain a b where efl: "eval fs ps fuel s st env l = Some (VBool a)"
      and efr: "eval fs ps fuel s st env r = Some (VBool b)"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BAnd obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TAnd lt rt"
    by (auto split: option.splits)
  show ?thesis using teq ev tt deN bcN br BAnd efl efr
      IHl[OF efl tl] IHr[OF efr tr] by simp
next
  case BOr
  from ev tt deN bcN br BOr obtain a b where efl: "eval fs ps fuel s st env l = Some (VBool a)"
      and efr: "eval fs ps fuel s st env r = Some (VBool b)"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BOr obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TOr lt rt"
    by (auto split: option.splits)
  show ?thesis using teq ev tt deN bcN br BOr efl efr
      IHl[OF efl tl] IHr[OF efr tr] by simp
next
  case BImplies
  from ev tt deN bcN br BImplies obtain a b where efl: "eval fs ps fuel s st env l = Some (VBool a)"
      and efr: "eval fs ps fuel s st env r = Some (VBool b)"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BImplies obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TImplies lt rt"
    by (auto split: option.splits)
  show ?thesis using teq ev tt deN bcN br BImplies efl efr
      IHl[OF efl tl] IHr[OF efr tr] by simp
next
  case BIff
  from ev tt deN bcN br BIff obtain a b where efl: "eval fs ps fuel s st env l = Some (VBool a)"
      and efr: "eval fs ps fuel s st env r = Some (VBool b)"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BIff obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt"
      and teq: "t = TAnd (TImplies lt rt) (TImplies rt lt)"
    by (auto split: option.splits)
  have veq: "v = VBool (a = b)" using ev BIff deN bcN efl efr by simp
  show ?thesis using teq veq efl efr ev tt deN bcN br
      IHl[OF efl tl] IHr[OF efr tr] by auto
next
  case BEq
  from ev deN BEq bcN obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  have rnc: "\<nexists>var dnm s2 p s3. r = SetComprehensionF var (IdentifierF dnm s2) p s3"
    using efr by (cases r) auto
  have lcdom: "lookup_callee fs ps (STR ''dom'') = None"
    using br by (simp add: builtins_reserved_def)
  have dnone: "dom_arg l = None \<or> dom_arg r = None"
  proof (rule ccontr)
    assume "\<not> (dom_arg l = None \<or> dom_arg r = None)"
    then obtain rx where "dom_arg l = Some rx" by auto
    then obtain a b c where "l = CallF (IdentifierF (STR ''dom'') a) [IdentifierF rx b] c"
      using dom_arg_SomeD by blast
    hence "eval fs ps fuel s st env l = None" using eval_dom_CallF[OF lcdom] by simp
    thus False using efl by simp
  qed
  from tt[unfolded BEq translate_BEq_noncomp[OF rnc dnone]] obtain lt rt
      where tl: "translate enums l = Some lt"
        and tr: "translate enums r = Some rt"
        and teq: "t = TEq lt rt"
    by (auto split: option.splits)
  have veq: "v = VBool (ir_val_eq vl vr)"
    using ev BEq deN bcN efl efr by simp
  show ?thesis using teq veq ev tt deN bcN br
      TEq_sound[OF IHl[OF efl tl] IHr[OF efr tr]] by simp
next
  case BNeq
  from ev tt deN bcN br BNeq obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  from ev tt deN bcN br BNeq obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TNot (TEq lt rt)"
    by (auto split: option.splits)
  have veq: "v = VBool (\<not> ir_val_eq vl vr)"
    using ev BNeq deN bcN efl efr by simp
  show ?thesis using teq veq ev tt deN bcN br
      TEq_sound[OF IHl[OF efl tl] IHr[OF efr tr]] by simp
next
  case BLt
  from ev tt deN bcN br BLt obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  from ev tt deN bcN br BLt obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TLt lt rt"
    by (auto split: option.splits)
  have ec: "eval_cmp LtOp (Some vl) (Some vr) = Some v"
    using ev BLt deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TLt_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BGt
  from ev tt deN bcN br BGt obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  from ev tt deN bcN br BGt obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TLt rt lt"
    by (auto split: option.splits)
  have ec: "eval_cmp GtOp (Some vl) (Some vr) = Some v"
    using ev BGt deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TGt_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BLe
  from ev tt deN bcN br BLe obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  from ev tt deN bcN br BLe obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt"
      and teq: "t = TOr (TLt lt rt) (TEq lt rt)"
    by (auto split: option.splits)
  have ec: "eval_cmp LeOp (Some vl) (Some vr) = Some v"
    using ev BLe deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TLe_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BGe
  from ev tt deN bcN br BGe obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits)
  from ev tt deN bcN br BGe obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt"
      and teq: "t = TOr (TLt rt lt) (TEq lt rt)"
    by (auto split: option.splits)
  have ec: "eval_cmp GeOp (Some vl) (Some vr) = Some v"
    using ev BGe deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TGe_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BAdd
  from ev tt deN bcN br BAdd obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BAdd obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TAdd lt rt"
    by (auto split: option.splits)
  have ec: "eval_arith AddOp (Some vl) (Some vr) = Some v"
    using ev BAdd deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TArith_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BSub
  from ev tt deN bcN br BSub obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BSub obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TSub lt rt"
    by (auto split: option.splits)
  have ec: "eval_arith SubOp (Some vl) (Some vr) = Some v"
    using ev BSub deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TArith_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BMul
  from ev tt deN bcN br BMul obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BMul obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TMul lt rt"
    by (auto split: option.splits)
  have ec: "eval_arith MulOp (Some vl) (Some vr) = Some v"
    using ev BMul deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TArith_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BDiv
  from ev tt deN bcN br BDiv obtain vl vr where efl: "eval fs ps fuel s st env l = Some vl"
      and efr: "eval fs ps fuel s st env r = Some vr"
    by (auto split: option.splits ir_value.splits)
  from ev tt deN bcN br BDiv obtain lt rt where tl: "translate enums l = Some lt"
      and tr: "translate enums r = Some rt" and teq: "t = TDiv lt rt"
    by (auto split: option.splits)
  have ec: "eval_arith DivOp (Some vl) (Some vr) = Some v"
    using ev BDiv deN bcN efl efr by simp
  show ?thesis using teq ev tt deN bcN br
      TArith_sound[OF IHl[OF efl tl] IHr[OF efr tr] ec] by simp
next
  case BUnion
  with ev tt deN bcN br show ?thesis by simp
next
  case BIntersect
  with ev tt deN bcN br show ?thesis by simp
next
  case BDiff
  with ev tt deN bcN br show ?thesis by simp
next
  case BSubset
  with ev tt deN bcN br show ?thesis by simp
next
  case BIn
  with ev tt deN bcN br show ?thesis by simp
next
  case BNotIn
  with ev tt deN bcN br show ?thesis by simp
qed


lemma direct_soundness:
  "eval fs ps fuel s st env e = Some v \<Longrightarrow> translate enums e = Some t
     \<Longrightarrow> enums_wf s enums \<Longrightarrow> builtins_reserved fs ps
     \<Longrightarrow> smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
  "eval_list fs ps fuel s st env es = Some vs \<Longrightarrow> enums_wf s enums \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>t. translateSeqList enums es = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t = Some (SSeq (map value_to_smt vs))) \<and>
     (\<forall>t. translateSetList enums es = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t
          = Some (SSet (map value_to_smt (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))))"
  "eval_entries fs ps fuel s st env ents = Some mps \<Longrightarrow> enums_wf s enums \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>t. translateMapEntries enums ents = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t = Some (SMap (value_to_smt_entries mps)))"
  "eval_fields fs ps fuel s st env fas = Some fvs \<Longrightarrow> enums_wf s enums \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt t bv. smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv) \<longrightarrow>
        translate_with_assigns enums fas bt = Some t \<longrightarrow>
        smtEval (correlate_model s st) (correlate_env env) t
          = Some (value_to_smt (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)))"
  "eval_the fs ps fuel s st env var dmv body = Some tms \<Longrightarrow> enums_wf s enums \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt. translate enums body = Some bt \<longrightarrow>
        smtEval_the_rel (correlate_model s st) (correlate_env env) var (map value_to_smt dmv) bt
          = Some (map value_to_smt tms))"
  "eval_forall fs ps fuel s st env var dmv body = Some fr \<Longrightarrow> enums_wf s enums \<Longrightarrow>
     builtins_reserved fs ps \<Longrightarrow>
     (\<forall>bt. translate enums body = Some bt \<longrightarrow>
        smtEval_forall_rel (correlate_model s st) (correlate_env env) var (map value_to_smt dmv) bt
          = Some (value_to_smt fr))"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body
        and fs ps fuel s st env var dmv body
        arbitrary: v t and vs and mps and fvs and tms and fr
        rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
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
      using "6.prems"(1)[unfolded eval_dom_eq[OF de]] by simp
    have teq: "t = translate_dom_eq rx ry"
      using "6.prems"(2)[unfolded f0] f2 f3
      by (simp add: translate_beq_dom_or_none_def)
    show ?thesis using teq veq translate_dom_eq_sound[OF f2d f3d] by simp
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
    case None
    show ?thesis
      by (rule binop_noncomp_step[OF deN None
            IHl[OF deN None _ _ "6.prems"(3) "6.prems"(4)]
            IHr[OF deN None _ _ "6.prems"(3) "6.prems"(4)]
            "6.prems"(1) "6.prems"(2) "6.prems"(4)])
  next
    case (Some tc)
    then obtain var dnm pred where bc: "beq_comp bop r = Some (var, dnm, pred)"
      by (cases tc) auto
    have bopeq: "bop = BEq" using bc by (cases bop) auto
    from bc bopeq obtain s2 s3 where req: "r = SetComprehensionF var (IdentifierF dnm s2) pred s3"
      by (cases r) (auto split: expr.splits)
    from "6.prems"(1) deN bc obtain dvs xs where
        enr: "schema_lookup_enum s dnm = None"
        and srd: "state_relation_domain st dnm = Some dvs"
        and efl: "eval fs ps fuel s st env l = Some (VSet xs)"
        and la: "list_all (\<lambda>x. contains_value dvs x) xs"
      by (fastforce split: if_splits option.splits ir_value.splits)
    from "6.prems"(1) deN bc enr srd efl obtain ms where
        etm: "eval_the fs ps fuel s st env var dvs pred = Some ms"
        and veq: "v = VBool (set xs = set ms)"
      by (auto split: if_splits option.splits ir_value.splits)
    have lcdom: "lookup_callee fs ps (STR ''dom'') = None"
      using "6.prems"(4) by (simp add: builtins_reserved_def)
    have dnone: "dom_arg l = None"
    proof (rule ccontr)
      assume "dom_arg l \<noteq> None"
      then obtain rx where "dom_arg l = Some rx" by auto
      then obtain a b c where "l = CallF (IdentifierF (STR ''dom'') a) [IdentifierF rx b] c"
        using dom_arg_SomeD by blast
      hence "eval fs ps fuel s st env l = None" using eval_dom_CallF[OF lcdom] by simp
      thus False using efl by simp
    qed
    have bn: "translate_beq_dom_or_none l r = None"
      using dnone by (auto simp: translate_beq_dom_or_none_def split: option.splits)
    from "6.prems"(2) bopeq req bn obtain lt pt where
        tl: "translate enums l = Some lt"
        and tp: "translate enums pred = Some pt"
        and teq: "t = translate_set_comp_eq enums var dnm lt pt"
      by (auto split: option.splits)
    have dne: "\<not> string_in_list dnm enums" by (rule sil_none[OF "6.prems"(3) enr])
    have enr': "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
    have etr: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                 (map value_to_smt dvs) pt = Some (map value_to_smt ms)"
      using "6.IH"(3)[OF deN bc refl refl enr' srd etm "6.prems"(3) "6.prems"(4)] tp by blast
    have elt: "smtEval (correlate_model s st) (correlate_env env) lt
                 = Some (SSet (map value_to_smt xs))"
      using "6.IH"(4)[OF deN bc refl refl enr' srd etm efl tl "6.prems"(3) "6.prems"(4)] by simp
    have srd': "smt_model_lookup_rel (correlate_model s st) dnm = Some (map value_to_smt dvs)"
      using srd by simp
    have la': "list_all (\<lambda>x. contains_smt_val (map value_to_smt dvs) x) (map value_to_smt xs)"
      using la by (auto simp: list_all_iff)
    show ?thesis
      using teq veq
        smt_comp_assembly[OF elt etr srd' la' dne]
      by simp
  qed
  qed
next
  case (7 fs ps fuel s st env uop e sp v t)
  show ?case
  proof (cases uop)
    case UNot
    from "7.prems" UNot obtain b where ef: "eval fs ps fuel s st env e = Some (VBool b)"
        and veq: "v = VBool (\<not> b)"
      by (auto split: option.splits ir_value.splits)
    from "7.prems" UNot obtain et where te: "translate enums e = Some et"
        and teq: "t = TNot et"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) et = Some (SBool b)"
      using "7.IH"[OF ef te "7.prems"(3)] "7.prems"(4) by simp
    then show ?thesis using teq veq by simp
  next
    case UNegate
    from "7.prems" UNegate obtain v0 where ef: "eval fs ps fuel s st env e = Some v0"
      by (auto split: option.splits)
    from "7.prems" UNegate obtain et where te: "translate enums e = Some et"
        and teq: "t = TNeg et"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v0)"
      using "7.IH"[OF ef te "7.prems"(3)] "7.prems"(4) by simp
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
  from "8.prems" obtain ct at2 bt2 where tc: "translate enums c = Some ct"
      and ta: "translate enums a = Some at2" and tb: "translate enums b = Some bt2"
      and teq: "t = TIte ct at2 bt2"
    by (auto split: option.splits)
  from "8.prems" obtain bb where ec: "eval fs ps fuel s st env c = Some (VBool bb)"
    by (auto split: option.splits ir_value.splits)
  have ec': "smtEval (correlate_model s st) (correlate_env env) ct = Some (SBool bb)"
    using "8.IH"(1)[OF ec tc "8.prems"(3)] "8.prems"(4) by simp
  show ?case
  proof (cases bb)
    case True
    with ec have cT: "eval fs ps fuel s st env c = Some (VBool True)" by simp
    have ea: "eval fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    have "smtEval (correlate_model s st) (correlate_env env) at2 = Some (value_to_smt v)"
      using "8.IH"(2)[OF cT refl refl ea ta "8.prems"(3)] "8.prems"(4) by simp
    then show ?thesis using teq ec' True by simp
  next
    case False
    with ec have cF: "eval fs ps fuel s st env c = Some (VBool False)" by simp
    have eb: "eval fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    have "smtEval (correlate_model s st) (correlate_env env) bt2 = Some (value_to_smt v)"
      using "8.IH"(3)[OF cF refl refl eb tb "8.prems"(3)] "8.prems"(4) by simp
    then show ?thesis using teq ec' False by simp
  qed
next
  case (9 fs ps fuel s st env x ve body sp v t)
  from "9.prems" obtain va where eve: "eval fs ps fuel s st env ve = Some va"
      and ebody: "eval fs ps fuel s st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  from "9.prems" obtain vt bt where tve: "translate enums ve = Some vt"
      and tbody: "translate enums body = Some bt" and teq: "t = TLetIn x vt bt"
    by (auto split: option.splits)
  have ev': "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt va)"
    using "9.IH"(1)[OF eve tve "9.prems"(3)] "9.prems"(4) by simp
  have eb': "smtEval (correlate_model s st) (correlate_env ((x, va) # env)) bt = Some (value_to_smt v)"
    using "9.IH"(2)[OF eve ebody tbody "9.prems"(3)] "9.prems"(4) by simp
  show ?case using teq ev' eb' by simp
next
  case (10 fs ps fuel s st env base f sp v t)
  from "10.prems" obtain v0 where ef: "eval fs ps fuel s st env base = Some v0"
      and vfl: "value_field_lookup st v0 f = Some v"
    by (auto split: option.splits)
  from "10.prems" obtain bt where tb: "translate enums base = Some bt"
      and teq: "t = TFieldAccess bt f"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt v0)"
    using "10.IH"[OF ef tb "10.prems"(3)] "10.prems"(4) by simp
  then show ?case using teq vfl value_field_lookup_correlated[of s st v0 f] by simp
next
  case (11 fs ps fuel s st env e sp v t)
  from "11.prems" have ef: "eval fs ps fuel s st env e = Some v" by simp
  from "11.prems" obtain et where te: "translate enums e = Some et"
      and teq: "t = TPrime et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v)"
    using "11.IH"[OF ef te "11.prems"(3)] "11.prems"(4) by simp
  then show ?case using teq by simp
next
  case (12 fs ps fuel s st env e sp v t)
  from "12.prems" have ef: "eval fs ps fuel s st env e = Some v" by simp
  from "12.prems" obtain et where te: "translate enums e = Some et"
      and teq: "t = TPre et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v)"
    using "12.IH"[OF ef te "12.prems"(3)] "12.prems"(4) by simp
  then show ?case using teq by simp
next
  case (13 fs ps fuel s st env e sp v t)
  from "13.prems" obtain v0 where ef: "eval fs ps fuel s st env e = Some v0"
      and veq: "v = VSome v0"
    by (auto split: option.splits)
  from "13.prems" obtain et where te: "translate enums e = Some et"
      and teq: "t = TSome et"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (value_to_smt v0)"
    using "13.IH"[OF ef te "13.prems"(3)] "13.prems"(4) by simp
  then show ?case using teq veq by simp
next
  case (14 fs ps fuel s st env e pat sp v t)
  from "14.prems" obtain str where ef: "eval fs ps fuel s st env e = Some (VStr str)"
      and veq: "v = VBool (string_matches str pat)"
    by (auto split: option.splits ir_value.splits)
  from "14.prems" obtain et where te: "translate enums e = Some et"
      and teq: "t = TMatches et pat"
    by (auto split: option.splits)
  have "smtEval (correlate_model s st) (correlate_env env) et = Some (SStr str)"
    using "14.IH"[OF ef te "14.prems"(3)] "14.prems"(4) by simp
  then show ?case using teq veq by simp
next
  case (15 fs ps fuel s st env callee args sp v t)
  obtain fuel' where fuel: "fuel = Suc fuel'" using "15.prems"(1) by (cases fuel) auto
  from "15.prems"(2) obtain nm sp1 arg argt where
      ceq: "callee = IdentifierF nm sp1" and aeq: "args = [arg]" and bip: "is_builtin_pred nm"
      and ta: "translate enums arg = Some argt" and teq: "t = TUStrPred nm argt"
    by (cases callee; cases args) (auto split: if_splits list.splits option.splits)
  have lc_none: "lookup_callee fs ps nm = None"
    using "15.prems"(4) bip by (simp add: builtins_reserved_def)
  from "15.prems"(1) fuel ceq aeq lc_none bip obtain str where
      ea: "eval fs ps fuel s st env arg = Some (VStr str)"
      and veq: "v = VBool (str_predicate nm str)"
    by (auto split: option.splits ir_value.splits if_splits)
  have ev: "smtEval (correlate_model s st) (correlate_env env) argt = Some (SStr str)"
    using "15.IH" fuel ceq aeq lc_none bip ea ta "15.prems"(3) "15.prems"(4)
    by (auto split: if_splits)
  show ?case using teq veq ev by simp
next
  case (17 fs ps fuel s st env es sp v t)
  from "17.prems"(1) obtain vs where efl: "eval_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSeq vs"
    by (auto split: option.splits)
  from "17.prems"(2) have ls: "translateSeqList enums es = Some t" by simp
  show ?case using "17.IH"[OF efl "17.prems"(3)] ls veq "17.prems"(4) by fastforce
next
  case (18 fs ps fuel s st env es sp v t)
  from "18.prems"(1) obtain vs where efl: "eval_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])"
    by (auto split: option.splits)
  from "18.prems"(2) have ls: "translateSetList enums es = Some t" by simp
  show ?case using "18.IH"[OF efl "18.prems"(3)] ls veq "18.prems"(4) by fastforce
next
  case (19 fs ps fuel s st env entries sp v t)
  from "19.prems"(1) obtain mps where e: "eval_entries fs ps fuel s st env entries = Some mps"
      and veq: "v = VMap mps"
    by (auto split: option.splits)
  from "19.prems"(2) have ls: "translateMapEntries enums entries = Some t" by simp
  show ?case using "19.IH"[OF e "19.prems"(3)] ls veq "19.prems"(4) by fastforce
next
  case (20 fs ps fuel s st env name fas sp v t)
  from "20.prems"(1) obtain fvs where e: "eval_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  from "20.prems"(2) have lw: "translate_with_assigns enums fas (TEntityBase name) = Some t" by simp
  have eb: "smtEval (correlate_model s st) (correlate_env env) (TEntityBase name)
              = Some (value_to_smt (VEntity name (STR '''')))" by simp
  show ?case using "20.IH"[OF e "20.prems"(3)] eb lw veq "20.prems"(4) by fastforce
next
  case (21 fs ps fuel s st env base fas sp v t)
  from "21.prems"(1) obtain bv fvs where eb: "eval fs ps fuel s st env base = Some bv"
      and ef: "eval_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  from "21.prems"(2) obtain bt where tb: "translate enums base = Some bt"
      and lw: "translate_with_assigns enums fas bt = Some t"
    by (auto split: option.splits)
  have eb': "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv)"
    using "21.IH"(1)[OF eb tb "21.prems"(3)] "21.prems"(4) by simp
  show ?case using "21.IH"(2)[OF ef "21.prems"(3)] eb' lw veq "21.prems"(4) by fastforce
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
  from "23.prems"(1) obtain rel kv where pk: "peelRelationRef base = Some rel"
      and ek: "eval fs ps fuel s st env key = Some kv"
      and slk: "state_lookup_key st rel kv = Some v"
    by (auto split: option.splits)
  from "23.prems"(2) obtain bt kt where tb: "translate enums base = Some bt"
      and tk: "translate enums key = Some kt" and teq: "t = TIndexRel bt kt"
    by (auto split: option.splits)
  have pr: "peelSmtRelationRef bt = Some rel" using peelSmt_tfd[OF pk tb] .
  have "smtEval (correlate_model s st) (correlate_env env) kt = Some (value_to_smt kv)"
    using "23.IH"[OF ek tk "23.prems"(3)] "23.prems"(4) by simp
  then show ?case using teq pr slk correlate_model_lookup_key[of s st rel kv] by simp
next
  case (24 fs ps fuel s st env var dm body sp v t)
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    from "24.prems"(2) IdentifierF obtain bt where tb: "translate enums body = Some bt"
        and teq: "t = TTheRel var rel bt"
      by (auto split: if_splits option.splits)
    from "24.prems"(1) IdentifierF obtain dmv x rest where srd: "state_relation_domain st rel = Some dmv"
        and eft: "eval_the fs ps fuel s st env var dmv body = Some (x # rest)"
        and uniq: "list_all (\<lambda>y. y = x) rest" and v_eq: "v = x"
      by (auto split: option.splits list.splits if_splits)
    have etr: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                 (map value_to_smt dmv) bt = Some (map value_to_smt (x # rest))"
      using "24.IH"[OF IdentifierF srd eft "24.prems"(3)] tb "24.prems"(4) by fastforce
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
    have eff: "eval_forall fs ps fuel s st env var dmv body = Some v"
      using "25.prems"(1) qd by simp
    from quant_dom_some_shape[OF qd] obtain dnm sp1 dty a where
        kq: "k = QAll"
        and bseq: "bs = [QuantifierBindingFull var (IdentifierF dnm sp1) dty a]"
        and dmrel: "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
                     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)"
      by blast
    obtain bt where tbody: "translate enums body = Some bt"
      using "25.prems"(2) kq bseq by (auto split: option.splits)
    have ih: "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                (map value_to_smt dmv) bt = Some (value_to_smt v)"
      using "25.IH"[OF Some[unfolded vdeq] refl eff wf] tbody "25.prems"(4) by fastforce
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
  from "28.prems" obtain v0 vs0 where ev0: "eval fs ps fuel s st env e = Some v0"
      and evs0: "eval_list fs ps fuel s st env es = Some vs0" and vseq: "vs = v0 # vs0"
    by (auto split: option.splits)
  show ?case
  proof (intro conjI allI impI)
    fix t
    assume lse: "translateSeqList enums (e # es) = Some t"
    from lse obtain e0t s0t where te0: "translate enums e = Some e0t"
        and ts0: "translateSeqList enums es = Some s0t" and teq: "t = TSeqCons e0t s0t"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) e0t = Some (value_to_smt v0)"
      using "28.IH"(1)[OF ev0 te0 "28.prems"(2)] "28.prems"(3) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) s0t
                     = Some (SSeq (map value_to_smt vs0))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) "28.prems"(3)] ts0 by blast
    ultimately show "smtEval (correlate_model s st) (correlate_env env) t
                       = Some (SSeq (map value_to_smt vs))" using teq vseq by simp
  next
    fix t
    assume lse: "translateSetList enums (e # es) = Some t"
    from lse obtain e0t s0t where te0: "translate enums e = Some e0t"
        and ts0: "translateSetList enums es = Some s0t" and teq: "t = TSetInsert e0t s0t"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) e0t = Some (value_to_smt v0)"
      using "28.IH"(1)[OF ev0 te0 "28.prems"(2)] "28.prems"(3) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) s0t
                     = Some (SSet (map value_to_smt (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs0 [])))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2) "28.prems"(3)] ts0 by blast
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
  from "30.prems" obtain kv vv mps0 where ek: "eval fs ps fuel s st env k = Some kv"
      and ev: "eval fs ps fuel s st env v = Some vv"
      and er: "eval_entries fs ps fuel s st env rest = Some mps0" and mpeq: "mps = (kv, vv) # mps0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix t
    assume lme: "translateMapEntries enums (MapEntryFull k v msp # rest) = Some t"
    from lme obtain kt vt mt where tk: "translate enums k = Some kt"
        and tv: "translate enums v = Some vt"
        and tm: "translateMapEntries enums rest = Some mt" and teq: "t = TMapCons kt vt mt"
      by (auto split: option.splits)
    have "smtEval (correlate_model s st) (correlate_env env) kt = Some (value_to_smt kv)"
      using "30.IH"(1)[OF ek tk "30.prems"(2)] "30.prems"(3) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt vv)"
      using "30.IH"(2)[OF ev tv "30.prems"(2)] "30.prems"(3) by simp
    moreover have "smtEval (correlate_model s st) (correlate_env env) mt
                     = Some (SMap (value_to_smt_entries mps0))"
      using "30.IH"(3)[OF er "30.prems"(2)] tm "30.prems"(3) by fastforce
    ultimately show "smtEval (correlate_model s st) (correlate_env env) t
                       = Some (SMap (value_to_smt_entries mps))" using teq mpeq by simp
  qed
next
  case (31 fs ps fuel s st env fvs)
  then show ?case by (auto split: option.splits)
next
  case (32 fs ps fuel s st env fld v fsp rest fvs)
  from "32.prems" obtain fv fvs0 where ev: "eval fs ps fuel s st env v = Some fv"
      and er: "eval_fields fs ps fuel s st env rest = Some fvs0" and fveq: "fvs = (fld, fv) # fvs0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix bt t bv
    assume sb: "smtEval (correlate_model s st) (correlate_env env) bt = Some (value_to_smt bv)"
    assume dw: "translate_with_assigns enums (FieldAssignFull fld v fsp # rest) bt = Some t"
    from dw obtain vt where tv: "translate enums v = Some vt"
        and dwr: "translate_with_assigns enums rest (TWithRec bt fld vt) = Some t"
      by (auto split: option.splits)
    have ev': "smtEval (correlate_model s st) (correlate_env env) vt = Some (value_to_smt fv)"
      using "32.IH"(1)[OF ev tv "32.prems"(2)] "32.prems"(3) by simp
    have ebw: "smtEval (correlate_model s st) (correlate_env env) (TWithRec bt fld vt)
                 = Some (value_to_smt (VEntityWith bv fld fv))"
      using sb ev' by simp
    have "smtEval (correlate_model s st) (correlate_env env) t
            = Some (value_to_smt (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntityWith bv fld fv) fvs0))"
      using "32.IH"(2)[OF er "32.prems"(2)] ebw dwr "32.prems"(3) by fastforce
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
    assume tb: "translate enums body = Some bt"
    show "smtEval_the_rel (correlate_model s st) (correlate_env env) var
            (map value_to_smt (v # rest)) bt = Some (map value_to_smt tms)"
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
        have eb: "smtEval (correlate_model s st) ((var, value_to_smt v) # correlate_env env) bt
                    = Some (SBool b)"
          using "34.IH"(1)[OF evb tb "34.prems"(2)] "34.prems"(3) by simp
        have er: "smtEval_the_rel (correlate_model s st) (correlate_env env) var
                    (map value_to_smt rest) bt = Some (map value_to_smt matches)"
          using "34.IH"(2)[OF Some VBool mr "34.prems"(2)] tb "34.prems"(3) by fastforce
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
    assume tb: "translate enums body = Some bt"
    show "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
            (map value_to_smt (v # rest)) bt = Some (value_to_smt fr)"
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
        have eb: "smtEval (correlate_model s st) ((var, value_to_smt v) # correlate_env env) bt
                    = Some (SBool b)"
          using "36.IH"(1)[OF evb tb "36.prems"(2)] "36.prems"(3) by simp
        have er: "smtEval_forall_rel (correlate_model s st) (correlate_env env) var
                    (map value_to_smt rest) bt = Some (SBool acc)"
          using "36.IH"(2)[OF Some VBool mr "36.prems"(2)] tb "36.prems"(3) by fastforce
        show ?thesis using eb er fr_eq by simp
      qed (use "36.prems" Some in simp_all)
    qed
  qed
qed (auto split: option.splits ir_value.splits)

theorem translate_soundness_standalone:
  assumes "eval fs ps fuel s st env e = Some v"
      and "translate enums e = Some t"
      and "enums_wf s enums"
      and "builtins_reserved fs ps"
  shows "smtEval (correlate_model s st) (correlate_env env) t = Some (value_to_smt v)"
  by (rule direct_soundness(1)[OF assms])

end
