package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.Optional;

/** Where one Boss stands (y is the first air block above the floor) and which Stairwell its death reveals, if any. */
public record BossPlacement(BossDef def, int area, int x, int y, int z, Optional<Stairwell> reveals) {}
