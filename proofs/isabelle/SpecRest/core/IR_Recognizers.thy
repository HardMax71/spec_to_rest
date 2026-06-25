theory IR_Recognizers
  imports IR
begin

text \<open>Phase 9\<alpha> (small recognizers): \<open>isTrueLit\<close> matches the literal
  \<open>true\<close>; \<open>enumLiteralOf\<close> recognises an enum-member reference (via
  \<open>EnumAccessF\<close> or a bare \<open>IdentifierF\<close>). Replaces four \<open>case BoolLitF
  True _ \<Rightarrow> True\<close> copies in lint / testgen passes and three near-identical
  \<open>enumLiteralFor\<close> walkers in \<open>testgen.Behavioral\<close> /
  \<open>testgen.Stateful\<close>.\<close>

fun isTrueLit :: "expr \<Rightarrow> bool" where
  "isTrueLit (BoolLitF True _) = True"
| "isTrueLit _                 = False"

fun enumLiteralOf :: "expr \<Rightarrow> String.literal list \<Rightarrow> String.literal option" where
  "enumLiteralOf (EnumAccessF _ m _) ms = (if string_in_list m ms then Some m else None)"
| "enumLiteralOf (IdentifierF n _)   ms = (if string_in_list n ms then Some n else None)"
| "enumLiteralOf _                   _  = None"

text \<open>Phase 9\<alpha> (\<open>combineAnd\<close>): folds an \<open>expr list\<close> into a single
  left-associated AND-chain, with \<open>BoolLitF True None\<close> as the unit.
  Inverse of \<open>flattenAndAll\<close> modulo \<open>true\<close> identity. Replaces
  \<open>verify.Narration.combineConjuncts\<close> (which used \<open>foldLeft\<close> —
  left-associativity preserves byte-identical pretty-printed output). Uses
  a monomorphic accumulator to make the extracted Scala tail-recursive.\<close>

fun combineAnd_acc :: "expr \<Rightarrow> expr list \<Rightarrow> expr" where
  "combineAnd_acc acc []         = acc"
| "combineAnd_acc acc (x # rest) = combineAnd_acc (BinaryOpF BAnd acc x None) rest"

fun combineAnd :: "expr list \<Rightarrow> expr" where
  "combineAnd []          = BoolLitF True None"
| "combineAnd (x # rest)  = combineAnd_acc x rest"

text \<open>Phase 9\<beta> (\<open>decomposeAtom\<close>): canonical recognizer for a single
  atomic refinement constraint over \<open>value\<close>. Three consumers re-implement
  the recognizer (OpenAPI JSON-Schema, SQL CHECK, Hypothesis strategy
  synthesis) — this lifts the analysis half so each consumer becomes a
  pure renderer over \<open>refinement_atom\<close>. Compose with the existing
  \<open>flattenAnd\<close> to traverse \<open>BAnd\<close>-chains.\<close>

datatype (plugins only: code size) refinement_atom =
    RaLenCmp bin_op int
  | RaValueCmp bin_op int
  | RaMatches "String.literal"
  | RaMatchesIdent "String.literal" "String.literal"
  | RaPredCall "String.literal"
  | RaUnknown expr

fun isValueRef :: "expr \<Rightarrow> bool" where
  "isValueRef (IdentifierF n _) = (n = STR ''value'')"
| "isValueRef _                  = False"

fun isLenOfValue :: "expr \<Rightarrow> bool" where
  "isLenOfValue (CallF c args _) =
     ((case c of IdentifierF n _ \<Rightarrow> n = STR ''len'' | _ \<Rightarrow> False)
      \<and> (case args of [arg] \<Rightarrow> isValueRef arg | _ \<Rightarrow> False))"
| "isLenOfValue _ = False"

fun isRefinementCmp :: "bin_op \<Rightarrow> bool" where
  "isRefinementCmp BGe  = True"
| "isRefinementCmp BGt  = True"
| "isRefinementCmp BLe  = True"
| "isRefinementCmp BLt  = True"
| "isRefinementCmp BEq  = True"
| "isRefinementCmp BNeq = True"
| "isRefinementCmp _    = False"

