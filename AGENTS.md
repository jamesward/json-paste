# AGENTS.md: json-paste

JSON Paste: a Kyo server-push web app (`jsonpaste.WebApp`) that pretty-prints pasted JSON live. Built as a Scala Native binary for production and CI, and as a JVM app for development.

## Agent tooling

- Follow the `zen-of-projects` Skill (extract with `./sbta extractSkillsJars` into the gitignored `.kiro/skills/`); this file records only project-specific facts and exceptions.
- For Scala 3 and ZIO code (writing or reviewing it), follow `zen-of-scala`. For design, domain modeling, effects and testing strategy, follow `zen-of-james`. Read the relevant Skills in `.kiro/skills/` before working.
- MCP server `sbta` (this build's `./sbta --mcp`, configured in `.mcp.json` and `.kiro/settings/mcp.json`, approved in `.claude/settings.json`): use it for all sbt work: `compile`, `test` (`full` for all), `run`, `check` after editing Scala 3 sources, `glob-search` / `inspect` / `symbol-location` for symbols, `sbt-task` for any other command. Its tools are deferred in Claude Code: load them with ToolSearch (search `sbta`). If it's unavailable, say so and use `./sbta <command>`. Logs: `project/target/sbta/mcp.log`.
- Maintenance routine: `.factory/MAINTENANCE.md` (weekly), following the `zen-of-projects` Skill.

## Build and validation

- One source tree, two platforms, chosen by `project/NativeLinking.scala`: Scala Native when the headers of kyo-net's and
  kyo-http's C shims are present (`openssl/ssl.h` and `liburing.h`: on Heroku, where buildpack-scala-native installs them,
  and in CI), the JVM otherwise. `-Dnative=true|false` overrides the detection.
- Full validation (the native equivalent of `stage` is `nativeLink`): `./sbta shutdown`, `./sbta extractSkillsJars`, then
  `./sbta -Dnative=true "Test / compile; testFull; nativeLink"`, then the smoke test in `.github/workflows/ci.yml`. CI also
  runs `./sbta -Dnative=false "Test / compile; testFull"`.
- Needs Java 25, clang, `libssl-dev` and `liburing-dev` for the Native build. The binary:
  `target/out/native0.5/scala-3.10.1/json-paste/json-paste`.
- Dev run: `./sbta ~runReload` (add `-Dnative=false` where the Scala Native headers are installed) restarts the JVM app on
  every change; sbt-reload works on the JVM build only. No tests need Docker or paid services; `BrowserIntegrationTest`
  drives a real browser (on both platforms).

## Production

- Heroku, buildpacks `heroku/jvm` (the JDK for sbt, `system.properties`) then
  `https://github.com/jamesward/buildpack-scala-native`, which runs `nativeLink` and ships only the binary and `Procfile`.
- `KYO_NET_BACKEND=epoll` in the `Procfile`: Heroku dynos can't create io_uring rings.

## Exceptions

- **No sbt-native-packager:** a Scala Native server is a single binary that `buildpack-scala-native` ships, and `nativeLink`
  stands in for `stage`.
- **kyo-ffi-plugin's tasks reimplemented:** Kyo's documented Native setup uses kyo-ffi-plugin's
  `ffiNativeDependencyLinkingOptions` / `ffiNativeDependencyCompileOptions`, but the plugin is published for sbt 1 only.
  `project/NativeLinking.scala` (shared with `jamesward/aws-emul-mcp`) defines the same two tasks for sbt 2. Switch to the
  plugin once it is published for sbt 2.
- **`.jvmopts` sets `-Xmx3g`:** Scala Native's linker can run out of the default 1 GB heap.
