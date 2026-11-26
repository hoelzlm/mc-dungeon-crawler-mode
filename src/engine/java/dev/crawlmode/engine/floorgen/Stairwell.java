package dev.crawlmode.engine.floorgen;

/** A hidden Stairwell: the cell it sits in and the position of its marker block. */
public record Stairwell(CellPos cell, int x, int y, int z) {}
