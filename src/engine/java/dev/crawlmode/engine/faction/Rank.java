package dev.crawlmode.engine.faction;

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
