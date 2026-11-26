package dev.crawlmode.engine;

/** An achievements data file is malformed. */
public final class AchievementFormatException extends RuntimeException {
    public AchievementFormatException(String message) {
        super(message);
    }
}
