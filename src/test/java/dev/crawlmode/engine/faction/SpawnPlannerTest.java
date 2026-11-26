package dev.crawlmode.engine.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.floorgen.Cell;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpawnPlannerTest {
    private static Factions factions() throws Exception {
        try (var in = SpawnPlannerTest.class.getResourceAsStream("/data/crawlmode/faction/factions.txt")) {
            return Factions.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static FloorLayout layout(long seed) throws Exception {
        try (var in = SpawnPlannerTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return FloorLayout.generate(seed, TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyFactionOwnsAtLeastOneAreaAndAreasAreNotShared() throws Exception {
        FloorLayout layout = layout(7);
        FactionAreas areas = FactionAreas.assign(factions().forFloor(1), layout.areaCount());
        Set<String> owners = new HashSet<>();
        for (int a = 1; a < layout.areaCount(); a++) {
            owners.add(areas.factionOf(a).orElseThrow().id());
        }
        assertEquals(factions().forFloor(1).size(), owners.size());
        assertTrue(areas.factionOf(0).isEmpty(), "start area is safe");
        assertTrue(areas.factionOf(-1).isEmpty(), "corridors are empty");
    }

    @Test
    void mobsOnlySpawnInTheirAreasFactionAndNeverInCorridorsStartOrStairwellRooms() throws Exception {
        FloorLayout layout = layout(11);
        Factions factions = factions();
        FactionAreas areas = FactionAreas.assign(factions.forFloor(1), layout.areaCount());
        Map<Integer, Set<String>> seenFactions = new HashMap<>();
        for (Cell cell : layout.cells()) {
            List<PlannedSpawn> spawns = SpawnPlanner.forCell(cell, areas, 11);
            boolean stairwellRoom = cell.template().stairwellMarker().isPresent();
            if (cell.area() <= 0 || stairwellRoom) {
                assertTrue(spawns.isEmpty());
            }
            for (PlannedSpawn s : spawns) {
                seenFactions.computeIfAbsent(cell.area(), k -> new HashSet<>()).add(s.mob().factionId());
                assertEquals(cell.pos(), new CellPos(Math.floorDiv(s.x(), 16), Math.floorDiv(s.z(), 16)));
            }
        }
        assertEquals(layout.areaCount() - 1, seenFactions.size());
        seenFactions.values().forEach(s -> assertEquals(1, s.size()));
    }

    @Test
    void planningIsDeterministicAndRarityFollowsRank() throws Exception {
        FloorLayout layout = layout(3);
        FactionAreas areas = FactionAreas.assign(factions().forFloor(1), layout.areaCount());
        Map<Rank, Integer> counts = new HashMap<>();
        for (long seed = 0; seed < 40; seed++) {
            for (Cell cell : layout.cells()) {
                List<PlannedSpawn> a = SpawnPlanner.forCell(cell, areas, seed);
                assertEquals(a, SpawnPlanner.forCell(cell, areas, seed));
                a.forEach(s -> counts.merge(s.mob().rank(), 1, Integer::sum));
            }
        }
        assertTrue(counts.get(Rank.GRUNT) > counts.get(Rank.ELITE), counts.toString());
        assertTrue(counts.get(Rank.ELITE) > counts.get(Rank.SHAMAN), counts.toString());
        assertTrue(counts.get(Rank.SHAMAN) > 0);
    }
}
