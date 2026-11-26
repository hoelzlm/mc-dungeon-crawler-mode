Status: ready-for-agent

# Spec: Crawl v1 (single-player first, multiplayer-ready)

## Problem Statement

I want to play a deadly, televised-feeling dungeon descent inside Minecraft that feels like the book *Dungeon Crawler Carl*. Vanilla survival has no forced descent, no time pressure, no snarky announcer and no absurd achievements. I want to be dragged into a **Crawl** with the stuff I already own, fight tiered **Factions**, find hidden **Stairwells** and race a **Floor Timer**, all narrated by a ridiculous **System**. The mod uses original names and text and may be published someday.

## Solution

A NeoForge mod. After a configurable delay (default in-game day 3) the **Apocalypse** seals the vanilla world and pulls every **Crawler** into **Floor** 1, keeping their inventory. Three **Floors** follow, each its own generated dimension: marble halls, an industrial maze and scattered villages ringed by ruins. Each **Floor** has three **Factions** (**Grunt**, **Elite**, **Shaman**, **Boss**) made of vanilla mobs with custom names, stats and gear. A **Boss** kill opens a gate to a larger area and may reveal a **Stairwell**. A **Floor Timer** runs on screen. **Stairwells** are hidden, visibly sealed and usable only in the final ten minutes (**Descent Window**). **The Host** (the **System**) talks constantly through banners and chat, awards about 30 **Achievements**, and hands out four tiers of **Loot Boxes**. Passing the last **Stairwell** wins the **Crawl** and returns the **Crawler** to a restored vanilla world. **Elimination** ends the **Crawl** for that **Crawler**.

## User Stories

