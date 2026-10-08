enablePlugins(LauncherJarPlugin)

name         := "json-paste"
scalaVersion := "3.10.0"

val kyoVersion = "1.0.0-RC7"

libraryDependencies ++= Seq(
  "io.getkyo" %% "kyo-core"  % kyoVersion,
  "io.getkyo" %% "kyo-http"  % kyoVersion,
  "io.getkyo" %% "kyo-ui"    % kyoVersion,

  "io.getkyo" %% "kyo-test-api"    % kyoVersion % Test,
  "io.getkyo" %% "kyo-test-runner" % kyoVersion % Test,
  "io.getkyo" %% "kyo-browser"     % kyoVersion % Test,
)

// kyo-test integrates as an sbt test framework (no plugin published).
testFrameworks += new TestFramework("kyo.test.runner.SbtFramework")

// Compiler flags required by Kyo: they turn silent effect discards and
// untyped equality into compile errors.
scalacOptions ++= Seq(
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
  "-language:strictEquality"
)

Compile / mainClass := Some("jsonpaste.WebApp")

// Keep the distribution lean: no scaladoc artifact.
Compile / packageDoc / publishArtifact := false
Compile / doc / sources                := Seq.empty

// sbt-mcp (loopback-only: its tools can execute build tasks)
Global / mcpEnabled := true
Global / mcpHost := "127.0.0.1"
Global / mcpPort := 5107

// SkillsJars: extract agent Skills with `./sbt extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.12" % Skills
