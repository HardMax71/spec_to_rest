package specrest.cli

import specrest.codegen.EmittedFile

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum FileAction derives CanEqual:
  case Create, Update, Unchanged, Preserved

final case class FilePlan(action: FileAction, path: String)

object Plan:
  def classify(files: List[EmittedFile], outRoot: Path): List[FilePlan] =
    files.map: f =>
      val abs = outRoot.resolve(f.path)
      if Files.exists(abs) then
        if f.preserve then FilePlan(FileAction.Preserved, f.path)
        else
          val onDisk = Files.readAllBytes(abs)
          val want   = f.content.getBytes(StandardCharsets.UTF_8)
          if java.util.Arrays.equals(onDisk, want) then FilePlan(FileAction.Unchanged, f.path)
          else FilePlan(FileAction.Update, f.path)
      else FilePlan(FileAction.Create, f.path)

  def render(plans: List[FilePlan], palette: Palette): String =
    val rows = plans.sortBy(_.path).map: p =>
      val label = p.action match
        case FileAction.Create    => palette.green("create   ")
        case FileAction.Update    => palette.yellow("update   ")
        case FileAction.Unchanged => palette.dim("unchanged")
        case FileAction.Preserved => palette.dim("preserve ")
      s"  $label ${p.path}"
    rows.mkString("\n")

  final case class Tally(create: Int, update: Int, unchanged: Int, preserved: Int):
    def total: Int = create + update + unchanged + preserved

  def tally(plans: List[FilePlan]): Tally =
    Tally(
      create = plans.count(_.action == FileAction.Create),
      update = plans.count(_.action == FileAction.Update),
      unchanged = plans.count(_.action == FileAction.Unchanged),
      preserved = plans.count(_.action == FileAction.Preserved)
    )
