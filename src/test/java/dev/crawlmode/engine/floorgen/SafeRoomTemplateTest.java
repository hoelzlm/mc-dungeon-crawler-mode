package dev.crawlmode.engine.floorgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static final String BLACK = "minecraft:black_concrete";
    private static final Set<String> ALLOWED_LIGHT = Set.of("minecraft:lantern", "minecraft:candle", "minecraft:campfire");
    private static final Set<String> KNOWN_LIGHT = Set.of("minecraft:lantern", "minecraft:candle", "minecraft:campfire",
            "minecraft:shroomlight", "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:jack_o_lantern",
            "minecraft:redstone_lamp", "minecraft:torch", "minecraft:soul_torch", "minecraft:soul_lantern",
            "minecraft:soul_campfire", "minecraft:ochre_froglight", "minecraft:verdant_froglight",
            "minecraft:pearlescent_froglight", "minecraft:lava", "minecraft:fire", "minecraft:magma_block",
            "minecraft:end_rod", "minecraft:beacon", "minecraft:conduit", "minecraft:crying_obsidian",
            "minecraft:respawn_anchor", "minecraft:copper_torch");

    private static String id(FloorTemplate t, int x, int y, int z) {
        return t.blockAt(x, y, z, Set.of()).map(b -> b.blockId().replaceAll("\\[.*", "")).orElse("");
    }

    private static boolean walkable(FloorTemplate t, int x, int z) {
        String block = id(t, x, 1, z);
        return block.isEmpty() || block.endsWith("_carpet");
    }

    private static Set<List<Integer>> reachableFromArrival(FloorTemplate t) {
        FloorTemplate.Marker start = t.markers(FloorTemplate.Furniture.ARRIVAL).getFirst();
        Set<List<Integer>> seen = new HashSet<>();
        ArrayDeque<List<Integer>> queue = new ArrayDeque<>();
        queue.add(List.of(start.x(), start.z()));
        seen.add(queue.peek());
        while (!queue.isEmpty()) {
            List<Integer> at = queue.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at.get(0) + d[0];
                int z = at.get(1) + d[1];
                if (x >= 0 && x < FloorTemplate.SIZE && z >= 0 && z < FloorTemplate.SIZE && walkable(t, x, z)
                        && seen.add(List.of(x, z))) {
                    queue.add(List.of(x, z));
                }
            }
        }
        return seen;
    }

    private static boolean touchesReachable(Set<List<Integer>> reachable, FloorTemplate.Marker m) {
        return reachable.contains(List.of(m.x() + 1, m.z())) || reachable.contains(List.of(m.x() - 1, m.z()))
                || reachable.contains(List.of(m.x(), m.z() + 1)) || reachable.contains(List.of(m.x(), m.z() - 1));
    }

    @Test
    void everyChestThePotAndTheExitCanBeReachedFromTheArrivalSpot() {
        FloorTemplate t = FloorTemplate.parse(bundled());
        Set<List<Integer>> reachable = reachableFromArrival(t);

        for (FloorTemplate.Furniture kind : List.of(FloorTemplate.Furniture.CHEST, FloorTemplate.Furniture.FOOD_POT,
                FloorTemplate.Furniture.EXIT_DOOR)) {
            for (FloorTemplate.Marker m : t.markers(kind)) {
                assertTrue(touchesReachable(reachable, m), kind + " at " + m + " is walled in");
            }
        }
    }

    @Test
    void everyChestAndThePotHasHeadroomAboveIt() {
        FloorTemplate t = FloorTemplate.parse(bundled());

        for (FloorTemplate.Furniture kind : List.of(FloorTemplate.Furniture.CHEST, FloorTemplate.Furniture.FOOD_POT)) {
            for (FloorTemplate.Marker m : t.markers(kind)) {
                assertEquals("", id(t, m.x(), m.y() + 1, m.z()), kind + " at " + m + " is covered");
            }
        }
    }

    @Test
    void everyWindowIsGlassOverABlackBackdrop() {
        FloorTemplate t = FloorTemplate.parse(bundled());
        int panes = 0;
        for (int y = 0; y < t.height(); y++) {
            for (int z = 0; z < FloorTemplate.SIZE; z++) {
                for (int x = 0; x < FloorTemplate.SIZE; x++) {
                    if (!id(t, x, y, z).equals("minecraft:glass_pane")) {
                        continue;
                    }
                    panes++;
                    boolean alongZ = id(t, x, y, z - 1).equals(BLACK) && id(t, x, y, z + 1).isEmpty()
                            || id(t, x, y, z + 1).equals(BLACK) && id(t, x, y, z - 1).isEmpty();
                    boolean alongX = id(t, x - 1, y, z).equals(BLACK) && id(t, x + 1, y, z).isEmpty()
                            || id(t, x + 1, y, z).equals(BLACK) && id(t, x - 1, y, z).isEmpty();
                    assertTrue(alongZ || alongX, "window at " + x + "," + y + "," + z + " has no black backdrop");
                }
            }
        }
        assertTrue(panes > 0);
    }

    @Test
    void theRoofIsSolidSoNoSkyShowsThrough() {
        FloorTemplate t = FloorTemplate.parse(bundled());

        for (int z = 0; z < FloorTemplate.SIZE; z++) {
            for (int x = 0; x < FloorTemplate.SIZE; x++) {
                assertFalse(id(t, x, t.height() - 1, z).isEmpty(), "gap in the roof at " + x + "," + z);
            }
        }
    }

    @Test
    void onlyLanternsCandlesAndTheHearthGiveLight() {
        FloorTemplate t = FloorTemplate.parse(bundled());
        Set<String> used = new HashSet<>();
        for (int y = 0; y < t.height(); y++) {
            for (int z = 0; z < FloorTemplate.SIZE; z++) {
                for (int x = 0; x < FloorTemplate.SIZE; x++) {
                    used.add(id(t, x, y, z));
                }
            }
        }
        used.retainAll(KNOWN_LIGHT);

        assertTrue(ALLOWED_LIGHT.containsAll(used), "unexpected light source in " + used);
        assertTrue(used.containsAll(ALLOWED_LIGHT));
    }
}
