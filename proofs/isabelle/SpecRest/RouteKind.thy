theory RouteKind
  imports Main "HOL-Library.Code_Target_Numeral"
begin

text \<open>Route-kind classification for the codegen + testgen route emitters.
  The hand-written \<open>specrest.codegen.RouteKind\<close> is retired; consumers use
  the extracted enum and \<open>classify\<close> / \<open>effectiveRouteKind\<close> directly.
  Method and operation-kind enums are passed as \<open>String.literal\<close> primitives
  to keep the lift self-contained — the hand-written \<open>HttpMethod\<close> and
  \<open>OperationKind\<close> enums stay untouched, and consumers do \<open>method.toString\<close>
  at the call boundary.

  Constructor names carry an \<open>Rk\<close> prefix so the extracted \<open>RkList\<close> does
  not shadow \<open>scala.collection.immutable.List\<close>.\<close>

datatype route_kind = RkCreate | RkRead | RkList | RkDelete | RkRedirect | RkOther

definition isRedirectStatus :: "int \<Rightarrow> bool" where
  "isRedirectStatus s = (s = 301 \<or> s = 302 \<or> s = 303 \<or> s = 307 \<or> s = 308)"

definition classifyShape ::
  "String.literal \<Rightarrow> int \<Rightarrow> nat \<Rightarrow> String.literal \<Rightarrow> route_kind"
where
  "classifyShape method status pathParamCount kind = (
    if isRedirectStatus status then RkRedirect
    else if kind = STR ''Create'' then RkCreate
    else if kind = STR ''Read'' \<and> pathParamCount = 1 then RkRead
    else if kind = STR ''Read'' \<and> pathParamCount = 0 then RkList
    else if kind = STR ''FilteredRead'' \<and> pathParamCount = 0 then RkList
    else if kind = STR ''Delete'' \<and> pathParamCount = 1 then RkDelete
    else if method = STR ''GET'' \<and> pathParamCount = 0 then RkList
    else RkOther)"

text \<open>The \<open>list\<close> route returns every row and takes no arguments, so any declared
  filter input would be silently dropped. Downgrade to \<open>RkOther\<close> (the fail-loud
  stub) when the operation has body or query parameters.\<close>

definition classify ::
  "String.literal \<Rightarrow> int \<Rightarrow> nat \<Rightarrow> String.literal \<Rightarrow> bool \<Rightarrow> route_kind"
where
  "classify method status pathParamCount kind hasFilterInputs = (
    let shape = classifyShape method status pathParamCount kind in
    if shape = RkList \<and> hasFilterInputs then RkOther else shape)"

text \<open>The effective route kind: downgrades a \<open>RkCreate\<close> whose request body
  doesn't match the entity's non-id columns to the fail-loud stub.\<close>

definition effectiveRouteKind ::
  "route_kind \<Rightarrow> bool \<Rightarrow> route_kind"
where
  "effectiveRouteKind initial matchesCreateShape = (
    if initial = RkCreate \<and> \<not> matchesCreateShape then RkOther else initial)"

text \<open>Predicate for matchesEntityCreateShape: the operation classifies as Create
  and its body params exactly cover the entity's non-id columns.\<close>

definition matchesCreateShape ::
  "route_kind \<Rightarrow> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "matchesCreateShape classification bodyParamNames entityNonIdColumns = (
    classification = RkCreate
    \<and> length bodyParamNames = length entityNonIdColumns
    \<and> list_all (\<lambda>n. n \<in> set entityNonIdColumns) bodyParamNames)"

text \<open>A handler is a fail-loud stub when the synthesis pass produced no kernel
  method and the effective shape is one the convention engine cannot derive
  a body for (\<open>RkRedirect\<close> carries spec side-effects; \<open>RkOther\<close> is the
  non-CRUD / shape-mismatch fallback).\<close>

definition isFailLoudStub ::
  "bool \<Rightarrow> route_kind \<Rightarrow> bool"
where
  "isFailLoudStub hasDafnyMethod effectiveKind =
    (\<not> hasDafnyMethod \<and> (effectiveKind = RkRedirect \<or> effectiveKind = RkOther))"

lemmas isRedirectStatus_code [code]   = isRedirectStatus_def
lemmas classifyShape_code [code]      = classifyShape_def
lemmas classify_code [code]           = classify_def
lemmas effectiveRouteKind_code [code] = effectiveRouteKind_def
lemmas matchesCreateShape_code [code] = matchesCreateShape_def
lemmas isFailLoudStub_code [code]     = isFailLoudStub_def

end
