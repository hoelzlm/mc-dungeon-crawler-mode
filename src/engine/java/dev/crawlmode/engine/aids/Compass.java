package dev.crawlmode.engine.aids;

public enum Compass {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

    public static Compass of(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.round(degrees / 45.0);
        return values()[Math.floorMod(sector, 8)];
    }
}
