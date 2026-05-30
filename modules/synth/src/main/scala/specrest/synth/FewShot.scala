package specrest.synth

import specrest.ir.generated.SpecRestGenerated.*

import scala.io.Source
import scala.util.Using

object FewShot:

  private val resourceRoot = "/specrest/synth/few-shot"

  enum Snippet derives CanEqual:
    case MapInsertFresh, MapUpdateExisting, MapDelete, SeqSum, StateModify

  def fileName(s: Snippet): String = s match
    case Snippet.MapInsertFresh    => "map_insert_fresh.dfy"
    case Snippet.MapUpdateExisting => "map_update_existing.dfy"
    case Snippet.MapDelete         => "map_delete.dfy"
    case Snippet.SeqSum            => "seq_sum.dfy"
    case Snippet.StateModify       => "state_modify.dfy"

  def text(s: Snippet): String =
    val name     = fileName(s)
    val resource = s"$resourceRoot/$name"
    val stream   = Option(getClass.getResourceAsStream(resource))
      .getOrElse(sys.error(s"Few-shot resource not found: $resource"))
    Using.resource(stream): in =>
      Source.fromInputStream(in, "UTF-8").mkString

  def selectFor(kind: operation_kind): List[Snippet] = kind match
    case _: Create        => List(Snippet.MapInsertFresh, Snippet.StateModify)
    case _: Read          => List(Snippet.MapUpdateExisting)
    case _: Replace       => List(Snippet.MapUpdateExisting, Snippet.StateModify)
    case _: PartialUpdate => List(Snippet.MapUpdateExisting, Snippet.StateModify)
    case _: Deletea       => List(Snippet.MapDelete)
    case _: CreateChild   => List(Snippet.MapInsertFresh)
    case _: FilteredRead  => List(Snippet.SeqSum)
    case _: SideEffect    => List(Snippet.StateModify, Snippet.SeqSum)
    case _: BatchMutation => List(Snippet.SeqSum, Snippet.MapInsertFresh)
    case _: Transition    => List(Snippet.StateModify)
