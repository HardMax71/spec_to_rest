theory Classify
  imports Methods RouteKind SpecRest_IR.IR_Helpers SpecRest_IR.IR_Analysis
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

primrec signalsMutatedRelations :: "analysis_signals \<Rightarrow> String.literal list" where
  "signalsMutatedRelations (AnalysisSignals m _ _ _ _ _ _ _ _) = m"
primrec signalsPreservedRelations :: "analysis_signals \<Rightarrow> String.literal list" where
  "signalsPreservedRelations (AnalysisSignals _ p _ _ _ _ _ _ _) = p"
primrec signalsCreatesNewKey :: "analysis_signals \<Rightarrow> bool" where
  "signalsCreatesNewKey (AnalysisSignals _ _ c _ _ _ _ _ _) = c"
primrec signalsDeletesKey :: "analysis_signals \<Rightarrow> bool" where
  "signalsDeletesKey (AnalysisSignals _ _ _ d _ _ _ _ _) = d"
primrec signalsTargetEntityFieldCount :: "analysis_signals \<Rightarrow> nat option" where
  "signalsTargetEntityFieldCount (AnalysisSignals _ _ _ _ t _ _ _ _) = t"
primrec signalsWithFieldCount :: "analysis_signals \<Rightarrow> nat option" where
  "signalsWithFieldCount (AnalysisSignals _ _ _ _ _ w _ _ _) = w"
primrec signalsFilterParamCount :: "analysis_signals \<Rightarrow> nat" where
  "signalsFilterParamCount (AnalysisSignals _ _ _ _ _ _ f _ _) = f"
primrec signalsIsTransition :: "analysis_signals \<Rightarrow> bool" where
  "signalsIsTransition (AnalysisSignals _ _ _ _ _ _ _ t _) = t"
primrec signalsHasCollectionInput :: "analysis_signals \<Rightarrow> bool" where
  "signalsHasCollectionInput (AnalysisSignals _ _ _ _ _ _ _ _ h) = h"

primrec setTargetEntityFieldCount ::
  "nat option \<Rightarrow> analysis_signals \<Rightarrow> analysis_signals"
where
  "setTargetEntityFieldCount v (AnalysisSignals m p c d _ w f t h) =
     AnalysisSignals m p c d v w f t h"

datatype operation_classification = OperationClassification
  String.literal             \<comment> \<open>operationName\<close>
  operation_kind             \<comment> \<open>kind\<close>
  http_method                \<comment> \<open>method\<close>
  String.literal             \<comment> \<open>matchedRule\<close>
  "String.literal option"    \<comment> \<open>targetEntity\<close>
  synthesis_strategy         \<comment> \<open>strategy\<close>
  analysis_signals           \<comment> \<open>signals\<close>

primrec classificationOperationName :: "operation_classification \<Rightarrow> String.literal" where
  "classificationOperationName (OperationClassification n _ _ _ _ _ _) = n"
primrec classificationKind :: "operation_classification \<Rightarrow> operation_kind" where
  "classificationKind (OperationClassification _ k _ _ _ _ _) = k"
primrec classificationMethod :: "operation_classification \<Rightarrow> http_method" where
  "classificationMethod (OperationClassification _ _ m _ _ _ _) = m"
primrec classificationMatchedRule :: "operation_classification \<Rightarrow> String.literal" where
  "classificationMatchedRule (OperationClassification _ _ _ r _ _ _) = r"
primrec classificationTargetEntity :: "operation_classification \<Rightarrow> String.literal option" where
  "classificationTargetEntity (OperationClassification _ _ _ _ t _ _) = t"
primrec classificationStrategy :: "operation_classification \<Rightarrow> synthesis_strategy" where
  "classificationStrategy (OperationClassification _ _ _ _ _ s _) = s"
primrec classificationSignals :: "operation_classification \<Rightarrow> analysis_signals" where
  "classificationSignals (OperationClassification _ _ _ _ _ _ sg) = sg"

datatype classification_result = ClassificationResult
  operation_kind
  http_method
  String.literal             \<comment> \<open>matchedRule\<close>
  analysis_signals           \<comment> \<open>possibly-updated signals\<close>

text \<open>Combines a routing decision with the operation name, target entity,
  and strategy into a full \<open>operation_classification\<close>. The Scala caller
  computes name/targetEntity/strategy at the data-extraction boundary and
  passes them in.\<close>

