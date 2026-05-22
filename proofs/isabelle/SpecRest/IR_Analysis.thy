theory IR_Analysis
  imports IR
begin

text \<open>Phase 9\<alpha> (small recognizers): \<open>isTrueLit\<close> matches the literal
  \<open>true\<close>; \<open>enumLiteralOf\<close> recognises an enum-member reference (via
  \<open>EnumAccessF\<close> or a bare \<open>IdentifierF\<close>). Replaces four \<open>case BoolLitF
  True _ \<Rightarrow> True\<close> copies in lint / testgen passes and three near-identical
  \<open>enumLiteralFor\<close> walkers in \<open>testgen.Behavioral\<close> /
  \<open>testgen.Stateful\<close>.\<close>

fun isTrueLit :: "expr_full \<Rightarrow> bool" where
  "isTrueLit (BoolLitF True _) = True"
| "isTrueLit _                 = False"

fun enumLiteralOf :: "expr_full \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "enumLiteralOf (EnumAccessF _ m _) ms = (if string_in_list m ms then Some m else None)"
| "enumLiteralOf (IdentifierF n _)   ms = (if string_in_list n ms then Some n else None)"
| "enumLiteralOf _                   _  = None"

text \<open>Phase 9\<alpha> (\<open>combineAnd\<close>): folds an \<open>expr_full list\<close> into a single
  left-associated AND-chain, with \<open>BoolLitF True None\<close> as the unit.
  Inverse of \<open>flattenAndAll\<close> modulo \<open>true\<close> identity. Replaces
  \<open>verify.Narration.combineConjuncts\<close> (which used \<open>foldLeft\<close> —
  left-associativity preserves byte-identical pretty-printed output). Uses
  a monomorphic accumulator to make the extracted Scala tail-recursive.\<close>

fun combineAnd_acc :: "expr_full \<Rightarrow> expr_full list \<Rightarrow> expr_full" where
  "combineAnd_acc acc []         = acc"
| "combineAnd_acc acc (x # rest) = combineAnd_acc (BinaryOpF BAnd acc x None) rest"

fun combineAnd :: "expr_full list \<Rightarrow> expr_full" where
  "combineAnd []          = BoolLitF True None"
| "combineAnd (x # rest)  = combineAnd_acc x rest"

text \<open>Phase 9\<beta> (\<open>decomposeAtom\<close>): canonical recognizer for a single
  atomic refinement constraint over \<open>value\<close>. Three consumers re-implement
  the recognizer (OpenAPI JSON-Schema, SQL CHECK, Hypothesis strategy
  synthesis) — this lifts the analysis half so each consumer becomes a
  pure renderer over \<open>refinement_atom\<close>. Compose with the existing
  \<open>flattenAnd\<close> to traverse \<open>BAnd\<close>-chains.\<close>

datatype (plugins only: code size) refinement_atom =
    RaLenCmp bin_op_full int
  | RaValueCmp bin_op_full int
  | RaMatches "String.literal"
  | RaMatchesIdent "String.literal" "String.literal"
  | RaPredCall "String.literal"
  | RaUnknown expr_full

fun isValueRef :: "expr_full \<Rightarrow> bool" where
  "isValueRef (IdentifierF n _) = (n = STR ''value'')"
| "isValueRef _                  = False"

fun isLenOfValue :: "expr_full \<Rightarrow> bool" where
  "isLenOfValue (CallF (IdentifierF n _) [arg] _) =
     (n = STR ''len'' \<and> isValueRef arg)"
| "isLenOfValue _ = False"

fun isRefinementCmp :: "bin_op_full \<Rightarrow> bool" where
  "isRefinementCmp BGe  = True"
| "isRefinementCmp BGt  = True"
| "isRefinementCmp BLe  = True"
| "isRefinementCmp BLt  = True"
| "isRefinementCmp BEq  = True"
| "isRefinementCmp BNeq = True"
| "isRefinementCmp _    = False"

