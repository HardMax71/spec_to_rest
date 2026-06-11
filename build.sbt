import scala.jdk.CollectionConverters.*

ThisBuild / scalaVersion := "3.6.3"
ThisBuild / organization := "dev.specrest"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-explain",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wsafe-init",
  "-Wshadow:all",
  "-Yexplicit-nulls",
  "-language:strictEquality"
) ++ (if (sys.env.contains("CI")) Seq("-Werror") else Seq.empty)

ThisBuild / semanticdbEnabled := true

ThisBuild / scalafixDependencies ++= Seq(
  "org.typelevel" %% "typelevel-scalafix-cats-effect" % "0.5.0",
  "org.typelevel" %% "typelevel-scalafix-cats"        % "0.5.0"
)

// Skip scalafix on Isabelle/ANTLR-generated sources — hand-written lint rules don't apply
// to mechanically-generated code (mirrors the coverageExcludedPackages exclusion below).
// Applied per-project where the generated/ directory exists.
val skipGeneratedScalafix = Seq(
  Compile / scalafix / unmanagedSources :=
    (Compile / scalafix / unmanagedSources).value
      .filterNot(_.toPath.iterator.asScala.exists(_.toString == "generated"))
)

import wartremover.Wart
import wartremover.WartRemover.autoImport.*

val strictWarts = Seq(
  Wart.Null,
  Wart.Var,
  Wart.Return,
  Wart.OptionPartial,
  Wart.EitherProjectionPartial,
  Wart.TryPartial,
  Wart.AsInstanceOf,
  Wart.IsInstanceOf,
  Wart.JavaSerializable,
  Wart.StringPlusAny
)

ThisBuild / wartremoverErrors ++= strictWarts

val noTestWarts = Seq(
  Test / wartremoverErrors := Seq.empty,
  Test / scalacOptions := (Test / scalacOptions).value
    .filterNot(_.startsWith("-P:wartremover:"))
)

ThisBuild / coverageMinimumStmtTotal   := 50
ThisBuild / coverageMinimumBranchTotal := 50
ThisBuild / coverageFailOnMinimum      := true
ThisBuild / coverageHighlighting       := true
ThisBuild / coverageExcludedPackages := List(
  "specrest\\.parser\\.generated\\..*",
  "specrest\\.ir\\.generated\\..*",
  ".*\\.bench\\..*"
).mkString(";")

val circeVersion            = "0.14.10"
val munitVersion            = "1.0.3"
val munitCEVersion          = "2.2.0"
val scalacheckEffectVersion = "2.1.0"
val antlrVersion            = "4.13.2"
val z3TurnkeyVersion        = "4.13.0.1"
val alloyVersion            = "6.2.0"
val handlebarsVersion       = "4.3.1"
val declineVersion          = "2.6.2"
val apispecVersion          = "0.11.3"
val snakeYamlVersion        = "2.3"
val catsEffectVersion       = "3.7.0"
val jacksonCoreVersion      = "2.18.6"
val anthropicJavaVersion    = "2.30.0"
val openaiJavaVersion       = "4.35.0"

// Scala 3.6.3's scaladoc toolchain still resolves jackson-core 2.15.1.
ThisBuild / dependencyOverrides += "com.fasterxml.jackson.core" % "jackson-core" % jacksonCoreVersion

lazy val commonMainDeps = Seq(
  "org.typelevel" %% "cats-effect" % catsEffectVersion
)

lazy val commonTestDeps = Seq(
  "org.scalameta" %% "munit"             % munitVersion   % Test,
  "org.typelevel" %% "munit-cats-effect" % munitCEVersion % Test
)

