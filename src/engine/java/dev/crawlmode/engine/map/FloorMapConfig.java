package dev.crawlmode.engine.map;

public record FloorMapConfig(int revealRadius, int dotRadius) {
    public static final int DEFAULT_REVEAL_RADIUS = 24;
    public static final int DEFAULT_DOT_RADIUS = 24;
    public static final FloorMapConfig DEFAULT = new FloorMapConfig(DEFAULT_REVEAL_RADIUS, DEFAULT_DOT_RADIUS);

    public FloorMapConfig {
        if (revealRadius < 1) {
            throw new IllegalArgumentException("The reveal radius must be at least 1: " + revealRadius);
        }
        if (dotRadius < 1) {
            throw new IllegalArgumentException("The dot radius must be at least 1: " + dotRadius);
        }
    }

    public FloorMapConfig(int revealRadius) {
        this(revealRadius, DEFAULT_DOT_RADIUS);
    }
}