definition decomposeAtom :: "expr_full \<Rightarrow> refinement_atom" where
  "decomposeAtom e \<equiv>
     (case e of
        MatchesF inner pat _ \<Rightarrow>
          (case inner of
             IdentifierF n _ \<Rightarrow>
               (if n = STR ''value'' then RaMatches pat else RaMatchesIdent n pat)
           | _ \<Rightarrow> RaUnknown e)
      | BinaryOpF op l rhs sp \<Rightarrow>
          (if \<not> isRefinementCmp op then RaUnknown e
           else (case rhs of
                   IntLitF n _ \<Rightarrow>
                     (if isLenOfValue l then RaLenCmp op n
                      else if isValueRef l then RaValueCmp op n
                      else RaUnknown e)
                 | _ \<Rightarrow> RaUnknown e))
      | CallF f args sp \<Rightarrow>
          (case (f, args) of
             (IdentifierF p _, [arg]) \<Rightarrow>
               (if isValueRef arg then RaPredCall p else RaUnknown e)
           | _ \<Rightarrow> RaUnknown e)
      | _ \<Rightarrow> RaUnknown e)"

text \<open>Phase 9\<gamma> (free-var helpers): monomorphic \<open>qb_names\<close>,
  \<open>remove_name\<close>, \<open>remove_names\<close> avoid the polymorphic \<open>map\<close>/\<open>filter\<close>
  HOFs that blow up Isabelle build wall-time when used inside large mutual
  \<open>fun\<close> declarations.\<close>

fun qb_names :: "quantifier_binding_full list \<Rightarrow> String.literal list" where
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
  in an \<open>expr_full\<close>, respecting binders (\<open>LetF\<close>, \<open>LambdaF\<close>,
  \<open>SetComprehensionF\<close>, \<open>TheF\<close>, \<open>QuantifierF\<close>). Replaces the 60-line
  hand-rolled walker \<open>testgen.Behavioral.containsStateRefIn\<close>.\<close>

fun free_vars :: "expr_full \<Rightarrow> String.literal list"
and free_vars_list :: "expr_full list \<Rightarrow> String.literal list"
and free_vars_fields :: "field_assign_full list \<Rightarrow> String.literal list"
and free_vars_entries :: "map_entry_full list \<Rightarrow> String.literal list"
and free_vars_bindings :: "quantifier_binding_full list \<Rightarrow> String.literal list"
where
  "free_vars (IdentifierF n _)           = [n]"
| "free_vars (BinaryOpF _ l r _)         = free_vars l @ free_vars r"
| "free_vars (UnaryOpF _ e _)            = free_vars e"
| "free_vars (FieldAccessF b _ _)        = free_vars b"
| "free_vars (EnumAccessF b _ _)         = free_vars b"
| "free_vars (IndexF b i _)              = free_vars b @ free_vars i"
| "free_vars (CallF c args _)            = free_vars c @ free_vars_list args"
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

