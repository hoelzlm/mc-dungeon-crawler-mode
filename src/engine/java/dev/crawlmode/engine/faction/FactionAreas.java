package dev.crawlmode.engine.faction;

import java.util.List;
import java.util.Optional;

public final class FactionAreas {
    private final List<Faction> factions;
    private final int areaCount;

    private FactionAreas(List<Faction> factions, int areaCount) {
        this.factions = factions;
        this.areaCount = areaCount;
    }

    public static FactionAreas assign(List<Faction> factions, int areaCount) {
        if (areaCount - 1 < factions.size()) {
            throw new IllegalArgumentException("not enough areas for " + factions.size() + " factions");
        }
        return new FactionAreas(List.copyOf(factions), areaCount);
    }

    public Optional<Faction> factionOf(int area) {
        if (area < 1 || area >= areaCount) {
            return Optional.empty();
        }
        return Optional.of(factions.get((area - 1) % factions.size()));
    }
}
