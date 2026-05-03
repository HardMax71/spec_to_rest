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

end
