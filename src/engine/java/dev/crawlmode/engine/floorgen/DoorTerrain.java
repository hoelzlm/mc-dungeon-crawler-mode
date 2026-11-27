package dev.crawlmode.engine.floorgen;

public interface DoorTerrain {
    boolean solid(int x, int y, int z);

    boolean wall(int x, int y, int z);

    boolean stairwell(int x, int y, int z);
}
