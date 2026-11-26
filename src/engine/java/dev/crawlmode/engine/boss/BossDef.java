package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.LootTier;
import dev.crawlmode.engine.faction.GearSlot;
import java.util.Map;

/** The Boss of a Faction: a vanilla entity with custom name, stats and gear, a Loot Box reward and a reveal flag. */
public record BossDef(String factionId, String entity, String name, int health, int damage, LootTier tier,
        boolean revealsStairwell, Map<GearSlot, String> gear) {}
