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

text \<open>\<open>L06 — CircularPredicate\<close>: pure HOL DFS cycle-finder. Two parts:

  \<^item> \<open>collectCallNames\<close>: filter expression walker — returns the names
    of every \<open>CallF (IdentifierF n _) _\<close> callee whose name is in the
    supplied filter list. Used Scala-side to build the edge AList.
  \<^item> \<open>findCycles\<close>: the full DFS algorithm — pure HOL, terminating by
    structural recursion on an explicit fuel counter. The fuel is
    \<open>length nodes ^ 2 + length nodes + 1\<close> at the entry point — a safe
    upper bound on the total number of \<open>dfsNode\<close> calls (each node is
    \<^emph>\<open>processed\<close> at most once but may trigger up to \<open>length nodes\<close>
    \<^emph>\<open>visited\<close>-check early returns per edge, so quadratic suffices).
    All cycle dedupe / stack maintenance / span-aware emission stays
    in HOL; Scala iterates the resulting cycles and formats
    diagnostics.\<close>

fun collectCallNames :: "expr_full \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
and collectCallNames_list :: "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
and collectCallNames_fields :: "field_assign_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
and collectCallNames_entries :: "map_entry_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
and collectCallNames_bindings :: "quantifier_binding_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
where
  "collectCallNames (CallF (IdentifierF n _) args sp) filt =
     (if List.member filt n
      then n # collectCallNames_list args filt
      else collectCallNames_list args filt)"
| "collectCallNames (CallF c args _) filt =
     collectCallNames c filt @ collectCallNames_list args filt"
| "collectCallNames (BinaryOpF _ l r _) filt = collectCallNames l filt @ collectCallNames r filt"
| "collectCallNames (UnaryOpF _ e _) filt = collectCallNames e filt"
| "collectCallNames (FieldAccessF b _ _) filt = collectCallNames b filt"
| "collectCallNames (EnumAccessF b _ _) filt = collectCallNames b filt"
| "collectCallNames (IndexF b i _) filt = collectCallNames b filt @ collectCallNames i filt"
| "collectCallNames (PrimeF e _) filt = collectCallNames e filt"
| "collectCallNames (PreF e _) filt = collectCallNames e filt"
| "collectCallNames (WithF b upds _) filt = collectCallNames b filt @ collectCallNames_fields upds filt"
| "collectCallNames (IfF c t e _) filt = collectCallNames c filt @ collectCallNames t filt @ collectCallNames e filt"
| "collectCallNames (LetF _ val body _) filt = collectCallNames val filt @ collectCallNames body filt"
| "collectCallNames (LambdaF _ b _) filt = collectCallNames b filt"
| "collectCallNames (ConstructorF _ fs _) filt = collectCallNames_fields fs filt"
| "collectCallNames (SetLiteralF xs _) filt = collectCallNames_list xs filt"
| "collectCallNames (MapLiteralF es _) filt = collectCallNames_entries es filt"
| "collectCallNames (SetComprehensionF _ d p _) filt = collectCallNames d filt @ collectCallNames p filt"
| "collectCallNames (SeqLiteralF xs _) filt = collectCallNames_list xs filt"
| "collectCallNames (MatchesF x _ _) filt = collectCallNames x filt"
| "collectCallNames (SomeWrapF x _) filt = collectCallNames x filt"
| "collectCallNames (TheF _ d b _) filt = collectCallNames d filt @ collectCallNames b filt"
| "collectCallNames (QuantifierF _ bs body _) filt =
     collectCallNames_bindings bs filt @ collectCallNames body filt"
| "collectCallNames (IdentifierF _ _) _ = []"
| "collectCallNames (IntLitF _ _) _ = []"
| "collectCallNames (FloatLitF _ _) _ = []"
| "collectCallNames (StringLitF _ _) _ = []"
| "collectCallNames (BoolLitF _ _) _ = []"
| "collectCallNames (NoneLitF _) _ = []"
| "collectCallNames_list [] _ = []"
| "collectCallNames_list (x # xs) filt = collectCallNames x filt @ collectCallNames_list xs filt"
| "collectCallNames_fields [] _ = []"
| "collectCallNames_fields (FieldAssignFull _ v _ # fs) filt =
     collectCallNames v filt @ collectCallNames_fields fs filt"
| "collectCallNames_entries [] _ = []"
| "collectCallNames_entries (MapEntryFull k v _ # es) filt =
     collectCallNames k filt @ collectCallNames v filt @ collectCallNames_entries es filt"
| "collectCallNames_bindings [] _ = []"
| "collectCallNames_bindings (QuantifierBindingFull _ d _ _ # bs) filt =
     collectCallNames d filt @ collectCallNames_bindings bs filt"

text \<open>DFS state — uses lists everywhere so the extracted Scala stays
  on plain List operations (no HOL-Library Set imports). Membership is
  via \<open>List.member\<close>; cycle dedupe compares cycles up to permutation
  by checking both directions of inclusion (matches the original
  \<open>cyc.toSet ∈ seenCycles\<close> Scala check).\<close>

record dfs_state =
  onStack    :: "String.literal list"
  visited    :: "String.literal list"
  stack      :: "String.literal list"
  cycles     :: "String.literal list list"
  seenCycles :: "String.literal list list"

definition initDfsState :: dfs_state where
  "initDfsState = \<lparr>onStack = [], visited = [], stack = [],
                    cycles = [], seenCycles = []\<rparr>"

fun lookupEdges ::
  "String.literal \<Rightarrow> (String.literal \<times> String.literal list) list \<Rightarrow> String.literal list"
