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
lemmas parseConventionValue_code [code] = parseConventionValue_def

end
