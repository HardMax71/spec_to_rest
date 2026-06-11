theory OpenApiConstraints
  imports SpecRest_Core.IR_Helpers SpecRest_Core.IR_Analysis
begin

text \<open>Constraint-bounds walker for OpenAPI schema emission. Lifts
  \<open>specrest.codegen.openapi.OpenApi.Constraints.applyAtom\<close> in full —
  both the Int subset (\<open>RaValueCmp\<close> / \<open>RaLenCmp\<close> / \<open>RaMatches\<close>) and
  the Float-literal fallback (\<open>BinaryOpF\<close> + \<open>FloatLitF\<close>) the original
  walker handled inline.

  Float-literal values arrive as text payloads on \<open>FloatLitF\<close>. We
  parse them in Isabelle (\<open>parseDecimalLit\<close>) so there is no Scala-side
  Double parsing — the Scala caller only converts the final lifted
  \<open>decimal_lit\<close> to its target numeric type at the API boundary.

  Numeric bounds are stored as \<open>decimal_lit\<close> = \<open>mantissa * 10\<^bsup>exponent\<^esup>\<close>
  to preserve the user's input form exactly. Length bounds remain
  \<open>int\<close> because lengths can't be fractional.\<close>

datatype decimal_lit = DecimalLit int int
  \<comment> \<open>\<open>DecimalLit m e\<close> represents \<open>m * 10\<^sup>e\<close>.
       Examples: "3.14" \<rightharpoonup> DecimalLit 314 (-2), "5" \<rightharpoonup> DecimalLit 5 0,
                 "0.5" \<rightharpoonup> DecimalLit 5 (-1)\<close>

text \<open>Comparison: align exponents to the smaller, scale the larger-exponent
  mantissa up by the difference, then compare mantissas. Result holds for
  arbitrary integer mantissas and exponents (positive or negative).\<close>

fun decimalGt :: "decimal_lit \<Rightarrow> decimal_lit \<Rightarrow> bool" where
  "decimalGt (DecimalLit m1 e1) (DecimalLit m2 e2) =
     (if e1 \<le> e2
      then m1 > m2 * (10 ^ nat (e2 - e1))
      else m1 * (10 ^ nat (e1 - e2)) > m2)"

definition maxDecimal :: "decimal_lit \<Rightarrow> decimal_lit \<Rightarrow> decimal_lit" where
  "maxDecimal a b = (if decimalGt a b then a else b)"

definition minDecimal :: "decimal_lit \<Rightarrow> decimal_lit \<Rightarrow> decimal_lit" where
  "minDecimal a b = (if decimalGt a b then b else a)"

definition decimalOfInt :: "int \<Rightarrow> decimal_lit" where
  "decimalOfInt n = DecimalLit n 0"

text \<open>Coerce a \<open>decimal_lit\<close> to \<open>int\<close> if it represents a non-negative
  integer; \<open>None\<close> otherwise. Used to feed Float-literal length bounds
  through the integer-only length path (the Scala original silently
  rejected fractional length bounds with the same check).\<close>

fun decimalToNonNegInt :: "decimal_lit \<Rightarrow> int option" where
  "decimalToNonNegInt (DecimalLit m e) =
     (if m < 0 then None
      else if e \<ge> 0 then Some (m * (10 ^ nat e))
      else (let p = 10 ^ nat (- e)
            in if m mod p = 0 then Some (m div p) else None))"

text \<open>Decimal-literal parser: \<open>String.literal \<Rightarrow> decimal_lit option\<close>.
  Accepts an optional leading \<open>-\<close>, an integer part, and an optional
  fractional part (\<open>.\<close> followed by digits). Rejects scientific notation
  (\<open>e\<close>/\<open>E\<close> exponent) and any other trailing characters — spec refinements
  use plain decimals in practice.

  Implementation walks the ASCII octet list returned by
  \<open>String.asciis_of_literal\<close>. ASCII codes: \<open>'0'..'9' = 48..57\<close>,
  \<open>'-' = 45\<close>, \<open>'.' = 46\<close>.\<close>

definition isDigitAscii :: "integer \<Rightarrow> bool" where
  "isDigitAscii c = (48 \<le> c \<and> c \<le> 57)"

definition digitValue :: "integer \<Rightarrow> int" where
  "digitValue c = int_of_integer (c - 48)"

fun consumeDigitsAux ::
  "integer list \<Rightarrow> int \<Rightarrow> nat \<Rightarrow> (int \<times> nat \<times> integer list)"
where
  "consumeDigitsAux [] acc count = (acc, count, [])"
