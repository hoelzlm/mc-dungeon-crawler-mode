package dev.crawlmode.engine;

public enum DoorFacing {
    NORTH(0, -1, 180),
    EAST(1, 0, 270),
    SOUTH(0, 1, 0),
    WEST(-1, 0, 90);

    private final int dx;
    private final int dz;
    private final int yawDegrees;

    DoorFacing(int dx, int dz, int yawDegrees) {
        this.dx = dx;
        this.dz = dz;
        this.yawDegrees = yawDegrees;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public int yawDegrees() {
        return yawDegrees;
    }
}
