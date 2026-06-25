theory IR
  imports Names "HOL.Rat"
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

datatype (plugins only: code size) schema_type =
    BoolT
  | IntT
  | RealT
  | StrT
  | EnumT "String.literal"
  | EntityT "String.literal"
  | RelationT "schema_type" "schema_type"
  | OptionT "schema_type"
  | SeqT "schema_type"
  | MapT "schema_type" "schema_type"

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

text \<open>\<open>string_matches s pat\<close> is the regex-match predicate for \<open>MatchesF\<close>. It is
  deliberately \<^emph>\<open>abstract\<close> (no defining equation): formalising the full SMT-LIB
  regular-expression semantics in HOL is out of scope, and the trusted translator
  realises it concretely as Z3's \<open>str.in_re\<close> over the parsed pattern. Keeping it
  abstract is what makes the soundness theorem parametric in the matcher, so any
  realisation that \<open>eval\<close> and \<open>smtEval\<close> share (here, the same constant) is sound
  by construction; the (unused) extracted reference interpreters get a serialisation
  stub in \<open>Codegen\<close>.\<close>
consts string_matches :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool"

text \<open>\<open>str_predicate name s\<close> is the uninterpreted built-in string predicate \<open>name\<close>
  (e.g.\ \<open>isValidURI\<close>) applied to string \<open>s\<close>. Like \<open>string_matches\<close> it is abstract: the
  trusted translator emits it as a Z3 uninterpreted boolean function, so the soundness
  theorem stays parametric in the predicate and any realisation \<open>eval\<close>/\<open>smtEval\<close>
  share is sound by construction. Verifying an obligation mentioning
  \<open>str_predicate name\<close> is thus a proof for every interpretation, which soundly
  over-approximates the intended built-in.\<close>
consts str_predicate :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool"

text \<open>\<open>is_builtin_pred nm\<close> marks \<open>nm\<close> as a reserved built-in string predicate (e.g.\
  \<open>isValidURI\<close>) - a name the surface language forbids as a user function/predicate. It
  gates \<open>lower\<close>'s lifting of \<open>CallF (IdentifierF nm) [arg]\<close> to \<open>UStrPred\<close>: only reserved
  built-ins are lifted, so a user call (which the reference semantics inlines) is never
  mistaken for an uninterpreted predicate. Soundness uses \<open>is_builtin_pred nm \<Longrightarrow>
  lookup_callee fs ps nm = None\<close> (reserved names are not user-defined).\<close>
definition is_builtin_pred :: "String.literal \<Rightarrow> bool" where
  "is_builtin_pred nm \<longleftrightarrow> nm = STR ''isValidURI'' \<or> nm = STR ''isValidEmail''"

text \<open>\<open>builtin_const_val name\<close> is the uninterpreted value of the reserved 0-argument
  built-in \<open>name\<close> (e.g.\ \<open>now\<close>, the current timestamp). Like \<open>str_predicate\<close> it is
  abstract: the trusted translator emits it as a Z3 uninterpreted constant, so soundness
  stays parametric in the value and any realisation \<open>eval\<close>/\<open>smtEval\<close> share is sound by
  construction - a proof for every interpretation soundly over-approximates the builtin.\<close>
consts builtin_const_val :: "String.literal \<Rightarrow> int"

definition is_builtin_const :: "String.literal \<Rightarrow> bool" where
  "is_builtin_const nm \<longleftrightarrow> nm = STR ''now''"

text \<open>\<open>builtin_str_func name s\<close> is the uninterpreted value of the reserved 1-argument
  built-in string function \<open>name\<close> (e.g.\ \<open>hash\<close>) applied to string \<open>s\<close>. Like
  \<open>str_predicate\<close> it is abstract - the trusted translator emits it as a Z3 uninterpreted
  string function - so soundness stays parametric in the function and any realisation
  \<open>eval\<close>/\<open>smtEval\<close> share is sound by construction. \<open>is_builtin_func nm\<close> gates lifting
  \<open>CallF (IdentifierF nm) [arg]\<close> to \<open>UStrFunc\<close>: only reserved built-ins are lifted.\<close>
consts builtin_str_func :: "String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal"

