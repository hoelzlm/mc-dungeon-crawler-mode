package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VillageRuinsLayoutTest {
    static String template(String name, String role) {
        StringBuilder sb = new StringBuilder("name " + name + "\nrole " + role + "\nheight 4\n");
        sb.append("palette # minecraft:stone\npalette F minecraft:grass_block\npalette X minecraft:end_portal_frame\n");
        sb.append("layer 0\n").append(("F".repeat(16) + "\n").repeat(16));
        if (role.equals("stairwell_room")) {
            sb.append("layer 1\nX").append(".".repeat(15)).append('\n').append((".".repeat(16) + "\n").repeat(15));
        }
        return sb.toString();
    }

    static TemplateLibrary library() {
        return TemplateLibrary.parse(String.join("---\n",
                template("v1", "village"), template("v2", "village"), template("r1", "room"),
                template("r2", "room"), template("p1", "corridor"), template("s1", "stairwell_room")));
    }

    @Test
    void libraryKeepsVillageTemplatesApartFromRuinRooms() {
        TemplateLibrary lib = library();

        assertEquals(2, lib.villages().size());
        assertEquals(2, lib.rooms().size());
        assertTrue(lib.villages().stream().allMatch(t -> t.role() == FloorTemplate.Role.VILLAGE));
        assertFalse(lib.villages().isEmpty());
    }

    private static FloorLayout layout(long seed) {
        return VillageRuinsLayout.generate(seed, library());
    }

    @Test
    void aStartVillageAndThreeRuinsOfNineCellsEach() {
        FloorLayout layout = layout(1);

        assertEquals(4, layout.areaCount());
        for (int area = 0; area < 4; area++) {
            int a = area;
            assertEquals(9, layout.cells().stream().filter(c -> c.area() == a).count());
        }
    }

    @Test
    void theStartAreaIsAllVillageAndEveryRuinHasAVillageAtItsHeartRingedByRuins() {
        for (long seed = 0; seed < 30; seed++) {
            FloorLayout layout = layout(seed);
            for (Cell c : layout.cells()) {
                if (c.area() == 0 && c.template().stairwellMarker().isEmpty()) {
                    assertEquals(FloorTemplate.Role.VILLAGE, c.template().role());
                }
                if (c.area() > 0) {
                    Cell heart = layout.cells().stream().filter(o -> o.area() == c.area()).filter(o -> isHeart(layout, o))
                            .findFirst().orElseThrow();
                    assertEquals(FloorTemplate.Role.VILLAGE, heart.template().role());
                    if (c != heart && c.template().role() != FloorTemplate.Role.CORRIDOR) {
                        assertTrue(c.template().role() == FloorTemplate.Role.ROOM
                                || c.template().stairwellMarker().isPresent());
                    }
                }
            }
        }
    }

    private static boolean isHeart(FloorLayout layout, Cell c) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                var n = layout.cell(new CellPos(c.pos().x() + dx, c.pos().z() + dz));
                if (n.isEmpty() || n.get().area() != c.area()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void threeHiddenStairwellsOneInTheStartAreaButNotWhereCrawlersArrive() {
        for (long seed = 0; seed < 50; seed++) {
            FloorLayout layout = layout(seed);
            List<Stairwell> wells = layout.stairwells();

            assertEquals(3, wells.size());
            assertEquals(1, wells.stream().filter(w -> layout.cell(w.cell()).orElseThrow().area() == 0).count());
            assertFalse(wells.stream().anyMatch(w -> w.cell().equals(new CellPos(0, 0))));
            assertEquals(3, new HashSet<>(wells).size());
            assertTrue(wells.stream().allMatch(w -> layout.isStairwell(w.x(), w.y(), w.z())));
            assertTrue(wells.stream().allMatch(w -> layout.cell(w.cell()).orElseThrow().template().role()
                    == FloorTemplate.Role.STAIRWELL_ROOM));
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
                for (Side s : layout.cell(p).orElseThrow().open()) {
                    CellPos n = new CellPos(p.x() + s.dx, p.z() + s.dz);
                    assertTrue(layout.cell(n).orElseThrow().open().contains(s.opposite()), "mutual at " + p);
                    todo.add(n);
                }
            }
            assertEquals(layout.cells().size(), seen.size(), "seed " + seed);
        }
    }

    @Test
    void sameSeedGivesTheSameLayout() {
        assertEquals(layout(9).stairwells(), layout(9).stairwells());
        assertEquals(layout(9).gates(), layout(9).gates());
    }

    @Test
    void crawlersArriveInTheMiddleOfAVillageWithOpenSkyAbove() {
        FloorLayout layout = layout(4);
        SpawnPoint spawn = layout.spawn();

        assertEquals(FloorTemplate.Role.VILLAGE, layout.cell(new CellPos(0, 0)).orElseThrow().template().role());
        assertTrue(layout.blockAt(spawn.x(), spawn.y() - 1, spawn.z()).isPresent());
        assertTrue(layout.blockAt(spawn.x(), spawn.y(), spawn.z()).isEmpty());
    }

    @Test
    void ruinAreasBeyondTheFirstAreGatedBehindABoss() {
        for (long seed = 0; seed < 30; seed++) {
            FloorLayout layout = layout(seed);
            for (Gate g : layout.gates()) {
                assertTrue(g.parentArea() > 0 && g.area() > g.parentArea());
                assertEquals(FloorTemplate.Role.CORRIDOR, layout.cell(g.cell()).orElseThrow().template().role());
            }
        }
    }
}
