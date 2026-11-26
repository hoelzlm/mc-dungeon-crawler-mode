# 07: Safe Room lifecycle

**What to build:** A late-joining Crawler gets a fresh, empty Safe Room on first entry. An Eliminated Crawler's room is deleted with them and nobody can loot it. A Crawler who wins the Crawl has the room's contents delivered to their inventory before the room is deleted.

**Blocked by:** 02, 03

**Status:** ready-for-agent

- [x] A late joiner's first entry creates an empty room
- [x] Elimination deletes that Crawler's room and its contents
- [x] Winning hands every stored item to the Crawler, then deletes the room
- [x] No Crawler can reach another Crawler's room at any point
- [x] Engine tests cover late join, Elimination and winning
