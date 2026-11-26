package dev.crawlmode.engine.floorgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The plan of one Floor: connected areas of rooms joined by corridors, laid out on a grid of 16 by 16 block cells
 * from a seed, plus the hidden Stairwells. Pure and Minecraft-free: it answers "what block belongs at this
 * position?" so the adapter can build the Floor a cell at a time.
 *
 * Area 0 is the start area, a 3 by 3 block of rooms around cell (0, 0). Other areas sit 7 cells apart on a lattice and
 * are joined to an earlier area by a straight corridor of four cells.
 */
public final class FloorLayout {
    /** World y of the floor layer of every template. */
    public static final int FLOOR_Y = 60;

    public static final int DEFAULT_AREAS = 5;
    private static final int LATTICE = 7;
    /** Which corridor cell, counted from the parent area, holds a Boss gate. */
    private static final int GATE_STEP = 3;

    private final int areaCount;
    private final Map<CellPos, Cell> cells;
    private final List<Stairwell> stairwells;
    private final Set<Stairwell> stairwellSet;
    private final List<Gate> gates;

    private FloorLayout(int areaCount, Map<CellPos, Cell> cells, List<Stairwell> stairwells, List<Gate> gates) {
        this.gates = gates;
        this.areaCount = areaCount;
        this.cells = cells;
        this.stairwells = stairwells;
        this.stairwellSet = Set.copyOf(stairwells);
    }

    /** Assembles a plan built by another generator (for example {@link VillageRuinsLayout}). */
    static FloorLayout assemble(int areaCount, Map<CellPos, Cell> cells, List<Stairwell> stairwells, List<Gate> gates) {
        return new FloorLayout(areaCount, Collections.unmodifiableMap(cells), List.copyOf(stairwells), List.copyOf(gates));
    }

    public static FloorLayout generate(long seed, TemplateLibrary library) {
        return generate(seed, library, DEFAULT_AREAS);
    }

    public static FloorLayout generate(long seed, TemplateLibrary library, int areas) {
        return generate(seed, library, areas, 5, 6);
    }

    /** A library with village templates builds a village-and-ruins Floor (see {@link VillageRuinsLayout}). */
    public static FloorLayout generate(long seed, TemplateLibrary library, FloorSpec spec) {
        if (!library.villages().isEmpty()) {
            return VillageRuinsLayout.generate(seed, library);
        }
        return generate(seed, library, spec.areas(), spec.minStairwells(), spec.maxStairwells());
    }

    /** @param minWells fewest Stairwells hidden on the Floor; {@code maxWells} the most (inclusive). */
    public static FloorLayout generate(long seed, TemplateLibrary library, int areas, int minWells, int maxWells) {
        Random rnd = new Random(seed);

        // Area centres on a lattice, each joined to an earlier one.
        List<CellPos> centres = new ArrayList<>(List.of(new CellPos(0, 0)));
        List<int[]> links = new ArrayList<>(); // {parent index, side ordinal}
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
        Map<CellPos, Set<Side>> open = new HashMap<>();
        for (int a = 0; a < centres.size(); a++) {
            CellPos c = centres.get(a);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    areaOf.put(new CellPos(c.x() + dx, c.z() + dz), a);
                }
            }
            connectRooms(c, open, rnd);
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

        // Stairwell rooms: one in the start area (not the spawn cell), the rest anywhere else.
        int wellCount = minWells + rnd.nextInt(maxWells - minWells + 1);
        List<CellPos> startCandidates = new ArrayList<>();
        List<CellPos> otherCandidates = new ArrayList<>();
        for (Map.Entry<CellPos, Integer> e : areaOf.entrySet()) {
            if (e.getValue() == 0 && !e.getKey().equals(new CellPos(0, 0))) {
                startCandidates.add(e.getKey());
            } else if (e.getValue() > 0) {
                otherCandidates.add(e.getKey());
            }
        }
        Collections.shuffle(startCandidates, rnd);
        Collections.shuffle(otherCandidates, rnd);
        Set<CellPos> wellCells = new HashSet<>();
        wellCells.add(startCandidates.get(0));
        wellCells.addAll(otherCandidates.subList(0, wellCount - 1));

