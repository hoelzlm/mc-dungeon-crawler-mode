package dev.crawlmode.engine.floorgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record FloorSpec(int floor, String templatesResource, int areas, int minStairwells, int maxStairwells) {
    public static final long SEED_STRIDE = 0x9E3779B97F4A7C15L;

    public static final List<FloorSpec> ALL = List.of(
            new FloorSpec(1, "/data/crawlmode/floor/floor1_templates.txt", 5, 5, 6),
            new FloorSpec(2, "/data/crawlmode/floor/floor2_templates.txt", 4, 3, 4),
            new FloorSpec(3, "/data/crawlmode/floor/floor3_templates.txt", 4, 3, 3));

    public static Optional<FloorSpec> forFloor(int floor) {
        return ALL.stream().filter(s -> s.floor == floor).findFirst();
    }

    public long seedFor(long worldSeed) {
        return worldSeed + (floor - 1) * SEED_STRIDE;
    }

    public static String readFactionData(String kind) {
        List<String> paths = new ArrayList<>();
        paths.add("/data/crawlmode/faction/" + kind + ".txt");
        for (FloorSpec spec : ALL) {
            if (spec.floor > 1) {
                paths.add("/data/crawlmode/faction/floor" + spec.floor + "_" + kind + ".txt");
            }
        }
        StringBuilder all = new StringBuilder();
        for (String path : paths) {
            try (InputStream in = FloorSpec.class.getResourceAsStream(path)) {
                if (in != null) {
                    all.append(new String(in.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return all.toString();
    }
}
