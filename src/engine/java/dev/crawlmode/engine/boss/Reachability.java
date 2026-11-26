package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Gate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class Reachability {
    private Reachability() {}

    static boolean isOpen(Gate gate, FactionAreas areas, Set<String> deadBosses) {
        return areas.factionOf(gate.parentArea()).map(f -> deadBosses.contains(f.id())).orElse(true);
    }

    static Set<Integer> areas(FloorLayout layout, FactionAreas areas, Set<String> deadBosses) {
        Map<Integer, Gate> gateInto = new HashMap<>();
        for (Gate g : layout.gates()) {
            gateInto.put(g.area(), g);
        }
        Set<Integer> reachable = new TreeSet<>();
        reachable.add(0);
        for (int a = 1; a < layout.areaCount(); a++) {
            Gate g = gateInto.get(a);
            if (g == null) {
                reachable.add(a);
            } else if (reachable.contains(g.parentArea()) && isOpen(g, areas, deadBosses)) {
                reachable.add(a);
            }
        }
        return reachable;
    }
}
