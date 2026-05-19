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
  in both \<open>eval\<close> and \<open>smt_eval\<close>, preserving symmetry for the soundness
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

datatype (plugins only: code size) convention_rule_full =
    ConventionRuleFull "String.literal" "String.literal" "String.literal option" expr_full option_span

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

fun is_lit_full :: "expr_full \<Rightarrow> bool" where
  "is_lit_full (BoolLitF _ _)   = True"
| "is_lit_full (IntLitF _ _)    = True"
| "is_lit_full (FloatLitF _ _)  = True"
| "is_lit_full (StringLitF _ _) = True"
| "is_lit_full (NoneLitF _)     = True"
| "is_lit_full _                = False"

text \<open>Phase 8 (verifier classifier port): \<open>requires_alloy\<close> identifies
  \<open>expr_full\<close> shapes that contain a \<open>UPower\<close> (set-power) constructor anywhere
  in the expression tree. The verifier routes such checks to the Alloy backend
  (which models set power) instead of Z3. Pure structural fold; mutually
  recursive over \<open>expr_full\<close> and the three child-list-bearing companions
  (\<open>field_assign_full\<close>, \<open>map_entry_full\<close>, \<open>quantifier_binding_full\<close>) that
  also carry \<open>expr_full\<close> subterms.\<close>

fun requires_alloy :: "expr_full \<Rightarrow> bool"
and requires_alloy_list :: "expr_full list \<Rightarrow> bool"
and requires_alloy_fields :: "field_assign_full list \<Rightarrow> bool"
and requires_alloy_entries :: "map_entry_full list \<Rightarrow> bool"
and requires_alloy_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "requires_alloy (UnaryOpF op e _)             = (op = UPower \<or> requires_alloy e)"
| "requires_alloy (BinaryOpF _ l r _)           = (requires_alloy l \<or> requires_alloy r)"
| "requires_alloy (QuantifierF _ bs body _)     = (requires_alloy_bindings bs \<or> requires_alloy body)"
| "requires_alloy (SomeWrapF x _)               = requires_alloy x"
| "requires_alloy (TheF _ d b _)                = (requires_alloy d \<or> requires_alloy b)"
| "requires_alloy (FieldAccessF b _ _)          = requires_alloy b"
| "requires_alloy (EnumAccessF b _ _)           = requires_alloy b"
| "requires_alloy (IndexF b i _)                = (requires_alloy b \<or> requires_alloy i)"
| "requires_alloy (CallF c args _)              = (requires_alloy c \<or> requires_alloy_list args)"
| "requires_alloy (PrimeF x _)                  = requires_alloy x"
| "requires_alloy (PreF x _)                    = requires_alloy x"
| "requires_alloy (WithF b upds _)              = (requires_alloy b \<or> requires_alloy_fields upds)"
| "requires_alloy (IfF c t e _)                 = (requires_alloy c \<or> requires_alloy t \<or> requires_alloy e)"
| "requires_alloy (LetF _ v b _)                = (requires_alloy v \<or> requires_alloy b)"
| "requires_alloy (LambdaF _ b _)               = requires_alloy b"
| "requires_alloy (ConstructorF _ fs _)         = requires_alloy_fields fs"
| "requires_alloy (SetLiteralF xs _)            = requires_alloy_list xs"
| "requires_alloy (MapLiteralF es _)            = requires_alloy_entries es"
| "requires_alloy (SetComprehensionF _ d p _)   = (requires_alloy d \<or> requires_alloy p)"
| "requires_alloy (SeqLiteralF xs _)            = requires_alloy_list xs"
| "requires_alloy (MatchesF x _ _)              = requires_alloy x"
| "requires_alloy (IntLitF _ _)                 = False"
| "requires_alloy (FloatLitF _ _)               = False"
| "requires_alloy (StringLitF _ _)              = False"
| "requires_alloy (BoolLitF _ _)                = False"
| "requires_alloy (NoneLitF _)                  = False"
| "requires_alloy (IdentifierF _ _)             = False"
| "requires_alloy_list []                       = False"
| "requires_alloy_list (x # xs)                 = (requires_alloy x \<or> requires_alloy_list xs)"
| "requires_alloy_fields []                     = False"
| "requires_alloy_fields (FieldAssignFull _ v _ # fs) = (requires_alloy v \<or> requires_alloy_fields fs)"
| "requires_alloy_entries []                    = False"
| "requires_alloy_entries (MapEntryFull k v _ # es) = (requires_alloy k \<or> requires_alloy v \<or> requires_alloy_entries es)"
| "requires_alloy_bindings []                   = False"
| "requires_alloy_bindings (QuantifierBindingFull _ d _ _ # bs) = (requires_alloy d \<or> requires_alloy_bindings bs)"

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

