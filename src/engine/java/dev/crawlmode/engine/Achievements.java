package dev.crawlmode.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Achievements {
    private final Map<String, Achievement> byId;
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();

    private Achievements(Map<String, Achievement> byId) {
        this.byId = byId;
    }

    public static Achievements load(Reader reader) {
        Map<String, Achievement> byId = new LinkedHashMap<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String raw;
            int number = 0;
            while ((raw = in.readLine()) != null) {
                number++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|", 4);
                if (parts.length != 4) {
                    throw new AchievementFormatException("line " + number + ": expected 'id | trigger | tier | text'");
                }
                String id = parts[0].strip();
                String trigger = parts[1].strip();
                String text = parts[3].strip();
                if (id.isEmpty() || trigger.isEmpty() || text.isEmpty()) {
                    throw new AchievementFormatException("line " + number + ": empty field");
                }
                LootTier tier;
                try {
                    tier = LootTier.valueOf(parts[2].strip().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new AchievementFormatException("line " + number + ": unknown tier '" + parts[2].strip() + "'");
                }
                if (byId.putIfAbsent(id, new Achievement(id, trigger, tier, text)) != null) {
                    throw new AchievementFormatException("line " + number + ": duplicate id '" + id + "'");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Achievements(byId);
    }

    public List<Achievement> all() {
        return List.copyOf(byId.values());
    }

    List<Achievement> unlock(UUID crawler, String trigger) {
        List<Achievement> fresh = new ArrayList<>();
        Set<String> have = unlocked.computeIfAbsent(crawler, c -> new HashSet<>());
        for (Achievement a : byId.values()) {
            if (a.trigger().equals(trigger) && have.add(a.id())) {
                fresh.add(a);
            }
        }
        return fresh;
    }
}
