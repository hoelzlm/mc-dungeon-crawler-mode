package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.floorgen.Stairwell;
import java.util.Optional;

public record BossPlacement(BossDef def, int area, int x, int y, int z, Optional<Stairwell> reveals) {}
