package dev.crawlmode.engine.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Gate;
import dev.crawlmode.engine.floorgen.TemplateLibrary;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GateGeometryTest {
    @Test
    void gateSealsTheWholeCorridorCrossSectionWithoutTouchingStructure() throws Exception {
        TemplateLibrary lib;
        try (var in = GateGeometryTest.class.getResourceAsStream("/data/crawlmode/floor/floor1_templates.txt")) {
            lib = TemplateLibrary.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (long seed = 0; seed < 30; seed++) {
            FloorLayout layout = FloorLayout.generate(seed, lib);
            for (Gate gate : layout.gates()) {
                var blocks = GateGeometry.blocks(layout, gate);
                assertFalse(blocks.isEmpty());
                for (var p : blocks) {
                    assertTrue(layout.blockAt(p.x(), p.y(), p.z()).isEmpty(), "only air is replaced");
                    assertTrue(Math.floorDiv(p.x(), 16) == gate.cell().x() && Math.floorDiv(p.z(), 16) == gate.cell().z());
                }
                boolean alongZ = gate.side().dz != 0;
                int fixed = alongZ ? blocks.get(0).z() : blocks.get(0).x();
                for (var p : blocks) {
                    assertTrue((alongZ ? p.z() : p.x()) == fixed, "plane is flat across the travel direction");
                }
                int height = layout.cell(gate.cell()).orElseThrow().template().height();
                for (int i = 0; i < 16; i++) {
                    for (int y = FloorLayout.FLOOR_Y + 1; y < FloorLayout.FLOOR_Y + height; y++) {
                        int x = alongZ ? gate.cell().x() * 16 + i : fixed;
                        int z = alongZ ? fixed : gate.cell().z() * 16 + i;
                        if (layout.blockAt(x, y, z).isEmpty()) {
                            assertTrue(blocks.contains(new GateGeometry.Pos(x, y, z)), "sealed at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
    }
}
