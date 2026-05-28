theory OpenApiSchema
  imports OpenApiConstraints SchemaTraversal
begin

text \<open>Lifted core of the OpenAPI \<open>SchemaObject\<close> datatype and the
  non-recursive construction primitives behind
  \<open>specrest.codegen.openapi.OpenApi.SchemaObject\<close>,
  \<open>OpenApi.Schema.primitiveDefToSchema\<close>,
  \<open>OpenApi.Schema.mergeConstraints\<close>, and
  \<open>OpenApi.Schema.makeNullable\<close>.

  Field order matches the hand-written Scala case class (positional);
  the Scala-side \<open>SchemaObjectAdapter\<close> converts the lifted record back
  into the named-field facade that the YAML emitter reflects over.

  Numeric bounds carry \<open>decimal_lit\<close> here (matching \<open>openapi_bounds\<close>);
  the Scala adapter coerces to \<open>Double\<close> at the API boundary via the
  existing \<open>decimalToDouble\<close> helper. This keeps the lifted layer free
  of \<open>Double\<close> and preserves the user's literal mantissa+exponent shape
  through to emit-time.

  \<open>schema_object\<close> and \<open>schema_object_or_bool\<close> are mutually recursive
  (\<open>additionalProperties\<close> may hold either a nested schema or a bool),
  so they are declared in a single \<open>datatype\<close> block.\<close>

datatype schema_object = SchemaObject
  "String.literal list option"            \<comment> \<open>type (one or more JSON-schema type tags, ordered)\<close>
  "String.literal option"                 \<comment> \<open>format\<close>
  "int option"                            \<comment> \<open>minLength\<close>
  "int option"                            \<comment> \<open>maxLength\<close>
  "decimal_lit option"                    \<comment> \<open>minimum\<close>
  "decimal_lit option"                    \<comment> \<open>maximum\<close>
  "decimal_lit option"                    \<comment> \<open>exclusiveMinimum\<close>
  "decimal_lit option"                    \<comment> \<open>exclusiveMaximum\<close>
  "int option"                            \<comment> \<open>minItems\<close>
  "int option"                            \<comment> \<open>maxItems\<close>
  "String.literal option"                 \<comment> \<open>pattern\<close>
  "String.literal list option"            \<comment> \<open>enum_\<close>
  "schema_object option"                  \<comment> \<open>items\<close>
  "String.literal option"                 \<comment> \<open>ref\<close>
  "String.literal list option"            \<comment> \<open>required\<close>
  "(String.literal \<times> schema_object) list option" \<comment> \<open>properties (ordered alist)\<close>
  "schema_object_or_bool option"          \<comment> \<open>additionalProperties\<close>
  "schema_object list option"             \<comment> \<open>anyOf\<close>
  "String.literal option"                 \<comment> \<open>description\<close>
  bool                                    \<comment> \<open>includeNullInEnum\<close>
and schema_object_or_bool =
    SOBSchema schema_object
  | SOBBool bool

text \<open>Field-blank \<open>schema_object\<close>. Used as the base for incremental
  construction; mirrors the Scala default-argument \<open>SchemaObject()\<close>.\<close>

definition emptySchemaObject :: schema_object where
  "emptySchemaObject = SchemaObject
     None None None None None None None None None None
     None None None None None None None None None False"

text \<open>Lift of \<open>OpenApi.Schema.primitiveDefToSchema\<close>: construct a
  \<open>schema_object\<close> from a primitive's \<open>type\<close> tags and optional \<open>format\<close>.\<close>

fun primitiveDefToSchema :: "openapi_primitive_def \<Rightarrow> schema_object" where
  "primitiveDefToSchema (OpenApiPrimDef types fmt) =
     SchemaObject (Some types) fmt None None None None None None None None
                  None None None None None None None None None False"

