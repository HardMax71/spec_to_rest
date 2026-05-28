theory AlloyBuildSigs
  imports AlloyTypes
begin

text \<open>Lifted Alloy signature builder. Mirrors
  \<open>specrest.verify.alloy.Translator.buildSigs\<close>: walks the IR's
  entities/enums plus the operation-specific state-field and
  input-field lists to construct the Alloy sig list. Uses the
  already-lifted \<open>alloyFieldTypeOf\<close> (\<open>AlloyTypes\<close>) for per-field
  shape classification.

  No \<open>expr_full\<close> recursion, so the build stays fast — pattern
  completeness on the \<open>entity_decl_full\<close>/\<open>field_decl_full\<close>
  datatypes is shallow.

  The lifted function returns \<open>None\<close> when any field type is
  unsupported (\<open>alloyFieldTypeOf\<close> returned \<open>None\<close>); the Scala
  caller \<open>failAlloy\<close>'s with the matching diagnostic.\<close>

datatype alloy_field = AlloyFieldLifted
  "String.literal"                  \<comment> \<open>field name\<close>
  alloy_field_multiplicity          \<comment> \<open>multiplicity tag\<close>
  "String.literal"                  \<comment> \<open>element sig name\<close>

datatype alloy_sig = AlloySigLifted
  "String.literal"                  \<comment> \<open>sig name\<close>
  bool                              \<comment> \<open>abstract\<close>
  bool                              \<comment> \<open>isOne\<close>
  "String.literal option"           \<comment> \<open>extends parent (e.g. Bool/enum)\<close>
  "alloy_field list"                \<comment> \<open>fields\<close>

fun fieldDeclTypeOf :: "field_decl_full \<Rightarrow> type_expr_full" where
  "fieldDeclTypeOf (FieldDeclFull _ t _ _) = t"

fun fieldDeclNameOf :: "field_decl_full \<Rightarrow> String.literal" where
  "fieldDeclNameOf (FieldDeclFull n _ _ _) = n"

fun entityDeclNameOf :: "entity_decl_full \<Rightarrow> String.literal" where
  "entityDeclNameOf (EntityDeclFull n _ _ _ _) = n"

fun entityDeclFieldsOf :: "entity_decl_full \<Rightarrow> field_decl_full list" where
  "entityDeclFieldsOf (EntityDeclFull _ _ fs _ _) = fs"

fun enumDeclNameOf :: "enum_decl_full \<Rightarrow> String.literal" where
  "enumDeclNameOf (EnumDeclFull n _ _) = n"

fun enumDeclValuesOf :: "enum_decl_full \<Rightarrow> String.literal list" where
  "enumDeclValuesOf (EnumDeclFull _ vs _) = vs"

definition fieldDeclToAlloyField ::
  "field_decl_full \<Rightarrow> alloy_field option"
where
  "fieldDeclToAlloyField fd =
     (case alloyFieldTypeOf (fieldDeclTypeOf fd) of
        None \<Rightarrow> None
      | Some mn \<Rightarrow>
          Some (AlloyFieldLifted (fieldDeclNameOf fd) (fst mn) (snd mn)))"

fun fieldDeclsToAlloyFields ::
  "field_decl_full list \<Rightarrow> alloy_field list option"
where
  "fieldDeclsToAlloyFields [] = Some []"
