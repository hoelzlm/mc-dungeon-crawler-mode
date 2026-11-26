package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloorSpecTest {
    private static TemplateLibrary library(FloorSpec spec) throws Exception {
        try (InputStream in = FloorSpecTest.class.getResourceAsStream(spec.templatesResource())) {
            return TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void floorOneKeepsItsOriginalShape() {
        FloorSpec one = FloorSpec.forFloor(1).orElseThrow();
        assertEquals(5, one.areas());
        assertEquals(5, one.minStairwells());
        assertEquals(6, one.maxStairwells());
        assertEquals(100, one.timerPercent());
    }

    @Test
    void floorTwoHasFourAreasAndThreeToFourStairwells() throws Exception {
        FloorSpec two = FloorSpec.forFloor(2).orElseThrow();
        TemplateLibrary lib = library(two);
        for (long seed = 0; seed < 60; seed++) {
            FloorLayout layout = FloorLayout.generate(seed, lib, two);
            assertEquals(4, layout.areaCount());
            int wells = layout.stairwells().size();
            assertTrue(wells >= 3 && wells <= 4, "wells " + wells);
            assertTrue(layout.stairwells().stream().anyMatch(w -> layout.cell(w.cell()).orElseThrow().area() == 0),
                    "a Stairwell in the start area");
        }
    }

    @Test
    void floorTwoHasItsOwnTimerLength() {
        assertEquals(80, FloorSpec.forFloor(2).orElseThrow().timerPercent());
        assertEquals(100, FloorSpec.timerPercent(7), "floors without a spec run the full timer");
    }

    @Test
    void floorTwoIsBuiltOfIndustrialTemplatesNotMarble() throws Exception {
        TemplateLibrary two = library(FloorSpec.forFloor(2).orElseThrow());
        assertTrue(two.rooms().size() >= 3);
        assertTrue(two.corridors().size() >= 2);
        assertFalse(two.stairwellRooms().isEmpty());
        for (FloorTemplate t : two.rooms()) {
            String floor = t.blockAt(0, 0, 0, Set.of()).orElseThrow().blockId();
            assertFalse(floor.contains("diorite") || floor.contains("quartz"), floor);
        }
    }

    /** Walkable means two free blocks of headroom above the floor; secret walls count as free (a Crawler can break them). */
    private static boolean walkable(FloorTemplate t, int x, int z) {
        return (t.blockAt(x, 1, z, EnumSet.allOf(Side.class)).map(TemplateBlock::secret).orElse(true))
                && (t.blockAt(x, 2, z, EnumSet.allOf(Side.class)).map(TemplateBlock::secret).orElse(true));
    }

    private static Set<Integer> reach(FloorTemplate t, int sx, int sz) {
        Set<Integer> seen = new HashSet<>();
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        todo.add(new int[] {sx, sz});
        while (!todo.isEmpty()) {
            int[] p = todo.pop();
            if (p[0] < 0 || p[0] > 15 || p[1] < 0 || p[1] > 15 || !walkable(t, p[0], p[1]) || !seen.add(p[1] * 16 + p[0])) {
                continue;
            }
            todo.add(new int[] {p[0] + 1, p[1]});
            todo.add(new int[] {p[0] - 1, p[1]});
            todo.add(new int[] {p[0], p[1] + 1});
            todo.add(new int[] {p[0], p[1] - 1});
        }
        return seen;
    }

    @Test
    void everyTemplateJoinsAllFourDoorsAndTheStairwellMarkerIsReachable() throws Exception {
        for (FloorSpec spec : FloorSpec.ALL) {
            TemplateLibrary lib = library(spec);
            List<FloorTemplate> all = new ArrayList<>(lib.rooms());
            all.addAll(lib.corridors());
            all.addAll(lib.stairwellRooms());
            for (FloorTemplate t : all) {
                Set<Integer> seen = reach(t, 0, 7);
                for (int[] door : new int[][] {{7, 0}, {7, 15}, {0, 7}, {15, 7}}) {
                    assertTrue(seen.contains(door[1] * 16 + door[0]), t.name() + " door " + door[0] + "," + door[1]);
                }
                t.stairwellMarker().ifPresent(m -> assertTrue(seen.contains(m.z() * 16 + m.x() + 1) || seen.contains(m.z() * 16 + m.x() - 1)
                        || seen.contains((m.z() + 1) * 16 + m.x()) || seen.contains((m.z() - 1) * 16 + m.x()), t.name() + " marker"));
            }
        }
    }
}
