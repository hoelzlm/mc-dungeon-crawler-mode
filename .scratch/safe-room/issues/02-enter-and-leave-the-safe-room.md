# 02: Enter and leave the Safe Room

**What to build:** A Crawler uses a Safe Room Door and is instantly taken into their own private Safe Room dimension, created on first entry and kept for the whole Crawl (ADR 0005). Leaving is instant and returns them to the door they entered from. The engine remembers the origin Floor and door per Crawler. One placeholder door on Floor 1 is enough here; ticket 04 places the real doors. The room itself can be a bare floor.

**Blocked by:** None (can start immediately)

**Status:** in-review

- [x] First entry creates a dedicated dimension for that Crawler; later entries reuse it
- [x] Entry and exit are instant, with no channel time and no cooldown
- [x] Exit returns the Crawler to the origin door, tracked per Crawler in the engine
- [x] A Crawler cannot enter or see another Crawler's room
- [x] Engine tests cover entry, exit and origin tracking through the Crawl engine seam
