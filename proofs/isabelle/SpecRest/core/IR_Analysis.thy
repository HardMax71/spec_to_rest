theory IR_Analysis
  imports IR_Recognizers IR_Lint IR_FreeVars
begin

text \<open>Phase 9 analysis surface, re-exported as one theory so consumers keep a
  single \<open>imports SpecRest_IR.IR_Analysis\<close>. The definitions live in three
  cohesive parents: \<open>IR_Recognizers\<close> (pure structural pattern matchers),
  \<open>IR_Lint\<close> (type-mismatch / contradictory-bound diagnostics), and
  \<open>IR_FreeVars\<close> (free variables, capture-aware substitution, call inlining).\<close>

end