definition is_builtin_func :: "String.literal \<Rightarrow> bool" where
  "is_builtin_func nm \<longleftrightarrow> nm = STR ''hash''"

text \<open>\<open>builtin_int_func name n\<close> is the uninterpreted value of a reserved 1-argument
  built-in \<open>int \<Rightarrow> int\<close> function \<open>name\<close> (the duration constructors \<open>seconds\<close>,
  \<open>minutes\<close>, \<open>hours\<close>, \<open>days\<close>, \<open>weeks\<close>, each a duration in epoch seconds)
  applied to \<open>n\<close>. The integer analogue of \<open>builtin_str_func\<close>: abstract, so soundness
  stays parametric in the realisation. \<open>is_builtin_int_func nm\<close> gates lifting
  \<open>CallF (IdentifierF nm) [arg]\<close> to \<open>UIntFunc\<close>.\<close>
consts builtin_int_func :: "String.literal \<Rightarrow> int \<Rightarrow> int"

definition is_builtin_int_func :: "String.literal \<Rightarrow> bool" where
  "is_builtin_int_func nm \<longleftrightarrow> nm = STR ''seconds'' \<or> nm = STR ''minutes''
                              \<or> nm = STR ''hours'' \<or> nm = STR ''days''
                              \<or> nm = STR ''weeks''"

text \<open>\<open>str_length s\<close> is the length of string \<open>s\<close>, the meaning of the reserved
  builtin \<open>len(s)\<close>. Abstract here (like \<open>builtin_str_func\<close>); the codegen serialisation
  is Scala \<open>.length\<close> (the real length, for the reference interpreter). \<open>len\<close> is
  vacuous-on-eval, so the trusted Z3 translator is free to encode it as an uninterpreted
  Int function -- which it does, rather than native \<open>str.len\<close>, whose exact-length
  constraints make Z3's string solver materialise fixed-length strings -- without
  affecting soundness.\<close>
consts str_length :: "String.literal \<Rightarrow> int"

record schema_field_decl =
  fd_name :: "String.literal"
  fd_ty   :: "schema_type"
  fd_span :: "option_span"

record schema_entity_decl =
  ed_name   :: "String.literal"
  ed_fields :: "schema_field_decl list"
  ed_span   :: "option_span"

record schema_enum_decl =
  enm_name    :: "String.literal"
  enm_members :: "String.literal list"
  enm_span    :: "option_span"

text \<open>Issue #202: full input-language IR (canonical for Scala consumers).
  Coexists with the verified-subset above. The verified-subset stays as records
  (proof-internal); the full-language IR uses datatypes — Code_Target_Scala
  emits flat case classes (positional fields), no \<open>_ext[A]\<close> polymorphism.
  See research/10_translator_soundness.md \<section>17.\<close>

datatype (plugins only: code size) multiplicity = MultOne | MultLone | MultSome | MultSet

datatype (plugins only: code size) bin_op =
    BAnd | BOr | BImplies | BIff
  | BEq | BNeq
  | BLt | BGt | BLe | BGe
  | BIn | BNotIn
  | BSubset | BUnion | BIntersect | BDiff
  | BAdd | BSub | BMul | BDiv

datatype (plugins only: code size) un_op = UNot | UNegate | UCardinality | UPower

datatype (plugins only: code size) quant_kind = QAll | QSome | QNo | QExists

datatype (plugins only: code size) binding_kind = BkIn | BkColon

datatype (plugins only: code size) type_expr =
    NamedTypeF "String.literal" option_span
  | SetTypeF type_expr option_span
  | MapTypeF type_expr type_expr option_span
  | SeqTypeF type_expr option_span
  | OptionTypeF type_expr option_span
  | RelationTypeF type_expr multiplicity type_expr option_span

