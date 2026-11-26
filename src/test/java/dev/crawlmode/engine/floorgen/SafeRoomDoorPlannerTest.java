package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.DoorPos;
import dev.crawlmode.engine.boss.BossPlacement;
import dev.crawlmode.engine.boss.BossPlan;
import dev.crawlmode.engine.boss.Bosses;
import dev.crawlmode.engine.faction.FactionAreas;
import dev.crawlmode.engine.faction.Factions;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
                    .map((BossPlacement p) -> new CellPos(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16)))
                    .collect(Collectors.toSet());
            return new Floor(spec, layout, bossCells);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<DoorPos> doors(Floor f) {
        return SafeRoomDoorPlanner.plan(f.spec().floor(), f.layout(), f.bossCells());
    }

    private static CellPos cellOf(DoorPos d) {
        return new CellPos(Math.floorDiv(d.x(), 16), Math.floorDiv(d.z(), 16));
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
                        int apart = distance(cellOf(doors.get(i)), cellOf(doors.get(j)));
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
                        assertTrue(distance(cellOf(d), boss) > SafeRoomDoorPlanner.BOSS_CLEARANCE_CELLS,
                                "floor " + spec.floor() + " seed " + seed + " door near boss");
                    }
                }
            }
        }
    }

    @Test
    void doorsStandOnSolidFloorWithRoomToWalkIn() {
        for (FloorSpec spec : FloorSpec.ALL) {
            for (long seed = 0; seed < 20; seed++) {
                Floor f = floor(spec, seed);
                for (DoorPos d : doors(f)) {
                    assertTrue(f.layout().blockAt(d.x(), d.y() - 1, d.z()).isPresent(), "floor under door");
                    assertFalse(f.layout().blockAt(d.x(), d.y(), d.z()).isPresent(), "door cell free");
                    assertFalse(f.layout().blockAt(d.x(), d.y() + 1, d.z()).isPresent(), "headroom above door");
                    assertEquals(FloorLayout.FLOOR_Y + 1, d.y());
                }
            }
        }
    }

    @Test
    void theSamePlanComesOutForTheSameLayout() {
        Floor f = floor(FloorSpec.ALL.get(0), 7);
        assertEquals(doors(f), doors(f));
    }
}
