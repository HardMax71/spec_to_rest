theory IR_FreeVars
  imports IR
begin

text \<open>Phase 9\<gamma> (free-var helpers): monomorphic \<open>qb_names\<close>,
  \<open>remove_name\<close>, \<open>remove_names\<close> avoid the polymorphic \<open>map\<close>/\<open>filter\<close>
  HOFs that blow up Isabelle build wall-time when used inside large mutual
  \<open>fun\<close> declarations.\<close>

fun qb_names :: "quantifier_binding list \<Rightarrow> String.literal list" where
  "qb_names [] = []"
| "qb_names (QuantifierBindingFull n _ _ _ # bs) = n # qb_names bs"

fun remove_name :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "remove_name _ [] = []"
| "remove_name n (x # xs) =
     (if x = n then remove_name n xs else x # remove_name n xs)"

fun remove_names :: "String.literal list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "remove_names []       xs = xs"
| "remove_names (n # ns) xs = remove_name n (remove_names ns xs)"

text \<open>Phase 9\<gamma> (\<open>free_vars\<close>): collects the names of all free identifiers
  in an \<open>expr\<close>, respecting binders (\<open>LetF\<close>, \<open>LambdaF\<close>,
  \<open>SetComprehensionF\<close>, \<open>TheF\<close>, \<open>QuantifierF\<close>). Replaces the 60-line
  hand-rolled walker \<open>testgen.Behavioral.containsStateRefIn\<close>.\<close>

fun free_vars :: "expr \<Rightarrow> String.literal list"
and free_vars_list :: "expr list \<Rightarrow> String.literal list"
and free_vars_fields :: "field_assign list \<Rightarrow> String.literal list"
and free_vars_entries :: "map_entry list \<Rightarrow> String.literal list"
and free_vars_bindings :: "quantifier_binding list \<Rightarrow> String.literal list"
where
  "free_vars (IdentifierF n _)           = [n]"
| "free_vars (BinaryOpF _ l r _)         = free_vars l @ free_vars r"
| "free_vars (UnaryOpF _ e _)            = free_vars e"
| "free_vars (FieldAccessF b _ _)        = free_vars b"
| "free_vars (EnumAccessF b _ _)         = free_vars b"
| "free_vars (IndexF b i _)              = free_vars b @ free_vars i"
| "free_vars (CallF c args _)            =
     (case identName c of Some _ \<Rightarrow> [] | None \<Rightarrow> free_vars c) @ free_vars_list args"
| "free_vars (PrimeF e _)                = free_vars e"
| "free_vars (PreF e _)                  = free_vars e"
| "free_vars (WithF b upds _)            = free_vars b @ free_vars_fields upds"
| "free_vars (IfF c t e _)               = free_vars c @ free_vars t @ free_vars e"
| "free_vars (LetF v val body _)         = free_vars val @ remove_name v (free_vars body)"
| "free_vars (LambdaF p b _)             = remove_name p (free_vars b)"
| "free_vars (ConstructorF _ fs _)       = free_vars_fields fs"
| "free_vars (SetLiteralF xs _)          = free_vars_list xs"
| "free_vars (MapLiteralF es _)          = free_vars_entries es"
| "free_vars (SetComprehensionF v d p _) = free_vars d @ remove_name v (free_vars p)"
| "free_vars (SeqLiteralF xs _)          = free_vars_list xs"
| "free_vars (MatchesF x _ _)            = free_vars x"
| "free_vars (SomeWrapF x _)             = free_vars x"
| "free_vars (TheF v d b _)              = free_vars d @ remove_name v (free_vars b)"
| "free_vars (QuantifierF _ bs body _)   =
     free_vars_bindings bs @ remove_names (qb_names bs) (free_vars body)"
| "free_vars (IntLitF _ _)               = []"
| "free_vars (FloatLitF _ _)             = []"
| "free_vars (StringLitF _ _)            = []"
| "free_vars (BoolLitF _ _)              = []"
| "free_vars (NoneLitF _)                = []"
| "free_vars_list []                                            = []"
| "free_vars_list (x # xs)                                      = free_vars x @ free_vars_list xs"
| "free_vars_fields []                                          = []"
| "free_vars_fields (FieldAssignFull _ v _ # fs)                = free_vars v @ free_vars_fields fs"
| "free_vars_entries []                                         = []"
| "free_vars_entries (MapEntryFull k v _ # es)                  = free_vars k @ free_vars v @ free_vars_entries es"
| "free_vars_bindings []                                        = []"
| "free_vars_bindings (QuantifierBindingFull _ d _ _ # bs)      = free_vars d @ free_vars_bindings bs"

text \<open>Phase 9\<gamma> (\<open>hasPrePrime\<close>): true iff the expression contains a
  \<open>PrimeF\<close> or \<open>PreF\<close> constructor anywhere. Used together with \<open>free_vars\<close>
  to express the \<open>testgen.Behavioral.containsStateRef\<close> predicate as a
  one-liner.\<close>

fun isPrePrime :: "expr \<Rightarrow> bool" where
  "isPrePrime (PrimeF _ _) = True"
| "isPrePrime (PreF _ _)   = True"
| "isPrePrime _            = False"

definition hasPrePrime :: "expr \<Rightarrow> bool" where
  "hasPrePrime e = list_ex isPrePrime (allSubexprs e)"

text \<open>Phase 9\<gamma> (\<open>subst\<close>): structural substitution of a free identifier
  by an expression. Stops at binders that shadow the substituted name —
  does NOT perform \<open>\<alpha>\<close>-renaming, matching the semantics of
  \<open>convention.dafny.Generator.rewriteValueRef\<close>. The caller is responsible
  for ensuring the replacement expression's free variables cannot be
  captured (typical use: replace \<open>value\<close> by
  \<open>FieldAccessF (IdentifierF p None) STR ''value'' None\<close> where \<open>p\<close> is not
  bound anywhere relevant).\<close>

fun subst :: "String.literal \<Rightarrow> expr \<Rightarrow> expr \<Rightarrow> expr"
and subst_list :: "String.literal \<Rightarrow> expr \<Rightarrow> expr list \<Rightarrow> expr list"
and subst_fields :: "String.literal \<Rightarrow> expr \<Rightarrow> field_assign list \<Rightarrow> field_assign list"
and subst_entries :: "String.literal \<Rightarrow> expr \<Rightarrow> map_entry list \<Rightarrow> map_entry list"
and subst_bindings :: "String.literal \<Rightarrow> expr \<Rightarrow> quantifier_binding list \<Rightarrow> quantifier_binding list"
where
  "subst x r (IdentifierF n sp)              = (if n = x then r else IdentifierF n sp)"
| "subst x r (BinaryOpF op l rr sp)          = BinaryOpF op (subst x r l) (subst x r rr) sp"
| "subst x r (UnaryOpF op e sp)              = UnaryOpF op (subst x r e) sp"
| "subst x r (FieldAccessF b f sp)           = FieldAccessF (subst x r b) f sp"
| "subst x r (EnumAccessF b m sp)            = EnumAccessF (subst x r b) m sp"
| "subst x r (IndexF b i sp)                 = IndexF (subst x r b) (subst x r i) sp"
| "subst x r (CallF c args sp)               = CallF (subst x r c) (subst_list x r args) sp"
| "subst x r (PrimeF e sp)                   = PrimeF (subst x r e) sp"
| "subst x r (PreF e sp)                     = PreF (subst x r e) sp"
| "subst x r (WithF b upds sp)               = WithF (subst x r b) (subst_fields x r upds) sp"
| "subst x r (IfF c t e sp)                  = IfF (subst x r c) (subst x r t) (subst x r e) sp"
| "subst x r (LetF v val body sp)            =
     LetF v (subst x r val) (if v = x then body else subst x r body) sp"
| "subst x r (LambdaF p b sp)                =
     LambdaF p (if p = x then b else subst x r b) sp"
| "subst x r (ConstructorF n fs sp)          = ConstructorF n (subst_fields x r fs) sp"
| "subst x r (SetLiteralF xs sp)             = SetLiteralF (subst_list x r xs) sp"
| "subst x r (MapLiteralF es sp)             = MapLiteralF (subst_entries x r es) sp"
| "subst x r (SetComprehensionF v d p sp)    =
     SetComprehensionF v (subst x r d) (if v = x then p else subst x r p) sp"
| "subst x r (SeqLiteralF xs sp)             = SeqLiteralF (subst_list x r xs) sp"
| "subst x r (MatchesF e pat sp)             = MatchesF (subst x r e) pat sp"
| "subst x r (SomeWrapF e sp)                = SomeWrapF (subst x r e) sp"
| "subst x r (TheF v d b sp)                 =
     TheF v (subst x r d) (if v = x then b else subst x r b) sp"
| "subst x r (QuantifierF q bs body sp)      =
     QuantifierF q (subst_bindings x r bs)
                   (if string_in_list x (qb_names bs) then body else subst x r body) sp"
| "subst _ _ (IntLitF n sp)                  = IntLitF n sp"
| "subst _ _ (FloatLitF n sp)                = FloatLitF n sp"
| "subst _ _ (StringLitF n sp)               = StringLitF n sp"
| "subst _ _ (BoolLitF v sp)                 = BoolLitF v sp"
| "subst _ _ (NoneLitF sp)                   = NoneLitF sp"
| "subst_list _ _ []                                   = []"
| "subst_list x r (e # es)                             = subst x r e # subst_list x r es"
| "subst_fields _ _ []                                 = []"
| "subst_fields x r (FieldAssignFull f v sp # fs)      =
     FieldAssignFull f (subst x r v) sp # subst_fields x r fs"
| "subst_entries _ _ []                                = []"
| "subst_entries x r (MapEntryFull k v sp # es)        =
     MapEntryFull (subst x r k) (subst x r v) sp # subst_entries x r es"
| "subst_bindings _ _ []                               = []"
| "subst_bindings x r (QuantifierBindingFull n d kk sp # bs) =
     QuantifierBindingFull n (subst x r d) kk sp # subst_bindings x r bs"

fun is_binder :: "expr \<Rightarrow> bool" where
  "is_binder (LetF _ _ _ _)               = True"
| "is_binder (QuantifierF _ _ _ _)        = True"
| "is_binder (LambdaF _ _ _)              = True"
| "is_binder (SetComprehensionF _ _ _ _)  = True"
| "is_binder (TheF _ _ _ _)               = True"
| "is_binder _                            = False"

fun is_call :: "expr \<Rightarrow> bool" where
  "is_call (CallF _ _ _) = True"
| "is_call _             = False"

text \<open>\<open>inline_calls\<close> is a pre-\<open>lower\<close> desugar that beta-reduces a call to a user
  function/predicate, substituting its arguments for the parameters in the body.
  \<open>capture_safe\<close> inlines only a \<^emph>\<open>simple\<close> body, on conditions that make the
  (non-renaming) \<open>subst\<close> sound: (i) binder-free and (ii) call-free, so substitution
  is a structural splice; (iii) every free variable of the body is a parameter, so it
  cannot reference \<^emph>\<open>and capture\<close> an outer or state name; and (iv) no argument
  mentions a parameter, so the sequential per-parameter substitution behaves
  simultaneously. Otherwise the \<open>CallF\<close> is left in place, falls outside \<open>lower\<close>, and
  the check routes best-effort. Under these conditions \<open>inline_calls\<close> preserves the
  reference semantics \<open>eval\<close> (lemma \<open>inline_calls_eval\<close>, theory \<open>Semantics_Inlining\<close>).
  It inlines one level; the Scala driver iterates to a fixpoint (capped), so nested
  non-recursive calls resolve and recursive ones stop.\<close>

definition capture_safe :: "expr \<Rightarrow> String.literal list \<Rightarrow> expr list \<Rightarrow> bool" where
  "capture_safe body params args \<equiv>
     distinct params
       \<and> (\<not> list_ex is_binder (allSubexprs body))
       \<and> (\<not> list_ex is_call (allSubexprs body))
       \<and> list_all (\<lambda>x. string_in_list x params) (free_vars body)
       \<and> list_all (\<lambda>a. list_all (\<lambda>p. \<not> string_in_list p (free_vars a)) params) args"

fun bind_params :: "String.literal list \<Rightarrow> expr list \<Rightarrow> expr \<Rightarrow> expr" where
  "bind_params (p # ps) (a # args) body = LetF p a (bind_params ps args body) None"
| "bind_params _ _ body = body"

fun inline_calls :: "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> expr \<Rightarrow> expr"
and inline_calls_list :: "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> expr list \<Rightarrow> expr list"
and inline_calls_fields :: "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> field_assign list \<Rightarrow> field_assign list"
and inline_calls_entries :: "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> map_entry list \<Rightarrow> map_entry list"
and inline_calls_bindings :: "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> quantifier_binding list \<Rightarrow> quantifier_binding list"
where
  "inline_calls fs ps (CallF callee args sp) =
     (let args' = inline_calls_list fs ps args
      in case callee of
           IdentifierF nm sp1 \<Rightarrow>
             (case List.find (\<lambda>f. fncName f = nm) fs of
                Some f \<Rightarrow>
                  (if length (fncParams f) = length args'
                        \<and> capture_safe (fncBody f) (map prmName (fncParams f)) args'
                     then bind_params (map prmName (fncParams f)) args' (fncBody f)
                     else CallF callee args' sp)
              | None \<Rightarrow>
                  (case List.find (\<lambda>q. prdName q = nm) ps of
                     Some pr \<Rightarrow>
                       (if length (prdParams pr) = length args'
                             \<and> capture_safe (prdBody pr) (map prmName (prdParams pr)) args'
                          then bind_params (map prmName (prdParams pr)) args' (prdBody pr)
                          else CallF callee args' sp)
                   | None \<Rightarrow> CallF callee args' sp))
         | _ \<Rightarrow> CallF (inline_calls fs ps callee) args' sp)"
| "inline_calls fs ps (BinaryOpF op l r sp)        = BinaryOpF op (inline_calls fs ps l) (inline_calls fs ps r) sp"
| "inline_calls fs ps (UnaryOpF op e sp)           = UnaryOpF op (inline_calls fs ps e) sp"
| "inline_calls fs ps (FieldAccessF b f sp)        = FieldAccessF (inline_calls fs ps b) f sp"
| "inline_calls fs ps (EnumAccessF b m sp)         = EnumAccessF (inline_calls fs ps b) m sp"
| "inline_calls fs ps (IndexF b i sp)              = IndexF (inline_calls fs ps b) (inline_calls fs ps i) sp"
| "inline_calls fs ps (PrimeF e sp)                = PrimeF (inline_calls fs ps e) sp"
| "inline_calls fs ps (PreF e sp)                  = PreF (inline_calls fs ps e) sp"
| "inline_calls fs ps (WithF b upds sp)            = WithF (inline_calls fs ps b) (inline_calls_fields fs ps upds) sp"
| "inline_calls fs ps (IfF c t e sp)               = IfF (inline_calls fs ps c) (inline_calls fs ps t) (inline_calls fs ps e) sp"
| "inline_calls fs ps (LetF v val body sp)         = LetF v (inline_calls fs ps val) (inline_calls fs ps body) sp"
| "inline_calls fs ps (LambdaF p b sp)             = LambdaF p (inline_calls fs ps b) sp"
| "inline_calls fs ps (ConstructorF n flds sp)     = ConstructorF n (inline_calls_fields fs ps flds) sp"
| "inline_calls fs ps (SetLiteralF xs sp)          = SetLiteralF (inline_calls_list fs ps xs) sp"
| "inline_calls fs ps (MapLiteralF es sp)          = MapLiteralF (inline_calls_entries fs ps es) sp"
| "inline_calls fs ps (SetComprehensionF v d p sp) = SetComprehensionF v (inline_calls fs ps d) (inline_calls fs ps p) sp"
| "inline_calls fs ps (SeqLiteralF xs sp)          = SeqLiteralF (inline_calls_list fs ps xs) sp"
| "inline_calls fs ps (MatchesF e pat sp)          = MatchesF (inline_calls fs ps e) pat sp"
| "inline_calls fs ps (SomeWrapF e sp)             = SomeWrapF (inline_calls fs ps e) sp"
| "inline_calls fs ps (TheF v d b sp)              = TheF v (inline_calls fs ps d) (inline_calls fs ps b) sp"
| "inline_calls fs ps (QuantifierF q bs body sp)   = QuantifierF q (inline_calls_bindings fs ps bs) (inline_calls fs ps body) sp"
| "inline_calls _ _ (IntLitF n sp)                 = IntLitF n sp"
| "inline_calls _ _ (FloatLitF n sp)               = FloatLitF n sp"
| "inline_calls _ _ (StringLitF n sp)              = StringLitF n sp"
| "inline_calls _ _ (BoolLitF v sp)                = BoolLitF v sp"
| "inline_calls _ _ (NoneLitF sp)                  = NoneLitF sp"
| "inline_calls _ _ (IdentifierF n sp)             = IdentifierF n sp"
| "inline_calls_list _ _ []                        = []"
| "inline_calls_list fs ps (e # es)                = inline_calls fs ps e # inline_calls_list fs ps es"
| "inline_calls_fields _ _ []                      = []"
| "inline_calls_fields fs ps (FieldAssignFull f v sp # rest) =
     FieldAssignFull f (inline_calls fs ps v) sp # inline_calls_fields fs ps rest"
| "inline_calls_entries _ _ []                     = []"
| "inline_calls_entries fs ps (MapEntryFull k v sp # rest) =
     MapEntryFull (inline_calls fs ps k) (inline_calls fs ps v) sp # inline_calls_entries fs ps rest"
| "inline_calls_bindings _ _ []                    = []"
| "inline_calls_bindings fs ps (QuantifierBindingFull n d kk sp # rest) =
     QuantifierBindingFull n (inline_calls fs ps d) kk sp # inline_calls_bindings fs ps rest"

text \<open>Phase 9\<kappa> (\<open>structuralIneligibility\<close>): classifies why an
  \<open>ensures\<close> clause is not structurally checkable (the structural layer
  emits one HTTP round-trip and asserts a single boolean over the
  response, so it cannot observe pre/prime/state values). Returns
  \<open>None\<close> when the clause IS structurally checkable
  (\<open>\<not> hasPrePrime e \<and> no fv \<in> stateFields \<and> some fv \<in> outputs\<close>),
  else the categorical reason. Lifted from
  \<open>testgen.Structural.referencesOnlyInputsAndOutputs\<close> +
  \<open>nonPureOutputReason\<close>; rendering of the reason text stays Scala-side.\<close>

datatype (plugins only: code size) structural_ineligibility =
    SceReferencesPrePrime
  | SceReferencesStateField
  | SceReferencesNoOutput

definition structuralIneligibility ::
  "expr \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow>
   structural_ineligibility option" where
  "structuralIneligibility e outputs stateFields \<equiv>
     (let fvs = free_vars e in
        if hasPrePrime e then Some SceReferencesPrePrime
        else if list_ex (\<lambda>n. List.member stateFields n) fvs
             then Some SceReferencesStateField
        else if \<not> list_ex (\<lambda>n. List.member outputs n) fvs
             then Some SceReferencesNoOutput
        else None)"

lemmas isPrePrime_code [code]  = isPrePrime.simps
lemmas hasPrePrime_code [code] = hasPrePrime_def

end
