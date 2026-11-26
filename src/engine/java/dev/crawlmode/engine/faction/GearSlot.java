package dev.crawlmode.engine.faction;

public enum GearSlot {
    MAINHAND("mainhand"),
    OFFHAND("offhand"),
    HEAD("head"),
    CHEST("chest"),
    LEGS("legs"),
    FEET("feet");

    private final String key;

    GearSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
