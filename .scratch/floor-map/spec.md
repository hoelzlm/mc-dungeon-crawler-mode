# Floor Map

Status: ready-for-agent

## Problem Statement

A Crawler on a Floor of generated rooms and corridors has no way to remember where they have been or to tell where the Safe Room Doors are. They get lost, they walk the same corridor twice, and a Safe Room Door that was supposed to be "fairly visible" is only visible if they happen to stumble on it. The book gives its Crawlers a map that fills in as they explore, with monsters and other Crawlers marked on it; this game has no equivalent. Third-party minimap mods cannot provide one that follows the Crawl's rules (ADR 0007).

## Solution

Each Crawler gets a **Floor Map**, built into the game's interface rather than carried as an item. A small corner minimap sits in the top-right of the screen while they are on a Floor, and holding a key (default `M`) draws the same data over the whole screen. The map starts empty on every Floor and fills in as the Crawler walks. Safe Room Doors show as a large icon once their cell is revealed, and killing a Boss reveals the whole area it died in. Monsters appear as red dots, NPCs as white dots and other Crawlers as blue dots, but only inside revealed cells and within range. Stairwells are never shown directly, only as a rough area painted by a Stairwell Aid. The map is the Crawler's own, is hidden inside the Safe Room and disappears entirely for an eliminated Crawler.

## User Stories

1. As a Crawler, I want a minimap in the top-right corner of my screen on a Floor, so that I always know where I am without opening anything
2. As a Crawler, I want the map to be part of the game interface and not an item, so that I cannot lose it, drop it or forget to hold it
3. As a Crawler, I want the map to start empty on every Floor, so that a new Floor feels unexplored
4. As a Crawler, I want cells to be revealed as I walk, so that the map is a record of what I have explored
5. As a Crawler, I want a cell to be revealed within 24 blocks of me, so that I reveal rooms as I enter them and not only the block I stand on
6. As a Crawler, I want the map to be north up and centred on me, so that it reads like a normal minimap
7. As a Crawler, I want to hold `M` to draw the map over my whole screen, so that I can plan a route and let go to carry on
8. As a Crawler, I want the full-screen map to show the same data as the minimap, so that I do not have to learn two maps
9. As a Crawler, I want to rebind the full-screen key in the Controls menu, so that it fits my layout
10. As a Crawler, I want the minimap hidden behind open screens such as my inventory, so that it does not clutter them
11. As a player, I want to configure the minimap's corner and size, so that it does not cover something I need
12. As a Crawler, I want a Safe Room Door to show as a large icon once I have revealed its cell, so that I can find the nearest escape
13. As a Crawler, I want a Safe Room Door never shown before its cell is revealed, so that finding one still rewards exploring
14. As a Crawler, I want Safe Room Doors visible on both the minimap and the full-screen map, so that I see them at a glance
15. As a Crawler, I want killing a Boss to reveal the whole area it died in, so that its Safe Room Doors appear and the reward for the fight is visible
16. As a Crawler, I want a Boss kill to reveal at least a 64-block disc around the Boss, so that a small area still gives a worthwhile reveal
17. As a Crawler, I want part of a revealed area that has not generated yet to be revealed once it does, so that a Boss kill never leaves a hole
18. As a Crawler, I want monsters near me to show as red dots, so that I can see danger coming
19. As a Crawler, I want a monster to show only if it is within 24 blocks of me and in a revealed cell, so that unexplored corridors never leak information
20. As a Crawler, I want other Crawlers to show as blue dots under the same rules, so that I can find allies or avoid rivals without a radar that sees everything
21. As a Crawler, I want NPCs to show as white dots under the same rules once NPCs exist, so that traders are easy to find
22. As a Crawler, I want the dot radius and the reveal radius to be configurable, so that we can tune them in playtesting
23. As a Crawler, I want Stairwells never shown on the map directly, so that finding them stays a hunt
24. As a Crawler, I want a Stairwell Aid to paint a rough area on my map that contains a Stairwell, so that an Aid actually helps me
25. As a Crawler, I want the area an Aid paints to never be an exact spot, so that the glossary rule of "without giving its exact position" holds
26. As a Crawler, I want each of the four Aids to paint a different size or accuracy of area, so that they feel different to use
27. As a Crawler, I want my map to be only mine, so that other players cannot read it
28. As a Crawler, I want my map to survive rejoining the server, so that a disconnect does not cost me my exploration
29. As a Crawler, I want my map hidden inside the Safe Room, so that the small private room is not covered by an irrelevant map
30. As a Crawler, I want my Floor's map to be exactly as I left it when I leave the Safe Room, so that a trip to rest costs me nothing
31. As a Crawler, I want a fresh map when I descend to a new Floor, so that each Floor is its own exploration
32. As a Crawler, I want my map to be deleted when its Floor collapses, so that nothing stale is left behind
33. As a Crawler, I want a fresh Crawl to start with no maps, so that nothing carries over from the last one
34. As an eliminated Crawler, I want the map overlay gone entirely, so that I do not see a map while I spectate
35. As a Crawler, I want the Floor Timer to keep running while the full-screen map is held, so that looking at the map is never a way to pause the Crawl
36. As a competitive Crawler, I want the server to decide what my map shows and to send only that, so that a modified client cannot read hidden monsters
37. As a host, I want the map traffic to be small, so that many Crawlers on a Floor do not strain the server
38. As a host running the mod through a modpack, I want the map to need nothing beyond the mod, so that installing it stays easy
39. As the Host (the game's narrator), I want to stay out of the map for v1, so that it is not another thing to explain
40. As a future team player, I want the map's data to be per Crawler and held by the server, so that sharing it with a team later is a merge on the server

## Implementation Decisions

- **Engine owns the rules.** Following ADR 0002 and the coding standards, the Floor Map's rules live in the engine, with no Minecraft types: which cells a position reveals, what a Boss kill reveals, which Safe Room Doors are visible, which entities qualify for a dot, how an Aid paints an area, and when a map is created, kept or deleted. Adapters translate game callbacks into engine events and engine effects into packets and drawing.
- **One map per Crawler per Floor**, owned by the server, saved with the Crawl's state, deleted when its Floor is deleted and on a fresh Crawl. Rejoining keeps the current Floor's map. Descending creates a new one. The Safe Room neither creates nor deletes a map, it only hides the overlay.
- **Cells.** One cell is one block column. Walking reveals every cell within the reveal radius (default 24, configurable) of the Crawler, with no line of sight in v1. A Floor has no fixed size, as it generates in chunks near Crawlers, so the map is a sparse set of revealed cells rather than a fixed grid.
- **Safe Room Doors** are shown only when their cell is revealed, as a large icon, on both the minimap and the full-screen map.
- **Boss reveal.** Killing a Boss reveals the whole area it died in, and at least a disc around the Boss with a configurable radius (default 64). Parts of the area that generate later are revealed when they generate. Doors in the revealed region appear.
- **Dots.** Monsters (red), NPCs (white) and other Crawlers (blue) are shown only if they are within the dot radius (default 24, configurable) of the viewing Crawler and stand in a revealed cell. NPCs are not yet designed, so v1 only needs the rule to accept them. No X markers for dead monsters, no loot markers and no waypoints.
- **Stairwells** are never marked directly. A Stairwell Aid paints a rough area on the viewing Crawler's map that contains a Stairwell, never an exact position. The four Aids (Seeker's Draught, Echo Chime, Revealing Lantern, Fragment Map) differ in the size or accuracy of the area, and the numbers are tuned in playtesting. The Stairwell Aid glossary rule is kept.
- **Server filters, client draws.** The server sends only the revealed-cell changes and the qualifying dots, and never positions of hidden entities. The client holds a copy purely to draw. Dots are sent as changes, at a modest rate, not as a full list each tick.
- **Client.** A HUD layer draws the corner minimap (top-right by default, 128 px at normal GUI scale, north up, centred on the Crawler, with corner and size configurable). A hold-to-show keybind (default `M`, rebindable in Controls) draws the same data full-screen. The minimap hides behind open screens. The overlay is hidden inside the Safe Room and for an eliminated Crawler.
- **Floor Timer** is unaffected. Holding the map never pauses it.
- **Config.** Reveal radius, dot radius, Boss reveal minimum radius, minimap corner and size are configuration values.
- **Glossary and ADR.** `GLOSSARY.md` already has Floor Map and NPC, and ADR 0007 records building our own. The Stairwell Aid glossary entry gets a line saying an Aid paints an area on the Floor Map.
- **Single-player first.** The map is per Crawler and nothing is shared. Team sharing is later, and holding the data on the server keeps it a server-side merge.

