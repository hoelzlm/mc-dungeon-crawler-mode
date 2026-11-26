# 01: Corner minimap shows what I've explored

**What to build:** A Crawler on a Floor sees a **Floor Map** minimap in the top-right of the screen that starts empty and fills in as they walk. The engine keeps a Floor Map per **Crawler** and reveals every cell (one block column) within the reveal radius (default 24, configurable), with no line of sight. The server owns and saves the map and sends the client only the changes to draw. The minimap is part of the game interface, not an item: north up, centred on the Crawler, 128 px at normal GUI scale, with corner and size configurable, hidden behind open screens such as the inventory. See `.scratch/floor-map/spec.md` and ADR 0007.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Walking reveals cells within the reveal radius and no others
- [ ] The reveal radius, minimap corner and size are configurable
- [ ] The server owns the map and sends only changes; the client only draws
- [ ] The minimap shows top-right, north up and centred, and hides behind open screens
- [ ] The map is the Crawler's own; another Crawler cannot see it
- [ ] Engine tests cover revealing through the Crawl engine seam; `./gradlew build` passes
