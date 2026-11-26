package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.Event;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.List;
import java.util.Optional;

/**
 * What a Boss kill does: gates that just opened, the Stairwell it revealed (if any) and the Loot Box grant for the
 * killer, ready to feed to the engine.
 */
public record BossKill(List<Gate> openedGates, Optional<Stairwell> revealedStairwell, Event.LootBoxGranted loot) {}
