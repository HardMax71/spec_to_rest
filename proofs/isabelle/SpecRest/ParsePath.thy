theory ParsePath
  imports IR_Helpers Methods
begin

text \<open>Pure path-derivation helpers used by \<open>Path.autoDerivePath\<close>:
  picking the URL-path id parameter from an operation's inputs.

  The path-segment composition itself is \<open>derivePathPattern\<close> in
  \<open>Methods\<close>; this theory adds the input-list selection rule.\<close>

text \<open>\<open>literalEndsWith suf s\<close>: \<open>True\<close> when \<open>suf\<close> is a (possibly equal,
  possibly empty) tail of \<open>s\<close>. Mirrors Scala's \<open>String.endsWith\<close> on
  ASCII strings.\<close>

definition literalEndsWith :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "literalEndsWith suf s =
     (let xs = String.asciis_of_literal s; ys = String.asciis_of_literal suf
      in length ys \<le> length xs \<and> drop (length xs - length ys) xs = ys)"

text \<open>State-relation key types: the named types reachable by following
  the \<open>from\<close> column of every \<open>RelationTypeF\<close> in the state fields.
  Operation inputs whose declared type matches one of these names are
  presumed to be primary-key references and become \<open>{id}\<close> placeholders
  in the URL path.\<close>

primrec stateRelationKeyTypeNamesAux ::
  "state_field_decl_full list \<Rightarrow> String.literal list"
where
  "stateRelationKeyTypeNamesAux [] = []"
| "stateRelationKeyTypeNamesAux (sf # rest) = (case sf of
       StateFieldDeclFull _ (RelationTypeF frm _ _ _) _ \<Rightarrow>
         (case typeName frm of
            None   \<Rightarrow> stateRelationKeyTypeNamesAux rest
          | Some n \<Rightarrow> n # stateRelationKeyTypeNamesAux rest)
     | _ \<Rightarrow> stateRelationKeyTypeNamesAux rest)"

definition stateRelationKeyTypeNames ::
  "state_decl_full option \<Rightarrow> String.literal list"
where
  "stateRelationKeyTypeNames stateOpt = (case stateOpt of
       None \<Rightarrow> []
     | Some (StateDeclFull fs _) \<Rightarrow> stateRelationKeyTypeNamesAux fs)"

text \<open>A parameter "looks like" an integer id when its type is the bare
  \<open>NamedTypeF "Int"\<close> and its name is either exactly \<open>id\<close> or ends with
  the \<open>_id\<close> suffix. Per-parameter OR with the state-relation-key check;
  iteration order across the param list decides ties — first-match wins,
  matching the legacy Scala iterator + \<open>collectFirst\<close> semantics. (So an
  earlier param's \<open>Int id\<close> heuristic match can preempt a later param's
  state-relation match — this is intentional, not a regression.)\<close>

fun paramTypeIsInt :: "type_expr_full \<Rightarrow> bool" where
  "paramTypeIsInt (NamedTypeF n _) = (n = STR ''Int'')"
| "paramTypeIsInt _ = False"

definition paramNameLooksLikeId :: "String.literal \<Rightarrow> bool" where
  "paramNameLooksLikeId name =
     (name = STR ''id'' \<or> literalEndsWith (STR ''_id'') name)"

fun findIdParamAux ::
  "String.literal list \<Rightarrow> param_decl_full list \<Rightarrow> String.literal option"
where
  "findIdParamAux _ [] = None"
| "findIdParamAux keys (ParamDeclFull name ty _ # rest) =
     (let matchesKey = (case typeName ty of
                          None \<Rightarrow> False
                        | Some n \<Rightarrow> List.member keys n);
          matchesNameRule = paramTypeIsInt ty \<and> paramNameLooksLikeId name
      in if matchesKey \<or> matchesNameRule then Some name
         else findIdParamAux keys rest)"

