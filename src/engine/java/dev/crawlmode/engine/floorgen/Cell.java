package dev.crawlmode.engine.floorgen;

import java.util.Set;

/** One placed cell: which area it belongs to (-1 for corridors), which sides lead on, and its template. */
public record Cell(CellPos pos, int area, Set<Side> open, FloorTemplate template) {}
