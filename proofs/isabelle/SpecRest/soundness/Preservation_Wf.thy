theory Preservation_Wf
  imports SpecRest_Core.Semantics
begin

text \<open>Phase 9j (dual of 9i). \<open>requiresAlloy_imp_lower_none\<close> proved the
  Alloy-routed fragment maps to \<open>None\<close>; here a syntactic \<open>wf_z3\<close>
  predicate carves out the Z3-verifiable subset and we prove
  \<open>wf_z3 e \<Longrightarrow> lower enums e \<noteq> None\<close>. This upgrades the
  \<open>Trust.classify\<close> runtime oracle (\<open>lower(e).isDefined \<Longrightarrow>
  Sound\<close>) to a proven syntactic guarantee: in-fragment specs are Sound,
  never best-effort. \<open>IndexF\<close> requires a relation-ref base shape (the
  \<open>peel_relation_ref\<close> side-condition); quantifier bodies require every
  binding domain to be an identifier (the \<open>lower_forall_bindings\<close>
  totality condition, dual to \<open>lfb_none_of_alloy\<close>).\<close>

fun is_ident_dom :: "quantifier_binding \<Rightarrow> bool" where
  "is_ident_dom (QuantifierBindingFull _ (IdentifierF _ _) _ _) = True"
| "is_ident_dom _ = False"

fun wf_z3_bindings :: "quantifier_binding list \<Rightarrow> bool" where
  "wf_z3_bindings [] = False"
| "wf_z3_bindings [b] = is_ident_dom b"
| "wf_z3_bindings (b # rest) = (is_ident_dom b \<and> wf_z3_bindings rest)"

fun rel_ref_shape :: "expr \<Rightarrow> bool" where
  "rel_ref_shape (IdentifierF _ _) = True"
| "rel_ref_shape (PreF b _)        = (identName b \<noteq> None)"
| "rel_ref_shape (PrimeF b _)      = (identName b \<noteq> None)"
| "rel_ref_shape _                 = False"

lemma peelRelationRefFull_some_imp_rel_ref_shape:
  "peelRelationRef base = Some rel \<Longrightarrow> rel_ref_shape base"
  by (cases base rule: peelRelationRef.cases) (auto dest!: identName_SomeD)

text \<open>\<open>comp_pred_or_self\<close> hoists the ident-domain-comprehension dispatch
  out of \<open>wf_z3\<close>'s recursive equation. Nested-pattern case bodies inside a
  recursive \<open>fun\<close> duplicate the recursive default branch per constructor,
  which is what made the old equation cost ~29 s to elaborate.\<close>

fun comp_pred_or_self :: "expr \<Rightarrow> expr" where
  "comp_pred_or_self (SetComprehensionF v d p s) =
     (case d of IdentifierF _ _ \<Rightarrow> p | _ \<Rightarrow> SetComprehensionF v d p s)"
| "comp_pred_or_self e = e"

lemma comp_pred_or_self_size [termination_simp]:
  "size (comp_pred_or_self e) \<le> size e"
  by (cases e rule: comp_pred_or_self.cases) (auto split: expr.splits)

lemma comp_pred_or_self_noncomp:
  "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3
     \<Longrightarrow> comp_pred_or_self r = r"
  by (cases r rule: comp_pred_or_self.cases) (auto split: expr.splits)

fun wf_z3 :: "expr \<Rightarrow> bool"
and wf_z3_list :: "expr list \<Rightarrow> bool"
and wf_z3_fields :: "field_assign list \<Rightarrow> bool"
and wf_z3_entries :: "map_entry list \<Rightarrow> bool"
where
  "wf_z3 (BoolLitF _ _)            = True"
| "wf_z3 (IntLitF _ _)             = True"
| "wf_z3 (IdentifierF _ _)         = True"
| "wf_z3 (UnaryOpF op e _)         =
     (case op of UNot \<Rightarrow> wf_z3 e | UNegate \<Rightarrow> wf_z3 e
        | UCardinality \<Rightarrow> (\<exists>x s. e = IdentifierF x s)
        | UPower \<Rightarrow> False)"
