theory Codegen
  imports
    Translate
    Semantics
    IR_Helpers
    IR_Analysis
    IR_Lower
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
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
