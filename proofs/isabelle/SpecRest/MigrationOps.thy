theory MigrationOps
  imports Schema
begin

text \<open>Schema-migration operation ADT. Constructor names match the desired Scala
  hand-written case names; the type name \<open>migration_op\<close> is promoted to PascalCase
  via \<open>code_identifier\<close> in \<open>Codegen\<close>. The Scala layer drops its
  hand-written enum and uses these constructors directly.\<close>

datatype migration_op =
    CreateTable table_spec
  | DropTable table_spec
  | AddColumn String.literal column_spec
  | DropColumn String.literal column_spec
  | AlterColumnType String.literal String.literal String.literal String.literal
      \<comment> \<open>table, name, old_sql_type, new_sql_type\<close>
  | AlterColumnNullable String.literal String.literal bool bool
      \<comment> \<open>table, name, old_nullable, new_nullable\<close>
  | AlterColumnDefault String.literal String.literal
      "String.literal option" "String.literal option"
      \<comment> \<open>table, name, old_default, new_default\<close>
  | AddCheck String.literal String.literal String.literal
      \<comment> \<open>table, name, sql\<close>
  | DropCheck String.literal String.literal String.literal
      \<comment> \<open>table, name, old_sql\<close>
  | AddForeignKey String.literal foreign_key_spec
  | DropForeignKey String.literal foreign_key_spec
  | AddIndex String.literal index_spec
  | DropIndex String.literal index_spec
  | AddTrigger trigger_spec
  | DropTrigger trigger_spec

primrec inverseOp :: "migration_op \<Rightarrow> migration_op" where
  "inverseOp (CreateTable t)                       = DropTable t"
| "inverseOp (DropTable t)                         = CreateTable t"
| "inverseOp (AddColumn tn c)                      = DropColumn tn c"
| "inverseOp (DropColumn tn c)                     = AddColumn tn c"
| "inverseOp (AlterColumnType tn cn oldv newv)     = AlterColumnType tn cn newv oldv"
| "inverseOp (AlterColumnNullable tn cn oldv newv) = AlterColumnNullable tn cn newv oldv"
| "inverseOp (AlterColumnDefault tn cn oldv newv)  = AlterColumnDefault tn cn newv oldv"
| "inverseOp (AddCheck tn cn sql)                  = DropCheck tn cn sql"
| "inverseOp (DropCheck tn cn sql)                 = AddCheck tn cn sql"
| "inverseOp (AddForeignKey tn fk)                 = DropForeignKey tn fk"
| "inverseOp (DropForeignKey tn fk)                = AddForeignKey tn fk"
| "inverseOp (AddIndex tn ix)                      = DropIndex tn ix"
| "inverseOp (DropIndex tn ix)                     = AddIndex tn ix"
| "inverseOp (AddTrigger tg)                       = DropTrigger tg"
| "inverseOp (DropTrigger tg)                      = AddTrigger tg"

primrec isDestructiveOp :: "migration_op \<Rightarrow> bool" where
  "isDestructiveOp (CreateTable _)                = False"
| "isDestructiveOp (DropTable _)                  = True"
| "isDestructiveOp (AddColumn _ _)                = False"
| "isDestructiveOp (DropColumn _ _)               = True"
| "isDestructiveOp (AlterColumnType _ _ _ _)      = False"
| "isDestructiveOp (AlterColumnNullable _ _ _ _)  = False"
| "isDestructiveOp (AlterColumnDefault _ _ _ _)   = False"
| "isDestructiveOp (AddCheck _ _ _)               = False"
| "isDestructiveOp (DropCheck _ _ _)              = False"
| "isDestructiveOp (AddForeignKey _ _)            = False"
| "isDestructiveOp (DropForeignKey _ _)           = False"
| "isDestructiveOp (AddIndex _ _)                 = False"
| "isDestructiveOp (DropIndex _ _)                = False"
| "isDestructiveOp (AddTrigger _)                 = False"
| "isDestructiveOp (DropTrigger _)                = False"

theorem inverseOpInvolution: "inverseOp (inverseOp op) = op"
  by (cases op; simp)

text \<open>The list-level involution falls out of the single-op theorem by induction.
  Any caller that double-inverts a sequence of migration ops gets the original
  sequence back — used as the basis for the up\<rightarrow>down\<rightarrow>up round-trip story.\<close>

lemma inverseOpInvolutionList:
  "map inverseOp (map inverseOp ops) = ops"
  by (induction ops; simp add: inverseOpInvolution)

lemma inverseOpInj: "inj inverseOp"
proof
  fix x y :: migration_op
  assume "inverseOp x = inverseOp y"
  hence "inverseOp (inverseOp x) = inverseOp (inverseOp y)" by simp
  thus "x = y" by (simp add: inverseOpInvolution)
qed

text \<open>Reversal-plus-inversion is the canonical \<open>down\<close>-list operation that any
  migration tool needs: to roll back an applied diff, reverse the order and
  invert each op. Doing it twice is identity.\<close>

definition downList :: "migration_op list \<Rightarrow> migration_op list" where
  "downList ops = rev (map inverseOp ops)"

lemma downListInvolution: "downList (downList ops) = ops"
  unfolding downList_def
  by (simp add: rev_map o_def inverseOpInvolution)

lemmas downListCode [code] = downList_def

end
