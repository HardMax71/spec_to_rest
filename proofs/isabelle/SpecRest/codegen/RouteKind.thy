theory RouteKind
  imports Main "HOL-Library.Code_Target_Numeral" Methods
begin

text \<open>Route-kind classification for the codegen + testgen route emitters.
  The hand-written \<open>specrest.codegen.RouteKind\<close> is retired; consumers use
  the extracted enum and \<open>classify\<close> / \<open>effectiveRouteKind\<close> directly.

  Constructor names carry an \<open>Rk\<close> prefix so the extracted \<open>RkList\<close> does
  not shadow \<open>scala.collection.immutable.List\<close>.\<close>

datatype (plugins only: code size) route_kind = RkCreate | RkRead | RkList | RkDelete | RkRedirect | RkOther

definition isRedirectStatus :: "int \<Rightarrow> bool" where
  "isRedirectStatus s = (s = 301 \<or> s = 302 \<or> s = 303 \<or> s = 307 \<or> s = 308)"

definition classifyShape ::
  "http_method \<Rightarrow> int \<Rightarrow> nat \<Rightarrow> operation_kind \<Rightarrow> route_kind"
where
  "classifyShape method status pathParamCount kind = (
    if isRedirectStatus status then RkRedirect
    else case kind of
           Create \<Rightarrow> RkCreate
         | Read \<Rightarrow> (if pathParamCount = 1 then RkRead
                    else if pathParamCount = 0 then RkList else RkOther)
         | FilteredRead \<Rightarrow> (if pathParamCount = 0 then RkList else RkOther)
         | Delete \<Rightarrow> (if pathParamCount = 1 then RkDelete else RkOther)
         | _ \<Rightarrow> (case method of
                   GET \<Rightarrow> (if pathParamCount = 0 then RkList else RkOther)
                 | _ \<Rightarrow> RkOther))"

text \<open>The \<open>list\<close> route returns every row and takes no arguments, so any declared
  filter input would be silently dropped. Downgrade to \<open>RkOther\<close> (the fail-loud
  stub) when the operation has body or query parameters.\<close>

fun isRkList :: "route_kind \<Rightarrow> bool" where
  "isRkList RkList = True"
| "isRkList _      = False"

fun isRkCreate :: "route_kind \<Rightarrow> bool" where
  "isRkCreate RkCreate = True"
| "isRkCreate _        = False"

fun isStubShape :: "route_kind \<Rightarrow> bool" where
  "isStubShape RkRedirect = True"
| "isStubShape RkOther    = True"
| "isStubShape _          = False"

definition classify ::
  "http_method \<Rightarrow> int \<Rightarrow> nat \<Rightarrow> operation_kind \<Rightarrow> bool \<Rightarrow> route_kind"
where
  "classify method status pathParamCount kind hasFilterInputs = (
    let shape = classifyShape method status pathParamCount kind in
    if isRkList shape \<and> hasFilterInputs then RkOther else shape)"

text \<open>The effective route kind: downgrades a \<open>RkCreate\<close> whose request body
  doesn't match the entity's non-id columns to the fail-loud stub.\<close>

definition effectiveRouteKind ::
  "route_kind \<Rightarrow> bool \<Rightarrow> route_kind"
where
  "effectiveRouteKind initial matchesCreateShape = (
    if isRkCreate initial \<and> \<not> matchesCreateShape then RkOther else initial)"

text \<open>Predicate for matchesEntityCreateShape: the operation classifies as Create
  and its body params exactly cover the entity's non-id columns.

  Set-equality plus distinctness on the body-param side rules out the
  duplicate-name pathology where \<open>[a, a]\<close> would otherwise match \<open>[a, b]\<close>
  via length+forall (length 2, every element in \<open>{a,b\<close>}). The entity
  column list is derived from a \<open>Set.toList\<close> on the Scala side so it is
  already distinct.\<close>

definition matchesCreateShape ::
  "route_kind \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "matchesCreateShape classification bodyParamNames entityNonIdColumns = (
    isRkCreate classification
    \<and> distinct bodyParamNames
    \<and> list_all (\<lambda>x. List.member entityNonIdColumns x) bodyParamNames
    \<and> list_all (\<lambda>y. List.member bodyParamNames y) entityNonIdColumns)"

text \<open>A handler is a fail-loud stub when the synthesis pass produced no kernel
  method and the effective shape is one the convention engine cannot derive
  a body for (\<open>RkRedirect\<close> carries spec side-effects; \<open>RkOther\<close> is the
  non-CRUD / shape-mismatch fallback).\<close>

definition isFailLoudStub ::
  "bool \<Rightarrow> route_kind \<Rightarrow> bool"
where
  "isFailLoudStub hasDafnyMethod effectiveKind =
    (\<not> hasDafnyMethod \<and> isStubShape effectiveKind)"

lemmas isRedirectStatus_code [code]   = isRedirectStatus_def
lemmas isRkList_code [code]           = isRkList.simps
lemmas isRkCreate_code [code]         = isRkCreate.simps
lemmas isStubShape_code [code]        = isStubShape.simps
lemmas classifyShape_code [code]      = classifyShape_def
lemmas classify_code [code]           = classify_def
lemmas effectiveRouteKind_code [code] = effectiveRouteKind_def
lemmas matchesCreateShape_code [code] = matchesCreateShape_def
lemmas isFailLoudStub_code [code]     = isFailLoudStub_def

end
