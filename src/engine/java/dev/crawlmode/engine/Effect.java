package dev.crawlmode.engine;

import java.util.UUID;

/** What the engine wants done in the world, for the adapter to carry out. */
public sealed interface Effect {
    record PullToFloor(UUID crawler, int floor) implements Effect {}

    /** The floor countdown on this Crawler's screen, in ticks left. */
    record ShowTimer(UUID crawler, long remainingTicks) implements Effect {}

    /** The Host speaks to one Crawler: banner, sound cue and chat. */
    record HostSays(UUID crawler, HostLine line) implements Effect {}

    /** The short opening animation of a Loot Box. */
    record LootBoxOpening(UUID crawler, LootTier tier) implements Effect {}

    /** Hand the rolled rewards to a Crawler. */
    record GrantLoot(UUID crawler, LootTier tier, java.util.List<LootItem> items) implements Effect {}
}
