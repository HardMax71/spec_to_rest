theory IR
  imports Main
begin

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

text \<open>Issue #210 (M_L.4.l): \<open>IndexRel\<close>'s base is widened from a bare
  relation name to an arbitrary \<open>expr\<close>, so the operation-side
  \<open>pre(rel)[k]\<close> and \<open>rel'[k]\<close> shapes can lower into the verified subset.
  The intended bases are \<open>Ident rel\<close>, \<open>Pre (Ident rel)\<close>, and
  \<open>Prime (Ident rel)\<close>; \<open>peel_relation_ref\<close> recognises exactly those
  shapes and returns the relation name. Other bases evaluate to \<open>None\<close>
  in both \<open>eval\<close> and \<open>smtEval\<close>, preserving symmetry for the soundness
  theorem.\<close>

fun peel_relation_ref :: "expr \<Rightarrow> String.literal option" where
  "peel_relation_ref (Ident rel _)             = Some rel"
| "peel_relation_ref (Pre (Ident rel _) _)     = Some rel"
| "peel_relation_ref (Prime (Ident rel _) _)   = Some rel"
| "peel_relation_ref _                          = None"

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

record state_scalar =
  ss_name :: "String.literal"
  ss_ty   :: "type_expr"
  ss_span :: "option_span"

record state_relation =
  sr_name  :: "String.literal"
  sr_key   :: "type_expr"
  sr_value :: "type_expr"
  sr_span  :: "option_span"

record state_decl =
  st_scalars   :: "state_scalar list"
  st_relations :: "state_relation list"
  st_span      :: "option_span"

record invariant_decl =
  inv_name :: "String.literal"
  inv_body :: "expr"
  inv_span :: "option_span"

record operation_decl =
  op_name     :: "String.literal"
  op_requires :: "expr list"
  op_ensures  :: "expr list"
  op_span     :: "option_span"

record service_ir =
  svc_name       :: "String.literal"
  svc_enums      :: "enum_decl list"
  svc_entities   :: "entity_decl list"
  svc_state      :: "state_decl"
  svc_invariants :: "invariant_decl list"
  svc_operations :: "operation_decl list"
  svc_span       :: "option_span"

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
    FieldAssignFull "String.literal" expr_full option_span
and map_entry_full =
    MapEntryFull expr_full expr_full option_span
and quantifier_binding_full =
    QuantifierBindingFull "String.literal" expr_full binding_kind_full option_span

datatype (plugins only: code size) field_decl_full =
    FieldDeclFull "String.literal" type_expr_full "expr_full option" option_span

datatype (plugins only: code size) entity_decl_full =
    EntityDeclFull "String.literal" "String.literal option" "field_decl_full list" "expr_full list" option_span

datatype (plugins only: code size) enum_decl_full =
    EnumDeclFull "String.literal" "String.literal list" option_span

datatype (plugins only: code size) type_alias_decl_full =
    TypeAliasDeclFull "String.literal" type_expr_full "expr_full option" option_span

datatype (plugins only: code size) state_field_decl_full =
    StateFieldDeclFull "String.literal" type_expr_full option_span

datatype (plugins only: code size) state_decl_full =
    StateDeclFull "state_field_decl_full list" option_span

datatype (plugins only: code size) param_decl_full =
    ParamDeclFull "String.literal" type_expr_full option_span

datatype (plugins only: code size) operation_decl_full =
    OperationDeclFull "String.literal" "param_decl_full list" "param_decl_full list" "expr_full list" "expr_full list" option_span

datatype (plugins only: code size) transition_rule_full =
    TransitionRuleFull "String.literal" "String.literal" "String.literal" "expr_full option" option_span

datatype (plugins only: code size) transition_decl_full =
    TransitionDeclFull "String.literal" "String.literal" "String.literal" "transition_rule_full list" option_span

datatype (plugins only: code size) invariant_decl_full =
    InvariantDeclFull "String.literal option" expr_full option_span

datatype (plugins only: code size) temporal_decl_full =
    TemporalDeclFull "String.literal" expr_full option_span

datatype (plugins only: code size) fact_decl_full =
    FactDeclFull "String.literal option" expr_full option_span

datatype (plugins only: code size) function_decl_full =
    FunctionDeclFull "String.literal" "param_decl_full list" type_expr_full expr_full option_span

datatype (plugins only: code size) predicate_decl_full =
    PredicateDeclFull "String.literal" "param_decl_full list" expr_full option_span

text \<open>Convention values: parse-don't-validate shape, streamlined to a small
  fixed set of payload shapes. The parser dispatches on the property name
  at construction time and produces:
  \<^enum> \<open>CvOk pv\<close> when the value parses cleanly to one of the four generic
    \<open>parsed_value\<close> shapes (string, int, bool, string-pair). The \<open>pv\<close>
    carries the validated payload; the property name on the enclosing
    \<open>convention_rule_full\<close> disambiguates which property it belongs to.
  \<^enum> \<open>CvBad failure\<close> when the parser recognised the property but the
    value was wrong-typed or out-of-range. Validator emits the diagnostic
    from the carried \<open>validation_failure\<close>.
  \<^enum> \<open>CvUnknown\<close> when the property name itself wasn't recognised.

  Three outcome variants \<times> four payload shapes \<gg> twelve per-property
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
    ConventionRuleFull "String.literal" "String.literal" "String.literal option"
                       convention_value option_span
                       \<comment> \<open>target, property_name, qualifier_opt, value, span\<close>

