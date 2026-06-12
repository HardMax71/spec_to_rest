package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures

class AuthWiringTest extends CatsEffectSuite:

  private def emitted(name: String) =
    SpecFixtures.loadProfiled(name).map(p =>
      Emit.emitProject(p).map(f => f.path -> f.content).toMap
    )

  test("auth_service security.py emits a verifier per scheme plus the OR-combination"):
    emitted("auth_service").map: files =>
      val sec = files("app/security.py")
      assert(sec.contains("import jwt"), sec)
      assert(sec.contains("def require_bearer("), sec)
      assert(sec.contains("def require_api_key("), sec)
      assert(sec.contains("def require_bearer_or_api_key("), sec)
      assert(sec.contains("jwt.decode("), sec)
      assert(sec.contains("APIKeyHeader(name=\"X-API-Key\", auto_error=False)"), sec)
      assert(sec.contains("def require_admin("), "admin guard must survive")
      assert(sec.contains("if _check_bearer(bearer) or _check_api_key(api_key):"), sec)

  test("protected routes carry route-level dependencies; public routes do not"):
    emitted("auth_service").map: files =>
      val users = files("app/routers/users.py")
      assert(users.contains("dependencies=[Depends(require_bearer)]"), users)
      assert(users.contains("from app.security import require_bearer"), users)
      val sessions = files("app/routers/sessions.py")
      assert(sessions.contains("dependencies=[Depends(require_bearer_or_api_key)]"), sessions)
      // Register and Login are public
      val registerLine = users.linesIterator.find(_.contains("\"/auth/register\"")).getOrElse("")
      assert(!registerLine.contains("dependencies"), registerLine)
      val loginLine = sessions.linesIterator.find(_.contains("\"/auth/login\"")).getOrElse("")
      assert(!loginLine.contains("dependencies"), loginLine)

  test("config gains scheme settings and pyproject gains pyjwt only when needed"):
    emitted("auth_service").map: files =>
      val config = files("app/config.py")
      assert(config.contains("jwt_secret: SecretStr | None = None"), config)
      assert(config.contains("jwt_algorithm: str = \"HS256\""), config)
      assert(config.contains("auth_key_api_key: SecretStr | None = None"), config)
      assert(files("pyproject.toml").contains("pyjwt"), files("pyproject.toml"))
      val env = files(".env.example")
      assert(env.contains("JWT_SECRET="), env)
      assert(env.contains("AUTH_KEY_API_KEY="), env)

  test("url_shortener (no security block) emits no scheme code and no pyjwt"):
    emitted("url_shortener").map: files =>
      val sec = files("app/security.py")
      assert(sec.contains("def require_admin("), sec)
      assert(!sec.contains("_check_"), sec)
      assert(!sec.contains("import jwt"), sec)
      assert(!files("pyproject.toml").contains("pyjwt"), files("pyproject.toml"))
      assert(!files("app/config.py").contains("jwt_secret"), files("app/config.py"))
