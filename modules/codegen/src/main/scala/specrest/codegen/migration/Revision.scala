package specrest.codegen.migration

import specrest.profile.Registry

import java.nio.file.Files
import java.nio.file.Path
import scala.jdk.CollectionConverters.*

object Revision:

  private val NumericPrefix = """([0-9]+)_.*""".r

  def discover(outDir: Path, target: String): List[String] =
    Registry.resolveSlug(target).toOption.map(_.migrationTool) match
      case Some("alembic") =>
        scanFiles(outDir.resolve("alembic/versions"), suffix = ".py")
      case Some("golang-migrate") =>
        scanFiles(outDir.resolve("migrations"), suffix = ".up.sql")
      case Some("prisma-migrate") =>
        scanSubdirs(outDir.resolve("prisma/migrations"))
      case _ => Nil

  def next(existing: List[String]): String =
    val n = existing.flatMap(_.toIntOption).maxOption.getOrElse(0) + 1
    f"$n%03d"

  def head(existing: List[String]): Option[String] =
    existing.flatMap(_.toIntOption).maxOption.map(n => f"$n%03d")

  private def scanFiles(dir: Path, suffix: String): List[String] =
    if !Files.isDirectory(dir) then Nil
    else
      val stream = Files.list(dir)
      try
        stream
          .iterator()
          .asScala
          .map(_.getFileName.toString)
          .filter(_.endsWith(suffix))
          .flatMap:
            case NumericPrefix(num) => Some(num)
            case _                  => None
          .toList
          .distinct
          .sorted
      finally stream.close()

  private def scanSubdirs(dir: Path): List[String] =
    if !Files.isDirectory(dir) then Nil
    else
      val stream = Files.list(dir)
      try
        stream
          .iterator()
          .asScala
          .filter(Files.isDirectory(_))
          .map(_.getFileName.toString)
          .flatMap:
            case NumericPrefix(num) => Some(num)
            case _                  => None
          .toList
          .distinct
          .sorted
      finally stream.close()
