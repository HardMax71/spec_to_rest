theory IR_Helpers
  imports IR
begin

text \<open>Phase 9ww: per-clause matchers lifted off Scala
  \<open>convention.ExprAnalysis\<close>. \<open>flattenEnsures\<close> already breaks \<open>\<and>\<close>-chains
  so each matcher pattern-matches the top-level clause head and returns
  hits; the consumer composes \<open>concat (map matcher (flattenEnsures es))\<close>.
  No expr_full walker needed (Scala keeps the AST walk for the few
  callers that need it — \<open>collectPrimedIdentifiers\<close>,
  \<open>collectWithFields\<close>, \<open>collectFieldAccessNames\<close> — to avoid a
  polymorphic-HOF walker that blows up Isabelle build wall-time).\<close>

definition preservedRelationOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "preservedRelationOf stateFields e \<equiv>
     (case e of
        BinaryOpF BEq (PrimeF (IdentifierF l _) _) (IdentifierF r _) _ \<Rightarrow>
          (if l = r \<and> string_in_list l stateFields then [l] else [])
      | _ \<Rightarrow> [])"

definition collectPreservedRelations ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "collectPreservedRelations es stateFields \<equiv>
     remdups (List.concat (map (preservedRelationOf stateFields) (flattenEnsures es)))"

fun containsPreInPlusChain ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "containsPreInPlusChain (PreF (IdentifierF n _) _) field = (n = field)"
| "containsPreInPlusChain (BinaryOpF BAdd l r _) field =
     (containsPreInPlusChain l field \<or> containsPreInPlusChain r field)"
| "containsPreInPlusChain _ _ = False"

definition createPatternOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "createPatternOf stateFields e \<equiv>
     (case e of
        BinaryOpF BEq (PrimeF (IdentifierF name _) _) rhs _ \<Rightarrow>
          (case rhs of
             BinaryOpF BAdd _ _ _ \<Rightarrow>
               (if string_in_list name stateFields
                   \<and> containsPreInPlusChain rhs name
                then [name] else [])
           | _ \<Rightarrow> [])
      | _ \<Rightarrow> [])"

