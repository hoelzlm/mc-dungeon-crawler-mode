package dev.crawlmode.engine;

import java.util.Map;

/**
 * One reward: vanilla gear (with enchants, possibly custom ones), food, or a Stairwell Aid. Ids
 * are namespaced item ids, except aids, which use the bare aid name.
 */
public record LootItem(Kind kind, String id, int count, Map<String, Integer> enchants) {
    public enum Kind { GEAR, FOOD, AID }
}