fun buildOperationClassification ::
  "String.literal \<Rightarrow> String.literal option \<Rightarrow> synthesis_strategy \<Rightarrow>
   classification_result \<Rightarrow> operation_classification"
where
  "buildOperationClassification name targetEntity strategy (ClassificationResult k m rule sig) =
     OperationClassification name k m rule targetEntity strategy sig"

lemmas buildOperationClassification_code [code] = buildOperationClassification.simps

text \<open>The M3/M4 PUT-vs-PATCH refinement: replace if the with-clause covers
  every entity field; partial-update otherwise. Updates the signals record
  with the resolved target-entity field count whenever both \<open>withFieldCount\<close>
  and \<open>entityFieldCount\<close> are present, so downstream consumers see the
  resolved value.\<close>

definition decidePutPatch ::
  "analysis_signals \<Rightarrow> nat option \<Rightarrow> classification_result"
where
  "decidePutPatch signals entityFieldCount = (
    case signalsWithFieldCount signals of
       None \<Rightarrow> ClassificationResult PartialUpdate PATCH (STR ''M4'') signals
     | Some wfc \<Rightarrow>
         (case entityFieldCount of
            None \<Rightarrow> ClassificationResult PartialUpdate PATCH (STR ''M4'') signals
          | Some totalCount \<Rightarrow>
              let updated = setTargetEntityFieldCount (Some totalCount) signals
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
    if signalsIsTransition signals then
      ClassificationResult Transition POST (STR ''M10'') signals
    else if signalsDeletesKey signals then
      ClassificationResult Delete DELETE (STR ''M5'') signals
    else if signalsMutatedRelations signals \<noteq> [] \<and> signalsCreatesNewKey signals then
      ClassificationResult Create POST (STR ''M1'') signals
    else if signalsMutatedRelations signals = [] then
      (if signalsFilterParamCount signals > 3 then
         ClassificationResult FilteredRead GET (STR ''M7'') signals
       else
         ClassificationResult Read GET (STR ''M2'') signals)
    else if signalsHasCollectionInput signals \<and>
            signalsMutatedRelations signals \<noteq> [] then
      ClassificationResult BatchMutation POST (STR ''M9'') signals
    else if signalsMutatedRelations signals \<noteq> [] \<and>
            \<not> signalsCreatesNewKey signals \<and>
            \<not> signalsDeletesKey signals then
      decidePutPatch signals entityFieldCount
    else
      ClassificationResult SideEffect POST (STR ''M8'') signals)"

