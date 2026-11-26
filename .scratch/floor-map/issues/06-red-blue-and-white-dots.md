# 06: Red, blue and white dots

**What to build:** The **Floor Map** shows monsters as red dots, other **Crawlers** as blue dots and **NPCs** as white dots. An entity gets a dot only if it is within the dot radius (default 24, configurable) of the viewing **Crawler** and stands in a revealed cell. The server filters and sends only qualifying dots, never positions of hidden entities. **NPCs** are not yet designed, so only the rule must accept them.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A monster in range and in a revealed cell shows red
- [ ] A monster in range but in an unrevealed cell shows nothing
- [ ] A monster out of range shows nothing
- [ ] Other Crawlers follow the same rules and show blue; the rule accepts NPCs as white
- [ ] The server never sends positions of entities that do not qualify
- [ ] The dot radius is configurable
- [ ] No X markers, loot markers or waypoints exist
- [ ] Engine tests cover the dot rules through the Crawl engine seam
