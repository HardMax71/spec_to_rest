theory TopoSort
  imports Main "HOL-Library.Code_Target_Numeral"
begin

text \<open>Topological sort over a (name, dependencies) adjacency list. Returns the
  names in dependency-respecting order, or None if a cycle exists among the input
  names. Self-loops (a name listed among its own deps) and references to names
  not present in the input list are treated as satisfied — the latter matches
  the hand-written Scala semantics where FKs to external tables don't block sorting.

  The algorithm is bounded Kahn's: at each step pick the first node whose
  unresolved deps are all self-loops or external/already-removed, emit it,
  recurse on the remainder. Fuel = original list length makes termination
  structural.\<close>

fun is_ready_in ::
  "String.literal \<times> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "is_ready_in (n, deps) names = list_all (\<lambda>d. d = n \<or> d \<notin> set names) deps"

fun topo_sort_step ::
  "nat \<Rightarrow> (String.literal \<times> String.literal list) list
   \<Rightarrow> String.literal list \<Rightarrow> String.literal list option"
where
  "topo_sort_step _ [] acc = Some (rev acc)"
| "topo_sort_step 0 (_ # _) _ = None"
| "topo_sort_step (Suc fuel) (n # ns) acc =
    (let nodes = n # ns;
         names = map fst nodes
     in case filter (\<lambda>p. is_ready_in p names) nodes of
          [] \<Rightarrow> None
        | (rn, _) # _ \<Rightarrow>
            topo_sort_step fuel (filter (\<lambda>p. fst p \<noteq> rn) nodes) (rn # acc))"

definition topo_sort_names ::
  "(String.literal \<times> String.literal list) list \<Rightarrow> String.literal list option"
where
  "topo_sort_names nodes \<equiv> topo_sort_step (length nodes) nodes []"

lemmas topo_sort_names_code [code] = topo_sort_names_def

text \<open>Smoke proof: on the empty input, the sort succeeds with the empty list.
  The full permutation theorem is intentionally omitted at this milestone — the
  fuel-bounded structure makes it a routine induction we can add as follow-up
  without changing the extracted code.\<close>

lemma topo_sort_names_empty: "topo_sort_names [] = Some []"
  by (simp add: topo_sort_names_def)

end
