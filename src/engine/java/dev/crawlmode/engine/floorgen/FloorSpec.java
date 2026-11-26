package dev.crawlmode.engine.floorgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Per-Floor generation and timer settings: which template file builds the Floor, how many areas it has, how many
 * Stairwells it hides (inclusive range) and how long its Floor Timer runs relative to the configured timer.
 * To add a Floor, add its entry to {@link #ALL}.
 *
 * @param timerPercent the Floor Timer length as a percentage of {@code CrawlConfig.floorTimerTicks()}
 */
public record FloorSpec(int floor, String templatesResource, int areas, int minStairwells, int maxStairwells,
        int timerPercent) {
    public static final List<FloorSpec> ALL = List.of(
            new FloorSpec(1, "/data/crawlmode/floor/floor1_templates.txt", 5, 5, 6, 100),
            new FloorSpec(2, "/data/crawlmode/floor/floor2_templates.txt", 4, 3, 4, 80),
            new FloorSpec(3, "/data/crawlmode/floor/floor3_templates.txt", 4, 3, 3, 100));

    public static Optional<FloorSpec> forFloor(int floor) {
        return ALL.stream().filter(s -> s.floor == floor).findFirst();
    }

    /** The Floor Timer percentage for a Floor; floors with no spec run the full configured timer. */
    public static int timerPercent(int floor) {
        return forFloor(floor).map(FloorSpec::timerPercent).orElse(100);
    }

    /**
     * The text of a Faction data file for every Floor: {@code /data/crawlmode/faction/<kind>.txt} followed by each
     * later Floor's optional {@code floor<N>_<kind>.txt}, where {@code kind} is "factions" or "bosses". Missing
     * files are skipped, so a Floor may add its lines to the base file or to its own file.
     */
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
