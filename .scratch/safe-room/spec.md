# Safe Room and inventory Loot Boxes

Status: ready-for-agent

## Problem Statement

While playtesting the Crawl, a Crawler has nowhere to rest, nowhere to store gear between Floors and no choice about when rewards arrive. Loot Boxes open the instant they are granted and spill their contents into the inventory, so they have no weight as an object. The book's safe rooms, where nothing can happen to you, are missing, and so is any way to keep belongings safe while exploring a dangerous Floor.

## Solution

Loot Boxes become stackable inventory items in four tiers, opened only inside the Safe Room. Each Crawler has a private Safe Room, the same one across every Floor of the Crawl, reached through at least four visible Safe Room Doors on each Floor. Inside, nothing can hurt the Crawler, a light regeneration soothes them, chests hold their stored items and a refilling food supply feeds them. Entry and exit are instant, so a door doubles as an escape. The Floor Timer keeps running, so the Safe Room is never a way to skip a collapse.

## User Stories

1. As a Crawler, I want Loot Boxes to sit unopened in my inventory, so that rewards feel like objects I own
2. As a Crawler, I want Loot Boxes in four tiers (Copper, Silver, Gold, Radiant) as distinct items, so that I can tell them apart at a glance
3. As a Crawler, I want Loot Boxes of the same tier to stack, so that my inventory does not fill with single items
4. As a Crawler, I want to open a Loot Box by right-clicking it in the Safe Room, so that opening is a deliberate moment
5. As a Crawler, I want one right-click to open exactly one box, so that I can savour each reward
6. As a Crawler, I want the existing animation and Host line when a box opens, so that it still feels like an event
7. As a Crawler, I want Achievements, Boss kills and milestones to give me an unopened Loot Box, so that every reward follows the same rules
8. As a Crawler, I want the Host to tell me a new box is in my inventory and to find a Safe Room, so that I know what to do with it
9. As a Crawler, I want a box right-clicked outside the Safe Room to poof away with no reward, so that carelessness has a price
10. As a Crawler, I want a one-time joke Achievement for opening a box outside the Safe Room, rewarding a Copper Loot Box, so that the mistake is funny rather than bitter
11. As a Crawler, I want the Host to explain at the Apocalypse that Loot Boxes only open in a Safe Room, so that I am warned before I lose one
12. As a Crawler, I want the rule repeated when I receive my first Loot Box, so that I remember it at the moment it matters
13. As a Crawler, I want a welcome line on my first Safe Room entry covering the chests, food and Loot Boxes, so that I learn the room naturally
14. As a Crawler, I want at least four visible Safe Room Doors on every Floor, so that one is never far away
15. As a Crawler, I want Safe Room Doors spread across the Floor, so that they are not clustered in one corner
16. As a Crawler, I want Safe Room Doors to stay out of Boss areas, so that I cannot duck out of a Boss fight
17. As a Crawler, I want a Safe Room Door usable at any time, including the Descent Window, so that I can always reach my belongings
18. As a Crawler, I want entering a door to be instant, so that it works as an escape as in the book
19. As a Crawler, I want leaving to be instant and to return me to the door I came through, so that I resume where I left off
20. As a Crawler, I want every door on every Floor to lead to the same Safe Room, so that my stored items are always there
21. As a Crawler, I want my Safe Room to be private to me, so that nobody can take what I stored
22. As a Crawler, I want my Safe Room to last the whole Crawl, so that I can keep gear while descending
23. As a Crawler, I want to take no damage of any kind in the Safe Room, so that it is truly safe
24. As a Crawler, I want no hostile mobs in the Safe Room, so that I can rest
25. As a Crawler, I want no PvP in the Safe Room, so that other Crawlers cannot threaten me
26. As a Crawler, I want a light regeneration while inside, so that resting heals me
27. As a Crawler, I want chests in the Safe Room, so that I can store items
28. As a Crawler, I want a refilling basic food supply I can carry out, so that I can eat without leaving the Crawl
29. As a Crawler, I want the Floor Timer to keep running while I am inside, so that the tension of the Crawl is not broken
30. As a Crawler, I want to die if the Floor I entered from collapses while I am inside, so that hiding cannot skip the collapse
31. As a Crawler, I want to leave and use a Stairwell before the collapse to survive, so that the Descent Window still demands action
32. As a late-joining Crawler, I want a fresh empty Safe Room created when I first enter, so that I can take part like everyone else
33. As an Eliminated Crawler, I want my Safe Room deleted with me, so that nobody can loot it
34. As a Crawler who wins the Crawl, I want my Safe Room contents delivered to my inventory, so that nothing I stored is lost
35. As a content author, I want the Safe Room food, regeneration strength and door count defined in data files, so that I can tune them after playtesting
36. As a content author, I want the new Host lines defined in the Host data files, so that I can change the wording without code
37. As a multiplayer host, I want one dedicated dimension per Crawler for their Safe Room, so that privacy is guaranteed

