package dev.crawlmode.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record SafeRoomRules(int regenerationAmplifier, long buffetCooldownTicks, List<LootItem> food) {
    public SafeRoomRules {
        if (regenerationAmplifier < 0 || buffetCooldownTicks < 0 || food.isEmpty()) {
            throw new SafeRoomRulesFormatException("invalid Safe Room rules");
        }
        food = List.copyOf(food);
    }

    public static SafeRoomRules load(Reader reader) {
        Integer regeneration = null;
        Long cooldown = null;
        List<LootItem> food = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String raw;
            int number = 0;
            while ((raw = in.readLine()) != null) {
                number++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                String key = parts[0].strip();
                try {
                    switch (key) {
                        case "regeneration" -> regeneration = (int) number(parts, 2);
                        case "buffet-cooldown-ticks" -> cooldown = number(parts, 2);
                        case "food" -> food.add(foodItem(parts));
                        default -> throw new SafeRoomRulesFormatException("unknown key '" + key + "'");
                    }
                } catch (SafeRoomRulesFormatException e) {
                    throw new SafeRoomRulesFormatException("line " + number + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (regeneration == null || cooldown == null) {
            throw new SafeRoomRulesFormatException("missing 'regeneration' or 'buffet-cooldown-ticks'");
        }
        if (food.isEmpty()) {
            throw new SafeRoomRulesFormatException("no food entries");
        }
        return new SafeRoomRules(regeneration, cooldown, food);
    }

    private static long number(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new SafeRoomRulesFormatException("expected " + expected + " fields");
        }
        try {
            long value = Long.parseLong(parts[expected - 1].strip());
            if (value < 0) {
                throw new SafeRoomRulesFormatException("must not be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new SafeRoomRulesFormatException("not a number '" + parts[expected - 1].strip() + "'");
        }
    }

    private static LootItem foodItem(String[] parts) {
        if (parts.length != 3) {
            throw new SafeRoomRulesFormatException("expected 'food | id | count'");
        }
        int count = (int) number(new String[] {parts[0], parts[2]}, 2);
        String id = parts[1].strip();
        if (count < 1 || id.isEmpty()) {
            throw new SafeRoomRulesFormatException("expected 'food | id | count' with count of at least 1");
        }
        return new LootItem(LootItem.Kind.FOOD, id, count, Map.of());
    }
}
