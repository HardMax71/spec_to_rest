theory ValidateConventions
  imports IR Methods OpenApiConstraints
begin

text \<open>Single-dispatcher parse-don't-validate machinery for convention
  rules. The parser calls \<open>parseConventionValue\<close> at IR construction
  time with the property name + raw expression and gets back one of the
  three \<open>convention_value\<close> outcomes: \<open>CvOk\<close> (validated payload),
  \<open>CvBad\<close> (recognised property, bad value, carries failure reason),
  or \<open>CvUnknown\<close> (unrecognised property name).

  All per-property shape-and-range knowledge lives in this one function
  and the small string-predicate helpers below.\<close>

definition literalIsEmpty :: "String.literal \<Rightarrow> bool" where
  "literalIsEmpty s = (String.asciis_of_literal s = [])"

text \<open>\<open>literalIsBlank\<close>: empty after stripping ASCII whitespace (space 32,
  tab 9, LF 10, CR 13). Mirrors Scala's \<open>String.trim.isEmpty\<close> for the
  common case (we don't recognise non-ASCII Unicode whitespace, which is
  fine for spec convention strings).\<close>

fun asciiIsWhitespace :: "integer \<Rightarrow> bool" where
  "asciiIsWhitespace c = (c = 32 \<or> c = 9 \<or> c = 10 \<or> c = 13)"

definition literalIsBlank :: "String.literal \<Rightarrow> bool" where
  "literalIsBlank s =
     list_all asciiIsWhitespace (String.asciis_of_literal s)"

definition literalStartsWithSlash :: "String.literal \<Rightarrow> bool" where
  "literalStartsWithSlash s = (case String.asciis_of_literal s of
       []     \<Rightarrow> False
     | c # _ \<Rightarrow> c = 47)"

text \<open>Split a literal on the first \<open>':'\<close> octet (58). Returns
  \<open>None\<close> when no colon is present. Used by the \<open>strategy\<close> parser
  to break \<open>module:symbol\<close>.\<close>

fun splitOnColonAux ::
  "integer list \<Rightarrow> integer list \<Rightarrow> (integer list \<times> integer list) option"
where
  "splitOnColonAux _ [] = None"
| "splitOnColonAux acc (c # cs) =
     (if c = 58 then Some (rev acc, cs)
      else splitOnColonAux (c # acc) cs)"

definition splitOnColon ::
  "String.literal \<Rightarrow> (String.literal \<times> String.literal) option"
where
  "splitOnColon s = (case splitOnColonAux [] (String.asciis_of_literal s) of
       None \<Rightarrow> None
     | Some (l, r) \<Rightarrow> Some (String.literal_of_asciis l, String.literal_of_asciis r))"

text \<open>Per-shape extractors used by the dispatcher to keep the case-tree
  narrow. Each returns \<open>Some payload\<close> or \<open>None\<close> on shape mismatch.\<close>

fun asStringLit :: "expr_full \<Rightarrow> String.literal option" where
  "asStringLit (StringLitF v _) = Some v"
| "asStringLit _ = None"

fun asIntLit :: "expr_full \<Rightarrow> int option" where
  "asIntLit (IntLitF n _) = Some n"
| "asIntLit _ = None"

fun asBoolLit :: "expr_full \<Rightarrow> bool option" where
  "asBoolLit (BoolLitF b _) = Some b"
| "asBoolLit _ = None"

text \<open>Per-property parsers. Each takes the raw expression and returns a
  \<open>convention_value\<close>. Composed inline by the dispatcher; pulled out as
  separate definitions only when they need helpers (e.g. range, regex
  startsWith, colon split, enum membership). Trivially-shaped properties
  (just-non-empty string, just-string, just-bool) are handled by short
  inline let-bindings in the dispatcher.\<close>

definition parseHttpMethodPv :: "expr_full \<Rightarrow> convention_value" where
  "parseHttpMethodPv e = (case asStringLit e of
       None \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow> (case parseHttpMethod v of
                    Some _ \<Rightarrow> CvOk (PvString v)
                  | None   \<Rightarrow> CvBad (BadHttpMethod v) e))"