// The `dependsOn` graph below is the module architecture; it is asserted explicitly by
// modules/arch/ArchitectureTest (`sbt arch/test`). Adding a module or changing a `dependsOn`
// requires updating that test's layeredArchitecture rule. See CONTRIBUTING.md.
lazy val ir = (project in file("modules/ir"))
  .settings(noTestWarts *)
  .settings(skipGeneratedScalafix *)
  .settings(
    name := "spec-ir",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val parser = (project in file("modules/parser"))
  .settings(noTestWarts *)
  .settings(skipGeneratedScalafix *)
  .dependsOn(ir)
  .enablePlugins(Antlr4Plugin)
  .settings(
    name                       := "spec-parser",
    Antlr4 / antlr4Version     := antlrVersion,
    Antlr4 / antlr4PackageName := Some("specrest.parser.generated"),
    Antlr4 / antlr4GenListener := false,
    Antlr4 / antlr4GenVisitor  := true,
    libraryDependencies ++= Seq(
      "org.antlr" % "antlr4-runtime" % antlrVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val convention = (project in file("modules/convention"))
  .settings(noTestWarts *)
  .dependsOn(ir, parser % Test)
  .settings(
    name := "spec-convention",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

// Dafny codegen is its own module (not under `convention`) so `synth`/`cli` use it
// without depending on the REST/SQL convention engine; shared utilities `Naming`/
// `Builtins` likewise live in `ir` (the foundation), not `convention`. `Schema`/
// `Classify` deliberately stay in `convention` — moving them would create a
// `profile`/`codegen` dependency cycle. Main deps are `ir` only; the golden tests
// reuse convention's `SpecFixtures` in test scope.
lazy val dafny = (project in file("modules/dafny"))
  .settings(noTestWarts *)
  .dependsOn(ir, convention % "test->test")
  .settings(
    name := "spec-dafny",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

lazy val lint = (project in file("modules/lint"))
  .settings(noTestWarts *)
  .dependsOn(ir, parser % Test)
  .settings(
    name := "spec-lint",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

lazy val profile = (project in file("modules/profile"))
  .settings(noTestWarts *)
  .dependsOn(ir, convention, parser % Test)
  .settings(
    name := "spec-profile",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

lazy val verify = (project in file("modules/verify"))
  .settings(noTestWarts *)
  .dependsOn(ir, parser % Test)
  .settings(
    name := "spec-verify",
    libraryDependencies ++= Seq(
      "tools.aqua"     % "z3-turnkey"                       % z3TurnkeyVersion,
      "io.circe"      %% "circe-core"                       % circeVersion,
      "org.alloytools" % "org.alloytools.alloy.application" % alloyVersion,
      "org.alloytools" % "org.alloytools.alloy.core"        % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.core"     % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.native"   % alloyVersion,
      "org.typelevel" %% "scalacheck-effect-munit"          % scalacheckEffectVersion % Test
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val codegen = (project in file("modules/codegen"))
  .settings(noTestWarts *)
  .dependsOn(ir, convention, profile, parser % Test)
  .settings(
    name := "spec-codegen",
    libraryDependencies ++= Seq(
      "com.github.jknack"              % "handlebars"    % handlebarsVersion,
      "com.softwaremill.sttp.apispec" %% "openapi-model" % apispecVersion,
      "org.yaml"                       % "snakeyaml"     % snakeYamlVersion,
      "io.circe"                      %% "circe-core"    % circeVersion,
      "io.circe"                      %% "circe-generic" % circeVersion,
      "io.circe"                      %% "circe-parser"  % circeVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val synth = (project in file("modules/synth"))
  .settings(noTestWarts *)
  .dependsOn(ir, dafny, convention % Test, parser % Test)
  .settings(
    name := "spec-synth",
    libraryDependencies ++= Seq(
      "com.anthropic" % "anthropic-java" % anthropicJavaVersion,
      "com.openai"    % "openai-java"    % openaiJavaVersion,
      "io.circe"     %% "circe-core"     % circeVersion,
      "io.circe"     %% "circe-generic"  % circeVersion,
      "io.circe"     %% "circe-parser"   % circeVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val testgen = (project in file("modules/testgen"))
  .settings(noTestWarts *)
  .dependsOn(ir, convention, profile, codegen, parser % Test)
  .settings(
    name := "spec-testgen",
    libraryDependencies ++= Seq(
      "com.github.jknack" % "handlebars" % handlebarsVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val bench = (project in file("modules/bench"))
  .dependsOn(ir, parser, verify)
  .enablePlugins(JmhPlugin)
  .settings(
    name           := "spec-bench",
    publish / skip := true,
    // JMH @State fields need default-initialization (`_` / `uninitialized`); drop
    // explicit-nulls for the bench module to keep state classes idiomatic.
    scalacOptions   := scalacOptions.value.filterNot(Set("-Yexplicit-nulls", "-Wnonunit-statement")),
    coverageEnabled := false,
    libraryDependencies ++= commonMainDeps
  )

lazy val cli = (project in file("modules/cli"))
  .settings(noTestWarts *)
  .dependsOn(ir, parser, convention, dafny, profile, verify, codegen, testgen, lint, synth)
  .enablePlugins(NativeImagePlugin)
  .settings(
    name                := "spec-to-rest",
    Compile / mainClass := Some("specrest.cli.Main"),
    libraryDependencies ++= Seq(
      "com.monovore"  %% "decline"                          % declineVersion,
      "com.monovore"  %% "decline-effect"                   % declineVersion,
      "org.alloytools" % "org.alloytools.alloy.application" % alloyVersion,
      "org.alloytools" % "org.alloytools.alloy.core"        % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.core"     % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.native"   % alloyVersion
    ) ++ commonMainDeps ++ commonTestDeps,
    nativeImageInstalled := true,
    nativeImageOptions ++= Seq(
      "--no-fallback",
      // The analysis peak grew past the builder's AUTO heap cap with the June
      // 2026 verify-layer lifts (May 28: 7.15 GB peak under an 8.3 GB cap;
      // June 4+: OOM under 11.8 GB). The auto cap also scales DOWN with
      // --parallelism (50% of RAM at 2 threads), so it cannot be tuned from
      // here alone: CI sets NATIVE_IMAGE_MAX_HEAP per runner class and adds a
      // swapfile for headroom; local builds keep the auto policy. Parallelism
      // stays halved to keep the working set flat - the output binary is
      // identical either way.
      "--parallelism=2",
      // Required by GraalVM 23+ for the -H: options below; on 21 it's
      // accepted as a no-op and silences the "experimental option" warnings.
      "-H:+UnlockExperimentalVMOptions",
      "-H:+ReportExceptionStackTraces",
      // Bundle z3-turnkey's native libraries so libz3.so / libz3java.so can be
      // extracted at runtime on the target OS. Pattern covers linux + osx + windows.
      "-H:IncludeResources=com/microsoft/z3/linux/amd64/.*",
      "-H:IncludeResources=com/microsoft/z3/linux/aarch64/.*",
      "-H:IncludeResources=com/microsoft/z3/osx/amd64/.*",
      "-H:IncludeResources=com/microsoft/z3/osx/aarch64/.*",
      "-H:IncludeResources=com/microsoft/z3/windows/amd64/.*",
      // Z3's Native class uses JNI + reflection. Initialize at runtime so the
      // shared library can be loaded per-execution (build-time init fails since
      // the .so isn't available until the binary extracts it on first call).
      "--initialize-at-run-time=com.microsoft.z3.Native",
      "--initialize-at-run-time=com.microsoft.z3.Version"
    ) ++ sys.env.get("NATIVE_IMAGE_MAX_HEAP").filter(_.nonEmpty).map(m => s"-J-Xmx$m").toSeq
  )

// Test-only module: depends on every other module so ArchUnit can analyze the whole
// codebase's bytecode in one place and assert the module layering (mirrors the
// `dependsOn` graph above) + package-cycle-freedom. No main sources.
lazy val arch = (project in file("modules/arch"))
  .settings(noTestWarts *)
  .dependsOn(
    ir,
    parser,
    convention,
    dafny,
    profile,
    verify,
    codegen,
    synth,
    testgen,
    lint,
    bench,
    cli
  )
  .settings(
    name            := "spec-arch",
    publish / skip  := true,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "com.tngtech.archunit" % "archunit" % "1.3.0" % Test
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val root = (project in file("."))
  .aggregate(
    ir,
    parser,
    convention,
    dafny,
    profile,
    verify,
    codegen,
    testgen,
    lint,
    synth,
    cli,
    bench,
    arch
  )
  .settings(
    name           := "spec-to-rest-root",
    publish / skip := true
  )
