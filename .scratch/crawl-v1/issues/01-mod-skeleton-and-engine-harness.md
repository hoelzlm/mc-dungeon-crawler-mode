# 01: Mod skeleton and Crawl engine harness

**What to build:** A NeoForge mod loads in the game. A Minecraft-free Crawl engine runs with an injected fake clock, takes ticks and events and returns effects, with a thin adapter between it and the game. This is the single test seam.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Mod loads in a dev client and server without errors
- [ ] Engine can be driven in plain unit tests with a fake clock
- [ ] One smoke test passes through the engine interface (event in, effect out)
- [ ] Latest stable NeoForge and Minecraft versions are verified against official docs
