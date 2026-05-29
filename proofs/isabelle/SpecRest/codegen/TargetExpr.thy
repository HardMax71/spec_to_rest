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

end
