# 04: Safe Room Doors appear on the map

**What to build:** Each **Safe Room Door** shows on the **Floor Map** as a large, distinct icon once its cell is revealed, on both the minimap and the full-screen map. A door is never shown before its cell is revealed.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A door in a revealed cell shows as a large icon on both maps
- [ ] A door in an unrevealed cell is not shown, and nothing about it is sent to the client
- [ ] Engine tests cover door visibility through the Crawl engine seam
