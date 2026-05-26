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

fun extractTsTuple ::
  "convention_rule_full \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal \<times>
       convention_rule_full) option"
where
  "extractTsTuple (ConventionRuleFull target prop qualOpt val span) ens =
     (if prop = STR ''test_strategy'' \<and> List.member ens target then
        (case qualOpt of
           None   \<Rightarrow> None
         | Some f \<Rightarrow>
            (case val of
               CvOk (PvBool live) \<Rightarrow>
                 Some (f, target,
                       (if live then STR ''live'' else STR ''redacted''),
                       ConventionRuleFull target prop qualOpt val span)
             | _ \<Rightarrow> None))
      else None)"

fun extractTsTuples ::
  "convention_rule_full list \<Rightarrow> String.literal list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal \<times>
       convention_rule_full) list"
where
  "extractTsTuples [] _ = []"
| "extractTsTuples (r # rest) ens =
     (case extractTsTuple r ens of
        None   \<Rightarrow> extractTsTuples rest ens
      | Some t \<Rightarrow> t # extractTsTuples rest ens)"

fun fieldFilter ::
  "String.literal
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal \<times>
       convention_rule_full) list
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal \<times>
       convention_rule_full) list"
where
  "fieldFilter _ [] = []"
| "fieldFilter field ((g, t, v, r) # rest) =
     (if field = g then (g, t, v, r) # fieldFilter field rest
      else fieldFilter field rest)"

fun targetsOf ::
  "(String.literal \<times> String.literal \<times> String.literal \<times>
    convention_rule_full) list \<Rightarrow> String.literal list"
where
  "targetsOf [] = []"
| "targetsOf ((_, t, _, _) # rest) = t # targetsOf rest"

fun valuesOf ::
  "(String.literal \<times> String.literal \<times> String.literal \<times>
    convention_rule_full) list \<Rightarrow> String.literal list"
where
  "valuesOf [] = []"
| "valuesOf ((_, _, v, _) # rest) = v # valuesOf rest"

fun otherPairsForField ::
  "String.literal
   \<Rightarrow> (String.literal \<times> String.literal \<times> String.literal \<times>
       convention_rule_full) list
   \<Rightarrow> (String.literal \<times> String.literal) list"
where
  "otherPairsForField _ [] = []"
| "otherPairsForField curTarget ((_, t, v, _) # rest) =
     (if t \<noteq> curTarget then (t, v) # otherPairsForField curTarget rest
      else otherPairsForField curTarget rest)"

definition collisionsForRule ::
  "convention_rule_full \<Rightarrow> convention_rule_full list \<Rightarrow>
   String.literal list \<Rightarrow> (String.literal \<times> String.literal) list"
where
  "collisionsForRule rule rules entityNames =
     (case extractTsTuple rule entityNames of
        None \<Rightarrow> []
      | Some (field, target, _, _) \<Rightarrow>
         let allTups   = extractTsTuples rules entityNames;
             sameField = fieldFilter field allTups;
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
lemmas collisionsForRule_code [code] = collisionsForRule_def

end
