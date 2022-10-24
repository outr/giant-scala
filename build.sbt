import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "giant-scala"
organization in ThisBuild := "com.outr"
version in ThisBuild := "1.5.0-SNAPSHOT"
scalaVersion in ThisBuild := "2.13.9"
crossScalaVersions in ThisBuild := List("2.13.9", "3.2.0")
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

val scribeVersion = "3.10.3"
val profigVersion = "3.4.4"
val reactifyVersion = "4.0.8"
val mongoScalaDriverVersion = "4.7.2"
val catsEffectVersion: String = "3.3.14"
val fs2Version: String = "3.3.0"
val scalatestVersion: String = "3.2.14"

lazy val root = project.in(file("."))
  .aggregate(coreJS, coreJVM)
  .settings(
    name := "giant-scala",
    publish := {},
    publishLocal := {}
  )

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .in(file("core"))
  .settings(
    name := "giant-scala",
    libraryDependencies ++= Seq(
      "com.outr" %%% "profig" % profigVersion,
      "com.outr" %%% "reactify" % reactifyVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
      "co.fs2" %%% "fs2-core" % fs2Version,
      "com.outr" %%% "scribe" % scribeVersion,
      "org.scalatest" %%% "scalatest" % scalatestVersion % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
    )
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

lazy val plugin = project.in(file("plugin"))
  .settings(
    name := "giant-scala-plugin",
    sbtPlugin := true,
    crossSbtVersions := Vector("0.13.18", "1.2.8")
  )

lazy val backup = project.in(file("backup"))
  .dependsOn(coreJVM)