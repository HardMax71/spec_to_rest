theory Codegen
  imports
    SpecRest_Core.Semantics
    SpecRest_Core.IR_Helpers
    SpecRest_Core.IR_Analysis
    SpecRest_Core.Translate
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
    OpenApiSchema
    ValidateConventions
    DialectSchema
    ParseTemporal
    ParsePath
    LintAnalysis
    Classify
    AlloyTypes
    AlloyBuildSigs
    VerifierDispatch
    TargetExpr
    DafnyTransform
    "HOL-Library.Code_Target_Int"
    "HOL-Library.Code_Target_Numeral"
begin

text \<open>Productionized Scala extraction. The full-language IR (\<open>expr\<close>,
  \<open>type_expr\<close>, the 18 \<open>*_full\<close> declaration types) is plain datatypes.
  Code_Target_Scala emits flat case classes with positional fields — no
  \<open>_ext[A]\<close> polymorphic record-scheme machinery, no post-processing step.
  The export at this theory's bottom is the canonical Scala consumer-facing
  artifact; consumers \<open>import\<close> it directly.

  Verified-subset records (\<open>schema_field_decl\<close>, …) extract as polymorphic scheme
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

text \<open>Emit \<open>int\<close> directly as Scala \<open>BigInt\<close> instead of the \<open>Code_Target_Int\<close>
  wrapper \<open>int_of_integer(BigInt)\<close>. \<open>integer\<close> is a \<open>typedef\<close> copy of \<open>int\<close>
  (\<open>Code_Numeral\<close>) with \<open>int_of_integer\<close>/\<open>integer_of_int\<close> its proven-inverse
  morphisms, and the imported \<open>Code_Target_Int\<close> already serializes \<open>integer\<close> to
  \<open>BigInt\<close>. Collapsing \<open>int\<close> onto the same \<open>BigInt\<close> and printing the bijection as
  identity removes both conversions everywhere — the verified \<open>plus_int\<close>/\<open>equal_int\<close>
  code equations compose into native \<open>+\<close>/\<open>==\<close>. The \<open>ord\<close> and \<open>equal\<close> instances for
  \<open>int\<close> are dropped because each would otherwise be a second \<open>ord[BigInt]\<close>/\<open>equal[BigInt]\<close>
  given alongside \<open>integer\<close>'s (the \<open>equal\<close> clash surfaced once \<open>rat\<close>, via the \<open>VReal\<close>/\<open>SReal\<close>
  values, made \<open>equal\<close> on \<open>int\<close> reachable); resolution falls through to the surviving
  \<open>ord_integer\<close>/\<open>equal_integer\<close>, which are the same native \<open><\<close>/\<open>==\<close> on \<open>BigInt\<close>.\<close>
code_printing
  type_constructor Int.int \<rightharpoonup> (Scala) "BigInt"
| constant Code_Numeral.int_of_integer \<rightharpoonup> (Scala) "(_)"
| constant Code_Numeral.integer_of_int \<rightharpoonup> (Scala) "(_)"
| class_instance Int.int :: ord \<rightharpoonup> (Scala) -
| class_instance Int.int :: equal \<rightharpoonup> (Scala) -

text \<open>\<open>string_matches\<close>, \<open>str_predicate\<close>, \<open>builtin_const_val\<close>, \<open>builtin_str_func\<close>, and \<open>agg_sum\<close> are
  abstract (see \<open>IR\<close>); the real semantics is Z3's (regex \<open>str.in_re\<close>, an uninterpreted predicate /
  constant / function, an uninterpreted sum keyed by collection and field) in the hand-written
  translator. The extracted \<open>eval\<close>/\<open>smtEval\<close> reference interpreters are not called by Scala consumers,
  so these serialisations are never-evaluated stubs that only have to type-check.\<close>
code_printing
  constant string_matches \<rightharpoonup> (Scala) "((_) == (_))"
| constant str_predicate \<rightharpoonup> (Scala) "((_) == (_))"
| constant builtin_const_val \<rightharpoonup> (Scala) "(BigInt((_).hashCode))"
| constant builtin_str_func \<rightharpoonup> (Scala) "((_) + (_))"
| constant builtin_int_func \<rightharpoonup> (Scala) "(BigInt((_).hashCode) + (_))"
| constant agg_sum \<rightharpoonup> (Scala) "(BigInt((_).hashCode + (_).hashCode))"

