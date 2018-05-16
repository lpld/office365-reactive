name := "reactive-office365"
organization := "com.github.lpld"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

val playAhcWsVersion = "2.0.0-M1"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-ws-standalone" % playAhcWsVersion,
  "com.typesafe.play" %% "play-ws-standalone-json" % playAhcWsVersion,
  "com.typesafe.akka" %% "akka-stream" % "2.5.11",

  // test:
    "com.typesafe.play" %% "play-ahc-ws-standalone" % playAhcWsVersion % "test"
)