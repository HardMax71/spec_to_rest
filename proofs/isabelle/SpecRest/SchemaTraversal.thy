theory SchemaTraversal
  imports IR_Helpers IR_Lower
begin

text \<open>Pure IR traversals lifted from \<open>specrest.convention.Schema\<close>'s
  deriveSchema pipeline. \<open>aliasRefinements\<close> walks a type expression's alias
  chain (and through \<open>OptionTypeF\<close>) collecting the \<open>where\<close> predicates
  carried by each \<open>TypeAliasDeclFull\<close>; the traversal needs a visited-set
  guard against cyclic aliases.

  Alias maps are passed as association lists because Isabelle's \<open>Map\<close> is
  abstract; the Scala caller converts \<open>Map[String, TypeAliasDeclFull]\<close> to
  the equivalent \<open>(String.literal \<times> type_alias_decl_full) list\<close> at the
  boundary.\<close>

type_synonym alias_map = "(String.literal \<times> type_alias_decl_full) list"

text \<open>Fuel-bounded traversal: the visited set monotonically grows by one
  on each \<open>NamedTypeF\<close> hop and bounds the recursion depth, so we cap by
  the size of the alias map. Even simpler: cap by an explicit fuel
  parameter equal to the alias map's length, which Isabelle accepts as
  obviously decreasing.\<close>

fun aliasRefinementsAux ::
  "nat \<Rightarrow> type_expr_full \<Rightarrow> alias_map \<Rightarrow> String.literal list \<Rightarrow> expr_full list"
where
  "aliasRefinementsAux 0 _ _ _ = []"
| "aliasRefinementsAux (Suc fuel) ty am seen = (case ty of
       NamedTypeF name _ \<Rightarrow>
         (if name \<in> set seen then []
          else case map_of am name of
                 None \<Rightarrow> []
               | Some (TypeAliasDeclFull _ base predOpt _) \<Rightarrow>
                   (case predOpt of None \<Rightarrow> [] | Some p \<Rightarrow> [p]) @
                     aliasRefinementsAux fuel base am (name # seen))
     | OptionTypeF inner _ \<Rightarrow> aliasRefinementsAux fuel inner am seen
     | _ \<Rightarrow> [])"

definition aliasRefinements :: "type_expr_full \<Rightarrow> alias_map \<Rightarrow> expr_full list" where
  "aliasRefinements ty am = aliasRefinementsAux (Suc (length am)) ty am []"

lemmas aliasRefinementsAux_code [code] = aliasRefinementsAux.simps
lemmas aliasRefinements_code [code] = aliasRefinements_def

end
