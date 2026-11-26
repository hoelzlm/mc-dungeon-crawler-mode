package dev.crawlmode.engine.floorgen;

public record CellPos(int x, int z) {
    public static CellPos containing(int blockX, int blockZ) {
        return new CellPos(Math.floorDiv(blockX, FloorTemplate.SIZE), Math.floorDiv(blockZ, FloorTemplate.SIZE));
    }

    public int originX() {
        return x * FloorTemplate.SIZE;
    }

    public int originZ() {
        return z * FloorTemplate.SIZE;
    }
}
