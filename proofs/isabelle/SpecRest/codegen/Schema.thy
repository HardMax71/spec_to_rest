theory Schema
  imports Main "HOL-Library.Code_Target_Numeral"
begin

text \<open>Schema model mirroring \<open>specrest.convention.{ColumnSpec, ForeignKeySpec,
  IndexSpec, TableSpec, TriggerSpec, DatabaseSchema}\<close>. Constructors carry an \<open>F\<close>
  suffix in this milestone so the extracted Scala can coexist with the hand-written
  case classes via wildcard imports; a follow-up that retires the hand-written
  types will drop the suffix.\<close>

datatype (plugins only: code size) column_spec = ColumnSpec
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>sql_type\<close>
  bool                        \<comment> \<open>nullable\<close>
  "String.literal option"     \<comment> \<open>default_value\<close>

datatype (plugins only: code size) foreign_key_spec = ForeignKeySpec
  String.literal              \<comment> \<open>column\<close>
  String.literal              \<comment> \<open>ref_table\<close>
  String.literal              \<comment> \<open>ref_column\<close>
  String.literal              \<comment> \<open>on_delete\<close>

datatype (plugins only: code size) index_spec = IndexSpec
  String.literal              \<comment> \<open>name\<close>
  "String.literal list"       \<comment> \<open>columns\<close>
  bool                        \<comment> \<open>unique\<close>
  "String.literal option"     \<comment> \<open>filter_clause\<close>

datatype (plugins only: code size) table_spec = TableSpec
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>entity_name\<close>
  "column_spec list"          \<comment> \<open>columns\<close>
  String.literal              \<comment> \<open>primary_key\<close>
  "foreign_key_spec list"     \<comment> \<open>foreign_keys\<close>
  "String.literal list"       \<comment> \<open>checks\<close>
  "index_spec list"           \<comment> \<open>indexes\<close>

datatype (plugins only: code size) trigger_aggregate = SumAgg | CountAgg | MinAgg | MaxAgg

datatype (plugins only: code size) trigger_spec = TriggerSpec
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>function_name\<close>
  String.literal              \<comment> \<open>target_table\<close>
  String.literal              \<comment> \<open>target_column\<close>
  String.literal              \<comment> \<open>source_table\<close>
  String.literal              \<comment> \<open>source_foreign_key\<close>
  trigger_aggregate           \<comment> \<open>aggregate\<close>
  "String.literal option"     \<comment> \<open>source_column\<close>

datatype (plugins only: code size) database_schema = DatabaseSchema
  "table_spec list"           \<comment> \<open>tables\<close>
  "trigger_spec list"         \<comment> \<open>triggers\<close>

primrec columnName :: "column_spec \<Rightarrow> String.literal" where
  "columnName (ColumnSpec n _ _ _) = n"
primrec columnSqlType :: "column_spec \<Rightarrow> String.literal" where
  "columnSqlType (ColumnSpec _ t _ _) = t"
primrec columnNullable :: "column_spec \<Rightarrow> bool" where
  "columnNullable (ColumnSpec _ _ n _) = n"
primrec columnDefaultValue :: "column_spec \<Rightarrow> String.literal option" where
  "columnDefaultValue (ColumnSpec _ _ _ d) = d"

primrec fkColumn :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fkColumn (ForeignKeySpec c _ _ _) = c"
primrec fkRefTable :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fkRefTable (ForeignKeySpec _ rt _ _) = rt"
primrec fkRefColumn :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fkRefColumn (ForeignKeySpec _ _ rc _) = rc"
primrec fkOnDelete :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fkOnDelete (ForeignKeySpec _ _ _ od) = od"

primrec indexName :: "index_spec \<Rightarrow> String.literal" where
  "indexName (IndexSpec n _ _ _) = n"
primrec indexColumns :: "index_spec \<Rightarrow> String.literal list" where
  "indexColumns (IndexSpec _ cs _ _) = cs"
primrec indexUnique :: "index_spec \<Rightarrow> bool" where
  "indexUnique (IndexSpec _ _ u _) = u"
primrec indexFilterClause :: "index_spec \<Rightarrow> String.literal option" where
  "indexFilterClause (IndexSpec _ _ _ f) = f"

primrec tableName :: "table_spec \<Rightarrow> String.literal" where
  "tableName (TableSpec n _ _ _ _ _ _) = n"
primrec tableEntityName :: "table_spec \<Rightarrow> String.literal" where
  "tableEntityName (TableSpec _ e _ _ _ _ _) = e"
primrec tableColumns :: "table_spec \<Rightarrow> column_spec list" where
  "tableColumns (TableSpec _ _ cs _ _ _ _) = cs"
primrec tablePrimaryKey :: "table_spec \<Rightarrow> String.literal" where
  "tablePrimaryKey (TableSpec _ _ _ pk _ _ _) = pk"
primrec tableForeignKeys :: "table_spec \<Rightarrow> foreign_key_spec list" where
  "tableForeignKeys (TableSpec _ _ _ _ fks _ _) = fks"
primrec tableChecks :: "table_spec \<Rightarrow> String.literal list" where
  "tableChecks (TableSpec _ _ _ _ _ cks _) = cks"
primrec tableIndexes :: "table_spec \<Rightarrow> index_spec list" where
  "tableIndexes (TableSpec _ _ _ _ _ _ ixs) = ixs"

primrec triggerName :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerName (TriggerSpec n _ _ _ _ _ _ _) = n"
primrec triggerFunctionName :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerFunctionName (TriggerSpec _ fn _ _ _ _ _ _) = fn"
primrec triggerTargetTable :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerTargetTable (TriggerSpec _ _ tt _ _ _ _ _) = tt"
primrec triggerTargetColumn :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerTargetColumn (TriggerSpec _ _ _ tc _ _ _ _) = tc"
primrec triggerSourceTable :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerSourceTable (TriggerSpec _ _ _ _ st _ _ _) = st"
primrec triggerSourceForeignKey :: "trigger_spec \<Rightarrow> String.literal" where
  "triggerSourceForeignKey (TriggerSpec _ _ _ _ _ sfk _ _) = sfk"
primrec triggerAggregateOf :: "trigger_spec \<Rightarrow> trigger_aggregate" where
  "triggerAggregateOf (TriggerSpec _ _ _ _ _ _ a _) = a"
primrec triggerSourceColumn :: "trigger_spec \<Rightarrow> String.literal option" where
  "triggerSourceColumn (TriggerSpec _ _ _ _ _ _ _ sc) = sc"

primrec schemaTables :: "database_schema \<Rightarrow> table_spec list" where
  "schemaTables (DatabaseSchema ts _) = ts"
primrec schemaTriggers :: "database_schema \<Rightarrow> trigger_spec list" where
  "schemaTriggers (DatabaseSchema _ tg) = tg"

definition tableDepPairs ::
  "table_spec list \<Rightarrow> (String.literal \<times> String.literal list) list"
where
  "tableDepPairs ts =
    map (\<lambda>t. (tableName t, map fkRefTable (tableForeignKeys t))) ts"

lemmas tableDepPairsCode [code] = tableDepPairs_def

end
