# Coding standards

Read this during review. It holds the judgement calls; machines hold the mechanical rules (see Checks).

## Standards

- **The engine decides, adapters translate.** A rule, number, threshold or branch on game state lives in `src/engine` and is tested there (ADR 0002). An adapter converts a game callback into an engine `Event` and an engine `Effect` into world changes, and nothing more. When an adapter grows an `if` about the Crawl, move it into the engine.
- **Adapters meet through the hub.** `CrawlAdapter` wires the others; a sibling adapter calls another only through the allowlist in `ArchitectureTest`. Reach for a new engine `Event` or `Effect` before adding an edge.
- **One source of truth.** A constant, a resource path, a tag prefix, the mod id and the Host prefix (`[The Host] `) are each declared once and referenced everywhere else (`CrawlMode.MOD_ID`, `HostChat`). A second copy is a bug even when the values match today.
- **Per-floor data over hard-coded floors.** Floor-specific behaviour is an entry in `FloorSpec` plus data files (ADR 0004). A `switch` or `if` on a floor number in code means the data model is missing a field.
- **Content is data.** New mobs, loot, Achievements, Host lines and templates are text-file changes validated on load with a line number; code changes only when the format itself changes.
- **Names come from `GLOSSARY.md`.** Use **Crawler**, **Floor**, **Stairwell** and the rest exactly, in types, methods and tests; never an `_Avoid_` term. A concept without a glossary entry gets one in the same change.
- **Model with the type system.** Use records for values, sealed interfaces with pattern-matching `switch` for closed sets such as `Event` and `Effect` (no `default` branch that hides a new case), immutable state by default, and constructor validation so a bad value cannot exist. Prefer flexible constructor bodies (Java 25) to static factory workarounds when arguments need checking before `super(...)` or `this(...)`.
- **Absence is explicit.** Return `Optional` from a query that may find nothing; never take it as a parameter or store it in a field. Elsewhere state non-null as the contract and fail early on a violation; return empty collections, not `null`.
- **State has an owner.** Mutable state lives in an object the engine or an adapter instance owns, reset on server start and stop. A new `static` mutable field in an adapter is a smell to justify in an ADR, since two servers in one JVM (integrated server plus a test) share it.
- **Server logic stays on the server.** Gameplay decisions run only where `isClientSide()` is false and in server-only classes; client code (HUD, screens) is registered in a `dist`-guarded class, and anything the client needs travels as a packet or an existing vanilla mechanism.
- **Register in the right place.** Registry content goes through `DeferredRegister` attached to the mod bus in the mod constructor; runtime game events go on `NeoForge.EVENT_BUS`; lifecycle events on the mod bus. Do not query a registry during registration.
- **Prefer data-driven Minecraft content.** Dimensions, loot, tags and similar belong in datapack JSON under `data/crawlmode/` over code that builds them.
- **Tests state behaviour.** One engine seam, injected `Clock` and randomness, no sleeping, no Minecraft in unit tests. Name a test for the behaviour (`lateArrivalGetsLessTime`), assert one outcome, and build fixtures inline so the test reads on its own. A bug fix starts with a failing test.
- **Logging goes through the logger.** `LogUtils.getLogger()`; no `System.out` or `printStackTrace`.

## Checks

Run `./gradlew check` (also part of `./gradlew build` and the pre-commit hook). The mechanical rules are enforced there.

- No comments in Java or `*.gradle` files: Checkstyle `TodoComment` plus `checkGradleComments` (ADR 0003). Reasons that must last go into an ADR.
- Unused imports: Checkstyle `UnusedImports`. Config in `config/checkstyle/checkstyle.xml`.
- Engine stays Minecraft-free, adapters keep to the allowlist, no console output: `ArchitectureTest` (ArchUnit).
- No string constant declared twice, mod id and Host prefix defined once: `ConstantsTest`.
- Smoke run: `./gradlew smokeTest` boots the dedicated server, runs `crawl apocalypse`, and checks `build/smoke/server.log`. It needs the EULA accepted once in `run/eula.txt`.
- Enable the hook once per clone: `git config core.hooksPath .githooks`.
- Gradle itself needs any JDK 17 or newer on `PATH` or `JAVA_HOME`; it provisions Java 25 through the foojay toolchain resolver. Never export `JAVA_HOME` to a JDK 25 by hand.

## Sources

- JEP 513, Flexible Constructor Bodies (final in Java 25): https://openjdk.org/jeps/513
- Java language updates, pattern matching for switch and record patterns: https://docs.oracle.com/en/java/javase/21/language/pattern-matching.html
- `Optional` API note, intended for return types: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html
- NeoForge docs, sides (logical sides, static state, server separation): https://docs.neoforged.net/docs/concepts/sides
- NeoForge docs, events (mod bus versus game bus): https://docs.neoforged.net/docs/concepts/events
- NeoForge docs, registries (`DeferredRegister`, datapack registries): https://docs.neoforged.net/docs/concepts/registries
- JUnit 5 user guide, writing tests: https://docs.junit.org/current/user-guide/
