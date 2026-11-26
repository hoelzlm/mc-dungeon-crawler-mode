package dev.crawlmode.engine.floorgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class VillageRuinsLayout {
    public static final int RUIN_AREAS = 3;
    public static final int STAIRWELLS = 3;
    private static final int LATTICE = 7;
    private static final int GATE_STEP = 3;

    private VillageRuinsLayout() {}

    public static FloorLayout generate(long seed, TemplateLibrary library) {
        Random rnd = new Random(seed ^ 0x7111A6E5L);
        int areas = 1 + RUIN_AREAS;

        List<CellPos> centres = new ArrayList<>(List.of(new CellPos(0, 0)));
        List<int[]> links = new ArrayList<>();
        while (centres.size() < areas) {
            int parent = rnd.nextInt(centres.size());
            Side side = Side.values()[rnd.nextInt(4)];
            CellPos p = centres.get(parent);
            CellPos c = new CellPos(p.x() + side.dx * LATTICE, p.z() + side.dz * LATTICE);
            if (!centres.contains(c)) {
                centres.add(c);
                links.add(new int[] {parent, side.ordinal()});
            }
        }

        Map<CellPos, Integer> areaOf = new LinkedHashMap<>();
        Map<CellPos, Set<Side>> open = new LinkedHashMap<>();
        for (int a = 0; a < centres.size(); a++) {
            CellPos c = centres.get(a);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    areaOf.put(new CellPos(c.x() + dx, c.z() + dz), a);
                }
            }
            if (a == 0) {
                openVillage(c, open);
            } else {
                openRuin(c, open, rnd);
            }
        }
        List<Gate> gates = new ArrayList<>();
        for (int li = 0; li < links.size(); li++) {
            int[] link = links.get(li);
            Side side = Side.values()[link[1]];
            CellPos p = centres.get(link[0]);
            if (link[0] > 0) {
                gates.add(new Gate(li + 1, link[0], step(p, side, GATE_STEP), side));
            }
            CellPos prev = step(p, side, 1);
            for (int i = 2; i <= LATTICE - 1; i++) {
                CellPos next = step(p, side, i);
                if (i < LATTICE - 1) {
                    areaOf.put(next, -1);
                }
                join(open, prev, side);
                prev = next;
            }
        }

        Set<CellPos> wellCells = pickStairwellCells(areaOf, centres, rnd);

        Map<CellPos, Cell> cells = new LinkedHashMap<>();
        List<Stairwell> wells = new ArrayList<>();
        for (Map.Entry<CellPos, Integer> e : areaOf.entrySet()) {
            CellPos pos = e.getKey();
            int area = e.getValue();
            FloorTemplate t;
            if (area < 0) {
                t = pick(library.corridors(), rnd);
            } else if (wellCells.contains(pos)) {
                t = pick(library.stairwellRooms(), rnd);
                FloorTemplate.Marker m = t.stairwellMarker().orElseThrow();
                wells.add(new Stairwell(pos, pos.x() * 16 + m.x(), FloorLayout.FLOOR_Y + m.y(), pos.z() * 16 + m.z()));
            } else if (area == 0 || pos.equals(centres.get(area))) {
                t = pick(library.villages(), rnd);
            } else {
                t = pick(library.rooms(), rnd);
            }
            Set<Side> sides = open.getOrDefault(pos, EnumSet.noneOf(Side.class));
            cells.put(pos, new Cell(pos, area, Collections.unmodifiableSet(EnumSet.copyOf(
                    sides.isEmpty() ? EnumSet.noneOf(Side.class) : sides)), t));
        }
        return FloorLayout.assemble(areas, cells, wells, gates);
    }

    private static Set<CellPos> pickStairwellCells(Map<CellPos, Integer> areaOf, List<CellPos> centres, Random rnd) {
        List<CellPos> start = new ArrayList<>();
        List<CellPos> ruins = new ArrayList<>();
        for (Map.Entry<CellPos, Integer> e : areaOf.entrySet()) {
            int area = e.getValue();
            if (area == 0 && !e.getKey().equals(centres.get(0))) {
                start.add(e.getKey());
            } else if (area > 0 && !e.getKey().equals(centres.get(area))) {
                ruins.add(e.getKey());
            }
        }
        Collections.shuffle(start, rnd);
        Collections.shuffle(ruins, rnd);
        Set<CellPos> chosen = new HashSet<>();
        chosen.add(start.get(0));
        chosen.addAll(ruins.subList(0, STAIRWELLS - 1));
        return chosen;
    }

    private static FloorTemplate pick(List<FloorTemplate> from, Random rnd) {
        return from.get(rnd.nextInt(from.size()));
    }

    private static CellPos step(CellPos p, Side side, int n) {
        return new CellPos(p.x() + side.dx * n, p.z() + side.dz * n);
    }

    private static void join(Map<CellPos, Set<Side>> open, CellPos from, Side side) {
        open.computeIfAbsent(from, k -> EnumSet.noneOf(Side.class)).add(side);
        CellPos to = new CellPos(from.x() + side.dx, from.z() + side.dz);
        open.computeIfAbsent(to, k -> EnumSet.noneOf(Side.class)).add(side.opposite());
    }

    private static void openVillage(CellPos centre, Map<CellPos, Set<Side>> open) {
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                CellPos p = new CellPos(centre.x() + dx, centre.z() + dz);
                if (dx < 1) {
                    join(open, p, Side.EAST);
                }
                if (dz < 1) {
                    join(open, p, Side.SOUTH);
                }
            }
        }
    }

    private static void openRuin(CellPos centre, Map<CellPos, Set<Side>> open, Random rnd) {
        for (Side s : Side.values()) {
            join(open, centre, s);
        }
        for (int dz = -1; dz <= 1; dz += 2) {
            for (int dx = -1; dx <= 1; dx += 2) {
                CellPos corner = new CellPos(centre.x() + dx, centre.z() + dz);
                join(open, corner, rnd.nextBoolean() ? (dx < 0 ? Side.EAST : Side.WEST) : (dz < 0 ? Side.SOUTH : Side.NORTH));
            }
        }
        List<CellPos> ring = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    ring.add(new CellPos(centre.x() + dx, centre.z() + dz));
                }
            }
        }
        for (CellPos p : ring) {
            for (Side s : new Side[] {Side.EAST, Side.SOUTH}) {
                CellPos q = new CellPos(p.x() + s.dx, p.z() + s.dz);
                boolean inRing = ring.contains(q);
                if (inRing && rnd.nextInt(3) == 0) {
                    join(open, p, s);
                }
            }
        }
    }
}
