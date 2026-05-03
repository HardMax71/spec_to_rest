theory CodegenSmoke
  imports Translate
begin

export_code translate eval smt_eval
  in Scala
  module_name SpecRestGenerated
  file_prefix "SpecRestGenerated"

end
