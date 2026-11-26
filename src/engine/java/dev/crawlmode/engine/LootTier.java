package dev.crawlmode.engine;

import java.util.Locale;

/** Loot Box tiers, weakest first. */
public enum LootTier {
    COPPER,
    SILVER,
    GOLD,
    RADIANT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
