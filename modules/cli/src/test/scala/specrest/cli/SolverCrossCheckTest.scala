package specrest.cli

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import io.circe.parser
import munit.CatsEffectSuite

import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class SolverCrossCheckTest extends CatsEffectSuite:

  override def munitIOTimeout: Duration = 8.minutes

  private def log: Logger = Logger.fromFlags(verbose = false, quiet = true)
  private val devnull     = new PrintStream(OutputStream.nullOutputStream())
  private val alloyJar: String =
    sys.env.getOrElse("ALLOY_JAR", s"${sys.props("user.home")}/.cache/alloy/alloy.jar")

  final private case class Entry(id: String, tool: String, rawStatus: String, file: String)
  final private case class Result(
      spec: String,
      entry: Entry,
      z3: Option[String],
      cvc5: Option[String],
      alloy: Option[String]
  )

  private def commandOk(cmd: String*): Boolean =
    try
      val p = new ProcessBuilder(cmd*)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
      p.getOutputStream.close()
      if !p.waitFor(30, TimeUnit.SECONDS) then { p.destroyForcibly(); false }
      else p.exitValue() == 0
    catch case _: java.io.IOException => false

  private def solversPresent: Boolean =
    commandOk("z3", "--version") && commandOk("cvc5", "--version") && Files.exists(
      Paths.get(alloyJar)
    )

  private def smtVerdict(cmd: List[String], file: Path, outer: FiniteDuration): IO[Option[String]] =
    IO.blocking {
      val p = new ProcessBuilder((cmd :+ file.toString).asJava).redirectErrorStream(true).start()
      p.getOutputStream.close()
      val lines =
        scala.io.Source.fromInputStream(p.getInputStream, "UTF-8").getLines().map(_.trim).toList
      p.waitFor()
      lines.find(l => l == "sat" || l == "unsat" || l == "unknown")
    }.timeoutTo(outer, IO.pure(None))

  private def alloyVerdict(als: Path): IO[Option[String]] =
    IO.blocking {
      val work = Files.createTempDirectory("alloy-xcheck")
      val bn   = als.getFileName.toString
      Files.copy(als, work.resolve(bn))
      val p = new ProcessBuilder("java", "-jar", alloyJar, "exec", "-q", "-f", "-s", "sat4j", bn)
        .directory(work.toFile)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
      p.getOutputStream.close()
      p.waitFor(90, TimeUnit.SECONDS)
      val receipt = work.resolve(bn.stripSuffix(".als")).resolve("receipt.json")
      Option
        .when(Files.exists(receipt))(Files.readString(receipt))
        .flatMap(parser.parse(_).toOption)
        .flatMap(firstCommand)
        .map(cmd => if cmd.hcursor.downField("solution").succeeded then "sat" else "unsat")
    }.timeoutTo(2.minutes, IO.pure(None))

  private def firstCommand(receipt: Json): Option[Json] =
    val commands = receipt.hcursor.downField("commands")
    commands.keys.flatMap(_.headOption).flatMap(k => commands.downField(k).focus)

  private def entries(verdicts: Path): List[Entry] =
    parser
      .parse(Files.readString(verdicts))
      .toOption
      .flatMap(_.hcursor.downField("entries").values)
      .getOrElse(Vector.empty)
      .toList
      .flatMap: e =>
        val c = e.hcursor
        (
          c.downField("id").as[String].toOption,
          c.downField("tool").as[String].toOption,
          c.downField("rawStatus").as[String].toOption,
          c.downField("file").as[String].toOption
        ).mapN(Entry.apply)
      .filter(e => e.rawStatus == "sat" || e.rawStatus == "unsat")

  private def crossCheck(spec: Path): IO[List[Result]] =
    for
      base <- IO.blocking(Files.createTempDirectory("vc-xcheck"))
      dir   = base.resolve("d")
      opts  = VerifyOptions(30_000L, dumpSmt = false, dumpSmtOut = None, dumpVc = Some(dir.toString))
      _    <- Verify.run(spec.toString, opts, log, devnull)
      es <- IO.blocking(Option.when(Files.exists(dir.resolve("verdicts.json")))(
              entries(dir.resolve("verdicts.json"))
            ).getOrElse(Nil))
      out <- es.traverse: e =>
               val f = dir.resolve(e.file)
               e.tool match
                 case "z3" =>
                   (
                     smtVerdict(List("z3", "-T:30"), f, 40.seconds),
                     smtVerdict(List("cvc5", "--tlimit=5000"), f, 15.seconds)
                   ).mapN((z, c) => Result(spec.toString, e, z, c, None))
                 case "alloy" => alloyVerdict(f).map(a => Result(spec.toString, e, None, None, a))
                 case _       => IO.pure(Result(spec.toString, e, None, None, None))
    yield out

  test("independent solvers agree with the compiler on every spec's verification conditions"):
    assume(solversPresent, s"z3/cvc5/alloy not available (ALLOY_JAR=$alloyJar); skipping")
    val specs = Files
      .list(Paths.get("fixtures/spec"))
      .iterator
      .asScala
      .filter(_.getFileName.toString.endsWith(".spec"))
      .toList
      .sortBy(_.toString)
    specs.traverse(crossCheck).map: perSpec =>
      val all      = perSpec.flatten
      val z3Checks = all.filter(_.entry.tool == "z3")

      val disagreements = all.flatMap: r =>
        val exp = r.entry.rawStatus
        r.entry.tool match
          case "z3" =>
            val z = List(
              Option.when(r.z3 != Some(exp))(
                s"z3 ${r.spec}:${r.entry.id} recorded=$exp z3=${r.z3.getOrElse("none")}"
              )
            ).flatten
            val c = r.cvc5 match
              case Some(v) if v != "unknown" && v != exp =>
                List(s"cvc5 ${r.spec}:${r.entry.id} recorded=$exp cvc5=$v")
              case _ => Nil
            z ++ c
          case "alloy" =>
            Option
              .when(r.alloy != Some(exp))(
                s"alloy ${r.spec}:${r.entry.id} recorded=$exp alloy=${r.alloy.getOrElse("none")}"
              )
              .toList
          case _ => Nil

      val cvc5Confirmed = z3Checks.count(r => r.cvc5.contains(r.entry.rawStatus))

      assert(z3Checks.nonEmpty, "no SMT checks were generated; the compiler dump path is broken")
      assert(
        disagreements.isEmpty,
        s"${disagreements.size} solver disagreement(s):\n${disagreements.mkString("\n")}"
      )
      assert(
        cvc5Confirmed > 0,
        s"cvc5 confirmed none of ${z3Checks.size} SMT checks; the independent oracle is misconfigured"
      )
