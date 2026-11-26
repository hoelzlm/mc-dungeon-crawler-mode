package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatesTest {
    private static FloorLayout layout(long seed) throws Exception {
        try (var in = GatesTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            return FloorLayout.generate(seed, TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyAreaNotTouchingTheStartAreaIsBehindOneGateOnACorridorCell() throws Exception {
        for (long seed = 0; seed < 40; seed++) {
            FloorLayout layout = layout(seed);
            Set<Integer> gated = new HashSet<>();
            for (Gate g : layout.gates()) {
                assertTrue(g.parentArea() > 0, "start-adjacent areas are open");
                assertTrue(gated.add(g.area()), "one gate per area");
                assertEquals(-1, layout.cell(g.cell()).orElseThrow().area(), "gate sits in a corridor");
            }
            int open = 0;
            for (int a = 1; a < layout.areaCount(); a++) {
                if (!gated.contains(a)) {
                    open++;
                }
            }
            assertTrue(open >= 1, "at least one area is open from the start");
        }
    }
}
