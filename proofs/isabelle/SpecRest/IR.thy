theory IR
  imports Main
begin

datatype type_expr =
    BoolT
  | IntT
  | EnumT "String.literal"
  | EntityT "String.literal"
  | RelationT "type_expr" "type_expr"

datatype bool_bin_op =
    AndOp
  | OrOp
  | ImpliesOp
  | IffOp

datatype arith_op =
    AddOp
  | SubOp
  | MulOp
  | DivOp

datatype set_op =
    UnionOp
  | IntersectOp
  | DiffOp

datatype cmp_op =
    EqOp
  | NeqOp
  | LtOp
  | LeOp
  | GtOp
  | GeOp

datatype expr =
    BoolLit bool
  | IntLit int
  | Ident "String.literal"
  | UnNot "expr"
  | UnNeg "expr"
  | BoolBin "bool_bin_op" "expr" "expr"
  | Arith "arith_op" "expr" "expr"
  | Cmp "cmp_op" "expr" "expr"
  | LetIn "String.literal" "expr" "expr"
  | EnumAccess "String.literal" "String.literal"
  | Member "expr" "String.literal"
  | ForallEnum "String.literal" "String.literal" "expr"
  | ForallRel "String.literal" "String.literal" "expr"
  | Prime "expr"
  | Pre "expr"
  | CardRel "String.literal"
  | IndexRel "String.literal" "expr"
  | FieldAccess "expr" "String.literal"
  | SetEmpty
  | SetInsert "expr" "expr"
  | SetMember "expr" "expr"
  | SetBin "set_op" "expr" "expr"
  | WithRec "expr" "String.literal" "expr"

record field_decl =
  fd_name :: "String.literal"
  fd_ty   :: "type_expr"

record entity_decl =
  ed_name   :: "String.literal"
  ed_fields :: "field_decl list"

record enum_decl =
  enm_name    :: "String.literal"
  enm_members :: "String.literal list"

record state_scalar =
  ss_name :: "String.literal"
  ss_ty   :: "type_expr"

record state_relation =
  sr_name  :: "String.literal"
  sr_key   :: "type_expr"
  sr_value :: "type_expr"

record state_decl =
  st_scalars   :: "state_scalar list"
  st_relations :: "state_relation list"

record invariant_decl =
  inv_name :: "String.literal"
  inv_body :: "expr"

record operation_decl =
  op_name     :: "String.literal"
  op_requires :: "expr list"
  op_ensures  :: "expr list"

record service_ir =
  svc_name       :: "String.literal"
  svc_enums      :: "enum_decl list"
  svc_entities   :: "entity_decl list"
  svc_state      :: "state_decl"
  svc_invariants :: "invariant_decl list"
  svc_operations :: "operation_decl list"

text \<open>Issue #202: full input-language ADT (mirrors the Scala \<open>Expr\<close> enum). Coexists
  with the verified-subset \<open>expr\<close> above. See research/10_translator_soundness.md \<section>17.\<close>

datatype span_t = SpanT int int int int

type_synonym option_span = "span_t option"

datatype multiplicity = MultOne | MultLone | MultSome | MultSet

datatype bin_op_full =
    BAnd | BOr | BImplies | BIff
  | BEq | BNeq
  | BLt | BGt | BLe | BGe
  | BIn | BNotIn
  | BSubset | BUnion | BIntersect | BDiff
  | BAdd | BSub | BMul | BDiv

datatype un_op_full = UNot | UNegate | UCardinality | UPower

datatype quant_kind_full = QAll | QSome | QNo | QExists

datatype binding_kind_full = BkIn | BkColon

datatype type_expr_full =
    NamedTypeF "String.literal" "option_span"
  | SetTypeF "type_expr_full" "option_span"
  | MapTypeF "type_expr_full" "type_expr_full" "option_span"
  | SeqTypeF "type_expr_full" "option_span"
  | OptionTypeF "type_expr_full" "option_span"
  | RelationTypeF "type_expr_full" "multiplicity" "type_expr_full" "option_span"

datatype expr_full =
    BinaryOpF "bin_op_full" "expr_full" "expr_full" "option_span"
  | UnaryOpF "un_op_full" "expr_full" "option_span"
  | QuantifierF "quant_kind_full" "quantifier_binding_full list" "expr_full" "option_span"
  | SomeWrapF "expr_full" "option_span"
  | TheF "String.literal" "expr_full" "expr_full" "option_span"
  | FieldAccessF "expr_full" "String.literal" "option_span"
  | EnumAccessF "expr_full" "String.literal" "option_span"
  | IndexF "expr_full" "expr_full" "option_span"
  | CallF "expr_full" "expr_full list" "option_span"
  | PrimeF "expr_full" "option_span"
  | PreF "expr_full" "option_span"
  | WithF "expr_full" "field_assign_full list" "option_span"
  | IfF "expr_full" "expr_full" "expr_full" "option_span"
  | LetF "String.literal" "expr_full" "expr_full" "option_span"
  | LambdaF "String.literal" "expr_full" "option_span"
  | ConstructorF "String.literal" "field_assign_full list" "option_span"
  | SetLiteralF "expr_full list" "option_span"
  | MapLiteralF "map_entry_full list" "option_span"
  | SetComprehensionF "String.literal" "expr_full" "expr_full" "option_span"
  | SeqLiteralF "expr_full list" "option_span"
  | MatchesF "expr_full" "String.literal" "option_span"
  | IntLitF int "option_span"
  | FloatLitF "String.literal" "option_span"
  | StringLitF "String.literal" "option_span"
  | BoolLitF bool "option_span"
  | NoneLitF "option_span"
  | IdentifierF "String.literal" "option_span"
