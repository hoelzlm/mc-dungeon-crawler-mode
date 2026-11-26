package dev.crawlmode.engine.floorgen;

import dev.crawlmode.engine.DoorPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class SafeRoomDoorPlanner {
    public static final int DOORS_PER_FLOOR = 4;
    public static final int MIN_SPACING_CELLS = 2;
    public static final int BOSS_CLEARANCE_CELLS = 1;
    private static final int[] CORNER_OFFSETS = {2, 3, 12, 13};

    private SafeRoomDoorPlanner() {}

    public static List<DoorPos> plan(int floor, FloorLayout layout, Set<CellPos> bossCells) {
        List<DoorPos> candidates = new ArrayList<>();
        for (Cell cell : layout.cells()) {
            if (!nearBoss(cell.pos(), bossCells)) {
                spot(floor, layout, cell).ifPresent(candidates::add);
            }
        }
        List<DoorPos> chosen = new ArrayList<>();
        while (chosen.size() < DOORS_PER_FLOOR) {
            Optional<DoorPos> next = farthestFromChosen(candidates, chosen);
            if (next.isEmpty()) {
                break;
            }
            chosen.add(next.get());
            candidates.remove(next.get());
        }
        return List.copyOf(chosen);
    }

    private static Optional<DoorPos> farthestFromChosen(List<DoorPos> candidates, List<DoorPos> chosen) {
        if (chosen.isEmpty()) {
            return candidates.stream().findFirst();
        }
        DoorPos best = null;
        int bestGap = MIN_SPACING_CELLS - 1;
        for (DoorPos c : candidates) {
            int gap = Integer.MAX_VALUE;
            for (DoorPos d : chosen) {
                gap = Math.min(gap, distance(cellOf(c), cellOf(d)));
            }
            if (gap > bestGap) {
                best = c;
                bestGap = gap;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean nearBoss(CellPos pos, Set<CellPos> bossCells) {
        return bossCells.stream().anyMatch(b -> distance(pos, b) <= BOSS_CLEARANCE_CELLS);
    }

    private static CellPos cellOf(DoorPos d) {
        return new CellPos(Math.floorDiv(d.x(), 16), Math.floorDiv(d.z(), 16));
    }

    private static int distance(CellPos a, CellPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    private static Optional<DoorPos> spot(int floor, FloorLayout layout, Cell cell) {
        List<int[]> offsets = new ArrayList<>();
        for (int x : CORNER_OFFSETS) {
            for (int z : CORNER_OFFSETS) {
                offsets.add(new int[] {x, z});
            }
        }
        Collections.shuffle(offsets, new Random(cell.pos().x() * 31L + cell.pos().z()));
        int y = FloorLayout.FLOOR_Y + 1;
        for (int[] o : offsets) {
            int x = cell.pos().x() * 16 + o[0];
            int z = cell.pos().z() * 16 + o[1];
            boolean solidUnder = layout.blockAt(x, y - 1, z).isPresent();
            boolean headroom = layout.blockAt(x, y, z).isEmpty() && layout.blockAt(x, y + 1, z).isEmpty();
            if (solidUnder && headroom && !layout.isStairwell(x, y, z)) {
                return Optional.of(new DoorPos(floor, x, y, z));
            }
        }
        return Optional.empty();
    }
}
