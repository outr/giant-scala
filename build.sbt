name := "giant-scala"
organization := "com.matthicks"
version := "1.0.0"

scalaVersion := "2.12.4"

val profigVersion = "2.0.0"
val mongoScalaDriverVersion = "2.2.0"

libraryDependencies ++= Seq(
  "com.outr" %% "profig" % profigVersion,
  "org.mongodb.scala" %% "mongo-scala-driver" % mongoScalaDriverVersion
)