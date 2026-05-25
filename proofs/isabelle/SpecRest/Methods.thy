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

lemmas parseHttpMethod_code [code] = parseHttpMethod_def

end
