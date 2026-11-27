package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.DoorFacing;
import dev.crawlmode.engine.DoorPos;
import dev.crawlmode.engine.boss.BossPlacement;
import dev.crawlmode.engine.boss.BossPlan;
import dev.crawlmode.engine.boss.Bosses;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.Factions;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SafeRoomDoorPlannerTest {
    private static final Factions FACTIONS = Factions.load(new StringReader(FloorSpec.readFactionData("factions")));
    private static final Bosses BOSSES = Bosses.load(new StringReader(FloorSpec.readFactionData("bosses")), FACTIONS);

    private record Floor(FloorSpec spec, FloorLayout layout, Set<CellPos> bossCells) {}

    private static Floor floor(FloorSpec spec, long seed) {
        try (var in = SafeRoomDoorPlannerTest.class.getResourceAsStream(spec.templatesResource())) {
            TemplateLibrary library = TemplateLibrary.parse(new String(in.readAllBytes()));
            FloorLayout layout = FloorLayout.generate(seed, library, spec);
            FactionAreas areas = FactionAreas.assign(FACTIONS.forFloor(spec.floor()), layout.areaCount());
            BossPlan plan = BossPlan.generate(layout, areas, FACTIONS.forFloor(spec.floor()), BOSSES, seed);
            Set<CellPos> bossCells = plan.placements().stream()
                    .map((BossPlacement p) -> CellPos.containing(p.x(), p.z()))
                    .collect(Collectors.toSet());
            return new Floor(spec, layout, bossCells);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<DoorPos> doors(Floor f) {
        return SafeRoomDoorPlanner.plan(f.spec().floor(), f.layout(), f.bossCells());
    }

    private static int distance(CellPos a, CellPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    @Test
    void everyFloorGetsAtLeastFourDoorsForEverySeed() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 60; seed++) {
                List<DoorPos> doors = doors(floor(spec, seed));
                assertTrue(doors.size() >= 4, "floor " + spec.floor() + " seed " + seed + " has " + doors.size());
                assertTrue(doors.stream().allMatch(d -> d.floor() == spec.floor()));
            }
        }
    }

    @Test
    void doorsKeepMinimumSpacingSoTheyAreNotClustered() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 60; seed++) {
                List<DoorPos> doors = doors(floor(spec, seed));
                for (int i = 0; i < doors.size(); i++) {
                    for (int j = i + 1; j < doors.size(); j++) {
                        int apart = distance(doors.get(i).cell(), doors.get(j).cell());
                        assertTrue(apart >= SafeRoomDoorPlanner.MIN_SPACING_CELLS,
                                "floor " + spec.floor() + " seed " + seed + " doors " + apart + " cells apart");
                    }
                }
            }
        }
    }

    @Test
    void doorsAreNeverInOrNearABossArea() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 60; seed++) {
                Floor f = floor(spec, seed);
                for (DoorPos d : doors(f)) {
                    for (CellPos boss : f.bossCells()) {
                        assertTrue(distance(d.cell(), boss) > SafeRoomDoorPlanner.BOSS_CLEARANCE_CELLS,
                                "floor " + spec.floor() + " seed " + seed + " door near boss");
                    }
                }
            }
        }
    }

    @Test
    void everyDoorIsSetIntoAProtectedWallBetweenTwoPillarsNeverInAGate() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 20; seed++) {
                Floor f = floor(spec, seed);
                for (DoorPos d : doors(f)) {
                    assertEquals(FloorLayout.FLOOR_Y + 1, d.y());
                    for (int up = 0; up <= 3; up++) {
                        assertTrue(f.layout().isProtected(d.x(), d.y() + up, d.z()), "wall at door column " + up);
                        assertFalse(f.layout().isGate(d.x(), d.y() + up, d.z()), "no gate at door column " + up);
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        for (int up = 0; up < 3; up++) {
                            assertTrue(f.layout().isProtected(d.sideX(side), d.y() + up, d.sideZ(side)), "pillar wall");
                            assertFalse(f.layout().isGate(d.sideX(side), d.y() + up, d.sideZ(side)), "pillar not a gate");
                        }
                        assertEquals(d.cell(), CellPos.containing(d.sideX(side), d.sideZ(side)), "pillar in own cell");
                    }
                }
            }
        }
    }

    @Test
    void everyDoorsFrontSpotIsFreeOnSolidGround() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 20; seed++) {
                Floor f = floor(spec, seed);
                for (DoorPos d : doors(f)) {
                    int frontX = d.x() + d.facing().dx();
                    int frontZ = d.z() + d.facing().dz();
                    assertTrue(f.layout().blockAt(frontX, d.y() - 1, frontZ).isPresent(), "ground in front");
                    assertFalse(f.layout().blockAt(frontX, d.y(), frontZ).isPresent(), "front free");
                    assertFalse(f.layout().blockAt(frontX, d.y() + 1, frontZ).isPresent(), "front headroom");
                }
            }
        }
    }

    @Test
    void facingsPointTheWayAnObserverWouldLookAwayFromTheDoor() {
        assertEquals(0, DoorFacing.SOUTH.yawDegrees());
        assertEquals(90, DoorFacing.WEST.yawDegrees());
        assertEquals(180, DoorFacing.NORTH.yawDegrees());
        assertEquals(270, DoorFacing.EAST.yawDegrees());
        assertEquals(1, DoorFacing.SOUTH.dz());
        assertEquals(-1, DoorFacing.WEST.dx());
    }

    private record Flat(Set<List<Integer>> wallColumns) implements DoorTerrain {
        @Override
        public boolean solid(int x, int y, int z) {
            return y <= FloorLayout.FLOOR_Y || wall(x, y, z);
        }

        @Override
        public boolean wall(int x, int y, int z) {
            return y > FloorLayout.FLOOR_Y && y <= FloorLayout.FLOOR_Y + 4 && wallColumns.contains(List.of(x, z));
        }

        @Override
        public boolean gate(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean stairwell(int x, int y, int z) {
            return false;
        }
    }

    private static List<CellPos> row(int length) {
        return IntStream.range(0, length).mapToObj(x -> new CellPos(x, 0)).toList();
    }

    private static Set<List<Integer>> columns(int[]... xz) {
        Set<List<Integer>> all = new HashSet<>();
        for (int[] c : xz) {
            all.add(List.of(c[0], c[1]));
        }
        return all;
    }

    private static Set<List<Integer>> wallAlongX(int[]... segments) {
        Set<List<Integer>> all = new HashSet<>();
        for (int[] segment : segments) {
            for (int x = segment[0]; x <= segment[1]; x++) {
                all.add(List.of(x, segment[2]));
            }
        }
        return all;
    }

    @Test
    void aWallSegmentOffersADoorOnEachSideWithPillarsInTheWallPlane() {
        Flat terrain = new Flat(wallAlongX(new int[] {7, 9, 8}));

        List<DoorPos> doors = SafeRoomDoorPlanner.candidates(1, terrain, new CellPos(0, 0));

        assertTrue(doors.contains(new DoorPos(1, 8, 61, 8, DoorFacing.SOUTH)));
        assertTrue(doors.contains(new DoorPos(1, 8, 61, 8, DoorFacing.NORTH)));
        assertEquals(2, doors.size());
    }

    @Test
    void aWallTooNarrowForTwoPillarsOffersNoDoor() {
        Flat terrain = new Flat(wallAlongX(new int[] {7, 8, 8}));

        assertEquals(List.of(), SafeRoomDoorPlanner.candidates(1, terrain, new CellPos(0, 0)));
    }

    @Test
    void aDoorsPillarsStayInsideItsOwnCell() {
        Flat terrain = new Flat(wallAlongX(new int[] {14, 16, 8}));

        assertEquals(List.of(), SafeRoomDoorPlanner.candidates(1, terrain, new CellPos(0, 0)));
    }

    @Test
    void aCellWithoutAWallSpotBorrowsOneFromANeighbouringCell() {
        Flat terrain = new Flat(wallAlongX(new int[] {7, 9, 8}, new int[] {18, 20, 8}, new int[] {28, 30, 8}, new int[] {55, 57, 8}));

        List<DoorPos> doors = SafeRoomDoorPlanner.plan(1, terrain, row(4), Set.of());

        assertEquals(4, doors.size());
        assertEquals(2, doors.stream().filter(d -> d.cell().equals(new CellPos(1, 0))).count());
    }

    @Test
    void borrowedSpotsMayShareACellButStayEightBlocksApart() {
        Flat terrain = new Flat(wallAlongX(new int[] {7, 9, 8}, new int[] {18, 20, 8}, new int[] {28, 30, 8}, new int[] {55, 57, 8}));

        List<DoorPos> shared = SafeRoomDoorPlanner.plan(1, terrain, row(4), Set.of()).stream()
                .filter(d -> d.cell().equals(new CellPos(1, 0))).toList();

        assertEquals(2, shared.size());
        assertTrue(Math.abs(shared.get(0).x() - shared.get(1).x()) >= SafeRoomDoorPlanner.BORROWED_SPACING_BLOCKS);
    }

    @Test
    void aFloorWithoutAnyWallSpotFailsLoudly() {
        Flat terrain = new Flat(columns());

        assertThrows(IllegalStateException.class, () -> SafeRoomDoorPlanner.plan(1, terrain, row(6), Set.of()));
    }

    @Test
    void theSamePlanComesOutForTheSameLayout() {
        Floor f = floor(FloorSpec.ALL.get(0), 7);
        assertEquals(doors(f), doors(f));
    }

    @Test
    void whenBossClearanceLeavesFewRoomsDoorsRelaxTheirSpacingInsteadOfComingUpShort() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 60; seed++) {
                Floor f = floor(spec, seed);
                Set<CellPos> crowded = f.layout().cells().stream().limit(1).map(Cell::pos).collect(Collectors.toSet());
                assertTrue(SafeRoomDoorPlanner.plan(spec.floor(), f.layout(), crowded).size() >= 4);
            }
        }
    }

    @Test
    void aFloorWithNoRoomForFourDoorsFailsLoudly() {
        Floor f = floor(FloorSpec.ALL.get(0), 1);
        Set<CellPos> everywhere = f.layout().cells().stream().map(Cell::pos).collect(Collectors.toSet());

        assertThrows(IllegalStateException.class,
                () -> SafeRoomDoorPlanner.plan(f.spec().floor(), f.layout(), everywhere));
    }

    @Test
    void doorCellsAreTheCellsContainingThem() {
        assertEquals(new CellPos(-1, 1), new DoorPos(1, -1, 65, 16, DoorFacing.NORTH).cell());
    }
}
