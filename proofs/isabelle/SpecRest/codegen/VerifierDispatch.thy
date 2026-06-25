theory VerifierDispatch
  imports SpecRest_IR.IR_Helpers SpecRest_IR.IR_Analysis SpecRest_Semantics.Translate
begin

text \<open>Verifier-dispatch and trust classification. Lifts the pure
  per-formula decisions behind \<open>specrest.verify.Classifier\<close> and
  \<open>specrest.verify.Trust\<close>:

  \<^item> \<open>verifier_tool\<close>: which backend (\<open>Z3\<close> or \<open>Alloy\<close>) handles a
    given formula. A formula goes to Alloy iff it contains any
    Alloy-only construct (\<open>requiresAlloy\<close>, lifted in \<open>IR_Helpers\<close>);
    otherwise Z3.
  \<^item> \<open>trust_level\<close>: \<open>Sound\<close> iff every formula's direct translation
    \<open>translate\<close> succeeds (i.e. the formula is in the
    soundness-proven fragment); otherwise \<open>BestEffort\<close>.

  Both classifications are folds over a list of \<open>expr\<close>s; this
  theory provides the \<open>fold\<close> primitives plus the per-check-shape
  wrappers (global, requires, enabled, preservation, temporal) so the
  Scala caller becomes a thin delegation.\<close>

datatype (plugins only: code size) verifier_tool = VtZ3 | VtAlloy

datatype (plugins only: code size) trust_level = TlSound | TlBestEffort

text \<open>\<open>foldVerifier\<close>: any formula needs Alloy \<Longrightarrow> bundle goes Alloy.\<close>

definition foldVerifier :: "expr list \<Rightarrow> verifier_tool" where
  "foldVerifier exprs =
     (if list_ex requiresAlloy exprs then VtAlloy else VtZ3)"

text \<open>\<open>foldTrust\<close>: every formula in the bundle must successfully
  translate directly for the result to be \<open>Sound\<close>. Identical to the
  historical \<open>lower enums e \<noteq> None\<close> oracle by \<open>translate_eq\<close>.\<close>

definition foldTrust ::
  "String.literal list \<Rightarrow> expr list \<Rightarrow> trust_level"
where
  "foldTrust enums exprs =
     (if list_all (\<lambda>e. translate enums e \<noteq> None) exprs
      then TlSound
      else TlBestEffort)"

text \<open>IR-decomposition selectors. Per-constructor pattern-in-head
  \<open>fun\<close>s keep the extracted Scala in proper \<open>match\<close> form (Scala 3
  rejects the \<open>val Ctor(...) = e\<close> shape that \<open>case ... of\<close>
  produces inside a \<open>definition\<close> as a soft-failure warning, which
  is escalated to a compile error by \<open>-Werror\<close>).\<close>

fun invariantBody :: "invariant_decl \<Rightarrow> expr" where
  "invariantBody (InvariantDeclFull _ b _) = b"

fun operationRequires :: "operation_decl \<Rightarrow> expr list" where
  "operationRequires (OperationDeclFull _ _ _ requires _ _ _) = requires"

fun operationEnsures :: "operation_decl \<Rightarrow> expr list" where
  "operationEnsures (OperationDeclFull _ _ _ _ ensures _ _) = ensures"

fun enumDeclName :: "enum_decl \<Rightarrow> String.literal" where
  "enumDeclName (EnumDeclFull n _ _) = n"

fun serviceIrEntities :: "service_ir \<Rightarrow> entity_decl list" where
  "serviceIrEntities (ServiceIRFull _ _ es _ _ _ _ _ _ _ _ _ _ _ _ _) = es"

fun serviceIrEnums :: "service_ir \<Rightarrow> enum_decl list" where
  "serviceIrEnums (ServiceIRFull _ _ _ es _ _ _ _ _ _ _ _ _ _ _ _) = es"

fun serviceIrInvariants :: "service_ir \<Rightarrow> invariant_decl list" where
  "serviceIrInvariants (ServiceIRFull _ _ _ _ _ _ _ _ invs _ _ _ _ _ _ _) = invs"

definition invariantBodies :: "service_ir \<Rightarrow> expr list" where
  "invariantBodies ir = map invariantBody (serviceIrInvariants ir)"

text \<open>Per-check-shape verifier dispatchers. Each constructs the
  exact formula bundle that the original Scala \<open>Classifier\<close>
  function would, then folds.\<close>

