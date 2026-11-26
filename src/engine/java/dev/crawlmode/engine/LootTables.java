package dev.crawlmode.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Data-driven loot tables, one per {@link LootTier}. Lines (blank and {@code #} lines ignored):
 * <pre>
 * copper rolls 2
 * copper gear minecraft:iron_sword weight=3 enchant=crawlmode:blazing:1
 * copper food minecraft:bread weight=5 count=4
 * copper aid echo_chime weight=2
 * </pre>
 * Validated on load: every tier needs rolls (at least 1) and entries, weights are positive, aids
 * are real Stairwell Aids, and rolls never decrease from one tier to the next (tier scaling).
 */
public final class LootTables {
    public static final Set<String> STAIRWELL_AIDS =
            Set.of("seekers_draught", "echo_chime", "revealing_lantern", "fragment_map");

    private record Entry(LootItem item, int weight) {}

    private static final class Table {
        int rolls;
        final List<Entry> entries = new ArrayList<>();
    }

    private final Map<LootTier, Table> tables;

    private LootTables(Map<LootTier, Table> tables) {
        this.tables = tables;
    }

    public static LootTables load(Reader reader) {
        Map<LootTier, Table> tables = new EnumMap<>(LootTier.class);
        for (LootTier tier : LootTier.values()) {
            tables.put(tier, new Table());
        }
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
                    parseLine(line, tables);
                } catch (LootTableFormatException e) {
                    throw new LootTableFormatException("line " + number + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int previous = 0;
        for (LootTier tier : LootTier.values()) {
            Table table = tables.get(tier);
            if (table.rolls < 1) {
                throw new LootTableFormatException(tier.key() + ": missing 'rolls'");
            }
            if (table.entries.isEmpty()) {
                throw new LootTableFormatException(tier.key() + ": no entries");
            }
            if (table.rolls < previous) {
                throw new LootTableFormatException(tier.key() + ": rolls must not drop below the tier before it");
            }
            previous = table.rolls;
        }
        return new LootTables(tables);
    }

    private static void parseLine(String line, Map<LootTier, Table> tables) {
        String[] parts = line.split("\\s+");
        LootTier tier = null;
        for (LootTier t : LootTier.values()) {
            if (t.key().equals(parts[0])) {
                tier = t;
            }
        }
        if (tier == null) {
            throw new LootTableFormatException("unknown tier '" + parts[0] + "'");
        }
        if (parts.length < 3) {
            throw new LootTableFormatException("expected '<tier> <kind> <id> ...'");
        }
        Table table = tables.get(tier);
        if (parts[1].equals("rolls")) {
            table.rolls = positive(parts[2], "rolls");
            return;
        }
        LootItem.Kind kind = switch (parts[1]) {
            case "gear" -> LootItem.Kind.GEAR;
            case "food" -> LootItem.Kind.FOOD;
            case "aid" -> LootItem.Kind.AID;
            default -> throw new LootTableFormatException("unknown kind '" + parts[1] + "'");
        };
        String id = parts[2];
        if (kind == LootItem.Kind.AID && !STAIRWELL_AIDS.contains(id)) {
            throw new LootTableFormatException("unknown Stairwell Aid '" + id + "'");
        }
        int weight = 0;
        int count = 1;
        Map<String, Integer> enchants = new LinkedHashMap<>();
        for (int i = 3; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length != 2) {
                throw new LootTableFormatException("bad option '" + parts[i] + "'");
            }
            switch (kv[0]) {
                case "weight" -> weight = positive(kv[1], "weight");
                case "count" -> count = positive(kv[1], "count");
                case "enchant" -> {
                    int colon = kv[1].lastIndexOf(':');
                    if (colon <= 0) {
                        throw new LootTableFormatException("enchant must be 'id:level'");
                    }
                    enchants.put(kv[1].substring(0, colon), positive(kv[1].substring(colon + 1), "enchant level"));
                }
                default -> throw new LootTableFormatException("unknown option '" + kv[0] + "'");
            }
        }
        if (weight < 1) {
            throw new LootTableFormatException("missing 'weight'");
        }
        table.entries.add(new Entry(new LootItem(kind, id, count, Map.copyOf(enchants)), weight));
    }

    private static int positive(String text, String what) {
        try {
            int n = Integer.parseInt(text);
            if (n >= 1) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new LootTableFormatException(what + " must be a whole number of at least 1");
    }

    public int rolls(LootTier tier) {
        return tables.get(tier).rolls;
    }

    public List<LootItem> entries(LootTier tier) {
        return tables.get(tier).entries.stream().map(Entry::item).toList();
    }

    /** Rolls the tier's table; {@code pick} returns a number in {@code [0, bound)} per roll. */
    public List<LootItem> open(LootTier tier, IntUnaryOperator pick) {
        Table table = tables.get(tier);
        int total = table.entries.stream().mapToInt(Entry::weight).sum();
        List<LootItem> result = new ArrayList<>();
        for (int roll = 0; roll < table.rolls; roll++) {
            int at = pick.applyAsInt(total);
            for (Entry entry : table.entries) {
                if (at < entry.weight()) {
                    result.add(entry.item());
                    break;
                }
                at -= entry.weight();
            }
        }
        return result;
    }
}
