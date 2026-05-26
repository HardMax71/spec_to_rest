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
    OpenApiConstraints
    ValidateConventions
    ParseTemporal
    ParsePath
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

text \<open>Override HOL's default \<open>equal\<close> equation on \<open>bool\<close>. The standard
  typeclass derivation extracts as a 4-arm \<open>(p, pa) match\<close> where the last
  two arms are subsumed by the first two — Scala 3 flags them as unreachable.
  The direct \<open>iff\<close> form extracts to \<open>p == pa\<close> with no match at all.\<close>

lemma equal_bool_direct [code]:
  "HOL.equal (p::bool) q = (if p then q else \<not> q)"
  by (cases p; cases q; simp add: equal_bool_def)

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

text \<open>\<open>Num.One\<close> constructor renamed to \<open>Onea\<close> for the same case-collision
  reason: the extracted module references \<open>one_class.one\<close> (lowercase) via
  the numeral machinery the decimal-literal parser pulls in, and on
  case-insensitive filesystems (macOS APFS, Windows NTFS) the class files
  \<open>SpecRestGenerated$One.class\<close> and \<open>SpecRestGenerated$one.class\<close>
  collide.\<close>
code_identifier
  constant Num.One \<rightharpoonup>
    (Scala) "SpecRestGenerated.Onea"

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
    topoSortNames
    tableDepPairs
    columnName
    columnSqlType
    columnNullable
    columnDefaultValue
    fkColumn
    fkRefTable
    fkRefColumn
    fkOnDelete
    indexName
    indexColumns
    indexUnique
    indexFilterClause
    tableName
    tableEntityName
    tableColumns
    tablePrimaryKey
    tableForeignKeys
    tableChecks
    tableIndexes
    triggerName
    triggerFunctionName
    triggerTargetTable
    triggerTargetColumn
    triggerSourceTable
    triggerSourceForeignKey
    triggerAggregateOf
    triggerSourceColumn
    schemaTables
    schemaTriggers
    inverseOp
    isDestructiveOp
    downList
    computeDiff
    intraDrops
    intraAdds
    intraAlters
    sortTablesByFk
    lookupChecks
    columnNames
    alterForPair
    walkIntConstraint
    walkStringConstraint
    intAtom
    stringAtom
    emptyIntConstraint
    emptyStringConstraint
    mergeIntConstraint
    mergeStringConstraint
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
    findEnumValuesInType
    openapiPrimitiveOf
    classifyOpenApiNamedType
    emptyOpenApiBounds
    visitConstraintOpenApi
    decimalToNonNegInt
    parseDecimalLit
    showNat
    disambiguateKeys
    anonInvariantName
    parseTemporalBody
    temporalArg
    synthTemporalExpr
    primitiveTypeToSql
    classifyColumnType
    collectionElementEntityName
    uniqueBackFkColumn
    validateTrigger
    extractPartialIndexRules
    appendPartialIndexes
    parseConventionValue
    synthConventionValue
    findOperationByName
    operationHasParamNamed
    validateIrContextRule
    extractTsTuples
    collisionsForRule
    parseHttpMethod
    decidePutPatch
    decideKindAndMethod
    buildOperationClassification
    isCardinalityRhs
    isDirectEmitShape
    classifyStrategy
    synthesisStrategyLabel
    signalsMutatedRelations
    signalsPreservedRelations
    signalsCreatesNewKey
    signalsDeletesKey
    signalsTargetEntityFieldCount
    signalsWithFieldCount
    signalsFilterParamCount
    signalsIsTransition
    signalsHasCollectionInput
    setTargetEntityFieldCount
    classificationOperationName
    classificationKind
    classificationMethod
    classificationMatchedRule
    classificationTargetEntity
    classificationStrategy
    classificationSignals
    isGetMethod
    isDeleteMethod
    isCreateLikeKind
    isDeleteKind
    defaultStatus
    resolveStatus
    resolveMethod
    pathWithIdSuffix
    derivePathPattern
    literalEndsWith
    paramTypeIsInt
    paramNameLooksLikeId
    stateRelationKeyTypeNames
    findIdParam
    literalLength
    literalStartsWith
    literalDropLeft
    literalDropRight
    extractVerbBeforeKebab
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
