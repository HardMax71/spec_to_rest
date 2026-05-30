package specrest.codegen

object SensitiveFields:
  def isSensitive(name: String): Boolean =
    specrest.ir.generated.SpecRestGenerated.isSensitiveField(name)