definition detectCreatePattern ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "detectCreatePattern es stateFields \<equiv>
     (case List.concat (map (createPatternOf stateFields) (flattenEnsures es)) of
        []       \<Rightarrow> None
      | (x # _)  \<Rightarrow> Some x)"

definition deletePatternOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "deletePatternOf stateFields e \<equiv>
     (case e of
        BinaryOpF BNotIn _ (PrimeF (IdentifierF n _) _) _ \<Rightarrow>
          (if string_in_list n stateFields then [n] else [])
      | _ \<Rightarrow> [])"

definition detectDeletePattern ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "detectDeletePattern es stateFields \<equiv>
     (case List.concat (map (deletePatternOf stateFields) (flattenEnsures es)) of
        []       \<Rightarrow> None
      | (x # _)  \<Rightarrow> Some x)"

definition keyExistsInRequiresOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "keyExistsInRequiresOf stateFields e \<equiv>
     (case e of
        BinaryOpF BIn _ (IdentifierF n _) _ \<Rightarrow>
          (if string_in_list n stateFields then [n] else [])
      | _ \<Rightarrow> [])"

definition detectKeyExistsInRequires ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "detectKeyExistsInRequires requires stateFields \<equiv>
     remdups (List.concat
       (map (keyExistsInRequiresOf stateFields) (flattenEnsures requires)))"

fun resolveWithBase :: "expr_full \<Rightarrow> String.literal option" where
  "resolveWithBase (IdentifierF _ _) = None"
| "resolveWithBase (IndexF (PreF (IdentifierF n _) _) _ _) = Some n"
| "resolveWithBase (IndexF (IdentifierF n _) _ _)          = Some n"
| "resolveWithBase (IndexF base _ _)                       = rootIdentifier base"
| "resolveWithBase other                                   = rootIdentifier other"

fun fieldAssignName :: "field_assign_full \<Rightarrow> String.literal" where
  "fieldAssignName (FieldAssignFull n _ _) = n"

datatype (plugins only: code size) with_info_full =
  WithInfoFull "String.literal list" "String.literal option"

fun withInfoFieldNames :: "with_info_full \<Rightarrow> String.literal list" where
  "withInfoFieldNames (WithInfoFull fs _) = fs"

fun withInfoBaseIdentifier :: "with_info_full \<Rightarrow> String.literal option" where
  "withInfoBaseIdentifier (WithInfoFull _ b) = b"

text \<open>Phase 9ww: \<open>collectExprInfo\<close> is the unified expr_full walker
  that collects, in a single pass, every datum the classifier /
  diagnostic layers need from an AST: primed identifiers, field-access
  names, and with-clause info. Returning a tuple lets three logically
  distinct collectors share one 5-way mutual \<open>fun\<close> instead of three
  copies; that cuts Isabelle build wall-time roughly 3x vs split walkers
  (mutual \<open>fun\<close>'s meta-theory simp / induct / elim rules scales
  superlinearly per declaration, so one big walker is cheaper than
  three).\<close>

text \<open>Streamlined: previously this section had a 5-way mutual \<open>fun\<close>
  (\<open>collectExprInfo\<close>) returning a 3-tuple, with three consumers each
  extracting one field — the doc-original rationale was build-time
  (\<open>fun\<close>'s meta-theory simp / induct rules scale superlinearly per
  declaration). After hoisting \<open>allSubexprs\<close> into \<open>IR.thy\<close> we no
  longer pay the mutual-fun overhead per walker: each consumer is now
  a one-line composition over the shared \<open>allSubexprs\<close> enumeration
  with a tiny per-node selector.\<close>

fun primedIdSelect :: "expr_full \<Rightarrow> String.literal list" where
  "primedIdSelect (PrimeF inner _) =
     (case rootIdentifier inner of None \<Rightarrow> [] | Some n \<Rightarrow> [n])"
| "primedIdSelect _ = []"

fun fieldAccessNameSelect :: "expr_full \<Rightarrow> String.literal list" where
  "fieldAccessNameSelect (FieldAccessF _ n _) = [n]"
| "fieldAccessNameSelect _ = []"

fun identifierNameSelect :: "expr_full \<Rightarrow> String.literal list" where
  "identifierNameSelect (IdentifierF n _) = [n]"
| "identifierNameSelect _ = []"

fun withInfoSelect :: "expr_full \<Rightarrow> with_info_full list" where
  "withInfoSelect (WithF base ups _) =
     [WithInfoFull (map fieldAssignName ups) (resolveWithBase base)]"
| "withInfoSelect _ = []"

definition collectPrimedIdentifiers ::
  "expr_full list \<Rightarrow> String.literal list" where
  "collectPrimedIdentifiers es =
     remdups (concat (map (\<lambda>e. concat (map primedIdSelect (allSubexprs e))) es))"

definition collectFieldAccessNames :: "expr_full \<Rightarrow> String.literal list" where
  "collectFieldAccessNames e =
     remdups (concat (map fieldAccessNameSelect (allSubexprs e)))"

definition collectIdentifierNames :: "expr_full \<Rightarrow> String.literal list" where
  "collectIdentifierNames e =
     remdups (concat (map identifierNameSelect (allSubexprs e)))"

definition collectWithFields ::
  "expr_full list \<Rightarrow> with_info_full option" where
  "collectWithFields es =
     (case concat (map (\<lambda>e. concat (map withInfoSelect (allSubexprs e))) es) of
        []      \<Rightarrow> None
      | (x # _) \<Rightarrow> Some x)"

fun isInputCollectionType :: "type_expr_full \<Rightarrow> bool" where
  "isInputCollectionType (SetTypeF _ _)   = True"
| "isInputCollectionType (SeqTypeF _ _)   = True"
| "isInputCollectionType (MapTypeF _ _ _) = True"
| "isInputCollectionType _                = False"

fun paramTypeFull :: "param_decl_full \<Rightarrow> type_expr_full" where
  "paramTypeFull (ParamDeclFull _ t _) = t"

definition countFilterParams :: "param_decl_full list \<Rightarrow> nat" where
  "countFilterParams ps \<equiv>
     length (filter (\<lambda>p. case paramTypeFull p of OptionTypeF _ _ \<Rightarrow> True | _ \<Rightarrow> False) ps)"

definition hasCollectionInput :: "param_decl_full list \<Rightarrow> bool" where
  "hasCollectionInput ps \<equiv> list_ex (\<lambda>p. isInputCollectionType (paramTypeFull p)) ps"

fun typeName :: "type_expr_full \<Rightarrow> String.literal option" where
  "typeName (NamedTypeF n _) = Some n"
| "typeName _                = None"

text \<open>Phase 9e: \<open>flattenInheritance\<close> resolves \<open>entity Child extends
  Base\<close> chains into self-contained entities so every downstream consumer
  (schema, model emitters, Alloy / Dafny, the verifier) sees a single
  flattened entity. Order is root-first then own; a same-named child field
  shadows the inherited one *in the inherited position* (LinkedHashMap
  upsert semantics). The visited list is seeded with the entity itself, so a
  malformed \<open>extends\<close> cycle collapses to no inheritance instead of looping
  — a structural guard, no exception, mirroring alias resolution. \<open>chain_up\<close>
  carries a \<open>fuel\<close> bounded by the entity count: a simple path of distinct
  names cannot exceed it, and the visited-list guard always triggers first
  on a cycle, so the fuel arm is unreachable on real input and only serves
  \<open>fun\<close>'s structural-termination obligation. This is the canonical
  replacement for the hand \<open>parser.Builder.flattenInheritance\<close>.\<close>

fun enumNameFull :: "enum_decl_full \<Rightarrow> String.literal" where
  "enumNameFull (EnumDeclFull n _ _) = n"

fun entityNameFull :: "entity_decl_full \<Rightarrow> String.literal" where
  "entityNameFull (EntityDeclFull n _ _ _ _) = n"

fun entityParentFull :: "entity_decl_full \<Rightarrow> String.literal option" where
  "entityParentFull (EntityDeclFull _ p _ _ _) = p"

fun entityFieldsFull :: "entity_decl_full \<Rightarrow> field_decl_full list" where
  "entityFieldsFull (EntityDeclFull _ _ fs _ _) = fs"

fun entityInvsFull :: "entity_decl_full \<Rightarrow> expr_full list" where
  "entityInvsFull (EntityDeclFull _ _ _ iv _) = iv"

fun fieldNameFull :: "field_decl_full \<Rightarrow> String.literal" where
  "fieldNameFull (FieldDeclFull n _ _ _) = n"

fun fieldTypeFull :: "field_decl_full \<Rightarrow> type_expr_full" where
  "fieldTypeFull (FieldDeclFull _ t _ _) = t"

fun state_fieldNameFull :: "state_field_decl_full \<Rightarrow> String.literal" where
  "state_fieldNameFull (StateFieldDeclFull n _ _) = n"

fun state_fieldTypeFull :: "state_field_decl_full \<Rightarrow> type_expr_full" where
  "state_fieldTypeFull (StateFieldDeclFull _ t _) = t"

fun enumValuesFull :: "enum_decl_full \<Rightarrow> String.literal list" where
  "enumValuesFull (EnumDeclFull _ vs _) = vs"

fun typeAliasName :: "type_alias_decl_full \<Rightarrow> String.literal" where
  "typeAliasName (TypeAliasDeclFull n _ _ _) = n"

fun typeAliasType :: "type_alias_decl_full \<Rightarrow> type_expr_full" where
  "typeAliasType (TypeAliasDeclFull _ t _ _) = t"

text \<open>\<open>enumValuesForType\<close> resolves a named type to its enum values,
  following \<open>type X = Y\<close> aliases transitively. Fuel parameter caps
  recursion at \<open>length aliases + 1\<close> in the wrapper \<open>enumValuesForField\<close>,
  which suffices for any acyclic alias chain and degrades safely on
  cycles (returns \<open>None\<close>). Lifted from duplicated definitions in
  \<open>testgen.Stateful.enumValuesForType\<close> and
  \<open>testgen.Behavioral.enumValuesForField\<close> (the Behavioral copy had no
  cycle guard \<rightarrow> could non-terminate on \<open>type A = B; type B = A\<close>).\<close>

function enumValuesForType ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> enum_decl_full list \<Rightarrow>
   type_alias_decl_full list \<Rightarrow> String.literal list option" where
  "enumValuesForType fuel t enums aliases =
     (if fuel = 0 then None
      else case t of
        NamedTypeF name _ \<Rightarrow>
          (case List.find (\<lambda>e. enumNameFull e = name) enums of
             Some e \<Rightarrow> Some (enumValuesFull e)
           | None \<Rightarrow>
               (case List.find (\<lambda>a. typeAliasName a = name) aliases of
                  Some a \<Rightarrow>
                    enumValuesForType (fuel - 1) (typeAliasType a) enums aliases
                | None \<Rightarrow> None))
      | _ \<Rightarrow> None)"
  by pat_completeness auto
termination
  by (relation "measure (\<lambda>(fuel, _, _, _). fuel)") auto

definition enumValuesForField ::
  "field_decl_full \<Rightarrow> enum_decl_full list \<Rightarrow>
   type_alias_decl_full list \<Rightarrow> String.literal list option" where
  "enumValuesForField f enums aliases \<equiv>
     enumValuesForType (Suc (length aliases)) (fieldTypeFull f) enums aliases"

definition entityByName ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> entity_decl_full option" where
  "entityByName es nm =
     map_of (map (\<lambda>e. (entityNameFull e, e)) (rev es)) nm"

definition findFieldDeclFull ::
  "field_decl_full list \<Rightarrow> String.literal \<Rightarrow> field_decl_full option" where
  "findFieldDeclFull fs nm \<equiv>
     List.find (\<lambda>fd. fieldNameFull fd = nm) fs"

definition entityFieldDeclLookup ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal
     \<Rightarrow> field_decl_full option" where
  "entityFieldDeclLookup es ename fname \<equiv>
     case entityByName es ename of
       None    \<Rightarrow> None
     | Some ed \<Rightarrow> findFieldDeclFull (entityFieldsFull ed) fname"

definition entityHasField ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "entityHasField es ename fname \<equiv>
     (case entityByName es ename of
        None    \<Rightarrow> False
      | Some ed \<Rightarrow> list_ex (\<lambda>fd. fieldNameFull fd = fname) (entityFieldsFull ed))"

definition entityFieldNames ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal list" where
  "entityFieldNames es ename \<equiv>
     (case entityByName es ename of
        None    \<Rightarrow> []
      | Some ed \<Rightarrow> map fieldNameFull (entityFieldsFull ed))"

definition entityNameInList ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal option" where
  "entityNameInList es nm \<equiv>
     (case entityByName es nm of
        None    \<Rightarrow> None
      | Some _  \<Rightarrow> Some nm)"

fun isCollectionType :: "type_expr_full \<Rightarrow> bool" where
  "isCollectionType (SetTypeF _ _)          = True"
| "isCollectionType (SeqTypeF _ _)          = True"
| "isCollectionType (MapTypeF _ _ _)        = True"
| "isCollectionType (RelationTypeF _ _ _ _) = True"
| "isCollectionType _                       = False"

fun assignsField ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "assignsField (FieldAccessF _ f _) field = (f = field)"
| "assignsField (IdentifierF n _)    field = (n = field)"
| "assignsField (PrimeF inner _)     field = assignsField inner field"
| "assignsField (IndexF base _ _)    field = assignsField base field"
| "assignsField _                    _     = False"

text \<open>Narration counterexample helpers: \<open>extractFieldAssignRhs\<close> descends
  through \<open>BAnd\<close>-chains and returns the RHS of every \<open>field = rhs\<close>
  assignment (decided by \<open>assignsField\<close>) that targets the given field.
  \<open>ensuresRhsForField\<close> aggregates across an \<open>ensures\<close> clause list and
  returns the singleton RHS iff exactly one assignment targets the field
  (multiple matches are ambiguous \<rightarrow> \<open>None\<close>). Lifted from
  \<open>verify.Narration\<close>.\<close>

fun extractFieldAssignRhs ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> expr_full list" where
  "extractFieldAssignRhs (BinaryOpF BEq lhs rhs _) field =
     (if assignsField lhs field then [rhs] else [])"
| "extractFieldAssignRhs (BinaryOpF BAnd l r _) field =
     extractFieldAssignRhs l field @ extractFieldAssignRhs r field"
| "extractFieldAssignRhs _ _ = []"

definition ensuresRhsForField ::
  "expr_full list \<Rightarrow> String.literal \<Rightarrow> expr_full option" where
  "ensuresRhsForField ensures field \<equiv>
     (case concat (map (\<lambda>e. extractFieldAssignRhs e field) ensures) of
        [r] \<Rightarrow> Some r
      | _   \<Rightarrow> None)"

fun entityNameFromType ::
  "type_expr_full \<Rightarrow> String.literal option" where
  "entityNameFromType (RelationTypeF _ _ to _) = typeName to"
| "entityNameFromType (NamedTypeF n _)         = Some n"
| "entityNameFromType (SetTypeF inner _)       = entityNameFromType inner"
| "entityNameFromType (SeqTypeF inner _)       = entityNameFromType inner"
| "entityNameFromType (OptionTypeF inner _)    = entityNameFromType inner"
| "entityNameFromType (MapTypeF _ v _)         = entityNameFromType v"

fun relationTargetEntityName ::
  "type_expr_full \<Rightarrow> String.literal option" where
  "relationTargetEntityName
     (RelationTypeF _ _ (NamedTypeF n _) _) = Some n"
| "relationTargetEntityName (NamedTypeF n _) = Some n"
| "relationTargetEntityName _ = None"

fun referencesPrimedRelation ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "referencesPrimedRelation (PrimeF (IdentifierF n _) _) rel = (n = rel)"
| "referencesPrimedRelation _ _ = False"

fun referencesPreRelation ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "referencesPreRelation (PreF (IdentifierF n _) _) rel = (n = rel)"
| "referencesPreRelation (IdentifierF n _) rel = (n = rel)"
| "referencesPreRelation _ _ = False"

fun chain_up ::
  "entity_decl_full list \<Rightarrow> nat \<Rightarrow> String.literal \<Rightarrow> String.literal list
     \<Rightarrow> entity_decl_full list" where
  "chain_up _ 0 _ _ = []"
| "chain_up es (Suc f) name seen =
     (case entityByName es name of
        None \<Rightarrow> []
      | Some e \<Rightarrow>
          (case entityParentFull e of
             None \<Rightarrow> [e]
           | Some parent \<Rightarrow>
               (if List.member seen parent then [e]
                else chain_up es f parent (name # seen) @ [e])))"

fun upsert_field ::
  "field_decl_full list \<Rightarrow> field_decl_full \<Rightarrow> field_decl_full list" where
  "upsert_field acc fd =
     (if list_ex (\<lambda>g. fieldNameFull g = fieldNameFull fd) acc
      then map (\<lambda>g. if fieldNameFull g = fieldNameFull fd then fd else g) acc
      else acc @ [fd])"

fun flatten_entity ::
  "entity_decl_full list \<Rightarrow> entity_decl_full \<Rightarrow> entity_decl_full" where
  "flatten_entity es (EntityDeclFull nm pa fs iv sp) =
     (case pa of
        None \<Rightarrow> EntityDeclFull nm pa fs iv sp
      | Some _ \<Rightarrow>
          (let anc = butlast (chain_up es (length es) nm [nm])
           in if anc = [] then EntityDeclFull nm pa fs iv sp
              else EntityDeclFull nm pa
                     (foldl upsert_field []
                        (concat (map entityFieldsFull anc) @ fs))
                     (concat (map entityInvsFull anc) @ iv)
                     sp))"

fun flattenInheritance :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity c) c) d e f g h i j k l m n p"

text \<open>Phase 9f — the Phase 9 structural primitives are now backed by proof,
  not merely totality.

  \<^item> \<open>stripSpans\<close> is idempotent: re-erasing a spans-erased tree is a
    no-op. This is exactly the property that makes the lint L04
    \<open>stripSpans e\<close>-keyed overlap classes well-defined (a fact previously
    only argued informally in the Scala rewrite).
  \<^item> \<open>flattenAnd\<close> fully decomposes: re-flattening the conjunct list is
    stable, so no top-level \<open>BAnd\<close> survives — the normalisation guarantee
    its consumers rely on.
  \<^item> \<open>flatten_entity\<close> (hence \<open>flattenInheritance\<close>) is the identity on a
    parent-less entity / service: inheritance flattening only ever rewrites
    \<open>extends\<close> declarations.

  NB \<open>flattenInheritance\<close> is deliberately NOT proven globally idempotent
  because it is not: the parent ref is retained and inherited invariants are
  concatenated, so a second application duplicates them. This is latent and
  harmless (\<open>parser.Builder.buildIRCore\<close> applies it exactly once); a fix
  (clearing the parent ref) is a separate behaviour change — it would alter
  \<open>lint.UnusedEntity\<close> reachability and the IR-JSON \<open>extends_\<close> field.\<close>

lemma stripSpans_idem:
  "stripSpans (stripSpans e) = stripSpans e"
  "stripSpans_list (stripSpans_list xs) = stripSpans_list xs"
  "stripSpans_fields (stripSpans_fields fs) = stripSpans_fields fs"
  "stripSpans_entries (stripSpans_entries ms) = stripSpans_entries ms"
  "stripSpans_bindings (stripSpans_bindings bs) = stripSpans_bindings bs"
  by (induction e and xs and fs and ms and bs
      rule: stripSpans_stripSpans_list_stripSpans_fields_stripSpans_entries_stripSpans_bindings.induct)
     auto

lemma flattenAnd_decompose:
  "concat (map flattenAnd (flattenAnd e)) = flattenAnd e"
  by (induction e rule: flattenAnd.induct) auto

lemma flattenAnd_nonempty:
  "flattenAnd e \<noteq> []"
  by (induction e rule: flattenAnd.induct) auto

lemma flattenAnd_requiresAlloy_iff:
  "requiresAlloy e \<longleftrightarrow> (\<exists>x \<in> set (flattenAnd e). requiresAlloy x)"
  by (induction e rule: flattenAnd.induct) auto

lemma flatten_entity_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity es e = e"
  by (cases e) (auto split: option.splits)

lemma flattenInheritance_id_on_parentless:
  assumes "list_all (\<lambda>x. entityParentFull x = None) c"
  shows "flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p)
           = ServiceIRFull a b c d e f g h i j k l m n p"
proof -
  have "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  thus ?thesis by simp
qed

text \<open>Phase 9k — hardened inheritance flattening.
  \<open>flatten_entity2\<close> / \<open>flattenInheritance2\<close> differ from v1 by clearing
  the parent ref on the output (\<open>pa := None\<close>) so that a second application
  is a no-op: \<open>flattenInheritance2_idem\<close>. The fields/invariants
  computation is unchanged, so on parentless input v1 and v2 agree
  (\<open>flattenInheritance2_eq_on_parentless\<close>). The PR that switches
  \<open>parser.Builder.buildIRCore\<close> to v2 is a deliberate behaviour change —
  it alters \<open>lint.UnusedEntity\<close> reachability (parent ref no longer in the
  \<open>extends_\<close> graph) and the IR-JSON \<open>extends_\<close> field — and rides on
  these proofs.\<close>

fun flatten_entity2 ::
  "entity_decl_full list \<Rightarrow> entity_decl_full \<Rightarrow> entity_decl_full" where
  "flatten_entity2 es (EntityDeclFull nm pa fs iv sp) =
     (case pa of
        None \<Rightarrow> EntityDeclFull nm None fs iv sp
      | Some _ \<Rightarrow>
          (let anc = butlast (chain_up es (length es) nm [nm])
           in if anc = [] then EntityDeclFull nm None fs iv sp
              else EntityDeclFull nm None
                     (foldl upsert_field []
                        (concat (map entityFieldsFull anc) @ fs))
                     (concat (map entityInvsFull anc) @ iv)
                     sp))"

fun flattenInheritance2 :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flattenInheritance2 (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity2 c) c) d e f g h i j k l m n p"

lemma flatten_entity2_parent_cleared:
  "entityParentFull (flatten_entity2 es e) = None"
  by (cases e) (auto simp: Let_def split: option.splits if_splits)

lemma flatten_entity2_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity2 es e = e"
  by (cases e) (auto split: option.splits)

lemma flatten_entity2_eq_on_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity2 es e = flatten_entity es e"
  by (cases e) (auto split: option.splits)

lemma flattenInheritance2_idem:
  "flattenInheritance2 (flattenInheritance2 s) = flattenInheritance2 s"
proof (cases s)
  case (ServiceIRFull a b c d ee f g h i j k l m n p)
  let ?c' = "map (flatten_entity2 c) c"
  have "list_all (\<lambda>x. entityParentFull x = None) ?c'"
    by (simp add: list_all_iff flatten_entity2_parent_cleared)
  hence "map (flatten_entity2 ?c') ?c' = ?c'"
    by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  thus ?thesis using ServiceIRFull by simp
qed

lemma flattenInheritance2_eq_on_parentless:
  assumes "list_all (\<lambda>x. entityParentFull x = None) c"
  shows "flattenInheritance2 (ServiceIRFull a b c d e f g h i j k l m n p)
           = flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p)"
proof -
  have a: "map (flatten_entity2 c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  have b: "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  from a b show ?thesis by simp
qed

definition emptyServiceIrFull :: "String.literal \<Rightarrow> service_ir_full" where
  "emptyServiceIrFull nm =
     ServiceIRFull nm [] [] [] [] None [] [] [] [] [] [] [] None None"

text \<open>Issue #202 close-out: \<open>lower\<close> projects \<open>expr_full\<close> onto the
  verified-subset \<open>expr\<close>. Out-of-subset constructors return \<open>None\<close>. Span
  field preserved.

  Schema-awareness: the \<open>enums\<close> parameter (list of declared enum names) lets
  \<open>lower\<close> disambiguate a \<open>QuantifierF\<close> binding into \<open>ForallEnum\<close> vs
  \<open>ForallRel\<close> — both reachable from the same \<open>QuantifierBindingFull v
  (IdentifierF dom _) _ _\<close> shape, with the choice schema-dependent.

  Coverage v2 (Issue #202 close-out): \<open>QuantifierF\<close> over all four kinds
  (\<open>QAll\<close>/\<open>QNo\<close>/\<open>QSome\<close>/\<open>QExists\<close>, multi-binding right-folded), and
  multi-field \<open>WithF\<close> (folded into nested \<open>WithRec\<close>s). Still punted:
  \<open>CallF\<close> (predicate inlining requires definition lookup beyond enum names),
  \<open>IfF\<close> (no \<open>If\<close> ctor in the verified subset), \<open>BSubset\<close> (the \<open>\<forall> x \<in> r1.
  Member x r2\<close> desugar requires fresh-variable generation that this v2 does
  not thread).

  \<open>lower_soundness\<close> in \<open>Soundness.thy\<close> remains a thin corollary of
  \<open>soundness\<close>: every \<open>e :: expr\<close> produced by \<open>lower\<close> falls under the
  universal soundness theorem regardless of which \<open>expr_full\<close> shape it came
  from. No per-case \<open>lower_*_step\<close> proofs are needed.\<close>

end
