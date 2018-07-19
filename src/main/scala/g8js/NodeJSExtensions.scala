package g8js

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import io.scalajs.nodejs.buffer.Buffer
import io.scalajs.nodejs.child_process.{ ChildProcess => BaseChildProcess, ExecOptions }

@js.native
trait SyncChildProcess extends BaseChildProcess {
  def execSync(command: String, options: ExecOptions): Buffer = js.native
}

@js.native
@JSImport("child_process", JSImport.Namespace)
object ChildProcess extends SyncChildProcess
