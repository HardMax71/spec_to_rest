package specrest.codegen.python

import specrest.codegen.AuthSchemes
import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledService

object SecurityPython:

  export AuthSchemes.isJwt

  // sorted so that equivalent OR-alternative sets share one dependency
  def dependencyName(requiresAuth: List[String]): String =
    s"require_${requiresAuth.sorted.mkString("_or_")}"

  def settingLines(ir: ServiceIRFull): List[String] =
    val schemes = svcSecurity(ir)
    val jwtPair =
      if schemes.exists(s => isJwt(ssdKind(s))) then
        List(
          "jwt_secret: SecretStr | None = None",
          "jwt_algorithm: str = \"HS256\""
        )
      else Nil
    jwtPair ++ schemes.flatMap: s =>
      val n = ssdName(s)
      ssdKind(s) match
        case SsBearer(format) if format.exists(_.equalsIgnoreCase("JWT")) => Nil
        case SsBearer(_)                                                  => List(s"auth_token_$n: SecretStr | None = None")
        case SsApiKey(_, _)                                               => List(s"auth_key_$n: SecretStr | None = None")
        case SsBasic() =>
          List(
            s"auth_basic_${n}_username: str | None = None",
            s"auth_basic_${n}_password: SecretStr | None = None"
          )

  def emit(profiled: ProfiledService): String =
    val ir       = profiled.ir
    val schemes  = svcSecurity(ir)
    val needsJwt = schemes.exists(s => isJwt(ssdKind(s)))
    val needsBasic = schemes.exists(s =>
      ssdKind(s) match { case SsBasic() => true; case _ => false }
    )
    val apiKeyClasses = schemes
      .flatMap(s =>
        ssdKind(s) match
          case SsApiKey(location, _) => Some(apiKeyClass(location))
          case _                     => None
      )
      .distinct
      .sorted

    val securityImports =
      (List("HTTPAuthorizationCredentials", "HTTPBearer") ++
        apiKeyClasses ++
        (if needsBasic then List("HTTPBasic", "HTTPBasicCredentials") else Nil)).distinct.sorted

    val combos = profiled.operations
      .map(_.requiresAuth.sorted)
      .filter(_.sizeIs > 1)
      .distinct

    val schemeSections = schemes.map(schemeSection)
    val comboSections  = combos.map(comboSection(schemes, _))
    val unauthorizedHelper =
      if schemes.isEmpty then ""
      else
        """|
           |
           |
           |def _unauthorized() -> HTTPException:
           |    return HTTPException(
           |        status_code=401,
           |        detail="Unauthorized",
           |        headers={"WWW-Authenticate": "Bearer"},
           |    )""".stripMargin

    s"""import hmac
       |${if needsJwt then "\nimport jwt" else ""}
       |from fastapi import Depends, HTTPException
       |from fastapi.security import ${securityImports.mkString(", ")}
       |
       |from app.config import settings
       |
       |_bearer = HTTPBearer(auto_error=False)
       |
       |
       |def require_admin(
       |    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
       |) -> None:
       |    token = settings.admin_token.get_secret_value() if settings.admin_token else ""
       |    # No token configured (the production default): the surface does not exist.
       |    if not token:
       |        raise HTTPException(status_code=404, detail="Not Found")
       |    if credentials is None or not hmac.compare_digest(credentials.credentials, token):
       |        raise HTTPException(
       |            status_code=401,
       |            detail="Unauthorized",
       |            headers={"WWW-Authenticate": "Bearer"},
       |        )$unauthorizedHelper${schemeSections.mkString}${comboSections.mkString}
       |""".stripMargin

  private def apiKeyClass(location: String): String = location match
    case "query"  => "APIKeyQuery"
    case "cookie" => "APIKeyCookie"
    case _        => "APIKeyHeader"

  private def credentialParam(schemes: List[security_scheme_decl], name: String): String =
    val kind = schemes.find(s => ssdName(s) == name).map(ssdKind)
    kind match
      case Some(SsApiKey(_, _)) => s"$name: str | None = Depends(_${name}_key)"
      case Some(SsBasic()) =>
        s"$name: HTTPBasicCredentials | None = Depends(_${name}_basic)"
      case _ => s"$name: HTTPAuthorizationCredentials | None = Depends(_bearer)"

  private def schemeSection(decl: security_scheme_decl): String =
    val n = ssdName(decl)
    val (singletons, checker) = ssdKind(decl) match
      case SsBearer(format) if format.exists(_.equalsIgnoreCase("JWT")) =>
        (
          "",
          s"""|def _check_$n(credentials: HTTPAuthorizationCredentials | None) -> bool:
              |    if credentials is None or settings.jwt_secret is None:
              |        return False
              |    try:
              |        jwt.decode(
              |            credentials.credentials,
              |            settings.jwt_secret.get_secret_value(),
              |            algorithms=[settings.jwt_algorithm],
              |        )
              |    except jwt.InvalidTokenError:
              |        return False
              |    return True""".stripMargin
        )
      case SsBearer(_) =>
        (
          "",
          s"""|def _check_$n(credentials: HTTPAuthorizationCredentials | None) -> bool:
              |    expected = settings.auth_token_$n
              |    return (
              |        credentials is not None
              |        and expected is not None
              |        and hmac.compare_digest(credentials.credentials, expected.get_secret_value())
              |    )""".stripMargin
        )
      case SsApiKey(location, paramName) =>
        (
          s"_${n}_key = ${apiKeyClass(location)}(name=\"$paramName\", auto_error=False)\n\n\n",
          s"""|def _check_$n(value: str | None) -> bool:
              |    expected = settings.auth_key_$n
              |    return (
              |        value is not None
              |        and expected is not None
              |        and hmac.compare_digest(value, expected.get_secret_value())
              |    )""".stripMargin
        )
      case SsBasic() =>
        (
          s"_${n}_basic = HTTPBasic(auto_error=False)\n\n\n",
          s"""|def _check_$n(credentials: HTTPBasicCredentials | None) -> bool:
              |    username = settings.auth_basic_${n}_username
              |    password = settings.auth_basic_${n}_password
              |    return (
              |        credentials is not None
              |        and username is not None
              |        and password is not None
              |        and hmac.compare_digest(credentials.username, username)
              |        and hmac.compare_digest(credentials.password, password.get_secret_value())
              |    )""".stripMargin
        )
    val param = credentialParam(List(decl), n)
    s"""|
        |
        |
        |$singletons$checker
        |
        |
        |def require_$n(
        |    $param,
        |) -> None:
        |    if not _check_$n($n):
        |        raise _unauthorized()""".stripMargin

  private def comboSection(
      schemes: List[security_scheme_decl],
      names: List[String]
  ): String =
    val params = names.map(n => s"    ${credentialParam(schemes, n)},").mkString("\n")
    val checks = names.map(n => s"_check_$n($n)").mkString(" or ")
    s"""|
        |
        |
        |# requires_auth alternatives: a request may satisfy any one scheme
        |def ${dependencyName(names)}(
        |$params
        |) -> None:
        |    if $checks:
        |        return
        |    raise _unauthorized()""".stripMargin
