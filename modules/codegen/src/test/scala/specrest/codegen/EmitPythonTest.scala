package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.DatabaseId
import specrest.profile.Fastapi
import specrest.profile.LanguageId
import specrest.profile.TargetKey

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class EmitPythonTest extends CatsEffectSuite:

  private def goldenRoot(db: DatabaseId): Path =
    Paths
      .get("fixtures/golden/codegen", TargetKey(LanguageId.Python, Fastapi.id, db).segments*)
      .resolve("url_shortener")

  private val dialectCases = List(
    DatabaseId.Postgres -> "python-fastapi-postgres",
    DatabaseId.Sqlite   -> "python-fastapi-sqlite",
    DatabaseId.Mysql    -> "python-fastapi-mysql"
  )

  dialectCases.foreach: (db, target) =>
    test(s"emitProject for $target matches the checked-in url_shortener golden"):
      SpecFixtures.loadProfiled("url_shortener", target).map: profiled =>
        val files           = Emit.emitProject(profiled).map(f => f.path -> f.content).toMap
        val expected        = walkGolden(goldenRoot(db))
        val missingInOutput = expected.keySet.diff(files.keySet)
        val extraInOutput   = files.keySet.diff(expected.keySet)
        assert(expected.nonEmpty, s"no golden tree at ${goldenRoot(db)}")
        assert(
          missingInOutput.isEmpty,
          s"emitter dropped golden files: ${missingInOutput.toList.sorted.mkString(", ")}"
        )
        assert(
          extraInOutput.isEmpty,
          s"emitter produced files not in golden: ${extraInOutput.toList.sorted.mkString(", ")}"
        )
        // `Create Date:` in the Alembic migration is `LocalDate.now` at emit time — an
        // inherently non-deterministic generated timestamp that must not be golden-compared
        // (it would only ever match on the exact day the golden was regenerated).
        def norm(s: String): String =
          s.replaceAll("(?m)^Create Date: .*$", "Create Date: <date>")
        expected.toList.sortBy(_._1).foreach: (rel, want) =>
          val got = files(rel)
          if norm(got) != norm(want) then
            fail(
              s"$rel diverges from golden\n--- expected ---\n$want\n--- got ---\n$got\n--- end ---"
            )

  private def walkGolden(root: Path): Map[String, String] =
    if !Files.isDirectory(root) then Map.empty
    else
      val stream = Files.walk(root)
      try
        import scala.jdk.CollectionConverters.*
        stream.iterator.asScala
          .filter(Files.isRegularFile(_))
          .map(p => root.relativize(p).toString.replace('\\', '/') -> Files.readString(p))
          .toMap
      finally stream.close()