definition decomposeAtom :: "expr \<Rightarrow> refinement_atom" where
  "decomposeAtom e \<equiv>
     (case e of
        MatchesF inner pat _ \<Rightarrow>
          (case inner of
             IdentifierF n _ \<Rightarrow>
               (if n = STR ''value'' then RaMatches pat else RaMatchesIdent n pat)
           | _ \<Rightarrow> RaUnknown e)
      | BinaryOpF op l rhs sp \<Rightarrow>
          (if \<not> isRefinementCmp op then RaUnknown e
           else (case rhs of
                   IntLitF n _ \<Rightarrow>
                     (if isLenOfValue l then RaLenCmp op n
                      else if isValueRef l then RaValueCmp op n
                      else RaUnknown e)
                 | _ \<Rightarrow> RaUnknown e))
      | CallF f args sp \<Rightarrow>
          (case (f, args) of
             (IdentifierF p _, [arg]) \<Rightarrow>
               (if isValueRef arg then RaPredCall p else RaUnknown e)
           | _ \<Rightarrow> RaUnknown e)
      | _ \<Rightarrow> RaUnknown e)"

text \<open>Phase 9\<epsilon> (small recognizers, scattered consumers):
  \<open>negate\<close> — partial logical negation of comparison-shaped exprs (used
  by testgen guard satisfier); \<open>isLenOrCardOf\<close> — extracts the bare
  identifier inside \<open>len(x)\<close> or \<open>|x|\<close>; \<open>isLiteral\<close> — narrow
  literal recognizer (Int/Float/String only, distinct from \<open>isLitFull\<close>
  which also accepts Bool/None); \<open>extractFieldName\<close> — recognizer for
  \<open>self.field\<close> or bare \<open>field\<close> (SQL CHECK emission);
  \<open>enumLitName\<close> — bare name extractor for EnumAccess/Identifier
  (distinct from \<open>enumLiteralOf\<close> which filters by enum-values list);
  \<open>isMapType\<close>, \<open>isEntityType\<close>, \<open>sameNamedType\<close> — trivial
  type-shape predicates.\<close>

definition negate :: "expr \<Rightarrow> expr option" where
  "negate e \<equiv>
     (case e of
        UnaryOpF UNot inner _    \<Rightarrow> Some inner
      | BinaryOpF op l r sp \<Rightarrow>
          (case op of
             BGt  \<Rightarrow> Some (BinaryOpF BLe  l r sp)
           | BGe  \<Rightarrow> Some (BinaryOpF BLt  l r sp)
           | BLt  \<Rightarrow> Some (BinaryOpF BGe  l r sp)
           | BLe  \<Rightarrow> Some (BinaryOpF BGt  l r sp)
           | BEq  \<Rightarrow> Some (BinaryOpF BNeq l r sp)
           | BNeq \<Rightarrow> Some (BinaryOpF BEq  l r sp)
           | _    \<Rightarrow> None)
      | _ \<Rightarrow> None)"

definition isLenOrCardOf :: "expr \<Rightarrow> String.literal option" where
  "isLenOrCardOf e \<equiv>
     (case e of
        UnaryOpF UCardinality (IdentifierF n _) _ \<Rightarrow> Some n
      | CallF (IdentifierF f _) [IdentifierF n _] _ \<Rightarrow>
          (if f = STR ''len'' then Some n else None)
      | _ \<Rightarrow> None)"

fun isLiteral :: "expr \<Rightarrow> bool" where
  "isLiteral (IntLitF _ _)    = True"
| "isLiteral (FloatLitF _ _)  = True"
| "isLiteral (StringLitF _ _) = True"
| "isLiteral _                = False"

fun extractFieldName :: "expr \<Rightarrow> String.literal option" where
  "extractFieldName (FieldAccessF b name _) =
     (case b of
        IdentifierF s _ \<Rightarrow> (if s = STR ''self'' then Some name else None)
      | _ \<Rightarrow> None)"
| "extractFieldName (IdentifierF name _) = Some name"
| "extractFieldName _ = None"

fun enumLitName :: "expr \<Rightarrow> String.literal option" where
  "enumLitName (EnumAccessF _ m _) = Some m"
| "enumLitName (IdentifierF n _)   = Some n"
| "enumLitName _                   = None"

fun isMapType :: "type_expr \<Rightarrow> bool" where
  "isMapType (MapTypeF _ _ _) = True"
| "isMapType _                = False"

fun isEntityType :: "type_expr \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isEntityType (NamedTypeF n _) name = (n = name)"
| "isEntityType _ _                   = False"

fun sameNamedType :: "type_expr \<Rightarrow> type_expr \<Rightarrow> bool" where
  "sameNamedType (NamedTypeF a _) (NamedTypeF b _) = (a = b)"
| "sameNamedType _ _                                = False"

