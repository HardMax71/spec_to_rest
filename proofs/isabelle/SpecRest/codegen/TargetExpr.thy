theory TargetExpr
  imports SpecRest_Core.IR
begin

text \<open>Language-neutral decision kernels shared by the per-target expression backends
  (\<open>testgen.ExprToPython\<close>, \<open>TsExprBackend\<close>, \<open>GoExprBackend\<close>). The three backends walk
  \<open>expr_full\<close> with byte-identical structure and differ only in the rendered tokens;
  this theory lifts the parts that are pure decisions, so each backend becomes a
  renderer over the lifted result.

  \<open>classifyIdent\<close> lifts the identifier-resolution precedence cascade — the
  \<open>resolveIdent\<close> head shared verbatim by all three backends. Given the lexical / IR
  context it classifies a bare identifier into one of ten outcomes; the backend
  renders each outcome in its own syntax (an output reference is
  \<open>response_data["x"]\<close> in Python, \<open>responseData["x"]\<close> in TypeScript,
  \<open>_field(responseData, "x")\<close> in Go) and labels the reserved-name skip with its own
  language. Single-sourcing the precedence makes the skip decision identical across
  targets by construction.

  \<open>ident_ctx\<close> is a positional datatype (not a \<open>record\<close>) so it extracts cleanly via
  \<open>Code_Target_Scala\<close>, mirroring the \<open>schema_object\<close> convention.\<close>

datatype ident_ctx = IdentCtx
  "String.literal list"   \<comment> \<open>reserved words (per target language)\<close>
  "String.literal list"   \<comment> \<open>bound variables in scope\<close>
  "String.literal option" \<comment> \<open>the bare-body output name, if any\<close>
  "String.literal list"   \<comment> \<open>operation outputs\<close>
  "String.literal list"   \<comment> \<open>operation inputs\<close>
  "String.literal list"   \<comment> \<open>state field names\<close>
  "String.literal list"   \<comment> \<open>state fields the /state endpoint cannot project\<close>
  "String.literal list"   \<comment> \<open>enum type names\<close>
  "String.literal list"   \<comment> \<open>all enum member values (flattened)\<close>

datatype ident_class =
    IcReserved
  | IcBound
  | IcBareBody
  | IcOutput
  | IcInput
  | IcStateField
  | IcUnbackedState
  | IcEnumType
  | IcEnumValue
  | IcUnbound

fun classifyIdent :: "ident_ctx \<Rightarrow> String.literal \<Rightarrow> ident_class" where
  "classifyIdent
     (IdentCtx reserved bound bareBody outputs inputs stateFields unbackedState enumTypes enumValues)
     name =
    (if string_in_list name reserved \<and> (string_in_list name bound \<or> string_in_list name inputs)
       then IcReserved
     else if string_in_list name bound then IcBound
     else if bareBody = Some name then IcBareBody
     else if string_in_list name outputs then IcOutput
     else if string_in_list name inputs then IcInput
     else if string_in_list name stateFields
       then (if string_in_list name unbackedState then IcUnbackedState else IcStateField)
     else if string_in_list name enumTypes then IcEnumType
     else if string_in_list name enumValues then IcEnumValue
     else IcUnbound)"

lemmas classifyIdent_code [code] = classifyIdent.simps

text \<open>\<open>classifyUserCall\<close> lifts the arity dispatch shared by the three backends'
  \<open>userDefinedCall\<close>: look the call name up among the user-defined function then
  predicate arities and compare to the supplied argument count. The backend renders
  the unknown / wrong-arity skips (identical neutral messages) and, on \<open>UcOk\<close>, emits
  the call in its own syntax (Python snake-cases the name with no reserved-name guard;
  TypeScript and Go keep the name and add their own reserved-name skip).\<close>

fun lookupArity :: "(String.literal \<times> int) list \<Rightarrow> String.literal \<Rightarrow> int option" where
  "lookupArity [] _ = None"
| "lookupArity ((nm, ar) # rest) fname =
     (if nm = fname then Some ar else lookupArity rest fname)"

datatype user_call_class = UcUnknown | UcWrongArity int | UcOk

definition classifyUserCall ::
  "(String.literal \<times> int) list \<Rightarrow> (String.literal \<times> int) list
    \<Rightarrow> String.literal \<Rightarrow> int \<Rightarrow> user_call_class"
where
  "classifyUserCall fnArities predArities fname argCount =
    (case (case lookupArity fnArities fname of
             Some n \<Rightarrow> Some n
           | None \<Rightarrow> lookupArity predArities fname) of
       None \<Rightarrow> UcUnknown
     | Some n \<Rightarrow> (if n = argCount then UcOk else UcWrongArity n))"

text \<open>\<open>quantifierAllIn\<close> lifts the gate at the head of each backend's \<open>quantifier\<close>:
  the comprehension form is only emitted when every binding uses \<open>in\<close> (\<open>BkIn\<close>); a
  \<open>:\<close>-binding (\<open>BkColon\<close>) forces the shared skip.\<close>

fun quantBindingIsIn :: "quantifier_binding_full \<Rightarrow> bool" where
  "quantBindingIsIn (QuantifierBindingFull _ _ BkIn _) = True"
| "quantBindingIsIn _ = False"

definition quantifierAllIn :: "quantifier_binding_full list \<Rightarrow> bool" where
  "quantifierAllIn bs = list_all quantBindingIsIn bs"

lemmas lookupArity_code [code]      = lookupArity.simps
lemmas classifyUserCall_code [code] = classifyUserCall_def
lemmas quantBindingIsIn_code [code] = quantBindingIsIn.simps
lemmas quantifierAllIn_code [code]  = quantifierAllIn_def

