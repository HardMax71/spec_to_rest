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

lemma lower_preserves_eval_full:
  "eval_full fs ps fuel s st env e = Some v \<Longrightarrow> lower enums e = Some e'
     \<Longrightarrow> eval s st env e' = Some v"
  "eval_full_list fs ps fuel s st env es = Some vs \<Longrightarrow>
     (\<forall>enums sp e'. lowerSeqList enums es sp = Some e' \<longrightarrow> eval s st env e' = Some (VSeq vs)) \<and>
     (\<forall>enums sp e'. lowerSetList enums es sp = Some e' \<longrightarrow>
        eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])))"
  "eval_full_entries fs ps fuel s st env ents = Some mps \<Longrightarrow>
     (\<forall>enums sp e'. lowerMapEntries enums ents sp = Some e' \<longrightarrow> eval s st env e' = Some (VMap mps))"
  "eval_full_fields fs ps fuel s st env fas = Some fvs \<Longrightarrow>
     (\<forall>enums b sp e' bv. eval s st env b = Some bv \<longrightarrow> lower_with_assigns enums fas b sp = Some e'
        \<longrightarrow> eval s st env e' = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs))"
  "eval_full_the fs ps fuel s st env var dmv body = Some tms \<Longrightarrow>
     (\<forall>enums body'. lower enums body = Some body' \<longrightarrow> eval_the_rel s st env var dmv body' = Some tms)"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es and fs ps fuel s st env ents
        and fs ps fuel s st env fas and fs ps fuel s st env var dmv body
        arbitrary: v e' enums and vs and mps and fvs and tms
        rule: eval_full_eval_full_list_eval_full_entries_eval_full_fields_eval_full_the.induct)
  case (6 fs ps fuel s st env bop l r sp v e' enums)
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
  case (7 fs ps fuel s st env uop e sp v e' enums)
  show ?case
  proof (cases uop)
    case UNot
    from "7.prems" UNot obtain b where ef: "eval_full fs ps fuel s st env e = Some (VBool b)"
        and veq: "v = VBool (\<not> b)"
      by (auto split: option.splits ir_value.splits)
    from "7.prems" UNot obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNot e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some (VBool b)" using "7.IH"[OF ef le] .
    then show ?thesis using e'eq veq by simp
  next
    case UNegate
    from "7.prems" UNegate obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      by (auto split: option.splits)
    from "7.prems" UNegate obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = UnNeg e'' sp"
      by (auto split: option.splits)
    have "eval s st env e'' = Some v0" using "7.IH"[OF ef le] .
    then show ?thesis using e'eq ef "7.prems" UNegate by (auto split: ir_value.splits)
  next
    case UCardinality
    with "7.prems" show ?thesis by (auto split: option.splits)
  next
    case UPower
    with "7.prems" show ?thesis by (auto split: option.splits)
  qed
next
  case (8 fs ps fuel s st env c a b sp v e' enums)
  from "8.prems" obtain c' a' b' where lc: "lower enums c = Some c'"
      and la: "lower enums a = Some a'" and lb: "lower enums b = Some b'"
      and e'eq: "e' = Ite c' a' b' sp"
    by (auto split: option.splits)
  from "8.prems" obtain bb where ec: "eval_full fs ps fuel s st env c = Some (VBool bb)"
    by (auto split: option.splits ir_value.splits)
  have ec': "eval s st env c' = Some (VBool bb)" using "8.IH"(1)[OF ec lc] .
  show ?case
  proof (cases bb)
    case True
    with ec have cT: "eval_full fs ps fuel s st env c = Some (VBool True)" by simp
    have ea: "eval_full fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    have "eval s st env a' = Some v" using "8.IH"(2)[OF cT refl refl ea la] .
    then show ?thesis using e'eq ec' True by simp
  next
    case False
    with ec have cF: "eval_full fs ps fuel s st env c = Some (VBool False)" by simp
    have eb: "eval_full fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    have "eval s st env b' = Some v" using "8.IH"(3)[OF cF refl refl eb lb] .
    then show ?thesis using e'eq ec' False by simp
  qed
next
  case (9 fs ps fuel s st env x ve body sp v e' enums)
  from "9.prems" obtain va where eve: "eval_full fs ps fuel s st env ve = Some va"
      and ebody: "eval_full fs ps fuel s st ((x, va) # env) body = Some v"
    by (auto split: option.splits)
  from "9.prems" obtain v' b' where lve: "lower enums ve = Some v'"
      and lbody: "lower enums body = Some b'" and e'eq: "e' = LetIn x v' b' sp"
    by (auto split: option.splits)
  have ev': "eval s st env v' = Some va" using "9.IH"(1)[OF eve lve] .
  have eb': "eval s st ((x, va) # env) b' = Some v" using "9.IH"(2)[OF eve ebody lbody] .
  show ?case using e'eq ev' eb' by simp
next
  case (10 fs ps fuel s st env base f sp v e' enums)
  from "10.prems" obtain v0 where ef: "eval_full fs ps fuel s st env base = Some v0"
      and vfl: "value_field_lookup st v0 f = Some v"
    by (auto split: option.splits)
  from "10.prems" obtain b' where lb: "lower enums base = Some b'"
      and e'eq: "e' = FieldAccess b' f sp"
    by (auto split: option.splits)
  have "eval s st env b' = Some v0" using "10.IH"[OF ef lb] .
  then show ?case using e'eq vfl by simp
next
  case (11 fs ps fuel s st env e sp v e' enums)
  from "11.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "11.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Prime e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "11.IH"[OF ef le] .
  then show ?case using e'eq by simp
next
  case (12 fs ps fuel s st env e sp v e' enums)
  from "12.prems" have ef: "eval_full fs ps fuel s st env e = Some v" by simp
  from "12.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Pre e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v" using "12.IH"[OF ef le] .
  then show ?case using e'eq by simp
next
  case (13 fs ps fuel s st env e sp v e' enums)
  from "13.prems" obtain v0 where ef: "eval_full fs ps fuel s st env e = Some v0"
      and veq: "v = VSome v0"
    by (auto split: option.splits)
  from "13.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = SomeE e'' sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some v0" using "13.IH"[OF ef le] .
  then show ?case using e'eq veq by simp
next
  case (14 fs ps fuel s st env e pat sp v e' enums)
  from "14.prems" obtain str where ef: "eval_full fs ps fuel s st env e = Some (VStr str)"
      and veq: "v = VBool (string_matches str pat)"
    by (auto split: option.splits ir_value.splits)
  from "14.prems" obtain e'' where le: "lower enums e = Some e''" and e'eq: "e' = Matches e'' pat sp"
    by (auto split: option.splits)
  have "eval s st env e'' = Some (VStr str)" using "14.IH"[OF ef le] .
  then show ?case using e'eq veq by simp
next
  case (15 fs ps fuel s st env callee args sp v e' enums)
  then show ?case by simp
next
  case (17 fs ps fuel s st env es sp v e' enums)
  from "17.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSeq vs"
    by (auto split: option.splits)
  from "17.prems"(2) have ls: "lowerSeqList enums es sp = Some e'" by simp
  show ?case using "17.IH"[OF efl] ls veq by auto
next
  case (18 fs ps fuel s st env es sp v e' enums)
  from "18.prems"(1) obtain vs where efl: "eval_full_list fs ps fuel s st env es = Some vs"
      and veq: "v = VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs [])"
    by (auto split: option.splits)
  from "18.prems"(2) have ls: "lowerSetList enums es sp = Some e'" by simp
  show ?case using "18.IH"[OF efl] ls veq by auto
next
  case (19 fs ps fuel s st env entries sp v e' enums)
  from "19.prems"(1) obtain mps where e: "eval_full_entries fs ps fuel s st env entries = Some mps"
      and veq: "v = VMap mps"
    by (auto split: option.splits)
  from "19.prems"(2) have ls: "lowerMapEntries enums entries sp = Some e'" by simp
  show ?case using "19.IH"[OF e] ls veq by auto
next
  case (22 fs ps fuel s st env base mem sp v e' enums)
  show ?case
  proof (cases base)
    case (IdentifierF en sp')
    have e': "e' = EnumAccess en mem sp" using "22.prems"(2) IdentifierF by simp
    have "eval s st env (EnumAccess en mem sp) = Some v" using "22.prems"(1) IdentifierF by simp
    thus ?thesis using e' by simp
  qed (use "22.prems" in simp_all)
next
  case (23 fs ps fuel s st env base key sp v e' enums)
  from "23.prems"(1) obtain rel kv where pk: "peelRelationRefFull base = Some rel"
      and ek: "eval_full fs ps fuel s st env key = Some kv"
      and slk: "state_lookup_key st rel kv = Some v"
    by (auto split: option.splits)
  from "23.prems"(2) obtain base' key' where lb: "lower enums base = Some base'"
      and lk: "lower enums key = Some key'" and e'eq: "e' = IndexRel base' key' sp"
    by (auto split: option.splits)
  have pr: "peel_relation_ref base' = Some rel" using peelRelationRefFull_lower[OF pk lb] .
  have "eval s st env key' = Some kv" using "23.IH"[OF ek lk] .
  then show ?case using e'eq pr slk by simp
next
  case (26 fs ps fuel s st env vs)
  then show ?case by (auto split: option.splits)
next
  case (27 fs ps fuel s st env e es vs)
  from "27.prems" obtain v0 vs0 where ev0: "eval_full fs ps fuel s st env e = Some v0"
      and evs0: "eval_full_list fs ps fuel s st env es = Some vs0" and vseq: "vs = v0 # vs0"
    by (auto split: option.splits)
  show ?case
  proof (intro conjI allI impI)
    fix enums sp e'
    assume lse: "lowerSeqList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSeqList enums es sp = Some s0'" and e'eq: "e' = SeqCons e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "27.IH"(1)[OF ev0 le0] .
    moreover have "eval s st env s0' = Some (VSeq vs0)"
      using "27.IH"(2)[OF ev0 evs0] ls0 by blast
    ultimately show "eval s st env e' = Some (VSeq vs)" using e'eq vseq by simp
  next
    fix enums sp e'
    assume lse: "lowerSetList enums (e # es) sp = Some e'"
    from lse obtain e0' s0' where le0: "lower enums e = Some e0'"
        and ls0: "lowerSetList enums es sp = Some s0'" and e'eq: "e' = SetInsert e0' s0' sp"
      by (auto split: option.splits)
    have "eval s st env e0' = Some v0" using "27.IH"(1)[OF ev0 le0] .
    moreover have "eval s st env s0' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs0 []))"
      using "27.IH"(2)[OF ev0 evs0] ls0 by blast
    ultimately show "eval s st env e' = Some (VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))"
      using e'eq vseq by simp
  qed
next
  case (28 fs ps fuel s st env mps)
  then show ?case by (auto split: option.splits)
next
  case (29 fs ps fuel s st env k v msp rest mps)
  from "29.prems" obtain kv vv mps0 where ek: "eval_full fs ps fuel s st env k = Some kv"
      and ev: "eval_full fs ps fuel s st env v = Some vv"
      and er: "eval_full_entries fs ps fuel s st env rest = Some mps0" and mpeq: "mps = (kv, vv) # mps0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix enums sp e'
    assume lme: "lowerMapEntries enums (MapEntryFull k v msp # rest) sp = Some e'"
    from lme obtain k' v' m' where lk: "lower enums k = Some k'" and lv: "lower enums v = Some v'"
        and lm: "lowerMapEntries enums rest sp = Some m'" and e'eq: "e' = MapCons k' v' m' sp"
      by (auto split: option.splits)
    have "eval s st env k' = Some kv" using "29.IH"(1)[OF ek lk] .
    moreover have "eval s st env v' = Some vv" using "29.IH"(2)[OF ev lv] .
    moreover have "eval s st env m' = Some (VMap mps0)"
      using "29.IH"(3)[OF er] lm by blast
    ultimately show "eval s st env e' = Some (VMap mps)" using e'eq mpeq by simp
  qed
next
  case (20 fs ps fuel s st env name fas sp v e' enums)
  from "20.prems"(1) obtain fvs where e: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs"
    by (auto split: option.splits)
  from "20.prems"(2) have lw: "lower_with_assigns enums fas (EntityBase name sp) sp = Some e'" by simp
  have eb: "eval s st env (EntityBase name sp) = Some (VEntity name (STR ''''))" by simp
  show ?case using "20.IH"[OF e] eb lw veq by blast
next
  case (21 fs ps fuel s st env base fas sp v e' enums)
  from "21.prems"(1) obtain bv fvs where eb: "eval_full fs ps fuel s st env base = Some bv"
      and ef: "eval_full_fields fs ps fuel s st env fas = Some fvs"
      and veq: "v = foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs"
    by (auto split: option.splits)
  from "21.prems"(2) obtain base' where lb: "lower enums base = Some base'"
      and lw: "lower_with_assigns enums fas base' sp = Some e'"
    by (auto split: option.splits)
  have eb': "eval s st env base' = Some bv" using "21.IH"(1)[OF eb lb] .
  show ?case using "21.IH"(2)[OF ef] eb' lw veq by blast
next
  case (30 fs ps fuel s st env fvs)
  then show ?case by (auto split: option.splits)
next
  case (31 fs ps fuel s st env fld v fsp rest fvs)
  from "31.prems" obtain fv fvs0 where ev: "eval_full fs ps fuel s st env v = Some fv"
      and er: "eval_full_fields fs ps fuel s st env rest = Some fvs0" and fveq: "fvs = (fld, fv) # fvs0"
    by (auto split: option.splits)
  show ?case
  proof (intro allI impI)
    fix enums b sp e' bv
    assume eb: "eval s st env b = Some bv"
    assume lw: "lower_with_assigns enums (FieldAssignFull fld v fsp # rest) b sp = Some e'"
    from lw obtain v' where lv: "lower enums v = Some v'"
        and lwr: "lower_with_assigns enums rest (WithRec b fld v' sp) sp = Some e'"
      by (auto split: option.splits)
    have ev': "eval s st env v' = Some fv" using "31.IH"(1)[OF ev lv] .
    have ebw: "eval s st env (WithRec b fld v' sp) = Some (VEntityWith bv fld fv)"
      using eb ev' by simp
    have "eval s st env e'
            = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntityWith bv fld fv) fvs0)"
      using "31.IH"(2)[OF er] ebw lwr by blast
    then show "eval s st env e'
                 = Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)"
      using fveq by simp
  qed
next
  case (24 fs ps fuel s st env var dm body sp v e' enums)
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
      using "24.IH"[OF IdentifierF srd eft] lb by blast
    show ?thesis using e'eq srd etr uniq v_eq by simp
  qed (use "24.prems"(2) in simp_all)
next
  case (32 fs ps fuel s st env var body tms)
  show ?case using "32.prems" by auto
next
  case (33 fs ps fuel s st env var v rest body tms)
  show ?case
  proof (intro allI impI)
    fix enums body'
    assume lb: "lower enums body = Some body'"
    show "eval_the_rel s st env var (v # rest) body' = Some tms"
    proof (cases "eval_full fs ps fuel s st ((var, v) # env) body")
      case None
      then show ?thesis using "33.prems" by simp
    next
      case (Some bv)
      show ?thesis
      proof (cases bv)
        case (VBool b)
        have evb: "eval_full fs ps fuel s st ((var, v) # env) body = Some (VBool b)"
          using Some VBool by simp
        obtain matches where mr: "eval_full_the fs ps fuel s st env var rest body = Some matches"
            and tms_eq: "tms = (if b then v # matches else matches)"
          using "33.prems" evb by (auto split: option.splits)
        have eb: "eval s st ((var, v) # env) body' = Some (VBool b)"
          using "33.IH"(1)[OF evb lb] .
        have er: "eval_the_rel s st env var rest body' = Some matches"
          using "33.IH"(2)[OF Some VBool mr] lb by blast
        show ?thesis using eb er tms_eq by simp
      qed (use "33.prems" Some in simp_all)
    qed
  qed
qed (auto split: option.splits ir_value.splits)

end
