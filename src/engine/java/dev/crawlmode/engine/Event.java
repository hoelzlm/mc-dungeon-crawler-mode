package dev.crawlmode.engine;

import java.util.UUID;

/** Things that happen in the world, as reported to the engine by the adapter. */
public sealed interface Event {
    record CrawlerJoined(UUID crawler) implements Event {}

    record CrawlerLeft(UUID crawler) implements Event {}

    record ApocalypseCommanded() implements Event {}

    /** A Crawler earned a Loot Box (Achievement, Boss kill or floor milestone). */
    record LootBoxGranted(UUID crawler, LootTier tier, LootSource source) implements Event {}

    /** A Crawler tried to use a Stairwell. */
    record StairwellUsed(UUID crawler) implements Event {}
}