definition parseHttpStatusPv :: "expr_full \<Rightarrow> convention_value" where
  "parseHttpStatusPv e = (case asIntLit e of
       None \<Rightarrow> CvBad ExpectedInteger e
     | Some n \<Rightarrow> (if 100 \<le> n \<and> n \<le> 599 then CvOk (PvInt n)
                  else CvBad (HttpStatusOutOfRange n) e))"

definition parseHttpPathPv :: "expr_full \<Rightarrow> convention_value" where
  "parseHttpPathPv e = (case asStringLit e of
       None \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow> (if literalStartsWithSlash v then CvOk (PvString v)
                  else CvBad HttpPathMissingSlash e))"

definition parseNonEmptyStringPv :: "expr_full \<Rightarrow> convention_value" where
  "parseNonEmptyStringPv e = (case asStringLit e of
       None \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow> (if literalIsBlank v then CvBad EmptyString e else CvOk (PvString v)))"

definition parseStringPv :: "expr_full \<Rightarrow> convention_value" where
  "parseStringPv e = (case asStringLit e of
       None   \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow> CvOk (PvString v))"

text \<open>\<open>http_header\<close> tolerates runtime-evaluated values: a header value can
  be a literal, a field access (\<open>output.url\<close>), or a bare identifier.
  Non-literal shapes pass through as \<open>PvExpr\<close> so downstream consumers
  see the raw expression; no diagnostic is emitted.\<close>

definition parseHttpHeaderPv :: "expr_full \<Rightarrow> convention_value" where
  "parseHttpHeaderPv e = (case asStringLit e of
       Some v \<Rightarrow> CvOk (PvString v)
     | None   \<Rightarrow> CvOk (PvExpr e))"

definition parseBoolPv :: "expr_full \<Rightarrow> convention_value" where
  "parseBoolPv e = (case asBoolLit e of
       None   \<Rightarrow> CvBad ExpectedBoolean e
     | Some b \<Rightarrow> CvOk (PvBool b))"

definition parseTestStrategyPv :: "expr_full \<Rightarrow> convention_value" where
  "parseTestStrategyPv e = (case asStringLit e of
       None \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow>
         (if v = STR ''live'' then CvOk (PvBool True)
          else if v = STR ''redacted'' then CvOk (PvBool False)
          else CvBad (BadTestStrategy v) e))"

definition parseStrategyPv :: "expr_full \<Rightarrow> convention_value" where
  "parseStrategyPv e = (case asStringLit e of
       None \<Rightarrow> CvBad ExpectedString e
     | Some v \<Rightarrow>
         (case splitOnColon v of
            None \<Rightarrow> CvBad (BadStrategyFormat v) e
          | Some (m, s) \<Rightarrow>
              (if literalIsEmpty m \<or> literalIsEmpty s
               then CvBad (BadStrategyFormat v) e
               else CvOk (PvStrPair m s))))"

text \<open>Single-dispatch parser. Property-name string \<open>\<longmapsto>\<close> per-property
  parser \<open>\<longmapsto>\<close> typed convention_value.\<close>

definition parseConventionValue ::
  "String.literal \<Rightarrow> expr_full \<Rightarrow> convention_value"
where
  "parseConventionValue prop e =
     (if prop = STR ''http_method''         then parseHttpMethodPv e
      else if prop = STR ''http_status_success'' then parseHttpStatusPv e
      else if prop = STR ''http_path''           then parseHttpPathPv e
      else if prop = STR ''http_header''         then parseHttpHeaderPv e
      else if prop = STR ''db_table''            then parseNonEmptyStringPv e
      else if prop = STR ''db_timestamps''       then parseBoolPv e
      else if prop = STR ''plural''              then parseNonEmptyStringPv e
      else if prop = STR ''partial_index''       then parseNonEmptyStringPv e
      else if prop = STR ''test_strategy''       then parseTestStrategyPv e
      else if prop = STR ''strategy''            then parseStrategyPv e
      else CvUnknown e)"

text \<open>IR-context validation for individual convention rules. Two
  diagnostics: partial_index references a non-existent entity field;
  test_strategy references a non-existent operation-input / entity
  field. The cross-cutting collection bookkeeping (rule iteration,
  diagnostic span tracking) stays in Scala; this function takes one
  rule + the IR-context lookups and emits the typed diagnostics for
  Scala to format and accumulate.

  Mirrors the legacy \<open>Validate.validateIrContext\<close> branch logic.\<close>

