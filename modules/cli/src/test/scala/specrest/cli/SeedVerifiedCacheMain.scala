package specrest.cli

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import specrest.dafny.Generator as DafnyGenerator
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.synth.Cache
import specrest.synth.CacheEntry
import specrest.synth.SynthPromptVersion
import specrest.synth.TokenUsage

import java.nio.file.Files
import java.nio.file.Paths

object SeedVerifiedCacheMain extends IOApp:

  // Seeds cache entries without running CEGIS. The caller is responsible for
  // having dafny-verified each body against the current skeleton first; the
  // key must use the model/temperature the consuming compile will pass.
  def run(args: List[String]): IO[ExitCode] =
    if args.length < 6 || args.length % 2 != 0 then
      IO.println(
        "usage: SeedVerifiedCacheMain <spec> <cache-root> <model> <temperature> " +
          "<op1> <body-file1> [<op2> <body-file2> ...]"
      ).as(ExitCode.Error)
    else
      val spec  = args.head
      val root  = Paths.get(args(1)).resolve("verified")
      val model = args(2)
      val temp  = args(3).toDouble
      val pairs = args
        .drop(4)
        .grouped(2)
        .collect:
          case List(op, bodyFile) => op -> bodyFile
        .toList

      val program =
        for
          src    <- IO.blocking(Files.readString(Paths.get(spec)))
          parsed <- Parse.parseSpec(src)
          tree   <- IO.fromEither(parsed.left.map(e => new RuntimeException(s"parse: $e")))
          irE    <- Builder.buildIR(tree.tree)
          ir     <- IO.fromEither(irE.left.map(e => new RuntimeException(s"build: ${e.message}")))
          dafny <- IO.fromEither(
                     DafnyGenerator
                       .generate(ir)
                       .left
                       .map(e => new RuntimeException(s"dafny gen: ${e.message}"))
                   )
          cache <- Cache.make(root)
          _ <- pairs.foldLeft(IO.unit): (acc, kv) =>
                 val (op, bodyFile) = kv
                 dafny.methods.find(_.name == op) match
                   case None =>
                     acc *> IO.raiseError(new RuntimeException(s"no Dafny method '$op'"))
                   case Some(header) =>
                     for
                       _    <- acc
                       body <- IO.blocking(Files.readString(Paths.get(bodyFile)))
                       key   = Cache.keyFor(header, model, temp)
                       entry = CacheEntry(
                                 candidate = body,
                                 body = body,
                                 usage = TokenUsage(0, 0),
                                 model = model,
                                 promptVersion = SynthPromptVersion
                               )
                       _ <- cache.store(key, entry)
                       _ <- IO.println(s"seeded $op from $bodyFile")
                     yield ()
        yield ExitCode.Success
      program.handleErrorWith: t =>
        IO.println(s"seed failed: ${t.getMessage}").as(ExitCode.Error)
