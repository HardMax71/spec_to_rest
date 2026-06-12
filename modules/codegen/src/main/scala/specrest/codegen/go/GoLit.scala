package specrest.codegen.go

object GoLit:
  def str(s: String): String =
    val sb = new StringBuilder
    sb.append('"')
    s.foreach:
      case '"'                  => sb.append("\\\"")
      case '\\'                 => sb.append("\\\\")
      case '\n'                 => sb.append("\\n")
      case '\r'                 => sb.append("\\r")
      case '\t'                 => sb.append("\\t")
      case c if c < 0x20.toChar => sb.append(f"\\u${c.toInt}%04x")
      case c                    => sb.append(c)
    sb.append('"')
    sb.toString
