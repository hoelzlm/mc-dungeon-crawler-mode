package dev.crawlmode.engine.floorgen;

import java.util.Set;

public record Cell(CellPos pos, int area, Set<Side> open, FloorTemplate template) {}
