theory OpenApiConstraints
  imports IR_Helpers IR_Analysis
begin

text \<open>Constraint-bounds walker for OpenAPI schema emission. Lifts the Int
  subset of \<open>specrest.codegen.openapi.OpenApi.Constraints.applyAtom\<close>: walks
  the refinement-atoms recognised by \<open>decomposeAtom\<close> and accumulates
  JSON-Schema-style bounds (length, numeric, pattern).

  OpenAPI distinguishes inclusive vs exclusive bounds (\<open>minimum\<close> vs
  \<open>exclusiveMinimum\<close>) — the strategy walker in \<open>Strategies.thy\<close> just
  collapses BGt to +1 because it only cares about generator value ranges,
  but OpenAPI emits the exclusive form to JSON output. We track both
  separately here.

  Float-literal atoms (the \<open>RaUnknown\<close> fallback in the Scala original)
  stay in Scala — Isabelle handles the Int subset cleanly, the Scala
  caller overlays Double-typed bounds after.\<close>

datatype openapi_int_bounds = OpenApiIntBounds
  "int option"               \<comment> \<open>min_length (inclusive)\<close>
  "int option"               \<comment> \<open>max_length (inclusive)\<close>
  "int option"               \<comment> \<open>minimum (inclusive)\<close>
  "int option"               \<comment> \<open>maximum (inclusive)\<close>
  "int option"               \<comment> \<open>exclusive_minimum\<close>
  "int option"               \<comment> \<open>exclusive_maximum\<close>
  "String.literal option"    \<comment> \<open>pattern\<close>

definition emptyOpenApiIntBounds :: openapi_int_bounds where
  "emptyOpenApiIntBounds = OpenApiIntBounds None None None None None None None"

definition tightenIntMin :: "int option \<Rightarrow> int \<Rightarrow> int option" where
  "tightenIntMin cur n = (case cur of None \<Rightarrow> Some n | Some x \<Rightarrow> Some (max x n))"

definition tightenIntMax :: "int option \<Rightarrow> int \<Rightarrow> int option" where
  "tightenIntMax cur n = (case cur of None \<Rightarrow> Some n | Some x \<Rightarrow> Some (min x n))"

primrec withMinLength :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withMinLength v (OpenApiIntBounds _ ml mn mx emn emx p) = OpenApiIntBounds v ml mn mx emn emx p"
primrec withMaxLength :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withMaxLength v (OpenApiIntBounds nl _ mn mx emn emx p) = OpenApiIntBounds nl v mn mx emn emx p"
primrec withMinimum :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withMinimum v (OpenApiIntBounds nl ml _ mx emn emx p) = OpenApiIntBounds nl ml v mx emn emx p"
primrec withMaximum :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withMaximum v (OpenApiIntBounds nl ml mn _ emn emx p) = OpenApiIntBounds nl ml mn v emn emx p"
primrec withExclusiveMinimum :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withExclusiveMinimum v (OpenApiIntBounds nl ml mn mx _ emx p) = OpenApiIntBounds nl ml mn mx v emx p"
primrec withExclusiveMaximum :: "int option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withExclusiveMaximum v (OpenApiIntBounds nl ml mn mx emn _ p) = OpenApiIntBounds nl ml mn mx emn v p"
primrec withPattern :: "String.literal option \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds" where
  "withPattern v (OpenApiIntBounds nl ml mn mx emn emx _) = OpenApiIntBounds nl ml mn mx emn emx v"

primrec minLengthOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "minLengthOf (OpenApiIntBounds nl _ _ _ _ _ _) = nl"
primrec maxLengthOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "maxLengthOf (OpenApiIntBounds _ ml _ _ _ _ _) = ml"
primrec minimumOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "minimumOf (OpenApiIntBounds _ _ mn _ _ _ _) = mn"
primrec maximumOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "maximumOf (OpenApiIntBounds _ _ _ mx _ _ _) = mx"
primrec exclusiveMinimumOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "exclusiveMinimumOf (OpenApiIntBounds _ _ _ _ emn _ _) = emn"
primrec exclusiveMaximumOf :: "openapi_int_bounds \<Rightarrow> int option" where
  "exclusiveMaximumOf (OpenApiIntBounds _ _ _ _ _ emx _) = emx"
