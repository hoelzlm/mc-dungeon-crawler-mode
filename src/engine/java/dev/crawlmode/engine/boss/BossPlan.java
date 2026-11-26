package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.faction.Faction;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.FloorTemplate;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Where each Faction's Boss stands on a Floor, chosen at random from the seed. Every Boss stands in an ordinary
 * room of its own Faction's territory, and the plan is always solvable: killing Bosses in some order eventually
 * opens every gate, so no Boss is walled off behind a gate only it can open.
 */
public final class BossPlan {
    private static final int ATTEMPTS = 100;

    private final List<BossPlacement> placements;

    private BossPlan(List<BossPlacement> placements) {
        this.placements = placements;
    }

    public static BossPlan generate(FloorLayout layout, FactionAreas areas, List<Faction> factions, Bosses bosses,
            long seed) {
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + 0xB055);
        List<List<Cell>> roomsPerFaction = new ArrayList<>();
        List<List<Integer>> areasPerFaction = new ArrayList<>();
        for (Faction f : factions) {
            List<Integer> owned = new ArrayList<>();
            for (int a = 1; a < layout.areaCount(); a++) {
                if (areas.factionOf(a).map(x -> x.id().equals(f.id())).orElse(false)) {
                    owned.add(a);
                }
            }
            areasPerFaction.add(owned);
            List<Cell> rooms = new ArrayList<>();
            for (Cell c : layout.cells()) {
                if (owned.contains(c.area()) && c.template().role() == FloorTemplate.Role.ROOM) {
                    rooms.add(c);
                }
            }
            roomsPerFaction.add(rooms);
        }

        int[] chosenArea = new int[factions.size()];
        boolean solved = false;
        for (int attempt = 0; attempt < ATTEMPTS && !solved; attempt++) {
            for (int i = 0; i < factions.size(); i++) {
                List<Integer> owned = areasPerFaction.get(i);
                chosenArea[i] = owned.get(rnd.nextInt(owned.size()));
            }
            solved = solvable(layout, areas, factions, chosenArea);
        }
        if (!solved) {
            // The shallowest area of each Faction is always solvable: its gates depend only on shallower Factions.
            for (int i = 0; i < factions.size(); i++) {
                chosenArea[i] = areasPerFaction.get(i).get(0);
            }
        }

        List<Stairwell> candidates = new ArrayList<>();
        for (Stairwell w : layout.stairwells()) {
            if (layout.cell(w.cell()).orElseThrow().area() > 0) {
                candidates.add(w);
            }
        }
        List<BossPlacement> out = new ArrayList<>();
        for (int i = 0; i < factions.size(); i++) {
            BossDef def = bosses.of(factions.get(i).id());
            List<Cell> rooms = new ArrayList<>();
            for (Cell c : roomsPerFaction.get(i)) {
                if (c.area() == chosenArea[i]) {
                    rooms.add(c);
                }
            }
            Cell room = rooms.get(rnd.nextInt(rooms.size()));
            int x = room.pos().x() * 16 + 2 + rnd.nextInt(12);
            int z = room.pos().z() * 16 + 2 + rnd.nextInt(12);
            Optional<Stairwell> reveals = def.revealsStairwell()
                    ? Optional.of(candidates.get(rnd.nextInt(candidates.size()))) : Optional.empty();
            out.add(new BossPlacement(def, chosenArea[i], x, FloorLayout.FLOOR_Y + 1, z, reveals));
        }
        return new BossPlan(List.copyOf(out));
    }

    private static boolean solvable(FloorLayout layout, FactionAreas areas, List<Faction> factions, int[] bossArea) {
        Set<String> dead = new HashSet<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            Set<Integer> reachable = Reachability.areas(layout, areas, dead);
            for (int i = 0; i < factions.size(); i++) {
                if (!dead.contains(factions.get(i).id()) && reachable.contains(bossArea[i])) {
                    dead.add(factions.get(i).id());
                    progress = true;
                }
            }
        }
        return dead.size() == factions.size();
    }

    public List<BossPlacement> placements() {
        return placements;
    }

    /** The Bosses standing in the given cell (a cell is 16 by 16 blocks). */
    public List<BossPlacement> placementsIn(CellPos cell) {
        return placements.stream()
                .filter(p -> Math.floorDiv(p.x(), 16) == cell.x() && Math.floorDiv(p.z(), 16) == cell.z())
                .toList();
    }

    public Optional<BossPlacement> of(String factionId) {
        return placements.stream().filter(p -> p.def().factionId().equals(factionId)).findFirst();
    }
}