datatype (plugins only: code size) expr =
    BinaryOpF bin_op expr expr option_span
  | UnaryOpF un_op expr option_span
  | QuantifierF quant_kind "quantifier_binding list" expr option_span
  | SomeWrapF expr option_span
  | TheF "String.literal" expr expr option_span
  | FieldAccessF expr "String.literal" option_span
  | EnumAccessF expr "String.literal" option_span
  | IndexF expr expr option_span
  | CallF expr "expr list" option_span
  | PrimeF expr option_span
  | PreF expr option_span
  | WithF expr "field_assign list" option_span
  | IfF expr expr expr option_span
  | LetF "String.literal" expr expr option_span
  | LambdaF "String.literal" expr option_span
  | ConstructorF "String.literal" "field_assign list" option_span
  | SetLiteralF "expr list" option_span
  | MapLiteralF "map_entry list" option_span
  | SetComprehensionF "String.literal" expr expr option_span
  | SeqLiteralF "expr list" option_span
  | MatchesF expr "String.literal" option_span
  | IntLitF int option_span
  | FloatLitF "String.literal" option_span
  | StringLitF "String.literal" option_span
  | BoolLitF bool option_span
  | NoneLitF option_span
  | IdentifierF "String.literal" option_span
and field_assign =
    FieldAssignFull (fasName: "String.literal") (fasValue: expr) (fasSpan: option_span)
and map_entry =
    MapEntryFull (mpeKey: expr) (mpeValue: expr) (mpeSpan: option_span)
and quantifier_binding =
    QuantifierBindingFull (qbdVar: "String.literal") (qbdCollection: expr) (qbdKind: binding_kind) (qbdSpan: option_span)

datatype (plugins only: code size) field_decl =
    FieldDeclFull (fldName: "String.literal") (fldType: type_expr) (fldDefault: "expr option") (fldSpan: option_span)

datatype (plugins only: code size) entity_decl =
    EntityDeclFull (entName: "String.literal") (entParent: "String.literal option") (entFields: "field_decl list") (entInvariants: "expr list") (entSpan: option_span)

datatype (plugins only: code size) enum_decl =
    EnumDeclFull (enmName: "String.literal") (enmVariants: "String.literal list") (enmSpan: option_span)

datatype (plugins only: code size) type_alias_decl =
    TypeAliasDeclFull (talName: "String.literal") (talType: type_expr) (talConstraint: "expr option") (talSpan: option_span)

datatype (plugins only: code size) state_field_decl =
    StateFieldDeclFull (stfName: "String.literal") (stfType: type_expr) (stfSpan: option_span)

datatype (plugins only: code size) state_decl =
    StateDeclFull (stdFields: "state_field_decl list") (stdSpan: option_span)

datatype (plugins only: code size) param_decl =
    ParamDeclFull (prmName: "String.literal") (prmType: type_expr) (prmSpan: option_span)

datatype (plugins only: code size) operation_decl =
    OperationDeclFull (operName: "String.literal") (operInputs: "param_decl list") (operOutputs: "param_decl list") (operRequires: "expr list") (operEnsures: "expr list") (operRequiresAuth: "(String.literal list) option") (operSpan: option_span)

datatype (plugins only: code size) transition_rule =
    TransitionRuleFull (trlFrom: "String.literal") (trlTo: "String.literal") (trlVia: "String.literal") (trlGuard: "expr option") (trlSpan: option_span)

datatype (plugins only: code size) transition_decl =
    TransitionDeclFull (trnName: "String.literal") (trnEntity: "String.literal") (trnField: "String.literal") (trnRules: "transition_rule list") (trnSpan: option_span)

datatype (plugins only: code size) invariant_decl =
    InvariantDeclFull (invName: "String.literal option") (invBody: expr) (invSpan: option_span)

datatype (plugins only: code size) temporal_body =
    TbAlways expr
  | TbEventually expr
  | TbFairness expr
  | TbInvalid expr

datatype (plugins only: code size) temporal_decl =
    TemporalDeclFull (tmpName: "String.literal") (tmpBody: temporal_body) (tmpSpan: option_span)

datatype (plugins only: code size) fact_decl =
    FactDeclFull (fctName: "String.literal option") (fctBody: expr) (fctSpan: option_span)

datatype (plugins only: code size) function_decl =
    FunctionDeclFull (fncName: "String.literal") (fncParams: "param_decl list") (fncRetType: type_expr) (fncBody: expr) (fncSpan: option_span)

datatype (plugins only: code size) predicate_decl =
    PredicateDeclFull (prdName: "String.literal") (prdParams: "param_decl list") (prdBody: expr) (prdSpan: option_span)

