package dev.crawlmode.engine.aids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.LootItem;
import dev.crawlmode.engine.LootTier;
import dev.crawlmode.engine.LootTables;
import dev.crawlmode.engine.floorgen.CellPos;
import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Stairwell;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StairwellAidsTest {
    private static Stairwell well(int x, int z) {
        return new Stairwell(new CellPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), x, 61, z);
    }

    @Test
    void seekersDraughtPointsTowardsTheNearestStairwellInEightCompassPoints() {
        List<Stairwell> wells = List.of(well(100, 0), well(0, -200));

        assertEquals(Optional.of(Compass.EAST), StairwellAids.seekersDraught(0, 0, wells));
        assertEquals(Optional.of(Compass.NORTH), StairwellAids.seekersDraught(0, -150, wells));
        assertEquals(Optional.of(Compass.SOUTH_WEST), StairwellAids.seekersDraught(100, 0, List.of(well(0, 100))));
        assertEquals(Optional.of(Compass.NORTH_EAST), StairwellAids.seekersDraught(0, 0, List.of(well(50, -50))));
    }

    @Test
    void seekersDraughtGivesTheSameCompassPointForAnyBearingWithinTheSameSector() {
        assertEquals(StairwellAids.seekersDraught(0, 0, List.of(well(1000, 176))),
                StairwellAids.seekersDraught(0, 0, List.of(well(1000, 364))));
    }

    @Test
    void noStairwellsMeansNoSignal() {
        assertEquals(Optional.empty(), StairwellAids.seekersDraught(0, 0, List.of()));
        assertEquals(Optional.empty(), StairwellAids.echoChime(0, 0, List.of()));
        assertEquals(Optional.empty(), StairwellAids.fragmentMap(0, 0, List.of(), 1L));
    }

    @Test
    void echoChimeReportsOnlyARoughDistanceBand() {
        assertEquals(Optional.of(DistanceBand.VERY_CLOSE), StairwellAids.echoChime(0, 0, List.of(well(10, 0))));
        assertEquals(Optional.of(DistanceBand.CLOSE), StairwellAids.echoChime(0, 0, List.of(well(0, 40))));
        assertEquals(Optional.of(DistanceBand.FAR), StairwellAids.echoChime(0, 0, List.of(well(100, 0))));
        assertEquals(Optional.of(DistanceBand.VERY_FAR), StairwellAids.echoChime(0, 0, List.of(well(300, 0))));
        assertEquals(Optional.of(DistanceBand.CLOSE), StairwellAids.echoChime(0, 0, List.of(well(300, 0), well(0, -40))));
        assertEquals(StairwellAids.echoChime(0, 0, List.of(well(70, 0))), StairwellAids.echoChime(0, 0, List.of(well(150, 0))));
    }

    @Test
    void fragmentMapOutlinesAnAreaContainingTheNearestStairwellButMuchLargerThanIt() {
        Stairwell near = well(37, 91);
        Area area = StairwellAids.fragmentMap(0, 0, List.of(near, well(500, 500)), 7L).orElseThrow();

        assertTrue(area.contains(near.x(), near.z()));
        assertEquals(StairwellAids.MAP_SIZE, area.maxX() - area.minX() + 1);
        assertEquals(StairwellAids.MAP_SIZE, area.maxZ() - area.minZ() + 1);
        assertTrue(area.minX() < near.x() && near.x() < area.maxX());
    }

    @Test
    void fragmentMapIsStableForOneStairwellAndDoesNotAlwaysCentreIt() {
        Stairwell w = well(37, 91);
        assertEquals(StairwellAids.fragmentMap(0, 0, List.of(w), 7L), StairwellAids.fragmentMap(500, 9, List.of(w), 7L));

        Set<Integer> offsets = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            Stairwell s = well(37 + i * 200, 91);
            Area a = StairwellAids.fragmentMap(s.x(), s.z(), List.of(s), 7L).orElseThrow();
            assertTrue(a.contains(s.x(), s.z()));
            offsets.add(s.x() - a.minX());
        }
        assertTrue(offsets.size() > 5, "the stairwell sits at varied places inside the outline");
    }

    @Test
    void revealingLanternShowsOnlySecretBlocksNearby() {
        FloorLayout layout = layout(3);
        int[] at = findSecretBlock(layout);
        List<StairwellAids.Pos> shown = StairwellAids.revealingLantern(layout, at[0], at[1], at[2]);

        assertTrue(shown.contains(new StairwellAids.Pos(at[0], at[1], at[2])));
        for (StairwellAids.Pos p : shown) {
            assertTrue(layout.blockAt(p.x(), p.y(), p.z()).orElseThrow().secret());
            assertTrue(Math.abs(p.x() - at[0]) <= StairwellAids.LANTERN_RADIUS);
            assertTrue(Math.abs(p.y() - at[1]) <= StairwellAids.LANTERN_RADIUS);
            assertTrue(Math.abs(p.z() - at[2]) <= StairwellAids.LANTERN_RADIUS);
        }
        assertEquals(List.of(), StairwellAids.revealingLantern(layout, 100000, 100, 100000));
    }

    @Test
    void revealingLanternNeverMarksAStairwellMarker() {
        FloorLayout layout = layout(3);
        for (Stairwell w : layout.stairwells()) {
            assertFalse(StairwellAids.revealingLantern(layout, w.x(), w.y(), w.z())
                    .contains(new StairwellAids.Pos(w.x(), w.y(), w.z())));
        }
    }

    @Test
    void everyAidIsSpentOnUseAndTheLanternRevealLastsOnlyBriefly() {
        assertEquals(LootTables.STAIRWELL_AIDS, StairwellAids.ids());
        assertTrue(StairwellAids.CONSUMED_ON_USE);
        assertTrue(StairwellAids.LANTERN_REVEAL_TICKS > 0 && StairwellAids.LANTERN_REVEAL_TICKS <= 20 * 60);
    }

    @Test
    void everyAidIsInSomeLootBoxTable() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/data/crawlmode/loot/tables.txt")) {
            LootTables tables = LootTables.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> found = new HashSet<>();
            for (LootTier tier : LootTier.values()) {
                tables.entries(tier).stream().filter(i -> i.kind() == LootItem.Kind.AID)
                        .forEach(i -> found.add(i.id()));
            }
            assertEquals(StairwellAids.ids(), found);
        }
    }

    private static FloorLayout layout(long seed) {
        try (InputStream in = StairwellAidsTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return FloorLayout.generate(seed, TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int[] findSecretBlock(FloorLayout layout) {
        for (var cell : layout.cells()) {
            for (int y = 0; y < 8; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int wx = cell.pos().x() * 16 + x;
                        int wy = FloorLayout.FLOOR_Y + y;
                        int wz = cell.pos().z() * 16 + z;
                        if (layout.blockAt(wx, wy, wz).map(b -> b.secret()).orElse(false)) {
                            return new int[] {wx, wy, wz};
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no secret blocks in bundled templates");
    }
}
