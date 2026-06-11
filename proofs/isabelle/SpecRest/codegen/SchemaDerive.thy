theory SchemaDerive
  imports Schema MigrationOps SpecRest_Core.IR_Helpers SpecRest_Core.IR_Analysis SchemaTraversal
begin

text \<open>Pure utility lifts from \<open>specrest.convention.Schema\<close>. The bulk of
  \<open>deriveSchema\<close> (entity traversal, junction-table construction, partial-index
  conventions) still lives in Scala because it weaves in convention rules and
  map lookups; these lifts cover the deterministic micro-functions that just
  classify or rewrite extracted IR values.

  Function names use \<open>camelCase\<close> (matching the IR precedent
  \<open>flattenAnd\<close>/\<open>flattenEnsuresExpr\<close>) so the extracted Scala can be called
  directly from hand-written code without a wrapper-rename indirection.\<close>

datatype detected_aggregate = DetectedAggregate
  String.literal             \<comment> \<open>target_field\<close>
  String.literal             \<comment> \<open>collection_field\<close>
  trigger_aggregate          \<comment> \<open>aggregate\<close>
  "String.literal option"    \<comment> \<open>source_field\<close>

definition widenExplicitIdPkSqlType ::
  "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal"
where
  "widenExplicitIdPkSqlType field_name sql_type =
    (if field_name = STR ''id'' \<and> sql_type = STR ''INTEGER''
     then STR ''BIGINT''
     else sql_type)"

definition sqlOp :: "bin_op \<Rightarrow> String.literal option" where
  "sqlOp op = (case op of
       BGt \<Rightarrow> Some (STR ''>'')
     | BLt \<Rightarrow> Some (STR ''<'')
     | BGe \<Rightarrow> Some (STR ''>='')
     | BLe \<Rightarrow> Some (STR ''<='')
     | BEq \<Rightarrow> Some (STR ''='')
     | BNeq \<Rightarrow> Some (STR ''!='')
     | _ \<Rightarrow> None)"

definition aggregateForName :: "String.literal \<Rightarrow> trigger_aggregate option" where
  "aggregateForName name = (
    if name = STR ''sum'' then Some SumAgg
    else if name = STR ''count'' then Some CountAgg
    else if name = STR ''min'' then Some MinAgg
    else if name = STR ''max'' then Some MaxAgg
    else None)"

definition lambdaProjection :: "expr \<Rightarrow> String.literal option" where
  "lambdaProjection body = (case body of
       FieldAccessF (IdentifierF _ _) field _ \<Rightarrow> Some field
     | _ \<Rightarrow> None)"

text \<open>Aggregate-call decoding: a verified-subset SUM/COUNT/MIN/MAX over a
  collection field, optionally with a single-projection lambda. Returns the
  collection field, aggregate kind, and optional source-projection name — all
  three packaged as a flat triple via the dedicated \<open>detected_aggregate\<close>
  record (Isabelle tuples extract as right-nested in Scala, which makes them
  awkward to destructure; a datatype lifts cleanly to a flat case class).\<close>

datatype aggregate_call = AggregateCall
  String.literal             \<comment> \<open>collection_field\<close>
  trigger_aggregate          \<comment> \<open>aggregate\<close>
  "String.literal option"    \<comment> \<open>source_projection\<close>

definition decodeAggregateCall :: "expr \<Rightarrow> aggregate_call option" where
  "decodeAggregateCall call = (case call of
       CallF (IdentifierF name _) args _ \<Rightarrow>
         (case aggregateForName name of
            None \<Rightarrow> None
          | Some agg \<Rightarrow>
              (case agg of
                 CountAgg \<Rightarrow>
                   (case args of
                      [coll] \<Rightarrow>
                        (case extractFieldName coll of
                           None \<Rightarrow> None
                         | Some c \<Rightarrow> Some (AggregateCall c CountAgg None))
                    | _ \<Rightarrow> None)
               | _ \<Rightarrow>
                   (case args of
                      [coll, LambdaF _ body _] \<Rightarrow>
                        (case extractFieldName coll of
                           None \<Rightarrow> None
                         | Some coln \<Rightarrow>
                             (case lambdaProjection body of
                                None \<Rightarrow> None
                              | Some src \<Rightarrow> Some (AggregateCall coln agg (Some src))))
                    | _ \<Rightarrow> None)))
     | _ \<Rightarrow> None)"

