package g8js

import scala.scalajs.js
import scala.scalajs.js.JSStringOps._

import Properties.Pair

case class Properties(keyValues: js.Array[Pair]) {
  def get(key: String): Option[String] =
    keyValues.find(_._1 == key).map(_._2)

  def set(key: String, value: String): this.type = {
    val i = keyValues.indexWhere(_._1 == key)
    if (i > -1) {
      keyValues(i) = (key, value)
    } else {
      keyValues.push((key, value))
    }
    this
  }

  def mergeAndReport(params: Map[String, String]): Seq[String] = {
    // Update props with params
    params.foreach { case (k, v) => set(k, v) }
    // Report back what else need to fill
    keyValues.map(_._1) diff params.keys.toSeq
  }

  def resolve(): this.type = {
    // Substitute
    val ctx = keyValues.toMap
    keyValues.indices.foreach { i =>
      val (k, v) = keyValues(i)
      Template.Varname.findFirstMatchIn(v) match {
        case Some(_) =>
          keyValues(i) = (k, Template.render(v, ctx))
        case _ =>
      }
    }
    this
  }
}

object Properties {
  type Pair = (String, String)

  def empty(): Properties = new Properties(js.Array())

  def parse(string: String): Properties = new Properties(
    string
      .jsReplace(new js.RegExp("\r\n", "g"), "\n")
      .jsSplit("\n")
      .filter(s => s != "" && !s.startsWith("#"))
      .map { kv =>
        val (k, v) = kv.span(_ != '=')
        (k.trim, v.tail)
      }
  )
}
