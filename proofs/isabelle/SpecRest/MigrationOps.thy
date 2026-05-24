theory MigrationOps
  imports Schema
begin

text \<open>Schema-migration operation ADT. Constructor names match the desired Scala
  hand-written case names; the type name \<open>migration_op\<close> is promoted to PascalCase
  via \<open>code_identifier\<close> in \<open>Codegen\<close>. The Scala layer drops its
  hand-written enum and uses these constructors directly.\<close>

datatype migration_op =
    CreateTableOp table_spec
  | DropTableOp table_spec
  | AddColumnOp String.literal column_spec
  | DropColumnOp String.literal column_spec
  | AlterColumnTypeOp String.literal String.literal String.literal String.literal
      \<comment> \<open>table, name, old_sql_type, new_sql_type\<close>
  | AlterColumnNullableOp String.literal String.literal bool bool
      \<comment> \<open>table, name, old_nullable, new_nullable\<close>
  | AlterColumnDefaultOp String.literal String.literal
      "String.literal option" "String.literal option"
      \<comment> \<open>table, name, old_default, new_default\<close>
  | AddCheckOp String.literal String.literal String.literal
      \<comment> \<open>table, name, sql\<close>
  | DropCheckOp String.literal String.literal String.literal
      \<comment> \<open>table, name, old_sql\<close>
  | AddForeignKeyOp String.literal foreign_key_spec
  | DropForeignKeyOp String.literal foreign_key_spec
  | AddIndexOp String.literal index_spec
  | DropIndexOp String.literal index_spec
  | AddTriggerOp trigger_spec
  | DropTriggerOp trigger_spec

primrec inverse_op :: "migration_op \<Rightarrow> migration_op" where
  "inverse_op (CreateTableOp t)                       = DropTableOp t"
| "inverse_op (DropTableOp t)                         = CreateTableOp t"
| "inverse_op (AddColumnOp tn c)                      = DropColumnOp tn c"
| "inverse_op (DropColumnOp tn c)                     = AddColumnOp tn c"
| "inverse_op (AlterColumnTypeOp tn cn oldv newv)     = AlterColumnTypeOp tn cn newv oldv"
| "inverse_op (AlterColumnNullableOp tn cn oldv newv) = AlterColumnNullableOp tn cn newv oldv"
| "inverse_op (AlterColumnDefaultOp tn cn oldv newv)  = AlterColumnDefaultOp tn cn newv oldv"
| "inverse_op (AddCheckOp tn cn sql)                  = DropCheckOp tn cn sql"
| "inverse_op (DropCheckOp tn cn sql)                 = AddCheckOp tn cn sql"
| "inverse_op (AddForeignKeyOp tn fk)                 = DropForeignKeyOp tn fk"
| "inverse_op (DropForeignKeyOp tn fk)                = AddForeignKeyOp tn fk"
| "inverse_op (AddIndexOp tn ix)                      = DropIndexOp tn ix"
| "inverse_op (DropIndexOp tn ix)                     = AddIndexOp tn ix"
| "inverse_op (AddTriggerOp tg)                       = DropTriggerOp tg"
| "inverse_op (DropTriggerOp tg)                      = AddTriggerOp tg"

primrec is_destructive_op :: "migration_op \<Rightarrow> bool" where
  "is_destructive_op (CreateTableOp _)                = False"
| "is_destructive_op (DropTableOp _)                  = True"
| "is_destructive_op (AddColumnOp _ _)                = False"
| "is_destructive_op (DropColumnOp _ _)               = True"
| "is_destructive_op (AlterColumnTypeOp _ _ _ _)      = False"
| "is_destructive_op (AlterColumnNullableOp _ _ _ _)  = False"
| "is_destructive_op (AlterColumnDefaultOp _ _ _ _)   = False"
| "is_destructive_op (AddCheckOp _ _ _)               = False"
| "is_destructive_op (DropCheckOp _ _ _)              = False"
| "is_destructive_op (AddForeignKeyOp _ _)            = False"
| "is_destructive_op (DropForeignKeyOp _ _)           = False"
| "is_destructive_op (AddIndexOp _ _)                 = False"
| "is_destructive_op (DropIndexOp _ _)                = False"
| "is_destructive_op (AddTriggerOp _)                 = False"
| "is_destructive_op (DropTriggerOp _)                = False"

theorem inverse_op_involution: "inverse_op (inverse_op op) = op"
  by (cases op; simp)

end
