theory IR_Lint
  imports IR
begin

text \<open>Phase 9\<delta> (lint TypeMismatch / L01): \<open>lit_class\<close> classifies an
  expression literal into a small ADT; \<open>litClass\<close> recognises which class
  (if any) an \<open>expr\<close> belongs to; \<open>binOpName\<close> renders a binary
  operator's user-facing name (used in diagnostic messages).
  \<open>describeLitClass\<close> renders the class as a noun for diagnostics.
  Replaces \<open>lint.TypeMismatch.LitClass\<close>, \<open>litClass\<close>, \<open>describe\<close>, and
  \<open>binOpName\<close> — the bulk of L01's pure analysis surface.\<close>

datatype (plugins only: code size) lit_class =
    LcNumeric | LcBool | LcStringLike | LcCollection | LcNone

fun litClass :: "expr \<Rightarrow> lit_class option" where
  "litClass (IntLitF _ _)     = Some LcNumeric"
| "litClass (FloatLitF _ _)   = Some LcNumeric"
| "litClass (BoolLitF _ _)    = Some LcBool"
| "litClass (StringLitF _ _)  = Some LcStringLike"
| "litClass (SetLiteralF _ _) = Some LcCollection"
| "litClass (MapLiteralF _ _) = Some LcCollection"
| "litClass (SeqLiteralF _ _) = Some LcCollection"
| "litClass (NoneLitF _)      = Some LcNone"
| "litClass _                 = None"

fun describeLitClass :: "lit_class \<Rightarrow> String.literal" where
  "describeLitClass LcNumeric    = STR ''numeric''"
| "describeLitClass LcBool       = STR ''boolean''"
| "describeLitClass LcStringLike = STR ''string''"
| "describeLitClass LcCollection = STR ''collection''"
| "describeLitClass LcNone       = STR ''none''"

fun binOpName :: "bin_op \<Rightarrow> String.literal" where
  "binOpName BAdd       = STR ''+''"
| "binOpName BSub       = STR ''-''"
| "binOpName BMul       = STR ''*''"
| "binOpName BDiv       = STR ''/''"
| "binOpName BLt        = STR ''<''"
| "binOpName BGt        = STR ''>''"
| "binOpName BLe        = STR ''<=''"
| "binOpName BGe        = STR ''>=''"
| "binOpName BAnd       = STR ''and''"
| "binOpName BOr        = STR ''or''"
| "binOpName BImplies   = STR ''implies''"
| "binOpName BIff       = STR ''iff''"
| "binOpName BIn        = STR ''in''"
| "binOpName BNotIn     = STR ''not in''"
| "binOpName BEq        = STR ''=''"
| "binOpName BNeq       = STR ''!=''"
| "binOpName BSubset    = STR ''subset''"
| "binOpName BUnion     = STR ''++''"
| "binOpName BIntersect = STR ''&''"
| "binOpName BDiff      = STR ''--''"

text \<open>Phase 9\<delta> (\<open>typeContainsNamed\<close>, \<open>exprContainsBoolLit\<close>): two
  structural predicates lifted from \<open>verify.alloy.Translator\<close>. The first
  asks whether a \<open>type_expr\<close> mentions a given named type anywhere
  in its structural unfolding (only descending into \<open>SetTypeF\<close> and
  \<open>OptionTypeF\<close>, matching the original walker's narrow scope). The
  second is a structural fold returning \<open>True\<close> iff a \<open>BoolLitF\<close> appears
  anywhere in the expression tree.\<close>

fun typeContainsNamed :: "String.literal \<Rightarrow> type_expr \<Rightarrow> bool" where
  "typeContainsNamed n (NamedTypeF m _)      = (n = m)"
| "typeContainsNamed n (SetTypeF inner _)    = typeContainsNamed n inner"
| "typeContainsNamed n (OptionTypeF inner _) = typeContainsNamed n inner"
| "typeContainsNamed _ _                     = False"

fun isBoolLit :: "expr \<Rightarrow> bool" where
  "isBoolLit (BoolLitF _ _) = True"
| "isBoolLit _              = False"

definition exprContainsBoolLit :: "expr \<Rightarrow> bool" where
  "exprContainsBoolLit e = list_ex isBoolLit (allSubexprs e)"

text \<open>Phase 9\<delta> (Narration conflict helpers): pure pattern matches lifted
  from \<open>verify.Narration\<close>. \<open>isComp\<close>/\<open>isLowBound\<close>/\<open>isStrictBound\<close> classify
  a \<open>bin_op\<close>; \<open>mirrorBinOp\<close> swaps a comparison's direction (for the
  \<open>IntLit cmp Identifier\<close> case); \<open>rangeOf\<close> extracts a
  \<open>(name, op, bound)\<close> triple from a comparison-against-literal shape;
  \<open>conflicts\<close> detects whether two bounds on the same identifier carve out
  disjoint ranges. Used by the contradictory-invariants diagnostic.\<close>

