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

definition lookup_checks :: "String.literal \<Rightarrow> check_assign \<Rightarrow> check_pair list"
where
  "lookup_checks tn assigns = (case map_of assigns tn of None \<Rightarrow> [] | Some cs \<Rightarrow> cs)"

definition column_names :: "table_spec \<Rightarrow> String.literal list"
where
  "column_names t = map column_name (table_columns t)"

definition intra_drops ::
  "table_spec \<Rightarrow> table_spec \<Rightarrow> check_pair list \<Rightarrow> check_pair list \<Rightarrow> migration_op list"
where
  "intra_drops prev nxt prev_cks nxt_cks \<equiv>
    let tn = table_name prev;
        nxt_col_set = set (column_names nxt);
        drop_col_ops = map (\<lambda>c. DropColumn tn c)
                         (filter (\<lambda>c. column_name c \<notin> nxt_col_set) (table_columns prev));
        nxt_ix_set = set (table_indexes nxt);
        drop_ix_ops = map (\<lambda>i. DropIndex tn i)
                        (filter (\<lambda>i. i \<notin> nxt_ix_set) (table_indexes prev));
        nxt_fk_set = set (table_foreign_keys nxt);
        drop_fk_ops = map (\<lambda>f. DropForeignKey tn f)
                        (filter (\<lambda>f. f \<notin> nxt_fk_set) (table_foreign_keys prev));
        nxt_ck_set = set nxt_cks;
        drop_ck_ops = map (\<lambda>p. DropCheck tn (fst p) (snd p))
                        (filter (\<lambda>p. p \<notin> nxt_ck_set) prev_cks)
    in drop_ix_ops @ drop_ck_ops @ drop_fk_ops @ drop_col_ops"

definition intra_adds ::
  "table_spec \<Rightarrow> table_spec \<Rightarrow> check_pair list \<Rightarrow> check_pair list \<Rightarrow> migration_op list"
where
  "intra_adds prev nxt prev_cks nxt_cks \<equiv>
    let tn = table_name nxt;
        prev_col_set = set (column_names prev);
        add_col_ops = map (\<lambda>c. AddColumn tn c)
                        (filter (\<lambda>c. column_name c \<notin> prev_col_set) (table_columns nxt));
        prev_ix_set = set (table_indexes prev);
        add_ix_ops = map (\<lambda>i. AddIndex tn i)
                       (filter (\<lambda>i. i \<notin> prev_ix_set) (table_indexes nxt));
        prev_fk_set = set (table_foreign_keys prev);
        add_fk_ops = map (\<lambda>f. AddForeignKey tn f)
                       (filter (\<lambda>f. f \<notin> prev_fk_set) (table_foreign_keys nxt));
        prev_ck_set = set prev_cks;
        add_ck_ops = map (\<lambda>p. AddCheck tn (fst p) (snd p))
                       (filter (\<lambda>p. p \<notin> prev_ck_set) nxt_cks)
    in add_col_ops @ add_fk_ops @ add_ck_ops @ add_ix_ops"

definition alter_for_pair :: "String.literal \<Rightarrow> column_spec \<Rightarrow> column_spec \<Rightarrow> migration_op list"
where
  "alter_for_pair tn pc nc \<equiv>
    let type_change =
          (if column_sql_type pc = column_sql_type nc then []
           else [AlterColumnType tn (column_name nc) (column_sql_type pc) (column_sql_type nc)]);
        null_change =
          (if column_nullable pc = column_nullable nc then []
           else [AlterColumnNullable tn (column_name nc) (column_nullable pc) (column_nullable nc)]);
        def_change =
          (if column_default_value pc = column_default_value nc then []
           else [AlterColumnDefault tn (column_name nc) (column_default_value pc) (column_default_value nc)])
    in type_change @ null_change @ def_change"

