theory DafnyTransform
  imports SpecRest_Core.IR_Analysis SpecRest_Core.IR_Helpers "HOL-Library.Code_Target_Numeral"
begin

text \<open>Lift of \<open>convention.dafny.Generator.rewriteEntityFieldRefs\<close>: rewrite every free
  reference to an entity field name \<open>n\<close> into \<open>x.n\<close> (a \<open>FieldAccessF\<close> on the entity
  receiver \<open>x\<close>), leaving names shadowed by an enclosing binder untouched. Structural
  recursion threading the bound-variable scope — the same shape as \<open>subst\<close>; the field-name
  set is supplied by the already-lifted \<open>entityFieldNames\<close>.

  Mirrors the Scala exactly: \<open>EnumAccessF\<close> and \<open>MatchesF\<close> are NOT descended into — the
  original's \<open>case other => other\<close> leaves them (and the literals) unchanged. The introduced
  receiver \<open>x\<close> is never re-rewritten because the rewrite returns the \<open>FieldAccessF\<close> directly
  without recursing on it.\<close>

fun rewriteFieldRefsAux ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> expr \<Rightarrow> expr"
  and rewriteFieldRefsList ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> expr list \<Rightarrow> expr list"
  and rewriteFieldRefsFields ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> field_assign list \<Rightarrow> field_assign list"
  and rewriteFieldRefsEntries ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> map_entry list \<Rightarrow> map_entry list"
  and rewriteFieldRefsBindings ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> quantifier_binding list
      \<Rightarrow> quantifier_binding list"
where
  "rewriteFieldRefsAux flds bound (IdentifierF n sp) =
     (if string_in_list n flds \<and> \<not> string_in_list n bound
        then FieldAccessF (IdentifierF (STR ''x'') sp) n sp
        else IdentifierF n sp)"
| "rewriteFieldRefsAux flds bound (BinaryOpF op l r sp) =
     BinaryOpF op (rewriteFieldRefsAux flds bound l) (rewriteFieldRefsAux flds bound r) sp"
| "rewriteFieldRefsAux flds bound (UnaryOpF op e sp) =
     UnaryOpF op (rewriteFieldRefsAux flds bound e) sp"
| "rewriteFieldRefsAux flds bound (FieldAccessF b f sp) =
     FieldAccessF (rewriteFieldRefsAux flds bound b) f sp"
| "rewriteFieldRefsAux flds bound (EnumAccessF b m sp) = EnumAccessF b m sp"
| "rewriteFieldRefsAux flds bound (IndexF b i sp) =
     IndexF (rewriteFieldRefsAux flds bound b) (rewriteFieldRefsAux flds bound i) sp"
| "rewriteFieldRefsAux flds bound (CallF c args sp) =
     CallF (rewriteFieldRefsAux flds bound c) (rewriteFieldRefsList flds bound args) sp"
| "rewriteFieldRefsAux flds bound (PrimeF e sp) =
     PrimeF (rewriteFieldRefsAux flds bound e) sp"
| "rewriteFieldRefsAux flds bound (PreF e sp) =
     PreF (rewriteFieldRefsAux flds bound e) sp"
| "rewriteFieldRefsAux flds bound (WithF b upds sp) =
     WithF (rewriteFieldRefsAux flds bound b) (rewriteFieldRefsFields flds bound upds) sp"
| "rewriteFieldRefsAux flds bound (IfF c t e sp) =
     IfF (rewriteFieldRefsAux flds bound c) (rewriteFieldRefsAux flds bound t)
         (rewriteFieldRefsAux flds bound e) sp"
