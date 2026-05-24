theory SchemaDerive
  imports Schema MigrationOps IR_Helpers IR_Analysis IR_Lower
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

lemmas widenExplicitIdPkSqlType_code [code] = widenExplicitIdPkSqlType_def
lemmas sqlOp_code [code] = sqlOp_def
lemmas aggregateForName_code [code] = aggregateForName_def
lemmas lambdaProjection_code [code] = lambdaProjection_def
lemmas decodeAggregateCall_code [code] = decodeAggregateCall_def
lemmas detectAggregateInvariant_code [code] = detectAggregateInvariant_def

end
