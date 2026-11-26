package dev.crawlmode.engine;

import java.util.UUID;

/** Things that happen in the world, as reported to the engine by the adapter. */
public sealed interface Event {
    record CrawlerJoined(UUID crawler) implements Event {}

    record CrawlerLeft(UUID crawler) implements Event {}

    record ApocalypseCommanded() implements Event {}

    record CrawlerDied(UUID crawler) implements Event {}

    record FreshCrawlChosen(UUID crawler) implements Event {}

    record LeaveChosen(UUID crawler) implements Event {}

    /** A Crawler earned a Loot Box (Achievement, Boss kill or floor milestone). */
    record LootBoxGranted(UUID crawler, LootTier tier, LootSource source) implements Event {}

    /** A Crawler tried to use a Stairwell. */
    record StairwellUsed(UUID crawler) implements Event {}

    /** A Crawler killed a mob of this Rank. */
    record MobKilled(UUID crawler, dev.crawlmode.engine.faction.Rank rank) implements Event {}

    /** Something Achievements can key on happened to a Crawler, e.g. {@code kill.boss} or {@code death.fall}. */
    record Triggered(UUID crawler, String trigger) implements Event {}
}
