package specrest.verify.alloy

object Render:

  def render(m: AlloyModule): String =
    val parts = List.newBuilder[String]
    parts += s"module ${m.name}"
    m.sigs.foreach(s => parts += renderSig(s))
    m.facts.foreach(f => parts += renderFact(f))
    m.commands.foreach(c => parts += renderCommand(c))
    parts.result().mkString("\n\n") + "\n"

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