export_code
    translate
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
    typeMismatchAt
    typeMismatchDiagnostics
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
    requiresAlloy
    subexprs
    allSubexprs
    inline_calls
    stripSpans
    typeStripSpans
    peelSmtRelationRef
    peelRelationRef
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
    irStateFields
    irStateFieldNames
    irConventionRules
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
    collectIdentifierNames
    extractFieldAssignRhs
    ensuresRhsForField
    matchesIdentityShape
    keyExistencePair
    desiredSize
    fieldNameIfStateIndex
    enumValuesForType
    enumValuesForField
    structuralIneligibility
    isAutoIncrement
    saTypeExpr
    saTypeImportModule
    postgresSaType
    sqliteSaType
    mysqlSaType
    sqliteTypeRender
    mysqlTypeRender
    isSerial4
    postgresSerialColumnDef
    sqliteSerialColumnDef
    mysqlSerialColumnDef
    postgresCaps
    sqliteCaps
    mysqlCaps
    capsSupportsPartialIndex
    capsSupportsCheckConstraint
    capsSupportsCheckOnAutoIncrement
    capsFkEnforcedByDefault
    capsRequiresTextIndexPrefix
    capsTransactionalDdl
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
    classifyInvariantAtom
    classifyColumnCheckAtom
    emptySchemaObject
    primitiveDefToSchema
    mergeConstraintsLifted
    decideNullable
    makeNullableLifted
    integerSchema
    textSchema
    enumStringSchema
    entityRefSchema
    arraySchema
    mapObjectSchema
    boundsMinLength
    boundsMaxLength
    computeFieldBounds
    typeExprToSchema
    fieldToSchema
    isSensitiveField
    buildEntitySchemas
    mapAlloyPrimitive
    typeToSigNameAlloy
    alloyFieldTypeOf
    fieldElementSigNameAlloy
    needsBoolSig
    alloyBinopShape
    alloyUnopShape
    classifyAlloyIdentifier
    alloyQuantifierClass
    alloyQuantifierKeyword
    enumNameInList
    domainSigNameAlloy
    classifyAlloyBindingIdentifier
    buildAlloySigs
    classifyGlobalVerifier
    classifyInvariantVerifier
    classifyRequiresVerifier
    classifyEnabledVerifier
    classifyPreservationVerifier
    classifyTemporalVerifier
    trustGlobal
    trustRequires
    trustEnabled
    trustPreservation
    verifyEnumNames
    parseConventionValue
    synthConventionValue
    showBool
    showInt
    parsedValueToString
    findOperationByName
    operationHasParamNamed
    validateIrContextRule
    extractTsTuples
    collisionsForRule
    operationMissingEnsures
    collectExprNames
    collectTypeNames
    walkUndefinedExpr
    collectCallNames
    collectAllCallNames
    findCycles
    parseHttpMethod
    decidePutPatch
    decideKindAndMethod
    buildOperationClassification
    isCardinalityRhs
    isDirectEmitShape
    scalarRhsOf
    scalarUpdateOf
    scalarGuardOf
    isScalarUpdateClause
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
    classifyIdent
    classifyUserCall
    quantifierAllIn
    isMapLiteralExpr
    rewriteEntityFieldRefs
    desugarOptionGuards
    collectExternItems
    classifyExternItems
    isNumericType
    isOptionalType
    isDateTimeType
    collectionElementType
    fldName fldType fldDefault fldSpan
    entName entParent entFields entInvariants entSpan
    enmName enmVariants enmSpan
    talName talType talConstraint talSpan
    stfName stfType stfSpan
    stdFields stdSpan
    prmName prmType prmSpan
    operName operInputs operOutputs operRequires operEnsures operRequiresAuth operSpan
    trlFrom trlTo trlVia trlGuard trlSpan
    trnName trnEntity trnField trnRules trnSpan
    invName invBody invSpan
    tmpName tmpBody tmpSpan
    fctName fctBody fctSpan
    fncName fncParams fncRetType fncBody fncSpan
    prdName prdParams prdBody prdSpan
    cvrTarget cvrProperty cvrQualifier cvrValue cvrSpan
    cvdRules cvdSpan
    ssdName ssdKind ssdSpan
    fasName fasValue fasSpan
    mpeKey mpeValue mpeSpan
    qbdVar qbdCollection qbdKind qbdSpan
    svcName svcImports svcEntities svcEnums svcTypeAliases svcState
    svcOperations svcTransitions svcInvariants svcTemporals svcFacts
    svcFunctions svcPredicates svcConventions svcSecurity svcSpan
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
