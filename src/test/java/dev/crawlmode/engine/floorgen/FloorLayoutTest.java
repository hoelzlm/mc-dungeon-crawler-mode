package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloorLayoutTest {
    private static final TemplateLibrary LIBRARY = bundled();

    private static TemplateLibrary bundled() {
        try (InputStream in = FloorLayoutTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FloorLayout layout(long seed) {
        return FloorLayout.generate(seed, LIBRARY);
    }

    @Test
    void bundledTemplatesCoverEveryRoleWithVariety() {
        assertTrue(LIBRARY.rooms().size() >= 3);
        assertTrue(LIBRARY.corridors().size() >= 2);
        assertFalse(LIBRARY.stairwellRooms().isEmpty());
    }

    @Test
    void hasFiveAreasOfNineRoomsEach() {
        FloorLayout layout = layout(1);

        assertEquals(5, layout.areaCount());
        for (int area = 0; area < 5; area++) {
            int a = area;
            assertEquals(9, layout.cells().stream().filter(c -> c.area() == a).count());
        }
    }

    @Test
    void everyCellIsReachableFromTheStartAndConnectionsAreMutual() {
        for (long seed = 0; seed < 50; seed++) {
            FloorLayout layout = layout(seed);
            Set<CellPos> seen = new HashSet<>();
            ArrayDeque<CellPos> todo = new ArrayDeque<>(List.of(new CellPos(0, 0)));
            while (!todo.isEmpty()) {
                CellPos p = todo.pop();
                if (!seen.add(p)) {
                    continue;
                }
                Cell cell = layout.cell(p).orElseThrow();
                for (Side side : cell.open()) {
                    CellPos next = new CellPos(p.x() + side.dx, p.z() + side.dz);
                    Cell other = layout.cell(next).orElseThrow(() -> new AssertionError("open into nothing at " + next));
                    assertTrue(other.open().contains(side.opposite()), "one-way door at " + p + " " + side);
                    todo.push(next);
                }
            }
            assertEquals(layout.cells().size(), seen.size(), "seed " + seed);
        }
    }

    @Test
    void sameSeedGivesSameFloorAndSeedsDiffer() {
        assertEquals(layout(42).cells(), layout(42).cells());
        Set<Set<Cell>> distinct = new HashSet<>();
        for (long seed = 0; seed < 10; seed++) {
            distinct.add(Set.copyOf(layout(seed).cells()));
        }
        assertTrue(distinct.size() > 5);
    }

    @Test
    void fiveOrSixDistinctStairwellsWithAtLeastOneInTheStartArea() {
        Set<Integer> counts = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            FloorLayout layout = layout(seed);
            List<Stairwell> wells = layout.stairwells();
            counts.add(wells.size());
            assertTrue(wells.size() == 5 || wells.size() == 6, "seed " + seed);
            assertEquals(wells.size(), Set.copyOf(wells).size());
            assertTrue(wells.stream().anyMatch(w -> layout.cell(w.cell()).orElseThrow().area() == 0), "seed " + seed);
            assertTrue(wells.stream().noneMatch(w -> w.cell().equals(new CellPos(0, 0))), "never on the spawn cell");
            for (Stairwell w : wells) {
                assertTrue(layout.isStairwell(w.x(), w.y(), w.z()));
                assertEquals(Optional.of("minecraft:end_portal_frame"),
                        layout.blockAt(w.x(), w.y(), w.z()).map(TemplateBlock::blockId));
            }
        }
        assertEquals(Set.of(5, 6), counts);
    }

    @Test
    void stairwellsAreHiddenBehindASecretWallAndNotGuarded() {
        FloorLayout layout = layout(7);
        for (Stairwell w : layout.stairwells()) {
            assertTrue(reaches(layout, w, false) == false, "reachable without breaking anything");
            assertTrue(reaches(layout, w, true), "unreachable even through the secret wall");
        }
    }

    private static boolean reaches(FloorLayout layout, Stairwell w, boolean secretIsAir) {
        int x0 = w.cell().x() * 16;
        int z0 = w.cell().z() * 16;
        int y0 = FloorLayout.FLOOR_Y + 1;
        ArrayDeque<int[]> todo = new ArrayDeque<>();
        Set<List<Integer>> seen = new HashSet<>();
        todo.push(new int[] {x0 + 8, y0, z0 + 8});
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        while (!todo.isEmpty()) {
            int[] p = todo.pop();
            if (p[0] < x0 || p[0] >= x0 + 16 || p[2] < z0 || p[2] >= z0 + 16 || p[1] < y0 || p[1] > y0 + 3) {
                continue;
            }
            Optional<TemplateBlock> b = layout.blockAt(p[0], p[1], p[2]);
            if (b.isPresent() && !(secretIsAir && b.get().secret())) {
                continue;
            }
            if (!seen.add(List.of(p[0], p[1], p[2]))) {
                continue;
            }
            if (Math.abs(p[0] - w.x()) + Math.abs(p[2] - w.z()) == 1 && p[1] == w.y()) {
                return true;
            }
            for (int[] d : dirs) {
                todo.push(new int[] {p[0] + d[0], p[1] + d[1], p[2] + d[2]});
            }
        }
        return false;
    }

    @Test
    void spawnPointIsOpenAirAboveSolidFloorInTheStartArea() {
        for (long seed = 0; seed < 50; seed++) {
            FloorLayout layout = layout(seed);
            SpawnPoint s = layout.spawn();

            assertEquals(Optional.empty(), layout.blockAt(s.x(), s.y(), s.z()));
            assertEquals(Optional.empty(), layout.blockAt(s.x(), s.y() + 1, s.z()));
            assertTrue(layout.blockAt(s.x(), s.y() - 1, s.z()).isPresent());
            assertEquals(0, layout.cell(new CellPos(Math.floorDiv(s.x(), 16), Math.floorDiv(s.z(), 16))).orElseThrow().area());
        }
    }

    @Test
    void structureIsProtectedButAirSecretsAndEmptySpaceAreNot() {
        FloorLayout layout = layout(3);
        int y = FloorLayout.FLOOR_Y;

        assertTrue(layout.isProtected(8, y, 8), "floor");
        assertTrue(layout.isProtected(0, y + 2, 0), "wall");
        assertFalse(layout.isProtected(8, y + 1, 8), "air a player may fill and then break");
        assertFalse(layout.isProtected(100_000, y, 100_000), "outside the floor");
        assertFalse(layout.isProtected(8, y + 100, 8), "far above");
        Stairwell w = layout.stairwells().get(0);
        assertTrue(layout.isProtected(w.x(), w.y(), w.z()));
        assertTrue(layout.blockAt(w.x() + 2, w.y(), w.z()).orElseThrow().secret());
        assertFalse(layout.isProtected(w.x() + 2, w.y(), w.z()));
    }

    @Test
    void differentSeedsPlaceStairwellsDifferently() {
        assertNotEquals(layout(1).stairwells(), layout(2).stairwells());
    }
}
