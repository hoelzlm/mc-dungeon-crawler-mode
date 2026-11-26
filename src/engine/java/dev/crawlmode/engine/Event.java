package dev.crawlmode.engine;

import java.util.UUID;

public sealed interface Event {
    record CrawlerJoined(UUID crawler) implements Event {}

    record CrawlerLeft(UUID crawler) implements Event {}

    record ApocalypseCommanded() implements Event {}

    record CrawlerDied(UUID crawler) implements Event {}

    record FreshCrawlChosen(UUID crawler) implements Event {}

    record LeaveChosen(UUID crawler) implements Event {}

    record LootBoxGranted(UUID crawler, LootTier tier, LootSource source) implements Event {}

    record StairwellUsed(UUID crawler) implements Event {}

    record MobKilled(UUID crawler, dev.crawlmode.engine.faction.Rank rank) implements Event {}

    record Triggered(UUID crawler, String trigger) implements Event {}

    record SafeRoomDoorUsed(UUID crawler, DoorPos door) implements Event {}

    record SafeRoomExited(UUID crawler) implements Event {}
}
