name := "reactive-office365"
organization := "com.github.lpld"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

val playWsVersion = "2.0.0-M1"
val akkaVersion = "2.5.12"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws-standalone" % playWsVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playWsVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,

  // test:
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "com.typesafe.play" %% "play-ahc-ws-standalone" % playWsVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
)