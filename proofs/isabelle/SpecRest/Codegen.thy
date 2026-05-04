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

text \<open>NOTE: `eval`, `smt_eval`, and `value_to_smt` reference the records
`schema`, `state`, `smt_model` (and their extension schemes `'a schema_scheme`,
etc.). Isabelle's code generator rejects them with `Illegal fixed variable
'a` because records expose their extensible-scheme parameter at code-eq
time. Two paths to enable their extraction: (a) replace records with plain
datatypes (refactor IR.thy / Semantics.thy / Smt.thy); (b) introduce
`mk_schema` / `mk_state` definitional wrappers and re-orient the generator
through them. Either is a separable pass; not blocking Phase 5's deliverable
because the round-trip oracle (Phase 7) only needs `translate` extracted —
the Scala-side `EvalIR.eval` continues to drive eval-side oracle inputs.\<close>

export_code
    translate
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
