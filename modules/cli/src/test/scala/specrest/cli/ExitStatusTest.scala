package specrest.cli

import munit.CatsEffectSuite

class ExitStatusTest extends CatsEffectSuite:

  List(
    (ExitStatus.Ok, 0, "ok"),
    (ExitStatus.Violations, 1, "violations"),
    (ExitStatus.Translator, 2, "translator-limit"),
    (ExitStatus.Backend, 3, "backend-error"),
    (ExitStatus.Trust, 4, "trust-limit")
  ).foreach:
    case (status, code, label) =>
      test(s"exit $code ($label): code, label, conversion, and a non-empty meaning"):
        assertEquals(status.code, code)
        assertEquals(status.label, label)
        assertEquals(status.exit.code, code)
        assert(status.meaning.nonEmpty)

  test("legend renders every status as 'code label'"):
    ExitStatus.values.foreach: s =>
      assert(ExitStatus.legend.contains(s"${s.code} ${s.label}"))
