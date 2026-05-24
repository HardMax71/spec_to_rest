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

primrec inverse_op :: "migration_op \<Rightarrow> migration_op" where
  "inverse_op (CreateTable t)                       = DropTable t"
| "inverse_op (DropTable t)                         = CreateTable t"
| "inverse_op (AddColumn tn c)                      = DropColumn tn c"
| "inverse_op (DropColumn tn c)                     = AddColumn tn c"
| "inverse_op (AlterColumnType tn cn oldv newv)     = AlterColumnType tn cn newv oldv"
| "inverse_op (AlterColumnNullable tn cn oldv newv) = AlterColumnNullable tn cn newv oldv"
| "inverse_op (AlterColumnDefault tn cn oldv newv)  = AlterColumnDefault tn cn newv oldv"
| "inverse_op (AddCheck tn cn sql)                  = DropCheck tn cn sql"
| "inverse_op (DropCheck tn cn sql)                 = AddCheck tn cn sql"
| "inverse_op (AddForeignKey tn fk)                 = DropForeignKey tn fk"
| "inverse_op (DropForeignKey tn fk)                = AddForeignKey tn fk"
| "inverse_op (AddIndex tn ix)                      = DropIndex tn ix"
| "inverse_op (DropIndex tn ix)                     = AddIndex tn ix"
| "inverse_op (AddTrigger tg)                       = DropTrigger tg"
| "inverse_op (DropTrigger tg)                      = AddTrigger tg"

primrec is_destructive_op :: "migration_op \<Rightarrow> bool" where
  "is_destructive_op (CreateTable _)                = False"
| "is_destructive_op (DropTable _)                  = True"
| "is_destructive_op (AddColumn _ _)                = False"
| "is_destructive_op (DropColumn _ _)               = True"
| "is_destructive_op (AlterColumnType _ _ _ _)      = False"
| "is_destructive_op (AlterColumnNullable _ _ _ _)  = False"
| "is_destructive_op (AlterColumnDefault _ _ _ _)   = False"
| "is_destructive_op (AddCheck _ _ _)               = False"
| "is_destructive_op (DropCheck _ _ _)              = False"
| "is_destructive_op (AddForeignKey _ _)            = False"
| "is_destructive_op (DropForeignKey _ _)           = False"
| "is_destructive_op (AddIndex _ _)                 = False"
| "is_destructive_op (DropIndex _ _)                = False"
| "is_destructive_op (AddTrigger _)                 = False"
| "is_destructive_op (DropTrigger _)                = False"

theorem inverse_op_involution: "inverse_op (inverse_op op) = op"
  by (cases op; simp)

end
