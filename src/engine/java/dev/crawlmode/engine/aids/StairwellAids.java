package dev.crawlmode.engine.aids;

import dev.crawlmode.engine.floorgen.FloorLayout;
import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

public final class StairwellAids {
    public static final String SEEKERS_DRAUGHT = "seekers_draught";
    public static final String ECHO_CHIME = "echo_chime";
    public static final String REVEALING_LANTERN = "revealing_lantern";
    public static final String FRAGMENT_MAP = "fragment_map";

    public static final boolean CONSUMED_ON_USE = true;
    public static final int MAP_SIZE = 64;
    public static final int LANTERN_RADIUS = 8;
    public static final int LANTERN_REVEAL_TICKS = 20 * 30;
    private static final int MAP_MARGIN = 8;

    public record Pos(int x, int y, int z) {}

    private StairwellAids() {}

    public static Set<String> ids() {
        return Set.of(SEEKERS_DRAUGHT, ECHO_CHIME, REVEALING_LANTERN, FRAGMENT_MAP);
    }

    public static Optional<Compass> seekersDraught(int x, int z, List<Stairwell> wells) {
        return nearest(x, z, wells).map(w -> Compass.of(w.x() - x, w.z() - z));
    }

    public static Optional<DistanceBand> echoChime(int x, int z, List<Stairwell> wells) {
        return nearest(x, z, wells).map(w -> DistanceBand.of(Math.hypot(w.x() - x, w.z() - z)));
    }

    public static Optional<Area> fragmentMap(int x, int z, List<Stairwell> wells, long salt) {
        return nearest(x, z, wells).map(w -> {
            SplittableRandom rnd = new SplittableRandom(salt ^ (w.x() * 73856093L) ^ (w.z() * 19349663L));
            int span = MAP_SIZE - 2 * MAP_MARGIN;
            int minX = w.x() - MAP_MARGIN - rnd.nextInt(span);
            int minZ = w.z() - MAP_MARGIN - rnd.nextInt(span);
            return new Area(minX, minZ, minX + MAP_SIZE - 1, minZ + MAP_SIZE - 1);
        });
    }

    public static List<Pos> revealingLantern(FloorLayout layout, int x, int y, int z) {
        List<Pos> found = new ArrayList<>();
        for (int dy = -LANTERN_RADIUS; dy <= LANTERN_RADIUS; dy++) {
            for (int dz = -LANTERN_RADIUS; dz <= LANTERN_RADIUS; dz++) {
                for (int dx = -LANTERN_RADIUS; dx <= LANTERN_RADIUS; dx++) {
                    int bx = x + dx;
                    int by = y + dy;
                    int bz = z + dz;
                    if (layout.blockAt(bx, by, bz).map(b -> b.secret()).orElse(false)) {
                        found.add(new Pos(bx, by, bz));
                    }
                }
            }
        }
        return found;
    }

    private static Optional<Stairwell> nearest(int x, int z, List<Stairwell> wells) {
        return wells.stream().min(Comparator.comparingDouble(w -> Math.hypot(w.x() - x, w.z() - z)));
    }
}