fun hasPrePrime :: "expr_full \<Rightarrow> bool"
and hasPrePrime_list :: "expr_full list \<Rightarrow> bool"
and hasPrePrime_fields :: "field_assign_full list \<Rightarrow> bool"
and hasPrePrime_entries :: "map_entry_full list \<Rightarrow> bool"
and hasPrePrime_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "hasPrePrime (PrimeF _ _)                  = True"
| "hasPrePrime (PreF _ _)                    = True"
| "hasPrePrime (BinaryOpF _ l r _)           = (hasPrePrime l \<or> hasPrePrime r)"
| "hasPrePrime (UnaryOpF _ e _)              = hasPrePrime e"
| "hasPrePrime (FieldAccessF b _ _)          = hasPrePrime b"
| "hasPrePrime (EnumAccessF b _ _)           = hasPrePrime b"
| "hasPrePrime (IndexF b i _)                = (hasPrePrime b \<or> hasPrePrime i)"
| "hasPrePrime (CallF c args _)              = (hasPrePrime c \<or> hasPrePrime_list args)"
| "hasPrePrime (WithF b upds _)              = (hasPrePrime b \<or> hasPrePrime_fields upds)"
| "hasPrePrime (IfF c t e _)                 = (hasPrePrime c \<or> hasPrePrime t \<or> hasPrePrime e)"
| "hasPrePrime (LetF _ v b _)                = (hasPrePrime v \<or> hasPrePrime b)"
| "hasPrePrime (LambdaF _ b _)               = hasPrePrime b"
| "hasPrePrime (ConstructorF _ fs _)         = hasPrePrime_fields fs"
| "hasPrePrime (SetLiteralF xs _)            = hasPrePrime_list xs"
| "hasPrePrime (MapLiteralF es _)            = hasPrePrime_entries es"
| "hasPrePrime (SetComprehensionF _ d p _)   = (hasPrePrime d \<or> hasPrePrime p)"
| "hasPrePrime (SeqLiteralF xs _)            = hasPrePrime_list xs"
| "hasPrePrime (MatchesF x _ _)              = hasPrePrime x"
| "hasPrePrime (SomeWrapF x _)               = hasPrePrime x"
| "hasPrePrime (TheF _ d b _)                = (hasPrePrime d \<or> hasPrePrime b)"
| "hasPrePrime (QuantifierF _ bs body _)     = (hasPrePrime_bindings bs \<or> hasPrePrime body)"
| "hasPrePrime (IntLitF _ _)                 = False"
| "hasPrePrime (FloatLitF _ _)               = False"
| "hasPrePrime (StringLitF _ _)              = False"
| "hasPrePrime (BoolLitF _ _)                = False"
| "hasPrePrime (NoneLitF _)                  = False"
| "hasPrePrime (IdentifierF _ _)             = False"
| "hasPrePrime_list []                                       = False"
| "hasPrePrime_list (x # xs)                                 = (hasPrePrime x \<or> hasPrePrime_list xs)"
| "hasPrePrime_fields []                                     = False"
| "hasPrePrime_fields (FieldAssignFull _ v _ # fs)           = (hasPrePrime v \<or> hasPrePrime_fields fs)"
| "hasPrePrime_entries []                                    = False"
| "hasPrePrime_entries (MapEntryFull k v _ # es)             = (hasPrePrime k \<or> hasPrePrime v \<or> hasPrePrime_entries es)"
| "hasPrePrime_bindings []                                   = False"
| "hasPrePrime_bindings (QuantifierBindingFull _ d _ _ # bs) = (hasPrePrime d \<or> hasPrePrime_bindings bs)"

text \<open>Phase 9\<gamma> (\<open>subst\<close>): structural substitution of a free identifier
  by an expression. Stops at binders that shadow the substituted name —
  does NOT perform \<open>\<alpha>\<close>-renaming, matching the semantics of
  \<open>convention.dafny.Generator.rewriteValueRef\<close>. The caller is responsible
  for ensuring the replacement expression's free variables cannot be
  captured (typical use: replace \<open>value\<close> by
  \<open>FieldAccessF (IdentifierF p None) STR ''value'' None\<close> where \<open>p\<close> is not
  bound anywhere relevant).\<close>

fun subst :: "String.literal \<Rightarrow> expr_full \<Rightarrow> expr_full \<Rightarrow> expr_full"
and subst_list :: "String.literal \<Rightarrow> expr_full \<Rightarrow> expr_full list \<Rightarrow> expr_full list"
and subst_fields :: "String.literal \<Rightarrow> expr_full \<Rightarrow> field_assign_full list \<Rightarrow> field_assign_full list"
and subst_entries :: "String.literal \<Rightarrow> expr_full \<Rightarrow> map_entry_full list \<Rightarrow> map_entry_full list"
and subst_bindings :: "String.literal \<Rightarrow> expr_full \<Rightarrow> quantifier_binding_full list \<Rightarrow> quantifier_binding_full list"
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

