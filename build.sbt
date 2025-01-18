name := "rcfbot"

scalaVersion := "2.12.8"

version := "0.0.1"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.12.8",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime,
  "org.reactivemongo" %% "reactivemongo" % "0.16.0",
  "com.typesafe.play" %% "play-json" % "2.7.1",
  "org.scala-lang.modules" %% "scala-async" % "0.9.7",
  "com.googlecode.concurrentlinkedhashmap" % "concurrentlinkedhashmap-lru" % "1.4.2",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"

) ++ akka("2.5.17") ++ akkahttp("10.1.5")

def akka(v: String) = Seq(
  "com.typesafe.akka" %% "akka-actor" % v,
  "com.typesafe.akka" %% "akka-stream" % v,
  "com.typesafe.akka" %% "akka-slf4j" % v
  //  "com.typesafe.akka" %% "akka-cluster" % v
)

def akkahttp(v: String) = Seq(
  "com.typesafe.akka" %% "akka-http" % v,
//  "com.typesafe.akka" %% "akka-http-testkit" % v,
  "com.typesafe.akka" %% "akka-http-spray-json" % v
)