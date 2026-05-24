theory Classify
  imports Methods RouteKind IR_Helpers IR_Analysis IR_Lower
begin

text \<open>Operation-classification ADTs and decision logic lifted from
  \<open>specrest.convention.Classify\<close>. The decision tree mapping
  \<open>analysis_signals\<close> to \<open>(operation_kind, http_method, matched_rule)\<close>
  is the M1\<dots>M10 routing oracle used by codegen + testgen + openapi.

  The hand-written enums and case classes (\<open>AnalysisSignals\<close>,
  \<open>SynthesisStrategy\<close>, \<open>OperationClassification\<close>) are retired;
  consumers use these extracted forms directly.\<close>

datatype synthesis_strategy = DirectEmit | LlmSynthesis

datatype analysis_signals = AnalysisSignals
  "String.literal list"     \<comment> \<open>mutatedRelations\<close>
  "String.literal list"     \<comment> \<open>preservedRelations\<close>
  bool                      \<comment> \<open>createsNewKey\<close>
  bool                      \<comment> \<open>deletesKey\<close>
  "nat option"              \<comment> \<open>targetEntityFieldCount\<close>
  "nat option"              \<comment> \<open>withFieldCount\<close>
  nat                       \<comment> \<open>filterParamCount\<close>
  bool                      \<comment> \<open>isTransition\<close>
  bool                      \<comment> \<open>hasCollectionInput\<close>

primrec signals_mutated_relations :: "analysis_signals \<Rightarrow> String.literal list" where
  "signals_mutated_relations (AnalysisSignals m _ _ _ _ _ _ _ _) = m"
primrec signals_preserved_relations :: "analysis_signals \<Rightarrow> String.literal list" where
  "signals_preserved_relations (AnalysisSignals _ p _ _ _ _ _ _ _) = p"
primrec signals_creates_new_key :: "analysis_signals \<Rightarrow> bool" where
  "signals_creates_new_key (AnalysisSignals _ _ c _ _ _ _ _ _) = c"
primrec signals_deletes_key :: "analysis_signals \<Rightarrow> bool" where
  "signals_deletes_key (AnalysisSignals _ _ _ d _ _ _ _ _) = d"
primrec signals_target_entity_field_count :: "analysis_signals \<Rightarrow> nat option" where
  "signals_target_entity_field_count (AnalysisSignals _ _ _ _ t _ _ _ _) = t"
primrec signals_with_field_count :: "analysis_signals \<Rightarrow> nat option" where
  "signals_with_field_count (AnalysisSignals _ _ _ _ _ w _ _ _) = w"
primrec signals_filter_param_count :: "analysis_signals \<Rightarrow> nat" where
  "signals_filter_param_count (AnalysisSignals _ _ _ _ _ _ f _ _) = f"
primrec signals_is_transition :: "analysis_signals \<Rightarrow> bool" where
  "signals_is_transition (AnalysisSignals _ _ _ _ _ _ _ t _) = t"
primrec signals_has_collection_input :: "analysis_signals \<Rightarrow> bool" where
  "signals_has_collection_input (AnalysisSignals _ _ _ _ _ _ _ _ h) = h"

primrec set_target_entity_field_count ::
  "nat option \<Rightarrow> analysis_signals \<Rightarrow> analysis_signals"
where
  "set_target_entity_field_count v (AnalysisSignals m p c d _ w f t h) =
     AnalysisSignals m p c d v w f t h"

datatype operation_classification = OperationClassification
  String.literal             \<comment> \<open>operationName\<close>
  operation_kind             \<comment> \<open>kind\<close>
  http_method                \<comment> \<open>method\<close>
  String.literal             \<comment> \<open>matchedRule\<close>
  "String.literal option"    \<comment> \<open>targetEntity\<close>
  synthesis_strategy         \<comment> \<open>strategy\<close>
  analysis_signals           \<comment> \<open>signals\<close>

primrec classification_operation_name :: "operation_classification \<Rightarrow> String.literal" where
  "classification_operation_name (OperationClassification n _ _ _ _ _ _) = n"
primrec classification_kind :: "operation_classification \<Rightarrow> operation_kind" where
  "classification_kind (OperationClassification _ k _ _ _ _ _) = k"
primrec classification_method :: "operation_classification \<Rightarrow> http_method" where
  "classification_method (OperationClassification _ _ m _ _ _ _) = m"
