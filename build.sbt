import sbtcrossproject.CrossPlugin.autoImport.crossProject

name := "giant-scala"
ThisBuild / organization := "com.outr"
ThisBuild / version := "1.5.1"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / crossScalaVersions := List("2.13.10")
ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")
ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("releases") ++ Resolver.sonatypeOssRepos("snapshots")

ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeProfileName := "com.outr"
ThisBuild / licenses := Seq("MIT" -> url("https://github.com/outr/giantscala/blob/master/LICENSE"))
ThisBuild / sonatypeProjectHosting := Some(xerial.sbt.Sonatype.GitHubHosting("outr", "giantscala", "matt@outr.com"))
ThisBuild / homepage := Some(url("https://github.com/outr/giantscala"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/outr/giantscala"),
    "scm:git@github.com:outr/giantscala.git"
  )
)
ThisBuild / developers := List(
  Developer(id="darkfrog", name="Matt Hicks", email="matt@matthicks.com", url=url("https://matthicks.com"))
)

ThisBuild / testOptions += Tests.Argument("-oD")

val scribeVersion = "3.10.4"
val profigVersion = "3.4.4"
val reactifyVersion = "4.0.8"
val mongoScalaDriverVersion = "4.7.2"
val catsEffectVersion: String = "3.3.14"
val fs2Version: String = "3.9.4"
val scalatestVersion: String = "3.2.14"
val catsEffectTestingVersion: String = "1.4.0"

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
      "co.fs2" %% "fs2-reactive-streams" % fs2Version,
      "com.outr" %%% "scribe" % scribeVersion,
      "com.outr" %% "scribe-slf4j" % scribeVersion,
      "org.scalatest" %%% "scalatest" % scalatestVersion % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest" % catsEffectTestingVersion % Test
    )
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
    )
  )

lazy val coreJS = core.js
lazy val coreJVM = core.jvm

//lazy val plugin = project.in(file("plugin"))
//  .settings(
//    name := "giant-scala-plugin",
//    sbtPlugin := true,
//    crossSbtVersions := Vector("0.13.18", "1.7.2")
//  )
//
//lazy val backup = project.in(file("backup"))
//  .dependsOn(coreJVM)