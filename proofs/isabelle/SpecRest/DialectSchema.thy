theory DialectSchema
  imports SchemaDerive ValidateConventions
begin

text \<open>Canonical SQL column type discriminator + per-dialect rendering.
  Lifted from \<open>codegen.migration.CanonicalType\<close> + \<open>codegen.migration.Dialect\<close>.
  The Scala \<open>parse(sqlType: String)\<close> companion stays Scala-side (uses
  regex to extract \<open>VARCHAR(n)\<close> / \<open>NUMERIC(p, s)\<close> parameters); the ADT
  and the dialect-specific renderers move here.\<close>

datatype canonical_type =
    CtText
  | CtVarchar int
  | CtInt4
  | CtSerial4
  | CtInt8
  | CtSerial8
  | CtFloat8
  | CtBool
  | CtTimestamptz
  | CtDateOnly
  | CtUuid
  | CtNumeric int "int option"
  | CtBytes
  | CtJson

text \<open>\<open>isAutoIncrement\<close>: distinguishes DB-generated surrogate keys
  (\<open>SERIAL\<close>/\<open>BIGSERIAL\<close>) from explicit \<open>id: Int\<close> declarations. Used by
  every renderer + the MySQL-3818 CHECK filter so the three dialects
  cannot diverge.\<close>

fun isAutoIncrement :: "canonical_type \<Rightarrow> bool" where
  "isAutoIncrement CtSerial4 = True"
| "isAutoIncrement CtSerial8 = True"
| "isAutoIncrement _         = False"

end
