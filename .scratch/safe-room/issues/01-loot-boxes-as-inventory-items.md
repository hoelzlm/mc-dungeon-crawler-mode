# 01: Loot Boxes as inventory items

**What to build:** Loot Boxes become four stackable tier items (Copper, Silver, Gold, Radiant) in a Crawler's inventory. Every grant source (Achievements, Boss kills, milestones) gives an unopened box instead of spilling its contents, and the Host grant line tells the Crawler a box is in their inventory. Right-clicking a box opens exactly one with the existing animation and Host line and hands out the contents from the per-tier loot tables. For now opening works anywhere; ticket 05 restricts it to the Safe Room.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] Four distinct tier items exist and stack by tier
- [x] Every existing grant path yields an unopened box item
- [x] Right-click opens one box, consumes it, plays the animation and Host line, and grants the table contents
- [x] The grant Host line says the box is in the inventory
- [x] Engine tests cover grant and open through the Crawl engine seam
