package specrest.codegen

import com.github.jknack.handlebars.EscapingStrategy
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.helper.StringHelpers
import specrest.convention.Naming

import scala.jdk.CollectionConverters.*

@SuppressWarnings(Array("org.wartremover.warts.Null"))
final class TemplateEngine:
  private given anyAnyCanEqual: CanEqual[Any, Any] = CanEqual.derived
  private val hbs: Handlebars =
    val h = new Handlebars()
    h.`with`(EscapingStrategy.NOOP)

  registerDefaultHelpers(hbs)

  def render(templateSource: String, context: AnyRef): String =
    hbs.compileInline(templateSource).apply(toJava(context).orNull)

  def renderAny(templateSource: String, context: Any): String =
    hbs.compileInline(templateSource).apply(toJava(context).orNull)

  private[codegen] def toJava(v: Any): Option[AnyRef] = v match
    case null                 => None
    case None                 => None
    case Some(x)              => toJava(x)
    case s: String            => Some(s)
    case b: Boolean           => Some(java.lang.Boolean.valueOf(b))
    case i: Int               => Some(java.lang.Integer.valueOf(i))
    case l: Long              => Some(java.lang.Long.valueOf(l))
    case d: Double            => Some(java.lang.Double.valueOf(d))
    case f: Float             => Some(java.lang.Float.valueOf(f))
    case n: java.lang.Number  => Some(n)
    case b: java.lang.Boolean => Some(b)
    case m: Map[?, ?] =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      m.foreach: (k, v) =>
        toJava(v).foreach(ja => out.put(k.toString, ja))
      Some(out)
    case xs: Iterable[?]                   => Some(xs.flatMap(toJava).toList.asJava)
    case arr: Array[?]                     => Some(arr.toList.flatMap(toJava).asJava)
    case p: Product if p.productArity == 0 => Some(p.toString)
    case p: Product =>
      val out = new java.util.LinkedHashMap[String, AnyRef]()
      p.productElementNames.toList.zip(p.productIterator.toList).foreach: (k, v) =>
        toJava(v).foreach(ja => out.put(k, ja))
      Some(out)
    case x: AnyRef => Some(x)
    case other     => Some(other.toString)

  def compileTemplate(source: String): Template =
    hbs.compileInline(source)

  def registerPartial(name: String, source: String): Unit =
    val _ = hbs.registerHelper(
      name,
      new Helper[AnyRef]:
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
    hbs.registerHelper(
      "eq",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          java.lang.Boolean.valueOf(ctx == opts.param[AnyRef](0))
    )
    hbs.registerHelper(
      "ne",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          java.lang.Boolean.valueOf(ctx != opts.param[AnyRef](0))
    )
    hbs.registerHelper(
      "and",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          java.lang.Boolean.valueOf(truthy(ctx) && truthy(opts.param[AnyRef](0)))
    )
    hbs.registerHelper(
      "or",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          java.lang.Boolean.valueOf(truthy(ctx) || truthy(opts.param[AnyRef](0)))
    )
    hbs.registerHelper(
      "not",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          val _ = opts
          java.lang.Boolean.valueOf(!truthy(ctx))
    )

    hbs.registerHelper(
      "concat",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          val parts = (ctx +: opts.params.toList).collect { case s: String => s }
          parts.mkString
    )

    hbs.registerHelper(
      "join",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef | Null, opts: Options): AnyRef =
          val sep = opts.param[AnyRef](0) match
            case s: String => s
            case _         => ","
          Option(ctx) match
            case None => ""
            case Some(xs: java.util.Collection[?]) =>
              val sb = new java.util.StringJoiner(sep)
              xs.forEach { x =>
                val _ = sb.add(String.valueOf(x))
              }
              sb.toString
            case Some(xs: Iterable[?]) => xs.map(String.valueOf).mkString(sep)
            case Some(other)           => String.valueOf(other)
    )

    hbs.registerHelper(
      "indent",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef | Null, opts: Options): AnyRef =
          val spaces = Option(ctx) match
            case Some(n: Number) => n.intValue()
            case Some(s: String) => s.toIntOption.getOrElse(0)
            case _               => 0
          val content = Option(opts.param[AnyRef](0)) match
            case Some(s: String) => s
            case Some(other)     => String.valueOf(other)
            case None            => ""
          indentString(content, spaces)
    )

    val _ = StringHelpers.lower // force classload reference

  private def stringHelper(f: String => String): Helper[AnyRef] =
    new Helper[AnyRef]:
      override def apply(ctx: AnyRef | Null, opts: Options): AnyRef =
        val _ = opts
        Option(ctx) match
          case Some(s: String) => f(s)
          case Some(other)     => f(String.valueOf(other))
          case None            => ""

  private def truthy(v: Any): Boolean = Option(v) match
    case None                             => false
    case Some(b: java.lang.Boolean)       => b.booleanValue()
    case Some(s: String)                  => s.nonEmpty
    case Some(n: Number)                  => n.doubleValue() != 0.0
    case Some(c: java.util.Collection[?]) => !c.isEmpty
    case Some(_)                          => true

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