text \<open>\<open>isCardinalityRhs\<close>: recognises an RHS like \<open>(|n| | |n'| | |n|+k | |n|-k)\<close>
  that re-asserts the cardinality of a state field. Split into three \<open>fun\<close>s
  with top-level-only patterns on \<open>expr\<close>; the original five-equation
  form with deeply-nested patterns made Isabelle's pattern-completeness check
  explode against \<open>expr\<close>'s ~50 constructors (152s -> seconds).\<close>

fun innerIsTargetCard :: "expr \<Rightarrow> String.literal \<Rightarrow> bool" where
  "innerIsTargetCard (PreF e _) n =
     (case e of IdentifierF m _ \<Rightarrow> m = n | _ \<Rightarrow> False)"
| "innerIsTargetCard (IdentifierF m _) n = (m = n)"
| "innerIsTargetCard _ _ = False"

fun isIntLit :: "expr \<Rightarrow> bool" where
  "isIntLit (IntLitF _ _) = True"
| "isIntLit _ = False"

fun isCardinalityRhs :: "expr \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isCardinalityRhs (UnaryOpF op inner _) n =
     ((case op of UCardinality \<Rightarrow> True | _ \<Rightarrow> False) \<and> innerIsTargetCard inner n)"
| "isCardinalityRhs (BinaryOpF op inner rhs _) n =
     ((case op of BAdd \<Rightarrow> True | BSub \<Rightarrow> True | _ \<Rightarrow> False)
      \<and> isIntLit rhs \<and> isCardinalityRhs inner n)"
| "isCardinalityRhs _ _ = False"

text \<open>Top-level \<open>fun\<close> pattern instead of an inline \<open>case\<close>: avoids the
  Scala 3 type-narrowing warning the code generator otherwise emits for
  single-constructor destructuring inside a lambda.\<close>

fun mapEntryIsLeafLeaf :: "map_entry \<Rightarrow> bool" where
  "mapEntryIsLeafLeaf (MapEntryFull k v _) = (isLeafValue k \<and> isLeafValue v)"

text \<open>Scalar direct-emit recognisers (issue #407). A scalar-update ensures
  clause assigns an Int-typed state scalar from arithmetic over itself
  (current or \<open>pre\<close>) and integer literals. The extractors return the
  structured update / guard rather than a bare bool so the classifier gate
  and the emitters consume the same parse - the recognised fragment and
  the emitted SQL cannot drift. Guards are the requires atoms an emitter
  can fold into the atomic UPDATE's WHERE clause.\<close>

datatype scalar_rhs =
    SrLit int
  | SrSelf
  | SrAdd scalar_rhs scalar_rhs
  | SrSub scalar_rhs scalar_rhs
  | SrMul scalar_rhs scalar_rhs

fun scalarRhsOf :: "String.literal \<Rightarrow> expr \<Rightarrow> scalar_rhs option" where
  "scalarRhsOf n (IntLitF k _) = Some (SrLit k)"
| "scalarRhsOf n (IdentifierF m _) = (if m = n then Some SrSelf else None)"
| "scalarRhsOf n (PreF e _) =
     (case e of
        IdentifierF m _ \<Rightarrow> (if m = n then Some SrSelf else None)
      | _ \<Rightarrow> None)"
| "scalarRhsOf n (UnaryOpF op e _) =
     (case op of
        UNegate \<Rightarrow> (case e of IntLitF k _ \<Rightarrow> Some (SrLit (- k)) | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "scalarRhsOf n (BinaryOpF op l r _) =
     (case (scalarRhsOf n l, scalarRhsOf n r) of
        (Some a, Some b) \<Rightarrow>
          (case op of
             BAdd \<Rightarrow> Some (SrAdd a b)
           | BSub \<Rightarrow> Some (SrSub a b)
           | BMul \<Rightarrow> Some (SrMul a b)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "scalarRhsOf _ _ = None"

fun scalarUpdateOf ::
  "String.literal list \<Rightarrow> expr \<Rightarrow> (String.literal \<times> scalar_rhs) option"
where
  "scalarUpdateOf scalars (BinaryOpF op lhs rhs _) =
     (case op of
        BEq \<Rightarrow>
          (case lhs of
             PrimeF inner _ \<Rightarrow>
               (case inner of
                  IdentifierF n _ \<Rightarrow>
                    (if n \<in> set scalars
                     then map_option (Pair n) (scalarRhsOf n rhs)
                     else None)
                | _ \<Rightarrow> None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "scalarUpdateOf _ _ = None"

datatype scalar_cmp = ScGt | ScGe | ScLt | ScLe | ScEq | ScNeq

datatype scalar_guard =
    SgTrue
  | SgCmp String.literal scalar_cmp int

text \<open>\<open>scalar_cmp\<close> is a dedicated closed comparator set (not \<open>bin_op\<close>)
  so Scala consumers can render guards with an exhaustive match instead
  of a defensive catch-all over the ~50 \<open>bin_op\<close> constructors.\<close>

fun scalarCmpOf :: "bin_op \<Rightarrow> scalar_cmp option" where
  "scalarCmpOf BGt  = Some ScGt"
| "scalarCmpOf BGe  = Some ScGe"
| "scalarCmpOf BLt  = Some ScLt"
| "scalarCmpOf BLe  = Some ScLe"
| "scalarCmpOf BEq  = Some ScEq"
| "scalarCmpOf BNeq = Some ScNeq"
| "scalarCmpOf _    = None"

text \<open>The parser renders a negative literal as \<open>UNegate (IntLitF k)\<close>,
  so guard atoms accept both spellings.\<close>

fun scalarLitOf :: "expr \<Rightarrow> int option" where
  "scalarLitOf (IntLitF k _) = Some k"
| "scalarLitOf (UnaryOpF op e _) =
     (case op of
        UNegate \<Rightarrow> (case e of IntLitF k _ \<Rightarrow> Some (- k) | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "scalarLitOf _ = None"

fun scalarGuardOf ::
  "String.literal list \<Rightarrow> expr \<Rightarrow> scalar_guard option"
where
  "scalarGuardOf scalars (BoolLitF b _) = (if b then Some SgTrue else None)"
| "scalarGuardOf scalars (BinaryOpF op l r _) =
     (case scalarCmpOf op of
        None \<Rightarrow> None
      | Some c \<Rightarrow>
          (case l of
             IdentifierF n _ \<Rightarrow>
               (if n \<in> set scalars
                then map_option (SgCmp n c) (scalarLitOf r)
                else None)
           | _ \<Rightarrow> None))"
| "scalarGuardOf _ _ = None"

definition isScalarUpdateClause ::
  "String.literal list \<Rightarrow> expr \<Rightarrow> bool"
where
  "isScalarUpdateClause scalars c = (scalarUpdateOf scalars c \<noteq> None)"

text \<open>A conjunction assigning the same scalar twice with different
  right-hand sides has no single-UPDATE interpretation (it is an
  unsatisfiable ensures); identical repeats are harmless. Checked at
  classification so the emitters never see a conflicting op.\<close>

fun scalarUpdatesConsistent ::
  "(String.literal \<times> scalar_rhs) list \<Rightarrow> bool"
where
  "scalarUpdatesConsistent [] = True"
| "scalarUpdatesConsistent ((n, r) # rest) =
     (list_all (\<lambda>(m, s). m \<noteq> n \<or> s = r) rest \<and>
      scalarUpdatesConsistent rest)"

text \<open>\<open>isDirectEmitShape\<close>: a single ensures-clause is direct-emit-able when its
  shape matches one of the recognised verified-subset patterns (preserve a
  state field, append-disjoint, cardinality-preserving, single-index update,
  output binding to a pure read).\<close>

definition isDirectEmitShape ::
  "expr \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
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
         list_all mapEntryIsLeafLeaf entries
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
  Scala boundary.

  Scalar branch (issue #407): if ANY clause is a scalar update, ALL clauses
  must be - a mixed entity/scalar operation would otherwise be emitted by
  the entity-CRUD path with the scalar clauses silently dropped. Scalar
  operations additionally require: no declared inputs (the literal-only
  handlers take none, and an unused id-shaped input would put a path
  parameter in the derived route that no handler binds); pairwise
  consistent updates (\<open>scalarUpdatesConsistent\<close>); and every
  requires-atom guardable (foldable into the atomic UPDATE's WHERE
  clause) - verification assumes requires holds on entry, but HTTP
  callers can invoke the route in any state, so an unguardable
  precondition would let a runtime call break a verified invariant.\<close>

definition classifyStrategy ::
  "expr list \<Rightarrow> expr list \<Rightarrow> String.literal list \<Rightarrow> String.literal list
     \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> synthesis_strategy"
where
  "classifyStrategy ensures reqs inputNames stateFieldNames scalarFieldNames
     outputNames = (
    let clauses = flattenEnsures ensures in
    if clauses = [] then LlmSynthesis
    else if list_ex (isScalarUpdateClause scalarFieldNames) clauses then
      (if list_all (isScalarUpdateClause scalarFieldNames) clauses \<and>
          inputNames = [] \<and>
          scalarUpdatesConsistent
            (List.map_filter (scalarUpdateOf scalarFieldNames) clauses) \<and>
          list_all (\<lambda>r. scalarGuardOf scalarFieldNames r \<noteq> None)
                   (flattenEnsures reqs)
       then DirectEmit else LlmSynthesis)
    else if list_all (\<lambda>c. isDirectEmitShape c stateFieldNames outputNames) clauses
    then DirectEmit else LlmSynthesis)"

definition synthesisStrategyLabel :: "synthesis_strategy \<Rightarrow> String.literal" where
  "synthesisStrategyLabel s = (case s of DirectEmit \<Rightarrow> STR ''DIRECT_EMIT''
                                       | LlmSynthesis \<Rightarrow> STR ''LLM_SYNTHESIS'')"

lemmas decidePutPatch_code [code] = decidePutPatch_def
lemmas decideKindAndMethod_code [code] = decideKindAndMethod_def
lemmas isCardinalityRhs_code [code] = isCardinalityRhs.simps
lemmas isDirectEmitShape_code [code] = isDirectEmitShape_def
lemmas scalarRhsOf_code [code] = scalarRhsOf.simps
lemmas scalarUpdateOf_code [code] = scalarUpdateOf.simps
lemmas scalarCmpOf_code [code] = scalarCmpOf.simps
lemmas scalarLitOf_code [code] = scalarLitOf.simps
lemmas scalarGuardOf_code [code] = scalarGuardOf.simps
lemmas isScalarUpdateClause_code [code] = isScalarUpdateClause_def
lemmas scalarUpdatesConsistent_code [code] = scalarUpdatesConsistent.simps
lemmas classifyStrategy_code [code] = classifyStrategy_def
lemmas synthesisStrategyLabel_code [code] = synthesisStrategyLabel_def

end
