// One source tree, built as a Scala Native binary (Heroku, CI) or a JVM app (development with `./sbta ~runReload`), chosen by
// project/NativeLinking.scala. sbt-reload only works on the JVM.
import com.jamesward.sbtreload.ReloadPlugin

enablePlugins((if (NativeLinking.nativeEnabled) Seq[AutoPlugin](ScalaNativePlugin) else Nil)*)
disablePlugins((if (NativeLinking.nativeEnabled) Seq[AutoPlugin](ReloadPlugin) else Nil)*)

name         := "json-paste"
scalaVersion := "3.10.1"

val kyoVersion = "1.0.0-RC7"

// `%%` picks the _native0.5_3 artifacts when ScalaNativePlugin is enabled and the _3 ones on the JVM.
libraryDependencies ++= Seq(
  "io.getkyo" %% "kyo-core"  % kyoVersion,
  "io.getkyo" %% "kyo-http"  % kyoVersion,
  "io.getkyo" %% "kyo-ui"    % kyoVersion,

  "io.getkyo" %% "kyo-test-api"    % kyoVersion % Test,
  "io.getkyo" %% "kyo-test-runner" % kyoVersion % Test,
  "io.getkyo" %% "kyo-browser"     % kyoVersion % Test,
)

// kyo-test's framework class differs per platform; a wrong name runs zero tests and still passes.
Test / testFrameworks += new TestFramework(
  if (NativeLinking.nativeEnabled) "kyo.test.runner.NativeFramework" else "kyo.test.runner.SbtFramework"
)

// Compiler flags required by Kyo: they turn silent effect discards and
// untyped equality into compile errors.
scalacOptions ++= Seq(
  "-Wvalue-discard",
  "-Wnonunit-statement",
  "-Wconf:msg=(unused.*value|discarded.*value|pure.*statement):error",
  "-language:strictEquality"
)

Compile / mainClass := Some("jsonpaste.WebApp")

// JVM: run and test in a forked JVM, where kyo-net loads its shims with the foreign-function API. Scala Native runs its own
// test binary and requires fork to be off.
fork := !NativeLinking.nativeEnabled
javaOptions ++= Seq("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")

// SkillsJars: extract agent Skills with `./sbta extractSkillsJars`
skillsJarsOutputDir := Some(file(".kiro/skills"))

libraryDependencies += "com.jamesward" % "skills" % "0.0.26" % Skills
