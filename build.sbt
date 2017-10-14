organization := "eu.inn"

name := "sbus"

version := "0.1"

scalaVersion := "2.12.3"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-optimise",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-encoding", "UTF-8",
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",

  "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.1",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.1",

  "com.rabbitmq" % "amqp-client" % "4.2.2",
  "eu.shiftforward" %% "amqp-client" % "1.6.0",

  "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
  "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"
)

pomExtra := {
  <url>https://github.com/kulikov/sbus</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://opensource.org/licenses/BSD-3-Clause</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:kulikov/sbus.git</url>
    <connection>scm:git:git@github.com:kulikov/sbus.git</connection>
  </scm>
  <developers>
    <developer>
      <id>kulikov</id>
      <name>Dmitry Kulikov</name>
      <url>https://github.com/kulikov</url>
    </developer>
  </developers>
}

pgpSecretRing := file("./travis/gpg-private.asc.gpg")

pgpPublicRing := file("./travis/gpg-public.asc.gpg")

usePgpKeyHex("1FC91057C33D1A33")

pgpPassphrase := Option(System.getenv().get("oss_gpg_passphrase")).map(_.toCharArray)

credentials += Credentials("Sonatype Nexus Repository Manager",
  "oss.sonatype.org",
  System.getenv().get("sonatype_username"),
  System.getenv().get("sonatype_password"))