| "consumeDigitsAux (c # cs) acc count =
     (if isDigitAscii c
      then consumeDigitsAux cs (acc * 10 + digitValue c) (count + 1)
      else (acc, count, c # cs))"

definition parseDecimalLit :: "String.literal \<Rightarrow> decimal_lit option" where
  "parseDecimalLit s = (
    let cs0 = String.asciis_of_literal s;
        (sign, cs1) = (case cs0 of
                         []     \<Rightarrow> (1::int, cs0)
                       | c # rs \<Rightarrow> (if c = 45 then (-1::int, rs)
                                    else (1::int, cs0)));
        (intPart, intLen, rest1) = consumeDigitsAux cs1 0 0
    in case rest1 of
         [] \<Rightarrow> (if intLen = 0 then None
                else Some (DecimalLit (sign * intPart) 0))
       | (c # rs) \<Rightarrow>
           (if c = 46 \<comment> \<open>'.'\<close>
            then let (fracPart, fracLen, rest2) = consumeDigitsAux rs 0 0
                 in (if rest2 = [] \<and> intLen > 0 \<and> fracLen > 0
                     then Some (DecimalLit
                                  (sign * (intPart * (10 ^ fracLen) + fracPart))
                                  (- int fracLen))
                     else None)
            else None))"

text \<open>Bounds record. Length bounds stay \<open>int option\<close> (lengths can't be
  fractional); numeric bounds are \<open>decimal_lit option\<close>.\<close>

datatype openapi_bounds = OpenApiBounds
  "int option"               \<comment> \<open>min_length (inclusive)\<close>
  "int option"               \<comment> \<open>max_length (inclusive)\<close>
  "decimal_lit option"       \<comment> \<open>minimum (inclusive)\<close>
  "decimal_lit option"       \<comment> \<open>maximum (inclusive)\<close>
  "decimal_lit option"       \<comment> \<open>exclusive_minimum\<close>
  "decimal_lit option"       \<comment> \<open>exclusive_maximum\<close>
  "String.literal option"    \<comment> \<open>pattern\<close>

definition emptyOpenApiBounds :: openapi_bounds where
  "emptyOpenApiBounds = OpenApiBounds None None None None None None None"

definition tightenIntMin :: "int option \<Rightarrow> int \<Rightarrow> int option" where
  "tightenIntMin cur n = (case cur of None \<Rightarrow> Some n | Some x \<Rightarrow> Some (max x n))"

definition tightenIntMax :: "int option \<Rightarrow> int \<Rightarrow> int option" where
  "tightenIntMax cur n = (case cur of None \<Rightarrow> Some n | Some x \<Rightarrow> Some (min x n))"

definition tightenDecMin :: "decimal_lit option \<Rightarrow> decimal_lit \<Rightarrow> decimal_lit option" where
  "tightenDecMin cur d = (case cur of None \<Rightarrow> Some d | Some x \<Rightarrow> Some (maxDecimal x d))"

definition tightenDecMax :: "decimal_lit option \<Rightarrow> decimal_lit \<Rightarrow> decimal_lit option" where
  "tightenDecMax cur d = (case cur of None \<Rightarrow> Some d | Some x \<Rightarrow> Some (minDecimal x d))"

primrec withMinLength :: "int option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withMinLength v (OpenApiBounds _ ml mn mx emn emx p) = OpenApiBounds v ml mn mx emn emx p"
primrec withMaxLength :: "int option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withMaxLength v (OpenApiBounds nl _ mn mx emn emx p) = OpenApiBounds nl v mn mx emn emx p"
primrec withMinimum :: "decimal_lit option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withMinimum v (OpenApiBounds nl ml _ mx emn emx p) = OpenApiBounds nl ml v mx emn emx p"
primrec withMaximum :: "decimal_lit option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withMaximum v (OpenApiBounds nl ml mn _ emn emx p) = OpenApiBounds nl ml mn v emn emx p"
primrec withExclusiveMinimum :: "decimal_lit option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withExclusiveMinimum v (OpenApiBounds nl ml mn mx _ emx p) = OpenApiBounds nl ml mn mx v emx p"
primrec withExclusiveMaximum :: "decimal_lit option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withExclusiveMaximum v (OpenApiBounds nl ml mn mx emn _ p) = OpenApiBounds nl ml mn mx emn v p"
primrec withPattern :: "String.literal option \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds" where
  "withPattern v (OpenApiBounds nl ml mn mx emn emx _) = OpenApiBounds nl ml mn mx emn emx v"

primrec minLengthOf :: "openapi_bounds \<Rightarrow> int option" where
  "minLengthOf (OpenApiBounds nl _ _ _ _ _ _) = nl"
primrec maxLengthOf :: "openapi_bounds \<Rightarrow> int option" where
  "maxLengthOf (OpenApiBounds _ ml _ _ _ _ _) = ml"
primrec minimumOf :: "openapi_bounds \<Rightarrow> decimal_lit option" where
  "minimumOf (OpenApiBounds _ _ mn _ _ _ _) = mn"
primrec maximumOf :: "openapi_bounds \<Rightarrow> decimal_lit option" where
  "maximumOf (OpenApiBounds _ _ _ mx _ _ _) = mx"
primrec exclusiveMinimumOf :: "openapi_bounds \<Rightarrow> decimal_lit option" where
  "exclusiveMinimumOf (OpenApiBounds _ _ _ _ emn _ _) = emn"
primrec exclusiveMaximumOf :: "openapi_bounds \<Rightarrow> decimal_lit option" where
  "exclusiveMaximumOf (OpenApiBounds _ _ _ _ _ emx _) = emx"
primrec patternOf :: "openapi_bounds \<Rightarrow> String.literal option" where
  "patternOf (OpenApiBounds _ _ _ _ _ _ p) = p"

text \<open>Length bounds are non-negative integers — \<open>BGt\<close> collapses to \<open>+1\<close>
  (length is always integer-valued), \<open>BLt 0\<close> yields no bound.\<close>

definition applyLengthBoundOpenApi ::
  "bin_op \<Rightarrow> int \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds"
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
  "bin_op \<Rightarrow> decimal_lit \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds"
where
  "applyNumericBoundOpenApi op d bounds = (case op of
       BGe \<Rightarrow> withMinimum (tightenDecMin (minimumOf bounds) d) bounds
     | BLe \<Rightarrow> withMaximum (tightenDecMax (maximumOf bounds) d) bounds
     | BGt \<Rightarrow> withExclusiveMinimum (tightenDecMin (exclusiveMinimumOf bounds) d) bounds
     | BLt \<Rightarrow> withExclusiveMaximum (tightenDecMax (exclusiveMaximumOf bounds) d) bounds
     | BEq \<Rightarrow> withMaximum (tightenDecMax (maximumOf bounds) d)
              (withMinimum (tightenDecMin (minimumOf bounds) d) bounds)
     | _ \<Rightarrow> bounds)"

