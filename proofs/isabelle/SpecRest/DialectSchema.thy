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

text \<open>SQLAlchemy type rendering per Postgres / Sqlite / MySQL.
  \<open>sa_type\<close> carries the SA expression plus an optional dialect-only
  import module (e.g. \<open>sqlalchemy.dialects.postgresql\<close> for JSONB).
  The Scala caller stitches the expr into Alembic column definitions
  and uses \<open>importModule\<close> to inject \<open>from postgresql import JSONB\<close>
  style imports.\<close>

datatype sa_type = SaType "String.literal" "String.literal option"

fun saTypeExpr :: "sa_type \<Rightarrow> String.literal" where
  "saTypeExpr (SaType e _) = e"

fun saTypeImportModule :: "sa_type \<Rightarrow> String.literal option" where
  "saTypeImportModule (SaType _ m) = m"

text \<open>Per-dialect type-mapping tables. Each is a pure pattern match on
  \<open>canonical_type\<close> producing the dialect's SQLAlchemy expression.
  Lifted verbatim from \<open>Postgres.saType\<close> / \<open>Sqlite.saType\<close> /
  \<open>Mysql.saType\<close> in \<open>codegen.migration.Dialect\<close>.

  Note: Sqlite + MySQL flatten \<open>Bytes/Json\<close> to plain
  \<open>sa.LargeBinary()\<close>/\<open>sa.Text()\<close> (no JSONB import); only Postgres needs
  the dialect import module.\<close>

fun postgresSaType :: "canonical_type \<Rightarrow> sa_type" where
  "postgresSaType CtText                = SaType (STR ''sa.Text()'') None"
| "postgresSaType (CtVarchar n)         = SaType (STR ''sa.String(length='' + showInt n + STR '')'') None"
| "postgresSaType CtInt4                = SaType (STR ''sa.Integer()'') None"
| "postgresSaType CtSerial4             = SaType (STR ''sa.Integer()'') None"
| "postgresSaType CtInt8                = SaType (STR ''sa.BigInteger()'') None"
| "postgresSaType CtSerial8             = SaType (STR ''sa.BigInteger()'') None"
| "postgresSaType CtFloat8              = SaType (STR ''sa.Float()'') None"
| "postgresSaType CtBool                = SaType (STR ''sa.Boolean()'') None"
| "postgresSaType CtTimestamptz         = SaType (STR ''sa.DateTime(timezone=True)'') None"
| "postgresSaType CtDateOnly            = SaType (STR ''sa.Date()'') None"
| "postgresSaType CtUuid                = SaType (STR ''sa.Uuid()'') None"
| "postgresSaType (CtNumeric p (Some s)) = SaType (STR ''sa.Numeric('' + showInt p + STR '', '' + showInt s + STR '')'') None"
| "postgresSaType (CtNumeric p None)     = SaType (STR ''sa.Numeric('' + showInt p + STR '')'') None"
| "postgresSaType CtBytes               = SaType (STR ''sa.LargeBinary()'') None"
| "postgresSaType CtJson                = SaType (STR ''postgresql.JSONB()'') (Some (STR ''sqlalchemy.dialects.postgresql''))"

fun sqliteSaType :: "canonical_type \<Rightarrow> sa_type" where
  "sqliteSaType CtText                  = SaType (STR ''sa.Text()'') None"
| "sqliteSaType (CtVarchar _)           = SaType (STR ''sa.Text()'') None"
| "sqliteSaType CtInt4                  = SaType (STR ''sa.Integer()'') None"
| "sqliteSaType CtSerial4               = SaType (STR ''sa.Integer()'') None"
| "sqliteSaType CtInt8                  = SaType (STR ''sa.BigInteger()'') None"
| "sqliteSaType CtSerial8               = SaType (STR ''sa.Integer()'') None"
| "sqliteSaType CtFloat8                = SaType (STR ''sa.Float()'') None"
| "sqliteSaType CtBool                  = SaType (STR ''sa.Boolean()'') None"
| "sqliteSaType CtTimestamptz           = SaType (STR ''sa.DateTime()'') None"
| "sqliteSaType CtDateOnly              = SaType (STR ''sa.Date()'') None"
| "sqliteSaType CtUuid                  = SaType (STR ''sa.Uuid()'') None"
| "sqliteSaType (CtNumeric p (Some s))  = SaType (STR ''sa.Numeric('' + showInt p + STR '', '' + showInt s + STR '')'') None"
| "sqliteSaType (CtNumeric p None)      = SaType (STR ''sa.Numeric('' + showInt p + STR '')'') None"
| "sqliteSaType CtBytes                 = SaType (STR ''sa.LargeBinary()'') None"
| "sqliteSaType CtJson                  = SaType (STR ''sa.JSON()'') None"

fun mysqlSaType :: "canonical_type \<Rightarrow> sa_type" where
  "mysqlSaType CtText                   = SaType (STR ''sa.String(length=255)'') None"
| "mysqlSaType (CtVarchar n)            = SaType (STR ''sa.String(length='' + showInt n + STR '')'') None"
| "mysqlSaType CtInt4                   = SaType (STR ''sa.Integer()'') None"
| "mysqlSaType CtSerial4                = SaType (STR ''sa.Integer()'') None"
| "mysqlSaType CtInt8                   = SaType (STR ''sa.BigInteger()'') None"
| "mysqlSaType CtSerial8                = SaType (STR ''sa.BigInteger()'') None"
| "mysqlSaType CtFloat8                 = SaType (STR ''sa.Float()'') None"
| "mysqlSaType CtBool                   = SaType (STR ''sa.Boolean()'') None"
| "mysqlSaType CtTimestamptz            = SaType (STR ''sa.DateTime()'') None"
| "mysqlSaType CtDateOnly               = SaType (STR ''sa.Date()'') None"
| "mysqlSaType CtUuid                   = SaType (STR ''sa.Uuid()'') None"
| "mysqlSaType (CtNumeric p (Some s))   = SaType (STR ''sa.Numeric('' + showInt p + STR '', '' + showInt s + STR '')'') None"
| "mysqlSaType (CtNumeric p None)       = SaType (STR ''sa.Numeric('' + showInt p + STR '')'') None"
| "mysqlSaType CtBytes                  = SaType (STR ''sa.LargeBinary()'') None"
| "mysqlSaType CtJson                   = SaType (STR ''sa.JSON()'') None"

end
