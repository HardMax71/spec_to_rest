theory OpenApiSchema
  imports OpenApiConstraints SchemaTraversal ParsePath
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

text \<open>Lifted variant of \<open>OpenApi.Schema.makeNullable\<close> that operates on a
  \<open>schema_object\<close> directly (no round-trip through the Scala \<open>SchemaObject\<close>).
  Used by the lifted recursive walker for the \<open>SetTypeF\<close>/\<open>SeqTypeF\<close>/\<open>MapTypeF\<close>
  inner-nullable cases; the Scala-side \<open>Schema.makeNullable\<close> keeps using
  \<open>decideNullable\<close> at the API boundary where the input is a Scala
  \<open>SchemaObject\<close>.\<close>

definition nullSchema :: schema_object where
  "nullSchema = SchemaObject (Some [STR ''null'']) None None None None None None None None None
                             None None None None None None None None None False"

fun makeNullableLifted :: "schema_object \<Rightarrow> schema_object" where
  "makeNullableLifted
      (SchemaObject ty fmt minL maxL mn mx emn emx mnI mxI pat en it rf rq pr ap aof desc inE) =
    (case decideNullable rf ty of
       NdNoop \<Rightarrow> SchemaObject ty fmt minL maxL mn mx emn emx mnI mxI pat en it rf rq pr ap aof desc inE
     | NdAppendNull \<Rightarrow>
         (case ty of
            Some currentTypes \<Rightarrow>
              SchemaObject (Some (currentTypes @ [STR ''null'']))
                           fmt minL maxL mn mx emn emx mnI mxI pat en it rf rq pr ap aof desc inE
          \<comment> \<open>Unreachable: \<open>decideNullable\<close> only returns \<open>NdAppendNull\<close> when \<open>typeOpt\<close>
              is \<open>Some\<close>. The \<open>None\<close> branch is fed back into the original schema unchanged so
              the function remains total.\<close>
          | None \<Rightarrow>
              SchemaObject ty fmt minL maxL mn mx emn emx mnI mxI pat en it rf rq pr ap aof desc inE)
     | NdWrapAnyOfNull \<Rightarrow>
         SchemaObject None None None None None None None None None None None None None None None None None
                      (Some [SchemaObject ty fmt minL maxL mn mx emn emx mnI mxI pat en it rf rq pr ap aof desc inE,
                             nullSchema])
                      None False)"

text \<open>Constructor helpers for the lifted recursive walker. Each wraps the
  20-field \<open>SchemaObject\<close> constructor with only the relevant fields set
  for a given OpenAPI shape (string-typed enum, \<open>$ref\<close>, array with
  \<open>items\<close>, object with \<open>additionalProperties\<close>, integer for relation FK).\<close>

definition integerSchema :: schema_object where
  "integerSchema = SchemaObject (Some [STR ''integer'']) None None None None None None None None
                                None None None None None None None None None None False"

definition textSchema :: schema_object where
  "textSchema = SchemaObject (Some [STR ''string'']) None None None None None None None None
                             None None None None None None None None None None False"

definition enumStringSchema :: "String.literal list \<Rightarrow> schema_object" where
  "enumStringSchema values =
     SchemaObject (Some [STR ''string'']) None None None None None None None None None
                  None (Some values) None None None None None None None False"

