package specrest.synth

import munit.FunSuite
import specrest.convention.OperationKind

class FewShotTest extends FunSuite:

  test("each snippet kind loads from resources"):
    FewShot.Snippet.values.foreach: s =>
      val text = FewShot.text(s)
      assert(text.nonEmpty, s"empty snippet: $s")
      assert(text.contains("method") || text.contains("function"), s"no Dafny declaration in $s")

  test("Create operations get a map_insert example"):
    val picks = FewShot.selectFor(OperationKind.Create)
    assert(picks.contains(FewShot.Snippet.MapInsertFresh), s"got $picks")

  test("Delete operations get the map_delete example"):
    assertEquals(FewShot.selectFor(OperationKind.Delete), List(FewShot.Snippet.MapDelete))

  test("Read operations get the map_update_existing example"):
    assertEquals(
      FewShot.selectFor(OperationKind.Read),
      List(FewShot.Snippet.MapUpdateExisting)
    )
