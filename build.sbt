import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

name := "giant-scala"
organization in ThisBuild := "com.outr"
version in ThisBuild := "1.2.12"
scalaVersion in ThisBuild := "2.12.6"
crossScalaVersions in ThisBuild := List("2.12.6", "2.11.12")
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

publishTo in ThisBuild := sonatypePublishTo.value
sonatypeProfileName in ThisBuild := "com.outr"
publishMavenStyle in ThisBuild := true
licenses in ThisBuild := Seq("MIT" -> url("https://github.com/outr/giantscala/blob/master/LICENSE"))
sonatypeProjectHosting in ThisBuild := Some(xerial.sbt.Sonatype.GitHubHosting("outr", "giantscala", "matt@outr.com"))
homepage in ThisBuild := Some(url("https://github.com/outr/giantscala"))
scmInfo in ThisBuild := Some(
  ScmInfo(
    url("https://github.com/outr/giantscala"),
    "scm:git@github.com:outr/giantscala.git"
  )
)
developers in ThisBuild := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("http://matthicks.com"))
)

testOptions in ThisBuild += Tests.Argument("-oD")

val scribeVersion = "2.7.0"
val profigVersion = "2.3.4"
val reactifyVersion = "3.0.3"
val mongoScalaDriverVersion = "2.5.0"
val macroParadiseVersion = "2.1.1"
val scalatestVersion: String = "3.0.5"

lazy val root = project.in(file("."))
  .aggregate(macrosJS, macrosJVM, coreJS, coreJVM)
  .settings(
    name := "giant-scala",
    publish := {},
    publishLocal := {}
  )

lazy val macros = crossProject(JVMPlatform, JSPlatform)
  .in(file("macros"))
  .settings(
    name := "giant-scala-macros",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val macrosJS = macros.js
lazy val macrosJVM = macros.jvm

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .dependsOn(macros)
  .settings(
    name := "giant-scala",
    libraryDependencies ++= Seq(
      "com.outr" %%% "scribe" % scribeVersion,
      "com.outr" %%% "profig" % profigVersion,
      "com.outr" %%% "reactify" % reactifyVersion,
      "org.scalatest" %%% "scalatest" % scalatestVersion % Test
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full)
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
    )
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val plugin = project.in(file("plugin"))
  .dependsOn(coreJVM)
  .settings(
    name := "giant-scala-plugin",
    sbtPlugin := true,
    crossSbtVersions := Vector("0.13.17", "1.2.0")
  )

lazy val backup = project.in(file("backup"))
  .dependsOn(coreJVM)