text \<open>Phase 9b (structural primitives): \<open>strip_spans\<close> erases every
  \<open>option_span\<close> in an \<open>expr_full\<close> tree (and the three child-list-bearing
  companions) to \<open>None\<close>, leaving structure and scalar payloads intact.
  \<open>type_strip_spans\<close> does the same for \<open>type_expr_full\<close>. Two spans-erased
  values are HOL-equal iff the originals are structurally equal modulo source
  position — so consumers compare span-insensitive shape by extracted
  structural \<open>equals\<close> instead of hand-rolling a 50-arm string fingerprint
  that must track every \<open>expr_full\<close> constructor by hand (lint L04 operation
  overlap). Total structural maps; termination is automatic.\<close>

fun strip_spans :: "expr_full \<Rightarrow> expr_full"
and strip_spans_list :: "expr_full list \<Rightarrow> expr_full list"
and strip_spans_fields :: "field_assign_full list \<Rightarrow> field_assign_full list"
and strip_spans_entries :: "map_entry_full list \<Rightarrow> map_entry_full list"
and strip_spans_bindings :: "quantifier_binding_full list \<Rightarrow> quantifier_binding_full list"
where
  "strip_spans (BinaryOpF op l r _)        = BinaryOpF op (strip_spans l) (strip_spans r) None"
| "strip_spans (UnaryOpF op e _)           = UnaryOpF op (strip_spans e) None"
| "strip_spans (QuantifierF k bs body _)   = QuantifierF k (strip_spans_bindings bs) (strip_spans body) None"
| "strip_spans (SomeWrapF e _)             = SomeWrapF (strip_spans e) None"
| "strip_spans (TheF v d b _)              = TheF v (strip_spans d) (strip_spans b) None"
| "strip_spans (FieldAccessF b f _)        = FieldAccessF (strip_spans b) f None"
| "strip_spans (EnumAccessF b m _)         = EnumAccessF (strip_spans b) m None"
| "strip_spans (IndexF b i _)              = IndexF (strip_spans b) (strip_spans i) None"
| "strip_spans (CallF c args _)            = CallF (strip_spans c) (strip_spans_list args) None"
| "strip_spans (PrimeF e _)                = PrimeF (strip_spans e) None"
| "strip_spans (PreF e _)                  = PreF (strip_spans e) None"
| "strip_spans (WithF b ups _)             = WithF (strip_spans b) (strip_spans_fields ups) None"
| "strip_spans (IfF c t e _)               = IfF (strip_spans c) (strip_spans t) (strip_spans e) None"
| "strip_spans (LetF x v b _)              = LetF x (strip_spans v) (strip_spans b) None"
| "strip_spans (LambdaF p b _)             = LambdaF p (strip_spans b) None"
| "strip_spans (ConstructorF n fs _)       = ConstructorF n (strip_spans_fields fs) None"
| "strip_spans (SetLiteralF xs _)          = SetLiteralF (strip_spans_list xs) None"
| "strip_spans (MapLiteralF es _)          = MapLiteralF (strip_spans_entries es) None"
| "strip_spans (SetComprehensionF v d p _) = SetComprehensionF v (strip_spans d) (strip_spans p) None"
| "strip_spans (SeqLiteralF xs _)          = SeqLiteralF (strip_spans_list xs) None"
| "strip_spans (MatchesF e pat _)          = MatchesF (strip_spans e) pat None"
| "strip_spans (IntLitF n _)               = IntLitF n None"
| "strip_spans (FloatLitF v _)             = FloatLitF v None"
| "strip_spans (StringLitF v _)            = StringLitF v None"
| "strip_spans (BoolLitF b _)              = BoolLitF b None"
| "strip_spans (NoneLitF _)                = NoneLitF None"
| "strip_spans (IdentifierF x _)           = IdentifierF x None"
| "strip_spans_list []                                    = []"
| "strip_spans_list (x # xs)                              = strip_spans x # strip_spans_list xs"
| "strip_spans_fields []                                  = []"
| "strip_spans_fields (FieldAssignFull n v _ # fs)        = FieldAssignFull n (strip_spans v) None # strip_spans_fields fs"
| "strip_spans_entries []                                 = []"
| "strip_spans_entries (MapEntryFull k v _ # es)          = MapEntryFull (strip_spans k) (strip_spans v) None # strip_spans_entries es"
| "strip_spans_bindings []                                = []"
| "strip_spans_bindings (QuantifierBindingFull v d k _ # bs) = QuantifierBindingFull v (strip_spans d) k None # strip_spans_bindings bs"

