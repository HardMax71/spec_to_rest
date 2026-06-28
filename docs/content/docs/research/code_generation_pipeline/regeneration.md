---
title: "Regeneration and extensibility"
description: "Incremental regeneration, protected regions, and adding a target"
---

## 8. Incremental regeneration

### 8.1 Diff-based regeneration

When the spec changes, the compiler does not regenerate everything blindly. It computes the diff
between the old IR and new IR and regenerates only affected files:

```text
old_ir = load("url_shortener.spec.v1")
new_ir = parse("url_shortener.spec.v2")
diff = compute_ir_diff(old_ir, new_ir)

for change in diff:
    affected_files = dependency_map[change.kind]
    for file in affected_files:
        regenerate(file, new_ir)
```

#### Dependency map

| Change Kind                 | Affected Files                                           |
| --------------------------- | -------------------------------------------------------- |
| New entity                  | models, schemas, validators, migration                   |
| Changed entity invariant    | validators, schemas, migration (CHECK constraint), tests |
| New operation               | routers, services, tests, OpenAPI                        |
| Changed operation pre/post  | services, validators, tests, OpenAPI                     |
| New state relation          | models, migration, database config                       |
| Changed convention override | routers (HTTP mapping), OpenAPI                          |
| Global invariant change     | validators, migration, tests                             |

### 8.2 Migration generation for schema changes

When a state relation changes, the compiler generates a new migration file (incrementing the version
number) rather than modifying the initial migration:

```text
# Spec change: add 'click_count: ShortCode -> Int' to state
# Generates: alembic/versions/002_add_click_count.py

def upgrade() -> None:
    op.add_column("store", sa.Column("click_count", sa.Integer(), server_default="0",
                                      nullable=False))

def downgrade() -> None:
    op.drop_column("store", "click_count")
```

### 8.3 Preserving manual modifications

Users may need to modify generated code. The compiler supports two strategies:

**Strategy 1: Protected Regions (default)**

Generated files contain marked regions:

```python
# === GENERATED CODE START — do not edit below this line ===
class StoreEntry(Base):
    __tablename__ = "store"
    code: Mapped[str] = mapped_column(String(10), primary_key=True)
    url: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
# === GENERATED CODE END ===

# === USER CODE START — your modifications here ===
# Add custom methods, properties, or class attributes below.
# This region is preserved during regeneration.

# === USER CODE END ===
```

During regeneration, the compiler replaces the content between `GENERATED CODE START` and
`GENERATED CODE END` while preserving everything between `USER CODE START` and `USER CODE END`.

**Strategy 2: Extension Files (recommended)**

Rather than modifying generated files, users create extension files:

```python
# app/extensions/url_shortener.py — never overwritten by the compiler

from app.services.url_shortener import UrlShortenerService

# Add custom methods to the service
async def get_top_urls(self, limit: int = 10):
    """Custom method not in the spec."""
    ...

# Monkey-patch or use dependency injection to add the method
UrlShortenerService.get_top_urls = get_top_urls
```

Extension files live in `app/extensions/` (or `internal/extensions/`, `src/extensions/`) and are
never touched by the compiler. The generated `main.py` auto-discovers and loads extensions at
startup.

### 8.4 Git integration

The compiler can optionally commit generated changes:

```bash
spec-to-rest generate --commit url_shortener.spec
```

This runs:

1. Generate all files
2. `git add` only generated files (tracked via `.spec-to-rest-manifest.json`)
3. `git commit -m "regen: update from spec change (added click_count state)"`
4. Show the diff summary

The manifest file tracks which files were generated, so the compiler knows what to `git add` and
what to leave alone.

## 9. Extensibility

### 9.1 Hook points

Generated services expose hook points at standard locations:

