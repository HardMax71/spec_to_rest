theory ParseTemporal
  imports IR
begin

text \<open>Parse-don't-validate dispatcher for temporal declarations. Mirrors
  the convention machinery in \<open>ValidateConventions\<close>: the parser calls
  \<open>parseTemporalBody\<close> at IR construction with the raw expression and
  gets back a typed \<open>temporal_body\<close>. The three recognised call shapes
  (\<open>always(P)\<close>, \<open>eventually(P)\<close>, \<open>fairness(op)\<close>) yield the
  corresponding constructor; everything else flows into \<open>TbInvalid\<close>
  carrying the raw expression for diagnostics + faithful round-trip.\<close>

definition parseTemporalBody :: "expr_full \<Rightarrow> temporal_body" where
  "parseTemporalBody e = (case e of
       CallF (IdentifierF name _) [arg] _ \<Rightarrow>
         (if name = STR ''always''      then TbAlways arg
          else if name = STR ''eventually'' then TbEventually arg
          else if name = STR ''fairness''   then TbFairness arg
          else TbInvalid e)
     | _ \<Rightarrow> TbInvalid e)"

text \<open>Accessor for the underlying expression. Wellformed bodies expose
  their predicate argument; \<open>TbInvalid\<close> falls back to the raw
  expression. Used by IR-walking visitors (lint, dafny, typecheck)
  that don't care about the keyword wrapper.\<close>

fun temporalArg :: "temporal_body \<Rightarrow> expr_full" where
  "temporalArg (TbAlways e) = e"
| "temporalArg (TbEventually e) = e"
| "temporalArg (TbFairness e) = e"
| "temporalArg (TbInvalid e) = e"

text \<open>Inverse direction: re-synthesise the original \<open>expr_full\<close> shape
  from a typed body. Used by Scala's \<open>Serialize\<close> to round-trip
  \<open>temporal_decl_full\<close> through JSON without expanding the wire format.
  The synthesised \<open>CallF\<close>s carry no spans — span information is
  preserved on the outer \<open>TemporalDeclFull\<close> wrapper, not on the
  re-emitted call wrapper.\<close>

definition synthTemporalExpr :: "temporal_body \<Rightarrow> expr_full" where
  "synthTemporalExpr b = (case b of
       TbAlways arg     \<Rightarrow> CallF (IdentifierF (STR ''always'') None) [arg] None
     | TbEventually arg \<Rightarrow> CallF (IdentifierF (STR ''eventually'') None) [arg] None
     | TbFairness arg   \<Rightarrow> CallF (IdentifierF (STR ''fairness'') None) [arg] None
     | TbInvalid raw    \<Rightarrow> raw)"

text \<open>Round-trip laws. For the three wellformed body shapes, the
  synthesiser and parser are inverses by construction. The
  \<open>TbInvalid\<close> case requires the invariant that the carried raw
  expression doesn't match a wellformed shape — captured by the
  conditional lemma below — and the unconditional idempotence
  corollary follows from case analysis on \<open>parseTemporalBody e\<close>.

  These prove that the Scala wire format chosen in PR #330
  (encode \<open>synthTemporalExpr b\<close>, decode with \<open>parseTemporalBody\<close>)
  is sound: every typed body that originally came from the parser
  round-trips faithfully.\<close>

lemma parseTemporalBody_synth_always:
  "parseTemporalBody (synthTemporalExpr (TbAlways e)) = TbAlways e"
  by (simp add: synthTemporalExpr_def parseTemporalBody_def)

lemma parseTemporalBody_synth_eventually:
  "parseTemporalBody (synthTemporalExpr (TbEventually e)) = TbEventually e"
  by (simp add: synthTemporalExpr_def parseTemporalBody_def)

lemma parseTemporalBody_synth_fairness:
  "parseTemporalBody (synthTemporalExpr (TbFairness e)) = TbFairness e"
  by (simp add: synthTemporalExpr_def parseTemporalBody_def)

lemma parseTemporalBody_TbInvalid_raw_eq:
  "parseTemporalBody e = TbInvalid raw \<Longrightarrow> raw = e"
  by (auto simp: parseTemporalBody_def
           split: expr_full.splits list.splits if_splits)

lemma parseTemporalBody_synth_invalid:
  assumes "parseTemporalBody e = TbInvalid e"
  shows   "parseTemporalBody (synthTemporalExpr (TbInvalid e)) = TbInvalid e"
  using assms by (simp add: synthTemporalExpr_def)

theorem parseTemporalBody_idempotent:
  "parseTemporalBody (synthTemporalExpr (parseTemporalBody e)) = parseTemporalBody e"
proof (cases "parseTemporalBody e")
  case (TbAlways arg)
  then show ?thesis using parseTemporalBody_synth_always by simp
next
  case (TbEventually arg)
  then show ?thesis using parseTemporalBody_synth_eventually by simp
next
  case (TbFairness arg)
  then show ?thesis using parseTemporalBody_synth_fairness by simp
next
  case (TbInvalid raw)
  hence "raw = e" using parseTemporalBody_TbInvalid_raw_eq by blast
  thus ?thesis using TbInvalid by (simp add: synthTemporalExpr_def)
qed

lemmas parseTemporalBody_code [code] = parseTemporalBody_def
lemmas temporalArg_code [code] = temporalArg.simps
lemmas synthTemporalExpr_code [code] = synthTemporalExpr_def

end