| "wf_z3 (BinaryOpF op l r _)      =
     (case op of
        BEq \<Rightarrow> wf_z3 l \<and> wf_z3 (comp_pred_or_self r)
      | BIn \<Rightarrow>
          wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s)
                       \<or> wf_z3 (comp_pred_or_self r))
      | BNotIn \<Rightarrow> wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r)
      | _ \<Rightarrow> wf_z3 l \<and> wf_z3 r)"
| "wf_z3 (LetF _ v b _)            = (wf_z3 v \<and> wf_z3 b)"
| "wf_z3 (EnumAccessF base _ _)    = (\<exists>en s. base = IdentifierF en s)"
| "wf_z3 (FieldAccessF base _ _)   = wf_z3 base"
| "wf_z3 (IndexF base key _)       = (rel_ref_shape base \<and> wf_z3 key)"
| "wf_z3 (PrimeF e _)              = wf_z3 e"
| "wf_z3 (PreF e _)                = wf_z3 e"
| "wf_z3 (WithF base ups _)        = (wf_z3 base \<and> wf_z3_fields ups)"
| "wf_z3 (SetLiteralF es _)        = wf_z3_list es"
| "wf_z3 (QuantifierF _ bs body _) = (wf_z3_bindings bs \<and> wf_z3 body)"
| "wf_z3 (FloatLitF s _)           = (decimalToRat s \<noteq> None)"
| "wf_z3 (StringLitF _ _)          = True"
| "wf_z3 (NoneLitF _)              = True"
| "wf_z3 (LambdaF _ _ _)           = False"
| "wf_z3 (CallF _ _ _)             = False"
| "wf_z3 (ConstructorF _ fas _) = wf_z3_fields fas"
| "wf_z3 (MapLiteralF entries _)   = wf_z3_entries entries"
| "wf_z3 (SeqLiteralF es _)        = wf_z3_list es"
| "wf_z3 (SetComprehensionF _ _ _ _) = False"
| "wf_z3 (SomeWrapF e _)           = wf_z3 e"
| "wf_z3 (TheF _ _ _ _)            = False"
| "wf_z3 (MatchesF e _ _)          = wf_z3 e"
| "wf_z3 (IfF c a b _)             = (wf_z3 c \<and> wf_z3 a \<and> wf_z3 b)"
| "wf_z3_list []                   = True"
| "wf_z3_list (e # rest)           = (wf_z3 e \<and> wf_z3_list rest)"
| "wf_z3_fields []                 = True"
| "wf_z3_fields (FieldAssignFull _ v _ # rest) = (wf_z3 v \<and> wf_z3_fields rest)"
| "wf_z3_entries []                = True"
| "wf_z3_entries (MapEntryFull k v _ # rest) = (wf_z3 k \<and> wf_z3 v \<and> wf_z3_entries rest)"

lemma not_expr_has_ty_set_comp:
  "expr_has_ty \<Gamma> (SetComprehensionF v dm pr sp) t \<Longrightarrow> False"
  by (auto elim: expr_has_ty.cases)

lemma wf_z3_BEq_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "wf_z3 (BinaryOpF BEq l r sp) = (wf_z3 l \<and> wf_z3 r)"
proof (cases r)
  case (SetComprehensionF v dom pr s)
  with assms show ?thesis by (cases dom) auto
qed auto

lemma wf_z3_BIn_noncomp:
  assumes "\<nexists>var dnm sp2 p sp3. r = SetComprehensionF var (IdentifierF dnm sp2) p sp3"
  shows "wf_z3 (BinaryOpF BIn l r sp)
           = (wf_z3 l \<and> ((\<exists>rel s. r = IdentifierF rel s) \<or> wf_z3 r))"
proof (cases r)
  case (SetComprehensionF v dom pr s)
  with assms show ?thesis by (cases dom) auto
qed auto

lemma wf_z3_fields_iff:
  "wf_z3_fields updates
     \<longleftrightarrow> (\<forall>fld v sp. FieldAssignFull fld v sp \<in> set updates \<longrightarrow> wf_z3 v)"
proof (induction updates)
  case Nil thus ?case by simp
next
  case (Cons hd rest)
  thus ?case by (cases hd) auto
qed


end
