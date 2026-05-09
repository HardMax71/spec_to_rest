package specrest.synth

import munit.CatsEffectSuite
import specrest.convention.OperationKind

class FewShotTest extends CatsEffectSuite:

  test("each snippet kind loads from resources"):
    FewShot.Snippet.values.foreach: s =>
      val text = FewShot.text(s)
      assert(text.nonEmpty, s"empty snippet: $s")
      assert(text.contains("method") || text.contains("function"), s"no Dafny declaration in $s")

  test("Create operations get a map_insert example"):
    val picks = FewShot.selectFor(OperationKind.Create)
    assert(picks.contains(FewShot.Snippet.MapInsertFresh), s"got $picks")

  List(
    (
      "Delete operations get the map_delete example",
      OperationKind.Delete,
      List(FewShot.Snippet.MapDelete)
    ),
    (
      "Read operations get the map_update_existing example",
      OperationKind.Read,
      List(FewShot.Snippet.MapUpdateExisting)
    )
  ).foreach: (name, kind, expected) =>
    test(name):
      assertEquals(FewShot.selectFor(kind), expected)
