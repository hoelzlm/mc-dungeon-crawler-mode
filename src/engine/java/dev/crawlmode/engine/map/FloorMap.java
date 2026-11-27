package dev.crawlmode.engine.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FloorMap {
    private final int floor;
    private final Set<MapCell> revealed = new HashSet<>();
    private final Set<MapCell> shownDoors = new HashSet<>();
    private MapCell lastCentre;

    public FloorMap(int floor) {
        this.floor = floor;
    }

    public int floor() {
        return floor;
    }

    public Set<MapCell> revealed() {
        return Set.copyOf(revealed);
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
}
