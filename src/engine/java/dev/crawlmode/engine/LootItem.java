package dev.crawlmode.engine;

import java.util.Map;

public record LootItem(Kind kind, String id, int count, Map<String, Integer> enchants) {
    public enum Kind { GEAR, FOOD, AID }
}
