package specrest.testgen

import munit.FunSuite

class TemplatesTest extends FunSuite:

  test("conftest template loads and contains the admin-availability fixture"):
    assert(Templates.conftest.contains("_admin_endpoint_available"))
    assert(Templates.conftest.contains("/__test_admin__/reset"))
    assert(Templates.conftest.contains("ENABLE_TEST_ADMIN"))

  test("predicates template provides is_valid_uri and is_valid_email helpers"):
    assert(Templates.predicates.contains("def is_valid_uri"))
    assert(Templates.predicates.contains("def is_valid_email"))
    assert(Templates.predicates.contains("urlparse"))

  test("pytest.ini disables xdist parallelism (matches plan risk #4 mitigation)"):
    assert(Templates.pytestIni.contains("-p no:xdist"))
