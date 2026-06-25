theory Semantics_Reference
  imports Semantics
begin

text \<open>\<open>eval\<close> is a reference semantics for the surface IR (\<open>expr\<close>): the
  meaning the trusted \<open>lower\<close> and \<open>inline_calls\<close> desugars must preserve. It reuses
  the verified-subset \<open>eval\<close>'s value operations and gives each operator a meaning
  \<^emph>\<open>independent\<close> of \<open>lower\<close>, so agreement with \<open>lower\<close> is a real theorem rather than
  a tautology. A \<open>CallF\<close> evaluates its arguments, binds their values in the
  environment (call by value, hence capture-free), then evaluates the callee body;
  this needs \<^emph>\<open>fuel\<close> because a call unfolds into a body of unrelated size, so
  termination is lexicographic on \<open>(fuel, size)\<close>. The binder and collection forms
  (\<open>QuantifierF\<close>, \<open>SetComprehensionF\<close>, \<open>TheF\<close>, \<open>ConstructorF\<close>, the list literals,
  \<open>WithF\<close>) are modelled by the equations below; \<open>LambdaF\<close> alone has no equation and
  falls through to the catch-all (\<open>None\<close>) pending later coverage: a gap in the
  modelled fragment, not an unsoundness.\<close>

definition builtins_reserved ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> bool" where
  "builtins_reserved fs ps \<equiv> (\<forall>nm. is_builtin_pred nm \<longrightarrow> lookup_callee fs ps nm = None)
                              \<and> (\<forall>nm. is_builtin_const nm \<longrightarrow> lookup_callee fs ps nm = None)
                              \<and> (\<forall>nm. is_builtin_func nm \<longrightarrow> lookup_callee fs ps nm = None)
                              \<and> (\<forall>nm. is_builtin_int_func nm \<longrightarrow> lookup_callee fs ps nm = None)
                              \<and> lookup_callee fs ps (STR ''dom'') = None
                              \<and> lookup_callee fs ps (STR ''range'') = None
                              \<and> lookup_callee fs ps (STR ''ran'') = None
                              \<and> lookup_callee fs ps (STR ''sum'') = None
                              \<and> lookup_callee fs ps (STR ''len'') = None"

fun quant_dom ::
  "schema \<Rightarrow> state \<Rightarrow> quant_kind \<Rightarrow> quantifier_binding list
     \<Rightarrow> (String.literal \<times> ir_value list) option" where
  "quant_dom s st QAll [QuantifierBindingFull var (IdentifierF dnm _) _ _] =
     (case schema_lookup_enum s dnm of
        Some d \<Rightarrow> Some (var, map (\<lambda>m. VEnum dnm m) (enm_members d))
      | None \<Rightarrow> (case state_relation_domain st dnm of
                   Some dvs \<Rightarrow> Some (var, dvs)
                 | None \<Rightarrow> None))"
| "quant_dom _ _ _ _ = None"

fun beq_comp :: "bin_op \<Rightarrow> expr
                   \<Rightarrow> (String.literal \<times> String.literal \<times> expr) option" where
  "beq_comp BEq (SetComprehensionF var cdom pred _) =
     (case cdom of IdentifierF dnm _ \<Rightarrow> Some (var, dnm, pred) | _ \<Rightarrow> None)"
| "beq_comp _ _ = None"

lemma beq_comp_size:
  "beq_comp op r = Some (var, dnm, pred) \<Longrightarrow> size pred < size r"
  by (erule beq_comp.elims; auto split: expr.splits)

lemma beq_comp_free_vars:
  "beq_comp op r = Some (var, dnm, pred)
     \<Longrightarrow> string_in_list y (remove_name var (free_vars pred)) \<Longrightarrow> string_in_list y (free_vars r)"
  by (erule beq_comp.elims; auto split: expr.splits)

lemma beq_comp_allsub:
  "beq_comp op r = Some (var, dnm, pred)
     \<Longrightarrow> x \<in> set (allSubexprs pred) \<Longrightarrow> x \<in> set (allSubexprs r)"
  by (erule beq_comp.elims; auto split: expr.splits)

lemma beq_comp_inline_Some:
  "beq_comp op r = Some (var, dnm, pred)
     \<Longrightarrow> beq_comp op (inline_calls fs ps r) = Some (var, dnm, inline_calls fs ps pred)"
  by (erule beq_comp.elims; auto split: expr.splits)

