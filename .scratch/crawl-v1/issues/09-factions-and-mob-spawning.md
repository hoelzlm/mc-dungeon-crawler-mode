# 09: Factions and mob spawning

**What to build:** **Factions** are defined in data: **Grunt**, **Elite** and **Shaman** as vanilla mobs with custom names, stats and gear, rarer with each rank, **Shamans** casting support or magic abilities. Floor 1's three **Factions** spawn in their own areas.

**Blocked by:** 08

**Status:** ready-for-agent

- [x] Faction and rank definitions load from data and are validated
- [x] Spawn rarity is Grunt > Elite > Shaman
- [ ] Shamans use at least one support or magic ability
- [x] Three Floor 1 Factions occupy separate areas
- [ ] Custom names, stats and gear are visible in game

Note: engine logic is unit-tested; Shaman abilities and visible names/gear are implemented in the adapter (FactionMobs) but not yet verified in a running game.
