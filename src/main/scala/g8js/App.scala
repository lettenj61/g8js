package g8js

import scala.annotation.tailrec
import scala.util.{ Try, Success, Failure }
import scala.scalajs.js

import scopt.OptionParser

import io.scalajs.nodejs.{ console, process }
import io.scalajs.nodejs.child_process.ExecOptions
import io.scalajs.nodejs.fs.Fs
import io.scalajs.nodejs.os.OS
import io.scalajs.nodejs.path.Path
import io.scalajs.nodejs.readline._

case class Config(
  repo: String = "",
  out: Option[String] = None,
  params: Map[String, String] = Map()
)

trait Operations {

  def normalizePath(path: String): String =
    if (Path.isAbsolute(path)) path
    else Path.join(Path.resolve(), path)

  def mkdirs(pathname: String): Unit = {
    var entry = pathname
    val root = Path.parse(Path.resolve()).root
    val dirs: js.Array[String] = js.Array()
    while (entry != "" && root.filter(_ != entry).nonEmpty) {
      dirs.push(entry)
      entry = Path.dirname(entry)
    }
    while (dirs.length > 0) {
      val dir = dirs.pop
      if (!Fs.existsSync(dir)) {
        Fs.mkdirSync(dir)
      }
    }
  }

  def gitClone(config: Config, cacheDir: String): Try[String] = Try {
    val Array(user, repo) = config.repo.split("/", 2)
    val url = s"https://github.com/$user/$repo"
    val cache = Path.join(cacheDir, repo)
    if (Fs.existsSync(cache)) cache
    else {
      // This is defined in NodeJSExtensions, not ScalaJS.io facade
      ChildProcess.execSync(s"git clone $url $repo", new ExecOptions(cwd = cacheDir))
      cache
    }
  }

  def templateFiles(baseDir: String): Try[Seq[String]] = Try {
    val templateRoot = Path.join(baseDir, "src/main/g8")
    if (!Fs.existsSync(templateRoot)) {
      throw new NoSuchElementException(templateRoot)
    } else {
      listFiles(templateRoot)
    }
  }

  def listFiles(baseDir: String): Seq[String] = {
    def absolutePaths(base: String): Seq[String] =
      Fs.readdirSync(base).map(f => Path.join(base, f))

    @tailrec def loop(
      current: String,
      rest: Seq[String],
      collect: Seq[String]
    ): Seq[String] = {
      if (rest.isEmpty) collect
      else {
        if (!Fs.lstatSync(current).isDirectory) {
          loop(rest.head, rest.tail, collect :+ current)
        } else {
          loop(
            rest.head,
            rest.tail ++ absolutePaths(current),
            collect :+ current
          )
        }
      }
    }

    val children = absolutePaths(baseDir)
    loop(children.head, children.tail, Nil)
  }

  def findProps(paths: Seq[String]): Properties = {
    paths.find(
      p => Path.parse(p).base == js.defined("default.properties")
    ).flatMap { path =>
      Try {
        val content = Fs.readFileSync(path).toString()
        Properties.parse(content)
      } match {
        case Success(props) => Some(props)
        case Failure(err) =>
          console.warn(s"Failed to parse properties: ${err}")
          Some(Properties.empty)
      }
    }.getOrElse { Properties.empty }
  }

  def runPrompt(
    props: Properties,
    config: Config
  ): Properties = {
    val requests = props.keyValues.map(_._1)
    props.resolve(config.params)

    val prompt = new Prompt(props, requests)
    prompt.start()

    prompt.questions.foreach { case (k, v) =>
      prompt.rl.question(s"$k [$v]:", (input: String) => {
        if (input.trim != "") {
          props.set(k, input.trim())
        }
      })
    }

    prompt.rl.close()
    props
  }

  lazy val homeDir: String =
    Path.join(OS.homedir(), "_g8js")

  def setup(): Try[Unit] = Try {
    if (!Fs.existsSync(homeDir)) {
      mkdirs(homeDir)
    }
  }

  def generate(config: Config): Unit = {
    val result = for {
      _ <- setup()
      cloneDir <- gitClone(config, homeDir)
      files <- templateFiles(cloneDir)
      props = runPrompt(findProps(files), config)
    } yield (props, files)

    result match {
      case Success((p, files)) =>
        println(files)
        console.log(p.keyValues)
      case Failure(err) =>
        throw js.JavaScriptException(err)
    }
  }
}

class Prompt(props: Properties, requests: Seq[String]) {
  self =>

  val questions: Iterator[(String, String)] =
    props
      .keyValues
      .iterator
      .filter(kv => requests.contains(kv._1))

  var rl: Interface = null

  def start(): Unit = {
    rl = Readline.createInterface(new ReadlineOptions(
      input = process.stdin,
      output = process.stdout
    ))
  }
}

object App extends Operations { self =>
  val parser: OptionParser[Config] = new OptionParser[Config]("g8js") {
    head("g8js", "0.0.1")

    arg[String]("<template>")
      .required()
      .action { (repo, config) => config.copy(repo = repo) }
      .text("github user/repo")

    opt[String]('o', "out")
      .action { (out, config) => config.copy(out = Some(out)) }
      .text("output directory")

    opt[Map[String, String]]("params")
      .valueName("key1=value1,key2=value2...")
      .action { (params, config) => config.copy(params = params) }
      .text("additional key-value args (prior to defaults)")
  }

  def main(args: Array[String]): Unit = {
    parser.parse(process.argv.drop(2), Config()) match {
      case Some(config) => generate(config)
      case None =>
    }
  }
}
