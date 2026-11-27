package dev.crawlmode.engine;

import dev.crawlmode.engine.faction.Rank;
import dev.crawlmode.engine.map.Sighting;
import java.util.List;
import java.util.UUID;

public sealed interface Event {
    record CrawlerJoined(UUID crawler) implements Event {}

    record CrawlerLeft(UUID crawler) implements Event {}

    record ApocalypseCommanded() implements Event {}

    record CrawlerDied(UUID crawler) implements Event {}

    record FreshCrawlChosen(UUID crawler) implements Event {}

    record LeaveChosen(UUID crawler) implements Event {}

    record LootBoxGranted(UUID crawler, LootTier tier, LootSource source) implements Event {}

    record LootBoxOpened(UUID crawler, LootTier tier) implements Event {}

    record StairwellUsed(UUID crawler) implements Event {}

    record MobKilled(UUID crawler, Rank rank) implements Event {}

    record Triggered(UUID crawler, String trigger) implements Event {}

    record SafeRoomDoorUsed(UUID crawler, DoorPos door) implements Event {}

    record SafeRoomExited(UUID crawler) implements Event {}

    record SafeRoomDoorsPlanned(int floor, List<DoorPos> doors) implements Event {}

    record CrawlerMoved(UUID crawler, int x, int z) implements Event {}

    record EntitiesScanned(UUID crawler, int x, int z, List<Sighting> sightings) implements Event {}

    record BuffetUsed(UUID crawler) implements Event {}
}
