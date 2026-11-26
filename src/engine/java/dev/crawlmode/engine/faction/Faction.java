package dev.crawlmode.engine.faction;

import java.util.Map;

public record Faction(String id, String name, int floor, Map<Rank, MobDef> mobs) {
    public MobDef mob(Rank rank) {
        return mobs.get(rank);
    }
}
