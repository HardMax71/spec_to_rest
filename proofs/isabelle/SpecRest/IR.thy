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

text \<open>Phase 9m — IR codec round-trips. \<open>binOpToTs\<close> is reused as the
  encoder; \<open>dec_bin_op\<close> is its inverse. Same for the four sibling enums
  (\<open>un_op\<close>, \<open>quant_kind\<close>, \<open>multiplicity\<close>, \<open>binding_kind\<close>) whose
  string tokens match \<open>modules/ir/.../Serialize.scala\<close> verbatim. The
  round-trip theorems below are the cleanest dedup target for the
  Phase-9m extraction PR that replaces those hand circe codecs with
  calls to extracted functions.\<close>

fun un_op_to_ts :: "un_op_full \<Rightarrow> String.literal" where
  "un_op_to_ts UNot         = STR ''not''"
| "un_op_to_ts UNegate      = STR ''negate''"
| "un_op_to_ts UCardinality = STR ''cardinality''"
| "un_op_to_ts UPower       = STR ''power''"

fun quant_kind_to_ts :: "quant_kind_full \<Rightarrow> String.literal" where
  "quant_kind_to_ts QAll    = STR ''all''"
| "quant_kind_to_ts QSome   = STR ''some''"
| "quant_kind_to_ts QNo     = STR ''no''"
| "quant_kind_to_ts QExists = STR ''exists''"

fun multiplicity_to_ts :: "multiplicity \<Rightarrow> String.literal" where
  "multiplicity_to_ts MultOne  = STR ''one''"
| "multiplicity_to_ts MultLone = STR ''lone''"
| "multiplicity_to_ts MultSome = STR ''some''"
| "multiplicity_to_ts MultSet  = STR ''set''"

fun binding_kind_to_ts :: "binding_kind_full \<Rightarrow> String.literal" where
  "binding_kind_to_ts BkIn    = STR ''in''"
| "binding_kind_to_ts BkColon = STR ''colon''"

definition dec_bin_op :: "String.literal \<Rightarrow> bin_op_full option" where
  "dec_bin_op s =
     (if s = STR ''and''       then Some BAnd
      else if s = STR ''or''        then Some BOr
      else if s = STR ''implies''   then Some BImplies
      else if s = STR ''iff''       then Some BIff
      else if s = STR ''=''         then Some BEq
      else if s = STR ''!=''        then Some BNeq
      else if s = STR ''<''         then Some BLt
      else if s = STR ''>''         then Some BGt
      else if s = STR ''<=''        then Some BLe
      else if s = STR ''>=''        then Some BGe
      else if s = STR ''in''        then Some BIn
      else if s = STR ''not_in''    then Some BNotIn
      else if s = STR ''subset''    then Some BSubset
      else if s = STR ''union''     then Some BUnion
      else if s = STR ''intersect'' then Some BIntersect
      else if s = STR ''minus''     then Some BDiff
      else if s = STR ''+''         then Some BAdd
      else if s = STR ''-''         then Some BSub
      else if s = STR ''*''         then Some BMul
      else if s = STR ''/''         then Some BDiv
      else None)"

definition dec_un_op :: "String.literal \<Rightarrow> un_op_full option" where
  "dec_un_op s =
     (if s = STR ''not''         then Some UNot
      else if s = STR ''negate''      then Some UNegate
      else if s = STR ''cardinality'' then Some UCardinality
      else if s = STR ''power''       then Some UPower
      else None)"

definition dec_quant_kind :: "String.literal \<Rightarrow> quant_kind_full option" where
  "dec_quant_kind s =
     (if s = STR ''all''    then Some QAll
      else if s = STR ''some''   then Some QSome
      else if s = STR ''no''     then Some QNo
      else if s = STR ''exists'' then Some QExists
      else None)"

definition dec_multiplicity :: "String.literal \<Rightarrow> multiplicity option" where
  "dec_multiplicity s =
     (if s = STR ''one''  then Some MultOne
      else if s = STR ''lone'' then Some MultLone
      else if s = STR ''some'' then Some MultSome
      else if s = STR ''set''  then Some MultSet
      else None)"

definition dec_binding_kind :: "String.literal \<Rightarrow> binding_kind_full option" where
  "dec_binding_kind s =
     (if s = STR ''in''    then Some BkIn
      else if s = STR ''colon'' then Some BkColon
      else None)"

lemma binOpToTs_inverse:
  "dec_bin_op (binOpToTs op) = Some op"
  by (cases op) (simp_all add: dec_bin_op_def)

lemma un_op_to_ts_inverse:
  "dec_un_op (un_op_to_ts op) = Some op"
  by (cases op) (simp_all add: dec_un_op_def)

lemma quant_kind_to_ts_inverse:
  "dec_quant_kind (quant_kind_to_ts k) = Some k"
  by (cases k) (simp_all add: dec_quant_kind_def)

lemma multiplicity_to_ts_inverse:
  "dec_multiplicity (multiplicity_to_ts m) = Some m"
  by (cases m) (simp_all add: dec_multiplicity_def)

lemma binding_kind_to_ts_inverse:
  "dec_binding_kind (binding_kind_to_ts k) = Some k"
  by (cases k) (simp_all add: dec_binding_kind_def)

text \<open>Phase 9m (continued) — JSON value model + structural codecs.
  Minimal \<open>json\<close> ADT modelling circe's \<open>Json\<close> (null/bool/int/string/
  array/object). \<open>enc_*\<close>/\<open>dec_*\<close> for \<open>span_t\<close> and \<open>type_expr_full\<close>
  follow the circe convention used by \<open>Serialize.scala\<close>; round-trip
  lemmas (\<open>enc_dec_span\<close>, \<open>enc_dec_type_expr\<close>) close via the relevant
  structural induction. The \<open>expr_full\<close> codec (mutual with the four
  inner-list types) is the next size-induction proof in this layer.\<close>

datatype (plugins only: code size) json =
    JNull
  | JBool bool
  | JInt int
  | JStr "String.literal"
  | JArr "json list"
  | JObj "(String.literal \<times> json) list"

fun json_lookup :: "(String.literal \<times> json) list \<Rightarrow> String.literal \<Rightarrow> json option" where
  "json_lookup [] _ = None"
| "json_lookup ((k, v) # rest) key =
     (if k = key then Some v else json_lookup rest key)"

fun dec_int :: "json \<Rightarrow> int option" where
  "dec_int (JInt n) = Some n"
| "dec_int _        = None"

fun enc_span :: "span_t \<Rightarrow> json" where
  "enc_span (SpanT sl sc el ec) =
     JObj [(STR ''startLine'', JInt sl),
           (STR ''startCol'',  JInt sc),
           (STR ''endLine'',   JInt el),
           (STR ''endCol'',    JInt ec)]"

