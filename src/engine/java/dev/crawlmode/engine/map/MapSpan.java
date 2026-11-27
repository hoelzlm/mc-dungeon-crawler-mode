package dev.crawlmode.engine.map;

public record MapSpan(int z, int fromX, int toX) {
    public MapSpan {
        if (toX < fromX) {
            throw new IllegalArgumentException("A span cannot end before it starts: " + fromX + ".." + toX);
        }
    }
}
