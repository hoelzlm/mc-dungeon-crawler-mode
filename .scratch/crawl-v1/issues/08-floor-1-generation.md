# 08: Floor 1 generation

**What to build:** Floor 1 is a huge dimension of connected areas built from hand-made room and corridor templates, generated in chunks as **Crawlers** approach. Structure is unbreakable; players can place and break their own blocks. 5 to 6 hidden, unguarded, reusable **Stairwells** are placed, at least one in the starting area.

**Blocked by:** 03, 05

**Status:** ready-for-agent

- [ ] Floor generates progressively without long hitches
- [ ] About five connected areas with templates in a marble-hall mood
- [ ] Structure blocks cannot be broken, player blocks can
- [x] 5 to 6 hidden Stairwells, at least one in the start area (layout level, unit-tested; in-game not verified)
- [ ] Stairwells use the sealed/Descent Window behaviour from ticket 05

## Implementation notes (not yet verified in-game)

- Pure layout in `src/engine/.../floorgen` (`FloorLayout`, `FloorTemplate`, `GenerationPlanner`); templates in `data/crawlmode/floor/floor1_templates.txt`. Unit-tested: 5 areas of 9 rooms, all connected, deterministic by seed, 5-6 stairwells (>=1 in start area, never on spawn cell), each hidden behind an unprotected secret wall block, spawn is open air, protection rule, generation budget and ordering.
- Adapter `FloorOneAdapter` writes blocks (2 cells per tick, radius 3 cells), cancels `BreakBlockEvent` and explosions on protected positions, and turns a right-click on a stairwell marker (end portal frame) into `StairwellUsed` (sealed/Descent Window behaviour is ticket 05's). It compiles but was never run in a game. The unticked boxes need a manual play check: hitching, marble mood, break/place behaviour, stairwell click.
