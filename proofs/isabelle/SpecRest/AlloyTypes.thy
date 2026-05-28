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

text \<open>\<open>renderBinaryOp\<close>'s structural decision: each spec binary operator
  emits one of three Alloy expression shapes. The token and shape come
  from the lift; the Scala caller assembles the final string after
  recursively rendering the operands. Three shapes are distinguished:

  \<^item> \<open>AbsLogical tok\<close>: \<open>(($l) tok ($r))\<close> — parenthesise each
    operand. Used by the propositional connectives (\<open>and\<close>, \<open>or\<close>,
    \<open>implies\<close>, \<open>iff\<close>) so precedence is unambiguous.
  \<^item> \<open>AbsInfix tok\<close>: \<open>($l tok $r)\<close> — single outer parens. Used by
    \<open>=\<close>/\<open>!=\<close>/\<open><\<close>/\<open><=\<close>/\<open>>\<close>/\<open>>=\<close>/\<open>in\<close>/\<open>!in\<close>/\<open>+\<close>/\<open>&\<close>/\<open>-\<close>.
    (\<open>BSubset\<close> shares the \<open>in\<close> token with \<open>BIn\<close> — Alloy uses
    \<open>in\<close> for both membership and subset.)
  \<^item> \<open>AbsPrefixCall tok\<close>: \<open>tok[$l, $r]\<close> — Alloy's prefix-call form
    used for the arithmetic builtins \<open>plus\<close>/\<open>minus\<close>/\<open>mul\<close>/\<open>div\<close>.\<close>

datatype alloy_binop_shape =
    AbsLogical "String.literal"
  | AbsInfix "String.literal"
  | AbsPrefixCall "String.literal"

fun alloyBinopShape :: "bin_op_full \<Rightarrow> alloy_binop_shape" where
  "alloyBinopShape BAnd       = AbsLogical (STR ''and'')"
| "alloyBinopShape BOr        = AbsLogical (STR ''or'')"
| "alloyBinopShape BImplies   = AbsLogical (STR ''implies'')"
| "alloyBinopShape BIff       = AbsLogical (STR ''iff'')"
| "alloyBinopShape BEq        = AbsInfix (STR ''='')"
| "alloyBinopShape BNeq       = AbsInfix (STR ''!='')"
| "alloyBinopShape BLt        = AbsInfix (STR ''<'')"
| "alloyBinopShape BLe        = AbsInfix (STR ''<='')"
| "alloyBinopShape BGt        = AbsInfix (STR ''>'')"
| "alloyBinopShape BGe        = AbsInfix (STR ''>='')"
| "alloyBinopShape BIn        = AbsInfix (STR ''in'')"
| "alloyBinopShape BNotIn     = AbsInfix (STR ''!in'')"
| "alloyBinopShape BSubset    = AbsInfix (STR ''in'')"
| "alloyBinopShape BUnion     = AbsInfix (STR ''+'')"
| "alloyBinopShape BIntersect = AbsInfix (STR ''&'')"
| "alloyBinopShape BDiff      = AbsInfix (STR ''-'')"
| "alloyBinopShape BAdd       = AbsPrefixCall (STR ''plus'')"
| "alloyBinopShape BSub       = AbsPrefixCall (STR ''minus'')"
| "alloyBinopShape BMul       = AbsPrefixCall (STR ''mul'')"
| "alloyBinopShape BDiv       = AbsPrefixCall (STR ''div'')"

text \<open>\<open>renderExpr\<close>'s unary-operator dispatch. \<open>UPower\<close> as a standalone
  prefix is unsupported (it requires higher-order Alloy reasoning); the
  binder-domain form is handled separately in \<open>buildBinding\<close>.\<close>

datatype alloy_unop_shape =
    AusNot           \<comment> \<open>\<open>not (X)\<close>\<close>
  | AusCardinality   \<comment> \<open>\<open>#(X)\<close>\<close>
  | AusMinusZero     \<comment> \<open>\<open>minus[0, X]\<close>\<close>
  | AusUnsupported   \<comment> \<open>standalone power — Scala caller fails the translation\<close>

fun alloyUnopShape :: "un_op_full \<Rightarrow> alloy_unop_shape" where
  "alloyUnopShape UNot         = AusNot"
| "alloyUnopShape UCardinality = AusCardinality"
| "alloyUnopShape UNegate      = AusMinusZero"
| "alloyUnopShape UPower       = AusUnsupported"

text \<open>\<open>IdentifierF\<close> resolution classifier. Mirrors the four-way
  precedence in \<open>renderExpr\<close>'s \<open>IdentifierF\<close> case: a name resolves
  to a bound variable first, then a state field, then an input field,
  then falls through unprefixed. The Scala caller emits the prefix
  (\<open>currentStateSig.\<close> or \<open>Inputs.\<close>) for the field cases.\<close>

datatype alloy_identifier_kind =
    AikBoundVar
  | AikStateField
  | AikInputField
  | AikPlain

definition classifyAlloyIdentifier ::
  "String.literal
   \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> type_expr_full) list
   \<Rightarrow> (String.literal \<times> type_expr_full) list
   \<Rightarrow> alloy_identifier_kind"
where
  "classifyAlloyIdentifier name boundVars stateFields inputFields = (
     if name \<in> set boundVars then AikBoundVar
     else if name \<in> set (map fst stateFields) then AikStateField
     else if name \<in> set (map fst inputFields) then AikInputField
     else AikPlain)"

text \<open>Quantifier-keyword classifier. \<open>QExists\<close> shares the \<open>some\<close>
  keyword with \<open>QSome\<close> (the spec language distinguishes them; Alloy
  collapses both onto the same surface keyword).\<close>

datatype alloy_quantifier_class =
    AqAll
  | AqSome
  | AqExists
  | AqNo

fun alloyQuantifierClass :: "quant_kind_full \<Rightarrow> alloy_quantifier_class" where
  "alloyQuantifierClass QAll    = AqAll"
| "alloyQuantifierClass QSome   = AqSome"
| "alloyQuantifierClass QExists = AqExists"
| "alloyQuantifierClass QNo     = AqNo"

fun alloyQuantifierKeyword :: "alloy_quantifier_class \<Rightarrow> String.literal" where
  "alloyQuantifierKeyword AqAll    = STR ''all''"
| "alloyQuantifierKeyword AqSome   = STR ''some''"
| "alloyQuantifierKeyword AqExists = STR ''some''"
| "alloyQuantifierKeyword AqNo     = STR ''no''"

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
lemmas alloyBinopShape_code [code]          = alloyBinopShape.simps
lemmas alloyUnopShape_code [code]           = alloyUnopShape.simps
lemmas classifyAlloyIdentifier_code [code]  = classifyAlloyIdentifier_def
lemmas alloyQuantifierClass_code [code]     = alloyQuantifierClass.simps
lemmas alloyQuantifierKeyword_code [code]   = alloyQuantifierKeyword.simps

end
