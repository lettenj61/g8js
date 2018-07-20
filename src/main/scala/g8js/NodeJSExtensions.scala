package g8js

import scala.scalajs.js
import scala.scalajs.js.annotation.{ JSImport, JSName }

import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.child_process.{ ChildProcess => BaseChildProcess, ExecOptions }
import io.scalajs.nodejs.path.{ Path => NodePath }

@js.native
trait SyncChildProcess extends BaseChildProcess {
  def execSync(command: String, options: ExecOptions): Buffer = js.native
}

@js.native
@JSImport("child_process", JSImport.Namespace)
object ChildProcess extends SyncChildProcess

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
