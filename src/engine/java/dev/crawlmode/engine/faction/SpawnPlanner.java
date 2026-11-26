package dev.crawlmode.engine.faction;

import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.FloorLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Decides which mobs a freshly built cell gets, deterministically from the seed and the cell position. */
public final class SpawnPlanner {
    /** Mobs per room cell. */
    public static final int MOBS_PER_ROOM = 4;

    private SpawnPlanner() {}

    public static List<PlannedSpawn> forCell(Cell cell, FactionAreas areas, long seed) {
        if (cell.area() < 1 || cell.template().stairwellMarker().isPresent()) {
            return List.of();
        }
        var faction = areas.factionOf(cell.area());
        if (faction.isEmpty()) {
            return List.of();
        }
        Random rnd = new Random(seed * 31 + cell.pos().x() * 7919L + cell.pos().z() * 104729L);
        int total = 0;
        for (Rank r : Rank.values()) {
            total += faction.get().mob(r).weight();
        }
        List<PlannedSpawn> out = new ArrayList<>();
        for (int i = 0; i < MOBS_PER_ROOM; i++) {
            int at = rnd.nextInt(total);
            MobDef pick = null;
            for (Rank r : Rank.values()) {
                MobDef m = faction.get().mob(r);
                if (at < m.weight()) {
                    pick = m;
                    break;
                }
                at -= m.weight();
            }
            int x = cell.pos().x() * 16 + 2 + rnd.nextInt(12);
            int z = cell.pos().z() * 16 + 2 + rnd.nextInt(12);
            out.add(new PlannedSpawn(pick, x, FloorLayout.FLOOR_Y + 1, z));
        }
        return out;
    }
}
