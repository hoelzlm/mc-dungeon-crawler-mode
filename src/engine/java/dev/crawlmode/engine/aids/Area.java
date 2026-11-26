package dev.crawlmode.engine.aids;

/** A rectangle of blocks on the x/z plane, bounds inclusive. */
public record Area(int minX, int minZ, int maxX, int maxZ) {
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
