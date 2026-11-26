package dev.crawlmode.engine.floorgen;

public enum Side {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    Side(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Side opposite() {
        return values()[(ordinal() + 2) % 4];
    }
}