definition detectAggregateInvariant :: "expr \<Rightarrow> detected_aggregate option" where
  "detectAggregateInvariant invExpr = (case invExpr of
       BinaryOpF BEq lhs rhs _ \<Rightarrow>
         (case extractFieldName lhs of
            None \<Rightarrow> None
          | Some tgt \<Rightarrow>
              (case decodeAggregateCall rhs of
                 None \<Rightarrow> None
               | Some ac \<Rightarrow>
                   (case ac of AggregateCall coll agg src \<Rightarrow>
                      Some (DetectedAggregate tgt coll agg src))))
     | _ \<Rightarrow> None)"

text \<open>SQL type lookup for the 11 spec primitives understood by deriveSchema.
  Lifted from \<open>specrest.convention.Schema.PrimitiveTypeMap\<close>. \<open>Money\<close> is an
  INTEGER alias (cents-as-int convention); \<open>Boolean\<close> aliases \<open>Bool\<close>.\<close>

definition primitiveTypeToSql :: "String.literal \<Rightarrow> String.literal option" where
  "primitiveTypeToSql nm = (
    if nm = STR ''String''   then Some (STR ''TEXT'')
    else if nm = STR ''Int''      then Some (STR ''INTEGER'')
    else if nm = STR ''Float''    then Some (STR ''DOUBLE PRECISION'')
    else if nm = STR ''Bool''     then Some (STR ''BOOLEAN'')
    else if nm = STR ''Boolean''  then Some (STR ''BOOLEAN'')
    else if nm = STR ''DateTime'' then Some (STR ''TIMESTAMPTZ'')
    else if nm = STR ''Date''     then Some (STR ''DATE'')
    else if nm = STR ''UUID''     then Some (STR ''UUID'')
    else if nm = STR ''Decimal''  then Some (STR ''NUMERIC(19,4)'')
    else if nm = STR ''Bytes''    then Some (STR ''BYTEA'')
    else if nm = STR ''Money''    then Some (STR ''INTEGER'')
    else None)"

text \<open>Column-kind classification for the deriveSchema field walker. Lifts the
  pure recursive part of \<open>specrest.convention.Schema.mapTypeToColumn\<close>: peels
  \<open>OptionTypeF\<close> wrappers tracking nullability, follows alias chains via
  \<open>map_of am\<close>, and produces a flat \<open>column_kind\<close> tag the Scala caller
  uses to build the final \<open>column_spec\<close> (FK col name, CHECK string,
  default literals are all string-building post-steps that stay in Scala).

  Alias-cycle protection: the same fuel-bounded visited-set pattern as
  \<open>aliasRefinements\<close> / \<open>findEnumValuesInType\<close> in \<open>SchemaTraversal\<close>.\<close>

datatype column_kind =
    CkPrim "String.literal"        \<comment> \<open>sql type from primitiveTypeToSql\<close>
  | CkEnum "String.literal list"   \<comment> \<open>enum value list (used to build IN-list CHECK)\<close>
  | CkEntityRef "String.literal"   \<comment> \<open>target entity name (Scala builds FK + _id suffix)\<close>
  | CkJsonArray                    \<comment> \<open>SetTypeF / SeqTypeF → JSONB with '[]'::jsonb default\<close>
  | CkJsonObject                   \<comment> \<open>MapTypeF → JSONB with '{}'::jsonb default\<close>
  | CkRelation                     \<comment> \<open>RelationTypeF → BIGINT\<close>
  | CkUnknown                      \<comment> \<open>unresolved NamedTypeF → TEXT fallback\<close>

