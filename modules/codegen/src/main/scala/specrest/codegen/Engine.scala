package specrest.codegen

import com.github.jknack.handlebars.EscapingStrategy
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Helper
import com.github.jknack.handlebars.Options
import com.github.jknack.handlebars.Template
import com.github.jknack.handlebars.helper.StringHelpers
import specrest.ir.Naming

import scala.jdk.CollectionConverters.*

@SuppressWarnings(Array("org.wartremover.warts.Null"))
final class TemplateEngine:
  private val hbs: Handlebars =
    val h = new Handlebars()
    // standalone block-tag lines must not leak blank lines into output
    h.setPrettyPrint(true)
    h.`with`(EscapingStrategy.NOOP)

  registerDefaultHelpers(hbs)

  def render(templateSource: String, context: AnyRef): String =
    hbs.compileInline(templateSource).apply(toJava(context).orNull)

  def renderAny(templateSource: String, context: Any): String =
    hbs.compileInline(templateSource).apply(toJava(context).orNull)

  private[codegen] def toJava(v: Any): Option[AnyRef] = Option(v).flatMap {
    case _: None.type         => None
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
  }

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
    hbs.registerHelper("snake_case", stringHelper(Naming.toSnakeCase))
    hbs.registerHelper("pascal_case", stringHelper(s => Naming.toPascalCase(s)))
    hbs.registerHelper("pluralize", stringHelper(Naming.pluralize))

    // handlebars.java passes the helper's context as `ctx` and the first argument as opts.param(0)
    hbs.registerHelper(
      "eq",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef, opts: Options): AnyRef =
          java.lang.Boolean.valueOf(java.util.Objects.equals(ctx, opts.param[AnyRef](0)))
    )

    hbs.registerHelper(
      "join",
      new Helper[AnyRef]:
        override def apply(ctx: AnyRef | Null, opts: Options): AnyRef =
          val sep = opts.param[AnyRef](0) match
            case s: String => s
            case _         => ","
          Option(ctx) match
            case _: None.type => ""
            case Some(xs: java.util.Collection[?]) =>
              val sb = new java.util.StringJoiner(sep)
              xs.forEach { x =>
                val _ = sb.add(String.valueOf(x))
              }
              sb.toString
            case Some(xs: Iterable[?]) => xs.map(String.valueOf).mkString(sep)
            case Some(other)           => String.valueOf(other)
    )

    val _ = StringHelpers.lower // force classload reference

  private def stringHelper(f: String => String): Helper[AnyRef] =
    new Helper[AnyRef]:
      override def apply(ctx: AnyRef | Null, opts: Options): AnyRef =
        val _ = opts
        Option(ctx) match
          case Some(s: String) => f(s)
          case Some(other)     => f(String.valueOf(other))
          case _: None.type    => ""
