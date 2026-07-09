package specrest.arch

import cats.effect.IO
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import munit.CatsEffectSuite

class ArchitectureTest extends CatsEffectSuite:

  private lazy val classes =
    ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("specrest")

  private def check(rule: ArchRule): IO[Unit] = IO(rule.check(classes))

  test("module dependencies follow the declared layering (mirrors build.sbt dependsOn)"):
    check(
      layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("ir").definedBy("specrest.ir..")
        .layer("parser").definedBy("specrest.parser..")
        .layer("convention").definedBy("specrest.convention..")
        .layer("dafny").definedBy("specrest.dafny..")
        .layer("profile").definedBy("specrest.profile..")
        .layer("verify").definedBy("specrest.verify..")
        .layer("codegen").definedBy("specrest.codegen..")
        .layer("synth").definedBy("specrest.synth..")
        .layer("testgen").definedBy("specrest.testgen..")
        .layer("lint").definedBy("specrest.lint..")
        .layer("bench").definedBy("specrest.bench..")
        .layer("cli").definedBy("specrest.cli..")
        .whereLayer("parser").mayOnlyBeAccessedByLayers("bench", "cli")
        .whereLayer("convention").mayOnlyBeAccessedByLayers(
          "profile",
          "codegen",
          "testgen",
          "lint",
          "cli"
        )
        .whereLayer("dafny").mayOnlyBeAccessedByLayers("synth", "cli")
        .whereLayer("profile").mayOnlyBeAccessedByLayers("codegen", "testgen", "cli")
        .whereLayer("verify").mayOnlyBeAccessedByLayers("bench", "cli")
        .whereLayer("codegen").mayOnlyBeAccessedByLayers("testgen", "cli")
        .whereLayer("synth").mayOnlyBeAccessedByLayers("cli")
        .whereLayer("testgen").mayOnlyBeAccessedByLayers("cli")
        .whereLayer("lint").mayOnlyBeAccessedByLayers("cli")
        .whereLayer("bench").mayNotBeAccessedByAnyLayer()
        .whereLayer("cli").mayNotBeAccessedByAnyLayer()
    )

  test("the specrest package graph is free of cycles"):
    check(slices().matching("specrest.(*)..").should().beFreeOfCycles())

  test("verify (the trusted soundness core) must not depend on downstream layers"):
    check(
      noClasses()
        .that()
        .resideInAPackage("specrest.verify..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
          "specrest.convention..",
          "specrest.codegen..",
          "specrest.testgen..",
          "specrest.synth..",
          "specrest.cli..",
          "specrest.profile..",
          "specrest.lint..",
          "specrest.dafny.."
        )
    )

  test("convention must not reach into downstream layers"):
    check(
      noClasses()
        .that()
        .resideInAPackage("specrest.convention..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
          "specrest.profile..",
          "specrest.codegen..",
          "specrest.testgen..",
          "specrest.synth..",
          "specrest.cli..",
          "specrest.dafny..",
          "specrest.lint.."
        )
    )