text \<open>Phase 9\<delta> (lint TypeMismatch / L01): \<open>lit_class\<close> classifies an
  expression literal into a small ADT; \<open>litClass\<close> recognises which class
  (if any) an \<open>expr_full\<close> belongs to; \<open>binOpName\<close> renders a binary
  operator's user-facing name (used in diagnostic messages).
  \<open>describeLitClass\<close> renders the class as a noun for diagnostics.
  Replaces \<open>lint.TypeMismatch.LitClass\<close>, \<open>litClass\<close>, \<open>describe\<close>, and
  \<open>binOpName\<close> — the bulk of L01's pure analysis surface.\<close>

datatype (plugins only: code size) lit_class =
    LcNumeric | LcBool | LcStringLike | LcCollection | LcNone

fun litClass :: "expr_full \<Rightarrow> lit_class option" where
  "litClass (IntLitF _ _)     = Some LcNumeric"
| "litClass (FloatLitF _ _)   = Some LcNumeric"
| "litClass (BoolLitF _ _)    = Some LcBool"
| "litClass (StringLitF _ _)  = Some LcStringLike"
| "litClass (SetLiteralF _ _) = Some LcCollection"
| "litClass (MapLiteralF _ _) = Some LcCollection"
| "litClass (SeqLiteralF _ _) = Some LcCollection"
| "litClass (NoneLitF _)      = Some LcNone"
| "litClass _                 = None"

fun describeLitClass :: "lit_class \<Rightarrow> String.literal" where
  "describeLitClass LcNumeric    = STR ''numeric''"
| "describeLitClass LcBool       = STR ''boolean''"
| "describeLitClass LcStringLike = STR ''string''"
| "describeLitClass LcCollection = STR ''collection''"
| "describeLitClass LcNone       = STR ''none''"

fun binOpName :: "bin_op_full \<Rightarrow> String.literal" where
  "binOpName BAdd       = STR ''+''"
| "binOpName BSub       = STR ''-''"
| "binOpName BMul       = STR ''*''"
| "binOpName BDiv       = STR ''/''"
| "binOpName BLt        = STR ''<''"
| "binOpName BGt        = STR ''>''"
| "binOpName BLe        = STR ''<=''"
| "binOpName BGe        = STR ''>=''"
| "binOpName BAnd       = STR ''and''"
| "binOpName BOr        = STR ''or''"
| "binOpName BImplies   = STR ''implies''"
| "binOpName BIff       = STR ''iff''"
| "binOpName BIn        = STR ''in''"
| "binOpName BNotIn     = STR ''not in''"
| "binOpName BEq        = STR ''=''"
| "binOpName BNeq       = STR ''!=''"
| "binOpName BSubset    = STR ''subset''"
| "binOpName BUnion     = STR ''++''"
| "binOpName BIntersect = STR ''&''"
| "binOpName BDiff      = STR ''--''"

