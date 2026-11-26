package dev.crawlmode.engine;

/** A Host line data file is malformed. */
public final class HostLineFormatException extends RuntimeException {
    public HostLineFormatException(String message) {
        super(message);
    }
}
