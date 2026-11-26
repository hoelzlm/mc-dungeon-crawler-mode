# 10: Bosses, area gating and Stairwell reveal

**What to build:** Each **Faction** has one optional **Boss** at a random spot in its territory. Killing it opens a gate to the next, larger area and grants a high-tier loot reward hook. At least one **Boss** per **Floor** can reveal a **Stairwell**.

**Blocked by:** 09

**Status:** done

**Note:** Engine logic is unit-tested; the in-game adapter compiles but was not run in a live game.

- [x] Boss appears at a random spot in its Faction's area
- [x] Kill opens the gate to the next area
- [x] Some Bosses reveal a Stairwell, most do not
- [x] Bosses are optional: a Stairwell is reachable without killing one
- [x] Boss placement is random per generation
