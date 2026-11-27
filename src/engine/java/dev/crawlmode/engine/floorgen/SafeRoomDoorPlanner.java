package dev.crawlmode.engine.floorgen;

import dev.crawlmode.engine.DoorFacing;
import dev.crawlmode.engine.DoorPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import java.util.stream.Stream;

public final class SafeRoomDoorPlanner {
    public static final int DOORS_PER_FLOOR = 4;
    public static final int MIN_SPACING_CELLS = 2;
    public static final int BOSS_CLEARANCE_CELLS = 1;
    public static final int BORROWED_SPACING_BLOCKS = 8;
    private static final int FRAME_HEIGHT = 3;

    private SafeRoomDoorPlanner() {}

    public static List<DoorPos> plan(int floor, FloorLayout layout, Set<CellPos> bossCells) {
        List<CellPos> cells = layout.cells().stream().map(Cell::pos).toList();
        return plan(floor, terrainOf(layout), cells, bossCells);
    }

    public static List<DoorPos> plan(int floor, DoorTerrain terrain, List<CellPos> cells, Set<CellPos> bossCells) {
        List<CellPos> eligible = cells.stream().filter(c -> !nearBoss(c, bossCells)).toList();
        List<DoorPos> own = new ArrayList<>();
        List<CellPos> bare = new ArrayList<>();
        for (CellPos cell : eligible) {
            Optional<DoorPos> spot = spots(floor, terrain, cell).findFirst();
            spot.ifPresentOrElse(own::add, () -> bare.add(cell));
        }
        for (int spacing = MIN_SPACING_CELLS; spacing >= 1; spacing--) {
            List<DoorPos> chosen = choose(own, (a, b) -> distance(a.cell(), b.cell()), spacing);
            if (chosen.size() >= DOORS_PER_FLOOR) {
                return List.copyOf(chosen);
            }
        }
        List<DoorPos> widened = new ArrayList<>(own);
        for (CellPos cell : bare) {
            for (CellPos neighbour : eligible) {
                if (distance(cell, neighbour) == 1) {
                    widened.addAll(spots(floor, terrain, neighbour).toList());
                }
            }
        }
        List<DoorPos> chosen = choose(widened, SafeRoomDoorPlanner::blockDistance, BORROWED_SPACING_BLOCKS);
        if (chosen.size() >= DOORS_PER_FLOOR) {
            return List.copyOf(chosen);
        }
        throw new IllegalStateException("Floor " + floor + " has no room for " + DOORS_PER_FLOOR + " Safe Room Doors");
    }

    public static List<DoorPos> candidates(int floor, DoorTerrain terrain, CellPos cell) {
        return spots(floor, terrain, cell).toList();
    }

    private static List<DoorPos> choose(List<DoorPos> candidates, ToIntBiFunction<DoorPos, DoorPos> distance,
            int minimum) {
        List<DoorPos> remaining = new ArrayList<>(candidates);
        List<DoorPos> chosen = new ArrayList<>();
        while (chosen.size() < DOORS_PER_FLOOR) {
            Optional<DoorPos> next = farthestFromChosen(remaining, chosen, distance, minimum);
            if (next.isEmpty()) {
                break;
            }
            chosen.add(next.get());
            remaining.remove(next.get());
        }
        return chosen;
    }

    private static Optional<DoorPos> farthestFromChosen(List<DoorPos> candidates, List<DoorPos> chosen,
            ToIntBiFunction<DoorPos, DoorPos> distance, int minimum) {
        if (chosen.isEmpty()) {
            return candidates.stream().findFirst();
        }
        DoorPos best = null;
        int bestGap = minimum - 1;
        for (DoorPos c : candidates) {
            int gap = Integer.MAX_VALUE;
            for (DoorPos d : chosen) {
                gap = Math.min(gap, distance.applyAsInt(c, d));
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

    private static int distance(CellPos a, CellPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    private static int blockDistance(DoorPos a, DoorPos b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    private static Stream<DoorPos> spots(int floor, DoorTerrain terrain, CellPos cell) {
        List<DoorPos> all = new ArrayList<>();
        int y = FloorLayout.FLOOR_Y + 1;
        for (int x = 0; x < FloorTemplate.SIZE; x++) {
            for (int z = 0; z < FloorTemplate.SIZE; z++) {
                for (DoorFacing facing : DoorFacing.values()) {
                    all.add(new DoorPos(floor, cell.originX() + x, y, cell.originZ() + z, facing));
                }
            }
        }
        Collections.shuffle(all, new Random(cell.x() * 31L + cell.z()));
        return all.stream().filter(d -> fits(terrain, cell, d));
    }

    private static boolean fits(DoorTerrain t, CellPos cell, DoorPos d) {
        for (int up = 0; up <= FRAME_HEIGHT; up++) {
            if (!framed(t, d.x(), d.y() + up, d.z())) {
                return false;
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int up = 0; up < FRAME_HEIGHT; up++) {
                if (!framed(t, d.sideX(side), d.y() + up, d.sideZ(side))) {
                    return false;
                }
            }
            if (!CellPos.containing(d.sideX(side), d.sideZ(side)).equals(cell)) {
                return false;
            }
        }
        return !t.stairwell(d.x(), d.y(), d.z())
                && !t.stairwell(d.frontX(), d.y(), d.frontZ())
                && t.solid(d.frontX(), d.y() - 1, d.frontZ())
                && !t.solid(d.frontX(), d.y(), d.frontZ())
                && !t.solid(d.frontX(), d.y() + 1, d.frontZ());
    }

    private static boolean framed(DoorTerrain t, int x, int y, int z) {
        return t.wall(x, y, z) && !t.gate(x, y, z);
    }

    private static DoorTerrain terrainOf(FloorLayout layout) {
        return new DoorTerrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return layout.blockAt(x, y, z).isPresent();
            }

            @Override
            public boolean wall(int x, int y, int z) {
                return layout.isProtected(x, y, z);
            }

            @Override
            public boolean gate(int x, int y, int z) {
                return layout.isGate(x, y, z);
            }

            @Override
            public boolean stairwell(int x, int y, int z) {
                return layout.isStairwell(x, y, z);
            }
        };
    }
}
