package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledOperation

object AuthSchemes:

  // One credential slot per configuration value a scheme needs. The env-var
  // spelling lives here once; the per-target generators (.env.example, Python
  // settings, Go config struct, TS zod schema and config object) only choose
  // their language's property syntax, so the names cannot drift apart between
  // the env file and the config reader.
  enum CredSlot derives CanEqual:
    case JwtSecret
    case JwtAlgorithm
    case Token(scheme: String)
    case Key(scheme: String)
    case BasicUsername(scheme: String)
    case BasicPassword(scheme: String)

    def envKey: String = this match
      case JwtSecret        => "JWT_SECRET"
      case JwtAlgorithm     => "JWT_ALGORITHM"
      case Token(s)         => s"AUTH_TOKEN_${s.toUpperCase}"
      case Key(s)           => s"AUTH_KEY_${s.toUpperCase}"
      case BasicUsername(s) => s"AUTH_BASIC_${s.toUpperCase}_USERNAME"
      case BasicPassword(s) => s"AUTH_BASIC_${s.toUpperCase}_PASSWORD"

  def credSlots(ir: ServiceIRFull): List[CredSlot] =
    val jwtPair =
      if needsJwt(ir) then List(CredSlot.JwtSecret, CredSlot.JwtAlgorithm) else Nil
    jwtPair ++ svcSecurity(ir).flatMap: s =>
      val n = ssdName(s)
      ssdKind(s) match
        case k if isJwt(k)  => Nil
        case SsBearer(_)    => List(CredSlot.Token(n))
        case SsApiKey(_, _) => List(CredSlot.Key(n))
        case SsBasic()      => List(CredSlot.BasicUsername(n), CredSlot.BasicPassword(n))

  def isJwt(kind: security_scheme_kind): Boolean = kind match
    case SsBearer(format) => format.exists(_.equalsIgnoreCase("JWT"))
    case _                => false

  def needsJwt(ir: ServiceIRFull): Boolean =
    svcSecurity(ir).exists(s => isJwt(ssdKind(s)))

  // Non-JWT schemes compare a presented credential against a configured one,
  // which must be constant-time (crypto/subtle, timingSafeEqual).
  def needsConstantTimeCompare(ir: ServiceIRFull): Boolean =
    svcSecurity(ir).exists(s =>
      ssdKind(s) match
        case SsBearer(_) => !isJwt(ssdKind(s))
        case _           => true
    )

  // sorted so that equivalent OR-alternative sets share one dependency/middleware
  def orCombos(operations: List[ProfiledOperation]): List[List[String]] =
    operations
      .map(_.requiresAuth.sorted)
      .filter(_.sizeIs > 1)
      .distinct

  def envEntries(ir: ServiceIRFull): List[(String, String)] =
    credSlots(ir).map: slot =>
      slot.envKey -> (slot match
        case CredSlot.JwtAlgorithm => "HS256"
        case _                     => ""
      )

  def pascalName(schemeName: String): String =
    schemeName.split('_').filter(_.nonEmpty).map(_.capitalize).mkString
