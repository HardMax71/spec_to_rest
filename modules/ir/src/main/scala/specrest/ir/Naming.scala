package specrest.ir

object Naming:

  private val Uncountable: Set[String] = Set(
    "data",
    "info",
    "information",
    "metadata",
    "inventory",
    "feedback",
    "equipment",
    "software",
    "hardware",
    "middleware",
    "auth"
  )

  private val Irregular: Map[String, String] = Map(
    "person"    -> "people",
    "child"     -> "children",
    "man"       -> "men",
    "woman"     -> "women",
    "mouse"     -> "mice",
    "goose"     -> "geese",
    "tooth"     -> "teeth",
    "foot"      -> "feet",
    "ox"        -> "oxen",
    "criterion" -> "criteria",
    "datum"     -> "data",
    "index"     -> "indices",
    "matrix"    -> "matrices",
    "vertex"    -> "vertices",
    "analysis"  -> "analyses",
    "basis"     -> "bases",
    "crisis"    -> "crises",
    "thesis"    -> "theses"
  )

  def pluralize(word: String): String =
    val lower = word.toLowerCase
    if Uncountable.contains(lower) then word
    else
      Irregular.get(lower) match
        case Some(plural) =>
          val firstChar = word.charAt(0)
          if firstChar == firstChar.toUpper then
            plural.head.toUpper +: plural.tail
          else plural
        case None =>
          if word.matches("(?i).*[aeiou]z$") then word + "zes"
          else if word.matches("(?i).*(s|x|z|ch|sh)$") then word + "es"
          else if word.matches("(?i).*[^aeiou]y$") then word.dropRight(1) + "ies"
          else if word.matches("(?i).*fe$") then word.dropRight(2) + "ves"
          else if word.matches("(?i).*f$") then word.dropRight(1) + "ves"
          else word + "s"

  def splitCamelCase(name: String): List[String] =
    name
      .replaceAll("([a-z0-9])([A-Z])", "$1\u0000$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1\u0000$2")
      .split('\u0000')
      .toList

  def toKebabCase(name: String): String =
    splitCamelCase(name).map(_.toLowerCase).mkString("-")

  def toSnakeCase(name: String): String =
    splitCamelCase(name).map(_.toLowerCase).mkString("_")

  def toPathSegment(entityName: String): String =
    val parts   = splitCamelCase(entityName)
    val last    = parts.last
    val updated = parts.init :+ pluralize(last)
    updated.map(_.toLowerCase).mkString("-")

  def toTableName(entityName: String): String =
    val parts   = splitCamelCase(entityName)
    val last    = parts.last
    val updated = parts.init :+ pluralize(last)
    updated.map(_.toLowerCase).mkString("_")

  def toColumnName(fieldName: String): String =
    if fieldName.contains("_") || fieldName == fieldName.toLowerCase then fieldName
    else toSnakeCase(fieldName)

  final case class CamelStrategy(
      reservedNames: Set[String] = Set.empty,
      reservedSuffix: String = "_"
  )

  object CamelStrategy:
    val Plain: CamelStrategy = CamelStrategy()

    val Ts: CamelStrategy = CamelStrategy(
      reservedNames =
        Set("class", "function", "default", "delete", "new", "return", "var", "let", "const")
    )

  final case class PascalStrategy(initialisms: Set[String] = Set.empty)

  object PascalStrategy:
    val Plain: PascalStrategy = PascalStrategy()

    val Go: PascalStrategy = PascalStrategy(
      initialisms =
        Set("id", "url", "uuid", "api", "http", "json", "html", "sql", "ip", "tcp", "udp")
    )

  def toCamelCase(name: String, strategy: CamelStrategy = CamelStrategy.Plain): String =
    val parts = name.split('_').toList.flatMap(p => splitCamelCase(p)).filter(_.nonEmpty)
    val joined = parts.zipWithIndex.map { (w, i) =>
      if i == 0 then w.toLowerCase
      else w.head.toUpper +: w.tail.toLowerCase
    }.mkString
    if strategy.reservedNames.contains(joined) then joined + strategy.reservedSuffix else joined

  def toPascalCase(name: String, strategy: PascalStrategy = PascalStrategy.Plain): String =
    val parts = name.split('_').toList.flatMap(p => splitCamelCase(p)).filter(_.nonEmpty)
    parts.map { w =>
      if strategy.initialisms.contains(w.toLowerCase) then w.toUpperCase
      else w.head.toUpper +: w.tail.toLowerCase
    }.mkString
