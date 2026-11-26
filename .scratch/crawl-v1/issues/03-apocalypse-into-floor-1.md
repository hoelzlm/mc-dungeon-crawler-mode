# 03: Apocalypse into Floor 1

**What to build:** After a configurable delay (default in-game day 3), with **Host** warnings at the start and shortly before, the **Apocalypse** happens: the vanilla world is sealed and the **Crawler** is pulled into an empty Floor 1 dimension keeping their inventory. A command triggers it immediately.

**Blocked by:** 01, 02

**Status:** in-review

- [x] Delay is configurable and defaults to day 3 (engine config verified by test; adapter reads -Dcrawlmode.apocalypseDelayDays, untested in game)
- [x] Host warns at the start and shortly before (engine-level; lines in lines.txt)
- [x] Command triggers the Apocalypse at once (engine verified; /crawl apocalypse compiled, not run in game)
- [ ] Inventory is kept and the vanilla world can no longer be reached
- [x] Engine scenarios cover the delay and the command