datatype convention_ir_diagnostic =
    PartialIndexFieldMissing "String.literal" "String.literal"
      \<comment> \<open>target entity name, qualifier field name\<close>
  | TestStrategyFieldMissing "String.literal" "String.literal" "String.literal"
      \<comment> \<open>target name, qualifier field name, target-kind label
          ("operation" / "entity" / "target")\<close>

fun findOperationByName ::
  "operation_decl_full list \<Rightarrow> String.literal \<Rightarrow> operation_decl_full option"
where
  "findOperationByName [] _ = None"
| "findOperationByName (OperationDeclFull n a b c d e # rest) nm =
     (if n = nm then Some (OperationDeclFull n a b c d e)
      else findOperationByName rest nm)"

fun paramListHasName ::
  "param_decl_full list \<Rightarrow> String.literal \<Rightarrow> bool"
where
  "paramListHasName [] _ = False"
| "paramListHasName (ParamDeclFull pn _ _ # rest) nm =
     (if pn = nm then True else paramListHasName rest nm)"

fun operationHasParamNamed ::
  "operation_decl_full \<Rightarrow> String.literal \<Rightarrow> bool"
where
  "operationHasParamNamed (OperationDeclFull _ inputs _ _ _ _) nm =
     paramListHasName inputs nm"

fun validateIrContextRule ::
  "convention_rule_full \<Rightarrow> entity_decl_full list \<Rightarrow>
   operation_decl_full list \<Rightarrow> convention_ir_diagnostic list"
where
  "validateIrContextRule (ConventionRuleFull target prop qualOpt _ _) entities ops =
     (case qualOpt of
        None \<Rightarrow> []
      | Some field \<Rightarrow>
          if prop = STR ''partial_index'' then
            (case entityByName entities target of
               None \<Rightarrow> []
             | Some _ \<Rightarrow>
                 if entityHasField entities target field then []
                 else [PartialIndexFieldMissing target field])
          else if prop = STR ''test_strategy'' then
            let opMatch     = findOperationByName ops target;
                entityMatch = entityByName entities target;
                inParams    = (case opMatch of
                                 None    \<Rightarrow> False
                               | Some op \<Rightarrow> operationHasParamNamed op field);
                inEntity    = entityHasField entities target field;
                targetKind  = (if opMatch \<noteq> None then STR ''operation''
                               else if entityMatch \<noteq> None then STR ''entity''
                               else STR ''target'')
            in if inParams \<or> inEntity then []
               else [TestStrategyFieldMissing target field targetKind]
          else [])"

lemmas literalIsEmpty_code [code] = literalIsEmpty_def
lemmas asciiIsWhitespace_code [code] = asciiIsWhitespace.simps
lemmas literalIsBlank_code [code] = literalIsBlank_def
lemmas literalStartsWithSlash_code [code] = literalStartsWithSlash_def
lemmas splitOnColonAux_code [code] = splitOnColonAux.simps
lemmas splitOnColon_code [code] = splitOnColon_def
lemmas asStringLit_code [code] = asStringLit.simps
lemmas asIntLit_code [code] = asIntLit.simps
lemmas asBoolLit_code [code] = asBoolLit.simps
lemmas parseHttpMethodPv_code [code] = parseHttpMethodPv_def
lemmas parseHttpStatusPv_code [code] = parseHttpStatusPv_def
lemmas parseHttpPathPv_code [code] = parseHttpPathPv_def
lemmas parseNonEmptyStringPv_code [code] = parseNonEmptyStringPv_def
lemmas parseStringPv_code [code] = parseStringPv_def
lemmas parseHttpHeaderPv_code [code] = parseHttpHeaderPv_def
lemmas parseBoolPv_code [code] = parseBoolPv_def
lemmas parseTestStrategyPv_code [code] = parseTestStrategyPv_def
lemmas parseStrategyPv_code [code] = parseStrategyPv_def
text \<open>Cross-rule test_strategy collision detection. Two entity-targeted
  test_strategy rules conflict when they share a field-qualifier name
  but specify different entities AND different live/redacted values
  (operation inputs named that field would resolve ambiguously).

  Per-rule entry point: \<open>collisionsForRule\<close> returns the distinct
  \<open>(other_target, other_value)\<close> pairs in conflict with \<open>rule\<close>, or
  \<open>[]\<close> if there's no conflict. Scala iterates rules, formats the
  "others" string from the returned pairs, and emits the diagnostic.\<close>