text \<open>Phase 9\<delta> (\<open>typeContainsNamed\<close>, \<open>exprContainsBoolLit\<close>): two
  structural predicates lifted from \<open>verify.alloy.Translator\<close>. The first
  asks whether a \<open>type_expr_full\<close> mentions a given named type anywhere
  in its structural unfolding (only descending into \<open>SetTypeF\<close> and
  \<open>OptionTypeF\<close>, matching the original walker's narrow scope). The
  second is a structural fold returning \<open>True\<close> iff a \<open>BoolLitF\<close> appears
  anywhere in the expression tree.\<close>

fun typeContainsNamed :: "String.literal \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "typeContainsNamed n (NamedTypeF m _)      = (n = m)"
| "typeContainsNamed n (SetTypeF inner _)    = typeContainsNamed n inner"
| "typeContainsNamed n (OptionTypeF inner _) = typeContainsNamed n inner"
| "typeContainsNamed _ _                     = False"

fun exprContainsBoolLit :: "expr_full \<Rightarrow> bool"
and exprContainsBoolLit_list :: "expr_full list \<Rightarrow> bool"
and exprContainsBoolLit_fields :: "field_assign_full list \<Rightarrow> bool"
and exprContainsBoolLit_entries :: "map_entry_full list \<Rightarrow> bool"
and exprContainsBoolLit_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "exprContainsBoolLit (BoolLitF _ _)              = True"
| "exprContainsBoolLit (BinaryOpF _ l r _)         = (exprContainsBoolLit l \<or> exprContainsBoolLit r)"
| "exprContainsBoolLit (UnaryOpF _ e _)            = exprContainsBoolLit e"
| "exprContainsBoolLit (FieldAccessF b _ _)        = exprContainsBoolLit b"
| "exprContainsBoolLit (EnumAccessF b _ _)         = exprContainsBoolLit b"
| "exprContainsBoolLit (IndexF b i _)              = (exprContainsBoolLit b \<or> exprContainsBoolLit i)"
| "exprContainsBoolLit (CallF c args _)            = (exprContainsBoolLit c \<or> exprContainsBoolLit_list args)"
| "exprContainsBoolLit (PrimeF e _)                = exprContainsBoolLit e"
| "exprContainsBoolLit (PreF e _)                  = exprContainsBoolLit e"
| "exprContainsBoolLit (WithF b upds _)            = (exprContainsBoolLit b \<or> exprContainsBoolLit_fields upds)"
| "exprContainsBoolLit (IfF c t e _)               = (exprContainsBoolLit c \<or> exprContainsBoolLit t \<or> exprContainsBoolLit e)"
| "exprContainsBoolLit (LetF _ v b _)              = (exprContainsBoolLit v \<or> exprContainsBoolLit b)"
| "exprContainsBoolLit (LambdaF _ b _)             = exprContainsBoolLit b"
| "exprContainsBoolLit (ConstructorF _ fs _)       = exprContainsBoolLit_fields fs"
| "exprContainsBoolLit (SetLiteralF xs _)          = exprContainsBoolLit_list xs"
| "exprContainsBoolLit (MapLiteralF es _)          = exprContainsBoolLit_entries es"
| "exprContainsBoolLit (SetComprehensionF _ d p _) = (exprContainsBoolLit d \<or> exprContainsBoolLit p)"
| "exprContainsBoolLit (SeqLiteralF xs _)          = exprContainsBoolLit_list xs"
| "exprContainsBoolLit (MatchesF x _ _)            = exprContainsBoolLit x"
| "exprContainsBoolLit (SomeWrapF x _)             = exprContainsBoolLit x"
| "exprContainsBoolLit (TheF _ d b _)              = (exprContainsBoolLit d \<or> exprContainsBoolLit b)"
| "exprContainsBoolLit (QuantifierF _ bs body _)   = (exprContainsBoolLit_bindings bs \<or> exprContainsBoolLit body)"
| "exprContainsBoolLit (IntLitF _ _)               = False"
| "exprContainsBoolLit (FloatLitF _ _)             = False"
| "exprContainsBoolLit (StringLitF _ _)            = False"
| "exprContainsBoolLit (NoneLitF _)                = False"
| "exprContainsBoolLit (IdentifierF _ _)           = False"
| "exprContainsBoolLit_list []                                                  = False"
| "exprContainsBoolLit_list (x # xs)                                            = (exprContainsBoolLit x \<or> exprContainsBoolLit_list xs)"
| "exprContainsBoolLit_fields []                                                = False"
| "exprContainsBoolLit_fields (FieldAssignFull _ v _ # fs)                      = (exprContainsBoolLit v \<or> exprContainsBoolLit_fields fs)"
| "exprContainsBoolLit_entries []                                               = False"
| "exprContainsBoolLit_entries (MapEntryFull k v _ # es)                        = (exprContainsBoolLit k \<or> exprContainsBoolLit v \<or> exprContainsBoolLit_entries es)"
| "exprContainsBoolLit_bindings []                                              = False"
| "exprContainsBoolLit_bindings (QuantifierBindingFull _ d _ _ # bs)            = (exprContainsBoolLit d \<or> exprContainsBoolLit_bindings bs)"

text \<open>Phase 9\<delta> (Narration conflict helpers): pure pattern matches lifted
  from \<open>verify.Narration\<close>. \<open>isComp\<close>/\<open>isLowBound\<close>/\<open>isStrictBound\<close> classify
  a \<open>bin_op_full\<close>; \<open>mirrorBinOp\<close> swaps a comparison's direction (for the
  \<open>IntLit cmp Identifier\<close> case); \<open>rangeOf\<close> extracts a
  \<open>(name, op, bound)\<close> triple from a comparison-against-literal shape;
  \<open>conflicts\<close> detects whether two bounds on the same identifier carve out
  disjoint ranges. Used by the contradictory-invariants diagnostic.\<close>

