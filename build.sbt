name := "rcfbot"

scalaVersion := "2.12.8"

version := "0.0.1"

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.12.8",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "com.github.slack-scala-client" %% "slack-scala-client" % "0.2.6",
  "org.reactivemongo" %% "reactivemongo" % "0.16.0"
) ++ akka("2.5.17") ++ akkahttp("10.1.5")

def akka(v: String) = Seq(
  "com.typesafe.akka" %% "akka-actor" % v,
  "com.typesafe.akka" %% "akka-stream" % v,
  "com.typesafe.akka" %% "akka-slf4j" % v
  //  "com.typesafe.akka" %% "akka-cluster" % v
)

def akkahttp(v: String) = Seq(
  "com.typesafe.akka" %% "akka-http" % v,
  "com.typesafe.akka" %% "akka-http-testkit" % v,
  "com.typesafe.akka" %% "akka-http-spray-json" % v
  //"com.typesafe.akka" %% "akka-http-spray-json" % v
)