primrec classification_matched_rule :: "operation_classification \<Rightarrow> String.literal" where
  "classification_matched_rule (OperationClassification _ _ _ r _ _ _) = r"
primrec classification_target_entity :: "operation_classification \<Rightarrow> String.literal option" where
  "classification_target_entity (OperationClassification _ _ _ _ t _ _) = t"
primrec classification_strategy :: "operation_classification \<Rightarrow> synthesis_strategy" where
  "classification_strategy (OperationClassification _ _ _ _ _ s _) = s"
primrec classification_signals :: "operation_classification \<Rightarrow> analysis_signals" where
  "classification_signals (OperationClassification _ _ _ _ _ _ sg) = sg"

datatype classification_result = ClassificationResult
  operation_kind
  http_method
  String.literal             \<comment> \<open>matchedRule\<close>
  analysis_signals           \<comment> \<open>possibly-updated signals\<close>

text \<open>Combines a routing decision with the operation name, target entity,
  and strategy into a full \<open>operation_classification\<close>. The Scala caller
  computes name/targetEntity/strategy at the data-extraction boundary and
  passes them in.\<close>

definition buildOperationClassification ::
  "String.literal \<Rightarrow> String.literal option \<Rightarrow> synthesis_strategy \<Rightarrow>
   classification_result \<Rightarrow> operation_classification"
where
  "buildOperationClassification name targetEntity strategy res = (
    case res of ClassificationResult k m rule sig \<Rightarrow>
      OperationClassification name k m rule targetEntity strategy sig)"

lemmas buildOperationClassification_code [code] = buildOperationClassification_def

text \<open>The M3/M4 PUT-vs-PATCH refinement: replace if the with-clause covers
  every entity field; partial-update otherwise. Updates the signals record
  with the resolved target-entity field count whenever both \<open>withFieldCount\<close>
  and \<open>entityFieldCount\<close> are present, so downstream consumers see the
  resolved value.\<close>

definition decidePutPatch ::
  "analysis_signals \<Rightarrow> nat option \<Rightarrow> classification_result"
where
  "decidePutPatch signals entityFieldCount = (
    case signals_with_field_count signals of
       None \<Rightarrow> ClassificationResult PartialUpdate PATCH (STR ''M4'') signals
     | Some wfc \<Rightarrow>
         (case entityFieldCount of
            None \<Rightarrow> ClassificationResult PartialUpdate PATCH (STR ''M4'') signals
          | Some totalCount \<Rightarrow>
              let updated = set_target_entity_field_count (Some totalCount) signals
              in if totalCount \<le> wfc
                 then ClassificationResult Replace PUT (STR ''M3'') updated
                 else ClassificationResult PartialUpdate PATCH (STR ''M4'') updated))"

text \<open>The M1\<dots>M10 routing oracle. Pure decision tree over \<open>analysis_signals\<close>
  and the resolved target-entity field count (for the PUT/PATCH branch).
  Used by codegen, testgen, and openapi via Scala's \<open>Classify.classifyOperation\<close>.\<close>

definition decideKindAndMethod ::
  "analysis_signals \<Rightarrow> nat option \<Rightarrow> classification_result"
where
  "decideKindAndMethod signals entityFieldCount = (
    if signals_is_transition signals then
      ClassificationResult Transition POST (STR ''M10'') signals
    else if signals_deletes_key signals then
      ClassificationResult Delete DELETE (STR ''M5'') signals
    else if signals_mutated_relations signals \<noteq> [] \<and> signals_creates_new_key signals then
      ClassificationResult Create POST (STR ''M1'') signals
    else if signals_mutated_relations signals = [] then
      (if signals_filter_param_count signals > 3 then
         ClassificationResult FilteredRead GET (STR ''M7'') signals
       else
         ClassificationResult Read GET (STR ''M2'') signals)
    else if signals_has_collection_input signals \<and>
            signals_mutated_relations signals \<noteq> [] then
      ClassificationResult BatchMutation POST (STR ''M9'') signals
    else if signals_mutated_relations signals \<noteq> [] \<and>
            \<not> signals_creates_new_key signals \<and>
            \<not> signals_deletes_key signals then
      decidePutPatch signals entityFieldCount
    else
      ClassificationResult SideEffect POST (STR ''M8'') signals)"

