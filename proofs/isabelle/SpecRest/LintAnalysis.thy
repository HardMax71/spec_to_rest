theory LintAnalysis
  imports IR
begin

text \<open>Pure decision predicates for the lint analyses in
  \<open>modules/lint/\<close>. Scala iterates the IR slots, calls the lifted
  predicate per declaration, and emits formatted diagnostics
  Scala-side — same boundary established by \<open>validateIrContextRule\<close>
  in \<open>ValidateConventions\<close>.

  Starting tiny: \<open>operationMissingEnsures\<close> for \<open>L03\<close>. Subsequent
  lint passes (\<open>UnusedEntity\<close> / \<open>UndefinedRef\<close> / \<open>TypeMismatch\<close>)
  need IR walkers that respect or ignore binders and will land in
  follow-up PRs.\<close>

fun operationMissingEnsures :: "operation_decl_full \<Rightarrow> bool" where
  "operationMissingEnsures (OperationDeclFull _ _ outputs _ ensures _) =
     (outputs \<noteq> [] \<and> ensures = [])"

lemmas operationMissingEnsures_code [code] = operationMissingEnsures.simps

end
