package dev.crawlmode.engine.faction;

/** A mob to place at a world position when its cell is built; y is the first air block above the floor. */
public record PlannedSpawn(MobDef mob, int x, int y, int z) {}
