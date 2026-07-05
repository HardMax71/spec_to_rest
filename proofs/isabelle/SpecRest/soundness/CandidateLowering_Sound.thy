theory CandidateLowering_Sound
  imports
    Semantics_Inlining
    "SpecRest_Codegen.CandidateLowering"
begin

text \<open>The entailment the candidate lowering promised: whenever the reference
  semantics gives an original ensures conjunct a value at all, that value is
  forced to true by the lowered contract, i.e. by the copied requires clause
  (position substituted by the candidate, \<open>pre()\<close> stripped) together with the
  glue equality \<open>pos = cand\<close> the lowering appends.

  Two syntactic side conditions scope the claim. \<open>sn_ok x e\<close> says the
  substituted name never occurs where \<open>eval\<close> reads a state or schema NAME
  rather than a value (\<open>dom\<close> arguments, comprehension and \<open>the\<close> and
  quantifier domains, index bases, enum bases): an output name in such a
  position is spec nonsense, but the reference evaluator would key its state
  lookups on the renamed identifier there. \<open>bn_ok c e\<close> says no binder in
  \<open>e\<close> shadows the candidate name, so the substituted identifier cannot be
  captured; generated candidate names carry the \<open>cand_\<close> prefix, which no
  spec binder uses.\<close>

fun sn_ok :: "String.literal \<Rightarrow> expr \<Rightarrow> bool"
and sn_ok_list :: "String.literal \<Rightarrow> expr list \<Rightarrow> bool"
and sn_ok_fields :: "String.literal \<Rightarrow> field_assign list \<Rightarrow> bool"
and sn_ok_entries :: "String.literal \<Rightarrow> map_entry list \<Rightarrow> bool"
and sn_ok_bindings :: "String.literal \<Rightarrow> quantifier_binding list \<Rightarrow> bool"
where
  "sn_ok x (BinaryOpF op l r _) = (sn_ok x l \<and> sn_ok x r)"
| "sn_ok x (UnaryOpF op e _) = sn_ok x e"
| "sn_ok x (FieldAccessF b f _) = sn_ok x b"
| "sn_ok x (EnumAccessF b m _) =
     (case b of IdentifierF n _ \<Rightarrow> n \<noteq> x | _ \<Rightarrow> sn_ok x b)"
| "sn_ok x (IndexF b i _) =
     (peelRelationRef b \<noteq> Some x \<and> sn_ok x b \<and> sn_ok x i)"
| "sn_ok x (CallF c args _) =
     ((case (identName c, args) of
         (Some d, [IdentifierF a _]) \<Rightarrow> d \<noteq> STR ''dom'' \<or> a \<noteq> x
       | _ \<Rightarrow> True) \<and>
      sn_ok_list x args)"
| "sn_ok x (PrimeF e _) = sn_ok x e"
| "sn_ok x (PreF e _) = sn_ok x e"
| "sn_ok x (WithF b upds _) = (sn_ok x b \<and> sn_ok_fields x upds)"
| "sn_ok x (IfF c t e _) = (sn_ok x c \<and> sn_ok x t \<and> sn_ok x e)"
| "sn_ok x (LetF v vl body _) = (sn_ok x vl \<and> sn_ok x body)"
| "sn_ok x (LambdaF p b _) = sn_ok x b"
| "sn_ok x (ConstructorF n fs _) = sn_ok_fields x fs"
| "sn_ok x (SetLiteralF xs _) = sn_ok_list x xs"
| "sn_ok x (MapLiteralF es _) = sn_ok_entries x es"
| "sn_ok x (SetComprehensionF v d p _) =
     ((case d of IdentifierF n _ \<Rightarrow> n \<noteq> x | _ \<Rightarrow> True) \<and> sn_ok x d \<and> sn_ok x p)"
| "sn_ok x (SeqLiteralF xs _) = sn_ok_list x xs"
| "sn_ok x (MatchesF e pat _) = sn_ok x e"
| "sn_ok x (SomeWrapF e _) = sn_ok x e"
| "sn_ok x (TheF v d b _) =
     ((case d of IdentifierF n _ \<Rightarrow> n \<noteq> x | _ \<Rightarrow> True) \<and> sn_ok x d \<and> sn_ok x b)"
| "sn_ok x (QuantifierF q bs body _) =
     (list_all (\<lambda>b. case b of QuantifierBindingFull _ (IdentifierF n _) _ _ \<Rightarrow> n \<noteq> x
                    | _ \<Rightarrow> True) bs \<and>
      sn_ok_bindings x bs \<and> sn_ok x body)"
| "sn_ok x (IntLitF n _) = True"
| "sn_ok x (FloatLitF n _) = True"
| "sn_ok x (StringLitF n _) = True"
| "sn_ok x (BoolLitF v _) = True"
| "sn_ok x (NoneLitF _) = True"
| "sn_ok x (IdentifierF n _) = True"
| "sn_ok_list x [] = True"
| "sn_ok_list x (e # es) = (sn_ok x e \<and> sn_ok_list x es)"
| "sn_ok_fields x [] = True"
| "sn_ok_fields x (FieldAssignFull f v _ # fs) = (sn_ok x v \<and> sn_ok_fields x fs)"
| "sn_ok_entries x [] = True"
| "sn_ok_entries x (MapEntryFull k v _ # es) =
     (sn_ok x k \<and> sn_ok x v \<and> sn_ok_entries x es)"
| "sn_ok_bindings x [] = True"
| "sn_ok_bindings x (QuantifierBindingFull n d kk _ # bs) =
     (sn_ok x d \<and> sn_ok_bindings x bs)"

fun bn_ok :: "String.literal \<Rightarrow> expr \<Rightarrow> bool"
and bn_ok_list :: "String.literal \<Rightarrow> expr list \<Rightarrow> bool"
and bn_ok_fields :: "String.literal \<Rightarrow> field_assign list \<Rightarrow> bool"
and bn_ok_entries :: "String.literal \<Rightarrow> map_entry list \<Rightarrow> bool"
and bn_ok_bindings :: "String.literal \<Rightarrow> quantifier_binding list \<Rightarrow> bool"
where
  "bn_ok c (BinaryOpF op l r _) = (bn_ok c l \<and> bn_ok c r)"
| "bn_ok c (UnaryOpF op e _) = bn_ok c e"
| "bn_ok c (FieldAccessF b f _) = bn_ok c b"
| "bn_ok c (EnumAccessF b m _) = bn_ok c b"
| "bn_ok c (IndexF b i _) = (bn_ok c b \<and> bn_ok c i)"
| "bn_ok c (CallF cal args _) = bn_ok_list c args"
| "bn_ok c (PrimeF e _) = bn_ok c e"
| "bn_ok c (PreF e _) = bn_ok c e"
| "bn_ok c (WithF b upds _) = (bn_ok c b \<and> bn_ok_fields c upds)"
| "bn_ok c (IfF cnd t e _) = (bn_ok c cnd \<and> bn_ok c t \<and> bn_ok c e)"
| "bn_ok c (LetF v vl body _) = (v \<noteq> c \<and> bn_ok c vl \<and> bn_ok c body)"
| "bn_ok c (LambdaF p b _) = (p \<noteq> c \<and> bn_ok c b)"
| "bn_ok c (ConstructorF n fs _) = bn_ok_fields c fs"
| "bn_ok c (SetLiteralF xs _) = bn_ok_list c xs"
| "bn_ok c (MapLiteralF es _) = bn_ok_entries c es"
| "bn_ok c (SetComprehensionF v d p _) = (v \<noteq> c \<and> bn_ok c d \<and> bn_ok c p)"
| "bn_ok c (SeqLiteralF xs _) = bn_ok_list c xs"
| "bn_ok c (MatchesF e pat _) = bn_ok c e"
| "bn_ok c (SomeWrapF e _) = bn_ok c e"
| "bn_ok c (TheF v d b _) = (v \<noteq> c \<and> bn_ok c d \<and> bn_ok c b)"
| "bn_ok c (QuantifierF q bs body _) =
     (\<not> string_in_list c (qb_names bs) \<and> bn_ok_bindings c bs \<and> bn_ok c body)"
| "bn_ok c (IntLitF n _) = True"
| "bn_ok c (FloatLitF n _) = True"
| "bn_ok c (StringLitF n _) = True"
| "bn_ok c (BoolLitF v _) = True"
| "bn_ok c (NoneLitF _) = True"
| "bn_ok c (IdentifierF n _) = True"
| "bn_ok_list c [] = True"
| "bn_ok_list c (e # es) = (bn_ok c e \<and> bn_ok_list c es)"
| "bn_ok_fields c [] = True"
| "bn_ok_fields c (FieldAssignFull f v _ # fs) = (bn_ok c v \<and> bn_ok_fields c fs)"
| "bn_ok_entries c [] = True"
| "bn_ok_entries c (MapEntryFull k v _ # es) =
     (bn_ok c k \<and> bn_ok c v \<and> bn_ok_entries c es)"
| "bn_ok_bindings c [] = True"
| "bn_ok_bindings c (QuantifierBindingFull n d kk _ # bs) =
     (bn_ok c d \<and> bn_ok_bindings c bs)"

text \<open>Stripping \<open>pre()\<close> preserves any value the reference evaluator assigns:
  \<open>eval\<close> is already pre-transparent, and each of its syntactic fast paths
  (\<open>dom(a) = dom(b)\<close>, comprehension equality, \<open>the\<close> and quantifier and index
  domains) either sees the same shape after stripping or forced the original
  evaluation to \<open>None\<close> in the first place.\<close>

text \<open>Helper facts about the syntactic fast paths under stripping. The
  reference evaluator gives \<open>dom(\<cdot>)\<close> and standalone comprehensions no value
  of their own (both live only inside the \<open>BEq\<close> fast paths), so any
  expression whose stripped form has one of those shapes evaluated to
  \<open>None\<close> before stripping.\<close>

lemma stripPre_dom_call:
  "dom_arg l = Some a \<Longrightarrow> stripPre l = l"
  by (auto dest!: dom_arg_SomeD)

lemma eval_dom_call_None:
  assumes "lookup_callee fs ps (STR ''dom'') = None"
  shows "eval fs ps fuel s st env (CallF (IdentifierF (STR ''dom'') sp1) [a] sp) = None"
proof (cases fuel)
  case 0 then show ?thesis by simp
next
  case (Suc f)
  then show ?thesis
    using assms
    by (auto simp: is_builtin_pred_def is_builtin_func_def is_builtin_int_func_def
             split: option.splits ir_value.splits)
qed

lemma stripPre_domshape_eval_None:
  assumes "lookup_callee fs ps (STR ''dom'') = None"
  shows "stripPre l = CallF (IdentifierF (STR ''dom'') spd) [aa] sp
           \<Longrightarrow> eval fs ps fuel s st env l = None"
proof (induction l arbitrary: fuel)
  case (CallF c args spc)
  then have ceq: "c = IdentifierF (STR ''dom'') spd"
    and aeq: "stripPre_list args = [aa]" and speq: "spc = sp"
    by simp_all
  obtain a0 where args_eq: "args = [a0]"
    using aeq by (cases args) (auto split: if_splits, cases "tl args", auto)
  show ?case
    using ceq args_eq assms eval_dom_call_None by simp
