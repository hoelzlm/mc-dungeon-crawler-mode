# 01: Mod skeleton and Crawl engine harness

**What to build:** A NeoForge mod loads in the game. A Minecraft-free Crawl engine runs with an injected fake clock, takes ticks and events and returns effects, with a thin adapter between it and the game. This is the single test seam.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Mod loads in a dev client and server without errors (dedicated server: mod discovered and loaded by FML, no errors in mod loading; full server start and runClient not verified, see report)
- [x] Engine can be driven in plain unit tests with a fake clock
- [x] One smoke test passes through the engine interface (event in, effect out)
- [x] Latest stable NeoForge and Minecraft versions are verified against official docs (MC 26.3, NeoForge 26.3.0.118, Java 25; see gradle.properties)
