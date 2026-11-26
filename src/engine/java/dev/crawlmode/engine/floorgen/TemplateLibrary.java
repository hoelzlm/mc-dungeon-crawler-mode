package dev.crawlmode.engine.floorgen;

import java.util.ArrayList;
import java.util.List;

public final class TemplateLibrary {
    private final List<FloorTemplate> rooms = new ArrayList<>();
    private final List<FloorTemplate> corridors = new ArrayList<>();
    private final List<FloorTemplate> villages = new ArrayList<>();
    private final List<FloorTemplate> stairwellRooms = new ArrayList<>();

    private TemplateLibrary() {}

    public static TemplateLibrary parse(String text) {
        TemplateLibrary lib = new TemplateLibrary();
        for (String chunk : text.split("(?m)^---\\s*$")) {
            FloorTemplate t = FloorTemplate.parse(chunk);
            switch (t.role()) {
                case ROOM -> lib.rooms.add(t);
                case CORRIDOR -> lib.corridors.add(t);
                case VILLAGE -> lib.villages.add(t);
                case STAIRWELL_ROOM -> lib.stairwellRooms.add(t);
            }
        }
        return lib;
    }

    public List<FloorTemplate> rooms() {
        return List.copyOf(rooms);
    }

    public List<FloorTemplate> corridors() {
        return List.copyOf(corridors);
    }

    public List<FloorTemplate> villages() {
        return List.copyOf(villages);
    }

    public List<FloorTemplate> stairwellRooms() {
        return List.copyOf(stairwellRooms);
    }
}
