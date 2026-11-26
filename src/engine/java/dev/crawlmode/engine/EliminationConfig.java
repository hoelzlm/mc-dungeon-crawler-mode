package dev.crawlmode.engine;

public record EliminationConfig(Mode mode, boolean singlePlayer) {
    public enum Mode {
        PERMANENT,
        RESPAWN
    }

    public static final EliminationConfig DEFAULT = new EliminationConfig(Mode.PERMANENT, false);
}