text \<open>\<open>isCardinalityRhs\<close>: recognises an RHS like \<open>(|n| | |n'| | |n|+k | |n|-k)\<close>
  that re-asserts the cardinality of a state field. Pure recursion over \<open>expr_full\<close>
  (BAdd/BSub branches are structurally decreasing on the inner expression).\<close>

fun isCardinalityRhs :: "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isCardinalityRhs (UnaryOpF UCardinality (PreF (IdentifierF m _) _) _) n = (m = n)"
| "isCardinalityRhs (UnaryOpF UCardinality (IdentifierF m _) _) n = (m = n)"
| "isCardinalityRhs (BinaryOpF BAdd inner (IntLitF _ _) _) n = isCardinalityRhs inner n"
| "isCardinalityRhs (BinaryOpF BSub inner (IntLitF _ _) _) n = isCardinalityRhs inner n"
| "isCardinalityRhs _ _ = False"

text \<open>\<open>isDirectEmitShape\<close>: a single ensures-clause is direct-emit-able when its
  shape matches one of the recognised verified-subset patterns (preserve a
  state field, append-disjoint, cardinality-preserving, single-index update,
  output binding to a pure read).\<close>

definition isDirectEmitShape ::
  "expr_full \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "isDirectEmitShape clause stateFieldNames outputNames = (case clause of
       BinaryOpF BEq (PrimeF (IdentifierF l _) _) (IdentifierF r _) _ \<Rightarrow>
         l = r \<and> l \<in> set stateFieldNames
     | BinaryOpF BNotIn _ (PrimeF (IdentifierF n _) _) _ \<Rightarrow>
         n \<in> set stateFieldNames
     | BinaryOpF BEq (UnaryOpF UCardinality (PrimeF (IdentifierF n _) _) _) rhs _ \<Rightarrow>
         n \<in> set stateFieldNames \<and> isCardinalityRhs rhs n
     | BinaryOpF BEq
         (PrimeF (IdentifierF l _) _)
         (BinaryOpF BAdd (PreF (IdentifierF r _) _) (MapLiteralF entries _) _) _ \<Rightarrow>
         l = r \<and> l \<in> set stateFieldNames \<and>
         list_all (\<lambda>e. case e of MapEntryFull k v _ \<Rightarrow> isLeafValue k \<and> isLeafValue v) entries
     | BinaryOpF BEq (IndexF (PrimeF (IdentifierF n _) _) idx _) rhs _ \<Rightarrow>
         n \<in> set stateFieldNames \<and> isLeafValue idx \<and> isLeafValue rhs
     | BinaryOpF BEq
         (FieldAccessF (IndexF (PrimeF (IdentifierF n _) _) idx _) _ _) rhs _ \<Rightarrow>
         n \<in> set stateFieldNames \<and> isLeafValue idx \<and> isLeafValue rhs
     | BinaryOpF BEq (IdentifierF name _) rhs _ \<Rightarrow>
         name \<in> set outputNames \<and> isPureRead rhs
     | _ \<Rightarrow> False)"

text \<open>\<open>classifyStrategy\<close>: an operation is direct-emit-able when every flattened
  ensures-clause matches one of the recognised shapes, otherwise it falls back
  to LLM synthesis. Takes the ensures body and field-name lists directly so
  the caller can do the \<open>flattenEnsures\<close> + name-set computation at the
  Scala boundary.\<close>

definition classifyStrategy ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> synthesis_strategy"
where
  "classifyStrategy ensures stateFieldNames outputNames = (
    let clauses = flattenEnsures ensures in
    if clauses \<noteq> [] \<and>
       list_all (\<lambda>c. isDirectEmitShape c stateFieldNames outputNames) clauses
    then DirectEmit else LlmSynthesis)"

definition synthesisStrategyLabel :: "synthesis_strategy \<Rightarrow> String.literal" where
  "synthesisStrategyLabel s = (case s of DirectEmit \<Rightarrow> STR ''DIRECT_EMIT''
                                       | LlmSynthesis \<Rightarrow> STR ''LLM_SYNTHESIS'')"

lemmas decidePutPatch_code [code] = decidePutPatch_def
lemmas decideKindAndMethod_code [code] = decideKindAndMethod_def
lemmas isCardinalityRhs_code [code] = isCardinalityRhs.simps
lemmas isDirectEmitShape_code [code] = isDirectEmitShape_def
lemmas classifyStrategy_code [code] = classifyStrategy_def
lemmas synthesisStrategyLabel_code [code] = synthesisStrategyLabel_def

end
