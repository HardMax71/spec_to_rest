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

lemmas literalEndsWith_code [code]            = literalEndsWith_def
lemmas stateRelationKeyTypeNamesAux_code [code] = stateRelationKeyTypeNamesAux.simps
lemmas stateRelationKeyTypeNames_code [code]  = stateRelationKeyTypeNames_def
lemmas paramTypeIsInt_code [code]             = paramTypeIsInt.simps
lemmas paramNameLooksLikeId_code [code]       = paramNameLooksLikeId_def
lemmas findIdParamAux_code [code]             = findIdParamAux.simps
lemmas findIdParam_code [code]                = findIdParam_def

end
