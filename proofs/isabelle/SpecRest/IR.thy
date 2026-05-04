theory IR
  imports Main
begin

datatype span_t = SpanT int int int int

type_synonym option_span = "span_t option"

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
  | IndexRel "String.literal" "expr" "option_span"
  | FieldAccess "expr" "String.literal" "option_span"
  | SetEmpty "option_span"
  | SetInsert "expr" "expr" "option_span"
  | SetMember "expr" "expr" "option_span"
  | SetBin "set_op" "expr" "expr" "option_span"
  | WithRec "expr" "String.literal" "expr" "option_span"

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

text \<open>Issue #202: full input-language ADT (mirrors the Scala \<open>Expr\<close> enum). Coexists
  with the verified-subset \<open>expr\<close> above. See research/10_translator_soundness.md \<section>17.\<close>

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

text \<open>Issue #202 Phase 3: \<open>lower\<close> projects \<open>expr_full\<close> onto the verified-subset
  \<open>expr\<close>. Out-of-subset constructors return \<open>None\<close>. Span field preserved.
  v1 coverage punts on \<open>QuantifierF\<close>, \<open>BSubset\<close>, multi-field \<open>WithF\<close>, and the
  desugar of \<open>Subset\<close> over relation-identifier pairs (Phase 8 Scala-side
  EmitIsabelle path stays load-bearing for those until coverage parity ships
  as a follow-up).\<close>

fun lower :: "expr_full \<Rightarrow> expr option"
and lower_set_list :: "expr_full list \<Rightarrow> option_span \<Rightarrow> expr option"
where
  "lower (BoolLitF b sp)   = Some (BoolLit b sp)"
| "lower (IntLitF n sp)    = Some (IntLit n sp)"
| "lower (IdentifierF x sp) = Some (Ident x sp)"
| "lower (FloatLitF _ _)   = None"
| "lower (StringLitF _ _)  = None"
| "lower (NoneLitF _)      = None"
| "lower (LambdaF _ _ _)   = None"
| "lower (CallF _ _ _)     = None"
| "lower (ConstructorF _ _ _)     = None"
| "lower (MapLiteralF _ _)        = None"
| "lower (SeqLiteralF _ _)        = None"
| "lower (SetComprehensionF _ _ _ _) = None"
| "lower (SomeWrapF _ _)   = None"
| "lower (TheF _ _ _ _)    = None"
| "lower (MatchesF _ _ _)  = None"
| "lower (IfF _ _ _ _)     = None"
| "lower (QuantifierF _ _ _ _) = None"

| "lower (UnaryOpF op e sp) =
     (case op of
        UNot \<Rightarrow> map_option (\<lambda>e'. UnNot e' sp) (lower e)
      | UNegate \<Rightarrow> map_option (\<lambda>e'. UnNeg e' sp) (lower e)
      | UCardinality \<Rightarrow>
          (case e of
             IdentifierF x _ \<Rightarrow> Some (CardRel x sp)
           | _ \<Rightarrow> None)
      | UPower \<Rightarrow> None)"

| "lower (BinaryOpF op l r sp) =
     (case op of
        BAnd \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin AndOp l' r' sp)
           | _ \<Rightarrow> None)
      | BOr \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin OrOp l' r' sp)
           | _ \<Rightarrow> None)
      | BImplies \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin ImpliesOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIff \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (BoolBin IffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BEq \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp EqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BNeq \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp NeqOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLt \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGt \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GtOp l' r' sp)
           | _ \<Rightarrow> None)
      | BLe \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp LeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BGe \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Cmp GeOp l' r' sp)
           | _ \<Rightarrow> None)
      | BAdd \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Arith AddOp l' r' sp)
           | _ \<Rightarrow> None)
      | BSub \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Arith SubOp l' r' sp)
           | _ \<Rightarrow> None)
      | BMul \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Arith MulOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiv \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (Arith DivOp l' r' sp)
           | _ \<Rightarrow> None)
      | BUnion \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin UnionOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIntersect \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin IntersectOp l' r' sp)
           | _ \<Rightarrow> None)
      | BDiff \<Rightarrow>
          (case (lower l, lower r) of
             (Some l', Some r') \<Rightarrow> Some (SetBin DiffOp l' r' sp)
           | _ \<Rightarrow> None)
      | BIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. Member l' rel sp) (lower l)
           | _ \<Rightarrow>
               (case (lower l, lower r) of
                  (Some l', Some r') \<Rightarrow> Some (SetMember l' r' sp)
                | _ \<Rightarrow> None))
      | BNotIn \<Rightarrow>
          (case r of
             IdentifierF rel _ \<Rightarrow>
               map_option (\<lambda>l'. UnNot (Member l' rel sp) sp) (lower l)
           | _ \<Rightarrow>
               (case (lower l, lower r) of
                  (Some l', Some r') \<Rightarrow> Some (UnNot (SetMember l' r' sp) sp)
                | _ \<Rightarrow> None))
      | BSubset \<Rightarrow> None)"

| "lower (LetF x v body sp) =
     (case (lower v, lower body) of
        (Some v', Some b') \<Rightarrow> Some (LetIn x v' b' sp)
      | _ \<Rightarrow> None)"
| "lower (EnumAccessF base mem sp) =
     (case base of
        IdentifierF en _ \<Rightarrow> Some (EnumAccess en mem sp)
      | _ \<Rightarrow> None)"
| "lower (FieldAccessF base fname sp) =
     map_option (\<lambda>b'. FieldAccess b' fname sp) (lower base)"
| "lower (IndexF base key sp) =
     (case base of
        IdentifierF rel _ \<Rightarrow>
          map_option (\<lambda>k'. IndexRel rel k' sp) (lower key)
      | _ \<Rightarrow> None)"
| "lower (PrimeF e sp) = map_option (\<lambda>e'. Prime e' sp) (lower e)"
| "lower (PreF e sp)   = map_option (\<lambda>e'. Pre e' sp) (lower e)"
| "lower (WithF base updates sp) =
     (case updates of
        [FieldAssignFull fld val _] \<Rightarrow>
          (case (lower base, lower val) of
             (Some b', Some v') \<Rightarrow> Some (WithRec b' fld v' sp)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "lower (SetLiteralF elems sp) = lower_set_list elems sp"

| "lower_set_list [] sp = Some (SetEmpty sp)"
| "lower_set_list (e # rest) sp =
     (case (lower e, lower_set_list rest sp) of
        (Some e', Some s') \<Rightarrow> Some (SetInsert e' s' sp)
      | _ \<Rightarrow> None)"

end
