package dev.crawlmode.engine.faction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data-driven Faction definitions. Lines (blank and {@code #} lines ignored; values may be double-quoted):
 * <pre>
 * faction goblins floor=1 name="Marble Goblins"
 * mob goblins grunt entity=minecraft:zombie name="Hall Goblin" health=14 damage=3 weight=60 mainhand=minecraft:stone_sword
 * mob goblins shaman entity=minecraft:witch name="Hexer" health=24 damage=2 weight=10 ability=heal_allies
 * </pre>
 * Validated on load: every Faction has a Grunt, Elite and Shaman, spawn weights fall strictly from Grunt to
 * Elite to Shaman, stats are positive, a Shaman has at least one known ability, and every Floor has three or more
 * Factions.
 */
public final class Factions {
    public static final int MIN_FACTIONS_PER_FLOOR = 3;

    private final Map<String, Faction> byId;

    private Factions(Map<String, Faction> byId) {
        this.byId = byId;
    }

    private record Draft(String id, String name, int floor, Map<Rank, MobDef> mobs) {}

    public static Factions load(Reader reader) {
        Map<String, Draft> drafts = new LinkedHashMap<>();
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
                    parseLine(line, drafts);
                } catch (FactionFormatException e) {
                    throw new FactionFormatException("line " + number + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Faction> result = new LinkedHashMap<>();
        Map<Integer, Integer> perFloor = new LinkedHashMap<>();
        for (Draft d : drafts.values()) {
            for (Rank rank : Rank.values()) {
                if (!d.mobs().containsKey(rank)) {
                    throw new FactionFormatException(d.id() + ": missing " + rank.key());
                }
            }
            int g = d.mobs().get(Rank.GRUNT).weight();
            int e = d.mobs().get(Rank.ELITE).weight();
            int s = d.mobs().get(Rank.SHAMAN).weight();
            if (!(g > e && e > s)) {
                throw new FactionFormatException(d.id() + ": each rank must be rarer than the one before (weights grunt > elite > shaman)");
            }
            result.put(d.id(), new Faction(d.id(), d.name(), d.floor(), Map.copyOf(d.mobs())));
            perFloor.merge(d.floor(), 1, Integer::sum);
        }
        perFloor.forEach((floor, n) -> {
            if (n < MIN_FACTIONS_PER_FLOOR) {
                throw new FactionFormatException("floor " + floor + " needs at least " + MIN_FACTIONS_PER_FLOOR + " factions");
            }
        });
        return new Factions(result);
    }

    private static void parseLine(String line, Map<String, Draft> drafts) {
        List<String> tokens = tokenize(line);
        if (tokens.size() < 2) {
            throw new FactionFormatException("expected 'faction <id> ...' or 'mob <faction> <rank> ...'");
        }
        switch (tokens.get(0)) {
            case "faction" -> {
                String id = tokens.get(1);
                if (drafts.containsKey(id)) {
                    throw new FactionFormatException("duplicate faction '" + id + "'");
                }
                Map<String, List<String>> opts = options(tokens, 2);
                int floor = number(single(opts, "floor"), "floor");
                drafts.put(id, new Draft(id, single(opts, "name"), floor, new EnumMap<>(Rank.class)));
            }
            case "mob" -> parseMob(tokens, drafts);
            default -> throw new FactionFormatException("unknown line type '" + tokens.get(0) + "'");
        }
    }

    private static void parseMob(List<String> tokens, Map<String, Draft> drafts) {
        if (tokens.size() < 3) {
            throw new FactionFormatException("expected 'mob <faction> <rank> ...'");
        }
        Draft draft = drafts.get(tokens.get(1));
        if (draft == null) {
            throw new FactionFormatException("unknown faction '" + tokens.get(1) + "'");
        }
        Rank rank = Rank.fromKey(tokens.get(2));
        if (rank == null) {
            throw new FactionFormatException("unknown rank '" + tokens.get(2) + "'");
        }
        if (draft.mobs().containsKey(rank)) {
            throw new FactionFormatException("duplicate " + rank.key() + " for " + draft.id());
        }
        Map<String, List<String>> opts = options(tokens, 3);
        String entity = single(opts, "entity");
        if (!isId(entity)) {
            throw new FactionFormatException("entity must be a namespaced id like minecraft:zombie");
        }
        Map<GearSlot, String> gear = new EnumMap<>(GearSlot.class);
        for (GearSlot slot : GearSlot.values()) {
            List<String> v = opts.get(slot.key());
            if (v != null) {
                if (!isId(v.get(0))) {
                    throw new FactionFormatException(slot.key() + " must be a namespaced item id");
                }
                gear.put(slot, v.get(0));
            }
        }
        List<Ability> abilities = new ArrayList<>();
        for (String key : opts.getOrDefault("ability", List.of())) {
            Ability a = Ability.fromKey(key);
            if (a == null) {
                throw new FactionFormatException("unknown ability '" + key + "'");
            }
            abilities.add(a);
        }
        if (rank == Rank.SHAMAN && abilities.isEmpty()) {
            throw new FactionFormatException("a shaman needs at least one ability");
        }
        draft.mobs().put(rank, new MobDef(draft.id(), rank, entity, single(opts, "name"),
                number(single(opts, "health"), "health"), number(single(opts, "damage"), "damage"),
                number(single(opts, "weight"), "weight"), Map.copyOf(gear), List.copyOf(abilities)));
    }

    private static boolean isId(String s) {
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static int number(String text, String what) {
        try {
            int n = Integer.parseInt(text);
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new FactionFormatException(what + " must be a whole number of at least 1");
    }

    private static String single(Map<String, List<String>> opts, String key) {
        List<String> v = opts.get(key);
        if (v == null) {
            throw new FactionFormatException("missing '" + key + "'");
        }
        return v.get(0);
    }

    private static Map<String, List<String>> options(List<String> tokens, int from) {
        Map<String, List<String>> opts = new LinkedHashMap<>();
        for (int i = from; i < tokens.size(); i++) {
            String[] kv = tokens.get(i).split("=", 2);
            if (kv.length != 2) {
                throw new FactionFormatException("bad option '" + tokens.get(i) + "'");
            }
            opts.computeIfAbsent(kv[0], k -> new ArrayList<>()).add(kv[1]);
        }
        return opts;
    }

    /** Splits on spaces; a double-quoted part (as in {@code name="Hall Goblin"}) may contain spaces. */
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
            throw new FactionFormatException("unterminated quote");
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    public List<Faction> forFloor(int floor) {
        return byId.values().stream().filter(f -> f.floor() == floor).toList();
    }

    public Faction faction(String id) {
        return byId.get(id);
    }
}
