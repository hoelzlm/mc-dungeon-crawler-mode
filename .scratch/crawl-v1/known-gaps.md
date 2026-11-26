# Crawl v1: known gaps

Found in review and deliberately not fixed on `fix/review-crawl-v1`.

- **No persistence across server restarts.** The Floor Timer and the engine's state live in memory and the clock is the server tick count, so a restart loses the Crawl's progress.
- **No visible marker for a sealed Stairwell.** A Crawler camping near a sealed Stairwell gets no in-world cue that it is sealed; only the "opens in mm:ss" message on use.
- **A fresh Crawl does not restore Floor dimensions or world.** The engine resets its rules state and teleports the Crawler to the overworld, but built Floor blocks, spawned mobs and Boss/gate state are not rebuilt or cleared.
- **Floor 3 area count** is not settled against the spec.
- **Boss reveal message** does not give the exact coordinates the spec describes as written; it announces the revealed Stairwell's position in a Host chat line only.
- **`/crawlgame` and `/stairwell` dev commands** are present and callable by any player (`/stairwell` has no permission gate); whether they belong in v1 scope is undecided.
- **Never run in-game.** Every adapter (Floor building, Boss spawning, gates, HUD, Loot Box animation, Stairwell Aids, win screen, collapse and Elimination handling, including the engine-driven collapse Elimination) compiles but was never exercised in a live game. Unticked acceptance boxes in the tickets list what still needs a manual play check.

## Choices made in the review fixes

- A **collapse is final**: its victims are eliminated by the engine (Spectate, Host elimination or game-over line) in every Elimination mode, including Respawn. Respawn mode only respawns ordinary deaths, on the Floor the Crawler is on.
- Every Floor runs the full configured Floor Timer (default 90 minutes); the per-Floor timer percentage was removed.
