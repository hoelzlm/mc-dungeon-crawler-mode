package dev.crawlmode.engine;

public record Achievement(String id, String trigger, LootTier reward, String hostLine) {
    public HostLine line() {
        return new HostLine("achievement." + id, hostLine);
    }
}
