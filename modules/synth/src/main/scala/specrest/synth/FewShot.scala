package specrest.synth

import specrest.convention.OperationKind

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
    val stream = Option(getClass.getResourceAsStream(resource))
      .getOrElse(sys.error(s"Few-shot resource not found: $resource"))
    Using.resource(stream): in =>
      Source.fromInputStream(in, "UTF-8").mkString

  def selectFor(kind: OperationKind): List[Snippet] = kind match
    case OperationKind.Create        => List(Snippet.MapInsertFresh, Snippet.StateModify)
    case OperationKind.Read          => List(Snippet.MapUpdateExisting)
    case OperationKind.Replace       => List(Snippet.MapUpdateExisting, Snippet.StateModify)
    case OperationKind.PartialUpdate => List(Snippet.MapUpdateExisting, Snippet.StateModify)
    case OperationKind.Delete        => List(Snippet.MapDelete)
    case OperationKind.CreateChild   => List(Snippet.MapInsertFresh)
    case OperationKind.FilteredRead  => List(Snippet.SeqSum)
    case OperationKind.SideEffect    => List(Snippet.StateModify, Snippet.SeqSum)
    case OperationKind.BatchMutation => List(Snippet.SeqSum, Snippet.MapInsertFresh)
    case OperationKind.Transition    => List(Snippet.StateModify)
