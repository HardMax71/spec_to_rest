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

fun isReadyIn ::
  "String.literal \<times> String.literal list \<Rightarrow> String.literal list \<Rightarrow> bool"
where
  "isReadyIn (n, deps) names = list_all (\<lambda>d. d = n \<or> d \<notin> set names) deps"

function topoSortStep ::
  "nat \<Rightarrow> (String.literal \<times> String.literal list) list
   \<Rightarrow> String.literal list \<Rightarrow> String.literal list option"
where
  "topoSortStep _ [] acc = Some (rev acc)"
| "topoSortStep 0 (_ # _) _ = None"
| "topoSortStep (Suc fuel) (n # ns) acc =
    (let nodes = n # ns;
         names = map fst nodes
     in case filter (\<lambda>p. isReadyIn p names) nodes of
          [] \<Rightarrow> None
        | (rn, _) # _ \<Rightarrow>
            topoSortStep fuel (filter (\<lambda>p. fst p \<noteq> rn) nodes) (rn # acc))"
  by pat_completeness auto

termination
  by (relation "measure (\<lambda>(fuel, _, _). fuel)") auto

definition topoSortNames ::
  "(String.literal \<times> String.literal list) list \<Rightarrow> String.literal list option"
where
  "topoSortNames nodes \<equiv> topoSortStep (length nodes) nodes []"

lemmas topoSortNamesCode [code] = topoSortNames_def

text \<open>Smoke proof: on the empty input, the sort succeeds with the empty list.
  The full permutation theorem is intentionally omitted at this milestone — the
  fuel-bounded structure makes it a routine induction we can add as follow-up
  without changing the extracted code.\<close>

lemma topoSortNamesEmpty: "topoSortNames [] = Some []"
  by (simp add: topoSortNames_def)

end
