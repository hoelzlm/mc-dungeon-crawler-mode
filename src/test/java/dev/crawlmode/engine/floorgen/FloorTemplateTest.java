package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FloorTemplateTest {
    /** A tiny template: a floor layer, then one layer with a wall, a north gate, a secret block and a marker. */
    private static String sample() {
        StringBuilder sb = new StringBuilder();
        sb.append("name tiny\nrole stairwell_room\nheight 2\n");
        sb.append("palette # minecraft:stone\npalette F minecraft:smooth_quartz\npalette X minecraft:end_portal_frame\n");
        sb.append("layer 0\n");
        for (int z = 0; z < 16; z++) {
            sb.append("F".repeat(16)).append('\n');
        }
        sb.append("layer 1\n");
        sb.append("#nkX").append(".".repeat(12)).append('\n');
        for (int z = 1; z < 16; z++) {
            sb.append(".".repeat(16)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void solidBlocksComeFromThePalette() {
        FloorTemplate t = FloorTemplate.parse(sample());

        assertEquals("tiny", t.name());
        assertEquals(Optional.of(new TemplateBlock("minecraft:smooth_quartz", false)),
                t.blockAt(5, 0, 5, Set.of()));
        assertEquals(Optional.of(new TemplateBlock("minecraft:stone", false)), t.blockAt(0, 1, 0, Set.of()));
    }

    @Test
    void airAndOutOfRangeAreEmpty() {
        FloorTemplate t = FloorTemplate.parse(sample());

        assertEquals(Optional.empty(), t.blockAt(5, 1, 5, Set.of()));
        assertEquals(Optional.empty(), t.blockAt(5, 9, 5, Set.of()));
    }

    @Test
    void gateIsWallUntilItsSideIsConnected() {
        FloorTemplate t = FloorTemplate.parse(sample());

        assertEquals(Optional.of(new TemplateBlock("minecraft:stone", false)), t.blockAt(1, 1, 0, Set.of()));
        assertEquals(Optional.empty(), t.blockAt(1, 1, 0, EnumSet.of(Side.NORTH)));
        assertEquals(Optional.of(new TemplateBlock("minecraft:stone", false)),
                t.blockAt(1, 1, 0, EnumSet.of(Side.SOUTH)));
    }

    @Test
    void secretBlockLooksLikeWallButIsMarkedSecret() {
        FloorTemplate t = FloorTemplate.parse(sample());

        assertEquals(Optional.of(new TemplateBlock("minecraft:stone", true)), t.blockAt(2, 1, 0, Set.of()));
    }

    @Test
    void markerPositionIsFoundAndUsesItsPaletteBlock() {
        FloorTemplate t = FloorTemplate.parse(sample());

        assertEquals(Optional.of(new FloorTemplate.Marker(3, 1, 0)), t.stairwellMarker());
        assertEquals(Optional.of(new TemplateBlock("minecraft:end_portal_frame", false)), t.blockAt(3, 1, 0, Set.of()));
    }

    @Test
    void layerRangesShareOneGrid() {
        String text = sample().replace("layer 1\n", "layer 1-2\n").replace("height 2", "height 3");
        FloorTemplate t = FloorTemplate.parse(text);

        assertEquals(Optional.of(new TemplateBlock("minecraft:stone", false)), t.blockAt(0, 2, 0, Set.of()));
    }

    @Test
    void malformedTemplatesAreRejected() {
        assertThrows(FloorTemplateFormatException.class,
                () -> FloorTemplate.parse("name x\nrole room\nheight 1\nlayer 0\nabc\n"));
        assertThrows(FloorTemplateFormatException.class,
                () -> FloorTemplate.parse(sample().replace("palette # minecraft:stone\n", "")));
        assertThrows(FloorTemplateFormatException.class,
                () -> FloorTemplate.parse(sample().replace("role stairwell_room", "role banana")));
    }
}
