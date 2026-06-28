---
title: "Testing the compiler"
description: "How the compiler itself is held to a standard"
---

## 7. Testing strategy for the compiler itself

### 7.1 Unit tests

#### Parser tests (spec text -> expected AST)

```python
# tests/unit/test_parser.py
import pytest
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.parser.ast import *

class TestParseEntity:
    def test_simple_entity(self):
        ast = parse_spec_file("""
            service Test {
                entity Foo {
                    name: String
                    age: Int
                }
            }
        """)
        assert len(ast.entities) == 1
        entity = ast.entities[0]
        assert entity.name == "Foo"
        assert len(entity.fields) == 2
        assert entity.fields[0].name == "name"
        assert entity.fields[0].type_expr.name == "String"

    def test_entity_with_invariant(self):
        ast = parse_spec_file("""
            service Test {
                entity Code {
                    value: String
                    invariant: len(value) >= 6
                }
            }
        """)
        entity = ast.entities[0]
        assert len(entity.invariants) == 1
        inv = entity.invariants[0]
        assert inv.kind == ExprKind.BINARY_OP
        assert inv.binary_op == BinaryOp.GTE

    def test_parse_error_missing_brace(self):
        with pytest.raises(ParseError) as exc_info:
            parse_spec_file("""
                service Test {
                    entity Foo {
                        name: String
                    # missing closing brace
                }
            """)
        error = exc_info.value
        assert "expected '}'" in str(error)
        assert error.span.start_line > 0

class TestParseOperation:
    def test_operation_with_requires_ensures(self):
        ast = parse_spec_file("""
            service Test {
                state { items: String -> set Int }
                operation Add {
                    input: key: String, value: Int
                    requires: key not in items
                    ensures: items'[key] = value
                }
            }
        """)
        op = ast.operations[0]
        assert op.name == "Add"
        assert len(op.inputs) == 2
        assert len(op.requires) == 1
        assert len(op.ensures) == 1

class TestParseExpressions:
    def test_quantifier(self):
        expr = parse_expr("all x in store | valid(x)")
        assert expr.kind == ExprKind.QUANTIFIER
        assert expr.quantifier_kind == QuantifierKind.FORALL
        assert expr.quantifier_var == "x"

    def test_cardinality(self):
        expr = parse_expr("#store + 1")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.left.kind == ExprKind.CARDINALITY

    def test_pre_state(self):
        expr = parse_expr("code not in pre(store)")
        assert expr.kind == ExprKind.MEMBERSHIP
        assert expr.membership_negated == True
        assert expr.membership_collection.kind == ExprKind.PRE_STATE

    def test_post_state(self):
        expr = parse_expr("store'[code] = url")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.left.kind == ExprKind.INDEX
        assert expr.left.index_base.kind == ExprKind.POST_STATE

    def test_operator_precedence(self):
        # 'and' binds tighter than 'or'
        expr = parse_expr("a or b and c")
        assert expr.kind == ExprKind.BINARY_OP
        assert expr.binary_op == BinaryOp.OR
        assert expr.right.binary_op == BinaryOp.AND
```

#### Convention engine tests (IR -> expected HTTP mapping)

