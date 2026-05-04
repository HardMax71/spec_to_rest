theory Codegen
  imports
    Translate
    "HOL-Library.Code_Target_Int"
    "HOL-Library.Code_Target_Numeral"
begin

text \<open>Productionized Scala extraction for the verified subset:
\<bullet> `int` -> Scala `BigInt` via `HOL-Library.Code_Target_Int`.
\<bullet> `nat` -> Scala `BigInt` via `HOL-Library.Code_Target_Numeral`.
\<bullet> `String.literal` -> Scala `String` (default `Code_Target_String`).
\<bullet> Records extract as Scala case classes; datatypes as sealed-class hierarchies.
The output module name `SpecRestGenerated` matches the consumer-side package
`specrest.verify.cert.generated`. Round-trip tests against the live
canonical-probes corpus live on the Scala side (Phase 7).\<close>

text \<open>Make the polymorphic record-scheme accessors visible to the code
generator at the concrete (`unit`) instantiation. Without these `[code]`
declarations on the records' auto-generated `defs`, the generator hits
`Illegal fixed variable 'a'` on record-using functions like `eval` and
`smt_eval`.

`value_to_smt` (no record references but invoked transitively) currently
remains blocked — its `fun`-derived code equation gets a phantom type
variable somewhere in the dependency chain. Skipped from this export;
Phase 6's Scala-side glue can re-derive it from the extracted shape via
a thin hand-written wrapper if needed.\<close>

declare schema.defs[code]
declare state.defs[code]
declare state_pair.defs[code]
declare smt_model.defs[code]
declare smt_model_pair.defs[code]

declare field_decl_full.defs[code]
declare entity_decl_full.defs[code]
declare enum_decl_full.defs[code]
declare type_alias_decl_full.defs[code]
declare state_field_decl_full.defs[code]
declare state_decl_full.defs[code]
declare param_decl_full.defs[code]
declare operation_decl_full.defs[code]
declare transition_rule_full.defs[code]
declare transition_decl_full.defs[code]
declare invariant_decl_full.defs[code]
declare temporal_decl_full.defs[code]
declare fact_decl_full.defs[code]
declare function_decl_full.defs[code]
declare predicate_decl_full.defs[code]
declare convention_rule_full.defs[code]
declare conventions_decl_full.defs[code]
declare service_ir_full.defs[code]

export_code
    translate
    eval
    smt_eval
    is_lit_full
    empty_service_ir_full
    lower
    lower_set_list
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
