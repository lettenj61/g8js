package g8js

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import scala.scalajs.js

import scopt.OptionParser

import io.scalajs.nodejs.child_process.{ChildProcess, ExecOptions}
import io.scalajs.nodejs.console_module.{Console => console}
import io.scalajs.nodejs.process.Process
import io.scalajs.nodejs.fs.{Fs, Stats}
import io.scalajs.nodejs.os.OS
import io.scalajs.nodejs.readline._

case class Config(
    repo: String = "",
    host: String = "github.com",
    out: Option[String] = None,
    props: Map[String, String] = Map(),
    noGenerate: Boolean = false,
    noCache: Boolean = false,
    verbose: Boolean = false,
    yes: Boolean = false,
    var cloneRoot: String = "",
    var workDir: String = "",
    var files: Seq[String] = Nil)

trait Operations {
  def mkdirs(pathname: String): Unit = {
    var entry                  = pathname
    val root                   = Path.parseSafe(Path.resolve()).root
    val dirs: js.Array[String] = js.Array()
    while (entry != "" && root != entry) {
      dirs.push(entry)
      entry = Path.dirname(entry)
    }
    while (dirs.length > 0) {
      val dir = dirs.pop()
      if (!Fs.existsSync(dir)) {
        Fs.mkdirSync(dir)
      }
    }
  }

  def rmdirs(base: String): Unit = {
    val sepChar = Path.sep.charAt(0)
    def rmdirInner(files: Seq[String]): Unit = {
      files.foreach { f =>
        val stats = Fs.lstatSync(f)
        if (!stats.isDirectory()) {
          Fs.unlinkSync(f)
        } else {
          Fs.rmdirSync(f)
        }
      }
    }
    val files = listFiles(base)
      .sortBy(_.count(_ == sepChar))
      .reverse

    rmdirInner(files)
    Fs.rmdirSync(base)
  }

  def gitClone(config: Config, cloneRoot: String): Try[String] = Try {
    val Array(user, repo) = config.repo.split("/", 2)
    val url               = s"https://${config.host}/$user/$repo"
    val target = Path.normalize(
      Path.join(
        cloneRoot,
        if (config.noCache) repo else s"${config.host}/${config.repo}"
      )
    )

    if (Fs.existsSync(target)) target
    else {
      val gitCommand = s"git clone $url $target"
      if (config.verbose) {
        if (!config.noCache) {
          println(s"No cache found for template [$url]")
        }
        println("Attempt to run git with: ")
        println(s"`$gitCommand`")
      }
      ChildProcess.execSync(gitCommand, new ExecOptions {
        // overrides
        cwd = cloneRoot
        // extra fields
        val stdio: String = "inherit"
      })
      target
    }
  }

  def absolutePaths(base: String): js.Array[String] =
    Fs.readdirSync(base).map(f => Path.join(base, f))

  def templateFiles(baseDir: String): Try[(String, Seq[String])] = Try {
    val templateRoot = Path.join(baseDir, "src/main/g8")
    if (!Fs.existsSync(templateRoot)) {
      throw new NoSuchElementException(templateRoot)
    } else {
      (templateRoot, listFiles(templateRoot))
    }
  }