lemma beq_comp_SetComp:
  "beq_comp op r = Some t \<Longrightarrow> \<exists>v d p sp. r = SetComprehensionF v d p sp"
  by (erule beq_comp.elims; auto split: expr.splits)

definition dom_eq_domains ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> state \<Rightarrow> bin_op
     \<Rightarrow> expr \<Rightarrow> expr \<Rightarrow> (ir_value list \<times> ir_value list) option" where
  "dom_eq_domains fs ps st op l r =
     (if op = BEq \<and> lookup_callee fs ps (STR ''dom'') = None
      then (case (dom_arg l, dom_arg r) of
              (Some rx, Some ry) \<Rightarrow>
                (case (state_relation_domain st rx, state_relation_domain st ry) of
                   (Some dx, Some dy) \<Rightarrow> Some (dx, dy) | _ \<Rightarrow> None)
            | _ \<Rightarrow> None)
      else None)"

lemma dom_eq_domains_SomeD:
  "dom_eq_domains fs ps st op l r = Some (dx, dy)
     \<Longrightarrow> op = BEq \<and> lookup_callee fs ps (STR ''dom'') = None
           \<and> (\<exists>rx. dom_arg l = Some rx \<and> state_relation_domain st rx = Some dx)
           \<and> (\<exists>ry. dom_arg r = Some ry \<and> state_relation_domain st ry = Some dy)"
  by (auto simp: dom_eq_domains_def split: bin_op.splits option.splits if_splits)

lemma dom_eq_domains_non_BEq [simp]:
  "op \<noteq> BEq \<Longrightarrow> dom_eq_domains fs ps st op l r = None"
  by (simp add: dom_eq_domains_def)

function (sequential) eval ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr \<Rightarrow> ir_value option"
and eval_list ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> expr list \<Rightarrow> ir_value list option"
and eval_entries ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> map_entry list \<Rightarrow> (ir_value \<times> ir_value) list option"
and eval_fields ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> field_assign list \<Rightarrow> (String.literal \<times> ir_value) list option"
and eval_the ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> ir_value list \<Rightarrow> expr \<Rightarrow> ir_value list option"
and eval_forall ::
  "function_decl list \<Rightarrow> predicate_decl list \<Rightarrow> nat
     \<Rightarrow> schema \<Rightarrow> state \<Rightarrow> env \<Rightarrow> String.literal \<Rightarrow> ir_value list \<Rightarrow> expr \<Rightarrow> ir_value option" where
  "eval fs ps fuel s st env (IntLitF n _)     = Some (VInt n)"
| "eval fs ps fuel s st env (BoolLitF b _)    = Some (VBool b)"
| "eval fs ps fuel s st env (StringLitF v _)  = Some (VStr v)"
| "eval fs ps fuel s st env (NoneLitF _)      = Some VNone"
| "eval fs ps fuel s st env (IdentifierF x _) =
     (case env_lookup env x of Some v \<Rightarrow> Some v | None \<Rightarrow> state_lookup_scalar st x)"
| "eval fs ps fuel s st env (BinaryOpF op l r _) =
     (case dom_eq_domains fs ps st op l r of
        Some (dx, dy) \<Rightarrow> Some (VBool (set dx = set dy))
      | None \<Rightarrow>
      (case beq_comp op r of
        Some (var, dnm, pred) \<Rightarrow>
          (if schema_lookup_enum s dnm \<noteq> None then None
           else case state_relation_domain st dnm of
             Some dvs \<Rightarrow>
               (case eval_the fs ps fuel s st env var dvs pred of
                  Some ms \<Rightarrow>
                    (case eval fs ps fuel s st env l of
                       Some (VSet xs) \<Rightarrow>
                         (if list_all (\<lambda>x. contains_value dvs x) xs
                            then Some (VBool (set xs = set ms)) else None)
                     | _ \<Rightarrow> None)
                | None \<Rightarrow> None)
           | None \<Rightarrow> None)
      | None \<Rightarrow> eval_bin op (eval fs ps fuel s st env l)
                               (eval fs ps fuel s st env r)))"
| "eval fs ps fuel s st env (UnaryOpF op e _) =
     eval_un op (eval fs ps fuel s st env e)"
