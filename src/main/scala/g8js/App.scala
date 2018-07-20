package g8js

import scala.annotation.tailrec
import scala.util.{ Try, Success, Failure }
import scala.scalajs.js

import scopt.OptionParser

import io.scalajs.nodejs.{ console, process }
import io.scalajs.nodejs.child_process.ExecOptions
import io.scalajs.nodejs.fs.{ Fs, Stats }
import io.scalajs.nodejs.os.OS
// import io.scalajs.nodejs.path.Path <= `Path` object below comes from `NodeJSExtensions.scala`
import io.scalajs.nodejs.readline._

case class Config(
  repo: String = "",
  host: String = "github.com",
  out: Option[String] = None,
  params: Map[String, String] = Map(),
  noGenerate: Boolean = false,
  var cachedRoot: String = "",
  var files: Seq[String] = Nil
)

trait Operations {
  def mkdirs(pathname: String): Unit = {
    var entry = pathname
    val root = Path.parseSafe(Path.resolve()).root
    val dirs: js.Array[String] = js.Array()
    while (entry != "" && root != entry) {
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
    val url = s"https://${config.host}/$user/$repo"
    val cache = Path.normalize(Path.join(cacheDir, s"${config.host}/${config.repo}"))
    if (Fs.existsSync(cache)) cache
    else {
      // This is defined in NodeJSExtensions, not ScalaJS.io facade
      ChildProcess.execSync(s"git clone $url $cache", new ExecOptions(cwd = cacheDir))
      cache
    }
  }

  def templateFiles(baseDir: String): Try[(String, Seq[String])] = Try {
    val templateRoot = Path.join(baseDir, "src/main/g8")
    if (!Fs.existsSync(templateRoot)) {
      throw new NoSuchElementException(templateRoot)
    } else {
      (templateRoot, listFiles(templateRoot))
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
      p => Path.parseSafe(p).base == "default.properties"
    ).flatMap { path =>
      Try {
        val content = Fs.readFileSync(path, "utf8")
        Properties.parse(content)
      } match {
        case Success(props) => Some(props)
        case Failure(err) =>
          console.warn(s"Failed to parse properties: ${err}")
          Some(Properties.empty)
      }
    }.getOrElse { Properties.empty }
  }

  lazy val homeDir: String =
    Path.join(OS.homedir(), "_g8js")

  def setup(): Try[Unit] = Try {
    if (!Fs.existsSync(homeDir)) {
      mkdirs(homeDir)
    }
  }

  def generate(config: Config): Unit = (for {
    _ <- setup()
    cloneDir <- gitClone(config, homeDir)
    (cachedRoot, files) <- templateFiles(cloneDir)
    props = findProps(files)
  } yield (cachedRoot, props, files)) match {
    case Success((cachedRoot, props, files)) =>
      // Fill params with command line args
      val whitelist = props.mergeAndReport(config.params)
      // Update config with params for current run
      config.cachedRoot = cachedRoot
      config.files = files
      new Prompt(config, props, whitelist).start()
    case Failure(err) =>
      throw js.JavaScriptException(err)
  }

  /**
   * Filter function used to exclude files specified by 'verbatim' option.
   */
  class PathFilter(val pattern: String) extends (String => Boolean) {
    /**
     * Returns `true` when given path matches pattern
     */
    def apply(path: String): Boolean = {
      val checkPath = Path.parseSafe(path)
      val patternPath = Path.parseSafe(pattern)
      patternPath.name match {
        case "*" =>
          // On wildcard like "*.html"
          checkPath.ext == patternPath.ext
        case _ =>
          // On normal file name, like "Dockerfile" or "app.conf",
          // test with base name
          checkPath.base == patternPath.base
      }
    }
  }

  class Generator(config: Config, props: Properties) {
    def normalizePath(path: String): String =
      if (Path.isAbsolute(path)) path
      else Path.join(Path.resolve(), path)

    def run(): Unit = {
      val defaultProps = Path.join(config.cachedRoot, "default.properties")
      val name = props
        .get("name")
        .map(Formatter.normalize(_))
        .getOrElse {
          throw new IllegalArgumentException("Error: 'name' property must not be empty.")
        }
      val targetRoot = config.out
        .map(normalizePath)
        .getOrElse { Path.join(process.cwd(), name) }

      // Finally we can resolve variables in props
      props.resolve()

      val ctx = props.keyValues.toMap.filterKeys(_ != "verbatim")
      val pathFilters: Seq[PathFilter] =
        props.get("verbatim")
          .map(e => e.split(" ").map(new PathFilter(_)).toSeq)
          .getOrElse(Nil)

      // Process each template file
      if (config.noGenerate) {
        println("'--no-generate' flag found.")
      } else {
        Operations.this.mkdirs(targetRoot)
      }
      for (file <- config.files if file != defaultProps) {
        val toPath = Template.renderPath(
          Path.join(targetRoot, file.stripPrefix(config.cachedRoot)),
          ctx
        )
        val fromStats = Fs.lstatSync(file)
        val gen: FileProcessor =
          if (config.noGenerate) FileProcessor.NoGenerate
          else FileProcessor.Emit

        gen.run(
          fromStats,
          file,
          toPath,
          pathFilters,
          ctx
        )
      }
      println(s"Successfully generated: $targetRoot")
    }
  }

  abstract class FileProcessor {
    def copy(from: String, to: String): Unit
    def mkdir(path: String): Unit
    def render(src: String, dest: String, ctx: Map[String, String])
    def error(from: String, to: String): Unit =
      println(s"Cannot process '$from' to '$to', as it is neither directory nor file")

    def run(
      stats: Stats,
      from: String,
      to: String,
      pathFilters: Seq[PathFilter],
      ctx: Map[String, String]
    ): Unit = {
      if (stats.isDirectory) {
        mkdir(to)
      } else if (stats.isFile()) {
        if (pathFilters.exists(matcher => matcher(to))) {
          copy(from, to)
        } else {
          render(from, to, ctx)
        }
      } else {
        error(from, to)
      }
    }
  }
  object FileProcessor {
    object Emit extends FileProcessor {
      def copy(from: String, to: String): Unit =
        Fs.copyFileSync(from, to, 0)
      def mkdir(path: String): Unit = {
        if (!Fs.existsSync(path)) {
          Operations.this.mkdirs(path)
        }
      }
      def render(src: String, dest: String, ctx: Map[String, String]): Unit = {
        val content = Fs.readFileSync(src, "utf8")
        Fs.writeFileSync(dest, Template.render(content, ctx))
      }
    }
    object NoGenerate extends FileProcessor {
      def copy(from: String, to: String): Unit = println(s"Copying (without rendering): $to")
      def mkdir(path: String): Unit = println(s"Creating directory: $path")
      def render(src: String, dest: String, ctx: Map[String, String]): Unit =
        println(s"Rendering template: $src => $dest")
    }
  }

  class Prompt(
    config: Config,
    props: Properties,
    whitelist: Seq[String]
  ) {
    prompt =>

    val questions: Iterator[(String, String)] =
      props
        .keyValues
        .iterator
        .filter(kv => whitelist.contains(kv._1))

    private def next(): Option[(String, String)] =
      if (questions.hasNext) Some(questions.next())
      else None

    def start(): Unit = {
      if (questions.hasNext) {
        val rl = Readline.createInterface(new ReadlineOptions(
          input = process.stdin,
          output = process.stdout
        ))

        var current = prompt.next() // FIXME: Remove mutable state

        def proceed(): Unit = {
          current match {
            case Some((k, v)) =>
              rl.setPrompt(s"$k [$v]:")
              rl.prompt(true)
            case None =>
              rl.close()
          }
        }

        // Complete props with user input
        rl.on("line", (input: String) => {
          current match {
            case Some((k, _)) =>
              val trimmed = input.trim
              if (trimmed != "") {
                prompt.props.set(k, trimmed)
              }
              // Move to next question
              current = prompt.next()
              proceed()
            case None =>
              rl.close() // FIXME: remove duplicated code
          }
        })
        // On finish, call generator
        rl.on("close", () => {
          new Generator(config, props).run()
        })
        // Show first prompt
        proceed()
      } else {
        // When we don't need to show prompt, just run with defaults
        new Generator(config, props).run()
      }
    }
  }
}

object App extends Operations { self =>
  val parser: OptionParser[Config] = new OptionParser[Config]("g8js") {
    head("g8js", "0.0.1")

    help("help").text("show this help message")

    arg[String]("<template>")
      .required()
      .action { (repo, config) => config.copy(repo = repo) }
      .text("github user/repo")

    opt[String]('H', "host")
      .valueName("[hostname]")
      .action { (host, config) => config.copy(host = host) }
      .text("hostname for template repository (default: 'github.com')")

    opt[String]('o', "out")
      .valueName("[path]")
      .action { (out, config) => config.copy(out = Some(out)) }
      .text("output directory")

    opt[Unit]('D', "no-generate")
      .action { (_, config) => config.copy(noGenerate = true) }
      .text("never generate files (`git clone` will be executed anyway)")

    opt[Map[String, String]]('p', "params")
      .valueName("key1=value1,key2=value2...")
      .unbounded()
      .action { (more, config) => config.copy(params = config.params ++ more) }
      .text("additional key-value args (prior to defaults)")

    // implementations
    override def terminate(exitState: Either[String, Unit]): Unit = {
      process.exit(0)
    }
  }

  def main(args: Array[String]): Unit = {
    parser.parse(process.argv.drop(2), Config()) match {
      case Some(config) => generate(config)
      case None =>
    }
  }
}