definition classifyGlobalVerifier :: "service_ir \<Rightarrow> verifier_tool" where
  "classifyGlobalVerifier ir = foldVerifier (invariantBodies ir)"

definition classifyInvariantVerifier :: "invariant_decl \<Rightarrow> verifier_tool" where
  "classifyInvariantVerifier ivd =
     (if requiresAlloy (invariantBody ivd) then VtAlloy else VtZ3)"

definition classifyRequiresVerifier :: "operation_decl \<Rightarrow> verifier_tool" where
  "classifyRequiresVerifier op = foldVerifier (operationRequires op)"

definition classifyEnabledVerifier ::
  "operation_decl \<Rightarrow> service_ir \<Rightarrow> verifier_tool"
where
  "classifyEnabledVerifier op ir =
     foldVerifier (operationRequires op @ invariantBodies ir)"

definition classifyPreservationVerifier ::
  "operation_decl \<Rightarrow> invariant_decl \<Rightarrow> verifier_tool"
where
  "classifyPreservationVerifier op ivd =
     foldVerifier (invariantBody ivd # operationRequires op @ operationEnsures op)"

text \<open>Temporal checks always go to Alloy (Z3 doesn't support the
  trace semantics needed for \<open>always\<close>/\<open>eventually\<close>).\<close>

definition classifyTemporalVerifier :: "verifier_tool" where
  "classifyTemporalVerifier = VtAlloy"

text \<open>Trust classifications mirror the Scala \<open>planTrust\<close> dispatcher
  in \<open>specrest.verify.Consistency\<close>. Same formula bundles as the
  verifier classifiers; the difference is what they decide.\<close>

definition trustGlobal ::
  "String.literal list \<Rightarrow> service_ir \<Rightarrow> trust_level"
where
  "trustGlobal enums ir = foldTrust enums (invariantBodies ir)"

definition trustRequires ::
  "String.literal list \<Rightarrow> operation_decl \<Rightarrow> trust_level"
where
  "trustRequires enums op = foldTrust enums (operationRequires op)"

definition trustEnabled ::
  "String.literal list \<Rightarrow> operation_decl \<Rightarrow> service_ir \<Rightarrow> trust_level"
where
  "trustEnabled enums op ir =
     foldTrust enums (operationRequires op @ invariantBodies ir)"

definition trustPreservation ::
  "String.literal list \<Rightarrow> operation_decl \<Rightarrow> invariant_decl
   \<Rightarrow> trust_level"
where
  "trustPreservation enums op ivd =
     foldTrust enums (invariantBody ivd # operationRequires op @ operationEnsures op)"

text \<open>Enum-name extraction from the IR. Lifts \<open>Trust.enumNames\<close>
  used to feed the \<open>foldTrust\<close> enum-recognition step.\<close>

definition verifyEnumNames :: "service_ir \<Rightarrow> String.literal list" where
  "verifyEnumNames ir = map enumDeclName (serviceIrEnums ir)"

lemmas foldVerifier_code [code]                = foldVerifier_def
lemmas foldTrust_code [code]                   = foldTrust_def
lemmas invariantBody_code [code]               = invariantBody.simps
lemmas operationRequires_code [code]           = operationRequires.simps
lemmas operationEnsures_code [code]            = operationEnsures.simps
lemmas enumDeclName_code [code]                = enumDeclName.simps
lemmas serviceIrEntities_code [code]           = serviceIrEntities.simps
lemmas serviceIrEnums_code [code]              = serviceIrEnums.simps
lemmas serviceIrInvariants_code [code]         = serviceIrInvariants.simps
lemmas invariantBodies_code [code]             = invariantBodies_def
lemmas classifyGlobalVerifier_code [code]      = classifyGlobalVerifier_def
lemmas classifyInvariantVerifier_code [code]   = classifyInvariantVerifier_def
lemmas classifyRequiresVerifier_code [code]    = classifyRequiresVerifier_def
lemmas classifyEnabledVerifier_code [code]     = classifyEnabledVerifier_def
lemmas classifyPreservationVerifier_code [code] = classifyPreservationVerifier_def
lemmas classifyTemporalVerifier_code [code]    = classifyTemporalVerifier_def
lemmas trustGlobal_code [code]                 = trustGlobal_def
lemmas trustRequires_code [code]               = trustRequires_def
lemmas trustEnabled_code [code]                = trustEnabled_def
lemmas trustPreservation_code [code]           = trustPreservation_def
lemmas verifyEnumNames_code [code]             = verifyEnumNames_def

end