fun type_strip_spans :: "type_expr_full \<Rightarrow> type_expr_full" where
  "type_strip_spans (NamedTypeF n _)        = NamedTypeF n None"
| "type_strip_spans (SetTypeF t _)          = SetTypeF (type_strip_spans t) None"
| "type_strip_spans (MapTypeF k v _)        = MapTypeF (type_strip_spans k) (type_strip_spans v) None"
| "type_strip_spans (SeqTypeF t _)          = SeqTypeF (type_strip_spans t) None"
| "type_strip_spans (OptionTypeF t _)       = OptionTypeF (type_strip_spans t) None"
| "type_strip_spans (RelationTypeF f m t _) = RelationTypeF (type_strip_spans f) m (type_strip_spans t) None"

text \<open>Phase 9c: \<open>bin_op_to_ts\<close> is the single source of truth for the
  surface spelling of each \<open>bin_op_full\<close>. Consumed by the verifier's
  diagnostic messages (\<open>z3.Translator\<close>); replaces a hand 20-arm table that
  had to track every \<open>bin_op_full\<close> constructor by hand.\<close>

fun bin_op_to_ts :: "bin_op_full \<Rightarrow> String.literal" where
  "bin_op_to_ts BAnd       = STR ''and''"
| "bin_op_to_ts BOr        = STR ''or''"
| "bin_op_to_ts BImplies   = STR ''implies''"
| "bin_op_to_ts BIff       = STR ''iff''"
| "bin_op_to_ts BEq        = STR ''=''"
| "bin_op_to_ts BNeq       = STR ''!=''"
| "bin_op_to_ts BLt        = STR ''<''"
| "bin_op_to_ts BGt        = STR ''>''"
| "bin_op_to_ts BLe        = STR ''<=''"
| "bin_op_to_ts BGe        = STR ''>=''"
| "bin_op_to_ts BIn        = STR ''in''"
| "bin_op_to_ts BNotIn     = STR ''not_in''"
| "bin_op_to_ts BSubset    = STR ''subset''"
| "bin_op_to_ts BUnion     = STR ''union''"
| "bin_op_to_ts BIntersect = STR ''intersect''"
| "bin_op_to_ts BDiff      = STR ''minus''"
| "bin_op_to_ts BAdd       = STR ''+''"
| "bin_op_to_ts BSub       = STR ''-''"
| "bin_op_to_ts BMul       = STR ''*''"
| "bin_op_to_ts BDiv       = STR ''/''"

text \<open>Phase 9d: \<open>span_of\<close> projects the trailing \<open>option_span\<close> of any
  \<open>expr_full\<close> (dual of \<open>strip_spans\<close>); \<open>flatten_and\<close> right-flattens a
  \<open>BAnd\<close> conjunction tree into its conjunct list; \<open>type_name\<close> extracts a
  \<open>NamedTypeF\<close>'s name. Each replaced several byte-identical hand copies
  scattered across verify / lint / convention / testgen (3x \<open>spanOpt\<close>,
  4x \<open>flattenAnd\<close>, 5x \<open>typeName\<close>). Total; termination automatic.\<close>

fun span_of :: "expr_full \<Rightarrow> option_span" where
  "span_of (BinaryOpF _ _ _ sp)         = sp"
