theory LintAnalysis
  imports IR IR_Analysis
begin

text \<open>Pure decision predicates and IR walkers for the lint analyses in
  \<open>modules/lint/\<close>. Scala iterates the IR slots, calls the lifted
  function per declaration, and emits formatted diagnostics Scala-side
  — same boundary established by \<open>validateIrContextRule\<close> in
  \<open>ValidateConventions\<close>.\<close>

text \<open>\<open>L03 — MissingEnsures\<close>: trivial per-op predicate.\<close>

fun operationMissingEnsures :: "operation_decl_full \<Rightarrow> bool" where
  "operationMissingEnsures (OperationDeclFull _ _ outputs _ ensures _) =
     (outputs \<noteq> [] \<and> ensures = [])"

text \<open>\<open>L05 — UnusedEntity\<close>: naive recursive collector for every
  Identifier + Constructor + Enum-access base name appearing in an
  expression. Does NOT respect binders (matches the Scala
  \<open>ExprWalk.foreach\<close> behaviour — an entity name appearing as a
  binder would erroneously count as a reference, but in practice
  entity names start with uppercase and binders are lowercase, so the
  asymmetry is harmless and the simpler walk is preferable).\<close>

fun collectExprNames :: "expr_full \<Rightarrow> String.literal list"
and collectExprNames_list :: "expr_full list \<Rightarrow> String.literal list"
and collectExprNames_fields :: "field_assign_full list \<Rightarrow> String.literal list"
and collectExprNames_entries :: "map_entry_full list \<Rightarrow> String.literal list"
and collectExprNames_bindings :: "quantifier_binding_full list \<Rightarrow> String.literal list"
where
  "collectExprNames (IdentifierF n _)            = [n]"
| "collectExprNames (ConstructorF n fs _)        = n # collectExprNames_fields fs"
| "collectExprNames (BinaryOpF _ l r _)          = collectExprNames l @ collectExprNames r"
| "collectExprNames (UnaryOpF _ e _)             = collectExprNames e"
| "collectExprNames (FieldAccessF b _ _)         = collectExprNames b"
| "collectExprNames (EnumAccessF b _ _)          = collectExprNames b"
| "collectExprNames (IndexF b i _)               = collectExprNames b @ collectExprNames i"
| "collectExprNames (CallF c args _)             = collectExprNames c @ collectExprNames_list args"
| "collectExprNames (PrimeF e _)                 = collectExprNames e"
| "collectExprNames (PreF e _)                   = collectExprNames e"
| "collectExprNames (WithF b upds _)             = collectExprNames b @ collectExprNames_fields upds"
| "collectExprNames (IfF c t e _)                = collectExprNames c @ collectExprNames t @ collectExprNames e"
| "collectExprNames (LetF _ val body _)          = collectExprNames val @ collectExprNames body"
| "collectExprNames (LambdaF _ b _)              = collectExprNames b"
| "collectExprNames (SetLiteralF xs _)           = collectExprNames_list xs"
| "collectExprNames (MapLiteralF es _)           = collectExprNames_entries es"
| "collectExprNames (SetComprehensionF _ d p _)  = collectExprNames d @ collectExprNames p"
| "collectExprNames (SeqLiteralF xs _)           = collectExprNames_list xs"
| "collectExprNames (MatchesF x _ _)             = collectExprNames x"
| "collectExprNames (SomeWrapF x _)              = collectExprNames x"
| "collectExprNames (TheF _ d b _)               = collectExprNames d @ collectExprNames b"
| "collectExprNames (QuantifierF _ bs body _)    =
     collectExprNames_bindings bs @ collectExprNames body"
| "collectExprNames (IntLitF _ _)                = []"
| "collectExprNames (FloatLitF _ _)              = []"
| "collectExprNames (StringLitF _ _)             = []"
| "collectExprNames (BoolLitF _ _)               = []"
| "collectExprNames (NoneLitF _)                 = []"
| "collectExprNames_list []                                       = []"
| "collectExprNames_list (x # xs)                                 = collectExprNames x @ collectExprNames_list xs"
| "collectExprNames_fields []                                     = []"
| "collectExprNames_fields (FieldAssignFull _ v _ # fs)           = collectExprNames v @ collectExprNames_fields fs"
| "collectExprNames_entries []                                    = []"
| "collectExprNames_entries (MapEntryFull k v _ # es)             = collectExprNames k @ collectExprNames v @ collectExprNames_entries es"
| "collectExprNames_bindings []                                   = []"
| "collectExprNames_bindings (QuantifierBindingFull _ d _ _ # bs) = collectExprNames d @ collectExprNames_bindings bs"

text \<open>Recursive type-name collector: every \<open>NamedTypeF\<close> name reachable
  through the type structure.\<close>

fun collectTypeNames :: "type_expr_full \<Rightarrow> String.literal list" where
  "collectTypeNames (NamedTypeF n _)          = [n]"
| "collectTypeNames (SetTypeF t _)            = collectTypeNames t"
| "collectTypeNames (SeqTypeF t _)            = collectTypeNames t"
| "collectTypeNames (OptionTypeF t _)         = collectTypeNames t"
| "collectTypeNames (MapTypeF k v _)          = collectTypeNames k @ collectTypeNames v"
| "collectTypeNames (RelationTypeF f _ t _)   = collectTypeNames f @ collectTypeNames t"