datatype classified_column = ClassifiedColumn column_kind bool
  \<comment> \<open>kind + nullable flag (true iff the type expression had an outer OptionTypeF
      anywhere in the alias chain)\<close>

text \<open>\<open>stripOptions\<close> (from SchemaTraversal) peels all outer \<open>OptionTypeF\<close>
  layers; we use it before each alias-hop dispatch so fuel is only consumed on
  alias chains, not on Option-nesting depth (which is structurally bounded by
  the input type). The nullable flag is set whenever stripping changed the type
  — at the top of the input or after following an alias whose body was an
  \<open>OptionTypeF\<close>.\<close>

fun classifyColumnTypeAux ::
  "nat \<Rightarrow> type_expr \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list
    \<Rightarrow> String.literal list \<Rightarrow> bool \<Rightarrow> classified_column"
where
  "classifyColumnTypeAux 0 _ _ _ _ _ nullable = ClassifiedColumn CkUnknown nullable"
| "classifyColumnTypeAux (Suc fuel) ty am em entityNames seen nullable =
     (let stripped = stripOptions ty;
          nullable' = nullable \<or> stripped \<noteq> ty
      in case stripped of
         NamedTypeF name _ \<Rightarrow>
           (case primitiveTypeToSql name of
              Some sqlType \<Rightarrow> ClassifiedColumn (CkPrim sqlType) nullable'
            | None \<Rightarrow>
                (case map_of em name of
                   Some (EnumDeclFull _ vs _) \<Rightarrow> ClassifiedColumn (CkEnum vs) nullable'
                 | None \<Rightarrow>
                     (if name \<in> set entityNames then
                        ClassifiedColumn (CkEntityRef name) nullable'
                      else if name \<in> set seen then
                        ClassifiedColumn CkUnknown nullable'
                      else case map_of am name of
                             Some (TypeAliasDeclFull _ base _ _) \<Rightarrow>
                               classifyColumnTypeAux fuel base am em entityNames
                                 (name # seen) nullable'
                           | None \<Rightarrow> ClassifiedColumn CkUnknown nullable')))
       | SetTypeF _ _      \<Rightarrow> ClassifiedColumn CkJsonArray nullable'
       | SeqTypeF _ _      \<Rightarrow> ClassifiedColumn CkJsonArray nullable'
       | MapTypeF _ _ _    \<Rightarrow> ClassifiedColumn CkJsonObject nullable'
       | RelationTypeF _ _ _ _ \<Rightarrow> ClassifiedColumn CkRelation nullable'
       \<comment> \<open>Unreachable: \<open>stripOptions\<close> peels all outer \<open>OptionTypeF\<close> layers, so
         \<open>stripped\<close> is never \<open>OptionTypeF\<close>. Required for \<open>fun\<close> match completeness;
         anyone weakening \<open>stripOptions\<close> would need to revisit this branch.\<close>
       | OptionTypeF _ _   \<Rightarrow> ClassifiedColumn CkUnknown nullable')"

definition classifyColumnType ::
  "type_expr \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list
    \<Rightarrow> classified_column"
where
  "classifyColumnType ty am em entityNames =
     classifyColumnTypeAux (Suc (length am)) ty am em entityNames [] False"

lemmas primitiveTypeToSql_code [code] = primitiveTypeToSql_def
lemmas classifyColumnTypeAux_code [code] = classifyColumnTypeAux.simps
lemmas classifyColumnType_code [code] = classifyColumnType_def

text \<open>Aggregate-trigger validation: the pure validity-check kernel of
  \<open>specrest.convention.Schema.detectAggregateTriggers\<close>. The Scala caller
  handles orchestration (parent/child name → table/entity lookups via
  \<open>Map.get\<close> in O(1)) so the lifted predicate operates on already-resolved
  inputs and avoids the O(N) per-invariant list scans an orchestration-shaped
  lift would force. The result carries the resolved table/column names; the
  Scala caller formats the PostgreSQL function and trigger identifiers
  (\<open>recalc_X_Y\<close> / \<open>trg_X_Y\<close>), which is regex/Naming work that stays in Scala.

  Validity checks (all must hold to emit a candidate):
  \<^enum> the named target field exists on the parent
  \<^enum> the child table has exactly one foreign key back to the parent (ambiguous
    multi-FK setups produce no trigger rather than guessing)
  \<^enum> if the aggregate carries a source-projection name, the child entity has
    a field with that name\<close>

datatype trigger_candidate = TriggerCandidate
  String.literal             \<comment> \<open>parent_table\<close>
  String.literal             \<comment> \<open>target_field (Scala turns into column name)\<close>
  String.literal             \<comment> \<open>child_table\<close>
  String.literal             \<comment> \<open>child_back_fk_column\<close>
  trigger_aggregate          \<comment> \<open>aggregate kind\<close>
  "String.literal option"    \<comment> \<open>source_field (Scala turns into column name)\<close>

definition collectionElementEntityName ::
  "type_expr \<Rightarrow> String.literal option"
where
  "collectionElementEntityName ty = (case ty of
       SetTypeF (NamedTypeF n _) _ \<Rightarrow> Some n
     | SeqTypeF (NamedTypeF n _) _ \<Rightarrow> Some n
     | _ \<Rightarrow> None)"

definition uniqueBackFkColumn ::
  "foreign_key_spec list \<Rightarrow> String.literal \<Rightarrow> String.literal option"
where
  "uniqueBackFkColumn fks parentTable =
    (let matching = filter (\<lambda>fk. fkRefTable fk = parentTable) fks
     in case matching of [fk] \<Rightarrow> Some (fkColumn fk) | _ \<Rightarrow> None)"

definition validateTrigger ::
  "table_spec \<Rightarrow> field_decl list \<Rightarrow>
    table_spec \<Rightarrow> entity_decl \<Rightarrow>
    String.literal \<Rightarrow> trigger_aggregate \<Rightarrow> String.literal option \<Rightarrow>
    trigger_candidate option"
where
  "validateTrigger parentTbl parentFields childTbl childEntity tgt agg src =
    (if \<not> (\<exists>f \<in> set parentFields. fieldNameFull f = tgt) then None
     else case uniqueBackFkColumn (tableForeignKeys childTbl) (tableName parentTbl) of
            None \<Rightarrow> None
          | Some fkCol \<Rightarrow>
              let childFields = entityFieldsFull childEntity;
                  srcOk = (case src of
                             None \<Rightarrow> True
                           | Some sf \<Rightarrow>
                               \<exists>f \<in> set childFields. fieldNameFull f = sf)
              in if srcOk
                 then Some (TriggerCandidate
                              (tableName parentTbl) tgt
                              (tableName childTbl) fkCol agg src)
                 else None)"

lemmas collectionElementEntityName_code [code] = collectionElementEntityName_def
lemmas uniqueBackFkColumn_code [code] = uniqueBackFkColumn_def
lemmas validateTrigger_code [code] = validateTrigger_def

lemmas widenExplicitIdPkSqlType_code [code] = widenExplicitIdPkSqlType_def
lemmas sqlOp_code [code] = sqlOp_def
lemmas aggregateForName_code [code] = aggregateForName_def
lemmas lambdaProjection_code [code] = lambdaProjection_def
lemmas decodeAggregateCall_code [code] = decodeAggregateCall_def
lemmas detectAggregateInvariant_code [code] = detectAggregateInvariant_def

text \<open>Partial-index convention extraction. Lifts the pure parts of
  \<open>specrest.convention.Schema.applyPartialIndexConventions\<close>:

  \<^item> \<open>extractPartialIndexRules\<close>: scans the convention block for
    \<open>partial_index\<close> rules with a String-literal filter clause, returning
    a list of \<open>(target_entity, column, filter)\<close> triples. The Scala caller
    then resolves \<open>target_entity\<close> to a table name (uses \<open>Path.getConvention\<close>
    + \<open>Naming.toTableName\<close>, both regex/lookup-bound and staying in Scala).

  \<^item> \<open>partialIndexSpec\<close>: builds the partial \<open>index_spec\<close> with the
    canonical \<open>idx_<table>_<col>_partial\<close> identifier.

  \<^item> \<open>appendPartialIndexes\<close>: appends a list of \<open>(col, filter)\<close>
    derived indexes onto a \<open>table_spec\<close>, preserving the other fields.\<close>

primrec extractPartialIndexRuleOpt ::
  "convention_rule \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) option"
where
  "extractPartialIndexRuleOpt (ConventionRuleFull target prop colOpt value _) =
     (if prop = STR ''partial_index''
      then (case (colOpt, value) of
              (Some col, CvOk (PvString filt)) \<Rightarrow> Some (target, col, filt)
            | _ \<Rightarrow> None)
      else None)"

definition extractPartialIndexRules ::
  "conventions_decl option \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list"
where
  "extractPartialIndexRules conv = (case conv of
       None \<Rightarrow> []
     | Some (ConventionsDeclFull rs _) \<Rightarrow> List.map_filter extractPartialIndexRuleOpt rs)"

definition partialIndexSpec ::
  "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> index_spec"
where
  "partialIndexSpec tableNm col filt =
     IndexSpec (STR ''idx_'' + tableNm + STR ''_'' + col + STR ''_partial'')
               [col] False (Some filt)"

primrec appendPartialIndexes ::
  "table_spec \<Rightarrow> (String.literal \<times> String.literal) list \<Rightarrow> table_spec"
where
  "appendPartialIndexes (TableSpec nm ent cols pk fks cks ixs) colFilters =
     TableSpec nm ent cols pk fks cks
       (ixs @ map (\<lambda>cf. case cf of (col, filt) \<Rightarrow> partialIndexSpec nm col filt) colFilters)"

text \<open>Invariant-atom classifier for entity \<open>CHECK\<close> constraint derivation
  in \<open>Schema.extractInvariantChecks\<close>. After flattening a conjunctive
  invariant body via \<open>flattenAnd\<close>, each atom maps to one of three
  templates:

  \<^item> \<open>IcInClause field literals\<close>: \<open>field IN (\<dots>)\<close> — the atom was
    \<open>BIn\<close> against a \<open>SetLiteralF\<close> whose elements are all literals
    (Scala-side restriction to \<open>StringLitF\<close>/\<open>IntLitF\<close> happens at
    emit time);
  \<^item> \<open>IcCompare field op rhs\<close>: \<open>field <op> <literal>\<close> — the atom was a
    binary comparison with an extractable field name on the left and a
    literal-shaped RHS, and \<open>sqlOp op\<close> recognises the operator;
  \<^item> \<open>IcSkip\<close>: nothing emitted — atom didn't fit any template.

  Scala dispatches on the typed class and emits the SQL string from
  the components (escape, format, interpolate).\<close>

datatype invariant_check_class =
    IcSkip
  | IcInClause "String.literal" "expr list"
  | IcCompare "String.literal" bin_op expr

definition classifyInvariantAtom :: "expr \<Rightarrow> invariant_check_class" where
  "classifyInvariantAtom e = (case e of
      BinaryOpF op left rhs _ \<Rightarrow>
        (case extractFieldName left of
           None    \<Rightarrow> IcSkip
         | Some fn \<Rightarrow>
            (case (op, rhs) of
               (BIn, SetLiteralF elements _) \<Rightarrow>
                 (if elements \<noteq> [] \<and> list_all isLiteral elements
                  then IcInClause fn elements
                  else IcSkip)
             | _ \<Rightarrow>
                 (if isLiteral rhs \<and> sqlOp op \<noteq> None
                  then IcCompare fn op rhs
                  else IcSkip)))
    | _ \<Rightarrow> IcSkip)"

text \<open>Column-CHECK atom classifier for \<open>Schema.applyAtom\<close>. Mirrors
  \<open>classifyInvariantAtom\<close>/\<open>invariant_check_class\<close> but for *field
  refinements* (the per-column \<open>where value matches \<dots>\<close> / \<open>len(value) > n\<close>
  forms) rather than entity invariants: returns a structured template the
  Scala caller formats into the SQL CHECK string.

  Inputs are the atomic expressions produced by \<open>flattenAnd refinement\<close>,
  one per field-level constraint. Each atom maps to one template:

  \<^item> \<open>CcRegexMatch\<close>: regex via \<open>RaMatches\<close> or \<open>RaMatchesIdent\<close> (the
    identifier-qualified form ignores the qualifier — the lifted classifier
    matches the pre-existing Scala behaviour, which is to bind the regex to
    the column being walked regardless of identifier).
  \<^item> \<open>CcLenCompare\<close>: \<open>length(col) op n\<close> via \<open>RaLenCmp\<close>.
  \<^item> \<open>CcValueCompare\<close>: \<open>col op n\<close> via \<open>RaValueCmp\<close>.
  \<^item> \<open>CcLenLitCompare\<close> / \<open>CcValueLitCompare\<close>: \<open>BinaryOpF op lhs rhs\<close>
    with \<open>isLiteral rhs\<close>, \<open>sqlOp op \<noteq> None\<close>, and \<open>lhs\<close> shaped as
    \<open>len(value)\<close> / \<open>value\<close>. Reaches this branch only when
    \<open>decomposeAtom\<close> returned \<open>RaUnknown\<close>, i.e. RHS is a non-integer
    literal (Float / String) — \<open>decomposeAtom\<close> only recognises \<open>IntLitF\<close>
    on the RHS for length/value comparisons.
  \<^item> \<open>CcSkip\<close>: \<open>RaPredCall\<close>, or an unrecognised shape.\<close>

datatype column_check_class =
    CcSkip
  | CcRegexMatch "String.literal"               \<comment> \<open>regex pattern\<close>
  | CcLenCompare bin_op int                \<comment> \<open>op, n\<close>
  | CcValueCompare bin_op int              \<comment> \<open>op, n\<close>
  | CcLenLitCompare bin_op expr       \<comment> \<open>op, literal\<close>
  | CcValueLitCompare bin_op expr     \<comment> \<open>op, literal\<close>

definition classifyColumnCheckAtom :: "expr \<Rightarrow> column_check_class" where
  "classifyColumnCheckAtom e =
     (case decomposeAtom e of
        RaMatches pat \<Rightarrow> CcRegexMatch pat
      | RaMatchesIdent _ pat \<Rightarrow> CcRegexMatch pat
      | RaLenCmp op n \<Rightarrow> CcLenCompare op n
      | RaValueCmp op n \<Rightarrow> CcValueCompare op n
      | RaPredCall _ \<Rightarrow> CcSkip
      | RaUnknown _ \<Rightarrow>
          (case e of
             BinaryOpF op lhs rhs _ \<Rightarrow>
               (if isLiteral rhs \<and> sqlOp op \<noteq> None
                then (if isLenOfValue lhs then CcLenLitCompare op rhs
                      else if isValueRef lhs then CcValueLitCompare op rhs
                      else CcSkip)
                else CcSkip)
           | _ \<Rightarrow> CcSkip))"

lemmas extractPartialIndexRuleOpt_code [code] = extractPartialIndexRuleOpt.simps
lemmas extractPartialIndexRules_code [code] = extractPartialIndexRules_def
lemmas partialIndexSpec_code [code] = partialIndexSpec_def
lemmas appendPartialIndexes_code [code] = appendPartialIndexes.simps
lemmas classifyInvariantAtom_code [code] = classifyInvariantAtom_def
lemmas classifyColumnCheckAtom_code [code] = classifyColumnCheckAtom_def

end