fun isComp :: "bin_op_full \<Rightarrow> bool" where
  "isComp BGe = True"
| "isComp BGt = True"
| "isComp BLe = True"
| "isComp BLt = True"
| "isComp _   = False"

fun isLowBound :: "bin_op_full \<Rightarrow> bool" where
  "isLowBound BGe = True"
| "isLowBound BGt = True"
| "isLowBound _   = False"

fun isStrictBound :: "bin_op_full \<Rightarrow> bool" where
  "isStrictBound BGt = True"
| "isStrictBound BLt = True"
| "isStrictBound _   = False"

fun mirrorBinOp :: "bin_op_full \<Rightarrow> bin_op_full" where
  "mirrorBinOp BGe    = BLe"
| "mirrorBinOp BLe    = BGe"
| "mirrorBinOp BGt    = BLt"
| "mirrorBinOp BLt    = BGt"
| "mirrorBinOp other  = other"

definition rangeOf :: "expr_full \<Rightarrow> (String.literal \<times> bin_op_full \<times> int) option" where
  "rangeOf e \<equiv>
     (case e of
        BinaryOpF op l r _ \<Rightarrow>
          (case (l, r) of
             (IdentifierF n _, IntLitF v _) \<Rightarrow>
               (if isComp op then Some (n, op, v) else None)
           | (IntLitF v _, IdentifierF n _) \<Rightarrow>
               (if isComp op then Some (n, mirrorBinOp op, v) else None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

text \<open>Integer-discrete bound normalization: \<open>x > 3\<close> is satisfied by integers
  \<open>x \<ge> 4\<close>, and \<open>x < 4\<close> by \<open>x \<le> 3\<close>. Bumping strict bounds inward by 1 turns
  the disjointness check into a single \<open>low_eff > high_eff\<close> comparison and
  catches contradictions like \<open>x > 3 \<and> x < 4\<close> (no integer satisfies both)
  that a dense-order check would miss.\<close>

fun lowBoundEffective :: "bin_op_full \<Rightarrow> int \<Rightarrow> int" where
  "lowBoundEffective BGt n = n + 1"
| "lowBoundEffective _   n = n"

fun highBoundEffective :: "bin_op_full \<Rightarrow> int \<Rightarrow> int" where
  "highBoundEffective BLt n = n - 1"
| "highBoundEffective _   n = n"

fun conflicts :: "bin_op_full \<Rightarrow> int \<Rightarrow> bin_op_full \<Rightarrow> int \<Rightarrow> bool" where
  "conflicts aOp aB bOp bB =
     (if isLowBound aOp \<and> \<not> isLowBound bOp
      then lowBoundEffective aOp aB > highBoundEffective bOp bB
      else if \<not> isLowBound aOp \<and> isLowBound bOp
      then lowBoundEffective bOp bB > highBoundEffective aOp aB
      else False)"

text \<open>Phase 9\<epsilon> (small recognizers, scattered consumers):
  \<open>negate\<close> — partial logical negation of comparison-shaped exprs (used
  by testgen guard satisfier); \<open>isLenOrCardOf\<close> — extracts the bare
  identifier inside \<open>len(x)\<close> or \<open>|x|\<close>; \<open>isLiteral\<close> — narrow
  literal recognizer (Int/Float/String only, distinct from \<open>isLitFull\<close>
  which also accepts Bool/None); \<open>extractFieldName\<close> — recognizer for
  \<open>self.field\<close> or bare \<open>field\<close> (SQL CHECK emission);
  \<open>enumLitName\<close> — bare name extractor for EnumAccess/Identifier
  (distinct from \<open>enumLiteralOf\<close> which filters by enum-values list);
  \<open>isMapType\<close>, \<open>isEntityType\<close>, \<open>sameNamedType\<close> — trivial
  type-shape predicates.\<close>

definition negate :: "expr_full \<Rightarrow> expr_full option" where
  "negate e \<equiv>
     (case e of
        UnaryOpF UNot inner _    \<Rightarrow> Some inner
      | BinaryOpF op l r sp \<Rightarrow>
          (case op of
             BGt  \<Rightarrow> Some (BinaryOpF BLe  l r sp)
           | BGe  \<Rightarrow> Some (BinaryOpF BLt  l r sp)
           | BLt  \<Rightarrow> Some (BinaryOpF BGe  l r sp)
           | BLe  \<Rightarrow> Some (BinaryOpF BGt  l r sp)
           | BEq  \<Rightarrow> Some (BinaryOpF BNeq l r sp)
           | BNeq \<Rightarrow> Some (BinaryOpF BEq  l r sp)
           | _    \<Rightarrow> None)
      | _ \<Rightarrow> None)"

definition isLenOrCardOf :: "expr_full \<Rightarrow> String.literal option" where
  "isLenOrCardOf e \<equiv>
     (case e of
        UnaryOpF UCardinality (IdentifierF n _) _ \<Rightarrow> Some n
      | CallF (IdentifierF f _) [IdentifierF n _] _ \<Rightarrow>
          (if f = STR ''len'' then Some n else None)
      | _ \<Rightarrow> None)"

fun isLiteral :: "expr_full \<Rightarrow> bool" where
  "isLiteral (IntLitF _ _)    = True"
| "isLiteral (FloatLitF _ _)  = True"
| "isLiteral (StringLitF _ _) = True"
| "isLiteral _                = False"

fun extractFieldName :: "expr_full \<Rightarrow> String.literal option" where
  "extractFieldName (FieldAccessF (IdentifierF s _) name _) =
     (if s = STR ''self'' then Some name else None)"
| "extractFieldName (IdentifierF name _) = Some name"
| "extractFieldName _ = None"

fun enumLitName :: "expr_full \<Rightarrow> String.literal option" where
  "enumLitName (EnumAccessF _ m _) = Some m"
| "enumLitName (IdentifierF n _)   = Some n"
| "enumLitName _                   = None"

fun isMapType :: "type_expr_full \<Rightarrow> bool" where
  "isMapType (MapTypeF _ _ _) = True"
| "isMapType _                = False"

fun isEntityType :: "type_expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isEntityType (NamedTypeF n _) name = (n = name)"
| "isEntityType _ _                   = False"

fun sameNamedType :: "type_expr_full \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "sameNamedType (NamedTypeF a _) (NamedTypeF b _) = (a = b)"
| "sameNamedType _ _                                = False"

text \<open>Phase 9\<zeta> (semantic classifiers from \<open>convention.Classify\<close>):
  \<open>isLeafValue\<close> — literals + bare identifier + enum access (the closed
  set of expr forms that read no state and call no function);
  \<open>isPureRead\<close> — recursive: identifier / literal / enum / \<open>pre(...)\<close> /
  field-access / index where every subterm is itself pure-read;
  \<open>isCardinalityRhs\<close> — \<open>|x|\<close> / \<open>|pre(x)|\<close> / arithmetic-on-them shape
  used by the convention classifier's cardinality-frame inference;
  \<open>relationTargetsEntity\<close> — type predicate for \<open>Relation(_, _, NamedType
  e)\<close> or bare \<open>NamedType e\<close> (lifted from \<open>testgen.Behavioral\<close>);
  \<open>extractKeySet\<close> / \<open>extractMapEntries\<close> — set / map literal extractors
  used by the Z3 frame translator.\<close>

fun isLeafValue :: "expr_full \<Rightarrow> bool" where
  "isLeafValue (IntLitF _ _)      = True"
| "isLeafValue (FloatLitF _ _)    = True"
| "isLeafValue (StringLitF _ _)   = True"
| "isLeafValue (BoolLitF _ _)     = True"
| "isLeafValue (NoneLitF _)       = True"
| "isLeafValue (IdentifierF _ _)  = True"
| "isLeafValue (EnumAccessF _ _ _) = True"
| "isLeafValue _                  = False"

fun (sequential) isPureRead :: "expr_full \<Rightarrow> bool" where
  "isPureRead (PreF inner _)          = isPureRead inner"
| "isPureRead (IndexF base idx _)     = (isPureRead base \<and> isPureRead idx)"
| "isPureRead (FieldAccessF base _ _) = isPureRead base"
| "isPureRead e                       = isLeafValue e"

fun relationTargetsEntity :: "type_expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "relationTargetsEntity (RelationTypeF _ _ (NamedTypeF n _) _) entity = (n = entity)"
| "relationTargetsEntity (NamedTypeF n _) entity                       = (n = entity)"
| "relationTargetsEntity _ _                                            = False"

fun extractKeySetEntries :: "map_entry_full list \<Rightarrow> expr_full list" where
  "extractKeySetEntries []                          = []"
| "extractKeySetEntries (MapEntryFull k _ _ # rest) = k # extractKeySetEntries rest"

fun extractKeySet :: "expr_full \<Rightarrow> expr_full list option" where
  "extractKeySet (SetLiteralF elements _) = Some elements"
| "extractKeySet (MapLiteralF entries _)  = Some (extractKeySetEntries entries)"
| "extractKeySet _                        = None"

fun extractMapEntriesPairs :: "map_entry_full list \<Rightarrow> (expr_full \<times> expr_full) list" where
  "extractMapEntriesPairs []                          = []"
| "extractMapEntriesPairs (MapEntryFull k v _ # rest) = (k, v) # extractMapEntriesPairs rest"

fun extractMapEntries :: "expr_full \<Rightarrow> (expr_full \<times> expr_full) list option" where
  "extractMapEntries (MapLiteralF entries _) = Some (extractMapEntriesPairs entries)"
| "extractMapEntries _                       = None"

text \<open>Phase 9\<eta> (cardinality + key-existence recognizers):
  \<open>isCardinalityRhs\<close> — \<open>|x|\<close>/\<open>|pre(x)|\<close> with optional \<open>± IntLit\<close>
  peeling (uses a helper \<open>stripAddSubIntLit\<close> stripper + a leaf
  \<open>case\<close>-based check to keep elaboration time bounded — the obvious
  recursive \<open>fun\<close> formulation costs ~42 s of pattern overlap analysis);
  \<open>isKeyExistsConj\<close> — recognizer for the \<open>input \<in> state\<close> shape used
  by stateful test seeding.\<close>

fun (sequential) stripAddSubIntLit :: "expr_full \<Rightarrow> expr_full" where
  "stripAddSubIntLit (BinaryOpF BAdd inner (IntLitF _ _) _) = stripAddSubIntLit inner"
| "stripAddSubIntLit (BinaryOpF BSub inner (IntLitF _ _) _) = stripAddSubIntLit inner"
| "stripAddSubIntLit e = e"

definition isCardinalityRhs :: "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isCardinalityRhs e n \<equiv>
     (case stripAddSubIntLit e of
        UnaryOpF UCardinality inner _ \<Rightarrow>
          (case inner of
             IdentifierF m _ \<Rightarrow> m = n
           | PreF (IdentifierF m _) _ \<Rightarrow> m = n
           | _ \<Rightarrow> False)
      | _ \<Rightarrow> False)"

definition isKeyExistsConj ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isKeyExistsConj c inputName stateName \<equiv>
     (case c of
        BinaryOpF BIn (IdentifierF i _) (IdentifierF s _) _ \<Rightarrow>
          i = inputName \<and> s = stateName
      | _ \<Rightarrow> False)"

end
