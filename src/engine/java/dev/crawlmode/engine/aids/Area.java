package dev.crawlmode.engine.aids;

public record Area(int minX, int minZ, int maxX, int maxZ) {
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