primrec patternOf :: "openapi_int_bounds \<Rightarrow> String.literal option" where
  "patternOf (OpenApiIntBounds _ _ _ _ _ _ p) = p"

text \<open>Length bounds are non-negative integers — collapsing strict to non-strict
  by \<open>\<plusminus>1\<close>. \<open>BLt 0\<close> yields no bound (we'd get a negative max length).\<close>

definition applyLengthBoundOpenApi ::
  "bin_op_full \<Rightarrow> int \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds"
where
  "applyLengthBoundOpenApi op n bounds = (
    if n < 0 then bounds
    else case op of
       BGe \<Rightarrow> withMinLength (tightenIntMin (minLengthOf bounds) n) bounds
     | BLe \<Rightarrow> withMaxLength (tightenIntMax (maxLengthOf bounds) n) bounds
     | BGt \<Rightarrow> withMinLength (tightenIntMin (minLengthOf bounds) (n + 1)) bounds
     | BLt \<Rightarrow> (if n - 1 < 0 then bounds
               else withMaxLength (tightenIntMax (maxLengthOf bounds) (n - 1)) bounds)
     | BEq \<Rightarrow> withMaxLength (tightenIntMax (maxLengthOf bounds) n)
              (withMinLength (tightenIntMin (minLengthOf bounds) n) bounds)
     | _ \<Rightarrow> bounds)"

text \<open>Numeric bounds preserve the inclusive/exclusive distinction — JSON Schema
  treats \<open>minimum\<close> and \<open>exclusiveMinimum\<close> as distinct annotations.\<close>

definition applyNumericBoundOpenApi ::
  "bin_op_full \<Rightarrow> int \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds"
where
  "applyNumericBoundOpenApi op n bounds = (case op of
       BGe \<Rightarrow> withMinimum (tightenIntMin (minimumOf bounds) n) bounds
     | BLe \<Rightarrow> withMaximum (tightenIntMax (maximumOf bounds) n) bounds
     | BGt \<Rightarrow> withExclusiveMinimum (tightenIntMin (exclusiveMinimumOf bounds) n) bounds
     | BLt \<Rightarrow> withExclusiveMaximum (tightenIntMax (exclusiveMaximumOf bounds) n) bounds
     | BEq \<Rightarrow> withMaximum (tightenIntMax (maximumOf bounds) n)
              (withMinimum (tightenIntMin (minimumOf bounds) n) bounds)
     | _ \<Rightarrow> bounds)"

definition applyAtomOpenApi ::
  "expr_full \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds"
where
  "applyAtomOpenApi atom bounds = (case decomposeAtom atom of
       RaMatches pat \<Rightarrow> withPattern (Some pat) bounds
     | RaLenCmp op n \<Rightarrow> applyLengthBoundOpenApi op n bounds
     | RaValueCmp op n \<Rightarrow> applyNumericBoundOpenApi op n bounds
     | _ \<Rightarrow> bounds)"

definition visitConstraintOpenApi ::
  "expr_full \<Rightarrow> openapi_int_bounds \<Rightarrow> openapi_int_bounds"
where
  "visitConstraintOpenApi e bounds =
     foldl (\<lambda>acc atom. applyAtomOpenApi atom acc) bounds (flattenAnd e)"

lemmas emptyOpenApiIntBounds_code [code] = emptyOpenApiIntBounds_def
lemmas tightenIntMin_code [code] = tightenIntMin_def
lemmas tightenIntMax_code [code] = tightenIntMax_def
lemmas applyLengthBoundOpenApi_code [code] = applyLengthBoundOpenApi_def
lemmas applyNumericBoundOpenApi_code [code] = applyNumericBoundOpenApi_def
lemmas applyAtomOpenApi_code [code] = applyAtomOpenApi_def
lemmas visitConstraintOpenApi_code [code] = visitConstraintOpenApi_def

end
