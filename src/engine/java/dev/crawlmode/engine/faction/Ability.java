package dev.crawlmode.engine.faction;

public enum Ability {
    HEAL_ALLIES("heal_allies"),
    RALLY("rally"),
    FIREBALL("fireball");

    private final String key;

    Ability(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Ability fromKey(String key) {
        for (Ability a : values()) {
            if (a.key.equals(key)) {
                return a;
            }
        }
        return null;
    }
}
