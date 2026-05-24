theory Schema
  imports Main "HOL-Library.Code_Target_Numeral"
begin

text \<open>Schema model mirroring \<open>specrest.convention.{ColumnSpec, ForeignKeySpec,
  IndexSpec, TableSpec, TriggerSpec, DatabaseSchema}\<close>. Constructors carry an \<open>F\<close>
  suffix in this milestone so the extracted Scala can coexist with the hand-written
  case classes via wildcard imports; a follow-up that retires the hand-written
  types will drop the suffix.\<close>

datatype column_spec = ColumnSpecF
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>sql_type\<close>
  bool                        \<comment> \<open>nullable\<close>
  "String.literal option"     \<comment> \<open>default_value\<close>

datatype foreign_key_spec = ForeignKeySpecF
  String.literal              \<comment> \<open>column\<close>
  String.literal              \<comment> \<open>ref_table\<close>
  String.literal              \<comment> \<open>ref_column\<close>
  String.literal              \<comment> \<open>on_delete\<close>

datatype index_spec = IndexSpecF
  String.literal              \<comment> \<open>name\<close>
  "String.literal list"       \<comment> \<open>columns\<close>
  bool                        \<comment> \<open>unique\<close>
  "String.literal option"     \<comment> \<open>filter_clause\<close>

datatype table_spec = TableSpecF
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>entity_name\<close>
  "column_spec list"          \<comment> \<open>columns\<close>
  String.literal              \<comment> \<open>primary_key\<close>
  "foreign_key_spec list"     \<comment> \<open>foreign_keys\<close>
  "String.literal list"       \<comment> \<open>checks\<close>
  "index_spec list"           \<comment> \<open>indexes\<close>

datatype trigger_aggregate = SumAgg | CountAgg | MinAgg | MaxAgg

datatype trigger_spec = TriggerSpecF
  String.literal              \<comment> \<open>name\<close>
  String.literal              \<comment> \<open>function_name\<close>
  String.literal              \<comment> \<open>target_table\<close>
  String.literal              \<comment> \<open>target_column\<close>
  String.literal              \<comment> \<open>source_table\<close>
  String.literal              \<comment> \<open>source_foreign_key\<close>
  trigger_aggregate           \<comment> \<open>aggregate\<close>
  "String.literal option"     \<comment> \<open>source_column\<close>

datatype database_schema = DatabaseSchemaF
  "table_spec list"           \<comment> \<open>tables\<close>
  "trigger_spec list"         \<comment> \<open>triggers\<close>

primrec column_name :: "column_spec \<Rightarrow> String.literal" where
  "column_name (ColumnSpecF n _ _ _) = n"
primrec column_sql_type :: "column_spec \<Rightarrow> String.literal" where
  "column_sql_type (ColumnSpecF _ t _ _) = t"
primrec column_nullable :: "column_spec \<Rightarrow> bool" where
  "column_nullable (ColumnSpecF _ _ n _) = n"
primrec column_default_value :: "column_spec \<Rightarrow> String.literal option" where
  "column_default_value (ColumnSpecF _ _ _ d) = d"

primrec fk_column :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fk_column (ForeignKeySpecF c _ _ _) = c"
primrec fk_ref_table :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fk_ref_table (ForeignKeySpecF _ rt _ _) = rt"
primrec fk_ref_column :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fk_ref_column (ForeignKeySpecF _ _ rc _) = rc"
primrec fk_on_delete :: "foreign_key_spec \<Rightarrow> String.literal" where
  "fk_on_delete (ForeignKeySpecF _ _ _ od) = od"

primrec index_name :: "index_spec \<Rightarrow> String.literal" where
  "index_name (IndexSpecF n _ _ _) = n"
primrec index_columns :: "index_spec \<Rightarrow> String.literal list" where
  "index_columns (IndexSpecF _ cs _ _) = cs"
primrec index_unique :: "index_spec \<Rightarrow> bool" where
  "index_unique (IndexSpecF _ _ u _) = u"
primrec index_filter_clause :: "index_spec \<Rightarrow> String.literal option" where
  "index_filter_clause (IndexSpecF _ _ _ f) = f"

primrec table_name :: "table_spec \<Rightarrow> String.literal" where
  "table_name (TableSpecF n _ _ _ _ _ _) = n"
primrec table_entity_name :: "table_spec \<Rightarrow> String.literal" where
  "table_entity_name (TableSpecF _ e _ _ _ _ _) = e"
primrec table_columns :: "table_spec \<Rightarrow> column_spec list" where
  "table_columns (TableSpecF _ _ cs _ _ _ _) = cs"
primrec table_primary_key :: "table_spec \<Rightarrow> String.literal" where
  "table_primary_key (TableSpecF _ _ _ pk _ _ _) = pk"
primrec table_foreign_keys :: "table_spec \<Rightarrow> foreign_key_spec list" where
  "table_foreign_keys (TableSpecF _ _ _ _ fks _ _) = fks"
primrec table_checks :: "table_spec \<Rightarrow> String.literal list" where
  "table_checks (TableSpecF _ _ _ _ _ cks _) = cks"
primrec table_indexes :: "table_spec \<Rightarrow> index_spec list" where
  "table_indexes (TableSpecF _ _ _ _ _ _ ixs) = ixs"

primrec trigger_name :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_name (TriggerSpecF n _ _ _ _ _ _ _) = n"
primrec trigger_function_name :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_function_name (TriggerSpecF _ fn _ _ _ _ _ _) = fn"
primrec trigger_target_table :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_target_table (TriggerSpecF _ _ tt _ _ _ _ _) = tt"
primrec trigger_target_column :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_target_column (TriggerSpecF _ _ _ tc _ _ _ _) = tc"
primrec trigger_source_table :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_source_table (TriggerSpecF _ _ _ _ st _ _ _) = st"
primrec trigger_source_foreign_key :: "trigger_spec \<Rightarrow> String.literal" where
  "trigger_source_foreign_key (TriggerSpecF _ _ _ _ _ sfk _ _) = sfk"
primrec trigger_aggregate_of :: "trigger_spec \<Rightarrow> trigger_aggregate" where
  "trigger_aggregate_of (TriggerSpecF _ _ _ _ _ _ a _) = a"
primrec trigger_source_column :: "trigger_spec \<Rightarrow> String.literal option" where
  "trigger_source_column (TriggerSpecF _ _ _ _ _ _ _ sc) = sc"

primrec schema_tables :: "database_schema \<Rightarrow> table_spec list" where
  "schema_tables (DatabaseSchemaF ts _) = ts"
primrec schema_triggers :: "database_schema \<Rightarrow> trigger_spec list" where
  "schema_triggers (DatabaseSchemaF _ tg) = tg"

definition table_dep_pairs ::
  "table_spec list \<Rightarrow> (String.literal \<times> String.literal list) list"
where
  "table_dep_pairs ts =
    map (\<lambda>t. (table_name t, map fk_ref_table (table_foreign_keys t))) ts"

lemmas table_dep_pairs_code [code] = table_dep_pairs_def

end