text \<open>Convention values: parse-don't-validate shape, streamlined to a small
  fixed set of payload shapes. The parser dispatches on the property name
  at construction time and produces:
  \<^enum> \<open>CvOk pv\<close> when the value parses cleanly to one of the five
    \<open>parsed_value\<close> shapes (string, int, bool, string-pair, or
    runtime-expr for properties like \<open>http_header\<close> that accept
    non-literal expressions). The \<open>pv\<close> carries the validated payload;
    the property name on the enclosing \<open>convention_rule\<close>
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
  | PvExpr   expr
  \<comment> \<open>PvExpr carries an expression verbatim for properties (notably
    \<open>http_header\<close>) that accept runtime-evaluated values like
    \<open>output.url\<close> or bare identifiers — values the parser tolerates
    but can't reduce to a string at IR-build time.\<close>

datatype (plugins only: code size) convention_value =
    CvOk      parsed_value
  | CvBad     validation_failure expr   \<comment> \<open>why + raw expr (for diagnostics)\<close>
  | CvUnknown expr                       \<comment> \<open>unrecognised property name\<close>

datatype (plugins only: code size) convention_rule =
    ConventionRuleFull (cvrTarget: "String.literal") (cvrProperty: "String.literal") (cvrQualifier: "String.literal option")
                       (cvrValue: convention_value) (cvrSpan: option_span)

datatype (plugins only: code size) conventions_decl =
    ConventionsDeclFull (cvdRules: "convention_rule list") (cvdSpan: option_span)