```python
# tests/unit/test_conventions_http.py
from spec_to_rest.conventions.http import HttpConventionEngine
from spec_to_rest.ir.types import *

class TestHttpMethodMapping:
    def test_operation_with_state_mutation_is_post(self):
        """Operation that adds to state -> POST."""
        op = OperationDecl(
            name="Shorten",
            inputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            outputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            requires=[],
            ensures=[
                # store'[code] = url  (state mutation)
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.INDEX,
                        index_base=Expr(kind=ExprKind.POST_STATE,
                            state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                        index_key=Expr(kind=ExprKind.VARIABLE, var_name="code")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="url")),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "POST"
        assert annotation.http_status_success == 201

    def test_operation_with_no_mutation_is_get(self):
        """Operation where store' = store -> GET."""
        op = OperationDecl(
            name="Resolve",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            requires=[],
            ensures=[
                # store' = store  (no mutation)
                Expr(kind=ExprKind.BINARY_OP, binary_op=BinaryOp.EQ,
                    left=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store")),
                    right=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "GET"
        assert annotation.http_status_success == 200

    def test_operation_with_removal_is_delete(self):
        """Operation where element removed from state -> DELETE."""
        op = OperationDecl(
            name="Delete",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[],
            requires=[],
            ensures=[
                # code not in store'
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=True,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.POST_STATE,
                        state_expr=Expr(kind=ExprKind.VARIABLE, var_name="store"))),
            ],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert annotation.http_method == "DELETE"
        assert annotation.http_status_success == 204

    def test_requires_membership_maps_to_404(self):
        """requires: code in store -> 404 when code not found."""
        op = OperationDecl(
            name="Resolve",
            inputs=[ParamDecl("code", TypeExpr(TypeKind.SIMPLE, name="ShortCode"))],
            outputs=[ParamDecl("url", TypeExpr(TypeKind.SIMPLE, name="LongURL"))],
            requires=[
                Expr(kind=ExprKind.MEMBERSHIP, membership_negated=False,
                    membership_element=Expr(kind=ExprKind.VARIABLE, var_name="code"),
                    membership_collection=Expr(kind=ExprKind.VARIABLE, var_name="store")),
            ],
            ensures=[],
        )
        engine = HttpConventionEngine()
        annotation = engine.annotate_operation(op)
        assert "not_found" in annotation.http_status_errors
        assert annotation.http_status_errors["not_found"] == 404
```

#### Emitter tests (IR -> expected code snippets)

```python
# tests/unit/test_emitter_python.py
from spec_to_rest.emit.engine import PythonFastAPIEmitter
from spec_to_rest.ir.types import *
from spec_to_rest.conventions.engine import ConventionAnnotations, OperationAnnotation

class TestPythonRouteEmission:
    def test_post_route(self):
        ir = make_test_ir()  # fixture helper
        annotations = ConventionAnnotations(
            operation_annotations={
                "Shorten": OperationAnnotation(
                    http_method="POST", http_path="/shorten",
                    http_status_success=201,
                    http_status_errors={"validation": 422},
                    request_body_model="ShortenRequest",
                    response_body_model="ShortenResponse",
                    path_params=[], query_params=[],
                    operation_kind="create", is_idempotent=False,
                    headers={},
                ),
            },
            entity_annotations={}, relation_annotations={},
        )
        emitter = PythonFastAPIEmitter(ir, annotations)
        code = emitter.emit_route("Shorten")

        assert '@app.post("/shorten", status_code=201)' in code
        assert "async def shorten(body: ShortenRequest) -> ShortenResponse:" in code
        assert "HTTPException" in code  # for validation errors
```

### 7.2 Integration tests

#### End-to-end test

```python
# tests/integration/test_end_to_end.py
import subprocess
import time
import requests
import pytest
from pathlib import Path

@pytest.fixture(scope="module")
def generated_service(tmp_path_factory):
    """Generate a service from a fixture spec and start it with docker-compose."""
    output_dir = tmp_path_factory.mktemp("generated")
    spec_path = Path(__file__).parent.parent / "fixtures" / "url_shortener.spec"

    # Generate
    result = subprocess.run(
        ["python", "-m", "spec_to_rest", "generate",
         "--target", "python-fastapi",
         "--output", str(output_dir),
         "--no-synthesis",  # use TODOs for speed in CI
         str(spec_path)],
        capture_output=True, text=True
    )
    assert result.returncode == 0, f"Generation failed: {result.stderr}"

    # Start service
    subprocess.run(
        ["docker-compose", "up", "-d", "--build"],
        cwd=str(output_dir),
        capture_output=True, text=True,
        check=True
    )

    # Wait for service to be ready
    for _ in range(30):
        try:
            resp = requests.get("http://localhost:8000/health")
            if resp.status_code == 200:
                break
        except requests.ConnectionError:
            time.sleep(1)
    else:
        pytest.fail("Service failed to start within 30 seconds")

    yield output_dir

    # Cleanup
    subprocess.run(
        ["docker-compose", "down", "-v"],
        cwd=str(output_dir),
        capture_output=True
    )

def test_generated_code_compiles(generated_service):
    """The generated Python code should have no syntax errors."""
    result = subprocess.run(
        ["python", "-m", "py_compile", "app/main.py"],
        cwd=str(generated_service),
        capture_output=True, text=True
    )
    assert result.returncode == 0

def test_openapi_spec_valid(generated_service):
    """The generated OpenAPI spec should be valid."""
    resp = requests.get("http://localhost:8000/openapi.json")
    assert resp.status_code == 200
    spec = resp.json()
    assert "paths" in spec
    assert "/shorten" in spec["paths"]

def test_crud_operations(generated_service):
    """Basic CRUD cycle should work."""
    # Create
    resp = requests.post("http://localhost:8000/shorten",
                         json={"url": "https://example.com"})
    assert resp.status_code == 201
    data = resp.json()
    code = data["code"]

    # Read
    resp = requests.get(f"http://localhost:8000/{code}",
                        allow_redirects=False)
    assert resp.status_code in (200, 302)

    # Delete
    resp = requests.delete(f"http://localhost:8000/{code}")
    assert resp.status_code == 204

    # Read after delete -> 404
    resp = requests.get(f"http://localhost:8000/{code}")
    assert resp.status_code == 404
```

