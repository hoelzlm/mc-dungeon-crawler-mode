package dev.crawlmode.engine.aids;

/** Eight compass points; north is towards negative z, east towards positive x. */
public enum Compass {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

    /** The compass point whose 45 degree sector contains the direction {@code (dx, dz)}. */
    public static Compass of(int dx, int dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.round(degrees / 45.0);
        return values()[Math.floorMod(sector, 8)];
    }
}