text \<open>Phase 9\<zeta> (semantic classifiers from \<open>convention.Classify\<close>):
  \<open>isLeafValue\<close> — literals + bare identifier + enum access (the closed
  set of expr forms that read no state and call no function);
  \<open>isPureRead\<close> — recursive: identifier / literal / enum / \<open>pre(...)\<close> /
  field-access / index where every subterm is itself pure-read;
  \<open>isCardinalityRhs\<close> — \<open>|x|\<close> / \<open>|pre(x)|\<close> / arithmetic-on-them shape
  used by the convention classifier's cardinality-frame inference;
  \<open>relationTargetsEntity\<close> — type predicate for \<open>Relation(_, _, NamedType
  e)\<close> or bare \<open>NamedType e\<close> (lifted from \<open>testgen.Behavioral\<close>);
  \<open>extractKeySet\<close> / \<open>extractMapEntries\<close> — set / map literal extractors
  used by the Z3 frame translator.\<close>

fun isLeafValue :: "expr \<Rightarrow> bool" where
  "isLeafValue (IntLitF _ _)      = True"
| "isLeafValue (FloatLitF _ _)    = True"
| "isLeafValue (StringLitF _ _)   = True"
| "isLeafValue (BoolLitF _ _)     = True"
| "isLeafValue (NoneLitF _)       = True"
| "isLeafValue (IdentifierF _ _)  = True"
| "isLeafValue (EnumAccessF _ _ _) = True"
| "isLeafValue _                  = False"

fun (sequential) isPureRead :: "expr \<Rightarrow> bool" where
  "isPureRead (PreF inner _)          = isPureRead inner"
| "isPureRead (IndexF base idx _)     = (isPureRead base \<and> isPureRead idx)"
| "isPureRead (FieldAccessF base _ _) = isPureRead base"
| "isPureRead e                       = isLeafValue e"

fun relationTargetsEntity :: "type_expr \<Rightarrow> String.literal \<Rightarrow> bool" where
  "relationTargetsEntity (RelationTypeF _ _ (NamedTypeF n _) _) entity = (n = entity)"
| "relationTargetsEntity (NamedTypeF n _) entity                       = (n = entity)"
| "relationTargetsEntity _ _                                            = False"

fun extractKeySetEntries :: "map_entry list \<Rightarrow> expr list" where
  "extractKeySetEntries []                          = []"
| "extractKeySetEntries (MapEntryFull k _ _ # rest) = k # extractKeySetEntries rest"

fun extractKeySet :: "expr \<Rightarrow> expr list option" where
  "extractKeySet (SetLiteralF elements _) = Some elements"
| "extractKeySet (MapLiteralF entries _)  = Some (extractKeySetEntries entries)"
| "extractKeySet _                        = None"

fun extractMapEntriesPairs :: "map_entry list \<Rightarrow> (expr \<times> expr) list" where
  "extractMapEntriesPairs []                          = []"
| "extractMapEntriesPairs (MapEntryFull k v _ # rest) = (k, v) # extractMapEntriesPairs rest"

fun extractMapEntries :: "expr \<Rightarrow> (expr \<times> expr) list option" where
  "extractMapEntries (MapLiteralF entries _) = Some (extractMapEntriesPairs entries)"
| "extractMapEntries _                       = None"

text \<open>Phase 9\<eta> (key-existence recognizer): \<open>isKeyExistsConj\<close> —
  shallow split-case formulation: separate \<open>case\<close> on the binary
  operator, on the left operand, and on the right operand, conjoined
  via \<open>\<and>\<close>. The naive deep-nested pattern
  \<open>BinaryOpF BIn (IdentifierF i _) (IdentifierF s _) _\<close> extracts to
  a 200+-case cross-product match in Scala (op-vs-left-shape-vs-right-shape);
  the split form generates three independent ~28-arm matches that
  short-circuit on the first failure.

  (\<open>isCardinalityRhs\<close> — the recursive cardinality-frame recognizer —
  was attempted in this phase but reverted: the \<open>stripAddSubIntLit\<close>
  helper extracts to a per-constructor identity fallback that bloats
  the generated Scala unacceptably. Stays Scala-local in
  \<open>convention.Classify\<close>.)\<close>

definition isKeyExistsConj ::
  "expr \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> bool" where
  "isKeyExistsConj c inputName stateName \<equiv>
     (case c of
        BinaryOpF op l r _ \<Rightarrow>
          (case op of BIn \<Rightarrow> True | _ \<Rightarrow> False) \<and>
          (case l of IdentifierF i _ \<Rightarrow> i = inputName | _ \<Rightarrow> False) \<and>
          (case r of IdentifierF s _ \<Rightarrow> s = stateName | _ \<Rightarrow> False)
      | _ \<Rightarrow> False)"