definition dec_span :: "json \<Rightarrow> span_t option" where
  "dec_span j =
     (case j of
        JObj kvs \<Rightarrow>
          (case (json_lookup kvs (STR ''startLine''),
                 json_lookup kvs (STR ''startCol''),
                 json_lookup kvs (STR ''endLine''),
                 json_lookup kvs (STR ''endCol'')) of
             (Some sl, Some sc, Some el, Some ec) \<Rightarrow>
               (case (dec_int sl, dec_int sc, dec_int el, dec_int ec) of
                  (Some sl', Some sc', Some el', Some ec') \<Rightarrow>
                    Some (SpanT sl' sc' el' ec')
                | _ \<Rightarrow> None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

lemma enc_dec_span:
  "dec_span (enc_span s) = Some s"
  by (cases s) (simp add: dec_span_def)

fun enc_option_span :: "option_span \<Rightarrow> json" where
  "enc_option_span None     = JNull"
| "enc_option_span (Some s) = enc_span s"

definition dec_option_span :: "json \<Rightarrow> option_span option" where
  "dec_option_span j =
     (case j of JNull \<Rightarrow> Some None | _ \<Rightarrow> map_option Some (dec_span j))"

lemma enc_span_form:
  "\<exists>kvs. enc_span s = JObj kvs"
  by (cases s) auto

lemma enc_dec_option_span:
  "dec_option_span (enc_option_span sp) = Some sp"
proof (cases sp)
  case None
  thus ?thesis by (simp add: dec_option_span_def)
next
  case (Some a)
  obtain kvs where eq: "enc_span a = JObj kvs"
    using enc_span_form by blast
  have ds: "dec_span (JObj kvs) = Some a"
    using enc_dec_span[of a] eq by simp
  show ?thesis using Some eq ds by (simp add: dec_option_span_def)
qed

fun enc_type_expr :: "type_expr_full \<Rightarrow> json"
where
  "enc_type_expr (NamedTypeF nm sp) =
     JObj [(STR ''kind'', JStr (STR ''named'')),
           (STR ''name'', JStr nm),
           (STR ''span'', enc_option_span sp)]"
| "enc_type_expr (SetTypeF inner sp) =
     JObj [(STR ''kind'',  JStr (STR ''set'')),
           (STR ''inner'', enc_type_expr inner),
           (STR ''span'',  enc_option_span sp)]"
| "enc_type_expr (MapTypeF kt vt sp) =
     JObj [(STR ''kind'',  JStr (STR ''map'')),
           (STR ''key'',   enc_type_expr kt),
           (STR ''value'', enc_type_expr vt),
           (STR ''span'',  enc_option_span sp)]"
| "enc_type_expr (SeqTypeF inner sp) =
     JObj [(STR ''kind'',  JStr (STR ''seq'')),
           (STR ''inner'', enc_type_expr inner),
           (STR ''span'',  enc_option_span sp)]"
| "enc_type_expr (OptionTypeF inner sp) =
     JObj [(STR ''kind'',  JStr (STR ''option'')),
           (STR ''inner'', enc_type_expr inner),
           (STR ''span'',  enc_option_span sp)]"
| "enc_type_expr (RelationTypeF dt mult ct sp) =
     JObj [(STR ''kind'',  JStr (STR ''relation'')),
           (STR ''dom'',   enc_type_expr dt),
           (STR ''mult'',  JStr (multiplicity_to_ts mult)),
           (STR ''cod'',   enc_type_expr ct),
           (STR ''span'',  enc_option_span sp)]"

lemma json_lookup_size:
  "json_lookup kvs key = Some v
     \<Longrightarrow> size v < Suc (size_list (size_prod (\<lambda>_. 0) size) kvs)"
  by (induction kvs) (auto split: if_splits)

lemma json_lookup_size_sym:
  "Some v = json_lookup kvs key
     \<Longrightarrow> size v < Suc (size_list (size_prod (\<lambda>_. 0) size) kvs)"
  using json_lookup_size by metis

function dec_type_expr :: "json \<Rightarrow> type_expr_full option" where
  "dec_type_expr j =
     (case j of
        JObj kvs \<Rightarrow>
          (case json_lookup kvs (STR ''kind'') of
             Some (JStr k) \<Rightarrow>
               (if k = STR ''named'' then
                  (case (json_lookup kvs (STR ''name''),
                         json_lookup kvs (STR ''span'')) of
                     (Some (JStr nm), Some js) \<Rightarrow>
                       map_option (\<lambda>sp. NamedTypeF nm sp) (dec_option_span js)
                   | _ \<Rightarrow> None)
                else if k = STR ''set'' then
                  (case (json_lookup kvs (STR ''inner''),
                         json_lookup kvs (STR ''span'')) of
                     (Some ji, Some js) \<Rightarrow>
                       (case (dec_type_expr ji, dec_option_span js) of
                          (Some t, Some sp) \<Rightarrow> Some (SetTypeF t sp)
                        | _ \<Rightarrow> None)
                   | _ \<Rightarrow> None)
                else if k = STR ''map'' then
                  (case (json_lookup kvs (STR ''key''),
                         json_lookup kvs (STR ''value''),
                         json_lookup kvs (STR ''span'')) of
                     (Some jk, Some jv, Some js) \<Rightarrow>
                       (case (dec_type_expr jk, dec_type_expr jv,
                              dec_option_span js) of
                          (Some kt, Some vt, Some sp) \<Rightarrow> Some (MapTypeF kt vt sp)
                        | _ \<Rightarrow> None)
                   | _ \<Rightarrow> None)
                else if k = STR ''seq'' then
                  (case (json_lookup kvs (STR ''inner''),
                         json_lookup kvs (STR ''span'')) of
                     (Some ji, Some js) \<Rightarrow>
                       (case (dec_type_expr ji, dec_option_span js) of
                          (Some t, Some sp) \<Rightarrow> Some (SeqTypeF t sp)
                        | _ \<Rightarrow> None)
                   | _ \<Rightarrow> None)
                else if k = STR ''option'' then
                  (case (json_lookup kvs (STR ''inner''),
                         json_lookup kvs (STR ''span'')) of
                     (Some ji, Some js) \<Rightarrow>
                       (case (dec_type_expr ji, dec_option_span js) of
                          (Some t, Some sp) \<Rightarrow> Some (OptionTypeF t sp)
                        | _ \<Rightarrow> None)
                   | _ \<Rightarrow> None)
                else if k = STR ''relation'' then
                  (case (json_lookup kvs (STR ''dom''),
                         json_lookup kvs (STR ''mult''),
                         json_lookup kvs (STR ''cod''),
                         json_lookup kvs (STR ''span'')) of
                     (Some jd, Some (JStr jm), Some jc, Some js) \<Rightarrow>
                       (case (dec_type_expr jd, dec_multiplicity jm,
                              dec_type_expr jc, dec_option_span js) of
                          (Some dt, Some m, Some ct, Some sp) \<Rightarrow>
                            Some (RelationTypeF dt m ct sp)
                        | _ \<Rightarrow> None)
                   | _ \<Rightarrow> None)
                else None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measure size") (auto dest!: json_lookup_size json_lookup_size_sym)

lemma enc_dec_type_expr:
  "dec_type_expr (enc_type_expr t) = Some t"
proof (induction t)
  case (NamedTypeF nm sp) thus ?case
    by (simp add: enc_dec_option_span)
next
  case (SetTypeF inner sp) thus ?case
    by (simp add: enc_dec_option_span)
next
  case (MapTypeF kt vt sp) thus ?case
    by (simp add: enc_dec_option_span)
next
  case (SeqTypeF inner sp) thus ?case
    by (simp add: enc_dec_option_span)
next
  case (OptionTypeF inner sp) thus ?case
    by (simp add: enc_dec_option_span)
next
  case (RelationTypeF dt mult ct sp) thus ?case
    by (simp add: enc_dec_option_span multiplicity_to_ts_inverse)
qed

text \<open>Phase 9d: \<open>spanOf\<close> projects the trailing \<open>option_span\<close> of any
  \<open>expr_full\<close> (dual of \<open>stripSpans\<close>); \<open>flattenAnd\<close> right-flattens a
  \<open>BAnd\<close> conjunction tree into its conjunct list; \<open>typeName\<close> extracts a
  \<open>NamedTypeF\<close>'s name. Each replaced several byte-identical hand copies
  scattered across verify / lint / convention / testgen (3x \<open>spanOpt\<close>,
  4x \<open>flattenAnd\<close>, 5x \<open>typeName\<close>). Total; termination automatic.\<close>

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

text \<open>Phase 9ww: per-clause matchers lifted off Scala
  \<open>convention.ExprAnalysis\<close>. \<open>flattenEnsures\<close> already breaks \<open>\<and>\<close>-chains
  so each matcher pattern-matches the top-level clause head and returns
  hits; the consumer composes \<open>concat (map matcher (flattenEnsures es))\<close>.
  No expr_full walker needed (Scala keeps the AST walk for the few
  callers that need it — \<open>collectPrimedIdentifiers\<close>,
  \<open>collectWithFields\<close>, \<open>collectFieldAccessNames\<close> — to avoid a
  polymorphic-HOF walker that blows up Isabelle build wall-time).\<close>

fun preservedRelationOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "preservedRelationOf stateFields
     (BinaryOpF BEq (PrimeF (IdentifierF l _) _) (IdentifierF r _) _) =
        (if l = r \<and> list_ex (\<lambda>n. n = l) stateFields then [l] else [])"
| "preservedRelationOf _ _ = []"

definition collectPreservedRelations ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "collectPreservedRelations es stateFields \<equiv>
     remdups (List.concat (map (preservedRelationOf stateFields) (flattenEnsures es)))"

fun containsPreInPlusChain ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "containsPreInPlusChain (PreF (IdentifierF n _) _) field = (n = field)"
| "containsPreInPlusChain (BinaryOpF BAdd l r _) field =
     (containsPreInPlusChain l field \<or> containsPreInPlusChain r field)"
| "containsPreInPlusChain _ _ = False"

fun createPatternOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "createPatternOf stateFields
     (BinaryOpF BEq (PrimeF (IdentifierF name _) _)
                     (BinaryOpF BAdd l r sp) _) =
        (if list_ex (\<lambda>n. n = name) stateFields
            \<and> containsPreInPlusChain (BinaryOpF BAdd l r sp) name
         then [name] else [])"
| "createPatternOf _ _ = []"

definition detectCreatePattern ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "detectCreatePattern es stateFields \<equiv>
     (case List.concat (map (createPatternOf stateFields) (flattenEnsures es)) of
        []       \<Rightarrow> None
      | (x # _)  \<Rightarrow> Some x)"

fun deletePatternOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "deletePatternOf stateFields
     (BinaryOpF BNotIn _ (PrimeF (IdentifierF n _) _) _) =
        (if list_ex (\<lambda>s. s = n) stateFields then [n] else [])"
| "deletePatternOf _ _ = []"

definition detectDeletePattern ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "detectDeletePattern es stateFields \<equiv>
     (case List.concat (map (deletePatternOf stateFields) (flattenEnsures es)) of
        []       \<Rightarrow> None
      | (x # _)  \<Rightarrow> Some x)"

fun keyExistsInRequiresOf ::
  "String.literal list \<Rightarrow> expr_full \<Rightarrow> String.literal list" where
  "keyExistsInRequiresOf stateFields
     (BinaryOpF BIn _ (IdentifierF n _) _) =
        (if list_ex (\<lambda>s. s = n) stateFields then [n] else [])"
| "keyExistsInRequiresOf _ _ = []"

definition detectKeyExistsInRequires ::
  "expr_full list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "detectKeyExistsInRequires requires stateFields \<equiv>
     remdups (List.concat
       (map (keyExistsInRequiresOf stateFields) (flattenEnsures requires)))"

fun resolveWithBase :: "expr_full \<Rightarrow> String.literal option" where
  "resolveWithBase (IdentifierF _ _) = None"
| "resolveWithBase (IndexF (PreF (IdentifierF n _) _) _ _) = Some n"
| "resolveWithBase (IndexF (IdentifierF n _) _ _)          = Some n"
| "resolveWithBase (IndexF base _ _)                       = rootIdentifier base"
| "resolveWithBase other                                   = rootIdentifier other"

fun fieldAssignName :: "field_assign_full \<Rightarrow> String.literal" where
  "fieldAssignName (FieldAssignFull n _ _) = n"

datatype (plugins only: code size) with_info_full =
  WithInfoFull "String.literal list" "String.literal option"

fun withInfoFieldNames :: "with_info_full \<Rightarrow> String.literal list" where
  "withInfoFieldNames (WithInfoFull fs _) = fs"

fun withInfoBaseIdentifier :: "with_info_full \<Rightarrow> String.literal option" where
  "withInfoBaseIdentifier (WithInfoFull _ b) = b"

text \<open>Phase 9ww: \<open>collectExprInfo\<close> is the unified expr_full walker
  that collects, in a single pass, every datum the classifier /
  diagnostic layers need from an AST: primed identifiers, field-access
  names, and with-clause info. Returning a tuple lets three logically
  distinct collectors share one 5-way mutual \<open>fun\<close> instead of three
  copies; that cuts Isabelle build wall-time roughly 3x vs split walkers
  (mutual \<open>fun\<close>'s meta-theory simp / induct / elim rules scales
  superlinearly per declaration, so one big walker is cheaper than
  three).\<close>

type_synonym collected_full =
  "String.literal list \<times> String.literal list \<times> with_info_full list"

definition emptyCollected :: "collected_full" where
  "emptyCollected = ([], [], [])"

fun combineCollected :: "collected_full \<Rightarrow> collected_full \<Rightarrow> collected_full" where
  "combineCollected (p1, f1, w1) (p2, f2, w2) = (p1 @ p2, f1 @ f2, w1 @ w2)"

fun consPrimed :: "String.literal option \<Rightarrow> collected_full \<Rightarrow> collected_full" where
  "consPrimed None     c          = c"
| "consPrimed (Some n) (p, f, w)  = (n # p, f, w)"

fun consFieldAccess :: "String.literal \<Rightarrow> collected_full \<Rightarrow> collected_full" where
  "consFieldAccess n (p, f, w) = (p, n # f, w)"

fun consWithInfo :: "with_info_full \<Rightarrow> collected_full \<Rightarrow> collected_full" where
  "consWithInfo wi (p, f, w) = (p, f, wi # w)"

fun collectExprInfo :: "expr_full \<Rightarrow> collected_full"
and collectExprInfo_list :: "expr_full list \<Rightarrow> collected_full"
and collectExprInfo_fields :: "field_assign_full list \<Rightarrow> collected_full"
and collectExprInfo_entries :: "map_entry_full list \<Rightarrow> collected_full"
and collectExprInfo_bindings :: "quantifier_binding_full list \<Rightarrow> collected_full"
where
  "collectExprInfo (PrimeF inner _)        = consPrimed (rootIdentifier inner) (collectExprInfo inner)"
| "collectExprInfo (FieldAccessF base n _) = consFieldAccess n (collectExprInfo base)"
| "collectExprInfo (WithF base ups _) =
     consWithInfo (WithInfoFull (map fieldAssignName ups) (resolveWithBase base))
                  (combineCollected (collectExprInfo base) (collectExprInfo_fields ups))"
| "collectExprInfo (BinaryOpF _ l r _)         = combineCollected (collectExprInfo l) (collectExprInfo r)"
| "collectExprInfo (UnaryOpF _ e _)            = collectExprInfo e"
| "collectExprInfo (QuantifierF _ bs body _)   = combineCollected (collectExprInfo_bindings bs) (collectExprInfo body)"
| "collectExprInfo (SomeWrapF e _)             = collectExprInfo e"
| "collectExprInfo (TheF _ d b _)              = combineCollected (collectExprInfo d) (collectExprInfo b)"
| "collectExprInfo (EnumAccessF base _ _)      = collectExprInfo base"
| "collectExprInfo (IndexF b i _)              = combineCollected (collectExprInfo b) (collectExprInfo i)"
| "collectExprInfo (CallF c args _)            = combineCollected (collectExprInfo c) (collectExprInfo_list args)"
| "collectExprInfo (PreF e _)                  = collectExprInfo e"
| "collectExprInfo (IfF c t el _) =
     combineCollected (combineCollected (collectExprInfo c) (collectExprInfo t)) (collectExprInfo el)"
| "collectExprInfo (LetF _ v b _)              = combineCollected (collectExprInfo v) (collectExprInfo b)"
| "collectExprInfo (LambdaF _ b _)             = collectExprInfo b"
| "collectExprInfo (ConstructorF _ fs _)       = collectExprInfo_fields fs"
| "collectExprInfo (SetLiteralF xs _)          = collectExprInfo_list xs"
| "collectExprInfo (MapLiteralF es _)          = collectExprInfo_entries es"
| "collectExprInfo (SetComprehensionF _ d p _) = combineCollected (collectExprInfo d) (collectExprInfo p)"
| "collectExprInfo (SeqLiteralF xs _)          = collectExprInfo_list xs"
| "collectExprInfo (MatchesF e _ _)            = collectExprInfo e"
| "collectExprInfo (IntLitF _ _)               = emptyCollected"
| "collectExprInfo (FloatLitF _ _)             = emptyCollected"
| "collectExprInfo (StringLitF _ _)            = emptyCollected"
| "collectExprInfo (BoolLitF _ _)              = emptyCollected"
| "collectExprInfo (NoneLitF _)                = emptyCollected"
| "collectExprInfo (IdentifierF _ _)           = emptyCollected"
| "collectExprInfo_list []                                                = emptyCollected"
| "collectExprInfo_list (x # xs)                                          = combineCollected (collectExprInfo x) (collectExprInfo_list xs)"
| "collectExprInfo_fields []                                              = emptyCollected"
| "collectExprInfo_fields (FieldAssignFull _ v _ # fs)                    = combineCollected (collectExprInfo v) (collectExprInfo_fields fs)"
| "collectExprInfo_entries []                                             = emptyCollected"
| "collectExprInfo_entries (MapEntryFull k v _ # es)                      = combineCollected (combineCollected (collectExprInfo k) (collectExprInfo v)) (collectExprInfo_entries es)"
| "collectExprInfo_bindings []                                            = emptyCollected"
| "collectExprInfo_bindings (QuantifierBindingFull _ d _ _ # bs)          = combineCollected (collectExprInfo d) (collectExprInfo_bindings bs)"

definition collectPrimedIdentifiers ::
  "expr_full list \<Rightarrow> String.literal list" where
  "collectPrimedIdentifiers es \<equiv> remdups (fst (collectExprInfo_list es))"

definition collectFieldAccessNames :: "expr_full \<Rightarrow> String.literal list" where
  "collectFieldAccessNames e \<equiv> remdups (fst (snd (collectExprInfo e)))"

definition collectWithFields ::
  "expr_full list \<Rightarrow> with_info_full option" where
  "collectWithFields es \<equiv>
     (case snd (snd (collectExprInfo_list es)) of
        []       \<Rightarrow> None
      | (x # _)  \<Rightarrow> Some x)"

fun isInputCollectionType :: "type_expr_full \<Rightarrow> bool" where
  "isInputCollectionType (SetTypeF _ _)   = True"
| "isInputCollectionType (SeqTypeF _ _)   = True"
| "isInputCollectionType (MapTypeF _ _ _) = True"
| "isInputCollectionType _                = False"

fun paramTypeFull :: "param_decl_full \<Rightarrow> type_expr_full" where
  "paramTypeFull (ParamDeclFull _ t _) = t"

definition countFilterParams :: "param_decl_full list \<Rightarrow> nat" where
  "countFilterParams ps \<equiv>
     length (filter (\<lambda>p. case paramTypeFull p of OptionTypeF _ _ \<Rightarrow> True | _ \<Rightarrow> False) ps)"

definition hasCollectionInput :: "param_decl_full list \<Rightarrow> bool" where
  "hasCollectionInput ps \<equiv> list_ex (\<lambda>p. isInputCollectionType (paramTypeFull p)) ps"

fun typeName :: "type_expr_full \<Rightarrow> String.literal option" where
  "typeName (NamedTypeF n _) = Some n"
| "typeName _                = None"

text \<open>Phase 9e: \<open>flattenInheritance\<close> resolves \<open>entity Child extends
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

fun enumNameFull :: "enum_decl_full \<Rightarrow> String.literal" where
  "enumNameFull (EnumDeclFull n _ _) = n"

fun entityNameFull :: "entity_decl_full \<Rightarrow> String.literal" where
  "entityNameFull (EntityDeclFull n _ _ _ _) = n"

fun entityParentFull :: "entity_decl_full \<Rightarrow> String.literal option" where
  "entityParentFull (EntityDeclFull _ p _ _ _) = p"

fun entityFieldsFull :: "entity_decl_full \<Rightarrow> field_decl_full list" where
  "entityFieldsFull (EntityDeclFull _ _ fs _ _) = fs"

fun entityInvsFull :: "entity_decl_full \<Rightarrow> expr_full list" where
  "entityInvsFull (EntityDeclFull _ _ _ iv _) = iv"

fun fieldNameFull :: "field_decl_full \<Rightarrow> String.literal" where
  "fieldNameFull (FieldDeclFull n _ _ _) = n"

fun fieldTypeFull :: "field_decl_full \<Rightarrow> type_expr_full" where
  "fieldTypeFull (FieldDeclFull _ t _ _) = t"

fun state_fieldNameFull :: "state_field_decl_full \<Rightarrow> String.literal" where
  "state_fieldNameFull (StateFieldDeclFull n _ _) = n"

fun state_fieldTypeFull :: "state_field_decl_full \<Rightarrow> type_expr_full" where
  "state_fieldTypeFull (StateFieldDeclFull _ t _) = t"

definition entityByName ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> entity_decl_full option" where
  "entityByName es nm =
     map_of (map (\<lambda>e. (entityNameFull e, e)) (rev es)) nm"

definition findFieldDeclFull ::
  "field_decl_full list \<Rightarrow> String.literal \<Rightarrow> field_decl_full option" where
  "findFieldDeclFull fs nm \<equiv>
     List.find (\<lambda>fd. fieldNameFull fd = nm) fs"

definition entityFieldDeclLookup ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal
     \<Rightarrow> field_decl_full option" where
  "entityFieldDeclLookup es ename fname \<equiv>
     case entityByName es ename of
       None    \<Rightarrow> None
     | Some ed \<Rightarrow> findFieldDeclFull (entityFieldsFull ed) fname"

definition entityHasField ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "entityHasField es ename fname \<equiv>
     (case entityByName es ename of
        None    \<Rightarrow> False
      | Some ed \<Rightarrow> list_ex (\<lambda>fd. fieldNameFull fd = fname) (entityFieldsFull ed))"

definition entityFieldNames ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal list" where
  "entityFieldNames es ename \<equiv>
     (case entityByName es ename of
        None    \<Rightarrow> []
      | Some ed \<Rightarrow> map fieldNameFull (entityFieldsFull ed))"

definition entityNameInList ::
  "entity_decl_full list \<Rightarrow> String.literal \<Rightarrow> String.literal option" where
  "entityNameInList es nm \<equiv>
     (case entityByName es nm of
        None    \<Rightarrow> None
      | Some _  \<Rightarrow> Some nm)"

fun isCollectionType :: "type_expr_full \<Rightarrow> bool" where
  "isCollectionType (SetTypeF _ _)          = True"
| "isCollectionType (SeqTypeF _ _)          = True"
| "isCollectionType (MapTypeF _ _ _)        = True"
| "isCollectionType (RelationTypeF _ _ _ _) = True"
| "isCollectionType _                       = False"

fun assignsField ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "assignsField (FieldAccessF _ f _) field = (f = field)"
| "assignsField (IdentifierF n _)    field = (n = field)"
| "assignsField (PrimeF inner _)     field = assignsField inner field"
| "assignsField (IndexF base _ _)    field = assignsField base field"
| "assignsField _                    _     = False"

fun entityNameFromType ::
  "type_expr_full \<Rightarrow> String.literal option" where
  "entityNameFromType (RelationTypeF _ _ to _) = typeName to"
| "entityNameFromType (NamedTypeF n _)         = Some n"
| "entityNameFromType (SetTypeF inner _)       = entityNameFromType inner"
| "entityNameFromType (SeqTypeF inner _)       = entityNameFromType inner"
| "entityNameFromType (OptionTypeF inner _)    = entityNameFromType inner"
| "entityNameFromType (MapTypeF _ v _)         = entityNameFromType v"

fun relationTargetEntityName ::
  "type_expr_full \<Rightarrow> String.literal option" where
  "relationTargetEntityName
     (RelationTypeF _ _ (NamedTypeF n _) _) = Some n"
| "relationTargetEntityName (NamedTypeF n _) = Some n"
| "relationTargetEntityName _ = None"

fun referencesPrimedRelation ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "referencesPrimedRelation (PrimeF (IdentifierF n _) _) rel = (n = rel)"
| "referencesPrimedRelation _ _ = False"

fun referencesPreRelation ::
  "expr_full \<Rightarrow> String.literal \<Rightarrow> bool" where
  "referencesPreRelation (PreF (IdentifierF n _) _) rel = (n = rel)"
| "referencesPreRelation (IdentifierF n _) rel = (n = rel)"
| "referencesPreRelation _ _ = False"

fun chain_up ::
  "entity_decl_full list \<Rightarrow> nat \<Rightarrow> String.literal \<Rightarrow> String.literal list
     \<Rightarrow> entity_decl_full list" where
  "chain_up _ 0 _ _ = []"
| "chain_up es (Suc f) name seen =
     (case entityByName es name of
        None \<Rightarrow> []
      | Some e \<Rightarrow>
          (case entityParentFull e of
             None \<Rightarrow> [e]
           | Some parent \<Rightarrow>
               (if List.member seen parent then [e]
                else chain_up es f parent (name # seen) @ [e])))"

fun upsert_field ::
  "field_decl_full list \<Rightarrow> field_decl_full \<Rightarrow> field_decl_full list" where
  "upsert_field acc fd =
     (if list_ex (\<lambda>g. fieldNameFull g = fieldNameFull fd) acc
      then map (\<lambda>g. if fieldNameFull g = fieldNameFull fd then fd else g) acc
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
                        (concat (map entityFieldsFull anc) @ fs))
                     (concat (map entityInvsFull anc) @ iv)
                     sp))"

fun flattenInheritance :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity c) c) d e f g h i j k l m n p"

text \<open>Phase 9f — the Phase 9 structural primitives are now backed by proof,
  not merely totality.

  \<^item> \<open>stripSpans\<close> is idempotent: re-erasing a spans-erased tree is a
    no-op. This is exactly the property that makes the lint L04
    \<open>stripSpans e\<close>-keyed overlap classes well-defined (a fact previously
    only argued informally in the Scala rewrite).
  \<^item> \<open>flattenAnd\<close> fully decomposes: re-flattening the conjunct list is
    stable, so no top-level \<open>BAnd\<close> survives — the normalisation guarantee
    its consumers rely on.
  \<^item> \<open>flatten_entity\<close> (hence \<open>flattenInheritance\<close>) is the identity on a
    parent-less entity / service: inheritance flattening only ever rewrites
    \<open>extends\<close> declarations.

  NB \<open>flattenInheritance\<close> is deliberately NOT proven globally idempotent
  because it is not: the parent ref is retained and inherited invariants are
  concatenated, so a second application duplicates them. This is latent and
  harmless (\<open>parser.Builder.buildIRCore\<close> applies it exactly once); a fix
  (clearing the parent ref) is a separate behaviour change — it would alter
  \<open>lint.UnusedEntity\<close> reachability and the IR-JSON \<open>extends_\<close> field.\<close>

lemma stripSpans_idem:
  "stripSpans (stripSpans e) = stripSpans e"
  "stripSpans_list (stripSpans_list xs) = stripSpans_list xs"
  "stripSpans_fields (stripSpans_fields fs) = stripSpans_fields fs"
  "stripSpans_entries (stripSpans_entries ms) = stripSpans_entries ms"
  "stripSpans_bindings (stripSpans_bindings bs) = stripSpans_bindings bs"
  by (induction e and xs and fs and ms and bs
      rule: stripSpans_stripSpans_list_stripSpans_fields_stripSpans_entries_stripSpans_bindings.induct)
     auto

lemma flattenAnd_decompose:
  "concat (map flattenAnd (flattenAnd e)) = flattenAnd e"
  by (induction e rule: flattenAnd.induct) auto

lemma flattenAnd_nonempty:
  "flattenAnd e \<noteq> []"
  by (induction e rule: flattenAnd.induct) auto

lemma flattenAnd_requiresAlloy_iff:
  "requiresAlloy e \<longleftrightarrow> (\<exists>x \<in> set (flattenAnd e). requiresAlloy x)"
  by (induction e rule: flattenAnd.induct) auto

lemma flatten_entity_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity es e = e"
  by (cases e) (auto split: option.splits)

lemma flattenInheritance_id_on_parentless:
  assumes "list_all (\<lambda>x. entityParentFull x = None) c"
  shows "flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p)
           = ServiceIRFull a b c d e f g h i j k l m n p"
proof -
  have "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  thus ?thesis by simp
qed

text \<open>Phase 9k — hardened inheritance flattening.
  \<open>flatten_entity2\<close> / \<open>flattenInheritance2\<close> differ from v1 by clearing
  the parent ref on the output (\<open>pa := None\<close>) so that a second application
  is a no-op: \<open>flattenInheritance2_idem\<close>. The fields/invariants
  computation is unchanged, so on parentless input v1 and v2 agree
  (\<open>flattenInheritance2_eq_on_parentless\<close>). The PR that switches
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
                        (concat (map entityFieldsFull anc) @ fs))
                     (concat (map entityInvsFull anc) @ iv)
                     sp))"

fun flattenInheritance2 :: "service_ir_full \<Rightarrow> service_ir_full" where
  "flattenInheritance2 (ServiceIRFull a b c d e f g h i j k l m n p) =
     ServiceIRFull a b (map (flatten_entity2 c) c) d e f g h i j k l m n p"

lemma flatten_entity2_parent_cleared:
  "entityParentFull (flatten_entity2 es e) = None"
  by (cases e) (auto simp: Let_def split: option.splits if_splits)

lemma flatten_entity2_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity2 es e = e"
  by (cases e) (auto split: option.splits)

lemma flatten_entity2_eq_on_noparent:
  "entityParentFull e = None \<Longrightarrow> flatten_entity2 es e = flatten_entity es e"
  by (cases e) (auto split: option.splits)

lemma flattenInheritance2_idem:
  "flattenInheritance2 (flattenInheritance2 s) = flattenInheritance2 s"
proof (cases s)
  case (ServiceIRFull a b c d ee f g h i j k l m n p)
  let ?c' = "map (flatten_entity2 c) c"
  have "list_all (\<lambda>x. entityParentFull x = None) ?c'"
    by (simp add: list_all_iff flatten_entity2_parent_cleared)
  hence "map (flatten_entity2 ?c') ?c' = ?c'"
    by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  thus ?thesis using ServiceIRFull by simp
qed

lemma flattenInheritance2_eq_on_parentless:
  assumes "list_all (\<lambda>x. entityParentFull x = None) c"
  shows "flattenInheritance2 (ServiceIRFull a b c d e f g h i j k l m n p)
           = flattenInheritance (ServiceIRFull a b c d e f g h i j k l m n p)"
proof -
  have a: "map (flatten_entity2 c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity2_noparent)
  have b: "map (flatten_entity c) c = c"
    using assms by (intro map_idI) (auto simp: list_all_iff flatten_entity_noparent)
  from a b show ?thesis by simp
qed

definition emptyServiceIrFull :: "String.literal \<Rightarrow> service_ir_full" where
  "emptyServiceIrFull nm =
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

text \<open>Phase 9\<alpha> (small recognizers): \<open>isTrueLit\<close> matches the literal
  \<open>true\<close>; \<open>enumLiteralOf\<close> recognises an enum-member reference (via
  \<open>EnumAccessF\<close> or a bare \<open>IdentifierF\<close>). Replaces four \<open>case BoolLitF
  True _ \<Rightarrow> True\<close> copies in lint / testgen passes and three near-identical
  \<open>enumLiteralFor\<close> walkers in \<open>testgen.Behavioral\<close> /
  \<open>testgen.Stateful\<close>.\<close>

fun isTrueLit :: "expr_full \<Rightarrow> bool" where
  "isTrueLit (BoolLitF True _) = True"
| "isTrueLit _                 = False"

fun enumLiteralOf :: "expr_full \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "enumLiteralOf (EnumAccessF _ m _) ms = (if string_in_list m ms then Some m else None)"
| "enumLiteralOf (IdentifierF n _)   ms = (if string_in_list n ms then Some n else None)"
| "enumLiteralOf _                   _  = None"

text \<open>Phase 9\<alpha> (\<open>combineAnd\<close>): folds an \<open>expr_full list\<close> into a single
  left-associated AND-chain, with \<open>BoolLitF True None\<close> as the unit.
  Inverse of \<open>flattenAndAll\<close> modulo \<open>true\<close> identity. Replaces
  \<open>verify.Narration.combineConjuncts\<close> (which used \<open>foldLeft\<close> —
  left-associativity preserves byte-identical pretty-printed output). Uses
  a monomorphic accumulator to make the extracted Scala tail-recursive.\<close>

fun combineAnd_acc :: "expr_full \<Rightarrow> expr_full list \<Rightarrow> expr_full" where
  "combineAnd_acc acc []         = acc"
| "combineAnd_acc acc (x # rest) = combineAnd_acc (BinaryOpF BAnd acc x None) rest"

fun combineAnd :: "expr_full list \<Rightarrow> expr_full" where
  "combineAnd []          = BoolLitF True None"
| "combineAnd (x # rest)  = combineAnd_acc x rest"

text \<open>Phase 9\<beta> (\<open>decomposeAtom\<close>): canonical recognizer for a single
  atomic refinement constraint over \<open>value\<close>. Three consumers re-implement
  the recognizer (OpenAPI JSON-Schema, SQL CHECK, Hypothesis strategy
  synthesis) — this lifts the analysis half so each consumer becomes a
  pure renderer over \<open>refinement_atom\<close>. Compose with the existing
  \<open>flattenAnd\<close> to traverse \<open>BAnd\<close>-chains.\<close>

datatype (plugins only: code size) refinement_atom =
    RaLenCmp bin_op_full int
  | RaValueCmp bin_op_full int
  | RaMatches "String.literal"
  | RaMatchesIdent "String.literal" "String.literal"
  | RaPredCall "String.literal"
  | RaUnknown expr_full

fun isValueRef :: "expr_full \<Rightarrow> bool" where
  "isValueRef (IdentifierF n _) = (n = STR ''value'')"
| "isValueRef _                  = False"

fun isLenOfValue :: "expr_full \<Rightarrow> bool" where
  "isLenOfValue (CallF (IdentifierF n _) [arg] _) =
     (n = STR ''len'' \<and> isValueRef arg)"
| "isLenOfValue _ = False"

fun decomposeAtom :: "expr_full \<Rightarrow> refinement_atom" where
  "decomposeAtom (MatchesF (IdentifierF n _) pat _) =
     (if n = STR ''value'' then RaMatches pat else RaMatchesIdent n pat)"
| "decomposeAtom (BinaryOpF op l (IntLitF n innersp) sp) =
     (if isLenOfValue l then RaLenCmp op n
      else if isValueRef l then RaValueCmp op n
      else RaUnknown (BinaryOpF op l (IntLitF n innersp) sp))"
| "decomposeAtom (CallF (IdentifierF p identsp) [arg] sp) =
     (if isValueRef arg then RaPredCall p
      else RaUnknown (CallF (IdentifierF p identsp) [arg] sp))"
| "decomposeAtom other = RaUnknown other"

text \<open>Phase 9\<gamma> (free-var helpers): monomorphic \<open>qb_names\<close>,
  \<open>remove_name\<close>, \<open>remove_names\<close> avoid the polymorphic \<open>map\<close>/\<open>filter\<close>
  HOFs that blow up Isabelle build wall-time when used inside large mutual
  \<open>fun\<close> declarations.\<close>

fun qb_names :: "quantifier_binding_full list \<Rightarrow> String.literal list" where
  "qb_names [] = []"
| "qb_names (QuantifierBindingFull n _ _ _ # bs) = n # qb_names bs"

fun remove_name :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "remove_name _ [] = []"
| "remove_name n (x # xs) =
     (if x = n then remove_name n xs else x # remove_name n xs)"

fun remove_names :: "String.literal list \<Rightarrow> String.literal list \<Rightarrow> String.literal list" where
  "remove_names []       xs = xs"
| "remove_names (n # ns) xs = remove_name n (remove_names ns xs)"

text \<open>Phase 9\<gamma> (\<open>free_vars\<close>): collects the names of all free identifiers
  in an \<open>expr_full\<close>, respecting binders (\<open>LetF\<close>, \<open>LambdaF\<close>,
  \<open>SetComprehensionF\<close>, \<open>TheF\<close>, \<open>QuantifierF\<close>). Replaces the 60-line
  hand-rolled walker \<open>testgen.Behavioral.containsStateRefIn\<close>.\<close>

fun free_vars :: "expr_full \<Rightarrow> String.literal list"
and free_vars_list :: "expr_full list \<Rightarrow> String.literal list"
and free_vars_fields :: "field_assign_full list \<Rightarrow> String.literal list"
and free_vars_entries :: "map_entry_full list \<Rightarrow> String.literal list"
and free_vars_bindings :: "quantifier_binding_full list \<Rightarrow> String.literal list"
where
  "free_vars (IdentifierF n _)           = [n]"
| "free_vars (BinaryOpF _ l r _)         = free_vars l @ free_vars r"
| "free_vars (UnaryOpF _ e _)            = free_vars e"
| "free_vars (FieldAccessF b _ _)        = free_vars b"
| "free_vars (EnumAccessF b _ _)         = free_vars b"
| "free_vars (IndexF b i _)              = free_vars b @ free_vars i"
| "free_vars (CallF c args _)            = free_vars c @ free_vars_list args"
| "free_vars (PrimeF e _)                = free_vars e"
| "free_vars (PreF e _)                  = free_vars e"
| "free_vars (WithF b upds _)            = free_vars b @ free_vars_fields upds"
| "free_vars (IfF c t e _)               = free_vars c @ free_vars t @ free_vars e"
| "free_vars (LetF v val body _)         = free_vars val @ remove_name v (free_vars body)"
| "free_vars (LambdaF p b _)             = remove_name p (free_vars b)"
| "free_vars (ConstructorF _ fs _)       = free_vars_fields fs"
| "free_vars (SetLiteralF xs _)          = free_vars_list xs"
| "free_vars (MapLiteralF es _)          = free_vars_entries es"
| "free_vars (SetComprehensionF v d p _) = free_vars d @ remove_name v (free_vars p)"
| "free_vars (SeqLiteralF xs _)          = free_vars_list xs"
| "free_vars (MatchesF x _ _)            = free_vars x"
| "free_vars (SomeWrapF x _)             = free_vars x"
| "free_vars (TheF v d b _)              = free_vars d @ remove_name v (free_vars b)"
| "free_vars (QuantifierF _ bs body _)   =
     free_vars_bindings bs @ remove_names (qb_names bs) (free_vars body)"
| "free_vars (IntLitF _ _)               = []"
| "free_vars (FloatLitF _ _)             = []"
| "free_vars (StringLitF _ _)            = []"
| "free_vars (BoolLitF _ _)              = []"
| "free_vars (NoneLitF _)                = []"
| "free_vars_list []                                            = []"
| "free_vars_list (x # xs)                                      = free_vars x @ free_vars_list xs"
| "free_vars_fields []                                          = []"
| "free_vars_fields (FieldAssignFull _ v _ # fs)                = free_vars v @ free_vars_fields fs"
| "free_vars_entries []                                         = []"
| "free_vars_entries (MapEntryFull k v _ # es)                  = free_vars k @ free_vars v @ free_vars_entries es"
| "free_vars_bindings []                                        = []"
| "free_vars_bindings (QuantifierBindingFull _ d _ _ # bs)      = free_vars d @ free_vars_bindings bs"

text \<open>Phase 9\<gamma> (\<open>hasPrePrime\<close>): true iff the expression contains a
  \<open>PrimeF\<close> or \<open>PreF\<close> constructor anywhere. Used together with \<open>free_vars\<close>
  to express the \<open>testgen.Behavioral.containsStateRef\<close> predicate as a
  one-liner.\<close>

fun hasPrePrime :: "expr_full \<Rightarrow> bool"
and hasPrePrime_list :: "expr_full list \<Rightarrow> bool"
and hasPrePrime_fields :: "field_assign_full list \<Rightarrow> bool"
and hasPrePrime_entries :: "map_entry_full list \<Rightarrow> bool"
and hasPrePrime_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "hasPrePrime (PrimeF _ _)                  = True"
| "hasPrePrime (PreF _ _)                    = True"
| "hasPrePrime (BinaryOpF _ l r _)           = (hasPrePrime l \<or> hasPrePrime r)"
| "hasPrePrime (UnaryOpF _ e _)              = hasPrePrime e"
| "hasPrePrime (FieldAccessF b _ _)          = hasPrePrime b"
| "hasPrePrime (EnumAccessF b _ _)           = hasPrePrime b"
| "hasPrePrime (IndexF b i _)                = (hasPrePrime b \<or> hasPrePrime i)"
| "hasPrePrime (CallF c args _)              = (hasPrePrime c \<or> hasPrePrime_list args)"
| "hasPrePrime (WithF b upds _)              = (hasPrePrime b \<or> hasPrePrime_fields upds)"
| "hasPrePrime (IfF c t e _)                 = (hasPrePrime c \<or> hasPrePrime t \<or> hasPrePrime e)"
| "hasPrePrime (LetF _ v b _)                = (hasPrePrime v \<or> hasPrePrime b)"
| "hasPrePrime (LambdaF _ b _)               = hasPrePrime b"
| "hasPrePrime (ConstructorF _ fs _)         = hasPrePrime_fields fs"
| "hasPrePrime (SetLiteralF xs _)            = hasPrePrime_list xs"
| "hasPrePrime (MapLiteralF es _)            = hasPrePrime_entries es"
| "hasPrePrime (SetComprehensionF _ d p _)   = (hasPrePrime d \<or> hasPrePrime p)"
| "hasPrePrime (SeqLiteralF xs _)            = hasPrePrime_list xs"
| "hasPrePrime (MatchesF x _ _)              = hasPrePrime x"
| "hasPrePrime (SomeWrapF x _)               = hasPrePrime x"
| "hasPrePrime (TheF _ d b _)                = (hasPrePrime d \<or> hasPrePrime b)"
| "hasPrePrime (QuantifierF _ bs body _)     = (hasPrePrime_bindings bs \<or> hasPrePrime body)"
| "hasPrePrime (IntLitF _ _)                 = False"
| "hasPrePrime (FloatLitF _ _)               = False"
| "hasPrePrime (StringLitF _ _)              = False"
| "hasPrePrime (BoolLitF _ _)                = False"
| "hasPrePrime (NoneLitF _)                  = False"
| "hasPrePrime (IdentifierF _ _)             = False"
| "hasPrePrime_list []                                       = False"
| "hasPrePrime_list (x # xs)                                 = (hasPrePrime x \<or> hasPrePrime_list xs)"
| "hasPrePrime_fields []                                     = False"
| "hasPrePrime_fields (FieldAssignFull _ v _ # fs)           = (hasPrePrime v \<or> hasPrePrime_fields fs)"
| "hasPrePrime_entries []                                    = False"
| "hasPrePrime_entries (MapEntryFull k v _ # es)             = (hasPrePrime k \<or> hasPrePrime v \<or> hasPrePrime_entries es)"
| "hasPrePrime_bindings []                                   = False"
| "hasPrePrime_bindings (QuantifierBindingFull _ d _ _ # bs) = (hasPrePrime d \<or> hasPrePrime_bindings bs)"

text \<open>Phase 9\<gamma> (\<open>subst\<close>): structural substitution of a free identifier
  by an expression. Stops at binders that shadow the substituted name —
  does NOT perform \<open>\<alpha>\<close>-renaming, matching the semantics of
  \<open>convention.dafny.Generator.rewriteValueRef\<close>. The caller is responsible
  for ensuring the replacement expression's free variables cannot be
  captured (typical use: replace \<open>value\<close> by
  \<open>FieldAccessF (IdentifierF p None) STR ''value'' None\<close> where \<open>p\<close> is not
  bound anywhere relevant).\<close>

fun subst :: "String.literal \<Rightarrow> expr_full \<Rightarrow> expr_full \<Rightarrow> expr_full"
and subst_list :: "String.literal \<Rightarrow> expr_full \<Rightarrow> expr_full list \<Rightarrow> expr_full list"
and subst_fields :: "String.literal \<Rightarrow> expr_full \<Rightarrow> field_assign_full list \<Rightarrow> field_assign_full list"
and subst_entries :: "String.literal \<Rightarrow> expr_full \<Rightarrow> map_entry_full list \<Rightarrow> map_entry_full list"
and subst_bindings :: "String.literal \<Rightarrow> expr_full \<Rightarrow> quantifier_binding_full list \<Rightarrow> quantifier_binding_full list"
where
  "subst x r (IdentifierF n sp)              = (if n = x then r else IdentifierF n sp)"
| "subst x r (BinaryOpF op l rr sp)          = BinaryOpF op (subst x r l) (subst x r rr) sp"
| "subst x r (UnaryOpF op e sp)              = UnaryOpF op (subst x r e) sp"
| "subst x r (FieldAccessF b f sp)           = FieldAccessF (subst x r b) f sp"
| "subst x r (EnumAccessF b m sp)            = EnumAccessF (subst x r b) m sp"
| "subst x r (IndexF b i sp)                 = IndexF (subst x r b) (subst x r i) sp"
| "subst x r (CallF c args sp)               = CallF (subst x r c) (subst_list x r args) sp"
| "subst x r (PrimeF e sp)                   = PrimeF (subst x r e) sp"
| "subst x r (PreF e sp)                     = PreF (subst x r e) sp"
| "subst x r (WithF b upds sp)               = WithF (subst x r b) (subst_fields x r upds) sp"
| "subst x r (IfF c t e sp)                  = IfF (subst x r c) (subst x r t) (subst x r e) sp"
| "subst x r (LetF v val body sp)            =
     LetF v (subst x r val) (if v = x then body else subst x r body) sp"
| "subst x r (LambdaF p b sp)                =
     LambdaF p (if p = x then b else subst x r b) sp"
| "subst x r (ConstructorF n fs sp)          = ConstructorF n (subst_fields x r fs) sp"
| "subst x r (SetLiteralF xs sp)             = SetLiteralF (subst_list x r xs) sp"
| "subst x r (MapLiteralF es sp)             = MapLiteralF (subst_entries x r es) sp"
| "subst x r (SetComprehensionF v d p sp)    =
     SetComprehensionF v (subst x r d) (if v = x then p else subst x r p) sp"
| "subst x r (SeqLiteralF xs sp)             = SeqLiteralF (subst_list x r xs) sp"
| "subst x r (MatchesF e pat sp)             = MatchesF (subst x r e) pat sp"
| "subst x r (SomeWrapF e sp)                = SomeWrapF (subst x r e) sp"
| "subst x r (TheF v d b sp)                 =
     TheF v (subst x r d) (if v = x then b else subst x r b) sp"
| "subst x r (QuantifierF q bs body sp)      =
     QuantifierF q (subst_bindings x r bs)
                   (if string_in_list x (qb_names bs) then body else subst x r body) sp"
| "subst _ _ (IntLitF n sp)                  = IntLitF n sp"
| "subst _ _ (FloatLitF n sp)                = FloatLitF n sp"
| "subst _ _ (StringLitF n sp)               = StringLitF n sp"
| "subst _ _ (BoolLitF v sp)                 = BoolLitF v sp"
| "subst _ _ (NoneLitF sp)                   = NoneLitF sp"
| "subst_list _ _ []                                   = []"
| "subst_list x r (e # es)                             = subst x r e # subst_list x r es"
| "subst_fields _ _ []                                 = []"
| "subst_fields x r (FieldAssignFull f v sp # fs)      =
     FieldAssignFull f (subst x r v) sp # subst_fields x r fs"
| "subst_entries _ _ []                                = []"
| "subst_entries x r (MapEntryFull k v sp # es)        =
     MapEntryFull (subst x r k) (subst x r v) sp # subst_entries x r es"
| "subst_bindings _ _ []                               = []"
| "subst_bindings x r (QuantifierBindingFull n d kk sp # bs) =
     QuantifierBindingFull n (subst x r d) kk sp # subst_bindings x r bs"

text \<open>Phase 9\<delta> (lint TypeMismatch / L01): \<open>lit_class\<close> classifies an
  expression literal into a small ADT; \<open>litClass\<close> recognises which class
  (if any) an \<open>expr_full\<close> belongs to; \<open>binOpName\<close> renders a binary
  operator's user-facing name (used in diagnostic messages).
  \<open>describeLitClass\<close> renders the class as a noun for diagnostics.
  Replaces \<open>lint.TypeMismatch.LitClass\<close>, \<open>litClass\<close>, \<open>describe\<close>, and
  \<open>binOpName\<close> — the bulk of L01's pure analysis surface.\<close>

datatype (plugins only: code size) lit_class =
    LcNumeric | LcBool | LcStringLike | LcCollection | LcNone

fun litClass :: "expr_full \<Rightarrow> lit_class option" where
  "litClass (IntLitF _ _)     = Some LcNumeric"
| "litClass (FloatLitF _ _)   = Some LcNumeric"
| "litClass (BoolLitF _ _)    = Some LcBool"
| "litClass (StringLitF _ _)  = Some LcStringLike"
| "litClass (SetLiteralF _ _) = Some LcCollection"
| "litClass (MapLiteralF _ _) = Some LcCollection"
| "litClass (SeqLiteralF _ _) = Some LcCollection"
| "litClass (NoneLitF _)      = Some LcNone"
| "litClass _                 = None"

fun describeLitClass :: "lit_class \<Rightarrow> String.literal" where
  "describeLitClass LcNumeric    = STR ''numeric''"
| "describeLitClass LcBool       = STR ''boolean''"
| "describeLitClass LcStringLike = STR ''string''"
| "describeLitClass LcCollection = STR ''collection''"
| "describeLitClass LcNone       = STR ''none''"

fun binOpName :: "bin_op_full \<Rightarrow> String.literal" where
  "binOpName BAdd       = STR ''+''"
| "binOpName BSub       = STR ''-''"
| "binOpName BMul       = STR ''*''"
| "binOpName BDiv       = STR ''/''"
| "binOpName BLt        = STR ''<''"
| "binOpName BGt        = STR ''>''"
| "binOpName BLe        = STR ''<=''"
| "binOpName BGe        = STR ''>=''"
| "binOpName BAnd       = STR ''and''"
| "binOpName BOr        = STR ''or''"
| "binOpName BImplies   = STR ''implies''"
| "binOpName BIff       = STR ''iff''"
| "binOpName BIn        = STR ''in''"
| "binOpName BNotIn     = STR ''not in''"
| "binOpName BEq        = STR ''=''"
| "binOpName BNeq       = STR ''!=''"
| "binOpName BSubset    = STR ''subset''"
| "binOpName BUnion     = STR ''++''"
| "binOpName BIntersect = STR ''&''"
| "binOpName BDiff      = STR ''--''"

text \<open>Phase 9\<delta> (\<open>typeContainsNamed\<close>, \<open>exprContainsBoolLit\<close>): two
  structural predicates lifted from \<open>verify.alloy.Translator\<close>. The first
  asks whether a \<open>type_expr_full\<close> mentions a given named type anywhere
  in its structural unfolding (only descending into \<open>SetTypeF\<close> and
  \<open>OptionTypeF\<close>, matching the original walker's narrow scope). The
  second is a structural fold returning \<open>True\<close> iff a \<open>BoolLitF\<close> appears
  anywhere in the expression tree.\<close>

fun typeContainsNamed :: "String.literal \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "typeContainsNamed n (NamedTypeF m _)      = (n = m)"
| "typeContainsNamed n (SetTypeF inner _)    = typeContainsNamed n inner"
| "typeContainsNamed n (OptionTypeF inner _) = typeContainsNamed n inner"
| "typeContainsNamed _ _                     = False"

fun exprContainsBoolLit :: "expr_full \<Rightarrow> bool"
and exprContainsBoolLit_list :: "expr_full list \<Rightarrow> bool"
and exprContainsBoolLit_fields :: "field_assign_full list \<Rightarrow> bool"
and exprContainsBoolLit_entries :: "map_entry_full list \<Rightarrow> bool"
and exprContainsBoolLit_bindings :: "quantifier_binding_full list \<Rightarrow> bool"
where
  "exprContainsBoolLit (BoolLitF _ _)              = True"
| "exprContainsBoolLit (BinaryOpF _ l r _)         = (exprContainsBoolLit l \<or> exprContainsBoolLit r)"
| "exprContainsBoolLit (UnaryOpF _ e _)            = exprContainsBoolLit e"
| "exprContainsBoolLit (FieldAccessF b _ _)        = exprContainsBoolLit b"
| "exprContainsBoolLit (EnumAccessF b _ _)         = exprContainsBoolLit b"
| "exprContainsBoolLit (IndexF b i _)              = (exprContainsBoolLit b \<or> exprContainsBoolLit i)"
| "exprContainsBoolLit (CallF c args _)            = (exprContainsBoolLit c \<or> exprContainsBoolLit_list args)"
| "exprContainsBoolLit (PrimeF e _)                = exprContainsBoolLit e"
| "exprContainsBoolLit (PreF e _)                  = exprContainsBoolLit e"
| "exprContainsBoolLit (WithF b upds _)            = (exprContainsBoolLit b \<or> exprContainsBoolLit_fields upds)"
| "exprContainsBoolLit (IfF c t e _)               = (exprContainsBoolLit c \<or> exprContainsBoolLit t \<or> exprContainsBoolLit e)"
| "exprContainsBoolLit (LetF _ v b _)              = (exprContainsBoolLit v \<or> exprContainsBoolLit b)"
| "exprContainsBoolLit (LambdaF _ b _)             = exprContainsBoolLit b"
| "exprContainsBoolLit (ConstructorF _ fs _)       = exprContainsBoolLit_fields fs"
| "exprContainsBoolLit (SetLiteralF xs _)          = exprContainsBoolLit_list xs"
| "exprContainsBoolLit (MapLiteralF es _)          = exprContainsBoolLit_entries es"
| "exprContainsBoolLit (SetComprehensionF _ d p _) = (exprContainsBoolLit d \<or> exprContainsBoolLit p)"
| "exprContainsBoolLit (SeqLiteralF xs _)          = exprContainsBoolLit_list xs"
| "exprContainsBoolLit (MatchesF x _ _)            = exprContainsBoolLit x"
| "exprContainsBoolLit (SomeWrapF x _)             = exprContainsBoolLit x"
| "exprContainsBoolLit (TheF _ d b _)              = (exprContainsBoolLit d \<or> exprContainsBoolLit b)"
| "exprContainsBoolLit (QuantifierF _ bs body _)   = (exprContainsBoolLit_bindings bs \<or> exprContainsBoolLit body)"
| "exprContainsBoolLit (IntLitF _ _)               = False"
| "exprContainsBoolLit (FloatLitF _ _)             = False"
| "exprContainsBoolLit (StringLitF _ _)            = False"
| "exprContainsBoolLit (NoneLitF _)                = False"
| "exprContainsBoolLit (IdentifierF _ _)           = False"
| "exprContainsBoolLit_list []                                                  = False"
| "exprContainsBoolLit_list (x # xs)                                            = (exprContainsBoolLit x \<or> exprContainsBoolLit_list xs)"
| "exprContainsBoolLit_fields []                                                = False"
| "exprContainsBoolLit_fields (FieldAssignFull _ v _ # fs)                      = (exprContainsBoolLit v \<or> exprContainsBoolLit_fields fs)"
| "exprContainsBoolLit_entries []                                               = False"
| "exprContainsBoolLit_entries (MapEntryFull k v _ # es)                        = (exprContainsBoolLit k \<or> exprContainsBoolLit v \<or> exprContainsBoolLit_entries es)"
| "exprContainsBoolLit_bindings []                                              = False"
| "exprContainsBoolLit_bindings (QuantifierBindingFull _ d _ _ # bs)            = (exprContainsBoolLit d \<or> exprContainsBoolLit_bindings bs)"

text \<open>Phase 9\<delta> (Narration conflict helpers): pure pattern matches lifted
  from \<open>verify.Narration\<close>. \<open>isComp\<close>/\<open>isLowBound\<close>/\<open>isStrictBound\<close> classify
  a \<open>bin_op_full\<close>; \<open>mirrorBinOp\<close> swaps a comparison's direction (for the
  \<open>IntLit cmp Identifier\<close> case); \<open>rangeOf\<close> extracts a
  \<open>(name, op, bound)\<close> triple from a comparison-against-literal shape;
  \<open>conflicts\<close> detects whether two bounds on the same identifier carve out
  disjoint ranges. Used by the contradictory-invariants diagnostic.\<close>

fun isComp :: "bin_op_full \<Rightarrow> bool" where
  "isComp BGe = True"
| "isComp BGt = True"
| "isComp BLe = True"
| "isComp BLt = True"
| "isComp _   = False"

fun isLowBound :: "bin_op_full \<Rightarrow> bool" where
  "isLowBound BGe = True"
| "isLowBound BGt = True"
| "isLowBound _   = False"

fun isStrictBound :: "bin_op_full \<Rightarrow> bool" where
  "isStrictBound BGt = True"
| "isStrictBound BLt = True"
| "isStrictBound _   = False"

fun mirrorBinOp :: "bin_op_full \<Rightarrow> bin_op_full" where
  "mirrorBinOp BGe    = BLe"
| "mirrorBinOp BLe    = BGe"
| "mirrorBinOp BGt    = BLt"
| "mirrorBinOp BLt    = BGt"
| "mirrorBinOp other  = other"

fun rangeOf :: "expr_full \<Rightarrow> (String.literal \<times> bin_op_full \<times> int) option" where
  "rangeOf (BinaryOpF op (IdentifierF n _) (IntLitF v _) _) =
     (if isComp op then Some (n, op, v) else None)"
| "rangeOf (BinaryOpF op (IntLitF v _) (IdentifierF n _) _) =
     (if isComp op then Some (n, mirrorBinOp op, v) else None)"
| "rangeOf _ = None"

fun conflicts :: "bin_op_full \<Rightarrow> int \<Rightarrow> bin_op_full \<Rightarrow> int \<Rightarrow> bool" where
  "conflicts aOp aB bOp bB =
     (let aLow    = isLowBound aOp;
          bLow    = isLowBound bOp;
          strict  = isStrictBound aOp \<or> isStrictBound bOp
      in (if aLow \<and> \<not> bLow then (if strict then aB \<ge> bB else aB > bB)
          else if \<not> aLow \<and> bLow then (if strict then bB \<ge> aB else bB > aB)
          else False))"

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
and lowerSetList ::
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
| "lower enums (SetLiteralF elems sp) = lowerSetList enums elems sp"

| "lowerSetList _ [] sp = Some (SetEmpty sp)"
| "lowerSetList enums (e # rest) sp =
     (case (lower enums e, lowerSetList enums rest sp) of
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