1. As a Crawler, I want the Apocalypse to trigger after a configurable delay, so that I can prepare and arrive with what I own
2. As a Crawler, I want The Host to warn me with a countdown before the Apocalypse, so that the event feels dramatic and not random
3. As a server owner, I want to trigger the Apocalypse by command, so that I can test it immediately
4. As a Crawler, I want to keep my inventory when pulled in, so that my preparation matters
5. As a Crawler, I want the vanilla world sealed once the Apocalypse happens, so that there is no going back
6. As a Crawler, I want Floor 1 to be huge and made of connected areas, so that exploring feels like a real dungeon
7. As a Crawler, I want each area to belong to one Faction, so that the dungeon has identity
8. As a Crawler, I want Floors generated in chunks as I approach, so that the game stays smooth
9. As a Crawler, I want each Floor to look different (marble halls, industrial maze, scattered villages and ruins), so that descending feels like progress
10. As a Crawler, I want each Faction to have Grunts, Elites and Shamans, with Grunts the most common and Shamans the rarest, so that fights escalate naturally
11. As a Crawler, I want Shamans to cast support or magic abilities, so that they change how I fight
12. As a Crawler, I want each Faction to have a Boss placed at a random spot in its territory, so that each Floor plays differently
13. As a Crawler, I want Bosses to be optional, so that I can choose risk against reward
14. As a Crawler, I want a Boss kill to open a gate to a larger area, so that fighting has a clear payoff
15. As a Crawler, I want some Bosses to reveal a Stairwell, so that fighting can help me escape
16. As a Crawler, I want multiple hidden Stairwells per Floor (5 to 6 on Floor 1), so that finding one feels achievable but not trivial
17. As a Crawler, I want Stairwells to be unguarded, so that stealth and exploration are valid
18. As a Crawler, I want a visible Floor Timer at the top centre of my screen, so that I always know how long I have
19. As a Crawler, I want the Floor Timer to default to 90 minutes and be configurable, so that it fits my play time
20. As a Crawler, I want Floor 1's timer to start at the Apocalypse and later Floors' timers to start when the previous Floor collapses, so that I get a short breather after descending
21. As a Crawler, I want The Host to announce 10 minutes, 1 minute and 10 seconds left, so that I feel the pressure
22. As a Crawler, I want Stairwells visibly sealed with an "opens in mm:ss" hint until the Descent Window, so that I can camp nearby
23. As a Crawler, I want The Host to announce when the Descent Window opens, so that I know to move
24. As a Crawler, I want to use a Stairwell only in the last ten minutes, so that the timer stays tense
25. As a Crawler, I want the Floor to collapse at zero and kill everyone still on it, so that failing has teeth
26. As a Crawler, I want Crawlers who already went down to survive the collapse, so that planning pays off
27. As a Crawler, I want Elimination to be permanent by default, so that death matters
28. As a server owner, I want elimination handling to be configurable, so that I can pick a softer mode
29. As a single-player, I want a mocking game-over from The Host and the choice of a fresh Crawl or leaving when eliminated, so that failure is funny and I can retry
30. As a Crawler, I want the world restored to its pre-Apocalypse state on a fresh Crawl, so that retrying is clean
31. As a Crawler, I want four Stairwell Aids (Seeker's Draught, Echo Chime, Revealing Lantern, Fragment Map), so that finding stairwells is not pure luck
32. As a Crawler, I want Stairwell Aids to never reveal the exact position, so that exploring stays necessary
33. As a Crawler, I want Loot Boxes in four tiers (Copper, Silver, Gold, Radiant), so that rewards feel graded
34. As a Crawler, I want opening a Loot Box to trigger a Host message and a short animation, so that it feels like a moment
35. As a Crawler, I want Boss kills to give high-tier Loot Boxes, so that bosses are worth fighting
36. As a Crawler, I want Loot Boxes to hold vanilla gear with custom enchants, food and Stairwell Aids by tier, so that rewards are useful
37. As a Crawler, I want about 30 Achievements, many silly and pointless, so that I laugh or shake my head
38. As a Crawler, I want a snarky Host line with every Achievement, so that the tone stays over the top
39. As a Crawler, I want Achievements for first Grunt, Elite, Shaman and Boss kills, reaching a Stairwell, surviving to the Descent Window and dying to a fall, so that core progress is recognised
40. As a Crawler, I want Achievements to reward Loot Boxes, so that silliness has value
41. As a Crawler, I want The Host to speak as a banner with a sound cue and the same lines in chat history, so that I can reread them
42. As a Crawler, I want villages on Floor 3 to have no hostile spawns, so that I can regroup there
43. As a Crawler, I want village shops to be decorative, so that the village feels alive without needing trading yet
44. As a Crawler, I want ruins around villages to hold the Factions, so that danger sits outside the villages
45. As a Crawler, I want to build and break my own blocks while Floor structures stay unbreakable, so that I can play but not wreck the dungeon
46. As a Crawler, I want hunger, crafting and combat to work as in vanilla, so that survival skills carry over
47. As a Crawler, I want a win screen from The Host when I pass the last Stairwell, so that finishing feels rewarding
48. As a Crawler, I want to return to the restored vanilla world with my loot on winning, so that my reward persists
49. As a content author, I want Factions, mob stats, Achievements, Loot Box tables and Host lines defined in data files, so that I can add content without code
50. As a content author, I want Host lines behind an interface, so that an LLM can generate them in a future version
51. As a developer, I want the Crawl logic independent of Minecraft, so that timers and rules are testable quickly
52. As a developer, I want a model that supports several Crawlers even though v1 focuses on one, so that multiplayer does not need a rewrite

## Implementation Decisions

- **Platform**: NeoForge on the latest stable Minecraft release, with the exact versions verified against official docs when implementation starts.
- **Crawl engine**: a Minecraft-free core module owning the Crawl state machine: pre-Apocalypse countdown, Apocalypse, Floor lifecycle, Floor Timer, Descent Window, Stairwell sealing and opening, collapse, Elimination, winning, Achievement checks and Loot Box grants. Input is ticks and game events (Crawler joined, mob killed, Stairwell used, Crawler died). Output is effects (Host lines, teleports, collapse, loot grants, HUD state). The clock is injected so it can be faked.
- **Minecraft adapter**: a thin layer that translates NeoForge events into engine events and applies engine effects to the world (dimensions, teleports, mob spawns, HUD packets).
- **Floors as dimensions**: one generated dimension per Floor, each shared by all Crawlers on it; see ADR 0001.
- **Floor generation**: connected areas built from hand-made room and corridor templates, generated in chunks near Crawlers. Floor 1 has about five areas, Floor 2 about four, Floor 3 three. Floor 3 uses scattered villages and surrounding ruins instead of corridors. Floor structure is unbreakable. Players may place and break their own blocks.
- **Floor themes**: Floor 1 follows the book's marble-hall mood with a goblin-like, a construct and an irritable-llama Faction. Floor 2 follows the industrial-maze mood with a grub, a dingo-rider and a wasp Faction. Floor 3 is original: bandit ghosts, wild animals and a cult. All names are original; the research notes are in `docs/research/book-floors-1-3.md`. This lineup is provisional pending playtesting.
- **Factions**: each has Grunt (most common), Elite, Shaman (support or magic) and one Boss, built from vanilla mobs with custom names, stats and gear.
- **Bosses**: one per Faction, at a random spot in its territory; optional. A kill opens a gate to the next area and may reveal a Stairwell. At least one Boss per Floor can reveal one.
- **Stairwells**: hidden, unguarded, reusable, several per Floor (Floor 1: 5 to 6, Floor 2: 3 to 4, Floor 3: 3), at least one in the starting area. Usable only in the Descent Window. Visibly sealed, with an "opens in mm:ss" hint before it.
- **Floor Timer**: wall-clock time, default 90 minutes, configurable, counted only while a Crawler is online. Floor 1's starts at the Apocalypse. Each later Floor's starts when the previous Floor collapses. A late arrival simply has less time. Shown as a digital countdown at the top centre. Host warnings at 10 minutes, 1 minute and 10 seconds.
- **Descent Window**: the last ten minutes of the timer. Crawlers who descend arrive on a Floor whose timer is not yet running. A Crawler not on the next Floor by collapse is killed.
- **Apocalypse**: a configurable delay, default in-game day 3, with Host warnings at the start and shortly before. A command triggers it at once. It seals the vanilla world and pulls all Crawlers in with their inventory. Opt-in entry is deferred.
- **Elimination**: permanent, spectate by default, configurable. In single-player the Host mocks the player and offers a fresh Crawl or leaving. A fresh Crawl restores the pre-Apocalypse world.
- **Winning**: passing the last Stairwell wins for that Crawler, with a Host win screen and return to the restored vanilla world with loot.
- **The System ("The Host")**: a giddy, sarcastic game-show host speaking pre-written, data-driven lines as an on-screen banner with a sound cue, plus chat. Lines sit behind an interface so an LLM source can be added later.
- **Achievements**: about 30 defined in data. They include the core set (first kill per rank, first Boss, reaching a Stairwell, reaching the Descent Window) and a large set of deliberately silly ones. Each has a Host line and a Loot Box reward.
- **Loot Boxes**: four tiers (Copper, Silver, Gold, Radiant). Sources are Achievements (mostly), Boss kills (high tier) and floor milestones. Contents are per-tier data tables (vanilla gear with custom enchants, food, Stairwell Aids). Opening triggers a Host line and a short animation.
- **Stairwell Aids**: Seeker's Draught (direction pulse), Echo Chime (rough distance), Revealing Lantern (nearby hidden doors and walls) and Fragment Map (area outline). None gives exact location.
- **Content is data-driven**: factions, mob definitions, Achievements, loot tables and Host lines load from data packs.
- **Multiplayer readiness**: the engine models multiple Crawlers per Crawl. Late-join handling, parties and a multiplayer polish pass are out of scope for v1.

## Testing Decisions

- **Seam**: the Crawl engine interface (events and ticks in, effects out), confirmed with the user. It is the single test seam.
- A good test drives the engine only through that interface with a fake clock and asserts on the emitted effects and visible state, never on internal fields.
- **Engine scenarios** to cover: Apocalypse delay and command trigger; Floor Timer start and pause rules; Descent Window opening and sealed-stairwell rejection; collapse killing those left behind and sparing those who descended; next Floor's timer starting at the previous collapse; Elimination and single-player game-over; Achievement unlock and Loot Box grants; winning.
- Content loading (data validation of Factions, loot tables, Host lines) gets plain unit tests.
- The Minecraft adapter gets only a small GameTest smoke check, since the engine carries the logic.
- **Prior art**: none yet, as the repo has no code.

## Out of Scope

- Opt-in entry via a trigger item or command.
- Late-join penalties and handling, parties, guilds and full multiplayer polish.
- Levels, stats and classes.
- Sponsors, viewership, the Desperado Club safe room, pets and functional shops.
- Custom mob models and entities, or entities from other mods.
- An LLM-generated Host.
- Floors larger than the v1 sizes.
- Publishing the mod; IP risk is reduced by original names, but not reviewed.

## Further Notes

- The faction lineup, Floor sizes and balance are provisional: the user wants to playtest and adjust.
- The research notes on the book's floors are partly unconfirmed (Floor 3 and the stairwell rules); the stairwell design here is original.
- Glossary: `GLOSSARY.md`. ADRs: `docs/adr/`.
