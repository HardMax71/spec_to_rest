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

  def run(args: List[String]): IO[ExitCode] =
    if args.length < 4 || args.length % 2 != 0 then
      IO.println(
        "usage: SeedVerifiedCacheMain <spec> <cache-root> <op1> <body1> [<op2> <body2> ...]"
      ).as(ExitCode.Error)
    else
      val spec  = args.head
      val root  = Paths.get(args(1)).resolve("verified")
      val pairs = args
        .drop(2)
        .grouped(2)
        .collect:
          case List(op, body) => op -> body
        .toList

      val program =
        for
          src    <- IO.blocking(Files.readString(Paths.get(spec)))
          parsed <- Parse.parseSpec(src)
          tree   <- IO.fromEither(parsed.left.map(e => new RuntimeException(s"parse: $e")))
          irE    <- Builder.buildIR(tree.tree)
          ir     <- IO.fromEither(irE.left.map(e => new RuntimeException(s"build: ${e.message}")))
          dafny  <- IO.fromEither(
                     DafnyGenerator
                       .generate(ir)
                       .left
                       .map(e => new RuntimeException(s"dafny gen: ${e.message}"))
                   )
          cache <- Cache.make(root)
          _     <- pairs.foldLeft(IO.unit): (acc, kv) =>
                 val (op, body) = kv
                 dafny.methods.find(_.name == op) match
                   case None =>
                     acc *> IO.raiseError(new RuntimeException(s"no Dafny method '$op'"))
                   case Some(header) =>
                     val key   = Cache.keyFor(header, "claude-sonnet-4-6", 1.0)
                     val entry = CacheEntry(
                       candidate = "stub",
                       body = body,
                       usage = TokenUsage(0, 0),
                       model = "claude-sonnet-4-6",
                       promptVersion = SynthPromptVersion
                     )
                     acc *> cache.store(key, entry) *> IO.println(s"seeded $op")
        yield ExitCode.Success
      program.handleErrorWith: t =>
        IO.println(s"seed failed: ${t.getMessage}").as(ExitCode.Error)
