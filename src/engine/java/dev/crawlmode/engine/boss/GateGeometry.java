package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Gate;
import java.util.ArrayList;
import java.util.List;

/** Which blocks a closed gate fills: the air of one flat plane across the corridor cell, never structure blocks. */
public final class GateGeometry {
    public record Pos(int x, int y, int z) {}

    private GateGeometry() {}

    public static List<Pos> blocks(FloorLayout layout, Gate gate) {
        int height = layout.cell(gate.cell()).orElseThrow().template().height();
        boolean alongZ = gate.side().dz != 0;
        int baseX = gate.cell().x() * 16;
        int baseZ = gate.cell().z() * 16;
        List<Pos> out = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            int x = alongZ ? baseX + i : baseX + 8;
            int z = alongZ ? baseZ + 8 : baseZ + i;
            for (int y = FloorLayout.FLOOR_Y + 1; y < FloorLayout.FLOOR_Y + height; y++) {
                if (layout.blockAt(x, y, z).isEmpty()) {
                    out.add(new Pos(x, y, z));
                }
            }
        }
        return out;
    }
}