text \<open>Per-rule tuple is \<open>(field, target, value)\<close>. The original rule is
  not threaded through — downstream helpers only inspect the three
  string components, and Scala already has the rule in hand at the
  call site. Carrying the full rule through every tuple inflated the
  extracted Scala type for no consumer.\<close>

fun extractTsTuple ::
  "convention_rule_full \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) option"
where
  "extractTsTuple (ConventionRuleFull target prop qualOpt val _) ens =
     (if prop = STR ''test_strategy'' \<and> List.member ens target then
        (case qualOpt of
           None   \<Rightarrow> None
         | Some f \<Rightarrow>
            (case val of
               CvOk (PvBool live) \<Rightarrow>
                 Some (f, target,
                       (if live then STR ''live'' else STR ''redacted''))
             | _ \<Rightarrow> None))
      else None)"

fun extractTsTuples ::
  "convention_rule_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list"
where
  "extractTsTuples [] _ = []"
| "extractTsTuples (r # rest) ens =
     (case extractTsTuple r ens of
        None   \<Rightarrow> extractTsTuples rest ens
      | Some t \<Rightarrow> t # extractTsTuples rest ens)"

fun fieldFilter ::
  "String.literal
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list"
where
  "fieldFilter _ [] = []"
| "fieldFilter field ((g, t, v) # rest) =
     (if field = g then (g, t, v) # fieldFilter field rest
      else fieldFilter field rest)"

fun targetsOf ::
  "(String.literal \<times> String.literal \<times> String.literal) list
   \<Rightarrow> String.literal list"
where
  "targetsOf [] = []"
| "targetsOf ((_, t, _) # rest) = t # targetsOf rest"

fun valuesOf ::
  "(String.literal \<times> String.literal \<times> String.literal) list
   \<Rightarrow> String.literal list"
where
  "valuesOf [] = []"
| "valuesOf ((_, _, v) # rest) = v # valuesOf rest"

fun otherPairsForField ::
  "String.literal
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list
   \<Rightarrow> (String.literal \<times> String.literal) list"
where
  "otherPairsForField _ [] = []"
| "otherPairsForField curTarget ((_, t, v) # rest) =
     (if t \<noteq> curTarget then (t, v) # otherPairsForField curTarget rest
      else otherPairsForField curTarget rest)"

text \<open>Takes precomputed tuples so Scala can call \<open>extractTsTuples\<close>
  once at the top of the validator loop and reuse the result across
  every rule — the old per-rule re-extraction was cubic worst-case in
  the rule count.\<close>

definition collisionsForRule ::
  "convention_rule_full
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal) list
   \<Rightarrow> String.literal list \<Rightarrow> (String.literal \<times> String.literal) list"
where
  "collisionsForRule rule tuples entityNames =
     (case extractTsTuple rule entityNames of
        None \<Rightarrow> []
      | Some (field, target, _) \<Rightarrow>
         let sameField = fieldFilter field tuples;
             targets   = remdups (targetsOf sameField);
             values    = remdups (valuesOf sameField)
         in if length targets > 1 \<and> length values > 1
            then remdups (otherPairsForField target sameField)
            else [])"