text \<open>Atom dispatch. The \<open>decomposeAtom\<close> recognizer covers integer literals
  and \<open>matches\<close> calls; the \<open>RaUnknown\<close> fallback for \<open>BinaryOpF op lhs
  (FloatLitF v _) _\<close> dispatches to the decimal-literal walker (length or
  numeric depending on whether the LHS is a length-of-value expression).\<close>

definition applyFloatAtomOpenApi ::
  "expr \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds"
where
  "applyFloatAtomOpenApi atom bounds = (case atom of
       BinaryOpF op lhs (FloatLitF v _) _ \<Rightarrow>
         (case parseDecimalLit v of
            None \<Rightarrow> bounds
          | Some d \<Rightarrow>
              if isLenOfValue lhs
              then (case decimalToNonNegInt d of
                      None \<Rightarrow> bounds
                    | Some ni \<Rightarrow> applyLengthBoundOpenApi op ni bounds)
              else if isValueRef lhs
              then applyNumericBoundOpenApi op d bounds
              else bounds)
     | _ \<Rightarrow> bounds)"

definition applyAtomOpenApi ::
  "expr \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds"
where
  "applyAtomOpenApi atom bounds = (case decomposeAtom atom of
       RaMatches pat \<Rightarrow> withPattern (Some pat) bounds
     | RaLenCmp op n \<Rightarrow> applyLengthBoundOpenApi op n bounds
     | RaValueCmp op n \<Rightarrow> applyNumericBoundOpenApi op (decimalOfInt n) bounds
     | _ \<Rightarrow> applyFloatAtomOpenApi atom bounds)"

definition visitConstraintOpenApi ::
  "expr \<Rightarrow> openapi_bounds \<Rightarrow> openapi_bounds"
where
  "visitConstraintOpenApi e bounds =
     foldl (\<lambda>acc atom. applyAtomOpenApi atom acc) bounds (flattenAnd e)"

lemmas decimalGt_code [code] = decimalGt.simps
lemmas maxDecimal_code [code] = maxDecimal_def
lemmas minDecimal_code [code] = minDecimal_def
lemmas decimalOfInt_code [code] = decimalOfInt_def
lemmas decimalToNonNegInt_code [code] = decimalToNonNegInt.simps
lemmas isDigitAscii_code [code] = isDigitAscii_def
lemmas digitValue_code [code] = digitValue_def
lemmas consumeDigitsAux_code [code] = consumeDigitsAux.simps
lemmas parseDecimalLit_code [code] = parseDecimalLit_def
lemmas emptyOpenApiBounds_code [code] = emptyOpenApiBounds_def
lemmas tightenIntMin_code [code] = tightenIntMin_def
lemmas tightenIntMax_code [code] = tightenIntMax_def
lemmas tightenDecMin_code [code] = tightenDecMin_def
lemmas tightenDecMax_code [code] = tightenDecMax_def
lemmas applyLengthBoundOpenApi_code [code] = applyLengthBoundOpenApi_def
lemmas applyNumericBoundOpenApi_code [code] = applyNumericBoundOpenApi_def
lemmas applyFloatAtomOpenApi_code [code] = applyFloatAtomOpenApi_def
lemmas applyAtomOpenApi_code [code] = applyAtomOpenApi_def
lemmas visitConstraintOpenApi_code [code] = visitConstraintOpenApi_def

