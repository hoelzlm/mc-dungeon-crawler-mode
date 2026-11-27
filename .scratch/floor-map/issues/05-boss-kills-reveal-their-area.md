# 05: Boss kills reveal their area

**What to build:** Killing a **Boss** reveals on the killer's **Floor Map** the whole area the **Boss** died in, with a configurable minimum disc around the **Boss** (default 64 blocks). Parts of the area that generate later are revealed once they do. **Safe Room Doors** inside the revealed region appear.

**Blocked by:** 01, 04

**Status:** in-review

- [x] A Boss kill reveals the whole area it died in
- [x] At least a disc of the configured radius around the Boss is revealed (default 64)
- [ ] Parts of the area not yet generated are revealed when they generate
- [x] Safe Room Doors in the revealed region appear on the map
- [x] Engine tests cover the reveal through the Crawl engine seam
