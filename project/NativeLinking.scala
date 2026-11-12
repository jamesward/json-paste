import java.io.File
import java.util.zip.ZipFile
import sbt.*
import sbt.Keys.*
import scala.jdk.CollectionConverters.*
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.*

/** Chooses Scala Native or the JVM for the build, and wires the Native link the way Kyo documents it.
  *
  * Native when the headers of kyo-net's and kyo-http's C shims are present (`openssl/ssl.h` and `liburing.h`): on Heroku, where
  * buildpack-scala-native installs them, and in CI. Elsewhere the build is a JVM app for development with `~runReload`.
  * `-Dnative=true|false` overrides the detection.
  *
  * Kyo's Scala Native builds (kyo-net README, "Scala Native builds") fold the link and compile flags kyo-net declares for its C
  * shims into `nativeConfig` with kyo-ffi-plugin's `ffiNativeDependencyLinkingOptions` / `ffiNativeDependencyCompileOptions`.
  * That plugin is published for sbt 1 only, so these tasks do the same thing for sbt 2: read the
  * `META-INF/kyo-ffi/native-{link,compile}-flags/<module>-<os>.flags` manifests off the dependency classpath. On top of those:
  * `-lssl -lcrypto` (kyo-http's TLS needs the system OpenSSL, which the manifests leave out) and
  * `-z noexecstack` (Scala Native's hand-written assembly has no `.note.GNU-stack` section).
  */
object NativeLinking extends AutoPlugin {
  override def requires: Plugins      = ScalaNativePlugin
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val ffiNativeDependencyLinkingOptions = taskKey[Seq[String]]("Native link flags declared by dependencies' kyo-ffi manifests")
    val ffiNativeDependencyCompileOptions = taskKey[Seq[String]]("Native compile flags declared by dependencies' kyo-ffi manifests")
  }
  import autoImport.*

  private def onSearchPath(envVars: Seq[String], fallback: Seq[String], fileName: String): Boolean =
    (envVars.flatMap(sys.env.get).flatMap(_.split(File.pathSeparatorChar).toSeq) ++ fallback)
      .filter(_.nonEmpty)
      .exists(dir => new File(dir, fileName).isFile)

  private val includeDirs = Seq("/usr/include", "/usr/local/include", "/usr/include/x86_64-linux-gnu", "/usr/include/aarch64-linux-gnu")

  private def header(name: String): Boolean = onSearchPath(Seq("C_INCLUDE_PATH", "CPATH"), includeDirs, name)

  val nativeEnabled: Boolean = sys.props.get("native") match {
    case Some(v) => v.trim.toLowerCase != "false"
    case None    => header("liburing.h") && header("openssl/ssl.h")
  }

  /** kyo-ffi's OS tag for the build host (`CCompiler.detectOs`). */
  private def targetOs: String = {
    val name = sys.props("os.name").toLowerCase
    if (name.contains("mac")) "darwin"
    else if (name.contains("linux"))
      if (new File("/lib/ld-musl-x86_64.so.1").exists || new File("/lib/ld-musl-aarch64.so.1").exists) "linux-musl" else "linux"
    else if (name.contains("windows")) "windows"
    else sys.error(s"Unsupported OS: $name")
  }

  /** The flags in every `<dir>/<module>-<os>.flags` manifest of the classpath's jars and directories, one flag per line. */
  def readManifests(classpath: Seq[File], dir: String): Seq[String] = {
    val suffix = s"-$targetOs.flags"
    classpath.flatMap { entry =>
      if (entry.isDirectory) {
        val d = new File(entry, dir)
        Option(d.listFiles).toSeq.flatten.filter(_.getName.endsWith(suffix)).sortBy(_.getName).flatMap(IO.readLines(_))
      } else if (entry.isFile && entry.getName.endsWith(".jar")) {
        val zip = new ZipFile(entry)
        try
          zip.entries.asScala.toSeq
            .filter(e => e.getName.startsWith(dir + "/") && e.getName.endsWith(suffix))
            .sortBy(_.getName)
            .flatMap(e => IO.readStream(zip.getInputStream(e)).linesIterator.toSeq)
        finally zip.close()
      } else Seq.empty
    }.map(_.trim).filter(_.nonEmpty)
  }

  override def projectSettings: Seq[Setting[?]] = Seq(
    ffiNativeDependencyLinkingOptions := Def.uncached {
      val conv = fileConverter.value
      readManifests((Compile / dependencyClasspath).value.map(a => conv.toPath(a.data).toFile), "META-INF/kyo-ffi/native-link-flags")
    },
    ffiNativeDependencyCompileOptions := Def.uncached {
      val conv = fileConverter.value
      readManifests((Compile / dependencyClasspath).value.map(a => conv.toPath(a.data).toFile), "META-INF/kyo-ffi/native-compile-flags")
    },
    nativeConfig := {
      val base = nativeConfig.value
      base
        .withLinkingOptions(base.linkingOptions ++ ffiNativeDependencyLinkingOptions.value ++ Seq("-lssl", "-lcrypto", "-Wl,-z,noexecstack"))
        .withCompileOptions(base.compileOptions ++ ffiNativeDependencyCompileOptions.value)
    },
  )
}
