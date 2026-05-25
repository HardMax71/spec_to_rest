theory SchemaDerive
  imports Schema MigrationOps IR_Helpers IR_Analysis IR_Lower SchemaTraversal
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

definition sqlOp :: "bin_op_full \<Rightarrow> String.literal option" where
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

definition lambdaProjection :: "expr_full \<Rightarrow> String.literal option" where
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

definition decodeAggregateCall :: "expr_full \<Rightarrow> aggregate_call option" where
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

definition detectAggregateInvariant :: "expr_full \<Rightarrow> detected_aggregate option" where
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
  "nat \<Rightarrow> type_expr_full \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list
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
       | OptionTypeF _ _   \<Rightarrow> ClassifiedColumn CkUnknown nullable')"

definition classifyColumnType ::
  "type_expr_full \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list
    \<Rightarrow> classified_column"
where
  "classifyColumnType ty am em entityNames =
     classifyColumnTypeAux (Suc (length am)) ty am em entityNames [] False"

lemmas primitiveTypeToSql_code [code] = primitiveTypeToSql_def
lemmas classifyColumnTypeAux_code [code] = classifyColumnTypeAux.simps
lemmas classifyColumnType_code [code] = classifyColumnType_def

lemmas widenExplicitIdPkSqlType_code [code] = widenExplicitIdPkSqlType_def
lemmas sqlOp_code [code] = sqlOp_def
lemmas aggregateForName_code [code] = aggregateForName_def
lemmas lambdaProjection_code [code] = lambdaProjection_def
lemmas decodeAggregateCall_code [code] = decodeAggregateCall_def
lemmas detectAggregateInvariant_code [code] = detectAggregateInvariant_def

end