## Testing Decisions

- A good test states behaviour through the engine seam (`CrawlEngine`) with an injected clock and randomness, no sleeping and no Minecraft, and asserts one outcome per test, named for the behaviour (coding standards).
- **One seam: the Crawl engine.** The Floor Map's rules are tested by sending events (a Crawler moved, a Boss died, an Aid was used, a Crawler entered the Safe Room, was eliminated, rejoined, descended, the Floor collapsed) and asserting the engine's effects and the map's state. Examples: walking reveals cells in the radius and no others; a Safe Room Door is not visible before its cell is revealed; a Boss kill reveals its whole area; a monster in range but in an unrevealed cell gets no dot; an Aid's painted area contains a Stairwell and is never an exact cell; a Floor's map is deleted with its Floor.
- Prior art: `StairwellAidsTest`, `BossEncounterTest`, `SafeRoomTest` and `EliminationTest` in `src/test/java/dev/crawlmode/engine`.
- The HUD drawing, the keybind and the packets are adapters and carry no Crawl rules, so they are covered by `ArchitectureTest` (adapters stay inside the allowlist) and by `./gradlew smokeTest` booting the server, plus a live playtest of the overlay.

## Out of Scope

- Team sharing of maps.
- NPCs themselves. Only the rule that an NPC gets a white dot is in scope.
- X markers for lootable corpses, which wait for corpse looting to be designed.
- Loot markers.
- Waypoints.
- Line of sight for reveal.
- Entity radar beyond the dot rules.
- A modpack. It remains fine as an installer wrapper, but it is not part of this feature.
- Final numbers for Aid areas, radii and map size. They are tuned in playtesting.

## Further Notes

- Floor sizes and balance are provisional (`.scratch/crawl-v1/spec.md`), which is why the reveal numbers are configuration and the Boss reveal scales with the area instead of using a Floor-wide size.
- The map must work in custom dimensions. Floors are their own dimensions (the Safe Room is created at runtime, ADR 0005), so drawing samples the client's loaded level and needs no dimension-specific support.
- Cost to remember: we maintain our own renderer across Minecraft 26.x changes (ADR 0007).
