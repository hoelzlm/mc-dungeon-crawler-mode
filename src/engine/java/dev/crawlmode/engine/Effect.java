package dev.crawlmode.engine;

import dev.crawlmode.engine.aids.Area;
import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapSpan;
import java.util.List;
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

    record ConsumeLootBox(UUID crawler, LootTier tier) implements Effect {}

    record LootBoxOpening(UUID crawler, LootTier tier) implements Effect {}

    record GrantLoot(UUID crawler, LootTier tier, List<LootItem> items) implements Effect {}

    record StairwellSealed(UUID crawler, long opensInTicks) implements Effect {}

    record Collapse(UUID crawler, int floor) implements Effect {}

    record ShowWinScreen(UUID crawler) implements Effect {}

    record EnterSafeRoom(UUID crawler, boolean firstEntry) implements Effect {}

    record ExitSafeRoom(UUID crawler, DoorPos door) implements Effect {}

    record StartSafeRoomRegeneration(UUID crawler, int amplifier) implements Effect {}

    record StopSafeRoomRegeneration(UUID crawler) implements Effect {}

    record HandOverSafeRoomContents(UUID crawler) implements Effect {}

    record DeleteSafeRoom(UUID crawler) implements Effect {}

    record RevealMapCells(UUID crawler, int floor, List<MapSpan> spans) implements Effect {}

    record PaintMapArea(UUID crawler, int floor, Area area) implements Effect {}

    record ShowMapDoors(UUID crawler, int floor, List<MapCell> doors) implements Effect {}

    record ServeBuffet(UUID crawler, List<LootItem> items) implements Effect {}
}
