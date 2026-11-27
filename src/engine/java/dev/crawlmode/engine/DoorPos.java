package dev.crawlmode.engine;

import dev.crawlmode.engine.floorgen.CellPos;

public record DoorPos(int floor, int x, int y, int z, DoorFacing facing) {
    public CellPos cell() {
        return CellPos.containing(x, z);
    }

    public int frontX() {
        return x + facing.dx();
    }

    public int frontZ() {
        return z + facing.dz();
    }

    public int sideX(int side) {
        return x - side * facing.dz();
    }

    public int sideZ(int side) {
        return z + side * facing.dx();
    }

    public boolean occupies(int blockX, int blockY, int blockZ) {
        return x == blockX && z == blockZ && (y == blockY || y + 1 == blockY);
    }
}
