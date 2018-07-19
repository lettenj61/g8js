package g8js

import scala.scalajs.js

object Formatter {

  def decapitalize(s: String) = if (s.isEmpty) s else s(0).toLower + s.substring(1)
  def startCase(s: String) = s.toLowerCase.split(" ").map(_.capitalize).mkString(" ")
  def wordOnly(s: String) = s.replaceAll("\\W", "")
  def upperCamel(s: String) = wordOnly(startCase(s))
  def lowerCamel(s: String) = decapitalize(upperCamel(s))
  def hyphenate(s: String) = s.replaceAll("\\s+", "-")
  def normalize(s: String) = hyphenate(s.toLowerCase)
  def snakeCase(s: String) = s.replaceAll("""[\s\.\-]+""", "_")
  def packageDir(s: String) = s.replace(".", "/")
  def addRandomId(s: String) = s + "-" + scala.util.Random.alphanumeric.take(32).mkString

  def apply(from: String, format: String): String = {
    format match {
      case "upper"|"uppercase"        => from.toUpperCase
      case "lower"|"lowercase"        => from.toLowerCase
      case "cap"|"capitalize"         => from.capitalize
      case "decap"|"decapitalize"     => decapitalize(from)
      case "start"|"start-case"       => startCase(from)
      case "word"|"word-only"         => wordOnly(from)
      case "Camel"|"upper-camel"      => upperCamel(from)
      case "camel"|"lower-camel"      => lowerCamel(from)
      case "hyphen"|"hyphenate"       => hyphenate(from)
      case "norm"|"normalize"         => normalize(from)
      case "snake"|"snake-case"       => snakeCase(from)
      case "packaged"|"package-dir"   => packageDir(from)
      case "random"|"generate-random" => addRandomId(from)
      case _                          => from
    }
  }
}

object Template {
  type Context = Map[String, String]

  val Varname = "\\${1}([\\w,;\\-=\"]+)\\${1}".r
  val FormatArgs = "format=\"([\\w,\\-]+)\"".r

  def expandPathPattern(path: String): String =
    path.replaceAll("""\$(\w+)__([\w,]+)\$""", """\$$1;format="$2"\$""")

  def replace(key: String, context: Context): String =
    context.getOrElse(key, key)

  def renderPath(path: String, context: Context): String =
    render(expandPathPattern(path), context)

  def render(body: String, context: Context): String = {
    val out = StringBuilder.newBuilder
    var input = body

    while (input != "") {
      val (lead, rest) = input.span(_ != '$')
      out ++= lead

      Varname.findFirstMatchIn(rest) match {
        case None =>
          input = if (rest == "") "" else rest.tail
        case Some(m) => {
          val varname = m.group(1)
          val rep = varname.split(";", 2) match {
            case Array(k, fmt) =>
              val v = replace(k, context)
              fmt match {
                case FormatArgs(args) => args.split(",").foldLeft(v)(Formatter.apply)
                case _ => v
              }
            case _ => replace(varname, context)
          }
          val before = m.before.toString.toBuffer
          var c0: Option[Char] = None
          while (before.nonEmpty) {
            val c = before.remove(0)
            c match {
              case '$' => c0 match {
                case Some('$') => out += '$'
                case _ =>
              }
              case _ => out += c
            }
            c0 = Some(c)
          }
          out ++= rep
          input = m.after.toString
        }
      }
    }

    out.result
  }
}
