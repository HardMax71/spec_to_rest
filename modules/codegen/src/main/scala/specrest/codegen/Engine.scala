package specrest.codegen

import com.github.jknack.handlebars.{EscapingStrategy, Handlebars, Helper, Options, Template}
import com.github.jknack.handlebars.helper.StringHelpers
import specrest.convention.Naming

final class TemplateEngine:
  private val hbs: Handlebars =
    val h = new Handlebars()
    h.`with`(EscapingStrategy.NOOP)

  registerDefaultHelpers(hbs)

  def render(templateSource: String, context: AnyRef): String =
    hbs.compileInline(templateSource).apply(context)

  def compileTemplate(source: String): Template =
    hbs.compileInline(source)

  def registerPartial(name: String, source: String): Unit =
    val _ = hbs.registerHelper(name, new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val _ = ctx
        hbs.compileInline(source).apply(opts.context)
    )

  private def registerDefaultHelpers(hbs: Handlebars): Unit =
    // String case transformations
    hbs.registerHelper("snake_case", stringHelper(Naming.toSnakeCase))
    hbs.registerHelper("kebab_case", stringHelper(Naming.toKebabCase))
    hbs.registerHelper("pascal_case", stringHelper(pascalCase))
    hbs.registerHelper("camel_case", stringHelper(camelCase))
    hbs.registerHelper("pluralize", stringHelper(Naming.pluralize))
    hbs.registerHelper("upper", stringHelper(_.toUpperCase))
    hbs.registerHelper("lower", stringHelper(_.toLowerCase))

    // Boolean helpers — handlebars.java passes context and options separately
    hbs.registerHelper("eq", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        java.lang.Boolean.valueOf(ctx == opts.param[AnyRef](0))
    )
    hbs.registerHelper("ne", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        java.lang.Boolean.valueOf(ctx != opts.param[AnyRef](0))
    )
    hbs.registerHelper("and", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        java.lang.Boolean.valueOf(truthy(ctx) && truthy(opts.param[AnyRef](0)))
    )
    hbs.registerHelper("or", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        java.lang.Boolean.valueOf(truthy(ctx) || truthy(opts.param[AnyRef](0)))
    )
    hbs.registerHelper("not", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val _ = opts
        java.lang.Boolean.valueOf(!truthy(ctx))
    )

    hbs.registerHelper("concat", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val parts = (ctx +: opts.params.toList).collect { case s: String => s }
        parts.mkString
    )

    hbs.registerHelper("join", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val sep = opts.param[AnyRef](0) match
          case s: String => s
          case _          => ","
        ctx match
          case xs: java.util.Collection[?] =>
            val sb = new java.util.StringJoiner(sep)
            xs.forEach(x => { val _ = sb.add(String.valueOf(x)) })
            sb.toString
          case xs: Iterable[?] => xs.map(String.valueOf).mkString(sep)
          case _                => String.valueOf(ctx)
    )

    hbs.registerHelper("indent", new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val spaces = ctx match
          case n: Number => n.intValue()
          case s: String => s.toIntOption.getOrElse(0)
          case _          => 0
        val content = opts.param[AnyRef](0) match
          case s: String => s
          case other     => String.valueOf(other)
        indentString(content, spaces)
    )

    val _ = StringHelpers.lower // force classload reference

  private def stringHelper(f: String => String): Helper[AnyRef] =
    new Helper[AnyRef]:
      override def apply(ctx: AnyRef, opts: Options): AnyRef =
        val _ = opts
        ctx match
          case s: String => f(s)
          case null      => ""
          case other     => f(String.valueOf(other))

  private def truthy(v: AnyRef): Boolean = v match
    case null                  => false
    case b: java.lang.Boolean  => b.booleanValue()
    case s: String             => s.nonEmpty
    case n: Number             => n.doubleValue() != 0.0
    case c: java.util.Collection[?] => !c.isEmpty
    case _                      => true

  private def camelCase(value: String): String =
    val parts = Naming.splitCamelCase(value).filter(_.nonEmpty)
    parts.zipWithIndex.map: (w, i) =>
      if i == 0 then w.toLowerCase
      else w.head.toUpper +: w.tail.toLowerCase
    .mkString

  private def pascalCase(value: String): String =
    val parts = Naming.splitCamelCase(value).filter(_.nonEmpty)
    parts.map(w => w.head.toUpper +: w.tail.toLowerCase).mkString

  private def indentString(content: String, spaces: Int): String =
    val pad = " " * spaces
    content.split("\n", -1).map: line =>
      if line.trim.isEmpty then "" else pad + line
    .mkString("\n")
