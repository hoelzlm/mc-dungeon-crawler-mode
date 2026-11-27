package dev.crawlmode.client;

import dev.crawlmode.engine.map.MapSpan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class FloorMapView {
    static final int UNSAMPLED = 0;

    private final Long2IntOpenHashMap colours = new Long2IntOpenHashMap();
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
            this.floor = floor;
            this.dimension = dimension;
        }
        for (MapSpan span : spans) {
            for (int x = span.fromX(); x <= span.toX(); x++) {
                colours.putIfAbsent(pack(x, span.z()), UNSAMPLED);
            }
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
        floor = -1;
        dimension = Level.OVERWORLD;
    }
}
