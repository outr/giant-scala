name := "giant-scala"
organization in ThisBuild := "com.outr"
version in ThisBuild := "1.0.6-SNAPSHOT"
scalaVersion in ThisBuild := "2.12.5"
crossScalaVersions in ThisBuild := List("2.12.5", "2.11.12")
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation")
resolvers in ThisBuild ++= Seq(
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

publishTo in ThisBuild := sonatypePublishTo.value
sonatypeProfileName in ThisBuild := "com.outr"
publishMavenStyle in ThisBuild := true
licenses in ThisBuild := Seq("MIT" -> url("https://github.com/outr/giantscala/blob/master/LICENSE"))
sonatypeProjectHosting in ThisBuild := Some(xerial.sbt.Sonatype.GithubHosting("outr", "giantscala", "matt@outr.com"))
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

val scribeVersion = "2.3.2"
val profigVersion = "2.2.1"
val reactifyVersion = "2.3.0"
val mongoScalaDriverVersion = "2.2.1"
val scalatestVersion: String = "3.0.5"

lazy val root = project.in(file("."))
  .aggregate(macros, core)
  .settings(
    name := "giant-scala",
    publish := {},
    publishLocal := {}
  )

lazy val macros = project.in(file("macros"))
  .settings(
    name := "giant-scala-macros",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val core = project.in(file("core"))
  .dependsOn(macros)
  .settings(
    name := "giant-scala",
    libraryDependencies ++= Seq(
      "com.outr" %% "scribe-slf4j" % scribeVersion,
      "com.outr" %% "profig" % profigVersion,
      "com.outr" %% "reactify" % reactifyVersion,
      "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion,
      "org.scalatest" %% "scalatest" % scalatestVersion % Test
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )