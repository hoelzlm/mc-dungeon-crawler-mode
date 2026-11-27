package dev.crawlmode.engine.map;

public record FloorMapConfig(int revealRadius) {
    public static final int DEFAULT_REVEAL_RADIUS = 24;
    public static final FloorMapConfig DEFAULT = new FloorMapConfig(DEFAULT_REVEAL_RADIUS);

    public FloorMapConfig {
        if (revealRadius < 1) {
            throw new IllegalArgumentException("The reveal radius must be at least 1: " + revealRadius);
        }
    }
}
