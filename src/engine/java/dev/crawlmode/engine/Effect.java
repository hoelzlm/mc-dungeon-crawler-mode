package dev.crawlmode.engine;

import java.util.UUID;

/** What the engine wants done in the world, for the adapter to carry out. */
public sealed interface Effect {
    record PullToFloor(UUID crawler, int floor) implements Effect {}

    /** The floor countdown on this Crawler's screen, in ticks left. */
    record ShowTimer(UUID crawler, long remainingTicks) implements Effect {}

    /** The Host speaks to one Crawler: banner, sound cue and chat. */
    record HostSays(UUID crawler, HostLine line) implements Effect {}

    /** The Crawler is eliminated: put them in spectator mode. */
    record Spectate(UUID crawler) implements Effect {}

    /** Single-player game-over: offer a fresh Crawl or leaving. */
    record OfferGameOverChoice(UUID crawler) implements Effect {}

    /** Return the Crawler to the pre-Apocalypse vanilla world for a fresh Crawl. */
    record RestoreWorld(UUID crawler) implements Effect {}

    /** The Crawler chose to leave the game. */
    record Leave(UUID crawler) implements Effect {}

    /** The short opening animation of a Loot Box. */
    record LootBoxOpening(UUID crawler, LootTier tier) implements Effect {}

    /** Hand the rolled rewards to a Crawler. */
    record GrantLoot(UUID crawler, LootTier tier, java.util.List<LootItem> items) implements Effect {}

    /** The Stairwell is still sealed; it opens in this many ticks. */
    record StairwellSealed(UUID crawler, long opensInTicks) implements Effect {}

    /** The Floor collapsed under this Crawler, killing them. */
    record Collapse(UUID crawler, int floor) implements Effect {}
}
