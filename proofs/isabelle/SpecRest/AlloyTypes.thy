theory AlloyTypes
  imports IR_Helpers IR_Analysis ParseTemporal
begin

text \<open>Type-to-sig mapping kernel for the Alloy translator. Lifts the pure
  per-type classification helpers \<open>alloyFieldTypeOf\<close>, \<open>typeToSigName\<close>,
  and \<open>fieldElementSigName\<close> from
  \<open>specrest.verify.alloy.Translator\<close>, which classify a spec
  \<open>type_expr_full\<close> into an Alloy field shape (multiplicity tag +
  element sig name).

  Field-multiplicity tags mirror Alloy's keyword set (\<open>one\<close>, \<open>lone\<close>,
  \<open>some\<close>, \<open>set\<close>). The lifted classifier returns \<open>None\<close> for shapes
  the Alloy v1 translator doesn't support (nested generics other than
  \<open>Set[Named]\<close>/\<open>Option[Named]\<close>, relation types, map types); the Scala
  caller turns \<open>None\<close> into a \<open>boundary/break\<close> \<open>failAlloy\<close>
  diagnostic with the unsupported shape's class name.\<close>

datatype alloy_field_multiplicity =
    AfmOne
  | AfmLone
  | AfmSome
  | AfmSet

text \<open>Primitive-name passthrough. The Scala \<open>mapPrimitive\<close> maps
  \<open>"Int"\<close>/\<open>"Bool"\<close>/\<open>"String"\<close> to themselves and falls through with
  any other name unchanged — so the function is identity in practice.
  Lifted here for completeness and to keep the call-site machinery in
  the proof artefact even if extraction inlines it.\<close>

definition mapAlloyPrimitive :: "String.literal \<Rightarrow> String.literal" where
  "mapAlloyPrimitive name =
     (if name = STR ''Int'' then STR ''Int''
      else if name = STR ''Bool'' then STR ''Bool''
      else if name = STR ''String'' then STR ''String''
      else name)"

text \<open>\<open>typeToSigName\<close>: extract the Alloy sig name for a type that's
  used in an element position (set member, option content). Only
  \<open>NamedTypeF\<close> is supported in v1; anything else returns \<open>None\<close>.\<close>

fun typeToSigNameAlloy :: "type_expr_full \<Rightarrow> String.literal option" where
  "typeToSigNameAlloy (NamedTypeF name _) = Some (mapAlloyPrimitive name)"
| "typeToSigNameAlloy _                   = None"

text \<open>\<open>alloyFieldTypeOf\<close>: classify a field-type's Alloy shape. Returns
  \<open>None\<close> for unsupported nested types (e.g. \<open>Set[Set[T]]\<close>,
  \<open>Relation\<close>, \<open>Map\<close>). The Scala caller fails the translation with a
  matching diagnostic on \<open>None\<close>.\<close>

fun alloyFieldTypeOf ::
  "type_expr_full \<Rightarrow> (alloy_field_multiplicity \<times> String.literal) option"
where
  "alloyFieldTypeOf (NamedTypeF name _) = Some (AfmOne, mapAlloyPrimitive name)"
| "alloyFieldTypeOf (SetTypeF inner _) =
     (case typeToSigNameAlloy inner of
        None \<Rightarrow> None
      | Some n \<Rightarrow> Some (AfmSet, n))"
| "alloyFieldTypeOf (OptionTypeF inner _) =
     (case typeToSigNameAlloy inner of
        None \<Rightarrow> None
      | Some n \<Rightarrow> Some (AfmLone, n))"
| "alloyFieldTypeOf _ = None"

text \<open>\<open>fieldElementSigName\<close>: extract the Alloy sig name from a
  quantifier-binding domain's field type. The Scala original accepts
  three shapes (\<open>NamedTypeF\<close>, \<open>SetTypeF (NamedTypeF _ _) _\<close>,
  \<open>OptionTypeF (NamedTypeF _ _) _\<close>); other shapes are unsupported.\<close>

fun fieldElementSigNameAlloy :: "type_expr_full \<Rightarrow> String.literal option" where
  "fieldElementSigNameAlloy (NamedTypeF name _) = Some (mapAlloyPrimitive name)"