next
  case (PreF l' spp)
  then show ?case by simp
qed simp_all

lemma stripPre_compshape_eval_None:
  "stripPre r = SetComprehensionF v d p sp
     \<Longrightarrow> eval fs ps fuel s st env r = None"
proof (induction r arbitrary: fuel)
  case (SetComprehensionF w dd pp spc)
  then show ?case by simp
next
  case (PreF r' spp)
  then show ?case by simp
qed simp_all

lemma stripPre_list_length [simp]:
  "length (stripPre_list xs) = length xs"
  by (induction xs) auto

lemma quant_dom_stripPre:
  "quant_dom s st k bs = Some p
     \<Longrightarrow> quant_dom s st k (stripPre_bindings bs) = Some p"
  by (erule quant_dom.elims) (auto split: option.splits)

lemma eval_stripPre:
  "eval fs ps fuel s st env e = Some v
     \<Longrightarrow> eval fs ps fuel s st env (stripPre e) = Some v"
  "eval_list fs ps fuel s st env es = Some vs
     \<Longrightarrow> eval_list fs ps fuel s st env (stripPre_list es) = Some vs"
  "eval_entries fs ps fuel s st env ents = Some kvs
     \<Longrightarrow> eval_entries fs ps fuel s st env (stripPre_entries ents) = Some kvs"
  "eval_fields fs ps fuel s st env fas = Some fvs
     \<Longrightarrow> eval_fields fs ps fuel s st env (stripPre_fields fas) = Some fvs"
  "eval_the fs ps fuel s st env var dmv body = Some ths
     \<Longrightarrow> eval_the fs ps fuel s st env var dmv (stripPre body) = Some ths"
  "eval_forall fs ps fuel s st env var dmv body = Some fav
     \<Longrightarrow> eval_forall fs ps fuel s st env var dmv (stripPre body) = Some fav"
proof (induction fs ps fuel s st env e and fs ps fuel s st env es
         and fs ps fuel s st env ents and fs ps fuel s st env fas
         and fs ps fuel s st env var dmv body and fs ps fuel s st env var dmv body
         arbitrary: v and vs and kvs and fvs and ths and fav
         rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (6 fs ps fuel s st env bop l r sp)
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    have de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some peq by simp
    obtain rx ry where args: "dom_arg l = Some rx" "dom_arg r = Some ry"
      using dom_eq_domains_SomeD[OF de] by blast
    have sl: "stripPre l = l" and sr: "stripPre r = r"
      using args by (auto intro: stripPre_dom_call)
    have de': "dom_eq_domains fs ps st bop (stripPre l) (stripPre r) = Some (dx, dy)"
      using de sl sr by simp
    have "eval fs ps fuel s st env (stripPre (BinaryOpF bop l r sp))
            = eval fs ps fuel s st env (BinaryOpF bop (stripPre l) (stripPre r) sp)"
      by simp
    also have "\<dots> = Some (VBool (set dx = set dy))"
      by (rule eval_dom_eq[OF de'])
    also have "\<dots> = Some v"
      using "6.prems" by (simp only: eval_dom_eq[OF de])
    finally show ?thesis .
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
      case (Some t)
      obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
      have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
      obtain csp cdsp where req: "r = SetComprehensionF var (IdentifierF dnm cdsp) pred csp"
        and bopEq: "bop = BEq"
        using bc by (auto elim!: beq_comp.elims split: expr.splits)
      have rstr: "stripPre r = SetComprehensionF var (IdentifierF dnm cdsp) (stripPre pred) csp"
        using req by simp
      have bc': "beq_comp bop (stripPre r) = Some (var, dnm, stripPre pred)"
        using rstr bopEq by simp
      have deN': "dom_eq_domains fs ps st bop (stripPre l) (stripPre r) = None"
        using rstr by (auto simp: dom_eq_domains_def split: option.splits)
      have enr: "schema_lookup_enum s dnm = None"
        using "6.prems" deN bc by (auto split: option.splits if_splits)
      have enrP: "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
      obtain dvs where srd: "state_relation_domain st dnm = Some dvs"
        using "6.prems" deN bc enr by (auto split: option.splits if_splits)
      obtain ms where etm: "eval_the fs ps fuel s st env var dvs pred = Some ms"
        using "6.prems" deN bc enr srd by (auto split: option.splits if_splits)
      obtain xs where elv: "eval fs ps fuel s st env l = Some (VSet xs)"
        and allc: "list_all (\<lambda>x. contains_value dvs x) xs"
        and vEq: "v = VBool (set xs = set ms)"
        using "6.prems" deN bc enr srd etm
        by (auto split: option.splits ir_value.splits if_splits)
      have ethe': "eval_the fs ps fuel s st env var dvs (stripPre pred) = Some ms"
        using "6.IH"(3)[OF deN bc refl refl enrP srd] etm by simp
      have el': "eval fs ps fuel s st env (stripPre l) = Some (VSet xs)"
        using "6.IH"(4)[OF deN bc refl refl enrP srd etm] elv by simp
      show ?thesis
        using deN' bc' enr srd ethe' el' allc vEq by simp
    next
      case None
      note bcN = this
      obtain lv rv where elv: "eval fs ps fuel s st env l = Some lv"
        and erv: "eval fs ps fuel s st env r = Some rv"
        and vbin: "eval_bin bop (Some lv) (Some rv) = Some v"
        using "6.prems" deN bcN
        by (auto split: option.splits dest: eval_bin_someD) (metis eval_bin_someD)
      have il: "eval fs ps fuel s st env (stripPre l) = Some lv"
        using "6.IH"(1)[OF deN bcN] elv by simp
      have ir: "eval fs ps fuel s st env (stripPre r) = Some rv"
        using "6.IH"(2)[OF deN bcN] erv by simp
      have deN': "dom_eq_domains fs ps st bop (stripPre l) (stripPre r) = None"
      proof (cases "dom_eq_domains fs ps st bop (stripPre l) (stripPre r)")
        case (Some q)
        obtain dx dy where qeq: "q = (dx, dy)" by (cases q)
        obtain rx where dsl: "dom_arg (stripPre l) = Some rx"
          and lk: "lookup_callee fs ps (STR ''dom'') = None"
          using dom_eq_domains_SomeD Some qeq by blast
        obtain spd spa spc where lshape:
          "stripPre l = CallF (IdentifierF (STR ''dom'') spd) [IdentifierF rx spa] spc"
          using dom_arg_SomeD[OF dsl] by blast
        have "eval fs ps fuel s st env l = None"
          using stripPre_domshape_eval_None[OF lk lshape] .
        with elv show ?thesis by simp
      qed simp
      have bcN': "beq_comp bop (stripPre r) = None"
      proof (cases "beq_comp bop (stripPre r)")
        case (Some t')
        obtain var' dnm' pred' where t'eq: "t' = (var', dnm', pred')" by (cases t')
        obtain csp' cdsp' where rshape:
          "stripPre r = SetComprehensionF var' (IdentifierF dnm' cdsp') pred' csp'"
          using Some t'eq by (auto elim!: beq_comp.elims split: expr.splits)
        have "eval fs ps fuel s st env r = None"
          using stripPre_compshape_eval_None[OF rshape] .
        with erv show ?thesis by simp
      qed simp
      show ?thesis
        using deN' bcN' il ir vbin "6.prems" deN bcN by simp
    qed
  qed
next
  case (7 fs ps fuel s st env uop e0 sp)
  obtain a where ea: "eval fs ps fuel s st env e0 = Some a"
    using "7.prems" eval_un_someD by fastforce
  show ?case using "7.IH"[OF ea] ea "7.prems" by simp
next
  case (8 fs ps fuel s st env c a b sp)
  obtain cv where ec: "eval fs ps fuel s st env c = Some cv"
    using "8.prems" by (auto split: option.splits)
  obtain cb where cvb: "cv = VBool cb"
    using "8.prems" ec by (cases cv) (auto split: option.splits)
  show ?case
  proof (cases cb)
    case True
    have cT: "eval fs ps fuel s st env c = Some (VBool True)" using ec cvb True by simp
    have ea: "eval fs ps fuel s st env a = Some v" using "8.prems" cT by simp
    show ?thesis using "8.IH"(1)[OF cT] "8.IH"(2)[OF cT refl refl] ea by simp
  next
    case False
    have cF: "eval fs ps fuel s st env c = Some (VBool False)" using ec cvb False by simp
    have eb: "eval fs ps fuel s st env b = Some v" using "8.prems" cF by simp
    show ?thesis using "8.IH"(1)[OF cF] "8.IH"(3)[OF cF refl refl] eb by simp
  qed
next
  case (9 fs ps fuel s st env x vl body sp)
  obtain va where ev: "eval fs ps fuel s st env vl = Some va"
    using "9.prems" by (auto split: option.splits)
  have eb: "eval fs ps fuel s st ((x, va) # env) body = Some v"
    using "9.prems" ev by simp
  show ?case using "9.IH"(1)[OF ev] "9.IH"(2)[OF ev eb] ev by simp
next
  case (15 fs ps fuel s st env callee cargs sp)
  show ?case
  proof (cases fuel)
    case 0 then show ?thesis using "15.prems" by simp
  next
    case (Suc f)
    obtain nm nsp where cal: "callee = IdentifierF nm nsp"
      using "15.prems" Suc by (cases callee) (auto split: option.splits)
    show ?thesis
    proof (cases "lookup_callee fs ps nm")
      case (Some pb)
      obtain params fbody where pbeq: "pb = (params, fbody)" by (cases pb)
      have lk: "lookup_callee fs ps nm = Some (params, fbody)" using Some pbeq by simp
      have guard: "length params = length cargs \<and> distinct params"
        using "15.prems" Suc cal lk by (auto split: if_splits)
      obtain vals where evs: "eval_list fs ps fuel s st env cargs = Some vals"
        using "15.prems" Suc cal lk guard by (auto split: option.splits)
      have body: "eval fs ps f s st (zip params vals) fbody = Some v"
        using "15.prems" Suc cal lk guard evs by simp
      show ?thesis
        using Suc cal lk guard evs body "15.IH"
        by (auto split: option.splits)
    next
      case None
      note lkN = this
      show ?thesis
      proof (cases cargs)
        case Nil
        then show ?thesis using "15.prems" Suc cal lkN by simp
      next
        case (Cons a0 rest)
        show ?thesis
        proof (cases rest)
          case Nil
          have one: "cargs = [a0]" using Cons Nil by simp
          show ?thesis
            using Suc cal lkN one "15.prems" "15.IH"
            by (auto split: option.splits ir_value.splits if_splits)
        next
          case (Cons a1 rest2)
          then show ?thesis using "15.prems" Suc cal lkN \<open>cargs = a0 # rest\<close> by simp
        qed
      qed
    qed
  qed
next
  case (22 fs ps fuel s st env base mem sp)
  obtain en esp where be: "base = IdentifierF en esp"
    using "22.prems" by (cases base) (auto split: option.splits)
  show ?case using "22.prems" be by simp
next
  case (23 fs ps fuel s st env base key sp)
  obtain rel where peel: "peelRelationRef base = Some rel"
    using "23.prems" by (auto split: option.splits)
  obtain kv where ek: "eval fs ps fuel s st env key = Some kv"
    using "23.prems" peel by (auto split: option.splits)
  have peel': "peelRelationRef (stripPre base) = Some rel"
    using peel
    by (cases base) (auto split: expr.splits option.splits elim!: identName.elims)
  show ?case using peel peel' ek "23.IH"[OF _] "23.prems"
    by (auto split: option.splits)
next
  case (24 fs ps fuel s st env var dm body sp)
  obtain rel dsp where dme: "dm = IdentifierF rel dsp"
    using "24.prems" by (cases dm) (auto split: option.splits)
  obtain dmv0 where srd: "state_relation_domain st rel = Some dmv0"
    using "24.prems" dme by (auto split: option.splits)
  obtain x0 rest0 where eth: "eval_the fs ps fuel s st env var dmv0 body = Some (x0 # rest0)"
    and alleq: "list_all (\<lambda>y. y = x0) rest0" and vEq: "v = x0"
    using "24.prems" dme srd
    by (auto split: option.splits list.splits if_splits)
  have eth': "eval_the fs ps fuel s st env var dmv0 (stripPre body) = Some (x0 # rest0)"
    using "24.IH"[OF dme srd eth] by simp
  show ?case using dme srd eth' alleq vEq by simp
next
  case (25 fs ps fuel s st env k bs body sp)
  obtain var0 dmv0 where qd: "quant_dom s st k bs = Some (var0, dmv0)"
    using "25.prems" by (auto split: option.splits)
  have ef: "eval_forall fs ps fuel s st env var0 dmv0 body = Some v"
    using "25.prems" qd by simp
  have qd': "quant_dom s st k (stripPre_bindings bs) = Some (var0, dmv0)"
    using quant_dom_stripPre[OF qd] .
  show ?case using qd qd' "25.IH"[OF qd] ef by simp
qed (auto split: option.splits ir_value.splits list.splits if_splits)

text \<open>Shape preservation of the syntactic fast paths under candidate
  substitution, given \<open>sn_ok\<close>: the walkers map every constructor to itself
  (an identifier only ever becomes another identifier), and the guarded
  positions keep their names.\<close>

lemma subst_ident_ctor_shape:
  "dom_arg (subst x (IdentifierF cn None) l) = dom_arg l"
  if "sn_ok x l"
proof (cases l)
  case (CallF c args sp)
  show ?thesis
  proof (cases "identName c")
    case None
    then have "dom_arg l = None" using CallF by (cases c) auto
    moreover have "dom_arg (subst x (IdentifierF cn None) l) = None"
      using None CallF by (cases c) (auto split: option.splits)
    ultimately show ?thesis by simp
  next
    case (Some d)
    obtain csp where ceq: "c = IdentifierF d csp"
      using Some by (cases c) auto
    show ?thesis
    proof (cases args)
      case Nil then show ?thesis using CallF ceq by simp
    next
      case (Cons a0 rest)
      show ?thesis
      proof (cases rest)
        case (Cons a1 rest2)
        then show ?thesis using CallF ceq \<open>args = a0 # rest\<close>
          by (cases a0) (auto split: expr.splits list.splits)
      next
        case Nil
        note one = this
        show ?thesis
        proof (cases a0)
          case (IdentifierF a asp)
          have guard: "d \<noteq> STR ''dom'' \<or> a \<noteq> x"
            using that CallF ceq \<open>args = a0 # rest\<close> one IdentifierF by simp
          show ?thesis
          proof (cases "d = STR ''dom''")
            case True
            then have "a \<noteq> x" using guard by simp
            then show ?thesis
              using CallF ceq \<open>args = a0 # rest\<close> one IdentifierF by simp
          next
            case False
            then show ?thesis
              using CallF ceq \<open>args = a0 # rest\<close> one IdentifierF
              by (auto split: list.splits expr.splits)
          qed
        qed (use CallF ceq \<open>args = a0 # rest\<close> one in
               \<open>auto split: list.splits expr.splits\<close>)
      qed
    qed
  qed
qed auto

lemma beq_comp_subst:
  assumes "sn_ok x r"
  shows "beq_comp bop (subst x (IdentifierF cn None) r) =
           (case beq_comp bop r of
              Some (var, dnm, pred) \<Rightarrow>
                Some (var, dnm, if var = x then pred else subst x (IdentifierF cn None) pred)
            | None \<Rightarrow> None)"
proof (cases "beq_comp bop r")
  case None
  then show ?thesis
    by (cases bop; cases r) (auto split: expr.splits)
next
  case (Some t)
  obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
  obtain csp cdsp where req: "r = SetComprehensionF var (IdentifierF dnm cdsp) pred csp"
    and bopEq: "bop = BEq"
    using Some teq by (auto elim!: beq_comp.elims split: expr.splits)
  have dk: "dnm \<noteq> x" using assms req by simp
  show ?thesis using Some teq req bopEq dk by simp
qed

lemma dom_eq_domains_subst:
  assumes "sn_ok x l" and "sn_ok x r"
  shows "dom_eq_domains fs ps st bop (subst x (IdentifierF cn None) l)
           (subst x (IdentifierF cn None) r) = dom_eq_domains fs ps st bop l r"
  unfolding dom_eq_domains_def
  by (simp add: subst_ident_ctor_shape[OF assms(1)] subst_ident_ctor_shape[OF assms(2)])

lemma quant_dom_multi:
  "quant_dom s st k (a # b # cs) = None"
  by (cases "quant_dom s st k (a # b # cs)") (auto elim!: quant_dom.elims)

lemma quant_dom_subst:
  assumes "list_all (\<lambda>b. case b of QuantifierBindingFull _ (IdentifierF n _) _ _ \<Rightarrow> n \<noteq> x
                         | _ \<Rightarrow> True) bs"
  shows "quant_dom s st k (subst_bindings x (IdentifierF cn None) bs) = quant_dom s st k bs"
proof (cases "quant_dom s st k bs")
  case (Some p)
  then show ?thesis using assms
    by (auto elim!: quant_dom.elims split: option.splits)
next
  case None
  then show ?thesis using assms
  proof (cases bs)
    case Nil then show ?thesis using None by simp
  next
    case (Cons b0 rest)
    show ?thesis
    proof (cases rest)
      case (Cons b1 rest2)
      then show ?thesis using \<open>bs = b0 # rest\<close> None
        by (cases b0; cases b1) (simp add: quant_dom_multi)
    next
      case Nil
      obtain n0 d0 kk0 sp0 where b0eq: "b0 = QuantifierBindingFull n0 d0 kk0 sp0"
        by (cases b0) auto
      show ?thesis
      proof (cases d0)
        case (IdentifierF dn dsp)
        then have "dn \<noteq> x" using assms \<open>bs = b0 # rest\<close> Nil b0eq by simp
        then show ?thesis using \<open>bs = b0 # rest\<close> Nil b0eq IdentifierF None
          by (cases k) auto
      qed (use \<open>bs = b0 # rest\<close> Nil b0eq None in \<open>cases k; auto\<close>)+
    qed
  qed
qed

lemma identName_subst:
  "identName b \<noteq> Some x
     \<Longrightarrow> identName (subst x (IdentifierF cn None) b) = identName b"
  by (cases b) auto

lemma peel_subst:
  assumes "peelRelationRef b \<noteq> Some x"
  shows "peelRelationRef (subst x (IdentifierF cn None) b) = peelRelationRef b"
  using assms
  by (cases b) (auto simp: identName_subst)

text \<open>Substituting the candidate identifier for the position is invisible to
  the reference evaluator across a pre/post environment pair that agrees
  everywhere except the position itself, provided the position is bound in
  the rich environment to exactly the candidate's value in the poor one.\<close>

lemma eval_subst_cand:
  "eval fs ps fuel s st env2 e = Some v
     \<Longrightarrow> sn_ok x e \<Longrightarrow> bn_ok cn e
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval fs ps fuel s st env1 (subst x (IdentifierF cn None) e) = Some v"
  "eval_list fs ps fuel s st env2 es = Some vs
     \<Longrightarrow> sn_ok_list x es \<Longrightarrow> bn_ok_list cn es
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_list fs ps fuel s st env1 (subst_list x (IdentifierF cn None) es) = Some vs"
  "eval_entries fs ps fuel s st env2 ents = Some kvs
     \<Longrightarrow> sn_ok_entries x ents \<Longrightarrow> bn_ok_entries cn ents
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_entries fs ps fuel s st env1 (subst_entries x (IdentifierF cn None) ents) = Some kvs"
  "eval_fields fs ps fuel s st env2 fas = Some fvs
     \<Longrightarrow> sn_ok_fields x fas \<Longrightarrow> bn_ok_fields cn fas
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_fields fs ps fuel s st env1 (subst_fields x (IdentifierF cn None) fas) = Some fvs"
  "eval_the fs ps fuel s st env2 var dmv body = Some ths
     \<Longrightarrow> sn_ok x body \<Longrightarrow> bn_ok cn body \<Longrightarrow> var \<noteq> x \<Longrightarrow> var \<noteq> cn
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_the fs ps fuel s st env1 var dmv (subst x (IdentifierF cn None) body) = Some ths"
  "eval_forall fs ps fuel s st env2 var dmv body = Some fav
     \<Longrightarrow> sn_ok x body \<Longrightarrow> bn_ok cn body \<Longrightarrow> var \<noteq> x \<Longrightarrow> var \<noteq> cn
     \<Longrightarrow> env_lookup env2 x = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> x \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_forall fs ps fuel s st env1 var dmv (subst x (IdentifierF cn None) body) = Some fav"
proof (induction fs ps fuel s st env2 e and fs ps fuel s st env2 es
         and fs ps fuel s st env2 ents and fs ps fuel s st env2 fas
         and fs ps fuel s st env2 var dmv body and fs ps fuel s st env2 var dmv body
         arbitrary: v env1 and vs env1 and kvs env1 and fvs env1 and ths env1 and fav env1
         rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (5 fs ps fuel s st env2 n sp)
  show ?case
  proof (cases "n = x")
    case True
    have "v = vc" using "5.prems"(1,4) True by (auto split: option.splits)
    then show ?thesis using True "5.prems"(5) by (simp add: )
  next
    case False
    have same: "env_lookup env2 n = env_lookup env1 n"
      using "5.prems"(6) False by simp
    show ?thesis using "5.prems"(1) same False by (simp split: option.splits)
  qed
next
  case (6 fs ps fuel s st env2 bop l r sp)
  have snl: "sn_ok x l" and snr: "sn_ok x r"
    using "6.prems"(2) by simp_all
  have bnl: "bn_ok cn l" and bnr: "bn_ok cn r"
    using "6.prems"(3) by simp_all
  have deq: "dom_eq_domains fs ps st bop (subst x (IdentifierF cn None) l)
               (subst x (IdentifierF cn None) r) = dom_eq_domains fs ps st bop l r"
    by (rule dom_eq_domains_subst[OF snl snr])
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    have de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some peq by simp
    have de': "dom_eq_domains fs ps st bop (subst x (IdentifierF cn None) l)
                 (subst x (IdentifierF cn None) r) = Some (dx, dy)"
      using deq de by simp
    have "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) (BinaryOpF bop l r sp))
            = Some (VBool (set dx = set dy))"
      by (simp only: subst.simps eval_dom_eq[OF de'])
    also have "\<dots> = Some v"
      using "6.prems"(1) by (simp only: eval_dom_eq[OF de])
    finally show ?thesis .
  next
    case None
    note deN = this
    have deN': "dom_eq_domains fs ps st bop (subst x (IdentifierF cn None) l)
                  (subst x (IdentifierF cn None) r) = None"
      using deq deN by simp
    show ?thesis
    proof (cases "beq_comp bop r")
      case (Some t)
      obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
      have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
      obtain csp cdsp where req: "r = SetComprehensionF var (IdentifierF dnm cdsp) pred csp"
        and bopEq: "bop = BEq"
        using bc by (auto elim!: beq_comp.elims split: expr.splits)
      have bc': "beq_comp bop (subst x (IdentifierF cn None) r)
                   = Some (var, dnm, if var = x then pred else subst x (IdentifierF cn None) pred)"
        using beq_comp_subst[OF snr, of bop cn] bc by simp
      have enr: "schema_lookup_enum s dnm = None"
        using "6.prems"(1) deN bc by (auto split: option.splits if_splits)
      have enrP: "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
      obtain dvs where srd: "state_relation_domain st dnm = Some dvs"
        using "6.prems"(1) deN bc enr by (auto split: option.splits if_splits)
      obtain ms where etm: "eval_the fs ps fuel s st env2 var dvs pred = Some ms"
        using "6.prems"(1) deN bc enr srd by (auto split: option.splits if_splits)
      obtain xs where elv: "eval fs ps fuel s st env2 l = Some (VSet xs)"
        and allc: "list_all (\<lambda>xx. contains_value dvs xx) xs"
        and vEq: "v = VBool (set xs = set ms)"
        using "6.prems"(1) deN bc enr srd etm
        by (auto split: option.splits ir_value.splits if_splits)
      have snp: "sn_ok x pred" using snr req by simp
      have bnp: "bn_ok cn pred" and vcn: "var \<noteq> cn" using bnr req by simp_all
      have ethe': "eval_the fs ps fuel s st env1 var dvs
                     (if var = x then pred else subst x (IdentifierF cn None) pred) = Some ms"
      proof (cases "var = x")
        case True
        have agr: "\<forall>y. string_in_list y (remove_name var (free_vars pred))
                     \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
          using "6.prems"(6) True by auto
        show ?thesis
          using eval_coincidence(5)[OF agr] etm True by simp
      next
        case False
        show ?thesis
          using "6.IH"(3)[OF deN bc refl refl enrP srd etm snp bnp False vcn
                             "6.prems"(4) "6.prems"(5) "6.prems"(6)] False by simp
      qed
      have el': "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) l) = Some (VSet xs)"
        using "6.IH"(4)[OF deN bc refl refl enrP srd etm elv snl bnl
                           "6.prems"(4) "6.prems"(5) "6.prems"(6)] by simp
      show ?thesis
        using deN' bc' enr srd ethe' el' allc vEq by simp
    next
      case None
      note bcN = this
      have bcN': "beq_comp bop (subst x (IdentifierF cn None) r) = None"
        using beq_comp_subst[OF snr, of bop cn] bcN by simp
      obtain lv rv where elv: "eval fs ps fuel s st env2 l = Some lv"
        and erv: "eval fs ps fuel s st env2 r = Some rv"
        and vbin: "eval_bin bop (Some lv) (Some rv) = Some v"
        using "6.prems"(1) deN bcN
        by (auto split: option.splits dest: eval_bin_someD) (metis eval_bin_someD)
      have il: "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) l) = Some lv"
        using "6.IH"(1)[OF deN bcN elv snl bnl "6.prems"(4) "6.prems"(5) "6.prems"(6)] by simp
      have ir: "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) r) = Some rv"
        using "6.IH"(2)[OF deN bcN erv snr bnr "6.prems"(4) "6.prems"(5) "6.prems"(6)] by simp
      show ?thesis
        using deN' bcN' il ir vbin "6.prems"(1) deN bcN by simp
    qed
  qed
next
  case (7 fs ps fuel s st env2 uop e0 sp)
  obtain a where ea: "eval fs ps fuel s st env2 e0 = Some a"
    using "7.prems"(1) eval_un_someD by fastforce
  show ?case
    using "7.IH"[OF ea] ea "7.prems" by simp
next
  case (8 fs ps fuel s st env2 c a b sp)
  obtain cv where ec: "eval fs ps fuel s st env2 c = Some cv"
    using "8.prems"(1) by (auto split: option.splits)
  obtain cb where cvb: "cv = VBool cb"
    using "8.prems"(1) ec by (cases cv) (auto split: option.splits)
  show ?case
  proof (cases cb)
    case True
    have cT: "eval fs ps fuel s st env2 c = Some (VBool True)" using ec cvb True by simp
    have ea: "eval fs ps fuel s st env2 a = Some v" using "8.prems"(1) cT by simp
    show ?thesis
      using "8.IH"(1)[OF cT] "8.IH"(2)[OF cT refl refl] ea "8.prems" by simp
  next
    case False
    have cF: "eval fs ps fuel s st env2 c = Some (VBool False)" using ec cvb False by simp
    have eb: "eval fs ps fuel s st env2 b = Some v" using "8.prems"(1) cF by simp
    show ?thesis
      using "8.IH"(1)[OF cF] "8.IH"(3)[OF cF refl refl] eb "8.prems" by simp
  qed
next
  case (9 fs ps fuel s st env2 w vl body sp)
  obtain va where ev: "eval fs ps fuel s st env2 vl = Some va"
    using "9.prems"(1) by (auto split: option.splits)
  have eb: "eval fs ps fuel s st ((w, va) # env2) body = Some v"
    using "9.prems"(1) ev by simp
  have snv: "sn_ok x vl" and snb: "sn_ok x body" using "9.prems"(2) by simp_all
  have bnv: "bn_ok cn vl" and bnb: "bn_ok cn body" and wcn: "w \<noteq> cn"
    using "9.prems"(3) by simp_all
  have ev': "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) vl) = Some va"
    using "9.IH"(1)[OF ev snv bnv "9.prems"(4) "9.prems"(5) "9.prems"(6)] by simp
  show ?case
  proof (cases "w = x")
    case True
    have agr: "\<forall>y. string_in_list y (free_vars body)
                 \<longrightarrow> env_lookup ((w, va) # env2) y = env_lookup ((w, va) # env1) y"
      using "9.prems"(6) True by (auto simp: env_lookup_def)
    have "eval fs ps fuel s st ((w, va) # env1) body = Some v"
      using eval_coincidence(1)[OF agr] eb by simp
    then show ?thesis using ev' True by simp
  next
    case False
    have lx: "env_lookup ((w, va) # env2) x = Some vc"
      using "9.prems"(4) False by (simp add: env_lookup_def)
    have lc: "env_lookup ((w, va) # env1) cn = Some vc"
      using "9.prems"(5) wcn by (simp add: env_lookup_def)
    have fr: "\<forall>y. y \<noteq> x \<longrightarrow> env_lookup ((w, va) # env2) y = env_lookup ((w, va) # env1) y"
      using "9.prems"(6) by (auto simp: env_lookup_def)
    have "eval fs ps fuel s st ((w, va) # env1) (subst x (IdentifierF cn None) body) = Some v"
      using "9.IH"(2)[OF ev eb snb bnb lx lc fr] by simp
    then show ?thesis using ev' False by simp
  qed
next
  case (15 fs ps fuel s st env2 callee cargs sp)
  show ?case
  proof (cases fuel)
    case 0 then show ?thesis using "15.prems"(1) by simp
  next
    case (Suc f)
    obtain nm nsp where cal: "callee = IdentifierF nm nsp"
      using "15.prems"(1) Suc by (cases callee) (auto split: option.splits)
    have calkeep:
      "subst x (IdentifierF cn None) (CallF callee cargs sp)
         = CallF callee (subst_list x (IdentifierF cn None) cargs) sp"
      using cal by simp
    show ?thesis
    proof (cases "lookup_callee fs ps nm")
      case (Some pb)
      obtain params fbody where pbeq: "pb = (params, fbody)" by (cases pb)
      have lk: "lookup_callee fs ps nm = Some (params, fbody)" using Some pbeq by simp
      have guard: "length params = length cargs \<and> distinct params"
        using "15.prems"(1) Suc cal lk by (auto split: if_splits)
      obtain vals where evs: "eval_list fs ps fuel s st env2 cargs = Some vals"
        using "15.prems"(1) Suc cal lk guard by (auto split: option.splits)
      have body: "eval fs ps f s st (zip params vals) fbody = Some v"
        using "15.prems"(1) Suc cal lk guard evs by simp
      have sna: "sn_ok_list x cargs" using "15.prems"(2) by simp
      have bna: "bn_ok_list cn cargs" using "15.prems"(3) by simp
      have evs': "eval_list fs ps fuel s st env1
                    (subst_list x (IdentifierF cn None) cargs) = Some vals"
        using "15.IH"(4)[OF Suc cal lk refl guard evs sna bna
                            "15.prems"(4) "15.prems"(5) "15.prems"(6)] by simp
      have len': "length (subst_list x (IdentifierF cn None) cargs) = length cargs"
        by (induction cargs) auto
      show ?thesis
        using Suc cal lk guard evs' body calkeep len' by simp
    next
      case None
      note lkN = this
      show ?thesis
      proof (cases cargs)
        case Nil
        then show ?thesis using "15.prems"(1) Suc cal lkN by simp
      next
        case (Cons a0 rest)
        note consR = this
        show ?thesis
        proof (cases rest)
          case Nil
          note restN = this
          have sna: "sn_ok x a0" using "15.prems"(2) consR restN by simp
          have bna: "bn_ok cn a0" using "15.prems"(3) consR restN by simp
          show ?thesis
          proof (cases "is_builtin_pred nm")
            case True
            obtain str where ea: "eval fs ps fuel s st env2 a0 = Some (VStr str)"
              and vv: "v = VBool (str_predicate nm str)"
              using "15.prems"(1) Suc cal lkN consR restN True
              by (auto split: option.splits ir_value.splits)
            have ea': "eval fs ps fuel s st env1
                         (subst x (IdentifierF cn None) a0) = Some (VStr str)"
              using "15.IH"(1)[OF Suc cal lkN consR restN True ea sna bna
                                  "15.prems"(4) "15.prems"(5) "15.prems"(6)] .
            show ?thesis using Suc cal lkN consR restN True ea' vv calkeep by simp
          next
            case False
            note npred = this
            show ?thesis
            proof (cases "is_builtin_func nm")
              case True
              obtain str where ea: "eval fs ps fuel s st env2 a0 = Some (VStr str)"
                and vv: "v = VStr (builtin_str_func nm str)"
                using "15.prems"(1) Suc cal lkN consR restN npred True
                by (auto split: option.splits ir_value.splits)
              have ea': "eval fs ps fuel s st env1
                           (subst x (IdentifierF cn None) a0) = Some (VStr str)"
                using "15.IH"(2)[OF Suc cal lkN consR restN npred True ea sna bna
                                    "15.prems"(4) "15.prems"(5) "15.prems"(6)] .
              show ?thesis
                using Suc cal lkN consR restN npred True ea' vv calkeep by simp
            next
              case False
              note nfunc = this
              have nint: "is_builtin_int_func nm"
                using "15.prems"(1) Suc cal lkN consR restN npred nfunc
                by (auto split: option.splits ir_value.splits if_splits)
              obtain n0 where ea: "eval fs ps fuel s st env2 a0 = Some (VInt n0)"
                and vv: "v = VInt (builtin_int_func nm n0)"
                using "15.prems"(1) Suc cal lkN consR restN npred nfunc nint
                by (auto split: option.splits ir_value.splits)
              have ea': "eval fs ps fuel s st env1
                           (subst x (IdentifierF cn None) a0) = Some (VInt n0)"
                using "15.IH"(3)[OF Suc cal lkN consR restN npred nfunc nint ea sna bna
                                    "15.prems"(4) "15.prems"(5) "15.prems"(6)] .
              show ?thesis
                using Suc cal lkN consR restN npred nfunc nint ea' vv calkeep by simp
            qed
          qed
        next
          case (Cons a1 rest2)
          then show ?thesis using "15.prems"(1) Suc cal lkN consR by simp
        qed
      qed
    qed
  qed
next
  case (22 fs ps fuel s st env2 base mem sp)
  obtain en esp where be: "base = IdentifierF en esp"
    using "22.prems"(1) by (cases base) (auto split: option.splits)
  have nx: "en \<noteq> x" using "22.prems"(2) be by simp
  show ?case using "22.prems"(1) be nx by simp
next
  case (23 fs ps fuel s st env2 base key sp)
  obtain rel where peel: "peelRelationRef base = Some rel"
    using "23.prems"(1) by (auto split: option.splits)
  obtain kv where ek: "eval fs ps fuel s st env2 key = Some kv"
    using "23.prems"(1) peel by (auto split: option.splits)
  have nx: "peelRelationRef base \<noteq> Some x" using "23.prems"(2) by simp
  have peel': "peelRelationRef (subst x (IdentifierF cn None) base) = Some rel"
    using peel_subst[OF nx] peel by simp
  have snk: "sn_ok x key" using "23.prems"(2) by simp
  have bnk: "bn_ok cn key" using "23.prems"(3) by simp
  have ek': "eval fs ps fuel s st env1 (subst x (IdentifierF cn None) key) = Some kv"
    using "23.IH"[OF _ snk bnk "23.prems"(4) "23.prems"(5) "23.prems"(6)] ek peel
    by (auto split: option.splits)
  show ?case using peel peel' ek ek' "23.prems"(1) by (auto split: option.splits)
next
  case (24 fs ps fuel s st env2 var dm body sp)
  obtain rel dsp where dme: "dm = IdentifierF rel dsp"
    using "24.prems"(1) by (cases dm) (auto split: option.splits)
  have relx: "rel \<noteq> x" using "24.prems"(2) dme by simp
  obtain dmv0 where srd: "state_relation_domain st rel = Some dmv0"
    using "24.prems"(1) dme by (auto split: option.splits)
  obtain x0 rest0 where eth: "eval_the fs ps fuel s st env2 var dmv0 body = Some (x0 # rest0)"
    and alleq: "list_all (\<lambda>y. y = x0) rest0" and vEq: "v = x0"
    using "24.prems"(1) dme srd
    by (auto split: option.splits list.splits if_splits)
  have snb: "sn_ok x body" using "24.prems"(2) dme by simp
  have bnb: "bn_ok cn body" and vcn: "var \<noteq> cn" using "24.prems"(3) dme by simp_all
  have eth': "eval_the fs ps fuel s st env1 var dmv0
                (if var = x then body else subst x (IdentifierF cn None) body)
                = Some (x0 # rest0)"
  proof (cases "var = x")
    case True
    have agr: "\<forall>y. string_in_list y (remove_name var (free_vars body))
                 \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
      using "24.prems"(6) True by auto
    show ?thesis using eval_coincidence(5)[OF agr] eth True by simp
  next
    case False
    show ?thesis
      using "24.IH"[OF dme srd eth snb bnb False vcn
                       "24.prems"(4) "24.prems"(5) "24.prems"(6)] False by simp
  qed
  show ?case using dme srd eth' alleq vEq relx by simp
next
  case (25 fs ps fuel s st env2 k bs body sp)
  obtain var0 dmv0 where qd: "quant_dom s st k bs = Some (var0, dmv0)"
    using "25.prems"(1) by (auto split: option.splits)
  have ef: "eval_forall fs ps fuel s st env2 var0 dmv0 body = Some v"
    using "25.prems"(1) qd by simp
  have doms: "list_all (\<lambda>b. case b of QuantifierBindingFull _ (IdentifierF n _) _ _ \<Rightarrow> n \<noteq> x
                            | _ \<Rightarrow> True) bs"
    using "25.prems"(2) by simp
  have qd': "quant_dom s st k (subst_bindings x (IdentifierF cn None) bs)
               = Some (var0, dmv0)"
    using quant_dom_subst[OF doms] qd by simp
  obtain dnm0 dsp0 kk0 sp0 where bs_eq:
    "bs = [QuantifierBindingFull var0 (IdentifierF dnm0 dsp0) kk0 sp0]"
    using qd by (auto elim!: quant_dom.elims split: option.splits)
  have snb: "sn_ok x body" using "25.prems"(2) by simp
  have bnb: "bn_ok cn body" using "25.prems"(3) by simp
  have vcn: "var0 \<noteq> cn"
    using "25.prems"(3) bs_eq by auto
  show ?case
  proof (cases "string_in_list x (qb_names bs)")
    case True
    have varx: "var0 = x" using True bs_eq by (simp add: string_in_list_iff)
    have agr: "\<forall>y. string_in_list y (remove_name var0 (free_vars body))
                 \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
      using "25.prems"(6) varx by auto
    have ef': "eval_forall fs ps fuel s st env1 var0 dmv0 body = Some v"
      using eval_coincidence(6)[OF agr] ef by simp
    then show ?thesis using qd qd' True by simp
  next
    case False
    have varx: "var0 \<noteq> x" using False bs_eq by (simp add: string_in_list_iff)
    have ef': "eval_forall fs ps fuel s st env1 var0 dmv0
                 (subst x (IdentifierF cn None) body) = Some v"
      using "25.IH"[OF qd refl ef snb bnb varx vcn
                       "25.prems"(4) "25.prems"(5) "25.prems"(6)] by simp
    then show ?thesis using qd qd' False by simp
  qed
next
  case (34 fs ps fuel s st env2 var v0 rest body)
  obtain b0 where eb: "eval fs ps fuel s st ((var, v0) # env2) body = Some (VBool b0)"
    using "34.prems"(1) by (auto split: option.splits ir_value.splits)
  obtain ms where erest: "eval_the fs ps fuel s st env2 var rest body = Some ms"
    and thsEq: "ths = (if b0 then v0 # ms else ms)"
    using "34.prems"(1) eb by (auto split: option.splits)
  have lx: "env_lookup ((var, v0) # env2) x = Some vc"
    using "34.prems"(6) "34.prems"(4) by (simp add: env_lookup_def)
  have lc: "env_lookup ((var, v0) # env1) cn = Some vc"
    using "34.prems"(7) "34.prems"(5) by (simp add: env_lookup_def)
  have fr: "\<forall>y. y \<noteq> x \<longrightarrow> env_lookup ((var, v0) # env2) y = env_lookup ((var, v0) # env1) y"
    using "34.prems"(8) by (auto simp: env_lookup_def)
  have eb': "eval fs ps fuel s st ((var, v0) # env1)
               (subst x (IdentifierF cn None) body) = Some (VBool b0)"
    using "34.IH"(1)[OF eb "34.prems"(2) "34.prems"(3) lx lc fr] by simp
  have erest': "eval_the fs ps fuel s st env1 var rest
                  (subst x (IdentifierF cn None) body) = Some ms"
    using "34.IH"(2)[OF eb refl erest "34.prems"(2) "34.prems"(3) "34.prems"(4) "34.prems"(5)
                        "34.prems"(6) "34.prems"(7) "34.prems"(8)] by simp
  show ?case using eb' erest' thsEq by simp
next
  case (36 fs ps fuel s st env2 var v0 rest body)
  obtain b0 where eb: "eval fs ps fuel s st ((var, v0) # env2) body = Some (VBool b0)"
    using "36.prems"(1) by (auto split: option.splits ir_value.splits)
  obtain acc where erest: "eval_forall fs ps fuel s st env2 var rest body = Some (VBool acc)"
    and favEq: "fav = VBool (b0 \<and> acc)"
    using "36.prems"(1) eb by (auto split: option.splits ir_value.splits)
  have lx: "env_lookup ((var, v0) # env2) x = Some vc"
    using "36.prems"(6) "36.prems"(4) by (simp add: env_lookup_def)
  have lc: "env_lookup ((var, v0) # env1) cn = Some vc"
    using "36.prems"(7) "36.prems"(5) by (simp add: env_lookup_def)
  have fr: "\<forall>y. y \<noteq> x \<longrightarrow> env_lookup ((var, v0) # env2) y = env_lookup ((var, v0) # env1) y"
    using "36.prems"(8) by (auto simp: env_lookup_def)
  have eb': "eval fs ps fuel s st ((var, v0) # env1)
               (subst x (IdentifierF cn None) body) = Some (VBool b0)"
    using "36.IH"(1)[OF eb "36.prems"(2) "36.prems"(3) lx lc fr] by simp
  have erest': "eval_forall fs ps fuel s st env1 var rest
                  (subst x (IdentifierF cn None) body) = Some (VBool acc)"
    using "36.IH"(2)[OF eb refl erest "36.prems"(2) "36.prems"(3) "36.prems"(4) "36.prems"(5)
                        "36.prems"(6) "36.prems"(7) "36.prems"(8)] by simp
  show ?case using eb' erest' favEq by simp
qed (auto split: option.splits ir_value.splits list.splits if_splits)

text \<open>The field-candidate analogue. \<open>of_ok out fld e\<close> says every free
  occurrence of \<open>out\<close> is exactly as the base of \<open>out.fld\<close> (the shape the
  lowering substitutes away); the movable filter guarantees it, since any
  other occurrence would leave \<open>out\<close> free in the substituted conjunct.
  \<open>substFieldCand\<close> renames no identifiers, so every syntactic fast path of
  the evaluator is preserved unconditionally.\<close>

fun of_ok :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr \<Rightarrow> bool"
and of_ok_list :: "String.literal \<Rightarrow> String.literal \<Rightarrow> expr list \<Rightarrow> bool"
and of_ok_fields :: "String.literal \<Rightarrow> String.literal \<Rightarrow> field_assign list \<Rightarrow> bool"
and of_ok_entries :: "String.literal \<Rightarrow> String.literal \<Rightarrow> map_entry list \<Rightarrow> bool"
and of_ok_bindings :: "String.literal \<Rightarrow> String.literal \<Rightarrow> quantifier_binding list \<Rightarrow> bool"
where
  "of_ok out fld (FieldAccessF b g _) =
     (case b of
        IdentifierF n _ \<Rightarrow> n \<noteq> out \<or> g = fld
      | _ \<Rightarrow> of_ok out fld b)"
| "of_ok out fld (IdentifierF n _) = (n \<noteq> out)"
| "of_ok out fld (BinaryOpF op l r _) = (of_ok out fld l \<and> of_ok out fld r)"
| "of_ok out fld (UnaryOpF op e _) = of_ok out fld e"
| "of_ok out fld (EnumAccessF b m _) = of_ok out fld b"
| "of_ok out fld (IndexF b i _) = (of_ok out fld b \<and> of_ok out fld i)"
| "of_ok out fld (CallF c args _) = of_ok_list out fld args"
| "of_ok out fld (PrimeF e _) = of_ok out fld e"
| "of_ok out fld (PreF e _) = of_ok out fld e"
| "of_ok out fld (WithF b upds _) = (of_ok out fld b \<and> of_ok_fields out fld upds)"
| "of_ok out fld (IfF c t e _) = (of_ok out fld c \<and> of_ok out fld t \<and> of_ok out fld e)"
| "of_ok out fld (LetF v vl body _) =
     (of_ok out fld vl \<and> (v = out \<or> of_ok out fld body))"
| "of_ok out fld (LambdaF p b _) = (p = out \<or> of_ok out fld b)"
| "of_ok out fld (ConstructorF n fs _) = of_ok_fields out fld fs"
| "of_ok out fld (SetLiteralF xs _) = of_ok_list out fld xs"
| "of_ok out fld (MapLiteralF es _) = of_ok_entries out fld es"
| "of_ok out fld (SetComprehensionF v d p _) =
     (of_ok out fld d \<and> (v = out \<or> of_ok out fld p))"
| "of_ok out fld (SeqLiteralF xs _) = of_ok_list out fld xs"
| "of_ok out fld (MatchesF e pat _) = of_ok out fld e"
| "of_ok out fld (SomeWrapF e _) = of_ok out fld e"
| "of_ok out fld (TheF v d b _) =
     (of_ok out fld d \<and> (v = out \<or> of_ok out fld b))"
| "of_ok out fld (QuantifierF q bs body _) =
     (of_ok_bindings out fld bs \<and>
      (string_in_list out (qb_names bs) \<or> of_ok out fld body))"
| "of_ok out fld (IntLitF n _) = True"
| "of_ok out fld (FloatLitF n _) = True"
| "of_ok out fld (StringLitF n _) = True"
| "of_ok out fld (BoolLitF v _) = True"
| "of_ok out fld (NoneLitF _) = True"
| "of_ok_list out fld [] = True"
| "of_ok_list out fld (e # es) = (of_ok out fld e \<and> of_ok_list out fld es)"
| "of_ok_fields out fld [] = True"
| "of_ok_fields out fld (FieldAssignFull g v _ # fs) =
     (of_ok out fld v \<and> of_ok_fields out fld fs)"
| "of_ok_entries out fld [] = True"
| "of_ok_entries out fld (MapEntryFull k v _ # es) =
     (of_ok out fld k \<and> of_ok out fld v \<and> of_ok_entries out fld es)"
| "of_ok_bindings out fld [] = True"
| "of_ok_bindings out fld (QuantifierBindingFull n d kk _ # bs) =
     (of_ok out fld d \<and> of_ok_bindings out fld bs)"

text \<open>Forward shape preservation: \<open>substFieldCand\<close> keeps identifiers (and so
  every name the fast paths read) verbatim; the one node it rewrites,
  \<open>out.fld\<close>, can only ever APPEAR inside a fast-path shape after
  substitution, and then the original expression evaluated to \<open>None\<close>.\<close>

lemma substF_dom_call:
  "dom_arg l = Some a \<Longrightarrow> substFieldCand out fld cn l = l"
  by (auto dest!: dom_arg_SomeD)

lemma eval_dom_any_args_None:
  assumes "lookup_callee fs ps (STR ''dom'') = None"
  shows "eval fs ps fuel s st env (CallF (IdentifierF (STR ''dom'') spd) args sp) = None"
proof (cases fuel)
  case 0 then show ?thesis by simp
next
  case (Suc f)
  then show ?thesis
    using assms
    by (auto simp: is_builtin_pred_def is_builtin_func_def is_builtin_int_func_def
                   is_builtin_const_def
             split: option.splits list.splits ir_value.splits if_splits)
qed

lemma substF_domshape_eval_None:
  assumes "lookup_callee fs ps (STR ''dom'') = None"
      and "substFieldCand out fld cn l = CallF (IdentifierF (STR ''dom'') spd) [aa] sp"
  shows "eval fs ps fuel s st env l = None"
proof -
  obtain c args spc where leq: "l = CallF c args spc"
    using assms(2) by (cases l) (auto split: expr.splits if_splits)
  have ceq: "c = IdentifierF (STR ''dom'') spd"
    using assms(2) leq by simp
  show ?thesis using leq ceq eval_dom_any_args_None[OF assms(1)] by simp
qed

lemma substF_compshape_eval_None:
  assumes "substFieldCand out fld cn r = SetComprehensionF v d p sp"
  shows "eval fs ps fuel s st env r = None"
proof -
  obtain w d0 p0 sp0 where "r = SetComprehensionF w d0 p0 sp0"
    using assms by (cases r) (auto split: expr.splits if_splits)
  then show ?thesis by simp
qed

lemma quant_dom_substF:
  "quant_dom s st k bs = Some pr
     \<Longrightarrow> quant_dom s st k (substFieldCand_bindings out fld cn bs) = Some pr"
  by (erule quant_dom.elims) (auto split: option.splits)

lemma identName_substF:
  "identName ba = Some n
     \<Longrightarrow> identName (substFieldCand out fld cn ba) = Some n"
  by (cases ba) auto

lemma peel_substF:
  assumes "peelRelationRef b = Some rel"
  shows "peelRelationRef (substFieldCand out fld cn b) = Some rel"
proof (cases b)
  case (IdentifierF n sp) then show ?thesis using assms by simp
next
  case (PreF ba sp)
  then have "identName ba = Some rel" using assms by simp
  then show ?thesis using PreF identName_substF by simp
next
  case (PrimeF ba sp)
  then have "identName ba = Some rel" using assms by simp
  then show ?thesis using PrimeF identName_substF by simp
qed (use assms in auto)

lemma eval_substFieldCand:
  "eval fs ps fuel s st env2 e = Some v
     \<Longrightarrow> of_ok out fld e \<Longrightarrow> bn_ok cn e
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval fs ps fuel s st env1 (substFieldCand out fld cn e) = Some v"
  "eval_list fs ps fuel s st env2 es = Some vs
     \<Longrightarrow> of_ok_list out fld es \<Longrightarrow> bn_ok_list cn es
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_list fs ps fuel s st env1 (substFieldCand_list out fld cn es) = Some vs"
  "eval_entries fs ps fuel s st env2 ents = Some kvs
     \<Longrightarrow> of_ok_entries out fld ents \<Longrightarrow> bn_ok_entries cn ents
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_entries fs ps fuel s st env1 (substFieldCand_entries out fld cn ents) = Some kvs"
  "eval_fields fs ps fuel s st env2 fas = Some fvs
     \<Longrightarrow> of_ok_fields out fld fas \<Longrightarrow> bn_ok_fields cn fas
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_fields fs ps fuel s st env1 (substFieldCand_fields out fld cn fas) = Some fvs"
  "eval_the fs ps fuel s st env2 var dmv body = Some ths
     \<Longrightarrow> of_ok out fld body \<Longrightarrow> bn_ok cn body \<Longrightarrow> var \<noteq> out \<Longrightarrow> var \<noteq> cn
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_the fs ps fuel s st env1 var dmv (substFieldCand out fld cn body) = Some ths"
  "eval_forall fs ps fuel s st env2 var dmv body = Some fav
     \<Longrightarrow> of_ok out fld body \<Longrightarrow> bn_ok cn body \<Longrightarrow> var \<noteq> out \<Longrightarrow> var \<noteq> cn
     \<Longrightarrow> env_lookup env2 out = Some ov
     \<Longrightarrow> value_field_lookup st ov fld = Some vc
     \<Longrightarrow> env_lookup env1 cn = Some vc
     \<Longrightarrow> (\<forall>y. y \<noteq> out \<longrightarrow> env_lookup env2 y = env_lookup env1 y)
     \<Longrightarrow> eval_forall fs ps fuel s st env1 var dmv (substFieldCand out fld cn body) = Some fav"
proof (induction fs ps fuel s st env2 e and fs ps fuel s st env2 es
         and fs ps fuel s st env2 ents and fs ps fuel s st env2 fas
         and fs ps fuel s st env2 var dmv body and fs ps fuel s st env2 var dmv body
         arbitrary: v env1 and vs env1 and kvs env1 and fvs env1 and ths env1 and fav env1
         rule: eval_eval_list_eval_entries_eval_fields_eval_the_eval_forall.induct)
  case (5 fs ps fuel s st env2 n sp)
  have nout: "n \<noteq> out" using "5.prems"(2) by simp
  have same: "env_lookup env2 n = env_lookup env1 n"
    using "5.prems"(7) nout by simp
  show ?case using "5.prems"(1) same by (simp split: option.splits)
next
  case (6 fs ps fuel s st env2 bop l r sp)
  have ofl: "of_ok out fld l" and ofr: "of_ok out fld r"
    using "6.prems"(2) by simp_all
  have bnl: "bn_ok cn l" and bnr: "bn_ok cn r"
    using "6.prems"(3) by simp_all
  show ?case
  proof (cases "dom_eq_domains fs ps st bop l r")
    case (Some p)
    obtain dx dy where peq: "p = (dx, dy)" by (cases p)
    have de: "dom_eq_domains fs ps st bop l r = Some (dx, dy)" using Some peq by simp
    obtain rx ry where args: "dom_arg l = Some rx" "dom_arg r = Some ry"
      using dom_eq_domains_SomeD[OF de] by blast
    have sl: "substFieldCand out fld cn l = l" and sr: "substFieldCand out fld cn r = r"
      using args by (auto intro: substF_dom_call)
    have de': "dom_eq_domains fs ps st bop (substFieldCand out fld cn l)
                 (substFieldCand out fld cn r) = Some (dx, dy)"
      using de sl sr by simp
    have "eval fs ps fuel s st env1
            (substFieldCand out fld cn (BinaryOpF bop l r sp))
            = Some (VBool (set dx = set dy))"
      by (simp only: substFieldCand.simps eval_dom_eq[OF de'])
    also have "\<dots> = Some v"
      using "6.prems"(1) by (simp only: eval_dom_eq[OF de])
    finally show ?thesis .
  next
    case None
    note deN = this
    show ?thesis
    proof (cases "beq_comp bop r")
      case (Some t)
      obtain var dnm pred where teq: "t = (var, dnm, pred)" by (cases t)
      have bc: "beq_comp bop r = Some (var, dnm, pred)" using Some teq by simp
      obtain csp cdsp where req: "r = SetComprehensionF var (IdentifierF dnm cdsp) pred csp"
        and bopEq: "bop = BEq"
        using bc by (auto elim!: beq_comp.elims split: expr.splits)
      have rsub: "substFieldCand out fld cn r
                    = SetComprehensionF var (IdentifierF dnm cdsp)
                        (if var = out then pred else substFieldCand out fld cn pred) csp"
        using req by simp
      have bc': "beq_comp bop (substFieldCand out fld cn r)
                   = Some (var, dnm, if var = out then pred else substFieldCand out fld cn pred)"
        using rsub bopEq by simp
      have deN': "dom_eq_domains fs ps st bop (substFieldCand out fld cn l)
                    (substFieldCand out fld cn r) = None"
        using rsub by (auto simp: dom_eq_domains_def split: option.splits)
      have enr: "schema_lookup_enum s dnm = None"
        using "6.prems"(1) deN bc by (auto split: option.splits if_splits)
      have enrP: "\<not> schema_lookup_enum s dnm \<noteq> None" using enr by simp
      obtain dvs where srd: "state_relation_domain st dnm = Some dvs"
        using "6.prems"(1) deN bc enr by (auto split: option.splits if_splits)
      obtain ms where etm: "eval_the fs ps fuel s st env2 var dvs pred = Some ms"
        using "6.prems"(1) deN bc enr srd by (auto split: option.splits if_splits)
      obtain xs where elv: "eval fs ps fuel s st env2 l = Some (VSet xs)"
        and allc: "list_all (\<lambda>xx. contains_value dvs xx) xs"
        and vEq: "v = VBool (set xs = set ms)"
        using "6.prems"(1) deN bc enr srd etm
        by (auto split: option.splits ir_value.splits if_splits)
      have ofp: "var = out \<or> of_ok out fld pred" using ofr req by auto
      have bnp: "bn_ok cn pred" and vcn: "var \<noteq> cn" using bnr req by simp_all
      have ethe': "eval_the fs ps fuel s st env1 var dvs
                     (if var = out then pred else substFieldCand out fld cn pred) = Some ms"
      proof (cases "var = out")
        case True
        have agr: "\<forall>y. string_in_list y (remove_name var (free_vars pred))
                     \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
          using "6.prems"(7) True by auto
        show ?thesis
          using eval_coincidence(5)[OF agr] etm True by simp
      next
        case False
        have ofp': "of_ok out fld pred" using ofp False by simp
        show ?thesis
          using "6.IH"(3)[OF deN bc refl refl enrP srd etm ofp' bnp False vcn
                             "6.prems"(4) "6.prems"(5) "6.prems"(6) "6.prems"(7)] False by simp
      qed
      have el': "eval fs ps fuel s st env1 (substFieldCand out fld cn l) = Some (VSet xs)"
        using "6.IH"(4)[OF deN bc refl refl enrP srd etm elv ofl bnl
                           "6.prems"(4) "6.prems"(5) "6.prems"(6) "6.prems"(7)] by simp
      show ?thesis
        using deN' bc' enr srd ethe' el' allc vEq by simp
    next
      case None
      note bcN = this
      obtain lv rv where elv: "eval fs ps fuel s st env2 l = Some lv"
        and erv: "eval fs ps fuel s st env2 r = Some rv"
        and vbin: "eval_bin bop (Some lv) (Some rv) = Some v"
        using "6.prems"(1) deN bcN
        by (auto split: option.splits dest: eval_bin_someD) (metis eval_bin_someD)
      have il: "eval fs ps fuel s st env1 (substFieldCand out fld cn l) = Some lv"
        using "6.IH"(1)[OF deN bcN elv ofl bnl
                           "6.prems"(4) "6.prems"(5) "6.prems"(6) "6.prems"(7)] by simp
      have ir: "eval fs ps fuel s st env1 (substFieldCand out fld cn r) = Some rv"
        using "6.IH"(2)[OF deN bcN erv ofr bnr
                           "6.prems"(4) "6.prems"(5) "6.prems"(6) "6.prems"(7)] by simp
      have deN': "dom_eq_domains fs ps st bop (substFieldCand out fld cn l)
                    (substFieldCand out fld cn r) = None"
      proof (cases "dom_eq_domains fs ps st bop (substFieldCand out fld cn l)
                      (substFieldCand out fld cn r)")
        case (Some q)
        obtain dx dy where qeq: "q = (dx, dy)" by (cases q)
        obtain rx where dsl: "dom_arg (substFieldCand out fld cn l) = Some rx"
          and lk: "lookup_callee fs ps (STR ''dom'') = None"
          using dom_eq_domains_SomeD Some qeq by blast
        obtain spd spa spc where lshape:
          "substFieldCand out fld cn l
             = CallF (IdentifierF (STR ''dom'') spd) [IdentifierF rx spa] spc"
          using dom_arg_SomeD[OF dsl] by blast
        have "eval fs ps fuel s st env2 l = None"
          using substF_domshape_eval_None[OF lk lshape] .
        with elv show ?thesis by simp
      qed simp
      have bcN': "beq_comp bop (substFieldCand out fld cn r) = None"
      proof (cases "beq_comp bop (substFieldCand out fld cn r)")
        case (Some t')
        obtain var' dnm' pred' where t'eq: "t' = (var', dnm', pred')" by (cases t')
        obtain csp' cdsp' where rshape:
          "substFieldCand out fld cn r
             = SetComprehensionF var' (IdentifierF dnm' cdsp') pred' csp'"
          using Some t'eq by (auto elim!: beq_comp.elims split: expr.splits)
        have "eval fs ps fuel s st env2 r = None"
          using substF_compshape_eval_None[OF rshape] .
        with erv show ?thesis by simp
      qed simp
      show ?thesis
        using deN' bcN' il ir vbin "6.prems"(1) deN bcN by simp
    qed
  qed
next
  case (7 fs ps fuel s st env2 uop e0 sp)
  obtain a where ea: "eval fs ps fuel s st env2 e0 = Some a"
    using "7.prems"(1) eval_un_someD by fastforce
  show ?case using "7.IH"[OF ea] ea "7.prems" by simp
next
  case (8 fs ps fuel s st env2 c a b sp)
  obtain cv where ec: "eval fs ps fuel s st env2 c = Some cv"
    using "8.prems"(1) by (auto split: option.splits)
  obtain cb where cvb: "cv = VBool cb"
    using "8.prems"(1) ec by (cases cv) (auto split: option.splits)
  show ?case
  proof (cases cb)
    case True
    have cT: "eval fs ps fuel s st env2 c = Some (VBool True)" using ec cvb True by simp
    have ea: "eval fs ps fuel s st env2 a = Some v" using "8.prems"(1) cT by simp
    show ?thesis
      using "8.IH"(1)[OF cT] "8.IH"(2)[OF cT refl refl] ea "8.prems" by simp
  next
    case False
    have cF: "eval fs ps fuel s st env2 c = Some (VBool False)" using ec cvb False by simp
    have eb: "eval fs ps fuel s st env2 b = Some v" using "8.prems"(1) cF by simp
    show ?thesis
      using "8.IH"(1)[OF cF] "8.IH"(3)[OF cF refl refl] eb "8.prems" by simp
  qed
next
  case (9 fs ps fuel s st env2 w vl body sp)
  obtain va where ev: "eval fs ps fuel s st env2 vl = Some va"
    using "9.prems"(1) by (auto split: option.splits)
  have eb: "eval fs ps fuel s st ((w, va) # env2) body = Some v"
    using "9.prems"(1) ev by simp
  have ofv: "of_ok out fld vl" and ofb: "w = out \<or> of_ok out fld body"
    using "9.prems"(2) by auto
  have bnv: "bn_ok cn vl" and bnb: "bn_ok cn body" and wcn: "w \<noteq> cn"
    using "9.prems"(3) by simp_all
  have ev': "eval fs ps fuel s st env1 (substFieldCand out fld cn vl) = Some va"
    using "9.IH"(1)[OF ev ofv bnv "9.prems"(4) "9.prems"(5) "9.prems"(6) "9.prems"(7)] by simp
  show ?case
  proof (cases "w = out")
    case True
    have agr: "\<forall>y. string_in_list y (free_vars body)
                 \<longrightarrow> env_lookup ((w, va) # env2) y = env_lookup ((w, va) # env1) y"
      using "9.prems"(7) True by (auto simp: env_lookup_def)
    have "eval fs ps fuel s st ((w, va) # env1) body = Some v"
      using eval_coincidence(1)[OF agr] eb by simp
    then show ?thesis using ev' True by simp
  next
    case False
    have ofb': "of_ok out fld body" using ofb False by simp
    have lo: "env_lookup ((w, va) # env2) out = Some ov"
      using "9.prems"(4) False by (simp add: env_lookup_def)
    have lc: "env_lookup ((w, va) # env1) cn = Some vc"
      using "9.prems"(6) wcn by (simp add: env_lookup_def)
    have fr: "\<forall>y. y \<noteq> out \<longrightarrow> env_lookup ((w, va) # env2) y = env_lookup ((w, va) # env1) y"
      using "9.prems"(7) by (auto simp: env_lookup_def)
    have "eval fs ps fuel s st ((w, va) # env1)
            (substFieldCand out fld cn body) = Some v"
      using "9.IH"(2)[OF ev eb ofb' bnb lo "9.prems"(5) lc fr] by simp
    then show ?thesis using ev' False by simp
  qed
next
  case (10 fs ps fuel s st env2 base g sp)
  show ?case
  proof (cases "\<exists>n nsp. base = IdentifierF n nsp")
    case True
    then obtain n nsp where beq: "base = IdentifierF n nsp" by blast
    show ?thesis
    proof (cases "n = out")
      case True
      have gfld: "g = fld" using "10.prems"(2) beq True by simp
      have ebase: "eval fs ps fuel s st env2 base = Some ov"
        using beq True "10.prems"(4) by (simp split: option.splits)
      have vvc: "v = vc"
        using "10.prems"(1) ebase gfld "10.prems"(5) by (auto split: option.splits)
      show ?thesis
        using beq True gfld "10.prems"(6) vvc by simp
    next
      case False
      have keep: "substFieldCand out fld cn (FieldAccessF base g sp)
                    = FieldAccessF base g sp"
        using beq False by simp
      have same: "env_lookup env2 n = env_lookup env1 n"
        using "10.prems"(7) False by simp
      have "eval fs ps fuel s st env1 base = eval fs ps fuel s st env2 base"
        using beq same by (simp split: option.splits)
      then show ?thesis using keep "10.prems"(1) by (auto split: option.splits)
    qed
  next
    case False
    obtain bv2 where eb2: "eval fs ps fuel s st env2 base = Some bv2"
      using "10.prems"(1) by (auto split: option.splits)
    have ofb: "of_ok out fld base"
      using "10.prems"(2) False by (cases base) auto
    have bnb: "bn_ok cn base" using "10.prems"(3) by simp
    have eb2': "eval fs ps fuel s st env1 (substFieldCand out fld cn base) = Some bv2"
      using "10.IH"[OF eb2 ofb bnb "10.prems"(4) "10.prems"(5) "10.prems"(6) "10.prems"(7)] .
    have sub: "substFieldCand out fld cn (FieldAccessF base g sp)
                 = FieldAccessF (substFieldCand out fld cn base) g sp"
      using False by (cases base) auto
    show ?thesis using sub eb2 eb2' "10.prems"(1) by (auto split: option.splits)
  qed
next
  case (15 fs ps fuel s st env2 callee cargs sp)
  show ?case
  proof (cases fuel)
    case 0 then show ?thesis using "15.prems"(1) by simp
  next
    case (Suc f)
    obtain nm nsp where cal: "callee = IdentifierF nm nsp"
      using "15.prems"(1) Suc by (cases callee) (auto split: option.splits)
    show ?thesis
    proof (cases "lookup_callee fs ps nm")
      case (Some pb)
      obtain params fbody where pbeq: "pb = (params, fbody)" by (cases pb)
      have lk: "lookup_callee fs ps nm = Some (params, fbody)" using Some pbeq by simp
      have guard: "length params = length cargs \<and> distinct params"
        using "15.prems"(1) Suc cal lk by (auto split: if_splits)
      obtain vals where evs: "eval_list fs ps fuel s st env2 cargs = Some vals"
        using "15.prems"(1) Suc cal lk guard by (auto split: option.splits)
      have body: "eval fs ps f s st (zip params vals) fbody = Some v"
        using "15.prems"(1) Suc cal lk guard evs by simp
      have ofa: "of_ok_list out fld cargs" using "15.prems"(2) by simp
      have bna: "bn_ok_list cn cargs" using "15.prems"(3) by simp
      have evs': "eval_list fs ps fuel s st env1
                    (substFieldCand_list out fld cn cargs) = Some vals"
        using "15.IH"(4)[OF Suc cal lk refl guard evs ofa bna
                            "15.prems"(4) "15.prems"(5) "15.prems"(6) "15.prems"(7)] by simp
      have len': "length (substFieldCand_list out fld cn cargs) = length cargs"
        by (induction cargs) auto
      show ?thesis
        using Suc cal lk guard evs' body len' by simp
    next
      case None
      note lkN = this
      show ?thesis
      proof (cases cargs)
        case Nil
        then show ?thesis using "15.prems"(1) Suc cal lkN by simp
      next
        case (Cons a0 rest)
        note consR = this
        show ?thesis
        proof (cases rest)
          case Nil
          note restN = this
          have ofa: "of_ok out fld a0" using "15.prems"(2) consR restN by simp
          have bna: "bn_ok cn a0" using "15.prems"(3) consR restN by simp
          show ?thesis
          proof (cases "is_builtin_pred nm")
            case True
            obtain str where ea: "eval fs ps fuel s st env2 a0 = Some (VStr str)"
              and vv: "v = VBool (str_predicate nm str)"
              using "15.prems"(1) Suc cal lkN consR restN True
              by (auto split: option.splits ir_value.splits)
            have ea': "eval fs ps fuel s st env1
                         (substFieldCand out fld cn a0) = Some (VStr str)"
              using "15.IH"(1)[OF Suc cal lkN consR restN True ea ofa bna
                                  "15.prems"(4) "15.prems"(5) "15.prems"(6) "15.prems"(7)] .
            show ?thesis using Suc cal lkN consR restN True ea' vv by simp
          next
            case False
            note npred = this
            show ?thesis
            proof (cases "is_builtin_func nm")
              case True
              obtain str where ea: "eval fs ps fuel s st env2 a0 = Some (VStr str)"
                and vv: "v = VStr (builtin_str_func nm str)"
                using "15.prems"(1) Suc cal lkN consR restN npred True
                by (auto split: option.splits ir_value.splits)
              have ea': "eval fs ps fuel s st env1
                           (substFieldCand out fld cn a0) = Some (VStr str)"
                using "15.IH"(2)[OF Suc cal lkN consR restN npred True ea ofa bna
                                    "15.prems"(4) "15.prems"(5) "15.prems"(6) "15.prems"(7)] .
              show ?thesis
                using Suc cal lkN consR restN npred True ea' vv by simp
            next
              case False
              note nfunc = this
              have nint: "is_builtin_int_func nm"
                using "15.prems"(1) Suc cal lkN consR restN npred nfunc
                by (auto split: option.splits ir_value.splits if_splits)
              obtain n0 where ea: "eval fs ps fuel s st env2 a0 = Some (VInt n0)"
                and vv: "v = VInt (builtin_int_func nm n0)"
                using "15.prems"(1) Suc cal lkN consR restN npred nfunc nint
                by (auto split: option.splits ir_value.splits)
              have ea': "eval fs ps fuel s st env1
                           (substFieldCand out fld cn a0) = Some (VInt n0)"
                using "15.IH"(3)[OF Suc cal lkN consR restN npred nfunc nint ea ofa bna
                                    "15.prems"(4) "15.prems"(5) "15.prems"(6) "15.prems"(7)] .
              show ?thesis
                using Suc cal lkN consR restN npred nfunc nint ea' vv by simp
            qed
          qed
        next
          case (Cons a1 rest2)
          then show ?thesis using "15.prems"(1) Suc cal lkN consR by simp
        qed
      qed
    qed
  qed
next
  case (22 fs ps fuel s st env2 base mem sp)
  obtain en esp where be: "base = IdentifierF en esp"
    using "22.prems"(1) by (cases base) (auto split: option.splits)
  show ?case using "22.prems"(1) be by simp
next
  case (23 fs ps fuel s st env2 base key sp)
  obtain rel where peel: "peelRelationRef base = Some rel"
    using "23.prems"(1) by (auto split: option.splits)
  obtain kv where ek: "eval fs ps fuel s st env2 key = Some kv"
    using "23.prems"(1) peel by (auto split: option.splits)
  have peel': "peelRelationRef (substFieldCand out fld cn base) = Some rel"
    using peel_substF[OF peel] .
  have ofk: "of_ok out fld key" using "23.prems"(2) by simp
  have bnk: "bn_ok cn key" using "23.prems"(3) by simp
  have ek': "eval fs ps fuel s st env1 (substFieldCand out fld cn key) = Some kv"
    using "23.IH"[OF _ ofk bnk "23.prems"(4) "23.prems"(5) "23.prems"(6) "23.prems"(7)]
          ek peel by (auto split: option.splits)
  show ?case using peel peel' ek ek' "23.prems"(1) by (auto split: option.splits)
next
  case (24 fs ps fuel s st env2 var dm body sp)
  obtain rel dsp where dme: "dm = IdentifierF rel dsp"
    using "24.prems"(1) by (cases dm) (auto split: option.splits)
  obtain dmv0 where srd: "state_relation_domain st rel = Some dmv0"
    using "24.prems"(1) dme by (auto split: option.splits)
  obtain x0 rest0 where eth: "eval_the fs ps fuel s st env2 var dmv0 body = Some (x0 # rest0)"
    and alleq: "list_all (\<lambda>y. y = x0) rest0" and vEq: "v = x0"
    using "24.prems"(1) dme srd
    by (auto split: option.splits list.splits if_splits)
  have ofb: "var = out \<or> of_ok out fld body" using "24.prems"(2) dme by auto
  have bnb: "bn_ok cn body" and vcn: "var \<noteq> cn" using "24.prems"(3) dme by simp_all
  have eth': "eval_the fs ps fuel s st env1 var dmv0
                (if var = out then body else substFieldCand out fld cn body)
                = Some (x0 # rest0)"
  proof (cases "var = out")
    case True
    have agr: "\<forall>y. string_in_list y (remove_name var (free_vars body))
                 \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
      using "24.prems"(7) True by auto
    show ?thesis using eval_coincidence(5)[OF agr] eth True by simp
  next
    case False
    have ofb': "of_ok out fld body" using ofb False by simp
    show ?thesis
      using "24.IH"[OF dme srd eth ofb' bnb False vcn
                       "24.prems"(4) "24.prems"(5) "24.prems"(6) "24.prems"(7)] False by simp
  qed
  show ?case using dme srd eth' alleq vEq by simp
next
  case (25 fs ps fuel s st env2 k bs body sp)
  obtain var0 dmv0 where qd: "quant_dom s st k bs = Some (var0, dmv0)"
    using "25.prems"(1) by (auto split: option.splits)
  have ef: "eval_forall fs ps fuel s st env2 var0 dmv0 body = Some v"
    using "25.prems"(1) qd by simp
  have qd': "quant_dom s st k (substFieldCand_bindings out fld cn bs)
               = Some (var0, dmv0)"
    using quant_dom_substF[OF qd] .
  obtain dnm0 dsp0 kk0 sp0 where bs_eq:
    "bs = [QuantifierBindingFull var0 (IdentifierF dnm0 dsp0) kk0 sp0]"
    using qd by (auto elim!: quant_dom.elims split: option.splits)
  have ofb: "string_in_list out (qb_names bs) \<or> of_ok out fld body"
    using "25.prems"(2) by auto
  have bnb: "bn_ok cn body" using "25.prems"(3) by simp
  have vcn: "var0 \<noteq> cn"
    using "25.prems"(3) bs_eq by auto
  show ?case
  proof (cases "string_in_list out (qb_names bs)")
    case True
    have varx: "var0 = out" using True bs_eq by (simp add: string_in_list_iff)
    have agr: "\<forall>y. string_in_list y (remove_name var0 (free_vars body))
                 \<longrightarrow> env_lookup env2 y = env_lookup env1 y"
      using "25.prems"(7) varx by auto
    have ef': "eval_forall fs ps fuel s st env1 var0 dmv0 body = Some v"
      using eval_coincidence(6)[OF agr] ef by simp
    then show ?thesis using qd qd' True by simp
  next
    case False
    have varx: "var0 \<noteq> out" using False bs_eq by (simp add: string_in_list_iff)
    have ofb': "of_ok out fld body" using ofb False by simp
    have ef': "eval_forall fs ps fuel s st env1 var0 dmv0
                 (substFieldCand out fld cn body) = Some v"
      using "25.IH"[OF qd refl ef ofb' bnb varx vcn
                       "25.prems"(4) "25.prems"(5) "25.prems"(6) "25.prems"(7)] by simp
    then show ?thesis using qd qd' False by simp
  qed
next
  case (34 fs ps fuel s st env2 var v0 rest body)
  obtain b0 where eb: "eval fs ps fuel s st ((var, v0) # env2) body = Some (VBool b0)"
    using "34.prems"(1) by (auto split: option.splits ir_value.splits)
  obtain ms where erest: "eval_the fs ps fuel s st env2 var rest body = Some ms"
    and thsEq: "ths = (if b0 then v0 # ms else ms)"
    using "34.prems"(1) eb by (auto split: option.splits)
  have lo: "env_lookup ((var, v0) # env2) out = Some ov"
    using "34.prems"(6) "34.prems"(4) by (simp add: env_lookup_def)
  have lc: "env_lookup ((var, v0) # env1) cn = Some vc"
    using "34.prems"(8) "34.prems"(5) by (simp add: env_lookup_def)
  have fr: "\<forall>y. y \<noteq> out \<longrightarrow> env_lookup ((var, v0) # env2) y = env_lookup ((var, v0) # env1) y"
    using "34.prems"(9) by (auto simp: env_lookup_def)
  have eb': "eval fs ps fuel s st ((var, v0) # env1)
               (substFieldCand out fld cn body) = Some (VBool b0)"
    using "34.IH"(1)[OF eb "34.prems"(2) "34.prems"(3) lo "34.prems"(7) lc fr] by simp
  have erest': "eval_the fs ps fuel s st env1 var rest
                  (substFieldCand out fld cn body) = Some ms"
    using "34.IH"(2)[OF eb refl erest "34.prems"(2) "34.prems"(3) "34.prems"(4) "34.prems"(5)
                        "34.prems"(6) "34.prems"(7) "34.prems"(8) "34.prems"(9)] by simp
  show ?case using eb' erest' thsEq by simp
next
  case (36 fs ps fuel s st env2 var v0 rest body)
  obtain b0 where eb: "eval fs ps fuel s st ((var, v0) # env2) body = Some (VBool b0)"
    using "36.prems"(1) by (auto split: option.splits ir_value.splits)
  obtain acc where erest: "eval_forall fs ps fuel s st env2 var rest body = Some (VBool acc)"
    and favEq: "fav = VBool (b0 \<and> acc)"
    using "36.prems"(1) eb by (auto split: option.splits ir_value.splits)
  have lo: "env_lookup ((var, v0) # env2) out = Some ov"
    using "36.prems"(6) "36.prems"(4) by (simp add: env_lookup_def)
  have lc: "env_lookup ((var, v0) # env1) cn = Some vc"
    using "36.prems"(8) "36.prems"(5) by (simp add: env_lookup_def)
  have fr: "\<forall>y. y \<noteq> out \<longrightarrow> env_lookup ((var, v0) # env2) y = env_lookup ((var, v0) # env1) y"
    using "36.prems"(9) by (auto simp: env_lookup_def)
  have eb': "eval fs ps fuel s st ((var, v0) # env1)
               (substFieldCand out fld cn body) = Some (VBool b0)"
    using "36.IH"(1)[OF eb "36.prems"(2) "36.prems"(3) lo "36.prems"(7) lc fr] by simp
  have erest': "eval_forall fs ps fuel s st env1 var rest
                  (substFieldCand out fld cn body) = Some (VBool acc)"
    using "36.IH"(2)[OF eb refl erest "36.prems"(2) "36.prems"(3) "36.prems"(4) "36.prems"(5)
                        "36.prems"(6) "36.prems"(7) "36.prems"(8) "36.prems"(9)] by simp
  show ?case using eb' erest' favEq by simp
qed (auto split: option.splits ir_value.splits list.splits if_splits)

theorem lower_entails_direct:
  assumes original: "eval fs ps fuel s st envPost e = Some bv"
      and lowered:
        "eval fs ps fuel s st envPre
           (stripPre (subst x (IdentifierF cn None) e)) = Some (VBool True)"
      and sn: "sn_ok x e" and bn: "bn_ok cn e"
      and glue: "env_lookup envPost x = Some vc"
      and cand: "env_lookup envPre cn = Some vc"
      and frame: "\<forall>y. y \<noteq> x \<longrightarrow> env_lookup envPost y = env_lookup envPre y"
  shows "bv = VBool True"
proof -
  have subd:
    "eval fs ps fuel s st envPre (subst x (IdentifierF cn None) e) = Some bv"
    by (rule eval_subst_cand(1)[OF original sn bn glue cand frame])
  have "eval fs ps fuel s st envPre
          (stripPre (subst x (IdentifierF cn None) e)) = Some bv"
    by (rule eval_stripPre(1)[OF subd])
  with lowered show ?thesis by simp
qed

theorem lower_entails_field:
  assumes original: "eval fs ps fuel s st envPost e = Some bv"
      and lowered:
        "eval fs ps fuel s st envPre
           (stripPre (substFieldCand out fld cn e)) = Some (VBool True)"
      and ofok: "of_ok out fld e" and bn: "bn_ok cn e"
      and outv: "env_lookup envPost out = Some ov"
      and glue: "value_field_lookup st ov fld = Some vc"
      and cand: "env_lookup envPre cn = Some vc"
      and frame: "\<forall>y. y \<noteq> out \<longrightarrow> env_lookup envPost y = env_lookup envPre y"
  shows "bv = VBool True"
proof -
  have subd:
    "eval fs ps fuel s st envPre (substFieldCand out fld cn e) = Some bv"
    by (rule eval_substFieldCand(1)[OF original ofok bn outv glue cand frame])
  have "eval fs ps fuel s st envPre
          (stripPre (substFieldCand out fld cn e)) = Some bv"
    by (rule eval_stripPre(1)[OF subd])
  with lowered show ?thesis by simp
qed

end
