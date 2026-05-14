package specrest.cli.nativehook

import org.graalvm.nativeimage.hosted.Feature

final class SpecRestInitFeature extends Feature:

  override def beforeAnalysis(access: Feature.BeforeAnalysisAccess): Unit =
    val target = "specrest.ir.generated.SpecRestGenerated$"
    val out    = System.err
    out.println()
    out.println(s"[SpecRestInitFeature] probing $target on build host")
    out.println(s"  jvm: ${prop("java.vm.name")} ${prop("java.version")}")
    out.println(s"  os:  ${prop("os.name")} ${prop("os.arch")}")

    Option(getClass.getClassLoader).map(_.nn) match
      case None =>
        out.println("  FAIL: getClassLoader returned null")
      case Some(ld) =>
        try
          val _ = Class.forName(target, true, ld)
          out.println("  OK: <clinit> succeeded on build host")
        catch
          case t: Throwable =>
            out.println(s"  FAIL: ${t.getClass.getName}: ${msg(t)}")
            walk(t, 1, out)

    out.println("[SpecRestInitFeature] probe complete")
    out.println()

  private def prop(key: String): String =
    Option(System.getProperty(key)).getOrElse("(unset)")

  private def msg(t: Throwable): String =
    Option(t.getMessage).getOrElse("(no message)")

  @scala.annotation.tailrec
  private def walk(t: Throwable, depth: Int, out: java.io.PrintStream): Unit =
    t.printStackTrace(out)
    Option(t.getCause).map(_.nn) match
      case Some(c) =>
        out.println(s"  >>> cause depth=$depth: ${c.getClass.getName}: ${msg(c)}")
        walk(c, depth + 1, out)
      case None => ()