text \<open>Phase 9\<iota> (\<open>keyExistencePair\<close>): extractor variant of
  \<open>isKeyExistsConj\<close> — returns the \<open>(input, state)\<close> identifier pair
  when the expression has shape \<open>input \<in> state\<close>, where both sides are
  bare identifiers. The set-membership filter (\<open>input \<in> known inputs\<close>,
  \<open>state \<in> known state fields\<close>) stays on the call site. Lifted from
  \<open>testgen.Behavioral.keyExistencePattern\<close>.

  Defined as a \<open>fun\<close> with a single specific equation + wildcard
  fallback so extraction stays compact — the deeply-nested pattern
  match (\<open>BinaryOpF BIn (IdentifierF _) (IdentifierF _)\<close>) extracts to
  one specific arm plus a single wildcard, not a 100-arm cross product.\<close>

fun keyExistencePair ::
  "expr \<Rightarrow> (String.literal \<times> String.literal) option" where
  "keyExistencePair e =
     (case e of
        BinaryOpF op lhs rhs _ \<Rightarrow>
          (case op of
             BIn \<Rightarrow>
               (case lhs of
                  IdentifierF inName _ \<Rightarrow>
                    (case rhs of
                       IdentifierF stName _ \<Rightarrow> Some (inName, stName)
                     | _ \<Rightarrow> None)
                | _ \<Rightarrow> None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

text \<open>\<open>fieldNameIfStateIndex\<close>: recognises an expression of shape
  \<open>state[input].field\<close> for given \<open>state\<close> and \<open>input\<close> names and
  returns the field name. Used by \<open>testgen.Stateful.fieldRestrictionConjunct\<close>
  to extract field-level enum restrictions from \<open>requires\<close> clauses.

  Defined via shallow nested \<open>case\<close>s (same shape as \<open>isKeyExistsConj\<close>)
  so each level extracts as a small per-constructor match, avoiding the
  cross-product blowup that the equivalent deep \<open>fun\<close> pattern
  \<open>FieldAccessF (IndexF (IdentifierF _) (IdentifierF _) _) _ _\<close> would
  trigger.\<close>

definition fieldNameIfStateIndex ::
  "expr \<Rightarrow> String.literal \<Rightarrow> String.literal \<Rightarrow> String.literal option" where
  "fieldNameIfStateIndex e inputName stateName \<equiv>
     (case e of
        FieldAccessF base fname _ \<Rightarrow>
          (case base of
             IndexF idx0 idx1 _ \<Rightarrow>
               (case idx0 of
                  IdentifierF s _ \<Rightarrow>
                    (case idx1 of
                       IdentifierF i _ \<Rightarrow>
                         (if s = stateName \<and> i = inputName then Some fname else None)
                     | _ \<Rightarrow> None)
                | _ \<Rightarrow> None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

text \<open>Phase 9\<iota> (\<open>desiredSize\<close>): given a size-comparison binop and a
  bound, returns the smallest non-negative collection size that
  satisfies the constraint. All Gt/Ge/Eq/Lt/Le branches clamp at zero
  (sizes are never negative); \<open>BEq n\<close> with \<open>n < 0\<close> is infeasible
  (\<open>None\<close>) since no non-negative size can equal it; same for
  \<open>BLt 0\<close>/\<open>BLe (-1)\<close>. Lifted from \<open>testgen.Behavioral.desiredSize\<close>,
  fixing the original's negative-result edge case (\<open>BGt (-5)\<close> used to
  return \<open>-4\<close>; now returns \<open>0\<close>).\<close>

fun desiredSize :: "bin_op \<Rightarrow> int \<Rightarrow> int option" where
  "desiredSize BGt n = Some (max 0 (n + 1))"
| "desiredSize BGe n = Some (max 0 n)"
| "desiredSize BEq n = (if n \<ge> 0 then Some n else None)"
| "desiredSize BLt n = (if 0 < n then Some 0 else None)"
| "desiredSize BLe n = (if 0 \<le> n then Some 0 else None)"
| "desiredSize _   _ = None"

text \<open>Phase 9\<theta> (\<open>matchesIdentityShape\<close>): recognises an expression of
  shape \<open>matches(IdentifierF n _, pattern)\<close> where \<open>n\<close> equals the given
  parameter name. Lifted from \<open>testgen.Strategies.inlineMatchesPredicate\<close>
  — used to detect single-arg predicate bodies that delegate entirely to
  a regex match (so a strategy filter can inline the pattern directly).\<close>

fun matchesIdentityShape ::
  "expr \<Rightarrow> String.literal \<Rightarrow> String.literal option" where
  "matchesIdentityShape (MatchesF x pattern _) name =
     (case x of
        IdentifierF p _ \<Rightarrow> (if p = name then Some pattern else None)
      | _ \<Rightarrow> None)"
| "matchesIdentityShape _ _ = None"

end