where
  "lookupEdges _ [] = []"
| "lookupEdges n ((k, v) # rest) = (if k = n then v else lookupEdges n rest)"

fun listRemoveAll ::
  "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
where
  "listRemoveAll _ [] = []"
| "listRemoveAll x (y # ys) =
     (if x = y then listRemoveAll x ys else y # listRemoveAll x ys)"

fun listIsSubset ::
  "String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "listIsSubset [] _ = True"
| "listIsSubset (x # xs) ys = (List.member ys x \<and> listIsSubset xs ys)"

definition cycleSetEq ::
  "String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "cycleSetEq c1 c2 = (listIsSubset c1 c2 \<and> listIsSubset c2 c1)"

fun cycleAlreadySeen ::
  "String.literal list \<Rightarrow> String.literal list list \<Rightarrow> bool"
where
  "cycleAlreadySeen _ [] = False"
| "cycleAlreadySeen c (s # rest) =
     (cycleSetEq c s \<or> cycleAlreadySeen c rest)"

fun sliceFromNode ::
  "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal list"
where
  "sliceFromNode _ [] = []"
| "sliceFromNode n (x # xs) =
     (if x = n then x # xs else sliceFromNode n xs)"

text \<open>Mutual fuel-bounded DFS. Termination is structural on the fuel
  argument (decreases by 1 on each recursive call). The driver supplies
  fuel \<open>length nodes ^ 2 + length nodes + 1\<close> — every node is processed
  at most once, but visited-check early returns can fire once per edge,
  so quadratic in the node count is a safe upper bound.

  Pattern ordering matters for fuel-zero shortcuts: the \<open>0\<close> clauses
  must precede the \<open>Suc f\<close> clauses, and the \<open>[]\<close>-children clause
  must precede the cons clause inside \<open>dfsChildrenFuel\<close>.\<close>

function dfsNodeFuel ::
  "nat \<Rightarrow> (String.literal \<times> String.literal list) list \<Rightarrow>
   String.literal \<Rightarrow> dfs_state \<Rightarrow> dfs_state"
and dfsChildrenFuel ::
  "nat \<Rightarrow> (String.literal \<times> String.literal list) list \<Rightarrow>
   String.literal list \<Rightarrow> dfs_state \<Rightarrow> dfs_state"
where
  "dfsNodeFuel fuel edges n state =
     (if fuel = 0 then state
      else if List.member (onStack state) n then
        (let cyc = sliceFromNode n (stack state)
         in if cyc = [] then state
            else if cycleAlreadySeen cyc (seenCycles state) then state
            else state\<lparr>cycles := cycles state @ [cyc],
                       seenCycles := cyc # seenCycles state\<rparr>)
      else if List.member (visited state) n then state
      else
        (let state1 = state\<lparr>onStack := n # onStack state,
                            visited := n # visited state,
                            stack := stack state @ [n]\<rparr>;
             children = lookupEdges n edges;
             state2   = dfsChildrenFuel (fuel - 1) edges children state1
         in state2\<lparr>onStack := listRemoveAll n (onStack state2),
                   stack := butlast (stack state2)\<rparr>))"
| "dfsChildrenFuel fuel edges [] state = state"
| "dfsChildrenFuel fuel edges (c # rest) state =
     (if fuel = 0 then state
      else dfsChildrenFuel (fuel - 1) edges rest
             (dfsNodeFuel (fuel - 1) edges c state))"
  by pat_completeness auto

termination
  by (relation "measure (\<lambda>x. case x of
                                Inl (fuel, _, _, _) \<Rightarrow> fuel
                              | Inr (fuel, _, _, _) \<Rightarrow> fuel)")
     auto

fun findCyclesAux ::
  "nat \<Rightarrow> (String.literal \<times> String.literal list) list \<Rightarrow>
   String.literal list \<Rightarrow> dfs_state \<Rightarrow> dfs_state"
where
  "findCyclesAux _ _ [] state = state"
| "findCyclesAux fuel edges (n # rest) state =
     (let state1 = state\<lparr>onStack := [], stack := []\<rparr>;
          state2 = dfsNodeFuel fuel edges n state1
      in findCyclesAux fuel edges rest state2)"

definition findCycles ::
  "String.literal list \<Rightarrow> (String.literal \<times> String.literal list) list
   \<Rightarrow> String.literal list list"
where
  "findCycles nodes edges =
     cycles (findCyclesAux (length nodes * length nodes + length nodes + 1)
                            edges nodes initDfsState)"

lemmas operationMissingEnsures_code [code] = operationMissingEnsures.simps
lemmas collectExprNames_code [code]          = collectExprNames.simps
lemmas collectTypeNames_code [code]          = collectTypeNames.simps
lemmas walkUndefinedExpr_code [code]         = walkUndefinedExpr.simps
lemmas collectCallNames_code [code]          = collectCallNames.simps
lemmas lookupEdges_code [code]               = lookupEdges.simps
lemmas listRemoveAll_code [code]             = listRemoveAll.simps
lemmas listIsSubset_code [code]              = listIsSubset.simps
lemmas cycleSetEq_code [code]                = cycleSetEq_def
lemmas cycleAlreadySeen_code [code]          = cycleAlreadySeen.simps
lemmas sliceFromNode_code [code]             = sliceFromNode.simps
lemmas dfsNodeFuel_code [code]               = dfsNodeFuel.simps dfsChildrenFuel.simps
lemmas findCyclesAux_code [code]             = findCyclesAux.simps
lemmas findCycles_code [code]                = findCycles_def

end
