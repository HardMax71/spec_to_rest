# Project Rules

## Formatting

- **No ASCII art diagrams.** Use Mermaid (or similar renderable notation) for diagrams and
  flowcharts instead of hand-drawn ASCII boxes and arrows.
- **All fenced code blocks must specify a language.** Use ` ```text ` when no specific language
  applies. Never leave the language identifier empty after the opening triple backticks.
- **No comments at the top of files.** No Scaladoc preambles, no block comments, no single-line
  summaries. The file name and exports are the documentation.
- **No inline comments that restate the code.** Only add a comment when the _why_ is non-obvious (a
  hidden invariant, a workaround for a specific library bug, a surprising constraint). If removing
  the comment would not confuse a future reader, do not write it.

## Imports

- **One import per line.** Follow the existing convention of explicit imports
  (`import cats.effect.IO`, `import cats.effect.Resource`) rather than wildcard imports. Exception:
  package-level alias imports already in use (e.g., `import scala.jdk.CollectionConverters.*`).
- **Scalafix `OrganizeImports` runs in CI.** After edits that add or rename imports, run
  `sbt scalafixAll` locally before pushing — CI rejects unorganized imports.

## Type Safety (Scala 3)

- **No `asInstanceOf` in application code.** Use pattern matching, `match` on sealed hierarchies, or
  restructure types so the compiler can infer them. Exception: conforming to Java/third-party APIs
  at FFI boundaries (e.g., `com.microsoft.z3.*` generic type erasure in
  `modules/verify/.../z3/Backend.scala`).
- **No `Any` in public signatures.** Use concrete types or union types. `Any` is only acceptable
  when interoperating with Java reflection / generic erasure at the FFI boundary.
- **No `null` in domain code.** Use `Option[A]`. `null` is only acceptable when calling Java APIs
  that accept `null` as a sentinel (e.g., Z3's `mkForall(..., null, null, null, null)`).
- **No defensive throws.** Do not add `require`/`assert`/`throw` guards for conditions that cannot
  happen given the type system's guarantees. Structural solutions (ADTs, `Either`,
  `boundary`/`break`) over speculative runtime checks.

## Cats Effect idioms (CE3)

- **Prefer `IO.blocking(body).onCancel(finalizer)` over `IO.async { cb => new Thread(...) }`** when
  all you need is an external-signal cancel hook. `onCancel` on `IO.blocking` fires concurrently
  with the still-blocked thread — sufficient for wrapping native code that has a thread-safe
  interrupt API (e.g., `ctx.interrupt()`).
- **Do not factor a private sync helper just to reduce nesting.** Inline the body inside
  `IO.blocking { ... }`. A 40-line block is fine; an artificial `private def checkSync` is not.
- **Single source of truth for config defaults.** `VerificationConfig.Default` is the only place
  timeout/scope/parallelism defaults live. Do not duplicate them at call sites.
- **Do not weaken test assertions to paper over flakiness.** Find and fix the root cause. If a
  test's invariant is already covered deterministically elsewhere, delete the redundant test rather
  than relaxing it.

## Testing

- **Prefer test parametrization over code duplication.** Use munit's
  `List(...).foreach: case => test(name): ...` pattern (see `modules/verify/.../ParallelTest.scala`,
  `modules/cli/.../CliSmokeTest.scala`) instead of copy-pasting nearly identical test cases.
- **Every new test extends `CatsEffectSuite`** (munit-cats-effect). Return `IO[Unit]` from test
  bodies; use `.assertEquals(...)` on `IO[A]` directly. No `unsafeRunSync` in test code.
- **Wall-clock / timing tests use generous tolerances.** CI machines vary — a test that passes in
  200 ms locally should assert `< 2000 ms`, not `< 250 ms`. Assert the _qualitative_ property
  (bounded, not unbounded), not the exact latency.

## Attribution

- **Never add "Co-Authored-By: Claude" (or any Claude/AI attribution) to commit messages or PR
  descriptions.** This is handled by settings — do not add it manually.
