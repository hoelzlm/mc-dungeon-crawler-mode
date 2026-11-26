package dev.crawlmode.engine.boss;

import dev.crawlmode.engine.LootTier;
import dev.crawlmode.engine.faction.Faction;
import dev.crawlmode.engine.faction.Factions;
import dev.crawlmode.engine.faction.GearSlot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Bosses {
    private final Map<String, BossDef> byFaction;

    private Bosses(Map<String, BossDef> byFaction) {
        this.byFaction = byFaction;
    }

    public static Bosses load(Reader reader, Factions factions) {
        Map<String, BossDef> defs = new LinkedHashMap<>();
        try (BufferedReader in = new BufferedReader(reader)) {
            String raw;
            int number = 0;
            while ((raw = in.readLine()) != null) {
                number++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    BossDef def = parse(line, factions);
                    if (defs.put(def.factionId(), def) != null) {
                        throw new BossFormatException("duplicate boss for '" + def.factionId() + "'");
                    }
                } catch (BossFormatException e) {
                    throw new BossFormatException("line " + number + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        validate(defs, factions);
        return new Bosses(Map.copyOf(defs));
    }

    private static void validate(Map<String, BossDef> defs, Factions factions) {
        for (int floor = 1; floor <= 99; floor++) {
            List<Faction> onFloor = factions.forFloor(floor);
            if (onFloor.isEmpty()) {
                if (floor > 3) {
                    break;
                }
                continue;
            }
            int revealing = 0;
            for (Faction f : onFloor) {
                BossDef def = defs.get(f.id());
                if (def == null) {
                    throw new BossFormatException(f.id() + ": missing boss");
                }
                if (def.revealsStairwell()) {
                    revealing++;
                }
            }
            if (revealing == 0) {
                throw new BossFormatException("floor " + floor + ": at least one boss must reveal a Stairwell");
            }
            if (revealing * 2 >= onFloor.size()) {
                throw new BossFormatException("floor " + floor + ": most bosses must not reveal a Stairwell");
            }
        }
    }

    private static BossDef parse(String line, Factions factions) {
        List<String> tokens = tokenize(line);
        if (tokens.size() < 2 || !tokens.get(0).equals("boss")) {
            throw new BossFormatException("expected 'boss <faction> ...'");
        }
        String factionId = tokens.get(1);
        if (factions.faction(factionId) == null) {
            throw new BossFormatException("unknown faction '" + factionId + "'");
        }
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 2; i < tokens.size(); i++) {
            String[] kv = tokens.get(i).split("=", 2);
            if (kv.length != 2) {
                throw new BossFormatException("bad option '" + tokens.get(i) + "'");
            }
            opts.put(kv[0], kv[1]);
        }
        String entity = required(opts, "entity");
        if (!isId(entity)) {
            throw new BossFormatException("entity must be a namespaced id like minecraft:ravager");
        }
        LootTier tier;
        try {
            tier = LootTier.valueOf(required(opts, "tier").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BossFormatException("unknown tier '" + opts.get("tier") + "'");
        }
        if (tier != LootTier.GOLD && tier != LootTier.RADIANT) {
            throw new BossFormatException("a boss rewards a gold or radiant Loot Box");
        }
        Map<GearSlot, String> gear = new EnumMap<>(GearSlot.class);
        for (GearSlot slot : GearSlot.values()) {
            String item = opts.get(slot.key());
            if (item != null) {
                if (!isId(item)) {
                    throw new BossFormatException(slot.key() + " must be a namespaced item id");
                }
                gear.put(slot, item);
            }
        }
        boolean reveals = Boolean.parseBoolean(opts.getOrDefault("reveals", "false"));
        return new BossDef(factionId, entity, required(opts, "name"), positive(opts, "health"),
                positive(opts, "damage"), tier, reveals, Map.copyOf(gear));
    }

    private static String required(Map<String, String> opts, String key) {
        String v = opts.get(key);
        if (v == null) {
            throw new BossFormatException("missing '" + key + "'");
        }
        return v;
    }

    private static int positive(Map<String, String> opts, String key) {
        try {
            int n = Integer.parseInt(required(opts, key));
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new BossFormatException(key + " must be a whole number of at least 1");
    }

    private static boolean isId(String s) {
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (quoted) {
            throw new BossFormatException("unterminated quote");
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    public BossDef of(String factionId) {
        return byFaction.get(factionId);
    }
}
