theory Codegen
  imports
    Translate
    Semantics
    IR_Helpers
    IR_Analysis
    IR_Lower
    TopoSort
    Schema
    MigrationOps
    SchemaDiff
    Strategies
    SchemaDerive
    Methods
    RouteKind
    SchemaTraversal
    Classify
    "HOL-Library.Code_Target_Int"
    "HOL-Library.Code_Target_Numeral"
begin

text \<open>Productionized Scala extraction. The full-language IR (\<open>expr_full\<close>,
  \<open>type_expr_full\<close>, the 18 \<open>*_full\<close> declaration types) is plain datatypes.
  Code_Target_Scala emits flat case classes with positional fields — no
  \<open>_ext[A]\<close> polymorphic record-scheme machinery, no post-processing step.
  The export at this theory's bottom is the canonical Scala consumer-facing
  artifact; consumers \<open>import\<close> it directly.

  Verified-subset records (\<open>field_decl\<close>, …) extract as polymorphic scheme
  types but Scala consumers don't reference them — \<open>lower\<close>/\<open>eval\<close>/\<open>smtEval\<close>
  output the verified-subset \<open>expr\<close> datatype which is flat.\<close>

declare schema.defs[code]
declare state.defs[code]
declare state_pair.defs[code]
declare smt_model.defs[code]
declare smt_model_pair.defs[code]

text \<open>Rename Code_Target_Nat's \<open>Nat\<close> constructor to \<open>Nata\<close> in Scala output:
  the generated module also defines \<open>sealed abstract class nat\<close>, and on
  case-insensitive filesystems (macOS APFS, Windows NTFS) the class files
  \<open>SpecRestGenerated$nat.class\<close> and \<open>SpecRestGenerated$Nat.class\<close>
  collide, causing \<open>NoClassDefFoundError\<close> at runtime. The \<open>_a\<close> suffix
  matches Isabelle's existing disambiguation convention (\<open>equal_smt_vala\<close>,
  \<open>equal_ir_valuea\<close>). See issue #222.\<close>
code_identifier
  constant Code_Target_Nat.Nat \<rightharpoonup>
    (Scala) "SpecRestGenerated.Nata"

text \<open>\<open>Methods.Delete\<close> (operation_kind) renamed to \<open>Deletea\<close> in Scala output:
  the same module also defines \<open>case class DELETE\<close> (http_method), and on
  case-insensitive filesystems (macOS APFS, Windows NTFS) the class files
  \<open>SpecRestGenerated$DELETE.class\<close> and \<open>SpecRestGenerated$Delete.class\<close>
  collide, causing \<open>NoClassDefFoundError\<close> at runtime. The \<open>_a\<close> suffix
  matches Isabelle's existing disambiguation convention (\<open>Nata\<close>,
  \<open>equal_smt_vala\<close>). Same shape as issue #222.\<close>
code_identifier
  constant Methods.Delete \<rightharpoonup>
    (Scala) "SpecRestGenerated.Deletea"

text \<open>Type and constructor names follow Isabelle convention: lowercase \<open>snake_case\<close>
  for types, PascalCase for constructors. Consumers that need to coexist with
  hand-written homonyms (the convention layer's \<open>TriggerAggregate\<close> enum and the
  codegen layer's \<open>MigrationOp\<close> enum still live in the Scala source) avoid name
  clashes because the extracted forms are \<open>trigger_aggregate\<close>/\<open>migration_op\<close>
  and the hand-written ones are \<open>TriggerAggregate\<close>/\<open>MigrationOp\<close>. A follow-up
  PR that retires the hand-written enums can re-introduce PascalCase renames here.\<close>

export_code
    translate
    eval
    smtEval
    isLitFull
    isTrueLit
    enumLiteralOf
    combineAnd
    decomposeAtom
    free_vars
    hasPrePrime
    subst
    litClass
    describeLitClass
    binOpName
    typeContainsNamed
    exprContainsBoolLit
    rangeOf
    conflicts
    negate
    isLenOrCardOf
    isLiteral
    extractFieldName
    enumLitName
    isMapType
    isEntityType
    sameNamedType
    isLeafValue
    isPureRead
    relationTargetsEntity
    extractKeySet
    extractMapEntries
    isKeyExistsConj
    emptyServiceIrFull
    lower
    lowerSetList
    requiresAlloy
    subexprs
    stripSpans
    typeStripSpans
    peelSmtRelationRef
    peelRelationRefFull
    typeExprToTy
    typeExprFullToTy
    schemaFieldType
    check_value_has_ty
    tyctxFromService
    enumNameFull
    schemaRelationValueType
    tyctxEmpty
    entityByName
    findFieldDeclFull
    entityFieldDeclLookup
    entityHasField
    entityFieldNames
    entityNameInList
    isCollectionType
    rootIdentifier
    assignsField
    entityNameFromType
    relationTargetEntityName
    referencesPrimedRelation
    referencesPreRelation
    binOpToTs
    spanOf
    flattenAnd
    flattenAndAll
    flattenEnsuresExpr
    flattenEnsures
    collectPrimedIdentifiers
    collectFieldAccessNames
    collectPreservedRelations
    containsPreInPlusChain
    detectCreatePattern
    detectDeletePattern
    detectKeyExistsInRequires
    resolveWithBase
    collectWithFields
    withInfoFieldNames
    withInfoBaseIdentifier
    isInputCollectionType
    countFilterParams
    hasCollectionInput
    typeName
    flattenInheritance
    topo_sort_names
    table_dep_pairs
    column_name
    column_sql_type
    column_nullable
    column_default_value
    fk_column
    fk_ref_table
    fk_ref_column
    fk_on_delete
    index_name
    index_columns
    index_unique
    index_filter_clause
    table_name
    table_entity_name
    table_columns
    table_primary_key
    table_foreign_keys
    table_checks
    table_indexes
    trigger_name
    trigger_function_name
    trigger_target_table
    trigger_target_column
    trigger_source_table
    trigger_source_foreign_key
    trigger_aggregate_of
    trigger_source_column
    schema_tables
    schema_triggers
    inverse_op
    is_destructive_op
    down_list
    compute_diff
    intra_drops
    intra_adds
    intra_alters
    sort_tables_by_fk
    lookup_checks
    column_names
    alter_for_pair
    walk_int_constraint
    walk_string_constraint
    int_atom
    string_atom
    empty_int_constraint
    empty_string_constraint
    merge_int_constraint
    merge_string_constraint
    widenExplicitIdPkSqlType
    sqlOp
    aggregateForName
    lambdaProjection
    decodeAggregateCall
    detectAggregateInvariant
    isRedirectStatus
    classifyShape
    classify
    effectiveRouteKind
    matchesCreateShape
    isFailLoudStub
    aliasRefinements
    parseHttpMethod
    decidePutPatch
    decideKindAndMethod
    buildOperationClassification
    isCardinalityRhs
    isDirectEmitShape
    classifyStrategy
    synthesisStrategyLabel
    signals_mutated_relations
    signals_preserved_relations
    signals_creates_new_key
    signals_deletes_key
    signals_target_entity_field_count
    signals_with_field_count
    signals_filter_param_count
    signals_is_transition
    signals_has_collection_input
    set_target_entity_field_count
    classification_operation_name
    classification_kind
    classification_method
    classification_matched_rule
    classification_target_entity
    classification_strategy
    classification_signals
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