text \<open>Security schemes (M8.1, issue #53): named credential declarations from the
  spec's \<open>security { ... }\<close> block. \<open>operRequiresAuth\<close> on an operation lists
  alternative scheme names (OpenAPI security-array OR semantics); \<open>None\<close>
  means the operation carries no annotation and is public. Auth is pure
  metadata: classification, verification and evaluation ignore it.\<close>

datatype (plugins only: code size) security_scheme_kind =
    SsBearer "String.literal option"
  | SsApiKey "String.literal" "String.literal"
  | SsBasic

datatype (plugins only: code size) security_scheme_decl =
    SecuritySchemeDeclFull (ssdName: "String.literal") (ssdKind: security_scheme_kind)
                           (ssdSpan: option_span)

datatype (plugins only: code size) service_ir =
    ServiceIRFull (svcName: "String.literal") (svcImports: "String.literal list")
                  (svcEntities: "entity_decl list") (svcEnums: "enum_decl list")
                  (svcTypeAliases: "type_alias_decl list") (svcState: "state_decl option")
                  (svcOperations: "operation_decl list") (svcTransitions: "transition_decl list")
                  (svcInvariants: "invariant_decl list") (svcTemporals: "temporal_decl list")
                  (svcFacts: "fact_decl list") (svcFunctions: "function_decl list")
                  (svcPredicates: "predicate_decl list") (svcConventions: "conventions_decl option")
                  (svcSecurity: "security_scheme_decl list") (svcSpan: option_span)


fun identName :: "expr \<Rightarrow> String.literal option" where
  "identName (IdentifierF rel _) = Some rel"
| "identName _ = None"

lemma identName_SomeD:
  "identName e = Some rel \<Longrightarrow> \<exists>sp. e = IdentifierF rel sp"
  by (cases e rule: identName.cases) auto

fun isLitFull :: "expr \<Rightarrow> bool" where
  "isLitFull (BoolLitF _ _)   = True"
| "isLitFull (IntLitF _ _)    = True"
| "isLitFull (FloatLitF _ _)  = True"
| "isLitFull (StringLitF _ _) = True"
| "isLitFull (NoneLitF _)     = True"
| "isLitFull _                = False"

text \<open>\<^bold>\<open>Generic tree-walk: \<open>allSubexprs\<close>\<close>. Returns every subterm of
  an \<open>expr\<close> (including the root) as a single list, via structural
  mutual \<open>fun\<close> recursion. Defined alongside \<open>subexprs\<close> (one-step
  children) so every binder-insensitive query / collector across IR /
  IR_Analysis / IR_Helpers / LintAnalysis can compose on it instead of
  re-enumerating the 28 constructors per walker.\<close>

fun allSubexprs :: "expr \<Rightarrow> expr list"
and allSubexprs_list :: "expr list \<Rightarrow> expr list"
and allSubexprs_fields :: "field_assign list \<Rightarrow> expr list"
and allSubexprs_entries :: "map_entry list \<Rightarrow> expr list"
and allSubexprs_bindings :: "quantifier_binding list \<Rightarrow> expr list"
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

text \<open>Phase 8 (verifier classifier port): \<open>requiresAlloy\<close> identifies \<open>expr\<close>
  shapes that contain a \<open>UPower\<close> (set-power) constructor anywhere in the tree.
  The verifier routes such checks to the Alloy backend (which models set power)
  instead of Z3. Defined as a structural mutual \<open>fun\<close> alongside its wrapper-list
  companions, so the per-constructor simp rules the Soundness proofs rely on are
  generated automatically.\<close>

fun requiresAlloy :: "expr \<Rightarrow> bool"
  and requiresAlloy_list :: "expr list \<Rightarrow> bool"
  and requiresAlloy_fields :: "field_assign list \<Rightarrow> bool"
  and requiresAlloy_entries :: "map_entry list \<Rightarrow> bool"
  and requiresAlloy_bindings :: "quantifier_binding list \<Rightarrow> bool"
where
  "requiresAlloy (UnaryOpF op e sp)               = (op = UPower \<or> requiresAlloy e)"
| "requiresAlloy (BinaryOpF op l r sp)            = (requiresAlloy l \<or> requiresAlloy r)"
| "requiresAlloy (QuantifierF q bs body sp)       = (requiresAlloy_bindings bs \<or> requiresAlloy body)"
| "requiresAlloy (SomeWrapF x sp)                 = requiresAlloy x"
| "requiresAlloy (TheF nm d body sp)              = (requiresAlloy d \<or> requiresAlloy body)"
| "requiresAlloy (FieldAccessF base fld sp)       = requiresAlloy base"
| "requiresAlloy (EnumAccessF base mem sp)        = requiresAlloy base"
| "requiresAlloy (IndexF base idx sp)             = (requiresAlloy base \<or> requiresAlloy idx)"
| "requiresAlloy (CallF callee args sp)           = (requiresAlloy callee \<or> requiresAlloy_list args)"
| "requiresAlloy (PrimeF x sp)                    = requiresAlloy x"
| "requiresAlloy (PreF x sp)                      = requiresAlloy x"
| "requiresAlloy (WithF base upds sp)             = (requiresAlloy base \<or> requiresAlloy_fields upds)"
| "requiresAlloy (IfF c t f sp)                   = (requiresAlloy c \<or> requiresAlloy t \<or> requiresAlloy f)"
| "requiresAlloy (LetF nm val body sp)            = (requiresAlloy val \<or> requiresAlloy body)"
| "requiresAlloy (LambdaF param body sp)          = requiresAlloy body"
| "requiresAlloy (ConstructorF nm fs sp)          = requiresAlloy_fields fs"
| "requiresAlloy (SetLiteralF xs sp)              = requiresAlloy_list xs"
| "requiresAlloy (MapLiteralF es sp)              = requiresAlloy_entries es"
| "requiresAlloy (SetComprehensionF nm d pred sp) = (requiresAlloy d \<or> requiresAlloy pred)"
| "requiresAlloy (SeqLiteralF xs sp)              = requiresAlloy_list xs"
| "requiresAlloy (MatchesF x pat sp)              = requiresAlloy x"
| "requiresAlloy (IntLitF n sp)                   = False"
| "requiresAlloy (FloatLitF f sp)                 = False"
| "requiresAlloy (StringLitF s sp)                = False"
| "requiresAlloy (BoolLitF b sp)                  = False"
| "requiresAlloy (NoneLitF sp)                    = False"
| "requiresAlloy (IdentifierF n sp)               = False"
| "requiresAlloy_list []                          = False"
| "requiresAlloy_list (x # xs)                    = (requiresAlloy x \<or> requiresAlloy_list xs)"
| "requiresAlloy_fields []                        = False"
| "requiresAlloy_fields (FieldAssignFull nm v sp # fs) = (requiresAlloy v \<or> requiresAlloy_fields fs)"
| "requiresAlloy_entries []                       = False"
| "requiresAlloy_entries (MapEntryFull k v sp # es) = (requiresAlloy k \<or> requiresAlloy v \<or> requiresAlloy_entries es)"
| "requiresAlloy_bindings []                      = False"
| "requiresAlloy_bindings (QuantifierBindingFull nm d a sp # bs) = (requiresAlloy d \<or> requiresAlloy_bindings bs)"

text \<open>Phase 9a (structural primitives): \<open>subexprs\<close> returns the direct
  \<open>expr\<close> children of an expression. Replaces ad-hoc 27-arm structural
  folds in lint walkers, classifier helpers, narration / diagnostic
  collectors, and per-target translators with one proven primitive. New
  consumers compose: e.g. \<open>def visit(e) = ...; subexprs(e).foreach(visit)\<close>.\<close>

fun subexprs :: "expr \<Rightarrow> expr list"
and subexprs_fields :: "field_assign list \<Rightarrow> expr list"
and subexprs_entries :: "map_entry list \<Rightarrow> expr list"
and subexprs_bindings :: "quantifier_binding list \<Rightarrow> expr list"
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
  \<open>option_span\<close> in an \<open>expr\<close> tree (and the three child-list-bearing
  companions) to \<open>None\<close>, leaving structure and scalar payloads intact.
  \<open>typeStripSpans\<close> does the same for \<open>type_expr\<close>. Two spans-erased
  values are HOL-equal iff the originals are structurally equal modulo source
  position — so consumers compare span-insensitive shape by extracted
  structural \<open>equals\<close> instead of hand-rolling a 50-arm string fingerprint
  that must track every \<open>expr\<close> constructor by hand (lint L04 operation
  overlap). Total structural maps; termination is automatic.\<close>

fun stripSpans :: "expr \<Rightarrow> expr"
and stripSpans_list :: "expr list \<Rightarrow> expr list"
and stripSpans_fields :: "field_assign list \<Rightarrow> field_assign list"
and stripSpans_entries :: "map_entry list \<Rightarrow> map_entry list"
and stripSpans_bindings :: "quantifier_binding list \<Rightarrow> quantifier_binding list"
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

fun typeStripSpans :: "type_expr \<Rightarrow> type_expr" where
  "typeStripSpans (NamedTypeF n _)        = NamedTypeF n None"
| "typeStripSpans (SetTypeF t _)          = SetTypeF (typeStripSpans t) None"
| "typeStripSpans (MapTypeF k v _)        = MapTypeF (typeStripSpans k) (typeStripSpans v) None"
| "typeStripSpans (SeqTypeF t _)          = SeqTypeF (typeStripSpans t) None"
| "typeStripSpans (OptionTypeF t _)       = OptionTypeF (typeStripSpans t) None"
| "typeStripSpans (RelationTypeF f m t _) = RelationTypeF (typeStripSpans f) m (typeStripSpans t) None"

text \<open>Phase 9c: \<open>binOpToTs\<close> is the single source of truth for the
  surface spelling of each \<open>bin_op\<close>. Consumed by the verifier's
  diagnostic messages (\<open>z3.Translator\<close>); replaces a hand 20-arm table that
  had to track every \<open>bin_op\<close> constructor by hand.\<close>

fun binOpToTs :: "bin_op \<Rightarrow> String.literal" where
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

fun spanOf :: "expr \<Rightarrow> option_span" where
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

fun flattenAnd :: "expr \<Rightarrow> expr list" where
  "flattenAnd (BinaryOpF BAnd l r _) = flattenAnd l @ flattenAnd r"
| "flattenAnd e                      = [e]"

fun flattenEnsuresExpr :: "expr \<Rightarrow> expr list" where
  "flattenEnsuresExpr (BinaryOpF BAnd l r _) =
     flattenEnsuresExpr l @ flattenEnsuresExpr r"
| "flattenEnsuresExpr (LetF _ v b _) =
     flattenEnsuresExpr v @ flattenEnsuresExpr b"
| "flattenEnsuresExpr e = [e]"

definition flattenEnsures :: "expr list \<Rightarrow> expr list" where
  "flattenEnsures es \<equiv> List.concat (map flattenEnsuresExpr es)"

definition flattenAndAll :: "expr list \<Rightarrow> expr list" where
  "flattenAndAll es \<equiv> List.concat (map flattenAnd es)"

fun rootIdentifier :: "expr \<Rightarrow> String.literal option" where
  "rootIdentifier (IdentifierF n _)        = Some n"
| "rootIdentifier (IndexF base _ _)        = rootIdentifier base"
| "rootIdentifier (FieldAccessF base _ _)  = rootIdentifier base"
| "rootIdentifier _                        = None"

fun dom_arg :: "expr \<Rightarrow> String.literal option" where
  "dom_arg (CallF c args _) =
     (case (c, args) of
        (IdentifierF d _, [IdentifierF x _]) \<Rightarrow> (if d = STR ''dom'' then Some x else None)
      | _ \<Rightarrow> None)"
| "dom_arg _ = None"

lemma dom_arg_SomeD:
  "dom_arg e = Some x
     \<Longrightarrow> \<exists>sp1 sp2 sp. e = CallF (IdentifierF (STR ''dom'') sp1) [IdentifierF x sp2] sp"
  by (erule dom_arg.elims; auto split: expr.splits list.splits if_splits prod.splits)

fun prime_ident_name :: "expr \<Rightarrow> String.literal option" where
  "prime_ident_name (PrimeF e _) = identName e"
| "prime_ident_name _            = None"

fun base_ident_name :: "expr \<Rightarrow> String.literal option" where
  "base_ident_name (PreF e _)        = identName e"
| "base_ident_name (PrimeF e _)      = identName e"
| "base_ident_name (IdentifierF x _) = Some x"
| "base_ident_name _                 = None"

fun map_single_entry :: "expr \<Rightarrow> (String.literal \<times> String.literal) option" where
  "map_single_entry (MapLiteralF es _) =
     (case es of
        [MapEntryFull kE vE _] \<Rightarrow>
          (case (identName kE, identName vE) of
             (Some kn, Some vn) \<Rightarrow> Some (kn, vn)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "map_single_entry _ = None"

fun rel_insert_rhs :: "expr \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) option" where
  "rel_insert_rhs (BinaryOpF bop base mlit _) =
     (if bop = BAdd
      then (case (base_ident_name base, map_single_entry mlit) of
              (Some brel, Some (kn, vn)) \<Rightarrow> Some (brel, kn, vn)
            | _ \<Rightarrow> None)
      else None)"
| "rel_insert_rhs _ = None"

definition rel_insert_parts ::
  "bin_op \<Rightarrow> expr \<Rightarrow> expr \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) option" where
  "rel_insert_parts op l r =
     (if op = BEq
      then (case (prime_ident_name l, rel_insert_rhs r) of
              (Some lrel, Some (brel, kn, vn)) \<Rightarrow>
                (if lrel = brel then Some (lrel, kn, vn) else None)
            | _ \<Rightarrow> None)
      else None)"

lemma rel_insert_parts_SomeD:
  "rel_insert_parts op l r = Some (rel, kn, vn)
     \<Longrightarrow> op = BEq \<and> prime_ident_name l = Some rel \<and> rel_insert_rhs r = Some (rel, kn, vn)"
  by (auto simp: rel_insert_parts_def split: option.splits if_splits prod.splits)

lemma rel_insert_parts_non_BEq [simp]:
  "op \<noteq> BEq \<Longrightarrow> rel_insert_parts op l r = None"
  by (simp add: rel_insert_parts_def)

lemma rel_insert_parts_SetComp_None [simp]:
  "rel_insert_parts op l (SetComprehensionF v d p sp) = None"
  by (simp add: rel_insert_parts_def split: option.splits)

lemma map_single_entry_MapLitD:
  "map_single_entry e = Some kv \<Longrightarrow> \<exists>es sp. e = MapLiteralF es sp"
  by (cases e) auto

lemma rel_insert_rhs_SomeD:
  "rel_insert_rhs r = Some (brel, kn, vn)
     \<Longrightarrow> \<exists>base es sp bsp. r = BinaryOpF BAdd base (MapLiteralF es sp) bsp"
  by (erule rel_insert_rhs.elims;
      auto dest!: map_single_entry_MapLitD split: if_splits option.splits prod.splits)

lemmas allSubexprs_code [code]            = allSubexprs.simps allSubexprs_list.simps
                                             allSubexprs_fields.simps
                                             allSubexprs_entries.simps
                                             allSubexprs_bindings.simps

end
