package specrest.ir

import specrest.ir.generated.SpecRestGenerated.DELETE
import specrest.ir.generated.SpecRestGenerated.GET
import specrest.ir.generated.SpecRestGenerated.PATCH
import specrest.ir.generated.SpecRestGenerated.POST
import specrest.ir.generated.SpecRestGenerated.PUT
import specrest.ir.generated.SpecRestGenerated.http_method

object HttpMethods:

  def lower(m: http_method): String = m match
    case _: GET    => "get"
    case _: POST   => "post"
    case _: PUT    => "put"
    case _: PATCH  => "patch"
    case _: DELETE => "delete"

  def upper(m: http_method): String = lower(m).toUpperCase

  def pascal(m: http_method): String = lower(m).capitalize
