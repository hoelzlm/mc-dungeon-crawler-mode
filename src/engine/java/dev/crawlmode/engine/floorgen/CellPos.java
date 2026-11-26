package dev.crawlmode.engine.floorgen;

/** Position of a 16 by 16 cell on the Floor's grid; cell (0, 0) covers blocks x 0..15, z 0..15. */
public record CellPos(int x, int z) {}
