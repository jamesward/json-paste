# AGENTS.md: json-paste

JSON Paste: a Kyo server-push web app (`jsonpaste.WebApp`) that pretty-prints pasted JSON live.

## Agent tooling

- Follow the `zen-of-projects` Skill (extract with `./sbta extractSkillsJars` into the gitignored `.kiro/skills/`); this file records only project-specific facts and exceptions.
- For Scala 3 and ZIO code (writing or reviewing it), follow `zen-of-scala`. For design, domain modeling, effects and testing strategy, follow `zen-of-james`. Read the relevant Skills in `.kiro/skills/` before working.
- MCP server `sbta` (this build's `./sbta --mcp`, configured in `.mcp.json` and `.kiro/settings/mcp.json`, approved in `.claude/settings.json`): use it for all sbt work: `compile`, `test` (`full` for all), `run`, `check` after editing Scala 3 sources, `glob-search` / `inspect` / `symbol-location` for symbols, `sbt-task` for any other command. Its tools are deferred in Claude Code: load them with ToolSearch (search `sbta`). If it's unavailable, say so and use `./sbta <command>`. Logs: `project/target/sbta/mcp.log`.
- Maintenance routine: `.factory/MAINTENANCE.md` (weekly), following the `zen-of-projects` Skill.

## Build and validation

- Full validation: `./sbta shutdown`, `./sbta extractSkillsJars`, then `./sbta "Test / compile; testFull; stage"`.
- Dev run: `run` through the `sbta` MCP server (or `./sbta run`). No tests need Docker or paid services; `BrowserIntegrationTest` drives a real browser.