text \<open>\<open>showNat\<close>: render a \<open>nat\<close> as a base-10 decimal \<open>String.literal\<close>
  via ASCII octet construction. Reverse of \<open>parseDecimalLit\<close>'s digit
  parsing. Foundational for any code-extracted formatter that needs
  numeric→text (auto-numbered names, error messages, etc.).\<close>

fun digitsRev :: "nat \<Rightarrow> nat list" where
  "digitsRev n = (if n < 10 then [n]
                  else (n mod 10) # digitsRev (n div 10))"

definition showNat :: "nat \<Rightarrow> String.literal" where
  "showNat n = String.literal_of_asciis
                 (rev (map (\<lambda>d. integer_of_nat (48 + d)) (digitsRev n)))"

text \<open>Name disambiguation: given an ordered list of \<open>(name, value)\<close>
  pairs, replace each duplicate name with \<open>\<open>name\<close>_<i>\<close> where \<open>i\<close>
  is the smallest non-negative index that doesn't collide with any
  already-emitted (or natural) name. Output preserves input order;
  matches the existing Scala \<open>asStableMap\<close> behaviour and its robust
  handling of pathological inputs like \<open>["foo", "foo_0", "foo"]\<close>
  (the second \<open>foo\<close> skips the explicit \<open>foo_0\<close> to land at \<open>foo_1\<close>).

  Complexity: O(n^2) on n input pairs, plus O(n) per collision iteration
  on the \<open>freshKey\<close> search (so O(n^3) worst-case on adversarial inputs
  where every entry collides). Acceptable for the real usage — invariant
  and temporal lists in spec files are bounded by a handful of entries.
  Same list-based \<open>List.member\<close> pattern as \<open>aliasRefinements\<close> /
  \<open>findEnumValuesInType\<close>; the planned \<open>HOL-Library.RBT_Mapping\<close>
  swap (see \<open>docs/.../isabelle-proofs.mdx\<close> Layer 2) drops every lookup
  to O(log n) without changing the call sites.\<close>

fun freshKeyAux ::
  "nat \<Rightarrow> String.literal \<Rightarrow> String.literal list \<Rightarrow> nat \<Rightarrow> String.literal"
where
  "freshKeyAux 0 base _ i = base + STR ''_'' + showNat i"
| "freshKeyAux (Suc fuel) base seen i =
     (let candidate = base + STR ''_'' + showNat i
      in if List.member seen candidate
         then freshKeyAux fuel base seen (Suc i)
         else candidate)"

definition freshKey :: "String.literal \<Rightarrow> String.literal list \<Rightarrow> String.literal" where
  "freshKey base seen = freshKeyAux (Suc (length seen)) base seen 0"

fun disambiguateKeysAux ::
  "(String.literal \<times> 'a) list \<Rightarrow> String.literal list
    \<Rightarrow> (String.literal \<times> 'a) list \<Rightarrow> (String.literal \<times> 'a) list"
where
  "disambiguateKeysAux [] _ acc = rev acc"
| "disambiguateKeysAux ((base, v) # rest) seen acc =
     (let key = (if List.member seen base then freshKey base seen else base)
      in disambiguateKeysAux rest (key # seen) ((key, v) # acc))"

definition disambiguateKeys ::
  "(String.literal \<times> 'a) list \<Rightarrow> (String.literal \<times> 'a) list"
where
  "disambiguateKeys pairs = disambiguateKeysAux pairs [] []"

text \<open>Anonymous-invariant naming: produces \<open>anon_<i>\<close> for invariants
  without explicit names. Pure mirror of the Scala \<open>s"anon_$idx"\<close>.\<close>

definition anonInvariantName :: "nat \<Rightarrow> String.literal" where
  "anonInvariantName idx = STR ''anon_'' + showNat idx"

lemmas digitsRev_code [code] = digitsRev.simps
lemmas showNat_code [code] = showNat_def
lemmas freshKeyAux_code [code] = freshKeyAux.simps
lemmas freshKey_code [code] = freshKey_def
lemmas disambiguateKeysAux_code [code] = disambiguateKeysAux.simps
lemmas disambiguateKeys_code [code] = disambiguateKeys_def
lemmas anonInvariantName_code [code] = anonInvariantName_def
end
