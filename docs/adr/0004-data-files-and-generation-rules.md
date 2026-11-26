# Data files define the Crawl's content; the engine decides, the adapter applies

Content is data under `src/main/resources/data/crawlmode/`; adding a Floor, Faction, Boss, Achievement, loot entry or Host line is a data change plus, for a Floor, one entry in `FloorSpec.ALL`. Every file ignores blank lines and `#` lines and is validated on load; a malformed line fails loading with its line number. This ADR holds what the source comments used to say.

## Formats

- Host lines (`host/lines.txt`): `topic: text`; several entries per topic mean one is picked at random. The `HostLineSource` seam lets an LLM-backed source replace the files later.
- Achievements (`achievement/achievements.txt`): `id | trigger | tier | host line`. Triggers are free-form strings such as `kill.grunt`, `kill.boss`, `death.fall`, `kind.<count>` milestones.
- Loot (`loot/tables.txt`): `<tier> rolls N`, `<tier> gear <item> weight=W enchant=ns:name:level`, `<tier> food <item> weight=W count=N`, `<tier> aid <aid> weight=W`. Every tier needs rolls of at least 1 and entries, weights are positive, aids are real Stairwell Aids, rolls never decrease from one tier to the next. Items are namespaced ids; aids use the bare name.
- Factions (`faction/factions.txt`, then `floor<N>_factions.txt`): `faction <id> floor=N name="..."` and `mob <faction> <grunt|elite|shaman> entity=... name="..." health=H damage=D weight=W [mainhand=...] [ability=...]`. Every Faction has a Grunt, Elite and Shaman; spawn weights fall strictly Grunt, Elite, Shaman; a Shaman has at least one known ability; every Floor has three or more Factions. Values may be double-quoted and then contain spaces.
- Bosses (`faction/bosses.txt`, then `floor<N>_bosses.txt`): `boss <faction> entity=... name="..." health=H damage=D tier=gold|radiant reveals=true|false [head=...]`. Exactly one Boss per Faction; on every Floor at least one Boss reveals a Stairwell and fewer than half do.
- Templates (`floor/floor<N>_templates.txt`): templates separated by `---`. Header lines `name`, `role room|corridor|village|stairwell_room`, `height`, `palette <char> <block id>`, then `layer N` or `layer A-B` (one grid shared by several layers) followed by 16 rows of 16 characters (row is z, column is x). `.` is air; `n e s w` are gates, air when connected on that side and wall otherwise; `k` is a secret wall block that looks like `#` but is not protected; `X` is the Stairwell marker; anything else comes from the palette. Unlisted layers are air.

## Generation rules

- A Floor is a grid of 16 by 16 block cells, planned purely from a seed. Floor 1 uses the world seed; each later Floor adds the 64-bit golden-ratio stride so layouts differ. The same seed always yields the same Floors, so they survive restarts.
- Room-and-corridor Floors: area 0 is the start area (3 by 3 rooms around cell 0,0, safe, no Faction); other areas sit 7 cells apart on a lattice and join an earlier area by a straight corridor of four cells. Rooms in an area form a random spanning tree plus two extra doors. One Stairwell room is in the start area but never the arrival cell; the rest are anywhere else.
- Village-and-ruins Floors (Floor 3): a start village of nine fully open cells and three ruin areas, each a ring of eight ruined rooms around a hamlet, joined by four-cell paths. Villages never get mobs. Faction areas, Boss gates and Stairwell reveals work unchanged because the lattice is the same. Stairwells are one in the village (not the arrival cell) and two in ruins, never in a hamlet. Shops are scenery: nothing a Crawler can open, trade at or loot, and no spawner or command block.
- Every non-start area belongs to exactly one Faction, dealt in turn so each Faction holds at least one. Bosses stand in an ordinary room of their own Faction's area, chosen from the seed. A gate into an area opens when the Boss of the Faction holding its parent area dies, and the plan is always solvable: the shallowest area of each Faction depends only on shallower ones, so no Boss is walled in behind its own gate. Mobs and Bosses may be placed at x and z 2..13 of a cell and the adapter nudges them up to two blocks, so never wall them in.
- Structure blocks cannot be broken or blown up; secret wall blocks, air and anything a Crawler placed outside the structure can. Closed Boss gates are likewise unbreakable.
- Floor Timer: every Floor runs the same configured length; Floor 1's timer starts at the Apocalypse and counts only while a Crawler is online. A collapse is final in every Elimination mode, so Respawn never returns a victim to a collapsed Floor.

## Stairwell Aids

All maths lives in `StairwellAids`, none yields an exact position: the Draught gives one of eight compass points, the Chime one of four distance bands, the Map a large square that merely contains a Stairwell (never near the edge), the Lantern only uncovers secret wall blocks nearby for 30 seconds. Every aid is spent on use and works only on a generated Floor. North is negative z, east is positive x.

## Adapter facts

- Settings come from system properties: `-Dcrawlmode.apocalypseDelayDays=N` (default 3), `-Dcrawlmode.floorTimerMinutes=N` (default 90), `-Dcrawlmode.elimination=permanent|respawn` (single-player is detected).
- Mobs carry Faction, rank and ability as entity tags, so nothing is remembered across restarts.
- Generation tuning: cells are built within 3 cells (48 blocks) of a Crawler, two per tick, each about two thousand block writes; a cell whose lowest corner is air was never built.
- `EliminationBridge`, `AchievementAdapter` and `LootBoxAdapter` stay apart from `CrawlAdapter` so parallel branches merge easily; no rules live in any adapter.