lemmas parseConventionValue_code [code] = parseConventionValue_def
lemmas findOperationByName_code [code] = findOperationByName.simps
lemmas paramListHasName_code [code] = paramListHasName.simps
lemmas operationHasParamNamed_code [code] = operationHasParamNamed.simps
lemmas validateIrContextRule_code [code] = validateIrContextRule.simps
lemmas extractTsTuple_code [code] = extractTsTuple.simps
lemmas extractTsTuples_code [code] = extractTsTuples.simps
lemmas fieldFilter_code [code] = fieldFilter.simps
lemmas targetsOf_code [code] = targetsOf.simps
lemmas valuesOf_code [code] = valuesOf.simps
lemmas otherPairsForField_code [code] = otherPairsForField.simps
text \<open>Inverse of \<open>parseConventionValue\<close>: re-synthesise an
  \<open>expr_full\<close> from a typed convention value. Used by Scala's
  \<open>Serialize\<close> to round-trip \<open>convention_rule_full\<close> through JSON.

  \<^item> \<open>CvOk pv\<close> emits the canonical literal form of \<open>pv\<close>;
  \<^item> \<open>CvBad _ raw\<close> / \<open>CvUnknown raw\<close> emit the original raw
    expression unchanged.

  Mirrors the same pattern as \<open>synthTemporalExpr\<close> (PR #336) — the
  emitted call carries no spans because the outer \<open>convention_rule_full\<close>
  preserves source location.\<close>

definition synthConventionValue :: "convention_value \<Rightarrow> expr_full" where
  "synthConventionValue v = (case v of
       CvOk (PvString s)    \<Rightarrow> StringLitF s None
     | CvOk (PvInt n)       \<Rightarrow> IntLitF n None
     | CvOk (PvBool b)      \<Rightarrow> BoolLitF b None
     | CvOk (PvStrPair a b) \<Rightarrow> StringLitF (a + STR '':'' + b) None
     | CvOk (PvExpr e)      \<Rightarrow> e
     | CvBad _ raw          \<Rightarrow> raw
     | CvUnknown raw        \<Rightarrow> raw)"

text \<open>Carry-equality lemmas: when the parser produces \<open>CvBad\<close> or
  \<open>CvUnknown\<close>, the carried raw expression is always the original input
  \<open>e\<close>. The parser never fishes a sub-expression for these
  outcomes. These are the convention analogue of
  \<open>parseTemporalBody_TbInvalid_raw_eq\<close>.\<close>

lemma parseConventionValue_CvBad_raw_eq:
  "parseConventionValue prop e = CvBad failure raw \<Longrightarrow> raw = e"
  by (auto simp: parseConventionValue_def
                 parseHttpMethodPv_def parseHttpStatusPv_def
                 parseHttpPathPv_def parseNonEmptyStringPv_def
                 parseStringPv_def parseHttpHeaderPv_def
                 parseBoolPv_def parseTestStrategyPv_def
                 parseStrategyPv_def
           split: option.splits if_splits)

lemma parseConventionValue_CvUnknown_raw_eq:
  "parseConventionValue prop e = CvUnknown raw \<Longrightarrow> raw = e"
  by (auto simp: parseConventionValue_def
                 parseHttpMethodPv_def parseHttpStatusPv_def
                 parseHttpPathPv_def parseNonEmptyStringPv_def
                 parseHttpHeaderPv_def parseBoolPv_def
                 parseTestStrategyPv_def parseStrategyPv_def
           split: option.splits if_splits)

text \<open>Idempotence of parse-then-synth-then-parse — the soundness law
  the Scala wire format relies on (encode via \<open>synthConventionValue\<close>,
  decode via \<open>parseConventionValue\<close>).

  The \<open>CvBad\<close> / \<open>CvUnknown\<close> branches go through purely by the
  carry-equality lemmas above. The \<open>CvOk\<close> branch is established via
  the per-property single-step round-trip lemmas that follow.\<close>

lemma parseConventionValue_synth_CvBad:
  assumes "parseConventionValue prop e = CvBad failure raw"
  shows   "parseConventionValue prop (synthConventionValue (CvBad failure raw))
             = CvBad failure raw"
proof -
  have "raw = e" using assms by (rule parseConventionValue_CvBad_raw_eq)
  thus ?thesis using assms by (simp add: synthConventionValue_def)
qed

lemma parseConventionValue_synth_CvUnknown:
  assumes "parseConventionValue prop e = CvUnknown raw"
  shows   "parseConventionValue prop (synthConventionValue (CvUnknown raw))
             = CvUnknown raw"
proof -
  have "raw = e" using assms by (rule parseConventionValue_CvUnknown_raw_eq)
  thus ?thesis using assms by (simp add: synthConventionValue_def)
qed

lemmas collisionsForRule_code [code] = collisionsForRule_def
lemmas synthConventionValue_code [code] = synthConventionValue_def

end