        Map<CellPos, Cell> cells = new LinkedHashMap<>();
        List<Stairwell> wells = new ArrayList<>();
        List<FloorTemplate> rooms = library.rooms();
        List<FloorTemplate> corridors = library.corridors();
        List<FloorTemplate> wellRooms = library.stairwellRooms();
        for (Map.Entry<CellPos, Integer> e : areaOf.entrySet()) {
            CellPos pos = e.getKey();
            Set<Side> sides = open.getOrDefault(pos, EnumSet.noneOf(Side.class));
            FloorTemplate t;
            if (e.getValue() < 0) {
                t = corridors.get(rnd.nextInt(corridors.size()));
            } else if (wellCells.contains(pos)) {
                t = wellRooms.get(rnd.nextInt(wellRooms.size()));
                FloorTemplate.Marker m = t.stairwellMarker().orElseThrow();
                wells.add(new Stairwell(pos, pos.x() * 16 + m.x(), FLOOR_Y + m.y(), pos.z() * 16 + m.z()));
            } else {
                t = rooms.get(rnd.nextInt(rooms.size()));
            }
            cells.put(pos, new Cell(pos, e.getValue(), Collections.unmodifiableSet(EnumSet.copyOf(
                    sides.isEmpty() ? EnumSet.noneOf(Side.class) : sides)), t));
        }
        return new FloorLayout(centres.size(), Collections.unmodifiableMap(cells), List.copyOf(wells), List.copyOf(gates));
    }

    private static CellPos step(CellPos p, Side side, int n) {
        return new CellPos(p.x() + side.dx * n, p.z() + side.dz * n);
    }

    private static void join(Map<CellPos, Set<Side>> open, CellPos from, Side side) {
        open.computeIfAbsent(from, k -> EnumSet.noneOf(Side.class)).add(side);
        CellPos to = new CellPos(from.x() + side.dx, from.z() + side.dz);
        open.computeIfAbsent(to, k -> EnumSet.noneOf(Side.class)).add(side.opposite());
    }

    /** Join the nine rooms of an area: a random spanning tree plus two extra doors, so there are loops. */
    private static void connectRooms(CellPos centre, Map<CellPos, Set<Side>> open, Random rnd) {
        record Edge(CellPos from, Side side) {}
        List<Edge> edges = new ArrayList<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                CellPos p = new CellPos(centre.x() + dx, centre.z() + dz);
                if (dx < 1) {
                    edges.add(new Edge(p, Side.EAST));
                }
                if (dz < 1) {
                    edges.add(new Edge(p, Side.SOUTH));
                }
            }
        }
        Collections.shuffle(edges, rnd);
        Map<CellPos, CellPos> parent = new HashMap<>();
        int extra = 2;
        for (Edge e : edges) {
            CellPos a = root(parent, e.from());
            CellPos b = root(parent, new CellPos(e.from().x() + e.side().dx, e.from().z() + e.side().dz));
            if (!a.equals(b)) {
                parent.put(a, b);
                join(open, e.from(), e.side());
            } else if (extra > 0) {
                extra--;
                join(open, e.from(), e.side());
            }
        }
    }

    private static CellPos root(Map<CellPos, CellPos> parent, CellPos p) {
        while (parent.containsKey(p)) {
            p = parent.get(p);
        }
        return p;
    }

    public int areaCount() {
        return areaCount;
    }

    public List<Cell> cells() {
        return List.copyOf(cells.values());
    }

    public Optional<Cell> cell(CellPos pos) {
        return Optional.ofNullable(cells.get(pos));
    }

    /** Gates on the corridors into areas that do not touch the start area. Ordered by area. */
    public List<Gate> gates() {
        return gates;
    }

    public List<Stairwell> stairwells() {
        return stairwells;
    }

    public boolean isStairwell(int x, int y, int z) {
        return stairwellSet.contains(new Stairwell(new CellPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), x, y, z));
    }

    /** Where Crawlers arrive: the middle of the start area's centre room. */
    public SpawnPoint spawn() {
        return new SpawnPoint(8, FLOOR_Y + 1, 8);
    }

    /** The structure block at a world position, or empty for air or anywhere outside the planned cells. */
    public Optional<TemplateBlock> blockAt(int x, int y, int z) {
        Cell cell = cells.get(new CellPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (cell == null) {
            return Optional.empty();
        }
        return cell.template().blockAt(Math.floorMod(x, 16), y - FLOOR_Y, Math.floorMod(z, 16), cell.open());
    }

    /** Structure blocks cannot be broken. Secret wall blocks, air and anything a player placed outside it can. */
    public boolean isProtected(int x, int y, int z) {
        return blockAt(x, y, z).map(b -> !b.secret()).orElse(false);
    }
}
