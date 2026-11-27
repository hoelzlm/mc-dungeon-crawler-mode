package dev.crawlmode.client;

import dev.crawlmode.engine.map.MapCell;
import dev.crawlmode.engine.map.MapDot;
import dev.crawlmode.engine.map.MapSpan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class FloorMapView {
    static final int UNSAMPLED = 0;

    private final Long2IntOpenHashMap colours = new Long2IntOpenHashMap();
    private final Set<MapCell> doors = new HashSet<>();
    private List<MapDot> dots = List.of();
    private int floor = -1;
    private ResourceKey<Level> dimension = Level.OVERWORLD;

    static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    boolean isEmpty() {
        return floor < 0;
    }

    ResourceKey<Level> dimension() {
        return dimension;
    }

    void reveal(int floor, ResourceKey<Level> dimension, List<MapSpan> spans) {
        if (floor != this.floor || !dimension.equals(this.dimension)) {
            colours.clear();
            doors.clear();
            dots = List.of();
            this.floor = floor;
            this.dimension = dimension;
        }
        for (MapSpan span : spans) {
            for (int x = span.fromX(); x <= span.toX(); x++) {
                colours.putIfAbsent(pack(x, span.z()), UNSAMPLED);
            }
        }
    }

    void showDoors(int floor, List<MapCell> shown) {
        if (floor == this.floor) {
            doors.addAll(shown);
        }
    }

    Set<MapCell> doors() {
        return doors;
    }

    List<MapDot> dots() {
        return dots;
    }

    void showDots(int floor, List<MapDot> dots) {
        if (floor == this.floor) {
            this.dots = List.copyOf(dots);
        }
    }

    boolean isRevealed(int x, int z) {
        return colours.containsKey(pack(x, z));
    }

    int colourAt(int x, int z) {
        return colours.get(pack(x, z));
    }

    void setColour(int x, int z, int argb) {
        colours.put(pack(x, z), argb);
    }

    void clear() {
        colours.clear();
        doors.clear();
        dots = List.of();
        floor = -1;
        dimension = Level.OVERWORLD;
    }
}
