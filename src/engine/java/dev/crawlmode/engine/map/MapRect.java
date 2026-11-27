package dev.crawlmode.engine.map;

public record MapRect(int fromX, int fromZ, int toX, int toZ) {
    public MapRect {
        if (toX < fromX || toZ < fromZ) {
            throw new IllegalArgumentException("A rectangle cannot end before it starts: "
                    + fromX + "," + fromZ + ".." + toX + "," + toZ);
        }
    }
}
