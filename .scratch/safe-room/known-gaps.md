# Safe Room known gaps

- Safe Room dimensions are created by reflection on private `MinecraftServer` fields (`levels`, `executor`, `storageSource`). A Minecraft or NeoForge update may break this. See ADR 0006.
- No restart persistence. The Crawl engine state (which rooms exist, who is inside, origin doors) and the room levels are not saved. After a server restart the rooms are not loaded, and a Crawler's first entry creates a fresh room on top of any leftover region files (the folder is reused by name, so old chests may reappear).
- Deletion is best effort. The level is removed from the `levels` map, closed and its dimension folder deleted, but a failure is only logged and can leave files behind. A Crawler who disconnects inside a room that is then deleted is moved to the overworld spawn by `delete`.
- Entry to the Safe Room on a Crawler's first visit completes at the end of the server tick, not in the same tick as the click.
- Unverified live: the deferred creation, the region folder deletion, the loot box consumption, and the elimination-inside spectator placement were only covered by unit tests of the engine and a compile, not by a smoke run or playtest.
