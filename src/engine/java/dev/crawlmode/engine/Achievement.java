package dev.crawlmode.engine;

/**
 * One Achievement, defined in data: unlocked the first time a Crawler causes {@code trigger}; the
 * Host says {@code hostLine}, and a Loot Box of {@code reward} is granted.
 */
public record Achievement(String id, String trigger, LootTier reward, String hostLine) {
    public HostLine line() {
        return new HostLine("achievement." + id, hostLine);
    }
}
