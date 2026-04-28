package specrest.testgen

import specrest.codegen.EmittedFile
import specrest.convention.Naming
import specrest.profile.ProfiledService

object TestEmit:

  def emit(profiled: ProfiledService): List[EmittedFile] =
    val ir             = profiled.ir
    val serviceSnake   = Naming.toSnakeCase(ir.name)
    val strategySpecs  = Strategies.forIR(ir)
    val behavioralOut  = Behavioral.emitFor(profiled)
    val statefulOut    = Stateful.emitFor(profiled)
    val structuralOut  = Structural.emitFor(profiled)
    val adminRouterSrc = AdminRouter.emit(profiled)
    val strategiesPy   = renderStrategiesFile(strategySpecs)
    val behavioralPy   = renderBehavioralFile(behavioralOut.tests, ir.name, strategySpecs)
    val skipsJson =
      renderSkipsJson(
        ir.name,
        strategySpecs,
        behavioralOut.skips ++ statefulOut.skips,
        structuralOut.skips
      )

    List(
      EmittedFile(FilePaths.AdminRouterFile, adminRouterSrc),
      EmittedFile(FilePaths.TestsInitFile, ""),
      EmittedFile(FilePaths.ConftestFile, Templates.conftest),
      EmittedFile(FilePaths.PredicatesFile, Templates.predicates(ir)),
      EmittedFile(FilePaths.PytestIniFile, Templates.pytestIni),
      EmittedFile(FilePaths.StrategiesFile, strategiesPy),
      EmittedFile(FilePaths.behavioralTestFile(serviceSnake), behavioralPy),
      EmittedFile(FilePaths.statefulTestFile(serviceSnake), statefulOut.file),
      EmittedFile(FilePaths.structuralTestFile(serviceSnake), structuralOut.file),
      EmittedFile(FilePaths.RunConformanceFile, Templates.runConformance),
      EmittedFile(FilePaths.SkipsFile, skipsJson)
    )

  private def renderStrategiesFile(specs: List[StrategySpec]): String =
    val header =
      """|\"\"\"Auto-generated Hypothesis strategies. Do not edit by hand.\"\"\"
         |from hypothesis import strategies as st
         |
         |from tests.predicates import is_valid_email, is_valid_uri
         |
         |""".stripMargin.replace("\\\"", "\"")

    if specs.isEmpty then header + "# No strategies generated for this service.\n"
    else
      val funcs = specs
        .map: spec =>
          val skipNote =
            if spec.skipped.isEmpty then ""
            else
              spec.skipped.map(s => s"# testgen-skip: $s").mkString("", "\n", "\n")
          s"${skipNote}def ${spec.functionName}():\n    return ${spec.body}\n"
        .mkString("\n")
      header + funcs

  private def renderBehavioralFile(
      tests: List[GeneratedTest],
      serviceName: String,
      strategies: List[StrategySpec]
  ): String =
    val strategyImport =
      if strategies.isEmpty then ""
      else
        val names = strategies.map(_.functionName).sorted.mkString(",\n    ")
        s"""|from tests.strategies import (
            |    $names,
            |)
            |
            |""".stripMargin

    val header =
      s"""|\"\"\"Auto-generated behavioral tests for $serviceName.
         |
         |Each ensures clause that ExprToPython could translate produces a positive
         |property test. Each `<input> in <state>` requires clause produces a
         |negative test asserting the missing-key path returns 4xx. Each global
         |invariant produces a post-operation check. See tests/_testgen_skips.json
         |for clauses that were not turned into tests.
         |\"\"\"
         |import datetime
         |import re
         |
         |from hypothesis import HealthCheck, assume, given, settings
         |from hypothesis import strategies as st
         |
         |from tests.conftest import client
         |from tests.predicates import is_valid_email, is_valid_uri
         |
         |""".stripMargin.replace("\\\"", "\"")

    val combined = header + strategyImport
    if tests.isEmpty then combined + "# No tests generated; see _testgen_skips.json.\n"
    else combined + tests.map(_.body).mkString("\n\n")

  private def renderSkipsJson(
      serviceName: String,
      strategies: List[StrategySpec],
      behavioralSkips: List[TestSkip],
      structuralSkips: List[TestSkip]
  ): String =
    val strategyEntries = strategies.flatMap: spec =>
      spec.skipped.map(reason =>
        s"""|    {"type": ${jsonString(spec.typeName)}, "reason": ${jsonString(reason)}}"""
      )
    val skipEntry = (s: TestSkip) =>
      s"""|    {"operation": ${jsonString(s.operation)}, "kind": ${jsonString(
          s.kind
        )}, "reason": ${jsonString(s.reason)}}"""
    val behavioralEntries = behavioralSkips.map(skipEntry)
    val structuralEntries = structuralSkips.map(skipEntry)

    val strategiesArr = renderJsonArray(strategyEntries)
    val behavioralArr = renderJsonArray(behavioralEntries)
    val structuralArr = renderJsonArray(structuralEntries)
    s"""|{
        |  "version": 1,
        |  "service": ${jsonString(serviceName)},
        |  "strategies_skipped": $strategiesArr,
        |  "behavioral_skipped": $behavioralArr,
        |  "structural_skipped": $structuralArr
        |}
        |""".stripMargin

  private def renderJsonArray(entries: List[String]): String =
    if entries.isEmpty then "[]"
    else entries.mkString("[\n", ",\n", "\n  ]")

  private def jsonString(s: String): String =
    val sb = new StringBuilder
    sb.append('"')
    s.foreach:
      case '"'                  => sb.append("\\\"")
      case '\\'                 => sb.append("\\\\")
      case '\n'                 => sb.append("\\n")
      case '\r'                 => sb.append("\\r")
      case '\t'                 => sb.append("\\t")
      case c if c < 0x20.toChar => sb.append(f"\\u${c.toInt}%04x")
      case c                    => sb.append(c)
    sb.append('"')
    sb.toString
