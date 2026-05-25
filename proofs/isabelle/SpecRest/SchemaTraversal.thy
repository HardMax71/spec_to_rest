theory SchemaTraversal
  imports IR_Helpers IR_Lower
begin

text \<open>Pure IR traversals lifted from \<open>specrest.convention.Schema\<close>'s
  deriveSchema pipeline. \<open>aliasRefinements\<close> walks a type expression's alias
  chain (and through \<open>OptionTypeF\<close>) collecting the \<open>where\<close> predicates
  carried by each \<open>TypeAliasDeclFull\<close>; the traversal needs a visited-set
  guard against cyclic aliases.

  Alias and enum decls are passed as association lists because Isabelle's
  abstract \<open>Mapping\<close> is the only finite-map type the code generator
  exposes; the Scala caller converts \<open>Map[String, TypeAliasDeclFull]\<close>
  / \<open>Map[String, EnumDeclFull]\<close> to the equivalent
  \<open>(String.literal \<times> _) list\<close> at the boundary (cached in
  \<open>specrest.ir.IrIndex\<close> so the conversion happens once per IR).

  Follow-up perf opportunity: \<open>HOL-Library.RBT_Mapping\<close> would swap the
  underlying lookup from O(n) \<open>map_of\<close> to O(log n) red-black tree, with
  no change to the walker signatures.\<close>

type_synonym alias_map = "(String.literal \<times> type_alias_decl_full) list"
type_synonym enum_map  = "(String.literal \<times> enum_decl_full) list"

text \<open>Fuel-bounded traversal: the visited set monotonically grows by one
  on each \<open>NamedTypeF\<close> hop and bounds the recursion depth, so we cap by
  the size of the alias map. Even simpler: cap by an explicit fuel
  parameter equal to the alias map's length, which Isabelle accepts as
  obviously decreasing.\<close>

text \<open>\<open>stripOptions\<close> strips any leading \<open>OptionTypeF\<close> wrappers so the
  alias-chain fuel is only consumed by genuine alias hops; Option-nesting
  depth cannot truncate the traversal.\<close>

fun stripOptions :: "type_expr_full \<Rightarrow> type_expr_full" where
  "stripOptions (OptionTypeF inner _) = stripOptions inner"
| "stripOptions ty = ty"

fun aliasRefinementsAux ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> alias_map \<Rightarrow> String.literal list \<Rightarrow> expr_full list"
where
  "aliasRefinementsAux 0 _ _ _ = []"
| "aliasRefinementsAux (Suc fuel) ty am seen = (case stripOptions ty of
       NamedTypeF name _ \<Rightarrow>
         (if name \<in> set seen then []
          else case map_of am name of
                 None \<Rightarrow> []
               | Some (TypeAliasDeclFull _ base predOpt _) \<Rightarrow>
                   (case predOpt of None \<Rightarrow> [] | Some p \<Rightarrow> [p]) @
                     aliasRefinementsAux fuel base am (name # seen))
     | _ \<Rightarrow> [])"

definition aliasRefinements :: "type_expr_full \<Rightarrow> alias_map \<Rightarrow> expr_full list" where
  "aliasRefinements ty am = aliasRefinementsAux (Suc (length am)) ty am []"

text \<open>\<open>findEnumValuesInType\<close> walks the same alias chain as
  \<open>aliasRefinements\<close>; if any hop lands on an \<open>enum_decl_full\<close> the
  enum's value list is returned. Used by OpenAPI schema emission to attach
  \<open>enum:\<close> annotations to fields whose effective type resolves to an enum
  (directly or through aliases).\<close>

fun findEnumValuesInTypeAux ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list
    \<Rightarrow> String.literal list option"
where
  "findEnumValuesInTypeAux 0 _ _ _ _ = None"
| "findEnumValuesInTypeAux (Suc fuel) ty am em seen = (case stripOptions ty of
       NamedTypeF name _ \<Rightarrow>
         (case map_of em name of
            Some (EnumDeclFull _ vs _) \<Rightarrow> Some vs
          | None \<Rightarrow>
              (if name \<in> set seen then None
               else case map_of am name of
                      None \<Rightarrow> None
                    | Some (TypeAliasDeclFull _ base _ _) \<Rightarrow>
                        findEnumValuesInTypeAux fuel base am em (name # seen)))
     | _ \<Rightarrow> None)"

definition findEnumValuesInType ::
  "type_expr_full \<Rightarrow> alias_map \<Rightarrow> enum_map \<Rightarrow> String.literal list option"
where
  "findEnumValuesInType ty am em =
     findEnumValuesInTypeAux (Suc (length am)) ty am em []"

lemmas stripOptions_code [code] = stripOptions.simps
lemmas aliasRefinementsAux_code [code] = aliasRefinementsAux.simps
lemmas aliasRefinements_code [code] = aliasRefinements_def
lemmas findEnumValuesInTypeAux_code [code] = findEnumValuesInTypeAux.simps
lemmas findEnumValuesInType_code [code] = findEnumValuesInType_def

end
