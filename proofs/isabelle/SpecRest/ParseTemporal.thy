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

lemmas parseTemporalBody_code [code] = parseTemporalBody_def
lemmas temporalArg_code [code] = temporalArg.simps

end
