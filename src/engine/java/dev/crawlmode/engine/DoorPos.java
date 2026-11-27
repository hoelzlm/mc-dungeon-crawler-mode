package dev.crawlmode.engine;

import dev.crawlmode.engine.floorgen.CellPos;

public record DoorPos(int floor, int x, int y, int z, DoorFacing facing) {
    public DoorPos(int floor, int x, int y, int z) {
        this(floor, x, y, z, DoorFacing.NORTH);
    }

    public CellPos cell() {
        return CellPos.containing(x, z);
    }

    public int frontX() {
        return x + facing.dx();
    }

    public int frontZ() {
        return z + facing.dz();
    }
}
