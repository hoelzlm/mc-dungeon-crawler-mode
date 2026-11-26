package dev.crawlmode.engine;

/** Source of game time in ticks. Injected so tests can control time. */
public interface Clock {
    long nowTicks();
}
