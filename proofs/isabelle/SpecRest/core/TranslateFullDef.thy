theory TranslateFullDef
  imports Translate IR_Lower
begin

definition translate_full :: "String.literal list \<Rightarrow> expr_full \<Rightarrow> smt_term option" where
  "translate_full enums e \<equiv> map_option translate (lower enums e)"

end