| "span_of (UnaryOpF _ _ sp)            = sp"
| "span_of (QuantifierF _ _ _ sp)       = sp"
| "span_of (SomeWrapF _ sp)             = sp"
| "span_of (TheF _ _ _ sp)              = sp"
| "span_of (FieldAccessF _ _ sp)        = sp"
| "span_of (EnumAccessF _ _ sp)         = sp"
| "span_of (IndexF _ _ sp)              = sp"
| "span_of (CallF _ _ sp)               = sp"
| "span_of (PrimeF _ sp)                = sp"
| "span_of (PreF _ sp)                  = sp"
| "span_of (WithF _ _ sp)               = sp"
| "span_of (IfF _ _ _ sp)               = sp"
| "span_of (LetF _ _ _ sp)              = sp"
| "span_of (LambdaF _ _ sp)             = sp"
| "span_of (ConstructorF _ _ sp)        = sp"
| "span_of (SetLiteralF _ sp)           = sp"
| "span_of (MapLiteralF _ sp)           = sp"
| "span_of (SetComprehensionF _ _ _ sp) = sp"
| "span_of (SeqLiteralF _ sp)           = sp"
| "span_of (MatchesF _ _ sp)            = sp"
| "span_of (IntLitF _ sp)               = sp"
| "span_of (FloatLitF _ sp)             = sp"
| "span_of (StringLitF _ sp)            = sp"
| "span_of (BoolLitF _ sp)              = sp"
| "span_of (NoneLitF sp)                = sp"
| "span_of (IdentifierF _ sp)           = sp"

fun flatten_and :: "expr_full \<Rightarrow> expr_full list" where
  "flatten_and (BinaryOpF BAnd l r _) = flatten_and l @ flatten_and r"
| "flatten_and e                      = [e]"

fun type_name :: "type_expr_full \<Rightarrow> String.literal option" where
  "type_name (NamedTypeF n _) = Some n"
| "type_name _                = None"

text \<open>Phase 9e: \<open>flatten_inheritance\<close> resolves \<open>entity Child extends
  Base\<close> chains into self-contained entities so every downstream consumer
  (schema, model emitters, Alloy / Dafny, the verifier) sees a single
  flattened entity. Order is root-first then own; a same-named child field
  shadows the inherited one *in the inherited position* (LinkedHashMap
  upsert semantics). The visited list is seeded with the entity itself, so a
  malformed \<open>extends\<close> cycle collapses to no inheritance instead of looping
  — a structural guard, no exception, mirroring alias resolution. \<open>chain_up\<close>
  carries a \<open>fuel\<close> bounded by the entity count: a simple path of distinct
  names cannot exceed it, and the visited-list guard always triggers first
  on a cycle, so the fuel arm is unreachable on real input and only serves
  \<open>fun\<close>'s structural-termination obligation. This is the canonical
  replacement for the hand \<open>parser.Builder.flattenInheritance\<close>.\<close>

fun entity_name_full :: "entity_decl_full \<Rightarrow> String.literal" where
  "entity_name_full (EntityDeclFull n _ _ _ _) = n"

fun entity_parent_full :: "entity_decl_full \<Rightarrow> String.literal option" where
  "entity_parent_full (EntityDeclFull _ p _ _ _) = p"

fun entity_fields_full :: "entity_decl_full \<Rightarrow> field_decl_full list" where
  "entity_fields_full (EntityDeclFull _ _ fs _ _) = fs"

fun entity_invs_full :: "entity_decl_full \<Rightarrow> expr_full list" where
  "entity_invs_full (EntityDeclFull _ _ _ iv _) = iv"

fun field_name_full :: "field_decl_full \<Rightarrow> String.literal" where
  "field_name_full (FieldDeclFull n _ _ _) = n"

definition entity_by_name ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> entity_decl_full option" where
  "entity_by_name es nm =
     map_of (map (\<lambda>e. (entity_name_full e, e)) (rev es)) nm"

fun chain_up ::
  "entity_decl_full list \<Rightarrow> nat \<Rightarrow> String.literal \<Rightarrow> String.literal list
     \<Rightarrow> entity_decl_full list" where
  "chain_up _ 0 _ _ = []"
