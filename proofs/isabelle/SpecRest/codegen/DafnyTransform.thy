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
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> expr_full \<Rightarrow> expr_full"
  and rewriteFieldRefsList ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> expr_full list \<Rightarrow> expr_full list"
  and rewriteFieldRefsFields ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> field_assign_full list \<Rightarrow> field_assign_full list"
  and rewriteFieldRefsEntries ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> map_entry_full list \<Rightarrow> map_entry_full list"
  and rewriteFieldRefsBindings ::
    "String.literal list \<Rightarrow> String.literal list \<Rightarrow> quantifier_binding_full list
      \<Rightarrow> quantifier_binding_full list"
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

definition rewriteEntityFieldRefs :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> expr_full" where
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

definition neqNoneName :: "expr_full \<Rightarrow> String.literal option" where
  "neqNoneName e =
     (case e of BinaryOpF BNeq (IdentifierF p _) (NoneLitF _) _ \<Rightarrow> Some p | _ \<Rightarrow> None)"

definition eqNoneName :: "expr_full \<Rightarrow> String.literal option" where
  "eqNoneName e =
     (case e of BinaryOpF BEq (IdentifierF p _) (NoneLitF _) _ \<Rightarrow> Some p | _ \<Rightarrow> None)"

definition substValue :: "String.literal \<Rightarrow> expr_full \<Rightarrow> expr_full" where
  "substValue p body = subst p (FieldAccessF (IdentifierF p None) (STR ''value'') None) body"

function (sequential) desugarGo :: "nat \<Rightarrow> String.literal list \<Rightarrow> expr_full \<Rightarrow> expr_full"
  and desugarBindings ::
    "nat \<Rightarrow> String.literal list \<Rightarrow> quantifier_binding_full list \<Rightarrow> quantifier_binding_full list"
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

definition desugarOptionGuards :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> expr_full" where
  "desugarOptionGuards opts e = desugarGo (length (allSubexprs e) + 100) opts e"

lemmas neqNoneName_code [code]         = neqNoneName_def
lemmas eqNoneName_code [code]          = eqNoneName_def
lemmas substValue_code [code]          = substValue_def
lemmas desugarGo_code [code]           = desugarGo.simps
lemmas desugarBindings_code [code]     = desugarBindings.simps
lemmas desugarOptionGuards_code [code] = desugarOptionGuards_def

end
