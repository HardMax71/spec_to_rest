theory Strategies
  imports IR_Analysis IR_Helpers
begin

text \<open>Constraint-walker primitives for the testgen strategy derivation.
  Lifts the pure subset of \<open>specrest.testgen.Strategies\<close> that does not depend
  on the full service IR — refinement-atom recognition into \<open>int_constraint\<close>
  and \<open>string_constraint\<close> records. Predicate-name lookups and backend rendering
  stay in Scala. The Scala layer composes these walkers with its IR-aware
  postprocessing.\<close>

datatype int_constraint = IntConstraint
  "int option"               \<comment> \<open>min_value (inclusive)\<close>
  "int option"               \<comment> \<open>max_value (inclusive)\<close>
  "String.literal list"      \<comment> \<open>extra_filters\<close>

datatype string_constraint = StringConstraint
  "int option"               \<comment> \<open>min_size (inclusive)\<close>
  "int option"               \<comment> \<open>max_size (inclusive)\<close>
  "String.literal list"      \<comment> \<open>regexes\<close>
  "String.literal list"      \<comment> \<open>predicate_helpers\<close>
  "String.literal list"      \<comment> \<open>extra_filters\<close>

definition empty_int_constraint :: int_constraint where
  "empty_int_constraint = IntConstraint None None []"

definition empty_string_constraint :: string_constraint where
  "empty_string_constraint = StringConstraint None None [] [] []"

definition merge_min_int :: "int option \<Rightarrow> int option \<Rightarrow> int option" where
  "merge_min_int a b = (case (a, b) of
       (Some x, Some y) \<Rightarrow> Some (max x y)
     | (Some x, None) \<Rightarrow> Some x
     | (None, y) \<Rightarrow> y)"

definition merge_max_int :: "int option \<Rightarrow> int option \<Rightarrow> int option" where
  "merge_max_int a b = (case (a, b) of
       (Some x, Some y) \<Rightarrow> Some (min x y)
     | (Some x, None) \<Rightarrow> Some x
     | (None, y) \<Rightarrow> y)"

fun merge_int_constraint :: "int_constraint \<Rightarrow> int_constraint \<Rightarrow> int_constraint" where
  "merge_int_constraint (IntConstraint amin amax af) (IntConstraint bmin bmax bf) =
     IntConstraint (merge_min_int amin bmin) (merge_max_int amax bmax) (af @ bf)"

fun merge_string_constraint :: "string_constraint \<Rightarrow> string_constraint \<Rightarrow> string_constraint" where
  "merge_string_constraint
     (StringConstraint amin amax ar ap af)
     (StringConstraint bmin bmax br bp bf) =
     StringConstraint
       (merge_min_int amin bmin)
       (merge_max_int amax bmax)
       (remdups (ar @ br))
       (remdups (ap @ bp))
       (af @ bf)"

text \<open>A walk result pairs the accumulated constraint with the list of skip
  reasons (unhandled atoms reported back to the caller).\<close>

type_synonym int_walk_result    = "int_constraint \<times> String.literal list"
type_synonym string_walk_result = "string_constraint \<times> String.literal list"

definition int_atom :: "expr_full \<Rightarrow> int_walk_result" where
  "int_atom atom = (case decomposeAtom atom of
       RaValueCmp op n \<Rightarrow>
         (case op of
            BGe \<Rightarrow> (IntConstraint (Some n) None [], [])
          | BGt \<Rightarrow> (IntConstraint (Some (n + 1)) None [], [])
          | BLe \<Rightarrow> (IntConstraint None (Some n) [], [])
          | BLt \<Rightarrow> (IntConstraint None (Some (n - 1)) [], [])
          | BEq \<Rightarrow> (IntConstraint (Some n) (Some n) [], [])
          | _   \<Rightarrow> (empty_int_constraint, [STR ''unsupported int comparison'']))
     | _ \<Rightarrow> (empty_int_constraint, [STR ''unhandled int constraint'']))"

definition walk_int_constraint :: "expr_full \<Rightarrow> int_walk_result" where
  "walk_int_constraint e =
     foldl
       (\<lambda>acc atom.
          let (cur, skips) = acc;
              (nxt, new_skips) = int_atom atom
          in (merge_int_constraint cur nxt, skips @ new_skips))
       (empty_int_constraint, [])
       (flattenAnd e)"

text \<open>String-constraint walker. Predicate-name lookups (predicate helpers,
  inline-matches resolution) stay in Scala because they need the service IR;
  this walker handles the structural atoms (\<open>RaLenCmp\<close>, \<open>RaMatches\<close>) and
  emits \<open>RaPredCall name\<close> as an unhandled-skip naming the predicate so the
  Scala post-processor can resolve it.\<close>

definition string_atom :: "expr_full \<Rightarrow> string_walk_result" where
  "string_atom atom = (case decomposeAtom atom of
       RaLenCmp op n \<Rightarrow>
         (case op of
            BGe \<Rightarrow> (StringConstraint (Some n) None [] [] [], [])
          | BGt \<Rightarrow> (StringConstraint (Some (n + 1)) None [] [] [], [])
          | BLe \<Rightarrow> (StringConstraint None (Some n) [] [] [], [])
          | BLt \<Rightarrow> (StringConstraint None (Some (n - 1)) [] [] [], [])
          | BEq \<Rightarrow> (StringConstraint (Some n) (Some n) [] [] [], [])
          | _   \<Rightarrow> (empty_string_constraint, [STR ''unsupported len comparison'']))
     | RaMatches pat \<Rightarrow> (StringConstraint None None [pat] [] [], [])
     | RaPredCall name \<Rightarrow> (empty_string_constraint, [name])
     | _ \<Rightarrow> (empty_string_constraint, [STR ''unhandled string constraint'']))"

definition walk_string_constraint :: "expr_full \<Rightarrow> string_walk_result" where
  "walk_string_constraint e =
     foldl
       (\<lambda>acc atom.
          let (cur, skips) = acc;
              (nxt, new_skips) = string_atom atom
          in (merge_string_constraint cur nxt, skips @ new_skips))
       (empty_string_constraint, [])
       (flattenAnd e)"

lemmas walk_int_constraint_code [code] = walk_int_constraint_def
lemmas walk_string_constraint_code [code] = walk_string_constraint_def
lemmas int_atom_code [code] = int_atom_def
lemmas string_atom_code [code] = string_atom_def
lemmas empty_int_constraint_code [code] = empty_int_constraint_def
lemmas empty_string_constraint_code [code] = empty_string_constraint_def
lemmas merge_min_int_code [code] = merge_min_int_def
lemmas merge_max_int_code [code] = merge_max_int_def

end
