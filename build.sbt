ThisBuild / scalaVersion := "3.6.3"
ThisBuild / version      := "1.0.0-SNAPSHOT"
ThisBuild / organization := "dev.specrest"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-explain",
  "-Wunused:all",
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wsafe-init",
  "-Yexplicit-nulls"
)

ThisBuild / semanticdbEnabled := true

val circeVersion      = "0.14.10"
val munitVersion      = "1.0.3"
val munitCEVersion    = "2.2.0"
val antlrVersion      = "4.13.2"
val z3TurnkeyVersion  = "4.13.0.1"
val alloyVersion      = "6.2.0"
val handlebarsVersion = "4.3.1"
val declineVersion    = "2.6.2"
val apispecVersion    = "0.11.3"
val snakeYamlVersion  = "2.3"
val catsEffectVersion = "3.7.0"

lazy val commonMainDeps = Seq(
  "org.typelevel" %% "cats-effect" % catsEffectVersion
)

lazy val commonTestDeps = Seq(
  "org.scalameta" %% "munit"             % munitVersion   % Test,
  "org.typelevel" %% "munit-cats-effect" % munitCEVersion % Test
)

lazy val ir = (project in file("modules/ir"))
  .settings(
    name := "spec-ir",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val parser = (project in file("modules/parser"))
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
  .dependsOn(ir, parser % Test)
  .settings(
    name := "spec-convention",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

lazy val profile = (project in file("modules/profile"))
  .dependsOn(ir, convention, parser % Test)
  .settings(
    name := "spec-profile",
    libraryDependencies ++= commonMainDeps ++ commonTestDeps
  )

lazy val verify = (project in file("modules/verify"))
  .dependsOn(ir, parser % Test)
  .settings(
    name := "spec-verify",
    libraryDependencies ++= Seq(
      "tools.aqua"     % "z3-turnkey"                       % z3TurnkeyVersion,
      "io.circe"      %% "circe-core"                       % circeVersion,
      "org.alloytools" % "org.alloytools.alloy.application" % alloyVersion,
      "org.alloytools" % "org.alloytools.alloy.core"        % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.core"     % alloyVersion,
      "org.alloytools" % "org.alloytools.pardinus.native"   % alloyVersion
    ) ++ commonMainDeps ++ commonTestDeps
  )

lazy val codegen = (project in file("modules/codegen"))
  .dependsOn(ir, convention, profile, parser % Test)
  .settings(
    name := "spec-codegen",
    libraryDependencies ++= Seq(
      "com.github.jknack"              % "handlebars"    % handlebarsVersion,
      "com.softwaremill.sttp.apispec" %% "openapi-model" % apispecVersion,
      "org.yaml"                       % "snakeyaml"     % snakeYamlVersion
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
    scalacOptions := scalacOptions.value.filterNot(Set("-Yexplicit-nulls", "-Wnonunit-statement")),
    libraryDependencies ++= commonMainDeps
  )

lazy val cli = (project in file("modules/cli"))
  .dependsOn(ir, parser, convention, profile, verify, codegen)
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
    )
  )

lazy val root = (project in file("."))
  .aggregate(ir, parser, convention, profile, verify, codegen, cli, bench)
  .settings(
    name           := "spec-to-rest-root",
    publish / skip := true
  )
