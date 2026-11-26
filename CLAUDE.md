## Agent skills

### Issue tracker

Issues and specs are local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.

## Standards and checks

Read `CODING_STANDARDS.md` before reviewing or finishing a change. `./gradlew build` (and `check`) enforce it; Gradle runs on any JDK 17+ and provisions Java 25 itself, so never export `JAVA_HOME` by hand. `./gradlew smokeTest` boots the server and writes `build/smoke/server.log`; the user must accept the Mojang EULA once (`run/eula.txt`, `eula=true`), agents never write it.