text \<open>\<open>isMapLiteralExpr\<close> recognises a \<open>MapLiteralF\<close>. Lifts the
  \<open>isInstanceOf[MapLiteralF]\<close> guard each backend uses to route a \<open>BAdd\<close> over
  map literals to a dict-merge, removing the reflection (and the wart suppression).\<close>

fun isMapLiteralExpr :: "expr_full \<Rightarrow> bool" where
  "isMapLiteralExpr (MapLiteralF _ _) = True"
| "isMapLiteralExpr _ = False"

lemmas isMapLiteralExpr_code [code] = isMapLiteralExpr.simps

text \<open>Type-expression walkers shared by the testgen admin / behavioral backends
  (\<open>AdminRouter.isNumericType\<close>/\<open>isOptionalType\<close>, \<open>AdminModel.isDateTimeType\<close>,
  \<open>Behavioral.collectionElementType\<close>). Each resolves a \<open>NamedTypeF\<close> through the spec's
  type-alias table. The Scala uses a \<open>seen\<close>-set to stop alias cycles; here a fuel bound
  (\<open>length aliases + 100\<close>, the \<open>typeExprToSchemaAux\<close> convention) plays the same role —
  identical on every realistic spec, and both reject cyclic aliases.\<close>

fun lookupAliasTarget ::
  "type_alias_decl_full list \<Rightarrow> String.literal \<Rightarrow> type_expr_full option"
where
  "lookupAliasTarget [] _ = None"
| "lookupAliasTarget (TypeAliasDeclFull nm tgt _ _ # rest) n =
     (if nm = n then Some tgt else lookupAliasTarget rest n)"

definition typeWalkFuel :: "type_alias_decl_full list \<Rightarrow> nat" where
  "typeWalkFuel aliases = length aliases + 100"

fun isNumericTypeAux :: "nat \<Rightarrow> type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isNumericTypeAux 0 _ _ = False"
| "isNumericTypeAux (Suc fuel) aliases t =
     (case t of
        NamedTypeF n _ \<Rightarrow>
          (if n = STR ''Int'' \<or> n = STR ''Long'' \<or> n = STR ''Float'' \<or> n = STR ''Double''
             then True
           else (case lookupAliasTarget aliases n of
                   Some tgt \<Rightarrow> isNumericTypeAux fuel aliases tgt
                 | None \<Rightarrow> False))
      | OptionTypeF inner _ \<Rightarrow> isNumericTypeAux fuel aliases inner
      | _ \<Rightarrow> False)"

fun isOptionalTypeAux :: "nat \<Rightarrow> type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isOptionalTypeAux 0 _ _ = False"
| "isOptionalTypeAux (Suc fuel) aliases t =
     (case t of
        OptionTypeF _ _ \<Rightarrow> True
      | NamedTypeF n _ \<Rightarrow>
          (case lookupAliasTarget aliases n of
             Some tgt \<Rightarrow> isOptionalTypeAux fuel aliases tgt
           | None \<Rightarrow> False)
      | _ \<Rightarrow> False)"

fun isDateTimeTypeAux :: "nat \<Rightarrow> type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isDateTimeTypeAux 0 _ _ = False"
| "isDateTimeTypeAux (Suc fuel) aliases t =
     (case t of
        NamedTypeF n _ \<Rightarrow>
          (if n = STR ''DateTime'' then True
           else (case lookupAliasTarget aliases n of
                   Some tgt \<Rightarrow> isDateTimeTypeAux fuel aliases tgt
                 | None \<Rightarrow> False))
      | OptionTypeF inner _ \<Rightarrow> isDateTimeTypeAux fuel aliases inner
      | _ \<Rightarrow> False)"

fun collectionElementTypeAux ::
  "nat \<Rightarrow> type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> type_expr_full option"
where
  "collectionElementTypeAux 0 _ _ = None"
| "collectionElementTypeAux (Suc fuel) aliases t =
     (case t of
        SetTypeF inner _ \<Rightarrow> Some inner
      | SeqTypeF inner _ \<Rightarrow> Some inner
      | OptionTypeF inner _ \<Rightarrow> collectionElementTypeAux fuel aliases inner
      | NamedTypeF n _ \<Rightarrow>
          (case lookupAliasTarget aliases n of
             Some tgt \<Rightarrow> collectionElementTypeAux fuel aliases tgt
           | None \<Rightarrow> None)
      | _ \<Rightarrow> None)"

definition isNumericType :: "type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isNumericType aliases t = isNumericTypeAux (typeWalkFuel aliases) aliases t"

definition isOptionalType :: "type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isOptionalType aliases t = isOptionalTypeAux (typeWalkFuel aliases) aliases t"

definition isDateTimeType :: "type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> bool" where
  "isDateTimeType aliases t = isDateTimeTypeAux (typeWalkFuel aliases) aliases t"

definition collectionElementType ::
  "type_alias_decl_full list \<Rightarrow> type_expr_full \<Rightarrow> type_expr_full option"
where
  "collectionElementType aliases t = collectionElementTypeAux (typeWalkFuel aliases) aliases t"

lemmas lookupAliasTarget_code [code]        = lookupAliasTarget.simps
lemmas typeWalkFuel_code [code]             = typeWalkFuel_def
lemmas isNumericTypeAux_code [code]         = isNumericTypeAux.simps
lemmas isOptionalTypeAux_code [code]        = isOptionalTypeAux.simps
lemmas isDateTimeTypeAux_code [code]        = isDateTimeTypeAux.simps
lemmas collectionElementTypeAux_code [code] = collectionElementTypeAux.simps
lemmas isNumericType_code [code]            = isNumericType_def
lemmas isOptionalType_code [code]           = isOptionalType_def
lemmas isDateTimeType_code [code]           = isDateTimeType_def
lemmas collectionElementType_code [code]    = collectionElementType_def

end