| "rewriteFieldRefsAux flds bound (LetF v val body sp) =
     LetF v (rewriteFieldRefsAux flds bound val)
            (rewriteFieldRefsAux flds (v # bound) body) sp"
| "rewriteFieldRefsAux flds bound (LambdaF p b sp) =
     LambdaF p (rewriteFieldRefsAux flds (p # bound) b) sp"
| "rewriteFieldRefsAux flds bound (ConstructorF n fs sp) =
     ConstructorF n (rewriteFieldRefsFields flds bound fs) sp"
| "rewriteFieldRefsAux flds bound (SetLiteralF xs sp) =
     SetLiteralF (rewriteFieldRefsList flds bound xs) sp"
| "rewriteFieldRefsAux flds bound (MapLiteralF es sp) =
     MapLiteralF (rewriteFieldRefsEntries flds bound es) sp"
| "rewriteFieldRefsAux flds bound (SetComprehensionF v d p sp) =
     SetComprehensionF v (rewriteFieldRefsAux flds bound d)
                         (rewriteFieldRefsAux flds (v # bound) p) sp"
| "rewriteFieldRefsAux flds bound (SeqLiteralF xs sp) =
     SeqLiteralF (rewriteFieldRefsList flds bound xs) sp"
| "rewriteFieldRefsAux flds bound (MatchesF e pat sp) = MatchesF e pat sp"
| "rewriteFieldRefsAux flds bound (SomeWrapF e sp) =
     SomeWrapF (rewriteFieldRefsAux flds bound e) sp"
| "rewriteFieldRefsAux flds bound (TheF v d b sp) =
     TheF v (rewriteFieldRefsAux flds bound d) (rewriteFieldRefsAux flds (v # bound) b) sp"
| "rewriteFieldRefsAux flds bound (QuantifierF q bs body sp) =
     QuantifierF q (rewriteFieldRefsBindings flds bound bs)
                   (rewriteFieldRefsAux flds (qb_names bs @ bound) body) sp"
| "rewriteFieldRefsAux _ _ (IntLitF n sp)    = IntLitF n sp"
| "rewriteFieldRefsAux _ _ (FloatLitF n sp)  = FloatLitF n sp"
| "rewriteFieldRefsAux _ _ (StringLitF n sp) = StringLitF n sp"
| "rewriteFieldRefsAux _ _ (BoolLitF v sp)   = BoolLitF v sp"
| "rewriteFieldRefsAux _ _ (NoneLitF sp)     = NoneLitF sp"
| "rewriteFieldRefsList _ _ [] = []"
| "rewriteFieldRefsList flds bound (e # es) =
     rewriteFieldRefsAux flds bound e # rewriteFieldRefsList flds bound es"
| "rewriteFieldRefsFields _ _ [] = []"
| "rewriteFieldRefsFields flds bound (FieldAssignFull f v sp # fs) =
     FieldAssignFull f (rewriteFieldRefsAux flds bound v) sp
       # rewriteFieldRefsFields flds bound fs"
| "rewriteFieldRefsEntries _ _ [] = []"
| "rewriteFieldRefsEntries flds bound (MapEntryFull k v sp # es) =
     MapEntryFull (rewriteFieldRefsAux flds bound k) (rewriteFieldRefsAux flds bound v) sp
       # rewriteFieldRefsEntries flds bound es"
| "rewriteFieldRefsBindings _ _ [] = []"
| "rewriteFieldRefsBindings flds bound (QuantifierBindingFull a d kk sp # bs) =
     QuantifierBindingFull a (rewriteFieldRefsAux flds bound d) kk sp
       # rewriteFieldRefsBindings flds bound bs"

definition rewriteEntityFieldRefs :: "String.literal list \<Rightarrow> expr \<Rightarrow> expr" where
  "rewriteEntityFieldRefs flds e = rewriteFieldRefsAux flds [] e"

lemmas rewriteEntityFieldRefs_code [code] = rewriteEntityFieldRefs_def

text \<open>Lift of \<open>convention.dafny.Generator.desugarOptionGuards\<close>: for an Option-typed
  parameter \<open>p\<close> (\<open>p \<in> opts\<close>), rewrite the three guard shapes — \<open>p \<noteq> None \<longrightarrow> body\<close>,
  \<open>p = None \<or> body\<close>, \<open>body \<or> p = None\<close> — by substituting \<open>p\<close> with \<open>p.value\<close> in the body
  (via the lifted \<open>subst\<close>), so the Dafny side sees a non-nullable value under the guard.

  The Scala recurses on the substituted body, which is not a structural subterm, so a
  fuel bound is used. The substituted \<open>FieldAccessF\<close> is a desugar leaf (the walk only
  descends \<open>BinaryOpF\<close>/\<open>UnaryOpF\<close>/\<open>QuantifierF\<close>), so the recursion never goes deeper than
  the original tree; \<open>length (allSubexprs e) + 100\<close> bounds the node count and the fuel
  never runs out on any real ensures clause — same output as the Scala.\<close>

definition neqNoneName :: "expr \<Rightarrow> String.literal option" where
  "neqNoneName e =
     (case e of BinaryOpF BNeq (IdentifierF p _) (NoneLitF _) _ \<Rightarrow> Some p | _ \<Rightarrow> None)"

definition eqNoneName :: "expr \<Rightarrow> String.literal option" where
  "eqNoneName e =
     (case e of BinaryOpF BEq (IdentifierF p _) (NoneLitF _) _ \<Rightarrow> Some p | _ \<Rightarrow> None)"

definition substValue :: "String.literal \<Rightarrow> expr \<Rightarrow> expr" where
  "substValue p body = subst p (FieldAccessF (IdentifierF p None) (STR ''value'') None) body"

function (sequential) desugarGo :: "nat \<Rightarrow> String.literal list \<Rightarrow> expr \<Rightarrow> expr"
  and desugarBindings ::
    "nat \<Rightarrow> String.literal list \<Rightarrow> quantifier_binding list \<Rightarrow> quantifier_binding list"
where
  "desugarGo 0 _ e = e"
| "desugarGo (Suc fuel) opts e =
     (case e of
        BinaryOpF op l r sp \<Rightarrow>
          (if op = BImplies then
             (case neqNoneName l of
                Some p \<Rightarrow> (if string_in_list p opts
                             then BinaryOpF BImplies l (desugarGo fuel opts (substValue p r)) sp
                             else BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp)
              | None \<Rightarrow> BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp)
           else if op = BOr then
             (case eqNoneName l of
                Some p \<Rightarrow> (if string_in_list p opts
                             then BinaryOpF BOr l (desugarGo fuel opts (substValue p r)) sp
                             else
                               (case eqNoneName r of
                                  Some q \<Rightarrow> (if string_in_list q opts
                                               then BinaryOpF BOr (desugarGo fuel opts (substValue q l)) r sp
                                               else BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp)
                                | None \<Rightarrow> BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp))
              | None \<Rightarrow>
                  (case eqNoneName r of
                     Some q \<Rightarrow> (if string_in_list q opts
                                  then BinaryOpF BOr (desugarGo fuel opts (substValue q l)) r sp
                                  else BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp)
                   | None \<Rightarrow> BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp))
           else BinaryOpF op (desugarGo fuel opts l) (desugarGo fuel opts r) sp)
      | UnaryOpF op x sp \<Rightarrow> UnaryOpF op (desugarGo fuel opts x) sp
      | QuantifierF q bs body sp \<Rightarrow>
          QuantifierF q (desugarBindings fuel opts bs) (desugarGo fuel opts body) sp
      | _ \<Rightarrow> e)"
| "desugarBindings 0 _ bs = bs"
| "desugarBindings (Suc fuel) opts bs =
     (case bs of
        [] \<Rightarrow> []
      | QuantifierBindingFull a d kind bsp # rest \<Rightarrow>
          QuantifierBindingFull a (desugarGo fuel opts d) kind bsp # desugarBindings fuel opts rest)"
  by pat_completeness auto

termination
  by (relation "measure (\<lambda>p. case p of Inl (fuel, _, _) \<Rightarrow> fuel | Inr (fuel, _, _) \<Rightarrow> fuel)") auto

definition desugarOptionGuards :: "String.literal list \<Rightarrow> expr \<Rightarrow> expr" where
  "desugarOptionGuards opts e = desugarGo (length (allSubexprs e) + 100) opts e"

lemmas neqNoneName_code [code]         = neqNoneName_def
lemmas eqNoneName_code [code]          = eqNoneName_def
lemmas substValue_code [code]          = substValue_def
lemmas desugarGo_code [code]           = desugarGo.simps
lemmas desugarBindings_code [code]     = desugarBindings.simps
lemmas desugarOptionGuards_code [code] = desugarOptionGuards_def

text \<open>Lift of \<open>convention.dafny.Generator.classifyExterns\<close>'s analysis. The Scala walks
  every spec expression threading an "expected kind" (Predicate vs IntFunction) and records
  each non-builtin \<open>CallF\<close> callee as an extern, merging duplicates (IntFunction dominates;
  arity = max) into an insertion-ordered map, plus the \<open>MatchesF\<close> regex patterns into an
  insertion-ordered set.

  Split into two lifted pieces: \<open>collectExternItems\<close> is the structural walk that emits a flat
  \<open>extern_item list\<close> in traversal order (the kind-threading is the lifted decision); the Scala
  driver calls it per top-level declaration in the original order and concatenates. Then
  \<open>classifyExternItems\<close> folds that list into the order-preserving merged extern alist + the
  deduplicated pattern list, reproducing the \<open>LinkedHashMap\<close>/\<open>LinkedHashSet\<close> semantics. The
  info constructor is \<open>ExInfo\<close> (not \<open>ExternInfo\<close>) so it doesn't clash with the Scala
  \<open>ExternInfo\<close> case class the adapter targets.\<close>

datatype extern_kind = EkPredicate | EkIntFunction
datatype extern_info = ExInfo extern_kind int
datatype extern_item = EiExtern String.literal int extern_kind | EiPattern String.literal

definition knownBuiltinNames :: "String.literal list" where
  "knownBuiltinNames = [STR ''len'', STR ''dom'', STR ''ran'']"

fun collectExternItems :: "extern_kind \<Rightarrow> expr \<Rightarrow> extern_item list"
  and collectExternItemsArgs :: "expr list \<Rightarrow> extern_item list"
  and collectExternItemsFields :: "field_assign list \<Rightarrow> extern_item list"
  and collectExternItemsEntries :: "map_entry list \<Rightarrow> extern_item list"
  and collectExternItemsBindings :: "quantifier_binding list \<Rightarrow> extern_item list"
where
  "collectExternItems expected (CallF c args sp) =
     (case c of
        IdentifierF n _ \<Rightarrow>
          (if string_in_list n knownBuiltinNames
             then collectExternItemsArgs args
             else EiExtern n (int (length args)) expected # collectExternItemsArgs args)
      | _ \<Rightarrow> collectExternItemsArgs args)"
| "collectExternItems expected (BinaryOpF op l r sp) =
     (if op = BAnd \<or> op = BOr \<or> op = BImplies \<or> op = BIff
        then collectExternItems EkPredicate l @ collectExternItems EkPredicate r
        else collectExternItems EkIntFunction l @ collectExternItems EkIntFunction r)"
| "collectExternItems expected (UnaryOpF op x sp) =
     (if op = UNot then collectExternItems EkPredicate x else collectExternItems EkIntFunction x)"
| "collectExternItems expected (PrimeF x sp) = collectExternItems expected x"
| "collectExternItems expected (PreF x sp) = collectExternItems expected x"
| "collectExternItems expected (SomeWrapF x sp) = collectExternItems EkIntFunction x"
| "collectExternItems expected (FieldAccessF b f sp) = collectExternItems EkIntFunction b"
| "collectExternItems expected (IndexF b i sp) =
     collectExternItems EkIntFunction b @ collectExternItems EkIntFunction i"
| "collectExternItems expected (MapLiteralF es sp) = collectExternItemsEntries es"
| "collectExternItems expected (SetLiteralF es sp) = collectExternItemsArgs es"
| "collectExternItems expected (SeqLiteralF es sp) = collectExternItemsArgs es"
| "collectExternItems expected (IfF c t e sp) =
     collectExternItems EkPredicate c @ collectExternItems expected t @ collectExternItems expected e"
| "collectExternItems expected (LetF v val b sp) =
     collectExternItems EkIntFunction val @ collectExternItems expected b"
| "collectExternItems expected (QuantifierF q bs body sp) =
     collectExternItemsBindings bs @ collectExternItems EkPredicate body"
| "collectExternItems expected (SetComprehensionF v dm pr sp) =
     collectExternItems EkIntFunction dm @ collectExternItems EkPredicate pr"
| "collectExternItems expected (ConstructorF n fs sp) = collectExternItemsFields fs"
| "collectExternItems expected (WithF b fs sp) =
     collectExternItems EkIntFunction b @ collectExternItemsFields fs"
| "collectExternItems expected (TheF v dm body sp) =
     collectExternItems EkIntFunction dm @ collectExternItems EkPredicate body"
| "collectExternItems expected (LambdaF p body sp) = collectExternItems EkIntFunction body"
| "collectExternItems expected (MatchesF x p sp) = EiPattern p # collectExternItems EkIntFunction x"
| "collectExternItems _ _ = []"
| "collectExternItemsArgs [] = []"
| "collectExternItemsArgs (e # es) = collectExternItems EkIntFunction e @ collectExternItemsArgs es"
| "collectExternItemsFields [] = []"
| "collectExternItemsFields (FieldAssignFull f v sp # fs) =
     collectExternItems EkIntFunction v @ collectExternItemsFields fs"
| "collectExternItemsEntries [] = []"
| "collectExternItemsEntries (MapEntryFull k v sp # es) =
     collectExternItems EkIntFunction k @ collectExternItems EkIntFunction v
       @ collectExternItemsEntries es"
| "collectExternItemsBindings [] = []"
| "collectExternItemsBindings (QuantifierBindingFull a d kind sp # bs) =
     collectExternItems EkIntFunction d @ collectExternItemsBindings bs"

fun mergeExternInfo :: "extern_info \<Rightarrow> int \<Rightarrow> extern_kind \<Rightarrow> extern_info" where
  "mergeExternInfo (ExInfo prevKind prevArity) arity kind =
     ExInfo (if prevKind = EkIntFunction \<or> kind = EkIntFunction then EkIntFunction else EkPredicate)
            (max prevArity arity)"

fun upsertExtern ::
  "(String.literal \<times> extern_info) list \<Rightarrow> String.literal \<Rightarrow> int \<Rightarrow> extern_kind
    \<Rightarrow> (String.literal \<times> extern_info) list"
where
  "upsertExtern [] name arity kind = [(name, ExInfo kind arity)]"
| "upsertExtern ((n, info) # rest) name arity kind =
     (if n = name then (n, mergeExternInfo info arity kind) # rest
      else (n, info) # upsertExtern rest name arity kind)"

fun foldExternItems ::
  "extern_item list \<Rightarrow> (String.literal \<times> extern_info) list \<Rightarrow> String.literal list
    \<Rightarrow> (String.literal \<times> extern_info) list \<times> String.literal list"
where
  "foldExternItems [] externs patterns = (externs, patterns)"
| "foldExternItems (EiExtern name arity kind # rest) externs patterns =
     foldExternItems rest (upsertExtern externs name arity kind) patterns"
| "foldExternItems (EiPattern p # rest) externs patterns =
     foldExternItems rest externs (if string_in_list p patterns then patterns else patterns @ [p])"

definition classifyExternItems ::
  "extern_item list \<Rightarrow> (String.literal \<times> extern_info) list \<times> String.literal list"
where
  "classifyExternItems items = foldExternItems items [] []"

lemmas knownBuiltinNames_code [code]   = knownBuiltinNames_def
lemmas collectExternItems_code [code]  = collectExternItems.simps
lemmas mergeExternInfo_code [code]     = mergeExternInfo.simps
lemmas upsertExtern_code [code]        = upsertExtern.simps
lemmas foldExternItems_code [code]     = foldExternItems.simps
lemmas classifyExternItems_code [code] = classifyExternItems_def

end