definition entityRefSchema :: "String.literal \<Rightarrow> schema_object" where
  "entityRefSchema name =
     SchemaObject None None None None None None None None None None
                  None None None
                  (Some (STR ''#/components/schemas/'' + name + STR ''Read''))
                  None None None None None False"

definition arraySchema :: "schema_object \<Rightarrow> int option \<Rightarrow> int option \<Rightarrow> schema_object" where
  "arraySchema items mnI mxI =
     SchemaObject (Some [STR ''array'']) None None None None None None None mnI mxI
                  None None (Some items) None None None None None None False"

definition mapObjectSchema :: "schema_object \<Rightarrow> schema_object" where
  "mapObjectSchema ap =
     SchemaObject (Some [STR ''object'']) None None None None None None None None None
                  None None None None None None
                  (Some (SOBSchema ap)) None None False"

text \<open>Bounds selectors. The lifted recursive walker reads min/max length
  off the \<open>openapi_bounds\<close> tuple to populate \<open>minItems\<close>/\<open>maxItems\<close>
  on the array schema, matching the Scala \<open>buildArraySchema\<close> behaviour.\<close>

fun boundsMinLength :: "openapi_bounds \<Rightarrow> int option" where
  "boundsMinLength (OpenApiBounds mnL _ _ _ _ _ _) = mnL"

fun boundsMaxLength :: "openapi_bounds \<Rightarrow> int option" where
  "boundsMaxLength (OpenApiBounds _ mxL _ _ _ _ _) = mxL"

text \<open>Compute the constraint bounds for a field's type and optional
  inline refinement, by folding \<open>visitConstraintOpenApi\<close> over the alias
  refinement chain and then (if present) the field's own constraint
  expression. Mirrors \<open>OpenApi.Constraints.extractFieldConstraints\<close>
  but returns \<open>openapi_bounds\<close> directly (the Scala-side
  \<open>JsonSchemaConstraints\<close>-with-\<open>Double\<close> intermediate is eliminated).\<close>

definition computeFieldBounds ::
  "type_expr_full \<Rightarrow> expr_full option \<Rightarrow> alias_map \<Rightarrow> openapi_bounds"
where
  "computeFieldBounds ty cOpt am =
    (let alias_bounds =
           foldl (\<lambda>b p. visitConstraintOpenApi p b) emptyOpenApiBounds (aliasRefinements ty am);
         final_bounds =
           (case cOpt of None \<Rightarrow> alias_bounds
                       | Some c \<Rightarrow> visitConstraintOpenApi c alias_bounds)
     in final_bounds)"

text \<open>Recursive lifted walker. Mirrors \<open>OpenApi.Schema.typeExprToSchema\<close>,
  \<open>buildArraySchema\<close>, and \<open>OpenApi.Schema.fieldToSchema\<close> as a single
  mutually-recursive \<open>fun\<close> block, with a \<open>nat\<close> fuel decreasing on each
  recursive call (alias chains and structural recursion share the fuel
  bound; the entry-point definitions seed with \<open>Suc (length am)\<close> to
  bound alias-chain depth). \<open>namedTypeSchema\<close> is inlined into the
  \<open>NamedTypeF\<close> case because \<open>classifyOpenApiNamedType\<close> is a single
  pure call (already lifted in \<open>SchemaTraversal\<close>).

  Returns from \<open>fieldToSchemaAux\<close> use an \<open>option\<close>-shaped pair
  \<open>(schema_object, bool)\<close>: the boolean tracks whether the input type
  expression had an outer \<open>OptionTypeF\<close> wrapper (the Scala
  \<open>FieldSchema.nullable\<close> flag).\<close>

function typeExprToSchemaAux ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> openapi_bounds \<Rightarrow> String.literal list option
    \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list \<Rightarrow> schema_object"
and fieldToSchemaAux ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> expr_full option
    \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list \<Rightarrow> (schema_object \<times> bool)"
where
  "typeExprToSchemaAux 0 _ _ _ _ _ _ = emptySchemaObject"
| "typeExprToSchemaAux (Suc fuel) ty bounds enumOpt am em ens = (case ty of
     NamedTypeF name _ \<Rightarrow>
       (case classifyOpenApiNamedType name am em ens of
          OntPrimitive p \<Rightarrow> mergeConstraintsLifted (primitiveDefToSchema p) bounds enumOpt
        | OntEnum values \<Rightarrow> enumStringSchema values
        | OntEntityRef n \<Rightarrow> entityRefSchema n
        | OntAliasToType base \<Rightarrow> typeExprToSchemaAux fuel base bounds enumOpt am em ens
        | OntUnknown \<Rightarrow> mergeConstraintsLifted textSchema bounds enumOpt)
   | SetTypeF inner _ \<Rightarrow>
       (case fieldToSchemaAux fuel inner None am em ens of (innerSchema, nullable) \<Rightarrow>
         (let items = (if nullable then makeNullableLifted innerSchema else innerSchema)
          in arraySchema items (boundsMinLength bounds) (boundsMaxLength bounds)))
   | SeqTypeF inner _ \<Rightarrow>
       (case fieldToSchemaAux fuel inner None am em ens of (innerSchema, nullable) \<Rightarrow>
         (let items = (if nullable then makeNullableLifted innerSchema else innerSchema)
          in arraySchema items (boundsMinLength bounds) (boundsMaxLength bounds)))
   | MapTypeF _ v _ \<Rightarrow>
       (case fieldToSchemaAux fuel v None am em ens of (valueSchema, nullable) \<Rightarrow>
         (let ap = (if nullable then makeNullableLifted valueSchema else valueSchema)
          in mapObjectSchema ap))
   | OptionTypeF inner _ \<Rightarrow> typeExprToSchemaAux fuel inner bounds enumOpt am em ens
   | RelationTypeF _ _ _ _ \<Rightarrow> integerSchema)"

| "fieldToSchemaAux 0 _ _ _ _ _ = (emptySchemaObject, False)"
| "fieldToSchemaAux (Suc fuel) ty cOpt am em ens =
    (let nullable = (case ty of OptionTypeF _ _ \<Rightarrow> True | _ \<Rightarrow> False);
         effective = (case ty of OptionTypeF inner _ \<Rightarrow> inner | t \<Rightarrow> t);
         bounds = computeFieldBounds effective cOpt am;
         enumOpt = findEnumValuesInType effective am em
     in (typeExprToSchemaAux fuel effective bounds enumOpt am em ens, nullable))"
  by pat_completeness auto

termination
  by (relation "measure (\<lambda>p. case p of
        Inl (fuel, _, _, _, _, _, _) \<Rightarrow> fuel
      | Inr (fuel, _, _, _, _, _) \<Rightarrow> fuel)")
     auto

text \<open>Fuel seed: \<open>length am + 100\<close>. Fuel decrements on every recursive
  call across both \<open>typeExprToSchemaAux\<close> and \<open>fieldToSchemaAux\<close>; the
  alias chain inside a NamedType is resolved via the lifted
  \<open>classifyOpenApiNamedType\<close> which has its own internal fuel
  (\<open>length am\<close>) and doesn't consume this one. The \<open>+ 100\<close> margin
  covers structural nesting (each \<open>Set\<close>/\<open>Seq\<close>/\<open>Map\<close> wrap consumes 2
  fuel as \<open>typeExprToSchemaAux\<close> → \<open>fieldToSchemaAux\<close> → \<open>typeExprToSchemaAux\<close>);
  real-world type depth is \<le> 10.\<close>

definition openApiSchemaFuel :: "alias_map \<Rightarrow> nat" where
  "openApiSchemaFuel am = length am + 100"

definition typeExprToSchema ::
  "type_expr_full \<Rightarrow> openapi_bounds \<Rightarrow> String.literal list option
    \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list \<Rightarrow> schema_object"
where
  "typeExprToSchema ty bounds enumOpt am em ens =
     typeExprToSchemaAux (openApiSchemaFuel am) ty bounds enumOpt am em ens"

definition fieldToSchema ::
  "type_expr_full \<Rightarrow> expr_full option
    \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list \<Rightarrow> (schema_object \<times> bool)"
where
  "fieldToSchema ty cOpt am em ens =
     fieldToSchemaAux (openApiSchemaFuel am) ty cOpt am em ens"

text \<open>Sensitive-field predicate. Lift of \<open>specrest.codegen.SensitiveFields.isSensitive\<close>:
  an exact-name set plus a name-suffix set. The Scala \<open>SensitiveFields\<close> facade now
  delegates to this, so the Python emitter and testgen redaction share one source.\<close>

definition sensitiveExactNames :: "String.literal list" where
  "sensitiveExactNames =
     [STR ''password'', STR ''password_hash'', STR ''secret'', STR ''token'', STR ''api_key'']"

definition sensitiveSuffixNames :: "String.literal list" where
  "sensitiveSuffixNames =
     [STR ''_hash'', STR ''_secret'', STR ''_password'', STR ''_api_key'', STR ''_token'']"

definition isSensitiveField :: "String.literal \<Rightarrow> bool" where
  "isSensitiveField name =
     (string_in_list name sensitiveExactNames \<or>
      list_ex (\<lambda>sfx. literalEndsWith sfx name) sensitiveSuffixNames)"

text \<open>Entity-level OpenAPI schema assembly. Lift of
  \<open>OpenApi.Components.{create,read,update}Schema\<close> over the lifted \<open>schema_object\<close>:
  the Scala adapter converts the three results once each rather than per field, and the
  lossy \<open>schema_object \<rightarrow> Scala SchemaObject\<close> round-trip is avoided. Input is the decorated
  field list \<open>(column_name, field_schema, nullable)\<close> from the lifted \<open>fieldToSchema\<close>.

  \<^item> \<open>id\<close> is dropped from every payload (\<open>nonIdDecorated\<close>).
  \<^item> create: \<open>required\<close> = non-nullable field names; each property nullable-wrapped per its flag.
  \<^item> read: drops sensitive fields, injects an integer \<open>id\<close> property, \<open>required\<close> = \<open>id\<close> + non-nullable.
  \<^item> update: every property is nullable-wrapped, no \<open>required\<close>.\<close>

fun nonIdDecorated ::
  "(String.literal \<times> schema_object \<times> bool) list \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list"
where
  "nonIdDecorated [] = []"
| "nonIdDecorated ((n, s, nu) # rest) =
     (if n = STR ''id'' then nonIdDecorated rest else (n, s, nu) # nonIdDecorated rest)"

fun dropSensitive ::
  "(String.literal \<times> schema_object \<times> bool) list \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list"
where
  "dropSensitive [] = []"
| "dropSensitive ((n, s, nu) # rest) =
     (if isSensitiveField n then dropSensitive rest else (n, s, nu) # dropSensitive rest)"

fun requiredNames ::
  "(String.literal \<times> schema_object \<times> bool) list \<Rightarrow> String.literal list"
where
  "requiredNames [] = []"
| "requiredNames ((n, _, nu) # rest) =
     (if nu then requiredNames rest else n # requiredNames rest)"

definition fieldPropertySchema :: "schema_object \<Rightarrow> bool \<Rightarrow> schema_object" where
  "fieldPropertySchema s nullable = (if nullable then makeNullableLifted s else s)"

fun fieldProps ::
  "(String.literal \<times> schema_object \<times> bool) list \<Rightarrow> (String.literal \<times> schema_object) list"
where
  "fieldProps [] = []"
| "fieldProps ((n, s, nu) # rest) = (n, fieldPropertySchema s nu) # fieldProps rest"

fun fieldPropsNullable ::
  "(String.literal \<times> schema_object \<times> bool) list \<Rightarrow> (String.literal \<times> schema_object) list"
where
  "fieldPropsNullable [] = []"
| "fieldPropsNullable ((n, s, _) # rest) = (n, makeNullableLifted s) # fieldPropsNullable rest"

definition createSchemaLifted ::
  "String.literal \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list \<Rightarrow> schema_object"
where
  "createSchemaLifted ename decorated =
    (let fs = nonIdDecorated decorated
     in SchemaObject (Some [STR ''object''])
          None None None None None None None None None None None None None
          (Some (requiredNames fs))
          (Some (fieldProps fs))
          None None
          (Some (STR ''Create payload for '' + ename))
          False)"

definition readSchemaLifted ::
  "String.literal \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list \<Rightarrow> schema_object"
where
  "readSchemaLifted ename decorated =
    (let fs = dropSensitive (nonIdDecorated decorated)
     in SchemaObject (Some [STR ''object''])
          None None None None None None None None None None None None None
          (Some (STR ''id'' # requiredNames fs))
          (Some ((STR ''id'', integerSchema) # fieldProps fs))
          None None
          (Some (STR ''Read view for '' + ename))
          False)"

definition updateSchemaLifted ::
  "String.literal \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list \<Rightarrow> schema_object"
where
  "updateSchemaLifted ename decorated =
    (let fs = nonIdDecorated decorated
     in SchemaObject (Some [STR ''object''])
          None None None None None None None None None None None None None
          None
          (Some (fieldPropsNullable fs))
          None None
          (Some (STR ''Update payload for '' + ename))
          False)"

definition buildEntitySchemas ::
  "String.literal \<Rightarrow> (String.literal \<times> schema_object \<times> bool) list
    \<Rightarrow> schema_object \<times> schema_object \<times> schema_object"
where
  "buildEntitySchemas ename decorated =
     (createSchemaLifted ename decorated, readSchemaLifted ename decorated, updateSchemaLifted ename decorated)"

lemmas emptySchemaObject_code [code]      = emptySchemaObject_def
lemmas primitiveDefToSchema_code [code]   = primitiveDefToSchema.simps
lemmas mergeConstraintsLifted_code [code] = mergeConstraintsLifted.simps
lemmas decideNullable_code [code]         = decideNullable_def
lemmas nullSchema_code [code]             = nullSchema_def
lemmas makeNullableLifted_code [code]     = makeNullableLifted.simps
lemmas integerSchema_code [code]          = integerSchema_def
lemmas textSchema_code [code]             = textSchema_def
lemmas enumStringSchema_code [code]       = enumStringSchema_def
lemmas entityRefSchema_code [code]        = entityRefSchema_def
lemmas arraySchema_code [code]            = arraySchema_def
lemmas mapObjectSchema_code [code]        = mapObjectSchema_def
lemmas boundsMinLength_code [code]        = boundsMinLength.simps
lemmas boundsMaxLength_code [code]        = boundsMaxLength.simps
lemmas computeFieldBounds_code [code]     = computeFieldBounds_def
lemmas openApiSchemaFuel_code [code]      = openApiSchemaFuel_def
lemmas typeExprToSchemaAux_code [code]    = typeExprToSchemaAux.simps fieldToSchemaAux.simps
lemmas typeExprToSchema_code [code]       = typeExprToSchema_def
lemmas fieldToSchema_code [code]          = fieldToSchema_def
lemmas sensitiveExactNames_code [code]    = sensitiveExactNames_def
lemmas sensitiveSuffixNames_code [code]   = sensitiveSuffixNames_def
lemmas isSensitiveField_code [code]       = isSensitiveField_def
lemmas nonIdDecorated_code [code]         = nonIdDecorated.simps
lemmas dropSensitive_code [code]          = dropSensitive.simps
lemmas requiredNames_code [code]          = requiredNames.simps
lemmas fieldPropertySchema_code [code]    = fieldPropertySchema_def
lemmas fieldProps_code [code]             = fieldProps.simps
lemmas fieldPropsNullable_code [code]     = fieldPropsNullable.simps
lemmas createSchemaLifted_code [code]     = createSchemaLifted_def
lemmas readSchemaLifted_code [code]       = readSchemaLifted_def
lemmas updateSchemaLifted_code [code]     = updateSchemaLifted_def
lemmas buildEntitySchemas_code [code]     = buildEntitySchemas_def

end
