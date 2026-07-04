package specrest.dafny

import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures

class CandidateLoweringTest extends CatsEffectSuite:

  private def candidatesOf(name: String) =
    SpecFixtures
      .loadIR(name)
      .map: ir =>
        Generator.generate(ir) match
          case Left(err)  => fail(err.toString)
          case Right(out) => out.methods.map(m => m.name -> m.candidates).toMap

  test("auth: session tokens and the reset token become sampled candidates"):
    candidatesOf("auth_service").map: byOp =>
      assertEquals(
        byOp("Login").map(_.param),
        List("cand_access_token", "cand_refresh_token")
      )
      assertEquals(
        byOp("RefreshToken").map(_.param),
        List("cand_access_token", "cand_refresh_token")
      )
      assertEquals(byOp("RequestPasswordReset").map(_.param), List("cand_reset_token"))
      val token = byOp("Login").head
      assertEquals(token.sampleLength, 128)
      assertEquals(token.sampleCharset, "0123456789abcdef")
      assertEquals(byOp("Register"), Nil)
      assertEquals(byOp("ResetPassword"), Nil)

  test("url_shortener: the short code is a candidate, pinned outputs are not"):
    candidatesOf("url_shortener").map: byOp =>
      val code = byOp("Shorten") match
        case c :: Nil => c
        case other    => fail(s"expected one Shorten candidate, got $other")
      assertEquals(code.param, "cand_code")
      assertEquals(code.sampleLength, 6)
      assertEquals(
        code.sampleCharset,
        ('a' to 'z').mkString + ('A' to 'Z').mkString + ('0' to '9').mkString
      )
      assertEquals(byOp("Resolve"), Nil)
      assertEquals(byOp("Delete"), Nil)
