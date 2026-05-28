theory SchemaDiff
  imports Schema MigrationOps TopoSort
begin

text \<open>Pure schema-diff: given two schemas plus their dialect-rewritten named-check
  assignments, produces the canonical ordered list of \<open>migration_op\<close>s that take
  \<open>prev\<close> to \<open>next\<close>. Dialect-aware check rewriting and the auto-increment ALTER
  pre-validation stay in Scala — this theory contains only the deterministic
  delta-composition logic.\<close>

type_synonym check_pair    = "String.literal \<times> String.literal"
type_synonym check_assign  = "(String.literal \<times> check_pair list) list"

definition lookupChecks :: "String.literal \<Rightarrow> check_assign \<Rightarrow> check_pair list"
where
  "lookupChecks tn assigns = (case map_of assigns tn of None \<Rightarrow> [] | Some cs \<Rightarrow> cs)"

definition columnNames :: "table_spec \<Rightarrow> String.literal list"
where
  "columnNames t = map columnName (tableColumns t)"

definition intraDrops ::
  "table_spec \<Rightarrow> table_spec \<Rightarrow> check_pair list \<Rightarrow> check_pair list \<Rightarrow> migration_op list"
where
  "intraDrops prev nxt prev_cks nxt_cks \<equiv>
    let tn = tableName prev;
        nxt_col_set = set (columnNames nxt);
        drop_col_ops = map (\<lambda>c. DropColumn tn c)
                         (filter (\<lambda>c. columnName c \<notin> nxt_col_set) (tableColumns prev));
        nxt_ix_set = set (tableIndexes nxt);
        drop_ix_ops = map (\<lambda>i. DropIndex tn i)
                        (filter (\<lambda>i. i \<notin> nxt_ix_set) (tableIndexes prev));
        nxt_fk_set = set (tableForeignKeys nxt);
        drop_fk_ops = map (\<lambda>f. DropForeignKey tn f)
                        (filter (\<lambda>f. f \<notin> nxt_fk_set) (tableForeignKeys prev));
        nxt_ck_set = set nxt_cks;
        drop_ck_ops = map (\<lambda>p. DropCheck tn (fst p) (snd p))
                        (filter (\<lambda>p. p \<notin> nxt_ck_set) prev_cks)
    in drop_ix_ops @ drop_ck_ops @ drop_fk_ops @ drop_col_ops"

definition intraAdds ::
  "table_spec \<Rightarrow> table_spec \<Rightarrow> check_pair list \<Rightarrow> check_pair list \<Rightarrow> migration_op list"
where
  "intraAdds prev nxt prev_cks nxt_cks \<equiv>
    let tn = tableName nxt;
        prev_col_set = set (columnNames prev);
        add_col_ops = map (\<lambda>c. AddColumn tn c)
                        (filter (\<lambda>c. columnName c \<notin> prev_col_set) (tableColumns nxt));
        prev_ix_set = set (tableIndexes prev);
        add_ix_ops = map (\<lambda>i. AddIndex tn i)
                       (filter (\<lambda>i. i \<notin> prev_ix_set) (tableIndexes nxt));
        prev_fk_set = set (tableForeignKeys prev);
        add_fk_ops = map (\<lambda>f. AddForeignKey tn f)
                       (filter (\<lambda>f. f \<notin> prev_fk_set) (tableForeignKeys nxt));
        prev_ck_set = set prev_cks;
        add_ck_ops = map (\<lambda>p. AddCheck tn (fst p) (snd p))
                       (filter (\<lambda>p. p \<notin> prev_ck_set) nxt_cks)
    in add_col_ops @ add_fk_ops @ add_ck_ops @ add_ix_ops"

definition alterForPair :: "String.literal \<Rightarrow> column_spec \<Rightarrow> column_spec \<Rightarrow> migration_op list"
where
  "alterForPair tn pc nc \<equiv>
    let type_change =
          (if columnSqlType pc = columnSqlType nc then []
           else [AlterColumnType tn (columnName nc) (columnSqlType pc) (columnSqlType nc)]);
        null_change =
          (if columnNullable pc = columnNullable nc then []
           else [AlterColumnNullable tn (columnName nc) (columnNullable pc) (columnNullable nc)]);
        def_change =
          (if columnDefaultValue pc = columnDefaultValue nc then []
           else [AlterColumnDefault tn (columnName nc) (columnDefaultValue pc) (columnDefaultValue nc)])
    in type_change @ null_change @ def_change"