text \<open>Lift of \<open>OpenApi.Schema.mergeConstraints\<close> rewritten to consume the
  lifted \<open>openapi_bounds\<close> directly, eliminating the Scala-side
  \<open>JsonSchemaConstraints\<close> intermediate. Each incoming bound takes
  precedence on \<open>Some\<close>; \<open>None\<close> leaves the base value intact
  (same as Scala \<open>.orElse\<close> precedence).

  The Scala caller will adopt this in a follow-up PR that drops
  \<open>JsonSchemaConstraints\<close> and threads \<open>openapi_bounds\<close> through
  \<open>typeExprToSchema\<close> directly (the current Scala mergeConstraints reads
  from \<open>JsonSchemaConstraints\<close>, whose \<open>Double\<close> numeric fields are a
  one-way projection of the original \<open>decimal_lit\<close>; consuming bounds
  directly avoids the lossy round-trip).\<close>

fun mergeConstraintsLifted ::
  "schema_object \<Rightarrow> openapi_bounds \<Rightarrow> String.literal list option \<Rightarrow> schema_object"
where
  "mergeConstraintsLifted
      (SchemaObject ty fmt bMinL bMaxL bMn bMx bEmn bEmx mnI mxI bPat bEn it rf rq pr ap aof desc inE)
      (OpenApiBounds cMinL cMaxL cMn cMx cEmn cEmx cPat)
      enumOpt =
    SchemaObject
      ty fmt
      (case cMinL of None \<Rightarrow> bMinL | _ \<Rightarrow> cMinL)
      (case cMaxL of None \<Rightarrow> bMaxL | _ \<Rightarrow> cMaxL)
      (case cMn   of None \<Rightarrow> bMn   | _ \<Rightarrow> cMn)
      (case cMx   of None \<Rightarrow> bMx   | _ \<Rightarrow> cMx)
      (case cEmn  of None \<Rightarrow> bEmn  | _ \<Rightarrow> cEmn)
      (case cEmx  of None \<Rightarrow> bEmx  | _ \<Rightarrow> cEmx)
      mnI mxI
      (case cPat   of None \<Rightarrow> bPat | _ \<Rightarrow> cPat)
      (case enumOpt of None \<Rightarrow> bEn  | _ \<Rightarrow> enumOpt)
      it rf rq pr ap aof desc inE"

text \<open>Lift of \<open>OpenApi.Schema.makeNullable\<close> as a *decision classifier*
  rather than a value-producing function. The original makeNullable
  inspects only \<open>ref\<close> and \<open>type\<close> on its input and applies one of three
  shapes (no-op, wrap-in-anyOf-with-null, append-null-to-types). Lifting
  the *decision* on those two fields avoids round-tripping the Scala
  \<open>SchemaObject\<close> (whose \<open>Double\<close> numeric fields can't losslessly
  reverse-convert to \<open>decimal_lit\<close>); the Scala caller applies the
  chosen shape using its own \<open>SchemaObject\<close>:

  \<^item> \<open>NdWrapAnyOfNull\<close>: \<open>ref\<close> is set, \<emph>or> \<open>type\<close> is absent. Can't
    merge a \<open>"null"\<close> tag — wrap in \<open>anyOf [s, {type:[null]}]\<close>.
  \<^item> \<open>NdNoop\<close>: \<open>type\<close> already includes \<open>"null"\<close>.
  \<^item> \<open>NdAppendNull\<close>: \<open>type\<close> is set and doesn't yet contain \<open>"null"\<close>
    — append \<open>"null"\<close> to the type list.\<close>

datatype nullable_decision = NdNoop | NdWrapAnyOfNull | NdAppendNull

definition decideNullable ::
  "String.literal option \<Rightarrow> String.literal list option \<Rightarrow> nullable_decision"
where
  "decideNullable refOpt typeOpt = (
     case refOpt of
       Some _ \<Rightarrow> NdWrapAnyOfNull
     | None \<Rightarrow>
         (case typeOpt of
            None \<Rightarrow> NdWrapAnyOfNull
          | Some currentTypes \<Rightarrow>
              (if STR ''null'' \<in> set currentTypes
               then NdNoop
               else NdAppendNull)))"

lemmas emptySchemaObject_code [code]      = emptySchemaObject_def
lemmas primitiveDefToSchema_code [code]   = primitiveDefToSchema.simps
lemmas mergeConstraintsLifted_code [code] = mergeConstraintsLifted.simps
lemmas decideNullable_code [code]         = decideNullable_def

end