fun isComp :: "bin_op \<Rightarrow> bool" where
  "isComp BGe = True"
| "isComp BGt = True"
| "isComp BLe = True"
| "isComp BLt = True"
| "isComp _   = False"

fun isLowBound :: "bin_op \<Rightarrow> bool" where
  "isLowBound BGe = True"
| "isLowBound BGt = True"
| "isLowBound _   = False"

fun isStrictBound :: "bin_op \<Rightarrow> bool" where
  "isStrictBound BGt = True"
| "isStrictBound BLt = True"
| "isStrictBound _   = False"

fun mirrorBinOp :: "bin_op \<Rightarrow> bin_op" where
  "mirrorBinOp BGe    = BLe"
| "mirrorBinOp BLe    = BGe"
| "mirrorBinOp BGt    = BLt"
| "mirrorBinOp BLt    = BGt"
| "mirrorBinOp other  = other"

definition rangeOf :: "expr \<Rightarrow> (String.literal \<times> bin_op \<times> int) option" where
  "rangeOf e \<equiv>
     (case e of
        BinaryOpF op l r _ \<Rightarrow>
          (case (l, r) of
             (IdentifierF n _, IntLitF v _) \<Rightarrow>
               (if isComp op then Some (n, op, v) else None)
           | (IntLitF v _, IdentifierF n _) \<Rightarrow>
               (if isComp op then Some (n, mirrorBinOp op, v) else None)
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"

text \<open>Integer-discrete bound normalization: \<open>x > 3\<close> is satisfied by integers
  \<open>x \<ge> 4\<close>, and \<open>x < 4\<close> by \<open>x \<le> 3\<close>. Bumping strict bounds inward by 1 turns
  the disjointness check into a single \<open>low_eff > high_eff\<close> comparison and
  catches contradictions like \<open>x > 3 \<and> x < 4\<close> (no integer satisfies both)
  that a dense-order check would miss.\<close>

fun lowBoundEffective :: "bin_op \<Rightarrow> int \<Rightarrow> int" where
  "lowBoundEffective BGt n = n + 1"
| "lowBoundEffective _   n = n"

fun highBoundEffective :: "bin_op \<Rightarrow> int \<Rightarrow> int" where
  "highBoundEffective BLt n = n - 1"
| "highBoundEffective _   n = n"

fun conflicts :: "bin_op \<Rightarrow> int \<Rightarrow> bin_op \<Rightarrow> int \<Rightarrow> bool" where
  "conflicts aOp aB bOp bB =
     (if isLowBound aOp \<and> \<not> isLowBound bOp
      then lowBoundEffective aOp aB > highBoundEffective bOp bB
      else if \<not> isLowBound aOp \<and> isLowBound bOp
      then lowBoundEffective bOp bB > highBoundEffective aOp aB
      else False)"

text \<open>Phase 9\<delta> (lint TypeMismatch / L01 \<open>typeMismatchDiagnostics\<close>): full
  per-node classifier for type-mismatch diagnostics. Classify each
  \<open>expr\<close> independently into an optional \<open>type_mismatch_kind\<close>
  (\<open>typeMismatchAt\<close>); compose with the shared \<open>allSubexprs\<close> enumeration
  to collect all diagnostics for a top-level expression
  (\<open>typeMismatchDiagnostics\<close>). Reuses \<open>isComp\<close> (Narration helper above)
  for comparison classification. Message rendering stays Scala-side.\<close>

fun isArithBin :: "bin_op \<Rightarrow> bool" where
  "isArithBin BAdd = True"
| "isArithBin BSub = True"
| "isArithBin BMul = True"
| "isArithBin BDiv = True"
| "isArithBin _    = False"

fun isLogicalBin :: "bin_op \<Rightarrow> bool" where
  "isLogicalBin BAnd     = True"
| "isLogicalBin BOr      = True"
| "isLogicalBin BImplies = True"
| "isLogicalBin BIff     = True"
| "isLogicalBin _        = False"

fun isMembershipBin :: "bin_op \<Rightarrow> bool" where
  "isMembershipBin BIn    = True"
| "isMembershipBin BNotIn = True"
| "isMembershipBin _      = False"

datatype (plugins only: code size) type_mismatch_kind =
    TmUnaryNotOnNonBool lit_class
  | TmUnaryNegOnNonNumeric lit_class
  | TmArithLitMisuse bin_op lit_class
  | TmCompareLitMisuse bin_op lit_class
  | TmLogicalLitMisuse bin_op lit_class
  | TmMembershipLitMisuse bin_op lit_class

text \<open>\<open>(lc ++ rc).find(p)\<close> in Scala on \<open>Option[lit_class]\<close> pair flattens
  to a list (left-first) and finds the first matching class. In Isabelle:
  \<open>List.find p (List.map_filter id [lc, rc])\<close>.\<close>

definition typeMismatchAt ::
  "expr \<Rightarrow> (type_mismatch_kind \<times> span_t option) option" where
  "typeMismatchAt e \<equiv>
     (case e of
        UnaryOpF UNot inner sp \<Rightarrow>
          (case litClass inner of
             Some LcBool \<Rightarrow> None
           | Some c     \<Rightarrow> Some (TmUnaryNotOnNonBool c, sp)
           | None       \<Rightarrow> None)
      | UnaryOpF UNegate inner sp \<Rightarrow>
          (case litClass inner of
             Some LcNumeric \<Rightarrow> None
           | Some c        \<Rightarrow> Some (TmUnaryNegOnNonNumeric c, sp)
           | None          \<Rightarrow> None)
      | BinaryOpF op l r sp \<Rightarrow>
          (let cs = List.map_filter id [litClass l, litClass r] in
            if isArithBin op then
              map_option (\<lambda>c. (TmArithLitMisuse op c, sp))
                (List.find (\<lambda>c. c = LcBool \<or> c = LcNone) cs)
            else if isComp op then
              map_option (\<lambda>c. (TmCompareLitMisuse op c, sp))
                (List.find (\<lambda>c. c = LcBool \<or> c = LcNone) cs)
            else if isLogicalBin op then
              map_option (\<lambda>c. (TmLogicalLitMisuse op c, sp))
                (List.find (\<lambda>c. c \<noteq> LcBool) cs)
            else if isMembershipBin op then
              (case litClass r of
                 Some LcCollection \<Rightarrow> None
               | Some c             \<Rightarrow> Some (TmMembershipLitMisuse op c, sp)
               | None               \<Rightarrow> None)
            else None)
      | _ \<Rightarrow> None)"

definition typeMismatchDiagnostics ::
  "expr \<Rightarrow> (type_mismatch_kind \<times> span_t option) list" where
  "typeMismatchDiagnostics e = List.map_filter typeMismatchAt (allSubexprs e)"

lemmas isBoolLit_code [code]               = isBoolLit.simps
lemmas exprContainsBoolLit_code [code]     = exprContainsBoolLit_def
lemmas typeMismatchAt_code [code]          = typeMismatchAt_def
lemmas typeMismatchDiagnostics_code [code] = typeMismatchDiagnostics_def

end
