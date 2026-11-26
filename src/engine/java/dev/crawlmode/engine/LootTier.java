package dev.crawlmode.engine;

import java.util.Locale;

public enum LootTier {
    COPPER,
    SILVER,
    GOLD,
    RADIANT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
