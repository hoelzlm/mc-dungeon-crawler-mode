package dev.crawlmode.engine.faction;

/** Ranks of a Faction that spawn in the open. The Boss is placed separately. */
public enum Rank {
    GRUNT("grunt"),
    ELITE("elite"),
    SHAMAN("shaman");

    private final String key;

    Rank(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    static Rank fromKey(String key) {
        for (Rank r : values()) {
            if (r.key.equals(key)) {
                return r;
            }
        }
        return null;
    }
}