datatype (plugins only: code size) conventions_decl_full =
    ConventionsDeclFull "convention_rule_full list" option_span

datatype (plugins only: code size) service_ir_full =
    ServiceIRFull "String.literal" "String.literal list"
                  "entity_decl_full list" "enum_decl_full list"
                  "type_alias_decl_full list" "state_decl_full option"
                  "operation_decl_full list" "transition_decl_full list"
                  "invariant_decl_full list" "temporal_decl_full list"
                  "fact_decl_full list" "function_decl_full list"
                  "predicate_decl_full list" "conventions_decl_full option"
                  option_span

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

text \<open>Phase 8 (verifier classifier port): \<open>requiresAlloy\<close> identifies
  \<open>expr_full\<close> shapes that contain a \<open>UPower\<close> (set-power) constructor anywhere
  in the expression tree. The verifier routes such checks to the Alloy backend
  (which models set power) instead of Z3. Pure structural fold; mutually
  recursive over \<open>expr_full\<close> and the three child-list-bearing companions
  (\<open>field_assign_full\<close>, \<open>map_entry_full\<close>, \<open>quantifier_binding_full\<close>) that
  also carry \<open>expr_full\<close> subterms.\<close>

fun requiresAlloy :: "expr_full \<Rightarrow> bool"
and requiresAlloy_list :: "expr_full list \<Rightarrow> bool"
and requiresAlloy_fields :: "field_assign_full list \<Rightarrow> bool"
and requiresAlloy_entries :: "map_entry_full list \<Rightarrow> bool"
and requiresAlloy_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "requiresAlloy (UnaryOpF op e _)             = (op = UPower \<or> requiresAlloy e)"
| "requiresAlloy (BinaryOpF _ l r _)           = (requiresAlloy l \<or> requiresAlloy r)"
| "requiresAlloy (QuantifierF _ bs body _)     = (requiresAlloy_bindings bs \<or> requiresAlloy body)"
| "requiresAlloy (SomeWrapF x _)               = requiresAlloy x"
| "requiresAlloy (TheF _ d b _)                = (requiresAlloy d \<or> requiresAlloy b)"
| "requiresAlloy (FieldAccessF b _ _)          = requiresAlloy b"
| "requiresAlloy (EnumAccessF b _ _)           = requiresAlloy b"
| "requiresAlloy (IndexF b i _)                = (requiresAlloy b \<or> requiresAlloy i)"
| "requiresAlloy (CallF c args _)              = (requiresAlloy c \<or> requiresAlloy_list args)"
| "requiresAlloy (PrimeF x _)                  = requiresAlloy x"
| "requiresAlloy (PreF x _)                    = requiresAlloy x"
| "requiresAlloy (WithF b upds _)              = (requiresAlloy b \<or> requiresAlloy_fields upds)"
| "requiresAlloy (IfF c t e _)                 = (requiresAlloy c \<or> requiresAlloy t \<or> requiresAlloy e)"
| "requiresAlloy (LetF _ v b _)                = (requiresAlloy v \<or> requiresAlloy b)"
| "requiresAlloy (LambdaF _ b _)               = requiresAlloy b"
| "requiresAlloy (ConstructorF _ fs _)         = requiresAlloy_fields fs"
| "requiresAlloy (SetLiteralF xs _)            = requiresAlloy_list xs"
| "requiresAlloy (MapLiteralF es _)            = requiresAlloy_entries es"
| "requiresAlloy (SetComprehensionF _ d p _)   = (requiresAlloy d \<or> requiresAlloy p)"
| "requiresAlloy (SeqLiteralF xs _)            = requiresAlloy_list xs"
| "requiresAlloy (MatchesF x _ _)              = requiresAlloy x"
| "requiresAlloy (IntLitF _ _)                 = False"
| "requiresAlloy (FloatLitF _ _)               = False"
| "requiresAlloy (StringLitF _ _)              = False"
| "requiresAlloy (BoolLitF _ _)                = False"
| "requiresAlloy (NoneLitF _)                  = False"
| "requiresAlloy (IdentifierF _ _)             = False"
| "requiresAlloy_list []                       = False"
| "requiresAlloy_list (x # xs)                 = (requiresAlloy x \<or> requiresAlloy_list xs)"
| "requiresAlloy_fields []                     = False"
| "requiresAlloy_fields (FieldAssignFull _ v _ # fs) = (requiresAlloy v \<or> requiresAlloy_fields fs)"
| "requiresAlloy_entries []                    = False"
| "requiresAlloy_entries (MapEntryFull k v _ # es) = (requiresAlloy k \<or> requiresAlloy v \<or> requiresAlloy_entries es)"
| "requiresAlloy_bindings []                   = False"
| "requiresAlloy_bindings (QuantifierBindingFull _ d _ _ # bs) = (requiresAlloy d \<or> requiresAlloy_bindings bs)"

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

end