  def listFiles(baseDir: String): Seq[String] = {
    // TODO make it readable
    @tailrec def loop(current: String, rest: Seq[String], collect: Seq[String]): Seq[String] = {
      val isDir = Fs.lstatSync(current).isDirectory()
      if (rest.isEmpty) {
        if (!isDir) collect :+ current
        else {
          absolutePaths(current).toList match {
            case Nil =>
              collect :+ current
            case x :: xs =>
              loop(x, xs, collect :+ current)
          }
        }
      } else {
        if (!isDir) {
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

    val children = absolutePaths(baseDir).toIndexedSeq
    loop(children.head, children.tail, Nil)
  }

  def findProps(paths: Seq[String]): Properties = {
    paths
      .find(p => Path.parseSafe(p).base == "default.properties")
      .flatMap { path =>
        Try {
          val content = Fs.readFileSync(path, "utf8")
          Properties.parse(content)
        } match {
          case Success(props) => Some(props)
          case Failure(err) =>
            console.warn(s"Failed to parse properties: ${err}")
            Some(Properties.empty())
        }
      }
      .getOrElse { Properties.empty() }
  }

  lazy val appHome: String =
    Path.join(OS.homedir(), "_g8js")

  def selectWorkDir(config: Config): Try[String] = Try {
    if (config.noCache) {
      Fs.mkdtempSync(OS.tmpdir() + "/g8js_")
    } else {
      if (!Fs.existsSync(appHome)) {
        mkdirs(appHome)
      }
      appHome
    }
  }

  def generate(config: Config): Unit =
    (for {
      cloneRoot        <- selectWorkDir(config)
      templateDir      <- gitClone(config, cloneRoot)
      (workDir, files) <- templateFiles(templateDir)
      props            = findProps(files)
    } yield (cloneRoot, workDir, props, files)) match {
      case Success((cloneRoot, workDir, props, files)) =>
        if (config.verbose) {
          println(s"Template ${config.host}/${config.repo} includes: ")
          files.foreach(f => println("  " + f))
        }
        // Fill params with command line args
        val whitelist = props.mergeAndReport(config.props)
        // Update config with params for current run
        config.cloneRoot = cloneRoot
        config.workDir = workDir
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
      val checkPath   = Path.parseSafe(path)
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
      val defaultProps = Path.join(config.workDir, "default.properties")
      val name = props
        .get("name")
        .map(Formatter.normalize(_))
        .getOrElse {
          val generated = Formatter.addRandomId("g8js")
          console.warn("No 'name' property specified. Generating randomly: " + generated)
          props.set("name", generated)
          generated
        }
      val targetRoot = config.out
        .map(normalizePath)
        .getOrElse { Path.join(Process.cwd(), name) }

      // Finally we can resolve variables in props
      props.resolve()

      val ctx = props.keyValues.filter { case (key, _) => key != "verbatim" }.toMap
      val pathFilters: Seq[PathFilter] =
        props
          .get("verbatim")
          .map(e => e.split(" ").map(new PathFilter(_)).toSeq)
          .getOrElse(Nil)

      // Process each template file
      if (config.verbose && config.noGenerate) {
        println("'--no-generate' flag detected\n")
      } else {
        Operations.this.mkdirs(targetRoot)
      }
      for (file <- config.files if file != defaultProps) {
        val toPath = Template.renderPath(
          Path.join(targetRoot, file.stripPrefix(config.workDir)),
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
      if (config.noCache && config.cloneRoot != "") {
        Operations.this.rmdirs(config.cloneRoot)
      }
      println("")
      println(s"Successfully generated: $targetRoot")
    }
  }

  abstract class FileProcessor {
    def copy(from: String, to: String): Unit
    def mkdir(path: String): Unit
    def render(src: String, dest: String, ctx: Map[String, String]): Unit
    def error(from: String, to: String): Unit =
      println(s"Cannot process '$from' to '$to', as it is neither directory nor file")

    def run(
        stats: Stats,
        from: String,
        to: String,
        pathFilters: Seq[PathFilter],
        ctx: Map[String, String]
      ): Unit = {
      if (stats.isDirectory()) {
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
      def copy(from: String, to: String): Unit = println(s"COPY: $to")
      def mkdir(path: String): Unit            = println(s"MKDIR: $path")
      def render(src: String, dest: String, ctx: Map[String, String]): Unit = {
        println("RENDER: ")
        println(src + " =>")
        println(dest)
      }
    }
  }

  class Prompt(config: Config, props: Properties, whitelist: Seq[String]) {
    prompt =>

    val questions: Iterator[(String, String)] =
      props.keyValues.iterator
        .filter(kv => whitelist.contains(kv._1))

    private def next(): Option[(String, String)] =
      if (questions.hasNext) Some(questions.next())
      else None

    def start(): Unit = {
      if (!config.yes && questions.hasNext) {
        val rl = Readline.createInterface(
          ReadlineOptions(
            input = Process.stdin,
            output = Process.stdout
          )
        )

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
        rl.on(
          "line",
          (input: String) => {
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
          }
        )
        // On finish, call generator
        rl.on("close", () => {
          new Generator(config, props).run()
        })
        // Show first prompt
        proceed()
      } else {
        if (config.verbose && config.yes) {
          println("'--yes' flag detected\n")
        }
        // When we don't need to show prompt, just run with defaults
        new Generator(config, props).run()
      }
    }
  }
}

object App extends Operations { self =>
  val parser: OptionParser[Config] = new OptionParser[Config]("g8js") {
    head("g8js", "0.0.3")

    help("help").abbr("h") text ("show this help message")

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

    opt[Unit]('n', "no-cache")
      .action { (_, config) => config.copy(noCache = true) }
      .text("do not cache template")

    opt[Unit]('v', "verbose")
      .action { (_, config) => config.copy(verbose = true) }
      .text("verbose output")

    opt[Unit]('y', "yes")
      .action { (_, config) => config.copy(yes = true) }
      .text("no prompt and use defaults ('-p' options are respected)")

    opt[Map[String, String]]('p', "props")
      .valueName("key1=value1,key2=value2...")
      .unbounded()
      .action { (more, config) => config.copy(props = config.props ++ more) }
      .text("additional key-value args (prior to defaults)")

    // implementations
    override def terminate(exitState: Either[String, Unit]): Unit = {
      Process.exit(0)
    }
  }

  def main(args: Array[String]): Unit = {
    parser.parse(Process.argv.drop(2), Config()) match {
      case Some(config) => generate(config)
      case None         =>
    }
  }
}
