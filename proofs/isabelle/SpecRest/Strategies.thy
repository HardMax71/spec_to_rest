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

definition emptyIntConstraint :: int_constraint where
  "emptyIntConstraint = IntConstraint None None []"

definition emptyStringConstraint :: string_constraint where
  "emptyStringConstraint = StringConstraint None None [] [] []"

definition mergeMinInt :: "int option \<Rightarrow> int option \<Rightarrow> int option" where
  "mergeMinInt a b = (case (a, b) of
       (Some x, Some y) \<Rightarrow> Some (max x y)
     | (Some x, None) \<Rightarrow> Some x
     | (None, y) \<Rightarrow> y)"

definition mergeMaxInt :: "int option \<Rightarrow> int option \<Rightarrow> int option" where
  "mergeMaxInt a b = (case (a, b) of
       (Some x, Some y) \<Rightarrow> Some (min x y)
     | (Some x, None) \<Rightarrow> Some x
     | (None, y) \<Rightarrow> y)"

fun mergeIntConstraint :: "int_constraint \<Rightarrow> int_constraint \<Rightarrow> int_constraint" where
  "mergeIntConstraint (IntConstraint amin amax af) (IntConstraint bmin bmax bf) =
     IntConstraint (mergeMinInt amin bmin) (mergeMaxInt amax bmax) (af @ bf)"

fun mergeStringConstraint :: "string_constraint \<Rightarrow> string_constraint \<Rightarrow> string_constraint" where
  "mergeStringConstraint
     (StringConstraint amin amax ar ap af)
     (StringConstraint bmin bmax br bp bf) =
     StringConstraint
       (mergeMinInt amin bmin)
       (mergeMaxInt amax bmax)
       (remdups (ar @ br))
       (remdups (ap @ bp))
       (af @ bf)"

text \<open>A walk result pairs the accumulated constraint with the list of skip
  reasons (unhandled atoms reported back to the caller).\<close>

type_synonym int_walk_result    = "int_constraint \<times> String.literal list"
type_synonym string_walk_result = "string_constraint \<times> String.literal list"

definition intAtom :: "expr_full \<Rightarrow> int_walk_result" where
  "intAtom atom = (case decomposeAtom atom of
       RaValueCmp op n \<Rightarrow>
         (case op of
            BGe \<Rightarrow> (IntConstraint (Some n) None [], [])
          | BGt \<Rightarrow> (IntConstraint (Some (n + 1)) None [], [])
          | BLe \<Rightarrow> (IntConstraint None (Some n) [], [])
          | BLt \<Rightarrow> (IntConstraint None (Some (n - 1)) [], [])
          | BEq \<Rightarrow> (IntConstraint (Some n) (Some n) [], [])
          | _   \<Rightarrow> (emptyIntConstraint, [STR ''unsupported int comparison'']))
     | _ \<Rightarrow> (emptyIntConstraint, [STR ''unhandled int constraint'']))"

definition walkIntConstraint :: "expr_full \<Rightarrow> int_walk_result" where
  "walkIntConstraint e =
     foldl
       (\<lambda>acc atom.
          let (cur, skips) = acc;
              (nxt, new_skips) = intAtom atom
          in (mergeIntConstraint cur nxt, skips @ new_skips))
       (emptyIntConstraint, [])
       (flattenAnd e)"

text \<open>String-constraint walker. Predicate-name lookups (predicate helpers,
  inline-matches resolution) stay in Scala because they need the service IR;
  this walker handles the structural atoms (\<open>RaLenCmp\<close>, \<open>RaMatches\<close>) and
  emits \<open>RaPredCall name\<close> as an unhandled-skip naming the predicate so the
  Scala post-processor can resolve it.\<close>

definition stringAtom :: "expr_full \<Rightarrow> string_walk_result" where
  "stringAtom atom = (case decomposeAtom atom of
       RaLenCmp op n \<Rightarrow>
         (case op of
            BGe \<Rightarrow> (StringConstraint (Some n) None [] [] [], [])
          | BGt \<Rightarrow> (StringConstraint (Some (n + 1)) None [] [] [], [])
          | BLe \<Rightarrow> (StringConstraint None (Some n) [] [] [], [])
          | BLt \<Rightarrow> (StringConstraint None (Some (n - 1)) [] [] [], [])
          | BEq \<Rightarrow> (StringConstraint (Some n) (Some n) [] [] [], [])
          | _   \<Rightarrow> (emptyStringConstraint, [STR ''unsupported len comparison'']))
     | RaMatches pat \<Rightarrow> (StringConstraint None None [pat] [] [], [])
     | RaPredCall name \<Rightarrow> (emptyStringConstraint, [name])
     | _ \<Rightarrow> (emptyStringConstraint, [STR ''unhandled string constraint'']))"

definition walkStringConstraint :: "expr_full \<Rightarrow> string_walk_result" where
  "walkStringConstraint e =
     foldl
       (\<lambda>acc atom.
          let (cur, skips) = acc;
              (nxt, new_skips) = stringAtom atom
          in (mergeStringConstraint cur nxt, skips @ new_skips))
       (emptyStringConstraint, [])
       (flattenAnd e)"

lemmas walkIntConstraintCode [code] = walkIntConstraint_def
lemmas walkStringConstraintCode [code] = walkStringConstraint_def
lemmas intAtomCode [code] = intAtom_def
lemmas stringAtomCode [code] = stringAtom_def
lemmas emptyIntConstraintCode [code] = emptyIntConstraint_def
lemmas emptyStringConstraintCode [code] = emptyStringConstraint_def
lemmas mergeMinIntCode [code] = mergeMinInt_def
lemmas mergeMaxIntCode [code] = mergeMaxInt_def

end