| "fieldElementSigNameAlloy (SetTypeF (NamedTypeF name _) _) = Some (mapAlloyPrimitive name)"
| "fieldElementSigNameAlloy (OptionTypeF (NamedTypeF name _) _) = Some (mapAlloyPrimitive name)"
| "fieldElementSigNameAlloy _ = None"

text \<open>\<open>needsBoolSig\<close>: predicate deciding whether the Alloy module needs
  an explicit \<open>Bool\<close> sig hierarchy. True iff any entity field, state
  field, input field, invariant body, temporal arg, or operation
  pre/postcondition references the \<open>Bool\<close> type or a \<open>BoolLitF\<close>.

  Takes the IR plus the materialised state-/input-field type lists (as
  alists of \<open>name \<times> type_expr_full\<close>) because the Scala caller has
  already extracted those into \<open>Ctx\<close>. State fields are extracted from
  \<open>state_decl_full\<close>; input fields are operation-specific (different
  Ctx per operation in the preservation/enabled flows).\<close>

fun fieldTypeHasBool :: "field_decl_full \<Rightarrow> bool" where
  "fieldTypeHasBool (FieldDeclFull _ t _ _) = typeContainsNamed (STR ''Bool'') t"

fun entityHasBoolField :: "entity_decl_full \<Rightarrow> bool" where
  "entityHasBoolField (EntityDeclFull _ _ fs _ _) =
     (\<exists>f \<in> set fs. fieldTypeHasBool f)"

fun operationHasBoolLit :: "operation_decl_full \<Rightarrow> bool" where
  "operationHasBoolLit (OperationDeclFull _ _ _ requires ensures _) =
     ((\<exists>e \<in> set requires. exprContainsBoolLit e) \<or>
      (\<exists>e \<in> set ensures. exprContainsBoolLit e))"

fun invariantHasBoolLit :: "invariant_decl_full \<Rightarrow> bool" where
  "invariantHasBoolLit (InvariantDeclFull _ body _) = exprContainsBoolLit body"

fun temporalHasBoolLit :: "temporal_decl_full \<Rightarrow> bool" where
  "temporalHasBoolLit (TemporalDeclFull _ tb _) = exprContainsBoolLit (temporalArg tb)"

fun needsBoolSig ::
  "service_ir_full
   \<Rightarrow> (String.literal \<times> type_expr_full) list
   \<Rightarrow> (String.literal \<times> type_expr_full) list
   \<Rightarrow> bool"
where
  "needsBoolSig
      (ServiceIRFull _ _ es _ _ _ ops _ invs temps _ _ _ _ _)
      stateFields inputFields = (
     (\<exists>e \<in> set es. entityHasBoolField e) \<or>
     (\<exists>kv \<in> set stateFields. typeContainsNamed (STR ''Bool'') (snd kv)) \<or>
     (\<exists>kv \<in> set inputFields. typeContainsNamed (STR ''Bool'') (snd kv)) \<or>
     (\<exists>i \<in> set invs. invariantHasBoolLit i) \<or>
     (\<exists>t \<in> set temps. temporalHasBoolLit t) \<or>
     (\<exists>op \<in> set ops. operationHasBoolLit op))"

lemmas mapAlloyPrimitive_code [code]        = mapAlloyPrimitive_def
lemmas typeToSigNameAlloy_code [code]       = typeToSigNameAlloy.simps
lemmas alloyFieldTypeOf_code [code]         = alloyFieldTypeOf.simps
lemmas fieldElementSigNameAlloy_code [code] = fieldElementSigNameAlloy.simps
lemmas fieldTypeHasBool_code [code]         = fieldTypeHasBool.simps
lemmas entityHasBoolField_code [code]       = entityHasBoolField.simps
lemmas operationHasBoolLit_code [code]      = operationHasBoolLit.simps
lemmas invariantHasBoolLit_code [code]      = invariantHasBoolLit.simps
lemmas temporalHasBoolLit_code [code]       = temporalHasBoolLit.simps
lemmas needsBoolSig_code [code]             = needsBoolSig.simps

end
