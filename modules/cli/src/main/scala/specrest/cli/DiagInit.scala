package specrest.cli

import cats.effect.IO

import java.io.PrintStream

object DiagInit:

  private val targetClass = "specrest.ir.generated.SpecRestGenerated$"

  def run(out: PrintStream = System.err): IO[ExitStatus] = IO.delay {
    out.println("=== diag-init: substrate VM class-init probe ===")
    out.println(s"  jvm.name      = ${sysProp("java.vm.name")}")
    out.println(s"  jvm.vendor    = ${sysProp("java.vm.vendor")}")
    out.println(s"  jvm.version   = ${sysProp("java.vm.version")}")
    out.println(s"  os.name       = ${sysProp("os.name")}")
    out.println(s"  os.arch       = ${sysProp("os.arch")}")
    out.println(s"  graalvm.image = ${sysProp("org.graalvm.nativeimage.imagecode")}")
    out.println()

    val loaderOpt: Option[ClassLoader] = Option(getClass.getClassLoader).map(_.nn)

    val resolveOk: Option[Class[?]] = loaderOpt.flatMap: ld =>
      out.println(s"--- step A: resolve $targetClass without forcing <clinit> ---")
      try
        val c = Class.forName(targetClass, false, ld)
        out.println(s"  ok (resolved): ${c.getName}")
        Some(c)
      catch
        case t: Throwable =>
          out.println("  forName(initialize=false) threw:")
          walkCauses(t, out)
          None

    val initOk: Boolean = (loaderOpt, resolveOk) match
      case (Some(ld), Some(_)) =>
        out.println()
        out.println("--- step B: trigger <clinit> via Class.forName(name, true, loader) ---")
        try
          val _ = Class.forName(targetClass, true, ld)
          out.println("  ok (initialized)")
          true
        catch
          case t: Throwable =>
            out.println("  forName(initialize=true) threw (this is the bug we want):")
            walkCauses(t, out)
            false
      case _ => false

    val callOk: Boolean = if initOk then
      out.println()
      out.println(
        "--- step C: SpecRestGenerated.translate(Nil, BoolLitF(true, None)) ---"
      )
      try
        import specrest.ir.generated.SpecRestGenerated
        import specrest.ir.generated.SpecRestGenerated.BoolLitF
        val r = SpecRestGenerated.translate(Nil, BoolLitF(true, None))
        out.println(s"  ok: $r")
        true
      catch
        case t: Throwable =>
          out.println("  translate(...) threw:")
          walkCauses(t, out)
          false
    else false

    out.println()
    out.println(
      s"=== diag-init summary: resolve=${resolveOk.isDefined} init=$initOk call=$callOk ==="
    )
    if initOk && callOk then ExitStatus.Ok else ExitStatus.Violations
  }

  private def sysProp(key: String): String =
    Option(System.getProperty(key)).getOrElse("(unset)")

  @scala.annotation.tailrec
  private def walkCauses(t: Throwable, out: PrintStream, depth: Int = 0): Unit =
    val msg = Option(t.getMessage).getOrElse("(no message)")
    out.println(s">>> cause depth=$depth: ${t.getClass.getName}: $msg")
    t.printStackTrace(out)
    Option(t.getCause) match
      case Some(c) => walkCauses(c.nn, out, depth + 1)
      case None    => ()
