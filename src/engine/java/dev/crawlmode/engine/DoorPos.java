package dev.crawlmode.engine;

import dev.crawlmode.engine.floorgen.CellPos;

public record DoorPos(int floor, int x, int y, int z) {
    public CellPos cell() {
        return CellPos.containing(x, z);
    }
}
