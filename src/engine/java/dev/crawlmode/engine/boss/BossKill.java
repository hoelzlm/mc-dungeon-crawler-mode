package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.List;
import java.util.Optional;

public record BossKill(List<Gate> openedGates, Optional<Stairwell> revealedStairwell, Event.LootBoxGranted loot) {}
