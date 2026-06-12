package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*

object AuthSchemes:

  def isJwt(kind: security_scheme_kind): Boolean = kind match
    case SsBearer(format) => format.exists(_.equalsIgnoreCase("JWT"))
    case _                => false

  def needsJwt(ir: ServiceIRFull): Boolean =
    svcSecurity(ir).exists(s => isJwt(ssdKind(s)))

  def envEntries(ir: ServiceIRFull): List[(String, String)] =
    val schemes = svcSecurity(ir)
    val jwtPair =
      if needsJwt(ir) then List("JWT_SECRET" -> "", "JWT_ALGORITHM" -> "HS256")
      else Nil
    jwtPair ++ schemes.flatMap: s =>
      val n = ssdName(s).toUpperCase
      ssdKind(s) match
        case SsBearer(_) if isJwt(ssdKind(s)) => Nil
        case SsBearer(_)                      => List(s"AUTH_TOKEN_$n" -> "")
        case SsApiKey(_, _)                   => List(s"AUTH_KEY_$n" -> "")
        case SsBasic() =>
          List(s"AUTH_BASIC_${n}_USERNAME" -> "", s"AUTH_BASIC_${n}_PASSWORD" -> "")

  def pascalName(schemeName: String): String =
    schemeName.split('_').filter(_.nonEmpty).map(_.capitalize).mkString

  def camelName(schemeName: String): String =
    val p = pascalName(schemeName)
    p.head.toLower.toString + p.tail
