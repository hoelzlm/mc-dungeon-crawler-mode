package dev.crawlmode.engine.faction;

/** What a Shaman can cast. The adapter turns each into world effects on a cooldown. */
public enum Ability {
    /** Support: heals wounded Faction allies nearby. */
    HEAL_ALLIES("heal_allies"),
    /** Support: gives Faction allies nearby a short strength and speed boost. */
    RALLY("rally"),
    /** Magic: hurls a fireball at its target. */
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
