package dev.crawlmode.engine.floorgen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FloorTemplate {
    public static final int SIZE = 16;

    public enum Role {
        ROOM,
        CORRIDOR,
        VILLAGE,
        STAIRWELL_ROOM,
        SAFE_ROOM
    }

    public enum Furniture {
        CHEST('c', 3),
        FOOD_POT('f', 1),
        EXIT_DOOR('d', 1),
        ARRIVAL('a', 1);

        private final char symbol;
        private final int required;

        Furniture(char symbol, int required) {
            this.symbol = symbol;
            this.required = required;
        }

        static Furniture of(char symbol) {
            for (Furniture f : values()) {
                if (f.symbol == symbol) {
                    return f;
                }
            }
            return null;
        }
    }

    public record Marker(int x, int y, int z) {}

    private static final String RESERVED = ".newsk";

    private final String name;
    private final Role role;
    private final int height;
    private final Map<Character, String> palette;
    private final char[][][] grid;
    private final Marker marker;
    private final Map<Furniture, List<Marker>> furniture;

    private FloorTemplate(String name, Role role, int height, Map<Character, String> palette, char[][][] grid, Marker marker,
            Map<Furniture, List<Marker>> furniture) {
        this.name = name;
        this.role = role;
        this.height = height;
        this.palette = palette;
        this.grid = grid;
        this.marker = marker;
        this.furniture = furniture;
    }

    public String name() {
        return name;
    }

    public Role role() {
        return role;
    }

    public int height() {
        return height;
    }

    public Optional<Marker> stairwellMarker() {
        return Optional.ofNullable(marker);
    }

    public List<Marker> markers(Furniture kind) {
        return furniture.getOrDefault(kind, List.of());
    }

    public Optional<TemplateBlock> blockAt(int x, int y, int z, Set<Side> open) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < 0 || y >= height) {
            return Optional.empty();
        }
        char c = grid[y][z][x];
        if (c == 0 || c == '.' || role == Role.SAFE_ROOM && Furniture.of(c) != null) {
            return Optional.empty();
        }
        switch (c) {
            case 'n', 'e', 's', 'w' -> {
                Side side = c == 'n' ? Side.NORTH : c == 'e' ? Side.EAST : c == 's' ? Side.SOUTH : Side.WEST;
                return open.contains(side) ? Optional.empty() : Optional.of(new TemplateBlock(palette.get('#'), false));
            }
            case 'k' -> {
                return Optional.of(new TemplateBlock(palette.get('#'), true));
            }
            default -> {
                return Optional.of(new TemplateBlock(palette.get(c), false));
            }
        }
    }

    public static FloorTemplate parse(String text) {
        String name = null;
        Role role = null;
        Integer height = null;
        Map<Character, String> palette = new HashMap<>();
        String[] lines = text.split("\\R");
        char[][][] grid = null;
        Marker marker = null;
        List<Marker> cells = new ArrayList<>();
        List<Furniture> kinds = new ArrayList<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].strip();
            i++;
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "name" -> name = need(parts, 2, line)[1];
                case "role" -> role = parseRole(need(parts, 2, line)[1]);
                case "height" -> {
                    height = Integer.parseInt(need(parts, 2, line)[1]);
                    if (height < 1) {
                        throw new FloorTemplateFormatException("Height must be positive");
                    }
                    grid = new char[height][SIZE][SIZE];
                }
                case "palette" -> {
                    need(parts, 3, line);
                    if (parts[1].length() != 1 || RESERVED.indexOf(parts[1].charAt(0)) >= 0 && parts[1].charAt(0) != 'k') {
                        throw new FloorTemplateFormatException("Bad palette character in: " + line);
                    }
                    if (RESERVED.indexOf(parts[1].charAt(0)) >= 0) {
                        throw new FloorTemplateFormatException("Reserved palette character in: " + line);
                    }
                    palette.put(parts[1].charAt(0), parts[2]);
                }
                case "layer" -> {
                    if (grid == null || name == null) {
                        throw new FloorTemplateFormatException("'height' must come before layers");
                    }
                    String[] range = need(parts, 2, line)[1].split("-");
                    int from = Integer.parseInt(range[0]);
                    int to = range.length > 1 ? Integer.parseInt(range[1]) : from;
                    if (from < 0 || to < from || to >= height) {
                        throw new FloorTemplateFormatException("Layer out of range: " + line);
                    }
                    if (i + SIZE > lines.length) {
                        throw new FloorTemplateFormatException("Layer needs 16 rows: " + line);
                    }
                    for (int z = 0; z < SIZE; z++) {
                        String row = lines[i + z];
                        if (row.length() != SIZE) {
                            throw new FloorTemplateFormatException("Row must be 16 characters in " + name + ": '" + row + "'");
                        }
                        for (int x = 0; x < SIZE; x++) {
                            char c = row.charAt(x);
                            for (int y = from; y <= to; y++) {
                                grid[y][z][x] = c;
                            }
                            if (Furniture.of(c) != null) {
                                kinds.add(Furniture.of(c));
                                cells.add(new Marker(x, from, z));
                            }
                            if (c == 'X') {
                                if (marker != null) {
                                    throw new FloorTemplateFormatException("More than one Stairwell marker in " + name);
                                }
                                marker = new Marker(x, from, z);
                            }
                        }
                    }
                    i += SIZE;
                }
                default -> throw new FloorTemplateFormatException("Unknown line: " + line);
            }
        }
        if (name == null || role == null || height == null) {
            throw new FloorTemplateFormatException("Template needs name, role and height");
        }
        if (!palette.containsKey('#')) {
            throw new FloorTemplateFormatException("Template " + name + " needs a '#' palette entry");
        }
        for (char[][] layer : grid) {
            for (char[] row : layer) {
                for (char c : row) {
                    boolean furnished = role == Role.SAFE_ROOM && Furniture.of(c) != null;
                    if (c != 0 && RESERVED.indexOf(c) < 0 && !furnished && !palette.containsKey(c)) {
                        throw new FloorTemplateFormatException("Template " + name + " uses '" + c + "' with no palette entry");
                    }
                }
            }
        }
        if (role == Role.STAIRWELL_ROOM && marker == null) {
            throw new FloorTemplateFormatException("Stairwell room " + name + " has no X marker");
        }
        if (role != Role.STAIRWELL_ROOM && marker != null) {
            throw new FloorTemplateFormatException("Only stairwell rooms may have an X marker: " + name);
        }
        Map<Furniture, List<Marker>> furniture = new EnumMap<>(Furniture.class);
        if (role == Role.SAFE_ROOM) {
            for (char reserved : palette.keySet()) {
                if (Furniture.of(reserved) != null) {
                    throw new FloorTemplateFormatException("Marker character '" + reserved + "' cannot be a palette entry in " + name);
                }
            }
            for (int k = 0; k < kinds.size(); k++) {
                furniture.computeIfAbsent(kinds.get(k), f -> new ArrayList<>()).add(cells.get(k));
            }
            for (Furniture kind : Furniture.values()) {
                int found = furniture.getOrDefault(kind, List.of()).size();
                if (found != kind.required) {
                    throw new FloorTemplateFormatException("Safe room " + name + " needs " + kind.required + " " + kind
                            + " marker(s), found " + found);
                }
            }
        }
        return new FloorTemplate(name, role, height, Map.copyOf(palette), grid, marker, Map.copyOf(furniture));
    }

    private static String[] need(String[] parts, int count, String line) {
        if (parts.length != count) {
            throw new FloorTemplateFormatException("Bad line: " + line);
        }
        return parts;
    }

    private static Role parseRole(String s) {
        return switch (s) {
            case "room" -> Role.ROOM;
            case "corridor" -> Role.CORRIDOR;
            case "village" -> Role.VILLAGE;
            case "stairwell_room" -> Role.STAIRWELL_ROOM;
            case "safe_room" -> Role.SAFE_ROOM;
            default -> throw new FloorTemplateFormatException("Unknown role: " + s);
        };
    }
}
