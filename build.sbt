val scalaV = "2.13.5"

lazy val sharedSettings = Seq(
  organization := "com.github.lettenj61",
  scalaVersion := scalaV,
  Compile / scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    // "-Xfatal-warnings",
    "-Xlint",
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
      "com.github.scopt"  %%% "scopt" % "4.0.1",
      "net.exoego"        %%% "scala-js-nodejs-v14" % "0.13.0"
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    Compile / mainClass := Some("g8js.App"),
    scalaJSLinkerConfig ~= {
      _.withSourceMap(false).withModuleKind(ModuleKind.CommonJSModule)
    },
    scalaJSUseMainModuleInitializer := true
  )
