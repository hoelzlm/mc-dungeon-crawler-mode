package dev.crawlmode.engine;

/**
 * How a Crawler's death is handled. {@code singlePlayer} makes the engine run the Host game-over
 * (fresh Crawl or leave) instead of leaving the lone Crawler to spectate.
 */
public record EliminationConfig(Mode mode, boolean singlePlayer) {
    public enum Mode {
        /** Death ends the Crawler's part in the Crawl; they spectate. */
        PERMANENT,
        /** Softer: the Crawler respawns on Floor 1. */
        RESPAWN
    }

    public static final EliminationConfig DEFAULT = new EliminationConfig(Mode.PERMANENT, false);
}