### 7.3 Golden file tests

```python
# tests/golden/test_golden_files.py
import pytest
from pathlib import Path
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.ir import build_ir
from spec_to_rest.conventions import apply_conventions
from spec_to_rest.emit import emit_project

FIXTURES_DIR = Path(__file__).parent.parent / "fixtures"
EXPECTED_DIR = FIXTURES_DIR / "expected"

@pytest.mark.parametrize("spec_name", ["url_shortener", "todo_list"])
def test_golden_openapi(spec_name, snapshot):
    spec_text = (FIXTURES_DIR / f"{spec_name}.spec").read_text()
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    annotations = apply_conventions(ir, profile="python-fastapi")
    openapi = emit_openapi(ir, annotations)
    snapshot.assert_match(openapi, f"{spec_name}_openapi.yaml")

@pytest.mark.parametrize("spec_name", ["url_shortener", "todo_list"])
def test_golden_sql(spec_name, snapshot):
    spec_text = (FIXTURES_DIR / f"{spec_name}.spec").read_text()
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    annotations = apply_conventions(ir, profile="python-fastapi")
    sql = emit_sql(ir, annotations)
    snapshot.assert_match(sql, f"{spec_name}_migration.sql")
```

### 7.4 Property-based tests for the compiler

```python
# tests/unit/test_parser_properties.py
from hypothesis import given, strategies as st, assume
from spec_to_rest.parser import parse_spec_file
from spec_to_rest.ir import build_ir
from spec_to_rest.ir.printer import print_ir

# Strategy: generate random valid spec text
@st.composite
def valid_entity_name(draw):
    first = draw(st.sampled_from("ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
    rest = draw(st.text(alphabet="abcdefghijklmnopqrstuvwxyz0123456789_", min_size=0, max_size=20))
    return first + rest

@st.composite
def valid_field_type(draw):
    return draw(st.sampled_from(["String", "Int", "Float", "Bool", "DateTime"]))

@st.composite
def valid_entity(draw):
    name = draw(valid_entity_name())
    num_fields = draw(st.integers(min_value=1, max_value=5))
    fields = []
    used_names = set()
    for _ in range(num_fields):
        fname = draw(st.text(alphabet="abcdefghijklmnopqrstuvwxyz", min_size=1, max_size=10))
        assume(fname not in used_names)
        used_names.add(fname)
        ftype = draw(valid_field_type())
        fields.append(f"    {fname}: {ftype}")
    return f"  entity {name} {{\n" + "\n".join(fields) + "\n  }"

@st.composite
def valid_spec(draw):
    name = draw(valid_entity_name())
    num_entities = draw(st.integers(min_value=1, max_value=3))
    entities = [draw(valid_entity()) for _ in range(num_entities)]
    return f"service {name} {{\n" + "\n\n".join(entities) + "\n}"

@given(spec_text=valid_spec())
def test_parse_roundtrip(spec_text):
    """Any valid spec should parse without error."""
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    assert ir.name is not None
    assert len(ir.entities) >= 1

@given(spec_text=valid_spec())
def test_ir_serialization_roundtrip(spec_text):
    """IR -> JSON -> IR should be lossless."""
    ast = parse_spec_file(spec_text)
    ir = build_ir(ast)
    json_text = serialize_ir(ir)
    ir2 = deserialize_ir(json_text)
    assert ir.name == ir2.name
    assert len(ir.entities) == len(ir2.entities)
```
