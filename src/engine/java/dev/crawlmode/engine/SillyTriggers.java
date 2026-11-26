package dev.crawlmode.engine;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SillyTriggers {
    private static final Map<String, String> DEATHS = Map.ofEntries(
            Map.entry("minecraft:lava", "death.lava"),
            Map.entry("minecraft:drown", "death.drown"),
            Map.entry("minecraft:cactus", "death.cactus"),
            Map.entry("minecraft:on_fire", "death.fire"),
            Map.entry("minecraft:in_fire", "death.fire"),
            Map.entry("minecraft:explosion", "death.explosion"),
            Map.entry("minecraft:player_explosion", "death.explosion"),
            Map.entry("minecraft:starve", "death.starve"),
            Map.entry("minecraft:out_of_world", "death.void"),
            Map.entry("minecraft:lightning_bolt", "death.lightning"),
            Map.entry("minecraft:falling_anvil", "death.anvil"),
            Map.entry("minecraft:freeze", "death.freeze"),
            Map.entry("minecraft:sweet_berry_bush", "death.berry-bush"),
            Map.entry("minecraft:fly_into_wall", "death.wall"),
            Map.entry("minecraft:in_wall", "death.wall"));

    private static final Map<String, String> KILLS = Map.of(
            "minecraft:chicken", "kill.chicken",
            "minecraft:sheep", "kill.sheep",
            "minecraft:cat", "kill.cat",
            "minecraft:bat", "kill.bat",
            "minecraft:pig", "kill.pig");

    private static final Map<String, String> EATS = Map.of(
            "minecraft:rotten_flesh", "eat.rotten-flesh",
            "minecraft:cake", "eat.cake",
            "minecraft:pufferfish", "eat.pufferfish");

    private static final Map<String, List<Integer>> MILESTONES = Map.of(
            "jump", List.of(100, 1000),
            "kill.grunt", List.of(10),
            "kill.any", List.of(50));

    private SillyTriggers() {}

    public static Optional<String> death(String damageTypeId) {
        return Optional.ofNullable(DEATHS.get(damageTypeId));
    }

    public static Optional<String> kill(String entityTypeId) {
        return Optional.ofNullable(KILLS.get(entityTypeId));
    }

    public static Optional<String> eat(String itemId) {
        return Optional.ofNullable(EATS.get(itemId));
    }

    public static Set<String> allTriggers() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(DEATHS.values());
        all.addAll(KILLS.values());
        all.addAll(EATS.values());
        MILESTONES.forEach((kind, counts) -> counts.forEach(n -> all.add(kind + "." + n)));
        return all;
    }

    public static final class Tally {
        private final Map<UUID, Map<String, Integer>> counts = new HashMap<>();

        public List<String> count(UUID crawler, String kind) {
            int n = counts.computeIfAbsent(crawler, c -> new HashMap<>()).merge(kind, 1, Integer::sum);
            return MILESTONES.getOrDefault(kind, List.of()).contains(n) ? List.of(kind + "." + n) : List.of();
        }
    }
}
