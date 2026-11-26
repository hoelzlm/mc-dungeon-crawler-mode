package dev.crawlmode.engine.floorgen;

/**
 * A gate across the corridor leading into {@code area} from {@code parentArea}. {@code cell} is the corridor cell
 * holding it and {@code side} the direction of travel into the area (the gate plane is across that direction).
 */
public record Gate(int area, int parentArea, CellPos cell, Side side) {}
