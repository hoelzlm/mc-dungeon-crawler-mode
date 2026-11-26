package dev.crawlmode.engine.floorgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class GenerationPlanner {
    private GenerationPlanner() {}

    public static List<CellPos> cellsToBuild(FloorLayout layout, List<PlayerBlock> players, int radiusCells,
            Predicate<CellPos> alreadyBuilt, int budget) {
        record Candidate(CellPos pos, int distance) {}
        List<Candidate> candidates = new ArrayList<>();
        for (Cell cell : layout.cells()) {
            CellPos pos = cell.pos();
            if (alreadyBuilt.test(pos)) {
                continue;
            }
            int best = Integer.MAX_VALUE;
            for (PlayerBlock p : players) {
                int dx = Math.abs(Math.floorDiv(p.x(), 16) - pos.x());
                int dz = Math.abs(Math.floorDiv(p.z(), 16) - pos.z());
                best = Math.min(best, Math.max(dx, dz));
            }
            if (best <= radiusCells) {
                candidates.add(new Candidate(pos, best));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::distance));
        List<CellPos> result = new ArrayList<>();
        for (Candidate c : candidates) {
            if (result.size() == budget) {
                break;
            }
            result.add(c.pos());
        }
        return result;
    }
}
