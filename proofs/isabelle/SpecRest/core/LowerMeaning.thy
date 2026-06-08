theory LowerMeaning
  imports Semantics_Reference IR_Lower
begin

text \<open>Stage 1 of the \<open>expr\<close>/\<open>expr_full\<close> unification (#391): \<open>lower\<close> is meaning-preserving
  against the reference semantics \<open>eval_full\<close>. Today \<open>lower_soundness\<close> only relates
  \<open>translate\<close> to \<open>eval\<close> of lower's \<^emph>\<open>output\<close> (trusted); this closes the gap on lower's
  \<^emph>\<open>input\<close> (the #386 comprehension-bug class). The statement quantifies over all \<open>e\<close> and
  is vacuous wherever \<open>eval_full\<close> is still \<open>None\<close> (binders/collections), so it widens for
  free as \<open>eval_full\<close>'s coverage grows.\<close>

lemma identNameFull_SomeD:
  "identNameFull e = Some x \<Longrightarrow> (\<exists>sp. e = IdentifierF x sp)"
  by (cases e) auto

lemma peelRelationRefFull_lower:
  "peelRelationRefFull base = Some rel \<Longrightarrow> lower enums base = Some base'
     \<Longrightarrow> peel_relation_ref base' = Some rel"
  by (cases base rule: peelRelationRefFull.cases) (auto dest!: identNameFull_SomeD)

definition enums_wf :: "schema \<Rightarrow> String.literal list \<Rightarrow> bool" where
  "enums_wf s enums = (\<forall>en. string_in_list en enums = (schema_lookup_enum s en \<noteq> None))"

lemma eval_forall_enum_eq_rel:
  "eval_forall_enum s st env var en members body
     = eval_forall_rel s st env var (map (\<lambda>m. VEnum en m) members) body"
proof (induction members)
  case Nil
  show ?case by simp
next
  case (Cons mem rest)
  show ?case
  proof (cases "eval s st ((var, VEnum en mem) # env) body")
    case None
    then show ?thesis by simp
  next
    case (Some bv)
    show ?thesis using Some by (cases bv) (simp_all add: Cons.IH)
  qed
qed

lemma quant_dom_some_shape:
  "quant_dom s st k bs = Some (var, dmv) \<Longrightarrow>
     k = QAll \<and> (\<exists>dnm sp1 dty a. bs = [QuantifierBindingFull var (IdentifierF dnm sp1) dty a] \<and>
       ((\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
        \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)))"
  by (erule quant_dom.elims; auto split: option.splits)

lemma sil_enum:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = Some d \<Longrightarrow> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma sil_none:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = None \<Longrightarrow> \<not> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma dmrel_enum:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = Some d
   \<Longrightarrow> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
  by auto

lemma dmrel_rel:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = None
   \<Longrightarrow> state_relation_domain st dnm = Some dmv"
  by auto

lemma eval_ForallEnum_Some:
  "schema_lookup_enum s dnm = Some d \<Longrightarrow>
   eval s st env (ForallEnum var dnm body sp) = eval_forall_enum s st env var dnm (enm_members d) body"
  by simp

lemma eval_ForallRel_Some:
  "state_relation_domain st dnm = Some rd \<Longrightarrow>
   eval s st env (ForallRel var dnm body sp) = eval_forall_rel s st env var rd body"
  by simp

lemma lower_preserves_eval_full:
    "eval_full fs ps fuel s st env e = Some v \<Longrightarrow> lower enums e = Some e'
       \<Longrightarrow> enums_wf s enums \<Longrightarrow> eval s st env e' = Some v"
    "eval_full_list fs ps fuel s st env es = Some vs \<Longrightarrow> enums_wf s enums \<Longrightarrow>
       (\<forall>sp e'. lowerSeqList enums es sp = Some e' \<longrightarrow> eval s st env e' = Some (VSeq vs)) \<and>
       (\<forall>sp e'. lowerSetList enums es sp = Some e' \<longrightarrow>
          eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])))"
    "eval_full_entries fs ps fuel s st env ents = Some mps \<Longrightarrow> enums_wf s enums \<Longrightarrow>
       (\<forall>sp e'. lowerMapEntries enums ents sp = Some e' \<longrightarrow> eval s st env e' = Some (VMap mps))"
    "eval_full_fields fs ps fuel s st env fas = Some fvs \<Longrightarrow> enums_wf s enums \<Longrightarrow>
       (\<forall>b sp e' bv. eval s st env b = Some bv \<longrightarrow> lower_with_assigns enums fas b sp = Some e'
          \<longrightarrow> eval s st env e' = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs))"
    "eval_full_the fs ps fuel s st env var dmv body = Some tms \<Longrightarrow> enums_wf s enums \<Longrightarrow>
       (\<forall>body'. lower enums body = Some body' \<longrightarrow> eval_the_rel s st env var dmv body' = Some tms)"
    "eval_full_forall fs ps fuel s st env var dmv body = Some fr \<Longrightarrow> enums_wf s enums \<Longrightarrow>
       (\<forall>body'. lower enums body = Some body' \<longrightarrow> eval_forall_rel s st env var dmv body' = Some fr)"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body
        and fs ps fuel s st env var dmv body
        arbitrary: v e' and vs and mps and fvs and tms and fr
        rule: eval_full_eval_full_list_eval_full_entries_eval_full_fields_eval_full_the_eval_full_forall.induct)
  case (6 fs ps fuel s st env bop l r sp v e')
  note IHl = "6.IH"(1) and IHr = "6.IH"(2)
  show ?case
  proof (cases bop)
    case BAnd
    from "6.prems" BAnd obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BAnd obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin AndOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BAnd efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BOr
    from "6.prems" BOr obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BOr obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin OrOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BOr efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BImplies
    from "6.prems" BImplies obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BImplies obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin ImpliesOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BImplies efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BIff
    from "6.prems" BIff obtain a b where efl: "eval_full fs ps fuel s st env l = Some (VBool a)"
        and efr: "eval_full fs ps fuel s st env r = Some (VBool b)"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BIff obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = BoolBin IffOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BIff efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BEq
    from "6.prems" BEq obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    have rnc: "\<nexists>var dnm s2 p s3. r = SetComprehensionF var (IdentifierF dnm s2) p s3"
      using efr by (cases r) auto
    from "6.prems"(2)[unfolded BEq lower_BEq_noncomp[OF rnc]] obtain l' r'
        where ll: "lower enums l = Some l'" and lr: "lower enums r = Some r'"
          and e'eq: "e' = Cmp EqOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BEq efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BNeq
    from "6.prems" BNeq obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BNeq obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp NeqOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BNeq efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BLt
    from "6.prems" BLt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BLt obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp LtOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BLt efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BGt
    from "6.prems" BGt obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BGt obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp GtOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BGt efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BLe
    from "6.prems" BLe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BLe obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp LeOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BLe efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BGe
    from "6.prems" BGe obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits)
    from "6.prems" BGe obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Cmp GeOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BGe efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BAdd
    from "6.prems" BAdd obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BAdd obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith AddOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BAdd efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BSub
    from "6.prems" BSub obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BSub obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith SubOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BSub efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BMul
    from "6.prems" BMul obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BMul obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith MulOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BMul efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
  next
    case BDiv
    from "6.prems" BDiv obtain vl vr where efl: "eval_full fs ps fuel s st env l = Some vl"
        and efr: "eval_full fs ps fuel s st env r = Some vr"
      by (auto split: option.splits ir_value.splits)
    from "6.prems" BDiv obtain l' r' where ll: "lower enums l = Some l'"
        and lr: "lower enums r = Some r'" and e'eq: "e' = Arith DivOp l' r' sp"
      by (auto split: option.splits)
    show ?thesis using e'eq "6.prems" BDiv efl efr IHl[OF efl ll] IHr[OF efr lr] by simp
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
  case (7 fs ps fuel s st env uop e sp v e')
  show ?case
  proof (cases uop)
    case UNot
    from "7.prems" UNot obtain b where ef: "eval_full fs ps fuel s st env e = Some (VBool b)"
        and veq: "v = VBool (\<not> b)"
      by (auto split: option.splits ir_value.splits)
    from "7.prems" UNot obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNot e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some (VBool b)" using "7.IH"[OF ef le "7.prems"(3)] .
    then show ?thesis using e'eq veq by simp
  next
    case UNegate
    from "7.prems" UNegate obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      by (auto split: option.splits)
    from "7.prems" UNegate obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNeg e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some v0" using "7.IH"[OF ef le "7.prems"(3)] .
    then show ?thesis using e'eq ef "7.prems" UNegate by (auto split: ir_value.splits)
  next
    case UCardinality
    with "7.prems" show ?thesis by (auto split: option.splits)
  next
    case UPower
    with "7.prems" show ?thesis by (auto split: option.splits)
  qed
next
  case (8 fs ps fuel s st env c a b sp v e')
  from "8.prems" obtain c' a' b' where lc: "lower enums c = Some c'"
      and la: "lower enums a = Some a'" and lb: "lower enums b = Some b'"
      and e'eq: "e' = Ite c' a' b' sp"
    by (auto split: option.splits)
  from "8.prems" obtain bb where ec: "eval_full fs ps fuel s st env c = Some (VBool bb)"
    by (auto split: option.splits ir_value.splits)
  have ec': "eval s st env c' = Some (VBool bb)" using "8.IH"(1)[OF ec lc "8.prems"(3)] .
  show ?case
  proof (cases bb)
    case True
    with ec have cT: "eval_full fs ps fuel s st env c = Some (VBool True)" by simp
    have ea: "eval_full fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    have "eval s st env a' = Some v" using "8.IH"(2)[OF cT refl refl ea la "8.prems"(3)] .
    then show ?thesis using e'eq ec' True by simp
  next
    case False
    with ec have cF: "eval_full fs ps fuel s st env c = Some (VBool False)" by simp
    have eb: "eval_full fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    have "eval s st env b' = Some v" using "8.IH"(3)[OF cF refl refl eb lb "8.prems"(3)] .
    then show ?thesis using e'eq ec' False by simp
  qed
next
  case (9 fs ps fuel s st env x ve body sp v e')
  from "9.prems" obtain va where eve: "eval_full fs ps fuel s st env ve = Some va"
      and ebody: "eval_full fs ps fuel s st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  from "9.prems" obtain v' b' where lve: "lower enums ve = Some v'"
      and lbody: "lower enums body = Some b'" and e'eq: "e' = LetIn x v' b' sp"
    by (auto split: option.splits)
  have ev': "eval s st env v' = Some va" using "9.IH"(1)[OF eve lve "9.prems"(3)] .
  have eb': "eval s st ((x, va) # env) b' = Some v" using "9.IH"(2)[OF eve ebody lbody "9.prems"(3)] .
  show ?case using e'eq ev' eb' by simp
next
  case (10 fs ps fuel s st env base f sp v e')
  from "10.prems" obtain v0 where ef: "eval_full fs ps fuel s st env base = Some v0"
      and vfl: "value_field_lookup st v0 f = Some v"
    by (auto split: option.splits)
  from "10.prems" obtain b' where lb: "lower enums base = Some b'"
      and e'eq: "e' = FieldAccess b' f sp"
    by (auto split: option.splits)
  have "eval s st env b' = Some v0" using "10.IH"[OF ef lb "10.prems"(3)] .
  then show ?case using e'eq vfl by simp
next
  case (11 fs ps fuel s st env e sp v e')
  from "11.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "11.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Prime e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "11.IH"[OF ef le "11.prems"(3)] .
  then show ?case using e'eq by simp
next
  case (12 fs ps fuel s st env e sp v e')
  from "12.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "12.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Pre e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "12.IH"[OF ef le "12.prems"(3)] .
  then show ?case using e'eq by simp
next
  case (13 fs ps fuel s st env e sp v e')
  from "13.prems" obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      and veq: "v = VSome v0"
    by (auto split: option.splits)
  from "13.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = SomeE e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v0" using "13.IH"[OF ef le "13.prems"(3)] .
  then show ?case using e'eq veq by simp
next
  case (14 fs ps fuel s st env e pat sp v e')
  from "14.prems" obtain str where ef: "eval_full fs ps fuel s st env e = Some (VStr str)"
      and veq: "v = VBool (string_matches str pat)"
    by (auto split: option.splits ir_value.splits)
  from "14.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Matches e'' pat sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some (VStr str)" using "14.IH"[OF ef le "14.prems"(3)] .
  then show ?case using e'eq veq by simp
next
  case (15 fs ps fuel s st env callee args sp v e')
  then show ?case by simp
next
  case (17 fs ps fuel s st env es sp v e')
  from "17.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSeq vs"
    by (auto split: option.splits)
  from "17.prems"(2) have ls: "lowerSeqList enums es sp = Some e'" by simp
  show ?case using "17.IH"[OF efl "17.prems"(3)] ls veq by auto
next
  case (18 fs ps fuel s st env es sp v e')
  from "18.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])"
    by (auto split: option.splits)
  from "18.prems"(2) have ls: "lowerSetList enums es sp = Some e'" by simp
  show ?case using "18.IH"[OF efl "18.prems"(3)] ls veq by auto
next
  case (19 fs ps fuel s st env entries sp v e')
  from "19.prems"(1) obtain mps where e: "eval_full_entries fs ps fuel s st env entries = Some mps"
      and veq: "v = VMap mps"
    by (auto split: option.splits)
  from "19.prems"(2) have ls: "lowerMapEntries enums entries sp = Some e'" by simp
  show ?case using "19.IH"[OF e "19.prems"(3)] ls veq by auto
next
  case (22 fs ps fuel s st env base mem sp v e')
  show ?case
  proof (cases base)
    case (IdentifierF en sp')
    have e': "e' = EnumAccess en mem sp" using "22.prems"(2) IdentifierF by simp
    have "eval s st env (EnumAccess en mem sp) = Some v" using "22.prems"(1) IdentifierF by simp
    thus ?thesis using e' by simp
  qed (use "22.prems" in simp_all)
next
  case (23 fs ps fuel s st env base key sp v e')
  from "23.prems"(1) obtain rel kv where pk: "peelRelationRefFull base = Some rel"
      and ek: "eval_full fs ps fuel s st env key = Some kv"
      and slk: "state_lookup_key st rel kv = Some v"
    by (auto split: option.splits)
  from "23.prems"(2) obtain base' key' where lb: "lower enums base = Some base'"
      and lk: "lower enums key = Some key'" and e'eq: "e' = IndexRel base' key' sp"
    by (auto split: option.splits)
  have pr: "peel_relation_ref base' = Some rel" using peelRelationRefFull_lower[OF pk lb] .
  have "eval s st env key' = Some kv" using "23.IH"[OF ek lk "23.prems"(3)] .
  then show ?case using e'eq pr slk by simp
next
  case (27 fs ps fuel s st env vs)
  then show ?case by (auto split: option.splits)
next
  case (28 fs ps fuel s st env e es vs)
  from "28.prems" obtain v0 vs0 where ev0: "eval_full fs ps fuel s st env e = Some v0"
      and evs0: "eval_full_list fs ps fuel s st env es = Some vs0" and vseq: "vs = v0 # vs0"
    by (auto split: option.splits)
  show ?case
  proof (intro conjI allI impI)
    fix sp e'
    assume lse: "lowerSeqList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSeqList enums es sp = Some s0'" and e'eq: "e' = SeqCons e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "28.IH"(1)[OF ev0 le0 "28.prems"(2)] .
    moreover have "eval s st env s0' = Some (VSeq vs0)"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2)] ls0 by blast
    ultimately show "eval s st env e' = Some (VSeq vs)" using e'eq vseq by simp
  next
    fix sp e'
    assume lse: "lowerSetList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSetList enums es sp = Some s0'" and e'eq: "e' = SetInsert e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "28.IH"(1)[OF ev0 le0 "28.prems"(2)] .
    moreover have "eval s st env s0' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs0 []))"
      using "28.IH"(2)[OF ev0 evs0 "28.prems"(2)] ls0 by blast
    ultimately show "eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))"
      using e'eq vseq by simp
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
    fix sp e'
    assume lme: "lowerMapEntries enums (MapEntryFull k v msp # rest) sp = Some e'"
    from lme obtain k' v' m' where lk: "lower enums k = Some k'" and lv: "lower enums v = Some v'"
        and lm: "lowerMapEntries enums rest sp = Some m'" and e'eq: "e' = MapCons k' v' m' sp"
      by (auto split: option.splits)
    have "eval s st env k' = Some kv" using "30.IH"(1)[OF ek lk "30.prems"(2)] .
    moreover have "eval s st env v' = Some vv" using "30.IH"(2)[OF ev lv "30.prems"(2)] .
    moreover have "eval s st env m' = Some (VMap mps0)"
      using "30.IH"(3)[OF er "30.prems"(2)] lm by blast
    ultimately show "eval s st env e' = Some (VMap mps)" using e'eq mpeq by simp
  qed
next
  case (20 fs ps fuel s st env name fas sp v e')
  from "20.prems"(1) obtain fvs where e: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  from "20.prems"(2) have lw: "lower_with_assigns enums fas (EntityBase name sp) sp = Some e'" by simp
  have eb: "eval s st env (EntityBase name sp) = Some (VEntity name (STR ''''))" by simp
  show ?case using "20.IH"[OF e "20.prems"(3)] eb lw veq by blast
next
  case (21 fs ps fuel s st env base fas sp v e')
  from "21.prems"(1) obtain bv fvs where eb: "eval_full fs ps fuel s st env base = Some bv"
      and ef: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  from "21.prems"(2) obtain base' where lb: "lower enums base = Some base'"
      and lw: "lower_with_assigns enums fas base' sp = Some e'"
    by (auto split: option.splits)
  have eb': "eval s st env base' = Some bv" using "21.IH"(1)[OF eb lb "21.prems"(3)] .
  show ?case using "21.IH"(2)[OF ef "21.prems"(3)] eb' lw veq by blast
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
    fix b sp e' bv
    assume eb: "eval s st env b = Some bv"
    assume lw: "lower_with_assigns enums (FieldAssignFull fld v fsp # rest) b sp = Some e'"
    from lw obtain v' where lv: "lower enums v = Some v'"
        and lwr: "lower_with_assigns enums rest (WithRec b fld v' sp) sp = Some e'"
      by (auto split: option.splits)
    have ev': "eval s st env v' = Some fv" using "32.IH"(1)[OF ev lv "32.prems"(2)] .
    have ebw: "eval s st env (WithRec b fld v' sp) = Some (VEntityWith bv fld fv)"
      using eb ev' by simp
    have "eval s st env e'
            = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntityWith bv fld fv) fvs0)"
      using "32.IH"(2)[OF er "32.prems"(2)] ebw lwr by blast
    then show "eval s st env e'
                 = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)"
      using fveq by simp
  qed
next
  case (24 fs ps fuel s st env var dm body sp v e')
  show ?case
  proof (cases dm)
    case (IdentifierF rel sp')
    from "24.prems"(2) IdentifierF obtain body' where lb: "lower enums body = Some body'"
        and e'eq: "e' = TheRel var rel body' sp"
      by (auto split: if_splits option.splits)
    from "24.prems"(1) IdentifierF obtain dmv x rest where srd: "state_relation_domain st rel = Some dmv"
        and eft: "eval_full_the fs ps fuel s st env var dmv body = Some (x # rest)"
        and uniq: "list_all (\<lambda>y. y = x) rest" and v_eq: "v = x"
      by (auto split: option.splits list.splits if_splits)
    have etr: "eval_the_rel s st env var dmv body' = Some (x # rest)"
      using "24.IH"[OF IdentifierF srd eft "24.prems"(3)] lb by blast
    show ?thesis using e'eq srd etr uniq v_eq by simp
  qed (use "24.prems"(2) in simp_all)
next
  case (25 fs ps fuel s st env k bs body sp v e')
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
    obtain body' where lbody: "lower enums body = Some body'"
      using "25.prems"(2) kq bseq by (auto split: option.splits)
    have ih: "eval_forall_rel s st env var dmv body' = Some v"
      using "25.IH"[OF Some[unfolded vdeq] refl eff wf] lbody by blast
    show ?thesis
    proof (cases "schema_lookup_enum s dnm")
      case (Some d)
      note sd = this
      have si: "string_in_list dnm enums"
      proof (rule sil_enum)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = Some d" by (rule sd)
      qed
      have e'eq: "e' = ForallEnum var dnm body' sp"
        using "25.prems"(2) kq bseq lbody si by (auto split: option.splits)
      have dmv_eq: "dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
        using dmrel sd by (rule dmrel_enum)
      show ?thesis
        using e'eq sd dmv_eq ih
        by (simp add: eval_ForallEnum_Some eval_forall_enum_eq_rel)
    next
      case None
      note sn = this
      have nsi: "\<not> string_in_list dnm enums"
      proof (rule sil_none)
        show "enums_wf s enums" by (rule wf)
        show "schema_lookup_enum s dnm = None" by (rule sn)
      qed
      have e'eq: "e' = ForallRel var dnm body' sp"
        using "25.prems"(2) kq bseq lbody nsi by (auto split: option.splits)
      have sr: "state_relation_domain st dnm = Some dmv"
        using dmrel sn by (rule dmrel_rel)
      show ?thesis
        using e'eq sr ih
        by (simp add: eval_ForallRel_Some)
    qed
  qed
next
  case (33 fs ps fuel s st env var body tms)
  show ?case using "33.prems" by auto
next
  case (34 fs ps fuel s st env var v rest body tms)
  show ?case
  proof (intro allI impI)
    fix body'
    assume lb: "lower enums body = Some body'"
    show "eval_the_rel s st env var (v # rest) body' = Some tms"
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
        have eb: "eval s st ((var, v) # env) body' = Some (VBool b)"
          using "34.IH"(1)[OF evb lb "34.prems"(2)] .
        have er: "eval_the_rel s st env var rest body' = Some matches"
          using "34.IH"(2)[OF Some VBool mr "34.prems"(2)] lb by blast
        show ?thesis using eb er tms_eq by simp
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
    fix body'
    assume lb: "lower enums body = Some body'"
    show "eval_forall_rel s st env var (v # rest) body' = Some fr"
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
        have eb: "eval s st ((var, v) # env) body' = Some (VBool b)"
          using "36.IH"(1)[OF evb lb "36.prems"(2)] .
        have er: "eval_forall_rel s st env var rest body' = Some (VBool acc)"
          using "36.IH"(2)[OF Some VBool mr "36.prems"(2)] lb by blast
        show ?thesis using eb er fr_eq by simp
      qed (use "36.prems" Some in simp_all)
    qed
  qed
qed (auto split: option.splits ir_value.splits)

end