text \<open>Operations with no \<open>state\<close> block declared can never have a
  meaningful \<open>{id}\<close>-style URL placeholder — there's no entity to look
  up. Short-circuit \<open>None\<close> at the state level so the \<open>id\<close>/\<open>_id\<close>
  heuristic only fires when at least one state field exists. Mirrors
  the original Scala's \<open>ir.f match \<dots> case None \<Rightarrow> None\<close>.\<close>

definition findIdParam ::
  "param_decl_full list \<Rightarrow> state_decl_full option \<Rightarrow> String.literal option"
where
  "findIdParam params stateOpt = (case stateOpt of
       None \<Rightarrow> None
     | Some _ \<Rightarrow> findIdParamAux (stateRelationKeyTypeNames stateOpt) params)"

text \<open>General \<open>literalStartsWith\<close> / \<open>literalLength\<close> / \<open>literalDropLeft\<close>
  / \<open>literalDropRight\<close>. Same shape as \<open>literalEndsWith\<close> above — operate
  on the ASCII octet list. Used by \<open>extractVerbBeforeKebab\<close>; kept
  general so other Path-related helpers can reuse them.\<close>

definition literalLength :: "String.literal \<Rightarrow> nat" where
  "literalLength s = length (String.asciis_of_literal s)"

definition literalStartsWith :: "String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "literalStartsWith pre s =
     (let xs = String.asciis_of_literal s; ys = String.asciis_of_literal pre
      in length ys \<le> length xs \<and> take (length ys) xs = ys)"

definition literalDropLeft :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal" where
  "literalDropLeft n s =
     String.literal_of_asciis (drop n (String.asciis_of_literal s))"

definition literalDropRight :: "nat \<Rightarrow> String.literal \<Rightarrow> String.literal" where
  "literalDropRight n s =
     (let xs = String.asciis_of_literal s
      in String.literal_of_asciis (take (length xs - n) xs))"

text \<open>Extract the verb portion of an operation name before Scala feeds
  it to \<open>Naming.toKebabCase\<close>. Four outcomes:

  \<^enum> entity unknown \<Rightarrow> return the whole op name
  \<^enum> op name ends with entity name and the prefix verb is non-empty
    \<Rightarrow> drop the suffix, return the prefix
  \<^enum> op name starts with entity name and the trailing verb is non-empty
    \<Rightarrow> drop the prefix, return the trailing verb
  \<^enum> otherwise (no affix match, or the verb would collapse to empty)
    \<Rightarrow> fall back to the whole op name

  The Scala caller wraps the result in \<open>Naming.toKebabCase\<close>; the kebab
  conversion itself is regex-driven and stays Scala-side.\<close>

definition extractVerbBeforeKebab ::
  "String.literal \<Rightarrow> String.literal option \<Rightarrow> String.literal"
where
  "extractVerbBeforeKebab opName entityOpt = (case entityOpt of
       None    \<Rightarrow> opName
     | Some en \<Rightarrow>
         if literalEndsWith en opName then
           let verb = literalDropRight (literalLength en) opName
           in if literalLength verb = 0 then opName else verb
         else if literalStartsWith en opName then
           let verb = literalDropLeft (literalLength en) opName
           in if literalLength verb = 0 then opName else verb
         else opName)"

lemmas literalEndsWith_code [code]            = literalEndsWith_def
lemmas stateRelationKeyTypeNamesAux_code [code] = stateRelationKeyTypeNamesAux.simps
lemmas stateRelationKeyTypeNames_code [code]  = stateRelationKeyTypeNames_def
lemmas paramTypeIsInt_code [code]             = paramTypeIsInt.simps
lemmas paramNameLooksLikeId_code [code]       = paramNameLooksLikeId_def
lemmas findIdParamAux_code [code]             = findIdParamAux.simps
lemmas findIdParam_code [code]                = findIdParam_def
lemmas literalLength_code [code]              = literalLength_def
lemmas literalStartsWith_code [code]          = literalStartsWith_def
lemmas literalDropLeft_code [code]            = literalDropLeft_def
lemmas literalDropRight_code [code]           = literalDropRight_def
lemmas extractVerbBeforeKebab_code [code]     = extractVerbBeforeKebab_def

end