| Hook                 | When It Fires               | Use Case                                     |
| -------------------- | --------------------------- | -------------------------------------------- |
| `on_startup`         | Application boot            | Initialize external connections, warm caches |
| `on_shutdown`        | Application shutdown        | Close connections, flush buffers             |
| `before_request`     | Before each request handler | Logging, auth, rate limiting                 |
| `after_request`      | After each request handler  | Response logging, metrics                    |
| `before_{operation}` | Before the service method   | Custom validation, audit logging             |
| `after_{operation}`  | After the service method    | Notifications, cache invalidation            |
| `on_error`           | When an exception occurs    | Custom error responses, alerting             |

Hooks are registered via a hooks file:

```python
# app/hooks.py — user-defined, never overwritten

from app.hooks_registry import on_startup, before_request, after_shorten

@on_startup
async def init_redis():
    """Connect to Redis cache on startup."""
    ...

@before_request
async def check_api_key(request):
    """Validate API key header."""
    ...

@after_shorten
async def notify_analytics(result):
    """Send analytics event after URL shortening."""
    ...
```

### 9.2 Plugin system for custom code generators

Users can register custom code generator plugins. The shipped plugin shape is the
`DeploymentProfile` registry in
[`modules/profile/src/main/scala/specrest/profile/Registry.scala`](https://github.com/HardMax71/spec_to_rest/blob/main/modules/profile/src/main/scala/specrest/profile/Registry.scala);
new targets like Go/chi (#33) and TS/Express (#35) plug in as additional registry
entries plus a Handlebars template tree:

```scala
// modules/profile/src/main/scala/specrest/profile/KotlinSpring.scala (sketch)
package specrest.profile

object KotlinSpring:
  val profile: DeploymentProfile = DeploymentProfile(
    name = "kotlin-spring-postgres",
    displayName = "Kotlin + Spring Boot + PostgreSQL",
    language = "kotlin",
    framework = "spring",
    // ... typeMap, dependencies, etc.
  )

// Then register in Registry.scala:
object Registry:
  private val Profiles: Map[String, DeploymentProfile] = Map(
    "python-fastapi-postgres" -> PythonFastapi.profile,
    "kotlin-spring-postgres"  -> KotlinSpring.profile
  )
```

Templates live under `modules/codegen/src/main/resources/templates/<target-name>/`
and `modules/codegen/src/main/scala/specrest/codegen/Emit.scala` dispatches on the
profile's `name` field. There is no separate plugin-discovery mechanism today,
adding a target is a registry entry plus a template tree.

### 9.3 Custom endpoints

Users can add endpoints not in the spec using the extensions mechanism:

```python
# app/extensions/analytics.py

from fastapi import APIRouter

analytics_router = APIRouter(prefix="/analytics", tags=["analytics"])

@analytics_router.get("/top")
async def get_top_urls():
    """Custom endpoint: not in the spec, not overwritten by the compiler."""
    ...
```

The generated `main.py` auto-discovers routers in `app/extensions/`:

```python
# In generated main.py
import importlib
import pkgutil

for _, module_name, _ in pkgutil.iter_modules(["app/extensions"]):
    module = importlib.import_module(f"app.extensions.{module_name}")
    for attr_name in dir(module):
        attr = getattr(module, attr_name)
        if isinstance(attr, APIRouter):
            app.include_router(attr)
```

### 9.4 Overriding generated implementations

For any operation, users can replace the generated implementation:

```python
# app/overrides/url_shortener.py — user-provided, never overwritten

async def shorten_override(session, request):
    """Replace the generated Shorten implementation.

    The compiler-generated service will call this instead of its own logic
    when this override exists.
    """
    # Custom implementation using a counter-based scheme instead of random codes
    ...
```

The service layer checks for overrides at startup:

```python
class UrlShortenerService:
    def __init__(self, session):
        self._session = session
        # Check for user override
        try:
            from app.overrides.url_shortener import shorten_override
            self._shorten_impl = shorten_override
        except ImportError:
            self._shorten_impl = self._default_shorten

    async def shorten(self, request):
        return await self._shorten_impl(self._session, request)
```