## Implementation Decisions

- **Loot Box item**: four tiered, stackable items. Every existing grant path (Achievements, Boss kills, floor milestones) produces an unopened item instead of dumping the contents straight into the inventory. Contents are still drawn from the per-tier loot tables when a box is opened.
- **Opening rule**: the engine decides whether a right-click on a box is an opening (inside the Safe Room, consumes one box, plays the opening effects) or a poof (outside, consumes one box, no contents, joke Achievement once, reward a Copper Loot Box).
- **Safe Room dimension**: one dedicated dimension per Crawler, created on first entry and kept for the whole Crawl (ADR 0005). It holds the chests and the refilling food supply. The room's layout is data-driven like Floor templates.
- **Origin tracking**: the engine remembers, per Crawler, the Floor and the door they entered from. Leaving teleports them back to that door. The collapse of the origin Floor kills a Crawler who is still inside, and a Crawler inside during the Descent Window is treated as still on that Floor.
- **Safe Room Doors**: the Floor generation planner places at least four visible doors per Floor, spread apart, never in or near a Boss area. Being near a Stairwell is allowed. Doors work throughout the Floor Timer, including the Descent Window.
- **Rules inside**: no damage, no hostile mobs, no PvP, and a light regeneration effect. The Floor Timer is unaffected.
- **Entry and exit**: both instant, with no cooldown. Revisit after playtesting.
- **Lifecycle**: late joiners get a fresh empty room on first entry. Elimination deletes the room. Winning hands the room's contents to the Crawler and then deletes it.
- **Host lines**: Apocalypse rule explanation, first-box reminder, first-entry welcome, new-box announcement and joke Achievement text, all in Host data files.
- **Tuning**: food items, refill cooldown, regeneration strength and door count are data, because the user will tune them in playtesting.
- **Glossary and ADR**: Safe Room and Safe Room Door are in `GLOSSARY.md`, and ADR 0005 records the dimension-per-Crawler decision.

## Testing Decisions

- A good test drives external behaviour only: events in, effects out, with no assertions on internals.
- **Primary seam**: the Crawl engine with the fake clock. New scenarios: first entry and late join, entering and leaving with origin remembered, no damage and regeneration inside, collapse and Descent Window while inside, box opening inside versus poof outside including the one-time Achievement, every grant source producing an unopened box, Host lines, Elimination deleting the room, winning handing over the contents.
- **Second part of the same Minecraft-free layer**: the floor generation planner tests gain checks for at least four doors per Floor, spread apart, none in or near Boss areas.
- Content loading (new data files and Host lines) gets plain unit tests.
- No new Minecraft adapter tests. Adapters stay thin and are covered by the existing `smokeTest` and playtesting.
- Prior art: `LootBoxTest`, `DescentWindowTest`, `EliminationTest`, `WinningTest`, `HostVoiceTest` and the `floorgen` planner tests.

## Out of Scope

- The minimap, to be grilled separately.
- Group-shared Safe Rooms; they are private for now.
- Cooldowns or restrictions on door use after combat.
- Final tuning of food, doors and regeneration, which follows playtesting.
- Pets, shops, sponsors and the Desperado Club.
- Looting another Crawler's Safe Room.

## Further Notes

- This reverses the v1 spec's out-of-scope entry for the Desperado Club safe room.
- Instant entry makes doors an escape from ordinary fights by design, and Boss areas are kept door-free to protect Boss fights. Revisit after playtesting.
- Boss areas are the only restriction on door placement for now; being near a Stairwell is allowed.
