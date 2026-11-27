# 04: Safe Room Doors appear on the map

**What to build:** Each **Safe Room Door** shows on the **Floor Map** as a large, distinct icon once its cell is revealed, on both the minimap and the full-screen map. A door is never shown before its cell is revealed.

**Blocked by:** 01

**Status:** in-review

- [ ] A door in a revealed cell shows as a large icon on both maps (minimap drawn, needs live playtest; full-screen map arrives with ticket 05)
- [x] A door in an unrevealed cell is not shown, and nothing about it is sent to the client
- [x] Engine tests cover door visibility through the Crawl engine seam
