theory IR
  imports Main "HOL.Rat"
begin

fun asciiToIntAcc :: "integer list \<Rightarrow> int \<Rightarrow> int option" where
  "asciiToIntAcc [] acc = Some acc"
| "asciiToIntAcc (c # cs) acc =
     (if 48 \<le> c \<and> c \<le> 57
        then asciiToIntAcc cs (acc * 10 + int_of_integer (c - 48))
        else None)"

definition decimalToRat :: "String.literal \<Rightarrow> rat option" where
  "decimalToRat s =
     (let cs = String.asciis_of_literal s;
          (neg, body) =
            (case cs of [] \<Rightarrow> (False, cs)
                      | c # rest \<Rightarrow> if c = 45 then (True, rest) else (False, cs));
          ipart = takeWhile (\<lambda>c. c \<noteq> 46) body;
          fpart = (case dropWhile (\<lambda>c. c \<noteq> 46) body of [] \<Rightarrow> [] | _ # rest \<Rightarrow> rest)
      in if ipart = [] \<and> fpart = [] then None
         else case (asciiToIntAcc ipart 0, asciiToIntAcc fpart 0) of
                (Some iv, Some fv) \<Rightarrow>
                  Some ((if neg then - 1 else 1) *
                        (of_int iv + of_int fv / of_int (10 ^ length fpart)))
              | _ \<Rightarrow> None)"

datatype (plugins only: code size) span_t = SpanT int int int int

type_synonym option_span = "span_t option"

datatype (plugins only: code size) type_expr =
    BoolT
  | IntT
  | EnumT "String.literal"
  | EntityT "String.literal"
  | RelationT "type_expr" "type_expr"

datatype (plugins only: code size) bool_bin_op =
    AndOp
  | OrOp
  | ImpliesOp
  | IffOp

datatype (plugins only: code size) arith_op =
    AddOp
  | SubOp
  | MulOp
  | DivOp

datatype (plugins only: code size) set_op =
    UnionOp
  | IntersectOp
  | DiffOp

datatype (plugins only: code size) cmp_op =
    EqOp
  | NeqOp
  | LtOp
  | LeOp
  | GtOp
  | GeOp

datatype (plugins only: code size) state_mode = SmPre | SmPost

datatype (plugins only: code size) expr =
    BoolLit bool "option_span"
  | IntLit int "option_span"
  | RealLit rat "option_span"
  | Ident "String.literal" "option_span"
  | UnNot "expr" "option_span"
  | UnNeg "expr" "option_span"
  | BoolBin "bool_bin_op" "expr" "expr" "option_span"
  | Arith "arith_op" "expr" "expr" "option_span"
  | Cmp "cmp_op" "expr" "expr" "option_span"
  | LetIn "String.literal" "expr" "expr" "option_span"
  | EnumAccess "String.literal" "String.literal" "option_span"
  | Member "expr" "String.literal" "option_span"
  | ForallEnum "String.literal" "String.literal" "expr" "option_span"
  | ForallRel "String.literal" "String.literal" "expr" "option_span"
  | ForallSet "String.literal" "expr" "expr" "option_span"
  | TheRel "String.literal" "String.literal" "expr" "option_span"
  | EntityBase "String.literal" "option_span"
  | Prime "expr" "option_span"
  | Pre "expr" "option_span"
  | CardRel "String.literal" "option_span"
  | IndexRel "expr" "expr" "option_span"
  | FieldAccess "expr" "String.literal" "option_span"
  | SetEmpty "option_span"
  | SetInsert "expr" "expr" "option_span"
  | SetMember "expr" "expr" "option_span"
  | SetBin "set_op" "expr" "expr" "option_span"
  | WithRec "expr" "String.literal" "expr" "option_span"
  | Ite "expr" "expr" "expr" "option_span"
  | NoneE "option_span"
  | SomeE "expr" "option_span"
  | StrLit "String.literal" "option_span"
  | Matches "expr" "String.literal" "option_span"
  | SeqEmpty "option_span"
  | SeqCons "expr" "expr" "option_span"
  | MapEmpty "option_span"
  | MapCons "expr" "expr" "expr" "option_span"
  | UStrPred "String.literal" "expr" "option_span"

text \<open>\<open>string_matches s pat\<close> is the regex-match predicate for \<open>Matches\<close>. It is
  deliberately \<^emph>\<open>abstract\<close> (no defining equation): formalising the full SMT-LIB
  regular-expression semantics in HOL is out of scope, and the trusted translator
  realises it concretely as Z3's \<open>str.in_re\<close> over the parsed pattern. Keeping it
  abstract is what makes the soundness theorem parametric in the matcher, so any
  realisation that \<open>eval\<close> and \<open>smtEval\<close> share (here, the same constant) is sound by
  construction; the (unused) extracted \<open>eval\<close>/\<open>smtEval\<close> reference interpreters get a
  serialisation stub in \<open>Codegen\<close>.\<close>
consts string_matches :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool"

text \<open>\<open>str_predicate name s\<close> is the uninterpreted built-in string predicate \<open>name\<close>
  (e.g.\ \<open>isValidURI\<close>) applied to string \<open>s\<close>. Like \<open>string_matches\<close> it is abstract: the
  trusted translator emits it as a Z3 uninterpreted boolean function, so the soundness
  theorem stays parametric in the predicate and any realisation \<open>eval\<close>/\<open>smtEval\<close> share
  is sound by construction. Verifying an obligation mentioning \<open>str_predicate name\<close> is
  thus a proof for every interpretation, which soundly over-approximates the intended
  built-in.\<close>
consts str_predicate :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool"

text \<open>\<open>is_builtin_pred nm\<close> marks \<open>nm\<close> as a reserved built-in string predicate (e.g.\
  \<open>isValidURI\<close>) - a name the surface language forbids as a user function/predicate. It
  gates \<open>lower\<close>'s lifting of \<open>CallF (IdentifierF nm) [arg]\<close> to \<open>UStrPred\<close>: only reserved
  built-ins are lifted, so a user call (which the reference semantics inlines) is never
  mistaken for an uninterpreted predicate. Soundness uses \<open>is_builtin_pred nm \<Longrightarrow>
  lookup_callee fs ps nm = None\<close> (reserved names are not user-defined).\<close>
definition is_builtin_pred :: "String.literal \<Rightarrow> bool" where
  "is_builtin_pred nm \<longleftrightarrow> nm = STR ''isValidURI'' \<or> nm = STR ''isValidEmail''"

text \<open>Issue #210 (M_L.4.l): \<open>IndexRel\<close>'s base is widened from a bare
  relation name to an arbitrary \<open>expr\<close>, so the operation-side
  \<open>pre(rel)[k]\<close> and \<open>rel'[k]\<close> shapes can lower into the verified subset.
  The intended bases are \<open>Ident rel\<close>, \<open>Pre (Ident rel)\<close>, and
  \<open>Prime (Ident rel)\<close>; \<open>peel_relation_ref\<close> recognises exactly those
  shapes and returns the relation name. Other bases evaluate to \<open>None\<close>
  in both \<open>eval\<close> and \<open>smtEval\<close>, preserving symmetry for the soundness
  theorem.\<close>

fun identName :: "expr \<Rightarrow> String.literal option" where
  "identName (Ident rel _) = Some rel"
| "identName _ = None"

fun peel_relation_ref :: "expr \<Rightarrow> String.literal option" where
  "peel_relation_ref (Ident rel _) = Some rel"
| "peel_relation_ref (Pre b _)     = identName b"
| "peel_relation_ref (Prime b _)   = identName b"
| "peel_relation_ref _             = None"

record field_decl =
  fd_name :: "String.literal"
  fd_ty   :: "type_expr"
  fd_span :: "option_span"

record entity_decl =
  ed_name   :: "String.literal"
  ed_fields :: "field_decl list"
  ed_span   :: "option_span"

record enum_decl =
  enm_name    :: "String.literal"
  enm_members :: "String.literal list"
  enm_span    :: "option_span"

text \<open>Issue #202: full input-language IR (canonical for Scala consumers).
  Coexists with the verified-subset above. The verified-subset stays as records
  (proof-internal); the full-language IR uses datatypes — Code_Target_Scala
  emits flat case classes (positional fields), no \<open>_ext[A]\<close> polymorphism.
  See research/10_translator_soundness.md \<section>17.\<close>

datatype (plugins only: code size) multiplicity = MultOne | MultLone | MultSome | MultSet

datatype (plugins only: code size) bin_op_full =
    BAnd | BOr | BImplies | BIff
  | BEq | BNeq
  | BLt | BGt | BLe | BGe
  | BIn | BNotIn
  | BSubset | BUnion | BIntersect | BDiff
  | BAdd | BSub | BMul | BDiv

datatype (plugins only: code size) un_op_full = UNot | UNegate | UCardinality | UPower

datatype (plugins only: code size) quant_kind_full = QAll | QSome | QNo | QExists

datatype (plugins only: code size) binding_kind_full = BkIn | BkColon

datatype (plugins only: code size) type_expr_full =
    NamedTypeF "String.literal" option_span
  | SetTypeF type_expr_full option_span
  | MapTypeF type_expr_full type_expr_full option_span
  | SeqTypeF type_expr_full option_span
  | OptionTypeF type_expr_full option_span
  | RelationTypeF type_expr_full multiplicity type_expr_full option_span

datatype (plugins only: code size) expr_full =
    BinaryOpF bin_op_full expr_full expr_full option_span
  | UnaryOpF un_op_full expr_full option_span
  | QuantifierF quant_kind_full "quantifier_binding_full list" expr_full option_span
  | SomeWrapF expr_full option_span
  | TheF "String.literal" expr_full expr_full option_span
  | FieldAccessF expr_full "String.literal" option_span
  | EnumAccessF expr_full "String.literal" option_span
  | IndexF expr_full expr_full option_span
  | CallF expr_full "expr_full list" option_span
  | PrimeF expr_full option_span
  | PreF expr_full option_span
  | WithF expr_full "field_assign_full list" option_span
  | IfF expr_full expr_full expr_full option_span
  | LetF "String.literal" expr_full expr_full option_span
  | LambdaF "String.literal" expr_full option_span
  | ConstructorF "String.literal" "field_assign_full list" option_span
  | SetLiteralF "expr_full list" option_span
  | MapLiteralF "map_entry_full list" option_span
  | SetComprehensionF "String.literal" expr_full expr_full option_span
  | SeqLiteralF "expr_full list" option_span
  | MatchesF expr_full "String.literal" option_span
  | IntLitF int option_span
  | FloatLitF "String.literal" option_span
  | StringLitF "String.literal" option_span
  | BoolLitF bool option_span
  | NoneLitF option_span
  | IdentifierF "String.literal" option_span
and field_assign_full =
    FieldAssignFull (fasName: "String.literal") (fasValue: expr_full) (fasSpan: option_span)
and map_entry_full =
    MapEntryFull (mpeKey: expr_full) (mpeValue: expr_full) (mpeSpan: option_span)
and quantifier_binding_full =
    QuantifierBindingFull (qbdVar: "String.literal") (qbdCollection: expr_full) (qbdKind: binding_kind_full) (qbdSpan: option_span)

datatype (plugins only: code size) field_decl_full =
    FieldDeclFull (fldName: "String.literal") (fldType: type_expr_full) (fldDefault: "expr_full option") (fldSpan: option_span)

datatype (plugins only: code size) entity_decl_full =
    EntityDeclFull (entName: "String.literal") (entParent: "String.literal option") (entFields: "field_decl_full list") (entInvariants: "expr_full list") (entSpan: option_span)

datatype (plugins only: code size) enum_decl_full =
    EnumDeclFull (enmName: "String.literal") (enmVariants: "String.literal list") (enmSpan: option_span)

datatype (plugins only: code size) type_alias_decl_full =
    TypeAliasDeclFull (talName: "String.literal") (talType: type_expr_full) (talConstraint: "expr_full option") (talSpan: option_span)

datatype (plugins only: code size) state_field_decl_full =
    StateFieldDeclFull (stfName: "String.literal") (stfType: type_expr_full) (stfSpan: option_span)

datatype (plugins only: code size) state_decl_full =
    StateDeclFull (stdFields: "state_field_decl_full list") (stdSpan: option_span)

datatype (plugins only: code size) param_decl_full =
    ParamDeclFull (prmName: "String.literal") (prmType: type_expr_full) (prmSpan: option_span)

datatype (plugins only: code size) operation_decl_full =
    OperationDeclFull (operName: "String.literal") (operInputs: "param_decl_full list") (operOutputs: "param_decl_full list") (operRequires: "expr_full list") (operEnsures: "expr_full list") (operSpan: option_span)

datatype (plugins only: code size) transition_rule_full =
    TransitionRuleFull (trlFrom: "String.literal") (trlTo: "String.literal") (trlVia: "String.literal") (trlGuard: "expr_full option") (trlSpan: option_span)

datatype (plugins only: code size) transition_decl_full =
    TransitionDeclFull (trnName: "String.literal") (trnEntity: "String.literal") (trnField: "String.literal") (trnRules: "transition_rule_full list") (trnSpan: option_span)

datatype (plugins only: code size) invariant_decl_full =
    InvariantDeclFull (invName: "String.literal option") (invBody: expr_full) (invSpan: option_span)

datatype (plugins only: code size) temporal_body =
    TbAlways expr_full
  | TbEventually expr_full
  | TbFairness expr_full
  | TbInvalid expr_full

datatype (plugins only: code size) temporal_decl_full =
    TemporalDeclFull (tmpName: "String.literal") (tmpBody: temporal_body) (tmpSpan: option_span)

datatype (plugins only: code size) fact_decl_full =
    FactDeclFull (fctName: "String.literal option") (fctBody: expr_full) (fctSpan: option_span)

datatype (plugins only: code size) function_decl_full =
    FunctionDeclFull (fncName: "String.literal") (fncParams: "param_decl_full list") (fncRetType: type_expr_full) (fncBody: expr_full) (fncSpan: option_span)

datatype (plugins only: code size) predicate_decl_full =
    PredicateDeclFull (prdName: "String.literal") (prdParams: "param_decl_full list") (prdBody: expr_full) (prdSpan: option_span)

text \<open>Convention values: parse-don't-validate shape, streamlined to a small
  fixed set of payload shapes. The parser dispatches on the property name
  at construction time and produces:
  \<^enum> \<open>CvOk pv\<close> when the value parses cleanly to one of the five
    \<open>parsed_value\<close> shapes (string, int, bool, string-pair, or
    runtime-expr for properties like \<open>http_header\<close> that accept
    non-literal expressions). The \<open>pv\<close> carries the validated payload;
    the property name on the enclosing \<open>convention_rule_full\<close>
    disambiguates which property it belongs to.
  \<^enum> \<open>CvBad failure\<close> when the parser recognised the property but the
    value was wrong-typed or out-of-range. Validator emits the diagnostic
    from the carried \<open>validation_failure\<close>.
  \<^enum> \<open>CvUnknown\<close> when the property name itself wasn't recognised.

  Three outcome variants \<times> five payload shapes \<gg> twelve per-property
  variants. Downstream consumers pattern-match on \<open>CvOk pv\<close> + a
  property-name filter — same level of specificity, far fewer cases.\<close>

datatype (plugins only: code size) validation_failure =
    ExpectedString
  | ExpectedInteger
  | ExpectedBoolean
  | EmptyString
  | BadHttpMethod "String.literal"
  | HttpStatusOutOfRange int
  | HttpPathMissingSlash
  | BadTestStrategy "String.literal"
  | BadStrategyFormat "String.literal"

datatype (plugins only: code size) parsed_value =
    PvString "String.literal"
  | PvInt    int
  | PvBool   bool
  | PvStrPair "String.literal" "String.literal"
  | PvExpr   expr_full
  \<comment> \<open>PvExpr carries an expression verbatim for properties (notably
    \<open>http_header\<close>) that accept runtime-evaluated values like
    \<open>output.url\<close> or bare identifiers — values the parser tolerates
    but can't reduce to a string at IR-build time.\<close>

datatype (plugins only: code size) convention_value =
    CvOk      parsed_value
  | CvBad     validation_failure expr_full   \<comment> \<open>why + raw expr (for diagnostics)\<close>
  | CvUnknown expr_full                       \<comment> \<open>unrecognised property name\<close>

datatype (plugins only: code size) convention_rule_full =
    ConventionRuleFull (cvrTarget: "String.literal") (cvrProperty: "String.literal") (cvrQualifier: "String.literal option")
                       (cvrValue: convention_value) (cvrSpan: option_span)

datatype (plugins only: code size) conventions_decl_full =
    ConventionsDeclFull (cvdRules: "convention_rule_full list") (cvdSpan: option_span)

datatype (plugins only: code size) service_ir_full =
    ServiceIRFull (svcName: "String.literal") (svcImports: "String.literal list")
                  (svcEntities: "entity_decl_full list") (svcEnums: "enum_decl_full list")
                  (svcTypeAliases: "type_alias_decl_full list") (svcState: "state_decl_full option")
                  (svcOperations: "operation_decl_full list") (svcTransitions: "transition_decl_full list")
                  (svcInvariants: "invariant_decl_full list") (svcTemporals: "temporal_decl_full list")
                  (svcFacts: "fact_decl_full list") (svcFunctions: "function_decl_full list")
                  (svcPredicates: "predicate_decl_full list") (svcConventions: "conventions_decl_full option")
                  (svcSpan: option_span)

text \<open>\<open>string_in_list\<close> is the monomorphic membership predicate over
  \<open>String.literal list\<close>. Hoisted to the top of the file because using
  \<open>list_ex (\<lambda>n. n = x) xs\<close> inside \<open>fun\<close> declarations triggers heavy
  pattern-overlap analysis — see \<open>createPatternOf\<close> elaboration cost in
  a baseline profile (~106 s for a single 8-line \<open>fun\<close>).\<close>

primrec string_in_list :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> bool" where
  "string_in_list y [] = False"
| "string_in_list y (x # xs) = (x = y \<or> string_in_list y xs)"

fun isLitFull :: "expr_full \<Rightarrow> bool" where
  "isLitFull (BoolLitF _ _)   = True"
| "isLitFull (IntLitF _ _)    = True"
| "isLitFull (FloatLitF _ _)  = True"
| "isLitFull (StringLitF _ _) = True"
| "isLitFull (NoneLitF _)     = True"
| "isLitFull _                = False"

text \<open>\<^bold>\<open>Generic tree-walk: \<open>allSubexprs\<close>\<close>. Returns every subterm of
  an \<open>expr_full\<close> (including the root) as a single list, via structural
  mutual \<open>fun\<close> recursion. Defined alongside \<open>subexprs\<close> (one-step
  children) so every binder-insensitive query / collector across IR /
  IR_Analysis / IR_Helpers / LintAnalysis can compose on it instead of
  re-enumerating the 28 constructors per walker.\<close>

fun allSubexprs :: "expr_full \<Rightarrow> expr_full list"
and allSubexprs_list :: "expr_full list \<Rightarrow> expr_full list"
and allSubexprs_fields :: "field_assign_full list \<Rightarrow> expr_full list"
and allSubexprs_entries :: "map_entry_full list \<Rightarrow> expr_full list"
and allSubexprs_bindings :: "quantifier_binding_full list \<Rightarrow> expr_full list"
where
  "allSubexprs (BinaryOpF op l r sp)        = BinaryOpF op l r sp # allSubexprs l @ allSubexprs r"
| "allSubexprs (UnaryOpF op e sp)           = UnaryOpF op e sp # allSubexprs e"
| "allSubexprs (FieldAccessF b f sp)        = FieldAccessF b f sp # allSubexprs b"
| "allSubexprs (EnumAccessF b e sp)         = EnumAccessF b e sp # allSubexprs b"
| "allSubexprs (IndexF b i sp)              = IndexF b i sp # allSubexprs b @ allSubexprs i"
| "allSubexprs (CallF c args sp)            = CallF c args sp # allSubexprs c @ allSubexprs_list args"
| "allSubexprs (PrimeF e sp)                = PrimeF e sp # allSubexprs e"
| "allSubexprs (PreF e sp)                  = PreF e sp # allSubexprs e"
| "allSubexprs (WithF b ups sp)             = WithF b ups sp # allSubexprs b @ allSubexprs_fields ups"
| "allSubexprs (IfF c t e sp)               = IfF c t e sp # allSubexprs c @ allSubexprs t @ allSubexprs e"
| "allSubexprs (LetF v val body sp)         = LetF v val body sp # allSubexprs val @ allSubexprs body"
| "allSubexprs (LambdaF p b sp)             = LambdaF p b sp # allSubexprs b"
| "allSubexprs (ConstructorF n fs sp)       = ConstructorF n fs sp # allSubexprs_fields fs"
| "allSubexprs (SetLiteralF xs sp)          = SetLiteralF xs sp # allSubexprs_list xs"
| "allSubexprs (MapLiteralF es sp)          = MapLiteralF es sp # allSubexprs_entries es"
| "allSubexprs (SetComprehensionF v d p sp) = SetComprehensionF v d p sp # allSubexprs d @ allSubexprs p"
| "allSubexprs (SeqLiteralF xs sp)          = SeqLiteralF xs sp # allSubexprs_list xs"
| "allSubexprs (MatchesF e pat sp)          = MatchesF e pat sp # allSubexprs e"
| "allSubexprs (SomeWrapF e sp)             = SomeWrapF e sp # allSubexprs e"
| "allSubexprs (TheF v d b sp)              = TheF v d b sp # allSubexprs d @ allSubexprs b"
| "allSubexprs (QuantifierF q bs body sp)   = QuantifierF q bs body sp # allSubexprs_bindings bs @ allSubexprs body"
| "allSubexprs (IdentifierF n sp)           = [IdentifierF n sp]"
| "allSubexprs (IntLitF n sp)               = [IntLitF n sp]"
| "allSubexprs (FloatLitF v sp)             = [FloatLitF v sp]"
| "allSubexprs (StringLitF v sp)            = [StringLitF v sp]"
| "allSubexprs (BoolLitF b sp)              = [BoolLitF b sp]"
| "allSubexprs (NoneLitF sp)                = [NoneLitF sp]"
| "allSubexprs_list []                                          = []"
| "allSubexprs_list (x # xs)                                    = allSubexprs x @ allSubexprs_list xs"
| "allSubexprs_fields []                                        = []"
| "allSubexprs_fields (FieldAssignFull n v sp # fs)             = allSubexprs v @ allSubexprs_fields fs"
| "allSubexprs_entries []                                       = []"
| "allSubexprs_entries (MapEntryFull k v sp # es)               = allSubexprs k @ allSubexprs v @ allSubexprs_entries es"
| "allSubexprs_bindings []                                      = []"
| "allSubexprs_bindings (QuantifierBindingFull n d a sp # bs)   = allSubexprs d @ allSubexprs_bindings bs"

text \<open>Phase 8 (verifier classifier port): \<open>requiresAlloy\<close> identifies
  \<open>expr_full\<close> shapes that contain a \<open>UPower\<close> (set-power) constructor anywhere
  in the expression tree. The verifier routes such checks to the Alloy backend
  (which models set power) instead of Z3.

  Definition is a one-line composition over \<open>allSubexprs\<close>; the four
  helper predicates (\<open>requiresAlloy_list\<close>, \<open>_fields\<close>,
  \<open>_entries\<close>, \<open>_bindings\<close>) are wrappers used by the original
  Soundness proofs — derived equations below provide the per-cons
  / per-constructor simp rules that those proofs rely on, so existing
  Soundness theorems continue to discharge without restructuring.\<close>

fun isUPowerUnary :: "expr_full \<Rightarrow> bool" where
  "isUPowerUnary (UnaryOpF UPower _ _) = True"
| "isUPowerUnary _                     = False"

definition requiresAlloy :: "expr_full \<Rightarrow> bool" where
  "requiresAlloy e = list_ex isUPowerUnary (allSubexprs e)"

text \<open>Symmetric helpers over the wrapper-list types: each uses the
  matching \<open>allSubexprs_*\<close> flatten so the per-cons simp rules below
  fall out by definition unfolding + the \<open>allSubexprs.simps\<close>
  equations (which are already simp by \<open>fun\<close> auto-generation).\<close>

definition requiresAlloy_list :: "expr_full list \<Rightarrow> bool" where
  "requiresAlloy_list xs = list_ex isUPowerUnary (allSubexprs_list xs)"

definition requiresAlloy_fields :: "field_assign_full list \<Rightarrow> bool" where
  "requiresAlloy_fields fs = list_ex isUPowerUnary (allSubexprs_fields fs)"

definition requiresAlloy_entries :: "map_entry_full list \<Rightarrow> bool" where
  "requiresAlloy_entries es = list_ex isUPowerUnary (allSubexprs_entries es)"

definition requiresAlloy_bindings :: "quantifier_binding_full list \<Rightarrow> bool" where
  "requiresAlloy_bindings bs = list_ex isUPowerUnary (allSubexprs_bindings bs)"

text \<open>Derived equations: re-establish the per-constructor / per-cons
  rewrite rules that the original mutual \<open>fun\<close> would have produced as
  auto-simps. With these added to \<open>[simp]\<close>, downstream proofs in
  \<open>IR_Helpers\<close> / \<open>Soundness\<close> continue to discharge unchanged.\<close>

text \<open>Per-cons simp rules for the wrapper-list helpers fall out directly
  from \<open>allSubexprs_*\<close> unfolding + \<open>list_ex\<close> simplification.\<close>

lemma requiresAlloy_list_simps [simp]:
  "requiresAlloy_list [] = False"
  "requiresAlloy_list (x # xs) = (requiresAlloy x \<or> requiresAlloy_list xs)"
  by (auto simp: requiresAlloy_list_def requiresAlloy_def)

lemma requiresAlloy_fields_simps [simp]:
  "requiresAlloy_fields [] = False"
  "requiresAlloy_fields (FieldAssignFull nm v sp # fs) =
     (requiresAlloy v \<or> requiresAlloy_fields fs)"
  by (auto simp: requiresAlloy_fields_def requiresAlloy_def)

lemma requiresAlloy_entries_simps [simp]:
  "requiresAlloy_entries [] = False"
  "requiresAlloy_entries (MapEntryFull k v sp # es) =
     (requiresAlloy k \<or> requiresAlloy v \<or> requiresAlloy_entries es)"
  by (auto simp: requiresAlloy_entries_def requiresAlloy_def)

lemma requiresAlloy_bindings_simps [simp]:
  "requiresAlloy_bindings [] = False"
  "requiresAlloy_bindings (QuantifierBindingFull nm d a sp # bs) =
     (requiresAlloy d \<or> requiresAlloy_bindings bs)"
  by (auto simp: requiresAlloy_bindings_def requiresAlloy_def)

text \<open>Per-constructor simp rules for \<open>requiresAlloy\<close> — each is one-line
  \<open>simp add: requiresAlloy_def\<close> because the \<open>allSubexprs.simps\<close> rules
  are already in the global simp set (auto-generated by \<open>fun\<close>) and the
  helper-list \<open>requires*\<close> definitions point at the matching
  \<open>allSubexprs_*\<close>.\<close>

lemma requiresAlloy_UnaryOpF [simp]:
  "requiresAlloy (UnaryOpF op e sp) = (op = UPower \<or> requiresAlloy e)"
  by (cases op) (auto simp: requiresAlloy_def)

lemma requiresAlloy_BinaryOpF [simp]:
  "requiresAlloy (BinaryOpF op l r sp) = (requiresAlloy l \<or> requiresAlloy r)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_QuantifierF [simp]:
  "requiresAlloy (QuantifierF q bs body sp) = (requiresAlloy_bindings bs \<or> requiresAlloy body)"
  by (simp add: requiresAlloy_def requiresAlloy_bindings_def)

lemma requiresAlloy_SomeWrapF [simp]: "requiresAlloy (SomeWrapF x sp) = requiresAlloy x"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_TheF [simp]:
  "requiresAlloy (TheF nm d body sp) = (requiresAlloy d \<or> requiresAlloy body)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_FieldAccessF [simp]:
  "requiresAlloy (FieldAccessF base fld sp) = requiresAlloy base"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_EnumAccessF [simp]:
  "requiresAlloy (EnumAccessF base mem sp) = requiresAlloy base"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_IndexF [simp]:
  "requiresAlloy (IndexF base idx sp) = (requiresAlloy base \<or> requiresAlloy idx)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_CallF [simp]:
  "requiresAlloy (CallF callee args sp) = (requiresAlloy callee \<or> requiresAlloy_list args)"
  by (simp add: requiresAlloy_def requiresAlloy_list_def)

lemma requiresAlloy_PrimeF [simp]: "requiresAlloy (PrimeF x sp) = requiresAlloy x"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_PreF [simp]: "requiresAlloy (PreF x sp) = requiresAlloy x"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_WithF [simp]:
  "requiresAlloy (WithF base upds sp) = (requiresAlloy base \<or> requiresAlloy_fields upds)"
  by (simp add: requiresAlloy_def requiresAlloy_fields_def)

lemma requiresAlloy_IfF [simp]:
  "requiresAlloy (IfF c t f sp) = (requiresAlloy c \<or> requiresAlloy t \<or> requiresAlloy f)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_LetF [simp]:
  "requiresAlloy (LetF nm val body sp) = (requiresAlloy val \<or> requiresAlloy body)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_LambdaF [simp]:
  "requiresAlloy (LambdaF param body sp) = requiresAlloy body"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_ConstructorF [simp]:
  "requiresAlloy (ConstructorF nm fs sp) = requiresAlloy_fields fs"
  by (simp add: requiresAlloy_def requiresAlloy_fields_def)

lemma requiresAlloy_SetLiteralF [simp]:
  "requiresAlloy (SetLiteralF xs sp) = requiresAlloy_list xs"
  by (simp add: requiresAlloy_def requiresAlloy_list_def)

lemma requiresAlloy_MapLiteralF [simp]:
  "requiresAlloy (MapLiteralF es sp) = requiresAlloy_entries es"
  by (simp add: requiresAlloy_def requiresAlloy_entries_def)

lemma requiresAlloy_SetComprehensionF [simp]:
  "requiresAlloy (SetComprehensionF nm d pred sp) = (requiresAlloy d \<or> requiresAlloy pred)"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_SeqLiteralF [simp]:
  "requiresAlloy (SeqLiteralF xs sp) = requiresAlloy_list xs"
  by (simp add: requiresAlloy_def requiresAlloy_list_def)

lemma requiresAlloy_MatchesF [simp]:
  "requiresAlloy (MatchesF x pat sp) = requiresAlloy x"
  by (simp add: requiresAlloy_def)

lemma requiresAlloy_IntLitF [simp]: "requiresAlloy (IntLitF n sp) = False"
  by (simp add: requiresAlloy_def)
lemma requiresAlloy_FloatLitF [simp]: "requiresAlloy (FloatLitF f sp) = False"
  by (simp add: requiresAlloy_def)
lemma requiresAlloy_StringLitF [simp]: "requiresAlloy (StringLitF s sp) = False"
  by (simp add: requiresAlloy_def)
lemma requiresAlloy_BoolLitF [simp]: "requiresAlloy (BoolLitF b sp) = False"
  by (simp add: requiresAlloy_def)
lemma requiresAlloy_NoneLitF [simp]: "requiresAlloy (NoneLitF sp) = False"
  by (simp add: requiresAlloy_def)
lemma requiresAlloy_IdentifierF [simp]: "requiresAlloy (IdentifierF n sp) = False"
  by (simp add: requiresAlloy_def)

text \<open>Phase 9a (structural primitives): \<open>subexprs\<close> returns the direct
  \<open>expr_full\<close> children of an expression. Replaces ad-hoc 27-arm structural
  folds in lint walkers, classifier helpers, narration / diagnostic
  collectors, and per-target translators with one proven primitive. New
  consumers compose: e.g. \<open>def visit(e) = ...; subexprs(e).foreach(visit)\<close>.\<close>

fun subexprs :: "expr_full \<Rightarrow> expr_full list"
and subexprs_fields :: "field_assign_full list \<Rightarrow> expr_full list"
and subexprs_entries :: "map_entry_full list \<Rightarrow> expr_full list"
and subexprs_bindings :: "quantifier_binding_full list \<Rightarrow> expr_full list"
where
  "subexprs (BinaryOpF _ l r _)             = [l, r]"
| "subexprs (UnaryOpF _ e _)                = [e]"
| "subexprs (QuantifierF _ bs body _)       = subexprs_bindings bs @ [body]"
| "subexprs (SomeWrapF e _)                 = [e]"
| "subexprs (TheF _ d b _)                  = [d, b]"
| "subexprs (FieldAccessF b _ _)            = [b]"
| "subexprs (EnumAccessF b _ _)             = [b]"
| "subexprs (IndexF b i _)                  = [b, i]"
| "subexprs (CallF c args _)                = c # args"
| "subexprs (PrimeF e _)                    = [e]"
| "subexprs (PreF e _)                      = [e]"
| "subexprs (WithF b ups _)                 = b # subexprs_fields ups"
| "subexprs (IfF c t e _)                   = [c, t, e]"
| "subexprs (LetF _ v b _)                  = [v, b]"
| "subexprs (LambdaF _ b _)                 = [b]"
| "subexprs (ConstructorF _ fs _)           = subexprs_fields fs"
| "subexprs (SetLiteralF xs _)              = xs"
| "subexprs (MapLiteralF es _)              = subexprs_entries es"
| "subexprs (SetComprehensionF _ d p _)     = [d, p]"
| "subexprs (SeqLiteralF xs _)              = xs"
| "subexprs (MatchesF e _ _)                = [e]"
| "subexprs (IntLitF _ _)                   = []"
| "subexprs (FloatLitF _ _)                 = []"
| "subexprs (StringLitF _ _)                = []"
| "subexprs (BoolLitF _ _)                  = []"
| "subexprs (NoneLitF _)                    = []"
| "subexprs (IdentifierF _ _)               = []"
| "subexprs_fields []                                          = []"
| "subexprs_fields (FieldAssignFull _ v _ # fs)                = v # subexprs_fields fs"
| "subexprs_entries []                                         = []"
| "subexprs_entries (MapEntryFull k v _ # es)                  = k # v # subexprs_entries es"
| "subexprs_bindings []                                        = []"
| "subexprs_bindings (QuantifierBindingFull _ d _ _ # bs)      = d # subexprs_bindings bs"

text \<open>Phase 9b (structural primitives): \<open>stripSpans\<close> erases every
  \<open>option_span\<close> in an \<open>expr_full\<close> tree (and the three child-list-bearing
  companions) to \<open>None\<close>, leaving structure and scalar payloads intact.
  \<open>typeStripSpans\<close> does the same for \<open>type_expr_full\<close>. Two spans-erased
  values are HOL-equal iff the originals are structurally equal modulo source
  position — so consumers compare span-insensitive shape by extracted
  structural \<open>equals\<close> instead of hand-rolling a 50-arm string fingerprint
  that must track every \<open>expr_full\<close> constructor by hand (lint L04 operation
  overlap). Total structural maps; termination is automatic.\<close>

fun stripSpans :: "expr_full \<Rightarrow> expr_full"
and stripSpans_list :: "expr_full list \<Rightarrow> expr_full list"
and stripSpans_fields :: "field_assign_full list \<Rightarrow> field_assign_full list"
and stripSpans_entries :: "map_entry_full list \<Rightarrow> map_entry_full list"
and stripSpans_bindings :: "quantifier_binding_full list \<Rightarrow> quantifier_binding_full list"
where
  "stripSpans (BinaryOpF op l r _)        = BinaryOpF op (stripSpans l) (stripSpans r) None"
| "stripSpans (UnaryOpF op e _)           = UnaryOpF op (stripSpans e) None"
| "stripSpans (QuantifierF k bs body _)   = QuantifierF k (stripSpans_bindings bs) (stripSpans body) None"
| "stripSpans (SomeWrapF e _)             = SomeWrapF (stripSpans e) None"
| "stripSpans (TheF v d b _)              = TheF v (stripSpans d) (stripSpans b) None"
| "stripSpans (FieldAccessF b f _)        = FieldAccessF (stripSpans b) f None"
| "stripSpans (EnumAccessF b m _)         = EnumAccessF (stripSpans b) m None"
| "stripSpans (IndexF b i _)              = IndexF (stripSpans b) (stripSpans i) None"
| "stripSpans (CallF c args _)            = CallF (stripSpans c) (stripSpans_list args) None"
| "stripSpans (PrimeF e _)                = PrimeF (stripSpans e) None"
| "stripSpans (PreF e _)                  = PreF (stripSpans e) None"
| "stripSpans (WithF b ups _)             = WithF (stripSpans b) (stripSpans_fields ups) None"
| "stripSpans (IfF c t e _)               = IfF (stripSpans c) (stripSpans t) (stripSpans e) None"
| "stripSpans (LetF x v b _)              = LetF x (stripSpans v) (stripSpans b) None"
| "stripSpans (LambdaF p b _)             = LambdaF p (stripSpans b) None"
| "stripSpans (ConstructorF n fs _)       = ConstructorF n (stripSpans_fields fs) None"
| "stripSpans (SetLiteralF xs _)          = SetLiteralF (stripSpans_list xs) None"
| "stripSpans (MapLiteralF es _)          = MapLiteralF (stripSpans_entries es) None"
| "stripSpans (SetComprehensionF v d p _) = SetComprehensionF v (stripSpans d) (stripSpans p) None"
| "stripSpans (SeqLiteralF xs _)          = SeqLiteralF (stripSpans_list xs) None"
| "stripSpans (MatchesF e pat _)          = MatchesF (stripSpans e) pat None"
| "stripSpans (IntLitF n _)               = IntLitF n None"
| "stripSpans (FloatLitF v _)             = FloatLitF v None"
| "stripSpans (StringLitF v _)            = StringLitF v None"
| "stripSpans (BoolLitF b _)              = BoolLitF b None"
| "stripSpans (NoneLitF _)                = NoneLitF None"
| "stripSpans (IdentifierF x _)           = IdentifierF x None"
| "stripSpans_list []                                    = []"
| "stripSpans_list (x # xs)                              = stripSpans x # stripSpans_list xs"
| "stripSpans_fields []                                  = []"
| "stripSpans_fields (FieldAssignFull n v _ # fs)        = FieldAssignFull n (stripSpans v) None # stripSpans_fields fs"
| "stripSpans_entries []                                 = []"
| "stripSpans_entries (MapEntryFull k v _ # es)          = MapEntryFull (stripSpans k) (stripSpans v) None # stripSpans_entries es"
| "stripSpans_bindings []                                = []"
| "stripSpans_bindings (QuantifierBindingFull v d k _ # bs) = QuantifierBindingFull v (stripSpans d) k None # stripSpans_bindings bs"

fun typeStripSpans :: "type_expr_full \<Rightarrow> type_expr_full" where
  "typeStripSpans (NamedTypeF n _)        = NamedTypeF n None"
| "typeStripSpans (SetTypeF t _)          = SetTypeF (typeStripSpans t) None"
| "typeStripSpans (MapTypeF k v _)        = MapTypeF (typeStripSpans k) (typeStripSpans v) None"
| "typeStripSpans (SeqTypeF t _)          = SeqTypeF (typeStripSpans t) None"
| "typeStripSpans (OptionTypeF t _)       = OptionTypeF (typeStripSpans t) None"
| "typeStripSpans (RelationTypeF f m t _) = RelationTypeF (typeStripSpans f) m (typeStripSpans t) None"

text \<open>Phase 9c: \<open>binOpToTs\<close> is the single source of truth for the
  surface spelling of each \<open>bin_op_full\<close>. Consumed by the verifier's
  diagnostic messages (\<open>z3.Translator\<close>); replaces a hand 20-arm table that
  had to track every \<open>bin_op_full\<close> constructor by hand.\<close>

fun binOpToTs :: "bin_op_full \<Rightarrow> String.literal" where
  "binOpToTs BAnd       = STR ''and''"
| "binOpToTs BOr        = STR ''or''"
| "binOpToTs BImplies   = STR ''implies''"
| "binOpToTs BIff       = STR ''iff''"
| "binOpToTs BEq        = STR ''=''"
| "binOpToTs BNeq       = STR ''!=''"
| "binOpToTs BLt        = STR ''<''"
| "binOpToTs BGt        = STR ''>''"
| "binOpToTs BLe        = STR ''<=''"
| "binOpToTs BGe        = STR ''>=''"
| "binOpToTs BIn        = STR ''in''"
| "binOpToTs BNotIn     = STR ''not_in''"
| "binOpToTs BSubset    = STR ''subset''"
| "binOpToTs BUnion     = STR ''union''"
| "binOpToTs BIntersect = STR ''intersect''"
| "binOpToTs BDiff      = STR ''minus''"
| "binOpToTs BAdd       = STR ''+''"
| "binOpToTs BSub       = STR ''-''"
| "binOpToTs BMul       = STR ''*''"
| "binOpToTs BDiv       = STR ''/''"

fun spanOf :: "expr_full \<Rightarrow> option_span" where
  "spanOf (BinaryOpF _ _ _ sp)         = sp"
| "spanOf (UnaryOpF _ _ sp)            = sp"
| "spanOf (QuantifierF _ _ _ sp)       = sp"
| "spanOf (SomeWrapF _ sp)             = sp"
| "spanOf (TheF _ _ _ sp)              = sp"
| "spanOf (FieldAccessF _ _ sp)        = sp"
| "spanOf (EnumAccessF _ _ sp)         = sp"
| "spanOf (IndexF _ _ sp)              = sp"
| "spanOf (CallF _ _ sp)               = sp"
| "spanOf (PrimeF _ sp)                = sp"
| "spanOf (PreF _ sp)                  = sp"
| "spanOf (WithF _ _ sp)               = sp"
| "spanOf (IfF _ _ _ sp)               = sp"
| "spanOf (LetF _ _ _ sp)              = sp"
| "spanOf (LambdaF _ _ sp)             = sp"
| "spanOf (ConstructorF _ _ sp)        = sp"
| "spanOf (SetLiteralF _ sp)           = sp"
| "spanOf (MapLiteralF _ sp)           = sp"
| "spanOf (SetComprehensionF _ _ _ sp) = sp"
| "spanOf (SeqLiteralF _ sp)           = sp"
| "spanOf (MatchesF _ _ sp)            = sp"
| "spanOf (IntLitF _ sp)               = sp"
| "spanOf (FloatLitF _ sp)             = sp"
| "spanOf (StringLitF _ sp)            = sp"
| "spanOf (BoolLitF _ sp)              = sp"
| "spanOf (NoneLitF sp)                = sp"
| "spanOf (IdentifierF _ sp)           = sp"

fun flattenAnd :: "expr_full \<Rightarrow> expr_full list" where
  "flattenAnd (BinaryOpF BAnd l r _) = flattenAnd l @ flattenAnd r"
| "flattenAnd e                      = [e]"

fun flattenEnsuresExpr :: "expr_full \<Rightarrow> expr_full list" where
  "flattenEnsuresExpr (BinaryOpF BAnd l r _) =
     flattenEnsuresExpr l @ flattenEnsuresExpr r"
| "flattenEnsuresExpr (LetF _ v b _) =
     flattenEnsuresExpr v @ flattenEnsuresExpr b"
| "flattenEnsuresExpr e = [e]"

definition flattenEnsures :: "expr_full list \<Rightarrow> expr_full list" where
  "flattenEnsures es \<equiv> List.concat (map flattenEnsuresExpr es)"

definition flattenAndAll :: "expr_full list \<Rightarrow> expr_full list" where
  "flattenAndAll es \<equiv> List.concat (map flattenAnd es)"

fun rootIdentifier :: "expr_full \<Rightarrow> String.literal option" where
  "rootIdentifier (IdentifierF n _)        = Some n"
| "rootIdentifier (IndexF base _ _)        = rootIdentifier base"
| "rootIdentifier (FieldAccessF base _ _)  = rootIdentifier base"
| "rootIdentifier _                        = None"

fun dom_arg :: "expr_full \<Rightarrow> String.literal option" where
  "dom_arg (CallF c args _) =
     (case (c, args) of
        (IdentifierF d _, [IdentifierF x _]) \<Rightarrow> (if d = STR ''dom'' then Some x else None)
      | _ \<Rightarrow> None)"
| "dom_arg _ = None"

lemma dom_arg_SomeD:
  "dom_arg e = Some x
     \<Longrightarrow> \<exists>sp1 sp2 sp. e = CallF (IdentifierF (STR ''dom'') sp1) [IdentifierF x sp2] sp"
  by (erule dom_arg.elims; auto split: expr_full.splits list.splits if_splits prod.splits)

lemmas allSubexprs_code [code]            = allSubexprs.simps allSubexprs_list.simps
                                             allSubexprs_fields.simps
                                             allSubexprs_entries.simps
                                             allSubexprs_bindings.simps
lemmas isUPowerUnary_code [code]          = isUPowerUnary.simps
lemmas requiresAlloy_code [code]          = requiresAlloy_def
lemmas requiresAlloy_list_code [code]     = requiresAlloy_list_def
lemmas requiresAlloy_fields_code [code]   = requiresAlloy_fields_def
lemmas requiresAlloy_entries_code [code]  = requiresAlloy_entries_def
lemmas requiresAlloy_bindings_code [code] = requiresAlloy_bindings_def

end
