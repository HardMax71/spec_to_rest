package specrest.codegen

class CodegenSmokeTest extends munit.FunSuite:

  private val engine = new TemplateEngine

  test("renders simple handlebars template"):
    val out = engine.render("Hello {{name}}!", java.util.Map.of("name", "world"))
    assertEquals(out, "Hello world!")

  test("snake_case helper"):
    val out = engine.render("{{snake_case name}}", java.util.Map.of("name", "UrlMapping"))
    assertEquals(out, "url_mapping")

  test("kebab_case / pascal_case / camel_case helpers"):
    val ctx = java.util.Map.of("name", "UrlMapping")
    assertEquals(engine.render("{{kebab_case name}}", ctx), "url-mapping")
    assertEquals(engine.render("{{pascal_case name}}", ctx), "UrlMapping")
    assertEquals(engine.render("{{camel_case name}}", ctx), "urlMapping")

  test("upper / lower / pluralize helpers"):
    val ctx = java.util.Map.of("name", "User")
    assertEquals(engine.render("{{upper name}}", ctx), "USER")
    assertEquals(engine.render("{{lower name}}", ctx), "user")
    assertEquals(engine.render("{{pluralize name}}", ctx), "Users")

  test("eq / ne / not helpers used in an #if block"):
    val ctx = java.util.Map.of("x", "foo")
    val tpl = """{{#if (eq x "foo")}}yes{{else}}no{{/if}}"""
    assertEquals(engine.render(tpl, ctx), "yes")

  test("Templates.pythonFastapiPostgres loads all 25 template files from resources"):
    val t = Templates.pythonFastapiPostgres
    assert(t.main.contains("FastAPI"))
    assert(t.pyproject.contains("[project]"))
    assert(t.dockerfile.contains("FROM"))
    assert(t.alembicIni.contains("[alembic]"))
    assert(t.makefile.nonEmpty)
    assert(t.modelEntity.nonEmpty)
    assert(t.schemaEntity.nonEmpty)
    assert(t.routerEntity.nonEmpty)

  test("RouteKind classification"):
    import specrest.convention.{EndpointSpec, HttpMethod, OperationKind}
    import specrest.profile.ProfiledOperation
    val endpoint = EndpointSpec(
      operationName = "Shorten",
      method = HttpMethod.POST,
      path = "/shorten",
      pathParams = Nil,
      queryParams = Nil,
      bodyParams = Nil,
      successStatus = 201
    )
    val op = ProfiledOperation(
      operationName = "Shorten",
      handlerName = "shorten",
      endpoint = endpoint,
      kind = OperationKind.Create,
      targetEntity = Some("UrlMapping"),
      requestBodyFields = Nil,
      responseFields = Nil
    )
    assertEquals(RouteKind.classify(op), RouteKind.Create)

    val redirectEndpoint = endpoint.copy(
      successStatus = 302,
      path = "/{code}",
      pathParams = List(specrest.convention.ParamSpec(
        "code",
        specrest.ir.TypeExpr.NamedType("String"),
        required = true
      ))
    )
    val redirectOp = op.copy(endpoint = redirectEndpoint, kind = OperationKind.Read)
    assertEquals(RouteKind.classify(redirectOp), RouteKind.Redirect)

  test("SensitiveFields.isSensitive"):
    assert(SensitiveFields.isSensitive("password"))
    assert(SensitiveFields.isSensitive("api_key"))
    assert(SensitiveFields.isSensitive("session_token"))
    assert(SensitiveFields.isSensitive("user_password"))
    assert(!SensitiveFields.isSensitive("email"))
    assert(!SensitiveFields.isSensitive("click_count"))
