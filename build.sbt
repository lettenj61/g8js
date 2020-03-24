val scalaV = "2.13.1"
val libV = "0.0.3"

lazy val sharedSettings = Seq(
  organization := "com.github.lettenj61",
  version := libV,
  scalaVersion := scalaV,
  scalacOptions in Compile ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    // "-Xfatal-warnings",
    "-Xlint",
    "-P:scalajs:sjsDefinedByDefault"
  ),
)

lazy val g8js = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(sharedSettings)
  .settings(
    name := "g8js",
    libraryDependencies ++= Seq(
      "com.lihaoyi"       %%% "utest" % "0.7.4" % "test",
      "com.github.scopt"  %%% "scopt" % "3.7.1",
      "net.exoego"        %%% "scala-js-nodejs-v12" % "0.10.0"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    mainClass in Compile := Some("g8js.App"),
    scalaJSLinkerConfig ~= {
      _.withSourceMap(false).withModuleKind(ModuleKind.CommonJSModule)
    },
    scalaJSUseMainModuleInitializer := true
  )
