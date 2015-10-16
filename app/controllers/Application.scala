package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json.Json

import scala.util.Try

class Application extends Controller {

  def index = Action {
    Ok(views.html.index())
  }

  def json = Action(parse.urlFormEncoded) { request =>
    val json = request.body.getOrElse("json", Seq("")).head
    Try {
      Ok(Json.parse(json))
    } getOrElse {
      BadRequest(s"JSON Parse Error:\n$json")
    }
  }

}
