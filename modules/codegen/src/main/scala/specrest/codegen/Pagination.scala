package specrest.codegen

final case class PaginationView(
    defaultLimit: Int,
    minLimit: Int,
    maxLimit: Int,
    defaultOffset: Int
)

object Pagination:
  val defaultLimit: Int  = 50
  val minLimit: Int      = 1
  val maxLimit: Int      = 100
  val defaultOffset: Int = 0

  val view: PaginationView = PaginationView(defaultLimit, minLimit, maxLimit, defaultOffset)
