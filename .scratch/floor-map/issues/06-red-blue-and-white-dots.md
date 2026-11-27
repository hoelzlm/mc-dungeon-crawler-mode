# 06: Red, blue and white dots

**What to build:** The **Floor Map** shows monsters as red dots, other **Crawlers** as blue dots and **NPCs** as white dots. An entity gets a dot only if it is within the dot radius (default 24, configurable) of the viewing **Crawler** and stands in a revealed cell. The server filters and sends only qualifying dots, never positions of hidden entities. **NPCs** are not yet designed, so only the rule must accept them.

**Blocked by:** 01

**Status:** in-review

- [x] A monster in range and in a revealed cell shows red
- [x] A monster in range but in an unrevealed cell shows nothing
- [x] A monster out of range shows nothing
- [x] Other Crawlers follow the same rules and show blue; the rule accepts NPCs as white
- [x] The server never sends positions of entities that do not qualify
- [x] The dot radius is configurable
- [x] No X markers, loot markers or waypoints exist
- [x] Engine tests cover the dot rules through the Crawl engine seam

**Note (review):** Dots go out as the full qualifying list, not per-dot deltas. This is deliberate: the engine scans every 5 ticks and sends nothing when the list is unchanged (`FloorMap.showDots` dedupes), the list is capped by the dot radius, and a full list lets vanished dots clear without extra effect types. Dots are not replayed after leaving the Safe Room or rejoining; the next scan sends fresh ones.
