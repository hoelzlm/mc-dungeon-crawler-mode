package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SafeRoomTemplateTest {
    private static String bundled() {
        try (InputStream in = SafeRoomTemplateTest.class.getResourceAsStream("/data/crawlmode/saferoom/room.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String room(String topRow, String secondRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("name tiny_room\nrole safe_room\nheight 2\n");
        sb.append("palette # minecraft:stone\npalette F minecraft:oak_planks\n");
        sb.append("layer 0\n");
        for (int z = 0; z < 16; z++) {
            sb.append("F".repeat(16)).append('\n');
        }
        sb.append("layer 1\n");
        sb.append(topRow).append(".".repeat(16 - topRow.length())).append('\n');
        sb.append(secondRow).append(".".repeat(16 - secondRow.length())).append('\n');
        for (int z = 2; z < 16; z++) {
            sb.append(".".repeat(16)).append('\n');
        }
        return sb.toString();
    }

    private static String valid() {
        return room("cccfda", "");
    }

    @Test
    void markedCellsAreReadFromTheTemplate() {
        FloorTemplate t = FloorTemplate.parse(valid());

        assertEquals(List.of(new FloorTemplate.Marker(0, 1, 0), new FloorTemplate.Marker(1, 1, 0),
                new FloorTemplate.Marker(2, 1, 0)), t.markers(FloorTemplate.Furniture.CHEST));
        assertEquals(List.of(new FloorTemplate.Marker(3, 1, 0)), t.markers(FloorTemplate.Furniture.FOOD_POT));
        assertEquals(List.of(new FloorTemplate.Marker(4, 1, 0)), t.markers(FloorTemplate.Furniture.EXIT_DOOR));
        assertEquals(List.of(new FloorTemplate.Marker(5, 1, 0)), t.markers(FloorTemplate.Furniture.ARRIVAL));
    }

    @Test
    void markedCellsAreLeftForTheAdapterToFurnish() {
        FloorTemplate t = FloorTemplate.parse(valid());

        assertEquals(Optional.empty(), t.blockAt(3, 1, 0, Set.of()));
        assertEquals(Optional.of(new TemplateBlock("minecraft:oak_planks", false)), t.blockAt(3, 0, 0, Set.of()));
    }

    @Test
    void bundledRoomHasThreeChestsOnePotOneExitAndOneArrivalSpot() {
        FloorTemplate t = FloorTemplate.parse(bundled());

        assertEquals(3, t.markers(FloorTemplate.Furniture.CHEST).size());
        assertEquals(1, t.markers(FloorTemplate.Furniture.FOOD_POT).size());
        assertEquals(1, t.markers(FloorTemplate.Furniture.EXIT_DOOR).size());
        assertEquals(1, t.markers(FloorTemplate.Furniture.ARRIVAL).size());
    }

    @Test
    void roomWithTwoExitsIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("cccfdda", "")));
    }

    @Test
    void roomWithoutAnExitIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("cccfa", "")));
    }

    @Test
    void roomWithTwoPotsIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("cccffda", "")));
    }

    @Test
    void roomWithoutAPotIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("cccda", "")));
    }

    @Test
    void roomWithoutThreeChestsIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("ccfda", "")));
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("ccccfda", "")));
    }

    @Test
    void roomWithoutAnArrivalSpotIsRejected() {
        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(room("cccfd", "")));
    }

    @Test
    void markerCharactersCannotBePaletteEntries() {
        String text = valid().replace("palette F minecraft:oak_planks\n",
                "palette F minecraft:oak_planks\npalette c minecraft:chest\n");

        assertThrows(FloorTemplateFormatException.class, () -> FloorTemplate.parse(text));
    }

    @Test
    void aSafeRoomTemplateBelongsInNoFloorLibrary() {
        assertThrows(FloorTemplateFormatException.class, () -> TemplateLibrary.parse(valid()));
    }
}
