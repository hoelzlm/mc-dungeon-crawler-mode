package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GenerationPlannerTest {
    private static final FloorLayout LAYOUT = layout();

    private static FloorLayout layout() {
        try (InputStream in = GenerationPlannerTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return FloorLayout.generate(5, TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<CellPos> plan(List<PlayerBlock> players, int radiusCells, Set<CellPos> done, int budget) {
        return GenerationPlanner.cellsToBuild(LAYOUT, players, radiusCells, done::contains, budget);
    }

    @Test
    void buildsNothingWhenNobodyIsOnTheFloor() {
        assertEquals(List.of(), plan(List.of(), 3, Set.of(), 10));
    }

    @Test
    void buildsTheCellUnderThePlayerFirstThenOutwards() {
        List<CellPos> next = plan(List.of(new PlayerBlock(8, 8)), 2, Set.of(), 100);

        assertEquals(new CellPos(0, 0), next.get(0));
        for (int i = 1; i < next.size(); i++) {
            int prev = Math.max(Math.abs(next.get(i - 1).x()), Math.abs(next.get(i - 1).z()));
            int cur = Math.max(Math.abs(next.get(i).x()), Math.abs(next.get(i).z()));
            assertTrue(cur >= prev, "nearer cells come first");
        }
    }

    @Test
    void onlyCellsOfTheFloorWithinTheRadiusAreRequested() {
        List<CellPos> next = plan(List.of(new PlayerBlock(8, 8)), 1, Set.of(), 100);

        assertEquals(9, next.size());
        assertTrue(next.stream().allMatch(p -> Math.abs(p.x()) <= 1 && Math.abs(p.z()) <= 1));
        assertEquals(List.of(), plan(List.of(new PlayerBlock(16 * 200, 16 * 200)), 1, Set.of(), 100));
    }

    @Test
    void budgetCapsTheWorkPerCall() {
        assertEquals(2, plan(List.of(new PlayerBlock(8, 8)), 3, Set.of(), 2).size());
    }

    @Test
    void alreadyBuiltCellsAreSkippedSoRepeatedCallsFinishTheJob() {
        Set<CellPos> done = new HashSet<>();
        int calls = 0;
        List<CellPos> batch;
        while (!(batch = plan(List.of(new PlayerBlock(8, 8)), 1, done, 2)).isEmpty()) {
            done.addAll(batch);
            calls++;
        }
        assertEquals(9, done.size());
        assertEquals(5, calls);
    }

    @Test
    void nearestPlayerWinsWhenSeveralAreOnTheFloor() {
        List<CellPos> next = plan(List.of(new PlayerBlock(8, 8), new PlayerBlock(24, -8)), 1, Set.of(), 2);

        assertEquals(Set.of(new CellPos(0, 0), new CellPos(1, -1)), Set.copyOf(next));
    }
}