| "fieldDeclsToAlloyFields (fd # rest) =
     (case fieldDeclToAlloyField fd of
        None \<Rightarrow> None
      | Some f \<Rightarrow>
          (case fieldDeclsToAlloyFields rest of
             None \<Rightarrow> None
           | Some fs \<Rightarrow> Some (f # fs)))"

fun typedNamesToAlloyFields ::
  "(String.literal \<times> type_expr_full) list \<Rightarrow> alloy_field list option"
where
  "typedNamesToAlloyFields [] = Some []"
| "typedNamesToAlloyFields ((name, t) # rest) =
     (case alloyFieldTypeOf t of
        None \<Rightarrow> None
      | Some mn \<Rightarrow>
          (case typedNamesToAlloyFields rest of
             None \<Rightarrow> None
           | Some fs \<Rightarrow> Some (AlloyFieldLifted name (fst mn) (snd mn) # fs)))"

definition boolSigs :: "alloy_sig list" where
  "boolSigs = [
     AlloySigLifted (STR ''Bool'') True False None [],
     AlloySigLifted (STR ''True'') False True (Some (STR ''Bool'')) [],
     AlloySigLifted (STR ''False'') False True (Some (STR ''Bool'')) []
   ]"

definition entityToAlloySig ::
  "entity_decl_full \<Rightarrow> alloy_sig option"
where
  "entityToAlloySig e =
     (case fieldDeclsToAlloyFields (entityDeclFieldsOf e) of
        None \<Rightarrow> None
      | Some fs \<Rightarrow>
          Some (AlloySigLifted (entityDeclNameOf e) False False None fs))"

fun entitiesToAlloySigs ::
  "entity_decl_full list \<Rightarrow> alloy_sig list option"
where
  "entitiesToAlloySigs [] = Some []"
| "entitiesToAlloySigs (e # rest) =
     (case entityToAlloySig e of
        None \<Rightarrow> None
      | Some s \<Rightarrow>
          (case entitiesToAlloySigs rest of
             None \<Rightarrow> None
           | Some ss \<Rightarrow> Some (s # ss)))"

fun enumMembersToSigs ::
  "String.literal \<Rightarrow> String.literal list \<Rightarrow> alloy_sig list"
where
  "enumMembersToSigs _ [] = []"
| "enumMembersToSigs parent (v # rest) =
     AlloySigLifted v False True (Some parent) []
       # enumMembersToSigs parent rest"

definition enumToAlloySigs ::
  "enum_decl_full \<Rightarrow> alloy_sig list"
where
  "enumToAlloySigs e =
     AlloySigLifted (enumDeclNameOf e) True False None []
       # enumMembersToSigs (enumDeclNameOf e) (enumDeclValuesOf e)"

fun enumsToAlloySigs ::
  "enum_decl_full list \<Rightarrow> alloy_sig list"
where
  "enumsToAlloySigs [] = []"
| "enumsToAlloySigs (e # rest) =
     enumToAlloySigs e @ enumsToAlloySigs rest"

text \<open>Single helper for State / Inputs / StatePost sig construction.
  Returns \<open>Some []\<close> when the field list is empty (no sig to emit),
  \<open>Some [sig]\<close> when it builds cleanly, \<open>None\<close> on field-type
  failure. Collapses the previous \<open>option option\<close> shape into a
  flat option of list — the empty list serves as the
  "no-sig-needed" carrier and extracts cleanly.\<close>

definition stateOrInputSig ::
  "String.literal \<Rightarrow> (String.literal \<times> type_expr_full) list
   \<Rightarrow> alloy_sig list option"
where
  "stateOrInputSig sigName fs =
     (if fs = [] then Some []
      else case typedNamesToAlloyFields fs of
             None \<Rightarrow> None
           | Some afs \<Rightarrow> Some [AlloySigLifted sigName False True None afs])"

text \<open>Bind for \<open>option\<close>-of-list — collapses chains of \<open>None\<close>
  propagation that would otherwise drive the deeply nested
  case-of-case shape the previous version emitted.\<close>

definition optConcat ::
  "alloy_sig list option \<Rightarrow> alloy_sig list option \<Rightarrow> alloy_sig list option"
where
  "optConcat a b =
     (case a of
        None \<Rightarrow> None
      | Some xs \<Rightarrow>
          (case b of
             None \<Rightarrow> None
           | Some ys \<Rightarrow> Some (xs @ ys)))"

text \<open>Main lifted builder. Concatenates the five sig groups via
  \<open>optConcat\<close>; the \<open>includeStatePost\<close> flag covers both the
  \<open>buildSigs\<close> and \<open>buildPreservationSigs\<close> Scala call sites
  (the only difference between them is the extra \<open>StatePost\<close>
  sig appended when state fields are non-empty).\<close>

definition buildAlloySigs ::
  "bool                                       \<comment> \<open>needsBool\<close>
   \<Rightarrow> entity_decl_full list                    \<comment> \<open>ir.c\<close>
   \<Rightarrow> enum_decl_full list                      \<comment> \<open>ir.d\<close>
   \<Rightarrow> (String.literal \<times> type_expr_full) list   \<comment> \<open>stateFields\<close>
   \<Rightarrow> (String.literal \<times> type_expr_full) list   \<comment> \<open>inputFields\<close>
   \<Rightarrow> bool                                     \<comment> \<open>includeStatePost\<close>
   \<Rightarrow> alloy_sig list option"
where
  "buildAlloySigs needsBool ents enums stateFields inputFields includeStatePost =
     (let boolPart = Some (if needsBool then boolSigs else []);
          enumPart = Some (enumsToAlloySigs enums);
          entPart  = entitiesToAlloySigs ents;
          statePart = stateOrInputSig (STR ''State'') stateFields;
          inputPart = stateOrInputSig (STR ''Inputs'') inputFields;
          postPart  = (if includeStatePost
                       then stateOrInputSig (STR ''StatePost'') stateFields
                       else Some [])
      in optConcat boolPart
           (optConcat entPart
              (optConcat enumPart
                 (optConcat statePart
                    (optConcat inputPart postPart)))))"

lemmas fieldDeclTypeOf_code [code]          = fieldDeclTypeOf.simps
lemmas fieldDeclNameOf_code [code]          = fieldDeclNameOf.simps
lemmas entityDeclNameOf_code [code]         = entityDeclNameOf.simps
lemmas entityDeclFieldsOf_code [code]       = entityDeclFieldsOf.simps
lemmas enumDeclNameOf_code [code]           = enumDeclNameOf.simps
lemmas enumDeclValuesOf_code [code]         = enumDeclValuesOf.simps
lemmas fieldDeclToAlloyField_code [code]    = fieldDeclToAlloyField_def
lemmas fieldDeclsToAlloyFields_code [code]  = fieldDeclsToAlloyFields.simps
lemmas typedNamesToAlloyFields_code [code]  = typedNamesToAlloyFields.simps
lemmas boolSigs_code [code]                 = boolSigs_def
lemmas entityToAlloySig_code [code]         = entityToAlloySig_def
lemmas entitiesToAlloySigs_code [code]      = entitiesToAlloySigs.simps
lemmas enumMembersToSigs_code [code]        = enumMembersToSigs.simps
lemmas enumToAlloySigs_code [code]          = enumToAlloySigs_def
lemmas enumsToAlloySigs_code [code]         = enumsToAlloySigs.simps
lemmas stateOrInputSig_code [code]          = stateOrInputSig_def
lemmas optConcat_code [code]                = optConcat_def
lemmas buildAlloySigs_code [code]           = buildAlloySigs_def

end
