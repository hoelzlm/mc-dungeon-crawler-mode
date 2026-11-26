package dev.crawlmode.engine;

import java.util.UUID;

public sealed interface Effect {
    record PullToFloor(UUID crawler, int floor) implements Effect {}

    record ShowTimer(UUID crawler, long remainingTicks) implements Effect {}

    record HostSays(UUID crawler, HostLine line) implements Effect {}

    record Spectate(UUID crawler) implements Effect {}

    record OfferGameOverChoice(UUID crawler) implements Effect {}

    record RestoreWorld(UUID crawler) implements Effect {}

    record Leave(UUID crawler) implements Effect {}

    record GiveLootBox(UUID crawler, LootTier tier) implements Effect {}

    record LootBoxOpening(UUID crawler, LootTier tier) implements Effect {}

    record GrantLoot(UUID crawler, LootTier tier, java.util.List<LootItem> items) implements Effect {}

    record StairwellSealed(UUID crawler, long opensInTicks) implements Effect {}

    record Collapse(UUID crawler, int floor) implements Effect {}

    record ShowWinScreen(UUID crawler) implements Effect {}
}
