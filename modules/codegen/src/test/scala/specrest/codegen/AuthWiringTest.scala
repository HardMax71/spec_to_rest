package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures

class AuthWiringTest extends CatsEffectSuite:

  private def emitted(name: String, target: String = "python-fastapi-postgres") =
    SpecFixtures
      .loadProfiled(name, target)
      .map(p => Emit.emitProject(p).map(f => f.path -> f.content).toMap)

  test("auth_service security.py emits a verifier per scheme plus the OR-combination"):
    emitted("auth_service").map: files =>
      val sec = files("app/security.py")
      assert(sec.contains("import jwt"), sec)
      assert(sec.contains("def require_bearer("), sec)
      assert(sec.contains("def require_api_key("), sec)
      assert(sec.contains("def require_api_key_or_bearer("), sec)
      assert(sec.contains("jwt.decode("), sec)
      assert(sec.contains("APIKeyHeader(name=\"X-API-Key\", auto_error=False)"), sec)
      assert(sec.contains("def require_admin("), "admin guard must survive")
      assert(sec.contains("if _check_api_key(api_key) or _check_bearer(bearer):"), sec)

  test("protected routes carry route-level dependencies; public routes do not"):
    emitted("auth_service").map: files =>
      val users = files("app/routers/users.py")
      assert(users.contains("dependencies=[Depends(require_bearer)]"), users)
      assert(users.contains("from app.security import require_bearer"), users)
      val sessions = files("app/routers/sessions.py")
      assert(sessions.contains("dependencies=[Depends(require_api_key_or_bearer)]"), sessions)
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

  test("go: schemes.go middlewares, route guards, config fields, go.mod jwt"):
    emitted("auth_service", "go-chi-postgres").map: files =>
      val schemes = files("internal/auth/schemes.go")
      assert(schemes.contains("func RequireBearer(cfg *config.Config)"), schemes)
      assert(schemes.contains("func RequireApiKey(cfg *config.Config)"), schemes)
      assert(schemes.contains("func RequireApiKeyOrBearer(cfg *config.Config)"), schemes)
      assert(schemes.contains("jwt.Parse("), schemes)
      assert(schemes.contains("checkApiKey(cfg, r) || checkBearer(cfg, r)"), schemes)
      val mainGo = files("cmd/server/main.go")
      assert(mainGo.contains("r.With(auth.RequireBearer(cfg)).Post(\"/auth/logout\""), mainGo)
      assert(
        mainGo.contains("r.With(auth.RequireApiKeyOrBearer(cfg)).Post(\"/auth/refresh\""),
        mainGo
      )
      assert(mainGo.contains("r.Post(\"/auth/register\""), mainGo)
      val config = files("internal/config/config.go")
      assert(config.contains("JwtSecret"), config)
      assert(config.contains("AuthKeyApiKey"), config)
      assert(files("go.mod").contains("github.com/golang-jwt/jwt/v5"), files("go.mod"))

  test("ts: schemes.ts middlewares, route guards, config, package.json jwt"):
    emitted("auth_service", "ts-express-postgres").map: files =>
      val schemes = files("src/middleware/schemes.ts")
      assert(schemes.contains("export const requireBearer"), schemes)
      assert(schemes.contains("export const requireApiKey"), schemes)
      assert(schemes.contains("export const requireApiKeyOrBearer"), schemes)
      assert(schemes.contains("jwt.verify("), schemes)
      val users = files("src/routes/users.ts")
      assert(users.contains("requireBearer,"), users)
      val config = files("src/config.ts")
      assert(config.contains("JWT_SECRET: z.string().optional(),"), config)
      assert(config.contains("jwtSecret: parsed.JWT_SECRET,"), config)
      assert(files("package.json").contains("\"jsonwebtoken\""), files("package.json"))
      assert(files("package.json").contains("@types/jsonwebtoken"), files("package.json"))

  test("go/ts: url_shortener emits no scheme artifacts"):
    for
      goFiles <- emitted("url_shortener", "go-chi-postgres")
      tsFiles <- emitted("url_shortener", "ts-express-postgres")
    yield
      assert(!goFiles.contains("internal/auth/schemes.go"))
      assert(!tsFiles.contains("src/middleware/schemes.ts"))
      assert(!goFiles("go.mod").contains("golang-jwt"), goFiles("go.mod"))
      assert(!tsFiles("package.json").contains("jsonwebtoken"), tsFiles("package.json"))

  test("credential slots agree across .env, python settings, go config, ts schema/config"):
    val source =
      """|service Demo {
         |  state {
         |    count: Int
         |  }
         |
         |  security {
         |    jwt: Bearer(bearer_format: "JWT")
         |    token: Bearer
         |    api_key: ApiKey(header: "X-API-Key")
         |    login: Basic
         |  }
         |}
         |""".stripMargin
    SpecFixtures.buildFromSource("slot-service", source).map: ir =>
      assertEquals(
        AuthSchemes.envEntries(ir).map(_._1),
        List(
          "JWT_SECRET",
          "JWT_ALGORITHM",
          "AUTH_TOKEN_TOKEN",
          "AUTH_KEY_API_KEY",
          "AUTH_BASIC_LOGIN_USERNAME",
          "AUTH_BASIC_LOGIN_PASSWORD"
        )
      )
      assertEquals(
        specrest.codegen.python.SecurityPython.settingLines(ir),
        List(
          "jwt_secret: SecretStr | None = None",
          "jwt_algorithm: str = \"HS256\"",
          "auth_token_token: SecretStr | None = None",
          "auth_key_api_key: SecretStr | None = None",
          "auth_basic_login_username: str | None = None",
          "auth_basic_login_password: SecretStr | None = None"
        )
      )
      assertEquals(
        specrest.codegen.go.SecurityGo.configLines(ir),
        List(
          List("JwtSecret", "string", "`env:\"JWT_SECRET\" envDefault:\"\"`"),
          List("JwtAlgorithm", "string", "`env:\"JWT_ALGORITHM\" envDefault:\"HS256\"`"),
          List("AuthTokenToken", "string", "`env:\"AUTH_TOKEN_TOKEN\" envDefault:\"\"`"),
          List("AuthKeyApiKey", "string", "`env:\"AUTH_KEY_API_KEY\" envDefault:\"\"`"),
          List(
            "AuthBasicLoginUsername",
            "string",
            "`env:\"AUTH_BASIC_LOGIN_USERNAME\" envDefault:\"\"`"
          ),
          List(
            "AuthBasicLoginPassword",
            "string",
            "`env:\"AUTH_BASIC_LOGIN_PASSWORD\" envDefault:\"\"`"
          )
        )
      )
      assertEquals(
        specrest.codegen.ts.SecurityTs.schemaLines(ir),
        List(
          "JWT_SECRET: z.string().optional(),",
          "JWT_ALGORITHM: z.string().default('HS256'),",
          "AUTH_TOKEN_TOKEN: z.string().optional(),",
          "AUTH_KEY_API_KEY: z.string().optional(),",
          "AUTH_BASIC_LOGIN_USERNAME: z.string().optional(),",
          "AUTH_BASIC_LOGIN_PASSWORD: z.string().optional(),"
        )
      )
      assertEquals(
        specrest.codegen.ts.SecurityTs.configLines(ir),
        List(
          "jwtSecret: parsed.JWT_SECRET,",
          "jwtAlgorithm: parsed.JWT_ALGORITHM,",
          "authTokenToken: parsed.AUTH_TOKEN_TOKEN,",
          "authKeyApiKey: parsed.AUTH_KEY_API_KEY,",
          "authBasicLoginUsername: parsed.AUTH_BASIC_LOGIN_USERNAME,",
          "authBasicLoginPassword: parsed.AUTH_BASIC_LOGIN_PASSWORD,"
        )
      )
