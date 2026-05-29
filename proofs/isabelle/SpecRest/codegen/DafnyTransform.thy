theory DafnyTransform
  imports SpecRest_Core.IR_Analysis SpecRest_Core.IR_Helpers
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

end