| "eval fs ps fuel s st env (IfF c a b _) =
     (case eval fs ps fuel s st env c of
        Some (VBool True)  \<Rightarrow> eval fs ps fuel s st env a
      | Some (VBool False) \<Rightarrow> eval fs ps fuel s st env b
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (LetF x v body _) =
     (case eval fs ps fuel s st env v of
        Some va \<Rightarrow> eval fs ps fuel s st ((x, va) # env) body
      | None    \<Rightarrow> None)"
| "eval fs ps fuel s st env (FieldAccessF base f _) =
     (case eval fs ps fuel s st env base of
        Some v \<Rightarrow> value_field_lookup st v f
      | None   \<Rightarrow> None)"
| "eval fs ps fuel s st env (PrimeF e _) = eval fs ps fuel s st env e"
| "eval fs ps fuel s st env (PreF e _)   = eval fs ps fuel s st env e"
| "eval fs ps fuel s st env (SomeWrapF e _) =
     map_option VSome (eval fs ps fuel s st env e)"
| "eval fs ps fuel s st env (MatchesF e pat _) =
     (case eval fs ps fuel s st env e of
        Some (VStr str) \<Rightarrow> Some (VBool (string_matches str pat))
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (CallF callee args _) =
     (case fuel of
        0 \<Rightarrow> None
      | Suc fuel' \<Rightarrow>
          (case callee of
             IdentifierF nm _ \<Rightarrow>
               (case lookup_callee fs ps nm of
                  Some (params, body) \<Rightarrow>
                    (if length params = length args \<and> distinct params
                       then (case eval_list fs ps fuel s st env args of
                               Some vals \<Rightarrow>
                                 eval fs ps fuel' s st (zip params vals) body
                             | None \<Rightarrow> None)
                       else None)
                | None \<Rightarrow>
                    (case args of
                       [] \<Rightarrow>
                         (if is_builtin_const nm
                            then Some (VInt (builtin_const_val nm)) else None)
                     | [arg] \<Rightarrow>
                         (if is_builtin_pred nm
                            then (case eval fs ps fuel s st env arg of
                                    Some (VStr str) \<Rightarrow> Some (VBool (str_predicate nm str))
                                  | _ \<Rightarrow> None)
                          else if is_builtin_func nm
                            then (case eval fs ps fuel s st env arg of
                                    Some (VStr str) \<Rightarrow> Some (VStr (builtin_str_func nm str))
                                  | _ \<Rightarrow> None)
                          else if is_builtin_int_func nm
                            then (case eval fs ps fuel s st env arg of
                                    Some (VInt n) \<Rightarrow> Some (VInt (builtin_int_func nm n))
                                  | _ \<Rightarrow> None)
                            else None)
                     | _ \<Rightarrow> None))
           | _ \<Rightarrow> None))"
| "eval fs ps fuel s st env (FloatLitF d _) =
     map_option VReal (decimalToRat d)"
| "eval fs ps fuel s st env (SeqLiteralF es _) =
     map_option VSeq (eval_list fs ps fuel s st env es)"
| "eval fs ps fuel s st env (SetLiteralF es _) =
     map_option (\<lambda>vs. VSet (foldr (\<lambda>v acc. dedupe_values (v # acc)) vs []))
       (eval_list fs ps fuel s st env es)"
| "eval fs ps fuel s st env (MapLiteralF entries _) =
     map_option VMap (eval_entries fs ps fuel s st env entries)"
| "eval fs ps fuel s st env (ConstructorF name fas _) =
     map_option (\<lambda>fvs. foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) (VEntity name (STR '''')) fvs)
       (eval_fields fs ps fuel s st env fas)"
| "eval fs ps fuel s st env (WithF base fas _) =
     (case (eval fs ps fuel s st env base, eval_fields fs ps fuel s st env fas) of
        (Some bv, Some fvs) \<Rightarrow> Some (foldl (\<lambda>acc (fld, fv). VEntityWith acc fld fv) bv fvs)
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (EnumAccessF base mem _) =
     (case base of
        IdentifierF en _ \<Rightarrow>
          (case schema_lookup_enum s en of
             Some d \<Rightarrow> (if List.member (enm_members d) mem then Some (VEnum en mem) else None)
           | None \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (IndexF base key _) =
     (case (peelRelationRef base, eval fs ps fuel s st env key) of
        (Some rel, Some kv) \<Rightarrow> state_lookup_key st rel kv
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (TheF var dm body _) =
     (case dm of
        IdentifierF rel _ \<Rightarrow>
          (case state_relation_domain st rel of
             Some dmv \<Rightarrow>
               (case eval_the fs ps fuel s st env var dmv body of
                  Some (x # rest) \<Rightarrow> (if list_all (\<lambda>y. y = x) rest then Some x else None)
                | _ \<Rightarrow> None)
           | None \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "eval fs ps fuel s st env (QuantifierF k bs body _) =
     (case quant_dom s st k bs of
        Some (var, dmv) \<Rightarrow> eval_forall fs ps fuel s st env var dmv body
      | None \<Rightarrow> None)"
| "eval fs ps fuel s st env _ = None"
| "eval_list fs ps fuel s st env [] = Some []"
| "eval_list fs ps fuel s st env (e # es) =
     (case eval fs ps fuel s st env e of
        Some v \<Rightarrow>
          (case eval_list fs ps fuel s st env es of
             Some vs \<Rightarrow> Some (v # vs)
           | None \<Rightarrow> None)
      | None \<Rightarrow> None)"
| "eval_entries fs ps fuel s st env [] = Some []"
| "eval_entries fs ps fuel s st env (MapEntryFull k v _ # rest) =
     (case (eval fs ps fuel s st env k, eval fs ps fuel s st env v,
            eval_entries fs ps fuel s st env rest) of
        (Some kv, Some vv, Some ps') \<Rightarrow> Some ((kv, vv) # ps')
      | _ \<Rightarrow> None)"
| "eval_fields fs ps fuel s st env [] = Some []"
| "eval_fields fs ps fuel s st env (FieldAssignFull fld v _ # rest) =
     (case (eval fs ps fuel s st env v, eval_fields fs ps fuel s st env rest) of
        (Some fv, Some fvs) \<Rightarrow> Some ((fld, fv) # fvs)
      | _ \<Rightarrow> None)"
| "eval_the fs ps fuel s st env var [] body = Some []"
| "eval_the fs ps fuel s st env var (v # rest) body =
     (case eval fs ps fuel s st ((var, v) # env) body of
        Some (VBool b) \<Rightarrow>
          (case eval_the fs ps fuel s st env var rest body of
             Some matches \<Rightarrow> Some (if b then v # matches else matches)
           | None \<Rightarrow> None)
      | _ \<Rightarrow> None)"
| "eval_forall fs ps fuel s st env var [] body = Some (VBool True)"
| "eval_forall fs ps fuel s st env var (v # rest) body =
     (case eval fs ps fuel s st ((var, v) # env) body of
        Some (VBool b) \<Rightarrow>
          (case eval_forall fs ps fuel s st env var rest body of
             Some (VBool acc) \<Rightarrow> Some (VBool (b \<and> acc))
           | _ \<Rightarrow> None)
      | _ \<Rightarrow> None)"
  by pat_completeness auto

termination
  by (relation "measures [
        (\<lambda>x. case x of Inl (Inl (fs, ps, fuel, s, st, env, e)) \<Rightarrow> fuel
                     | Inl (Inr (Inl (fs, ps, fuel, s, st, env, es))) \<Rightarrow> fuel
                     | Inl (Inr (Inr (fs, ps, fuel, s, st, env, ents))) \<Rightarrow> fuel
                     | Inr (Inl (fs, ps, fuel, s, st, env, fas)) \<Rightarrow> fuel
                     | Inr (Inr (Inl (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> fuel
                     | Inr (Inr (Inr (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> fuel),
        (\<lambda>x. case x of Inl (Inl (fs, ps, fuel, s, st, env, e)) \<Rightarrow> size e
                     | Inl (Inr (Inl (fs, ps, fuel, s, st, env, es))) \<Rightarrow> size_list size es
                     | Inl (Inr (Inr (fs, ps, fuel, s, st, env, ents))) \<Rightarrow> size_list size ents
                     | Inr (Inl (fs, ps, fuel, s, st, env, fas)) \<Rightarrow> size_list size fas
                     | Inr (Inr (Inl (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> size body
                     | Inr (Inr (Inr (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> size body),
        (\<lambda>x. case x of Inl (Inl (fs, ps, fuel, s, st, env, e)) \<Rightarrow> 0
                     | Inl (Inr (Inl (fs, ps, fuel, s, st, env, es))) \<Rightarrow> 0
                     | Inl (Inr (Inr (fs, ps, fuel, s, st, env, ents))) \<Rightarrow> 0
                     | Inr (Inl (fs, ps, fuel, s, st, env, fas)) \<Rightarrow> 0
                     | Inr (Inr (Inl (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> Suc (length dmv)
                     | Inr (Inr (Inr (fs, ps, fuel, s, st, env, var, dmv, body))) \<Rightarrow> Suc (length dmv))]")
     (auto dest: beq_comp_size)

lemma eval_list_length:
  "eval_list fs ps fuel s st env es = Some vs \<Longrightarrow> length vs = length es"
  by (induction es arbitrary: vs) (auto split: option.splits)

lemma eval_dom_eq:
  assumes "dom_eq_domains fs ps st op l r = Some (dx, dy)"
  shows "eval fs ps fuel s st env (BinaryOpF op l r sp) = Some (VBool (set dx = set dy))"
  using assms by simp

lemma eval_dom_CallF:
  assumes "lookup_callee fs ps (STR ''dom'') = None"
  shows "eval fs ps fuel s st env
           (CallF (IdentifierF (STR ''dom'') sp1) [IdentifierF rel sp2] sp) = None"
  using assms
  by (simp add: is_builtin_pred_def is_builtin_func_def is_builtin_int_func_def split: nat.splits)

lemma quant_dom_qb_names:
  "quant_dom s st k bs = Some (var, dmv) \<Longrightarrow> qb_names bs = [var]"
  by (erule quant_dom.elims; auto split: option.splits)

lemma quant_dom_inline_calls:
  "quant_dom s st k bs = Some (var, dmv)
     \<Longrightarrow> quant_dom s st k (inline_calls_bindings fs ps bs) = Some (var, dmv)"
  by (erule quant_dom.elims; auto split: option.splits)

lemma eval_BAdd_MapLiteralF_None:
  "eval fs ps fuel s st env (BinaryOpF BAdd base (MapLiteralF es sp) bsp) = None"
proof (rule ccontr)
  assume "eval fs ps fuel s st env (BinaryOpF BAdd base (MapLiteralF es sp) bsp) \<noteq> None"
  then obtain v where
    v: "eval fs ps fuel s st env (BinaryOpF BAdd base (MapLiteralF es sp) bsp) = Some v" by auto
  have "eval fs ps fuel s st env (BinaryOpF BAdd base (MapLiteralF es sp) bsp)
        = eval_arith AddOp (eval fs ps fuel s st env base)
                           (map_option VMap (eval_entries fs ps fuel s st env es))"
    by simp
  with v have arith: "eval_arith AddOp (eval fs ps fuel s st env base)
                        (map_option VMap (eval_entries fs ps fuel s st env es)) = Some v" by simp
  show False
    using eval_arith_some_imp_numeric_or_str[OF arith]
    by (cases "eval_entries fs ps fuel s st env es") auto
qed

lemma rel_insert_rhs_eval_None:
  "rel_insert_rhs r = Some (rel, kn, vn)
     \<Longrightarrow> eval fs ps fuel s st env r = None"
proof -
  assume "rel_insert_rhs r = Some (rel, kn, vn)"
  then obtain base es sp bsp where "r = BinaryOpF BAdd base (MapLiteralF es sp) bsp"
    using rel_insert_rhs_SomeD by blast
  thus ?thesis by (simp only: eval_BAdd_MapLiteralF_None)
qed

definition enums_wf :: "schema \<Rightarrow> String.literal list \<Rightarrow> bool" where
  "enums_wf s enums = (\<forall>en. string_in_list en enums = (schema_lookup_enum s en \<noteq> None))"

lemma quant_dom_some_shape:
  "quant_dom s st k bs = Some (var, dmv) \<Longrightarrow>
     k = QAll \<and> (\<exists>dnm sp1 dty a. bs = [QuantifierBindingFull var (IdentifierF dnm sp1) dty a] \<and>
       ((\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
        \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)))"
  by (erule quant_dom.elims; auto split: option.splits)

lemma sil_enum:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = Some d \<Longrightarrow> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma sil_none:
  "enums_wf s enums \<Longrightarrow> schema_lookup_enum s dnm = None \<Longrightarrow> \<not> string_in_list dnm enums"
  unfolding enums_wf_def by auto

lemma dmrel_enum:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = Some d
   \<Longrightarrow> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d)"
  by auto

lemma dmrel_rel:
  "(\<exists>d. schema_lookup_enum s dnm = Some d \<and> dmv = map (\<lambda>m. VEnum dnm m) (enm_members d))
     \<or> (schema_lookup_enum s dnm = None \<and> state_relation_domain st dnm = Some dmv)
   \<Longrightarrow> schema_lookup_enum s dnm = None
   \<Longrightarrow> state_relation_domain st dnm = Some dmv"
  by auto

end