definition intra_alters :: "table_spec \<Rightarrow> table_spec \<Rightarrow> migration_op list"
where
  "intra_alters prev nxt \<equiv>
    let prev_cols = map (\<lambda>c. (column_name c, c)) (table_columns prev)
    in concat (map (\<lambda>nc.
        case map_of prev_cols (column_name nc) of
          None \<Rightarrow> []
        | Some pc \<Rightarrow> alter_for_pair (table_name nxt) pc nc
       ) (table_columns nxt))"

text \<open>Topo-sort the given tables, then map names back to their table_spec.
  Returns the input order on cycle (matches the Scala fallback when extracted topo_sort
  returns None — caller validates).\<close>

definition sort_tables_by_fk :: "table_spec list \<Rightarrow> table_spec list option"
where
  "sort_tables_by_fk ts \<equiv>
    (case topo_sort_names (table_dep_pairs ts) of
       None \<Rightarrow> None
     | Some ns \<Rightarrow>
         let by_name = map (\<lambda>t. (table_name t, t)) ts in
         Some (concat (map (\<lambda>n. case map_of by_name n of None \<Rightarrow> [] | Some t \<Rightarrow> [t]) ns)))"

definition compute_diff ::
  "database_schema \<Rightarrow> database_schema \<Rightarrow> check_assign \<Rightarrow> check_assign
   \<Rightarrow> migration_op list option"
where
  "compute_diff prev nxt prev_cks nxt_cks \<equiv>
    let prev_ts = schema_tables prev;
        nxt_ts  = schema_tables nxt;
        prev_names = map table_name prev_ts;
        nxt_names  = map table_name nxt_ts;
        prev_by_name = map (\<lambda>t. (table_name t, t)) prev_ts;
        kept_pairs = concat (map (\<lambda>n.
                       case map_of prev_by_name (table_name n) of
                         None \<Rightarrow> []
                       | Some p \<Rightarrow> [(p, n)]) nxt_ts);
        drops_inside = concat (map (\<lambda>pn.
                         intra_drops (fst pn) (snd pn)
                           (lookup_checks (table_name (snd pn)) prev_cks)
                           (lookup_checks (table_name (snd pn)) nxt_cks)) kept_pairs);
        adds_inside  = concat (map (\<lambda>pn.
                         intra_adds (fst pn) (snd pn)
                           (lookup_checks (table_name (snd pn)) prev_cks)
                           (lookup_checks (table_name (snd pn)) nxt_cks)) kept_pairs);
        alters_inside = concat (map (\<lambda>pn. intra_alters (fst pn) (snd pn)) kept_pairs);
        dropped_table_specs = filter (\<lambda>t. table_name t \<notin> set nxt_names) prev_ts;
        created_table_specs = filter (\<lambda>t. table_name t \<notin> set prev_names) nxt_ts;
        prev_trg = schema_triggers prev;
        nxt_trg  = schema_triggers nxt;
        dropped_triggers = map DropTrigger (filter (\<lambda>t. t \<notin> set nxt_trg) prev_trg);
        added_triggers   = map AddTrigger  (filter (\<lambda>t. t \<notin> set prev_trg) nxt_trg)
    in
      case (sort_tables_by_fk dropped_table_specs, sort_tables_by_fk created_table_specs) of
        (Some dts, Some cts) \<Rightarrow>
          Some (dropped_triggers
                @ drops_inside
                @ map DropTable (rev dts)
                @ map CreateTable cts
                @ alters_inside
                @ adds_inside
                @ added_triggers)
      | _ \<Rightarrow> None"

lemmas compute_diff_code [code] = compute_diff_def
lemmas intra_drops_code [code]  = intra_drops_def
lemmas intra_adds_code [code]   = intra_adds_def
lemmas intra_alters_code [code] = intra_alters_def
lemmas alter_for_pair_code [code] = alter_for_pair_def
lemmas sort_tables_by_fk_code [code] = sort_tables_by_fk_def
lemmas lookup_checks_code [code] = lookup_checks_def
lemmas column_names_code [code] = column_names_def

end