definition intraAlters :: "table_spec \<Rightarrow> table_spec \<Rightarrow> migration_op list"
where
  "intraAlters prev nxt \<equiv>
    let prev_cols = map (\<lambda>c. (columnName c, c)) (tableColumns prev)
    in concat (map (\<lambda>nc.
        case map_of prev_cols (columnName nc) of
          None \<Rightarrow> []
        | Some pc \<Rightarrow> alterForPair (tableName nxt) pc nc
       ) (tableColumns nxt))"

text \<open>Topo-sort the given tables, then map names back to their table_spec.
  Returns the input order on cycle (matches the Scala fallback when extracted topo_sort
  returns None — caller validates).\<close>

definition sortTablesByFk :: "table_spec list \<Rightarrow> table_spec list option"
where
  "sortTablesByFk ts \<equiv>
    (case topoSortNames (tableDepPairs ts) of
       None \<Rightarrow> None
     | Some ns \<Rightarrow>
         let by_name = map (\<lambda>t. (tableName t, t)) ts in
         Some (concat (map (\<lambda>n. case map_of by_name n of None \<Rightarrow> [] | Some t \<Rightarrow> [t]) ns)))"

definition computeDiff ::
  "database_schema \<Rightarrow> database_schema \<Rightarrow> check_assign \<Rightarrow> check_assign
   \<Rightarrow> migration_op list option"
where
  "computeDiff prev nxt prev_cks nxt_cks \<equiv>
    let prev_ts = schemaTables prev;
        nxt_ts  = schemaTables nxt;
        prev_names = map tableName prev_ts;
        nxt_names  = map tableName nxt_ts;
        prev_by_name = map (\<lambda>t. (tableName t, t)) prev_ts;
        kept_pairs = concat (map (\<lambda>n.
                       case map_of prev_by_name (tableName n) of
                         None \<Rightarrow> []
                       | Some p \<Rightarrow> [(p, n)]) nxt_ts);
        drops_inside = concat (map (\<lambda>pn.
                         intraDrops (fst pn) (snd pn)
                           (lookupChecks (tableName (snd pn)) prev_cks)
                           (lookupChecks (tableName (snd pn)) nxt_cks)) kept_pairs);
        adds_inside  = concat (map (\<lambda>pn.
                         intraAdds (fst pn) (snd pn)
                           (lookupChecks (tableName (snd pn)) prev_cks)
                           (lookupChecks (tableName (snd pn)) nxt_cks)) kept_pairs);
        alters_inside = concat (map (\<lambda>pn. intraAlters (fst pn) (snd pn)) kept_pairs);
        dropped_table_specs = filter (\<lambda>t. tableName t \<notin> set nxt_names) prev_ts;
        created_table_specs = filter (\<lambda>t. tableName t \<notin> set prev_names) nxt_ts;
        prev_trg = schemaTriggers prev;
        nxt_trg  = schemaTriggers nxt;
        dropped_triggers = map DropTrigger (filter (\<lambda>t. t \<notin> set nxt_trg) prev_trg);
        added_triggers   = map AddTrigger  (filter (\<lambda>t. t \<notin> set prev_trg) nxt_trg)
    in
      case (sortTablesByFk dropped_table_specs, sortTablesByFk created_table_specs) of
        (Some dts, Some cts) \<Rightarrow>
          Some (dropped_triggers
                @ drops_inside
                @ map DropTable (rev dts)
                @ map CreateTable cts
                @ alters_inside
                @ adds_inside
                @ added_triggers)
      | _ \<Rightarrow> None"

lemmas computeDiffCode [code] = computeDiff_def
lemmas intraDropsCode [code]  = intraDrops_def
lemmas intraAddsCode [code]   = intraAdds_def
lemmas intraAltersCode [code] = intraAlters_def
lemmas alterForPairCode [code] = alterForPair_def
lemmas sortTablesByFkCode [code] = sortTablesByFk_def
lemmas lookupChecksCode [code] = lookupChecks_def
lemmas columnNamesCode [code] = columnNames_def

end