| "chain_up es (Suc f) name seen =
     (case entity_by_name es name of
        None \<Rightarrow> []
      | Some e \<Rightarrow>
          (case entity_parent_full e of
             None \<Rightarrow> [e]
           | Some parent \<Rightarrow>
               (if List.member seen parent then [e]
                else chain_up es f parent (name # seen) @ [e])))"

fun upsert_field ::
  "field_decl_full list \<Rightarrow> field_decl_full \<Rightarrow> field_decl_full list" where
  "upsert_field acc fd =
     (if list_ex (\<lambda>g. field_name_full g = field_name_full fd) acc
      then map (\<lambda>g. if field_name_full g = field_name_full fd then fd else g) acc
      else acc @ [fd])"

fun flatten_entity ::
  "entity_decl_full list \<Rightarrow> entity_decl_full \<Rightarrow> entity_decl_full" where
  "flatten_entity es (EntityDeclFull nm pa fs iv sp) =
     (case pa of
        None \<Rightarrow> EntityDeclFull nm pa fs iv sp
      | Some _ \<Rightarrow>
          (let anc = butlast (chain_up es (length es) nm [nm])
           in if anc = [] then EntityDeclFull nm pa fs iv sp
              else EntityDeclFull nm pa
                     (foldl upsert_field []
                        (concat (map entity_fields_full anc) @ fs))
                     (concat (map entity_invs_full anc) @ iv)
                     sp))"

fun flatten_inheritance :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flatten_inheritance (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity c) c) d e f g h i j k l m n p"

text \<open>Phase 9f — the Phase 9 structural primitives are now backed by proof,
  not merely totality.

  \<^item> \<open>strip_spans\<close> is idempotent: re-erasing a spans-erased tree is a
    no-op. This is exactly the property that makes the lint L04
    \<open>strip_spans e\<close>-keyed overlap classes well-defined (a fact previously
    only argued informally in the Scala rewrite).
  \<^item> \<open>flatten_and\<close> fully decomposes: re-flattening the conjunct list is
    stable, so no top-level \<open>BAnd\<close> survives — the normalisation guarantee
    its consumers rely on.
  \<^item> \<open>flatten_entity\<close> (hence \<open>flatten_inheritance\<close>) is the identity on a
    parent-less entity / service: inheritance flattening only ever rewrites
    \<open>extends\<close> declarations.

  NB \<open>flatten_inheritance\<close> is deliberately NOT proven globally idempotent
  because it is not: the parent ref is retained and inherited invariants are
  concatenated, so a second application duplicates them. This is latent and
  harmless (\<open>parser.Builder.buildIRCore\<close> applies it exactly once); a fix
  (clearing the parent ref) is a separate behaviour change — it would alter
  \<open>lint.UnusedEntity\<close> reachability and the IR-JSON \<open>extends_\<close> field.\<close>

lemma strip_spans_idem:
  "strip_spans (strip_spans e) = strip_spans e"
  "strip_spans_list (strip_spans_list xs) = strip_spans_list xs"
  "strip_spans_fields (strip_spans_fields fs) = strip_spans_fields fs"
  "strip_spans_entries (strip_spans_entries ms) = strip_spans_entries ms"
  "strip_spans_bindings (strip_spans_bindings bs) = strip_spans_bindings bs"
  by (induction e and xs and fs and ms and bs
      rule: strip_spans_strip_spans_list_strip_spans_fields_strip_spans_entries_strip_spans_bindings.induct)
     auto

lemma flatten_and_decompose:
  "concat (map flatten_and (flatten_and e)) = flatten_and e"
  by (induction e rule: flatten_and.induct) auto

lemma flatten_entity_noparent:
  "entity_parent_full e = None \<Longrightarrow> flatten_entity es e = e"
  by (cases e) (auto split: option.splits)

lemma flatten_inheritance_id_on_parentless:
  assumes "list_all (\<lambda>x. entity_parent_full x = None) c"
  shows "flatten_inheritance (ServiceIRFull a b c d e f g h i j k l m n p)
           = ServiceIRFull a b c d e f g h i j k l m n p"
proof -
  have "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  thus ?thesis by simp
qed

text \<open>Phase 9k — hardened inheritance flattening.
  \<open>flatten_entity2\<close> / \<open>flatten_inheritance2\<close> differ from v1 by clearing
  the parent ref on the output (\<open>pa := None\<close>) so that a second application
  is a no-op: \<open>flatten_inheritance2_idem\<close>. The fields/invariants
  computation is unchanged, so on parentless input v1 and v2 agree
  (\<open>flatten_inheritance2_eq_on_parentless\<close>). The PR that switches
  \<open>parser.Builder.buildIRCore\<close> to v2 is a deliberate behaviour change —
  it alters \<open>lint.UnusedEntity\<close> reachability (parent ref no longer in the
  \<open>extends_\<close> graph) and the IR-JSON \<open>extends_\<close> field — and rides on
  these proofs.\<close>

fun flatten_entity2 ::
  "entity_decl_full list \<Rightarrow> entity_decl_full \<Rightarrow> entity_decl_full" where
  "flatten_entity2 es (EntityDeclFull nm pa fs iv sp) =
     (case pa of
        None \<Rightarrow> EntityDeclFull nm None fs iv sp
      | Some _ \<Rightarrow>
          (let anc = butlast (chain_up es (length es) nm [nm])
           in if anc = [] then EntityDeclFull nm None fs iv sp
              else EntityDeclFull nm None
                     (foldl upsert_field []
                        (concat (map entity_fields_full anc) @ fs))
                     (concat (map entity_invs_full anc) @ iv)
                     sp))"

fun flatten_inheritance2 :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flatten_inheritance2 (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity2 c) c) d e f g h i j k l m n p"

lemma flatten_entity2_parent_cleared:
  "entity_parent_full (flatten_entity2 es e) = None"
  by (cases e) (auto simp: Let_def split: option.splits if_splits)

lemma flatten_entity2_noparent:
  "entity_parent_full e = None \<Longrightarrow> flatten_entity2 es e = e"
  by (cases e) (auto split: option.splits)

lemma flatten_entity2_eq_on_noparent:
  "entity_parent_full e = None \<Longrightarrow> flatten_entity2 es e = flatten_entity es e"
  by (cases e) (auto split: option.splits)

lemma flatten_inheritance2_idem:
  "flatten_inheritance2 (flatten_inheritance2 s) = flatten_inheritance2 s"
proof (cases s)
  case (ServiceIRFull a b c d ee f g h i j k l m n p)
  let ?c' = "map (flatten_entity2 c) c"
  have "list_all (\<lambda>x. entity_parent_full x = None) ?c'"
    by (simp add: list_all_iff flatten_entity2_parent_cleared)
  hence "map (flatten_entity2 ?c') ?c' = ?c'"
    by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  thus ?thesis using ServiceIRFull by simp
qed

lemma flatten_inheritance2_eq_on_parentless:
  assumes "list_all (\<lambda>x. entity_parent_full x = None) c"
  shows "flatten_inheritance2 (ServiceIRFull a b c d e f g h i j k l m n p)
           = flatten_inheritance (ServiceIRFull a b c d e f g h i j k l m n p)"
proof -
  have a: "map (flatten_entity2 c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  have b: "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  from a b show ?thesis by simp
qed

definition empty_service_ir_full :: "String.literal \<Rightarrow> service_ir_full" where
  "empty_service_ir_full nm =
     ServiceIRFull nm [] [] [] [] None [] [] [] [] [] [] [] None None"

text \<open>Issue #202 close-out: \<open>lower\<close> projects \<open>expr_full\<close> onto the
  verified-subset \<open>expr\<close>. Out-of-subset constructors return \<open>None\<close>. Span
  field preserved.

  Schema-awareness: the \<open>enums\<close> parameter (list of declared enum names) lets
  \<open>lower\<close> disambiguate a \<open>QuantifierF\<close> binding into \<open>ForallEnum\<close> vs
  \<open>ForallRel\<close> — both reachable from the same \<open>QuantifierBindingFull v
  (IdentifierF dom _) _ _\<close> shape, with the choice schema-dependent.

  Coverage v2 (Issue #202 close-out): \<open>QuantifierF\<close> over all four kinds
  (\<open>QAll\<close>/\<open>QNo\<close>/\<open>QSome\<close>/\<open>QExists\<close>, multi-binding right-folded), and
  multi-field \<open>WithF\<close> (folded into nested \<open>WithRec\<close>s). Still punted:
  \<open>CallF\<close> (predicate inlining requires definition lookup beyond enum names),
  \<open>IfF\<close> (no \<open>If\<close> ctor in the verified subset), \<open>BSubset\<close> (the \<open>\<forall> x \<in> r1.
  Member x r2\<close> desugar requires fresh-variable generation that this v2 does
  not thread).

  \<open>lower_soundness\<close> in \<open>Soundness.thy\<close> remains a thin corollary of
  \<open>soundness\<close>: every \<open>e :: expr\<close> produced by \<open>lower\<close> falls under the
  universal soundness theorem regardless of which \<open>expr_full\<close> shape it came
  from. No per-case \<open>lower_*_step\<close> proofs are needed.\<close>

primrec string_in_list :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> bool" where
  "string_in_list y [] = False"
| "string_in_list y (x # xs) = (x = y \<or> string_in_list y xs)"

fun lower_forall_step ::
    "String.literal list \<Rightarrow> quantifier_binding_full
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower_forall_step enums (QuantifierBindingFull v (IdentifierF dnm _) _ _) body sp =
     (if string_in_list dnm enums
        then Some (ForallEnum v dnm body sp)
        else Some (ForallRel v dnm body sp))"
| "lower_forall_step _ _ _ _ = None"

fun lower_forall_bindings ::
    "String.literal list \<Rightarrow> quantifier_binding_full list
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower_forall_bindings _ [] _ _ = None"
| "lower_forall_bindings enums [b] body sp = lower_forall_step enums b body sp"
| "lower_forall_bindings enums (b # rest) body sp =
     (case lower_forall_bindings enums rest body sp of
        None \<Rightarrow> None
      | Some inner \<Rightarrow> lower_forall_step enums b inner sp)"

function (sequential) lower :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> expr option"
and lower_set_list ::
    "String.literal list \<Rightarrow> expr_full list \<Rightarrow> option_span \<Rightarrow> expr option"
and lower_with_assigns ::
    "String.literal list \<Rightarrow> field_assign_full list
       \<Rightarrow> expr \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower _ (BoolLitF b sp)     = Some (BoolLit b sp)"
| "lower _ (IntLitF n sp)      = Some (IntLit n sp)"
| "lower _ (IdentifierF x sp)  = Some (Ident x sp)"
| "lower _ (FloatLitF _ _)     = None"
| "lower _ (StringLitF _ _)    = None"
| "lower _ (NoneLitF _)        = None"
| "lower _ (LambdaF _ _ _)     = None"
| "lower _ (CallF _ _ _)       = None"
| "lower _ (ConstructorF _ _ _)     = None"
| "lower _ (MapLiteralF _ _)        = None"
| "lower _ (SeqLiteralF _ _)        = None"
| "lower _ (SetComprehensionF _ _ _ _) = None"
| "lower _ (SomeWrapF _ _)     = None"
| "lower _ (TheF _ _ _ _)      = None"
| "lower _ (MatchesF _ _ _)    = None"
| "lower _ (IfF _ _ _ _)       = None"

| "lower enums (QuantifierF k bs body sp) =
     (case lower enums body of
        None \<Rightarrow> None
      | Some body' \<Rightarrow>
          (case k of
             QAll \<Rightarrow> lower_forall_bindings enums bs body' sp
           | QNo \<Rightarrow> lower_forall_bindings enums bs (UnNot body' sp) sp
           | QSome \<Rightarrow>
               map_option (\<lambda>e. UnNot e sp)
                 (lower_forall_bindings enums bs (UnNot body' sp) sp)
           | QExists \<Rightarrow>
               map_option (\<lambda>e. UnNot e sp)
                 (lower_forall_bindings enums bs (UnNot body' sp) sp)))"

| "lower enums (UnaryOpF op e sp) =
     (case op of
        UNot \<Rightarrow> map_option (\<lambda>e'. UnNot e' sp) (lower enums e)
      | UNegate \<Rightarrow> map_option (\<lambda>e'. UnNeg e' sp) (lower enums e)
      | UCardinality \<Rightarrow>
          (case e of
             IdentifierF x _ \<Rightarrow> Some (CardRel x sp)
           | _ \<Rightarrow> None)
      | UPower \<Rightarrow> None)"

| "lower enums (BinaryOpF op l r sp) =
     (case op of
        BAnd \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin AndOp l' r' sp)
           | _ \<Rightarrow> None)
      | BOr \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin OrOp l' r' sp)
           | _ \<Rightarrow> None)
      | BImplies \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin ImpliesOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIff \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin IffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BEq \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BNeq \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp NeqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLt \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGt \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLe \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGe \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BAdd \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith AddOp l' r' sp)
           | _ \<Rightarrow> None)
      | BSub \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith SubOp l' r' sp)
           | _ \<Rightarrow> None)
      | BMul \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith MulOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiv \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (Arith DivOp l' r' sp)
           | _ \<Rightarrow> None)
      | BUnion \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin UnionOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIntersect \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin IntersectOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiff \<Rightarrow>
          (case (lower enums l, lower enums r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin DiffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. Member l' rel sp) (lower enums l)
           | _ \<Rightarrow>
               (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (SetMember l' r' sp)
                | _ \<Rightarrow> None))
      | BNotIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. UnNot (Member l' rel sp) sp) (lower enums l)
           | _ \<Rightarrow>
               (case (lower enums l, lower enums r) of
                  (Some l', Some r') \<Rightarrow> Some (UnNot (SetMember l' r' sp) sp)
                | _ \<Rightarrow> None))
      | BSubset \<Rightarrow> None)"

| "lower enums (LetF x v body sp) =
     (case (lower enums v, lower enums body) of
        (Some v', Some b') \<Rightarrow> Some (LetIn x v' b' sp)
      | _ \<Rightarrow> None)"
| "lower _ (EnumAccessF base mem sp) =
     (case base of
        IdentifierF en _ \<Rightarrow> Some (EnumAccess en mem sp)
      | _ \<Rightarrow> None)"
| "lower enums (FieldAccessF base fname sp) =
     map_option (\<lambda>b'. FieldAccess b' fname sp) (lower enums base)"
| "lower enums (IndexF base key sp) =
     (case (lower enums base, lower enums key) of
        (Some base', Some key') \<Rightarrow>
          (case peel_relation_ref base' of
             Some _ \<Rightarrow> Some (IndexRel base' key' sp)
           | None   \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "lower enums (PrimeF e sp) = map_option (\<lambda>e'. Prime e' sp) (lower enums e)"
| "lower enums (PreF e sp)   = map_option (\<lambda>e'. Pre e' sp) (lower enums e)"
| "lower enums (WithF base updates sp) =
     (case lower enums base of
        None \<Rightarrow> None
      | Some base' \<Rightarrow> lower_with_assigns enums updates base' sp)"
| "lower enums (SetLiteralF elems sp) = lower_set_list enums elems sp"

| "lower_set_list _ [] sp = Some (SetEmpty sp)"
| "lower_set_list enums (e # rest) sp =
     (case (lower enums e, lower_set_list enums rest sp) of
        (Some e', Some s') \<Rightarrow> Some (SetInsert e' s' sp)
      | _ \<Rightarrow> None)"

| "lower_with_assigns _ [] base _ = Some base"
| "lower_with_assigns enums (FieldAssignFull fld v _ # rest) base sp =
     (case lower enums v of
        None \<Rightarrow> None
      | Some v' \<Rightarrow> lower_with_assigns enums rest (WithRec base fld v' sp) sp)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>p. case p of
               Inl (_, e) \<Rightarrow> size e
             | Inr (Inl (_, elems, _)) \<Rightarrow> size_list size elems
             | Inr (Inr (_, updates, _, _)) \<Rightarrow> size_list size updates),
        (\<lambda>p. case p of
               Inl _ \<Rightarrow> 0
             | Inr (Inl (_, elems, _)) \<Rightarrow> Suc (length elems)
             | Inr (Inr (_, updates, _, _)) \<Rightarrow> Suc (length updates))
       ]")
     auto

end