and field_assign_full =
    FieldAssignFull "String.literal" "expr_full" "option_span"
and map_entry_full =
    MapEntryFull "expr_full" "expr_full" "option_span"
and quantifier_binding_full =
    QuantifierBindingFull "String.literal" "expr_full" "binding_kind_full" "option_span"

record field_decl_full =
  fdf_name        :: "String.literal"
  fdf_type        :: "type_expr_full"
  fdf_constraint  :: "expr_full option"
  fdf_span        :: "option_span"

record entity_decl_full =
  edf_name        :: "String.literal"
  edf_extends     :: "String.literal option"
  edf_fields      :: "field_decl_full list"
  edf_invariants  :: "expr_full list"
  edf_span        :: "option_span"

record enum_decl_full =
  enmf_name    :: "String.literal"
  enmf_values  :: "String.literal list"
  enmf_span    :: "option_span"

record type_alias_decl_full =
  tad_name        :: "String.literal"
  tad_type        :: "type_expr_full"
  tad_constraint  :: "expr_full option"
  tad_span        :: "option_span"

record state_field_decl_full =
  sfdf_name  :: "String.literal"
  sfdf_type  :: "type_expr_full"
  sfdf_span  :: "option_span"

record state_decl_full =
  sdf_fields  :: "state_field_decl_full list"
  sdf_span    :: "option_span"

record param_decl_full =
  pdf_name  :: "String.literal"
  pdf_type  :: "type_expr_full"
  pdf_span  :: "option_span"

record operation_decl_full =
  odf_name      :: "String.literal"
  odf_inputs    :: "param_decl_full list"
  odf_outputs   :: "param_decl_full list"
  odf_requires  :: "expr_full list"
  odf_ensures   :: "expr_full list"
  odf_span      :: "option_span"

record transition_rule_full =
  trf_from   :: "String.literal"
  trf_to     :: "String.literal"
  trf_via    :: "String.literal"
  trf_guard  :: "expr_full option"
  trf_span   :: "option_span"

record transition_decl_full =
  tdf_name        :: "String.literal"
  tdf_entity_name :: "String.literal"
  tdf_field_name  :: "String.literal"
  tdf_rules       :: "transition_rule_full list"
  tdf_span        :: "option_span"

record invariant_decl_full =
  idf_name  :: "String.literal option"
  idf_expr  :: "expr_full"
  idf_span  :: "option_span"

record temporal_decl_full =
  tdcf_name  :: "String.literal"
  tdcf_expr  :: "expr_full"
  tdcf_span  :: "option_span"

record fact_decl_full =
  fdcf_name  :: "String.literal option"
  fdcf_expr  :: "expr_full"
  fdcf_span  :: "option_span"

record function_decl_full =
  fnd_name        :: "String.literal"
  fnd_params      :: "param_decl_full list"
  fnd_return_type :: "type_expr_full"
  fnd_body        :: "expr_full"
  fnd_span        :: "option_span"

record predicate_decl_full =
  pdcf_name    :: "String.literal"
  pdcf_params  :: "param_decl_full list"
  pdcf_body    :: "expr_full"
  pdcf_span    :: "option_span"

record convention_rule_full =
  crf_target     :: "String.literal"
  crf_property   :: "String.literal"
  crf_qualifier  :: "String.literal option"
  crf_value      :: "expr_full"
  crf_span       :: "option_span"

record conventions_decl_full =
  csdf_rules  :: "convention_rule_full list"
  csdf_span   :: "option_span"

record service_ir_full =
  svf_name          :: "String.literal"
  svf_imports       :: "String.literal list"
  svf_entities      :: "entity_decl_full list"
  svf_enums         :: "enum_decl_full list"
  svf_type_aliases  :: "type_alias_decl_full list"
  svf_state         :: "state_decl_full option"
  svf_operations    :: "operation_decl_full list"
  svf_transitions   :: "transition_decl_full list"
  svf_invariants    :: "invariant_decl_full list"
  svf_temporals     :: "temporal_decl_full list"
  svf_facts         :: "fact_decl_full list"
  svf_functions     :: "function_decl_full list"
  svf_predicates    :: "predicate_decl_full list"
  svf_conventions   :: "conventions_decl_full option"
  svf_span          :: "option_span"

fun is_lit_full :: "expr_full \<Rightarrow> bool" where
  "is_lit_full (BoolLitF _ _)   = True"
| "is_lit_full (IntLitF _ _)    = True"
| "is_lit_full (FloatLitF _ _)  = True"
| "is_lit_full (StringLitF _ _) = True"
| "is_lit_full (NoneLitF _)     = True"
| "is_lit_full _                = False"

definition empty_service_ir_full :: "String.literal \<Rightarrow> service_ir_full" where
  "empty_service_ir_full nm = \<lparr>
     svf_name         = nm,
     svf_imports      = [],
     svf_entities     = [],
     svf_enums        = [],
     svf_type_aliases = [],
     svf_state        = None,
     svf_operations   = [],
     svf_transitions  = [],
     svf_invariants   = [],
     svf_temporals    = [],
     svf_facts        = [],
     svf_functions    = [],
     svf_predicates   = [],
     svf_conventions  = None,
     svf_span         = None
  \<rparr>"

end
