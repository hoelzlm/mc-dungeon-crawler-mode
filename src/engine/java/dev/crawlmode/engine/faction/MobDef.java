package dev.crawlmode.engine.faction;

import java.util.List;
import java.util.Map;

public record MobDef(String factionId, Rank rank, String entity, String name, int health, int damage, int weight,
        Map<GearSlot, String> gear, List<Ability> abilities) {}
