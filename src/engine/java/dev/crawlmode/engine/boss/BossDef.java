package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.LootTier;
import dev.crawlmode.engine.faction.GearSlot;
import java.util.Map;

public record BossDef(String factionId, String entity, String name, int health, int damage, LootTier tier,
        boolean revealsStairwell, Map<GearSlot, String> gear) {}
