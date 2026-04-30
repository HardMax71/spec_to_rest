package specrest.verify.alloy

import specrest.ir.Span

import scala.collection.mutable

final case class RenderedAlloy(source: String, factSpans: Map[Int, Span])

@SuppressWarnings(Array("org.wartremover.warts.Var"))
object Render:

  def render(m: AlloyModule): String = renderWithLineMap(m).source

  def renderWithLineMap(m: AlloyModule): RenderedAlloy =
    val sb    = new StringBuilder
    val spans = mutable.Map.empty[Int, Span]
    var line  = 1
    def emit(text: String, factSpan: Option[Span] = None): Unit =
      val startLine = line
      sb.append(text)
      val newlines = text.count(_ == '\n')
      line += newlines
      if !text.endsWith("\n") then
        sb.append('\n')
        line += 1
      factSpan.foreach: s =>
        // Body of a fact is rendered as `fact NAME {\n  BODY\n}`. The body
        // sits on (startLine + 1); register that line so Alloy Pos values
        // pointing into the body map back to the originating spec span.
        spans(startLine + 1) = s
    emit(s"module ${m.name}")
    m.sigs.foreach: s =>
      sb.append('\n'); line += 1
      emit(renderSig(s))
    m.facts.foreach: f =>
      sb.append('\n'); line += 1
      emit(renderFact(f), f.span)
    m.commands.foreach: c =>
      sb.append('\n'); line += 1
      emit(renderCommand(c))
    RenderedAlloy(sb.toString, spans.toMap)

  private def renderSig(s: AlloySig): String =
    val modifiers = List.newBuilder[String]
    if s.abstract_ then modifiers += "abstract"
    if s.isOne then modifiers += "one"
    val prefixStr = modifiers.result().mkString(" ")
    val prefix    = if prefixStr.isEmpty then "" else s"$prefixStr "
    val extend    = s.extends_.map(e => s" extends $e").getOrElse("")
    val body =
      if s.fields.isEmpty then " {}"
      else
        val fieldStrs = s.fields.map: f =>
          s"  ${f.name}: ${AlloyFieldMultiplicity.token(f.mult)} ${f.elemType}"
        " {\n" + fieldStrs.mkString(",\n") + "\n}"
    s"${prefix}sig ${s.name}$extend$body"

  private def renderFact(f: AlloyFact): String =
    val header = f.name match
      case Some(n) => s"fact $n {"
      case None    => "fact {"
    s"$header\n  ${f.body}\n}"

  private def renderCommand(c: AlloyCommand): String =
    val keyword = c.kind match
      case AlloyCommandKind.Run   => "run"
      case AlloyCommandKind.Check => "check"
    s"$keyword ${c.name} { ${c.body} } for ${c.scope}"
