theory Semantics
  imports Semantics_Eval Semantics_Typing
begin

text \<open>Reference value semantics, re-exported as one theory so consumers keep a
  single \<open>imports SpecRest_Semantics.Semantics\<close>. The definitions live in two
  cohesive parents: \<open>Semantics_Eval\<close> (the \<open>ir_value\<close> ADT and the
  \<open>eval_arith\<close> / \<open>eval_cmp\<close> evaluation primitives) and \<open>Semantics_Typing\<close>
  (the \<open>ty\<close> value-typing relation and the \<open>agrees\<close> typing-context invariants).\<close>

end
