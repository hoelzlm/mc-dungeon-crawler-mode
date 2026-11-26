package dev.crawlmode.engine.floorgen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FloorTemplate {
    public static final int SIZE = 16;

    public enum Role {
        ROOM,
        CORRIDOR,
        VILLAGE,
        STAIRWELL_ROOM
    }

    public record Marker(int x, int y, int z) {}

    private static final String RESERVED = ".newsk";

    private final String name;
    private final Role role;
    private final int height;
    private final Map<Character, String> palette;
    private final char[][][] grid;
    private final Marker marker;

    private FloorTemplate(String name, Role role, int height, Map<Character, String> palette, char[][][] grid, Marker marker) {
        this.name = name;
        this.role = role;
        this.height = height;
        this.palette = palette;
        this.grid = grid;
        this.marker = marker;
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

    public Optional<TemplateBlock> blockAt(int x, int y, int z, Set<Side> open) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE || y < 0 || y >= height) {
            return Optional.empty();
        }
        char c = grid[y][z][x];
        if (c == 0 || c == '.') {
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
                    if (c != 0 && RESERVED.indexOf(c) < 0 && !palette.containsKey(c)) {
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
        return new FloorTemplate(name, role, height, Map.copyOf(palette), grid, marker);
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
            default -> throw new FloorTemplateFormatException("Unknown role: " + s);
        };
    }
}
