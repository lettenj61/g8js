package g8js

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}
import io.scalajs.nodejs.path.{Path => NodePath}

/**
  * Object returned from `path.parse` API.
  */
@js.native
trait PathObject extends js.Object {
  def root: String
  def dir: String
  def base: String
  def ext: String
  def name: String
}

@js.native
@JSImport("path", JSImport.Namespace)
object Path extends NodePath {
  @JSName("parse")
  def parseSafe(path: String): PathObject = js.native
}
