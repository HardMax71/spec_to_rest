theory Methods
  imports Main
begin

text \<open>HTTP method and operation-kind enums lifted from
  \<open>specrest.convention.{HttpMethod, OperationKind}\<close>. Cases use the
  plain names from the hand-written enums (no prefix) — case differences
  rule out collisions (\<open>DELETE\<close> vs \<open>Delete\<close>), and none of these
  names clash with existing extracted constructors.\<close>

datatype http_method = GET | POST | PUT | PATCH | DELETE

datatype operation_kind =
    Create | Read | Replace | PartialUpdate | Delete
  | CreateChild | FilteredRead | SideEffect | BatchMutation | Transition

definition parseHttpMethod :: "String.literal \<Rightarrow> http_method option" where
  "parseHttpMethod s = (
    if s = STR ''GET'' then Some GET
    else if s = STR ''POST'' then Some POST
    else if s = STR ''PUT'' then Some PUT
    else if s = STR ''PATCH'' then Some PATCH
    else if s = STR ''DELETE'' then Some DELETE
    else None)"

text \<open>HTTP-method / status / GET-classification decisions lifted from
  \<open>specrest.convention.Path\<close>. Convention-rule lookups happen in Scala
  (rules carry mixed-type literal values that don't fit cleanly in HOL);
  the lifted decisions take the parsed override (\<open>Option\<close>) plus the
  classification's fallback method and kind, and produce the effective
  values used by codegen + testgen + openapi.

  Status codes are returned as \<open>String.literal\<close> (Scala parses to Int at
  the boundary) — sidesteps the polymorphic-numeral extraction issue
  with HOL nat/int literals.\<close>

fun isGetMethod :: "http_method \<Rightarrow> bool" where
  "isGetMethod GET = True"
| "isGetMethod _   = False"

fun isDeleteMethod :: "http_method \<Rightarrow> bool" where
  "isDeleteMethod DELETE = True"
| "isDeleteMethod _      = False"

fun isCreateLikeKind :: "operation_kind \<Rightarrow> bool" where
  "isCreateLikeKind Create      = True"
| "isCreateLikeKind CreateChild = True"
| "isCreateLikeKind _           = False"

fun isDeleteKind :: "operation_kind \<Rightarrow> bool" where
  "isDeleteKind Delete = True"
| "isDeleteKind _      = False"

definition defaultStatus :: "http_method \<Rightarrow> operation_kind \<Rightarrow> String.literal" where
  "defaultStatus mth knd = (
    if isDeleteMethod mth then STR ''204''
    else if isCreateLikeKind knd then STR ''201''
    else if isDeleteKind knd then STR ''204''
    else STR ''200'')"

definition resolveStatus ::
  "String.literal option \<Rightarrow> http_method \<Rightarrow> operation_kind \<Rightarrow> String.literal"
where
  "resolveStatus override mth knd = (
    case override of Some s \<Rightarrow> s | None \<Rightarrow> defaultStatus mth knd)"

definition resolveMethod :: "http_method option \<Rightarrow> http_method \<Rightarrow> http_method" where
  "resolveMethod override fallback = (
    case override of Some m \<Rightarrow> m | None \<Rightarrow> fallback)"

lemmas parseHttpMethod_code [code]    = parseHttpMethod_def
lemmas isGetMethod_code [code]        = isGetMethod.simps
lemmas isDeleteMethod_code [code]     = isDeleteMethod.simps
lemmas isCreateLikeKind_code [code]   = isCreateLikeKind.simps
lemmas isDeleteKind_code [code]       = isDeleteKind.simps
lemmas defaultStatus_code [code]      = defaultStatus_def
lemmas resolveStatus_code [code]      = resolveStatus_def
lemmas resolveMethod_code [code]      = resolveMethod_def

end