text \<open>\<open>L02 — UndefinedRef\<close>: walks an expression with a scope of defined
  identifiers (literal list), returning every \<open>(identifier, span)\<close> pair
  whose identifier is not in scope. Respects binders: \<open>LetF\<close>,
  \<open>LambdaF\<close>, \<open>TheF\<close>, \<open>SetComprehensionF\<close>, and \<open>QuantifierF\<close>
  bindings extend the scope for their bodies (the sub-domain of a
  \<open>QuantifierF\<close> binding is checked under the scope augmented by
  earlier bindings, matching the legacy Scala fold).\<close>

fun walkUndefinedExpr ::
  "expr_full \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> option_span) list"
and walkUndefinedExpr_list ::
  "expr_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> option_span) list"
and walkUndefinedExpr_fields ::
  "field_assign_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> option_span) list"
and walkUndefinedExpr_entries ::
  "map_entry_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> option_span) list"
and walkUndefinedExpr_bindings ::
  "quantifier_binding_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> option_span) list \<times> String.literal list"
where
  "walkUndefinedExpr (IdentifierF n sp) scope =
     (if List.member scope n then [] else [(n, sp)])"
| "walkUndefinedExpr (BinaryOpF _ l r _) scope =
     walkUndefinedExpr l scope @ walkUndefinedExpr r scope"
| "walkUndefinedExpr (UnaryOpF _ e _) scope = walkUndefinedExpr e scope"
| "walkUndefinedExpr (FieldAccessF b _ _) scope = walkUndefinedExpr b scope"
| "walkUndefinedExpr (EnumAccessF b _ _) scope = walkUndefinedExpr b scope"
| "walkUndefinedExpr (IndexF b i _) scope =
     walkUndefinedExpr b scope @ walkUndefinedExpr i scope"
| "walkUndefinedExpr (CallF c args _) scope =
     walkUndefinedExpr c scope @ walkUndefinedExpr_list args scope"
| "walkUndefinedExpr (PrimeF e _) scope = walkUndefinedExpr e scope"
| "walkUndefinedExpr (PreF e _) scope = walkUndefinedExpr e scope"
| "walkUndefinedExpr (WithF b upds _) scope =
     walkUndefinedExpr b scope @ walkUndefinedExpr_fields upds scope"
| "walkUndefinedExpr (IfF c t e _) scope =
     walkUndefinedExpr c scope @ walkUndefinedExpr t scope @ walkUndefinedExpr e scope"
| "walkUndefinedExpr (LetF v val body _) scope =
     walkUndefinedExpr val scope @ walkUndefinedExpr body (v # scope)"
| "walkUndefinedExpr (LambdaF p body _) scope =
     walkUndefinedExpr body (p # scope)"
| "walkUndefinedExpr (ConstructorF _ fs _) scope =
     walkUndefinedExpr_fields fs scope"
| "walkUndefinedExpr (SetLiteralF xs _) scope =
     walkUndefinedExpr_list xs scope"
| "walkUndefinedExpr (MapLiteralF es _) scope =
     walkUndefinedExpr_entries es scope"
| "walkUndefinedExpr (SetComprehensionF v d p _) scope =
     walkUndefinedExpr d scope @ walkUndefinedExpr p (v # scope)"
| "walkUndefinedExpr (SeqLiteralF xs _) scope =
     walkUndefinedExpr_list xs scope"
| "walkUndefinedExpr (MatchesF x _ _) scope = walkUndefinedExpr x scope"
| "walkUndefinedExpr (SomeWrapF x _) scope = walkUndefinedExpr x scope"
| "walkUndefinedExpr (TheF v d b _) scope =
     walkUndefinedExpr d scope @ walkUndefinedExpr b (v # scope)"
| "walkUndefinedExpr (QuantifierF _ bs body _) scope =
     (let (binds, scope') = walkUndefinedExpr_bindings bs scope
      in binds @ walkUndefinedExpr body scope')"
| "walkUndefinedExpr (IntLitF _ _) _ = []"
| "walkUndefinedExpr (FloatLitF _ _) _ = []"
| "walkUndefinedExpr (StringLitF _ _) _ = []"
| "walkUndefinedExpr (BoolLitF _ _) _ = []"
| "walkUndefinedExpr (NoneLitF _) _ = []"
| "walkUndefinedExpr_list [] _ = []"
| "walkUndefinedExpr_list (x # xs) scope =
     walkUndefinedExpr x scope @ walkUndefinedExpr_list xs scope"
| "walkUndefinedExpr_fields [] _ = []"
| "walkUndefinedExpr_fields (FieldAssignFull _ v _ # fs) scope =
     walkUndefinedExpr v scope @ walkUndefinedExpr_fields fs scope"
| "walkUndefinedExpr_entries [] _ = []"
| "walkUndefinedExpr_entries (MapEntryFull k v _ # es) scope =
     walkUndefinedExpr k scope @ walkUndefinedExpr v scope @ walkUndefinedExpr_entries es scope"
| "walkUndefinedExpr_bindings [] scope = ([], scope)"
| "walkUndefinedExpr_bindings (QuantifierBindingFull v d _ _ # bs) scope =
     (let cur  = walkUndefinedExpr d scope;
          (rest, finalScope) = walkUndefinedExpr_bindings bs (v # scope)
      in (cur @ rest, finalScope))"

lemmas operationMissingEnsures_code [code] = operationMissingEnsures.simps
lemmas collectExprNames_code [code]          = collectExprNames.simps
lemmas collectTypeNames_code [code]          = collectTypeNames.simps
lemmas walkUndefinedExpr_code [code]         = walkUndefinedExpr.simps

end
