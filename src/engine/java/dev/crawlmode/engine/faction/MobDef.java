package dev.crawlmode.engine.faction;

import java.util.List;
import java.util.Map;

/** One rank of a Faction: a vanilla entity with a custom name, stats, gear and (for Shamans) abilities. */
public record MobDef(String factionId, Rank rank, String entity, String name, int health, int damage, int weight,
        Map<GearSlot, String> gear, List<Ability> abilities) {}
