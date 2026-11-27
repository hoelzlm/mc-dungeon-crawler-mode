package dev.crawlmode.engine.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FloorMap {
    private final int floor;
    private final Set<MapCell> revealed = new HashSet<>();
    private final Set<MapCell> shownDoors = new HashSet<>();
    private MapCell lastCentre;
    private List<MapDot> shownDots = List.of();

    public FloorMap(int floor) {
        this.floor = floor;
    }

    public int floor() {
        return floor;
    }

    public Set<MapCell> revealed() {
        return Set.copyOf(revealed);
    }

    public List<MapCell> shownDoors() {
        return List.copyOf(shownDoors);
    }

    public List<MapCell> newlyVisibleDoors(Collection<MapCell> doors) {
        List<MapCell> visible = new ArrayList<>();
        for (MapCell door : doors) {
            if (revealed.contains(door) && shownDoors.add(door)) {
                visible.add(door);
            }
        }
        return visible;
    }

    public List<MapDot> shownDots() {
        return shownDots;
    }

    public boolean isRevealed(int x, int z) {
        return revealed.contains(new MapCell(x, z));
    }

    public boolean showDots(List<MapDot> dots) {
        if (dots.equals(shownDots)) {
            return false;
        }
        shownDots = List.copyOf(dots);
        return true;
    }

    public List<MapSpan> revealAround(int x, int z, int radius) {
        MapCell centre = new MapCell(x, z);
        if (centre.equals(lastCentre)) {
            return List.of();
        }
        lastCentre = centre;
        List<MapSpan> spans = new ArrayList<>();
        for (int row = z - radius; row <= z + radius; row++) {
            int dz = row - z;
            int reach = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dz * dz));
            boolean open = false;
            int start = 0;
            for (int column = x - reach; column <= x + reach; column++) {
                boolean added = revealed.add(new MapCell(column, row));
                if (added && !open) {
                    open = true;
                    start = column;
                } else if (!added && open) {
                    spans.add(new MapSpan(row, start, column - 1));
                    open = false;
                }
            }
            if (open) {
                spans.add(new MapSpan(row, start, x + reach));
            }
        }
        return spans;
    }

    public List<MapSpan> spans() {
        List<MapCell> cells = new ArrayList<>(revealed);
        cells.sort(Comparator.comparingInt(MapCell::z).thenComparingInt(MapCell::x));
        List<MapSpan> spans = new ArrayList<>();
        int i = 0;
        while (i < cells.size()) {
            MapCell first = cells.get(i);
            int last = first.x();
            i++;
            while (i < cells.size() && cells.get(i).z() == first.z() && cells.get(i).x() == last + 1) {
                last++;
                i++;
            }
            spans.add(new MapSpan(first.z(), first.x(), last));
        }
        return spans;
    }
}
